package app.piyokey.core.game

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FlowGameReducerTest {
  @Test fun countdownDoesNotConsumeGameTimeAndStartsAfterThreeSeconds() {
    val initial = state()
    val waiting = reduce(initial, FlowGameEvent.Tick(2_999))
    assertEquals(FlowPhase.COUNTDOWN, waiting.phase)
    assertEquals(60_000, waiting.remainingTimeMillis)
    val playing = reduce(waiting, FlowGameEvent.Tick(3_000))
    assertEquals(FlowPhase.PLAYING, playing.phase)
    assertEquals(60_000, playing.remainingTimeMillis)
  }

  @Test fun perfectWordScoresByJamoAndAddsTwoSeconds() {
    var state = playing()
    for (jamo in state.currentCard.judge.expectedSequence) state = reduce(state, FlowGameEvent.Key(jamo))
    assertEquals(1, state.completedItemCount)
    assertEquals(1, state.combo)
    assertEquals(2, state.correctJamoCount)
    assertEquals(62_000, state.remainingTimeMillis)
    assertEquals(20, state.score)
  }

  @Test fun mistakeResetsComboAndPreventsBonusWithoutTimePenalty() {
    var state = playing().copy(combo = 7, maxCombo = 7)
    val wrong = if (state.currentCard.judge.expectedNext == 'ㄱ') 'ㄴ' else 'ㄱ'
    state = reduce(state, FlowGameEvent.Key(wrong))
    assertEquals(0, state.combo)
    assertEquals(1, state.mistakeCount)
    assertEquals(1, state.reviewMistakeCounts.values.single())
    assertEquals(60_000, state.remainingTimeMillis)
    for (jamo in state.currentCard.judge.expectedSequence) state = reduce(state, FlowGameEvent.Key(jamo))
    assertEquals(0, state.combo)
    assertEquals(60_000, state.remainingTimeMillis)
  }

  @Test fun comboThresholdMultipliersAreStable() {
    assertEquals(1.0, FlowGameReducer.comboMultiplier(4))
    assertEquals(1.2, FlowGameReducer.comboMultiplier(5))
    assertEquals(1.5, FlowGameReducer.comboMultiplier(10))
    assertEquals(2.0, FlowGameReducer.comboMultiplier(20))
  }

  @Test fun cardExitCostsLifeAndThirdExitEndsImmediately() {
    var state = playing()
    repeat(2) {
      state = reduce(state, FlowGameEvent.Tick(requireNotNull(state.lastTickAtMillis) + state.currentCard.durationMillis))
      assertEquals(FlowPhase.PLAYING, state.phase)
    }
    state = reduce(state, FlowGameEvent.Tick(requireNotNull(state.lastTickAtMillis) + state.currentCard.durationMillis))
    assertEquals(FlowPhase.FINISHED, state.phase)
    assertEquals(FlowFinishReason.LIVES, state.finishReason)
    assertEquals(0, state.lives)
    assertEquals(3, state.reviewMistakeCounts.values.sum())
  }

  @Test fun timeExpiryEndsEvenWhenLivesRemain() {
    val state = reduce(playing(), FlowGameEvent.Tick(63_001))
    assertEquals(FlowPhase.FINISHED, state.phase)
    assertEquals(FlowFinishReason.TIME, state.finishReason)
  }

  @Test fun pauseFreezesThenResumeRequiresFreshCountdown() {
    val active = reduce(playing(), FlowGameEvent.Tick(4_000))
    val paused = reduce(active, FlowGameEvent.Pause)
    val stillPaused = reduce(paused, FlowGameEvent.Tick(100_000))
    assertEquals(active.remainingTimeMillis, stillPaused.remainingTimeMillis)
    val countdown = reduce(stillPaused, FlowGameEvent.Resume(100_000))
    assertEquals(FlowPhase.COUNTDOWN, countdown.phase)
    val resumed = reduce(countdown, FlowGameEvent.Tick(103_000))
    assertEquals(FlowPhase.PLAYING, resumed.phase)
    assertEquals(active.remainingTimeMillis, resumed.remainingTimeMillis)
  }

  @Test fun speedIsLinearAndCapsAtOnePointEightForNewCards() {
    assertEquals(1.0, FlowGameFactory.speedMultiplier(0))
    assertEquals(1.4, FlowGameFactory.speedMultiplier(25_000))
    assertEquals(1.8, FlowGameFactory.speedMultiplier(50_000))
    assertEquals(1.8, FlowGameFactory.speedMultiplier(80_000))
    val early = FlowGameFactory.makeCard(item("a", "나"), 0)
    val late = FlowGameFactory.makeCard(item("a", "나"), 50_000)
    assertTrue(late.durationMillis < early.durationMillis)
  }

  @Test fun explicitSeedReproducesOrderAndDifferentSeedChangesIt() {
    val deck = deck()
    val first = FlowGameFactory.create(deck, 0, seed = 42).cards.map { it.id }
    assertEquals(first, FlowGameFactory.create(deck, 0, seed = 42).cards.map { it.id })
    assertNotEquals(first, FlowGameFactory.create(deck, 0, seed = 43).cards.map { it.id })
  }

  @Test fun inputOutsidePlayingIsIgnored() {
    val initial = state()
    assertEquals(initial, reduce(initial, FlowGameEvent.Key('ㄴ')))
  }

  @Test fun rankTuningMatchesSharedAccuracyAndSpeedContract() {
    val tuning = FlowRankTuning()
    assertEquals("S", tuning.rank(100.0, 120.0))
    assertEquals("A", tuning.rank(80.0, 100.0))
    assertEquals("B", tuning.rank(70.0, 60.0))
    assertEquals("C", tuning.rank(40.0, 30.0))
  }

  private fun state() = FlowGameFactory.create(deck(), nowMillis = 0, seed = 42)
  private fun playing() = reduce(state(), FlowGameEvent.Tick(3_000))
  private fun reduce(state: FlowGameState, event: FlowGameEvent) = FlowGameReducer.reduce(state, event).state

  private fun deck() = Deck(
    deckId = "flow_topik_beginner",
    version = 3,
    name = "Flow",
    author = DeckAuthor("official", "official"),
    official = true,
    type = DeckType.WORD,
    level = 1,
    tags = listOf("TOPIK"),
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    items = listOf(item("a", "나"), item("b", "너"), item("c", "비"), item("d", "길")),
  )

  private fun item(id: String, ko: String) = DeckItem(id, ko, "reading", "meaning", null)
}
