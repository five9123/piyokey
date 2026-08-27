package app.piyokey.core.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayGamesPolicyTest {
  @Test
  fun mapsExactlyFiveModesAndThreeBundledCourses() {
    val mapped = PlayGamesPolicy.allMappedIdentities()
    assertEquals(15, mapped.size)
    assertEquals(15, mapped.values.toSet().size)
    assertEquals(
      PlayGamesLeaderboardKey.WORD_MATCH_ADVANCED,
      PlayGamesPolicy.leaderboard(
        PlayGamesResultIdentity("word_match", "word_match_topik_advanced", "builtin"),
      ),
    )
  }

  @Test
  fun excludesOsImeCustomDeckSpacingAndWeeklyCup() {
    assertNull(PlayGamesPolicy.leaderboard(PlayGamesResultIdentity("flow", "flow_topik_beginner", "os_ime")))
    assertNull(PlayGamesPolicy.leaderboard(PlayGamesResultIdentity("flow", "my_flow", "builtin")))
    assertNull(PlayGamesPolicy.leaderboard(PlayGamesResultIdentity("spacing", "spacing_1", "builtin")))
    assertNull(PlayGamesPolicy.leaderboard(PlayGamesResultIdentity("flow", "flow_topik_beginner", "builtin", isWeeklyCup = true)))
  }

  @Test
  fun achievementTargetsAreStableAndMonotonic() {
    assertEquals(12_000, PlayGamesAchievementKey.JAMO_12000.target)
    assertEquals(30, PlayGamesAchievementKey.STREAK_30.target)
    assertEquals(5, PlayGamesAchievementKey.entries.size)
  }
}
