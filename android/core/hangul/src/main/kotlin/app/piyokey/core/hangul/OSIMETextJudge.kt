package app.piyokey.core.hangul

/** Outcome of judging OS IME text against a target. */
sealed interface OSIMETextJudgeStatus {
  /** The full visible text is a valid target prefix. Marked text may still change. */
  data class Matching(val completed: Boolean, val isComposing: Boolean) : OSIMETextJudgeStatus

  /** Only the marked (unconfirmed) portion differs, so no mistake is recorded. */
  data object ComposingMismatch : OSIMETextJudgeStatus

  /** An ASCII key arrived from a non-Korean input source and must not affect the session. */
  data object UnsupportedASCIIInput : OSIMETextJudgeStatus

  /** Confirmed text differs from the target and counts as one mistake. */
  data class ConfirmedMismatch(val expectedIndex: Int) : OSIMETextJudgeStatus
}

/**
 * Result of [OSIMETextJudge.evaluate].
 *
 * @property acceptedSequence the longest safe target key-jamo prefix the session may reflect immediately.
 */
data class OSIMETextEvaluation(
  val status: OSIMETextJudgeStatus,
  val acceptedSequence: List<Char>,
)

/** Text-diff judge for OS IME input (PRD F2a). Port of `OSIMETextJudge.swift`. */
object OSIMETextJudge {
  /**
   * Judges text received from an OS IME against a target's two-beolsik jamo sequence.
   *
   * [committedText] excludes the active composing (marked) range. [markedText] is the active
   * composition at the end of that text, if any. A mismatching marked range is intentionally
   * ignored until the IME confirms it. Korean 10-key reachable intermediates in committed text
   * are reported as [OSIMETextJudgeStatus.ComposingMismatch] instead of a mistake.
   *
   * @throws JamoDecompositionError when [target] is empty or untypeable.
   */
  @Throws(JamoDecompositionError::class)
  fun evaluate(target: String, committedText: String, markedText: String? = null): OSIMETextEvaluation {
    val expected = JamoDecomposer.keySequence(target)
    // Marked text is an unconfirmed IME implementation detail. Korean 10-Key can expose transient
    // material there while replacing the active syllable, so only committed ASCII is evidence of a
    // non-Korean input source.
    if (containsUnsupportedASCII(committedText)) {
      val validPrefix = longestDecomposablePrefix(committedText)
      if (startsWithExpected(validPrefix, expected)) return completed(expected)
      val acceptedCount = mismatchIndex(validPrefix, expected) ?: minOf(validPrefix.size, expected.size)
      return OSIMETextEvaluation(OSIMETextJudgeStatus.UnsupportedASCIIInput, expected.take(acceptedCount))
    }

    val committed = keySequenceAllowingEmptyOrNull(committedText)
    if (committed == null) {
      val validPrefix = longestDecomposablePrefix(committedText)
      if (startsWithExpected(validPrefix, expected)) return completed(expected)
      if (Korean10KeyRecipe.committedDocumentEndsInReachableRawVowelPrefix(target, committedText)) {
        return OSIMETextEvaluation(OSIMETextJudgeStatus.ComposingMismatch, validPrefix)
      }
      val mismatch = mismatchIndex(validPrefix, expected) ?: validPrefix.size
      return OSIMETextEvaluation(OSIMETextJudgeStatus.ConfirmedMismatch(mismatch), expected.take(mismatch))
    }

    if (startsWithExpected(committed, expected)) return completed(expected)

    val committedMismatch = mismatchIndex(committed, expected)
    if (committedMismatch == null) {
      if (!markedText.isNullOrEmpty()) {
        val marked = keySequenceAllowingEmptyOrNull(markedText)
          ?: return OSIMETextEvaluation(OSIMETextJudgeStatus.ComposingMismatch, committed)
        val visible = committed + marked
        if (mismatchIndex(visible, expected) != null) {
          return OSIMETextEvaluation(OSIMETextJudgeStatus.ComposingMismatch, committed)
        }
        return OSIMETextEvaluation(
          OSIMETextJudgeStatus.Matching(completed = visible.size == expected.size, isComposing = true),
          visible,
        )
      }
      return OSIMETextEvaluation(
        OSIMETextJudgeStatus.Matching(completed = committed.size == expected.size, isComposing = false),
        committed,
      )
    }

    val acceptedPrefix = expected.take(committedMismatch)
    Korean10KeyRecipe.acceptedSequenceForUnconfirmedSameRecipeBoundaryCycle(target, committedText)?.let {
      return OSIMETextEvaluation(OSIMETextJudgeStatus.ComposingMismatch, it)
    }
    if (Korean10KeyRecipe.committedDocumentEndsInReachableIntermediate(target, committedText) ||
      Korean10KeyRecipe.committedDocumentEndsInReachableConsonantCycle(target, committedText) ||
      Korean10KeyRecipe.committedDocumentEndsInReachableClosedSyllableBoundary(target, committedText) ||
      Korean10KeyRecipe.committedDocumentEndsInReachableDanglingComplexTrailingPrefix(target, committedText) ||
      Korean10KeyRecipe.committedDocumentEndsInReachableComplexTrailingAssembly(target, committedText)
    ) {
      return OSIMETextEvaluation(OSIMETextJudgeStatus.ComposingMismatch, acceptedPrefix)
    }

    return OSIMETextEvaluation(OSIMETextJudgeStatus.ConfirmedMismatch(committedMismatch), acceptedPrefix)
  }

  private fun completed(expected: List<Char>) =
    OSIMETextEvaluation(OSIMETextJudgeStatus.Matching(completed = true, isComposing = false), expected)

  private fun startsWithExpected(candidate: List<Char>, expected: List<Char>): Boolean =
    candidate.size >= expected.size && candidate.subList(0, expected.size) == expected

  private fun keySequenceAllowingEmptyOrNull(text: String): List<Char>? =
    if (text.isEmpty()) {
      emptyList()
    } else {
      try {
        JamoDecomposer.keySequence(text)
      } catch (_: JamoDecompositionError) {
        null
      }
    }

  private fun containsUnsupportedASCII(text: String): Boolean =
    text.codePoints().anyMatch { it < 0x80 && it != 0x20 }

  private fun mismatchIndex(candidate: List<Char>, expected: List<Char>): Int? {
    for ((index, input) in candidate.withIndex()) {
      if (index >= expected.size || input != expected[index]) return index
    }
    return null
  }

  private fun longestDecomposablePrefix(text: String): List<Char> {
    val result = ArrayList<Char>()
    for (character in text.graphemeClusters()) {
      val sequence = try {
        JamoDecomposer.keySequence(character)
      } catch (_: JamoDecompositionError) {
        break
      }
      result += sequence
    }
    return result
  }
}
