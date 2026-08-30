import DeckKit
import XCTest

@testable import Hanco

@MainActor
final class ChoseongQuizViewModelTests: XCTestCase {
  private var originalLanguage: String?

  override func setUp() {
    super.setUp()
    originalLanguage = UserDefaults.standard.string(forKey: SettingsPreferenceKeys.language)
    UserDefaults.standard.set(
      AppLanguage.english.rawValue,
      forKey: SettingsPreferenceKeys.language
    )
  }

  override func tearDown() {
    if let originalLanguage {
      UserDefaults.standard.set(originalLanguage, forKey: SettingsPreferenceKeys.language)
    } else {
      UserDefaults.standard.removeObject(forKey: SettingsPreferenceKeys.language)
    }
    super.tearDown()
  }

  func testExtractorReturnsInitialsAndPreservesWordBoundaries() {
    XCTAssertEqual(ChoseongExtractor.extract(from: "회사"), "ㅎㅅ")
    XCTAssertEqual(ChoseongExtractor.extract(from: "행복하게 웃어요!"), "ㅎㅂㅎㄱ ㅇㅇㅇ")
    XCTAssertEqual(ChoseongExtractor.extract(from: "K-POP"), "")
  }

  func testBuilderIsDeterministicAndCreatesUnambiguousFourChoiceRounds() {
    let items = [
      item("school", "학교"), item("friend", "친구"), item("love", "사랑"),
      item("music", "음악"), item("korea", "한국"), item("travel", "여행"),
      item("cheer", "응원"), item("morning", "아침"), item("food", "맛집"),
      item("drama", "드라마"), item("company", "회사"), item("study", "공부"),
    ]

    let first = ChoseongQuizBuilder.rounds(items: items, limit: 10, seed: 42)
    let second = ChoseongQuizBuilder.rounds(items: items, limit: 10, seed: 42)

    XCTAssertEqual(first, second)
    XCTAssertEqual(first.count, 10)
    for round in first {
      XCTAssertEqual(round.options.count, 4)
      XCTAssertEqual(Set(round.options.map(\.id)).count, 4)
      XCTAssertTrue(round.options.contains(round.answer))
      XCTAssertTrue(
        round.options.filter { $0.id != round.answer.id }.allSatisfy {
          ChoseongExtractor.extract(from: $0.ko) != round.initials
        }
      )
    }
  }

  func testBuilderRejectsDeckWithoutFourUnambiguousChoices() {
    let tooSmall = [item("one", "학교"), item("two", "친구"), item("three", "사랑")]
    let sameInitials = [
      item("one", "가구"), item("two", "고기"), item("three", "구경"), item("four", "기계"),
    ]

    XCTAssertTrue(ChoseongQuizBuilder.rounds(items: tooSmall, seed: 1).isEmpty)
    XCTAssertTrue(ChoseongQuizBuilder.rounds(items: sameInitials, seed: 1).isEmpty)
  }

  func testTypingBuilderAcceptsOneDecomposableWordAndIsDeterministic() {
    let items = [
      item("school", "학교"),
      item("school-duplicate", "학교"),
      item("latin", "K-POP"),
    ]

    let first = ChoseongTypingBuilder.rounds(items: items, seed: 91)
    let second = ChoseongTypingBuilder.rounds(items: items, seed: 91)

    XCTAssertEqual(first, second)
    XCTAssertEqual(
      first,
      [
        ChoseongTypingRound(
          answer: items[0],
          initials: "ㅎㄱ",
          requiresMeaningHint: false
        )
      ]
    )
  }

  func testTypingBuilderMarksDuplicateInitialsForFreeMeaningDisambiguation() {
    let rounds = ChoseongTypingBuilder.rounds(
      items: [
        item("company", "회사"),
        item("rest", "휴식"),
        item("school", "학교"),
      ],
      seed: 17
    )

    let byWord = Dictionary(uniqueKeysWithValues: rounds.map { ($0.answer.ko, $0) })
    XCTAssertEqual(byWord["회사"]?.requiresMeaningHint, true)
    XCTAssertEqual(byWord["휴식"]?.requiresMeaningHint, true)
    XCTAssertEqual(byWord["학교"]?.requiresMeaningHint, false)
  }

