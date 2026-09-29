package app.piyokey.core.hangul

import app.piyokey.core.hangul.Korean10KeyKey.BIEUP
import app.piyokey.core.hangul.Korean10KeyKey.DIGEUT
import app.piyokey.core.hangul.Korean10KeyKey.DOT
import app.piyokey.core.hangul.Korean10KeyKey.GIYEOK
import app.piyokey.core.hangul.Korean10KeyKey.HORIZONTAL
import app.piyokey.core.hangul.Korean10KeyKey.IEUNG
import app.piyokey.core.hangul.Korean10KeyKey.JIEUT
import app.piyokey.core.hangul.Korean10KeyKey.NEXT
import app.piyokey.core.hangul.Korean10KeyKey.NIEUN
import app.piyokey.core.hangul.Korean10KeyKey.SIOT
import app.piyokey.core.hangul.Korean10KeyKey.SPACE
import app.piyokey.core.hangul.Korean10KeyKey.VERTICAL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Ports `HangulEngineTests` 10-key recipe tests and the iOS app's `Korean10KeyInterpreter` /
 * flick tests (`PracticeSessionViewModelTests`). The app view model is replaced by [Session],
 * a minimal composer + jamo judge harness.
 */
class Korean10KeyTest {
  private class Session(target: String) {
    private var judge = JamoJudgeState(target)
    private var composition = CompositionState()
    var mistakeCount = 0
      private set

    val nextExpectedKey: Char? get() = judge.expectedNext
    val completedJamoCount: Int get() = judge.correctCount
    val enteredText: String get() = composition.text
    val isComplete: Boolean get() = judge.isComplete

    fun input(jamo: Char) {
      val evaluation = JamoSequenceJudge.evaluate(jamo, judge)
      judge = evaluation.state
      when (evaluation.result) {
        is JamoJudgeResult.Correct -> composition = HangulComposer.reduce(composition, CompositionEvent.Key(jamo))
        is JamoJudgeResult.Incorrect -> mistakeCount += 1
        JamoJudgeResult.AlreadyComplete -> Unit
      }
    }

    fun recordConfirmedMistake() {
      mistakeCount += 1
    }
  }

  /** Mutable test wrapper so assertions read like the Swift `mutating` API. */
  private class Driver(var interpreter: Korean10KeyInterpreter = Korean10KeyInterpreter()) {
    fun input(key: Korean10KeyKey, expecting: Char?) =
      interpreter.input(key, expecting).also { interpreter = it.interpreter }.interpretation

    fun inputCompletedJamo(jamo: Char?, expecting: Char?) =
      interpreter.inputCompletedJamo(jamo, expecting).also { interpreter = it.interpreter }.interpretation

    fun backspace() = interpreter.backspace().also { interpreter = it.interpreter }.result

    fun nextKey(expected: Char?) = interpreter.nextKey(expected)
  }

  private fun pending(display: String) = Korean10KeyInterpretation.Pending(display)
  private fun committed(jamo: Char) = Korean10KeyInterpretation.Committed(jamo)
  private fun incorrect(expected: Char?) = Korean10KeyInterpretation.Incorrect(expected)

  // region HangulEngineTests

  @Test
  fun recipesExposeOnlyTargetReachableIntermediateVowels() {
    assertEquals(listOf(DOT, HORIZONTAL, VERTICAL, DOT, VERTICAL), Korean10KeyRecipe.recipe('ㅙ'))
    assertEquals('ㅝ', Korean10KeyRecipe.jamoForExactRecipe(listOf(HORIZONTAL, DOT, DOT, VERTICAL)))
    assertTrue(Korean10KeyRecipe.isReachableIntermediateVowel('ㅚ', toward = 'ㅙ'))
    assertTrue(Korean10KeyRecipe.isReachableIntermediateVowel('ㅘ', toward = 'ㅙ'))
    assertTrue(Korean10KeyRecipe.isReachableIntermediateVowel('ㅝ', toward = 'ㅞ'))
    assertTrue(Korean10KeyRecipe.isReachableIntermediateVowel('ㅏ', toward = 'ㅐ'))
    assertFalse(Korean10KeyRecipe.isReachableIntermediateVowel('ㅟ', toward = 'ㅙ'))
    assertFalse(Korean10KeyRecipe.isReachableIntermediateVowel('ㅙ', toward = 'ㅙ'))
    assertTrue(Korean10KeyRecipe.isReachableIntermediateConsonant('ㄴ', toward = 'ㄹ'))
    assertTrue(Korean10KeyRecipe.isReachableIntermediateConsonant('ㅅ', toward = 'ㅎ'))
    assertFalse(Korean10KeyRecipe.isReachableIntermediateConsonant('ㅈ', toward = 'ㅎ'))
    assertFalse(Korean10KeyRecipe.isReachableIntermediateConsonant('ㅎ', toward = 'ㅎ'))
    // Domain checks: a vowel is never a reachable consonant and vice versa.
    assertFalse(Korean10KeyRecipe.isReachableIntermediateConsonant('ㅏ', toward = 'ㅎ'))
    assertFalse(Korean10KeyRecipe.isReachableIntermediateConsonant('ㄴ', toward = 'ㅏ'))
    assertFalse(Korean10KeyRecipe.isReachableIntermediateVowel('ㄴ', toward = 'ㅐ'))
    assertNull(Korean10KeyRecipe.jamoForExactRecipe(listOf(NEXT)))
  }

