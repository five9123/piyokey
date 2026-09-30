package app.piyokey.android.feature.input

import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import app.piyokey.android.BuildConfig
import kotlin.math.ceil

/**
 * Debug-only input latency probe (iOS `InputLatencyMonitor` + `DisplayRefreshProbe`).
 *
 * [beginInput] records the pointer-event uptime (`MotionEvent`/`PointerInputChange.uptimeMillis`,
 * delivered by the keyboards' `onInputStart`). [InputLatencyProbe] then waits for the next frame
 * commit (`ViewTreeObserver.registerFrameCommitCallback`, API 29+; `Choreographer` + post before)
 * and calls [finishPendingInputs], producing a sliding window of samples with p50/p95.
 */
class InputLatencyMonitor(sampleLimit: Int = 120) {
  private val limit = maxOf(1, sampleLimit)
  private val pendingStarts = ArrayList<Long>()
  private val samples = ArrayDeque<Double>()

  var inputRevision by mutableIntStateOf(0)
    private set
  var p50Milliseconds by mutableStateOf<Double?>(null)
    private set
  var p95Milliseconds by mutableStateOf<Double?>(null)
    private set
  var sampleCount by mutableIntStateOf(0)
    private set

  fun beginInput(uptimeMillis: Long = SystemClock.uptimeMillis()) {
    if (!isEnabled) return
    pendingStarts += uptimeMillis
    inputRevision++
  }

  fun finishPendingInputs(uptimeMillis: Long = SystemClock.uptimeMillis()) {
    if (pendingStarts.isEmpty()) return
    pendingStarts.forEach { samples.addLast(maxOf(0L, uptimeMillis - it).toDouble()) }
    pendingStarts.clear()
    while (samples.size > limit) samples.removeFirst()
    val sorted = samples.sorted()
    sampleCount = sorted.size
    p50Milliseconds = percentile(sorted, 0.50)
    p95Milliseconds = percentile(sorted, 0.95)
  }

  fun reset() {
    pendingStarts.clear()
    samples.clear()
    sampleCount = 0
    p50Milliseconds = null
    p95Milliseconds = null
  }

  companion object {
    /** Debug builds only (iOS `#if DEBUG`). Tests may flip it. */
    @Volatile
    var isEnabled: Boolean = BuildConfig.DEBUG

    /** Nearest-rank percentile, iOS `ceil(count * p) - 1`. */
    fun percentile(sorted: List<Double>, p: Double): Double? {
      if (sorted.isEmpty()) return null
      val index = (ceil(sorted.size * p).toInt() - 1).coerceIn(0, sorted.lastIndex)
      return sorted[index]
    }
  }
}

/** Invisible helper that closes pending samples at the next frame commit after each input. */
@Composable
fun InputLatencyProbe(monitor: InputLatencyMonitor) {
  if (!InputLatencyMonitor.isEnabled) return
  val view = LocalView.current
  val revision = monitor.inputRevision
  var scheduledRevision by remember { mutableIntStateOf(0) }
  LaunchedEffect(revision) {
    if (revision == 0 || revision <= scheduledRevision) return@LaunchedEffect
    scheduledRevision = revision
    val finish = Runnable { monitor.finishPendingInputs(SystemClock.uptimeMillis()) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      view.viewTreeObserver.registerFrameCommitCallback(finish)
    } else {
      Choreographer.getInstance().postFrameCallback { view.post(finish) }
    }
    view.invalidate()
  }
}