  func testTypingBuilderUsesLegacyJapaneseBaseWhenLocalizationIsMissing() {
    let japaneseOnly = DeckItem(
      id: "company-ja-only",
      ko: "회사",
      readingJa: "フェサ",
      meaningJa: "会社",
      audio: nil
    )
    let rounds = ChoseongTypingBuilder.rounds(
      items: [japaneseOnly, item("rest", "휴식"), item("music", "음악")],
      seed: 17
    )

    let byID = Dictionary(uniqueKeysWithValues: rounds.map { ($0.answer.id, $0) })
    XCTAssertEqual(Set(byID.keys), Set(["company-ja-only", "rest", "music"]))
    XCTAssertEqual(byID["company-ja-only"]?.answer.appMeaning, "会社")
    XCTAssertEqual(byID["company-ja-only"]?.requiresMeaningHint, true)
    XCTAssertEqual(byID["rest"]?.requiresMeaningHint, true)
    XCTAssertEqual(byID["music"]?.requiresMeaningHint, false)
  }

  func testRequiredDisambiguationHintCanOpenWithoutScorePenalty() {
    let answer = item("company", "회사")
    let model = ChoseongTypingViewModel(
      rounds: [
        ChoseongTypingRound(answer: answer, initials: "ㅎㅅ", requiresMeaningHint: true)
      ]
    )
    let origin = Date(timeIntervalSince1970: 4_500)
    model.start(at: origin)
    model.useHint(shouldPenalize: false)

    var outcome: ChoseongTypingInputOutcome?
    for key in Array("ㅎㅗㅣㅅㅏ") {
      outcome = model.input(key, at: origin.addingTimeInterval(2))
    }

    guard case .completed(let completion) = outcome else {
      return XCTFail("The free disambiguation hint must still allow completion")
    }
    XCTAssertFalse(completion.usedHint)
    XCTAssertFalse(model.isHintPenaltyApplied)
    XCTAssertEqual(completion.points, 140)
  }

  func testPronunciationHintConsumesOneQuestionBudgetAndReplayIsFree() {
    let rounds = [
      ChoseongTypingRound(
        answer: item("school", "학교"),
        initials: "ㅎㄱ",
        requiresMeaningHint: false
      ),
      ChoseongTypingRound(
        answer: item("friend", "친구"),
        initials: "ㅊㄱ",
        requiresMeaningHint: false
      ),
    ]
    let model = ChoseongTypingViewModel(rounds: rounds)
    let origin = Date(timeIntervalSince1970: 4_600)
    model.start(at: origin)
    for key in Array("ㅎㅏㄱㄱㅛ") {
      _ = model.input(key, at: origin.addingTimeInterval(1))
    }
    XCTAssertEqual(model.combo, 1)
    model.advance(at: origin.addingTimeInterval(2))

    XCTAssertEqual(
      model.usePronunciationHint(),
      .firstUse(answer: rounds[1].answer)
    )
    XCTAssertEqual(model.pronunciationHintsRemaining, 2)
    XCTAssertEqual(model.combo, 0)
    XCTAssertTrue(model.isHintPenaltyApplied)
    XCTAssertEqual(
      model.usePronunciationHint(),
      .replay(answer: rounds[1].answer)
    )
    XCTAssertEqual(model.pronunciationHintsRemaining, 2)

    var outcome: ChoseongTypingInputOutcome?
    for key in Array("ㅊㅣㄴㄱㅜ") {
      outcome = model.input(key, at: origin.addingTimeInterval(3))
    }
    guard case .completed(let completion) = outcome else {
      return XCTFail("The hinted question must remain completable")
    }
    XCTAssertTrue(completion.usedHint)
    XCTAssertEqual(completion.points, 120)
  }

