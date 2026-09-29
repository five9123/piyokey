package app.piyokey.android.data.progress

import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.android.data.persistence.StoreJson
import app.piyokey.android.data.persistence.deleteTree
import app.piyokey.core.domain.CurriculumActiveSession
import app.piyokey.core.domain.CurriculumProgressMutation
import app.piyokey.core.domain.CurriculumProgressRules
import app.piyokey.core.domain.CurriculumProgressSnapshot
import app.piyokey.core.domain.CurriculumProgressStoreException
import app.piyokey.core.domain.CurriculumStageProgress
import app.piyokey.core.domain.PracticeSessionCheckpoint
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Hanco/Curriculum/curriculum-progress.json` (iOS `CurriculumProgressStore`, schema 1). Blocking IO. */
class CurriculumProgressStore(val rootDir: File) {
  @Serializable
  private data class Index(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("stage_progress") val stageProgress: List<CurriculumStageProgress>,
    @SerialName("active_session") val activeSession: CurriculumActiveSession? = null,
  )

  private val indexFile get() = File(rootDir, "curriculum-progress.json")

  fun loadSnapshot(): CurriculumProgressSnapshot = RecoverableJsonFile.load(
    indexFile,
    shouldRecover = { it !is CurriculumProgressStoreException.UnsupportedSchema },
    decode = ::decode,
  ) ?: CurriculumProgressSnapshot.EMPTY

  fun saveActiveSession(stageId: String, checkpoint: PracticeSessionCheckpoint, at: Instant = Instant.now()): CurriculumProgressMutation {
    val change = CurriculumProgressRules.applySave(stageId, checkpoint, at, loadSnapshot())
    write(change.snapshot)
    return change.mutation
  }

  fun finishStage(stageId: String, stars: Int, accuracy: Double, at: Instant = Instant.now()): CurriculumProgressMutation {
    val change = CurriculumProgressRules.applyFinish(stageId, stars, accuracy, at, loadSnapshot())
    write(change.snapshot)
    return change.mutation
  }

  fun clearActiveSession(stageId: String? = null): CurriculumProgressMutation {
    val change = CurriculumProgressRules.applyClear(stageId, loadSnapshot())
    if (change.mutation != CurriculumProgressMutation.UNCHANGED) write(change.snapshot)
    return change.mutation
  }

  fun reset() = rootDir.deleteTree()

  /** Inspects the on-disk generation first (never downgrades a future schema). */
  internal fun persistSnapshot(snapshot: CurriculumProgressSnapshot) {
    loadSnapshot()
    write(snapshot)
  }

  private fun decode(data: ByteArray): CurriculumProgressSnapshot {
    val index = StoreJson.decode(Index.serializer(), data)
    if (index.schemaVersion != CurriculumProgressRules.CURRENT_SCHEMA_VERSION) {
      throw CurriculumProgressStoreException.UnsupportedSchema(index.schemaVersion)
    }
    return CurriculumProgressRules.validated(index.stageProgress, index.activeSession)
  }

  private fun write(snapshot: CurriculumProgressSnapshot) {
    val index = Index(
      schemaVersion = CurriculumProgressRules.CURRENT_SCHEMA_VERSION,
      stageProgress = snapshot.stageProgress.values.sortedBy { it.stageId },
      activeSession = snapshot.activeSession,
    )
    RecoverableJsonFile.write(StoreJson.encode(Index.serializer(), index), indexFile)
  }
}

/**
 * Observable curriculum progress (iOS `CurriculumProgressLibrary`). Checkpoints are debounced
 * (750 ms); [finishAndWait] durably verifies a completion and rolls back the optimistic state if
 * the write fails (completion unlocks navigation, e.g. the hatch onboarding).
 */
class CurriculumProgressLibrary(
  private val store: CurriculumProgressStore,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher = Dispatchers.IO,
  private val debounceMillis: Long = 750,
  private val clock: () -> Instant = Instant::now,
) {
  private val progressState = MutableStateFlow<Map<String, CurriculumStageProgress>>(emptyMap())
  private val activeSessionState = MutableStateFlow<CurriculumActiveSession?>(null)
  private val saveFailedState = MutableStateFlow(false)
  private val writer = RevisionWriter<CurriculumProgressSnapshot>(io, store::persistSnapshot)
  private var retryAction: (() -> Unit)? = null
  private var debounceJob: Job? = null
  private var revision = 0

  val stageProgress: StateFlow<Map<String, CurriculumStageProgress>> = progressState.asStateFlow()
  val activeSession: StateFlow<CurriculumActiveSession?> = activeSessionState.asStateFlow()
  val saveFailed: StateFlow<Boolean> = saveFailedState.asStateFlow()

  init {
    reload()
  }

  val completedStageIds: Set<String> get() = progressState.value.keys

  fun progress(stageId: String): CurriculumStageProgress? = progressState.value[stageId]

  fun checkpoint(stageId: String): PracticeSessionCheckpoint? =
    activeSessionState.value?.takeIf { it.stageId == stageId }?.checkpoint

  /** Saves the resumable position (debounced write). */
  fun save(stageId: String, checkpoint: PracticeSessionCheckpoint) {
    accept(CurriculumProgressRules.applySave(stageId, checkpoint, clock(), currentSnapshot).snapshot)
    scheduleDebouncedFlush()
  }

  /** Fire-and-forget completion; use [finishAndWait] when navigation depends on durability. */
  fun finish(stageId: String, stars: Int, accuracy: Double) {
    applyFinish(stageId, stars, accuracy)
    flush()
  }

  suspend fun finishAndWait(stageId: String, stars: Int, accuracy: Double): Boolean {
    val previous = currentSnapshot
    applyFinish(stageId, stars, accuracy)
    val completionRevision = revision
    debounceJob?.cancel()
    debounceJob = null
    val snapshot = currentSnapshot
    val succeeded = try {
      writer.persistVerified(completionRevision) {
        withContext(io) {
          store.persistSnapshot(snapshot)
          val persisted = store.loadSnapshot()
          if (persisted.activeSession?.stageId == stageId) return@withContext false
          val expected = snapshot.stageProgress[stageId]
          val actual = persisted.stageProgress[stageId]
          when {
            expected == null && actual == null -> true
            expected != null && actual != null -> expected.stars == actual.stars && expected.bestAccuracy == actual.bestAccuracy
            else -> false
          }
        }
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (_: Exception) {
      false
    }
    if (succeeded) {
      if (completionRevision == revision) {
        saveFailedState.value = false
        retryAction = null
      }
      return true
    }
    if (completionRevision != revision) return false
    // Restore the exact prior state so a failed write never leaves an optimistic unlock visible.
    revision += 1
    progressState.value = previous.stageProgress
    activeSessionState.value = previous.activeSession
    saveFailedState.value = true
    retryAction = { finish(stageId, stars, accuracy) }
    return false
  }

  fun flush() {
    debounceJob?.cancel()
    debounceJob = null
    val snapshot = currentSnapshot
    val requested = revision
    scope.launch { persist(snapshot, requested) }
  }

  suspend fun flushAndWait(): Boolean {
    debounceJob?.cancel()
    debounceJob = null
    return persist(currentSnapshot, revision)
  }

  fun retryLastSave() {
    retryAction?.invoke()
  }

  private val currentSnapshot: CurriculumProgressSnapshot
    get() = CurriculumProgressSnapshot(progressState.value, activeSessionState.value)

  private fun applyFinish(stageId: String, stars: Int, accuracy: Double) {
    accept(CurriculumProgressRules.applyFinish(stageId, stars, accuracy, clock(), currentSnapshot).snapshot)
  }

  private fun accept(snapshot: CurriculumProgressSnapshot) {
    revision += 1
    progressState.value = snapshot.stageProgress
    activeSessionState.value = snapshot.activeSession
  }

  private fun scheduleDebouncedFlush() {
    debounceJob?.cancel()
    val snapshot = currentSnapshot
    val requested = revision
    debounceJob = scope.launch {
      delay(debounceMillis)
      if (requested != revision) return@launch
      debounceJob = null
      persist(snapshot, requested)
    }
  }

  private suspend fun persist(snapshot: CurriculumProgressSnapshot, requested: Int): Boolean = try {
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
      progressState.value = snapshot.stageProgress
      activeSessionState.value = snapshot.activeSession
      // Keep the revision monotonic: RevisionWriter drops revisions it has already seen.
      saveFailedState.value = false
      retryAction = null
    } catch (_: Exception) {
      progressState.value = emptyMap()
      activeSessionState.value = null
      saveFailedState.value = true
      retryAction = { reload() }
    }
  }
}
