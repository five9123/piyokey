package app.piyokey.android.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotEggPattern
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.PrivacyNoticePolicy
import app.piyokey.core.domain.OnboardingGoal
import app.piyokey.core.domain.OnboardingLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RootGateInstrumentedTest {
  @get:Rule val rule = createComposeRule()
  private val state = RootGateTestState()

  @Before fun setUp() = state.save()

  @After fun tearDown() = state.restore()

  private fun waitForTag(tag: String) = rule.waitUntilAtLeastOneExists(hasTestTag(tag), 10_000)

  private fun tap(tag: String) {
    waitForTag(tag)
    val node = rule.onNodeWithTag(tag)
    runCatching { node.performScrollTo() }
    node.performClick()
    rule.waitForIdle()
  }

  @Test
  fun freshInstallShowsOnboardingGoalStep() {
    state.seedFreshInstall()
    rule.setAppRoot()
    waitForTag("onboarding.goal.screen")
    rule.onNodeWithTag("onboarding.skip").assertExists()
    rule.onNodeWithTag("onboarding.mascot.egg").assertExists()
    rule.onNodeWithTag("onboarding.trust").assertExists()
  }

  @Test
  fun completingOnboardingWithinSixTapsReachesHatchMissions() {
    state.seedFreshInstall()
    rule.setAppRoot()
    tap("onboarding.goal.travel") // 1
    tap("onboarding.next") // 2 → level
    waitForTag("onboarding.level.screen")
    tap("onboarding.level.beginner") // 3
    tap("onboarding.next") // 4 → keyboard
    waitForTag("onboarding.keyboard.screen")
    tap("onboarding.next") // 5 → built-in keyboard lesson
    waitForTag("onboarding.lesson.coachmark")
    rule.onNodeWithTag("keyboard.key.ㄱ").performClick() // 6: first real keystroke
    rule.waitForIdle()
    rule.onNodeWithTag("keyboard.key.ㅏ").performClick()
    tap("onboarding.finish")
    waitForTag("onboarding.hatch.gate")

    assertEquals(OnboardingGoal.TRAVEL, AppData.onboarding.selectedGoal)
    assertEquals(OnboardingLevel.BEGINNER, AppData.onboarding.selectedLevel)
    assertEquals(MascotEggPattern.POLKA, MascotStore.eggPattern.value)
    assertFalse(AppData.onboarding.shouldPresent)
  }

  @Test
  fun skipCompletesOnboarding() {
    state.seedFreshInstall()
    rule.setAppRoot()
    tap("onboarding.skip")
    waitForTag("onboarding.hatch.gate")
    assertTrue(AppData.onboarding.snapshot.value.wasSkipped)
  }

  @Test
  fun appTourWalksSixStepsAndCompletes() {
    state.seedHatched(tourCompleted = false, privacyNoticeVersion = PrivacyNoticePolicy.CURRENT_VERSION)
    rule.setAppRoot()
    listOf("homePrimary", "discoverSearch", "practiceCurriculum", "gameModes", "myPageProfile", "settings").forEach { target ->
      waitForTag("app_tour.step.$target")
      rule.onNodeWithTag("app_tour.next").performClick()
      rule.waitForIdle()
    }
    rule.waitUntil(5_000) { AppData.onboarding.appTourCompleted }
    waitForTag("home.screen")
  }

  @Test
  fun appTourSkipCompletesTour() {
    state.seedHatched(tourCompleted = false, privacyNoticeVersion = PrivacyNoticePolicy.CURRENT_VERSION)
    rule.setAppRoot()
    tap("app_tour.skip")
    rule.waitUntil(5_000) { AppData.onboarding.appTourCompleted }
  }

  @Test
  fun privacyNoticeParticipateEnablesBothToggles() {
    state.seedHatched(tourCompleted = true, privacyNoticeVersion = 0)
    rule.setAppRoot()
    tap("privacy_consent.participate_and_continue")
    assertTrue(AppSettings.anonymousAnalyticsEnabled.value)
    assertTrue(AppSettings.crashDiagnosticsEnabled.value)
    assertEquals(PrivacyNoticePolicy.CURRENT_VERSION, AppSettings.privacyNoticeVersion.value)
  }

  @Test
  fun privacyNoticeContinueWithoutSharingDisablesBothToggles() {
    state.seedHatched(tourCompleted = true, privacyNoticeVersion = 0)
    rule.setAppRoot()
    tap("privacy_consent.continue_without_sharing")
    assertFalse(AppSettings.anonymousAnalyticsEnabled.value)
    assertFalse(AppSettings.crashDiagnosticsEnabled.value)
    assertEquals(PrivacyNoticePolicy.CURRENT_VERSION, AppSettings.privacyNoticeVersion.value)
  }

  @Test
  fun existingUserReconsentKeepsCrashDiagnosticsChoice() {
    state.seedHatched(tourCompleted = true, privacyNoticeVersion = 1, notificationRequested = false)
    AppSettings.crashDiagnosticsEnabled.value = true
    AppSettings.anonymousAnalyticsEnabled.value = true
    rule.setAppRoot()
    tap("privacy_consent.continue_without_sharing")
    assertFalse(AppSettings.anonymousAnalyticsEnabled.value)
    assertTrue(AppSettings.crashDiagnosticsEnabled.value)
    assertEquals(PrivacyNoticePolicy.CURRENT_VERSION, AppSettings.privacyNoticeVersion.value)
    // Existing users are never asked for notification permission by this flow.
    assertFalse(AppData.onboarding.notificationPermissionRequested)
  }
}
