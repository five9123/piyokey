package app.piyokey.core.platform

import app.piyokey.core.data.PendingPlayGamesAchievement
import app.piyokey.core.data.PendingPlayGamesScore
import app.piyokey.core.data.PlayGamesLocalData
import app.piyokey.core.game.PlayGamesAchievementKey
import app.piyokey.core.game.PlayGamesLeaderboardKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PlayGamesManagerTest {
  @Test
  fun disabledConfigurationNeverAuthenticatesOrOpensUi() = runTest {
    val gateway = FakeGateway(enabled = false)
    val manager = PlayGamesManager(gateway, FakeLocalData(), {})

    manager.prepare()
    assertFalse(manager.openLeaderboard(PlayGamesLeaderboardKey.FLOW_BEGINNER))
    assertEquals(0, gateway.authChecks)
    assertEquals(PlayGamesConnection.DISABLED, manager.state.value.connection)
  }

  @Test
  fun prepareDoesNotPromptSignedOutUser() = runTest {
    val gateway = FakeGateway(authenticated = false)
    val manager = PlayGamesManager(gateway, FakeLocalData(), {})

    manager.prepare()

    assertEquals(1, gateway.authChecks)
    assertEquals(0, gateway.signIns)
    assertEquals(PlayGamesConnection.SIGNED_OUT, manager.state.value.connection)
  }

  @Test
  fun explicitResultActionSignsInSyncsAndOpensOnlyRequestedBoard() = runTest {
    val gateway = FakeGateway(authenticated = false, signInResult = true)
    val local = FakeLocalData(
      scores = mutableListOf(PendingPlayGamesScore(PlayGamesLeaderboardKey.CHOSEONG_ADVANCED, 900)),
      achievements = mutableListOf(
        PendingPlayGamesAchievement(PlayGamesAchievementKey.JAMO_12000, 4_500, 2_000),
      ),
    )
    var trophy = 0
    val manager = PlayGamesManager(gateway, local) { trophy += 1 }

    assertTrue(manager.openLeaderboard(PlayGamesLeaderboardKey.CHOSEONG_ADVANCED))

    assertEquals(1, gateway.signIns)
    assertEquals(listOf(PlayGamesLeaderboardKey.CHOSEONG_ADVANCED to 900L), gateway.submitted)
    assertEquals(listOf(PlayGamesAchievementKey.JAMO_12000 to 4_500L), gateway.syncedAchievements)
    assertEquals(listOf(PlayGamesLeaderboardKey.CHOSEONG_ADVANCED), gateway.opened)
    assertEquals(1, trophy)
    assertTrue(local.scores.isEmpty())
    assertTrue(local.achievements.isEmpty())
  }

  @Test
  fun failedSubmissionStaysPendingAndLocalStateRemainsUsable() = runTest {
    val gateway = FakeGateway(authenticated = true, failSubmission = true)
    val local = FakeLocalData(
      scores = mutableListOf(PendingPlayGamesScore(PlayGamesLeaderboardKey.FLOW_BEGINNER, 500)),
    )
    val manager = PlayGamesManager(gateway, local, {})

    manager.syncAfterLocalSave()

    assertEquals(1, local.scores.size)
    assertEquals(PlayGamesConnection.ERROR, manager.state.value.connection)
  }

  @Test
  fun confirmedExistingServerScoreUnlocksTrophyWithoutPendingSubmission() = runTest {
    val gateway = FakeGateway(authenticated = true, serverScore = true)
    var trophy = false
    val manager = PlayGamesManager(gateway, FakeLocalData()) { trophy = true }

    manager.prepare()

    assertTrue(trophy)
    assertEquals(PlayGamesConnection.READY, manager.state.value.connection)
  }
}

private class FakeGateway(
  override val enabled: Boolean = true,
  var authenticated: Boolean = true,
  private val signInResult: Boolean = false,
  private val failSubmission: Boolean = false,
  private val serverScore: Boolean = false,
) : PlayGamesGateway {
  var authChecks = 0
  var signIns = 0
  val submitted = mutableListOf<Pair<PlayGamesLeaderboardKey, Long>>()
  val syncedAchievements = mutableListOf<Pair<PlayGamesAchievementKey, Long>>()
  val opened = mutableListOf<PlayGamesLeaderboardKey>()

  override suspend fun isAuthenticated(): Boolean { authChecks += 1; return authenticated }
  override suspend fun signIn(): Boolean { signIns += 1; authenticated = signInResult; return authenticated }
  override suspend fun submitScore(key: PlayGamesLeaderboardKey, score: Long) {
    if (failSubmission) error("offline")
    submitted += key to score
  }
  override suspend fun syncAchievement(key: PlayGamesAchievementKey, currentValue: Long) {
    syncedAchievements += key to currentValue
  }
  override suspend fun hasServerScore(key: PlayGamesLeaderboardKey): Boolean = serverScore
  override suspend fun openLeaderboard(key: PlayGamesLeaderboardKey) { opened += key }
}

private class FakeLocalData(
  val scores: MutableList<PendingPlayGamesScore> = mutableListOf(),
  val achievements: MutableList<PendingPlayGamesAchievement> = mutableListOf(),
) : PlayGamesLocalData {
  override suspend fun pendingScores(): List<PendingPlayGamesScore> = scores.toList()
  override suspend fun markScoreSubmitted(key: PlayGamesLeaderboardKey, score: Long) {
    scores.removeAll { it.key == key && it.score <= score }
  }
  override suspend fun pendingAchievements(): List<PendingPlayGamesAchievement> = achievements.toList()
  override suspend fun markAchievementSynced(key: PlayGamesAchievementKey, value: Long) {
    achievements.removeAll { it.key == key && it.currentValue <= value }
  }
}
