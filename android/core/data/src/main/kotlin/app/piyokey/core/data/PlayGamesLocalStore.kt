package app.piyokey.core.data

import androidx.room.withTransaction
import app.piyokey.core.game.PlayGamesAchievementKey
import app.piyokey.core.game.PlayGamesLeaderboardKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingPlayGamesScore(val key: PlayGamesLeaderboardKey, val score: Long)
data class PendingPlayGamesAchievement(
  val key: PlayGamesAchievementKey,
  val currentValue: Long,
  val syncedValue: Long,
)

interface PlayGamesLocalData {
  suspend fun pendingScores(): List<PendingPlayGamesScore>
  suspend fun markScoreSubmitted(key: PlayGamesLeaderboardKey, score: Long)
  suspend fun pendingAchievements(): List<PendingPlayGamesAchievement>
  suspend fun markAchievementSynced(key: PlayGamesAchievementKey, value: Long)
}

class RoomPlayGamesLocalData(
  private val database: PiyokeyDatabase,
) : PlayGamesLocalData {
  private val dao = database.dao()

  override suspend fun pendingScores(): List<PendingPlayGamesScore> = withContext(Dispatchers.IO) {
    dao.pendingPlayGamesScores().mapNotNull { entity ->
      PlayGamesLeaderboardKey.entries.firstOrNull { it.storageKey == entity.boardKey }
        ?.let { PendingPlayGamesScore(it, entity.bestScore) }
    }
  }

  override suspend fun markScoreSubmitted(key: PlayGamesLeaderboardKey, score: Long) =
    withContext(Dispatchers.IO) { dao.markPlayGamesScoreSubmitted(key.storageKey, score) }

  override suspend fun pendingAchievements(): List<PendingPlayGamesAchievement> = withContext(Dispatchers.IO) {
    dao.playGamesAchievementProgress().mapNotNull { entity ->
      PlayGamesAchievementKey.entries.firstOrNull { it.storageKey == entity.achievementKey }
        ?.takeIf { entity.currentValue > entity.syncedValue }
        ?.let { PendingPlayGamesAchievement(it, entity.currentValue, entity.syncedValue) }
    }
  }

  override suspend fun markAchievementSynced(key: PlayGamesAchievementKey, value: Long) = withContext(Dispatchers.IO) {
    database.withTransaction { dao.markPlayGamesAchievementSynced(key.storageKey, value) }
  }
}
