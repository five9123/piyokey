package app.piyokey.core.session

import app.piyokey.core.hangul.CompositionEvent
import app.piyokey.core.hangul.CompositionPhase
import app.piyokey.core.hangul.CompositionState
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge

const val PRACTICE_AUTO_ADVANCE_DELAY_MILLIS: Long = 650L

data class ActiveDurationClock(
  val accumulatedMillis: Long = 0,
  val activeSinceMillis: Long? = null,
) {
  init { require(accumulatedMillis >= 0) }

  fun start(nowMillis: Long): ActiveDurationClock =
    if (activeSinceMillis == null) copy(activeSinceMillis = nowMillis) else this

  fun pause(nowMillis: Long): ActiveDurationClock = if (activeSinceMillis == null) this else copy(
    accumulatedMillis = duration(nowMillis),
    activeSinceMillis = null,
  )

  fun duration(nowMillis: Long): Long = accumulatedMillis + (activeSinceMillis?.let {
    (nowMillis - it).coerceAtLeast(0)
  } ?: 0L)
}

sealed interface PracticeSessionEvent {
  data class Key(val jamo: Char) : PracticeSessionEvent

  data object Backspace : PracticeSessionEvent

  /**
   * Confirms a previously scheduled completion transition.
   *
   * The token makes delayed UI callbacks harmless after backspace, restart, or a newer completion.
   */
  data class Advance(val transitionToken: Long) : PracticeSessionEvent

  data object Restart : PracticeSessionEvent
}

enum class PracticeCompletionDestination {
  NEXT_TARGET,
  RESULTS,
}

data class PracticeCompletionTransition(
  val token: Long,
  val destination: PracticeCompletionDestination,
  val delayMillis: Long = PRACTICE_AUTO_ADVANCE_DELAY_MILLIS,
)

sealed interface PracticeSessionEffect {
  /** The UI should wait [delayMillis] and then dispatch Advance([transitionToken]). */
  data class ScheduleAdvance(
    val transitionToken: Long,
    val destination: PracticeCompletionDestination,
    val delayMillis: Long = PRACTICE_AUTO_ADVANCE_DELAY_MILLIS,
  ) : PracticeSessionEffect

  /** Emitted once a valid delayed transition reaches the result boundary. */
  data class SessionCompleted(val transitionToken: Long) : PracticeSessionEffect
}

data class PracticeSessionReduction(
  val state: PracticeSessionState,
  val effects: List<PracticeSessionEffect> = emptyList(),
)

sealed interface PracticeFeedback {
  data object Idle : PracticeFeedback
  data object Correct : PracticeFeedback
  data class Incorrect(val expected: Char) : PracticeFeedback
  data object Complete : PracticeFeedback
}

enum class TargetSyllableState {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
}

data class TargetSyllableProgress(
  val offset: Int,
  /** One Unicode code point from the target. Hangul syllables therefore remain one unit. */
  val character: String,
  val jamoRange: IntRange,
  val state: TargetSyllableState,
  val isHangul: Boolean,
)

data class PracticeItemResolution(
  val itemIndex: Int,
  val mistakeCount: Int,
  val mistakenJamoIndices: Set<Int>,
) {
  val hadMistake: Boolean
    get() = mistakeCount > 0
}

data class PracticeSessionCheckpoint(
  val currentTargetIndex: Int,
  val acceptedKeys: String,
  val mistakeCount: Int,
  val currentItemMistakeCount: Int,
  val currentItemMistakenJamoIndices: Set<Int>,
  val itemResolutions: List<PracticeItemResolution>,
  val activeDurationMillis: Long,
)

