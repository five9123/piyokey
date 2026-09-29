package app.piyokey.android.feature.game

import android.view.View
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.BuildConfig
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.appMeaning
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotPose
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.progress.RetentionSession
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.feature.input.OsImeInputPanel
import app.piyokey.android.feature.input.OsImeInputSourceBannerLayer
import app.piyokey.android.feature.input.SessionKeyboard
import app.piyokey.android.feature.input.SessionKeyboardState
import app.piyokey.android.feature.input.rememberSessionKeyboardState
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.audio.Pronunciation
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.mascot.rememberMascotReduceMotion
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.session.SessionExitCover
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.domain.GameRecord
import app.piyokey.core.domain.GameRecordSaveOutcome
import app.piyokey.core.domain.ReviewDeckItem
import app.piyokey.core.domain.ReviewDeckMutation
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.SessionInputMode
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.domain.SessionMode
import app.piyokey.core.domain.SessionReviewItem
import app.piyokey.core.domain.game.ChoseongInitialProgressUnit
import app.piyokey.core.domain.game.ChoseongTypingEngine
import app.piyokey.core.domain.game.ChoseongTypingInputOutcome
import app.piyokey.core.domain.game.ChoseongTypingRound
import app.piyokey.core.domain.game.GamePhase
import app.piyokey.core.domain.game.GamePresetSessionRandomizer
import app.piyokey.core.domain.game.GameResultPresentation
import app.piyokey.core.domain.game.JapaneseMeaningDisplayText
import app.piyokey.core.domain.game.PronunciationHintUse
import app.piyokey.core.domain.game.RecallTypingFeedback
import app.piyokey.core.domain.game.RecallTypingGameMode
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CountdownAction { START, RESUME, RESTART }

