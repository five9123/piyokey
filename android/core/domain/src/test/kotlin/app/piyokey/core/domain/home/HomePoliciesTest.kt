package app.piyokey.core.domain.home

import app.piyokey.core.domain.HatchOnboardingPolicy
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.RetentionDayRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomePoliciesTest {
  private val hatchDone = HatchOnboardingPolicy.requiredStages.map { it.id }.toSet()

  @Test
  fun rootGateOrderIsOnboardingThenHatchThenTabs() {
    assertEquals(RootGateScreen.ONBOARDING, RootGatePolicy.screen(onboardingPresented = true, hatchGateActive = true))
    assertEquals(RootGateScreen.ONBOARDING, RootGatePolicy.screen(onboardingPresented = true, hatchGateActive = false))
    assertEquals(RootGateScreen.HATCH_MISSIONS, RootGatePolicy.screen(onboardingPresented = false, hatchGateActive = true))
    assertEquals(RootGateScreen.MAIN_TABS, RootGatePolicy.screen(onboardingPresented = false, hatchGateActive = false))
  }

  @Test
  fun hatchGateIsActiveUntilAllHatchStagesAreCleared() {
    assertTrue(RootGatePolicy.initialHatchGateActive(emptySet()))
    assertTrue(RootGatePolicy.initialHatchGateActive(hatchDone.take(2).toSet()))
    assertFalse(RootGatePolicy.initialHatchGateActive(hatchDone))
    assertFalse(RootGatePolicy.canFinishHatch(hatchDone.take(1).toSet()))
    assertTrue(RootGatePolicy.canFinishHatch(hatchDone))
  }

  @Test
  fun appTourStartsOnlyAfterGateAndOnce() {
    assertTrue(RootGatePolicy.shouldStartAppTour(false, false, hatchDone, appTourCompleted = false, tourActive = false))
    assertFalse(RootGatePolicy.shouldStartAppTour(true, false, hatchDone, false, false))
    assertFalse(RootGatePolicy.shouldStartAppTour(false, true, hatchDone, false, false))
    assertFalse(RootGatePolicy.shouldStartAppTour(false, false, emptySet(), false, false))
    assertFalse(RootGatePolicy.shouldStartAppTour(false, false, hatchDone, appTourCompleted = true, tourActive = false))
    assertFalse(RootGatePolicy.shouldStartAppTour(false, false, hatchDone, appTourCompleted = false, tourActive = true))
  }

  @Test
  fun privacyNoticeWaitsForHomeWithoutCoversOrTour() {
    assertFalse(RootGatePolicy.privacyBlocked(coverPresented = false, tourActive = false, homeTabSelected = true))
    assertTrue(RootGatePolicy.privacyBlocked(coverPresented = true, tourActive = false, homeTabSelected = true))
    assertTrue(RootGatePolicy.privacyBlocked(coverPresented = false, tourActive = true, homeTabSelected = true))
    assertTrue(RootGatePolicy.privacyBlocked(coverPresented = false, tourActive = false, homeTabSelected = false))
    assertTrue(RootGatePolicy.privacyBlocked(false, false, true, homeStackDepth = 2))
  }

  @Test
  fun tourStepsMatchIosTargetsTabsAndOrder() {
    assertEquals(6, AppTourStep.count)
    assertEquals(
      listOf("homePrimary", "discoverSearch", "practiceCurriculum", "gameModes", "myPageProfile", "settings"),
      AppTourStep.entries.map { it.targetRaw },
    )
    assertEquals(listOf("home", "discover", "practice", "game", "my_page", "my_page"), AppTourStep.entries.map { it.tabRaw })
    assertEquals(AppTourStep.DISCOVER, AppTourStep.HOME.next)
    assertNull(AppTourStep.SETTINGS.next)
    assertTrue(AppTourStep.SETTINGS.isLast)
    assertEquals("app_tour.gameModes.title", AppTourStep.GAME.titleKey)
    assertEquals(3, AppTourStep.PRACTICE.position)
    assertEquals(18f, AppTourStep.DISCOVER.spotlightCornerRadius)
    assertEquals(26f, AppTourStep.SETTINGS.spotlightCornerRadius)
    assertEquals(28f, AppTourStep.HOME.spotlightCornerRadius)
  }

  @Test
  fun spotlightIsPaddedAndClamped() {
    val box = AppTourGeometry.clampedSpotlight(AppTourGeometry.Box(0f, 0f, 400f, 50f), width = 390f, height = 800f)
    assertEquals(AppTourGeometry.Box(8f, 8f, 382f, 59f), box)
    assertTrue(AppTourGeometry.calloutBelow(box, 800f))
    assertEquals(193f, AppTourGeometry.calloutCenterY(box, 800f, below = true))
    val low = AppTourGeometry.Box(20f, 600f, 300f, 700f)
    assertFalse(AppTourGeometry.calloutBelow(low, 800f))
    assertEquals(466f, AppTourGeometry.calloutCenterY(low, 800f, below = false))
  }

  @Test
  fun privacyFlowRequestsNotificationPermissionOnceBeforeNotice() {
    assertEquals(
      OnboardingPrivacyAction.REQUEST_NOTIFICATION_PERMISSION,
      OnboardingPrivacyFlowPolicy.nextAction(0, notificationPermissionRequested = false, hasStoredReminderPreference = false),
    )
    assertEquals(
      OnboardingPrivacyAction.MARK_REQUESTED_THEN_PRESENT,
      OnboardingPrivacyFlowPolicy.nextAction(0, notificationPermissionRequested = false, hasStoredReminderPreference = true),
    )
    assertEquals(
      OnboardingPrivacyAction.PRESENT_NOTICE,
      OnboardingPrivacyFlowPolicy.nextAction(0, notificationPermissionRequested = true, hasStoredReminderPreference = false),
    )
    // Existing users re-consenting to notice v2 are never asked for notifications here.
    assertEquals(
      OnboardingPrivacyAction.PRESENT_NOTICE,
      OnboardingPrivacyFlowPolicy.nextAction(1, notificationPermissionRequested = false, hasStoredReminderPreference = false),
    )
  }

  @Test
  fun homePrimaryActionPriority() {
    val stage = "chapter_4_batchim"
    assertEquals(
      HomePrimaryAction.ResumeCurriculum(stage),
      HomePrimaryActionPolicy.resolve(stage, "deck", "starter", true, false, false),
    )
    assertEquals(
      HomePrimaryAction.ResumeDeck("deck"),
      HomePrimaryActionPolicy.resolve("unknown_stage", "deck", "starter", true, false, false),
    )
    assertEquals(
      HomePrimaryAction.RecommendDeck("starter"),
      HomePrimaryActionPolicy.resolve(null, null, "starter", true, false, false),
    )
    assertEquals(HomePrimaryAction.DailyChallenge, HomePrimaryActionPolicy.resolve(null, null, "starter", false, false, false))
    assertEquals(HomePrimaryAction.DailyChallenge, HomePrimaryActionPolicy.resolve(null, null, "starter", true, true, false))
    assertEquals(HomePrimaryAction.DailyChallenge, HomePrimaryActionPolicy.resolve(null, null, "starter", true, false, true))
    assertEquals(HomePrimaryAction.DailyChallenge, HomePrimaryActionPolicy.resolve(null, null, null, true, false, false))
  }

  @Test
  fun hatchMissionsAreNotPriorHomeActivity() {
    val day = JstDay.parse("2026-07-16")!!
    val curriculumOnly = listOf(RetentionDayRecord(day, listOf(RetentionActivityKind.CURRICULUM)))
    assertFalse(HomePrimaryActionPolicy.hasPriorHomeActivity(hatchDone, curriculumOnly))
    assertTrue(HomePrimaryActionPolicy.hasPriorHomeActivity(hatchDone + "chapter_4_batchim", curriculumOnly))
    assertTrue(
      HomePrimaryActionPolicy.hasPriorHomeActivity(
        emptySet(),
        listOf(RetentionDayRecord(day, listOf(RetentionActivityKind.DAILY_CHALLENGE))),
      ),
    )
    assertTrue(HomePrimaryActionPolicy.shouldRememberLearningHistory(null, "deck", false))
    assertFalse(HomePrimaryActionPolicy.shouldRememberLearningHistory("nope", null, false))
  }

  @Test
  fun landscapeDashboardNeedsWideNonTallStandardType() {
    assertTrue(HomePrimaryActionPolicy.usesLandscapeDashboard(1100f, isTall = false, accessibilityFontSize = false))
    assertFalse(HomePrimaryActionPolicy.usesLandscapeDashboard(1099f, false, false))
    assertFalse(HomePrimaryActionPolicy.usesLandscapeDashboard(1200f, true, false))
    assertFalse(HomePrimaryActionPolicy.usesLandscapeDashboard(1200f, false, true))
  }
}
