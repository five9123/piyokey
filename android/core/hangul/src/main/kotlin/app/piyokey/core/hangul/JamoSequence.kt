package app.piyokey.core.hangul

/** Errors thrown by [JamoDecomposer.keySequence] and [JamoJudgeState]. */
sealed class JamoDecompositionError(message: String) : Exception(message) {
  /** The target text was empty. */
  data object EmptyTarget : JamoDecompositionError("Target text is empty")

  /** [character] (one grapheme cluster) at grapheme [offset] cannot be typed on two-beolsik. */
  data class UnsupportedCharacter(val character: String, val offset: Int) :
    JamoDecompositionError("Unsupported character '$character' at offset $offset")
}

/** Converts composed Hangul into two-beolsik key-jamo sequences. Port of `JamoDecomposer`. */
object JamoDecomposer {
  /**
   * Converts composed Hangul into the exact two-beolsik key-jamo sequence.
   * Compound vowels/finals expand to their two keys (`ㅘ` -> `ㅗㅏ`, `ㄳ` -> `ㄱㅅ`); shifted
   * jamo (`ㄲ`, `ㅒ`...) stay one element. Allowed literals (whitespace, numerals, selected
   * punctuation) pass through.
   *
   * @throws JamoDecompositionError.EmptyTarget when [target] is empty.
   * @throws JamoDecompositionError.UnsupportedCharacter for untypeable text.
   */
  @Throws(JamoDecompositionError::class)
  fun keySequence(target: String): List<Char> {
    if (target.isEmpty()) throw JamoDecompositionError.EmptyTarget

    val result = ArrayList<Char>()
    for ((offset, character) in target.graphemeClusters().withIndex()) {
      val scalar = character.onlyScalar()
      val single = character.singleChar()
      if (scalar != null && HangulTables.isSyllable(scalar)) {
        val syllableIndex = scalar - HangulTables.SYLLABLE_FIRST
        result += HangulTables.leading[syllableIndex / (21 * 28)]
        appendExpanded(HangulTables.medial[(syllableIndex % (21 * 28)) / 28], HangulTables.splitMedial, result)
        HangulTables.trailing[syllableIndex % 28]?.let {
          appendExpanded(it, HangulTables.splitTrailing, result)
        }
      } else if (single != null && HangulTables.leadingIndex.containsKey(single)) {
        result += single
      } else if (single != null && HangulTables.medialIndex.containsKey(single)) {
        appendExpanded(single, HangulTables.splitMedial, result)
      } else if (single != null && HangulTables.splitTrailing.containsKey(single)) {
        appendExpanded(single, HangulTables.splitTrailing, result)
      } else if (isAllowedLiteral(character)) {
        // A literal grapheme is one Swift Character; multi-UTF-16 literals (e.g. CRLF) expand per char.
        result.addAll(character.toList())
      } else {
        throw JamoDecompositionError.UnsupportedCharacter(character, offset)
      }
    }
    return result
  }

  /** Whether [character] is typed with Shift on two-beolsik (`ㄲㄸㅃㅆㅉㅒㅖ`). */
  fun isShiftJamo(character: Char): Boolean = character in HangulTables.shiftedJamo

  /** Whether [text] contains a leading consonant, a vowel, or a precomposed syllable. */
  fun containsHangul(text: String): Boolean =
    text.graphemeClusters().any { character ->
      val single = character.singleChar()
      if (single != null &&
        (HangulTables.leadingIndex.containsKey(single) || HangulTables.medialIndex.containsKey(single))
      ) {
        true
      } else {
        character.onlyScalar()?.let(HangulTables::isSyllable) ?: false
      }
    }

  private fun appendExpanded(jamo: Char, splitTable: Map<Char, JamoPair>, result: MutableList<Char>) {
    val split = splitTable[jamo]
    if (split != null) {
      result += split.first
      result += split.second
    } else {
      result += jamo
    }
  }

  private fun isAllowedLiteral(character: String): Boolean {
    val single = character.singleChar()
    if (single != null && single in HangulTables.allowedLiteralPunctuation) return true
    return character.codePoints().allMatch { isUnicodeWhitespace(it) || isNumeric(it) }
  }

