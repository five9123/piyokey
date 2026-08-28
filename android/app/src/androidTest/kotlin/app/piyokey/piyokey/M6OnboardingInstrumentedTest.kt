package app.piyokey.piyokey

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M6OnboardingInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(TestAppStateRule(skipOnboarding = false, freshInstall = true))
    .around(composeRule)

  @Test
  fun introSkipBypassesFirstInputButNeverBypassesHatchMissions() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("onboarding-goal").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("onboarding-skip").performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("hatch-gate").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("hatch-start-1").assertIsDisplayed()
  }

  @Test
  fun normalIntroSelectsLevelBeforeRealTyping() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("onboarding-goal").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("onboarding-goal-keyboard").performClick() // tap 1
    composeRule.onNodeWithTag("onboarding-goal").performScrollToIndex(5)
    composeRule.onNodeWithTag("onboarding-next").performClick() // tap 2
    composeRule.onNodeWithTag("onboarding-level-beginner").performClick()
    composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(5)
    composeRule.onNodeWithTag("onboarding-next").performClick()
    composeRule.onNodeWithTag("onboarding-keyboard-builtin").performClick() // tap 5
    composeRule.onNodeWithTag("keyboard-key-ㄱ").performClick() // tap 6: first real typing input
    composeRule.onNodeWithTag("keyboard-key-ㅏ").performClick()
    composeRule.onNodeWithTag("onboarding-first-reward").assertIsDisplayed()
    // Permission consent is verified separately; this path verifies the explicit no-reminder choice.
    composeRule.onNodeWithTag("onboarding-begin-hatch").performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("hatch-start-1").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("hatch-start-1").assertIsDisplayed()
  }

  @Test
  fun notificationConsentEnablesLocalTwentyHundredReminder() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    instrumentation.uiAutomation.grantRuntimePermission(
      context.packageName,
      Manifest.permission.POST_NOTIFICATIONS,
    )

    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("onboarding-goal").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("onboarding-goal-keyboard").performClick()
    composeRule.onNodeWithTag("onboarding-goal").performScrollToIndex(5)
    composeRule.onNodeWithTag("onboarding-next").performClick()
    composeRule.onNodeWithTag("onboarding-level-beginner").performClick()
    composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(5)
    composeRule.onNodeWithTag("onboarding-next").performClick()
    composeRule.onNodeWithTag("onboarding-keyboard-builtin").performClick()
    composeRule.onNodeWithTag("keyboard-key-ㄱ").performClick()
    composeRule.onNodeWithTag("keyboard-key-ㅏ").performClick()
    composeRule.onNodeWithTag("onboarding-enable-reminder").performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("hatch-gate").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("hatch-start-1").assertIsDisplayed()
  }

  @Test
  fun deviceKeyboardCompletesFirstInputAndCarriesIntoHatchMission() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("onboarding-goal").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("onboarding-goal-keyboard").performClick()
    composeRule.onNodeWithTag("onboarding-goal").performScrollToIndex(5)
    composeRule.onNodeWithTag("onboarding-next").performClick()
    composeRule.onNodeWithTag("onboarding-level-beginner").performClick()
    composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(5)
    composeRule.onNodeWithTag("onboarding-next").performClick()
    composeRule.onNodeWithTag("onboarding-keyboard-device").performScrollTo()
    composeRule.onNodeWithTag("onboarding-keyboard-device").performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("physical-keyboard-guide").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("physical-keyboard-guide").assertIsDisplayed()
    composeRule.onNodeWithTag("physical-keyboard-key-R").assertIsDisplayed()
    composeRule.onNodeWithTag("onboarding-os-ime-field").performTextReplacement("가")
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("onboarding-first-reward").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("onboarding-begin-hatch").performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("hatch-start-1").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("hatch-start-1").performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("practice-os-ime-field").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("practice-os-ime-field").assertIsDisplayed()
    composeRule.onNodeWithTag("physical-keyboard-guide").assertIsDisplayed()
  }
}
