package app.piyokey.android.platform.games

import android.app.Activity
import android.content.Context
import app.piyokey.android.R
import app.piyokey.android.Services
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional Play Games Services v2 adapter (iOS `GameCenterService`). Local records stay the
 * source of truth. The service is [isAvailable] only when the game project ID and **every**
 * leaderboard/achievement ID were injected at build time (`piyokey_pgs_*`); otherwise all calls
 * are no-ops. The SDK is initialized lazily on first use (never in `Application`), and the sign-in
 * UI is shown only from [signIn], i.e. an explicit user action.
 */
object PlayGamesService {
  enum class ConnectionState { IDLE, AUTHENTICATING, SIGN_IN_REQUIRED, AUTHENTICATED, UNAVAILABLE }

  private val leaderboardRes: Map<PiyoLeaderboard, Int> = mapOf(
    PiyoLeaderboard.FLOW_BEGINNER to R.string.piyokey_pgs_flow_beginner,
    PiyoLeaderboard.FLOW_INTERMEDIATE to R.string.piyokey_pgs_flow_intermediate,
    PiyoLeaderboard.FLOW_ADVANCED to R.string.piyokey_pgs_flow_advanced,
    PiyoLeaderboard.ACID_RAIN_BEGINNER to R.string.piyokey_pgs_acid_rain_beginner,
    PiyoLeaderboard.ACID_RAIN_INTERMEDIATE to R.string.piyokey_pgs_acid_rain_intermediate,
    PiyoLeaderboard.ACID_RAIN_ADVANCED to R.string.piyokey_pgs_acid_rain_advanced,
    PiyoLeaderboard.CHOSEONG_BEGINNER to R.string.piyokey_pgs_choseong_beginner,
    PiyoLeaderboard.CHOSEONG_INTERMEDIATE to R.string.piyokey_pgs_choseong_intermediate,
    PiyoLeaderboard.CHOSEONG_ADVANCED to R.string.piyokey_pgs_choseong_advanced,
    PiyoLeaderboard.WORD_MATCH_BEGINNER to R.string.piyokey_pgs_word_match_beginner,
    PiyoLeaderboard.WORD_MATCH_INTERMEDIATE to R.string.piyokey_pgs_word_match_intermediate,
    PiyoLeaderboard.WORD_MATCH_ADVANCED to R.string.piyokey_pgs_word_match_advanced,
    PiyoLeaderboard.DICTATION_BEGINNER to R.string.piyokey_pgs_dictation_beginner,
    PiyoLeaderboard.DICTATION_INTERMEDIATE to R.string.piyokey_pgs_dictation_intermediate,
    PiyoLeaderboard.DICTATION_ADVANCED to R.string.piyokey_pgs_dictation_advanced,
    PiyoLeaderboard.WEEKLY_PIYO_CUP to R.string.piyokey_pgs_cup_weekly_flow,
  )

  private val achievementRes: Map<PiyoAchievement, Int> = mapOf(
    PiyoAchievement.HATCHING to R.string.piyokey_pgs_growth_hatching,
    PiyoAchievement.CHICK to R.string.piyokey_pgs_growth_chick,
    PiyoAchievement.ROOSTER to R.string.piyokey_pgs_growth_rooster,
    PiyoAchievement.TYPED_JAMO to R.string.piyokey_pgs_growth_typed_12000,
    PiyoAchievement.STREAK to R.string.piyokey_pgs_growth_streak_30,
  )

  private data class Config(
    val leaderboards: Map<PiyoLeaderboard, String>,
    val achievements: Map<PiyoAchievement, String>,
  )

  private val config: Config? by lazy {
    if (!Services.isInstalled) return@lazy null
    val res = Services.context.resources
    val project = res.getString(R.string.game_services_project_id).trim()
    val boards = leaderboardRes.mapValues { res.getString(it.value).trim() }
    val achievements = achievementRes.mapValues { res.getString(it.value).trim() }
    val complete = project.isNotEmpty() && project != "0" &&
      boards.values.none(String::isEmpty) && achievements.values.none(String::isEmpty)
    if (complete) Config(boards, achievements) else null
  }

  @Volatile private var sdkInitialized = false

  private val connectionState = MutableStateFlow(ConnectionState.IDLE)
  private val playerNameState = MutableStateFlow<String?>(null)
  private val ranksState = MutableStateFlow<Map<PiyoLeaderboard, Long>>(emptyMap())
  private val statesState = MutableStateFlow<Map<PiyoLeaderboard, SubmissionState>>(emptyMap())

  private val submittedScores = mutableMapOf<PiyoLeaderboard, Int>()
  private val submittingScores = mutableMapOf<PiyoLeaderboard, Int>()
  private val reportedAchievements = mutableSetOf<PiyoAchievement>()

