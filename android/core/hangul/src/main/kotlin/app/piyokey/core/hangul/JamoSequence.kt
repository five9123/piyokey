package app.piyokey.core.hangul

sealed class JamoDecompositionException(message: String) : IllegalArgumentException(message) {
  data object EmptyTarget : JamoDecompositionException("Target must not be empty")

  data class UnsupportedCharacter(
    val character: Char,
    val offset: Int,
  ) : JamoDecompositionException("Unsupported character at $offset: $character")
}

object JamoDecomposer {
  /** Converts composed Hangul into the exact two-beolsik key-jamo sequence. */
  fun keySequenceFor(target: String): List<Char> {
    if (target.isEmpty()) throw JamoDecompositionException.EmptyTarget

    return buildList {
      var offset = 0
      while (offset < target.length) {
        val character = target[offset]
        if (
          character.isHighSurrogate() &&
          offset + 1 < target.length &&
          target[offset + 1].isLowSurrogate()
        ) {
          val lowSurrogate = target[offset + 1]
          val codePoint = Character.toCodePoint(character, lowSurrogate)
          if (isAllowedSupplementaryLiteral(codePoint)) {
            add(character)
            add(lowSurrogate)
            offset += 2
            continue
          }
        }
        when {
          character.code in 0xAC00..0xD7A3 -> {
            val syllableIndex = character.code - 0xAC00
            val leading = HangulTables.leading[syllableIndex / (21 * 28)]
            val medial = HangulTables.medial[(syllableIndex % (21 * 28)) / 28]
            val trailing = HangulTables.trailing[syllableIndex % 28]
            add(leading)
            appendExpanded(medial, HangulTables.splitMedial)
            trailing?.let { appendExpanded(it, HangulTables.splitTrailing) }
          }
          character in HangulTables.leadingIndex -> add(character)
          character in HangulTables.medialIndex -> {
            appendExpanded(character, HangulTables.splitMedial)
          }
          character in HangulTables.splitTrailing -> {
            val split = HangulTables.splitTrailing.getValue(character)
            add(split.first)
            add(split.second)
          }
          isAllowedLiteral(character) -> add(character)
          else -> throw JamoDecompositionException.UnsupportedCharacter(character, offset)
        }
        offset += 1
      }
    }
  }

  fun isShiftJamo(character: Char): Boolean = character in HangulTables.shiftedJamo

  fun containsHangul(text: String): Boolean = text.any { character ->
    character in HangulTables.leadingIndex ||
      character in HangulTables.medialIndex ||
      character.code in 0xAC00..0xD7A3
  }

  private fun MutableList<Char>.appendExpanded(
    jamo: Char,
    splitTable: Map<Char, JamoPair>,
  ) {
    val split = splitTable[jamo]
    if (split == null) {
      add(jamo)
    } else {
      add(split.first)
      add(split.second)
    }
  }

  private fun isAllowedLiteral(character: Char): Boolean {
    if (character in HangulTables.allowedLiteralPunctuation) return true
    return character.isWhitespace() || when (Character.getType(character)) {
      Character.DECIMAL_DIGIT_NUMBER.toInt(),
      Character.LETTER_NUMBER.toInt(),
      Character.OTHER_NUMBER.toInt(),
      -> true
      else -> false
    }
  }

  private fun isAllowedSupplementaryLiteral(codePoint: Int): Boolean =
    Character.isWhitespace(codePoint) || when (Character.getType(codePoint)) {
      Character.DECIMAL_DIGIT_NUMBER.toInt(),
      Character.LETTER_NUMBER.toInt(),
      Character.OTHER_NUMBER.toInt(),
      -> true
      else -> false
    }
}

@ConsistentCopyVisibility
data class JamoJudgeState private constructor(
  val target: String,
  val expectedSequence: List<Char>,
  val currentIndex: Int,
  val correctCount: Int,
  val errorCount: Int,
) {
  val expectedNext: Char?
    get() = expectedSequence.getOrNull(currentIndex)

  val isComplete: Boolean
    get() = currentIndex == expectedSequence.size

  val progress: Double
    get() = currentIndex.toDouble() / expectedSequence.size

  internal fun recordCorrect(): JamoJudgeState = copy(
    currentIndex = currentIndex + 1,
    correctCount = correctCount + 1,
  )

  internal fun recordError(): JamoJudgeState = copy(errorCount = errorCount + 1)

  companion object {
    fun forTarget(target: String): JamoJudgeState = JamoJudgeState(
      target = target,
      expectedSequence = JamoDecomposer.keySequenceFor(target),
      currentIndex = 0,
      correctCount = 0,
      errorCount = 0,
    )
  }
}

sealed interface JamoJudgeResult {
  data class Correct(val completed: Boolean) : JamoJudgeResult
  data class Incorrect(val expected: Char) : JamoJudgeResult
  data object AlreadyComplete : JamoJudgeResult
}

data class JamoJudgeEvaluation(
  val state: JamoJudgeState,
  val result: JamoJudgeResult,
)

object JamoSequenceJudge {
  /** Compares one logical jamo event. Shift+jamo arrives as its resulting jamo and counts once. */
  fun evaluate(input: Char, state: JamoJudgeState): JamoJudgeEvaluation {
    val expected = state.expectedNext
      ?: return JamoJudgeEvaluation(state, JamoJudgeResult.AlreadyComplete)
    if (input != expected) {
      return JamoJudgeEvaluation(
        state.recordError(),
        JamoJudgeResult.Incorrect(expected),
      )
    }
    val next = state.recordCorrect()
    return JamoJudgeEvaluation(
      next,
      JamoJudgeResult.Correct(completed = next.isComplete),
    )
  }
}