  func testPronunciationHintLimitSpansQuestionsAndRestartRestoresIt() {
    let rounds = (0..<4).map { index in
      ChoseongTypingRound(
        answer: item("ga-\(index)", "가"),
        initials: "ㄱ",
        requiresMeaningHint: false
      )
    }
    let model = ChoseongTypingViewModel(rounds: rounds)
    let origin = Date(timeIntervalSince1970: 4_700)
    model.start(at: origin)

    for index in 0..<3 {
      XCTAssertNotNil(model.usePronunciationHint())
      XCTAssertEqual(model.pronunciationHintsRemaining, 2 - index)
      _ = model.input("ㄱ", at: origin.addingTimeInterval(Double(index + 1)))
      _ = model.input("ㅏ", at: origin.addingTimeInterval(Double(index + 1)))
      model.advance(at: origin.addingTimeInterval(Double(index + 1)))
    }

    XCTAssertEqual(model.questionNumber, 4)
    XCTAssertFalse(model.canUsePronunciationHint)
    XCTAssertNil(model.usePronunciationHint())

    model.restart(at: origin.addingTimeInterval(10))
    XCTAssertEqual(model.pronunciationHintsRemaining, 3)
    XCTAssertTrue(model.canUsePronunciationHint)
    XCTAssertFalse(model.didUsePronunciationHintForCurrentRound)
  }

  func testFreeMeaningHintDoesNotErasePronunciationPenalty() {
    let answer = item("company", "회사")
    let model = ChoseongTypingViewModel(
      rounds: [
        ChoseongTypingRound(answer: answer, initials: "ㅎㅅ", requiresMeaningHint: true)
      ]
    )
    let origin = Date(timeIntervalSince1970: 4_800)
    model.start(at: origin)
    XCTAssertNotNil(model.usePronunciationHint())
    model.useHint(shouldPenalize: false)

    var outcome: ChoseongTypingInputOutcome?
    for key in Array("ㅎㅗㅣㅅㅏ") {
      outcome = model.input(key, at: origin.addingTimeInterval(2))
    }
    guard case .completed(let completion) = outcome else {
      return XCTFail("Both hints must still allow completion")
    }
    XCTAssertTrue(completion.usedHint)
    XCTAssertTrue(model.isHintPenaltyApplied)
    XCTAssertEqual(completion.points, 110)
  }

  func testInitialProgressTracksEachSyllableAndPreservesWordBoundaries() {
    let start = ChoseongInitialProgressBuilder.units(
      answer: "학교 생활",
      completedJamoCount: 0
    )
    XCTAssertEqual(start.map(\.character), Array("ㅎㄱ ㅅㅎ"))
    XCTAssertEqual(start.map(\.state), [.active, .pending, .pending, .pending, .pending])

    let firstSyllableComplete = ChoseongInitialProgressBuilder.units(
      answer: "학교 생활",
      completedJamoCount: 3
    )
    XCTAssertEqual(
      firstSyllableComplete.map(\.state),
      [.completed, .active, .pending, .pending, .pending]
    )

    let secondWordStarted = ChoseongInitialProgressBuilder.units(
      answer: "학교 생활",
      completedJamoCount: 7
    )
    XCTAssertEqual(
      secondWordStarted.map(\.state),
      [.completed, .completed, .pending, .active, .pending]
    )
  }

  func testDictationBuilderIsDeterministicAndKeepsOnlyUniqueTypeableKorean() {
    let items = [
      item("school", "학교"),
      item("love", "사랑해요"),
      item("school-duplicate", "학교"),
      item("latin", "K-POP"),
    ]

    let first = DictationTypingBuilder.rounds(items: items, seed: 2026)
    let second = DictationTypingBuilder.rounds(items: items, seed: 2026)

    XCTAssertEqual(first, second)
    XCTAssertEqual(Set(first.map(\.answer.ko)), Set(["학교", "사랑해요"]))
    XCTAssertTrue(first.allSatisfy { $0.initials.isEmpty && !$0.requiresMeaningHint })
  }

  func testDictationBuilderHonorsQuestionLimit() {
    let rounds = DictationTypingBuilder.rounds(
      items: [item("one", "하나"), item("two", "둘"), item("three", "셋")],
      limit: 2,
      seed: 7
    )

    XCTAssertEqual(rounds.count, 2)
  }

