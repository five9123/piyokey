package app.piyokey.android.feature.game

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.BuildConfig
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.appMeaning
import app.piyokey.android.data.decks.appReading
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotPose
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.progress.RetentionSession
import app.piyokey.android.feature.input.OsImeInputPanel
import app.piyokey.android.feature.input.OsImeInputSourceBannerLayer
import app.piyokey.android.feature.input.SessionKeyboard
import app.piyokey.android.feature.input.SessionKeyboardState
import app.piyokey.android.feature.input.rememberSessionKeyboardState
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.platform.games.PlayGamesRules
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.mascot.rememberMascotReduceMotion
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.session.SessionExitCover
import app.piyokey.android.ui.session.heartPath
import app.piyokey.android.ui.session.starPath
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.domain.FlowGameRankTuning
import app.piyokey.core.domain.GameCompetition
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.GameRecord
import app.piyokey.core.domain.GameRecordSaveOutcome
import app.piyokey.core.domain.ReviewDeckMutation
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.SessionInputMode
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.domain.SessionMode
import app.piyokey.core.domain.SessionReviewItem
import app.piyokey.core.domain.game.AcidRainLaneLayout
import app.piyokey.core.domain.game.FlowGameCourse
import app.piyokey.core.domain.game.FlowGameEngine
import app.piyokey.core.domain.game.FlowGameFeedback
import app.piyokey.core.domain.game.FlowGameMascotEvent
import app.piyokey.core.domain.game.FlowLaneLayout
import app.piyokey.core.domain.game.GameFrameRateMonitor
import app.piyokey.core.domain.game.GamePhase
import app.piyokey.core.domain.game.GamePresetRules
import app.piyokey.core.domain.game.GamePresetSessionRandomizer
import app.piyokey.core.domain.game.GameResultPresentation
import app.piyokey.core.domain.game.MovingWordCardLayout
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Session state of iOS `FlowGameView` around the pure [FlowGameEngine]. Compose observes
 * [version] (discrete changes) and [frameTime] (continuous card motion, read in layout/draw only).
 */
