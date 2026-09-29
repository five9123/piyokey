package app.piyokey.core.hangul

/** The candidate chosen by [OSIMECandidateTextJudge] and its evaluation. */
data class OSIMECandidateTextSelection(
  val target: String,
  val evaluation: OSIMETextEvaluation,
)

/**
 * Multi-target OS IME judge used when several targets are on screen at once (acid rain).
 * Port of the iOS app-layer `OSIMECandidateTextJudge` in `OSIMEInputView.swift`.
 *
 * Every typeable target is judged with [OSIMETextJudge]. Targets whose text is a confirmed
 * mismatch are dropped unless every candidate is one. The survivor is chosen by, in order:
 * completed match, longest accepted sequence, equal to [preferredTarget], earliest index.
 */
object OSIMECandidateTextJudge {
  fun evaluate(
    targets: List<String>,
    preferredTarget: String,
    committedText: String,
    markedText: String? = null,
  ): OSIMECandidateTextSelection? {
    val evaluations = targets.mapIndexedNotNull { index, target ->
      val evaluation = try {
        OSIMETextJudge.evaluate(target, committedText, markedText)
      } catch (_: JamoDecompositionError) {
        return@mapIndexedNotNull null
      }
      Candidate(index, target, evaluation)
    }
    if (evaluations.isEmpty()) return null

    val viable = evaluations.filterNot { it.isConfirmedMismatch }
    val pool = viable.ifEmpty { evaluations }
    val chosen = pool.maxWithOrNull(preference(preferredTarget)) ?: return null
    return OSIMECandidateTextSelection(chosen.target, chosen.evaluation)
  }

  private class Candidate(val index: Int, val target: String, val evaluation: OSIMETextEvaluation) {
    val isConfirmedMismatch: Boolean get() = evaluation.status is OSIMETextJudgeStatus.ConfirmedMismatch
    val isCompleted: Boolean get() = (evaluation.status as? OSIMETextJudgeStatus.Matching)?.completed == true
  }

  /** Lexicographic `(completed, acceptedCount, preferred, -index)` like the Swift tuple compare. */
  private fun preference(preferredTarget: String): Comparator<Candidate> =
    compareBy<Candidate> { if (it.isCompleted) 1 else 0 }
      .thenBy { it.evaluation.acceptedSequence.size }
      .thenBy { if (it.target == preferredTarget) 1 else 0 }
      .thenBy { -it.index }
}