  func testTypingCompletesOnlyAfterFullJamoSequenceAndAwardsCombo() {
    let model = typingModel()
    let origin = Date(timeIntervalSince1970: 5_000)
    model.start(at: origin)

    for key in Array("ㅎㅏㄱㄱ") {
      XCTAssertNotNil(model.input(key, at: origin.addingTimeInterval(1)))
      XCTAssertEqual(model.completedItemCount, 0)
    }
    let outcome = model.input("ㅛ", at: origin.addingTimeInterval(2))

    guard case .completed(let completion) = outcome else {
      return XCTFail("The final jamo must complete the typed answer")
    }
    XCTAssertEqual(completion.answer.ko, "학교")
    XCTAssertEqual(completion.points, 140)
    XCTAssertEqual(model.enteredText, "학교")
    XCTAssertEqual(model.completedItemCount, 1)
    XCTAssertEqual(model.combo, 1)
    XCTAssertEqual(model.maxCombo, 1)
    XCTAssertEqual(model.accuracyPercent, 100, accuracy: 0.001)
  }

  func testTypingMistakeIsRejectedWithoutPollutingComposition() {
    let model = typingModel()
    let origin = Date(timeIntervalSince1970: 6_000)
    model.start(at: origin)

    let mistake = model.input("ㄱ", at: origin.addingTimeInterval(1))
    guard case .incorrect(let answer, let expected, let index) = mistake else {
      return XCTFail("An incorrect initial must be rejected")
    }
    XCTAssertEqual(answer.ko, "학교")
    XCTAssertEqual(expected, "ㅎ")
    XCTAssertEqual(index, 0)
    XCTAssertEqual(model.enteredText, "")
    XCTAssertEqual(model.completedJamoCount, 0)

    for key in Array("ㅎㅏㄱㄱㅛ") {
      _ = model.input(key, at: origin.addingTimeInterval(2))
    }
    XCTAssertEqual(model.enteredText, "학교")
    XCTAssertEqual(model.imperfectItemCount, 1)
    XCTAssertEqual(model.combo, 0)
    XCTAssertEqual(model.accuracyPercent, 5.0 / 6.0 * 100, accuracy: 0.001)
  }

  func testTypingBackspaceRewindsOneJamoAndRebuildsJudge() {
    let model = typingModel()
    let origin = Date(timeIntervalSince1970: 7_000)
    model.start(at: origin)
    _ = model.input("ㅎ", at: origin)
    XCTAssertEqual(model.composingPreview, "ㅎ")
    XCTAssertEqual(model.lastAcceptedKey, "ㅎ")
    XCTAssertFalse(model.shouldAnimateSyllableJoin)
    _ = model.input("ㅏ", at: origin)
    XCTAssertEqual(model.enteredText, "하")
    XCTAssertEqual(model.composingPreview, "하")
    XCTAssertTrue(model.shouldAnimateSyllableJoin)
    XCTAssertEqual(model.completedJamoCount, 2)

    model.backspace(at: origin)

    XCTAssertEqual(model.enteredText, "ㅎ")
    XCTAssertEqual(model.composingPreview, "ㅎ")
    XCTAssertFalse(model.shouldAnimateSyllableJoin)
    XCTAssertEqual(model.completedJamoCount, 1)
    XCTAssertEqual(model.nextExpectedKey, "ㅏ")
    for key in Array("ㅏㄱㄱㅛ") {
      _ = model.input(key, at: origin)
    }
    XCTAssertEqual(model.enteredText, "학교")
    XCTAssertEqual(model.completedItemCount, 1)
  }

  func testTypingOSIMESynchronizesAcceptedPrefixAndCompletion() {
    let model = typingModel()
    let origin = Date(timeIntervalSince1970: 8_000)
    model.start(at: origin)

    let prefix = model.synchronizeOSIME(
      acceptedSequence: Array("ㅎㅏㄱ"),
      at: origin.addingTimeInterval(1)
    )
    XCTAssertEqual(prefix.count, 3)
    XCTAssertEqual(model.enteredText, "학")

    let completion = model.synchronizeOSIME(
      acceptedSequence: Array("ㅎㅏㄱㄱㅛ"),
      at: origin.addingTimeInterval(2)
    )
    XCTAssertEqual(completion.count, 2)
    guard case .completed = completion.last else {
      return XCTFail("OS IME accepted sequence must complete the answer")
    }
    XCTAssertEqual(model.completedItemCount, 1)
  }