@Stable
internal class FlowGameController(
  val deck: Deck,
  val gameKind: GameKind,
  val competition: GameCompetition?,
  private val keyboard: SessionKeyboardState,
  private val scope: CoroutineScope,
  private val view: View,
  private val reduceMotion: Boolean,
) {
  val isAcidRain = gameKind == GameKind.ACID_RAIN
  val course = FlowGameCourse.of(deck)
  val presentation = GameResultPresentation.of(gameKind, competition == GameCompetition.WEEKLY_PIYO_CUP)

  var sessionItems: List<DeckItem> = GamePresetSessionRandomizer.shuffledItems(deck, gameKind)
    private set

  val engine = FlowGameEngine(
    targets = sessionItems.map { it.ko },
    initialDuration = GameTestHooks.gameDurationSeconds ?: 60.0,
    cardTravelDuration = FlowGameCourse.travelDuration(course, isAcidRain),
    allowsConcurrentCards = isAcidRain,
    rankTuning = runCatching { AppData.bundled.rankTuning }.getOrElse { FlowGameRankTuning() },
  )

  var version by mutableIntStateOf(0)
    private set
  var frameTime by mutableDoubleStateOf(0.0)
    private set
  var countdownValue by mutableStateOf<Int?>(null)
    private set
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
  var lifeLossRevision by mutableIntStateOf(0)
    private set
  var completionStartedAt by mutableDoubleStateOf(-10.0)
    private set
  val frameRate = GameFrameRateMonitor()
  var frameRateVersion by mutableIntStateOf(0)
    private set

  private var countdownJob: Job? = null
  private var resultJob: Job? = null
  private var pendingStart = false
  private var pendingResume = false
  private var enteredBackground = false
  private var didPersist = false
  private var retention = RetentionSession.start()
  private var recordInputMode: SessionInputMode = domainMode(keyboard)
  private var priorBestCombo = 0
  private var didCelebrateBestCombo = false
  private var didCaptureStart = false
  private var didCaptureCompletion = false
  private var didCaptureAbandonment = false
  private var lastFeedbackRevision = engine.feedbackRevision
  private var lastAccepted = engine.totalAcceptedInputCount
  private var lastReviewRevision = engine.reviewResolutionRevision
  private var lastCardRevision = engine.cardRevision
  private var lastCompletionRevision = engine.completionRevision
  private var lastPhase = engine.phase

  val currentItem: DeckItem get() = sessionItems[engine.currentTargetIndex % sessionItems.size]
  fun item(index: Int): DeckItem = sessionItems[index % sessionItems.size]

  fun onAppear() {
    captureStart()
    priorBestCombo = AppData.gameProgress.bestCombo(deck.deckId, gameKind, recordInputMode, competition)
    if (engine.phase == GamePhase.READY) beginCountdown()
  }

  // Lifecycle (iOS scenePhase).
  fun onStop() {
    enteredBackground = true
    if (engine.phase == GamePhase.READY) cancelCountdown(reset = true)
    engine.pause(frameTime)
    frameRate.reset()
    publish()
  }

  fun onStart() {
    if (engine.phase == GamePhase.READY) beginCountdown() else pendingResume = true
  }

  fun onFrame(seconds: Double) {
    frameTime = seconds
    if (pendingStart) {
      pendingStart = false
      enteredBackground = false
      engine.start(seconds)
      if (AppData.deckLibrary.isInstalled(deck.deckId)) AppData.deckLibrary.markPlayed(deck.deckId)
    }
    if (pendingResume) {
      pendingResume = false
      engine.resume(seconds)
      if (enteredBackground && engine.phase == GamePhase.RUNNING) {
        enteredBackground = false
        engine.publishStretchReaction()
      }
    }
    if (engine.phase == GamePhase.RUNNING) {
      val before = signature()
      engine.tick(seconds)
      handleEvents()
      if (signature() != before) publish()
      if (BuildConfig.DEBUG && GameTestHooks.showsFrameRate && frameRate.record(seconds)) frameRateVersion++
    }
  }

  private fun signature(): Long {
    var hash = engine.phase.ordinal.toLong()
    hash = hash * 31 + engine.displayedSeconds
    hash = hash * 31 + engine.remainingLives
    hash = hash * 31 + engine.cardRevision
    hash = hash * 31 + engine.feedbackRevision
    hash = hash * 31 + engine.acidRainCards.sumOf { it.id * 7 + 1 }
    hash = hash * 31 + (engine.projectedCardProgress(frameTime) * 20).toInt()
    return hash
  }

  // Input.
  fun input(key: Char) = mutate { engine.input(key) }
  fun backspace() = mutate { engine.backspace() }
  fun mistake() = mutate { engine.recordConfirmedOsImeMistake() }
  fun synchronizeOsIme(sequence: List<Char>) = mutate { engine.synchronizeOsIme(sequence) }
  fun synchronizeAcidRainOsIme(target: String, sequence: List<Char>) = mutate { engine.synchronizeAcidRainOsIme(target, sequence) }

  val osImeCandidates: List<String>
    get() = if (!isAcidRain || !keyboard.usesOsIme) emptyList() else engine.acidRainCards.map { item(it.itemIndex).ko }.distinct()

  private inline fun mutate(block: () -> Unit) {
    block()
    handleEvents()
    publish()
  }

  private fun publish() {
    version++
  }

  /** iOS `onChange` handlers for feedback, accepted input, combo, review, card and phase. */
  private fun handleEvents() {
    if (engine.cardRevision != lastCardRevision) {
      lastCardRevision = engine.cardRevision
      keyboard.resetForNewTarget()
    }
    if (engine.completionRevision != lastCompletionRevision) {
      lastCompletionRevision = engine.completionRevision
      completionStartedAt = frameTime
    }
    if (engine.feedbackRevision != lastFeedbackRevision) {
      lastFeedbackRevision = engine.feedbackRevision
      when (val feedback = engine.feedback) {
        is FlowGameFeedback.Completed -> SoundEngine.completion(engine.combo)
        is FlowGameFeedback.Incorrect -> {
          SoundEngine.mistake()
          shakeRevision++
          MascotStore.recordMistake(feedback.expected)
        }
        FlowGameFeedback.Escaped -> {
          SoundEngine.lifeLost()
          GameHaptics.error(view)
          announce(view, app.piyokey.android.ui.theme.L.string(R.string.game_feedback_escaped))
          lifeLossRevision++
        }
        else -> Unit
      }
    }
    val accepted = engine.totalAcceptedInputCount
    if (accepted > lastAccepted) MascotStore.recordTypedJamo(accepted - lastAccepted)
    lastAccepted = accepted
    if (!didCelebrateBestCombo && priorBestCombo > 0 && engine.combo > priorBestCombo) {
      didCelebrateBestCombo = true
      engine.publishNewBestReaction()
    }
    if (engine.reviewResolutionRevision != lastReviewRevision) {
      lastReviewRevision = engine.reviewResolutionRevision
      engine.lastCompletedItem?.let(::persistReviewResolution)
    }
    if (engine.phase != lastPhase) {
      lastPhase = engine.phase
      if (engine.phase == GamePhase.FINISHED) {
        persistFinishedGame()
        resultJob?.cancel()
        resultJob = if (engine.feedback == FlowGameFeedback.Escaped) {
          scope.launch {
            delay(520)
            showsResult = true
          }
        } else {
          showsResult = true
          null
        }
      }
    }
  }

  private fun persistReviewResolution(resolution: SessionItemResolution) {
    val item = sessionItems.getOrNull(resolution.itemIndex) ?: return
    if (resolution.hadMistake) {
      AppData.review.recordMistake(item, deck.deckId)
      val id = app.piyokey.core.domain.ReviewDeckItem.id(item.id, deck.deckId)
      val index = reviewItems.indexOfFirst { it.id == id }
      reviewItems = if (index >= 0) {
        reviewItems.toMutableList().also { it[index] = it[index].merge(resolution) }
      } else {
        reviewItems + SessionReviewItem.of(item, deck.deckId, resolution)
      }
    } else if (AppData.review.recordPerfect(item.id, deck.deckId) == ReviewDeckMutation.GRADUATED) {
      MascotStore.publish(MascotReaction.ReviewGraduated)
    }
    AppData.review.flush()
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
        competition = GamePresetRules.resolvedCompetition(competition, gameKind, deck.deckId, deck.version, PlayGamesRules::isEligible),
        course = GamePresetRules.flowCourse(gameKind, deck),
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

  // Countdown (iOS `beginCountdown`).
  fun beginCountdown() {
    if (countdownJob != null || engine.phase != GamePhase.READY) return
    countdownJob = scope.launch {
      val values = if (reduceMotion) listOf(1) else listOf(3, 2, 1)
      val step = GameTestHooks.countdownStepMillis ?: 1_000L
      for (value in values) {
        if (engine.phase != GamePhase.READY) break
        countdownValue = value
        val duration = if (reduceMotion) minOf(step, 350L) else step
        if (duration > 0) delay(duration)
      }
      countdownJob = null
      if (engine.phase != GamePhase.READY) return@launch
      countdownValue = null
      pendingStart = true
    }
  }

  private fun cancelCountdown(reset: Boolean) {
    countdownJob?.cancel()
    countdownJob = null
    if (reset) countdownValue = null
  }

  fun retry() {
    showsResult = false
    recordOutcome = null
    didPersist = false
    reviewItems = emptyList()
    retention = RetentionSession.start()
    recordInputMode = domainMode(keyboard)
    priorBestCombo = AppData.gameProgress.bestCombo(deck.deckId, gameKind, recordInputMode, competition)
    didCelebrateBestCombo = false
    didCaptureStart = false
    didCaptureCompletion = false
    didCaptureAbandonment = false
    resultJob?.cancel()
    keyboard.resetForNewTarget()
    if (GamePresetSessionRandomizer.isBundledPreset(deck.deckId, gameKind)) {
      sessionItems = GamePresetSessionRandomizer.shuffledItems(deck, gameKind, avoiding = sessionItems)
      engine.restart(sessionItems.map { it.ko })
    } else {
      engine.restart()
    }
    lastFeedbackRevision = engine.feedbackRevision
    lastAccepted = 0
    lastReviewRevision = engine.reviewResolutionRevision
    lastCardRevision = engine.cardRevision
    lastCompletionRevision = engine.completionRevision
    lastPhase = engine.phase
    captureStart()
    publish()
    beginCountdown()
  }

  fun finishFromResult() {
    if (exits) return
    exits = true
  }

  fun onDispose() {
    cancelCountdown(reset = false)
    resultJob?.cancel()
    AppData.review.flush()
    captureAbandonment()
  }

  // Analytics (iOS `captureAnalytics*IfNeeded`).
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

  companion object {
    fun domainMode(keyboard: SessionKeyboardState): SessionInputMode =
      SessionInputMode.fromRaw(keyboard.recordInputMode.raw) ?: SessionInputMode.BUILT_IN
  }
}

/** iOS `FlowGameView`: Flow (single moving card) and Acid Rain (falling cards). */
@Composable
fun FlowGameScreen(deck: Deck, gameKind: GameKind, competition: GameCompetition?) {
  val appNav = LocalAppNavigator.current
  val scope = rememberCoroutineScope()
  val view = LocalView.current
  val reduceMotion = rememberMascotReduceMotion()
  val keyboard = rememberSessionKeyboardState()
  val controller = remember { FlowGameController(deck, gameKind, competition, keyboard, scope, view, reduceMotion) }

  LaunchedEffect(controller) {
    controller.onAppear()
    while (true) withFrameNanos { controller.onFrame(it / 1_000_000_000.0) }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.onStop() }
  LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.onStart() }
  DisposableEffect(controller) { onDispose { controller.onDispose() } }
  LaunchedEffect(controller.exits) { if (controller.exits) appNav.pop() }

  controller.version // observe discrete changes
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
  FlowPlayContent(controller, keyboard, onClose = { appNav.pop() })
}

