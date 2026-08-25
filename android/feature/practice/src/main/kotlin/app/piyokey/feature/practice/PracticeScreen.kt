package app.piyokey.feature.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.piyokey.core.session.PracticeFeedback
import app.piyokey.core.session.ActiveDurationClock
import app.piyokey.core.session.PracticeSessionEffect
import app.piyokey.core.session.PracticeSessionEvent
import app.piyokey.core.session.PracticeSessionReducer
import app.piyokey.core.session.PracticeSessionState
import app.piyokey.core.session.PracticeSessionCheckpoint
import app.piyokey.core.session.TargetSyllableState
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Self-contained M2 entry point used by the Android app shell. */
@Composable
fun PracticeRoute(
  modifier: Modifier = Modifier,
  keyboardOptions: PracticeKeyboardOptions = PracticeKeyboardOptions(),
  targets: List<String>? = null,
  initialCheckpoint: PracticeSessionCheckpoint? = null,
  onCheckpointChanged: (PracticeSessionCheckpoint) -> Unit = {},
  onSessionCompleted: (PracticeSessionState, activeDurationMillis: Long) -> Unit = { _, _ -> },
) {
  val sampleTargets = listOf(
    stringResource(R.string.practice_sample_target_1),
    stringResource(R.string.practice_sample_target_2),
    stringResource(R.string.practice_sample_target_3),
  )
  val resolvedTargets = targets?.takeIf(List<String>::isNotEmpty) ?: sampleTargets
  var state by remember(resolvedTargets, initialCheckpoint) {
    mutableStateOf(
      initialCheckpoint?.let { PracticeSessionReducer.restoreState(resolvedTargets, it) }
        ?: PracticeSessionReducer.initialState(resolvedTargets),
    )
  }
  var scheduledAdvance by remember(state.targets) {
    mutableStateOf(
      state.pendingTransition?.let {
        PracticeSessionEffect.ScheduleAdvance(it.token, it.destination, it.delayMillis)
      },
    )
  }
  var activeClock by remember(resolvedTargets, initialCheckpoint) {
    mutableStateOf(
      ActiveDurationClock(initialCheckpoint?.activeDurationMillis ?: 0L)
        .start(android.os.SystemClock.elapsedRealtime()),
    )
  }
  val lifecycleOwner = LocalLifecycleOwner.current

  fun activeDuration(): Long = activeClock.duration(android.os.SystemClock.elapsedRealtime())

  val latestCheckpointCallback = rememberUpdatedState(onCheckpointChanged)
  DisposableEffect(lifecycleOwner, state) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> activeClock = activeClock.start(android.os.SystemClock.elapsedRealtime())
        Lifecycle.Event.ON_STOP -> {
          activeClock = activeClock.pause(android.os.SystemClock.elapsedRealtime())
          latestCheckpointCallback.value(PracticeSessionReducer.checkpoint(state, activeClock.accumulatedMillis))
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val dispatch: (PracticeSessionEvent) -> Unit = { event ->
    val reduction = PracticeSessionReducer.reduce(state, event)
    state = reduction.state
    val newSchedule = reduction.effects
      .filterIsInstance<PracticeSessionEffect.ScheduleAdvance>()
      .lastOrNull()
    scheduledAdvance = when {
      newSchedule != null -> newSchedule
      reduction.state.pendingTransition == null -> null
      else -> scheduledAdvance
    }
    latestCheckpointCallback.value(PracticeSessionReducer.checkpoint(state, activeDuration()))
  }
  val latestDispatch = rememberUpdatedState(dispatch)

  LaunchedEffect(scheduledAdvance?.transitionToken) {
    val schedule = scheduledAdvance ?: return@LaunchedEffect
    delay(schedule.delayMillis)
    latestDispatch.value(PracticeSessionEvent.Advance(schedule.transitionToken))
  }

  val latestCompletion = rememberUpdatedState(onSessionCompleted)
  LaunchedEffect(state.isResultReady) {
    if (state.isResultReady) latestCompletion.value(state, activeDuration())
  }

  PracticeScreen(
    state = state,
    onEvent = dispatch,
    keyboardOptions = keyboardOptions,
    modifier = modifier,
  )
}

@Composable
fun PracticeScreen(
  state: PracticeSessionState,
  onEvent: (PracticeSessionEvent) -> Unit,
  modifier: Modifier = Modifier,
  keyboardOptions: PracticeKeyboardOptions = PracticeKeyboardOptions(),
) {
  val shake = remember { Animatable(0f) }
  val errorFlash = remember { Animatable(0f) }
  val compositionScale = remember { Animatable(1f) }
  val joinProgress = remember { Animatable(1f) }
  val completionProgress = remember { Animatable(0f) }

  LaunchedEffect(state.feedbackRevision) {
    when (state.feedback) {
      is PracticeFeedback.Incorrect -> {
        coroutineScope {
          launch {
            errorFlash.snapTo(0.20f)
            errorFlash.animateTo(0f, tween(durationMillis = 220))
          }
          launch {
            val shakeSequence = listOf(-8f, 8f, -6f, 5f, -3f, 0f)
            shakeSequence.forEach { target ->
              shake.animateTo(target, tween(durationMillis = 30))
            }
          }
        }
      }
      else -> {
        shake.snapTo(0f)
        errorFlash.snapTo(0f)
      }
    }
  }

  LaunchedEffect(state.compositionRevision) {
    if (state.shouldAnimateSyllableJoin) {
      compositionScale.snapTo(0.84f)
      joinProgress.snapTo(0f)
      coroutineScope {
        launch {
          compositionScale.animateTo(1.07f, tween(durationMillis = 95))
          compositionScale.animateTo(1f, tween(durationMillis = 80))
        }
        launch {
          joinProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
              durationMillis = PracticeMotion.SyllableJoinDurationMillis,
              easing = FastOutSlowInEasing,
            ),
          )
        }
      }
    } else {
      compositionScale.snapTo(1f)
      joinProgress.snapTo(1f)
    }
  }

  LaunchedEffect(state.pendingTransition?.token) {
    if (state.pendingTransition == null) {
      completionProgress.snapTo(0f)
    } else {
      completionProgress.snapTo(0f)
      completionProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
      )
    }
  }

  val feedbackDescription = when (val feedback = state.feedback) {
    PracticeFeedback.Idle -> stringResource(R.string.practice_feedback_idle)
    PracticeFeedback.Correct -> stringResource(R.string.practice_feedback_correct)
    is PracticeFeedback.Incorrect -> stringResource(
      R.string.practice_feedback_incorrect,
      feedback.expected.toString(),
    )
    PracticeFeedback.Complete -> stringResource(R.string.practice_feedback_complete)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("practice-screen")
      .background(PracticeColors.Background)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    Column(Modifier.fillMaxSize()) {
      PracticeProgressHeader(state)

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
      ) {
        TargetCard(
          state = state,
          errorFlash = errorFlash.value,
          shakeOffset = shake.value,
        )
        CompositionCard(
          state = state,
          assemblyScale = compositionScale.value,
          joinProgress = joinProgress.value,
          completionProgress = completionProgress.value,
        )
      }

      DubeolsikKeyboard(
        nextExpectedJamo = state.nextExpected,
        onJamo = { onEvent(PracticeSessionEvent.Key(it)) },
        onBackspace = { onEvent(PracticeSessionEvent.Backspace) },
        options = keyboardOptions,
      )
    }

    CompletionSparkles(
      progress = completionProgress.value,
      modifier = Modifier.fillMaxSize(),
    )

    // Feedback remains available to assistive technology without adding a status row above keys.
    Box(
      Modifier
        .size(1.dp)
        .alpha(0f)
        .semantics { contentDescription = feedbackDescription },
    )
  }
}

