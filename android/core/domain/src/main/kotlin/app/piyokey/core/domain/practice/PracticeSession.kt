package app.piyokey.core.domain.practice

import app.piyokey.core.domain.PracticeSessionCheckpoint
import app.piyokey.core.domain.SessionItemResolution
import app.piyokey.core.hangul.CompositionEvent
import app.piyokey.core.hangul.CompositionPhase
import app.piyokey.core.hangul.CompositionState
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge
import java.text.BreakIterator
import java.util.Locale

/** iOS `TargetSyllableProgress`: one visible character of the target and its jamo range. */
data class TargetSyllableProgress(
  val offset: Int,
  /** One grapheme cluster of the target (a syllable, a space or a literal). */
  val character: String,
  /** Jamo index range `[jamoStart, jamoEnd)` inside the target key sequence. */
  val jamoStart: Int,
  val jamoEnd: Int,
  val state: State,
) {
  enum class State { PENDING, IN_PROGRESS, COMPLETED }

  val id: Int get() = offset
  val isHangul: Boolean get() = JamoDecomposer.containsHangul(character)
  val isWhitespace: Boolean get() = character.isNotEmpty() && character.all { it.isWhitespace() }
}

/** iOS `PracticeReactionEvent`: what the mascot reacts to after each input. */
sealed interface PracticeReactionEvent {
  data object Idle : PracticeReactionEvent
  data object CorrectJamo : PracticeReactionEvent
  data class SyllableCompleted(val index: Int) : PracticeReactionEvent
  data object WordCompleted : PracticeReactionEvent
  data class ComboMilestone(val count: Int) : PracticeReactionEvent
  data class Mistake(val expected: Char) : PracticeReactionEvent
  data object PerfectSession : PracticeReactionEvent
  data class Dizzy(val expected: Char) : PracticeReactionEvent
  data object Stretch : PracticeReactionEvent
}

/** iOS `PracticeSessionViewModel.Feedback`. */
sealed interface PracticeFeedback {
  data object Idle : PracticeFeedback
  data object Correct : PracticeFeedback
  data class Incorrect(val expected: Char) : PracticeFeedback
  data object Complete : PracticeFeedback
}

/**
 * Pure port of iOS `PracticeSessionViewModel`: sequential jamo judging over several targets,
 * composition preview, per-item mistake resolutions (review collection), combo/reaction events,
 * active-time accounting (pause/resume), accuracy/CPM and resumable checkpoints.
 *
 * The class is mutable and single-threaded (call it from the main thread). UI layers observe it by
 * bumping their own revision after each call; [feedbackRevision], [itemCompletionRevision],
 * [compositionRevision] and [reactionRevision] mirror the iOS `@Published` revisions.
 *
 * @param now monotonic clock in seconds (injectable for tests).
 */