  // endregion

  // region PracticeSessionViewModelTests (10-key)

  @Test
  fun recipesCoverEveryConsonantVowelAndSpace() {
    val expected: Map<Char, List<Korean10KeyKey>> = mapOf(
      'ㄱ' to listOf(GIYEOK), 'ㅋ' to listOf(GIYEOK, GIYEOK), 'ㄲ' to listOf(GIYEOK, GIYEOK, GIYEOK),
      'ㄴ' to listOf(NIEUN), 'ㄹ' to listOf(NIEUN, NIEUN),
      'ㄷ' to listOf(DIGEUT), 'ㅌ' to listOf(DIGEUT, DIGEUT), 'ㄸ' to listOf(DIGEUT, DIGEUT, DIGEUT),
      'ㅂ' to listOf(BIEUP), 'ㅍ' to listOf(BIEUP, BIEUP), 'ㅃ' to listOf(BIEUP, BIEUP, BIEUP),
      'ㅅ' to listOf(SIOT), 'ㅎ' to listOf(SIOT, SIOT), 'ㅆ' to listOf(SIOT, SIOT, SIOT),
      'ㅈ' to listOf(JIEUT), 'ㅊ' to listOf(JIEUT, JIEUT), 'ㅉ' to listOf(JIEUT, JIEUT, JIEUT),
      'ㅇ' to listOf(IEUNG), 'ㅁ' to listOf(IEUNG, IEUNG),
      'ㅣ' to listOf(VERTICAL), 'ㅡ' to listOf(HORIZONTAL),
      'ㅏ' to listOf(VERTICAL, DOT), 'ㅑ' to listOf(VERTICAL, DOT, DOT),
      'ㅓ' to listOf(DOT, VERTICAL), 'ㅕ' to listOf(DOT, DOT, VERTICAL),
      'ㅗ' to listOf(DOT, HORIZONTAL), 'ㅛ' to listOf(DOT, DOT, HORIZONTAL),
      'ㅜ' to listOf(HORIZONTAL, DOT), 'ㅠ' to listOf(HORIZONTAL, DOT, DOT),
      'ㅐ' to listOf(VERTICAL, DOT, VERTICAL),
      'ㅒ' to listOf(VERTICAL, DOT, DOT, VERTICAL),
      'ㅔ' to listOf(DOT, VERTICAL, VERTICAL),
      'ㅖ' to listOf(DOT, DOT, VERTICAL, VERTICAL),
      'ㅘ' to listOf(DOT, HORIZONTAL, VERTICAL, DOT),
      'ㅙ' to listOf(DOT, HORIZONTAL, VERTICAL, DOT, VERTICAL),
      'ㅚ' to listOf(DOT, HORIZONTAL, VERTICAL),
      'ㅝ' to listOf(HORIZONTAL, DOT, DOT, VERTICAL),
      'ㅞ' to listOf(HORIZONTAL, DOT, DOT, VERTICAL, VERTICAL),
      'ㅟ' to listOf(HORIZONTAL, DOT, VERTICAL),
      'ㅢ' to listOf(HORIZONTAL, VERTICAL),
      ' ' to listOf(SPACE),
    )

    assertEquals(41, expected.size)
    for ((jamo, recipe) in expected) {
      assertEquals(recipe, Korean10KeyInterpreter.recipe(jamo), "recipe for $jamo")
    }
    assertNull(Korean10KeyInterpreter.recipe('A'))
  }