@Composable
private fun PracticeProgressHeader(state: PracticeSessionState) {
  val totalJamoCount = state.targetJamoCounts.sum().coerceAtLeast(1)
  val progress = state.acceptedJamoCount.toFloat() / totalJamoCount.toFloat()
  val progressLabel = stringResource(
    R.string.practice_overall_progress_accessibility,
    state.currentTargetIndex + 1,
    state.targets.size,
  )

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(48.dp)
      .padding(horizontal = 18.dp)
      .semantics(mergeDescendants = true) { contentDescription = progressLabel },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    LinearProgressIndicator(
      progress = { progress.coerceIn(0f, 1f) },
      modifier = Modifier
        .weight(1f)
        .height(8.dp)
        .clip(RoundedCornerShape(99.dp)),
      color = PracticeColors.Accent,
      trackColor = PracticeColors.Track,
      strokeCap = StrokeCap.Round,
      gapSize = 0.dp,
      drawStopIndicator = {},
    )
    Spacer(Modifier.width(10.dp))
    Text(
      text = stringResource(
        R.string.practice_overall_progress,
        state.currentTargetIndex + 1,
        state.targets.size,
      ),
      color = PracticeColors.MutedInk,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun TargetCard(
  state: PracticeSessionState,
  errorFlash: Float,
  shakeOffset: Float,
) {
  val shape = RoundedCornerShape(24.dp)
  val progressDescription = stringResource(
    R.string.practice_jamo_progress_accessibility,
    state.completedJamoCount.coerceAtMost(state.targetJamoSequence.size),
    state.targetJamoSequence.size,
  )

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer { translationX = shakeOffset }
      .semantics {
        contentDescription = state.currentTarget
        stateDescription = progressDescription
      },
    color = PracticeColors.Card,
    shape = shape,
    shadowElevation = 7.dp,
  ) {
    Box {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp),
      ) {
        TargetSyllableTrack(state)
        JamoProgressTrack(state)
      }
      Box(
        Modifier
          .matchParentSize()
          .background(PracticeColors.Error.copy(alpha = errorFlash), shape),
      )
    }
  }
}

