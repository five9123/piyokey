package app.piyokey.android.feature.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.android.data.settings.PrivacyNoticePolicy
import app.piyokey.android.feature.onboarding.RootGateTestState
import app.piyokey.android.feature.onboarding.setAppRoot
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeInstrumentedTest {
  @get:Rule val rule = createComposeRule()
  private val state = RootGateTestState()

  @Before
  fun setUp() {
    state.save()
    state.seedHatched(tourCompleted = true, privacyNoticeVersion = PrivacyNoticePolicy.CURRENT_VERSION)
  }

  @After fun tearDown() = state.restore()

  private fun waitForTag(tag: String) = rule.waitUntilAtLeastOneExists(hasTestTag(tag), 15_000)

  @Test
  fun homeShowsStampCardPrimaryQuickActionsAndRecommendations() {
    rule.setAppRoot()
    waitForTag("home.screen")
    rule.onNodeWithTag("home.my_piyo_card").assertExists()
    rule.onNodeWithTag("retention.stamp_calendar", useUnmergedTree = true).assertExists()
    rule.waitUntil(15_000) {
      listOf(
        "home.primary.resume_curriculum", "home.primary.resume_deck",
        "home.primary.recommend_deck", "retention.daily_challenge",
      ).any {
        rule.onAllNodesWithTagCount(it) > 0
      }
    }
    rule.onNodeWithTag("home.quick.piyo_cup").assertExists()
    rule.onNodeWithTag("home.quick.random").assertExists()
    waitForTag("home.recommendations.personal")
    rule.onNodeWithTag("home.recommendations.personal").performScrollTo()
    rule.onNodeWithTag("root.settings").assertExists()
  }

  @Test
  fun stampCardOpensMyPiyoDetailWithRewards() {
    rule.setAppRoot()
    waitForTag("home.my_piyo_card")
    rule.onNodeWithTag("home.my_piyo_card").performClick()
    waitForTag("my_piyo.detail.screen")
    rule.onNodeWithTag("my_piyo.detail.stamps").assertExists()
    listOf(3, 5, 7).forEach { rule.onNodeWithTag("my_piyo.detail.reward.$it").performScrollTo() }
  }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
  onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