  @Test
  fun flickMappingCoversVowelsAndConsonantAlternatives() {
    val l = Korean10KeyFlickDirection.LEFT
    val r = Korean10KeyFlickDirection.RIGHT
    val u = Korean10KeyFlickDirection.UP
    val d = Korean10KeyFlickDirection.DOWN
    val expected: List<Triple<Korean10KeyKey, Korean10KeyFlickDirection, Char?>> = listOf(
      Triple(VERTICAL, l, 'ㅓ'), Triple(VERTICAL, r, 'ㅏ'), Triple(VERTICAL, u, 'ㅕ'), Triple(VERTICAL, d, 'ㅑ'),
      Triple(DOT, l, 'ㅓ'), Triple(DOT, r, 'ㅏ'), Triple(DOT, u, 'ㅗ'), Triple(DOT, d, 'ㅜ'),
      Triple(HORIZONTAL, l, 'ㅠ'), Triple(HORIZONTAL, r, 'ㅛ'), Triple(HORIZONTAL, u, 'ㅗ'), Triple(HORIZONTAL, d, 'ㅜ'),
      Triple(GIYEOK, l, 'ㄱ'), Triple(GIYEOK, r, 'ㅋ'), Triple(GIYEOK, d, 'ㄲ'),
      Triple(NIEUN, l, 'ㄴ'), Triple(NIEUN, r, 'ㄹ'), Triple(NIEUN, d, null),
      Triple(DIGEUT, l, 'ㄷ'), Triple(DIGEUT, r, 'ㅌ'), Triple(DIGEUT, d, 'ㄸ'),
      Triple(BIEUP, l, 'ㅂ'), Triple(BIEUP, r, 'ㅍ'), Triple(BIEUP, d, 'ㅃ'),
      Triple(SIOT, l, 'ㅅ'), Triple(SIOT, r, 'ㅎ'), Triple(SIOT, d, 'ㅆ'),
      Triple(JIEUT, l, 'ㅈ'), Triple(JIEUT, r, 'ㅊ'), Triple(JIEUT, d, 'ㅉ'),
      Triple(IEUNG, l, 'ㅇ'), Triple(IEUNG, r, 'ㅁ'), Triple(IEUNG, d, null),
    )
    for ((key, direction, jamo) in expected) {
      assertEquals(jamo, Korean10KeyFlickMapping.completedJamo(key, direction), "$key $direction")
    }
    for (key in Korean10KeyKey.entries.filter { it.supportsFlick }) {
      if (key != VERTICAL && key != DOT && key != HORIZONTAL) {
        assertNull(Korean10KeyFlickMapping.completedJamo(key, u))
      }
    }
    for (direction in Korean10KeyFlickDirection.entries) {
      assertNull(Korean10KeyFlickMapping.completedJamo(NEXT, direction))
      assertNull(Korean10KeyFlickMapping.completedJamo(SPACE, direction))
    }
  }

  @Test
  fun flickGestureThresholdSeparatesTapFlickAndInvalidMotion() {
    val resolver = Korean10KeyFlickGestureResolver
    assertEquals(Korean10KeyTouchInterpretation.Tap, resolver.interpretation(23.9, 0.0, 0.1))
    assertEquals(
      Korean10KeyTouchInterpretation.Tap,
      resolver.interpretation(0.0, 0.0, 1.0),
      "A stationary long press must preserve the existing tap behavior",
    )
    assertEquals(
      Korean10KeyTouchInterpretation.Flick(Korean10KeyFlickDirection.LEFT),
      resolver.interpretation(-30.0, 2.0, 0.2),
    )
    assertEquals(
      Korean10KeyTouchInterpretation.Flick(Korean10KeyFlickDirection.UP),
      resolver.interpretation(2.0, -30.0, 0.2),
    )
    assertEquals(Korean10KeyTouchInterpretation.InvalidFlick, resolver.interpretation(30.0, 30.0, 0.2))
    assertEquals(Korean10KeyTouchInterpretation.InvalidFlick, resolver.interpretation(30.0, 0.0, 0.451))
    // Additional: remaining directions.
    assertEquals(
      Korean10KeyTouchInterpretation.Flick(Korean10KeyFlickDirection.RIGHT),
      resolver.interpretation(30.0, 0.0, 0.45),
    )
    assertEquals(
      Korean10KeyTouchInterpretation.Flick(Korean10KeyFlickDirection.DOWN),
      resolver.interpretation(0.0, 24.0, 0.2),
    )
  }