/** Session state of iOS `ChoseongTypingView` around [ChoseongTypingEngine]. */
@Stable
internal class RecallTypingController(
  val deck: Deck,
  val mode: RecallTypingGameMode,
  private val keyboard: SessionKeyboardState,
  private val scope: CoroutineScope,
  private val view: View,
  private val reduceMotion: Boolean,
) {
  val presentation = GameResultPresentation.of(mode.gameKind)
  val engine = ChoseongTypingEngine(buildRounds())

  var version by mutableIntStateOf(0)
    private set
  var countdownValue by mutableStateOf<Int?>(null)
    private set
  private var countdownAction: CountdownAction? = null
  var showsResult by mutableStateOf(false)
    private set
  var exits by mutableStateOf(false)
    private set
  var recordOutcome by mutableStateOf<GameRecordSaveOutcome?>(null)
    private set
  var reviewItems by mutableStateOf<List<SessionReviewItem>>(emptyList())
    private set
  var shakeRevision by mutableIntStateOf(0)
    private set

  private var isActive = true
  private var advanceJob: Job? = null
  private var countdownJob: Job? = null
  private var didPersist = false
  private var retention = RetentionSession.start()
  private var recordInputMode = FlowGameController.domainMode(keyboard)
  private var didCaptureStart = false
  private var didCaptureCompletion = false
  private var didCaptureAbandonment = false
  private var lastRoundRevision = engine.roundRevision

  private fun buildRounds(): List<ChoseongTypingRound> =
    mode.rounds(deck.items, GamePresetSessionRandomizer.seed(deck, mode.gameKind), appMeaningLookup)

  val isCountingDown: Boolean get() = countdownAction != null

  private fun now() = monotonicSeconds()

  private fun publish() {
    if (engine.roundRevision != lastRoundRevision) {
      lastRoundRevision = engine.roundRevision
      keyboard.resetForNewTarget()
    }
    version++
  }

  fun onAppear() {
    captureStart()
    if (mode.requiresCountdown) {
      when (engine.phase) {
        GamePhase.READY -> startCountdown(CountdownAction.START)
        GamePhase.PAUSED -> startCountdown(CountdownAction.RESUME)
        else -> Unit
      }
    } else if (engine.phase == GamePhase.READY) {
      engine.start(now())
      AppData.deckLibrary.markPlayed(deck.deckId)
      publish()
    }
  }

  fun onStop() {
    isActive = false
    advanceJob?.cancel()
    advanceJob = null
    if (mode.requiresCountdown) {
      Pronunciation.stop()
      if (countdownAction == null && engine.phase == GamePhase.RUNNING) countdownAction = CountdownAction.RESUME
      countdownJob?.cancel()
      countdownJob = null
      countdownValue = null
    }
    engine.pause(now())
    publish()
  }

  fun onStart() {
    isActive = true
    if (mode.requiresCountdown) {
      val action = countdownAction ?: when (engine.phase) {
        GamePhase.READY -> CountdownAction.START
        GamePhase.PAUSED -> CountdownAction.RESUME
        else -> null
      }
      action?.let(::startCountdown)
    } else {
      engine.resume(now())
      if (engine.isRoundComplete) scheduleAdvance()
      publish()
    }
  }

  private fun startCountdown(action: CountdownAction) {
    countdownAction = action
    if (!isActive) return
    countdownJob?.cancel()
    val values = if (reduceMotion) listOf(1) else listOf(3, 2, 1)
    val step = GameTestHooks.countdownStepMillis ?: if (reduceMotion) 350L else 1_000L
    countdownValue = values.first()
    publish()
    countdownJob = scope.launch {
      for ((index, value) in values.withIndex()) {
        if (index > 0) countdownValue = value
        delay(step)
      }
      if (!isActive) return@launch
      when (action) {
        CountdownAction.START, CountdownAction.RESTART -> engine.start(now())
        CountdownAction.RESUME -> engine.resume(now())
      }
      if (action != CountdownAction.RESUME) AppData.deckLibrary.markPlayed(deck.deckId)
      countdownValue = null
      countdownAction = null
      countdownJob = null
      if (engine.isRoundComplete) scheduleAdvance() else speakCurrentAnswer()
      publish()
    }
  }

  // Input.
  fun input(key: Char) {
    engine.input(key, now())?.let(::handle)
    publish()
  }

  fun backspace() {
    engine.backspace(now())
    publish()
  }

  fun mistake() {
    engine.recordConfirmedOsImeMistake(now())?.let(::handle)
    publish()
  }

  fun synchronizeOsIme(sequence: List<Char>) {
    engine.synchronizeOsIme(sequence, now()).forEach(::handle)
    publish()
  }

  fun useMeaningHint() {
    engine.useHint(shouldPenalize = !engine.currentRound.requiresMeaningHint)
    publish()
  }

  private fun handle(outcome: ChoseongTypingInputOutcome) {
    when (outcome) {
      ChoseongTypingInputOutcome.Correct -> MascotStore.recordTypedJamo(1)
      is ChoseongTypingInputOutcome.Incorrect -> {
        AppData.review.recordMistake(outcome.answer, deck.deckId)
        collectReviewItem(outcome.answer, setOf(outcome.jamoIndex))
        MascotStore.recordMistake(outcome.expected)
        MascotStore.publish(MascotReaction.Startle)
        SoundEngine.mistake()
        GameHaptics.error(view)
        shakeRevision++
      }
      is ChoseongTypingInputOutcome.Completed -> {
        val completion = outcome.completion
        MascotStore.recordTypedJamo(1)
        if (!completion.usedHint && !completion.resolution.hadMistake &&
          AppData.review.recordPerfect(completion.answer.id, deck.deckId) == ReviewDeckMutation.GRADUATED
        ) {
          MascotStore.publish(MascotReaction.ReviewGraduated)
        }
        if (mode == RecallTypingGameMode.CHOSEONG) {
          MascotStore.recordChoseongSolved(completion.usedHint)
        } else {
          MascotStore.publish(MascotReaction.Eureka)
        }
        SoundEngine.completion(engine.combo)
        GameHaptics.success(view)
        AppData.review.flush()
        scheduleAdvance()
      }
    }
  }

  private fun scheduleAdvance() {
    if (advanceJob != null || !isActive) return
    advanceJob = scope.launch {
      delay(mode.completedFeedbackMillis)
      advanceJob = null
      engine.advance(now())
      if (engine.phase == GamePhase.FINISHED) {
        Pronunciation.stop()
        persistFinishedGame()
        showsResult = true
      } else if (mode == RecallTypingGameMode.DICTATION) {
        speakCurrentAnswer()
      }
      publish()
    }
  }

  private fun collectReviewItem(item: DeckItem, indices: Set<Int>) {
    val resolution = SessionItemResolution(deck.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0), true, 1, indices)
    val id = ReviewDeckItem.id(item.id, deck.deckId)
    val index = reviewItems.indexOfFirst { it.id == id }
    reviewItems = if (index >= 0) {
      reviewItems.toMutableList().also { it[index] = it[index].merge(resolution) }
    } else {
      reviewItems + SessionReviewItem.of(item, deck.deckId, resolution)
    }
  }

  fun speakCurrentAnswer() {
    if (mode != RecallTypingGameMode.DICTATION || engine.phase != GamePhase.RUNNING || engine.isRoundComplete) return
    val answer = engine.currentRound.answer
    Pronunciation.speak(answer.ko, answer.audio)
  }

  /** Dictation replay button (always allowed while the round is open). */
  fun replay() {
    val answer = engine.currentRound.answer
    if (engine.phase == GamePhase.RUNNING) Pronunciation.speak(answer.ko, answer.audio)
  }

  fun usePronunciationHint() {
    if (mode != RecallTypingGameMode.CHOSEONG && mode != RecallTypingGameMode.WORD_MATCH) return
    val use = engine.usePronunciationHint() ?: return
    if (use is PronunciationHintUse.FirstUse) {
      AppData.review.recordMistake(use.answer, deck.deckId)
      collectReviewItem(use.answer, emptySet())
      AppData.review.flush()
      announce(view, 
        app.piyokey.android.ui.theme.L.string(R.string.game_pronunciation_hint_used_announcement_format, engine.pronunciationHintsRemaining),
      )
    }
    Pronunciation.speak(use.answer.ko, use.answer.audio)
    publish()
  }

  private fun persistFinishedGame() {
    if (didPersist) return
    didPersist = true
    val result = engine.result
    val outcome = AppData.gameProgress.append(
      GameRecord(
        id = GameRecord.newId(),
        mode = SessionMode.GAME,
        deckId = deck.deckId,
        deckVersion = deck.version,
        course = mode.gameKind.raw,
        score = result.score,
        maxCombo = result.maxCombo,
        accuracy = result.accuracyPercent,
        charactersPerMinute = result.charactersPerMinute,
        activeDuration = result.activeDuration,
        completedItemCount = result.completedItemCount,
        missedItemCount = result.missedCardCount,
        inputMode = recordInputMode,
        playedAt = Instant.now(),
      ),
    )
    recordOutcome = outcome
    if (outcome?.isNewBest == true) MascotStore.publish(MascotReaction.NewBest)
    AppData.retention.record(RetentionActivityKind.GAME, retention)
    captureCompletion()
  }

  fun retry() {
    recordOutcome = null
    didPersist = false
    reviewItems = emptyList()
    retention = RetentionSession.start()
    recordInputMode = FlowGameController.domainMode(keyboard)
    didCaptureStart = false
    didCaptureCompletion = false
    didCaptureAbandonment = false
    keyboard.resetForNewTarget()
    val next = if (GamePresetSessionRandomizer.isBundledPreset(deck.deckId, mode.gameKind)) buildRounds() else null
    if (mode.requiresCountdown) {
      Pronunciation.stop()
      engine.prepareRestart(next)
      startCountdown(CountdownAction.RESTART)
    } else {
      engine.restart(next, now())
    }
    showsResult = false
    captureStart()
    publish()
  }

  fun finishFromResult() {
    if (exits) return
    Pronunciation.stop()
    exits = true
  }

  fun onDispose() {
    advanceJob?.cancel()
    countdownJob?.cancel()
    Pronunciation.stop()
    AppData.review.flush()
    captureAbandonment()
  }

  private val deckSource get() = GameDecks.analyticsDeckSource(deck, presentation)

  private fun captureStart() {
    if (didCaptureStart) return
    didCaptureStart = true
    Telemetry.sessionStarted("game", deckSource, recordInputMode.raw, presentation.analyticsValue, presentation.analyticsDifficulty(deck))
    Telemetry.setCrashContext("game", "game", recordInputMode.raw, presentation.analyticsValue)
  }

  private fun captureCompletion() {
    if (didCaptureCompletion) return
    didCaptureCompletion = true
    val result = engine.result
    Telemetry.sessionCompleted(
      "game", "completed", result.activeDuration, result.completedItemCount, deckSource, recordInputMode.raw,
      presentation.analyticsValue, presentation.analyticsDifficulty(deck),
    )
  }

  private fun captureAbandonment() {
    if (!didCaptureStart || didCaptureCompletion || didCaptureAbandonment) return
    didCaptureAbandonment = true
    Telemetry.sessionAbandoned(
      "game", "user_closed", engine.result.activeDuration, deckSource, recordInputMode.raw,
      presentation.analyticsValue, presentation.analyticsDifficulty(deck),
    )
  }
}

