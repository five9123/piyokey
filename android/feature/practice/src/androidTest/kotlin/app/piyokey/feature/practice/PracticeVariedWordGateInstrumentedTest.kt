package app.piyokey.feature.practice

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.core.session.PracticeItemResolution
import app.piyokey.core.session.PracticeSessionEvent
import app.piyokey.core.session.PracticeSessionReducer
import app.piyokey.core.session.PracticeSessionState
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Locale

internal val VARIED_WORD_GATE_TARGETS = listOf(
  "가",
  "한글",
  "과자",
  "귀여워요",
  "꽃",
  "값이",
  "읽어요",
  "괜찮아요",
  "빨대",
  "짜장면",
  "쌀",
  "야구",
  "예쁜 얘기",
  "피카츄",
  "태권도",
  "딸기",
)

@RunWith(AndroidJUnit4::class)
class PracticeVariedWordGateInstrumentedTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun representativePlan_coversComplexCompositionAndTheWholeKeyboard() {
    val sequences = VARIED_WORD_GATE_TARGETS.associateWith { target ->
      PracticeSessionReducer.initialState(target).targetJamoSequence
    }
    val allJamo = sequences.values.flatten().toSet()

    assertEquals(
      listOf('ㄱ', 'ㅗ', 'ㅏ', 'ㅈ', 'ㅏ'),
      sequences.getValue("과자"),
    )
    assertEquals(
      listOf('ㄱ', 'ㅏ', 'ㅂ', 'ㅅ', 'ㅇ', 'ㅣ'),
      sequences.getValue("값이"),
    )
    assertEquals(
      listOf('ㅇ', 'ㅣ', 'ㄹ', 'ㄱ', 'ㅇ', 'ㅓ', 'ㅇ', 'ㅛ'),
      sequences.getValue("읽어요"),
    )
    assertTrue(setOf('ㄲ', 'ㄸ', 'ㅃ', 'ㅉ', 'ㅆ', 'ㅒ', 'ㅖ').all(allJamo::contains))
    assertTrue(' ' in allJamo)
    assertTrue(DubeolsikLayout.topRow.any { it.base in allJamo })
    assertTrue(DubeolsikLayout.homeRow.any { it.base in allJamo })
    assertTrue(DubeolsikLayout.bottomRow.all { it.base in allJamo })
    assertTrue(
      allJamo.all { jamo ->
        jamo == ' ' || DubeolsikLayout.definitionFor(jamo) != null
      },
    )
  }

  /**
   * Opt-in only:
   * `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.piyokeyVariedWordGate=true`
   */
  @Test
  @SdkSuppress(minSdkVersion = 29)
  fun physicalDevice_variedWords_completeThroughTheRealPracticePath() {
    val arguments = InstrumentationRegistry.getArguments()
    assumeTrue(
      "Set runner arg piyokeyVariedWordGate=true to run the varied-word gate",
      arguments.getString("piyokeyVariedWordGate").equals("true", ignoreCase = true),
    )
    assumeTrue(
      "The M2 physical gate cannot run on a recognized emulator",
      Build.HARDWARE !in setOf("goldfish", "ranchu", "gce_x86"),
    )

    val monitor = VariedWordGateMonitor(VARIED_WORD_GATE_TARGETS)
    composeRule.setContent {
      MaterialTheme {
        PracticeVariedWordGateHost(
          targets = VARIED_WORD_GATE_TARGETS,
          monitor = monitor,
        )
      }
    }

    var waitFailure: Throwable? = null
    try {
      composeRule.waitUntil(timeoutMillis = 600_000) {
        monitor.isResultReady
      }
    } catch (failure: Throwable) {
      waitFailure = failure
    }

    composeRule.waitForIdle()
    val snapshot = monitor.snapshot()
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val report = VariedWordGateJsonReportWriter.write(targetContext, snapshot)
    println("PIYOKEY_M2_VARIED_WORD_GATE_REPORT=${report.absolutePath}")
    if (waitFailure != null) {
      throw AssertionError(
        "Varied-word gate timed out; partial report: ${report.absolutePath}",
        waitFailure,
      )
    }
    assertTrue(
      "Varied-word gate failed; inspect ${report.absolutePath}: $snapshot",
      snapshot.overallPass,
    )
  }
}

