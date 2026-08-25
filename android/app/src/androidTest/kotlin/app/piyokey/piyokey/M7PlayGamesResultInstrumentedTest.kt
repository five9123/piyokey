package app.piyokey.piyokey

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.feature.game.GenericGameResultScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M7PlayGamesResultInstrumentedTest {
  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun eligibleResultExposesExplicitLeaderboardActionAndOnlyClickInvokesIt() {
    var opens = 0
    composeRule.activity.setContent {
      MaterialTheme {
        GenericGameResultScreen(
          score = 900,
          accuracy = 95.0,
          maxCombo = 10,
          completed = 8,
          onPlayGamesLeaderboard = { opens += 1 },
          onRetry = {},
          onDone = {},
        )
      }
    }

    composeRule.onNodeWithTag("play-games-leaderboard").assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(0, opens) }
    composeRule.onNodeWithTag("play-games-leaderboard").performClick()
    composeRule.runOnIdle { assertEquals(1, opens) }
  }

  @Test
  fun ineligibleResultHasNoLeaderboardAction() {
    composeRule.activity.setContent {
      MaterialTheme {
        GenericGameResultScreen(
          score = 10,
          accuracy = 50.0,
          maxCombo = 1,
          completed = 1,
          onRetry = {},
          onDone = {},
        )
      }
    }

    composeRule.onAllNodesWithTag("play-games-leaderboard").assertCountEquals(0)
  }
}
