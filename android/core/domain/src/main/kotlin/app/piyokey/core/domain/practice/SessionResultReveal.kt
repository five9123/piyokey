package app.piyokey.core.domain.practice

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * iOS `SessionResultRevealTiming`: the F12 result screen's ~2.5 s staged reveal. [scale] stretches
 * wall-clock time (UI tests) without changing canonical progress.
 */
data class SessionResultRevealTiming(val scale: Double = 1.0) {
  private val effectiveScale: Double get() = max(scale, 0.01)

  /** Seconds until the actions become interactive. */
  val totalDuration: Double get() = CANONICAL_DURATION * effectiveScale

  fun state(elapsed: Double, skipped: Boolean = false): SessionResultRevealState =
    SessionResultRevealState(if (skipped) CANONICAL_DURATION else max(0.0, elapsed / effectiveScale))

  companion object {
    const val CANONICAL_DURATION = 2.5
  }
}

/** iOS `SessionResultRevealState`: every zone's progress at a canonical elapsed time (seconds). */
data class SessionResultRevealState(val canonicalElapsed: Double) {
  val headerProgress: Double get() = easeOutBack(progress(canonicalElapsed, 0.0, 0.4))
  val headerScale: Double get() = 0.6 + headerProgress * 0.4
  val headerOpacity: Double get() = easeOut(progress(canonicalElapsed, 0.0, 0.2))
  val scoreProgress: Double get() = easeOut(progress(canonicalElapsed, 0.4, 0.8))
  val lowerZoneOpacity: Double get() = easeOut(progress(canonicalElapsed, 1.8, 0.4))
  val actionsEnabled: Boolean get() = canonicalElapsed >= SessionResultRevealTiming.CANONICAL_DURATION
  val headerParticleProgress: Double get() = progress(canonicalElapsed, 0.05, 0.65)
  val newRecordBurstProgress: Double get() = progress(canonicalElapsed, 1.2, 0.65)

  fun metricProgress(index: Int): Double = easeOut(progress(canonicalElapsed, 1.2 + index * 0.3, 0.3))

  fun starProgress(index: Int): Double = easeOutBack(progress(canonicalElapsed, index * 0.1, 0.24))

  companion object {
    val FINAL = SessionResultRevealState(SessionResultRevealTiming.CANONICAL_DURATION)

    private fun progress(elapsed: Double, start: Double, duration: Double): Double =
      min(max((elapsed - start) / duration, 0.0), 1.0)

    private fun easeOut(value: Double): Double = 1 - (1 - value).pow(3)

    private fun easeOutBack(value: Double): Double {
      val overshoot = 1.70158
      val shifted = value - 1
      return 1 + (overshoot + 1) * shifted.pow(3) + overshoot * shifted.pow(2)
    }
  }
}
