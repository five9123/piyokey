package app.piyokey.android.feature.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.android.R
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale
import app.piyokey.android.feature.input.BuiltInKeyboardLayout
import app.piyokey.android.feature.input.KeyboardPreferences
import app.piyokey.android.feature.input.KeyboardTags
import app.piyokey.android.feature.input.SessionInputMode
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.android.ui.session.SessionResultTags
import app.piyokey.android.ui.theme.PiyokeyTheme
import app.piyokey.core.deckkit.DeckItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PracticeFlowInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  @Before
  fun builtInDubeolsik() {
    KeyboardPreferences.setDefaultInputMode(SessionInputMode.BUILT_IN)
    KeyboardPreferences.setBuiltInLayout(BuiltInKeyboardLayout.DUBEOLSIK)
    AppSettings.practiceAutoSpeaks.value = false
    AppSettings.practiceShowsTarget.value = true
    AppSettings.practiceShowsJamo.value = true
    AppSettings.practiceShowsComposition.value = true
  }

  private fun stateDescription(tag: String): String? =
    rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

  private fun item(id: String, ko: String) = DeckItem(id = id, ko = ko, readingJa = "", meaningJa = "")

  @Test
  fun curriculumMapRendersAndFirstStageOpensSession() {
    lateinit var navigator: Navigator
    rule.setContent {
      navigator = remember { Navigator() }
      PiyokeyTheme(AppTheme.LIGHT, FontScale.STANDARD) {
        CompositionLocalProvider(LocalAppNavigator provides navigator) {
          Box(Modifier.fillMaxSize()) {
            PracticeTabRoot()
            navigator.top?.Content()
          }
        }
      }
    }
    rule.onNodeWithTag(CurriculumTags.MAP_SCREEN).assertIsDisplayed()
    rule.onNodeWithTag(CurriculumTags.stage("chapter_5_words")).performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag(CurriculumTags.stage("chapter_1_basic_consonants")).performScrollTo().performClick()
    rule.waitUntilAtLeastOneExists(hasTestTag(PracticeTags.SCREEN), 5_000)
    rule.runOnIdle { assertTrue(navigator.top is PracticeSessionRoute) }
    rule.onNodeWithTag(PracticeTags.TARGET_CARD).assertIsDisplayed()
    rule.onNodeWithTag(KeyboardTags.DUBEOLSIK_CONTAINER).assertIsDisplayed()
  }

  @Test
  fun typingFullTargetCompletesItemAdvancesAndShowsResult() {
    var dismissed = 0
    val config = PracticeSessionConfig(
      sources = listOf(PracticeItemSource(item("test_1", "가"), "test_deck"), PracticeItemSource(item("test_2", "나"), "test_deck")),
      title = "Test",
      source = PracticeSource.FREE,
    )
    rule.setContent {
      val navigator = remember { Navigator() }
      PiyokeyTheme(AppTheme.LIGHT, FontScale.STANDARD) {
        CompositionLocalProvider(LocalAppNavigator provides navigator) {
          PracticeSessionScreen(config, onReplaceConfig = {}, onDismiss = { dismissed++ })
        }
      }
    }
    assertEquals("1 / 2", stateDescription(PracticeTags.OVERALL_PROGRESS))
    // A wrong key counts a mistake without progress.
    rule.onNodeWithTag(KeyboardTags.key('ㄴ')).performClick()
    assertEquals("1", stateDescription(PracticeTags.MISTAKES))
    rule.onNodeWithTag(KeyboardTags.key('ㄱ')).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㅏ')).performClick()
    rule.waitUntil(5_000) { stateDescription(PracticeTags.OVERALL_PROGRESS) == "2 / 2" }
    assertEquals("…", stateDescription(PracticeTags.ENTERED_TEXT))

    rule.onNodeWithTag(KeyboardTags.key('ㄴ')).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㅏ')).performClick()
    rule.waitUntilAtLeastOneExists(hasTestTag(PracticeResultTags.SCREEN), 5_000)
    // Missed item is collected for review.
    rule.onNodeWithTag(SessionResultTags.REVIEW_LIST, useUnmergedTree = true).assertExists()

    // Tap to skip the staged reveal, then the finish CTA closes the session.
    rule.onNodeWithTag(PracticeResultTags.SCREEN).performClick()
    rule.waitUntil(3_000) {
      rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.result_animation_ready)))).fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag(SessionResultTags.DONE_BOTTOM).performClick()
    rule.runOnIdle { assertEquals(1, dismissed) }
  }

  @Test
  fun retryFromResultRestartsSession() {
    val config = PracticeSessionConfig(
      sources = listOf(PracticeItemSource(item("test_retry", "가"), "test_deck")),
      title = "Retry",
      source = PracticeSource.FREE,
    )
    rule.setContent {
      val navigator = remember { Navigator() }
      PiyokeyTheme(AppTheme.LIGHT, FontScale.STANDARD) {
        CompositionLocalProvider(LocalAppNavigator provides navigator) {
          PracticeSessionScreen(config, onReplaceConfig = {}, onDismiss = {})
        }
      }
    }
    rule.onNodeWithTag(KeyboardTags.key('ㄱ')).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㅏ')).performClick()
    rule.waitUntilAtLeastOneExists(hasTestTag(PracticeResultTags.RETRY), 5_000)
    rule.onNodeWithTag(PracticeResultTags.SCREEN).performClick()
    rule.onNodeWithTag(SessionResultTags.REVIEW_PERFECT, useUnmergedTree = true).assertExists()
    rule.waitUntil(3_000) {
      rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.result_animation_ready)))).fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag(PracticeResultTags.RETRY).performClick()
    rule.waitUntil(3_000) { rule.onAllNodes(hasTestTag(PracticeResultTags.SCREEN)).fetchSemanticsNodes().isEmpty() }
    assertEquals("…", stateDescription(PracticeTags.ENTERED_TEXT))
    assertEquals("0", stateDescription(PracticeTags.MISTAKES))
  }
}
