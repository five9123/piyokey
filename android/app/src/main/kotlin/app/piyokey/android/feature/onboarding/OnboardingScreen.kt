package app.piyokey.android.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.curriculum.DomainText
import app.piyokey.android.data.mascot.MascotEggPattern
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.feature.input.DubeolsikKeyboard
import app.piyokey.android.feature.input.KeyboardPreferences
import app.piyokey.android.feature.input.KoreanKeyboardAvailability
import app.piyokey.android.feature.input.KoreanKeyboardGuideSheet
import app.piyokey.android.feature.input.OsImeInputPanel
import app.piyokey.android.feature.input.PhysicalKeyboardGuide
import app.piyokey.android.feature.input.SessionInputMode
import app.piyokey.android.feature.input.rememberKeyboardOptions
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.ui.mascot.ChickMascot
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.OnboardingGoal
import app.piyokey.core.domain.OnboardingLevel
import app.piyokey.core.domain.OnboardingStep
import app.piyokey.core.hangul.CompositionEvent
import app.piyokey.core.hangul.CompositionState
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge

private const val LESSON_TARGET = "가"

/** iOS `OnboardingView`: goal → level → keyboard → first input, with skip and resume. */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
  val snapshot by AppData.onboarding.snapshot.collectAsState()
  val eggPattern by MascotStore.eggPattern.collectAsState()
  var eggReactionRevision by rememberSaveable { mutableIntStateOf(0) }
  var didPlayEggKnock by rememberSaveable { mutableStateOf(false) }
  var showsKeyboardGuide by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (!didPlayEggKnock) {
      didPlayEggKnock = true
      eggReactionRevision += 1
      SoundEngine.eggKnock()
    }
  }

  Column(
    modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("onboarding.screen"),
  ) {
    Header(snapshot.step)
    Box(Modifier.weight(1f).fillMaxWidth()) {
      when (snapshot.step) {
        OnboardingStep.GOAL -> GoalStep(snapshot.selectedGoal, eggPattern, eggReactionRevision)
        OnboardingStep.LEVEL -> LevelStep(snapshot.selectedLevel, eggPattern)
        OnboardingStep.KEYBOARD -> KeyboardStep(eggPattern, onNeedsKeyboardGuide = { showsKeyboardGuide = true })
        OnboardingStep.LESSON -> LessonStep(eggPattern)
      }
    }
  }
  if (showsKeyboardGuide) {
    KoreanKeyboardGuideSheet(onDismiss = { showsKeyboardGuide = false })
  }
}

private fun captureOnboardingStep(step: String) = Telemetry.onboardingStepCompleted(step)

@Composable
private fun Header(step: OnboardingStep) {
  val colors = Piyo.colors
  val total = OnboardingStep.entries.size
  Column(
    Modifier
      .centeredContent(Piyo.metrics.readableContentMaxWidth)
      .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.onboarding_progress_format, step.position, total),
        style = PiyoType.caption().copy(color = colors.secondary, fontWeight = FontWeight.Bold),
        modifier = Modifier.weight(1f),
      )
      Box(
        Modifier
          .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
          .clickable(role = Role.Button) { AppData.onboarding.complete(skipped = true) }
          .testTag("onboarding.skip"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          stringResource(R.string.onboarding_skip),
          style = PiyoType.subheadline().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold),
        )
      }
    }
    LinearProgressIndicator(
      progress = { step.position.toFloat() / total },
      modifier = Modifier.fillMaxWidth(),
      color = colors.accent,
      trackColor = colors.accent.copy(alpha = 0.18f),
      drawStopIndicator = {},
    )
  }
}

@Composable
private fun StepColumn(tag: String, spacing: Dp = 20.dp, verticalPadding: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .testTag(tag),
  ) {
    Column(
      Modifier
        .centeredContent(Piyo.metrics.readableContentMaxWidth)
        .padding(start = 20.dp, end = 20.dp, top = verticalPadding, bottom = maxOf(24.dp, verticalPadding)),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing),
      content = content,
    )
  }
}

@Composable
private fun StepTitle(title: String, subtitle: String, spacing: Dp = 7.dp) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing)) {
    Text(title, style = PiyoType.title2(), textAlign = TextAlign.Center)
    Text(subtitle, style = PiyoType.subheadline().copy(color = Piyo.colors.mutedInk), textAlign = TextAlign.Center)
  }
}