  func testTypingPauseExcludesBackgroundTimeAndFinalAdvanceFinishes() {
    let model = typingModel()
    let origin = Date(timeIntervalSince1970: 9_000)
    model.start(at: origin)
    model.pause(at: origin.addingTimeInterval(2))
    model.resume(at: origin.addingTimeInterval(102))
    for key in Array("ㅎㅏㄱㄱㅛ") {
      _ = model.input(key, at: origin.addingTimeInterval(105))
    }

    XCTAssertEqual(model.result.activeDuration, 5, accuracy: 0.001)
    XCTAssertEqual(model.questionsPerMinute, 12, accuracy: 0.001)
    model.advance(at: origin.addingTimeInterval(106))
    XCTAssertEqual(model.phase, .finished)
    let finishedResult = model.result

    XCTAssertNil(model.input("ㅎ", at: origin.addingTimeInterval(200)))
    XCTAssertTrue(
      model.synchronizeOSIME(
        acceptedSequence: Array("ㅎㅏ"),
        at: origin.addingTimeInterval(200)
      ).isEmpty
    )
    model.advance(at: origin.addingTimeInterval(200))
    model.resume(at: origin.addingTimeInterval(200))
    XCTAssertEqual(model.phase, .finished)
    XCTAssertEqual(model.result, finishedResult)

    model.restart(at: origin.addingTimeInterval(107))
    XCTAssertEqual(model.phase, .running)
    XCTAssertEqual(model.questionNumber, 1)
    XCTAssertEqual(model.score, 0)
    XCTAssertEqual(model.enteredText, "")
  }

  func testTypingRestartWithReplacementRoundsKeepsPromptAndJudgeAligned() {
    let origin = Date(timeIntervalSince1970: 9_500)
    let model = typingModel()
    let replacement = ChoseongTypingRound(
      answer: item("music", "음악"),
      initials: "ㅇㅇ",
      requiresMeaningHint: false
    )

    model.restart(rounds: [replacement], at: origin)

    XCTAssertEqual(model.currentRound, replacement)
    XCTAssertEqual(model.nextExpectedKey, "ㅇ")
    let staleAnswerInput = model.input("ㅎ", at: origin.addingTimeInterval(1))
    guard case .incorrect(let answer, let expected, let index) = staleAnswerInput else {
      return XCTFail("The replacement round must reject input for the previous visible answer")
    }
    XCTAssertEqual(answer.ko, "음악")
    XCTAssertEqual(expected, "ㅇ")
    XCTAssertEqual(index, 0)
    XCTAssertEqual(model.enteredText, "")

    var completion: ChoseongTypingInputOutcome?
    for key in Array("ㅇㅡㅁㅇㅏㄱ") {
      completion = model.input(key, at: origin.addingTimeInterval(2))
    }
    guard case .completed(let result) = completion else {
      return XCTFail("The displayed replacement answer must also be the judged answer")
    }
    XCTAssertEqual(result.answer.id, "music")
    XCTAssertEqual(model.enteredText, "음악")
  }

  func testTypingPrepareRestartClearsFinishedRunBeforeCountdownAndStartsReplacement() {
    let origin = Date(timeIntervalSince1970: 9_700)
    let model = typingModel()
    model.start(at: origin)
    for key in Array("ㅎㅏㄱㄱㅛ") {
      _ = model.input(key, at: origin.addingTimeInterval(2))
    }
    model.advance(at: origin.addingTimeInterval(3))
    XCTAssertEqual(model.phase, .finished)
    XCTAssertEqual(model.enteredText, "학교")

    let replacement = ChoseongTypingRound(
      answer: item("music", "음악"),
      initials: "ㅇㅇ",
      requiresMeaningHint: false
    )
    model.prepareRestart(rounds: [replacement])

    XCTAssertEqual(model.phase, .ready)
    XCTAssertEqual(model.currentRound, replacement)
    XCTAssertEqual(model.questionNumber, 1)
    XCTAssertEqual(model.score, 0)
    XCTAssertEqual(model.combo, 0)
    XCTAssertEqual(model.maxCombo, 0)
    XCTAssertEqual(model.mistakeCount, 0)
    XCTAssertEqual(model.completedItemCount, 0)
    XCTAssertEqual(model.enteredText, "")
    XCTAssertEqual(model.nextExpectedKey, "ㅇ")
    XCTAssertNil(model.input("ㅎ", at: origin.addingTimeInterval(4)))

    model.start(at: origin.addingTimeInterval(5))
    var completion: ChoseongTypingInputOutcome?
    for key in Array("ㅇㅡㅁㅇㅏㄱ") {
      completion = model.input(key, at: origin.addingTimeInterval(6))
    }
    guard case .completed(let result) = completion else {
      return XCTFail("The replacement first round must start after the countdown")
    }
    XCTAssertEqual(result.answer.id, "music")
  }