  /** Unicode `White_Space` property (Swift `isWhitespace`). */
  private fun isUnicodeWhitespace(codePoint: Int): Boolean =
    codePoint in 0x09..0x0D || codePoint == 0x85 || Character.isSpaceChar(codePoint)

  /** Approximates Swift `numericType != nil` with the Unicode numeric general categories. */
  private fun isNumeric(codePoint: Int): Boolean =
    when (Character.getType(codePoint)) {
      Character.DECIMAL_DIGIT_NUMBER.toInt(),
      Character.LETTER_NUMBER.toInt(),
      Character.OTHER_NUMBER.toInt() -> true
      else -> false
    }
}

/** Progress of one target through the jamo sequence judge. Immutable; see [JamoSequenceJudge.evaluate]. */
class JamoJudgeState private constructor(
  /** The composed target text. */
  val target: String,
  /** Two-beolsik key jamo expected for [target]. */
  val expectedSequence: List<Char>,
  /** Index of the next expected jamo. */
  val currentIndex: Int,
  /** Number of accepted jamo. */
  val correctCount: Int,
  /** Number of rejected jamo. */
  val errorCount: Int,
) {
  /**
   * Starts judging [target].
   * @throws JamoDecompositionError when the target is empty or untypeable.
   */
  @Throws(JamoDecompositionError::class)
  constructor(target: String) : this(target, JamoDecomposer.keySequence(target), 0, 0, 0)

  /** The jamo the user must type next, or null when complete. */
  val expectedNext: Char? get() = expectedSequence.getOrNull(currentIndex)

  /** Whether every expected jamo has been accepted. */
  val isComplete: Boolean get() = currentIndex == expectedSequence.size

  /** Fraction of the sequence accepted, 0.0..1.0. */
  val progress: Double
    get() = if (expectedSequence.isEmpty()) 1.0 else currentIndex.toDouble() / expectedSequence.size

  internal fun recordingCorrect() =
    JamoJudgeState(target, expectedSequence, currentIndex + 1, correctCount + 1, errorCount)

  internal fun recordingError() =
    JamoJudgeState(target, expectedSequence, currentIndex, correctCount, errorCount + 1)

  override fun equals(other: Any?): Boolean =
    other is JamoJudgeState &&
      target == other.target &&
      expectedSequence == other.expectedSequence &&
      currentIndex == other.currentIndex &&
      correctCount == other.correctCount &&
      errorCount == other.errorCount

  override fun hashCode(): Int =
    listOf(target, expectedSequence, currentIndex, correctCount, errorCount).hashCode()

  override fun toString(): String =
    "JamoJudgeState(target=$target, currentIndex=$currentIndex, correctCount=$correctCount, errorCount=$errorCount)"
}

/** Result of judging one jamo. */
sealed interface JamoJudgeResult {
  /** Input matched; [completed] is true when it was the final jamo. */
  data class Correct(val completed: Boolean) : JamoJudgeResult

  /** Input did not match; the index did not advance and one error was recorded. */
  data class Incorrect(val expected: Char) : JamoJudgeResult

  /** The target was already complete; state is unchanged. */
  data object AlreadyComplete : JamoJudgeResult
}

/** New judge state plus the result of one evaluation (Swift tuple `(state:, result:)`). */
data class JamoJudgeEvaluation(val state: JamoJudgeState, val result: JamoJudgeResult)

/** Jamo-sequence judge for the built-in keyboard. */
object JamoSequenceJudge {
  /** Compares one logical jamo event. Shift+jamo arrives as its resulting jamo and counts once. */
  fun evaluate(input: Char, state: JamoJudgeState): JamoJudgeEvaluation {
    val expected = state.expectedNext ?: return JamoJudgeEvaluation(state, JamoJudgeResult.AlreadyComplete)
    if (input != expected) {
      return JamoJudgeEvaluation(state.recordingError(), JamoJudgeResult.Incorrect(expected))
    }
    val next = state.recordingCorrect()
    return JamoJudgeEvaluation(next, JamoJudgeResult.Correct(next.isComplete))
  }
}