@NonSkippableComposable
@Composable
private fun FlowPlayContent(controller: FlowGameController, keyboard: SessionKeyboardState, onClose: () -> Unit) {
  val colors = Piyo.colors
  val engine = controller.engine
  val metrics = Piyo.metrics
  val tier = FlowGameEngine.comboTier(engine.combo)
  val bottom by animateColorAsState(if (tier >= 2) colors.accentSoft.copy(alpha = 0.92f) else colors.backgroundBottom, tween(350), label = "bg")
  val shake = rememberShake(controller.lifeLossRevision, amplitude = 6f, cycles = 6f, durationMillis = 340)
  val overlaysHiddenOsIme = keyboard.usesOsIme && !metrics.isExpanded

  Box(
    Modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(colors.backgroundTop, bottom)))
      .testTag(if (controller.isAcidRain) "acid_rain.play.screen" else "game.play.screen"),
  ) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().offset { IntOffset(shake.dp.roundToPx(), 0) }) {
      GameSessionTopBar(onClose = onClose) { FlowHud(controller) }
      Column(
        Modifier
          .weight(1f)
          .centeredContent(metrics.sessionLaneMaxWidth)
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
          if (controller.isAcidRain) AcidRainLane(controller, keyboard) else FlowLane(controller, keyboard)
        }
        Box(Modifier.fillMaxWidth()) {
          InputStatus(controller, keyboard)
          if (overlaysHiddenOsIme) {
            OsImeInputPanel(
              target = engine.target,
              acceptedText = engine.enteredText,
              resetRevision = keyboard.resetRevision + engine.cardRevision,
              onAcceptedSequence = controller::synchronizeOsIme,
              onConfirmedMismatch = controller::mistake,
              modifier = Modifier.matchParentSize(),
              candidateTargets = controller.osImeCandidates,
              onAcceptedCandidateSequence = controller::synchronizeAcidRainOsIme,
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
        osImeTarget = engine.target,
        osImeAcceptedText = engine.enteredText,
        onJamo = controller::input,
        onBackspace = controller::backspace,
        onKorean10KeyMistake = controller::mistake,
        onOsImeAcceptedSequence = controller::synchronizeOsIme,
        onOsImeConfirmedMismatch = controller::mistake,
        modifier = Modifier.centeredContent(metrics.sessionLaneMaxWidth),
        osImeCandidateTargets = controller.osImeCandidates,
        onOsImeAcceptedCandidateSequence = controller::synchronizeAcidRainOsIme,
        osImeResetRevision = engine.cardRevision,
        embedsOsImeField = !overlaysHiddenOsIme,
        onOsImeInputStart = { SoundEngine.warmUp(engine.combo) },
      )
    }
    if (overlaysHiddenOsIme) {
      OsImeInputSourceBannerLayer(keyboard.osImeBanner.value, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
    }
    // Hidden accessibility values (iOS 1pt overlay texts).
    if (controller.isAcidRain) {
      Text(
        controller.currentItem.ko,
        modifier = Modifier.size(1.dp).graphicsLayer { alpha = 0.01f }.testTag("acid_rain.target.value"),
      )
    }
    LifeLossOverlay(controller.lifeLossRevision)
    controller.countdownValue?.let { GameCountdownOverlay(it, "game.countdown") }
    if (BuildConfig.DEBUG && GameTestHooks.showsFrameRate) FrameRateBadge(controller)
  }
}

@NonSkippableComposable
@Composable
private fun FlowHud(controller: FlowGameController) {
  val colors = Piyo.colors
  val engine = controller.engine
  val tier = FlowGameEngine.comboTier(engine.combo)
  val flash = rememberLifeFlash(controller.lifeLossRevision)
  CompactGameHudMetric(
    Icons.Rounded.Timer, engine.displayedSeconds.toString(), stringResource(R.string.game_time), "game.timer.value",
    if (engine.remainingTime <= 10) colors.error else colors.secondary, 58,
  )
  CompactGameHudMetric(Icons.Rounded.Star, formatNumber(engine.score), stringResource(R.string.game_score), "game.score.value", colors.accent, 58)
  CompactGameHudMetric(
    Icons.Rounded.LocalFireDepartment, engine.combo.toString(), stringResource(R.string.game_combo), "game.combo.value",
    if (tier == 0) colors.mutedInk else GameColors.orange, 58,
  )
  CompactGameHudMetric(
    Icons.Rounded.Favorite, engine.remainingLives.toString(), stringResource(R.string.game_lives), "game.lives.value",
    if (engine.remainingLives == 1) colors.error else colors.accent, 58,
    Modifier.scale(1 + flash.value * 0.12f).shadow((8 * flash.value).dp, RoundedCornerShape(10.dp), spotColor = colors.error),
  )
}

/** Horizontal shake driven by a revision (iOS `FlowLifeLossShakeEffect` / `FlowCardShakeEffect`). */
@Composable
private fun rememberShake(revision: Int, amplitude: Float, cycles: Float, durationMillis: Int): Float {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(revision) {
    if (revision == 0) return@LaunchedEffect
    progress.snapTo(0f)
    progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
    progress.snapTo(0f)
  }
  return amplitude * sin(progress.value * PI.toFloat() * cycles)
}

@Composable
private fun rememberLifeFlash(revision: Int): Animatable<Float, *> {
  val flash = remember { Animatable(0f) }
  LaunchedEffect(revision) {
    if (revision == 0) return@LaunchedEffect
    flash.snapTo(1f)
    delay(140)
    flash.animateTo(0f, tween(420))
  }
  return flash
}

@Composable
private fun LifeLossOverlay(revision: Int) {
  val colors = Piyo.colors
  val flash = rememberLifeFlash(revision)
  if (flash.value <= 0f) return
  Box(
    Modifier.fillMaxSize().background(colors.error.copy(alpha = 0.16f * flash.value)).testTag("game.life_loss.feedback"),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      Modifier
        .scale(0.96f + flash.value * 0.08f)
        .shadow(12.dp, CircleShape, spotColor = colors.error)
        .clip(CircleShape)
        .background(colors.error.copy(alpha = 0.94f))
        .padding(horizontal = 18.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(Icons.Rounded.HeartBroken, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
      Text(stringResource(R.string.game_feedback_escaped), style = PiyoType.headline().copy(fontWeight = FontWeight.Black, color = Color.White))
    }
  }
}

@NonSkippableComposable
@Composable
private fun cardShakeOffset(controller: FlowGameController): Float =
  rememberShake(controller.shakeRevision, amplitude = 7f, cycles = 3f, durationMillis = 240)

@NonSkippableComposable
@Composable
private fun FlowLane(controller: FlowGameController, keyboard: SessionKeyboardState) {
  val colors = Piyo.colors
  val engine = controller.engine
  val item = controller.currentItem
  val fontScale = Piyo.fontScale
  val shake = cardShakeOffset(controller)
  val density = LocalDensity.current
  val laneShape = RoundedCornerShape(26.dp)
  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .heightIn(min = if (keyboard.usesOsIme) 110.dp else 178.dp)
      .clip(laneShape)
      .border(1.dp, colors.secondary.copy(alpha = 0.16f), laneShape),
  ) {
    val containerWidth = maxWidth.value
    val cardWidth = MovingWordCardLayout.flowWidth(
      item.ko, item.appMeaning ?: "", item.appReading ?: "", fontScale, minOf(236f, containerWidth * 0.68f),
    )
    FlowSimpleBackdrop()
    Box(
      Modifier
        .align(Alignment.CenterStart)
        .width(cardWidth.dp)
        .offset {
          val progress = engine.projectedCardProgress(controller.frameTime)
          val x = FlowLaneLayout.horizontalOffset(progress, containerWidth, cardWidth) + shake
          IntOffset(with(density) { x.dp.roundToPx() }, 0)
        },
    ) {
      CheeringCard(item)
    }
    ComboParticles(controller, Modifier.fillMaxSize())
    Text(
      stringResource(controller.course.nameRes),
      style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = colors.secondary),
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(10.dp)
        .clip(CircleShape)
        .background(colors.secondary.copy(alpha = 0.07f))
        .padding(horizontal = 10.dp, vertical = 6.dp)
        .testTag("game.flow.simple_lane"),
    )
    GameMascot(controller, if (keyboard.usesOsIme) 34.dp else 43.dp, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 7.dp))
  }
}

