package app.piyokey.core.game

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.hangul.JamoDecomposer
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge
import kotlin.math.roundToInt
import kotlin.random.Random

const val FLOW_INITIAL_TIME_MILLIS = 60_000L
const val FLOW_PERFECT_BONUS_MILLIS = 2_000L
const val FLOW_COUNTDOWN_MILLIS = 3_000L
const val FLOW_REDUCED_COUNTDOWN_MILLIS = 1_000L
const val FLOW_INITIAL_LIVES = 3
const val FLOW_ACCELERATION_WINDOW_MILLIS = 50_000L
const val FLOW_MAX_SPEED_MULTIPLIER = 1.8

enum class FlowPhase { COUNTDOWN, PLAYING, PAUSED, FINISHED }

enum class FlowFinishReason { TIME, LIVES, CLOSED }

data class FlowCard(
  val item: DeckItem,
  val judge: JamoJudgeState,
  val durationMillis: Long,
  val elapsedMillis: Long = 0,
  val hadMistake: Boolean = false,
) {
  val progress: Double
    get() = (elapsedMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)
}

data class FlowGameState(
  val deckId: String,
  val course: String,
  val cards: List<DeckItem>,
  val cardIndex: Int,
  val currentCard: FlowCard,
  val phase: FlowPhase,
  val countdownStartedAtMillis: Long,
  val countdownDurationMillis: Long,
  val lastTickAtMillis: Long?,
  val remainingTimeMillis: Long,
  val playElapsedMillis: Long,
  val score: Int,
  val combo: Int,
  val maxCombo: Int,
  val lives: Int,
  val correctJamoCount: Int,
  val scoredJamoCount: Int,
  val mistakeCount: Int,
  val completedItemCount: Int,
  val missedItemCount: Int,
  val feedbackRevision: Long,
  val finishReason: FlowFinishReason? = null,
) {
  val countdownValue: Int
    get() {
      val elapsed = (lastTickAtMillis ?: countdownStartedAtMillis) - countdownStartedAtMillis
      val remaining = (countdownDurationMillis - elapsed).coerceAtLeast(1L)
      return ((remaining + 999L) / 1_000L).toInt()
    }

  val accuracyPercent: Double
    get() {
      val total = correctJamoCount + mistakeCount
      return if (total == 0) 0.0 else correctJamoCount.toDouble() / total * 100.0
    }

  val charactersPerMinute: Double
    get() = if (playElapsedMillis == 0L) 0.0 else scoredJamoCount.toDouble() / playElapsedMillis * 60_000.0
}

sealed interface FlowGameEvent {
  data class Tick(val nowMillis: Long) : FlowGameEvent
  data class Key(val jamo: Char) : FlowGameEvent
  data object Pause : FlowGameEvent
  data class Resume(val nowMillis: Long, val reduceMotion: Boolean = false) : FlowGameEvent
  data object Close : FlowGameEvent
}

data class FlowGameReduction(
  val state: FlowGameState,
  val completedCard: Boolean = false,
  val lostLife: Boolean = false,
  val newBestCandidate: Boolean = false,
)

object FlowGameFactory {
  fun create(
    deck: Deck,
    nowMillis: Long,
    seed: Long = Random.nextLong(),
    reduceMotion: Boolean = false,
    baseCardDurationMillis: Long = 8_000L,
  ): FlowGameState {
    require(deck.items.isNotEmpty()) { "Flow deck must not be empty" }
    val shuffled = deck.items.shuffled(Random(seed))
    val countdown = if (reduceMotion) FLOW_REDUCED_COUNTDOWN_MILLIS else FLOW_COUNTDOWN_MILLIS
    return FlowGameState(
      deckId = deck.deckId,
      course = courseFor(deck.deckId),
      cards = shuffled,
      cardIndex = 0,
      currentCard = makeCard(shuffled.first(), 0L, baseCardDurationMillis),
      phase = FlowPhase.COUNTDOWN,
      countdownStartedAtMillis = nowMillis,
      countdownDurationMillis = countdown,
      lastTickAtMillis = nowMillis,
      remainingTimeMillis = FLOW_INITIAL_TIME_MILLIS,
      playElapsedMillis = 0,
      score = 0,
      combo = 0,
      maxCombo = 0,
      lives = FLOW_INITIAL_LIVES,
      correctJamoCount = 0,
      scoredJamoCount = 0,
      mistakeCount = 0,
      completedItemCount = 0,
      missedItemCount = 0,
      feedbackRevision = 0,
    )
  }

  internal fun makeCard(item: DeckItem, playElapsedMillis: Long, baseDurationMillis: Long = 8_000L): FlowCard {
    val speed = speedMultiplier(playElapsedMillis)
    return FlowCard(
      item = item,
      judge = JamoJudgeState.forTarget(item.ko),
      durationMillis = (baseDurationMillis / speed).roundToInt().toLong().coerceAtLeast(2_200L),
    )
  }

