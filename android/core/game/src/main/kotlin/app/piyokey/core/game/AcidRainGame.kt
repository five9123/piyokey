package app.piyokey.core.game

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.hangul.JamoJudgeResult
import app.piyokey.core.hangul.JamoJudgeState
import app.piyokey.core.hangul.JamoSequenceJudge
import app.piyokey.core.hangul.OSIMETextJudge
import app.piyokey.core.hangul.OSIMETextJudgeStatus
import kotlin.math.roundToLong
import kotlin.random.Random

const val ACID_RAIN_MAX_CARDS = 4
const val ACID_RAIN_LANES = 3
const val ACID_RAIN_MAX_SPEED_MULTIPLIER = 1.5
const val ACID_RAIN_MIN_SPAWN_MILLIS = 1_400L
const val ACID_RAIN_MAX_SPAWN_MILLIS = 3_600L

data class AcidRainCard(
  val id: Long,
  val item: DeckItem,
  val lane: Int,
  val durationMillis: Long,
  val elapsedMillis: Long = 0,
  val judge: JamoJudgeState = JamoJudgeState.forTarget(item.ko),
  val hadMistake: Boolean = false,
) {
  val progress: Double get() = (elapsedMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)
}

data class AcidRainState(
  val deckId: String,
  val course: String,
  val items: List<DeckItem>,
  val nextItemIndex: Int,
  val cards: List<AcidRainCard>,
  val activeCardId: Long?,
  val nextCardId: Long,
  val phase: FlowPhase,
  val countdownStartedAtMillis: Long,
  val countdownDurationMillis: Long,
  val lastTickAtMillis: Long?,
  val nextSpawnAtPlayMillis: Long,
  val remainingTimeMillis: Long = FLOW_INITIAL_TIME_MILLIS,
  val playElapsedMillis: Long = 0,
  val score: Int = 0,
  val combo: Int = 0,
  val maxCombo: Int = 0,
  val lives: Int = FLOW_INITIAL_LIVES,
  val correctJamoCount: Int = 0,
  val mistakeCount: Int = 0,
  val completedItemCount: Int = 0,
  val missedItemCount: Int = 0,
  val reviewMistakeCounts: Map<String, Int> = emptyMap(),
  val finishReason: FlowFinishReason? = null,
) {
  val activeCard: AcidRainCard? get() = cards.firstOrNull { it.id == activeCardId }
  val accuracyPercent: Double get() {
    val total = correctJamoCount + mistakeCount
    return if (total == 0) 0.0 else correctJamoCount.toDouble() / total * 100.0
  }
}

sealed interface AcidRainEvent {
  data class Tick(val nowMillis: Long) : AcidRainEvent
  data class Key(val jamo: Char) : AcidRainEvent
  data class IMEText(val committedText: String, val composingText: String?) : AcidRainEvent
  data object Backspace : AcidRainEvent
  data object Pause : AcidRainEvent
  data class Resume(val nowMillis: Long, val reduceMotion: Boolean = false) : AcidRainEvent
  data object Close : AcidRainEvent
}

object AcidRainFactory {
  fun create(
    deck: Deck,
    nowMillis: Long,
    seed: Long = Random.nextLong(),
    reduceMotion: Boolean = false,
    baseDurationMillis: Long = 8_000L,
  ): AcidRainState {
    require(deck.items.isNotEmpty()) { "Acid Rain deck must not be empty" }
    val items = deck.items.shuffled(Random(seed))
    val first = card(0, items.first(), 0, baseDurationMillis, 0)
    return AcidRainState(
      deckId = deck.deckId,
      course = courseFor(deck.deckId),
      items = items,
      nextItemIndex = if (items.size > 1) 1 else 0,
      cards = listOf(first),
      activeCardId = first.id,
      nextCardId = 1,
      phase = FlowPhase.COUNTDOWN,
      countdownStartedAtMillis = nowMillis,
      countdownDurationMillis = if (reduceMotion) FLOW_REDUCED_COUNTDOWN_MILLIS else FLOW_COUNTDOWN_MILLIS,
      lastTickAtMillis = nowMillis,
      nextSpawnAtPlayMillis = spawnInterval(first.durationMillis),
    )
  }

  fun speedMultiplier(playElapsedMillis: Long): Double {
    val fraction = playElapsedMillis.toDouble().coerceIn(0.0, FLOW_ACCELERATION_WINDOW_MILLIS.toDouble()) / FLOW_ACCELERATION_WINDOW_MILLIS
    return 1.0 + (ACID_RAIN_MAX_SPEED_MULTIPLIER - 1.0) * fraction
  }

  internal fun spawnInterval(durationMillis: Long): Long =
    (durationMillis * 0.42).roundToLong().coerceIn(ACID_RAIN_MIN_SPAWN_MILLIS, ACID_RAIN_MAX_SPAWN_MILLIS)
      .coerceAtMost((durationMillis * 0.72).roundToLong())

