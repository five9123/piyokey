package app.piyokey.core.domain.practice

import app.piyokey.core.hangul.JamoDecomposer
import kotlin.math.max
import kotlin.math.min

/** Small pure rules used by the practice session and its result screen (iOS `PracticeView`/`PracticeResultView`). */
object PracticeRules {
  /** A completed item waits this long before the next target / result appears (iOS 650 ms). */
  const val POST_COMPLETION_DELAY_MILLIS = 650L

  /** Source deck id of the built-in sample targets (iOS `builtInReviewSources`). */
  const val BUILT_IN_SOURCE_DECK_ID = "official_built_in_basics"

  /**
   * Stars shown on the result: the curriculum rating when given, otherwise the free-practice
   * accuracy bands (≥95 → 3, ≥80 → 2, else 1).
   */
  fun displayedStars(earnedStarsOverride: Int?, accuracyPercent: Double): Int {
    if (earnedStarsOverride != null) return earnedStarsOverride
    if (accuracyPercent >= 95) return 3
    if (accuracyPercent >= 80) return 2
    return 1
  }

  /** Overall session progress for the toolbar bar: finished targets + fraction of the current one. */
  fun overallProgress(currentTargetIndex: Int, completedJamoCount: Int, targetJamoCount: Int, targetCount: Int): Double {
    if (targetCount <= 0) return 0.0
    val currentFraction = min(completedJamoCount.toDouble() / max(targetJamoCount, 1), 1.0)
    return min((currentTargetIndex + currentFraction) / targetCount, 1.0)
  }

  /** `GameRecord.deckId` for a lesson record (iOS `persistLearningRecordIfNeeded`). */
  fun learningRecordDeckId(sourceDeckIds: Set<String>, curriculumStageId: String?): String = when {
    sourceDeckIds.size == 1 -> sourceDeckIds.first()
    sourceDeckIds.size > 1 -> "review_deck"
    curriculumStageId != null -> "official_curriculum"
    else -> "free_practice"
  }

  /** `GameRecord.course` for a lesson record. */
  fun learningRecordCourse(curriculumStageId: String?): String =
    curriculumStageId?.let { "curriculum:$it" } ?: "practice"

  /** A clear for the mascot's consecutive-lesson counter. */
  fun isLessonCleared(accuracyPercent: Double): Boolean = accuracyPercent >= 80

  /**
   * Result characters with an "had a mistake" flag, for underlining the review list (iOS
   * `SessionResultReviewSection.markedCharacters`).
   */
  fun markedCharacters(ko: String, mistakenJamoIndices: Set<Int>): List<Pair<String, Boolean>> {
    var offset = 0
    return PracticeSession.graphemes(ko).map { character ->
      val count = runCatching { JamoDecomposer.keySequence(character).size }.getOrDefault(1)
      val range = offset until offset + count
      offset += count
      character to mistakenJamoIndices.any { it in range }
    }
  }

  /**
   * Horizontal offset of the jamo chip track (iOS `JamoProgressTrack.horizontalOffset`): centred when it
   * fits, otherwise the active chip is kept centred within the scroll bounds.
   */
  fun jamoTrackOffset(
    availableWidth: Float,
    count: Int,
    completedCount: Int,
    chipWidth: Float,
    chipSpacing: Float = JAMO_CHIP_SPACING,
    safeInset: Float = JAMO_TRACK_SAFE_INSET,
  ): Float {
    val content = jamoTrackContentWidth(count, chipWidth, chipSpacing, safeInset)
    if (content <= availableWidth) return (availableWidth - content) / 2
    if (count <= 0) return 0f
    val activeIndex = min(completedCount, count - 1)
    val activeCenter = safeInset + activeIndex * (chipWidth + chipSpacing) + chipWidth / 2
    val centered = availableWidth / 2 - activeCenter
    return min(0f, max(availableWidth - content, centered))
  }

  fun jamoTrackContentWidth(
    count: Int,
    chipWidth: Float,
    chipSpacing: Float = JAMO_CHIP_SPACING,
    safeInset: Float = JAMO_TRACK_SAFE_INSET,
  ): Float = count * chipWidth + max(count - 1, 0) * chipSpacing + safeInset * 2

  const val JAMO_CHIP_SPACING = 7f
  const val JAMO_TRACK_SAFE_INSET = 2f
}

/** iOS `JapaneseMeaningDisplayText`: trims and drops one pair of surrounding 「」/『』. */
object JapaneseMeaningDisplayText {
  fun format(meaning: String): String {
    val trimmed = meaning.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.length >= 2 &&
      ((trimmed.startsWith("「") && trimmed.endsWith("」")) || (trimmed.startsWith("『") && trimmed.endsWith("』")))
    ) {
      return trimmed.substring(1, trimmed.length - 1)
    }
    return trimmed
  }
}
