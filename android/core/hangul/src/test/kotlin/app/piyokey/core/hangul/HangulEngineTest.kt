package app.piyokey.core.hangul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HangulEngineTest {
  @Test
  fun automatonPhasesAndBoundaryTransitions() {
    var state = CompositionState()
    assertEquals(CompositionPhase.EMPTY, state.phase)

    state = HangulComposer.reduce(state, CompositionEvent.Key('ㄱ'))
    assertEquals(CompositionPhase.CHO, state.phase)
    state = HangulComposer.reduce(state, CompositionEvent.Key('ㅏ'))
    assertEquals(CompositionPhase.CHO_JUNG, state.phase)
    state = HangulComposer.reduce(state, CompositionEvent.Key('ㄴ'))
    assertEquals(CompositionPhase.CHO_JUNG_JONG, state.phase)
    assertEquals("간", state.text)

    state = HangulComposer.reduce(state, CompositionEvent.Backspace)
    assertEquals(CompositionPhase.CHO_JUNG, state.phase)
    assertEquals("가", state.text)

    assertEquals("ㄱㄴ", compose("ㄱㄴ"))
    assertEquals("가ㄸ", compose("ㄱㅏㄸ"))
    assertEquals("가ㅓ", compose("ㄱㅏㅓ"))
    assertEquals("ㅘ", compose("ㅗㅏ"))
    assertEquals("ㅏㄱ", compose("ㅏㄱ"))
    assertEquals("달가", compose("ㄷㅏㄹㄱㅏ"))
    assertEquals("가나", compose("ㄱㅏㄴㅏ"))
    assertEquals("가 A", compose("ㄱㅏ A"))

    val standaloneVowels = HangulComposer.compose(sequenceOf('ㅏ', 'ㅓ'))
    assertEquals("ㅏㅓ", standaloneVowels.text)
    assertEquals("ㅏ", standaloneVowels.committedText)
    assertEquals(CompositionPhase.JUNG, standaloneVowels.phase)
  }

  @Test
  fun backspaceRestoresCarryoverAndIgnoresExcessDeletion() {
    var state = HangulComposer.compose("ㄱㅏㄴㅏ".asIterable())
    assertEquals("가나", state.text)
    state = HangulComposer.reduce(state, CompositionEvent.Backspace)
    assertEquals("간", state.text)
    repeat(10) {
      state = HangulComposer.reduce(state, CompositionEvent.Backspace)
    }
    assertEquals("", state.text)
    assertEquals(CompositionPhase.EMPTY, state.phase)
  }

  @Test
  fun decomposerExpandsCompoundJamoAndRejectsUntypeableText() {
    assertEquals("ㅗㅏㄱㅅ 2!".toList(), JamoDecomposer.keySequenceFor("ㅘㄳ 2!"))
    assertTrue(JamoDecomposer.containsHangul("한글 2"))
    assertTrue(JamoDecomposer.containsHangul("ㄱ"))
    assertTrue(JamoDecomposer.containsHangul("ㅏ"))
    assertFalse(JamoDecomposer.containsHangul("123"))
    assertFalse(JamoDecomposer.containsHangul(""))
    assertTrue(JamoDecomposer.isShiftJamo('ㄲ'))
    assertFalse(JamoDecomposer.isShiftJamo('ㄱ'))
    assertEquals("Ⅷ²".toList(), JamoDecomposer.keySequenceFor("Ⅷ²"))
    assertEquals("ㄱㅏ𝟙".toList(), JamoDecomposer.keySequenceFor("가𝟙"))
    assertEquals(
      JamoDecompositionException.EmptyTarget,
      assertFailsWith<JamoDecompositionException> { JamoDecomposer.keySequenceFor("") },
    )
    assertEquals(
      JamoDecompositionException.UnsupportedCharacter('A', offset = 0),
      assertFailsWith<JamoDecompositionException> { JamoDecomposer.keySequenceFor("ABC") },
    )
    assertTrue(
      assertFailsWith<JamoDecompositionException.UnsupportedCharacter> {
        JamoDecomposer.keySequenceFor("가😀")
      }.offset == 1,
    )
  }

  @Test
  fun judgeCountsMistakeWithoutAdvancingOrPollutingTarget() {
    var state = JamoJudgeState.forTarget("가")
    var evaluation = JamoSequenceJudge.evaluate('ㄴ', state)
    state = evaluation.state
    assertEquals(JamoJudgeResult.Incorrect(expected = 'ㄱ'), evaluation.result)
    assertEquals(0, state.currentIndex)
    assertEquals(0, state.correctCount)
    assertEquals(1, state.errorCount)

    evaluation = JamoSequenceJudge.evaluate('ㄱ', state)
    state = evaluation.state
    assertEquals(JamoJudgeResult.Correct(completed = false), evaluation.result)
    evaluation = JamoSequenceJudge.evaluate('ㅏ', state)
    state = evaluation.state
    assertEquals(JamoJudgeResult.Correct(completed = true), evaluation.result)
    assertEquals(2, state.correctCount)
    assertEquals(1, state.errorCount)
    assertEquals(JamoJudgeResult.AlreadyComplete, JamoSequenceJudge.evaluate('ㅏ', state).result)
  }

  @Test
  fun immutableCompositionStateHasStableValueDiagnostics() {
    val direct = HangulComposer.compose("ㅘ".asIterable())
    val same = HangulComposer.compose("ㅘ".asIterable())
    val sameSnapshotWithDifferentHistory = HangulComposer.compose(sequenceOf('ㅗ', 'ㅏ'))
    val differentSnapshot = HangulComposer.compose("ㅏ".asIterable())

    assertEquals(direct, direct)
    assertEquals(direct, same)
    assertEquals(direct.hashCode(), same.hashCode())
    assertEquals("CompositionState(text=ㅘ, phase=JUNG)", direct.toString())
    assertNotEquals(direct, sameSnapshotWithDifferentHistory)
    assertNotEquals(direct, differentSnapshot)
    assertFalse(direct.equals("ㅘ"))
  }

  private fun compose(keys: String): String = HangulComposer.compose(keys.asIterable()).text
}