  internal fun card(id: Long, item: DeckItem, lane: Int, baseDurationMillis: Long, playElapsedMillis: Long): AcidRainCard =
    AcidRainCard(
      id = id,
      item = item,
      lane = lane.mod(ACID_RAIN_LANES),
      durationMillis = (baseDurationMillis / speedMultiplier(playElapsedMillis)).roundToLong().coerceAtLeast(2_200L),
    )

  private fun courseFor(deckId: String): String = when {
    deckId.endsWith("beginner") -> "beginner"
    deckId.endsWith("intermediate") -> "intermediate"
    deckId.endsWith("advanced") -> "advanced"
    else -> "custom"
  }
}

object AcidRainReducer {
  fun reduce(state: AcidRainState, event: AcidRainEvent): AcidRainState = when (event) {
    is AcidRainEvent.Tick -> tick(state, event.nowMillis)
    is AcidRainEvent.Key -> key(state, event.jamo)
    is AcidRainEvent.IMEText -> ime(state, event.committedText, event.composingText)
    AcidRainEvent.Backspace -> backspace(state)
    AcidRainEvent.Pause -> if (state.phase == FlowPhase.FINISHED) state else state.copy(phase = FlowPhase.PAUSED, lastTickAtMillis = null)
    is AcidRainEvent.Resume -> if (state.phase == FlowPhase.PAUSED) state.copy(
      phase = FlowPhase.COUNTDOWN,
      countdownStartedAtMillis = event.nowMillis,
      countdownDurationMillis = if (event.reduceMotion) FLOW_REDUCED_COUNTDOWN_MILLIS else FLOW_COUNTDOWN_MILLIS,
      lastTickAtMillis = event.nowMillis,
    ) else state
    AcidRainEvent.Close -> finish(state, FlowFinishReason.CLOSED)
  }

  private fun tick(state: AcidRainState, now: Long): AcidRainState {
    if (state.phase == FlowPhase.FINISHED || state.phase == FlowPhase.PAUSED) return state
    if (state.phase == FlowPhase.COUNTDOWN) return if (now - state.countdownStartedAtMillis >= state.countdownDurationMillis) {
      state.copy(phase = FlowPhase.PLAYING, lastTickAtMillis = now)
    } else state.copy(lastTickAtMillis = now)
    val previous = requireNotNull(state.lastTickAtMillis)
    require(now >= previous) { "Monotonic time must not move backwards" }
    val delta = now - previous
    val elapsed = state.playElapsedMillis + delta
    val remaining = state.remainingTimeMillis - delta
    if (remaining <= 0) return finish(state.copy(remainingTimeMillis = 0, playElapsedMillis = elapsed), FlowFinishReason.TIME)

    val advanced = state.cards.map { it.copy(elapsedMillis = it.elapsedMillis + delta) }
    val missed = advanced.filter { it.elapsedMillis >= it.durationMillis }
    var next = state.copy(
      cards = advanced - missed.toSet(),
      remainingTimeMillis = remaining,
      playElapsedMillis = elapsed,
      lastTickAtMillis = now,
      lives = (state.lives - missed.size).coerceAtLeast(0),
      combo = if (missed.isEmpty()) state.combo else 0,
      missedItemCount = state.missedItemCount + missed.size,
      reviewMistakeCounts = missed.fold(state.reviewMistakeCounts) { counts, card -> counts.incrementAcid(card.item.id) },
    )
    if (next.lives == 0) return finish(next, FlowFinishReason.LIVES)
    while (next.playElapsedMillis >= next.nextSpawnAtPlayMillis && next.cards.size < ACID_RAIN_MAX_CARDS) next = spawn(next)
    return retarget(next)
  }

  private fun key(state: AcidRainState, jamo: Char): AcidRainState {
    if (state.phase != FlowPhase.PLAYING) return state
    val active = state.activeCard ?: return retarget(state)
    val evaluation = JamoSequenceJudge.evaluate(jamo, active.judge)
    return when (val result = evaluation.result) {
      JamoJudgeResult.AlreadyComplete -> state
      is JamoJudgeResult.Incorrect -> state.copy(
        cards = state.cards.replace(active.copy(judge = evaluation.state, hadMistake = true)),
        combo = 0,
        mistakeCount = state.mistakeCount + 1,
        reviewMistakeCounts = state.reviewMistakeCounts.incrementAcid(active.item.id),
      )
      is JamoJudgeResult.Correct -> if (result.completed) complete(state, active.copy(judge = evaluation.state)) else state.copy(
        cards = state.cards.replace(active.copy(judge = evaluation.state)),
        correctJamoCount = state.correctJamoCount + 1,
      )
    }
  }

