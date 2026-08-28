package app.piyokey.piyokey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M6GameOSIMEInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(TestAppStateRule(skipOnboarding = true, forceOSIME = true))
    .around(composeRule)

  @Test fun flowUsesOSIMEWhenItIsTheSavedDefault() {
    openGame("game-flow", "flow_topik_beginner", selectionTag = "flow-deck-flow_topik_beginner")
    composeRule.onNodeWithTag("flow-os-ime").assertIsDisplayed()
  }

  @Test fun directTypingUsesOSIMEWhenItIsTheSavedDefault() {
    openGame("game-word-match", "word_match_topik_beginner")
    composeRule.onNodeWithTag("game-os-ime").assertIsDisplayed()
  }

  private fun openGame(gameTag: String, deckId: String, selectionTag: String = "game-deck-$deckId") {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("nav-games").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("nav-games").performClick()
    composeRule.onNodeWithTag("game-hub").performScrollToIndex(if (gameTag == "game-flow") 2 else 4)
    composeRule.onNodeWithTag(gameTag).assertIsDisplayed().performClick()
    val route = gameTag.removePrefix("game-")
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("game-select-$route").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.mainClock.autoAdvance = false
    composeRule.onNodeWithTag(selectionTag).performScrollTo().performClick()
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.mainClock.advanceTimeByFrame()
  }
}
