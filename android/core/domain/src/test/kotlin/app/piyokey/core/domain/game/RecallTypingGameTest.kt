package app.piyokey.core.domain.game

import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.hangul.Korean10KeyInterpretation
import app.piyokey.core.hangul.Korean10KeyInterpreter
import app.piyokey.core.hangul.Korean10KeyKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of the typing parts of iOS `ChoseongQuizViewModelTests` (UI language: English). */
class RecallTypingGameTest {
  private val english: MeaningLookup = { it.localizedMeaning("en") }

  private fun item(id: String, ko: String) = DeckItem(
    id = id,
    ko = ko,
    readingJa = id,
    meaningJa = "meaning-$id",
    audio = null,
    localizations = mapOf(
      "en" to DeckItemLocalization("meaning-$id", id),
      "ko" to DeckItemLocalization("뜻-$id", id),
    ),
  )

  private fun typingModel() = ChoseongTypingEngine(listOf(ChoseongTypingRound(item("school", "학교"), "ㅎㄱ", false)))

  private fun ChoseongTypingEngine.type(keys: String, at: Double): ChoseongTypingInputOutcome? {
    var outcome: ChoseongTypingInputOutcome? = null
    for (key in keys) outcome = input(key, at)
    return outcome
  }

  @Test fun extractorReturnsInitialsAndPreservesWordBoundaries() {
    assertEquals("ㅎㅅ", ChoseongExtractor.extract("회사"))
    assertEquals("ㅎㅂㅎㄱ ㅇㅇㅇ", ChoseongExtractor.extract("행복하게 웃어요!"))
    assertEquals("", ChoseongExtractor.extract("K-POP"))
  }

  @Test fun typingBuilderAcceptsOneDecomposableWordAndIsDeterministic() {
    val items = listOf(item("school", "학교"), item("school-duplicate", "학교"), item("latin", "K-POP"))
    val first = ChoseongTypingBuilder.rounds(items, seed = 91u, meaning = english)
    assertEquals(first, ChoseongTypingBuilder.rounds(items, seed = 91u, meaning = english))
    assertEquals(listOf(ChoseongTypingRound(items[0], "ㅎㄱ", false)), first)
  }

  @Test fun typingBuilderMarksDuplicateInitialsForFreeMeaningDisambiguation() {
    val rounds = ChoseongTypingBuilder.rounds(
      listOf(item("company", "회사"), item("rest", "휴식"), item("school", "학교")),
      seed = 17u,
      meaning = english,
    )
    val byWord = rounds.associateBy { it.answer.ko }
    assertEquals(true, byWord["회사"]?.requiresMeaningHint)
    assertEquals(true, byWord["휴식"]?.requiresMeaningHint)
    assertEquals(false, byWord["학교"]?.requiresMeaningHint)
  }

  @Test fun typingBuilderDoesNotUseLegacyJapaneseBaseForEnglishDisambiguation() {
    val japaneseOnly = DeckItem("company-ja-only", "회사", "フェサ", "会社", null)
    val rounds = ChoseongTypingBuilder.rounds(listOf(japaneseOnly, item("rest", "휴식"), item("music", "음악")), seed = 17u, meaning = english)
    val byId = rounds.associateBy { it.answer.id }
    assertEquals(setOf("rest", "music"), byId.keys)
    assertNull(english(japaneseOnly))
    assertEquals(false, byId["rest"]?.requiresMeaningHint)
    assertEquals(false, byId["music"]?.requiresMeaningHint)
  }

  @Test fun korean10KeyPendingMatchingCompletedFlickCountsOneMistake() {
    val answer = item("ga", "가")
    val model = ChoseongTypingEngine(listOf(ChoseongTypingRound(answer, "ㄱ", false)))
    var interpreter = Korean10KeyInterpreter()
    model.start(4_400.0)
    val step = interpreter.input(Korean10KeyKey.GIYEOK, model.nextExpectedKey)
    interpreter = step.interpreter
    assertEquals(Korean10KeyInterpretation.Committed('ㄱ'), step.interpretation)
    assertEquals(ChoseongTypingInputOutcome.Correct, model.input('ㄱ', 4_401.0))
    interpreter = interpreter.input(Korean10KeyKey.VERTICAL, model.nextExpectedKey).interpreter
    val flick = interpreter.inputCompletedJamo('ㅏ', model.nextExpectedKey)
    interpreter = flick.interpreter
    assertEquals(Korean10KeyInterpretation.Incorrect('ㅏ'), flick.interpretation)
    assertEquals(ChoseongTypingInputOutcome.Incorrect(answer, 'ㅏ', 1), model.recordConfirmedOsImeMistake(4_402.0))
    assertEquals(1, model.mistakeCount)
    assertEquals(1, model.completedJamoCount)
    assertEquals("ㄱ", model.enteredText)
    assertEquals(Korean10KeyKey.VERTICAL, interpreter.nextKey(model.nextExpectedKey))
  }