  @Test
  fun completedFlickUsesOnlyGoldenRecipePrefixes() {
    val flickVowels = "ㅏㅑㅓㅕㅗㅛㅜㅠ".toList()
    val vowels = "ㅣㅡㅏㅑㅓㅕㅗㅛㅜㅠㅐㅒㅔㅖㅘㅙㅚㅝㅞㅟㅢ".toList()

    for (expected in vowels) {
      val interpreter = Driver()
      val recipe = assertNotNull(Korean10KeyInterpreter.recipe(expected))
      val shortcut = flickVowels
        .mapNotNull { jamo ->
          val candidate = Korean10KeyInterpreter.recipe(jamo)
          if (candidate != null && recipe.size >= candidate.size && recipe.subList(0, candidate.size) == candidate) {
            jamo to candidate
          } else {
            null
          }
        }
        .maxByOrNull { it.second.size }

      var consumed = 0
      if (shortcut != null) {
        val result = interpreter.inputCompletedJamo(shortcut.first, expecting = expected)
        consumed = shortcut.second.size
        if (consumed == recipe.size) {
          assertEquals(committed(expected), result, expected.toString())
          continue
        }
        if (result !is Korean10KeyInterpretation.Pending) {
          fail("Expected pending flick prefix for $expected, got $result")
        }
      }

      for ((index, key) in recipe.drop(consumed).withIndex()) {
        val result = interpreter.input(key, expecting = expected)
        if (index == recipe.size - consumed - 1) {
          assertEquals(committed(expected), result, expected.toString())
        }
      }
    }

    val invalidSplice = Driver()
    assertEquals(pending("ㅣ"), invalidSplice.input(VERTICAL, expecting = 'ㅒ'))
    assertEquals(incorrect('ㅒ'), invalidSplice.inputCompletedJamo('ㅓ', expecting = 'ㅒ'))
    assertEquals(VERTICAL, invalidSplice.nextKey('ㅒ'))
  }

  @Test
  fun completedConsonantFlickCanForceSameGroupBoundary() {
    val interpreter = Driver()

    assertEquals(committed('ㄴ'), interpreter.inputCompletedJamo('ㄴ', expecting = 'ㄴ'))
    assertEquals(NEXT, interpreter.nextKey('ㄴ'))
    assertEquals(committed('ㄴ'), interpreter.inputCompletedJamo('ㄴ', expecting = 'ㄴ'))
    assertEquals(incorrect('ㅏ'), interpreter.inputCompletedJamo(null, expecting = 'ㅏ'))
  }

  @Test
  fun pendingMatchingCompletedFlickCountsOneMistakeWithoutAdvancing() {
    val model = Session("가")
    val interpreter = Driver()

    assertEquals(committed('ㄱ'), interpreter.input(GIYEOK, expecting = model.nextExpectedKey))
    model.input('ㄱ')
    assertEquals('ㅏ', model.nextExpectedKey)

    assertEquals(pending("ㅣ"), interpreter.input(VERTICAL, expecting = model.nextExpectedKey))
    assertEquals(incorrect('ㅏ'), interpreter.inputCompletedJamo('ㅏ', expecting = model.nextExpectedKey))
    model.recordConfirmedMistake()

    assertEquals(1, model.mistakeCount)
    assertEquals(1, model.completedJamoCount)
    assertEquals('ㅏ', model.nextExpectedKey)
    assertEquals("ㄱ", model.enteredText)
    assertEquals(VERTICAL, interpreter.nextKey(model.nextExpectedKey))
  }

  @Test
  fun emitsOnlyCompletedJamoIntoSharedJudge() {
    for (target in listOf("가나", "꽤", "뼈", "휘", "의자", "언니", "띄어 쓰기", "외국")) {
      val model = Session(target)
      val interpreter = Driver()
      for (jamo in JamoDecomposer.keySequence(target)) {
        val recipe = assertNotNull(Korean10KeyInterpreter.recipe(jamo))
        if (interpreter.nextKey(model.nextExpectedKey) == NEXT) {
          assertEquals(
            Korean10KeyInterpretation.SeparatorAccepted,
            interpreter.input(NEXT, expecting = model.nextExpectedKey),
          )
        }
        for ((index, key) in recipe.withIndex()) {
          val result = interpreter.input(key, expecting = model.nextExpectedKey)
          if (index == recipe.size - 1) {
            val emitted = (result as? Korean10KeyInterpretation.Committed)?.jamo
              ?: fail("Expected committed $jamo, got $result")
            assertEquals(jamo, emitted)
            model.input(emitted)
          } else if (result is Korean10KeyInterpretation.Pending) {
            assertEquals(jamo, model.nextExpectedKey)
          } else {
            fail("Recipe for $jamo committed too early")
          }
        }
      }
      assertTrue(model.isComplete, target)
      assertEquals(target, model.enteredText)
      assertEquals(0, model.mistakeCount)
    }
  }

