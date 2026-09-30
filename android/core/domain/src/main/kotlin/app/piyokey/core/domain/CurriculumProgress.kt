package app.piyokey.core.domain

import app.piyokey.core.deckkit.Iso8601InstantSerializer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** iOS `CurriculumStarRating`: 80% clears (1★), 90%+40CPM 2★, 97%+60CPM 3★. */
object CurriculumStarRating {
  const val CLEAR_ACCURACY = 80.0

  fun stars(accuracy: Double, charactersPerMinute: Double): Int = when {
    accuracy < CLEAR_ACCURACY -> 0
    accuracy >= 97 && charactersPerMinute >= 60 -> 3
    accuracy >= 90 && charactersPerMinute >= 40 -> 2
    else -> 1
  }
}

/** Resumable lesson position (also used by the practice session). */
@Serializable
data class PracticeSessionCheckpoint(
  @SerialName("current_target_index") val currentTargetIndex: Int,
  @SerialName("accepted_keys") val acceptedKeys: String,
  @SerialName("mistake_count") val mistakeCount: Int,
  @SerialName("current_target_mistake_count") val currentTargetMistakeCount: Int,
  @SerialName("current_target_mistaken_jamo_indices") val currentTargetMistakenJamoIndices: List<Int>,
  /** Seconds. */
  @SerialName("active_duration") val activeDuration: Double,
)

@Serializable
data class CurriculumActiveSession(
  @SerialName("stage_id") val stageId: String,
  val checkpoint: PracticeSessionCheckpoint,
  @SerialName("updated_at") @Serializable(with = Iso8601InstantSerializer::class) val updatedAt: Instant,
)

@Serializable
data class CurriculumStageProgress(
  @SerialName("stage_id") val stageId: String,
  val stars: Int,
  @SerialName("best_accuracy") val bestAccuracy: Double,
  @SerialName("completed_at") @Serializable(with = Iso8601InstantSerializer::class) val completedAt: Instant,
) {
  val id: String get() = stageId
}

data class CurriculumProgressSnapshot(
  val stageProgress: Map<String, CurriculumStageProgress>,
  val activeSession: CurriculumActiveSession?,
) {
  val completedStageIds: Set<String> get() = stageProgress.keys

  companion object {
    val EMPTY = CurriculumProgressSnapshot(emptyMap(), null)
  }
}

enum class CurriculumProgressMutation { SAVED, CLEARED, COMPLETED, UNCHANGED }

sealed class CurriculumProgressStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) : CurriculumProgressStoreException("Unsupported curriculum schema $version")
  data object InvalidProgress : CurriculumProgressStoreException("Invalid curriculum progress")
}

data class CurriculumProgressChange(val snapshot: CurriculumProgressSnapshot, val mutation: CurriculumProgressMutation)

object CurriculumProgressRules {
  const val CURRENT_SCHEMA_VERSION = 1

  fun validated(stageProgress: List<CurriculumStageProgress>, activeSession: CurriculumActiveSession?): CurriculumProgressSnapshot {
    val progress = LinkedHashMap<String, CurriculumStageProgress>()
    for (item in stageProgress) {
      if (item.stars !in 1..3 || item.bestAccuracy !in 80.0..100.0) throw CurriculumProgressStoreException.InvalidProgress
      progress[item.stageId] = item
    }
    return CurriculumProgressSnapshot(progress, activeSession)
  }

  fun applySave(
    stageId: String,
    checkpoint: PracticeSessionCheckpoint,
    at: Instant,
    snapshot: CurriculumProgressSnapshot,
  ) = CurriculumProgressChange(
    snapshot.copy(activeSession = CurriculumActiveSession(stageId, checkpoint, at)),
    CurriculumProgressMutation.SAVED,
  )

  /** Ends [stageId]'s session; a clear (≥ 1★ and ≥ 80%) keeps the best stars/accuracy and first date. */
  fun applyFinish(
    stageId: String,
    stars: Int,
    accuracy: Double,
    at: Instant,
    snapshot: CurriculumProgressSnapshot,
  ): CurriculumProgressChange {
    val session = if (snapshot.activeSession?.stageId == stageId) null else snapshot.activeSession
    val cleared = snapshot.copy(activeSession = session)
    if (stars <= 0 || accuracy < CurriculumStarRating.CLEAR_ACCURACY) {
      return CurriculumProgressChange(cleared, CurriculumProgressMutation.CLEARED)
    }
    val existing = snapshot.stageProgress[stageId]
    val progress = if (existing != null) {
      existing.copy(
        stars = maxOf(existing.stars, minOf(stars, 3)),
        bestAccuracy = maxOf(existing.bestAccuracy, minOf(accuracy, 100.0)),
      )
    } else {
      CurriculumStageProgress(stageId, minOf(stars, 3), minOf(accuracy, 100.0), at)
    }
    return CurriculumProgressChange(
      cleared.copy(stageProgress = snapshot.stageProgress + (stageId to progress)),
      CurriculumProgressMutation.COMPLETED,
    )
  }

  fun applyClear(stageId: String?, snapshot: CurriculumProgressSnapshot): CurriculumProgressChange {
    val active = snapshot.activeSession
    if (active == null || (stageId != null && active.stageId != stageId)) {
      return CurriculumProgressChange(snapshot, CurriculumProgressMutation.UNCHANGED)
    }
    return CurriculumProgressChange(snapshot.copy(activeSession = null), CurriculumProgressMutation.CLEARED)
  }
}
