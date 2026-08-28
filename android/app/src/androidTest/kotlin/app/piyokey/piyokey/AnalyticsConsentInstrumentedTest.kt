package app.piyokey.piyokey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.core.settings.AppPreferencesStore
import app.piyokey.core.settings.PrivacyNoticePolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsConsentInstrumentedTest {
  private val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(
      TestAppStateRule(
        skipOnboarding = true,
        resetStorage = true,
        privacyNoticeReviewed = false,
      ),
    )
    .around(composeRule)

  @Test
  fun privacyChoicesAreIndependentOptionalAndPersisted() {
    composeRule.waitUntil(timeoutMillis = 60_000) {
      runCatching {
        composeRule.onNodeWithTag("privacy-consent-analytics").fetchSemanticsNode()
      }.isSuccess
    }
    composeRule.onNodeWithTag("privacy-consent-dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("privacy-consent-analytics").assertIsOff().performClick().assertIsOn()
    composeRule.onNodeWithTag("privacy-consent-diagnostics").assertIsOff()
    composeRule.onNodeWithTag("privacy-consent-save").performClick()
    composeRule.waitForIdle()
    assertTrue(
      runCatching {
        composeRule.onNodeWithTag("privacy-consent-dialog").fetchSemanticsNode()
      }.isFailure,
    )

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val stored = runBlocking {
      AppPreferencesStore.create(context).values.first { it.privacyNoticeVersion > 0 }
    }
    assertEquals(PrivacyNoticePolicy.currentVersion, stored.privacyNoticeVersion)
    assertEquals(true, stored.anonymousAnalyticsEnabled)
    assertFalse(stored.crashDiagnosticsEnabled)

    composeRule.onNodeWithTag("common-settings-button").performClick()
    composeRule.onNodeWithTag("settings-review-privacy-choices").performClick()
    composeRule.onNodeWithTag("privacy-consent-dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("privacy-consent-continue-without-sharing").performClick()

    val disabled = runBlocking {
      AppPreferencesStore.create(context).values.first {
        !it.anonymousAnalyticsEnabled && !it.crashDiagnosticsEnabled
      }
    }
    assertEquals(PrivacyNoticePolicy.currentVersion, disabled.privacyNoticeVersion)
  }
}