@ConsistentCopyVisibility
data class PracticeSessionState internal constructor(
  val targets: List<String>,
  val currentTargetIndex: Int,
  val targetJamoCounts: List<Int>,
  val targetJamoSequence: List<Char>,
  val acceptedKeys: List<Char>,
  val composition: CompositionState,
  val judge: JamoJudgeState,
  val feedback: PracticeFeedback,
  val feedbackRevision: Long,
  val compositionRevision: Long,
  val lastAcceptedKey: Char?,
  val shouldAnimateSyllableJoin: Boolean,
  val mistakeCount: Int,
  val currentItemMistakeCount: Int,
  val currentItemMistakenJamoIndices: Set<Int>,
  val itemResolutions: List<PracticeItemResolution>,
  val correctStreak: Int,
  val pendingTransition: PracticeCompletionTransition?,
  val transitionSequence: Long,
  val isResultReady: Boolean,
) {
  val currentTarget: String
    get() = targets[currentTargetIndex]

  val nextExpected: Char?
    get() = judge.expectedNext

  val completedJamoCount: Int
    get() = judge.currentIndex

  val enteredText: String
    get() = composition.text

  val composingText: String
    get() = composition.composingText

  val committedText: String
    get() = composition.committedText

  val isCurrentTargetComplete: Boolean
    get() = judge.isComplete

  val canAdvance: Boolean
    get() = isCurrentTargetComplete && currentTargetIndex + 1 < targets.size

  /** Input is complete immediately; [isResultReady] becomes true after the 650 ms boundary. */
  val isSessionInputComplete: Boolean
    get() = isCurrentTargetComplete && currentTargetIndex == targets.lastIndex

  val acceptedJamoCount: Int
    get() = targetJamoCounts.take(currentTargetIndex).sum() + completedJamoCount

  val totalInputCount: Int
    get() = acceptedJamoCount + mistakeCount

  val accuracyPercent: Double
    get() = if (totalInputCount == 0) {
      0.0
    } else {
      acceptedJamoCount.toDouble() / totalInputCount.toDouble() * 100.0
    }

  val completedItemCount: Int
    get() = itemResolutions.size

  val lastCompletedItem: PracticeItemResolution?
    get() = itemResolutions.lastOrNull()

  val targetSyllableProgress: List<TargetSyllableProgress>
    get() = makeTargetSyllableProgress(currentTarget, completedJamoCount)

  val completedSyllableCount: Int
    get() = targetSyllableProgress.count { it.isHangul && it.state == TargetSyllableState.COMPLETED }

  val targetSyllableCount: Int
    get() = targetSyllableProgress.count(TargetSyllableProgress::isHangul)
}

object PracticeSessionReducer {
  /** Validates every target up front and returns the immutable initial session state. */
  fun initialState(targets: List<String>): PracticeSessionState {
    require(targets.isNotEmpty()) { "Practice targets must not be empty" }
    val frozenTargets = targets.toList()
    val sequences = frozenTargets.map { target -> JamoDecomposer.keySequenceFor(target) }
    return stateForTarget(
      targets = frozenTargets,
      targetJamoCounts = sequences.map(List<Char>::size),
      targetIndex = 0,
      targetSequence = sequences.first(),
      mistakeCount = 0,
      itemResolutions = emptyList(),
      transitionSequence = 0,
      feedbackRevision = 0,
      compositionRevision = 0,
    )
  }

  fun initialState(target: String): PracticeSessionState = initialState(listOf(target))

  fun checkpoint(state: PracticeSessionState, activeDurationMillis: Long): PracticeSessionCheckpoint =
    PracticeSessionCheckpoint(
      currentTargetIndex = state.currentTargetIndex,
      acceptedKeys = state.acceptedKeys.joinToString(""),
      mistakeCount = state.mistakeCount,
      currentItemMistakeCount = state.currentItemMistakeCount,
      currentItemMistakenJamoIndices = state.currentItemMistakenJamoIndices,
      itemResolutions = state.itemResolutions,
      activeDurationMillis = activeDurationMillis.coerceAtLeast(0),
    )