@Composable
private fun TargetSyllableTrack(state: PracticeSessionState) {
  val count = state.targetSyllableProgress.size.coerceAtLeast(1)
  val currentOffset = state.targetSyllableProgress.indexOfFirst { unit ->
    state.completedJamoCount in unit.jamoRange && unit.state != TargetSyllableState.COMPLETED
  }
  val spaceDescription = stringResource(R.string.keyboard_space)

  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val spacing = if (count >= 8) 3.dp else 5.dp
    val desiredSize = when {
      count <= 4 -> 52.dp
      count <= 7 -> 42.dp
      else -> 32.dp
    }
    val fittedSize = ((maxWidth - spacing * (count - 1)) / count)
      .coerceAtMost(desiredSize)
      .coerceAtLeast(24.dp)
    val textSize = (fittedSize.value * 0.64f).coerceIn(16f, 34f).sp

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
    ) {
      state.targetSyllableProgress.forEachIndexed { index, unit ->
        val completed = unit.state == TargetSyllableState.COMPLETED
        val current = index == currentOffset && !state.isCurrentTargetComplete
        Box(
          modifier = Modifier
            .size(fittedSize)
            .clip(RoundedCornerShape(13.dp))
            .background(if (completed) PracticeColors.SuccessSoft else PracticeColors.TargetChip)
            .then(
              if (current) {
                Modifier.border(2.dp, PracticeColors.Accent, RoundedCornerShape(13.dp))
              } else {
                Modifier
              },
            )
            .semantics {
              contentDescription = if (unit.character.isBlank()) {
                spaceDescription
              } else {
                unit.character
              }
            },
          contentAlignment = Alignment.Center,
        ) {
          if (unit.character.isBlank()) {
            Box(
              Modifier
                .fillMaxWidth(0.56f)
                .height(4.dp)
                .background(
                  PracticeColors.MutedInk.copy(alpha = 0.35f),
                  RoundedCornerShape(99.dp),
                ),
            )
          } else {
            Text(
              text = unit.character,
              color = if (completed) PracticeColors.SuccessInk else PracticeColors.Ink,
              fontSize = textSize,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun JamoProgressTrack(state: PracticeSessionState) {
  val listState = rememberLazyListState()
  val currentIndex = state.completedJamoCount.coerceAtMost(state.targetJamoSequence.lastIndex)

  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val contentWidth = JamoTrackMetrics.contentWidthDp(state.targetJamoSequence.size).dp
    val requiresAutoTracking = contentWidth > maxWidth
    val edgePadding = if (requiresAutoTracking) {
      (maxWidth / 2 - 18.dp).coerceAtLeast(20.dp)
    } else {
      20.dp
    }

    LaunchedEffect(state.currentTargetIndex, currentIndex, requiresAutoTracking) {
      if (state.targetJamoSequence.isEmpty() || !requiresAutoTracking) {
        return@LaunchedEffect
      }
      listState.scrollToItem(currentIndex)
      val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
        ?: return@LaunchedEffect
      val viewportCenter = (
        listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset
        ) / 2
      val itemCenter = item.offset + item.size / 2
      listState.animateScrollBy((itemCenter - viewportCenter).toFloat())
    }

    LazyRow(
      state = listState,
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = edgePadding),
      horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
      itemsIndexed(state.targetJamoSequence) { index, jamo ->
        val completed = index < state.completedJamoCount
        val current = index == state.completedJamoCount && !state.isCurrentTargetComplete
        val shape = RoundedCornerShape(10.dp)
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (completed) PracticeColors.Success else PracticeColors.JamoChip)
            .then(
              if (current) Modifier.border(2.dp, PracticeColors.Accent, shape) else Modifier
            ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = if (jamo == ' ') "␣" else jamo.toString(),
            color = if (completed) Color.White else PracticeColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

internal object JamoTrackMetrics {
  private const val ChipWidthDp = 36f
  private const val ItemSpacingDp = 6f

  fun contentWidthDp(itemCount: Int): Float {
    require(itemCount >= 0)
    return ChipWidthDp * itemCount + ItemSpacingDp * (itemCount - 1).coerceAtLeast(0)
  }

  fun requiresAutoTracking(
    itemCount: Int,
    availableWidthDp: Float,
  ): Boolean = contentWidthDp(itemCount) > availableWidthDp
}

@Composable
private fun CompositionCard(
  state: PracticeSessionState,
  assemblyScale: Float,
  joinProgress: Float,
  completionProgress: Float,
) {
  val shape = RoundedCornerShape(24.dp)
  val cardColor = when (state.feedback) {
    PracticeFeedback.Idle -> PracticeColors.Card
    PracticeFeedback.Correct -> PracticeColors.CorrectCard
    is PracticeFeedback.Incorrect -> PracticeColors.Card
    PracticeFeedback.Complete -> PracticeColors.CompleteCard
  }
  val compositionLabel = stringResource(R.string.practice_composition_label)
  val placeholder = stringResource(R.string.practice_composition_placeholder)
  val preview = state.composingText.ifEmpty { state.enteredText.takeLast(1) }.ifEmpty { "…" }
  val entered = state.enteredText.ifEmpty { placeholder }
  val incomingSlideDistance = with(LocalDensity.current) {
    PracticeMotion.IncomingJamoSlideDistanceDp.dp.toPx()
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .scale(1f + sin(completionProgress * PI).toFloat() * 0.035f)
      .semantics {
        contentDescription = compositionLabel
        stateDescription = entered
      },
    color = cardColor,
    shape = shape,
    shadowElevation = 4.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier = Modifier
          .size(76.dp)
          .scale(assemblyScale)
          .background(PracticeColors.PreviewCircle, CircleShape)
          .border(2.dp, PracticeColors.AccentSoft, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = preview,
          color = PracticeColors.Ink,
          fontSize = 33.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          maxLines = 1,
          softWrap = false,
        )
        val incomingJamo = state.lastAcceptedKey
        if (state.shouldAnimateSyllableJoin && incomingJamo != null && joinProgress < 1f) {
          Text(
            text = incomingJamo.toString(),
            modifier = Modifier
              .graphicsLayer {
                translationX = incomingSlideDistance * (1f - joinProgress)
                alpha = (1f - joinProgress).coerceIn(0f, 1f)
                val incomingScale = 0.82f + joinProgress * 0.18f
                scaleX = incomingScale
                scaleY = incomingScale
              }
              .clearAndSetSemantics {},
            color = PracticeColors.Accent,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
          )
        }
      }

      Spacer(Modifier.width(16.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = compositionLabel,
          color = PracticeColors.MutedInk,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = entered,
          color = if (state.enteredText.isEmpty()) PracticeColors.MutedInk else PracticeColors.Ink,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
        )
      }
    }
  }
}

@Composable
private fun CompletionSparkles(
  progress: Float,
  modifier: Modifier = Modifier,
) {
  if (progress <= 0f || progress >= 1f) return
  Canvas(modifier = modifier) {
    val eased = FastOutSlowInEasing.transform(progress)
    val center = Offset(size.width / 2f, size.height * 0.43f)
    val radius = size.minDimension * (0.05f + eased * 0.20f)
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val colors = listOf(
      PracticeColors.Accent,
      PracticeColors.SparkleYellow,
      PracticeColors.Success,
      PracticeColors.SparklePink,
    )

    repeat(8) { index ->
      val angle = index * (2.0 * PI / 8.0) - PI / 2.0
      val particle = Offset(
        x = center.x + cos(angle).toFloat() * radius,
        y = center.y + sin(angle).toFloat() * radius,
      )
      val color = colors[index % colors.size].copy(alpha = alpha)
      if (index % 2 == 0) {
        val arm = 5.dp.toPx() * (1f - progress * 0.45f)
        drawLine(color, particle - Offset(arm, 0f), particle + Offset(arm, 0f), 2.dp.toPx())
        drawLine(color, particle - Offset(0f, arm), particle + Offset(0f, arm), 2.dp.toPx())
      } else {
        drawCircle(color, radius = 4.dp.toPx() * (1f - progress * 0.35f), center = particle)
      }
    }
  }
}

private object PracticeColors {
  val Background = Color(0xFFFFF8F1)
  val Card = Color(0xFFFFFFFF)
  val TargetChip = Color(0xFFF3F0F8)
  val JamoChip = Color(0xFFF0EDF5)
  val PreviewCircle = Color(0xFFF5F0FF)
  val Track = Color(0xFFE9E3F0)
  val Accent = Color(0xFF7557FF)
  val AccentSoft = Color(0xFFB9A8FF)
  val Ink = Color(0xFF252032)
  val MutedInk = Color(0xFF716A7B)
  val Success = Color(0xFF35B879)
  val SuccessSoft = Color(0xFFDDF7E9)
  val SuccessInk = Color(0xFF176A45)
  val Error = Color(0xFFFF5D6C)
  val CorrectCard = Color(0xFFF2FFF8)
  val CompleteCard = Color(0xFFEFFFF6)
  val SparkleYellow = Color(0xFFFFC83D)
  val SparklePink = Color(0xFFFF7FB2)
}
