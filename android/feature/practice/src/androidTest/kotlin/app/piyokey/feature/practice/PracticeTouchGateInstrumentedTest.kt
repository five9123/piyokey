package app.piyokey.feature.practice

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.advanceEventTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeTouchGateInstrumentedTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun oneGesture_twoPointerRollover_deliversGiyeokThenA_withoutLossOrDuplication() {
    val monitor = PracticeTouchGateMonitor(
      gateName = "automated-rollover",
      warmupTarget = 0,
      measurementTarget = 2,
    )
    val positions = launchGate(
      monitor = monitor,
      targetCount = 1,
      showPhysicalInstructions = false,
    )

    composeRule
      .onNodeWithTag(TOUCH_GATE_ROOT_TAG, useUnmergedTree = true)
      .performTouchInput {
        down(pointerId = 0, position = positions.giyeok)
        advanceEventTime(8)
        down(pointerId = 1, position = positions.a)
        advanceEventTime(8)
        up(pointerId = 1)
        advanceEventTime(8)
        up(pointerId = 0)
      }

    composeRule.waitUntil(timeoutMillis = 5_000) {
      monitor.acceptedDeliveryCount == 2 && monitor.snapshot().samples.size == 2
    }
    val snapshot = monitor.snapshot()
    val accepted = snapshot.deliveries.filter { it.acceptedRevision != null }
    val targetDowns = snapshot.downs.filter { it.key != null }

    assertEquals(listOf('ㄱ', 'ㅏ'), accepted.map(KeyDeliverySnapshot::jamo))
    assertEquals(listOf('ㄱ', 'ㅏ'), targetDowns.map(TouchDownSnapshot::key))
    assertEquals(2, targetDowns.size)
    assertNotEquals(targetDowns[0].pointerId, targetDowns[1].pointerId)
    assertEquals(2, accepted[1].activePointerIds.size)
    assertTrue(targetDowns[0].pointerId in accepted[1].activePointerIds)
    assertTrue(targetDowns[1].pointerId in accepted[1].activePointerIds)

    val pointerZeroUp = snapshot.rawTouches.single {
      it.kind == RawTouchKind.UP && it.pointerId == targetDowns[0].pointerId
    }
    assertTrue(
      "ㅏ must be delivered while the ㄱ pointer is still down",
      accepted[1].timelineSequence < pointerZeroUp.timelineSequence,
    )
    assertEquals(2, snapshot.summary.maxConcurrentPointerCount)
    assertEquals(0, snapshot.summary.missingCount)
    assertEquals(0, snapshot.summary.duplicateCount)
    assertEquals(0, snapshot.summary.undeliveredDownCount)
    assertEquals(0, snapshot.summary.rejectedDeliveryCount)
    assertEquals(0, snapshot.summary.unknownDownCount)
    assertEquals(2, snapshot.samples.size)
    assertTrue(snapshot.frameCommitErrors.isEmpty())
    assertTrue(snapshot.summary.hardwareFrameCommitAvailable)
    assertTrue(snapshot.summary.sequencePass)
    assertTrue(snapshot.summary.overlapPass)
  }

  /**
   * Opt-in only:
   * `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.piyokeyPhysicalGate=true`
   */
  @Test
  @SdkSuppress(minSdkVersion = 29)
  fun physicalDevice_touchDownToCommittedFrame_p95AtMost50Millis() {
    val arguments = InstrumentationRegistry.getArguments()
    assumeTrue(
      "Set runner arg piyokeyPhysicalGate=true to run the physical gate",
      arguments.getString("piyokeyPhysicalGate").equals("true", ignoreCase = true),
    )
    assumeTrue(
      "The M2 physical gate cannot run on a recognized emulator",
      Build.HARDWARE !in setOf("goldfish", "ranchu", "gce_x86"),
    )

    val monitor = PracticeTouchGateMonitor(
      gateName = "physical-touch-frame-gate",
      warmupTarget = 20,
      measurementTarget = 100,
    )
    launchGate(
      monitor = monitor,
      targetCount = 60,
      showPhysicalInstructions = true,
    )

    var waitFailure: Throwable? = null
    try {
      composeRule.waitUntil(timeoutMillis = 180_000) {
        monitor.measurementSampleCount >= 100
      }
    } catch (failure: Throwable) {
      waitFailure = failure
    }

    val snapshot = monitor.snapshot()
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val report = TouchGateJsonReportWriter.write(targetContext, snapshot)
    println("PIYOKEY_M2_TOUCH_GATE_REPORT=${report.absolutePath}")
    if (waitFailure != null) {
      throw AssertionError(
        "Physical gate timed out; partial report: ${report.absolutePath}",
        waitFailure,
      )
    }

    assertTrue(
      "Physical gate failed; inspect ${report.absolutePath}: ${snapshot.summary}",
      snapshot.summary.overallPass,
    )
  }

  private fun launchGate(
    monitor: PracticeTouchGateMonitor,
    targetCount: Int,
    showPhysicalInstructions: Boolean,
  ): GatePositions {
    composeRule.setContent {
      MaterialTheme {
        PracticeTouchGateHost(
          monitor = monitor,
          targetCount = targetCount,
          showPhysicalInstructions = showPhysicalInstructions,
        )
      }
    }

    val rootBounds = composeRule
      .onNodeWithTag(TOUCH_GATE_ROOT_TAG, useUnmergedTree = true)
      .fetchSemanticsNode()
      .boundsInRoot
    val giyeokBounds = composeRule
      .onNodeWithContentDescription("ㄱ", useUnmergedTree = true)
      .fetchSemanticsNode()
      .boundsInRoot
      .relativeTo(rootBounds)
    val aBounds = composeRule
      .onNodeWithContentDescription("ㅏ", useUnmergedTree = true)
      .fetchSemanticsNode()
      .boundsInRoot
      .relativeTo(rootBounds)
    val touchOutsetPx = 4f * InstrumentationRegistry
      .getInstrumentation()
      .targetContext
      .resources
      .displayMetrics
      .density

    composeRule.runOnIdle {
      monitor.arm(
        keyBoundsInHost = mapOf(
          'ㄱ' to giyeokBounds,
          'ㅏ' to aBounds,
        ),
        touchOutsetPx = touchOutsetPx,
      )
    }
    return GatePositions(
      giyeok = giyeokBounds.center,
      a = aBounds.center,
    )
  }

  private data class GatePositions(
    val giyeok: Offset,
    val a: Offset,
  )
}

private fun Rect.relativeTo(parent: Rect): Rect = Rect(
  left = left - parent.left,
  top = top - parent.top,
  right = right - parent.left,
  bottom = bottom - parent.top,
)