@Composable
private fun EggMascot(pattern: MascotEggPattern, size: Dp, boxWidth: Dp, boxHeight: Dp, tag: String? = null, calm: Boolean = false, reactionRevision: Int = 0, reaction: MascotReaction = MascotReaction.None, stage: MascotStage = MascotStage.EGG, mood: MascotMood = MascotMood.IDLE) {
  Box(
    Modifier.size(boxWidth, boxHeight).then(if (tag != null) Modifier.testTag(tag) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    ChickMascot(
      mood = mood,
      stage = stage,
      eggPattern = pattern,
      reaction = reaction,
      reactionRevision = reactionRevision,
      size = size,
      calm = calm,
    )
  }
}

// MARK: goal

private val OnboardingGoal.icon: ImageVector
  get() = when (this) {
    OnboardingGoal.KEYBOARD -> Icons.Rounded.Keyboard
    OnboardingGoal.TRAVEL -> Icons.Rounded.Flight
    OnboardingGoal.TOPIK -> Icons.Rounded.School
    OnboardingGoal.TRENDS -> Icons.Rounded.Forum
  }

@Composable
private fun GoalStep(selectedGoal: OnboardingGoal?, eggPattern: MascotEggPattern, eggReactionRevision: Int) {
  val colors = Piyo.colors
  StepColumn("onboarding.goal.screen") {
    EggMascot(
      eggPattern, size = 82.dp, boxWidth = 112.dp, boxHeight = 150.dp, tag = "onboarding.mascot.egg",
      reaction = MascotReaction.EggKnock, reactionRevision = eggReactionRevision,
    )
    Text(
      stringResource(R.string.onboarding_mascot_egg_intro),
      style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Bold),
      textAlign = TextAlign.Center,
    )
    StepTitle(stringResource(R.string.onboarding_goal_title), stringResource(R.string.onboarding_goal_subtitle))
    TrustCard()
    TwoColumnGrid(OnboardingGoal.entries) { goal, cardModifier ->
      GoalCard(goal, selected = selectedGoal == goal, modifier = cardModifier)
    }
    OnboardingPrimaryButton(
      title = stringResource(R.string.onboarding_next),
      icon = Icons.AutoMirrored.Rounded.ArrowForward,
      enabled = selectedGoal != null,
      tag = "onboarding.next",
    ) {
      AppData.onboarding.move(OnboardingStep.LEVEL)
      captureOnboardingStep("goal")
    }
  }
}

@Composable
private fun TrustCard() {
  val colors = Piyo.colors
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.card.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
      .padding(15.dp)
      .testTag("onboarding.trust"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    TrustLine(Icons.Rounded.Verified, stringResource(R.string.onboarding_trust_standard))
    TrustLine(Icons.Rounded.Shield, stringResource(R.string.onboarding_trust_local))
  }
}

@Composable
private fun TrustLine(icon: ImageVector, text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = Piyo.colors.accent, modifier = Modifier.size(16.dp))
    Text(text, style = PiyoType.caption().copy(fontWeight = FontWeight.SemiBold))
  }
}