  fun restoreState(targets: List<String>, checkpoint: PracticeSessionCheckpoint): PracticeSessionState {
    require(targets.isNotEmpty()) { "Practice targets must not be empty" }
    require(checkpoint.currentTargetIndex in targets.indices) { "Checkpoint target is out of range" }
    require(checkpoint.mistakeCount >= checkpoint.currentItemMistakeCount) { "Invalid checkpoint mistakes" }
    require(checkpoint.activeDurationMillis >= 0) { "Invalid checkpoint duration" }
    val frozenTargets = targets.toList()
    val targetJamoCounts = frozenTargets.map { JamoDecomposer.keySequenceFor(it).size }
    val acceptedKeys = checkpoint.acceptedKeys.toList()
    val rebuilt = rebuildCurrentTarget(frozenTargets[checkpoint.currentTargetIndex], acceptedKeys)
    require(checkpoint.currentItemMistakenJamoIndices.all { it in targetJamoCounts[checkpoint.currentTargetIndex].let { count -> 0 until count } }) {
      "Checkpoint mistake index is out of range"
    }
    require(checkpoint.itemResolutions.map(PracticeItemResolution::itemIndex).distinct().size == checkpoint.itemResolutions.size)
    require(checkpoint.itemResolutions.all { it.itemIndex in 0..checkpoint.currentTargetIndex })
    val completesTarget = rebuilt.judge.isComplete
    val transition = if (completesTarget) {
      PracticeCompletionTransition(
        token = 1,
        destination = if (checkpoint.currentTargetIndex == frozenTargets.lastIndex) {
          PracticeCompletionDestination.RESULTS
        } else {
          PracticeCompletionDestination.NEXT_TARGET
        },
      )
    } else null
    return stateForTarget(
      targets = frozenTargets,
      targetJamoCounts = targetJamoCounts,
      targetIndex = checkpoint.currentTargetIndex,
      targetSequence = JamoDecomposer.keySequenceFor(frozenTargets[checkpoint.currentTargetIndex]),
      mistakeCount = checkpoint.mistakeCount,
      itemResolutions = checkpoint.itemResolutions,
      transitionSequence = transition?.token ?: 0,
      feedbackRevision = 0,
      compositionRevision = 0,
    ).copy(
      acceptedKeys = acceptedKeys,
      composition = rebuilt.composition,
      judge = rebuilt.judge,
      feedback = if (completesTarget) PracticeFeedback.Complete else PracticeFeedback.Idle,
      lastAcceptedKey = acceptedKeys.lastOrNull(),
      currentItemMistakeCount = checkpoint.currentItemMistakeCount,
      currentItemMistakenJamoIndices = checkpoint.currentItemMistakenJamoIndices,
      pendingTransition = transition,
    )
  }

  fun reduce(
    state: PracticeSessionState,
    event: PracticeSessionEvent,
  ): PracticeSessionReduction = when (event) {
    is PracticeSessionEvent.Key -> reduceKey(state, event.jamo)
    PracticeSessionEvent.Backspace -> reduceBackspace(state)
    is PracticeSessionEvent.Advance -> reduceAdvance(state, event.transitionToken)
    PracticeSessionEvent.Restart -> reduceRestart(state)
  }

