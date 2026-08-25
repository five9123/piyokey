package app.piyokey.core.game

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TypingGamesTest {
  @Test fun roundBuilderIsUniqueDeterministicAndFiltersByMode() {
    val items = listOf(
      item("a", "가", meaning = "go"),
      item("duplicate", "가", meaning = "duplicate"),
      item("b", "공", meaning = "ball"),
      item("c", "나", meaning = ""),
    )
    val first = TypingRoundBuilder.build(TypingGameMode.WORD_MATCH, items, "en", seed = 7)
    val second = TypingRoundBuilder.build(TypingGameMode.WORD_MATCH, items, "en", seed = 7)
    assertEquals(first.map { it.item.id }, second.map { it.item.id })
    assertEquals(2, first.size)
    assertEquals(first.size, first.map { it.item.ko }.distinct().size)
    assertNotEquals(emptyList(), TypingRoundBuilder.build(TypingGameMode.DICTATION, items, "en", seed = 1))
  }

  @Test fun choseongExtractionPreservesSpacesAndExcludesPunctuation() {
    assertEquals("ㅎㄱ ㄱ", TypingRoundBuilder.extractInitials("한국 가!"))
  }

  @Test fun ambiguousChoseongRoundsRequireMeaningAndDictationKeepsAnswerSecret() {
    val ambiguous = TypingRoundBuilder.build(
      TypingGameMode.CHOSEONG,
      listOf(item("a", "가", "go"), item("b", "고", "and")),
      "en",
      seed = 2,
    )
    assertTrue(ambiguous.all(TypingRound::requiresMeaningHint))
    var dictation = TypingGameFactory.create(
      TypingGameMode.DICTATION,
      listOf(TypingRound(item("c", "나"), "", false)),
      nowMillis = 0,
    )
    assertFalse(dictation.answerVisible)
    dictation = TypingGameReducer.reduce(dictation, TypingGameEvent.Tick(3_000))
    assertFalse(dictation.answerVisible)
  }

  @Test fun directTypingIgnoresWrongJamoScoresAndAdvancesAfterHold() {
    val rounds = TypingRoundBuilder.build(TypingGameMode.WORD_MATCH, listOf(item("a", "가"), item("b", "나")), "en", seed = 1)
    var state = TypingGameFactory.create(TypingGameMode.WORD_MATCH, rounds, 0)
    state = TypingGameReducer.reduce(state, TypingGameEvent.Tick(3_000))
    val expected = state.judge.expectedSequence
    val wrong = if (expected.first() == 'ㄴ') 'ㄱ' else 'ㄴ'
    state = TypingGameReducer.reduce(state, TypingGameEvent.Key(wrong, 3_100))
    assertEquals(1, state.mistakeCount)
    assertEquals(TypingFeedback.WRONG, state.feedback)
    assertEquals("", state.composition.text)
    expected.forEachIndexed { index, key -> state = TypingGameReducer.reduce(state, TypingGameEvent.Key(key, 3_200L + index)) }
    assertEquals(TypingGamePhase.ANSWER_HOLD, state.phase)
    assertEquals(TypingFeedback.COMPLETE, state.feedback)
    assertTrue(state.answerVisible)
    assertEquals(1, state.completedItemCount)
    val holdStart = requireNotNull(state.answerHoldStartedAtMillis)
    state = TypingGameReducer.reduce(state, TypingGameEvent.Tick(holdStart + 649))
    assertEquals(0, state.roundIndex)
    state = TypingGameReducer.reduce(state, TypingGameEvent.Tick(holdStart + 650))
    assertEquals(1, state.roundIndex)
    assertEquals(TypingGamePhase.PLAYING, state.phase)
  }

  @Test fun composingMismatchNeverCountsAndConfirmedMismatchCountsOnce() {
    val round = TypingRoundBuilder.build(TypingGameMode.DICTATION, listOf(item("a", "가")), "en", seed = 1)
    var state = TypingGameFactory.create(TypingGameMode.DICTATION, round, 0)
    state = TypingGameReducer.reduce(state, TypingGameEvent.Tick(3_000))
    state = TypingGameReducer.reduce(state, TypingGameEvent.IMEText("", "나", 3_100))
    assertEquals(0, state.mistakeCount)
    assertFalse(state.answerVisible)
    state = TypingGameReducer.reduce(state, TypingGameEvent.IMEText("나", null, 3_200))
    assertEquals(1, state.mistakeCount)
  }

  @Test fun pauseFreezesAndRequiresFreshCountdown() {
    val round = TypingRoundBuilder.build(TypingGameMode.DICTATION, listOf(item("a", "가")), "en", seed = 1)
    var state = TypingGameFactory.create(TypingGameMode.DICTATION, round, 0)
    state = TypingGameReducer.reduce(state, TypingGameEvent.Tick(3_000))
    state = TypingGameReducer.reduce(state, TypingGameEvent.Pause(4_000))
    val elapsed = state.activeElapsedMillis
    state = TypingGameReducer.reduce(state, TypingGameEvent.Tick(100_000))
    assertEquals(elapsed, state.activeElapsedMillis)
    state = TypingGameReducer.reduce(state, TypingGameEvent.Resume(100_000))
    assertEquals(TypingGamePhase.COUNTDOWN, state.phase)
  }

  @Test fun acidRainSpawnsBeforeFirstCardLandsAndThirdMissEnds() {
    var state = AcidRainFactory.create(deck(), 0, seed = 1, baseDurationMillis = 8_000)
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(3_000))
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(6_400))
    assertTrue(state.cards.size >= 2)
    assertTrue(state.cards.first().progress < 1.0)
    assertEquals(3, state.lives)
    var now = 6_400L
    while (state.phase != FlowPhase.FINISHED) {
      now += 12_000
      state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(now))
    }
    assertEquals(FlowFinishReason.LIVES, state.finishReason)
    assertEquals(0, state.lives)
  }

  @Test fun acidRainImeCanCompleteNonActiveVisibleCard() {
    var state = AcidRainFactory.create(deck(), 0, seed = 1, baseDurationMillis = 8_000)
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(3_000))
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(6_400))
    val nonActive = state.cards.first { it.id != state.activeCardId }
    state = AcidRainReducer.reduce(state, AcidRainEvent.IMEText(nonActive.item.ko, null))
    assertTrue(state.cards.none { it.id == nonActive.id })
    assertEquals(1, state.completedItemCount)
    assertEquals(nonActive.judge.expectedSequence.size, state.correctJamoCount)
  }

  @Test fun acidRainPauseFreezesCardsAndResumesThroughCountdown() {
    var state = AcidRainFactory.create(deck(), 0, seed = 1, baseDurationMillis = 8_000)
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(3_000))
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(4_000))
    val elapsed = state.cards.first().elapsedMillis
    state = AcidRainReducer.reduce(state, AcidRainEvent.Pause)
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(50_000))
    assertEquals(elapsed, state.cards.first().elapsedMillis)
    state = AcidRainReducer.reduce(state, AcidRainEvent.Resume(50_000))
    assertEquals(FlowPhase.COUNTDOWN, state.phase)
    state = AcidRainReducer.reduce(state, AcidRainEvent.Tick(53_000))
    assertEquals(elapsed, state.cards.first().elapsedMillis)
  }

  @Test fun spacingUsesImmutableFirstDecisionsAndRejectsChangedNonSpaceText() {
    val engine = SpacingEngine("나는 간다.")
    assertEquals("나는간다.", engine.compactText)
    assertEquals(null, engine.normalize("너는 간다."))
    var state = SpacingGameState(engine)
    state = SpacingReducer.reduce(state, SpacingEvent.Start(0))
    state = state.copy(cursor = 2)
    state = SpacingReducer.reduce(state, SpacingEvent.ToggleSpace)
    state = SpacingReducer.reduce(state, SpacingEvent.ToggleSpace)
    state = SpacingReducer.reduce(state, SpacingEvent.ToggleSpace)
    assertEquals(true, state.firstDecisions[2])
    assertEquals(2, state.correctionCount)
    state = SpacingReducer.reduce(state, SpacingEvent.Submit(1_000))
    assertEquals(1_000, state.result?.score)
    assertEquals(100.0, state.result?.firstDecisionAccuracyPercent)
  }

  private fun item(id: String, ko: String, meaning: String = "meaning") = DeckItem(
    id = id,
    ko = ko,
    readingJa = "reading",
    meaningJa = meaning,
    audio = null,
    localizations = mapOf("en" to DeckItemLocalization(meaning, "reading")),
  )

  private fun deck() = Deck(
    deckId = "acid_rain_topik_beginner",
    version = 3,
    name = "Acid",
    author = DeckAuthor("official", "official"),
    official = true,
    type = DeckType.WORD,
    level = 1,
    tags = listOf("TOPIK"),
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    items = listOf(item("a", "가"), item("b", "나"), item("c", "다"), item("d", "라"), item("e", "마")),
  )
}
