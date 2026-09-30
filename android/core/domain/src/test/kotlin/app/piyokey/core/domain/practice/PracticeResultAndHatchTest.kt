package app.piyokey.core.domain.practice

import app.piyokey.core.domain.practice.HatchMissionTransitionCoordinator.Action
import app.piyokey.core.domain.practice.HatchMissionTransitionCoordinator.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ports of iOS `SessionResultRevealTimelineTests`, the hatch transition cases of `CurriculumProgressStoreTests`, and result rules. */
class PracticeResultAndHatchTest {
  private val timing = SessionResultRevealTiming()

  @Test
  fun initialStateKeepsScoreMetricsAndActionsHidden() {
    val state = timing.state(0.0)
    assertEquals(0.6, state.headerScale, 0.001)
    assertEquals(0.0, state.scoreProgress, 0.001)
    assertEquals(0.0, state.metricProgress(0), 0.001)
    assertEquals(0.0, state.lowerZoneOpacity, 0.001)
    assertFalse(state.actionsEnabled)
  }

  @Test
  fun scoreCountsUpFromPointFourToOnePointTwoSeconds() {
    assertEquals(0.0, timing.state(0.4).scoreProgress, 0.001)
    assertTrue(timing.state(0.8).scoreProgress > 0.5)
    assertEquals(1.0, timing.state(1.2).scoreProgress, 0.001)
  }

  @Test
  fun threeMetricsRevealSequentially() {
    val first = timing.state(1.5)
    assertEquals(1.0, first.metricProgress(0), 0.001)
    assertEquals(0.0, first.metricProgress(1), 0.001)
    assertEquals(0.0, first.metricProgress(2), 0.001)
    val all = timing.state(2.1)
    assertEquals(1.0, all.metricProgress(0), 0.001)
    assertEquals(1.0, all.metricProgress(1), 0.001)
    assertEquals(1.0, all.metricProgress(2), 0.001)
  }

  @Test
  fun lowerZonesAppearBeforeActionsBecomeInteractive() {
    val fading = timing.state(2.0)
    assertTrue(fading.lowerZoneOpacity > 0)
    assertFalse(fading.actionsEnabled)
    assertTrue(timing.state(2.5).actionsEnabled)
  }

  @Test
  fun skipImmediatelyProducesFinalNonParticleState() {
    val state = timing.state(0.1, skipped = true)
    assertEquals(1.0, state.headerScale, 0.001)
    assertEquals(1.0, state.scoreProgress, 0.001)
    assertEquals(1.0, state.metricProgress(2), 0.001)
    assertEquals(1.0, state.lowerZoneOpacity, 0.001)
    assertEquals(1.0, state.headerParticleProgress, 0.001)
    assertEquals(1.0, state.newRecordBurstProgress, 0.001)
    assertTrue(state.actionsEnabled)
    assertEquals(1.0, state.starProgress(2), 0.001)
  }

  @Test
  fun uiTestScaleChangesDurationButNotCanonicalProgress() {
    val slowed = SessionResultRevealTiming(scale = 4.0)
    assertEquals(10.0, slowed.totalDuration, 0.001)
    assertEquals(1.0, slowed.state(4.8).scoreProgress, 0.001)
    assertFalse(slowed.state(9.9).actionsEnabled)
    assertTrue(slowed.state(10.0).actionsEnabled)
  }

  @Test
  fun hatchTransitionSerializesMissionOneCelebrationBeforeMissionTwoExactlyOnce() {
    val transition = HatchMissionTransitionCoordinator<String>("mission-1")
    assertEquals(Action.WaitForPresentationTransition, transition.resultDidDismiss("mission-1", "mission-2", "hatching"))
    assertEquals(Action.None, transition.resultDidDismiss("mission-1", "mission-2", "hatching"))
    assertTrue(transition.ownsCelebration("mission-1"))
    assertEquals(Action.PresentCelebration("hatching"), transition.presentationTransitionDidFinish())
    assertEquals(Action.None, transition.presentationTransitionDidFinish())
    assertEquals(Action.AdvanceToMission("mission-2"), transition.celebrationDidDismiss())
    assertEquals(Action.None, transition.celebrationDidDismiss())
    assertTrue(transition.missionDidActivate("mission-2"))
    assertFalse(transition.missionDidActivate("mission-2"))
    assertEquals(State.AwaitingResultDismissal("mission-2"), transition.state)
    assertEquals(Action.None, transition.resultDidDismiss("mission-1", "mission-2", null))
  }

