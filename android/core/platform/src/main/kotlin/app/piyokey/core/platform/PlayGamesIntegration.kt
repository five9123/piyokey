package app.piyokey.core.platform

import android.app.Activity
import android.content.Context
import app.piyokey.core.data.PlayGamesLocalData
import app.piyokey.core.game.PlayGamesAchievementKey
import app.piyokey.core.game.PlayGamesLeaderboardKey
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine

data class PlayGamesResourceConfig(
  val projectId: String,
  val leaderboards: Map<PlayGamesLeaderboardKey, String>,
  val achievements: Map<PlayGamesAchievementKey, String>,
) {
  val isComplete: Boolean
    get() = projectId.isNotBlank() && projectId != "0" &&
      leaderboards.size == PlayGamesLeaderboardKey.entries.size &&
      achievements.size == PlayGamesAchievementKey.entries.size &&
      leaderboards.values.none(String::isBlank) && achievements.values.none(String::isBlank)

  companion object {
    fun from(context: Context): PlayGamesResourceConfig {
      fun string(name: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) "" else context.getString(id)
      }
      return PlayGamesResourceConfig(
        projectId = string("game_services_project_id"),
        leaderboards = PlayGamesLeaderboardKey.entries.associateWith { string("piyokey_pgs_${it.storageKey}") },
        achievements = PlayGamesAchievementKey.entries.associateWith { string("piyokey_pgs_${it.storageKey}") },
      )
    }
  }
}

object PlayGamesInitializer {
  fun initializeIfConfigured(context: Context): Boolean {
    if (!PlayGamesResourceConfig.from(context).isComplete) return false
    PlayGamesSdk.initialize(context.applicationContext)
    return true
  }
}

interface PlayGamesGateway {
  val enabled: Boolean
  suspend fun isAuthenticated(): Boolean
  suspend fun signIn(): Boolean
  suspend fun submitScore(key: PlayGamesLeaderboardKey, score: Long)
  suspend fun syncAchievement(key: PlayGamesAchievementKey, currentValue: Long)
  suspend fun hasServerScore(key: PlayGamesLeaderboardKey): Boolean
  suspend fun openLeaderboard(key: PlayGamesLeaderboardKey)
}

class GooglePlayGamesGateway(
  private val activity: Activity,
  private val config: PlayGamesResourceConfig = PlayGamesResourceConfig.from(activity),
) : PlayGamesGateway {
  override val enabled: Boolean get() = config.isComplete

  override suspend fun isAuthenticated(): Boolean {
    if (!enabled) return false
    return PlayGames.getGamesSignInClient(activity).isAuthenticated.await().isAuthenticated
  }

  override suspend fun signIn(): Boolean {
    if (!enabled) return false
    return PlayGames.getGamesSignInClient(activity).signIn().await().isAuthenticated
  }

  override suspend fun submitScore(key: PlayGamesLeaderboardKey, score: Long) {
    val id = requireNotNull(config.leaderboards[key])
    PlayGames.getLeaderboardsClient(activity).submitScoreImmediate(id, score).await()
  }

  override suspend fun syncAchievement(key: PlayGamesAchievementKey, currentValue: Long) {
    val id = requireNotNull(config.achievements[key])
    val client = PlayGames.getAchievementsClient(activity)
    if (key.target == 1L) {
      client.unlockImmediate(id).await()
    } else {
      client.setStepsImmediate(id, currentValue.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).await()
    }
  }

  override suspend fun hasServerScore(key: PlayGamesLeaderboardKey): Boolean {
    val id = requireNotNull(config.leaderboards[key])
    return PlayGames.getLeaderboardsClient(activity).loadCurrentPlayerLeaderboardScore(
      id,
      LeaderboardVariant.TIME_SPAN_ALL_TIME,
      LeaderboardVariant.COLLECTION_PUBLIC,
    ).await().get() != null
  }

  override suspend fun openLeaderboard(key: PlayGamesLeaderboardKey) {
    val id = requireNotNull(config.leaderboards[key])
    activity.startActivity(PlayGames.getLeaderboardsClient(activity).getLeaderboardIntent(id).await())
  }
}

enum class PlayGamesConnection { DISABLED, SIGNED_OUT, SYNCING, READY, ERROR }

data class PlayGamesState(
  val connection: PlayGamesConnection = PlayGamesConnection.DISABLED,
  val lastError: Boolean = false,
)

/** Local data is authoritative. Google failures retain the outbox and never block gameplay. */
class PlayGamesManager(
  private val gateway: PlayGamesGateway,
  private val localData: PlayGamesLocalData,
  private val onChampionUnlocked: () -> Unit,
) {
  private val mutex = Mutex()
  private val mutableState = MutableStateFlow(
    PlayGamesState(if (gateway.enabled) PlayGamesConnection.SIGNED_OUT else PlayGamesConnection.DISABLED),
  )
  val state: StateFlow<PlayGamesState> = mutableState.asStateFlow()

  suspend fun prepare() = mutex.withLock {
    if (!gateway.enabled) return@withLock
    runCatching { gateway.isAuthenticated() }
      .onSuccess { authenticated ->
        if (authenticated) syncLocked(confirmExistingScore = true)
        else mutableState.value = PlayGamesState(PlayGamesConnection.SIGNED_OUT)
      }
      .onFailure { error ->
        if (error is CancellationException) throw error
        mutableState.value = PlayGamesState(PlayGamesConnection.ERROR, lastError = true)
      }
  }

  suspend fun syncAfterLocalSave() = mutex.withLock {
    if (!gateway.enabled) return@withLock
    runCatching { gateway.isAuthenticated() }.getOrDefault(false).let { authenticated ->
      if (authenticated) syncLocked(confirmExistingScore = false)
      else mutableState.value = PlayGamesState(PlayGamesConnection.SIGNED_OUT)
    }
  }

  /** The only sign-in boundary: called from an eligible result-screen button. */
  suspend fun openLeaderboard(key: PlayGamesLeaderboardKey): Boolean = mutex.withLock {
    if (!gateway.enabled) return@withLock false
    return@withLock try {
      val authenticated = gateway.isAuthenticated() || gateway.signIn()
      if (!authenticated) {
        mutableState.value = PlayGamesState(PlayGamesConnection.SIGNED_OUT)
        false
      } else {
        syncLocked(confirmExistingScore = true)
        gateway.openLeaderboard(key)
        true
      }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      mutableState.value = PlayGamesState(PlayGamesConnection.ERROR, lastError = true)
      false
    }
  }

  private suspend fun syncLocked(confirmExistingScore: Boolean) {
    mutableState.value = PlayGamesState(PlayGamesConnection.SYNCING)
    var submitted = false
    var failed = false
    localData.pendingScores().forEach { pending ->
      try {
        gateway.submitScore(pending.key, pending.score)
        localData.markScoreSubmitted(pending.key, pending.score)
        submitted = true
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        failed = true
      }
    }
    localData.pendingAchievements().forEach { pending ->
      try {
        gateway.syncAchievement(pending.key, pending.currentValue)
        localData.markAchievementSynced(pending.key, pending.currentValue)
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        failed = true
      }
    }
    val confirmed = if (confirmExistingScore && !submitted) {
      PlayGamesLeaderboardKey.entries.any { key -> runCatching { gateway.hasServerScore(key) }.getOrDefault(false) }
    } else false
    if (submitted || confirmed) onChampionUnlocked()
    mutableState.value = PlayGamesState(
      connection = if (failed) PlayGamesConnection.ERROR else PlayGamesConnection.READY,
      lastError = failed,
    )
  }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
  addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
  addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
  addOnCanceledListener { continuation.cancel() }
}