@Composable
private fun <T> TwoColumnGrid(items: List<T>, cell: @Composable (T, Modifier) -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    items.chunked(2).forEach { row ->
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { item -> cell(item, Modifier.weight(1f).fillMaxHeight()) }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun GoalCard(goal: OnboardingGoal, selected: Boolean, modifier: Modifier) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(20.dp)
  val content = if (selected) Color.White else colors.ink
  Column(
    modifier
      .defaultMinSize(minHeight = 105.dp)
      .background(if (selected) colors.accent else colors.card, shape)
      .border(1.5.dp, if (selected) colors.accent else colors.keyShadow, shape)
      .clickable(role = Role.Button) {
        AppData.onboarding.select(goal)
        MascotStore.selectEggPattern(MascotEggPattern.forGoal(goal.raw))
      }
      .semantics { this.selected = selected }
      .padding(15.dp)
      .testTag("onboarding.goal.${goal.raw}"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(goal.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    Text(DomainText.string(goal.titleKey), style = PiyoType.headline().copy(color = content, fontWeight = FontWeight.Bold))
    Text(DomainText.string(goal.detailKey), style = PiyoType.caption().copy(color = content), maxLines = 2)
  }
}

// MARK: level

@Composable
private fun LevelStep(selectedLevel: OnboardingLevel?, eggPattern: MascotEggPattern) {
  StepColumn("onboarding.level.screen") {
    EggMascot(eggPattern, size = 82.dp, boxWidth = 112.dp, boxHeight = 150.dp)
    StepTitle(stringResource(R.string.onboarding_level_title), stringResource(R.string.onboarding_level_subtitle))
    TwoColumnGrid(OnboardingLevel.entries) { level, cardModifier ->
      LevelCard(level, selected = selectedLevel == level, modifier = cardModifier)
    }
    OnboardingPrimaryButton(
      title = stringResource(R.string.onboarding_next),
      icon = Icons.AutoMirrored.Rounded.ArrowForward,
      enabled = selectedLevel != null,
      tag = "onboarding.next",
    ) {
      AppData.onboarding.move(OnboardingStep.KEYBOARD)
    }
  }
}

private val OnboardingLevel.exampleRes: Int
  get() = when (this) {
    OnboardingLevel.BEGINNER -> R.string.onboarding_level_beginner_example
    OnboardingLevel.JAMO -> R.string.onboarding_level_jamo_example
    OnboardingLevel.WORDS -> R.string.onboarding_level_words_example
    OnboardingLevel.SENTENCES -> R.string.onboarding_level_sentences_example
  }

@Composable
private fun LevelCard(level: OnboardingLevel, selected: Boolean, modifier: Modifier) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(20.dp)
  Column(
    modifier
      .defaultMinSize(minHeight = 105.dp)
      .background(if (selected) colors.accentSoft else colors.card, shape)
      .border(1.5.dp, if (selected) colors.accent else colors.keyShadow, shape)
      .clickable(role = Role.Button) { AppData.onboarding.selectLevel(level) }
      .semantics { this.selected = selected }
      .padding(15.dp)
      .testTag("onboarding.level.${level.raw}"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(stringResource(level.exampleRes), style = PiyoType.title2().copy(color = colors.accent))
    Text(DomainText.string(level.titleKey), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
    Text(DomainText.string(level.detailKey), style = PiyoType.caption())
  }
}

// MARK: keyboard

@Composable
private fun KeyboardStep(eggPattern: MascotEggPattern, onNeedsKeyboardGuide: () -> Unit) {
  val colors = Piyo.colors
  val context = LocalContext.current
  fun beginLesson(mode: SessionInputMode) {
    if (mode == SessionInputMode.OS_IME && !KoreanKeyboardAvailability.isAvailable(context)) {
      onNeedsKeyboardGuide()
      return
    }
    KeyboardPreferences.setDefaultInputMode(mode)
    KeyboardPreferences.setShowsPhysicalKeyboardGuide(mode == SessionInputMode.OS_IME)
    AppData.onboarding.move(OnboardingStep.LESSON)
    captureOnboardingStep("keyboard")
  }

  StepColumn("onboarding.keyboard.screen", spacing = 22.dp, verticalPadding = 18.dp) {
    StepTitle(stringResource(R.string.onboarding_keyboard_title), stringResource(R.string.onboarding_keyboard_subtitle), spacing = 8.dp)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      HandCard(
        stringResource(R.string.onboarding_keyboard_left_hand), stringResource(R.string.onboarding_keyboard_consonants),
        "ㄱ ㄴ ㄷ ㄹ ㅁ", colors.secondary, Modifier.weight(1f).fillMaxHeight(),
      )
      HandCard(
        stringResource(R.string.onboarding_keyboard_right_hand), stringResource(R.string.onboarding_keyboard_vowels),
        "ㅏ ㅓ ㅗ ㅜ ㅣ", colors.accent, Modifier.weight(1f).fillMaxHeight(),
      )
    }
    val exampleLabel = stringResource(R.string.onboarding_keyboard_example_accessibility)
    Row(
      Modifier.semantics(mergeDescendants = true) { contentDescription = exampleLabel },
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      JamoTile("ㄱ")
      Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.ink)
      JamoTile("ㅏ")
      Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.accent)
      JamoTile("가", emphasized = true)
    }
    Row(
      Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(22.dp)).padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EggMascot(eggPattern, size = 62.dp, boxWidth = 84.dp, boxHeight = 116.dp, tag = "onboarding.mascot.keyboard_egg", calm = true)
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.onboarding_keyboard_tip), style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold))
        Text(
          stringResource(R.string.onboarding_mascot_hatch_hint),
          style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Bold),
        )
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      OnboardingPrimaryButton(
        title = stringResource(R.string.onboarding_keyboard_try_builtin),
        icon = Icons.Rounded.GridView,
        tag = "onboarding.next",
      ) { beginLesson(SessionInputMode.BUILT_IN) }
      val shape = RoundedCornerShape(18.dp)
      Row(
        Modifier
          .fillMaxWidth()
          .background(colors.card, shape)
          .border(BorderStroke(1.5.dp, colors.accent), shape)
          .clickable(role = Role.Button) { beginLesson(SessionInputMode.OS_IME) }
          .padding(vertical = 14.dp)
          .testTag("onboarding.input_device.hardware"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          stringResource(R.string.onboarding_keyboard_try_device),
          style = PiyoType.headline().copy(color = colors.accent, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = colors.accent)
      }
    }
    Text(
      stringResource(R.string.onboarding_keyboard_device_note),
      style = PiyoType.caption2().copy(color = colors.mutedInk),
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun HandCard(title: String, detail: String, jamo: String, color: Color, modifier: Modifier) {
  val colors = Piyo.colors
  Column(
    modifier
      .defaultMinSize(minHeight = 112.dp)
      .background(colors.card, RoundedCornerShape(22.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title, style = PiyoType.headline().copy(color = color, fontWeight = FontWeight.Bold))
    Text(detail, style = PiyoType.caption().copy(color = colors.mutedInk))
    Text(jamo, style = PiyoType.body().copy(fontWeight = FontWeight.Bold))
  }
}

@Composable
private fun JamoTile(value: String, emphasized: Boolean = false) {
  val colors = Piyo.colors
  Box(
    Modifier.size(50.dp).background(if (emphasized) colors.accent else colors.card, RoundedCornerShape(15.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(value, style = PiyoType.style(24f, FontWeight.Bold).copy(color = if (emphasized) Color.White else colors.ink))
  }
}

// MARK: first input

/** Minimal `PracticeSessionViewModel(target: "가")` for the first-input mini lesson. */
private class FirstInputLesson {
  var judge by mutableStateOf(JamoJudgeState(LESSON_TARGET))
  var composition by mutableStateOf(CompositionState())
  private var acceptedKeys = mutableListOf<Char>()
  var feedbackRevision by mutableIntStateOf(0)
    private set
  var lastWasMistake = false
    private set

  val isComplete: Boolean get() = judge.isComplete
  val enteredText: String get() = composition.text
  val nextExpectedKey: Char? get() = judge.expectedNext

  fun input(key: Char) {
    if (judge.isComplete) return
    val evaluation = JamoSequenceJudge.evaluate(key, judge)
    judge = evaluation.state
    when (evaluation.result) {
      is JamoJudgeResult.Correct -> {
        acceptedKeys += key
        composition = HangulComposer.reduce(composition, CompositionEvent.Key(key))
        lastWasMistake = false
        if (judge.isComplete) SoundEngine.completion(0)
      }
      is JamoJudgeResult.Incorrect -> {
        lastWasMistake = true
        SoundEngine.mistake()
      }
      JamoJudgeResult.AlreadyComplete -> Unit
    }
    feedbackRevision += 1
  }

  fun backspace() {
    if (acceptedKeys.isEmpty()) return
    acceptedKeys.removeAt(acceptedKeys.lastIndex)
    composition = HangulComposer.reduce(composition, CompositionEvent.Backspace)
    var rebuilt = JamoJudgeState(LESSON_TARGET)
    acceptedKeys.forEach { rebuilt = JamoSequenceJudge.evaluate(it, rebuilt).state }
    judge = rebuilt
    lastWasMistake = false
    feedbackRevision += 1
  }

  fun synchronizeOsIme(sequence: List<Char>) {
    val expected = judge.expectedSequence
    if (sequence.size > expected.size || expected.take(sequence.size) != sequence) return
    while (acceptedKeys.size > sequence.size) backspace()
    if (acceptedKeys.size >= sequence.size) return
    sequence.drop(acceptedKeys.size).forEach(::input)
  }

  fun recordConfirmedOsImeMistake() {
    if (judge.isComplete || judge.expectedNext == null) return
    lastWasMistake = true
    SoundEngine.mistake()
    feedbackRevision += 1
  }
}

@Composable
private fun LessonStep(eggPattern: MascotEggPattern) {
  val lesson = remember { FirstInputLesson() }
  var didCaptureFirstInput by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(lesson.isComplete) {
    if (lesson.isComplete && !didCaptureFirstInput) {
      didCaptureFirstInput = true
      captureOnboardingStep("first_input")
    }
  }
  if (lesson.isComplete) {
    HatchMissionHandoff(eggPattern)
  } else {
    LessonInput(lesson, eggPattern)
  }
}

@Composable
private fun LessonInput(lesson: FirstInputLesson, eggPattern: MascotEggPattern) {
  val colors = Piyo.colors
  val metrics = Piyo.metrics
  val inputModeRaw by KeyboardPreferences.inputModeDefault.flow.collectAsState()
  val physicalGuide by KeyboardPreferences.showsPhysicalKeyboardGuide.flow.collectAsState()
  val usesDeviceKeyboard = SessionInputMode.fromRaw(inputModeRaw) == SessionInputMode.OS_IME && physicalGuide
  val visibleFocusRecovery = metrics.isExpanded
  val entered = lesson.enteredText
  val contentAlpha by animateFloatAsState(if (entered.isEmpty()) 0.48f else 1f, tween(200), label = "lessonAlpha")

  Column(Modifier.fillMaxSize().imePadding()) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
      Column(
        Modifier
          .fillMaxSize()
          .alpha(contentAlpha)
          .verticalScroll(rememberScrollState()),
      ) {
        Column(
          Modifier
            .centeredContent(metrics.sessionLaneMaxWidth)
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EggMascot(
              eggPattern, size = 56.dp, boxWidth = 76.dp, boxHeight = 102.dp, tag = "onboarding.mascot.typing_egg", calm = true,
              stage = if (entered.isEmpty()) MascotStage.EGG else MascotStage.CRACKING,
              mood = if (lesson.isComplete) MascotMood.CHEER else MascotMood.IDLE,
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
              Text(stringResource(R.string.onboarding_lesson_title), style = PiyoType.title3().copy(fontWeight = FontWeight.Bold))
              Text(stringResource(R.string.onboarding_lesson_subtitle), style = PiyoType.caption().copy(color = colors.mutedInk))
            }
          }
          if (entered.isNotEmpty()) {
            Text(
              stringResource(R.string.onboarding_mascot_hatch_soon),
              style = PiyoType.caption().copy(color = colors.accent, fontWeight = FontWeight.Bold),
            )
          }
          Row(
            Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(22.dp)).padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(
                stringResource(R.string.onboarding_lesson_target),
                style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold),
              )
              Text(
                LESSON_TARGET,
                style = PiyoType.style(40f, FontWeight.Bold),
                modifier = Modifier.testTag("onboarding.lesson.target.value"),
              )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.accent)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(
                stringResource(R.string.onboarding_lesson_input),
                style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold),
              )
              Text(
                entered.ifEmpty { "…" },
                style = PiyoType.style(40f, FontWeight.Bold).copy(color = colors.accent),
                modifier = Modifier.testTag("onboarding.lesson.entered.value"),
              )
            }
          }
          Text(
            stringResource(R.string.onboarding_lesson_guide),
            style = PiyoType.caption().copy(color = colors.secondary, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
          )
        }
      }
      if (usesDeviceKeyboard && !visibleFocusRecovery) {
        OsImeInputPanel(
          target = LESSON_TARGET,
          acceptedText = entered,
          resetRevision = 0,
          onAcceptedSequence = lesson::synchronizeOsIme,
          onConfirmedMismatch = lesson::recordConfirmedOsImeMistake,
          onInputStart = { SoundEngine.warmUp(0) },
          showsChrome = false,
          showsFocusRecovery = false,
          modifier = Modifier.matchParentSize(),
        )
      }
    }

    AnimatedVisibility(
      visible = entered.isEmpty(),
      modifier = Modifier.align(Alignment.CenterHorizontally),
      enter = slideInVertically { it } + fadeIn(),
      exit = slideOutVertically { it } + fadeOut(),
    ) {
      Row(
        Modifier
          .padding(vertical = 6.dp)
          .shadow(8.dp, CircleShape, ambientColor = colors.secondary.copy(alpha = 0.25f), spotColor = colors.secondary.copy(alpha = 0.25f))
          .background(colors.secondary, CircleShape)
          .padding(horizontal = 14.dp, vertical = 9.dp)
          .testTag("onboarding.lesson.coachmark"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(
          stringResource(R.string.onboarding_lesson_coachmark),
          style = PiyoType.caption().copy(color = Color.White, fontWeight = FontWeight.Bold),
        )
      }
    }

    if (usesDeviceKeyboard) {
      if (visibleFocusRecovery) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 6.dp)) {
          OsImeInputPanel(
            target = LESSON_TARGET,
            acceptedText = entered,
            resetRevision = 0,
            onAcceptedSequence = lesson::synchronizeOsIme,
            onConfirmedMismatch = lesson::recordConfirmedOsImeMistake,
            onInputStart = { SoundEngine.warmUp(0) },
            showsChrome = false,
            showsFocusRecovery = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(56.dp),
          )
          PhysicalKeyboardGuide(nextExpectedKey = lesson.nextExpectedKey, modifier = Modifier.padding(horizontal = 8.dp))
        }
      }
    } else {
      DubeolsikKeyboard(
        nextExpectedKey = lesson.nextExpectedKey,
        onKey = lesson::input,
        onBackspace = lesson::backspace,
        options = rememberKeyboardOptions(),
        onKeyFeedback = { SoundEngine.keyTap(it) },
      )
    }
  }
}