  func testWordMatchTypingBuilderIncludesLegacyJapaneseBaseFallback() {
    let items = [
      item("school", "학교"), item("friend", "친구"), item("love", "사랑"),
      item("school-duplicate", "학교"), item("latin", "K-POP"),
      DeckItem(
        id: "japanese-only",
        ko: "음악",
        readingJa: "ウマク",
        meaningJa: "音楽",
        audio: nil
      ),
    ]

    let first = WordMatchTypingBuilder.rounds(items: items, limit: 5, seed: 77)
    let second = WordMatchTypingBuilder.rounds(items: items, limit: 5, seed: 77)

    XCTAssertEqual(first, second)
    XCTAssertEqual(Set(first.map(\.answer.ko)), Set(["학교", "친구", "사랑", "음악"]))
    XCTAssertEqual(first.first { $0.answer.id == "japanese-only" }?.answer.appMeaning, "音楽")
    XCTAssertTrue(first.allSatisfy { $0.initials.isEmpty && !$0.requiresMeaningHint })
    XCTAssertEqual(
      WordMatchTypingBuilder.rounds(items: [item("only", "하나")], seed: 1).count,
      1
    )
  }

  func testWordMatchChoiceBuilderUsesLocalizedThenLegacyBaseMeaningAsPrompt() {
    let items = [
      item("school", "학교"), item("friend", "친구"),
      item("love", "사랑"), item("music", "음악"),
      DeckItem(
        id: "japanese-only",
        ko: "여행",
        readingJa: "ヨヘン",
        meaningJa: "旅行",
        audio: nil
      ),
    ]

    let rounds = WordMatchQuizBuilder.rounds(items: items, seed: 88)

    XCTAssertEqual(
      Set(rounds.map(\.answer.id)),
      Set(["school", "friend", "love", "music", "japanese-only"])
    )
    XCTAssertTrue(rounds.allSatisfy { $0.initials == ($0.answer.appMeaning ?? "") })
    XCTAssertEqual(rounds.first { $0.answer.id == "japanese-only" }?.initials, "旅行")
  }

  func testCorrectAnswersAwardSpeedComboAndHintAdjustedPoints() {
    let model = ChoseongQuizViewModel(rounds: rounds())
    let origin = Date(timeIntervalSince1970: 1_000)
    model.start(at: origin)
    model.useHint()

    let first = model.select(optionID: "school", at: origin.addingTimeInterval(2))
    XCTAssertEqual(first?.isCorrect, true)
    XCTAssertEqual(first?.usedHint, true)
    XCTAssertEqual(first?.points, 110)
    XCTAssertEqual(model.score, 110)
    XCTAssertEqual(model.combo, 1)

    model.advance(at: origin.addingTimeInterval(3))
    let second = model.select(optionID: "friend", at: origin.addingTimeInterval(4))
    XCTAssertEqual(second?.points, 160)
    XCTAssertEqual(model.score, 270)
    XCTAssertEqual(model.combo, 2)
    XCTAssertEqual(model.maxCombo, 2)
  }

