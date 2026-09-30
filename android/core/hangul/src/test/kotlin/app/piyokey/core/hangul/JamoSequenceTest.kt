package app.piyokey.core.hangul

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JamoSequenceTest {
  @Test
  fun decomposerExpandsCompoundJamoAndRejectsUntypeableText() {
    assertEquals("ㅗㅏㄱㅅ 2!".toList(), JamoDecomposer.keySequence("ㅘㄳ 2!"))
    assertTrue(JamoDecomposer.containsHangul("한글 2"))
    assertTrue(JamoDecomposer.containsHangul("ㄱ"))
    assertFalse(JamoDecomposer.containsHangul("123"))
    assertEquals(
      JamoDecompositionError.EmptyTarget,
      assertFailsWith<JamoDecompositionError> { JamoDecomposer.keySequence("") },
    )
    assertEquals(
      JamoDecompositionError.UnsupportedCharacter("A", offset = 0),
      assertFailsWith<JamoDecompositionError> { JamoDecomposer.keySequence("ABC") },
    )
  }

  @Test
  fun judgeCountsMistakeWithoutAdvancingOrPollutingTarget() {
    var state = JamoJudgeState("가")
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

  // Additional Kotlin-side coverage.

  @Test
  fun decomposerHandlesShiftTrailingAndLiterals() {
    assertEquals("ㄲㅜㄹ".toList(), JamoDecomposer.keySequence("꿀"))
    assertEquals("ㄷㅏㄹㄱ".toList(), JamoDecomposer.keySequence("닭"))
    assertEquals("ㅎㅗㅏ".toList(), JamoDecomposer.keySequence("화"))
    assertEquals("ㄱㅅ".toList(), JamoDecomposer.keySequence("ㄳ"))
    assertEquals(listOf('ㅎ', 'ㅣ', '　', '½', '٣', 'Ⅳ'), JamoDecomposer.keySequence("히　½٣Ⅳ"))
    assertEquals(listOf('\t', '\n', '\u0085', ' '), JamoDecomposer.keySequence("\t\n\u0085 "))
    assertEquals("…♡。！？".toList(), JamoDecomposer.keySequence("…♡。！？"))
    // CRLF is one grapheme cluster; its UTF-16 chars pass through.
    assertEquals(listOf('\r', '\n'), JamoDecomposer.keySequence("\r\n"))
  }

  @Test
  fun decomposerReportsGraphemeOffsets() {
    assertEquals(
      JamoDecompositionError.UnsupportedCharacter("😀", offset = 2),
      assertFailsWith<JamoDecompositionError> { JamoDecomposer.keySequence("가 😀") },
    )
    // A combining sequence is one grapheme and is not typeable.
    assertEquals(
      JamoDecompositionError.UnsupportedCharacter("é", offset = 1),
      assertFailsWith<JamoDecompositionError> { JamoDecomposer.keySequence("가é") },
    )
    // A trailing-only jamo that is not a leading consonant or compound final is unsupported.
    assertFailsWith<JamoDecompositionError> { JamoDecomposer.keySequence("ㆍ") }
  }

  @Test
  fun containsHangulEdgeCases() {
    assertTrue(JamoDecomposer.containsHangul("ㅏ"))
    assertFalse(JamoDecomposer.containsHangul("ㄳ"))
    assertFalse(JamoDecomposer.containsHangul(""))
    assertFalse(JamoDecomposer.containsHangul("😀a"))
  }

  @Test
  fun shiftJamo() {
    for (c in "ㄲㄸㅃㅆㅉㅒㅖ") assertTrue(JamoDecomposer.isShiftJamo(c))
    assertFalse(JamoDecomposer.isShiftJamo('ㄱ'))
  }

  @Test
  fun judgeStateProperties() {
    val initial = JamoJudgeState("가 나")
    assertEquals("가 나", initial.target)
    assertEquals("ㄱㅏ ㄴㅏ".toList(), initial.expectedSequence)
    assertEquals('ㄱ', initial.expectedNext)
    assertEquals(0.0, initial.progress)
    assertFalse(initial.isComplete)
    val advanced = JamoSequenceJudge.evaluate('ㄱ', initial).state
    assertEquals(0.2, advanced.progress, 1e-9)
    assertEquals(JamoJudgeState("가 나"), initial)
    assertEquals(JamoJudgeState("가 나").hashCode(), initial.hashCode())
    assertNotEquals(initial, advanced)
    assertNotEquals<Any>(initial, "가 나")
    assertNotEquals(JamoSequenceJudge.evaluate('ㄴ', initial).state, initial)
    assertNotEquals(JamoJudgeState("가"), JamoJudgeState("나"))
    assertTrue(initial.toString().contains("가 나"))

    var done = initial
    for (c in initial.expectedSequence) done = JamoSequenceJudge.evaluate(c, done).state
    assertNull(done.expectedNext)
    assertTrue(done.isComplete)
    assertFailsWith<JamoDecompositionError> { JamoJudgeState("abc") }
  }
}
