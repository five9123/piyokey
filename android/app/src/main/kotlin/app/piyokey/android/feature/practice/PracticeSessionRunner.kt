package app.piyokey.android.feature.practice

import android.app.Activity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.progress.RetentionClock
import app.piyokey.android.data.progress.RetentionSession
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.feature.input.SessionKeyboardState
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.audio.Pronunciation
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.platform.games.GrowthSnapshot
import app.piyokey.android.platform.games.PlayGamesService
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.CurriculumChapterCompletionPolicy
import app.piyokey.core.domain.CurriculumStarRating
import app.piyokey.core.domain.GameRecord
import app.piyokey.core.domain.HatchOnboardingPolicy
import app.piyokey.core.domain.ReviewDeckMutation
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.domain.SessionMode
import app.piyokey.core.domain.SessionReviewItem
import app.piyokey.core.domain.practice.PracticeFeedback
import app.piyokey.core.domain.practice.PracticeReactionEvent
import app.piyokey.core.domain.practice.PracticeRules
import app.piyokey.core.domain.practice.PracticeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** iOS `PracticeCompletionPersistenceState` (curriculum completion is saved durably before hatch "next"). */
enum class PracticeCompletionPersistenceState { SAVING, SAVED, FAILED }

/**
 * Everything iOS `PracticeView` does outside of layout: routes input into [PracticeSession], runs the
 * 650 ms auto-advance, persists checkpoints / review resolutions / lesson records / curriculum
 * completion, feeds the mascot store, sound, pronunciation and analytics, and handles
 * background/foreground. Main-thread only; Compose observes [version] and the state fields.
 */