  func testWrongAnswerResetsComboAndIsIgnoredAfterFeedback() {
    let model = ChoseongQuizViewModel(rounds: rounds())
    let origin = Date(timeIntervalSince1970: 2_000)
    model.start(at: origin)
    _ = model.select(optionID: "school", at: origin.addingTimeInterval(1))
    model.advance(at: origin.addingTimeInterval(2))

    let wrong = model.select(optionID: "music", at: origin.addingTimeInterval(3))
    XCTAssertEqual(wrong?.isCorrect, false)
    XCTAssertEqual(model.combo, 0)
    XCTAssertEqual(model.incorrectCount, 1)
    XCTAssertEqual(model.accuracyPercent, 50, accuracy: 0.001)
    XCTAssertNil(model.select(optionID: "friend", at: origin.addingTimeInterval(4)))
  }

  func testPausedTimeIsExcludedFromQuestionRate() {
    let model = ChoseongQuizViewModel(rounds: rounds())
    let origin = Date(timeIntervalSince1970: 3_000)
    model.start(at: origin)
    model.pause(at: origin.addingTimeInterval(5))
    model.resume(at: origin.addingTimeInterval(105))
    _ = model.select(optionID: "school", at: origin.addingTimeInterval(110))

    XCTAssertEqual(model.result.activeDuration, 10, accuracy: 0.001)
    XCTAssertEqual(model.questionsPerMinute, 6, accuracy: 0.001)
  }

  func testFinalAdvanceFinishesAndRestartClearsRun() {
    let model = ChoseongQuizViewModel(rounds: rounds())
    let origin = Date(timeIntervalSince1970: 4_000)
    model.start(at: origin)
    _ = model.select(optionID: "school", at: origin.addingTimeInterval(1))
    model.advance(at: origin.addingTimeInterval(2))
    _ = model.select(optionID: "friend", at: origin.addingTimeInterval(3))
    model.advance(at: origin.addingTimeInterval(4))

    XCTAssertEqual(model.phase, .finished)
    XCTAssertEqual(model.result.rank, "S")

    model.restart(at: origin.addingTimeInterval(5))
    XCTAssertEqual(model.phase, .running)
    XCTAssertEqual(model.questionNumber, 1)
    XCTAssertEqual(model.score, 0)
    XCTAssertEqual(model.correctCount, 0)
    XCTAssertNil(model.feedback)
  }

  func testChoiceRestartWithReplacementRoundsUsesReplacementAnswer() {
    let origin = Date(timeIntervalSince1970: 4_100)
    let model = ChoseongQuizViewModel(rounds: rounds())
    let school = item("school-new", "학교")
    let music = item("music-new", "음악")
    let replacement = ChoseongQuizRound(
      answer: music,
      initials: "ㅇㅇ",
      options: [school, music]
    )

    model.restart(rounds: [replacement], at: origin)

    XCTAssertEqual(model.currentRound, replacement)
    let staleAnswerSelection = model.select(
      optionID: school.id,
      at: origin.addingTimeInterval(1)
    )
    XCTAssertEqual(staleAnswerSelection?.isCorrect, false)
    XCTAssertEqual(staleAnswerSelection?.answer.id, music.id)
  }

  private func rounds() -> [ChoseongQuizRound] {
    let school = item("school", "학교")
    let friend = item("friend", "친구")
    let love = item("love", "사랑")
    let music = item("music", "음악")
    return [
      ChoseongQuizRound(
        answer: school,
        initials: "ㅎㄱ",
        options: [school, friend, love, music]
      ),
      ChoseongQuizRound(
        answer: friend,
        initials: "ㅊㄱ",
        options: [music, friend, school, love]
      ),
    ]
  }

  private func typingModel() -> ChoseongTypingViewModel {
    ChoseongTypingViewModel(
      rounds: [
        ChoseongTypingRound(
          answer: item("school", "학교"),
          initials: "ㅎㄱ",
          requiresMeaningHint: false
        )
      ]
    )
  }

  private func item(_ id: String, _ ko: String) -> DeckItem {
    DeckItem(
      id: id,
      ko: ko,
      readingJa: id,
      meaningJa: "meaning-\(id)",
      audio: nil,
      localizations: [
        "en": DeckItemLocalization(meaning: "meaning-\(id)", reading: id),
        "ko": DeckItemLocalization(meaning: "뜻-\(id)", reading: id),
      ]
    )
  }
}
