package app.piyokey.android.feature.input

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.piyokey.core.hangul.Korean10KeyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DubeolsikKeyboardInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  private val keys = mutableListOf<Char>()
  private var backspaces = 0
  private val starts = mutableListOf<Long>()

  private fun setKeyboard(expected: Char? = null) {
    rule.setContent {
      DubeolsikKeyboard(
        nextExpectedKey = expected,
        onKey = { keys += it },
        onBackspace = { backspaces++ },
        onInputStart = { starts += it },
      )
    }
  }

  @Test
  fun tappingKeysEmitsJamoAndSpace() {
    setKeyboard()
    rule.onNodeWithTag(KeyboardTags.key('ㄱ')).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㅏ')).performClick()
    rule.onNodeWithTag(KeyboardTags.SPACE).performClick()
    rule.runOnIdle {
      assertEquals(listOf('ㄱ', 'ㅏ', ' '), keys)
      assertEquals(3, starts.size)
      assertTrue(starts.all { it >= 0 })
    }
  }

  @Test
  fun shiftIsOneShot() {
    setKeyboard()
    rule.onNodeWithTag(KeyboardTags.SHIFT).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㄲ')).performClick()
    // Shift released after one character: the key outputs ㄱ again.
    rule.onNodeWithTag(KeyboardTags.key('ㄱ')).performClick()
    rule.onNodeWithTag(KeyboardTags.SHIFT).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㅒ')).performClick()
    rule.runOnIdle { assertEquals(listOf('ㄲ', 'ㄱ', 'ㅒ'), keys) }
  }

  @Test
  fun backspaceAndAccessibilityActivation() {
    setKeyboard()
    rule.onNodeWithTag(KeyboardTags.BACKSPACE).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㅎ')).performSemanticsAction(SemanticsActions.OnClick)
    rule.runOnIdle {
      assertEquals(1, backspaces)
      assertEquals(listOf('ㅎ'), keys)
    }
  }

  @Test
  fun keyGuideHighlightsExpectedKeyAndShift() {
    setKeyboard(expected = 'ㄲ')
    rule.onNodeWithTag(KeyboardTags.SHIFT).assertIsSelected()
    rule.onNodeWithTag(KeyboardTags.key('ㄱ')).assertIsNotSelected()
    rule.onNodeWithTag(KeyboardTags.SHIFT).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㄲ')).assertIsSelected()
    rule.onNodeWithTag(KeyboardTags.SHIFT).assertIsNotSelected()
  }

  @Test
  fun rolloverCommitsEachPointerOnTouchDown() {
    setKeyboard()
    val container = rule.onNodeWithTag(KeyboardTags.DUBEOLSIK_CONTAINER)
    val origin = container.fetchSemanticsNode().boundsInRoot.topLeft
    fun center(tag: String): Offset = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center - origin
    val first = center(KeyboardTags.key('ㅇ'))
    val second = center(KeyboardTags.key('ㅏ'))
    container.performTouchInput {
      down(0, first)
      down(1, second)
    }
    // Both keys are committed while both fingers are still down.
    rule.runOnIdle { assertEquals(listOf('ㅇ', 'ㅏ'), keys) }
    container.performTouchInput {
      up(0)
      up(1)
    }
    rule.runOnIdle { assertEquals(listOf('ㅇ', 'ㅏ'), keys) }
  }
}