  @Test
  fun backspaceRewindsPendingStrokeBeforeHangul() {
    val interpreter = Driver()

    assertEquals(pending("ㅣ"), interpreter.input(VERTICAL, expecting = 'ㅑ'))
    assertEquals(pending("ㅏ"), interpreter.input(DOT, expecting = 'ㅑ'))
    assertEquals(Korean10KeyBackspaceResult.PendingChanged("ㅣ"), interpreter.backspace())
    assertEquals(pending("ㅏ"), interpreter.input(DOT, expecting = 'ㅑ'))
    assertEquals(committed('ㅑ'), interpreter.input(DOT, expecting = 'ㅑ'))
    assertEquals(Korean10KeyBackspaceResult.ForwardToHangulEngine, interpreter.backspace())
  }

  @Test
  fun requiresAdvanceBetweenConsecutiveConsonantsInSameGroup() {
    val model = Session("언니")
    val interpreter = Driver()

    assertEquals(committed('ㅇ'), interpreter.input(IEUNG, expecting = model.nextExpectedKey))
    model.input('ㅇ')
    assertEquals(pending("ㆍ"), interpreter.input(DOT, expecting = model.nextExpectedKey))
    assertEquals(committed('ㅓ'), interpreter.input(VERTICAL, expecting = model.nextExpectedKey))
    model.input('ㅓ')
    assertEquals(committed('ㄴ'), interpreter.input(NIEUN, expecting = model.nextExpectedKey))
    model.input('ㄴ')

    assertEquals('ㄴ', model.nextExpectedKey)
    assertEquals(NEXT, interpreter.nextKey(model.nextExpectedKey))
    assertEquals(incorrect('ㄴ'), interpreter.input(NIEUN, expecting = model.nextExpectedKey))
    model.recordConfirmedMistake()
    assertEquals(1, model.mistakeCount)
    assertEquals(3, model.completedJamoCount)
    assertEquals('ㄴ', model.nextExpectedKey)
    assertEquals(NEXT, interpreter.nextKey(model.nextExpectedKey))
    assertEquals(Korean10KeyInterpretation.SeparatorAccepted, interpreter.input(NEXT, expecting = model.nextExpectedKey))
    assertEquals(NIEUN, interpreter.nextKey(model.nextExpectedKey))
    assertEquals(committed('ㄴ'), interpreter.input(NIEUN, expecting = model.nextExpectedKey))
  }

  @Test
  fun wrongGroupCountsOneMistakeAndResetsPendingRecipe() {
    val model = Session("카")
    val interpreter = Driver()

    assertEquals(pending("ㄱ"), interpreter.input(GIYEOK, expecting = model.nextExpectedKey))
    assertEquals(incorrect('ㅋ'), interpreter.input(NIEUN, expecting = model.nextExpectedKey))
    model.input('ㄴ')
    assertEquals(1, model.mistakeCount)
    assertEquals(GIYEOK, interpreter.nextKey(model.nextExpectedKey))
  }

  // endregion

  // region Additional Kotlin-side coverage

  @Test
  fun unexpectedNextAndMissingExpectationAreIncorrect() {
    val interpreter = Driver()
    assertEquals(incorrect('ㄱ'), interpreter.input(NEXT, expecting = 'ㄱ'))
    assertEquals(incorrect(null), interpreter.input(GIYEOK, expecting = null))
    assertEquals(incorrect('A'), interpreter.input(GIYEOK, expecting = 'A'))
    assertEquals(incorrect(null), interpreter.inputCompletedJamo('ㄱ', expecting = null))
    assertEquals(incorrect('ㄱ'), interpreter.inputCompletedJamo('A', expecting = 'ㄱ'))
    // A flick that is not a prefix of the expected recipe.
    assertEquals(incorrect('ㄱ'), interpreter.inputCompletedJamo('ㄴ', expecting = 'ㄱ'))
    assertNull(interpreter.nextKey(null))
    assertNull(interpreter.nextKey('A'))
  }

