package app.piyokey.piyokey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.core.settings.AppTheme
import app.piyokey.core.settings.FontScale
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidReleasePolishInstrumentedTest {
  private val composeRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(
      TestAppStateRule(
        skipOnboarding = true,
        resetStorage = true,
        theme = AppTheme.DARK,
        fontScale = FontScale.LARGE,
      ),
    )
    .around(composeRule)

  @Test
  fun darkThemeAndLargeTextKeepEveryPrimaryDestinationReachable() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("nav-home").fetchSemanticsNode() }.isSuccess
    }

    listOf("home", "discover", "practice", "games", "profile").forEach { destination ->
      composeRule.onNodeWithTag("nav-$destination").assertIsDisplayed().performClick()
      composeRule.onNodeWithTag("common-settings-button").assertIsDisplayed()
    }
  }

  @Test
  fun settingsRemainsReadableFromDarkProfileSurface() {
    composeRule.waitUntil(timeoutMillis = 15_000) {
      runCatching { composeRule.onNodeWithTag("nav-profile").fetchSemanticsNode() }.isSuccess
    }
    composeRule.onNodeWithTag("nav-profile").performClick()
    composeRule.onNodeWithTag("common-settings-button").performClick()
    composeRule.onNodeWithTag("settings-sheet").assertIsDisplayed()
  }
}