@Composable
private fun PracticeVariedWordGateHost(
  targets: List<String>,
  monitor: VariedWordGateMonitor,
) {
  var state by remember(targets) {
    mutableStateOf(PracticeSessionReducer.initialState(targets).also(monitor::start))
  }
  val dispatch: (PracticeSessionEvent) -> Unit = { event ->
    val before = state
    val reduction = PracticeSessionReducer.reduce(before, event)
    state = reduction.state
    monitor.onReduction(before, event, state)
  }
  val latestDispatch = rememberUpdatedState(dispatch)
  val pendingTransition = state.pendingTransition

  LaunchedEffect(pendingTransition?.token) {
    val transition = pendingTransition ?: return@LaunchedEffect
    delay(transition.delayMillis)
    latestDispatch.value(PracticeSessionEvent.Advance(transition.token))
  }

  Box(Modifier.fillMaxSize()) {
    PracticeScreen(
      state = state,
      onEvent = dispatch,
      modifier = Modifier.fillMaxSize(),
      keyboardOptions = PracticeKeyboardOptions(
        showsKeyGuide = true,
        showsRomanHints = false,
        hapticsEnabled = true,
      ),
    )
    VariedWordGateStatus(
      monitor = monitor,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 4.dp),
    )
  }
}

@Composable
private fun VariedWordGateStatus(
  monitor: VariedWordGateMonitor,
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

internal data class VariedWordKeyAttempt(
  val eventUptimeMillis: Long,
  val targetIndex: Int,
  val target: String,
  val enteredJamo: Char,
  val expectedJamo: Char?,
  val accepted: Boolean,
  val acceptedJamoCountAfter: Int,
  val mistakeCountAfter: Int,
)

internal data class VariedWordGateSnapshot(
  val gateName: String,
  val capturedAtUtc: String,
  val targets: List<String>,
  val targetJamoCounts: List<Int>,
  val completedItemCount: Int,
  val totalExpectedJamoCount: Int,
  val acceptedJamoCount: Int,
  val mistakeCount: Int,
  val accuracyPercent: Double,
  val isResultReady: Boolean,
  val itemResolutions: List<PracticeItemResolution>,
  val keyAttempts: List<VariedWordKeyAttempt>,
  val overallPass: Boolean,
)

internal class VariedWordGateMonitor(
  private val targets: List<String>,
) {
  private val lock = Any()
  private var latestState: PracticeSessionState? = null
  private val keyAttempts = mutableListOf<VariedWordKeyAttempt>()

  val isResultReady: Boolean
    get() = synchronized(lock) { latestState?.isResultReady == true }

  fun start(state: PracticeSessionState) = synchronized(lock) {
    require(state.targets == targets)
    latestState = state
    keyAttempts.clear()
  }

  fun onReduction(
    before: PracticeSessionState,
    event: PracticeSessionEvent,
    after: PracticeSessionState,
  ) = synchronized(lock) {
    if (event is PracticeSessionEvent.Key) {
      keyAttempts += VariedWordKeyAttempt(
        eventUptimeMillis = SystemClock.uptimeMillis(),
        targetIndex = before.currentTargetIndex,
        target = before.currentTarget,
        enteredJamo = event.jamo,
        expectedJamo = before.nextExpected,
        accepted = after.compositionRevision > before.compositionRevision,
        acceptedJamoCountAfter = after.acceptedJamoCount,
        mistakeCountAfter = after.mistakeCount,
      )
    }
    latestState = after
  }

  fun statusText(): String = synchronized(lock) {
    val state = latestState ?: return@synchronized "다양한 단어 준비 중…"
    if (state.isResultReady) {
      "완료 · ${state.completedItemCount}/${targets.size} · 오타 ${state.mistakeCount} · 손을 떼세요"
    } else {
      "다양한 단어 ${state.currentTargetIndex + 1}/${targets.size} · ${state.currentTarget} · 오타 ${state.mistakeCount}"
    }
  }

  fun snapshot(): VariedWordGateSnapshot = synchronized(lock) {
    val state = requireNotNull(latestState) { "Varied-word gate did not start" }
    val totalExpected = state.targetJamoCounts.sum()
    VariedWordGateSnapshot(
      gateName = "physical-varied-word-gate",
      capturedAtUtc = Instant.now().toString(),
      targets = targets.toList(),
      targetJamoCounts = state.targetJamoCounts.toList(),
      completedItemCount = state.completedItemCount,
      totalExpectedJamoCount = totalExpected,
      acceptedJamoCount = state.acceptedJamoCount,
      mistakeCount = state.mistakeCount,
      accuracyPercent = state.accuracyPercent,
      isResultReady = state.isResultReady,
      itemResolutions = state.itemResolutions.toList(),
      keyAttempts = keyAttempts.toList(),
      overallPass =
        state.isResultReady &&
          state.completedItemCount == targets.size &&
          state.acceptedJamoCount == totalExpected,
    )
  }
}

internal object VariedWordGateJsonReportWriter {
  fun write(
    context: Context,
    snapshot: VariedWordGateSnapshot,
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
        .put("apiLevel", Build.VERSION.SDK_INT))
      .put("plan", JSONObject()
        .put("targets", JSONArray(snapshot.targets))
        .put("targetJamoCounts", JSONArray(snapshot.targetJamoCounts))
        .put("totalExpectedJamoCount", snapshot.totalExpectedJamoCount)
        .put("mistakesAreInformational", true))
      .put("summary", JSONObject()
        .put("completedItemCount", snapshot.completedItemCount)
        .put("acceptedJamoCount", snapshot.acceptedJamoCount)
        .put("mistakeCount", snapshot.mistakeCount)
        .put("accuracyPercent", snapshot.accuracyPercent)
        .put("isResultReady", snapshot.isResultReady)
        .put("overallPass", snapshot.overallPass))
      .put("itemResolutions", JSONArray().also { array ->
        snapshot.itemResolutions.forEach { resolution ->
          array.put(JSONObject()
            .put("itemIndex", resolution.itemIndex)
            .put("target", snapshot.targets[resolution.itemIndex])
            .put("mistakeCount", resolution.mistakeCount)
            .put("mistakenJamoIndices", JSONArray(resolution.mistakenJamoIndices.sorted())))
        }
      })
      .put("keyAttempts", JSONArray().also { array ->
        snapshot.keyAttempts.forEach { attempt -> array.put(attempt.toJson()) }
      })

    val directory = requireNotNull(context.getExternalFilesDir("m2-varied-word-gate")) {
      "App external files directory is unavailable"
    }
    check(directory.exists() || directory.mkdirs()) {
      "Could not create ${directory.absolutePath}"
    }
    val safeGateName = snapshot.gateName.lowercase(Locale.US).replace(Regex("[^a-z0-9-]+"), "-")
    val file = File(directory, "$safeGateName-${System.currentTimeMillis()}.json")
    file.writeText(report.toString(2), Charsets.UTF_8)
    return file
  }
}

private fun VariedWordKeyAttempt.toJson() = JSONObject()
  .put("eventUptimeMillis", eventUptimeMillis)
  .put("targetIndex", targetIndex)
  .put("target", target)
  .put("enteredJamo", enteredJamo.toString())
  .put("expectedJamo", expectedJamo?.toString() ?: JSONObject.NULL)
  .put("accepted", accepted)
  .put("acceptedJamoCountAfter", acceptedJamoCountAfter)
  .put("mistakeCountAfter", mistakeCountAfter)