  @Test
  fun pendingFlickPrefixAndPreview() {
    val interpreter = Driver()
    assertEquals(pending("ㅏ"), interpreter.inputCompletedJamo('ㅏ', expecting = 'ㅐ'))
    assertEquals("ㅏ", interpreter.interpreter.pendingDisplay)
    assertEquals(listOf(VERTICAL, DOT), interpreter.interpreter.pendingKeys)
    assertEquals(VERTICAL, interpreter.nextKey('ㅐ'))
    // Pending strokes that do not prefix the new expectation yield no guide key.
    assertNull(interpreter.nextKey('ㄱ'))
    assertEquals(committed('ㅐ'), interpreter.input(VERTICAL, expecting = 'ㅐ'))
    assertNull(interpreter.interpreter.pendingDisplay)
  }

  @Test
  fun rawStrokePreviewUsesKeycapLabels() {
    val interpreter = Driver()
    assertEquals(pending("ㆍ"), interpreter.input(DOT, expecting = 'ㅕ'))
    assertEquals(pending("ㆍㆍ"), interpreter.input(DOT, expecting = 'ㅕ'))
    assertEquals(Korean10KeyBackspaceResult.PendingChanged("ㆍ"), interpreter.backspace())
    assertEquals(Korean10KeyBackspaceResult.PendingChanged(null), interpreter.backspace())
  }

  @Test
  fun separatorIsClearedByBackspaceAndReset() {
    val interpreter = Driver()
    assertEquals(committed('ㄱ'), interpreter.input(GIYEOK, expecting = 'ㄱ'))
    assertEquals(NEXT, interpreter.nextKey('ㄱ'))
    // Vowel after a grouped consonant needs no separator.
    assertEquals(VERTICAL, interpreter.nextKey('ㅏ'))
    assertEquals(Korean10KeyBackspaceResult.ForwardToHangulEngine, interpreter.backspace())
    assertEquals(GIYEOK, interpreter.nextKey('ㄱ'))

    assertEquals(committed('ㄱ'), interpreter.input(GIYEOK, expecting = 'ㄱ'))
    val fresh = interpreter.interpreter.reset()
    assertEquals(Korean10KeyInterpreter(), fresh)
    assertEquals(GIYEOK, fresh.nextKey('ㄱ'))
  }

  @Test
  fun vowelCommitDoesNotRequireSeparator() {
    val interpreter = Driver()
    assertEquals(committed('ㅣ'), interpreter.input(VERTICAL, expecting = 'ㅣ'))
    assertEquals(VERTICAL, interpreter.nextKey('ㅣ'))
    assertEquals(committed('ㅣ'), interpreter.inputCompletedJamo('ㅣ', expecting = 'ㅣ'))
    assertEquals(committed(' '), interpreter.input(SPACE, expecting = ' '))
  }

  @Test
  fun interpreterValueSemantics() {
    val a = Korean10KeyInterpreter()
    val b = a.input(GIYEOK, 'ㅋ').interpreter
    assertNotEquals(a, b)
    assertEquals(b, Korean10KeyInterpreter().input(GIYEOK, 'ㅋ').interpreter)
    assertEquals(b.hashCode(), Korean10KeyInterpreter().input(GIYEOK, 'ㅋ').interpreter.hashCode())
    assertNotEquals<Any>(a, "x")
    assertTrue(b.toString().contains("GIYEOK"))
    // Different hidden separator state.
    val committedG = a.input(GIYEOK, 'ㄱ').interpreter
    assertNotEquals(a, committedG)
    assertNotEquals(committedG, committedG.input(NEXT, 'ㄱ').interpreter)
    // Original is unchanged.
    assertTrue(a.pendingKeys.isEmpty())
    assertIs<Korean10KeyInterpretation.Pending>(a.input(GIYEOK, 'ㅋ').interpretation)
  }

  @Test
  fun keyMetadata() {
    val labels = Korean10KeyKey.entries.associateWith { it.displayText }
    assertEquals(
      listOf("ㅣ", "ㆍ", "ㅡ", "ㄱㅋ", "ㄴㄹ", "ㄷㅌ", "ㅂㅍ", "ㅅㅎ", "ㅈㅊ", "ㅇㅁ", "→", " "),
      labels.values.toList(),
    )
    assertEquals(
      Korean10KeyKey.entries - NEXT - SPACE,
      Korean10KeyKey.entries.filter { it.supportsFlick },
    )
    assertEquals("vertical", VERTICAL.rawValue)
    assertEquals("next", NEXT.rawValue)
  }

  // endregion
}
