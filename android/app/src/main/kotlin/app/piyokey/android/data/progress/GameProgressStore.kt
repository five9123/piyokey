package app.piyokey.android.data.progress

import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.android.data.persistence.StoreJson
import app.piyokey.android.data.persistence.deleteTree
import app.piyokey.android.platform.games.PlayGamesRules
import app.piyokey.android.platform.games.RankedRecord
import app.piyokey.core.domain.DeckProgress
import app.piyokey.core.domain.GameCompetition
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.GameProgressRules
import app.piyokey.core.domain.GameProgressSnapshot
import app.piyokey.core.domain.GameProgressStoreException
import app.piyokey.core.domain.GameRecord
import app.piyokey.core.domain.GameRecordSaveOutcome
import app.piyokey.core.domain.LeaderboardRef
import app.piyokey.core.domain.SessionInputMode
import app.piyokey.core.domain.SessionMode
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Leaderboards a record counts toward (Play Games rules), used to keep compaction representatives. */
object PlayGamesLeaderboardRefs {
  fun of(record: GameRecord): List<LeaderboardRef> = PlayGamesRules.leaderboards(
    RankedRecord(
      id = record.id,
      isGameMode = record.mode == SessionMode.GAME,
      deckId = record.deckId,
      deckVersion = record.deckVersion,
      competition = record.competition?.raw,
      score = record.score,
      playedAt = record.playedAt,
    ),
  ).map { LeaderboardRef(it.resourceKey, it.isWeekly) }
}

/**
 * `Hanco/Progress/game-progress.json` (iOS `GameProgressStore`, schema 3): every lesson/game
 * record (bounded to 2,048 with leaderboard representatives kept) and per-key best summaries.
 * Blocking file IO.
 */
class GameProgressStore(
  val rootDir: File,
  private val leaderboards: (GameRecord) -> List<LeaderboardRef> = PlayGamesLeaderboardRefs::of,
  private val clock: () -> Instant = Instant::now,
) {
  @Serializable
  private data class Index(
    @SerialName("schema_version") val schemaVersion: Int,
    val records: List<GameRecord>,
    @SerialName("deck_progress") val deckProgress: List<DeckProgress>,
    @SerialName("legacy_mixed_progress") val legacyMixedProgress: List<DeckProgress>? = null,
  )

  private val indexFile get() = File(rootDir, "game-progress.json")

  fun loadSnapshot(): GameProgressSnapshot {
    val snapshot = RecoverableJsonFile.load(
      indexFile,
      shouldRecover = { it !is GameProgressStoreException.UnsupportedSchema },
      decode = ::decode,
    ) ?: GameProgressSnapshot.EMPTY
    return compact(snapshot)
  }

  fun append(record: GameRecord): GameRecordSaveOutcome {
    val (snapshot, outcome) = GameProgressRules.applyGameRecord(record, loadSnapshot())
    write(compact(snapshot))
    return outcome
  }

  fun appendLesson(record: GameRecord) {
    write(compact(GameProgressRules.applyLessonRecord(record, loadSnapshot())))
  }

  fun reset() = rootDir.deleteTree()

  /** Background write: validates the on-disk generation first so a future schema is never downgraded. */
  internal fun persistSnapshot(snapshot: GameProgressSnapshot) {
    loadSnapshot()
    write(compact(snapshot))
  }

  internal fun compact(snapshot: GameProgressSnapshot): GameProgressSnapshot =
    GameProgressRules.compacted(snapshot, clock(), leaderboards = leaderboards)

  private fun decode(data: ByteArray): GameProgressSnapshot {
    val index = StoreJson.decode(Index.serializer(), data)
    return GameProgressRules.validatedSnapshot(index.schemaVersion, index.records, index.deckProgress, index.legacyMixedProgress)
  }

  private fun write(snapshot: GameProgressSnapshot) {
    val index = Index(
      schemaVersion = GameProgressRules.CURRENT_SCHEMA_VERSION,
      records = snapshot.records,
      deckProgress = snapshot.deckProgress.values.sortedBy { it.id },
      legacyMixedProgress = snapshot.legacyMixedProgress.values.sortedBy { it.id },
    )
    RecoverableJsonFile.write(StoreJson.encode(Index.serializer(), index), indexFile)
  }
}