  private fun ime(state: AcidRainState, committed: String, composing: String?): AcidRainState {
    if (state.phase != FlowPhase.PLAYING) return state
    val evaluations = state.cards.map { it to OSIMETextJudge.evaluate(it.item.ko, committed, composing) }
    val completed = evaluations.firstOrNull { (_, result) ->
      val status = result.status
      status is OSIMETextJudgeStatus.Matching && status.completed && !status.isComposing
    }
    if (completed != null) {
      val (card, result) = completed
      val judge = replay(card.item.ko, result.acceptedSequence)
      return complete(state, card.copy(judge = judge))
    }
    val matching = evaluations.filter { (_, result) -> result.status is OSIMETextJudgeStatus.Matching }.maxByOrNull { it.second.acceptedSequence.size }
    if (matching != null) {
      val (card, result) = matching
      val updated = card.copy(judge = replay(card.item.ko, result.acceptedSequence))
      return state.copy(cards = state.cards.replace(updated), activeCardId = card.id)
    }
    val active = state.activeCard ?: return state
    val confirmed = evaluations.firstOrNull { it.first.id == active.id }?.second?.status as? OSIMETextJudgeStatus.ConfirmedMismatch
    return if (confirmed == null) state else state.copy(
      cards = state.cards.replace(active.copy(hadMistake = true)),
      combo = 0,
      mistakeCount = state.mistakeCount + 1,
      reviewMistakeCounts = state.reviewMistakeCounts.incrementAcid(active.item.id),
    )
  }

  private fun backspace(state: AcidRainState): AcidRainState {
    if (state.phase != FlowPhase.PLAYING) return state
    val active = state.activeCard ?: return state
    if (active.judge.currentIndex == 0) return state
    val judge = replay(active.item.ko, active.judge.expectedSequence.take(active.judge.currentIndex - 1))
    return state.copy(cards = state.cards.replace(active.copy(judge = judge)))
  }

  private fun complete(state: AcidRainState, card: AcidRainCard): AcidRainState {
    val combo = if (card.hadMistake) 0 else state.combo + 1
    val jamoCount = card.judge.expectedSequence.size
    val previousAccepted = state.cards.firstOrNull { it.id == card.id }?.judge?.currentIndex ?: 0
    val acceptedDelta = (card.judge.currentIndex - previousAccepted).coerceAtLeast(0)
    val points = (jamoCount * 10 * FlowGameReducer.comboMultiplier(combo)).roundToLong().toInt()
    return retarget(state.copy(
      cards = state.cards.filterNot { it.id == card.id },
      activeCardId = null,
      remainingTimeMillis = state.remainingTimeMillis + if (card.hadMistake) 0 else FLOW_PERFECT_BONUS_MILLIS,
      score = state.score + points,
      combo = combo,
      maxCombo = maxOf(state.maxCombo, combo),
      correctJamoCount = state.correctJamoCount + acceptedDelta,
      completedItemCount = state.completedItemCount + 1,
    ))
  }

  private fun spawn(state: AcidRainState): AcidRainState {
    val item = state.items[state.nextItemIndex]
    val lane = (state.nextCardId % ACID_RAIN_LANES).toInt()
    val card = AcidRainFactory.card(state.nextCardId, item, lane, baseDuration(item), state.playElapsedMillis)
    return state.copy(
      cards = state.cards + card,
      nextItemIndex = (state.nextItemIndex + 1) % state.items.size,
      nextCardId = state.nextCardId + 1,
      nextSpawnAtPlayMillis = state.playElapsedMillis + AcidRainFactory.spawnInterval(card.durationMillis),
    )
  }

  private fun retarget(state: AcidRainState): AcidRainState {
    val active = state.activeCard
    if (active != null && active.judge.currentIndex > 0) return state
    return state.copy(activeCardId = state.cards.maxByOrNull(AcidRainCard::progress)?.id)
  }

  private fun replay(target: String, keys: List<Char>): JamoJudgeState {
    var judge = JamoJudgeState.forTarget(target)
    keys.forEach { judge = JamoSequenceJudge.evaluate(it, judge).state }
    return judge
  }

  private fun finish(state: AcidRainState, reason: FlowFinishReason) = state.copy(phase = FlowPhase.FINISHED, finishReason = reason, lastTickAtMillis = null)
  private fun baseDuration(item: DeckItem): Long = when {
    item.ko.length <= 2 -> 6_800L
    item.ko.length <= 5 -> 8_000L
    else -> 10_000L
  }
}

private fun List<AcidRainCard>.replace(card: AcidRainCard): List<AcidRainCard> = map { if (it.id == card.id) card else it }
private fun Map<String, Int>.incrementAcid(key: String): Map<String, Int> = this + (key to ((this[key] ?: 0) + 1))