  @Test fun requiredDisambiguationHintOpensWithoutPenalty() {
    val model = ChoseongTypingEngine(listOf(ChoseongTypingRound(item("company", "회사"), "ㅎㅅ", true)))
    model.start(4_500.0)
    model.useHint(shouldPenalize = false)
    val completion = assertIs<ChoseongTypingInputOutcome.Completed>(model.type("ㅎㅗㅣㅅㅏ", 4_502.0)).completion
    assertFalse(completion.usedHint)
    assertFalse(model.isHintPenaltyApplied)
    assertEquals(140, completion.points)
  }

  @Test fun pronunciationHintConsumesBudgetAndReplayIsFree() {
    val rounds = listOf(
      ChoseongTypingRound(item("school", "학교"), "ㅎㄱ", false),
      ChoseongTypingRound(item("friend", "친구"), "ㅊㄱ", false),
    )
    val model = ChoseongTypingEngine(rounds)
    model.start(4_600.0)
    model.type("ㅎㅏㄱㄱㅛ", 4_601.0)
    assertEquals(1, model.combo)
    model.advance(4_602.0)
    assertEquals(PronunciationHintUse.FirstUse(rounds[1].answer), model.usePronunciationHint())
    assertEquals(2, model.pronunciationHintsRemaining)
    assertEquals(0, model.combo)
    assertTrue(model.isHintPenaltyApplied)
    assertEquals(PronunciationHintUse.Replay(rounds[1].answer), model.usePronunciationHint())
    assertEquals(2, model.pronunciationHintsRemaining)
    val completion = assertIs<ChoseongTypingInputOutcome.Completed>(model.type("ㅊㅣㄴㄱㅜ", 4_603.0)).completion
    assertTrue(completion.usedHint)
    assertEquals(120, completion.points)
  }

  @Test fun pronunciationHintLimitSpansQuestionsAndRestartRestoresIt() {
    val model = ChoseongTypingEngine((0 until 4).map { ChoseongTypingRound(item("ga-$it", "가"), "ㄱ", false) })
    model.start(4_700.0)
    for (index in 0 until 3) {
      assertNotNull(model.usePronunciationHint())
      assertEquals(2 - index, model.pronunciationHintsRemaining)
      val at = 4_700.0 + index + 1
      model.input('ㄱ', at)
      model.input('ㅏ', at)
      model.advance(at)
    }
    assertEquals(4, model.questionNumber)
    assertFalse(model.canUsePronunciationHint)
    assertNull(model.usePronunciationHint())
    model.restart(at = 4_710.0)
    assertEquals(3, model.pronunciationHintsRemaining)
    assertTrue(model.canUsePronunciationHint)
    assertFalse(model.didUsePronunciationHintForCurrentRound)
  }

  @Test fun freeMeaningHintDoesNotErasePronunciationPenalty() {
    val model = ChoseongTypingEngine(listOf(ChoseongTypingRound(item("company", "회사"), "ㅎㅅ", true)))
    model.start(4_800.0)
    assertNotNull(model.usePronunciationHint())
    model.useHint(shouldPenalize = false)
    val completion = assertIs<ChoseongTypingInputOutcome.Completed>(model.type("ㅎㅗㅣㅅㅏ", 4_802.0)).completion
    assertTrue(completion.usedHint)
    assertTrue(model.isHintPenaltyApplied)
    assertEquals(110, completion.points)
  }

  @Test fun initialProgressTracksSyllablesAndWordBoundaries() {
    val pending = ChoseongInitialProgressUnit.State.PENDING
    val active = ChoseongInitialProgressUnit.State.ACTIVE
    val done = ChoseongInitialProgressUnit.State.COMPLETED
    val start = ChoseongInitialProgressBuilder.units("학교 생활", 0)
    assertEquals("ㅎㄱ ㅅㅎ".toList(), start.map { it.character })
    assertEquals(listOf(active, pending, pending, pending, pending), start.map { it.state })
    assertEquals(listOf(done, active, pending, pending, pending), ChoseongInitialProgressBuilder.units("학교 생활", 3).map { it.state })
    assertEquals(listOf(done, done, pending, active, pending), ChoseongInitialProgressBuilder.units("학교 생활", 7).map { it.state })
  }

