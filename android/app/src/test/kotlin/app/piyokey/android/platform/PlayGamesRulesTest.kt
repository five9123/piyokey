package app.piyokey.android.platform

import app.piyokey.android.platform.games.GrowthSnapshot
import app.piyokey.android.platform.games.PiyoAchievement
import app.piyokey.android.platform.games.PiyoCupWeek
import app.piyokey.android.platform.games.PiyoLeaderboard
import app.piyokey.android.platform.games.PlayGamesRules
import app.piyokey.android.platform.games.RankedRecord
import app.piyokey.android.platform.games.SubmissionPolicy
import app.piyokey.android.platform.games.SubmissionState
import app.piyokey.android.platform.games.SubmissionTracker
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayGamesRulesTest {
  private val now = Instant.parse("2026-09-30T03:00:00Z") // Wed 12:00 JST

  private fun record(
    deck: String = "flow_topik_beginner",
    version: Int? = 3,
    competition: String? = null,
    score: Int = 100,
    game: Boolean = true,
    at: Instant = now,
    id: String = "r$score$deck$competition",
  ) = RankedRecord(id, game, deck, version, competition, score, at)

  @Test fun logicalIdsMatchIos() {
    assertEquals("piyokey.v5.flow.beginner", PiyoLeaderboard.FLOW_BEGINNER.iosId)
    assertEquals("piyokey.v4.flow.intermediate", PiyoLeaderboard.FLOW_INTERMEDIATE.iosId)
    assertEquals("piyokey.v4.word_match.advanced", PiyoLeaderboard.WORD_MATCH_ADVANCED.iosId)
    assertEquals("piyokey.v3.dictation.beginner", PiyoLeaderboard.DICTATION_BEGINNER.iosId)
    assertEquals("piyokey.v4.cup.weekly.flow", PiyoLeaderboard.WEEKLY_PIYO_CUP.iosId)
    assertEquals(16, PiyoLeaderboard.entries.map { it.iosId }.toSet().size)
  }

  @Test fun resourceKeysMatchGradlePlayGamesKeys() {
    val gradle = File("build.gradle.kts").takeIf { it.isFile } ?: File("app/build.gradle.kts")
    val block = gradle.readText().substringAfter("val playGamesKeys = listOf(").substringBefore(")")
    val keys = Regex("\"([a-z0-9_]+)\"").findAll(block).map { it.groupValues[1] }.toSet()
    val ours = PiyoLeaderboard.entries.map { it.resourceKey }.toSet() + PiyoAchievement.entries.map { it.resourceKey }
    assertEquals(keys, ours)
  }

  @Test fun onlyBundledPresetDecksV3AreRanked() {
    assertEquals(listOf(PiyoLeaderboard.FLOW_BEGINNER), PlayGamesRules.leaderboards(record()))
    assertEquals(listOf(PiyoLeaderboard.ACID_RAIN_ADVANCED), PlayGamesRules.leaderboards(record("acid_rain_topik_advanced")))
    assertTrue(PlayGamesRules.leaderboards(record(version = 2)).isEmpty())
    assertTrue(PlayGamesRules.leaderboards(record(version = null)).isEmpty())
    assertTrue(PlayGamesRules.leaderboards(record(deck = "user_deck_123")).isEmpty())
    assertTrue(PlayGamesRules.leaderboards(record(game = false)).isEmpty())
    assertEquals(15, PlayGamesRules.rankedDecks.size)
    assertTrue(PlayGamesRules.rankedDecks.all { it.version == 3 })
  }

  @Test fun piyoCupGoesOnlyToCupBoardAndOnlyForBeginnerFlow() {
    val cup = PlayGamesRules.WEEKLY_PIYO_CUP_COMPETITION
    assertEquals("weekly_piyo_cup_v1", cup)
    assertEquals(listOf(PiyoLeaderboard.WEEKLY_PIYO_CUP), PlayGamesRules.leaderboards(record(competition = cup)))
    assertTrue(PlayGamesRules.leaderboards(record(deck = "flow_topik_intermediate", competition = cup)).isEmpty())
  }

  @Test fun bestScoresKeepMaxAndCupOnlyThisJstWeek() {
    val lastWeek = Instant.parse("2026-09-27T14:59:00Z") // Sun 23:59 JST
    val monday = Instant.parse("2026-09-27T15:00:00Z") // Mon 00:00 JST
    val cup = PlayGamesRules.WEEKLY_PIYO_CUP_COMPETITION
    val best = PlayGamesRules.bestScores(
      listOf(
        record(score = 120),
        record(score = 300, id = "a"),
        record(competition = cup, score = 999, at = lastWeek, id = "b"),
        record(competition = cup, score = 50, at = monday, id = "c"),
      ),
      asOf = now,
    )
    assertEquals(300, best[PiyoLeaderboard.FLOW_BEGINNER])
    assertEquals(50, best[PiyoLeaderboard.WEEKLY_PIYO_CUP])
  }

  @Test fun jstWeekStartsMondayMidnight() {
    assertEquals(Instant.parse("2026-09-27T15:00:00Z"), PiyoCupWeek.start(now))
    assertTrue(PiyoCupWeek.contains(Instant.parse("2026-10-04T14:59:59Z"), now))
    assertFalse(PiyoCupWeek.contains(Instant.parse("2026-10-04T15:00:00Z"), now))
  }

  @Test fun rankIsBestOfAvailableBoards() {
    val ranks = mapOf(PiyoLeaderboard.FLOW_BEGINNER to 7L)
    assertEquals(7L, PlayGamesRules.rank(record(), ranks, setOf(PiyoLeaderboard.FLOW_BEGINNER)))
    assertNull(PlayGamesRules.rank(record(), ranks, emptySet()))
  }

  @Test fun achievementThresholds() {
    assertEquals(setOf(PiyoAchievement.HATCHING), PiyoAchievement.unlocked(GrowthSnapshot(1, 0, 0)))
    assertEquals(setOf(PiyoAchievement.HATCHING, PiyoAchievement.CHICK), PiyoAchievement.unlocked(GrowthSnapshot(3, 0, 0)))
    assertEquals(
      PiyoAchievement.entries.toSet(),
      PiyoAchievement.unlocked(GrowthSnapshot(6, 12_000, 30)),
    )
    assertEquals(50.0, PiyoAchievement.ROOSTER.percentComplete(GrowthSnapshot(3, 0, 0)))
    assertEquals(0.0, PiyoAchievement.STREAK.percentComplete(GrowthSnapshot.EMPTY))
    assertTrue(PiyoAchievement.unlocked(GrowthSnapshot(0, 11_999, 29)).isEmpty())
  }

  @Test fun submissionPolicy() {
    assertTrue(SubmissionPolicy.shouldSubmit(10, null, null))
    assertFalse(SubmissionPolicy.shouldSubmit(10, 10, null))
    assertFalse(SubmissionPolicy.shouldSubmit(10, null, 12))
    assertFalse(SubmissionPolicy.shouldSubmit(-1, null, null))
    assertTrue(SubmissionPolicy.isConfirmed(15, 10))
    assertFalse(SubmissionPolicy.isConfirmed(9, 10))
    assertFalse(SubmissionPolicy.isConfirmed(null, 10))
  }

  @Test fun trackerConfirmsByReadBack() {
    val tracker = SubmissionTracker(keptBest = 500)
    assertEquals(SubmissionState.SUBMITTING, tracker.begin())
    assertEquals(SubmissionState.CONFIRMING, tracker.submitFinished(true))
    assertEquals(SubmissionState.CONFIRMING, tracker.readBack(400))
    assertEquals(SubmissionState.CONFIRMED, tracker.readBack(500))
    assertNull(tracker.nextRetry())
  }

  @Test fun trackerRetriesAtMostTwice() {
    val tracker = SubmissionTracker(keptBest = 500)
    tracker.begin()
    assertEquals(SubmissionState.FAILED, tracker.submitFinished(false))
    assertEquals(1, tracker.nextRetry())
    tracker.begin()
    tracker.submitFinished(true)
    tracker.readBack(null)
    assertEquals(SubmissionState.UNCONFIRMED, tracker.confirmationExhausted())
    assertEquals(2, tracker.nextRetry())
    tracker.begin()
    tracker.submitFinished(false)
    assertNull(tracker.nextRetry())
    assertEquals(listOf(0L, 3_000L, 6_000L, 9_000L), SubmissionPolicy.confirmationDelaysMillis)
  }
}