  fun speedMultiplier(playElapsedMillis: Long): Double {
    val fraction = playElapsedMillis.toDouble().coerceIn(0.0, FLOW_ACCELERATION_WINDOW_MILLIS.toDouble()) /
      FLOW_ACCELERATION_WINDOW_MILLIS
    return 1.0 + (FLOW_MAX_SPEED_MULTIPLIER - 1.0) * fraction
  }

  private fun courseFor(deckId: String): String = when {
    deckId.endsWith("beginner") -> "beginner"
    deckId.endsWith("intermediate") -> "intermediate"
    deckId.endsWith("advanced") -> "advanced"
    else -> "custom"
  }
}

object FlowGameReducer {
  fun reduce(state: FlowGameState, event: FlowGameEvent): FlowGameReduction = when (event) {
    is FlowGameEvent.Tick -> tick(state, event.nowMillis)
    is FlowGameEvent.Key -> key(state, event.jamo)
    FlowGameEvent.Pause -> pause(state)
    is FlowGameEvent.Resume -> resume(state, event.nowMillis, event.reduceMotion)
    FlowGameEvent.Close -> FlowGameReduction(finish(state, FlowFinishReason.CLOSED))
  }

  private fun tick(state: FlowGameState, nowMillis: Long): FlowGameReduction {
    if (state.phase == FlowPhase.FINISHED || state.phase == FlowPhase.PAUSED) return FlowGameReduction(state)
    require(nowMillis >= (state.lastTickAtMillis ?: nowMillis)) { "Monotonic time must not move backwards" }
    if (state.phase == FlowPhase.COUNTDOWN) {
      val elapsed = nowMillis - state.countdownStartedAtMillis
      return if (elapsed >= state.countdownDurationMillis) {
        FlowGameReduction(state.copy(phase = FlowPhase.PLAYING, lastTickAtMillis = nowMillis))
      } else {
        FlowGameReduction(state.copy(lastTickAtMillis = nowMillis))
      }
    }

    val delta = nowMillis - requireNotNull(state.lastTickAtMillis)
    val remaining = state.remainingTimeMillis - delta
    if (remaining <= 0L) {
      return FlowGameReduction(finish(state.copy(remainingTimeMillis = 0, playElapsedMillis = state.playElapsedMillis + delta), FlowFinishReason.TIME))
    }
    val advancedCard = state.currentCard.copy(elapsedMillis = state.currentCard.elapsedMillis + delta)
    if (advancedCard.elapsedMillis < advancedCard.durationMillis) {
      return FlowGameReduction(
        state.copy(
          currentCard = advancedCard,
          remainingTimeMillis = remaining,
          playElapsedMillis = state.playElapsedMillis + delta,
          lastTickAtMillis = nowMillis,
        ),
      )
    }

    val lives = state.lives - 1
    val advanced = state.copy(
      currentCard = advancedCard,
      remainingTimeMillis = remaining,
      playElapsedMillis = state.playElapsedMillis + delta,
      lastTickAtMillis = nowMillis,
      lives = lives,
      combo = 0,
      missedItemCount = state.missedItemCount + 1,
      feedbackRevision = state.feedbackRevision + 1,
    )
    return if (lives == 0) {
      FlowGameReduction(finish(advanced, FlowFinishReason.LIVES), lostLife = true)
    } else {
      FlowGameReduction(nextCard(advanced), lostLife = true)
    }
  }

  private fun key(state: FlowGameState, jamo: Char): FlowGameReduction {
    if (state.phase != FlowPhase.PLAYING) return FlowGameReduction(state)
    val evaluation = JamoSequenceJudge.evaluate(jamo, state.currentCard.judge)
    return when (val result = evaluation.result) {
      JamoJudgeResult.AlreadyComplete -> FlowGameReduction(state)
      is JamoJudgeResult.Incorrect -> FlowGameReduction(
        state.copy(
          currentCard = state.currentCard.copy(judge = evaluation.state, hadMistake = true),
          combo = 0,
          mistakeCount = state.mistakeCount + 1,
          feedbackRevision = state.feedbackRevision + 1,
        ),
      )
      is JamoJudgeResult.Correct -> {
        val updatedCard = state.currentCard.copy(judge = evaluation.state)
        if (!result.completed) {
          FlowGameReduction(state.copy(currentCard = updatedCard, correctJamoCount = state.correctJamoCount + 1))
        } else {
          val nextCombo = if (updatedCard.hadMistake) 0 else state.combo + 1
          val jamoCount = updatedCard.judge.expectedSequence.size
          val points = (jamoCount * 10 * comboMultiplier(nextCombo)).roundToInt()
          val completed = state.copy(
            currentCard = updatedCard,
            remainingTimeMillis = state.remainingTimeMillis + if (updatedCard.hadMistake) 0 else FLOW_PERFECT_BONUS_MILLIS,
            score = state.score + points,
            combo = nextCombo,
            maxCombo = maxOf(state.maxCombo, nextCombo),
            correctJamoCount = state.correctJamoCount + 1,
            scoredJamoCount = state.scoredJamoCount + jamoCount,
            completedItemCount = state.completedItemCount + 1,
            feedbackRevision = state.feedbackRevision + 1,
          )
          FlowGameReduction(nextCard(completed), completedCard = true, newBestCandidate = true)
        }
      }
    }
  }

