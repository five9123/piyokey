package app.piyokey.core.domain.game

import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.domain.GameCompetition
import app.piyokey.core.domain.GameKind
import app.piyokey.core.domain.TestSupport
import app.piyokey.core.hangul.JamoDecomposer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of iOS `SpacingGameTests` + preset/randomizer parts of `FlowGameViewModelTests`. */
class SpacingAndPresetsTest {
  private val passages: List<SpacingPassage> by lazy {
    SpacingPassageCatalog.decode(File(TestSupport.sharedRoot, "mock_catalog/spacing_passages.json").readBytes())
  }

  @Test fun engineRemovesSpacesAndRestoresOnlyValidWhitespaceEdits() {
    val engine = SpacingGameEngine("오늘은 날씨가 좋아요.")
    assertEquals("오늘은날씨가좋아요.", engine.compactText)
    assertEquals("오늘은 날씨가 좋아요.", engine.normalizedDraft("오늘은  날씨가\n좋아요."))
    assertEquals("오늘은 날씨가 좋아요.", engine.normalizedDraft("오늘은\u3000날씨가 좋아요."))
    assertNull(engine.normalizedDraft("오늘은 날씨도 좋아요."))
    assertNull(engine.normalizedDraft("오늘은 날씨가 좋아요"))
  }

  @Test fun evaluationCountsCorrectMissedAndExtraBoundaries() {
    val result = SpacingGameEngine("나는 오늘 학교에 걸어서 간다.").evaluate("나는오 늘 학교에걸어서 간다.", 12.0)
    assertEquals(2, result.correctSpaceCount)
    assertEquals(4, result.expectedSpaceCount)
    assertEquals(2, result.missedSpaceCount)
    assertEquals(1, result.extraSpaceCount)
    assertEquals(40.0, result.accuracyPercent, 0.001)
    assertEquals(400, result.score)
    assertEquals("C", result.rank)
    assertFalse(result.isPerfect)
  }

  @Test fun perfectEvaluationGetsFullScoreAndSRank() {
    val engine = SpacingGameEngine("띄어쓰기를 정확하게 넣어요.")
    val result = engine.evaluate(engine.answer, 9.0)
    assertEquals(1_000, result.score)
    assertEquals("S", result.rank)
    assertTrue(result.isPerfect)
  }

  @Test fun firstDecisionsDriveScoreAndProduceMistakeReview() {
    val engine = SpacingGameEngine("나는 학교에 간다.")
    val decisions = (1..engine.boundaryCount).associateWith { engine.isCorrectBoundary(it) }.toMutableMap()
    decisions[1] = true
    val result = engine.evaluate(engine.answer, 12.0, decisions, 1)
    assertEquals(engine.boundaryCount - 1, result.firstAttemptCorrectCount)
    assertEquals(600.0 / 7.0, result.firstAttemptAccuracyPercent, 0.001)
    assertEquals(857, result.score)
    assertEquals(1, result.correctionCount)
    assertEquals(listOf(SpacingMistakeReview(1, "나 는", "나는", false)), result.firstAttemptMistakes)
    assertEquals(100.0, result.accuracyPercent, 0.001)
    assertFalse(result.isPerfect)
  }

  @Test fun bundledPassagesMatchIosCatalogRules() {
    assertEquals(6, passages.size)
    assertEquals((1..6).toList(), passages.map { it.level })
    assertTrue(passages.zipWithNext().all { (a, b) -> a.characterCount < b.characterCount })
    assertTrue(passages[5].characterCount >= 180)
    for (passage in passages) {
      assertTrue(passage.characterCount in 100..200, passage.id)
      assertTrue(passage.spaceCount >= 30, passage.id)
      assertFalse(passage.text.contains("  "), passage.id)
      assertEquals(passage.text, SpacingGameEngine(passage.text).answer, passage.id)
    }
  }

  @Test fun bundledPassagesEqualIosLocalizedPassageText() {
    val ios = File(TestSupport.sharedRoot, "../ios/Hanco/Hanco/Resources")
    val languages = ios.listFiles { f -> f.name.endsWith(".lproj") }.orEmpty()
    assertTrue(languages.isNotEmpty())
    for (language in languages) {
      val strings = File(language, "Localizable.strings").readText()
      for (passage in passages) {
        val key = passage.titleKey + ".text"
        val match = Regex("\"" + Regex.escape(key) + "\"\\s*=\\s*\"(.*?)\";").find(strings)
        assertEquals(passage.text, match?.groupValues?.get(1), "${language.name} ${passage.id}")
      }
    }
  }