@RunWith(AndroidJUnit4::class)
class Korean10KeyKeyboardInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  private val taps = mutableListOf<Korean10KeyKey>()
  private val flicks = mutableListOf<Char?>()
  private var backspaces = 0

  private fun setKeyboard(expected: Korean10KeyKey? = null) {
    rule.setContent {
      Korean10KeyKeyboard(
        nextExpectedKey = expected,
        onKey = { taps += it },
        onCompletedJamo = { flicks += it },
        onBackspace = { backspaces++ },
      )
    }
  }

  private fun key(k: Korean10KeyKey): SemanticsNodeInteraction = rule.onNodeWithTag(KeyboardTags.tenKey(k))

  @Test
  fun tapResolvesOnTouchUp() {
    setKeyboard()
    key(Korean10KeyKey.GIYEOK).performTouchInput { down(center) }
    rule.runOnIdle { assertTrue(taps.isEmpty()) }
    key(Korean10KeyKey.GIYEOK).performTouchInput { up() }
    key(Korean10KeyKey.NEXT).performClick()
    key(Korean10KeyKey.SPACE).performClick()
    rule.onNodeWithTag(KeyboardTags.BACKSPACE).performClick()
    rule.runOnIdle {
      assertEquals(listOf(Korean10KeyKey.GIYEOK, Korean10KeyKey.NEXT, Korean10KeyKey.SPACE), taps)
      assertEquals(1, backspaces)
      assertTrue(flicks.isEmpty())
    }
  }

  @Test
  fun fastFlicksMapToJamo() {
    setKeyboard()
    key(Korean10KeyKey.NIEUN).performTouchInput { swipe(center, center + Offset(48.dp.toPx(), 0f), 120) }
    key(Korean10KeyKey.GIYEOK).performTouchInput { swipe(center, center + Offset(0f, 48.dp.toPx()), 120) }
    key(Korean10KeyKey.VERTICAL).performTouchInput { swipe(center, center - Offset(48.dp.toPx(), 0f), 120) }
    key(Korean10KeyKey.DOT).performTouchInput { swipe(center, center - Offset(0f, 48.dp.toPx()), 120) }
    rule.runOnIdle {
      assertEquals(listOf<Char?>('ㄹ', 'ㄲ', 'ㅓ', 'ㅗ'), flicks)
      assertTrue(taps.isEmpty())
    }
  }

  @Test
  fun slowDiagonalOrUnassignedFlicksAreInvalid() {
    setKeyboard()
    // Slower than 0.45 s.
    key(Korean10KeyKey.SIOT).performTouchInput { swipe(center, center + Offset(48.dp.toPx(), 0f), 700) }
    // Diagonal (no dominant axis).
    key(Korean10KeyKey.SIOT).performTouchInput { swipe(center, center + Offset(40.dp.toPx(), 40.dp.toPx()), 120) }
    // ㄴㄹ has no downward flick.
    key(Korean10KeyKey.NIEUN).performTouchInput { swipe(center, center + Offset(0f, 48.dp.toPx()), 120) }
    // Short movement is still a tap.
    key(Korean10KeyKey.SIOT).performTouchInput { swipe(center, center + Offset(6.dp.toPx(), 0f), 120) }
    rule.runOnIdle {
      assertEquals(listOf<Char?>(null, null, null), flicks)
      assertEquals(listOf(Korean10KeyKey.SIOT), taps)
    }
  }

  @Test
  fun guideHighlightsNextKeyAndExposesFlickHint() {
    setKeyboard(expected = Korean10KeyKey.DOT)
    key(Korean10KeyKey.DOT).assertIsSelected()
    key(Korean10KeyKey.VERTICAL).assertIsNotSelected()
  }
}

@RunWith(AndroidJUnit4::class)
class SessionKeyboardInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun tenKeySessionCommitsCompletedJamoAndCountsMistakes() {
    val state = SessionKeyboardState(initialLayout = BuiltInKeyboardLayout.KOREAN_10KEY)
    val jamo = mutableListOf<Char>()
    var mistakes = 0
    var backspaces = 0
    rule.setContent {
      SessionKeyboard(
        state = state,
        expectedNextJamo = 'ㅏ',
        osImeTarget = "아",
        osImeAcceptedText = "",
        onJamo = { jamo += it },
        onBackspace = { backspaces++ },
        onKorean10KeyMistake = { mistakes++ },
        onOsImeAcceptedSequence = {},
        onOsImeConfirmedMismatch = {},
        options = HangulKeyboardOptions(hapticsEnabled = false),
        onKeyFeedback = {},
      )
    }
    rule.onNodeWithTag(KeyboardTags.tenKey(Korean10KeyKey.VERTICAL)).assertIsSelected()
    rule.onNodeWithTag(KeyboardTags.tenKey(Korean10KeyKey.VERTICAL)).performClick()
    rule.runOnIdle { assertEquals("ㅣ", state.korean10KeyPendingDisplay) }
    rule.onNodeWithTag(KeyboardTags.tenKey(Korean10KeyKey.DOT)).performClick()
    rule.onNodeWithTag(KeyboardTags.tenKey(Korean10KeyKey.GIYEOK)).performClick()
    rule.onNodeWithTag(KeyboardTags.BACKSPACE).performClick()
    rule.runOnIdle {
      assertEquals(listOf('ㅏ'), jamo)
      assertEquals(1, mistakes)
      assertEquals(1, backspaces)
    }
  }

  @Test
  fun dubeolsikSessionForwardsEveryKey() {
    val state = SessionKeyboardState()
    val jamo = mutableListOf<Char>()
    rule.setContent {
      SessionKeyboard(
        state = state,
        expectedNextJamo = null,
        osImeTarget = "가",
        osImeAcceptedText = "",
        onJamo = { jamo += it },
        onBackspace = {},
        onKorean10KeyMistake = {},
        onOsImeAcceptedSequence = {},
        onOsImeConfirmedMismatch = {},
        revealsExpectedKey = false,
        options = HangulKeyboardOptions(hapticsEnabled = false),
        onKeyFeedback = {},
      )
    }
    rule.onNodeWithTag(KeyboardTags.key('ㅂ')).performClick()
    rule.onNodeWithTag(KeyboardTags.key('ㅡ')).performClick()
    rule.runOnIdle { assertEquals(listOf('ㅂ', 'ㅡ'), jamo) }
  }
}

