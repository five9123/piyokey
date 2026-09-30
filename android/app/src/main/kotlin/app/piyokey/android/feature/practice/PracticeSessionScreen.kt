package app.piyokey.android.feature.practice

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.R
import app.piyokey.android.data.decks.appMeaning
import app.piyokey.android.data.decks.appReading
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.PracticePromptField
import app.piyokey.android.data.settings.PracticePromptOrder
import app.piyokey.android.feature.input.HangulKeyboardGeometry
import app.piyokey.android.feature.input.InputLatencyMonitor
import app.piyokey.android.feature.input.InputLatencyProbe
import app.piyokey.android.feature.input.Korean10KeyGeometry
import app.piyokey.android.feature.input.OsImeInputPanel
import app.piyokey.android.feature.input.OsImeInputSourceBannerLayer
import app.piyokey.android.feature.input.SessionKeyboard
import app.piyokey.android.feature.input.rememberSessionKeyboardState
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.session.SessionExitCover
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.practice.JapaneseMeaningDisplayText
import app.piyokey.core.domain.practice.PracticeFeedback
import app.piyokey.core.domain.practice.PracticeReactionEvent
import app.piyokey.core.domain.practice.PracticeRules
import app.piyokey.core.hangul.Korean10KeyInterpreter
import kotlinx.coroutines.launch

/**
 * Full-screen practice session destination (iOS `PracticeView` pushed with the tab bar hidden).
 * Inside it the app navigator also serves as the "tab" navigator, so pushes from the result
 * (deck detail, review practice) stack above the session.
 */
class PracticeSessionRoute(private val initial: PracticeSessionConfig) : Route {
  @Composable
  override fun Content() {
    val appNavigator = LocalAppNavigator.current
    var config by remember { mutableStateOf(initial) }
    CompositionLocalProvider(LocalTabNavigator provides appNavigator) {
      key(config.id) {
        PracticeSessionScreen(config, onReplaceConfig = { config = it }, onDismiss = { appNavigator.pop() })
      }
    }
  }
}

/** The practice session UI + its result (iOS `PracticeView` + `navigationDestination(PracticeResultView)`). */
@Composable
fun PracticeSessionScreen(
  config: PracticeSessionConfig,
  onReplaceConfig: (PracticeSessionConfig) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val keyboard = rememberSessionKeyboardState(config.allowsOsIme, config.allowsKorean10Key)
  val scope = rememberCoroutineScope()
  val activity = LocalActivity.current
  val runner = remember { PracticeSessionRunner(config, keyboard, scope) { activity } }
  runner.onDismiss = onDismiss
  runner.onReplaceConfig = onReplaceConfig
  val latency = remember { InputLatencyMonitor() }

  DisposableEffect(runner) {
    runner.onAppear()
    onDispose { runner.onDisappear() }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { runner.onBackground() }
  LifecycleEventEffect(Lifecycle.Event.ON_START) { runner.onForeground() }
  BackHandler(enabled = !runner.showsResult) {
    if (runner.isSessionSettingsPresented) runner.dismissSessionSettings() else onDismiss()
  }

  val colors = Piyo.colors
  Box(
    modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
      .testTag(PracticeTags.SCREEN),
  ) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
      SessionToolbar(runner)
      SessionBody(runner, Modifier.weight(1f))
      SessionKeyboard(
        state = keyboard,
        expectedNextJamo = runner.model.nextExpectedKey,
        osImeTarget = runner.model.target,
        osImeAcceptedText = runner.model.enteredText,
        onJamo = runner::input,
        onBackspace = runner::backspace,
        onKorean10KeyMistake = runner::recordMistake,
        onOsImeAcceptedSequence = runner::synchronizeOsIme,
        onOsImeConfirmedMismatch = runner::recordMistake,
        embedsOsImeField = !overlaysHiddenOsIme(runner),
        onInputStart = latency::beginInput,
        onOsImeInputStart = { SoundEngine.warmUp(runner.mascotCombo) },
      )
    }
    if (runner.model.isComplete) {
      key(runner.celebrationRevision) { CompletionCelebration(runner.celebrationRevision) }
    }
    InputLatencyProbe(latency)
    HiddenMistakeValue(runner.model.mistakeCount)
    AnimatedVisibility(
      runner.isSessionSettingsPresented,
      enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.97f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
      exit = fadeOut(tween(120)),
    ) {
      SessionSettingsOverlay(
        keyboard = keyboard,
        onClose = runner::dismissSessionSettings,
        onAutoSpeakChanged = runner::onAutoSpeakChanged,
        modifier = Modifier.statusBarsPadding(),
      )
    }
    SessionExitCover(runner.exitsAfterResultDismiss)
    AnimatedVisibility(
      runner.showsResult,
      enter = slideInHorizontally { it },
      exit = slideOutHorizontally { it },
    ) {
      PracticeResultScreen(runner)
    }
  }
}

