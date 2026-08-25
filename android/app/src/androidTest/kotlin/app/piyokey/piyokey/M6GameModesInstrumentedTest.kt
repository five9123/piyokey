package app.piyokey.piyokey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M6GameModesInstrumentedTest {
  val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(TestAppStateRule(skipOnboarding = true)).around(composeRule)

  @Test fun acidRainOpensOfflineCourseCountdownAndKeyboard() {
    openGame("game-acid-rain", "acid_rain_topik_beginner")
    composeRule.onNodeWithTag("acid-rain-game").assertIsDisplayed()
    composeRule.onNodeWithTag("acid-rain-keyboard").assertIsDisplayed()
  }

  @Test fun choseongOpensDirectTypingFlow() {
    openGame("game-choseong", "choseong_topik_beginner")
    composeRule.onNodeWithTag("typing-game-choseong").assertIsDisplayed()
    composeRule.onNodeWithTag("game-builtin-keyboard").assertIsDisplayed()
  }

  @Test fun wordMatchOpensMeaningToTypingFlow() {
    openGame("game-word-match", "word_match_topik_beginner")
    composeRule.onNodeWithTag("typing-game-word-match").assertIsDisplayed()
    composeRule.onNodeWithTag("game-builtin-keyboard").assertIsDisplayed()
  }

  @Test fun dictationDoesNotExposeKnownAnswerBeforeCompletion() {
    openGame("game-dictation", "dictation_topik_beginner")
    composeRule.onNodeWithTag("typing-game-dictation").assertIsDisplayed()
    assertTrue(composeRule.onAllNodesWithText("나", substring = false).fetchSemanticsNodes().isEmpty())
    composeRule.onNodeWithTag("game-builtin-keyboard").assertIsDisplayed()
  }

  @Test fun spacingOpensSixthOfflinePassageAndBoundaryControls() {
    waitForShell()
    composeRule.onNodeWithTag("nav-games").performClick()
    composeRule.onNodeWithTag("game-hub").performScrollToIndex(4)
    composeRule.onNodeWithTag("game-spacing").performClick()
    composeRule.onNodeWithTag("spacing-select").performScrollToIndex(6)
    composeRule.onNodeWithTag("spacing-level-6").performScrollTo().performClick()
    composeRule.onNodeWithTag("spacing-game").assertIsDisplayed()
  }

  private fun openGame(gameTag: String, deckId: String) {
    waitForShell()
    composeRule.onNodeWithTag("nav-games").performClick()
    val rowIndex = when (gameTag) {
      "game-flow", "game-acid-rain" -> 2
      "game-choseong", "game-dictation" -> 3
      else -> 4
    }
    composeRule.onNodeWithTag("game-hub").performScrollToIndex(rowIndex)
    composeRule.onNodeWithTag(gameTag).assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("game-select-${gameTag.removePrefix("game-")}").assertIsDisplayed()
    composeRule.mainClock.autoAdvance = false
    composeRule.onNodeWithTag("game-deck-$deckId").performScrollTo().performClick()
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.mainClock.advanceTimeByFrame()
  }

  private fun waitForShell() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      composeRule.onAllNodesWithTag("nav-games").fetchSemanticsNodes().isNotEmpty()
    }
  }
}
