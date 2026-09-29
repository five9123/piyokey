package app.piyokey.core.domain.game

import app.piyokey.core.domain.FlowGameRankTuning
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.hangul.Korean10KeyInterpretation
import app.piyokey.core.hangul.Korean10KeyInterpreter
import app.piyokey.core.hangul.Korean10KeyKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Port of iOS `FlowGameViewModelTests` (engine, pacing, layout, frame-rate parts). */
class FlowGameEngineTest {
  private fun FlowGameEngine.type(sequence: String) = sequence.forEach { input(it) }

  @Test fun compactHudUsesSmallerTypeAsValuesGrow() {
    assertEquals(15f, CompactGameHudMetricLayout.valuePointSize("67"))
    assertEquals(14f, CompactGameHudMetricLayout.valuePointSize("1,496"))
    assertEquals(12f, CompactGameHudMetricLayout.valuePointSize("99,999"))
    assertEquals(10f, CompactGameHudMetricLayout.valuePointSize("1,000,000"))
    assertTrue(CompactGameHudMetricLayout.VALUE_HORIZONTAL_INSET >= CompactGameHudMetricLayout.ICON_COLUMN_WIDTH)
  }

  @Test fun korean10KeyPendingMatchingCompletedFlickCountsOneMistakeWithoutAdvancing() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    var interpreter = Korean10KeyInterpreter()
    model.start(0.0)
    val first = interpreter.input(Korean10KeyKey.GIYEOK, model.nextExpectedKey)
    interpreter = first.interpreter
    assertEquals(Korean10KeyInterpretation.Committed('ㄱ'), first.interpretation)
    model.input('ㄱ')
    val vertical = interpreter.input(Korean10KeyKey.VERTICAL, model.nextExpectedKey)
    interpreter = vertical.interpreter
    assertEquals(Korean10KeyInterpretation.Pending("ㅣ"), vertical.interpretation)
    val flick = interpreter.inputCompletedJamo('ㅏ', model.nextExpectedKey)
    interpreter = flick.interpreter
    assertEquals(Korean10KeyInterpretation.Incorrect('ㅏ'), flick.interpretation)
    model.recordConfirmedOsImeMistake()