/**
 * Observable game/lesson history (iOS `GameProgressLibrary`). [append] updates memory at once and
 * returns the best-score outcome; the write happens in the background ([flushAndWait] to await).
 */
class GameProgressLibrary(
  private val store: GameProgressStore,
  private val scope: CoroutineScope,
  io: CoroutineDispatcher = Dispatchers.IO,
) {
  private val recordsState = MutableStateFlow<List<GameRecord>>(emptyList())
  private val progressState = MutableStateFlow<Map<String, DeckProgress>>(emptyMap())
  private val saveFailedState = MutableStateFlow(false)
  private var legacyMixedProgress: Map<String, DeckProgress> = emptyMap()
  private val writer = RevisionWriter<GameProgressSnapshot>(io, store::persistSnapshot)
  private var retryAction: (() -> Unit)? = null
  private var revision = 0

  val records: StateFlow<List<GameRecord>> = recordsState.asStateFlow()
  val deckProgress: StateFlow<Map<String, DeckProgress>> = progressState.asStateFlow()
  val saveFailed: StateFlow<Boolean> = saveFailedState.asStateFlow()

  init {
    reload()
  }

  /** Records a game result; `null` (and [saveFailed]) when the record is invalid. */
  fun append(record: GameRecord): GameRecordSaveOutcome? = try {
    val (snapshot, outcome) = GameProgressRules.applyGameRecord(record, currentSnapshot)
    accept(store.compact(snapshot))
    flush()
    outcome
  } catch (_: GameProgressStoreException) {
    saveFailedState.value = true
    retryAction = { append(record) }
    null
  }

  /** Records a lesson (practice/curriculum/spacing); never changes game bests. */
  fun appendLesson(record: GameRecord): Boolean = try {
    accept(store.compact(GameProgressRules.applyLessonRecord(record, currentSnapshot)))
    flush()
    true
  } catch (_: GameProgressStoreException) {
    saveFailedState.value = true
    retryAction = { appendLesson(record) }
    false
  }

  fun progress(
    deckId: String,
    gameKind: GameKind = GameKind.FLOW,
    inputMode: SessionInputMode = SessionInputMode.BUILT_IN,
    competition: GameCompetition? = null,
  ): DeckProgress? = progressState.value[GameProgressRules.progressKey(deckId, gameKind, inputMode, competition)]

  fun bestCombo(
    deckId: String,
    gameKind: GameKind = GameKind.FLOW,
    inputMode: SessionInputMode = SessionInputMode.BUILT_IN,
    competition: GameCompetition? = null,
  ): Int = GameProgressRules.bestCombo(recordsState.value, deckId, gameKind, inputMode, competition)

  fun retryLastSave() {
    retryAction?.invoke()
  }

  fun flush() {
    val snapshot = currentSnapshot
    val requested = revision
    scope.launch { persist(snapshot, requested) }
  }

  suspend fun flushAndWait(): Boolean = persist(currentSnapshot, revision)

  private val currentSnapshot: GameProgressSnapshot
    get() = GameProgressSnapshot(recordsState.value, progressState.value, legacyMixedProgress)

  private fun accept(snapshot: GameProgressSnapshot) {
    revision += 1
    recordsState.value = snapshot.records
    progressState.value = snapshot.deckProgress
    legacyMixedProgress = snapshot.legacyMixedProgress
  }

  private suspend fun persist(snapshot: GameProgressSnapshot, requested: Int): Boolean = try {
    writer.persist(snapshot, requested)
    if (requested == revision) {
      saveFailedState.value = false
      retryAction = null
    }
    true
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (_: Exception) {
    if (requested == revision) {
      saveFailedState.value = true
      retryAction = { flush() }
    }
    false
  }

  fun reload() {
    try {
      val snapshot = store.loadSnapshot()
      recordsState.value = snapshot.records
      progressState.value = snapshot.deckProgress
      legacyMixedProgress = snapshot.legacyMixedProgress
      // A loaded snapshot may include a schema migration; let the next flush persist it.
      // Stay monotonic: RevisionWriter drops revisions it has already seen.
      revision += 1
      saveFailedState.value = false
      retryAction = null
    } catch (_: Exception) {
      recordsState.value = emptyList()
      progressState.value = emptyMap()
      legacyMixedProgress = emptyMap()
      saveFailedState.value = true
      retryAction = { reload() }
    }
  }
}