  private fun reduceKey(
    state: PracticeSessionState,
    jamo: Char,
  ): PracticeSessionReduction {
    if (state.judge.isComplete || state.isResultReady) return PracticeSessionReduction(state)

    val evaluation = JamoSequenceJudge.evaluate(jamo, state.judge)
    return when (val result = evaluation.result) {
      is JamoJudgeResult.Correct -> {
        val previousPhase = state.composition.phase
        val composition = HangulComposer.reduce(state.composition, CompositionEvent.Key(jamo))
        val completesItem = result.completed
        val resolution = if (completesItem) {
          PracticeItemResolution(
            itemIndex = state.currentTargetIndex,
            mistakeCount = state.currentItemMistakeCount,
            mistakenJamoIndices = state.currentItemMistakenJamoIndices.toSet(),
          )
        } else {
          null
        }
        val transition = if (completesItem) {
          PracticeCompletionTransition(
            token = state.transitionSequence + 1,
            destination = if (state.currentTargetIndex == state.targets.lastIndex) {
              PracticeCompletionDestination.RESULTS
            } else {
              PracticeCompletionDestination.NEXT_TARGET
            },
          )
        } else {
          null
        }
        val nextState = state.copy(
          acceptedKeys = state.acceptedKeys + jamo,
          composition = composition,
          judge = evaluation.state,
          feedback = if (completesItem) PracticeFeedback.Complete else PracticeFeedback.Correct,
          feedbackRevision = state.feedbackRevision + 1,
          compositionRevision = state.compositionRevision + 1,
          lastAcceptedKey = jamo,
          shouldAnimateSyllableJoin = isSyllableJoin(previousPhase, composition.phase),
          itemResolutions = resolution?.let { state.itemResolutions + it } ?: state.itemResolutions,
          correctStreak = state.correctStreak + 1,
          pendingTransition = transition,
          transitionSequence = transition?.token ?: state.transitionSequence,
        )
        PracticeSessionReduction(
          state = nextState,
          effects = transition?.let {
            listOf(
              PracticeSessionEffect.ScheduleAdvance(
                transitionToken = it.token,
                destination = it.destination,
                delayMillis = it.delayMillis,
              ),
            )
          }.orEmpty(),
        )
      }

      is JamoJudgeResult.Incorrect -> PracticeSessionReduction(
        state.copy(
          judge = evaluation.state,
          feedback = PracticeFeedback.Incorrect(result.expected),
          feedbackRevision = state.feedbackRevision + 1,
          mistakeCount = state.mistakeCount + 1,
          currentItemMistakeCount = state.currentItemMistakeCount + 1,
          currentItemMistakenJamoIndices =
            state.currentItemMistakenJamoIndices + state.judge.currentIndex,
          correctStreak = 0,
        ),
      )

      JamoJudgeResult.AlreadyComplete -> PracticeSessionReduction(state)
    }
  }

  private fun reduceBackspace(state: PracticeSessionState): PracticeSessionReduction {
    if (state.acceptedKeys.isEmpty() || state.isResultReady) return PracticeSessionReduction(state)

    val acceptedKeys = state.acceptedKeys.dropLast(1)
    val rebuilt = rebuildCurrentTarget(state.currentTarget, acceptedKeys)
    val resolutions = if (state.itemResolutions.lastOrNull()?.itemIndex == state.currentTargetIndex) {
      state.itemResolutions.dropLast(1)
    } else {
      state.itemResolutions
    }
    return PracticeSessionReduction(
      state.copy(
        acceptedKeys = acceptedKeys,
        composition = rebuilt.composition,
        judge = rebuilt.judge,
        feedback = PracticeFeedback.Idle,
        feedbackRevision = state.feedbackRevision + 1,
        compositionRevision = state.compositionRevision + 1,
        lastAcceptedKey = acceptedKeys.lastOrNull(),
        shouldAnimateSyllableJoin = false,
        itemResolutions = resolutions,
        correctStreak = 0,
        pendingTransition = null,
      ),
    )
  }

  private fun reduceAdvance(
    state: PracticeSessionState,
    transitionToken: Long,
  ): PracticeSessionReduction {
    val transition = state.pendingTransition
    if (
      transition == null ||
      transition.token != transitionToken ||
      !state.judge.isComplete ||
      state.isResultReady
    ) {
      return PracticeSessionReduction(state)
    }

    if (transition.destination == PracticeCompletionDestination.RESULTS) {
      return PracticeSessionReduction(
        state = state.copy(
          pendingTransition = null,
          isResultReady = true,
        ),
        effects = listOf(PracticeSessionEffect.SessionCompleted(transitionToken)),
      )
    }

    val nextIndex = state.currentTargetIndex + 1
    if (nextIndex > state.targets.lastIndex) return PracticeSessionReduction(state)
    return PracticeSessionReduction(
      state = stateForTarget(
        targets = state.targets,
        targetJamoCounts = state.targetJamoCounts,
        targetIndex = nextIndex,
        targetSequence = JamoDecomposer.keySequenceFor(state.targets[nextIndex]),
        mistakeCount = state.mistakeCount,
        itemResolutions = state.itemResolutions,
        transitionSequence = state.transitionSequence,
        feedbackRevision = state.feedbackRevision + 1,
        compositionRevision = state.compositionRevision + 1,
      ),
    )
  }