/** iOS `ChoseongTypingView` (`RecallTypingGameMode`: 초성, word match, dictation). */
@Composable
fun RecallTypingScreen(deck: Deck, mode: RecallTypingGameMode) {
  val appNav = LocalAppNavigator.current
  val scope = rememberCoroutineScope()
  val view = LocalView.current
  val reduceMotion = rememberMascotReduceMotion()
  val keyboard = rememberSessionKeyboardState()
  val controller = remember { RecallTypingController(deck, mode, keyboard, scope, view, reduceMotion) }

  LaunchedEffect(controller) { controller.onAppear() }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.onStop() }
  LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.onStart() }
  DisposableEffect(controller) { onDispose { controller.onDispose() } }
  LaunchedEffect(controller.exits) { if (controller.exits) appNav.pop() }

  controller.version
  keyboard.isOsImeFocusSuspended = controller.showsResult || controller.countdownValue != null

  if (controller.showsResult) {
    Box(Modifier.fillMaxSize()) {
      GameResultScreen(
        deck = deck,
        result = controller.engine.result,
        recordOutcome = controller.recordOutcome,
        reviewItems = controller.reviewItems,
        presentation = controller.presentation,
        onRetry = controller::retry,
        onFinish = controller::finishFromResult,
      )
      SessionExitCover(controller.exits)
    }
    return
  }
  RecallPlayContent(controller, keyboard, onClose = { appNav.pop() })
}