  @Test
  fun hatchTransitionSerializesMissionTwoResultBeforeMissionThree() {
    val transition = HatchMissionTransitionCoordinator<String>("mission-2")
    assertEquals(Action.WaitForPresentationTransition, transition.resultDidDismiss("mission-2", "mission-3", null))
    assertEquals(Action.AdvanceToMission("mission-3"), transition.presentationTransitionDidFinish())
    assertEquals(Action.None, transition.presentationTransitionDidFinish())
    assertTrue(transition.missionDidActivate("mission-3"))
    assertEquals(State.AwaitingResultDismissal("mission-3"), transition.state)
  }

  @Test
  fun hatchTransitionSerializesFinalCelebrationAndHomeExactlyOnce() {
    val transition = HatchMissionTransitionCoordinator<String>("mission-3")
    assertEquals(Action.WaitForPresentationTransition, transition.resultDidDismiss("mission-3", null, "chick"))
    assertEquals(Action.PresentCelebration("chick"), transition.presentationTransitionDidFinish())
    assertEquals(Action.CompleteHatch, transition.celebrationDidDismiss())
    assertEquals(Action.None, transition.celebrationDidDismiss())
    assertEquals(Action.None, transition.presentationTransitionDidFinish())
    assertEquals(State.Completed, transition.state)
  }

  @Test
  fun hatchTransitionFinalWithoutPendingCelebrationStillWaitsForResultPop() {
    val transition = HatchMissionTransitionCoordinator<String>("mission-3")
    assertEquals(Action.WaitForPresentationTransition, transition.resultDidDismiss("mission-3", null, null))
    assertTrue(transition.isWaitingForPresentationTransition)
    assertEquals(Action.CompleteHatch, transition.presentationTransitionDidFinish())
    assertEquals(Action.None, transition.presentationTransitionDidFinish())
  }

  @Test
  fun resultStarsAndLearningRecordRules() {
    assertEquals(0, PracticeRules.displayedStars(0, 99.0))
    assertEquals(3, PracticeRules.displayedStars(null, 95.0))
    assertEquals(2, PracticeRules.displayedStars(null, 80.0))
    assertEquals(1, PracticeRules.displayedStars(null, 79.9))

    assertEquals("deck_a", PracticeRules.learningRecordDeckId(setOf("deck_a"), null))
    assertEquals("review_deck", PracticeRules.learningRecordDeckId(setOf("a", "b"), null))
    assertEquals("official_curriculum", PracticeRules.learningRecordDeckId(emptySet(), "stage"))
    assertEquals("free_practice", PracticeRules.learningRecordDeckId(emptySet(), null))
    assertEquals("curriculum:chapter_1_basic_consonants", PracticeRules.learningRecordCourse("chapter_1_basic_consonants"))
    assertEquals("practice", PracticeRules.learningRecordCourse(null))
  }

  @Test
  fun overallProgressAndMarkedCharacters() {
    assertEquals(0.0, PracticeRules.overallProgress(0, 0, 3, 0), 0.0001)
    assertEquals(0.5, PracticeRules.overallProgress(0, 3, 3, 2), 0.0001)
    assertEquals(0.75, PracticeRules.overallProgress(1, 2, 4, 2), 0.0001)
    assertEquals(1.0, PracticeRules.overallProgress(1, 9, 4, 2), 0.0001)

    // 학교: 학 = jamo 0..2, 교 = 3..4
    assertEquals(listOf("학" to false, "교" to true), PracticeRules.markedCharacters("학교", setOf(4)))
    assertEquals(listOf("가" to true, " " to false, "나" to false), PracticeRules.markedCharacters("가 나", setOf(1)))
  }

  @Test
  fun jamoTrackCentresWhenFittingAndFollowsActiveChip() {
    val chip = 30f
    // 3 chips: 90 + 14 + 4 = 108 → centred in 200.
    assertEquals(46f, PracticeRules.jamoTrackOffset(200f, 3, 0, chip), 0.001f)
    // 20 chips overflow 200: first chip keeps offset 0, last chip clamps to the trailing edge.
    assertEquals(0f, PracticeRules.jamoTrackOffset(200f, 20, 0, chip), 0.001f)
    val content = PracticeRules.jamoTrackContentWidth(20, chip)
    assertEquals(200f - content, PracticeRules.jamoTrackOffset(200f, 20, 19, chip), 0.001f)
  }

  @Test
  fun japaneseMeaningDropsOneBracketPair() {
    assertEquals("こんにちは", JapaneseMeaningDisplayText.format("  「こんにちは」 "))
    assertEquals("本", JapaneseMeaningDisplayText.format("『本』"))
    assertEquals("「半分", JapaneseMeaningDisplayText.format("「半分"))
    assertEquals("", JapaneseMeaningDisplayText.format("   "))
    assertEquals("hello", JapaneseMeaningDisplayText.format("hello"))
  }
}