    assertEquals(1, model.mistakeCount)
    assertEquals(1, model.completedJamoCount)
    assertEquals('ㅏ', model.nextExpectedKey)
    assertEquals("ㄱ", model.enteredText)
    assertEquals(0, model.completedItemCount)
    assertEquals(Korean10KeyKey.VERTICAL, interpreter.nextKey(model.nextExpectedKey))
  }

  @Test fun flowLaneMovesFromFullyRightToFullyLeft() {
    assertEquals(320f, FlowLaneLayout.horizontalOffset(0.0, 320f, 120f), 0.001f)
    assertEquals(100f, FlowLaneLayout.horizontalOffset(0.5, 320f, 120f), 0.001f)
    assertEquals(-120f, FlowLaneLayout.horizontalOffset(1.0, 320f, 120f), 0.001f)
  }

  @Test fun movingWordCardsFitContentAndRespectLaneCaps() {
    val shortFlow = MovingWordCardLayout.flowWidth("나", "私", "ナ", 1f, 236f)
    val longFlow = MovingWordCardLayout.flowWidth("국제경제협력", "国際経済協力", "ククチェギョンジェヒョムニョク", 1f, 236f)
    assertEquals(96f, shortFlow, 0.001f)
    assertTrue(longFlow > shortFlow)
    assertTrue(longFlow <= 236f)
    assertEquals(92f, MovingWordCardLayout.acidRainWidth("나", "私", "ナ", 1f, 188f), 0.001f)
    assertEquals(188f, MovingWordCardLayout.acidRainWidth("가".repeat(20), "語".repeat(20), "カ".repeat(20), 1f, 188f), 0.001f)
  }

  @Test fun acidRainCardUsesThreeSafeLanesAndStopsAtDangerLine() {
    val width = 320f
    val cardWidth = AcidRainLaneLayout.cardWidth(width)
    val centers = (0 until 3).map { AcidRainLaneLayout.cardCenterX(it, width, cardWidth) }
    assertTrue(centers[0] - cardWidth / 2 >= 0)
    assertTrue(centers[2] + cardWidth / 2 <= width)
    assertTrue(centers[0] < centers[1] && centers[1] < centers[2])
    assertEquals(93f, AcidRainLaneLayout.cardCenterY(0.0, 360f), 0.001f)
    assertEquals(360f - AcidRainLaneLayout.DANGER_ZONE_HEIGHT, AcidRainLaneLayout.cardCenterY(1.0, 360f) + 45, 0.001f)
  }

  @Test fun acidRainSpawnsAnotherCardBeforeTheFirstReachesDangerLine() {
    val model = FlowGameEngine(listOf("가", "나", "다"), cardTravelDuration = 10.0, allowsConcurrentCards = true)
    model.start(100.0)
    val interval = FlowGamePacing.acidRainSpawnInterval(10.0)
    model.tick(100 + interval + 0.1)
    val cards = model.projectedAcidRainCards(100 + interval + 0.1)
    assertEquals(2, cards.size)
    assertEquals(listOf(0, 1), cards.map { it.itemIndex })
    assertTrue(cards.first().isInputTarget)
    assertFalse(cards.last().isInputTarget)
    assertTrue(cards.all { it.progress < 1 })
  }

  @Test fun acidRainCompletionTargetsTheMostUrgentCardAlreadyFalling() {
    val model = FlowGameEngine(listOf("가", "나", "다"), cardTravelDuration = 10.0, allowsConcurrentCards = true)
    model.start(200.0)
    val elapsed = FlowGamePacing.acidRainSpawnInterval(10.0) + 0.5
    model.tick(200 + elapsed)
    val queued = model.projectedAcidRainCards(200 + elapsed).first { it.itemIndex == 1 }.progress
    model.type("ㄱㅏ")
    assertEquals(1, model.currentTargetIndex)
    assertEquals("나", model.target)
    val active = model.projectedAcidRainCards(200 + elapsed).first { it.isInputTarget }
    assertEquals(1, active.itemIndex)
    assertEquals(queued, active.progress, 0.001)
  }

  @Test fun acidRainOsImeCompletesAnyMatchingVisibleCard() {
    val model = FlowGameEngine(listOf("가", "너", "다"), cardTravelDuration = 10.0, allowsConcurrentCards = true)
    model.start(250.0)
    model.tick(250 + FlowGamePacing.acidRainSpawnInterval(10.0) + 0.1)
    assertEquals(0, model.currentTargetIndex)
    assertTrue(model.acidRainCards.any { it.itemIndex == 1 })
    model.synchronizeAcidRainOsIme("너", "ㄴㅓ".toList())
    assertEquals(1, model.completedItemCount)
    assertEquals(1, model.lastCompletedItem?.itemIndex)
    assertFalse(model.acidRainCards.any { it.itemIndex == 1 })
    assertTrue(model.acidRainCards.any { it.itemIndex == 0 })
    assertEquals(0, model.currentTargetIndex)
  }

  @Test fun concurrentAcidRainEndsWhenThreeCardsReachTheFloor() {
    val model = FlowGameEngine(listOf("가", "나", "다", "라"), cardTravelDuration = 4.0, allowsConcurrentCards = true)
    model.start(300.0)
    model.tick(320.0)
    assertEquals(GamePhase.FINISHED, model.phase)
    assertEquals(0, model.remainingLives)
    assertEquals(3, model.missedCardCount)
    assertTrue(model.result.endedByLives)
  }

  @Test fun concurrentAcidRainCardsFreezeWhilePaused() {
    val model = FlowGameEngine(listOf("가", "나", "다"), cardTravelDuration = 10.0, allowsConcurrentCards = true)
    model.start(400.0)
    model.tick(402.0)
    model.pause(402.0)
    assertEquals(0.2, model.projectedAcidRainCards(500.0).first().progress, 0.001)
    assertEquals(58.0, model.remainingTime, 0.001)
    model.resume(500.0)
    model.tick(501.0)
    assertEquals(0.3, model.projectedAcidRainCards(501.0).first().progress, 0.001)
    assertEquals(57.0, model.remainingTime, 0.001)
  }

  @Test fun flowPacingAcceleratesAndCapsAtOnePointEight() {
    assertEquals(1.0, FlowGamePacing.flowSpeedMultiplier(0.0), 0.001)
    assertEquals(1.16, FlowGamePacing.flowSpeedMultiplier(10.0), 0.001)
    assertEquals(1.4, FlowGamePacing.flowSpeedMultiplier(25.0), 0.001)
    assertEquals(1.8, FlowGamePacing.flowSpeedMultiplier(50.0), 0.001)
    assertEquals(1.8, FlowGamePacing.flowSpeedMultiplier(100.0), 0.001)
  }

  @Test fun acidRainKeepsOnePointFiveCap() {
    assertEquals(1.0, FlowGamePacing.acidRainSpeedMultiplier(0.0), 0.001)
    assertEquals(1.25, FlowGamePacing.acidRainSpeedMultiplier(25.0), 0.001)
    assertEquals(1.5, FlowGamePacing.acidRainSpeedMultiplier(50.0), 0.001)
    assertEquals(1.5, FlowGamePacing.acidRainSpeedMultiplier(100.0), 0.001)
  }

  @Test fun acidRainSpawnIntervalFormula() {
    // min(max(1.4, t × 0.42), 3.6, t × 0.72)
    assertEquals(3.6, FlowGamePacing.acidRainSpawnInterval(10.0), 0.001)
    assertEquals(2.94, FlowGamePacing.acidRainSpawnInterval(7.0), 0.001)
    assertEquals(1.4, FlowGamePacing.acidRainSpawnInterval(2.0), 0.001)
    assertEquals(0.72, FlowGamePacing.acidRainSpawnInterval(1.0), 0.001)
    assertEquals(9.0 * 0.78, FlowGameCourse.travelDuration(FlowGameCourse.WORD, isAcidRain = true), 0.001)
    assertEquals(5.5, FlowGameCourse.travelDuration(FlowGameCourse.SHORT_WORD, isAcidRain = true), 0.001)
  }

  @Test fun flawlessCompletionAddsBonusAndUsesComboMultiplierAtFive() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    model.start(0.0)
    repeat(5) { model.type("ㄱㅏ") }
    assertEquals(5, model.completedItemCount)
    assertEquals(5, model.combo)
    assertEquals(5, model.maxCombo)
    assertEquals(70.0, model.remainingTime, 0.001)
    assertEquals(104, model.score)
    assertEquals(5, model.completionRevision)
  }

  @Test fun mistakeResetsComboAndPreventsTimeBonus() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    model.start(0.0)
    model.type("ㄱㅏ")
    model.input('ㄴ')
    model.type("ㄱㅏ")
    assertEquals(0, model.combo)
    assertEquals(1, model.maxCombo)
    assertEquals(1, model.mistakeCount)
    assertEquals(62.0, model.remainingTime, 0.001)
    assertEquals(40, model.score)
    assertEquals(80.0, model.accuracyPercent, 0.001)
  }

  @Test fun pausedTimeDoesNotAdvanceTimerOrCard() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    model.start(1_000.0)
    model.tick(1_005.0)
    model.pause(1_005.0)
    assertEquals(GamePhase.PAUSED, model.phase)
    assertEquals(55.0, model.remainingTime, 0.001)
    assertEquals(0.05, model.projectedCardProgress(1_100.0), 0.001)
    model.resume(1_100.0)
    model.tick(1_105.0)
    assertEquals(50.0, model.remainingTime, 0.001)
    assertEquals(0.10, model.projectedCardProgress(1_105.0), 0.001)
  }

  @Test fun restartReturnsToReadyWithoutConsumingTimer() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    model.start(1_500.0)
    model.tick(1_505.0)
    model.restart()
    model.tick(1_550.0)
    assertEquals(GamePhase.READY, model.phase)
    assertEquals(60.0, model.remainingTime, 0.001)
    assertEquals(3, model.remainingLives)
    assertEquals(100.0, model.currentCardTravelDuration, 0.001)
    assertEquals(0.0, model.projectedCardProgress(1_550.0))
    model.start(1_550.0)
    model.tick(1_551.0)
    assertEquals(59.0, model.remainingTime, 0.001)
  }

  @Test fun restartWithReplacementTargetsKeepsTargetAndJudgeAligned() {
    val model = FlowGameEngine(listOf("가", "나"), cardTravelDuration = 100.0)
    model.restart(listOf("다", "라"))
    assertEquals(listOf("다", "라"), model.targets)
    assertEquals("다", model.target)
    assertEquals('ㄷ', model.nextExpectedKey)
    model.start(1_600.0)
    model.input('ㄱ')
    assertEquals(FlowGameFeedback.Incorrect('ㄷ'), model.feedback)
    assertEquals("", model.enteredText)
    model.type("ㄷㅏ")
    assertEquals(0, model.lastCompletedItem?.itemIndex)
    assertEquals(1, model.currentTargetIndex)
    assertEquals("라", model.target)
    assertEquals('ㄹ', model.nextExpectedKey)
  }

  @Test fun escapedCardAdvancesWithoutTimePenaltyAndResetsCombo() {
    val model = FlowGameEngine(listOf("가", "나"), cardTravelDuration = 8.0)
    model.start(2_000.0)
    model.type("ㄱㅏ")
    assertEquals(1, model.combo)
    model.tick(2_008.1)
    assertEquals(1, model.missedCardCount)
    assertEquals(2, model.remainingLives)
    assertEquals(0, model.combo)
    assertEquals(0, model.currentTargetIndex)
    assertEquals(53.9, model.remainingTime, 0.001)
    assertEquals(8 / 1.128, model.currentCardTravelDuration, 0.001)
    assertEquals(0.1 / model.currentCardTravelDuration, model.projectedCardProgress(2_008.1), 0.001)
  }

  @Test fun thirdEscapedCardExhaustsLivesBeforeTimer() {
    val model = FlowGameEngine(listOf("가", "나"), cardTravelDuration = 1.0)
    model.start(2_500.0)
    model.tick(2_510.0)
    assertEquals(GamePhase.FINISHED, model.phase)
    assertEquals(0, model.remainingLives)
    assertEquals(3, model.missedCardCount)
    assertTrue(model.remainingTime > 50)
    assertTrue(model.result.endedByLives)
  }

  @Test fun finishBuildsScoreAccuracySpeedAndRank() {
    val model = FlowGameEngine(listOf("가"), initialDuration = 1.0, flawlessBonus = 0.0, cardTravelDuration = 100.0)
    model.start(3_000.0)
    model.type("ㄱㅏ")
    model.tick(3_001.0)
    assertEquals(GamePhase.FINISHED, model.phase)
    assertEquals(FlowGameResult(20, 1, 100.0, 120.0, 1.0, 1, 0, "S"), model.result)
  }

  @Test fun finishedRunIgnoresLateCallbacks() {
    val model = FlowGameEngine(listOf("가"), initialDuration = 1.0, flawlessBonus = 0.0, cardTravelDuration = 100.0)
    model.start(3_500.0)
    model.tick(3_501.0)
    val finished = model.result
    model.input('ㄱ')
    model.backspace()
    model.synchronizeOsIme("ㄱㅏ".toList())
    model.recordConfirmedOsImeMistake()
    model.tick(3_600.0)
    model.resume(3_600.0)
    assertEquals(GamePhase.FINISHED, model.phase)
    assertEquals(finished, model.result)
    assertEquals(0, model.completedJamoCount)
  }

  @Test fun backspaceRewindsWithoutForgivingMistake() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    model.start(0.0)
    model.input('ㄴ')
    model.input('ㄱ')
    model.backspace()
    assertEquals(0, model.completedJamoCount)
    assertEquals("", model.enteredText)
    assertEquals(1, model.mistakeCount)
    assertEquals(0, model.combo)
  }

  @Test fun completionResolutionTracksMistakeStateBeforeAdvance() {
    val model = FlowGameEngine(listOf("가", "나"), cardTravelDuration = 100.0)
    model.start(0.0)
    model.input('ㄴ')
    model.type("ㄱㅏ")
    assertEquals(SessionItemResolution(0, true, 1, setOf(0)), model.lastCompletedItem)
    model.type("ㄴㅏ")
    assertEquals(SessionItemResolution(1, false, 0, emptySet()), model.lastCompletedItem)
    assertEquals(2, model.reviewResolutionRevision)
  }

  @Test fun mistakenEscapedCardPublishesReviewResolution() {
    val model = FlowGameEngine(listOf("가", "나"), cardTravelDuration = 1.0)
    model.start(4_000.0)
    model.input('ㄴ')
    model.tick(4_001.0)
    assertEquals(SessionItemResolution(0, true, 1, setOf(0)), model.lastCompletedItem)
    assertEquals(1, model.reviewResolutionRevision)
    assertEquals(0, model.completionRevision)
    assertEquals(1, model.currentTargetIndex)
  }

  @Test fun rankUsesSixtyFortyWeighting() {
    val tuning = FlowGameRankTuning()
    assertEquals("S", tuning.rank(100.0, 120.0))
    assertEquals("A", tuning.rank(90.0, 90.0))
    assertEquals("B", tuning.rank(70.0, 60.0))
    assertEquals("C", tuning.rank(50.0, 30.0))
  }

  @Test fun osImeSynchronizationAndMistakeUseGameScoring() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    model.start(0.0)
    model.recordConfirmedOsImeMistake()
    model.synchronizeOsIme("ㄱㅏ".toList())
    assertEquals(1, model.mistakeCount)
    assertEquals(1, model.completedItemCount)
    assertEquals(0, model.combo)
    assertEquals(20, model.score)
  }

  @Test fun mascotEventsCoverTypingRhythmMistakesAndResume() {
    val model = FlowGameEngine(listOf("가"), cardTravelDuration = 100.0)
    model.start(0.0)
    model.input('ㄱ')
    assertEquals(FlowGameMascotEvent.CorrectJamo, model.mascotEvent)
    assertEquals(1, model.totalAcceptedInputCount)
    model.input('ㅏ')
    assertEquals(FlowGameMascotEvent.WordCompleted, model.mascotEvent)
    repeat(4) { model.type("ㄱㅏ") }
    assertEquals(5, model.combo)
    assertEquals(FlowGameMascotEvent.Rhythm(5), model.mascotEvent)
    model.input('ㄴ')
    assertEquals(FlowGameMascotEvent.Startle('ㄱ'), model.mascotEvent)
    model.input('ㄴ')
    model.input('ㄴ')
    assertEquals(3, model.consecutiveMistakes)
    assertEquals(FlowGameMascotEvent.Dizzy('ㄱ'), model.mascotEvent)
    model.publishStretchReaction()
    assertEquals(FlowGameMascotEvent.Stretch, model.mascotEvent)
    model.publishNewBestReaction()
    assertEquals(FlowGameMascotEvent.NewBest, model.mascotEvent)
  }

  @Test fun frameRateCalculatorReportsP95AndOverBudgetFrames() {
    val snapshot = assertNotNull(GameFrameRateCalculator.snapshot(listOf(0.0, 0.016, 0.032, 0.057, 0.073)))
    assertEquals(4, snapshot.sampleCount)
    assertEquals(4 / 0.073, snapshot.averageFps, 0.001)
    assertEquals(25.0, snapshot.p95FrameDurationMilliseconds, 0.001)
    assertEquals(25.0, snapshot.worstFrameDurationMilliseconds, 0.001)
    assertEquals(1, snapshot.overBudgetFrameCount)
  }

  @Test fun frameRateMonitorPublishesAfterWarmup() {
    val monitor = GameFrameRateMonitor()
    var published = false
    for (frame in 0..150) published = monitor.record(frame / 60.0) || published
    assertTrue(published)
    assertEquals(60.0, assertNotNull(monitor.snapshot).averageFps, 0.5)
    monitor.reset()
    assertEquals(null, monitor.snapshot)
  }
}