@Composable
private fun FlowSimpleBackdrop() {
  val colors = Piyo.colors
  Box(Modifier.fillMaxSize().background(colors.card)) {
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(38.dp).background(colors.mutedInk.copy(alpha = 0.06f)))
    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
      repeat(11) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
          Box(Modifier.size(14.dp).clip(CircleShape).background(colors.mutedInk.copy(alpha = 0.12f)))
        }
      }
    }
  }
}

@Composable
private fun CheeringCard(item: DeckItem) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(18.dp)
  val meaning = item.appMeaning
  val reading = item.appReading
  val detail = listOfNotNull(meaning, reading).joinToString(", ")
  Column(
    Modifier
      .fillMaxWidth()
      .shadow(9.dp, shape, spotColor = colors.accent.copy(alpha = 0.15f))
      .clip(shape)
      .background(colors.card)
      .border(2.dp, colors.accentSoft, shape)
      .padding(horizontal = 14.dp, vertical = 11.dp)
      .semantics(mergeDescendants = true) { contentDescription = item.ko; stateDescription = detail }
      .testTag("game.target.value"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(item.ko, style = PiyoType.style(25f, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Clip)
    meaning?.let {
      Text(it, style = PiyoType.caption().copy(fontWeight = FontWeight.SemiBold, color = colors.mutedInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    reading?.let {
      Text("[$it]", style = PiyoType.caption2().copy(fontWeight = FontWeight.Medium, color = colors.secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@NonSkippableComposable
@Composable
private fun AcidRainLane(controller: FlowGameController, keyboard: SessionKeyboardState) {
  val colors = Piyo.colors
  val engine = controller.engine
  val fontScale = Piyo.fontScale
  val density = LocalDensity.current
  val shake = cardShakeOffset(controller)
  val laneShape = RoundedCornerShape(26.dp)
  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .heightIn(min = if (keyboard.usesOsIme) 150.dp else 214.dp)
      .clip(laneShape)
      .border(1.2.dp, colors.secondary.copy(alpha = 0.24f), laneShape)
      .testTag("acid_rain.lane"),
  ) {
    val width = maxWidth.value
    val height = maxHeight.value
    AcidRainBackdrop()
    RainStreaks(controller, Modifier.fillMaxSize())
    val acceptsFree = keyboard.usesOsIme
    engine.acidRainCards.forEach { card ->
      val item = controller.item(card.itemIndex)
      val isTarget = engine.projectedAcidRainCards(controller.frameTime).firstOrNull { it.id == card.id }?.isInputTarget == true
      val showsTarget = isTarget && (!keyboard.usesOsIme || engine.completedJamoCount > 0)
      val cardWidth = MovingWordCardLayout.acidRainWidth(
        item.ko, item.appMeaning ?: "", item.appReading ?: "", fontScale, AcidRainLaneLayout.cardWidth(width),
      )
      var measuredHeight by remember(card.id) { mutableIntStateOf(0) }
      Box(
        Modifier
          .width(cardWidth.dp)
          .onSizeChanged { measuredHeight = it.height }
          .offset {
            val snapshot = engine.projectedAcidRainCards(controller.frameTime).firstOrNull { it.id == card.id }
            val progress = snapshot?.progress ?: 0.0
            val cx = AcidRainLaneLayout.cardCenterX(card.laneIndex, width, cardWidth) - cardWidth / 2 + if (isTarget) shake else 0f
            val cy = with(density) { AcidRainLaneLayout.cardCenterY(progress, height).dp.toPx() } - measuredHeight / 2f
            IntOffset(with(density) { cx.dp.roundToPx() }, cy.roundToInt())
          }
          .zIndex(if (isTarget) 1f else 0f),
      ) {
        RainCard(controller, item, showsTarget, acceptsFree)
      }
    }
    ComboParticles(controller, Modifier.fillMaxSize())
    Row(
      Modifier
        .align(Alignment.TopStart)
        .padding(10.dp)
        .clip(CircleShape)
        .background(colors.card.copy(alpha = 0.8f))
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(Icons.Rounded.Thunderstorm, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(12.dp))
      Text(stringResource(R.string.game_mode_acid_rain), style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = colors.secondary))
    }
    GameMascot(controller, 34.dp, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp))
  }
}

@Composable
private fun AcidRainBackdrop() {
  val colors = Piyo.colors
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.secondary.copy(alpha = 0.12f), colors.card.copy(alpha = 0.88f))))) {
    Row(
      Modifier
        .fillMaxSize()
        .padding(start = 12.dp, end = 12.dp, top = 46.dp, bottom = (AcidRainLaneLayout.DANGER_ZONE_HEIGHT + 8).dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      repeat(3) {
        val stroke = colors.secondary.copy(alpha = 0.09f)
        Canvas(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(colors.secondary.copy(alpha = 0.025f))) {
          drawRoundRect(
            stroke,
            cornerRadius = CornerRadius(18.dp.toPx()),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 8.dp.toPx()))),
          )
        }
      }
    }
    Column(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(AcidRainLaneLayout.DANGER_ZONE_HEIGHT.dp)
        .background(Brush.verticalGradient(listOf(colors.error.copy(alpha = 0.13f), colors.secondary.copy(alpha = 0.08f))))
        .padding(horizontal = 8.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Box(Modifier.fillMaxWidth().height(2.dp).background(colors.error.copy(alpha = 0.58f)))
      Row(Modifier.fillMaxWidth()) {
        repeat(9) {
          Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.error.copy(alpha = 0.42f),
            modifier = Modifier.weight(1f).size(12.dp),
          )
        }
      }
    }
  }
}

