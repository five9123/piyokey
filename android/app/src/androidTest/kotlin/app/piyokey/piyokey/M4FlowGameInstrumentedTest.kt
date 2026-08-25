package app.piyokey.piyokey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M4FlowGameInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(TestAppStateRule(skipOnboarding = true)).around(composeRule)

  @Test
  fun gameHubSelectsBundledFlowAndStartsCountdownWithFixedKeyboard() {
    waitForShell()
    composeRule.onNodeWithTag("nav-games").performClick()
    composeRule.onNodeWithTag("game-flow").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("flow-deck-flow_topik_beginner").assertIsDisplayed()
    composeRule.mainClock.autoAdvance = false
    composeRule.onNodeWithTag("flow-deck-flow_topik_beginner").performClick()
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.onNodeWithTag("flow-card").fetchSemanticsNode()
    composeRule.onNodeWithTag("flow-keyboard").assertIsDisplayed()
  }

  @Test
  fun weeklyCupEndsAfterThreeMissesAndOpensCommonResult() {
    waitForShell()
    composeRule.onNodeWithTag("nav-games").performClick()
    composeRule.onNodeWithTag("weekly-cup").assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("flow-result").fetchSemanticsNodes().size == 1
    }
    composeRule.onNodeWithTag("flow-result").assertIsDisplayed()
  }

  private fun waitForShell() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("nav-games").fetchSemanticsNode() }.isSuccess
    }
  }
}