@NonSkippableComposable
@Composable
private fun RecallPlayContent(controller: RecallTypingController, keyboard: SessionKeyboardState, onClose: () -> Unit) {
  val colors = Piyo.colors
  val metrics = Piyo.metrics
  val engine = controller.engine
  val ns = controller.mode.accessibilityNamespace
  val bottom by animateColorAsState(if (engine.combo >= 5) colors.accentSoft.copy(alpha = 0.92f) else colors.backgroundBottom, tween(350), label = "bg")
  val overlaysHiddenOsIme = keyboard.usesOsIme && !metrics.isExpanded
  val countingDown = controller.isCountingDown

  Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(colors.backgroundTop, bottom))).testTag("$ns.play.screen")) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().alpha(if (countingDown) 0f else 1f)) {
      GameSessionTopBar(onClose = onClose) { RecallHud(controller) }
      val cardsModifier = Modifier.centeredContent(metrics.sessionLaneMaxWidth).padding(horizontal = 14.dp, vertical = 10.dp)
      Column(
        (if (metrics.isExpanded) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.weight(1f)).then(cardsModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(if (metrics.isExpanded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f)) { QuizCard(controller) }
        Box(Modifier.fillMaxWidth()) {
          CompositionCard(controller, keyboard)
          if (overlaysHiddenOsIme) {
            OsImeInputPanel(
              target = engine.currentRound.answer.ko,
              acceptedText = engine.enteredText,
              resetRevision = keyboard.resetRevision + engine.roundRevision,
              onAcceptedSequence = controller::synchronizeOsIme,
              onConfirmedMismatch = controller::mistake,
              modifier = Modifier.matchParentSize(),
              onInputStart = { SoundEngine.warmUp(engine.combo) },
              showsChrome = false,
              showsFocusRecovery = false,
              isFocusSuspended = keyboard.isOsImeFocusSuspended,
              externalBanner = keyboard.osImeBanner,
            )
          }
        }
      }
      SessionKeyboard(
        state = keyboard,
        expectedNextJamo = engine.nextExpectedKey,
        osImeTarget = engine.currentRound.answer.ko,
        osImeAcceptedText = engine.enteredText,
        onJamo = controller::input,
        onBackspace = controller::backspace,
        onKorean10KeyMistake = controller::mistake,
        onOsImeAcceptedSequence = controller::synchronizeOsIme,
        onOsImeConfirmedMismatch = controller::mistake,
        modifier = Modifier.centeredContent(metrics.sessionLaneMaxWidth),
        revealsExpectedKey = false,
        osImeResetRevision = engine.roundRevision,
        embedsOsImeField = !overlaysHiddenOsIme,
        onOsImeInputStart = { SoundEngine.warmUp(engine.combo) },
      )
    }
    if (overlaysHiddenOsIme) {
      OsImeInputSourceBannerLayer(keyboard.osImeBanner.value, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
    }
    if (BuildConfig.DEBUG && controller.mode != RecallTypingGameMode.DICTATION) {
      Text(engine.targetJamoSequence.joinToString(""), modifier = Modifier.size(1.dp).alpha(0.01f).testTag("$ns.answer.keys"))
    }
    controller.countdownValue?.let { value ->
      Box(
        Modifier.fillMaxSize().background(colors.card.copy(alpha = 0.88f)).testTag("$ns.countdown"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.game_countdown_ready), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = colors.secondary))
          Text(value.toString(), style = PiyoType.style(84f, FontWeight.Black).copy(color = colors.accent))
        }
      }
    }
  }
}