@RunWith(AndroidJUnit4::class)
class OsImeInputInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  private val accepted = mutableListOf<List<Char>>()
  private var mistakes = 0
  private lateinit var hostView: View
  private var resetRevision by mutableIntStateOf(0)
  private var acceptedText by mutableStateOf("")

  private fun setPanel(target: String) {
    rule.setContent {
      hostView = LocalView.current
      Column {
        OsImeInputPanel(
          target = target,
          acceptedText = acceptedText,
          resetRevision = resetRevision,
          onAcceptedSequence = { accepted += it },
          onConfirmedMismatch = { mistakes++ },
        )
      }
    }
  }

  private fun field(): ImeEditText {
    var edit: ImeEditText? = null
    rule.runOnIdle { edit = hostView.rootView.findViewWithTag(ImeEditText.TAG) }
    return requireNotNull(edit)
  }

  @Test
  fun committedTextIsJudged() {
    setPanel("가나")
    rule.onNodeWithTag(OsImeTags.TEXT_FIELD).performTextInput("가")
    rule.runOnIdle { assertEquals("ㄱㅏ".toList(), accepted.last()) }
    rule.onNodeWithTag(OsImeTags.TEXT_FIELD).performTextInput("나")
    rule.runOnIdle {
      assertEquals("ㄱㅏㄴㅏ".toList(), accepted.last())
      assertEquals(0, mistakes)
    }
  }

  @Test
  fun composingSpanIsReportedSeparately() {
    setPanel("가나")
    val edit = field()
    val reports = mutableListOf<Pair<String, String?>>()
    rule.runOnIdle {
      val forward = edit.onTextChange
      edit.onTextChange = { committed, marked ->
        reports += committed to marked
        forward(committed, marked)
      }
      val connection = edit.onCreateInputConnection(EditorInfo())!!
      connection.commitText("가", 1)
      connection.setComposingText("ㄴ", 1)
      connection.setComposingText("나", 1)
      connection.finishComposingText()
    }
    rule.runOnIdle {
      assertEquals(listOf("가" to null, "가" to "ㄴ", "가" to "나", "가나" to null), reports)
      assertEquals("ㄱㅏㄴㅏ".toList(), accepted.last())
      assertEquals(0, mistakes)
    }
  }

  @Test
  fun confirmedMismatchCountsOnceAndRewritesField() {
    setPanel("가나")
    rule.onNodeWithTag(OsImeTags.TEXT_FIELD).performTextInput("가다")
    rule.waitForIdle()
    rule.runOnIdle {
      assertEquals(1, mistakes)
      assertEquals("ㄱㅏ".toList(), accepted.last())
    }
    val edit = field()
    rule.runOnIdle { assertEquals("가", edit.text.toString()) }
  }

  @Test
  fun asciiInputShowsWarningAndIsRemoved() {
    setPanel("가")
    rule.onNodeWithTag(OsImeTags.TEXT_FIELD).performTextInput("a")
    rule.waitForIdle()
    rule.onNodeWithTag(OsImeTags.INPUT_SOURCE_WARNING).assertExists()
    val edit = field()
    rule.runOnIdle {
      assertEquals("", edit.text.toString())
      assertEquals(0, mistakes)
      assertTrue(accepted.isEmpty())
    }
    // Korean input dismisses the warning.
    rule.onNodeWithTag(OsImeTags.TEXT_FIELD).performTextInput("가")
    rule.waitForIdle()
    rule.onNodeWithTag(OsImeTags.INPUT_SOURCE_WARNING).assertDoesNotExist()
  }

  @Test
  fun resetRevisionClearsFieldAndComposition() {
    setPanel("가나")
    val edit = field()
    rule.runOnIdle {
      val connection = edit.onCreateInputConnection(EditorInfo())!!
      connection.setComposingText("가", 1)
    }
    rule.runOnIdle {
      acceptedText = ""
      resetRevision++
    }
    rule.waitForIdle()
    rule.runOnIdle {
      assertEquals("", edit.text.toString())
      assertEquals(null, edit.currentSnapshot()?.marked)
    }
  }
}
