package app.piyokey.feature.practice

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import app.piyokey.core.session.PracticeSessionEvent
import app.piyokey.core.session.PracticeSessionReducer
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.ceil

internal const val TOUCH_GATE_ROOT_TAG = "m2-touch-gate-root"

/**
 * Test-only host around the real practice UI. The ancestor observes the same pointer stream before
 * the keyboard consumes it, while [PracticeScreen] and [DubeolsikKeyboard] remain unmodified.
 */
@Composable
internal fun PracticeTouchGateHost(
  monitor: PracticeTouchGateMonitor,
  targetCount: Int,
  showPhysicalInstructions: Boolean,
) {
  val targets = remember(targetCount) { List(targetCount) { "가" } }
  var state by remember(targets) { mutableStateOf(PracticeSessionReducer.initialState(targets)) }

  val dispatch: (PracticeSessionEvent) -> Unit = { event ->
    val beforeRevision = state.compositionRevision
    val deliveryId = if (event is PracticeSessionEvent.Key) {
      monitor.onKeyDelivered(event.jamo)
    } else {
      null
    }
    val reduction = PracticeSessionReducer.reduce(state, event)
    state = reduction.state
    if (deliveryId != null) {
      monitor.onDeliveryReduced(
        deliveryId = deliveryId,
        acceptedRevision = state.compositionRevision.takeIf { it > beforeRevision },
      )
    }
  }
  val latestDispatch = rememberUpdatedState(dispatch)
  val pendingTransition = state.pendingTransition

  // Retain the production reducer's tokened 650 ms boundary so the accepted state reaches a frame.
  LaunchedEffect(pendingTransition?.token) {
    val transition = pendingTransition ?: return@LaunchedEffect
    delay(transition.delayMillis)
    latestDispatch.value(PracticeSessionEvent.Advance(transition.token))
  }

  val view = LocalView.current
  monitor.observeView(view)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .semantics { testTag = TOUCH_GATE_ROOT_TAG }
      .pointerInput(monitor) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { change ->
              when {
                change.changedToDownIgnoreConsumed() -> monitor.onPointerDown(
                  pointerId = change.id.value,
                  position = change.position,
                  eventUptimeMillis = change.uptimeMillis,
                )
                change.changedToUpIgnoreConsumed() || !change.pressed -> monitor.onPointerUp(
                  pointerId = change.id.value,
                  eventUptimeMillis = change.uptimeMillis,
                )
              }
            }
          }
        }
      }
      .drawWithContent {
        monitor.scheduleFrameCommit(
          drawnRevision = state.compositionRevision,
          view = view,
        )
        drawContent()
      },
  ) {
    PracticeScreen(
      state = state,
      onEvent = dispatch,
      modifier = Modifier.fillMaxSize(),
      keyboardOptions = PracticeKeyboardOptions(
        showsKeyGuide = true,
        showsRomanHints = false,
        hapticsEnabled = showPhysicalInstructions,
      ),
    )

    if (showPhysicalInstructions) {
      PhysicalGateStatus(
        monitor = monitor,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun PhysicalGateStatus(
  monitor: PracticeTouchGateMonitor,
  modifier: Modifier = Modifier,
) {
  var status by remember(monitor) { mutableStateOf(monitor.statusText()) }
  LaunchedEffect(monitor) {
    while (true) {
      status = monitor.statusText()
      delay(50)
    }
  }
  Text(
    text = status,
    modifier = modifier
      .background(Color(0xE6222222), RoundedCornerShape(8.dp))
      .padding(horizontal = 10.dp, vertical = 5.dp),
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    style = MaterialTheme.typography.labelMedium,
  )
}

internal enum class TouchGatePhase {
  WARMUP,
  MEASUREMENT,
  OVERFLOW,
  UNASSIGNED,
}

internal enum class RawTouchKind {
  DOWN,
  UP,
}

internal data class RawTouchRecord(
  val timelineSequence: Long,
  val kind: RawTouchKind,
  val pointerId: Long,
  val key: Char?,
  val phase: TouchGatePhase,
  val eventUptimeMillis: Long,
  val handlerUptimeMillis: Long,
  val handlerElapsedRealtimeNanos: Long,
  val activePointerCount: Int,
)

internal data class TouchDownSnapshot(
  val ordinal: Int?,
  val pointerId: Long,
  val key: Char?,
  val phase: TouchGatePhase,
  val timelineSequence: Long,
  val eventUptimeMillis: Long,
  val handlerUptimeMillis: Long,
  val handlerElapsedRealtimeNanos: Long,
  val deliveryId: Long?,
  val upTimelineSequence: Long?,
)

internal data class KeyDeliverySnapshot(
  val id: Long,
  val timelineSequence: Long,
  val jamo: Char,
  val phase: TouchGatePhase,
  val sourceDownOrdinal: Int?,
  val pointerId: Long?,
  val callbackUptimeMillis: Long,
  val callbackElapsedRealtimeNanos: Long,
  val activePointerIds: List<Long>,
  val acceptedRevision: Long?,
  val acceptedUptimeMillis: Long?,
  val acceptedElapsedRealtimeNanos: Long?,
)

internal data class TouchFrameSample(
  val phase: TouchGatePhase,
  val downOrdinal: Int,
  val pointerId: Long,
  val jamo: Char,
  val eventUptimeMillis: Long,
  val initialHandlerUptimeMillis: Long,
  val initialHandlerElapsedRealtimeNanos: Long,
  val deliveryUptimeMillis: Long,
  val deliveryElapsedRealtimeNanos: Long,
  val acceptedRevision: Long,
  val acceptedUptimeMillis: Long,
  val acceptedElapsedRealtimeNanos: Long,
  val frameCommitUptimeMillis: Long,
  val frameCommitElapsedRealtimeNanos: Long,
  val eventToAcceptedMillis: Long,
  val acceptedToFrameCommitMillis: Long,
  val eventToFrameCommitMillis: Long,
  val activePointerIdsAtAcceptance: List<Long>,
)

internal data class TouchGateSummary(
  val expectedWarmupCount: Int,
  val actualWarmupCount: Int,
  val rawWarmupDownCount: Int,
  val acceptedWarmupDeliveryCount: Int,
  val warmupUndeliveredDownCount: Int,
  val warmupRejectedDeliveryCount: Int,
  val warmupPass: Boolean,
  val expectedMeasurementCount: Int,
  val actualMeasurementCount: Int,
  val rawMeasurementDownCount: Int,
  val missingCount: Int,
  val duplicateCount: Int,
  val undeliveredDownCount: Int,
  val rejectedDeliveryCount: Int,
  val unknownDownCount: Int,
  val overflowDownCount: Int,
  val sequencePass: Boolean,
  val expectedOverlapPairCount: Int,
  val actualOverlapPairCount: Int,
  val overlapPass: Boolean,
  val maxConcurrentPointerCount: Int,
  val p50EventToFrameCommitMillis: Double?,
  val p95EventToFrameCommitMillis: Double?,
  val maxEventToFrameCommitMillis: Double?,
  val latencyThresholdMillis: Double,
  val sampleCountPass: Boolean,
  val latencyPass: Boolean,
  val hardwareFrameCommitAvailable: Boolean,
  val overallPass: Boolean,
)

internal data class TouchGateSnapshot(
  val gateName: String,
  val capturedAtUtc: String,
  val warmupTarget: Int,
  val measurementTarget: Int,
  val refreshRateHz: Float,
  val hardwareAccelerated: Boolean,
  val rawTouches: List<RawTouchRecord>,
  val downs: List<TouchDownSnapshot>,
  val deliveries: List<KeyDeliverySnapshot>,
  val samples: List<TouchFrameSample>,
  val frameCommitErrors: List<String>,
  val summary: TouchGateSummary,
)

/** Thread-safe correlation point shared by the Compose UI thread and frame-commit callbacks. */
internal class PracticeTouchGateMonitor(
  private val gateName: String,
  private val warmupTarget: Int,
  private val measurementTarget: Int,
  private val expectedPattern: List<Char> = listOf('ㄱ', 'ㅏ'),
  private val latencyThresholdMillis: Double = 50.0,
) {
  init {
    require(warmupTarget >= 0)
    require(measurementTarget > 0)
    require(expectedPattern.isNotEmpty())
  }

  private val lock = Any()
  private var armed = false
  private var targetBounds: Map<Char, Rect> = emptyMap()
  private var targetTouchOutsetPx = 0f
  private var timelineSequence = 0L
  private var targetDownOrdinal = 0
  private var deliverySequence = 0L
  private var maxConcurrentPointerCount = 0
  private var refreshRateHz = 0f
  private var hardwareAccelerated = false
  private val activeDowns = linkedMapOf<Long, MutableTouchDown>()
  private val downs = mutableListOf<MutableTouchDown>()
  private val rawTouches = mutableListOf<RawTouchRecord>()
  private val deliveries = mutableListOf<MutableKeyDelivery>()
  private val samples = mutableListOf<TouchFrameSample>()
  private val scheduledDrawRevisions = mutableSetOf<Long>()
  private val frameCommitErrors = mutableListOf<String>()

  fun arm(
    keyBoundsInHost: Map<Char, Rect>,
    touchOutsetPx: Float,
  ) = synchronized(lock) {
    require(expectedPattern.all(keyBoundsInHost::containsKey)) {
      "Every expected key needs a measured host-space bound"
    }
    targetBounds = keyBoundsInHost.toMap()
    targetTouchOutsetPx = touchOutsetPx.coerceAtLeast(0f)
    timelineSequence = 0L
    targetDownOrdinal = 0
    deliverySequence = 0L
    maxConcurrentPointerCount = 0
    activeDowns.clear()
    downs.clear()
    rawTouches.clear()
    deliveries.clear()
    samples.clear()
    scheduledDrawRevisions.clear()
    frameCommitErrors.clear()
    armed = true
  }

  fun observeView(view: View) = synchronized(lock) {
    refreshRateHz = view.display?.refreshRate ?: 0f
    hardwareAccelerated = view.isHardwareAccelerated
  }

  fun onPointerDown(
    pointerId: Long,
    position: Offset,
    eventUptimeMillis: Long,
  ) = synchronized(lock) {
    if (!armed) return@synchronized
    val handlerUptimeMillis = SystemClock.uptimeMillis()
    val handlerElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    val key = resolveKey(position)
    val ordinal = key?.let { ++targetDownOrdinal }
    val phase = ordinal?.let(::phaseForOrdinal) ?: TouchGatePhase.UNASSIGNED
    val sequence = ++timelineSequence
    val down = MutableTouchDown(
      ordinal = ordinal,
      pointerId = pointerId,
      key = key,
      phase = phase,
      timelineSequence = sequence,
      eventUptimeMillis = eventUptimeMillis,
      handlerUptimeMillis = handlerUptimeMillis,
      handlerElapsedRealtimeNanos = handlerElapsedRealtimeNanos,
    )
    activeDowns[pointerId]?.let { previous ->
      frameCommitErrors += "pointer $pointerId went down before ordinal ${previous.ordinal} went up"
    }
    activeDowns[pointerId] = down
    downs += down
    maxConcurrentPointerCount = maxOf(maxConcurrentPointerCount, activeDowns.size)
    rawTouches += RawTouchRecord(
      timelineSequence = sequence,
      kind = RawTouchKind.DOWN,
      pointerId = pointerId,
      key = key,
      phase = phase,
      eventUptimeMillis = eventUptimeMillis,
      handlerUptimeMillis = handlerUptimeMillis,
      handlerElapsedRealtimeNanos = handlerElapsedRealtimeNanos,
      activePointerCount = activeDowns.size,
    )
  }

  fun onPointerUp(
    pointerId: Long,
    eventUptimeMillis: Long,
  ) = synchronized(lock) {
    if (!armed) return@synchronized
    val handlerUptimeMillis = SystemClock.uptimeMillis()
    val handlerElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    val down = activeDowns.remove(pointerId)
    val sequence = ++timelineSequence
    down?.upTimelineSequence = sequence
    if (down == null) {
      frameCommitErrors += "pointer $pointerId went up without a tracked down"
    }
    rawTouches += RawTouchRecord(
      timelineSequence = sequence,
      kind = RawTouchKind.UP,
      pointerId = pointerId,
      key = down?.key,
      phase = down?.phase ?: TouchGatePhase.UNASSIGNED,
      eventUptimeMillis = eventUptimeMillis,
      handlerUptimeMillis = handlerUptimeMillis,
      handlerElapsedRealtimeNanos = handlerElapsedRealtimeNanos,
      activePointerCount = activeDowns.size,
    )
  }

  /** Called at the real keyboard callback, before the reducer decides whether the key is valid. */
  fun onKeyDelivered(jamo: Char): Long = synchronized(lock) {
    val deliveryId = ++deliverySequence
    if (!armed) return@synchronized deliveryId
    val matchingDown = activeDowns.values
      .toList()
      .asReversed()
      .firstOrNull { it.key == jamo && it.deliveryId == null }
    val activePointerIds = activeDowns.keys.sorted()
    val delivery = MutableKeyDelivery(
      id = deliveryId,
      timelineSequence = ++timelineSequence,
      jamo = jamo,
      phase = matchingDown?.phase ?: TouchGatePhase.UNASSIGNED,
      sourceDownOrdinal = matchingDown?.ordinal,
      pointerId = matchingDown?.pointerId,
      callbackUptimeMillis = SystemClock.uptimeMillis(),
      callbackElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
      activePointerIds = activePointerIds,
    )
    matchingDown?.deliveryId = deliveryId
    deliveries += delivery
    deliveryId
  }

  fun onDeliveryReduced(
    deliveryId: Long,
    acceptedRevision: Long?,
  ) = synchronized(lock) {
    if (!armed) return@synchronized
    val delivery = deliveries.lastOrNull { it.id == deliveryId } ?: return@synchronized
    if (acceptedRevision != null) {
      delivery.acceptedRevision = acceptedRevision
      delivery.acceptedUptimeMillis = SystemClock.uptimeMillis()
      delivery.acceptedElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
  }

  /** Register before drawing content; the callback denotes submission of that accepted UI frame. */
  fun scheduleFrameCommit(
    drawnRevision: Long,
    view: View,
  ) {
    if (Build.VERSION.SDK_INT < 29) return
    val deliveriesForRevision = synchronized(lock) {
      if (!armed || !view.isHardwareAccelerated || drawnRevision in scheduledDrawRevisions) {
        return@synchronized emptyList()
      }
      val pending = deliveries.filter {
        it.acceptedRevision != null &&
          it.acceptedRevision!! <= drawnRevision &&
          it.scheduledDrawRevision == null &&
          !it.sampled
      }
      if (pending.isNotEmpty()) {
        pending.forEach { it.scheduledDrawRevision = drawnRevision }
        scheduledDrawRevisions += drawnRevision
      }
      pending.map(MutableKeyDelivery::id)
    }
    if (deliveriesForRevision.isEmpty()) return

    val observer = view.viewTreeObserver
    if (!observer.isAlive) {
      markFrameRegistrationFailed(drawnRevision, "ViewTreeObserver was not alive")
      return
    }
    try {
      observer.registerFrameCommitCallback {
        onFrameCommitted(
          drawnRevision = drawnRevision,
          frameCommitUptimeMillis = SystemClock.uptimeMillis(),
          frameCommitElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
        )
      }
    } catch (failure: RuntimeException) {
      markFrameRegistrationFailed(
        drawnRevision,
        "frame callback registration failed: ${failure.javaClass.simpleName}",
      )
    }
  }

  val measurementSampleCount: Int
    get() = synchronized(lock) { samples.count { it.phase == TouchGatePhase.MEASUREMENT } }

  val acceptedDeliveryCount: Int
    get() = synchronized(lock) { deliveries.count { it.acceptedRevision != null } }

  fun statusText(): String = synchronized(lock) {
    if (!armed) return@synchronized "Preparing touch gate…"
    val warmupCount = samples.count { it.phase == TouchGatePhase.WARMUP }
    val measurementCount = samples.count { it.phase == TouchGatePhase.MEASUREMENT }
    when {
      warmupCount < warmupTarget ->
        "Warm-up $warmupCount/$warmupTarget · hold ㄱ, roll ㅏ"
      measurementCount < measurementTarget ->
        "Measure $measurementCount/$measurementTarget · keep two fingers overlapped"
      else -> "STOP · measurement complete"
    }
  }

  fun snapshot(): TouchGateSnapshot = synchronized(lock) {
    val downSnapshots = downs.map(MutableTouchDown::snapshot)
    val deliverySnapshots = deliveries.map(MutableKeyDelivery::snapshot)
    val sampleSnapshots = samples.toList()
    TouchGateSnapshot(
      gateName = gateName,
      capturedAtUtc = Instant.now().toString(),
      warmupTarget = warmupTarget,
      measurementTarget = measurementTarget,
      refreshRateHz = refreshRateHz,
      hardwareAccelerated = hardwareAccelerated,
      rawTouches = rawTouches.toList(),
      downs = downSnapshots,
      deliveries = deliverySnapshots,
      samples = sampleSnapshots,
      frameCommitErrors = frameCommitErrors.toList(),
      summary = calculateSummary(
        downSnapshots = downSnapshots,
        deliverySnapshots = deliverySnapshots,
        sampleSnapshots = sampleSnapshots,
      ),
    )
  }

  private fun resolveKey(position: Offset): Char? {
    var bestKey: Char? = null
    var bestEdgeDistance = Float.POSITIVE_INFINITY
    var bestCenterDistance = Float.POSITIVE_INFINITY
    targetBounds.forEach { (key, bounds) ->
      val expanded = Rect(
        left = bounds.left - targetTouchOutsetPx,
        top = bounds.top - targetTouchOutsetPx,
        right = bounds.right + targetTouchOutsetPx,
        bottom = bounds.bottom + targetTouchOutsetPx,
      )
      if (!expanded.contains(position)) return@forEach
      val edgeDistance = squaredDistanceToRect(position, bounds)
      val centerDistance = (position - bounds.center).getDistanceSquared()
      if (
        edgeDistance < bestEdgeDistance ||
        (edgeDistance == bestEdgeDistance && centerDistance < bestCenterDistance)
      ) {
        bestKey = key
        bestEdgeDistance = edgeDistance
        bestCenterDistance = centerDistance
      }
    }
    return bestKey
  }

  private fun phaseForOrdinal(ordinal: Int): TouchGatePhase = when {
    ordinal <= warmupTarget -> TouchGatePhase.WARMUP
    ordinal <= warmupTarget + measurementTarget -> TouchGatePhase.MEASUREMENT
    else -> TouchGatePhase.OVERFLOW
  }

  private fun onFrameCommitted(
    drawnRevision: Long,
    frameCommitUptimeMillis: Long,
    frameCommitElapsedRealtimeNanos: Long,
  ) = synchronized(lock) {
    deliveries
      .filter { it.scheduledDrawRevision == drawnRevision && !it.sampled }
      .sortedBy(MutableKeyDelivery::timelineSequence)
      .forEach { delivery ->
        val down = delivery.sourceDownOrdinal?.let { ordinal ->
          downs.firstOrNull { it.ordinal == ordinal }
        }
        val acceptedRevision = delivery.acceptedRevision
        val acceptedUptimeMillis = delivery.acceptedUptimeMillis
        val acceptedElapsedRealtimeNanos = delivery.acceptedElapsedRealtimeNanos
        if (
          down == null ||
          acceptedRevision == null ||
          acceptedUptimeMillis == null ||
          acceptedElapsedRealtimeNanos == null
        ) {
          frameCommitErrors += "accepted delivery ${delivery.id} could not be correlated"
          delivery.sampled = true
          return@forEach
        }
        samples += TouchFrameSample(
          phase = delivery.phase,
          downOrdinal = requireNotNull(down.ordinal),
          pointerId = down.pointerId,
          jamo = delivery.jamo,
          eventUptimeMillis = down.eventUptimeMillis,
          initialHandlerUptimeMillis = down.handlerUptimeMillis,
          initialHandlerElapsedRealtimeNanos = down.handlerElapsedRealtimeNanos,
          deliveryUptimeMillis = delivery.callbackUptimeMillis,
          deliveryElapsedRealtimeNanos = delivery.callbackElapsedRealtimeNanos,
          acceptedRevision = acceptedRevision,
          acceptedUptimeMillis = acceptedUptimeMillis,
          acceptedElapsedRealtimeNanos = acceptedElapsedRealtimeNanos,
          frameCommitUptimeMillis = frameCommitUptimeMillis,
          frameCommitElapsedRealtimeNanos = frameCommitElapsedRealtimeNanos,
          eventToAcceptedMillis = acceptedUptimeMillis - down.eventUptimeMillis,
          acceptedToFrameCommitMillis = frameCommitUptimeMillis - acceptedUptimeMillis,
          eventToFrameCommitMillis = frameCommitUptimeMillis - down.eventUptimeMillis,
          activePointerIdsAtAcceptance = delivery.activePointerIds,
        )
        delivery.sampled = true
      }
  }

  private fun markFrameRegistrationFailed(
    drawnRevision: Long,
    reason: String,
  ) = synchronized(lock) {
    frameCommitErrors += reason
    scheduledDrawRevisions.remove(drawnRevision)
    deliveries
      .filter { it.scheduledDrawRevision == drawnRevision && !it.sampled }
      .forEach { it.scheduledDrawRevision = null }
  }

  private fun calculateSummary(
    downSnapshots: List<TouchDownSnapshot>,
    deliverySnapshots: List<KeyDeliverySnapshot>,
    sampleSnapshots: List<TouchFrameSample>,
  ): TouchGateSummary {
    val warmupDowns = downSnapshots.filter { it.phase == TouchGatePhase.WARMUP }
    val measurementDowns = downSnapshots.filter { it.phase == TouchGatePhase.MEASUREMENT }
    val warmupDeliveries = deliverySnapshots.filter { it.phase == TouchGatePhase.WARMUP }
    val measurementDeliveries = deliverySnapshots.filter { it.phase == TouchGatePhase.MEASUREMENT }
    val acceptedWarmup = warmupDeliveries.filter { it.acceptedRevision != null }
    val accepted = measurementDeliveries.filter { it.acceptedRevision != null }
    val warmupSamples = sampleSnapshots.filter { it.phase == TouchGatePhase.WARMUP }
    val measuredSamples = sampleSnapshots.filter { it.phase == TouchGatePhase.MEASUREMENT }
    val expectedWarmup = List(warmupTarget) { index ->
      expectedPattern[index % expectedPattern.size]
    }
    val expected = List(measurementTarget) { index ->
      expectedPattern[(warmupTarget + index) % expectedPattern.size]
    }
    val actual = accepted.map(KeyDeliverySnapshot::jamo)
    val missingCount = (measurementTarget - accepted.size).coerceAtLeast(0)
    val duplicateCount = deliverySnapshots.count {
      it.sourceDownOrdinal == null
    } + (accepted.size - measurementTarget).coerceAtLeast(0)
    val undeliveredDownCount = measurementDowns.count { it.deliveryId == null }
    val rejectedDeliveryCount = measurementDeliveries.count { it.acceptedRevision == null }
    val warmupUndeliveredDownCount = warmupDowns.count { it.deliveryId == null }
    val warmupRejectedDeliveryCount = warmupDeliveries.count { it.acceptedRevision == null }
    val unknownDownCount = downSnapshots.count { it.key == null }
    val overflowDownCount = downSnapshots.count { it.phase == TouchGatePhase.OVERFLOW }
    val expectedOverlapPairCount = measurementTarget / expectedPattern.size
    var actualOverlapPairCount = 0
    accepted.chunked(expectedPattern.size).forEach { group ->
      if (group.size != expectedPattern.size) return@forEach
      val keysMatch = group.map(KeyDeliverySnapshot::jamo) == expectedPattern
      val firstPointer = group.first().pointerId
      val last = group.last()
      if (
        keysMatch &&
        firstPointer != null &&
        last.pointerId != null &&
        firstPointer != last.pointerId &&
        firstPointer in last.activePointerIds &&
        last.pointerId in last.activePointerIds
      ) {
        actualOverlapPairCount += 1
      }
    }
    val latencies = measuredSamples.map { it.eventToFrameCommitMillis.toDouble() }
    val p50 = percentileNearestRank(latencies, 0.50)
    val p95 = percentileNearestRank(latencies, 0.95)
    val max = latencies.maxOrNull()
    val sampleCountPass = measuredSamples.size == measurementTarget
    val warmupPass =
      warmupSamples.size == warmupTarget &&
        warmupDowns.size == warmupTarget &&
        acceptedWarmup.size == warmupTarget &&
        warmupUndeliveredDownCount == 0 &&
        warmupRejectedDeliveryCount == 0 &&
        acceptedWarmup.map(KeyDeliverySnapshot::jamo) == expectedWarmup
    val sequencePass = actual == expected
    val overlapPass =
      expectedOverlapPairCount > 0 && actualOverlapPairCount == expectedOverlapPairCount
    val latencyPass = sampleCountPass && p95 != null && p95 <= latencyThresholdMillis
    val hardwareFrameCommitAvailable = Build.VERSION.SDK_INT >= 29 && hardwareAccelerated
    val integrityPass =
      missingCount == 0 &&
        duplicateCount == 0 &&
        undeliveredDownCount == 0 &&
        rejectedDeliveryCount == 0 &&
        unknownDownCount == 0 &&
        overflowDownCount == 0
    return TouchGateSummary(
      expectedWarmupCount = warmupTarget,
      actualWarmupCount = warmupSamples.size,
      rawWarmupDownCount = warmupDowns.size,
      acceptedWarmupDeliveryCount = acceptedWarmup.size,
      warmupUndeliveredDownCount = warmupUndeliveredDownCount,
      warmupRejectedDeliveryCount = warmupRejectedDeliveryCount,
      warmupPass = warmupPass,
      expectedMeasurementCount = measurementTarget,
      actualMeasurementCount = measuredSamples.size,
      rawMeasurementDownCount = measurementDowns.size,
      missingCount = missingCount,
      duplicateCount = duplicateCount,
      undeliveredDownCount = undeliveredDownCount,
      rejectedDeliveryCount = rejectedDeliveryCount,
      unknownDownCount = unknownDownCount,
      overflowDownCount = overflowDownCount,
      sequencePass = sequencePass,
      expectedOverlapPairCount = expectedOverlapPairCount,
      actualOverlapPairCount = actualOverlapPairCount,
      overlapPass = overlapPass,
      maxConcurrentPointerCount = maxConcurrentPointerCount,
      p50EventToFrameCommitMillis = p50,
      p95EventToFrameCommitMillis = p95,
      maxEventToFrameCommitMillis = max,
      latencyThresholdMillis = latencyThresholdMillis,
      sampleCountPass = sampleCountPass,
      latencyPass = latencyPass,
      hardwareFrameCommitAvailable = hardwareFrameCommitAvailable,
      overallPass =
        warmupPass &&
          integrityPass &&
          sequencePass &&
          overlapPass &&
          latencyPass &&
          hardwareFrameCommitAvailable &&
          frameCommitErrors.isEmpty(),
    )
  }

  private data class MutableTouchDown(
    val ordinal: Int?,
    val pointerId: Long,
    val key: Char?,
    val phase: TouchGatePhase,
    val timelineSequence: Long,
    val eventUptimeMillis: Long,
    val handlerUptimeMillis: Long,
    val handlerElapsedRealtimeNanos: Long,
    var deliveryId: Long? = null,
    var upTimelineSequence: Long? = null,
  ) {
    fun snapshot() = TouchDownSnapshot(
      ordinal = ordinal,
      pointerId = pointerId,
      key = key,
      phase = phase,
      timelineSequence = timelineSequence,
      eventUptimeMillis = eventUptimeMillis,
      handlerUptimeMillis = handlerUptimeMillis,
      handlerElapsedRealtimeNanos = handlerElapsedRealtimeNanos,
      deliveryId = deliveryId,
      upTimelineSequence = upTimelineSequence,
    )
  }

  private data class MutableKeyDelivery(
    val id: Long,
    val timelineSequence: Long,
    val jamo: Char,
    val phase: TouchGatePhase,
    val sourceDownOrdinal: Int?,
    val pointerId: Long?,
    val callbackUptimeMillis: Long,
    val callbackElapsedRealtimeNanos: Long,
    val activePointerIds: List<Long>,
    var acceptedRevision: Long? = null,
    var acceptedUptimeMillis: Long? = null,
    var acceptedElapsedRealtimeNanos: Long? = null,
    var scheduledDrawRevision: Long? = null,
    var sampled: Boolean = false,
  ) {
    fun snapshot() = KeyDeliverySnapshot(
      id = id,
      timelineSequence = timelineSequence,
      jamo = jamo,
      phase = phase,
      sourceDownOrdinal = sourceDownOrdinal,
      pointerId = pointerId,
      callbackUptimeMillis = callbackUptimeMillis,
      callbackElapsedRealtimeNanos = callbackElapsedRealtimeNanos,
      activePointerIds = activePointerIds,
      acceptedRevision = acceptedRevision,
      acceptedUptimeMillis = acceptedUptimeMillis,
      acceptedElapsedRealtimeNanos = acceptedElapsedRealtimeNanos,
    )
  }
}

internal object TouchGateJsonReportWriter {
  fun write(
    context: Context,
    snapshot: TouchGateSnapshot,
  ): File {
    val report = JSONObject()
      .put("schemaVersion", 1)
      .put("gateName", snapshot.gateName)
      .put("capturedAtUtc", snapshot.capturedAtUtc)
      .put("buildVariant", "debugAndroidTest")
      .put("targetPackage", context.packageName)
      .put("device", JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("brand", Build.BRAND)
        .put("model", Build.MODEL)
        .put("device", Build.DEVICE)
        .put("fingerprint", Build.FINGERPRINT)
        .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        .put("apiLevel", Build.VERSION.SDK_INT)
        .put("refreshRateHz", snapshot.refreshRateHz.toDouble())
        .put("hardwareAccelerated", snapshot.hardwareAccelerated))
      .put("plan", JSONObject()
        .put("warmupCount", snapshot.warmupTarget)
        .put("measurementCount", snapshot.measurementTarget)
        .put("latencyMetric", "pointer event uptime to ViewTreeObserver frame commit"))
      .put("summary", snapshot.summary.toJson())
      .put("frameCommitErrors", JSONArray(snapshot.frameCommitErrors))
      .put("rawTouches", JSONArray().also { array ->
        snapshot.rawTouches.forEach { array.put(it.toJson()) }
      })
      .put("deliveries", JSONArray().also { array ->
        snapshot.deliveries.forEach { array.put(it.toJson()) }
      })
      .put("rawSamples", JSONArray().also { array ->
        snapshot.samples.forEach { array.put(it.toJson()) }
      })

    val directory = requireNotNull(context.getExternalFilesDir("m2-touch-gate")) {
      "App external files directory is unavailable"
    }
    check(directory.exists() || directory.mkdirs()) {
      "Could not create ${directory.absolutePath}"
    }
    val safeGateName = snapshot.gateName.lowercase(Locale.US).replace(Regex("[^a-z0-9-]+"), "-")
    val file = File(directory, "${safeGateName}-${System.currentTimeMillis()}.json")
    file.writeText(report.toString(2), Charsets.UTF_8)
    return file
  }
}

private fun RawTouchRecord.toJson() = JSONObject()
  .put("timelineSequence", timelineSequence)
  .put("kind", kind.name)
  .put("pointerId", pointerId)
  .putNullable("key", key?.toString())
  .put("phase", phase.name)
  .put("eventUptimeMillis", eventUptimeMillis)
  .put("handlerUptimeMillis", handlerUptimeMillis)
  .put("handlerElapsedRealtimeNanos", handlerElapsedRealtimeNanos)
  .put("activePointerCount", activePointerCount)

private fun KeyDeliverySnapshot.toJson() = JSONObject()
  .put("id", id)
  .put("timelineSequence", timelineSequence)
  .put("jamo", jamo.toString())
  .put("phase", phase.name)
  .putNullable("sourceDownOrdinal", sourceDownOrdinal)
  .putNullable("pointerId", pointerId)
  .put("callbackUptimeMillis", callbackUptimeMillis)
  .put("callbackElapsedRealtimeNanos", callbackElapsedRealtimeNanos)
  .put("activePointerIds", JSONArray(activePointerIds))
  .putNullable("acceptedRevision", acceptedRevision)
  .putNullable("acceptedUptimeMillis", acceptedUptimeMillis)
  .putNullable("acceptedElapsedRealtimeNanos", acceptedElapsedRealtimeNanos)

private fun TouchFrameSample.toJson() = JSONObject()
  .put("phase", phase.name)
  .put("downOrdinal", downOrdinal)
  .put("pointerId", pointerId)
  .put("jamo", jamo.toString())
  .put("eventUptimeMillis", eventUptimeMillis)
  .put("initialHandlerUptimeMillis", initialHandlerUptimeMillis)
  .put("initialHandlerElapsedRealtimeNanos", initialHandlerElapsedRealtimeNanos)
  .put("deliveryUptimeMillis", deliveryUptimeMillis)
  .put("deliveryElapsedRealtimeNanos", deliveryElapsedRealtimeNanos)
  .put("acceptedRevision", acceptedRevision)
  .put("acceptedUptimeMillis", acceptedUptimeMillis)
  .put("acceptedElapsedRealtimeNanos", acceptedElapsedRealtimeNanos)
  .put("frameCommitUptimeMillis", frameCommitUptimeMillis)
  .put("frameCommitElapsedRealtimeNanos", frameCommitElapsedRealtimeNanos)
  .put("eventToAcceptedMillis", eventToAcceptedMillis)
  .put("acceptedToFrameCommitMillis", acceptedToFrameCommitMillis)
  .put("eventToFrameCommitMillis", eventToFrameCommitMillis)
  .put("activePointerIdsAtAcceptance", JSONArray(activePointerIdsAtAcceptance))

private fun TouchGateSummary.toJson() = JSONObject()
  .put("expectedWarmupCount", expectedWarmupCount)
  .put("actualWarmupCount", actualWarmupCount)
  .put("rawWarmupDownCount", rawWarmupDownCount)
  .put("acceptedWarmupDeliveryCount", acceptedWarmupDeliveryCount)
  .put("warmupUndeliveredDownCount", warmupUndeliveredDownCount)
  .put("warmupRejectedDeliveryCount", warmupRejectedDeliveryCount)
  .put("warmupPass", warmupPass)
  .put("expectedMeasurementCount", expectedMeasurementCount)
  .put("actualMeasurementCount", actualMeasurementCount)
  .put("rawMeasurementDownCount", rawMeasurementDownCount)
  .put("missingCount", missingCount)
  .put("duplicateCount", duplicateCount)
  .put("undeliveredDownCount", undeliveredDownCount)
  .put("rejectedDeliveryCount", rejectedDeliveryCount)
  .put("unknownDownCount", unknownDownCount)
  .put("overflowDownCount", overflowDownCount)
  .put("sequencePass", sequencePass)
  .put("expectedOverlapPairCount", expectedOverlapPairCount)
  .put("actualOverlapPairCount", actualOverlapPairCount)
  .put("overlapPass", overlapPass)
  .put("maxConcurrentPointerCount", maxConcurrentPointerCount)
  .putNullable("p50EventToFrameCommitMillis", p50EventToFrameCommitMillis)
  .putNullable("p95EventToFrameCommitMillis", p95EventToFrameCommitMillis)
  .putNullable("maxEventToFrameCommitMillis", maxEventToFrameCommitMillis)
  .put("latencyThresholdMillis", latencyThresholdMillis)
  .put("sampleCountPass", sampleCountPass)
  .put("latencyPass", latencyPass)
  .put("hardwareFrameCommitAvailable", hardwareFrameCommitAvailable)
  .put("overallPass", overallPass)

private fun JSONObject.putNullable(
  key: String,
  value: Any?,
): JSONObject = put(key, value ?: JSONObject.NULL)

private fun percentileNearestRank(
  values: List<Double>,
  percentile: Double,
): Double? {
  if (values.isEmpty()) return null
  val sorted = values.sorted()
  val index = (ceil(sorted.size * percentile).toInt() - 1).coerceIn(sorted.indices)
  return sorted[index]
}

private fun squaredDistanceToRect(
  point: Offset,
  rect: Rect,
): Float {
  val dx = when {
    point.x < rect.left -> rect.left - point.x
    point.x > rect.right -> point.x - rect.right
    else -> 0f
  }
  val dy = when {
    point.y < rect.top -> rect.top - point.y
    point.y > rect.bottom -> point.y - rect.bottom
    else -> 0f
  }
  return dx * dx + dy * dy
}