@NonSkippableComposable
@Composable
private fun RecallHud(controller: RecallTypingController) {
  val colors = Piyo.colors
  val engine = controller.engine
  val ns = controller.mode.accessibilityNamespace
  val questionLabel = stringResource(
    when (controller.mode) {
      RecallTypingGameMode.CHOSEONG -> R.string.choseong_question
      RecallTypingGameMode.WORD_MATCH -> R.string.word_match_question
      RecallTypingGameMode.DICTATION -> R.string.dictation_question
    },
  )
  CompactGameHudMetric(Icons.Rounded.Numbers, "${engine.questionNumber}/${engine.rounds.size}", questionLabel, "$ns.question.value", colors.secondary, 64)
  CompactGameHudMetric(Icons.Rounded.Star, formatNumber(engine.score), stringResource(R.string.game_score), "$ns.score.value", colors.accent, 64)
  CompactGameHudMetric(
    Icons.Rounded.LocalFireDepartment, engine.combo.toString(), stringResource(R.string.game_combo), "$ns.combo.value",
    if (engine.combo == 0) colors.mutedInk else GameColors.orange, 64,
  )
}

@Composable
private fun QuizCardContainer(content: @Composable () -> Unit) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(26.dp)
  Column(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(colors.card)
      .border(1.dp, colors.secondary.copy(alpha = 0.2f), shape)
      .padding(horizontal = 22.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
  ) { content() }
}

@NonSkippableComposable
@Composable
private fun QuizCard(controller: RecallTypingController) {
  when (controller.mode) {
    RecallTypingGameMode.CHOSEONG -> ChoseongQuizCard(controller)
    RecallTypingGameMode.WORD_MATCH -> WordMatchQuizCard(controller)
    RecallTypingGameMode.DICTATION -> DictationQuizCard(controller)
  }
}

@NonSkippableComposable
@Composable
private fun ChoseongQuizCard(controller: RecallTypingController) {
  val colors = Piyo.colors
  val engine = controller.engine
  QuizCardContainer {
    Text(stringResource(R.string.choseong_initials_title), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary))
    if (engine.isRoundComplete) {
      val shape = RoundedCornerShape(18.dp)
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = (68 * Piyo.fontScale).dp)
          .clip(shape)
          .background(colors.success.copy(alpha = 0.12f))
          .border(1.5.dp, colors.success.copy(alpha = 0.42f), shape)
          .padding(horizontal = 18.dp)
          .testTag("choseong.answer.reveal"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
      ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(30.dp))
        Text(engine.currentRound.answer.ko, style = PiyoType.style(42f, FontWeight.Black), maxLines = 1)
      }
    } else {
      InitialProgressTrack(engine.currentRound.initials, engine.initialProgressUnits)
    }
    MeaningHint(controller)
    PronunciationHintButton(controller)
  }
}

