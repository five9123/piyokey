package app.piyokey.core.game

import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.hangul.CompositionEvent
import app.piyokey.core.hangul.CompositionState
import app.piyokey.core.hangul.HangulComposer
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge
import app.piyokey.core.hangul.OSIMETextJudge
import app.piyokey.core.hangul.OSIMETextJudgeStatus
import kotlin.random.Random

const val TYPING_GAME_COUNTDOWN_MILLIS = 3_000L
const val TYPING_GAME_REDUCED_COUNTDOWN_MILLIS = 1_000L
const val TYPING_GAME_ADVANCE_MILLIS = 650L
const val CHOSEONG_GAME_ADVANCE_MILLIS = 1_350L

enum class TypingGameMode { CHOSEONG, WORD_MATCH, DICTATION }
enum class TypingGamePhase { COUNTDOWN, PLAYING, PAUSED, ANSWER_HOLD, FINISHED }
enum class TypingFeedback { READY, CORRECT, WRONG, COMPLETE }

data class TypingRound(
  val item: DeckItem,
  val initials: String,
  val requiresMeaningHint: Boolean,
)

data class TypingGameState(
  val mode: TypingGameMode,
  val rounds: List<TypingRound>,
  val roundIndex: Int,
  val phase: TypingGamePhase,
  val countdownStartedAtMillis: Long,
  val countdownDurationMillis: Long,
  val answerHoldStartedAtMillis: Long? = null,
  val activeStartedAtMillis: Long? = null,
  val activeElapsedMillis: Long = 0,
  val questionStartedAtActiveMillis: Long = 0,
  val judge: JamoJudgeState,
  val acceptedKeys: List<Char> = emptyList(),
  val composition: CompositionState = CompositionState(),
  val score: Int = 0,
  val combo: Int = 0,
  val maxCombo: Int = 0,
  val correctJamoCount: Int = 0,
  val mistakeCount: Int = 0,
  val completedItemCount: Int = 0,
  val imperfectItemCount: Int = 0,
  val currentMistakeCount: Int = 0,
  val currentMistakenJamoIndices: Set<Int> = emptySet(),
  val reviewMistakeCounts: Map<String, Int> = emptyMap(),
  val hintVisible: Boolean = false,
  val hintPenalized: Boolean = false,
  val lastPoints: Int = 0,
  val feedback: TypingFeedback = TypingFeedback.READY,
  val feedbackRevision: Long = 0,
) {
  val currentRound: TypingRound get() = rounds[roundIndex]
  val answerVisible: Boolean get() = phase == TypingGamePhase.ANSWER_HOLD || phase == TypingGamePhase.FINISHED
  val accuracyPercent: Double get() {
    val total = correctJamoCount + mistakeCount
    return if (total == 0) 0.0 else correctJamoCount.toDouble() / total * 100.0
  }
  val questionsPerMinute: Double get() =
    if (activeElapsedMillis == 0L) 0.0 else completedItemCount.toDouble() / activeElapsedMillis * 60_000.0
}

sealed interface TypingGameEvent {
  data class Tick(val nowMillis: Long) : TypingGameEvent
  data class Key(val jamo: Char, val nowMillis: Long) : TypingGameEvent
  data object Backspace : TypingGameEvent
  data class IMEText(val committedText: String, val composingText: String?, val nowMillis: Long) : TypingGameEvent
  data class ShowHint(val penalize: Boolean) : TypingGameEvent
  data class Pause(val nowMillis: Long) : TypingGameEvent
  data class Resume(val nowMillis: Long, val reduceMotion: Boolean = false) : TypingGameEvent
}

object TypingRoundBuilder {
  fun build(mode: TypingGameMode, items: List<DeckItem>, language: String, limit: Int = 10, seed: Long): List<TypingRound> {
    if (limit <= 0) return emptyList()
    val unique = LinkedHashMap<String, DeckItem>()
    items.forEach { item ->
      val playable = runCatching { JamoDecomposer.keySequenceFor(item.ko) }.getOrNull()?.isNotEmpty() == true
      val hasMeaning = !item.localizedMeaning(language).isNullOrBlank()
      if (playable && (mode != TypingGameMode.WORD_MATCH || hasMeaning)) unique.putIfAbsent(item.ko, item)
    }
    var eligible = unique.values.toList()
    val initialsByWord = eligible.associate { it.ko to extractInitials(it.ko) }
    if (mode == TypingGameMode.CHOSEONG) {
      eligible = eligible.filter { initialsByWord.getValue(it.ko).isNotEmpty() }
      val groups = eligible.groupBy { initialsByWord.getValue(it.ko) }
      eligible = eligible.filter { groups.getValue(initialsByWord.getValue(it.ko)).size == 1 || !it.localizedMeaning(language).isNullOrBlank() }
    }
    val groups = eligible.groupBy { initialsByWord[it.ko].orEmpty() }
    return eligible.shuffled(Random(seed)).take(limit).map { item ->
      val initials = if (mode == TypingGameMode.CHOSEONG) initialsByWord.getValue(item.ko) else ""
      TypingRound(item, initials, mode == TypingGameMode.CHOSEONG && groups[initials].orEmpty().size > 1)
    }
  }

