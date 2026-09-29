package app.piyokey.android.feature.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale
import app.piyokey.android.feature.input.BuiltInKeyboardLayout
import app.piyokey.android.feature.input.KeyboardPreferences
import app.piyokey.android.feature.input.KeyboardTags
import app.piyokey.android.feature.input.SessionInputMode
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabController
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.nav.TabController
import app.piyokey.android.ui.theme.PiyokeyTheme
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.game.FlowGameResult
import app.piyokey.core.domain.game.GamePresetSessionRandomizer
import app.piyokey.core.domain.game.GameResultPresentation
import app.piyokey.core.domain.game.SpacingPassage
import app.piyokey.core.hangul.JamoDecomposer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GameInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  @Before
  fun setUp() {
    KeyboardPreferences.setDefaultInputMode(SessionInputMode.BUILT_IN)
    KeyboardPreferences.setBuiltInLayout(BuiltInKeyboardLayout.DUBEOLSIK)
    GameTestHooks.countdownStepMillis = 100
    GameTestHooks.resultAnimationScale = 0.01
    GamePresetSessionRandomizer.usesDeterministicOrder = true
  }

  @After
  fun tearDown() {
    GameTestHooks.countdownStepMillis = null
    GameTestHooks.resultAnimationScale = 1.0
    GamePresetSessionRandomizer.usesDeterministicOrder = false
  }

  private fun host(root: Route, content: @Composable () -> Unit = { }) {
    rule.setContent {
      val appNav = remember { Navigator() }
      val tabNav = remember { Navigator(root) }
      val tabs = remember { TabController() }
      PiyokeyTheme(AppTheme.LIGHT, FontScale.STANDARD) {
        CompositionLocalProvider(LocalAppNavigator provides appNav, LocalTabNavigator provides tabNav, LocalTabController provides tabs) {
          Box(Modifier.fillMaxSize()) {
            tabNav.top?.Content()
            appNav.top?.Content()
            content()
          }
        }
      }
    }
  }

  private fun stateDescription(tag: String): String? =
    rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

  private fun contentDescription(tag: String): String? =
    rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

  @Test
  fun hubRendersSixModesAndPiyoCupCard() {
    host(object : Route {
      @Composable override fun Content() = GameTabRoot()
    })
    rule.onNodeWithTag("game.selection.screen").assertIsDisplayed()
    rule.onNodeWithTag("game.piyo_cup").assertIsDisplayed()
    for (tag in listOf("flow", "acid_rain", "choseong", "word_match", "dictation", "spacing")) {
      rule.onNodeWithTag("game.mode.$tag").performScrollTo().assertIsDisplayed()
    }
    rule.onNodeWithTag("game.mode.flow").performScrollTo().performClick()
    rule.waitUntilAtLeastOneExists(hasTestTag("game.deck_selection.screen"), 5_000)
    rule.onNodeWithTag("game.flow.preset.beginner").assertIsDisplayed()
    rule.onNodeWithTag("game.flow.add_deck").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun flowShowsCountdownThenTargetAndTypingScores() {
    val preset = GameDecks.presets(GameKind.FLOW).first().deck
    val item = preset.items.first { word -> JamoDecomposer.keySequence(word.ko).none(JamoDecomposer::isShiftJamo) }
    val deck = preset.copy(deckId = "test_flow_deck", items = listOf(item))
    rule.mainClock.autoAdvance = false
    host(FlowGameRoute(deck, GameKind.FLOW, null))
    rule.mainClock.advanceTimeByFrame()
    rule.onNodeWithTag("game.countdown").assertExists()
    rule.mainClock.advanceTimeBy(600)
    rule.onNodeWithTag("game.countdown").assertDoesNotExist()
    assertEquals(item.ko, contentDescription("game.target.value"))
    assertEquals("0", contentDescription("game.score.value"))
    for (jamo in JamoDecomposer.keySequence(item.ko)) {
      rule.onNodeWithTag(KeyboardTags.key(jamo)).performClick()
      rule.mainClock.advanceTimeByFrame()
    }
    rule.mainClock.advanceTimeBy(100)
    val status = rule.onNodeWithTag("game.feedback.status").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString()
    assertNotEquals("status=$status jamo=${JamoDecomposer.keySequence(item.ko)}", "0", contentDescription("game.score.value"))
    assertEquals("1", contentDescription("game.combo.value"))
  }

  @Test
  fun spacingGameTogglesABoundary() {
    val passage = SpacingPassage("test", "spacing.passage.morning", "나는 학교에 간다.")
    host(SpacingGameRoute(passage))
    rule.onNodeWithTag("spacing.play.screen").assertIsDisplayed()
    assertEquals("나는학교에간다.", stateDescription("spacing.passage.display"))
    rule.onNodeWithTag("spacing.control.space").performClick()
    rule.onNodeWithTag("spacing.feedback.incorrect").assertExists()
    assertEquals("나 는학교에간다.", stateDescription("spacing.passage.display"))
    rule.onNodeWithTag("spacing.control.space").performClick()
    rule.onNodeWithTag("spacing.control.next").performClick()
    rule.onNodeWithTag("spacing.control.space").performClick()
    rule.onNodeWithTag("spacing.feedback.correct").assertExists()
    assertEquals("나는 학교에간다.", stateDescription("spacing.passage.display"))
  }

  @Test
  fun resultScreenShowsRank() {
    val deck = GameDecks.presets(GameKind.CHOSEONG).first().deck
    val result = FlowGameResult(1_234, 7, 91.5, 30.0, 40.0, 9, 1, "A")
    host(object : Route {
      @Composable
      override fun Content() = GameResultScreen(deck, result, null, emptyList(), GameResultPresentation.CHOSEONG, {}, {})
    })
    rule.waitUntilAtLeastOneExists(hasTestTag("game.result.rank"), 5_000)
    rule.onNodeWithTag("game.result.rank").assertIsDisplayed()
    rule.onNodeWithTag("game.result.screen").assertIsDisplayed()
    rule.onNodeWithTag("game.result.retry").assertExists()
  }
}