@NonSkippableComposable
@Composable
private fun RainStreaks(controller: FlowGameController, modifier: Modifier) {
  val color = Piyo.colors.secondary.copy(alpha = 0.18f)
  Canvas(modifier) {
    val progress = controller.engine.projectedCardProgress(controller.frameTime)
    for (index in 0 until 15) {
      val x = size.width * ((index * 37) % 101) / 100f
      val offset = (progress * 120).toFloat() + index * 29f
      val y = offset.dp.toPx() % maxOf(size.height, 1f)
      drawRoundRect(color, Offset(x, y), Size(2.dp.toPx(), 18.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
  }
}

@NonSkippableComposable
@Composable
private fun RainCard(controller: FlowGameController, item: DeckItem, isInputTarget: Boolean, acceptsFreeInput: Boolean) {
  val colors = Piyo.colors
  val engine = controller.engine
  val shape = RoundedCornerShape(17.dp)
  val border = when {
    isInputTarget -> colors.accent
    acceptsFreeInput -> colors.accent.copy(alpha = 0.56f)
    else -> colors.secondary.copy(alpha = 0.42f)
  }
  val detail = listOfNotNull(item.appMeaning, item.appReading).joinToString(", ")
  Column(
    Modifier
      .fillMaxWidth()
      .shadow(if (isInputTarget) 10.dp else 6.dp, shape, spotColor = if (isInputTarget) colors.accent.copy(alpha = 0.24f) else colors.secondary.copy(alpha = 0.16f))
      .clip(shape)
      .background(colors.card)
      .border(if (isInputTarget) 2.5.dp else 1.5.dp, border, shape)
      .padding(horizontal = 12.dp, vertical = 9.dp)
      .semantics(mergeDescendants = true) { contentDescription = item.ko; stateDescription = detail }
      .testTag("acid_rain.falling_card"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    Text(item.ko, style = PiyoType.style(21f, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Clip)
    val progress = if (isInputTarget) engine.completedJamoCount.toFloat() / maxOf(engine.targetJamoSequence.size, 1) else 0f
    LinearProgressIndicator(
      progress = { progress },
      modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
      color = if (isInputTarget) colors.success else colors.secondary.copy(alpha = 0.45f),
      trackColor = colors.accentSoft.copy(alpha = 0.4f),
      drawStopIndicator = {},
    )
    item.appMeaning?.let { Text(it, style = PiyoType.caption2().copy(color = colors.mutedInk), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    item.appReading?.let {
      Text("[$it]", style = PiyoType.style(10f, FontWeight.Medium).copy(color = colors.secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@NonSkippableComposable
@Composable
private fun ComboParticles(controller: FlowGameController, modifier: Modifier) {
  val accent = Piyo.colors.accent
  val secondary = Piyo.colors.secondary
  val tier = FlowGameEngine.comboTier(controller.engine.combo)
  Canvas(modifier) {
    val progress = ((controller.frameTime - controller.completionStartedAt) / 0.52).coerceIn(0.0, 1.0)
    if (controller.engine.completionRevision == 0 || progress >= 1) return@Canvas
    val center = Offset(size.width / 2, size.height / 2)
    val count = if (tier >= 2) 16 else 10
    val particle = 14.dp.toPx()
    for (index in 0 until count) {
      val angle = index.toDouble() / count * PI * 2 - PI / 2
      val radius = (20 + progress * (if (tier >= 3) 120 else 82)).dp.toPx()
      val point = Offset(center.x + (cos(angle) * radius).toFloat(), center.y + (sin(angle) * radius).toFloat())
      val path = if (index % 2 == 0) heartPath(Size(particle, particle)) else starPath(Size(particle, particle))
      translate(point.x - particle / 2, point.y - particle / 2) {
        drawPath(path, if (index % 2 == 0) accent else secondary, alpha = (1 - progress).toFloat())
      }
    }
  }
}

@NonSkippableComposable
@Composable
private fun GameMascot(controller: FlowGameController, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
  val engine = controller.engine
  val progress = engine.projectedCardProgress(controller.frameTime)
  val mood = when {
    engine.remainingTime <= 10 -> MascotMood.GRIT
    engine.consecutiveMistakes >= 3 -> MascotMood.DIZZY
    else -> when (engine.mascotEvent) {
      FlowGameMascotEvent.NewBest -> MascotMood.SURPRISE
      is FlowGameMascotEvent.Startle -> MascotMood.OOPS
      FlowGameMascotEvent.WordCompleted, is FlowGameMascotEvent.Rhythm -> MascotMood.CHEER
      FlowGameMascotEvent.CorrectJamo -> MascotMood.HAPPY
      is FlowGameMascotEvent.Dizzy -> MascotMood.DIZZY
      FlowGameMascotEvent.Stretch, FlowGameMascotEvent.Idle -> if (engine.combo >= 10) MascotMood.FOCUS else MascotMood.IDLE
    }
  }
  val reaction = when (val event = engine.mascotEvent) {
    FlowGameMascotEvent.Idle -> MascotReaction.None
    FlowGameMascotEvent.CorrectJamo -> MascotReaction.CorrectJamo
    FlowGameMascotEvent.WordCompleted -> MascotReaction.WordCompleted
    is FlowGameMascotEvent.Rhythm -> MascotReaction.Rhythm(event.combo)
    is FlowGameMascotEvent.Startle -> MascotReaction.Startle
    is FlowGameMascotEvent.Dizzy -> MascotReaction.Mistake
    FlowGameMascotEvent.Stretch -> MascotReaction.Stretch
    FlowGameMascotEvent.NewBest -> MascotReaction.NewBest
  }
  val speech = (engine.feedback as? FlowGameFeedback.Incorrect)
    ?.takeIf { engine.consecutiveMistakes >= 3 }
    ?.let { stringResource(R.string.mascot_speech_missed_jamo, it.expected.toString()) }
  val label = stringResource(R.string.game_mascot_label)
  GrowingMascot(
    modifier = modifier.semantics { contentDescription = label }.testTag("game.mascot"),
    mood = mood,
    reaction = reaction,
    reactionRevision = engine.mascotRevision,
    gazeX = (1 - progress * 2).toFloat(),
    gazeY = if (engine.consecutiveMistakes > 0) 0.7f else -0.05f,
    pose = MascotPose.FRONT,
    intensity = minOf(engine.combo / 20f, 1f),
    speech = speech,
    size = size,
    calm = true,
  )
}

@NonSkippableComposable
@Composable
private fun InputStatus(controller: FlowGameController, keyboard: SessionKeyboardState) {
  val colors = Piyo.colors
  val engine = controller.engine
  val free = controller.isAcidRain && keyboard.usesOsIme
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(colors.card.copy(alpha = 0.84f))
      .padding(horizontal = 12.dp, vertical = 9.dp)
      .then(if (free) Modifier.testTag("acid_rain.os_ime.free_input_status") else Modifier),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    if (!free || engine.completedJamoCount > 0) {
      JamoProgressTrack(engine.targetJamoSequence, engine.completedJamoCount, engine.cardRevision)
    }
    keyboard.korean10KeyPendingDisplay?.let {
      Text(it, style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary), modifier = Modifier.testTag("game.10key.pending"))
    }
    val statusModifier = Modifier.fillMaxWidth().heightIn(min = 25.dp).testTag("game.feedback.status")
    if (free && engine.completedJamoCount == 0 && engine.composingPreview.isEmpty() && engine.feedback == FlowGameFeedback.Ready) {
      Text(stringResource(R.string.acid_rain_os_ime_any_word_hint), style = PiyoType.body().copy(color = colors.mutedInk), textAlign = TextAlign.Center, modifier = statusModifier)
    } else {
      FeedbackLabel(engine.feedback, statusModifier)
    }
  }
}

@Composable
private fun FeedbackLabel(feedback: FlowGameFeedback, modifier: Modifier) {
  val colors = Piyo.colors
  val (text, color) = when (feedback) {
    FlowGameFeedback.Ready -> stringResource(R.string.game_feedback_ready) to colors.mutedInk
    FlowGameFeedback.Correct -> stringResource(R.string.game_feedback_correct) to colors.success
    is FlowGameFeedback.Incorrect -> stringResource(R.string.game_feedback_incorrect) to colors.error
    is FlowGameFeedback.Completed -> stringResource(R.string.game_feedback_completed, feedback.points) to colors.accent
    FlowGameFeedback.Escaped -> stringResource(R.string.game_feedback_escaped) to colors.mutedInk
  }
  Text(text, style = PiyoType.body().copy(color = color), textAlign = TextAlign.Center, modifier = modifier)
}

/** iOS `FlowJamoProgressTrack`: jamo chips that auto-scroll to the active one. */
@Composable
private fun JamoProgressTrack(sequence: List<Char>, completed: Int, cardRevision: Int) {
  val colors = Piyo.colors
  val listState = remember(cardRevision) { androidx.compose.foundation.lazy.LazyListState() }
  LaunchedEffect(completed, cardRevision) {
    if (sequence.isNotEmpty()) {
      val active = minOf(completed, sequence.size - 1)
      val center = (listState.layoutInfo.viewportSize.width / 2)
      listState.animateScrollToItem(active, -center + 12)
    }
  }
  BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp).testTag("game.jamo.track")) {
    LazyRow(
      state = listState,
      modifier = Modifier.fillMaxWidth().widthIn(min = maxWidth),
      horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
      itemsIndexed(sequence) { index, jamo ->
        val done = index < completed
        val shape = RoundedCornerShape(8.dp)
        Box(
          Modifier
            .size(24.dp, 28.dp)
            .clip(shape)
            .background(if (done) colors.accent else colors.accentSoft.copy(alpha = 0.42f))
            .then(if (index == completed) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .testTag(if (index == completed) "game.jamo.active" else "game.jamo.$index"),
          contentAlignment = Alignment.Center,
        ) {
          Text(jamo.toString(), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = if (done) Color.White else colors.ink))
        }
      }
    }
  }
}

@NonSkippableComposable
@Composable
private fun FrameRateBadge(controller: FlowGameController) {
  controller.frameRateVersion
  val snapshot = controller.frameRate.snapshot ?: return
  Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
    Text(
      stringResource(R.string.debug_game_fps_format, snapshot.averageFps, snapshot.p95FrameDurationMilliseconds, snapshot.overBudgetFrameCount),
      style = PiyoType.caption().copy(fontWeight = FontWeight.Black, color = Color.Black),
      modifier = Modifier
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.96f))
        .border(1.dp, Piyo.colors.accent.copy(alpha = 0.5f), CircleShape)
        .padding(horizontal = 10.dp, vertical = 5.dp)
        .testTag("debug.game_fps"),
    )
  }
}