  fun extractInitials(text: String): String = buildString {
    text.forEach { character ->
      when {
        character == ' ' -> append(' ')
        character.code in 0xAC00..0xD7A3 -> append(LEADING[(character.code - 0xAC00) / (21 * 28)])
        character in LEADING -> append(character)
      }
    }
  }

  private val LEADING = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
}

object TypingGameFactory {
  fun create(mode: TypingGameMode, rounds: List<TypingRound>, nowMillis: Long, reduceMotion: Boolean = false): TypingGameState {
    require(rounds.isNotEmpty()) { "Typing game requires at least one round" }
    return TypingGameState(
      mode = mode,
      rounds = rounds,
      roundIndex = 0,
      phase = TypingGamePhase.COUNTDOWN,
      countdownStartedAtMillis = nowMillis,
      countdownDurationMillis = if (reduceMotion) TYPING_GAME_REDUCED_COUNTDOWN_MILLIS else TYPING_GAME_COUNTDOWN_MILLIS,
      judge = JamoJudgeState.forTarget(rounds.first().item.ko),
    )
  }
}

object TypingGameReducer {
  fun reduce(state: TypingGameState, event: TypingGameEvent): TypingGameState = when (event) {
    is TypingGameEvent.Tick -> tick(state, event.nowMillis)
    is TypingGameEvent.Key -> key(consumeTime(state, event.nowMillis), event.jamo)
    TypingGameEvent.Backspace -> backspace(state)
    is TypingGameEvent.IMEText -> ime(consumeTime(state, event.nowMillis), event.committedText, event.composingText)
    is TypingGameEvent.ShowHint -> if (state.phase == TypingGamePhase.PLAYING) state.copy(
      hintVisible = true,
      hintPenalized = state.hintPenalized || event.penalize,
      feedbackRevision = state.feedbackRevision + 1,
    ) else state
    is TypingGameEvent.Pause -> pause(state, event.nowMillis)
    is TypingGameEvent.Resume -> resume(state, event.nowMillis, event.reduceMotion)
  }

  private fun tick(state: TypingGameState, now: Long): TypingGameState = when (state.phase) {
    TypingGamePhase.COUNTDOWN -> if (now - state.countdownStartedAtMillis >= state.countdownDurationMillis) {
      if (state.judge.isComplete) {
        state.copy(phase = TypingGamePhase.ANSWER_HOLD, answerHoldStartedAtMillis = now)
      } else {
        state.copy(phase = TypingGamePhase.PLAYING, activeStartedAtMillis = now, questionStartedAtActiveMillis = state.activeElapsedMillis)
      }
    } else state
    TypingGamePhase.PLAYING -> consumeTime(state, now)
    TypingGamePhase.ANSWER_HOLD -> if (now - requireNotNull(state.answerHoldStartedAtMillis) >= holdDuration(state.mode)) advance(state, now) else state
    else -> state
  }

  private fun key(state: TypingGameState, jamo: Char): TypingGameState {
    if (state.phase != TypingGamePhase.PLAYING) return state
    val evaluation = JamoSequenceJudge.evaluate(jamo, state.judge)
    return when (val result = evaluation.result) {
      JamoJudgeResult.AlreadyComplete -> state
      is JamoJudgeResult.Incorrect -> mistake(state.copy(judge = evaluation.state))
      is JamoJudgeResult.Correct -> {
        val updated = state.copy(
          judge = evaluation.state,
          acceptedKeys = state.acceptedKeys + jamo,
          composition = HangulComposer.reduce(state.composition, CompositionEvent.Key(jamo)),
          correctJamoCount = state.correctJamoCount + 1,
          feedback = TypingFeedback.CORRECT,
          feedbackRevision = state.feedbackRevision + 1,
        )
        if (result.completed) complete(updated) else updated
      }
    }
  }

  private fun ime(state: TypingGameState, committed: String, composing: String?): TypingGameState {
    if (state.phase != TypingGamePhase.PLAYING) return state
    val result = OSIMETextJudge.evaluate(state.currentRound.item.ko, committed, composing)
    val rebuilt = rebuild(state.currentRound.item.ko, result.acceptedSequence)
    var next = state.copy(
      acceptedKeys = result.acceptedSequence,
      composition = rebuilt.first,
      judge = rebuilt.second,
      correctJamoCount = state.correctJamoCount + (result.acceptedSequence.size - state.acceptedKeys.size).coerceAtLeast(0),
      feedback = TypingFeedback.CORRECT,
      feedbackRevision = state.feedbackRevision + 1,
    )
    return when (val status = result.status) {
      is OSIMETextJudgeStatus.ConfirmedMismatch -> mistake(next, status.expectedIndex)
      OSIMETextJudgeStatus.ComposingMismatch -> next
      is OSIMETextJudgeStatus.Matching -> if (status.completed && !status.isComposing) complete(next) else next
    }
  }

