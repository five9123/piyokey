package app.piyokey.core.hangul

sealed interface OSIMETextJudgeStatus {
  /** The full visible text is a valid target prefix. Marked text may still change. */
  data class Matching(
    val completed: Boolean,
    val isComposing: Boolean,
  ) : OSIMETextJudgeStatus

  /** Only the marked (unconfirmed) portion differs, so no mistake is recorded. */
  data object ComposingMismatch : OSIMETextJudgeStatus

  /** Confirmed text differs from the target and counts as one mistake. */
  data class ConfirmedMismatch(val expectedIndex: Int) : OSIMETextJudgeStatus
}

data class OSIMETextEvaluation(
  val status: OSIMETextJudgeStatus,
  /** The longest safe target prefix that the session may reflect immediately. */
  val acceptedSequence: List<Char>,
)

object OSIMETextJudge {
  /**
   * Judges text received from an OS IME against a target's two-beolsik jamo sequence.
   *
   * [committedText] excludes the active marked range. [markedText] is the active composition at
   * the end of that text, if any. A mismatching marked range is ignored until the IME confirms it.
   */
  fun evaluate(
    target: String,
    committedText: String,
    markedText: String? = null,
  ): OSIMETextEvaluation {
    val expected = JamoDecomposer.keySequenceFor(target)
    val committed = keySequenceAllowingEmptyOrNull(committedText)

    if (committed == null) {
      val validPrefix = longestDecomposablePrefix(committedText)
      if (validPrefix.size >= expected.size && validPrefix.take(expected.size) == expected) {
        return OSIMETextEvaluation(
          status = OSIMETextJudgeStatus.Matching(completed = true, isComposing = false),
          acceptedSequence = expected,
        )
      }
      val mismatch = mismatchIndex(validPrefix, expected) ?: validPrefix.size
      return OSIMETextEvaluation(
        status = OSIMETextJudgeStatus.ConfirmedMismatch(expectedIndex = mismatch),
        acceptedSequence = expected.take(mismatch),
      )
    }

    if (committed.size >= expected.size && committed.take(expected.size) == expected) {
      return OSIMETextEvaluation(
        status = OSIMETextJudgeStatus.Matching(completed = true, isComposing = false),
        acceptedSequence = expected,
      )
    }

    val committedMismatch = mismatchIndex(committed, expected)
    if (committedMismatch == null) {
      if (!markedText.isNullOrEmpty()) {
        val marked = keySequenceAllowingEmptyOrNull(markedText)
          ?: return OSIMETextEvaluation(
            status = OSIMETextJudgeStatus.ComposingMismatch,
            acceptedSequence = committed,
          )
        val visible = committed + marked
        if (mismatchIndex(visible, expected) != null) {
          return OSIMETextEvaluation(
            status = OSIMETextJudgeStatus.ComposingMismatch,
            acceptedSequence = committed,
          )
        }
        return OSIMETextEvaluation(
          status = OSIMETextJudgeStatus.Matching(
            completed = visible.size == expected.size,
            isComposing = true,
          ),
          acceptedSequence = visible,
        )
      }

      return OSIMETextEvaluation(
        status = OSIMETextJudgeStatus.Matching(
          // Exact completion returned above, so a matching committed prefix is incomplete here.
          completed = false,
          isComposing = false,
        ),
        acceptedSequence = committed,
      )
    }

    return OSIMETextEvaluation(
      status = OSIMETextJudgeStatus.ConfirmedMismatch(expectedIndex = committedMismatch),
      acceptedSequence = expected.take(committedMismatch),
    )
  }

  private fun keySequenceAllowingEmptyOrNull(text: String): List<Char>? = try {
    if (text.isEmpty()) emptyList() else JamoDecomposer.keySequenceFor(text)
  } catch (_: JamoDecompositionException) {
    null
  }

  private fun mismatchIndex(candidate: List<Char>, expected: List<Char>): Int? {
    candidate.forEachIndexed { index, input ->
      if (index >= expected.size || input != expected[index]) return index
    }
    return null
  }

  private fun longestDecomposablePrefix(text: String): List<Char> = buildList {
    for (character in text) {
      val sequence = try {
        JamoDecomposer.keySequenceFor(character.toString())
      } catch (_: JamoDecompositionException) {
        break
      }
      addAll(sequence)
    }
  }
}