  private fun reduceRestart(state: PracticeSessionState): PracticeSessionReduction =
    PracticeSessionReduction(
      state = stateForTarget(
        targets = state.targets,
        targetJamoCounts = state.targetJamoCounts,
        targetIndex = 0,
        targetSequence = JamoDecomposer.keySequenceFor(state.targets.first()),
        mistakeCount = 0,
        itemResolutions = emptyList(),
        // Preserve the issued sequence so delayed callbacks can never match a future completion.
        transitionSequence = state.transitionSequence,
        feedbackRevision = state.feedbackRevision + 1,
        compositionRevision = state.compositionRevision + 1,
      ),
    )

  private fun stateForTarget(
    targets: List<String>,
    targetJamoCounts: List<Int>,
    targetIndex: Int,
    targetSequence: List<Char>,
    mistakeCount: Int,
    itemResolutions: List<PracticeItemResolution>,
    transitionSequence: Long,
    feedbackRevision: Long,
    compositionRevision: Long,
  ): PracticeSessionState = PracticeSessionState(
    targets = targets,
    currentTargetIndex = targetIndex,
    targetJamoCounts = targetJamoCounts,
    targetJamoSequence = targetSequence,
    acceptedKeys = emptyList(),
    composition = CompositionState(),
    judge = JamoJudgeState.forTarget(targets[targetIndex]),
    feedback = PracticeFeedback.Idle,
    feedbackRevision = feedbackRevision,
    compositionRevision = compositionRevision,
    lastAcceptedKey = null,
    shouldAnimateSyllableJoin = false,
    mistakeCount = mistakeCount,
    currentItemMistakeCount = 0,
    currentItemMistakenJamoIndices = emptySet(),
    itemResolutions = itemResolutions,
    correctStreak = 0,
    pendingTransition = null,
    transitionSequence = transitionSequence,
    isResultReady = false,
  )

  private fun rebuildCurrentTarget(
    target: String,
    acceptedKeys: List<Char>,
  ): RebuiltInput {
    var judge = JamoJudgeState.forTarget(target)
    var composition = CompositionState()
    acceptedKeys.forEach { key ->
      val evaluation = JamoSequenceJudge.evaluate(key, judge)
      check(evaluation.result is JamoJudgeResult.Correct) {
        "Accepted practice prefix no longer matches its validated target"
      }
      judge = evaluation.state
      composition = HangulComposer.reduce(composition, CompositionEvent.Key(key))
    }
    return RebuiltInput(judge, composition)
  }

  private fun isSyllableJoin(
    previousPhase: CompositionPhase,
    nextPhase: CompositionPhase,
  ): Boolean = when (previousPhase to nextPhase) {
    CompositionPhase.CHO to CompositionPhase.CHO_JUNG,
    CompositionPhase.CHO_JUNG to CompositionPhase.CHO_JUNG,
    CompositionPhase.CHO_JUNG_JONG to CompositionPhase.CHO_JUNG,
    -> true
    else -> false
  }

  private data class RebuiltInput(
    val judge: JamoJudgeState,
    val composition: CompositionState,
  )
}

private fun makeTargetSyllableProgress(
  target: String,
  completedJamoCount: Int,
): List<TargetSyllableProgress> {
  var cursor = 0
  var offset = 0
  val progress = mutableListOf<TargetSyllableProgress>()
  while (offset < target.length) {
    val codePoint = target.codePointAt(offset)
    val character = String(Character.toChars(codePoint))
    val count = JamoDecomposer.keySequenceFor(character).size
    val range = cursor until (cursor + count)
    val state = when {
      completedJamoCount >= range.last + 1 -> TargetSyllableState.COMPLETED
      completedJamoCount > range.first -> TargetSyllableState.IN_PROGRESS
      else -> TargetSyllableState.PENDING
    }
    progress += TargetSyllableProgress(
      offset = progress.size,
      character = character,
      jamoRange = range,
      state = state,
      isHangul = JamoDecomposer.containsHangul(character),
    )
    cursor += count
    offset += Character.charCount(codePoint)
  }
  return progress
}