@Composable
private fun InitialProgressTrack(initials: String, units: List<ChoseongInitialProgressUnit>) {
  val colors = Piyo.colors
  val fontScale = Piyo.fontScale
  val glyphs = maxOf(1, units.count { !it.isSeparator })
  val base = glyphs * 58f + units.count { it.isSeparator } * 18f + maxOf(units.size - 1, 0) * 8f
  BoxWithConstraints(
    Modifier
      .fillMaxWidth()
      .height((68 * fontScale).dp)
      .semantics {
        contentDescription = initials
        stateDescription = "${units.count { it.state == ChoseongInitialProgressUnit.State.COMPLETED }} / $glyphs"
      }
      .testTag("choseong.initials.value"),
    contentAlignment = Alignment.Center,
  ) {
    val scale = minOf(fontScale, maxOf(0.48f, maxWidth.value / maxOf(1f, base)))
    Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
      units.forEach { unit ->
        if (unit.isSeparator) {
          Box(Modifier.size((18 * scale).dp, (64 * scale).dp))
        } else {
          val state = unit.state
          val tileScale by animateFloatAsState(if (state == ChoseongInitialProgressUnit.State.ACTIVE) 1.06f else 1f, spring(0.68f, 900f), label = "tile")
          val shape = RoundedCornerShape((18 * scale).dp)
          val (fg, bg, stroke) = when (state) {
            ChoseongInitialProgressUnit.State.PENDING -> Triple(colors.ink, colors.backgroundBottom.copy(alpha = 0.42f), colors.secondary.copy(alpha = 0.12f))
            ChoseongInitialProgressUnit.State.ACTIVE -> Triple(colors.secondary, colors.accentSoft.copy(alpha = 0.72f), colors.secondary.copy(alpha = 0.72f))
            ChoseongInitialProgressUnit.State.COMPLETED -> Triple(Color.White, colors.success, colors.success)
          }
          Box(
            Modifier
              .scale(tileScale)
              .size((58 * scale).dp, (62 * scale).dp)
              .shadow(if (state == ChoseongInitialProgressUnit.State.ACTIVE) (8 * scale).dp else 0.dp, shape, spotColor = colors.secondary.copy(alpha = 0.2f))
              .clip(shape)
              .background(bg)
              .border((2 * scale).dp, stroke, shape),
            contentAlignment = Alignment.Center,
          ) {
            Text(unit.character.toString(), style = PiyoType.style(42f * scale / fontScale, FontWeight.Black).copy(color = fg))
          }
        }
      }
    }
  }
}

@NonSkippableComposable
@Composable
private fun MeaningHint(controller: RecallTypingController) {
  val colors = Piyo.colors
  val engine = controller.engine
  val showsMeaning by AppSettings.choseongShowsMeaning.flow.collectAsState()
  val meaning = engine.currentRound.answer.appMeaning?.takeIf { it.isNotBlank() } ?: return
  if (showsMeaning || engine.isHintVisible) {
    val shape = RoundedCornerShape(14.dp)
    Row(
      Modifier
        .clip(shape)
        .background(GameColors.orange.copy(alpha = 0.11f))
        .border(1.dp, GameColors.orange.copy(alpha = 0.22f), shape)
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .semantics(mergeDescendants = true) {}
        .testTag("choseong.hint.value"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = GameColors.orange, modifier = Modifier.size(18.dp))
      Text(
        JapaneseMeaningDisplayText.format(meaning),
        style = PiyoType.style(maxOf(17f, 19f), FontWeight.Bold),
        textAlign = TextAlign.Center,
        maxLines = 2,
      )
    }
  } else {
    Row(
      Modifier
        .clip(CircleShape)
        .background(colors.accentSoft.copy(alpha = 0.48f))
        .clickable(role = Role.Button, onClick = controller::useMeaningHint)
        .defaultMinSize(minHeight = 44.dp)
        .padding(horizontal = 14.dp, vertical = 8.dp)
        .testTag("choseong.hint.show"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
      Text(stringResource(R.string.choseong_hint_show), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold, color = colors.accent))
    }
  }
}