  @Test fun sessionMovesAndTogglesAtCurrentBoundary() {
    val model = SpacingGameSession(SpacingPassage("test", "spacing.passage.morning", "나는 학교에 간다."))
    model.start(10_000.0)
    assertEquals(1, model.currentBoundary)
    assertFalse(model.canMoveLeft)
    assertEquals(SpacingPlacementOutcome.INCORRECT, model.toggleCurrentSpace())
    assertEquals("나 는학교에간다.", model.draft)
    assertNull(model.toggleCurrentSpace())
    assertNull(model.feedback)
    assertEquals("나는학교에간다.", model.draft)
    assertEquals(1, model.correctionCount)
    assertEquals(true, model.firstDecisions[1])
    model.moveRight()
    assertEquals(SpacingPlacementOutcome.CORRECT, model.toggleCurrentSpace())
    assertEquals("나는 학교에간다.", model.draft)
    assertEquals(2, model.answeredBoundaryCount)
    model.pause(10_005.0)
    assertEquals(5.0, model.activeDuration(10_100.0), 0.001)
    model.resume(10_100.0)
    while (model.canMoveRight) {
      if (model.currentBoundaryIsSelected != model.engine.isCorrectBoundary(model.currentBoundary)) model.toggleCurrentSpace()
      model.moveRight()
    }
    if (model.currentBoundaryIsSelected != model.engine.isCorrectBoundary(model.currentBoundary)) model.toggleCurrentSpace()
    assertTrue(model.isReadyToFinish)
    assertEquals(8.0, assertNotNull(model.submit(10_103.0)).activeDuration, 0.001)
    assertEquals(GamePhase.FINISHED, model.phase)
    model.restart(10_200.0)
    assertEquals(GamePhase.RUNNING, model.phase)
    assertEquals(model.engine.compactText, model.draft)
    assertNull(model.result)
  }

  @Test fun swiftCompatibleShuffleMatchesIosReference() {
    assertEquals(listOf(0, 6, 1, 4, 5, 7, 8, 2, 9, 3), (0 until 10).toList().swiftShuffled(GameRandom(42u)))
    assertEquals(14396483241276335906uL, GameSeed.forValue("flow_topik_beginner"))
    assertEquals(
      listOf(82, 61, 83, 86, 25, 14, 89, 79, 57, 64, 42, 56),
      (0 until 100).toList().swiftShuffled(GameRandom(GameSeed.forValue("flow_topik_beginner"))).take(12),
    )
  }

  private fun presets(kind: GameKind) = GamePresetRules.load(kind) { path ->
    File(TestSupport.sharedRoot, "mock_catalog/$path").takeIf { it.isFile }?.let { DeckJson.decodeDeck(it.readBytes()) }
  }

  @Test fun everyGameLoadsThreeHundredWordPresets() {
    for (kind in GameKind.entries) {
      val loaded = presets(kind)
      assertEquals(GamePresetLevel.entries, loaded.map { it.level }, kind.raw)
      assertEquals(listOf(1, 2, 3), loaded.map { it.deck.level })
      assertEquals(GamePresetLevel.entries.map { it.deckId(kind) }, loaded.map { it.deck.deckId })
      for (preset in loaded) assertEquals(preset.level, GameResultPresentation.of(kind).presetLevel(preset.deck))
    }
  }

  @Test fun presetContentSupportsEachMechanic() {
    val english: MeaningLookup = { it.localizedMeaning("en") }
    val wordMatch = presets(GameKind.WORD_MATCH).first().deck
    val rounds = WordMatchTypingBuilder.rounds(wordMatch.items, 100, 42u, english)
    assertEquals(100, rounds.size)
    assertTrue(rounds.all { JamoDecomposer.keySequence(it.answer.ko).isNotEmpty() })
    for (p in presets(GameKind.CHOSEONG)) assertEquals(100, ChoseongTypingBuilder.rounds(p.deck.items, 100, 42u, english).size)
    for (p in presets(GameKind.DICTATION)) assertEquals(100, DictationTypingBuilder.rounds(p.deck.items, 100, 42u).size)
  }

  @Test fun presetShuffleChangesOrderWithoutChangingContent() {
    val deck = presets(GameKind.FLOW).first().deck
    val first = GamePresetSessionRandomizer.shuffledItems(deck, GameKind.FLOW, seed = 11u)
    val second = GamePresetSessionRandomizer.shuffledItems(deck, GameKind.FLOW, seed = 29u)
    assertNotEquals(first.map { it.id }, second.map { it.id })
    assertEquals(first.map { it.id }.toSet(), second.map { it.id }.toSet())
    val avoided = GamePresetSessionRandomizer.shuffledItems(deck, GameKind.FLOW, seed = 11u, avoiding = first)
    assertNotEquals(first.map { it.id }, avoided.map { it.id })
  }

  @Test fun piyoCupDeckAndCompetitionRouting() {
    val cup = DeckJson.decodeDeck(File(TestSupport.sharedRoot, "mock_catalog/${GamePresetRules.PIYO_CUP_ASSET_PATH}").readBytes())
    assertTrue(GamePresetRules.isValidPiyoCupDeck(cup))
    val ranked = { id: String, v: Int -> id == "flow_topik_beginner" && v == 3 }
    assertEquals(GameCompetition.WEEKLY_PIYO_CUP, GamePresetRules.resolvedCompetition(GameCompetition.WEEKLY_PIYO_CUP, GameKind.FLOW, cup.deckId, 3, ranked))
    assertEquals(GameCompetition.OFFICIAL_DECK, GamePresetRules.resolvedCompetition(null, GameKind.FLOW, cup.deckId, 3, ranked))
    assertNull(GamePresetRules.resolvedCompetition(null, GameKind.ACID_RAIN, cup.deckId, 3, ranked))
    assertEquals("beginner", GameResultPresentation.PIYO_CUP.analyticsDifficulty(cup))
    assertEquals("piyo_cup.weekly_badge", GameResultPresentation.PIYO_CUP.resultLevelTitleKey(cup))
    assertEquals("acid_rain", GamePresetRules.flowCourse(GameKind.ACID_RAIN, cup))
    assertEquals("bundled", GameAnalytics.deckSource(true, null))
    assertEquals("catalog", GameAnalytics.deckSource(false, "remote"))
    assertEquals("unknown", GameAnalytics.deckSource(false, null))
  }
}