@Composable
private fun overlaysHiddenOsIme(runner: PracticeSessionRunner): Boolean =
  runner.keyboard.usesOsIme && !Piyo.metrics.isExpanded

@Composable
private fun HiddenMistakeValue(count: Int) {
  val label = stringResource(R.string.practice_mistakes)
  Text(
    count.toString(),
    fontSize = Piyo.sp(1f),
    modifier = Modifier
      .size(1.dp)
      .testTag(PracticeTags.MISTAKES)
      .semantics {
        contentDescription = label
        stateDescription = count.toString()
      },
  )
}

@Composable
private fun SessionToolbar(runner: PracticeSessionRunner) {
  val colors = Piyo.colors
  val model = runner.model
  val progress = PracticeRules.overallProgress(model.currentTargetIndex, model.completedJamoCount, model.targetJamoSequence.size, model.targets.size)
  val counter = "${model.currentTargetIndex + 1} / ${model.targets.size}"
  val progressLabel = stringResource(R.string.practice_overall_progress)
  Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
    ToolbarIcon(Icons.Rounded.Close, stringResource(R.string.practice_end), PracticeTags.END) { runner.onDismiss() }
    Row(
      Modifier
        .weight(1f)
        .testTag(PracticeTags.OVERALL_PROGRESS)
        .semantics(mergeDescendants = true) {
          contentDescription = progressLabel
          stateDescription = counter
        },
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LinearProgressIndicator(
        progress = { progress.toFloat() },
        modifier = Modifier.width(if (Piyo.metrics.isExpanded) 180.dp else 128.dp),
        color = colors.accent,
        trackColor = colors.accentSoft.copy(alpha = 0.6f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
      )
      Text(counter, style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk), modifier = Modifier.padding(start = 8.dp))
    }
    ToolbarIcon(Icons.Rounded.Settings, stringResource(R.string.practice_session_settings), PracticeTags.SETTINGS) {
      runner.presentSessionSettings()
    }
  }
}

@Composable
private fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
  Box(
    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).testTag(tag).semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = Piyo.colors.accent)
  }
}

@Composable
private fun SessionBody(runner: PracticeSessionRunner, modifier: Modifier) {
  val metrics = Piyo.metrics
  val showsMascot by AppSettings.practiceShowsMascot.flow.collectAsState()
  val showsComposition by AppSettings.practiceShowsComposition.flow.collectAsState()
  val overlaysHidden = overlaysHiddenOsIme(runner)
  val landscape = metrics.isExpanded && !metrics.isTall
  val spacing = if (landscape) 8.dp else if (metrics.isExpanded) 20.dp else 12.dp
  val verticalPadding = if (landscape) 6.dp else if (metrics.isExpanded) 20.dp else 10.dp
  val expands = overlaysHidden || (metrics.isExpanded && (!landscape || metrics.availableHeight >= 800.dp))

  BoxWithConstraints(modifier.fillMaxWidth()) {
    val viewport = maxHeight
    if (overlaysHidden) {
      OsImeInputPanel(
        target = runner.model.target,
        acceptedText = runner.model.enteredText,
        resetRevision = runner.keyboard.resetRevision,
        onAcceptedSequence = runner::synchronizeOsIme,
        onConfirmedMismatch = runner::recordMistake,
        onInputStart = { SoundEngine.warmUp(runner.mascotCombo) },
        showsChrome = false,
        showsFocusRecovery = false,
        isFocusSuspended = runner.keyboard.isOsImeFocusSuspended,
        externalBanner = runner.keyboard.osImeBanner,
        modifier = Modifier.matchParentSize(),
      )
    }
    val available = (viewport - spacing - verticalPadding * 2).coerceAtLeast(0.dp)
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .centeredContent(metrics.sessionLaneMaxWidth)
        .padding(horizontal = if (metrics.isExpanded) 24.dp else 14.dp, vertical = verticalPadding),
      verticalArrangement = Arrangement.spacedBy(spacing, if (metrics.isExpanded) Alignment.CenterVertically else Alignment.Top),
    ) {
      TargetCard(runner, landscape, if (expands) available * 0.55f else 0.dp)
      if (showsMascot || showsComposition) {
        CompositionCard(runner, showsMascot, showsComposition, landscape, if (expands) available * 0.45f else 0.dp)
      }
    }
    if (overlaysHidden) {
      OsImeInputSourceBannerLayer(
        runner.keyboard.osImeBanner.value,
        modifier = Modifier.align(Alignment.TopCenter),
        testTag = "os_ime.input_source_warning.foreground",
      )
    }
  }
}