@Stable
class PracticeSessionRunner(
  val config: PracticeSessionConfig,
  val keyboard: SessionKeyboardState,
  private val scope: CoroutineScope,
  private val activity: () -> Activity?,
) {
  private val session: PracticeSession = PracticeSession(
    targets = config.targets,
    checkpoint = config.curriculumStageId?.let { AppData.curriculum.checkpoint(it) },
  )

  /** Bumped after every mutation of [session]. */
  var version by mutableIntStateOf(0)
    private set

  /** The session, read through [version] so composables recompose on change. */
  val model: PracticeSession
    get() {
      version
      return session
    }

  var sessionReviewItems by mutableStateOf<List<SessionReviewItem>>(emptyList())
    private set
  var showsResult by mutableStateOf(false)
    private set
  var exitsAfterResultDismiss by mutableStateOf(false)
    private set
  var dismissesAfterPersistenceFailure by mutableStateOf(false)
    private set
  var completionPersistenceState by mutableStateOf<PracticeCompletionPersistenceState?>(null)
    private set
  var isSessionSettingsPresented by mutableStateOf(false)
    private set
  /** Bumps whenever an item completes (drives the celebration burst). */
  var celebrationRevision by mutableIntStateOf(0)
    private set

  /** Called when the whole session route should close (iOS `dismiss()`). */
  var onDismiss: () -> Unit = {}
  /** Called when a restart hook returns a replacement config (random words shuffle). */
  var onReplaceConfig: (PracticeSessionConfig) -> Unit = {}

  private var isForeground = true
  private var enteredBackground = false
  private var postCompletionJob: Job? = null
  private var completionJob: Job? = null
  private var completionGeneration = 0
  private var didReportCompletion = false
  private var didRecordCompanionOutcome = false
  private var didPersistLearningRecord = false
  private var didCaptureStart = false
  private var didCaptureCompletion = false
  private var didCaptureAbandonment = false
  private var previousAcceptedInputCount = session.totalAcceptedInputCount
  private var retentionSession = RetentionSession.start()

  val currentSource: PracticeItemSource?
    get() = config.sources.getOrNull(model.currentTargetIndex)

  val curriculumStars: Int
    get() = CurriculumStarRating.stars(model.accuracyPercent, model.charactersPerMinute)

  val analyticsInputMode: String get() = keyboard.recordInputMode.raw

  /** iOS `mascotCombo`. */
  val mascotCombo: Int
    get() = (model.reactionEvent as? PracticeReactionEvent.ComboMilestone)?.count ?: model.correctStreak

  // MARK: input

  fun input(jamo: Char) = mutate { it.input(jamo) }

  fun backspace() = mutate { it.backspace() }

  fun recordMistake() = mutate { it.recordConfirmedMistake() }

  fun synchronizeOsIme(sequence: List<Char>) = mutate { it.synchronizeOsIme(sequence) }

  private inline fun mutate(block: (PracticeSession) -> Unit) {
    val feedbackBefore = session.feedbackRevision
    val itemBefore = session.itemCompletionRevision
    val targetBefore = session.currentTargetIndex
    block(session)
    version++
    handleAcceptedInputCount()
    if (session.itemCompletionRevision != itemBefore) handleItemCompletion()
    if (session.feedbackRevision != feedbackBefore) handleFeedback()
    if (session.currentTargetIndex != targetBefore) handleTargetChange()
  }

  private fun handleAcceptedInputCount() {
    val count = session.totalAcceptedInputCount
    val delta = count - previousAcceptedInputCount
    if (delta > 0) MascotStore.recordTypedJamo(delta)
    previousAcceptedInputCount = count
  }

  private fun handleFeedback() {
    playFeedbackSound()
    val feedback = session.feedback
    if (feedback is PracticeFeedback.Incorrect) MascotStore.recordMistake(feedback.expected)
    if (!session.isComplete) persistCheckpoint()
  }

  private fun handleItemCompletion() {
    celebrationRevision++
    persistReviewResolution()
    AppData.review.flush()
    if (session.isLessonComplete) {
      Pronunciation.stop()
      finishTrackedSessionIfNeeded()
    } else {
      persistCheckpoint()
    }
    schedulePostCompletionTransition(flushingCheckpoint = !session.isLessonComplete)
  }

  private fun handleTargetChange() {
    Pronunciation.stop()
    keyboard.resetForNewTarget()
    speakCurrentTargetIfNeeded()
  }

  // MARK: lifecycle

  fun onAppear() {
    config.onAppear?.invoke()
    captureStartIfNeeded()
    previousAcceptedInputCount = session.totalAcceptedInputCount
    if (session.isLessonComplete || exitsAfterResultDismiss || dismissesAfterPersistenceFailure) {
      Pronunciation.stop()
      session.pauseTiming()
      return
    }
    session.resumeTiming()
    speakCurrentTargetIfNeeded()
    if (session.isComplete) schedulePostCompletionTransition(flushingCheckpoint = !session.isLessonComplete)
  }

  fun onDisappear() {
    cancelPostCompletionTransition()
    Pronunciation.stop()
    session.pauseTiming()
    captureAbandonmentIfNeeded()
    if (!session.isLessonComplete) persistCheckpoint()
  }

  /** App went to the background (iOS `.inactive/.background`). */
  fun onBackground() {
    isForeground = false
    enteredBackground = true
    cancelPostCompletionTransition()
    Pronunciation.stop()
    session.pauseTiming()
    if (!session.isLessonComplete) persistCheckpoint()
    version++
  }

  /** App returned to the foreground (iOS `.active`). */
  fun onForeground() {
    isForeground = true
    session.resumeTiming()
    val resumed = enteredBackground
    if (enteredBackground) {
      enteredBackground = false
      session.publishStretchReaction()
      version++
    }
    if (resumed) speakCurrentTargetIfNeeded()
    if (session.isComplete && !exitsAfterResultDismiss && !dismissesAfterPersistenceFailure) {
      schedulePostCompletionTransition(flushingCheckpoint = !session.isLessonComplete)
    }
  }

  // MARK: settings overlay

  fun presentSessionSettings() {
    if (isSessionSettingsPresented) return
    keyboard.isOsImeFocusSuspended = true
    isSessionSettingsPresented = true
  }

  fun dismissSessionSettings() {
    isSessionSettingsPresented = false
    scope.launch {
      delay(16)
      if (!showsResult) keyboard.isOsImeFocusSuspended = false
    }
  }

  fun onAutoSpeakChanged(enabled: Boolean) {
    if (enabled) speakCurrentTarget() else Pronunciation.stop()
  }

  // MARK: speech

  fun speakCurrentTargetIfNeeded() {
    if (!AppSettings.practiceAutoSpeaks.value) return
    speakCurrentTarget()
  }

  fun speakCurrentTarget() {
    if (session.isLessonComplete || showsResult || exitsAfterResultDismiss) return
    Pronunciation.speak(session.target, currentSource?.item?.audio)
  }

  // MARK: completion transition

  private fun schedulePostCompletionTransition(flushingCheckpoint: Boolean) {
    cancelPostCompletionTransition()
    if (!session.isComplete || showsResult || exitsAfterResultDismiss || dismissesAfterPersistenceFailure || !isForeground) return
    val completedIndex = session.currentTargetIndex
    val completesSession = session.isLessonComplete
    val preparedNext = flushingCheckpoint && !completesSession && session.canAdvance
    if (preparedNext) config.curriculumStageId?.let { AppData.curriculum.save(it, session.checkpointForNextTarget()) }
    postCompletionJob = scope.launch {
      if (flushingCheckpoint && config.curriculumStageId != null) AppData.curriculum.flushAndWait()
      delay(PracticeRules.POST_COMPLETION_DELAY_MILLIS)
      if (!isForeground || !isActive || session.currentTargetIndex != completedIndex || !session.isComplete ||
        showsResult || exitsAfterResultDismiss
      ) {
        return@launch
      }
      if (completesSession) {
        if (!session.isLessonComplete) return@launch
        presentResult()
      } else {
        if (!session.canAdvance) return@launch
        advanceSession(persistingCheckpoint = !preparedNext)
      }
    }
  }

  private fun cancelPostCompletionTransition() {
    postCompletionJob?.cancel()
    postCompletionJob = null
  }

  private fun advanceSession(persistingCheckpoint: Boolean) {
    cancelPostCompletionTransition()
    keyboard.resetForNewTarget()
    mutate { it.advance() }
    if (persistingCheckpoint) persistCheckpoint()
  }

  private fun presentResult() {
    Pronunciation.stop()
    keyboard.isOsImeFocusSuspended = true
    showsResult = true
  }

  // MARK: result actions

  /** Result "retry": reset the session (or replace it) and return to typing. */
  fun retryFromResult() {
    resetSession()
    showsResult = false
    keyboard.isOsImeFocusSuspended = false
  }

  /** Result "back" / "next mission" / "open app". */
  fun finishFromResult() {
    if (!showsResult || exitsAfterResultDismiss) return
    if (config.chainsHatchMissions && completionPersistenceState != PracticeCompletionPersistenceState.SAVED) return
    cancelPostCompletionTransition()
    exitsAfterResultDismiss = true
    Pronunciation.stop()
    showsResult = false
    if (!config.chainsHatchMissions) onDismiss()
    config.onResultFinished?.invoke()
  }

  fun retryCompletionPersistence() {
    if (completionPersistenceState != PracticeCompletionPersistenceState.FAILED) return
    beginCurriculumCompletionPersistence()
  }

  fun exitAfterPersistenceFailure() {
    if (!showsResult || completionPersistenceState != PracticeCompletionPersistenceState.FAILED || dismissesAfterPersistenceFailure) return
    cancelPostCompletionTransition()
    dismissesAfterPersistenceFailure = true
    Pronunciation.stop()
    showsResult = false
    config.onPersistenceFailureExit?.invoke() ?: onDismiss()
  }

  private fun resetSession() {
    cancelPostCompletionTransition()
    completionJob?.cancel()
    completionJob = null
    completionGeneration++
    completionPersistenceState = null
    sessionReviewItems = emptyList()
    didReportCompletion = false
    didRecordCompanionOutcome = false
    didPersistLearningRecord = false
    didCaptureStart = false
    didCaptureCompletion = false
    didCaptureAbandonment = false
    previousAcceptedInputCount = 0
    retentionSession = RetentionSession.start()
    val replacement = config.onSessionRestart?.invoke()
    if (replacement != null) {
      onReplaceConfig(replacement)
      return
    }
    keyboard.resetForNewTarget()
    session.reset()
    version++
    // An explicit retry replaces the old checkpoint even before the first key.
    config.curriculumStageId?.let { AppData.curriculum.save(it, session.checkpoint()) }
    captureStartIfNeeded()
    speakCurrentTargetIfNeeded()
  }

  // MARK: persistence

  private fun persistCheckpoint() {
    val stageId = config.curriculumStageId ?: return
    if (!session.hasResumableProgress) return
    AppData.curriculum.save(stageId, session.checkpoint())
  }

  private fun persistReviewResolution() {
    val resolution = session.lastCompletedItem ?: return
    val source = config.sources.getOrNull(resolution.itemIndex) ?: return
    if (resolution.hadMistake) {
      AppData.review.recordMistake(source.item, source.sourceDeckId)
      collectSessionReviewItem(source, resolution)
    } else if (AppData.review.recordPerfect(source.item.id, source.sourceDeckId) == ReviewDeckMutation.GRADUATED) {
      MascotStore.publish(MascotReaction.ReviewGraduated)
    }
  }

  private fun collectSessionReviewItem(source: PracticeItemSource, resolution: SessionItemResolution) {
    val candidate = SessionReviewItem.of(source.item, source.sourceDeckId, resolution)
    val index = sessionReviewItems.indexOfFirst { it.id == candidate.id }
    sessionReviewItems = if (index >= 0) {
      sessionReviewItems.toMutableList().also { it[index] = it[index].merge(resolution) }
    } else {
      sessionReviewItems + candidate
    }
  }

  private fun finishTrackedSessionIfNeeded() {
    session.pauseTiming()
    captureCompletionIfNeeded()
    persistLearningRecordIfNeeded()
    if (!didRecordCompanionOutcome) {
      didRecordCompanionOutcome = true
      MascotStore.recordLessonOutcome(PracticeRules.isLessonCleared(session.accuracyPercent))
    }
    if (didReportCompletion || (config.curriculumStageId == null && config.onPracticeCompletion == null)) return
    didReportCompletion = true
    if (config.curriculumStageId != null) beginCurriculumCompletionPersistence()
    config.onPracticeCompletion?.invoke(session.accuracyPercent, session.charactersPerMinute)
  }

  private fun beginCurriculumCompletionPersistence() {
    val stageId = config.curriculumStageId ?: return
    completionJob?.cancel()
    val generation = ++completionGeneration
    val accuracy = session.accuracyPercent
    val stars = curriculumStars
    completionPersistenceState = PracticeCompletionPersistenceState.SAVING
    completionJob = scope.launch {
      val saved = AppData.curriculum.finishAndWait(stageId, stars, accuracy)
      if (saved && stars > 0) {
        AppData.retention.record(RetentionActivityKind.CURRICULUM, retentionSession)
        if (config.chainsHatchMissions) {
          val index = HatchOnboardingPolicy.requiredStages.indexOfFirst { it.id == stageId }
          if (index >= 0) Telemetry.onboardingStepCompleted("hatch_${index + 1}")
        }
        PracticeGrowth.synchronize(activity())
      }
      if (!isActive || generation != completionGeneration) return@launch
      completionPersistenceState = if (saved) PracticeCompletionPersistenceState.SAVED else PracticeCompletionPersistenceState.FAILED
      completionJob = null
    }
  }

  private fun persistLearningRecordIfNeeded() {
    if (didPersistLearningRecord) return
    didPersistLearningRecord = true
    val deckIds = config.sources.map { it.sourceDeckId }.toSet()
    AppData.gameProgress.appendLesson(
      GameRecord(
        id = GameRecord.newId(),
        mode = SessionMode.LESSON,
        deckId = PracticeRules.learningRecordDeckId(deckIds, config.curriculumStageId),
        course = PracticeRules.learningRecordCourse(config.curriculumStageId),
        score = session.acceptedJamoCount,
        maxCombo = 0,
        accuracy = session.accuracyPercent,
        charactersPerMinute = session.charactersPerMinute,
        activeDuration = session.activeDuration,
        completedItemCount = session.completedItemCount,
        missedItemCount = sessionReviewItems.size,
        inputMode = app.piyokey.core.domain.SessionInputMode.fromRaw(keyboard.recordInputMode.raw) ?: app.piyokey.core.domain.SessionInputMode.BUILT_IN,
        playedAt = RetentionClock.now(),
      ),
    )
  }

  // MARK: analytics

  fun captureStartIfNeeded() {
    if (didCaptureStart) return
    didCaptureStart = true
    Telemetry.sessionStarted(config.analyticsSessionKind, config.analyticsDeckSource, analyticsInputMode)
    Telemetry.setCrashContext("practice", config.analyticsSessionKind, analyticsInputMode)
  }

  private fun captureCompletionIfNeeded() {
    if (didCaptureCompletion) return
    didCaptureCompletion = true
    Telemetry.sessionCompleted(
      sessionKind = config.analyticsSessionKind,
      result = "completed",
      durationSeconds = session.activeDuration,
      itemCount = session.completedItemCount,
      deckSource = config.analyticsDeckSource,
      inputMode = analyticsInputMode,
    )
    if (config.analyticsSessionKind == "review") Telemetry.reviewCompleted(session.completedItemCount, "completed")
  }

  private fun captureAbandonmentIfNeeded() {
    if (!didCaptureStart || didCaptureCompletion || didCaptureAbandonment) return
    didCaptureAbandonment = true
    Telemetry.sessionAbandoned(
      sessionKind = config.analyticsSessionKind,
      reason = "user_closed",
      durationSeconds = session.activeDuration,
      deckSource = config.analyticsDeckSource,
      inputMode = analyticsInputMode,
    )
  }

  // MARK: sound

  private fun playFeedbackSound() {
    when (val feedback = session.feedback) {
      PracticeFeedback.Complete -> SoundEngine.completion(mascotCombo)
      PracticeFeedback.Correct -> (session.reactionEvent as? PracticeReactionEvent.ComboMilestone)?.let { SoundEngine.completion(it.count) }
      is PracticeFeedback.Incorrect -> SoundEngine.mistake()
      PracticeFeedback.Idle -> Unit
    }
  }
}

/**
 * Growth side effects of cleared chapters: the mascot store's chapter signal and the Play Games
 * growth achievements (iOS `GameCenterService.synchronizeGrowthAchievements`).
 */
object PracticeGrowth {
  fun clearedChapterCount(completedStageIds: Set<String> = AppData.curriculum.completedStageIds): Int =
    CurriculumCatalog.chapters.count { CurriculumChapterCompletionPolicy.isCompleted(it, completedStageIds) }

  suspend fun synchronize(activity: Activity?) {
    val cleared = clearedChapterCount()
    MascotStore.updateClearedChapters(cleared)
    if (activity == null) return
    val streak = AppData.retention.streak(RetentionClock.today())
    runCatching {
      PlayGamesService.unlockAchievements(
        activity,
        GrowthSnapshot(cleared, MascotStore.typedJamoCount.value, streak.longest),
      )
    }
  }
}