  private fun backspace(state: TypingGameState): TypingGameState {
    if (state.phase != TypingGamePhase.PLAYING || state.acceptedKeys.isEmpty()) return state
    val rebuilt = rebuild(state.currentRound.item.ko, state.acceptedKeys.dropLast(1))
    return state.copy(
      acceptedKeys = state.acceptedKeys.dropLast(1),
      composition = rebuilt.first,
      judge = rebuilt.second,
      feedback = TypingFeedback.READY,
      feedbackRevision = state.feedbackRevision + 1,
    )
  }

  private fun mistake(state: TypingGameState, index: Int = state.judge.currentIndex): TypingGameState = state.copy(
    combo = 0,
    mistakeCount = state.mistakeCount + 1,
    currentMistakeCount = state.currentMistakeCount + 1,
    currentMistakenJamoIndices = state.currentMistakenJamoIndices + index,
    reviewMistakeCounts = state.reviewMistakeCounts.increment(state.currentRound.item.id),
    feedback = TypingFeedback.WRONG,
    feedbackRevision = state.feedbackRevision + 1,
  )

  private fun complete(state: TypingGameState): TypingGameState {
    val flawless = state.currentMistakeCount == 0
    val combo = if (flawless) state.combo + 1 else 0
    val responseMillis = (state.activeElapsedMillis - state.questionStartedAtActiveMillis).coerceAtLeast(0)
    val speedBonus = ((5_000L - responseMillis).coerceAtLeast(0) / 100L).toInt()
    val points = (100 + speedBonus + minOf(combo, 10) * 10 - if (state.hintPenalized) 30 else 0).coerceAtLeast(50)
    return state.copy(
      phase = TypingGamePhase.ANSWER_HOLD,
      answerHoldStartedAtMillis = state.activeStartedAtMillis?.plus(0) ?: 0,
      activeStartedAtMillis = null,
      score = state.score + points,
      combo = combo,
      maxCombo = maxOf(state.maxCombo, combo),
      completedItemCount = state.completedItemCount + 1,
      imperfectItemCount = state.imperfectItemCount + if (flawless) 0 else 1,
      lastPoints = points,
      feedback = TypingFeedback.COMPLETE,
      feedbackRevision = state.feedbackRevision + 1,
    )
  }

  private fun advance(state: TypingGameState, now: Long): TypingGameState {
    if (state.roundIndex == state.rounds.lastIndex) return state.copy(phase = TypingGamePhase.FINISHED, answerHoldStartedAtMillis = null)
    val next = state.roundIndex + 1
    return state.copy(
      roundIndex = next,
      phase = TypingGamePhase.PLAYING,
      answerHoldStartedAtMillis = null,
      activeStartedAtMillis = now,
      questionStartedAtActiveMillis = state.activeElapsedMillis,
      judge = JamoJudgeState.forTarget(state.rounds[next].item.ko),
      acceptedKeys = emptyList(),
      composition = CompositionState(),
      currentMistakeCount = 0,
      currentMistakenJamoIndices = emptySet(),
      hintVisible = false,
      hintPenalized = false,
      lastPoints = 0,
      feedback = TypingFeedback.READY,
      feedbackRevision = state.feedbackRevision + 1,
    )
  }

  private fun pause(state: TypingGameState, now: Long): TypingGameState = when (state.phase) {
    TypingGamePhase.PLAYING -> consumeTime(state, now).copy(phase = TypingGamePhase.PAUSED, activeStartedAtMillis = null)
    TypingGamePhase.COUNTDOWN, TypingGamePhase.ANSWER_HOLD -> state.copy(phase = TypingGamePhase.PAUSED, activeStartedAtMillis = null)
    else -> state
  }

  private fun resume(state: TypingGameState, now: Long, reduceMotion: Boolean): TypingGameState = if (state.phase == TypingGamePhase.PAUSED) state.copy(
    phase = TypingGamePhase.COUNTDOWN,
    countdownStartedAtMillis = now,
    countdownDurationMillis = if (reduceMotion) TYPING_GAME_REDUCED_COUNTDOWN_MILLIS else TYPING_GAME_COUNTDOWN_MILLIS,
    answerHoldStartedAtMillis = null,
  ) else state

  private fun consumeTime(state: TypingGameState, now: Long): TypingGameState {
    if (state.phase != TypingGamePhase.PLAYING) return state
    val started = state.activeStartedAtMillis ?: return state.copy(activeStartedAtMillis = now)
    return state.copy(activeElapsedMillis = state.activeElapsedMillis + (now - started).coerceAtLeast(0), activeStartedAtMillis = now)
  }

  private fun rebuild(target: String, keys: List<Char>): Pair<CompositionState, JamoJudgeState> {
    var composition = CompositionState()
    var judge = JamoJudgeState.forTarget(target)
    keys.forEach { composition = HangulComposer.reduce(composition, CompositionEvent.Key(it)) }
    keys.forEach { key -> judge = JamoSequenceJudge.evaluate(key, judge).state }
    return composition to judge
  }

  private fun holdDuration(mode: TypingGameMode) = if (mode == TypingGameMode.CHOSEONG) CHOSEONG_GAME_ADVANCE_MILLIS else TYPING_GAME_ADVANCE_MILLIS
}

private fun Map<String, Int>.increment(key: String): Map<String, Int> = this + (key to ((this[key] ?: 0) + 1))