@Composable
private fun TargetCard(runner: PracticeSessionRunner, landscape: Boolean, minHeight: Dp) {
  val colors = Piyo.colors
  val metrics = Piyo.metrics
  val model = runner.model
  val showsTarget by AppSettings.practiceShowsTarget.flow.collectAsState()
  val showsMeaning by AppSettings.practiceShowsMeaning.flow.collectAsState()
  val showsReading by AppSettings.practiceShowsReading.flow.collectAsState()
  val showsJamo by AppSettings.practiceShowsJamo.flow.collectAsState()
  val orderRaw by AppSettings.practicePromptOrder.flow.collectAsState()
  val order = PracticePromptOrder.resolved(orderRaw)

  val shake = remember { Animatable(0f) }
  val flash = remember { Animatable(0f) }
  LaunchedEffect(model.feedbackRevision) {
    if (model.feedback is PracticeFeedback.Incorrect) {
      flash.snapTo(0.2f)
      kotlinx.coroutines.coroutineScope {
        launch { shake.animateTo(shake.value + 1f, tween(250, easing = LinearEasing)) }
        launch { flash.animateTo(0f, tween(220)) }
      }
    } else if (model.feedback == PracticeFeedback.Idle) {
      flash.snapTo(0f)
    }
  }

  val shape = RoundedCornerShape(24.dp)
  Box(
    Modifier
      .fillMaxWidth()
      .offset(x = shakeOffset(shake.value).dp)
      .shadow(14.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .errorFlash(flash.value, colors.error, 24f)
      .heightIn(min = minHeight)
      .testTag(PracticeTags.TARGET_CARD),
  ) {
    Column(
      Modifier.fillMaxWidth().padding(if (landscape) 6.dp else if (metrics.isExpanded) 24.dp else 14.dp),
      verticalArrangement = Arrangement.spacedBy(if (landscape) 4.dp else 9.dp, Alignment.CenterVertically),
    ) {
      if (!landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SpeakButton(runner) }
      }
      val source = runner.currentSource
      order.fields.forEach { field ->
        val fieldModifier = Modifier.padding(horizontal = if (landscape) 120.dp else 0.dp)
        when (field) {
          PracticePromptField.TARGET -> if (showsTarget) {
            key(model.currentTargetIndex) {
              TargetSyllableProgressView(
                units = model.targetSyllableProgress,
                fontScale = Piyo.fontScale * metrics.learningScale,
                progressValue = stringResource(R.string.practice_syllable_progress_value, model.completedSyllableCount, model.targetSyllableCount),
                modifier = fieldModifier,
              )
            }
          }
          PracticePromptField.MEANING -> {
            val meaning = source?.item?.appMeaning
            if (showsMeaning && !meaning.isNullOrEmpty()) MeaningText(JapaneseMeaningDisplayText.format(meaning), landscape, fieldModifier)
          }
          PracticePromptField.READING -> {
            val reading = source?.item?.appReading
            if (showsReading && !reading.isNullOrEmpty()) {
              Text(
                reading,
                fontSize = Piyo.sp(maxOf(14f, 17f)),
                color = colors.mutedInk.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
                modifier = fieldModifier.fillMaxWidth().testTag(PracticeTags.READING),
              )
            }
          }
        }
      }
      if (showsJamo) {
        key(model.currentTargetIndex) {
          JamoProgressTrack(
            sequence = model.targetJamoSequence,
            completedCount = model.completedJamoCount,
            progressValue = "${minOf(model.completedJamoCount, model.targetJamoSequence.size)} / ${model.targetJamoSequence.size}",
          )
        }
      }
    }
    if (landscape) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { SpeakButton(runner) }
  }
}

@Composable
private fun MeaningText(value: String, landscape: Boolean, modifier: Modifier) {
  val colors = Piyo.colors
  val scale = if (landscape) Piyo.metrics.learningScale else 1f
  Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Text(
      value,
      fontSize = Piyo.sp(maxOf(14f, 17f * scale)),
      color = colors.mutedInk,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(colors.backgroundBottom.copy(alpha = 0.78f))
        .padding(horizontal = 14.dp, vertical = 7.dp)
        .testTag(PracticeTags.MEANING),
    )
  }
}