class PracticeSession(
  val targets: List<String>,
  checkpoint: PracticeSessionCheckpoint? = null,
  private val now: () -> Double = { System.nanoTime() / 1_000_000_000.0 },
) {
  private val targetJamoCounts: List<Int>

  var currentTargetIndex: Int = 0; private set
  var target: String; private set
  var targetJamoSequence: List<Char>; private set
  var composition: CompositionState = CompositionState(); private set
  var judgeState: JamoJudgeState; private set
  var feedback: PracticeFeedback = PracticeFeedback.Idle; private set
  var feedbackRevision: Int = 0; private set
  var compositionRevision: Int = 0; private set
  var lastAcceptedKey: Char? = null; private set
  var shouldAnimateSyllableJoin: Boolean = false; private set
  var mistakeCount: Int = 0; private set
  var lastCompletedItem: SessionItemResolution? = null; private set
  var itemCompletionRevision: Int = 0; private set
  var correctStreak: Int = 0; private set
  var reactionEvent: PracticeReactionEvent = PracticeReactionEvent.Idle; private set
  var reactionRevision: Int = 0; private set
  var consecutiveMistakes: Int = 0; private set
  var totalAcceptedInputCount: Int = 0; private set

  private val acceptedKeys = ArrayList<Char>()
  private var targetSyllableRanges: List<Triple<String, Int, Int>> = emptyList()
  private var currentTargetMistakeCount = 0
  private val currentTargetMistakenJamoIndices = LinkedHashSet<Int>()
  private var accumulatedActiveDuration = 0.0
  private var activeStartedAt: Double? = null
  private var hasStartedTiming = false

  init {
    require(targets.isNotEmpty()) { "Practice targets must not be empty" }
    targetJamoCounts = targets.map { JamoDecomposer.keySequence(it).size }
    target = targets[0]
    judgeState = JamoJudgeState(target)
    targetJamoSequence = judgeState.expectedSequence
    targetSyllableRanges = makeTargetSyllableRanges(target)
    if (checkpoint != null) restore(checkpoint)
  }

  constructor(target: String) : this(listOf(target))

  val completedJamoCount: Int get() = judgeState.currentIndex
  val acceptedKeySequence: List<Char> get() = acceptedKeys.toList()
  val nextExpectedKey: Char? get() = judgeState.expectedNext
  val isComplete: Boolean get() = judgeState.isComplete
  val canAdvance: Boolean get() = isComplete && currentTargetIndex + 1 < targets.size
  val isLessonComplete: Boolean get() = isComplete && currentTargetIndex == targets.size - 1
  val hasResumableProgress: Boolean get() = hasStartedTiming && !isLessonComplete
  val enteredText: String get() = composition.text
  val composingPreview: String get() = composition.composingText

  val targetSyllableProgress: List<TargetSyllableProgress>
    get() = targetSyllableRanges.mapIndexed { offset, (character, start, end) ->
      val state = when {
        completedJamoCount >= end -> TargetSyllableProgress.State.COMPLETED
        completedJamoCount > start -> TargetSyllableProgress.State.IN_PROGRESS
        else -> TargetSyllableProgress.State.PENDING
      }
      TargetSyllableProgress(offset, character, start, end, state)
    }

  val completedSyllableCount: Int
    get() = targetSyllableProgress.count { it.isHangul && it.state == TargetSyllableProgress.State.COMPLETED }

  val targetSyllableCount: Int get() = targetSyllableProgress.count { it.isHangul }

  val acceptedJamoCount: Int
    get() = targetJamoCounts.take(currentTargetIndex).sum() + judgeState.currentIndex

  /** Accepted jamo / (accepted + mistakes) × 100; 0 before any input. */
  val accuracyPercent: Double
    get() {
      val total = acceptedJamoCount + mistakeCount
      if (total <= 0) return 0.0
      return acceptedJamoCount.toDouble() / total * 100
    }

  val completedItemCount: Int get() = currentTargetIndex + if (isComplete) 1 else 0

  /** Seconds spent typing (excludes paused/background time). */
  val activeDuration: Double
    get() = accumulatedActiveDuration + (activeStartedAt?.let { maxOf(0.0, now() - it) } ?: 0.0)

  /** Accepted jamo per minute of [activeDuration]. */
  val charactersPerMinute: Double
    get() {
      val duration = activeDuration
      if (duration <= 0) return 0.0
      return acceptedJamoCount / (duration / 60)
    }

  fun input(key: Char) {
    if (judgeState.isComplete) return
    startTimingIfNeeded()

    val evaluation = JamoSequenceJudge.evaluate(key, judgeState)
    judgeState = evaluation.state

    when (val result = evaluation.result) {
      is JamoJudgeResult.Correct -> {
        val completed = result.completed
        val previousPhase = composition.phase
        acceptedKeys += key
        totalAcceptedInputCount += 1
        consecutiveMistakes = 0
        correctStreak += 1
        composition = HangulComposer.reduce(composition, CompositionEvent.Key(key))
        lastAcceptedKey = key
        shouldAnimateSyllableJoin = isSyllableJoin(previousPhase, composition.phase)
        compositionRevision += 1
        feedback = if (completed) PracticeFeedback.Complete else PracticeFeedback.Correct
        if (completed) {
          lastCompletedItem = SessionItemResolution(
            itemIndex = currentTargetIndex,
            hadMistake = currentTargetMistakeCount > 0,
            mistakeCount = currentTargetMistakeCount,
            mistakenJamoIndices = currentTargetMistakenJamoIndices.toSet(),
          )
          itemCompletionRevision += 1
          if (isLessonComplete) pauseTiming()
        }
        publishReaction(reactionForAcceptedInput(completed))
      }
      is JamoJudgeResult.Incorrect -> registerMistake(result.expected)
      JamoJudgeResult.AlreadyComplete -> feedback = PracticeFeedback.Complete
    }
    feedbackRevision += 1
  }

  fun backspace() {
    if (acceptedKeys.isEmpty()) return
    acceptedKeys.removeAt(acceptedKeys.lastIndex)
    composition = HangulComposer.reduce(composition, CompositionEvent.Backspace)
    lastAcceptedKey = acceptedKeys.lastOrNull()
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    rebuildJudgeFromAcceptedKeys()
    correctStreak = 0
    feedback = PracticeFeedback.Idle
    publishReaction(PracticeReactionEvent.Idle)
    feedbackRevision += 1
  }

  /** OS-IME snapshot: move progress to exactly [acceptedSequence] (a prefix of the target sequence). */
  fun synchronizeOsIme(acceptedSequence: List<Char>) {
    if (acceptedSequence.size > targetJamoSequence.size) return
    if (targetJamoSequence.subList(0, acceptedSequence.size) != acceptedSequence) return
    while (acceptedKeys.size > acceptedSequence.size) backspace()
    if (acceptedKeys.size >= acceptedSequence.size) return
    for (key in acceptedSequence.drop(acceptedKeys.size)) input(key)
  }

  /** A confirmed OS-IME mismatch or an impossible 10-key stroke: one mistake, no progress. */
  fun recordConfirmedMistake() {
    if (judgeState.isComplete) return
    val expected = judgeState.expectedNext ?: return
    startTimingIfNeeded()
    registerMistake(expected)
    feedbackRevision += 1
  }

  fun reset() {
    currentTargetIndex = 0
    target = targets[0]
    targetSyllableRanges = makeTargetSyllableRanges(target)
    targetJamoSequence = JamoJudgeState(target).expectedSequence
    acceptedKeys.clear()
    composition = CompositionState()
    lastAcceptedKey = null
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    rebuildJudgeFromAcceptedKeys()
    feedback = PracticeFeedback.Idle
    mistakeCount = 0
    consecutiveMistakes = 0
    totalAcceptedInputCount = 0
    currentTargetMistakeCount = 0
    currentTargetMistakenJamoIndices.clear()
    lastCompletedItem = null
    correctStreak = 0
    reactionEvent = PracticeReactionEvent.Idle
    reactionRevision += 1
    accumulatedActiveDuration = 0.0
    activeStartedAt = null
    hasStartedTiming = false
    feedbackRevision += 1
  }

  fun advance() {
    if (!canAdvance) return
    currentTargetIndex += 1
    target = targets[currentTargetIndex]
    targetSyllableRanges = makeTargetSyllableRanges(target)
    targetJamoSequence = JamoJudgeState(target).expectedSequence
    acceptedKeys.clear()
    composition = CompositionState()
    lastAcceptedKey = null
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    rebuildJudgeFromAcceptedKeys()
    feedback = PracticeFeedback.Idle
    currentTargetMistakeCount = 0
    currentTargetMistakenJamoIndices.clear()
    feedbackRevision += 1
  }

  fun pauseTiming() {
    val started = activeStartedAt ?: return
    accumulatedActiveDuration += maxOf(0.0, now() - started)
    activeStartedAt = null
  }

  fun resumeTiming() {
    if (!hasStartedTiming || activeStartedAt != null || isLessonComplete) return
    activeStartedAt = now()
  }

  fun publishStretchReaction() = publishReaction(PracticeReactionEvent.Stretch)

  fun checkpoint(): PracticeSessionCheckpoint = PracticeSessionCheckpoint(
    currentTargetIndex = currentTargetIndex,
    acceptedKeys = String(acceptedKeys.toCharArray()),
    mistakeCount = mistakeCount,
    currentTargetMistakeCount = currentTargetMistakeCount,
    currentTargetMistakenJamoIndices = currentTargetMistakenJamoIndices.sorted(),
    activeDuration = activeDuration,
  )

  /** Checkpoint that already points at the next target (persisted before the visible advance). */
  fun checkpointForNextTarget(): PracticeSessionCheckpoint {
    if (!canAdvance) return checkpoint()
    return PracticeSessionCheckpoint(
      currentTargetIndex = currentTargetIndex + 1,
      acceptedKeys = "",
      mistakeCount = mistakeCount,
      currentTargetMistakeCount = 0,
      currentTargetMistakenJamoIndices = emptyList(),
      activeDuration = activeDuration,
    )
  }

  private fun registerMistake(expected: Char) {
    mistakeCount += 1
    consecutiveMistakes += 1
    currentTargetMistakeCount += 1
    currentTargetMistakenJamoIndices += judgeState.currentIndex
    correctStreak = 0
    feedback = PracticeFeedback.Incorrect(expected)
    publishReaction(
      if (consecutiveMistakes >= 3) PracticeReactionEvent.Dizzy(expected) else PracticeReactionEvent.Mistake(expected),
    )
  }

  private fun rebuildJudgeFromAcceptedKeys() {
    var rebuilt = JamoJudgeState(target)
    for (key in acceptedKeys) rebuilt = JamoSequenceJudge.evaluate(key, rebuilt).state
    judgeState = rebuilt
  }

  private fun startTimingIfNeeded() {
    if (activeStartedAt != null || isLessonComplete) return
    hasStartedTiming = true
    activeStartedAt = now()
  }

  private fun restore(checkpoint: PracticeSessionCheckpoint) {
    if (checkpoint.currentTargetIndex !in targets.indices ||
      checkpoint.mistakeCount < 0 ||
      checkpoint.currentTargetMistakeCount < 0 ||
      checkpoint.activeDuration < 0
    ) {
      return
    }
    currentTargetIndex = checkpoint.currentTargetIndex
    target = targets[currentTargetIndex]
    targetSyllableRanges = makeTargetSyllableRanges(target)
    targetJamoSequence = JamoJudgeState(target).expectedSequence
    val candidateKeys = checkpoint.acceptedKeys.toList()
    acceptedKeys.clear()
    acceptedKeys += candidateKeys

    var restoredJudge = JamoJudgeState(target)
    var restoredComposition = CompositionState()
    var isValidPrefix = true
    for (key in candidateKeys) {
      val evaluation = JamoSequenceJudge.evaluate(key, restoredJudge)
      if (evaluation.result !is JamoJudgeResult.Correct) {
        isValidPrefix = false
        break
      }
      restoredJudge = evaluation.state
      restoredComposition = HangulComposer.reduce(restoredComposition, CompositionEvent.Key(key))
    }
    if (!isValidPrefix) {
      acceptedKeys.clear()
      restoredJudge = JamoJudgeState(target)
      restoredComposition = CompositionState()
    }

    judgeState = restoredJudge
    composition = restoredComposition
    lastAcceptedKey = acceptedKeys.lastOrNull()
    mistakeCount = checkpoint.mistakeCount
    currentTargetMistakeCount = checkpoint.currentTargetMistakeCount
    currentTargetMistakenJamoIndices.clear()
    currentTargetMistakenJamoIndices += checkpoint.currentTargetMistakenJamoIndices.filter { it in targetJamoSequence.indices }
    accumulatedActiveDuration = checkpoint.activeDuration
    hasStartedTiming = currentTargetIndex > 0 || acceptedKeys.isNotEmpty() || mistakeCount > 0 ||
      accumulatedActiveDuration > 0
    feedback = if (judgeState.isComplete) PracticeFeedback.Complete else PracticeFeedback.Idle
    correctStreak = 0
    reactionEvent = PracticeReactionEvent.Idle
  }

  private fun reactionForAcceptedInput(completedItem: Boolean): PracticeReactionEvent {
    if (completedItem) {
      return if (isLessonComplete && mistakeCount == 0) PracticeReactionEvent.PerfectSession else PracticeReactionEvent.WordCompleted
    }
    if (correctStreak in COMBO_MILESTONES) return PracticeReactionEvent.ComboMilestone(correctStreak)
    val syllableIndex = targetSyllableRanges.indexOfFirst { it.third == completedJamoCount }
    if (syllableIndex >= 0) return PracticeReactionEvent.SyllableCompleted(syllableIndex)
    return PracticeReactionEvent.CorrectJamo
  }

  private fun publishReaction(event: PracticeReactionEvent) {
    reactionEvent = event
    reactionRevision += 1
  }

  companion object {
    val COMBO_MILESTONES = setOf(5, 10, 20)

    /** Grapheme clusters of [text] with their jamo ranges (iOS `makeTargetSyllableRanges`). */
    internal fun makeTargetSyllableRanges(text: String): List<Triple<String, Int, Int>> {
      var cursor = 0
      return graphemes(text).map { character ->
        val count = JamoDecomposer.keySequence(character).size
        val range = Triple(character, cursor, cursor + count)
        cursor += count
        range
      }
    }

    fun graphemes(text: String): List<String> {
      if (text.isEmpty()) return emptyList()
      val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
      iterator.setText(text)
      val result = ArrayList<String>()
      var start = iterator.first()
      var end = iterator.next()
      while (end != BreakIterator.DONE) {
        result += text.substring(start, end)
        start = end
        end = iterator.next()
      }
      return result
    }

    internal fun isSyllableJoin(previous: CompositionPhase, next: CompositionPhase): Boolean =
      (previous == CompositionPhase.CHO && next == CompositionPhase.CHO_JUNG) ||
        (previous == CompositionPhase.CHO_JUNG && next == CompositionPhase.CHO_JUNG) ||
        (previous == CompositionPhase.CHO_JUNG_JONG && next == CompositionPhase.CHO_JUNG)
  }
}
