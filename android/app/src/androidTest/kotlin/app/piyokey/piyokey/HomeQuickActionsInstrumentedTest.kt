package app.piyokey.piyokey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeQuickActionsInstrumentedTest {
  private val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(TestAppStateRule(skipOnboarding = true, forceOSIME = true, resetStorage = true))
    .around(composeRule)

  @Test
  fun firstHomeShowsAStarterDeckInsteadOfResume() {
    waitForShell()
    composeRule.onNodeWithTag("home-primary-recommend-deck").performScrollTo().assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("deck-detail-download").assertIsDisplayed()
    composeRule.onAllNodesWithTag("home-primary-resume-deck").assertCountEquals(0)
  }

  @Test
  fun weeklyCupForcesBuiltinKeyboardWhenDefaultIsOsIme() {
    waitForShell()
    composeRule.onNodeWithTag("home-quick-piyo-cup").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 20_000) {
      runCatching { composeRule.onNodeWithTag("flow-result").fetchSemanticsNode() }.isSuccess ||
        runCatching { composeRule.onNodeWithTag("operation-error").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onAllNodesWithTag("operation-error").assertCountEquals(0)
    composeRule.onNodeWithTag("flow-result").assertIsDisplayed()
  }

  private fun waitForShell() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("nav-home").fetchSemanticsNode() }.isSuccess
    }
  }
}