  @Test fun dictationBuilderIsDeterministicAndKeepsUniqueTypeableKorean() {
    val items = listOf(item("school", "학교"), item("love", "사랑해요"), item("school-duplicate", "학교"), item("latin", "K-POP"))
    val first = DictationTypingBuilder.rounds(items, seed = 2026u)
    assertEquals(first, DictationTypingBuilder.rounds(items, seed = 2026u))
    assertEquals(setOf("학교", "사랑해요"), first.map { it.answer.ko }.toSet())
    assertTrue(first.all { it.initials.isEmpty() && !it.requiresMeaningHint })
  }

  @Test fun dictationBuilderHonorsLimitAndSwiftShuffleOrder() {
    val items = listOf(item("one", "하나"), item("two", "둘"), item("three", "셋"))
    val rounds = DictationTypingBuilder.rounds(items, limit = 2, seed = 7u)
    assertEquals(2, rounds.size)
    // Swift `[0,1,2].shuffle(using: G(seed: 7 ^ 0xD1C7A710))` == [0, 2, 1].
    assertEquals(listOf("one", "three"), rounds.map { it.answer.id })
  }

  @Test fun typingCompletesAfterFullSequenceAndAwardsCombo() {
    val model = typingModel()
    model.start(5_000.0)
    for (key in "ㅎㅏㄱㄱ") {
      assertNotNull(model.input(key, 5_001.0))
      assertEquals(0, model.completedItemCount)
    }
    val completion = assertIs<ChoseongTypingInputOutcome.Completed>(model.input('ㅛ', 5_002.0)).completion
    assertEquals("학교", completion.answer.ko)
    assertEquals(140, completion.points)
    assertEquals("학교", model.enteredText)
    assertEquals(1, model.completedItemCount)
    assertEquals(1, model.combo)
    assertEquals(1, model.maxCombo)
    assertEquals(100.0, model.accuracyPercent, 0.001)
  }

  @Test fun typingMistakeIsRejectedWithoutPollutingComposition() {
    val model = typingModel()
    model.start(6_000.0)
    val mistake = assertIs<ChoseongTypingInputOutcome.Incorrect>(model.input('ㄱ', 6_001.0))
    assertEquals("학교", mistake.answer.ko)
    assertEquals('ㅎ', mistake.expected)
    assertEquals(0, mistake.jamoIndex)
    assertEquals("", model.enteredText)
    model.type("ㅎㅏㄱㄱㅛ", 6_002.0)
    assertEquals("학교", model.enteredText)
    assertEquals(1, model.imperfectItemCount)
    assertEquals(0, model.combo)
    assertEquals(5.0 / 6.0 * 100, model.accuracyPercent, 0.001)
  }

  @Test fun typingBackspaceRewindsOneJamo() {
    val model = typingModel()
    model.start(7_000.0)
    model.input('ㅎ', 7_000.0)
    assertEquals("ㅎ", model.composingPreview)
    assertEquals('ㅎ', model.lastAcceptedKey)
    assertFalse(model.shouldAnimateSyllableJoin)
    model.input('ㅏ', 7_000.0)
    assertEquals("하", model.enteredText)
    assertTrue(model.shouldAnimateSyllableJoin)
    model.backspace(7_000.0)
    assertEquals("ㅎ", model.enteredText)
    assertFalse(model.shouldAnimateSyllableJoin)
    assertEquals(1, model.completedJamoCount)
    assertEquals('ㅏ', model.nextExpectedKey)
    model.type("ㅏㄱㄱㅛ", 7_000.0)
    assertEquals("학교", model.enteredText)
    assertEquals(1, model.completedItemCount)
  }

  @Test fun typingOsImeSynchronizesPrefixAndCompletion() {
    val model = typingModel()
    model.start(8_000.0)
    assertEquals(3, model.synchronizeOsIme("ㅎㅏㄱ".toList(), 8_001.0).size)
    assertEquals("학", model.enteredText)
    val completion = model.synchronizeOsIme("ㅎㅏㄱㄱㅛ".toList(), 8_002.0)
    assertEquals(2, completion.size)
    assertIs<ChoseongTypingInputOutcome.Completed>(completion.last())
    assertEquals(1, model.completedItemCount)
  }