  /** True when every Play Games ID was injected; otherwise the adapter is inert. */
  val isAvailable: Boolean get() = config != null

  val connection: StateFlow<ConnectionState> get() = connectionState.asStateFlow()
  val playerName: StateFlow<String?> = playerNameState.asStateFlow()
  val ranks: StateFlow<Map<PiyoLeaderboard, Long>> = ranksState.asStateFlow()
  val submissionStates: StateFlow<Map<PiyoLeaderboard, SubmissionState>> = statesState.asStateFlow()

  val isAuthenticated: Boolean get() = connectionState.value == ConnectionState.AUTHENTICATED

  fun leaderboardId(board: PiyoLeaderboard): String? = config?.leaderboards?.get(board)

  /** Boards (routed by [PlayGamesRules]) this build can submit the record to. */
  fun leaderboards(record: RankedRecord): List<PiyoLeaderboard> =
    if (isAvailable) PlayGamesRules.leaderboards(record) else emptyList()

  fun isLeaderboardAvailable(record: RankedRecord) = leaderboards(record).isNotEmpty()

  fun submissionState(record: RankedRecord): SubmissionState? =
    leaderboards(record).firstOrNull()?.let { statesState.value[it] ?: SubmissionState.IDLE }

  fun rank(record: RankedRecord): Long? =
    PlayGamesRules.rank(record, ranksState.value, PiyoLeaderboard.entries.filterTo(mutableSetOf()) { leaderboardId(it) != null })

  /** Silent check (no UI). Safe to call on screen appear/foreground. */
  suspend fun refreshSignInState(activity: Activity): Boolean {
    if (!ensureSdk(activity)) return false
    val authenticated = runCatching {
      PlayGames.getGamesSignInClient(activity).isAuthenticated().await().isAuthenticated
    }.getOrDefault(false)
    adopt(activity, authenticated)
    return authenticated
  }

  /** Explicit, user-initiated sign-in (the only path that may show Play Games UI). */
  suspend fun signIn(activity: Activity): Boolean {
    if (!ensureSdk(activity)) return false
    connectionState.value = ConnectionState.AUTHENTICATING
    val client = PlayGames.getGamesSignInClient(activity)
    val already = runCatching { client.isAuthenticated().await().isAuthenticated }.getOrDefault(false)
    val authenticated = already || runCatching { client.signIn().await().isAuthenticated }.getOrDefault(false)
    adopt(activity, authenticated)
    return authenticated
  }

  /**
   * Submits the kept best for [record]'s board (computed over [history] + [record]) and emits
   * `SUBMITTING → CONFIRMING → CONFIRMED`, or `FAILED`/`UNCONFIRMED` with at most
   * [SubmissionPolicy.MAX_RETRIES] retries. Emits nothing if unavailable, unranked or signed out.
   */
  fun submit(activity: Activity, record: RankedRecord, history: List<RankedRecord> = emptyList()): Flow<SubmissionState> = flow {
    val board = leaderboards(record).firstOrNull() ?: return@flow
    val id = leaderboardId(board) ?: return@flow
    if (!isAuthenticated && !refreshSignInState(activity)) return@flow
    val all = if (history.any { it.id == record.id }) history else history + record
    val best = PlayGamesRules.bestScores(all)[board] ?: return@flow
    if (!SubmissionPolicy.shouldSubmit(best, submittedScores[board], submittingScores[board])) {
      statesState.value[board]?.let { emit(it) }
      return@flow
    }
    val tracker = SubmissionTracker(best)
    val leaderboards = PlayGames.getLeaderboardsClient(activity)
    while (true) {
      publish(board, tracker.begin())
      emit(tracker.state)
      submittingScores[board] = best
      val accepted = withTimeoutOrNull(SubmissionPolicy.REQUEST_TIMEOUT_MILLIS) {
        runCatching { leaderboards.submitScoreImmediate(id, best.toLong()).await() }.isSuccess
      } == true
      submittingScores.remove(board)
      publish(board, tracker.submitFinished(accepted))
      emit(tracker.state)
      if (accepted) {
        submittedScores[board] = max(submittedScores[board] ?: 0, best)
        var serverScore: Long? = null
        for (wait in SubmissionPolicy.confirmationDelaysMillis) {
          if (wait > 0) delay(wait)
          val read = readBack(activity, board, id)
          if (read != null) {
            serverScore = read.first
            read.second?.let { rank -> ranksState.update { it + (board to rank) } }
          }
          publish(board, tracker.readBack(serverScore))
          if (tracker.state == SubmissionState.CONFIRMED) {
            emit(tracker.state)
            return@flow
          }
        }
        // A successful submit is not a successful read-back: release the floor so a retry can repair it.
        serverScore?.let { submittedScores[board] = it.toInt() } ?: submittedScores.remove(board)
        publish(board, tracker.confirmationExhausted())
        emit(tracker.state)
      }
      val attempt = tracker.nextRetry() ?: return@flow
      delay(SubmissionPolicy.retryDelayMillis(attempt))
    }
  }