@NonSkippableComposable
@Composable
private fun PronunciationHintButton(controller: RecallTypingController) {
  val colors = Piyo.colors
  val engine = controller.engine
  val enabled = engine.canUsePronunciationHint
  val title = stringResource(
    when {
      engine.didUsePronunciationHintForCurrentRound -> R.string.game_pronunciation_hint_replay_format
      engine.pronunciationHintsRemaining > 0 -> R.string.game_pronunciation_hint_use_format
      else -> R.string.game_pronunciation_hint_exhausted_format
    },
    engine.pronunciationHintsRemaining,
  )
  val tint = if (enabled) colors.accent else colors.mutedInk
  Row(
    Modifier
      .clip(CircleShape)
      .background(colors.accentSoft.copy(alpha = 0.46f))
      .clickable(enabled = enabled, role = Role.Button, onClick = controller::usePronunciationHint)
      .defaultMinSize(minHeight = 44.dp)
      .padding(horizontal = 13.dp, vertical = 8.dp)
      .testTag("${controller.mode.accessibilityNamespace}.pronunciation_hint"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    Text(title, style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = tint))
  }
}

@NonSkippableComposable
@Composable
private fun WordMatchQuizCard(controller: RecallTypingController) {
  val colors = Piyo.colors
  val engine = controller.engine
  QuizCardContainer {
    Text(stringResource(R.string.word_match_prompt_title), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary))
    val shape = RoundedCornerShape(18.dp)
    Column(
      Modifier
        .fillMaxWidth()
        .heightIn(min = (112 * Piyo.fontScale).dp)
        .clip(shape)
        .background(GameColors.orange.copy(alpha = 0.11f))
        .border(1.dp, GameColors.orange.copy(alpha = 0.24f), shape)
        .padding(horizontal = 18.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
    ) {
      Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = GameColors.orange, modifier = Modifier.size(26.dp))
      Text(
        JapaneseMeaningDisplayText.format(engine.currentRound.answer.appMeaning ?: ""),
        style = PiyoType.style(maxOf(22f / Piyo.fontScale, 30f), FontWeight.Black),
        textAlign = TextAlign.Center,
        maxLines = 3,
        modifier = Modifier.testTag("word_match.prompt.value"),
      )
    }
    GuideLabel(Icons.Rounded.Keyboard, stringResource(R.string.word_match_prompt_guide))
    PronunciationHintButton(controller)
  }
}

@NonSkippableComposable
@Composable
private fun DictationQuizCard(controller: RecallTypingController) {
  val colors = Piyo.colors
  val label = stringResource(R.string.dictation_replay)
  QuizCardContainer {
    Text(stringResource(R.string.dictation_prompt_title), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary))
    Box(
      Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(colors.accentSoft.copy(alpha = 0.7f))
        .clickable(role = Role.Button, onClick = controller::replay)
        .semantics { contentDescription = label }
        .testTag("dictation.replay"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
    }
    GuideLabel(Icons.Rounded.GraphicEq, stringResource(R.string.dictation_prompt_guide))
  }
}

@Composable
private fun GuideLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
  val colors = Piyo.colors
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
    Icon(icon, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(14.dp))
    Text(text, style = PiyoType.caption().copy(fontWeight = FontWeight.SemiBold, color = colors.mutedInk), textAlign = TextAlign.Center)
  }
}

