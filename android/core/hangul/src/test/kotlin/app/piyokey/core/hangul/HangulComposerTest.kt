package app.piyokey.core.hangul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HangulComposerTest {
  private fun key(c: Char) = CompositionEvent.Key(c)

  @Test
  fun automatonPhasesAndBoundaryTransitions() {
    var state = CompositionState()
    assertEquals(CompositionPhase.EMPTY, state.phase)

    state = HangulComposer.reduce(state, key('ㄱ'))
    assertEquals(CompositionPhase.CHO, state.phase)
    state = HangulComposer.reduce(state, key('ㅏ'))
    assertEquals(CompositionPhase.CHO_JUNG, state.phase)
    state = HangulComposer.reduce(state, key('ㄴ'))
    assertEquals(CompositionPhase.CHO_JUNG_JONG, state.phase)
    assertEquals("간", state.text)

    state = HangulComposer.reduce(state, CompositionEvent.Backspace)
    assertEquals(CompositionPhase.CHO_JUNG, state.phase)
    assertEquals("가", state.text)

    assertEquals("ㄱㄴ", HangulComposer.compose("ㄱㄴ").text)
    assertEquals("가ㄸ", HangulComposer.compose("ㄱㅏㄸ").text)
    assertEquals("가ㅓ", HangulComposer.compose("ㄱㅏㅓ").text)
    assertEquals("ㅘ", HangulComposer.compose("ㅗㅏ").text)
    assertEquals("ㅏㄱ", HangulComposer.compose("ㅏㄱ").text)
    assertEquals("달가", HangulComposer.compose("ㄷㅏㄹㄱㅏ").text)
    assertEquals("가나", HangulComposer.compose("ㄱㅏㄴㅏ").text)
    assertEquals("가 A", HangulComposer.compose("ㄱㅏ A").text)
  }

  @Test
  fun backspaceRestoresCarryoverAndIgnoresExcessDeletion() {
    var state = HangulComposer.compose("ㄱㅏㄴㅏ")
    assertEquals("가나", state.text)
    state = HangulComposer.reduce(state, CompositionEvent.Backspace)
    assertEquals("간", state.text)
    repeat(10) { state = HangulComposer.reduce(state, CompositionEvent.Backspace) }
    assertEquals("", state.text)
    assertEquals(CompositionPhase.EMPTY, state.phase)
  }

  // Additional Kotlin-side coverage for every automaton branch.

  @Test
  fun committedAndComposingTextSplit() {
    val state = HangulComposer.compose("ㄱㅏㄴㅏ")
    assertEquals("가", state.committedText)
    assertEquals("나", state.composingText)
    assertEquals(CompositionPhase.JUNG, HangulComposer.compose("ㅗ").phase)
    assertEquals("", CompositionState().text)
  }

  @Test
  fun compoundFinalsAndDokkaebiSplit() {
    assertEquals("닭", HangulComposer.compose("ㄷㅏㄹㄱ").text)
    assertEquals("달가", HangulComposer.compose("ㄷㅏㄹㄱㅏ").text)
    assertEquals("닭ㄷ", HangulComposer.compose("ㄷㅏㄹㄱㄷ").text)
    assertEquals("가자", HangulComposer.compose("ㄱㅏㅈㅏ").text)
    assertEquals("값", HangulComposer.compose("ㄱㅏㅂㅅ").text)
    assertEquals("갑사", HangulComposer.compose("ㄱㅏㅂㅅㅏ").text)
  }

  @Test
  fun compoundMedialsWithAndWithoutLeading() {
    assertEquals("과", HangulComposer.compose("ㄱㅗㅏ").text)
    assertEquals("의", HangulComposer.compose("ㅇㅡㅣ").text)
    assertEquals("ㅗㅓ", HangulComposer.compose("ㅗㅓ").text)
    assertEquals("ㅘㅏ", HangulComposer.compose("ㅗㅏㅏ").text)
  }

  @Test
  fun consonantAfterLoneVowelOrNonTrailingConsonant() {
    // Vowel-only buffer followed by consonant starts a new syllable.
    assertEquals("ㅏ가", HangulComposer.compose("ㅏㄱㅏ").text)
    // ㄸ cannot be a final; ㄸ after a CV syllable starts a new syllable.
    assertEquals("가따", HangulComposer.compose("ㄱㅏㄸㅏ").text)
    // Leading-only buffer followed by consonant flushes.
    assertEquals("ㄱㄱ", HangulComposer.compose(listOf('ㄱ', 'ㄱ')).text)
  }

  @Test
  fun literalKeysFlushTheBuffer() {
    val state = HangulComposer.compose("ㄱㅏㄴ1ㅏ")
    assertEquals("간1ㅏ", state.text)
    assertEquals(CompositionPhase.JUNG, state.phase)
  }

  @Test
  fun valueSemantics() {
    val a = HangulComposer.compose("ㄱㅏ")
    val b = HangulComposer.compose("ㄱㅏ")
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertNotEquals<Any>(a, "가")
    assertNotEquals(a, HangulComposer.compose("ㄱㅏㄴ"))
    // Same text, different undo history.
    assertNotEquals(HangulComposer.compose("ㅘ"), HangulComposer.compose("ㅗㅏ"))
    assertTrue(a.toString().contains("가"))
    // Reducing does not mutate the original.
    HangulComposer.reduce(a, CompositionEvent.Backspace)
    assertEquals("가", a.text)
    assertEquals("choJungJong", CompositionPhase.CHO_JUNG_JONG.rawValue)
  }

  @Test
  fun decompositionErrorsAreValues() {
    val error = assertFailsWith<JamoDecompositionError> { JamoDecomposer.keySequence("") }
    assertEquals(JamoDecompositionError.EmptyTarget, error)
    assertFalse(error.message.isNullOrEmpty())
  }
}