  /** Opens one board (or all boards when [board] is null). Signs in first if needed (explicit action). */
  suspend fun showLeaderboard(activity: Activity, board: PiyoLeaderboard? = null): Boolean {
    if (!isAvailable) return false
    if (!isAuthenticated && !signIn(activity)) return false
    val client = PlayGames.getLeaderboardsClient(activity)
    val intent = runCatching {
      if (board == null) {
        client.allLeaderboardsIntent.await()
      } else {
        val id = leaderboardId(board) ?: return false
        val span = if (board.isWeekly) LeaderboardVariant.TIME_SPAN_WEEKLY else LeaderboardVariant.TIME_SPAN_ALL_TIME
        client.getLeaderboardIntent(id, span).await()
      }
    }.getOrNull() ?: return false
    @Suppress("DEPRECATION")
    return runCatching { activity.startActivityForResult(intent, REQUEST_LEADERBOARD) }.isSuccess
  }

  /** Convenience for a result screen: the record's own board. */
  suspend fun showLeaderboard(activity: Activity, record: RankedRecord): Boolean {
    val board = leaderboards(record).firstOrNull() ?: return false
    return showLeaderboard(activity, board)
  }

  /**
   * Unlocks growth achievements that reached 100 % (iOS reports percentages; Play achievements
   * are configured as standard unlocks). No-op when signed out.
   */
  suspend fun unlockAchievements(activity: Activity, snapshot: GrowthSnapshot) {
    val ids = config?.achievements ?: return
    if (!isAuthenticated) return
    val client = PlayGames.getAchievementsClient(activity)
    for (achievement in PiyoAchievement.unlocked(snapshot) - reportedAchievements) {
      val id = ids[achievement] ?: continue
      val ok = withTimeoutOrNull(SubmissionPolicy.REQUEST_TIMEOUT_MILLIS) {
        runCatching { client.unlockImmediate(id).await() }.isSuccess
      } == true
      if (ok) reportedAchievements += achievement
    }
  }

  private const val REQUEST_LEADERBOARD = 9_001

  private fun ensureSdk(context: Context): Boolean {
    if (!isAvailable) {
      connectionState.value = ConnectionState.UNAVAILABLE
      return false
    }
    if (!sdkInitialized) {
      val ok = runCatching { PlayGamesSdk.initialize(context.applicationContext) }.isSuccess
      if (!ok) {
        connectionState.value = ConnectionState.UNAVAILABLE
        return false
      }
      sdkInitialized = true
    }
    return true
  }

  private suspend fun adopt(activity: Activity, authenticated: Boolean) {
    if (!authenticated) {
      clearPlayerSession()
      connectionState.value = ConnectionState.SIGN_IN_REQUIRED
      return
    }
    val player = runCatching { PlayGames.getPlayersClient(activity).currentPlayer.await() }.getOrNull()
    val name = player?.displayName
    if (player != null && playerId != player.playerId) {
      clearPlayerSession()
      playerId = player.playerId
    }
    playerNameState.value = name
    connectionState.value = ConnectionState.AUTHENTICATED
  }

  private var playerId: String? = null

  private fun clearPlayerSession() {
    playerId = null
    playerNameState.value = null
    ranksState.value = emptyMap()
    statesState.value = emptyMap()
    submittedScores.clear()
    submittingScores.clear()
    reportedAchievements.clear()
  }

  private fun publish(board: PiyoLeaderboard, state: SubmissionState) {
    statesState.update { it + (board to state) }
  }

  /** (raw score, rank) of the signed-in player, or null when the read failed or has no entry. */
  private suspend fun readBack(activity: Activity, board: PiyoLeaderboard, id: String): Pair<Long, Long?>? =
    withTimeoutOrNull(SubmissionPolicy.REQUEST_TIMEOUT_MILLIS) {
      runCatching {
        val span = if (board.isWeekly) LeaderboardVariant.TIME_SPAN_WEEKLY else LeaderboardVariant.TIME_SPAN_ALL_TIME
        val score = PlayGames.getLeaderboardsClient(activity)
          .loadCurrentPlayerLeaderboardScore(id, span, LeaderboardVariant.COLLECTION_PUBLIC)
          .await()
          .get() ?: return@runCatching null
        score.rawScore to score.rank.takeIf { it > 0 }
      }.getOrNull()
    }
}

/** Minimal `Task.await()` (kotlinx-coroutines-play-services is not a dependency). */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
  addOnCompleteListener { task ->
    if (task.isSuccessful) {
      @Suppress("UNCHECKED_CAST")
      continuation.resume(task.result as T)
    } else {
      continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Task failed")))
    }
  }
}