@NonSkippableComposable
@Composable
private fun CompositionCard(controller: RecallTypingController, keyboard: SessionKeyboardState) {
  val colors = Piyo.colors
  val engine = controller.engine
  val ns = controller.mode.accessibilityNamespace
  val shake = remember { Animatable(0f) }
  LaunchedEffect(controller.shakeRevision) {
    if (controller.shakeRevision == 0) return@LaunchedEffect
    shake.snapTo(0f)
    shake.animateTo(1f, tween(240, easing = LinearEasing))
    shake.snapTo(0f)
  }
  val offset = 7f * sin(shake.value * PI.toFloat() * 2)
  val mood = when (engine.feedback) {
    RecallTypingFeedback.Incorrect -> MascotMood.OOPS
    is RecallTypingFeedback.Completed -> MascotMood.EUREKA
    else -> MascotMood.FOCUS
  }
  val reaction = when (engine.feedback) {
    RecallTypingFeedback.Incorrect -> MascotReaction.Startle
    is RecallTypingFeedback.Completed -> MascotReaction.Eureka
    else -> MascotReaction.None
  }
  Column(
    Modifier
      .fillMaxWidth()
      .offset { IntOffset(offset.dp.roundToPx(), 0) }
      .clip(RoundedCornerShape(24.dp))
      .background(colors.card)
      .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 9.dp)
      .testTag("$ns.composition_card"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(Modifier.width(70.dp), contentAlignment = Alignment.Center) {
        GrowingMascot(
          mood = mood,
          reaction = reaction,
          reactionRevision = engine.feedbackRevision,
          pose = MascotPose.FRONT,
          size = 52.dp,
          calm = true,
          modifier = Modifier.testTag("$ns.mascot"),
        )
      }
      Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val preview = engine.composingPreview + (keyboard.korean10KeyPendingDisplay ?: "")
        SyllablePreview(preview, engine.compositionRevision)
        val entered = engine.enteredText.ifEmpty { "…" }
        val enteredLabel = stringResource(R.string.practice_entered_text)
        Row(
          Modifier.semantics(mergeDescendants = true) { contentDescription = enteredLabel; stateDescription = entered }.testTag("$ns.typing.value"),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(enteredLabel, style = PiyoType.subheadline().copy(color = colors.mutedInk))
          Text(entered, style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
      }
    }
    val (text, color) = when (val feedback = engine.feedback) {
      RecallTypingFeedback.Ready -> stringResource(
        when (controller.mode) {
          RecallTypingGameMode.CHOSEONG -> R.string.choseong_feedback_ready
          RecallTypingGameMode.WORD_MATCH -> R.string.word_match_feedback_ready
          RecallTypingGameMode.DICTATION -> R.string.dictation_feedback_ready
        },
      ) to colors.mutedInk
      RecallTypingFeedback.Correct -> stringResource(
        when (controller.mode) {
          RecallTypingGameMode.CHOSEONG -> R.string.choseong_feedback_jamo_correct
          RecallTypingGameMode.WORD_MATCH -> R.string.word_match_feedback_jamo_correct
          RecallTypingGameMode.DICTATION -> R.string.dictation_feedback_jamo_correct
        },
      ) to colors.success
      RecallTypingFeedback.Incorrect -> stringResource(
        when (controller.mode) {
          RecallTypingGameMode.CHOSEONG -> R.string.choseong_feedback_incorrect_typing
          RecallTypingGameMode.WORD_MATCH -> R.string.word_match_feedback_incorrect_typing
          RecallTypingGameMode.DICTATION -> R.string.dictation_feedback_incorrect_typing
        },
      ) to colors.error
      is RecallTypingFeedback.Completed -> stringResource(
        when (controller.mode) {
          RecallTypingGameMode.CHOSEONG -> R.string.choseong_feedback_correct
          RecallTypingGameMode.WORD_MATCH -> R.string.word_match_feedback_correct
          RecallTypingGameMode.DICTATION -> R.string.dictation_feedback_correct
        },
        feedback.points,
      ) to colors.success
    }
    Text(
      text,
      style = PiyoType.subheadline().copy(fontWeight = FontWeight.SemiBold, color = color),
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).testTag("$ns.typing.feedback"),
    )
  }
}

/** Simplified iOS `SyllableAssemblyPreview`: the composing syllable pops on every accepted jamo. */
@Composable
private fun SyllablePreview(text: String, revision: Int) {
  val colors = Piyo.colors
  val scale = remember { Animatable(1f) }
  LaunchedEffect(revision) {
    if (text.isEmpty()) return@LaunchedEffect
    scale.snapTo(0.88f)
    scale.animateTo(1f, spring(dampingRatio = 0.54f, stiffness = 900f))
  }
  Box(Modifier.size(112.dp, 100.dp).animateContentSize(), contentAlignment = Alignment.Center) {
    Box(Modifier.size(96.dp).clip(CircleShape).background(colors.accentSoft.copy(alpha = 0.66f)))
    Text(
      text.ifEmpty { "…" },
      style = PiyoType.style(46f, FontWeight.Bold),
      maxLines = 1,
      modifier = Modifier.scale(scale.value),
    )
  }
}