  @Test fun typingPauseExcludesBackgroundTimeAndFinalAdvanceFinishes() {
    val model = typingModel()
    model.start(9_000.0)
    model.pause(9_002.0)
    model.resume(9_102.0)
    model.type("ㅎㅏㄱㄱㅛ", 9_105.0)
    assertEquals(5.0, model.result.activeDuration, 0.001)
    assertEquals(12.0, model.questionsPerMinute, 0.001)
    model.advance(9_106.0)
    assertEquals(GamePhase.FINISHED, model.phase)
    val finished = model.result
    assertNull(model.input('ㅎ', 9_200.0))
    assertTrue(model.synchronizeOsIme("ㅎㅏ".toList(), 9_200.0).isEmpty())
    model.advance(9_200.0)
    model.resume(9_200.0)
    assertEquals(finished, model.result)
    model.restart(at = 9_107.0)
    assertEquals(GamePhase.RUNNING, model.phase)
    assertEquals(1, model.questionNumber)
    assertEquals(0, model.score)
    assertEquals("", model.enteredText)
  }

  @Test fun typingRestartWithReplacementRoundsKeepsPromptAndJudgeAligned() {
    val model = typingModel()
    val replacement = ChoseongTypingRound(item("music", "음악"), "ㅇㅇ", false)
    model.restart(listOf(replacement), 9_500.0)
    assertEquals(replacement, model.currentRound)
    assertEquals('ㅇ', model.nextExpectedKey)
    val stale = assertIs<ChoseongTypingInputOutcome.Incorrect>(model.input('ㅎ', 9_501.0))
    assertEquals("음악", stale.answer.ko)
    val done = assertIs<ChoseongTypingInputOutcome.Completed>(model.type("ㅇㅡㅁㅇㅏㄱ", 9_502.0)).completion
    assertEquals("music", done.answer.id)
  }

  @Test fun prepareRestartClearsFinishedRunBeforeCountdown() {
    val model = typingModel()
    model.start(9_700.0)
    model.type("ㅎㅏㄱㄱㅛ", 9_702.0)
    model.advance(9_703.0)
    assertEquals(GamePhase.FINISHED, model.phase)
    val replacement = ChoseongTypingRound(item("music", "음악"), "ㅇㅇ", false)
    model.prepareRestart(listOf(replacement))
    assertEquals(GamePhase.READY, model.phase)
    assertEquals(replacement, model.currentRound)
    assertEquals(0, model.score)
    assertEquals(0, model.mistakeCount)
    assertEquals("", model.enteredText)
    assertNull(model.input('ㅎ', 9_704.0))
    model.start(9_705.0)
    assertIs<ChoseongTypingInputOutcome.Completed>(model.type("ㅇㅡㅁㅇㅏㄱ", 9_706.0))
  }

  @Test fun wordMatchTypingBuilderExcludesLegacyJapaneseBaseForEnglish() {
    val items = listOf(
      item("school", "학교"), item("friend", "친구"), item("love", "사랑"),
      item("school-duplicate", "학교"), item("latin", "K-POP"),
      DeckItem("japanese-only", "음악", "ウマク", "音楽", null),
    )
    val first = WordMatchTypingBuilder.rounds(items, 5, 77u, english)
    assertEquals(first, WordMatchTypingBuilder.rounds(items, 5, 77u, english))
    assertEquals(setOf("학교", "친구", "사랑"), first.map { it.answer.ko }.toSet())
    assertTrue(first.all { it.initials.isEmpty() && !it.requiresMeaningHint })
    assertEquals(1, WordMatchTypingBuilder.rounds(listOf(item("only", "하나")), seed = 1u, meaning = english).size)
  }

  @Test fun scoreFormulaAndRank() {
    assertEquals(150, ChoseongTypingEngine.score(0.0, 0, false))
    assertEquals(70, ChoseongTypingEngine.score(100.0, 0, true))
    assertEquals(250, ChoseongTypingEngine.score(0.0, 15, false))
    assertEquals("S", ChoseongTypingEngine.accuracyRank(95.0))
    assertEquals("A", ChoseongTypingEngine.accuracyRank(80.0))
    assertEquals("B", ChoseongTypingEngine.accuracyRank(60.0))
    assertEquals("C", ChoseongTypingEngine.accuracyRank(59.9))
  }

  @Test fun japaneseMeaningDisplayStripsBrackets() {
    assertEquals("会社", JapaneseMeaningDisplayText.format(" 「会社」 "))
    assertEquals("本", JapaneseMeaningDisplayText.format("『本』"))
    assertEquals("", JapaneseMeaningDisplayText.format("  "))
  }
}