@Composable
private fun SpeakButton(runner: PracticeSessionRunner) {
  val colors = Piyo.colors
  val expanded = Piyo.metrics.isExpanded
  val target = runner.model.target
  Row(
    Modifier
      .defaultMinSize(minHeight = 44.dp)
      .clip(RoundedCornerShape(50))
      .clickable(role = Role.Button) { runner.speakCurrentTarget() }
      .background(colors.accentSoft.copy(alpha = 0.48f))
      .padding(horizontal = if (expanded) 16.dp else 11.dp, vertical = if (expanded) 10.dp else 7.dp)
      .testTag(PracticeTags.SPEAK)
      .semantics(mergeDescendants = true) { stateDescription = target },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(5.dp))
    Text(stringResource(R.string.practice_speak_target), style = PiyoType.subheadline().copy(color = colors.secondary, fontWeight = FontWeight.Bold))
  }
}

@Composable
private fun CompositionCard(runner: PracticeSessionRunner, showsMascot: Boolean, showsComposition: Boolean, landscape: Boolean, minHeight: Dp) {
  val colors = Piyo.colors
  val metrics = Piyo.metrics
  val model = runner.model
  val scale = remember { Animatable(1f) }
  LaunchedEffect(model.feedbackRevision) {
    when (model.feedback) {
      PracticeFeedback.Complete -> {
        scale.snapTo(0.96f)
        scale.animateTo(1.04f, spring(dampingRatio = 0.58f, stiffness = 900f))
        kotlinx.coroutines.delay(180)
        scale.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = 1500f))
      }
      else -> scale.snapTo(1f)
    }
  }
  val feedback = model.feedback
  val mood = when {
    model.consecutiveMistakes >= 3 -> MascotMood.DIZZY
    feedback == PracticeFeedback.Idle -> MascotMood.IDLE
    feedback == PracticeFeedback.Correct -> MascotMood.HAPPY
    feedback is PracticeFeedback.Incorrect -> MascotMood.OOPS
    else -> if (model.isLessonComplete && model.mistakeCount == 0) MascotMood.PROUD else MascotMood.CHEER
  }
  val reaction = when (val event = model.reactionEvent) {
    PracticeReactionEvent.Idle -> MascotReaction.None
    PracticeReactionEvent.CorrectJamo -> MascotReaction.CorrectJamo
    is PracticeReactionEvent.SyllableCompleted -> MascotReaction.SyllableCompleted
    PracticeReactionEvent.WordCompleted -> MascotReaction.WordCompleted
    is PracticeReactionEvent.ComboMilestone -> MascotReaction.ComboMilestone(event.count)
    is PracticeReactionEvent.Mistake, is PracticeReactionEvent.Dizzy -> MascotReaction.Mistake
    PracticeReactionEvent.PerfectSession -> MascotReaction.PerfectSession
    PracticeReactionEvent.Stretch -> MascotReaction.Stretch
  }
  val expected = (feedback as? PracticeFeedback.Incorrect)?.expected
  val speech = if (model.consecutiveMistakes >= 3 && expected != null) stringResource(R.string.mascot_speech_missed_jamo, expected.toString()) else null
  val gazeX = when {
    expected == null -> 0f
    runner.keyboard.usesKorean10Key -> Korean10KeyGeometry.normalizedHorizontalPosition(Korean10KeyInterpreter().nextKey(expected))
    else -> HangulKeyboardGeometry.normalizedHorizontalPosition(expected)
  }
  val previewText = model.composingPreview + (runner.keyboard.korean10KeyPendingDisplay ?: "")
  val entered = model.enteredText.ifEmpty { "…" }
  val enteredLabel = stringResource(R.string.practice_entered_text)
  val learning = metrics.learningScale

  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = minHeight)
      .scale(scale.value)
      .clip(RoundedCornerShape(24.dp))
      .background(colors.card)
      .padding(horizontal = 14.dp, vertical = if (landscape) 4.dp else 10.dp)
      .testTag(PracticeTags.COMPOSITION_CARD),
    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showsMascot) {
      Box(Modifier.size((70 * learning).dp, (106 * learning).dp), contentAlignment = Alignment.Center) {
        GrowingMascot(
          mood = mood,
          reaction = reaction,
          reactionRevision = model.reactionRevision,
          gazeX = gazeX,
          gazeY = if (expected != null) 0.82f else 0f,
          intensity = minOf(model.correctStreak / 20f, 1f),
          speech = speech,
          size = (52 * learning).dp,
          calm = true,
        )
      }
    }
    if (showsComposition) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SyllableAssemblyPreview(
          text = previewText,
          incomingJamo = model.lastAcceptedKey,
          revision = model.compositionRevision,
          shouldAnimateJoin = model.shouldAnimateSyllableJoin,
          displayScale = learning,
        )
        Row(
          Modifier.testTag(PracticeTags.ENTERED_TEXT).semantics(mergeDescendants = true) {
            contentDescription = enteredLabel
            stateDescription = entered
          },
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(enteredLabel, style = PiyoType.subheadline().copy(color = colors.mutedInk))
          Text(entered, style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
        }
      }
    }
  }
}
