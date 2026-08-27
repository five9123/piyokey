package app.piyokey.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.core.game.FlowGameRecord
import app.piyokey.core.game.PlayGamesAchievementKey
import app.piyokey.core.game.PlayGamesLeaderboardKey
import app.piyokey.core.retention.JstDay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayGamesPersistenceInstrumentedTest {
  private lateinit var database: PiyokeyDatabase
  private lateinit var repository: DeckRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, PiyokeyDatabase::class.java).build()
    repository = DeckRepository.createForTesting(context, database, clock = { 1234L })
  }

  @After
  fun tearDown() = database.close()

  @Test
  fun eligibleScoresCoalesceToMaximumAndWeeklyOrOsImeStayLocal() = runTest {
    repository.saveFlowRecord(flowRecord(500), inputMode = "builtin")
    repository.saveFlowRecord(flowRecord(300), inputMode = "builtin")
    repository.saveFlowRecord(flowRecord(900), inputMode = "builtin", isWeeklyCup = true)
    repository.saveFlowRecord(flowRecord(800), inputMode = "os_ime")

    val localData = RoomPlayGamesLocalData(database)
    val pending = localData.pendingScores()
    assertEquals(listOf(PendingPlayGamesScore(PlayGamesLeaderboardKey.FLOW_BEGINNER, 500)), pending)
    assertEquals(500, repository.flowProgress("flow_topik_beginner", "builtin")?.bestScore)
    assertEquals(900, repository.flowProgress("flow_topik_beginner", "weekly_cup")?.bestScore)
    assertEquals(800, repository.flowProgress("flow_topik_beginner", "os_ime")?.bestScore)
    localData.markScoreSubmitted(PlayGamesLeaderboardKey.FLOW_BEGINNER, 500)
    repository.saveFlowRecord(flowRecord(250), inputMode = "builtin")
    assertTrue(localData.pendingScores().isEmpty())
  }

  @Test
  fun practiceJamoEventIsIdempotentAndProgressSurvivesRestartBoundary() = runTest {
    repository.recordPracticeAcceptedJamo("session-one", 7_000)
    repository.recordPracticeAcceptedJamo("session-one", 7_000)
    repository.recordPracticeAcceptedJamo("session-two", 5_500)

    assertEquals(12_500L, database.dao().lifetimeStats()?.acceptedJamoCount)
    val progress = RoomPlayGamesLocalData(database).pendingAchievements().single {
      it.key == PlayGamesAchievementKey.JAMO_12000
    }
    assertEquals(12_000L, progress.currentValue)
  }

  @Test
  fun chapterAndThirtyDayStreakAchievementsOnlyMoveForward() = runTest {
    repository.finishCurriculumStage(
      stageId = "chapter_1_basic_consonants",
      accuracyPercent = 100.0,
      charactersPerMinute = 80.0,
      sessionDay = JstDay("2026-01-01"),
    )
    repeat(30) { offset ->
      repository.recordDailyCompletion(JstDay("2026-01-01").plusDays(offset.toLong()))
    }

    val pending = RoomPlayGamesLocalData(database).pendingAchievements().associateBy { it.key }
    assertEquals(1L, pending.getValue(PlayGamesAchievementKey.CHAPTER_ONE).currentValue)
    assertEquals(30L, pending.getValue(PlayGamesAchievementKey.STREAK_30).currentValue)
    assertTrue(PlayGamesAchievementKey.CHAPTER_THREE !in pending)
  }

  private fun flowRecord(score: Int) = FlowGameRecord(
    deckId = "flow_topik_beginner",
    course = "beginner",
    score = score,
    maxCombo = 2,
    accuracyPercent = 90.0,
    correctJamoCount = 10,
    charactersPerMinute = 50.0,
    mistakeCount = 1,
    completedItemCount = 2,
    missedItemCount = 0,
    playDurationMillis = 1_000,
    playedAtEpochMillis = 1234,
  )
}