@Composable
private fun HatchMissionHandoff(eggPattern: MascotEggPattern) {
  val colors = Piyo.colors
  StepColumn("onboarding.hatch.handoff.screen", spacing = 18.dp) {
    EggMascot(
      eggPattern, size = 70.dp, boxWidth = 94.dp, boxHeight = 126.dp, tag = "onboarding.mascot.cracking",
      stage = MascotStage.CRACKING, mood = MascotMood.HAPPY,
    )
    Row(
      Modifier
        .background(colors.secondary.copy(alpha = 0.12f), CircleShape)
        .padding(horizontal = 12.dp, vertical = 7.dp)
        .testTag("onboarding.reward.first"),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(14.dp))
      Text(
        stringResource(R.string.onboarding_reward_first),
        style = PiyoType.caption().copy(color = colors.secondary, fontWeight = FontWeight.Black),
      )
    }
    StepTitle(
      stringResource(R.string.onboarding_hatch_handoff_title),
      stringResource(R.string.onboarding_hatch_handoff_subtitle),
      spacing = 6.dp,
    )
    Row(
      Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(20.dp)).padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Rounded.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
      Text(
        stringResource(R.string.onboarding_hatch_handoff_detail),
        style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold),
      )
    }
    OnboardingPrimaryButton(
      title = stringResource(R.string.onboarding_hatch_begin),
      icon = Icons.AutoMirrored.Rounded.ArrowForward,
      tag = "onboarding.finish",
    ) { AppData.onboarding.complete(skipped = false) }
  }
}

@Composable
private fun OnboardingPrimaryButton(
  title: String,
  icon: ImageVector,
  tag: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(18.dp)
  Row(
    Modifier
      .fillMaxWidth()
      .alpha(if (enabled) 1f else 0.45f)
      .shadow(9.dp, shape, ambientColor = colors.accent.copy(alpha = 0.22f), spotColor = colors.accent.copy(alpha = 0.22f))
      .background(colors.accent, shape)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(vertical = 15.dp)
      .testTag(tag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = PiyoType.headline().copy(color = Color.White, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
    Spacer(Modifier.width(8.dp))
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
  }
}
