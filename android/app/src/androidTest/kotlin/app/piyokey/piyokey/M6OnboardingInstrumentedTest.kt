package app.piyokey.piyokey

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.core.settings.AppLanguage
import app.piyokey.core.settings.AppPreferencesStore
import app.piyokey.core.settings.OnboardingLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
  fun everyLanguageRestoresLevelSelectionAfterRecreation() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    // Exercise persisted app preferences after the initial fresh-install setup.
    context.getSharedPreferences("piyokey_test_overrides", 0)
      .edit().putBoolean("fresh_onboarding", false).commit()
    val store = AppPreferencesStore.create(context)
    val originalLanguage = runBlocking { store.values.first().language }
    for (language in AppLanguage.entries) {
      runBlocking {
        store.resetOnboarding()
        store.update { it.copy(language = language) }
      }
      // Locale changes recreate the activity automatically; avoid a concurrent manual recreation.
      composeRule.waitUntil(timeoutMillis = 15_000) {
        runCatching {
          composeRule.onNodeWithTag("onboarding-goal").fetchSemanticsNode()
          composeRule.activity.resources.configuration.locales[0].language == language.tag
        }.getOrDefault(false)
      }
      composeRule.waitForIdle()
      composeRule.onNodeWithTag("onboarding-goal-travel").performScrollTo().performClick()
      composeRule.onNodeWithTag("onboarding-goal").performScrollToIndex(3)
      composeRule.onNodeWithTag("onboarding-next").performClick()
      composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(3)
      composeRule.onNodeWithTag("onboarding-next").assertIsNotEnabled()
      composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(2)
      composeRule.onNodeWithTag("onboarding-level-sentences").performClick()
      composeRule.waitUntil(timeoutMillis = 5_000) {
        runBlocking { store.values.first().onboardingLevel == OnboardingLevel.SENTENCES }
      }
      composeRule.activityRule.scenario.recreate()
      composeRule.waitUntil(timeoutMillis = 15_000) {
        runCatching { composeRule.onNodeWithTag("onboarding-level").fetchSemanticsNode() }.isSuccess
      }
      composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(2)
      composeRule.onNodeWithTag("onboarding-level-sentences").assertIsSelected()
      composeRule.onNodeWithTag("onboarding-back").assertDoesNotExist()
      composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(0)
      composeRule.waitForIdle()
      instrumentation.waitForIdleSync()
      java.io.File(context.getExternalFilesDir(null), "onboarding-level-${language.tag}.png")
        .outputStream().use { output ->
          instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
      composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(3)
      composeRule.onNodeWithTag("onboarding-next").performClick()
      composeRule.onNodeWithTag("onboarding-keyboard-builtin").assertIsDisplayed()
      assertEquals(language, runBlocking { store.values.first().language })
    }
    runBlocking {
      store.resetOnboarding()
      store.update { it.copy(language = originalLanguage) }
    }
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching {
        composeRule.onNodeWithTag("onboarding-goal").fetchSemanticsNode()
        composeRule.activity.resources.configuration.locales[0].language == originalLanguage.tag
      }.getOrDefault(false)
    }
    composeRule.waitForIdle()
  }

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
    composeRule.onNodeWithTag("onboarding-goal").performScrollToIndex(3)
    composeRule.onNodeWithTag("onboarding-next").performClick() // tap 2
    composeRule.onNodeWithTag("onboarding-level-beginner").performClick()
    composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(3)
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
    composeRule.onNodeWithTag("onboarding-goal").performScrollToIndex(3)
    composeRule.onNodeWithTag("onboarding-next").performClick()
    composeRule.onNodeWithTag("onboarding-level-beginner").performClick()
    composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(3)
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
    composeRule.onNodeWithTag("onboarding-goal").performScrollToIndex(3)
    composeRule.onNodeWithTag("onboarding-next").performClick()
    composeRule.onNodeWithTag("onboarding-level-beginner").performClick()
    composeRule.onNodeWithTag("onboarding-level").performScrollToIndex(3)
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