  private fun pause(state: FlowGameState): FlowGameReduction = if (state.phase == FlowPhase.FINISHED) {
    FlowGameReduction(state)
  } else {
    FlowGameReduction(state.copy(phase = FlowPhase.PAUSED, lastTickAtMillis = null))
  }

  private fun resume(state: FlowGameState, nowMillis: Long, reduceMotion: Boolean): FlowGameReduction {
    if (state.phase != FlowPhase.PAUSED) return FlowGameReduction(state)
    return FlowGameReduction(
      state.copy(
        phase = FlowPhase.COUNTDOWN,
        countdownStartedAtMillis = nowMillis,
        countdownDurationMillis = if (reduceMotion) FLOW_REDUCED_COUNTDOWN_MILLIS else FLOW_COUNTDOWN_MILLIS,
        lastTickAtMillis = nowMillis,
      ),
    )
  }

  private fun nextCard(state: FlowGameState): FlowGameState {
    val index = (state.cardIndex + 1) % state.cards.size
    return state.copy(
      cardIndex = index,
      currentCard = FlowGameFactory.makeCard(state.cards[index], state.playElapsedMillis),
    )
  }

  private fun finish(state: FlowGameState, reason: FlowFinishReason): FlowGameState = state.copy(
    phase = FlowPhase.FINISHED,
    finishReason = reason,
    lastTickAtMillis = null,
  )

  fun comboMultiplier(combo: Int): Double = when {
    combo >= 20 -> 2.0
    combo >= 10 -> 1.5
    combo >= 5 -> 1.2
    else -> 1.0
  }
}

fun FlowGameState.toRecord(playedAtEpochMillis: Long): FlowGameRecord = FlowGameRecord(
  deckId = deckId,
  course = course,
  score = score,
  maxCombo = maxCombo,
  accuracyPercent = accuracyPercent,
  correctJamoCount = correctJamoCount,
  charactersPerMinute = charactersPerMinute,
  mistakeCount = mistakeCount,
  completedItemCount = completedItemCount,
  missedItemCount = missedItemCount,
  playDurationMillis = playElapsedMillis,
  playedAtEpochMillis = playedAtEpochMillis,
)

data class FlowGameRecord(
  val deckId: String,
  val course: String,
  val score: Int,
  val maxCombo: Int,
  val accuracyPercent: Double,
  val correctJamoCount: Int,
  val charactersPerMinute: Double,
  val mistakeCount: Int,
  val completedItemCount: Int,
  val missedItemCount: Int,
  val playDurationMillis: Long,
  val playedAtEpochMillis: Long,
)

data class FlowRankTuning(
  val accuracyWeight: Double = 0.6,
  val speedWeight: Double = 0.4,
  val speedCapCharactersPerMinute: Double = 120.0,
  val sThreshold: Double = 90.0,
  val aThreshold: Double = 75.0,
  val bThreshold: Double = 55.0,
) {
  init {
    require(accuracyWeight >= 0 && speedWeight >= 0 && kotlin.math.abs(accuracyWeight + speedWeight - 1.0) < 0.000_001)
    require(speedCapCharactersPerMinute > 0)
    require(sThreshold in 0.0..100.0 && aThreshold in 0.0..100.0 && bThreshold in 0.0..100.0)
    require(sThreshold > aThreshold && aThreshold > bThreshold)
  }

  fun rank(accuracyPercent: Double, charactersPerMinute: Double): String {
    val accuracyScore = accuracyPercent.coerceIn(0.0, 100.0)
    val speedScore = (charactersPerMinute / speedCapCharactersPerMinute * 100.0).coerceIn(0.0, 100.0)
    val weighted = accuracyScore * accuracyWeight + speedScore * speedWeight
    return when {
      weighted >= sThreshold -> "S"
      weighted >= aThreshold -> "A"
      weighted >= bThreshold -> "B"
      else -> "C"
    }
  }
}
