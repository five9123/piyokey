package app.piyokey.android.feature.input

import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.JamoDecompositionError
import app.piyokey.core.hangul.OSIMETextJudge
import app.piyokey.core.hangul.OSIMETextJudgeStatus

/** Field text split like iOS `markedTextRange`: committed (outside the composing span) + marked. */
data class ImeTextSnapshot(val committed: String, val marked: String?) {
  companion object {
    /**
     * Splits [fullText] around the IME composing span `[start, end)` (UTF-16 offsets from
     * `BaseInputConnection.getComposingSpanStart/End`, `-1` when absent). An invalid span is
     * treated as "not composing", like iOS.
     */
    fun split(fullText: String, composingStart: Int, composingEnd: Int): ImeTextSnapshot {
      val start = minOf(composingStart, composingEnd)
      val end = maxOf(composingStart, composingEnd)
      if (start < 0 || end > fullText.length || start == end) return ImeTextSnapshot(fullText, null)
      return ImeTextSnapshot(fullText.substring(0, start) + fullText.substring(end), fullText.substring(start, end))
    }
  }
}

/**
 * Post-reset stale-snapshot quarantine, ported from iOS `IMETextField.Coordinator`.
 *
 * After a target change the field is reset, but an IME may still publish the abandoned
 * previous-word document once more. Until the first legitimate post-reset input arrives, any
 * document that contains prior-target material and is not a valid prefix of a current target is
 * swallowed and the field is reset again, so it never reaches the judge.
 */
class ImeResetQuarantine {
  private var resetDocument = ""
  private val observedDocuments = LinkedHashSet<String>()
  private val staleDocuments = LinkedHashSet<String>()
  var isAwaitingPostResetInput = false
    private set

  enum class Decision {
    /** Report to the judge. */
    ACCEPT,

    /** Echo of the reset document: ignore silently. */
    IGNORE,

    /** Stale previous-target snapshot: ignore and schedule another reset to [resetDocument]. */
    RESET_AGAIN,
  }

  val pendingResetDocument: String get() = resetDocument

  /** Call when a session reset is scheduled with [replacement] while the field holds [currentText]. */
  fun beginReset(currentText: String, replacement: String, preservingStaleDocuments: Boolean = false) {
    resetDocument = replacement
    if (preservingStaleDocuments) return
    staleDocuments.clear()
    staleDocuments += observedDocuments
    staleDocuments += compositionSnapshots(currentText)
    staleDocuments -= replacement
    observedDocuments.clear()
    isAwaitingPostResetInput = true
  }

  /** Classifies a user-originated [fullText] change for the current [targets]. */
  fun classify(fullText: String, targets: List<String>): Decision {
    if (isAwaitingPostResetInput) {
      if (fullText == resetDocument) return Decision.IGNORE
      if (containsPriorTargetMaterial(fullText) && !isValidPostResetDocument(fullText, targets)) {
        return Decision.RESET_AGAIN
      }
      isAwaitingPostResetInput = false
      staleDocuments.clear()
    }
    observedDocuments += fullText
    return Decision.ACCEPT
  }

  private fun containsPriorTargetMaterial(text: String): Boolean {
    val candidate = if (resetDocument.isNotEmpty() && text.startsWith(resetDocument)) {
      text.substring(resetDocument.length)
    } else {
      text
    }
    return staleDocuments.any { stale ->
      if (stale == resetDocument) return@any false
      val sequence = try {
        JamoDecomposer.keySequence(stale)
      } catch (_: JamoDecompositionError) {
        return@any false
      }
      sequence.size >= 2 && candidate.contains(stale)
    }
  }

  private fun isValidPostResetDocument(text: String, targets: List<String>): Boolean = targets.any { target ->
    val evaluation = try {
      OSIMETextJudge.evaluate(target, text)
    } catch (_: JamoDecompositionError) {
      return@any false
    }
    evaluation.status is OSIMETextJudgeStatus.Matching || evaluation.status == OSIMETextJudgeStatus.ComposingMismatch
  }

  companion object {
    internal fun compositionSnapshots(text: String): Set<String> {
      if (text.isEmpty()) return emptySet()
      val snapshots = LinkedHashSet<String>()
      snapshots += text
      // Grapheme-safe enough here: Hangul syllables/jamo are single UTF-16 units.
      for (count in 1..text.length) snapshots += text.substring(0, count)
      val sequence = try {
        JamoDecomposer.keySequence(text)
      } catch (_: JamoDecompositionError) {
        null
      }
      if (sequence != null) {
        for (count in 1..sequence.size) snapshots += HangulComposer.compose(sequence.take(count)).text
      }
      return snapshots
    }
  }
}
