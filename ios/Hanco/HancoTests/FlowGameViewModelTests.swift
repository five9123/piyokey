import HangulEngine
import XCTest

@testable import Hanco

@MainActor
final class FlowGameViewModelTests: XCTestCase {
  func testCompactHUDUsesSmallerTypeAsMetricValuesGrow() {
    XCTAssertEqual(CompactGameHUDMetricLayout.valuePointSize(for: "67"), 15)
    XCTAssertEqual(CompactGameHUDMetricLayout.valuePointSize(for: "1,496"), 14)
    XCTAssertEqual(CompactGameHUDMetricLayout.valuePointSize(for: "99,999"), 12)
    XCTAssertEqual(CompactGameHUDMetricLayout.valuePointSize(for: "1,000,000"), 10)
    XCTAssertGreaterThanOrEqual(
      CompactGameHUDMetricLayout.valueHorizontalInset,
      CompactGameHUDMetricLayout.iconColumnWidth
    )
  }

  func testBundledFlowPresetDecksLoadInHybridDifficultyOrder() throws {
    let presets = GamePresetDeckLoader.load(gameKind: .flow)

    XCTAssertEqual(presets.map(\.level), [.beginner, .intermediate, .advanced])
    XCTAssertEqual(presets.map(\.deck.level), [1, 2, 3])
    XCTAssertEqual(presets.map(\.deck.version), [3, 3, 3])
    XCTAssertEqual(presets.map { $0.deck.items.count }, [100, 100, 100])
    XCTAssertTrue(presets.allSatisfy { $0.deck.type == .word })
    XCTAssertTrue(presets.allSatisfy { $0.deck.tags.contains("TOPIK") })
    XCTAssertTrue(presets.allSatisfy { $0.deck.tags.contains("タイピング") })
    try assertHybridDifficultyIncreases(presets)
  }

  func testBundledAcidRainPresetDecksGrowHarderToTypeAcrossLevels() throws {
    let presets = GamePresetDeckLoader.load(gameKind: .acidRain)

    XCTAssertEqual(presets.map(\.level), [.beginner, .intermediate, .advanced])
    XCTAssertEqual(presets.map(\.deck.level), [1, 2, 3])
    XCTAssertEqual(presets.map { $0.deck.items.count }, [100, 100, 100])
    XCTAssertEqual(
      presets.map(\.deck.deckId),
      [
        "acid_rain_topik_beginner",
        "acid_rain_topik_intermediate",
        "acid_rain_topik_advanced",
      ]
    )
    XCTAssertTrue(presets.allSatisfy { $0.deck.type == .word })
    XCTAssertTrue(presets.allSatisfy { $0.deck.tags.contains("TOPIK") })
    XCTAssertTrue(presets.allSatisfy { $0.deck.tags.contains("タイピング") })
    try assertHybridDifficultyIncreases(presets)
  }

  func testRemainingDeckGamesLoadThreeHundredWordTypingPresetLevels() throws {
    for gameKind in [GameKind.wordMatch, .choseong, .dictation] {
      let presets = GamePresetDeckLoader.load(gameKind: gameKind)

      XCTAssertEqual(presets.map(\.level), [.beginner, .intermediate, .advanced], gameKind.rawValue)
      XCTAssertEqual(presets.map(\.deck.level), [1, 2, 3], gameKind.rawValue)
      XCTAssertEqual(presets.map { $0.deck.items.count }, [100, 100, 100], gameKind.rawValue)
      XCTAssertEqual(
        presets.map(\.deck.deckId),
        [
          "\(gameKind.rawValue)_topik_beginner",
          "\(gameKind.rawValue)_topik_intermediate",
          "\(gameKind.rawValue)_topik_advanced",
        ],
        gameKind.rawValue
      )
      XCTAssertTrue(presets.allSatisfy { $0.deck.type == .word }, gameKind.rawValue)
      XCTAssertTrue(
        presets.allSatisfy { $0.deck.tags.contains("TOPIK") },
        gameKind.rawValue
      )
      XCTAssertTrue(
        presets.allSatisfy { $0.deck.tags.contains("タイピング") },
        gameKind.rawValue
      )
      try assertHybridDifficultyIncreases(presets)
    }
  }

  func testHundredWordPresetContentSupportsEachGameMechanic() throws {
    let wordMatch = try XCTUnwrap(
      GamePresetDeckLoader.load(gameKind: .wordMatch).first?.deck
    )
    let wordMatchRounds = WordMatchTypingBuilder.rounds(
      items: wordMatch.items,
      limit: 100,
      seed: 42
    )
    XCTAssertEqual(wordMatchRounds.count, 100)
    XCTAssertTrue(wordMatchRounds.allSatisfy {
      guard let sequence = try? JamoDecomposer.keySequence(for: $0.answer.ko) else {
        return false
      }
      return !$0.answer.meaningJa.isEmpty && !sequence.isEmpty
    })

    for preset in GamePresetDeckLoader.load(gameKind: .choseong) {
      XCTAssertEqual(
        ChoseongTypingBuilder.rounds(items: preset.deck.items, limit: 100, seed: 42).count,
        100
      )
    }

    for preset in GamePresetDeckLoader.load(gameKind: .dictation) {
      XCTAssertEqual(
        DictationTypingBuilder.rounds(items: preset.deck.items, limit: 100, seed: 42).count,
        100
      )
      for item in preset.deck.items {
        XCTAssertEqual(
          item.audio,
          BundledPronunciationAudio.relativePath(for: item.ko),
          "\(preset.deck.deckId): \(item.id)"
        )
      }
    }
  }

  func testBundledPresetShuffleChangesOrderWithoutChangingContent() throws {
    let preset = try XCTUnwrap(GamePresetDeckLoader.load(gameKind: .flow).first)
    let first = GamePresetSessionRandomizer.shuffledItems(
      from: preset.deck,
      gameKind: .flow,
      seed: 11
    )
    let second = GamePresetSessionRandomizer.shuffledItems(
      from: preset.deck,
      gameKind: .flow,
      seed: 29
    )

    XCTAssertNotEqual(first.map(\.id), second.map(\.id))
    XCTAssertEqual(Set(first.map(\.id)), Set(second.map(\.id)))
  }

  func testResultPresentationRecognizesEveryBundledPresetLevel() throws {
    let presentations: [(GameKind, GameResultPresentation)] = [
      (.flow, .flow),
      (.acidRain, .acidRain),
      (.choseong, .choseong),
      (.wordMatch, .wordMatch),
      (.dictation, .dictation),
    ]

    for (gameKind, presentation) in presentations {
      let presets = GamePresetDeckLoader.load(gameKind: gameKind)
      XCTAssertEqual(presets.count, 3, gameKind.rawValue)
      for preset in presets {
        let localizedDeck = preset.localizedDeck
        XCTAssertEqual(presentation.presetLevel(for: localizedDeck), preset.level)
        XCTAssertEqual(
          presentation.resultLevelTitle(for: localizedDeck),
          AppLocalization.string(preset.level.titleKey(for: gameKind))
        )
      }
    }
  }

  private func assertHybridDifficultyIncreases(_ presets: [GamePreset]) throws {
    XCTAssertEqual(presets.count, 3)
    for preset in presets {
      XCTAssertEqual(Set(preset.deck.items.map(\.ko)).count, 100)
      for item in preset.deck.items {
        XCTAssertEqual(
          item.audio,
          BundledPronunciationAudio.relativePath(for: item.ko),
          "\(preset.deck.deckId): \(item.id)"
        )
      }
    }

    var profiles: [[(keys: Int, syllables: Int, batchim: Int)]] = []
    for preset in presets {
      var profile: [(keys: Int, syllables: Int, batchim: Int)] = []
      for item in preset.deck.items {
        profile.append(try typingProfile(for: item.ko))
      }
      profiles.append(profile)
    }
    let lightWordCounts = profiles.map { profile in
      profile.filter { $0.syllables <= 2 && $0.batchim <= 1 }.count
    }
    let complexWordCounts = profiles.map { profile in
      profile.filter { $0.syllables >= 3 || $0.batchim >= 2 }.count
    }
    XCTAssertGreaterThanOrEqual(lightWordCounts[0], 70)
    XCTAssertLessThan(complexWordCounts[0], complexWordCounts[1])
    XCTAssertLessThan(complexWordCounts[1], complexWordCounts[2])
    XCTAssertGreaterThanOrEqual(complexWordCounts[2], 80)

    let averageKeyCounts = profiles.map { profile in
      Double(profile.reduce(0) { $0 + $1.keys }) / Double(profile.count)
    }
    XCTAssertLessThan(averageKeyCounts[0], averageKeyCounts[1])
    XCTAssertLessThan(averageKeyCounts[1], averageKeyCounts[2])

    let wordSets = presets.map { Set($0.deck.items.map(\.ko)) }
    XCTAssertTrue(
      Set(["사람", "가족", "학교", "오늘", "지하철", "가다", "먹다", "전화", "공부"])
        .isSubset(of: wordSets[0])
    )
    XCTAssertTrue(
      Set(["회의", "예약", "신청", "설명", "배송", "환불", "관리", "상황", "출근"])
        .isSubset(of: wordSets[1])
    )
    XCTAssertTrue(
      Set(["정책", "개인정보보호", "재택근무", "탄소중립", "노동시장", "국제경쟁력", "지속가능성"])
        .isSubset(of: wordSets[2])
    )
    XCTAssertEqual(wordSets.reduce(into: Set<String>()) { $0.formUnion($1) }.count, 300)
  }

  private func typingProfile(for word: String) throws
    -> (keys: Int, syllables: Int, batchim: Int)
  {
    let keys = try JamoDecomposer.keySequence(for: word).count
    var batchim = 0
    for scalar in word.unicodeScalars where scalar.value >= 0xAC00 && scalar.value <= 0xD7A3 {
      if (scalar.value - 0xAC00) % 28 != 0 { batchim += 1 }
    }
    return (keys, word.count, batchim)
  }

  func testFlowLaneMovesFromFullyRightToFullyLeft() {
    let start = FlowLaneLayout.horizontalOffset(
      progress: 0,
      containerWidth: 320,
      cardWidth: 120
    )
    let middle = FlowLaneLayout.horizontalOffset(
      progress: 0.5,
      containerWidth: 320,
      cardWidth: 120
    )
    let end = FlowLaneLayout.horizontalOffset(
      progress: 1,
      containerWidth: 320,
      cardWidth: 120
    )

    XCTAssertEqual(start, 320, accuracy: 0.001)
    XCTAssertEqual(middle, 100, accuracy: 0.001)
    XCTAssertEqual(end, -120, accuracy: 0.001)
    XCTAssertGreaterThan(start, middle)
    XCTAssertGreaterThan(middle, end)
  }

  func testMovingWordCardsFitTheirContentAndRespectLaneCaps() {
    let shortFlow = MovingWordCardLayout.flowWidth(
      korean: "나",
      meaning: "私",
      reading: "ナ",
      fontScale: 1,
      maximumWidth: 236
    )
    let longFlow = MovingWordCardLayout.flowWidth(
      korean: "국제경제협력",
      meaning: "国際経済協力",
      reading: "ククチェギョンジェヒョムニョク",
      fontScale: 1,
      maximumWidth: 236
    )
    let shortRain = MovingWordCardLayout.acidRainWidth(
      korean: "나",
      meaning: "私",
      reading: "ナ",
      fontScale: 1,
      maximumWidth: 188
    )
    let cappedRain = MovingWordCardLayout.acidRainWidth(
      korean: String(repeating: "가", count: 20),
      meaning: String(repeating: "語", count: 20),
      reading: String(repeating: "カ", count: 20),
      fontScale: 1,
      maximumWidth: 188
    )

    XCTAssertEqual(shortFlow, 96, accuracy: 0.001)
    XCTAssertGreaterThan(longFlow, shortFlow)
    XCTAssertLessThanOrEqual(longFlow, 236)
    XCTAssertEqual(shortRain, 92, accuracy: 0.001)
    XCTAssertEqual(cappedRain, 188, accuracy: 0.001)
  }

  func testAcidRainCardUsesThreeSafeLanesAndStopsAtDangerLine() {
    let containerWidth: CGFloat = 320
    let cardWidth = AcidRainLaneLayout.cardWidth(containerWidth: containerWidth)
    let centers = (0..<3).map {
      AcidRainLaneLayout.cardCenterX(
        itemIndex: $0,
        containerWidth: containerWidth,
        cardWidth: cardWidth
      )
    }
    let startY = AcidRainLaneLayout.cardCenterY(progress: 0, containerHeight: 360)
    let endY = AcidRainLaneLayout.cardCenterY(progress: 1, containerHeight: 360)

    XCTAssertGreaterThanOrEqual(centers[0] - cardWidth / 2, 0)
    XCTAssertLessThanOrEqual(centers[2] + cardWidth / 2, containerWidth)
    XCTAssertLessThan(centers[0], centers[1])
    XCTAssertLessThan(centers[1], centers[2])
    XCTAssertEqual(startY, 93, accuracy: 0.001)
    XCTAssertEqual(
      endY + 45,
      360 - AcidRainLaneLayout.dangerZoneHeight,
      accuracy: 0.001
    )
  }

  func testAcidRainSpawnsAnotherCardBeforeTheFirstReachesDangerLine() throws {
    let origin = Date(timeIntervalSince1970: 100)
    let travelDuration: TimeInterval = 10
    let model = FlowGameViewModel(
      targets: ["가", "나", "다"],
      cardTravelDuration: travelDuration,
      allowsConcurrentCards: true
    )
    model.start(at: origin)

    let spawnInterval = FlowGamePacing.acidRainSpawnInterval(
      cardTravelDuration: travelDuration
    )
    model.tick(at: origin.addingTimeInterval(spawnInterval + 0.1))

    let cards = model.projectedAcidRainCards(
      at: origin.addingTimeInterval(spawnInterval + 0.1)
    )
    XCTAssertEqual(cards.count, 2)
    XCTAssertEqual(cards.map(\.itemIndex), [0, 1])
    XCTAssertTrue(try XCTUnwrap(cards.first).isInputTarget)
    XCTAssertFalse(try XCTUnwrap(cards.last).isInputTarget)
    XCTAssertLessThan(try XCTUnwrap(cards.first).progress, 1)
    XCTAssertLessThan(try XCTUnwrap(cards.last).progress, 1)
  }

  func testAcidRainCompletionTargetsTheMostUrgentCardAlreadyFalling() throws {
    let origin = Date(timeIntervalSince1970: 200)
    let travelDuration: TimeInterval = 10
    let model = FlowGameViewModel(
      targets: ["가", "나", "다"],
      cardTravelDuration: travelDuration,
      allowsConcurrentCards: true
    )
    model.start(at: origin)

    let elapsed = FlowGamePacing.acidRainSpawnInterval(
      cardTravelDuration: travelDuration
    ) + 0.5
    model.tick(at: origin.addingTimeInterval(elapsed))
    let queuedProgress = try XCTUnwrap(
      model.projectedAcidRainCards(at: origin.addingTimeInterval(elapsed))
        .first(where: { $0.itemIndex == 1 })
    ).progress

    type("ㄱㅏ", into: model)

    XCTAssertEqual(model.currentTargetIndex, 1)
    XCTAssertEqual(model.target, "나")
    let activeCard = try XCTUnwrap(
      model.projectedAcidRainCards(at: origin.addingTimeInterval(elapsed))
        .first(where: \.isInputTarget)
    )
    XCTAssertEqual(activeCard.itemIndex, 1)
    XCTAssertEqual(activeCard.progress, queuedProgress, accuracy: 0.001)
  }

  func testAcidRainOSIMECompletesAnyMatchingVisibleCard() throws {
    let origin = Date(timeIntervalSince1970: 250)
    let travelDuration: TimeInterval = 10
    let model = FlowGameViewModel(
      targets: ["가", "너", "다"],
      cardTravelDuration: travelDuration,
      allowsConcurrentCards: true
    )
    model.start(at: origin)
    let spawnInterval = FlowGamePacing.acidRainSpawnInterval(
      cardTravelDuration: travelDuration
    )
    model.tick(at: origin.addingTimeInterval(spawnInterval + 0.1))

    XCTAssertEqual(model.currentTargetIndex, 0)
    XCTAssertTrue(model.acidRainCards.contains { $0.itemIndex == 1 })

    model.synchronizeAcidRainOSIME(
      matchingTarget: "너",
      acceptedSequence: Array("ㄴㅓ")
    )

    XCTAssertEqual(model.completedItemCount, 1)
    XCTAssertEqual(model.lastCompletedItem?.itemIndex, 1)
    XCTAssertFalse(model.acidRainCards.contains { $0.itemIndex == 1 })
    XCTAssertTrue(model.acidRainCards.contains { $0.itemIndex == 0 })
    XCTAssertEqual(model.currentTargetIndex, 0)
  }

  func testOSIMECandidateJudgePrefersAnyCompletedVisibleWord() throws {
    let selection = try XCTUnwrap(
      OSIMECandidateTextJudge.evaluate(
        targets: ["가", "너"],
        preferredTarget: "가",
        committedText: "너"
      )
    )

    XCTAssertEqual(selection.target, "너")
    XCTAssertEqual(
      selection.evaluation.status,
      .matching(completed: true, isComposing: false)
    )
    XCTAssertEqual(selection.evaluation.acceptedSequence, Array("ㄴㅓ"))
  }

  func testOSIMECandidateJudgeKeepsReachableCheonjiinCommittedVowelViable() throws {
    for committed in ["ㄷㆍ", "되"] {
      let selection = try XCTUnwrap(
        OSIMECandidateTextJudge.evaluate(
          targets: ["뒤", "돼지"],
          preferredTarget: "뒤",
          committedText: committed
        )
      )

      XCTAssertEqual(selection.target, "돼지", committed)
      XCTAssertEqual(selection.evaluation.status, .composingMismatch, committed)
      XCTAssertEqual(
        selection.evaluation.acceptedSequence,
        committed == "ㄷㆍ" ? Array("ㄷ") : Array("ㄷㅗ"),
        committed
      )
    }
  }

  func testConcurrentAcidRainEndsWhenThreeSeparateCardsReachTheFloor() {
    let origin = Date(timeIntervalSince1970: 300)
    let model = FlowGameViewModel(
      targets: ["가", "나", "다", "라"],
      cardTravelDuration: 4,
      allowsConcurrentCards: true
    )
    model.start(at: origin)

    model.tick(at: origin.addingTimeInterval(20))

    XCTAssertEqual(model.phase, .finished)
    XCTAssertEqual(model.remainingLives, 0)
    XCTAssertEqual(model.missedCardCount, 3)
    XCTAssertTrue(model.result.endedByLives)
  }

  func testConcurrentAcidRainCardsFreezeWhileTheAppIsPaused() throws {
    let origin = Date(timeIntervalSince1970: 400)
    let model = FlowGameViewModel(
      targets: ["가", "나", "다"],
      cardTravelDuration: 10,
      allowsConcurrentCards: true
    )
    model.start(at: origin)
    model.tick(at: origin.addingTimeInterval(2))
    model.pause(at: origin.addingTimeInterval(2))

    let pausedCard = try XCTUnwrap(
      model.projectedAcidRainCards(at: origin.addingTimeInterval(100)).first
    )
    XCTAssertEqual(pausedCard.progress, 0.2, accuracy: 0.001)
    XCTAssertEqual(model.remainingTime, 58, accuracy: 0.001)

    model.resume(at: origin.addingTimeInterval(100))
    model.tick(at: origin.addingTimeInterval(101))
    let resumedCard = try XCTUnwrap(
      model.projectedAcidRainCards(at: origin.addingTimeInterval(101)).first
    )
    XCTAssertEqual(resumedCard.progress, 0.3, accuracy: 0.001)
    XCTAssertEqual(model.remainingTime, 57, accuracy: 0.001)
  }

  func testFlowAndAcidRainMascotPreservesTheCanonicalFrontRatio() {
    XCTAssertEqual(FlowGameMascotLayout.pose, .front)
    XCTAssertEqual(
      MascotMotionPolicy.yawDegrees(
        pose: FlowGameMascotLayout.pose,
        isLookingBack: false
      ),
      0,
      accuracy: 0.001
    )
  }

  func testFlowAndAcidRainMascotDoesNotAutoEquipDeckPropsAcrossCards() {
    let appearance = MascotSessionAppearance()

    XCTAssertEqual(appearance.automaticProp, .none)
    for _ in 0..<10 {
      XCTAssertEqual(appearance.automaticProp, .none)
    }
  }

  func testFlowPacingAcceleratesGraduallyAndCapsAtOnePointEightSpeed() {
    XCTAssertEqual(FlowGamePacing.flowSpeedMultiplier(activeElapsed: 0), 1, accuracy: 0.001)
    XCTAssertEqual(FlowGamePacing.flowSpeedMultiplier(activeElapsed: 10), 1.16, accuracy: 0.001)
    XCTAssertEqual(FlowGamePacing.flowSpeedMultiplier(activeElapsed: 25), 1.4, accuracy: 0.001)
    XCTAssertEqual(FlowGamePacing.flowSpeedMultiplier(activeElapsed: 50), 1.8, accuracy: 0.001)
    XCTAssertEqual(FlowGamePacing.flowSpeedMultiplier(activeElapsed: 100), 1.8, accuracy: 0.001)
  }

  func testAcidRainKeepsItsExistingOnePointFiveSpeedCap() {
    XCTAssertEqual(FlowGamePacing.acidRainSpeedMultiplier(activeElapsed: 0), 1, accuracy: 0.001)
    XCTAssertEqual(FlowGamePacing.acidRainSpeedMultiplier(activeElapsed: 25), 1.25, accuracy: 0.001)
    XCTAssertEqual(FlowGamePacing.acidRainSpeedMultiplier(activeElapsed: 50), 1.5, accuracy: 0.001)
    XCTAssertEqual(FlowGamePacing.acidRainSpeedMultiplier(activeElapsed: 100), 1.5, accuracy: 0.001)
  }

  func testFlawlessCompletionAddsBonusAndUsesComboMultiplierAtFive() {
    let model = FlowGameViewModel(targets: ["가"], cardTravelDuration: 100)
    model.start(at: Date(timeIntervalSince1970: 0))

    for _ in 0..<5 {
      type("ㄱㅏ", into: model)
    }

    XCTAssertEqual(model.completedItemCount, 5)
    XCTAssertEqual(model.combo, 5)
    XCTAssertEqual(model.maxCombo, 5)
    XCTAssertEqual(model.remainingTime, 70, accuracy: 0.001)
    XCTAssertEqual(model.score, 104)
    XCTAssertEqual(model.completionRevision, 5)
  }

  func testMistakeResetsComboAndPreventsTimeBonusForThatWord() {
    let model = FlowGameViewModel(targets: ["가"], cardTravelDuration: 100)
    model.start(at: Date(timeIntervalSince1970: 0))
    type("ㄱㅏ", into: model)

    model.input("ㄴ")
    type("ㄱㅏ", into: model)

    XCTAssertEqual(model.combo, 0)
    XCTAssertEqual(model.maxCombo, 1)
    XCTAssertEqual(model.mistakeCount, 1)
    XCTAssertEqual(model.remainingTime, 62, accuracy: 0.001)
    XCTAssertEqual(model.score, 40)
    XCTAssertEqual(model.accuracyPercent, 80, accuracy: 0.001)
  }

  func testPausedTimeDoesNotAdvanceTimerOrCard() {
    let origin = Date(timeIntervalSince1970: 1_000)
    let model = FlowGameViewModel(targets: ["가"], cardTravelDuration: 100)
    model.start(at: origin)
    model.tick(at: origin.addingTimeInterval(5))
    model.pause(at: origin.addingTimeInterval(5))

    XCTAssertEqual(model.phase, .paused)
    XCTAssertEqual(model.remainingTime, 55, accuracy: 0.001)
    XCTAssertEqual(
      model.projectedCardProgress(at: origin.addingTimeInterval(100)), 0.05, accuracy: 0.001)

    model.resume(at: origin.addingTimeInterval(100))
    model.tick(at: origin.addingTimeInterval(105))
    XCTAssertEqual(model.remainingTime, 50, accuracy: 0.001)
    XCTAssertEqual(
      model.projectedCardProgress(at: origin.addingTimeInterval(105)), 0.10, accuracy: 0.001)
  }

  func testRestartReturnsToReadyWithoutConsumingTimerUntilStartedAgain() {
    let origin = Date(timeIntervalSince1970: 1_500)
    let model = FlowGameViewModel(targets: ["가"], cardTravelDuration: 100)
    model.start(at: origin)
    model.tick(at: origin.addingTimeInterval(5))

    model.restart()
    model.tick(at: origin.addingTimeInterval(50))

    XCTAssertEqual(model.phase, .ready)
    XCTAssertEqual(model.remainingTime, 60, accuracy: 0.001)
    XCTAssertEqual(model.remainingLives, 3)
    XCTAssertEqual(model.currentCardTravelDuration, 100, accuracy: 0.001)
    XCTAssertEqual(model.projectedCardProgress(at: origin.addingTimeInterval(50)), 0)

    model.start(at: origin.addingTimeInterval(50))
    model.tick(at: origin.addingTimeInterval(51))
    XCTAssertEqual(model.remainingTime, 59, accuracy: 0.001)
  }

  func testRestartWithReplacementTargetsKeepsVisibleTargetAndJudgeAligned() {
    let origin = Date(timeIntervalSince1970: 1_600)
    let model = FlowGameViewModel(targets: ["가", "나"], cardTravelDuration: 100)

    model.restart(targets: ["다", "라"])

    XCTAssertEqual(model.targets, ["다", "라"])
    XCTAssertEqual(model.currentTargetIndex, 0)
    XCTAssertEqual(model.target, "다")
    XCTAssertEqual(model.nextExpectedKey, "ㄷ")

    model.start(at: origin)
    model.input("ㄱ")
    XCTAssertEqual(model.feedback, .incorrect(expected: "ㄷ"))
    XCTAssertEqual(model.enteredText, "")

    type("ㄷㅏ", into: model)
    XCTAssertEqual(model.lastCompletedItem?.itemIndex, 0)
    XCTAssertEqual(model.currentTargetIndex, 1)
    XCTAssertEqual(model.target, "라")
    XCTAssertEqual(model.nextExpectedKey, "ㄹ")
  }

  func testEscapedCardAdvancesWithoutTimePenaltyAndResetsCombo() {
    let origin = Date(timeIntervalSince1970: 2_000)
    let model = FlowGameViewModel(targets: ["가", "나"], cardTravelDuration: 8)
    model.start(at: origin)
    type("ㄱㅏ", into: model)
    XCTAssertEqual(model.combo, 1)

    model.tick(at: origin.addingTimeInterval(8.1))

    XCTAssertEqual(model.missedCardCount, 1)
    XCTAssertEqual(model.remainingLives, 2)
    XCTAssertEqual(model.combo, 0)
    XCTAssertEqual(model.currentTargetIndex, 0)
    XCTAssertEqual(model.remainingTime, 53.9, accuracy: 0.001)
    XCTAssertEqual(model.currentCardTravelDuration, 8 / 1.128, accuracy: 0.001)
    XCTAssertEqual(
      model.projectedCardProgress(at: origin.addingTimeInterval(8.1)),
      0.1 / model.currentCardTravelDuration,
      accuracy: 0.001
    )
  }

  func testThirdEscapedCardExhaustsLivesAndFinishesBeforeTimer() {
    let origin = Date(timeIntervalSince1970: 2_500)
    let model = FlowGameViewModel(targets: ["가", "나"], cardTravelDuration: 1)
    model.start(at: origin)

    model.tick(at: origin.addingTimeInterval(10))

    XCTAssertEqual(model.phase, .finished)
    XCTAssertEqual(model.remainingLives, 0)
    XCTAssertEqual(model.missedCardCount, 3)
    XCTAssertGreaterThan(model.remainingTime, 50)
    XCTAssertTrue(model.result.endedByLives)
  }

  func testFinishBuildsScoreAccuracySpeedAndRankResult() {
    let origin = Date(timeIntervalSince1970: 3_000)
    let model = FlowGameViewModel(
      targets: ["가"],
      initialDuration: 1,
      flawlessBonus: 0,
      cardTravelDuration: 100
    )
    model.start(at: origin)
    type("ㄱㅏ", into: model)
    model.tick(at: origin.addingTimeInterval(1))

    XCTAssertEqual(model.phase, .finished)
    XCTAssertEqual(
      model.result,
      FlowGameResult(
        score: 20,
        maxCombo: 1,
        accuracyPercent: 100,
        charactersPerMinute: 120,
        activeDuration: 1,
        completedItemCount: 1,
        missedCardCount: 0,
        rank: "S"
      )
    )
  }

  func testFinishedRunIgnoresLateTickerAndInputCallbacks() {
    let origin = Date(timeIntervalSince1970: 3_500)
    let model = FlowGameViewModel(
      targets: ["가"],
      initialDuration: 1,
      flawlessBonus: 0,
      cardTravelDuration: 100
    )
    model.start(at: origin)
    model.tick(at: origin.addingTimeInterval(1))
    let finishedResult = model.result

    model.input("ㄱ")
    model.backspace()
    model.synchronizeOSIME(acceptedSequence: Array("ㄱㅏ"))
    model.recordConfirmedOSIMEMistake()
    model.tick(at: origin.addingTimeInterval(100))
    model.resume(at: origin.addingTimeInterval(100))

    XCTAssertEqual(model.phase, .finished)
    XCTAssertEqual(model.result, finishedResult)
    XCTAssertEqual(model.completedJamoCount, 0)
  }

  func testBackspaceRewindsCurrentJamoWithoutForgivingMistake() {
    let model = FlowGameViewModel(targets: ["가"], cardTravelDuration: 100)
    model.start(at: Date(timeIntervalSince1970: 0))
    model.input("ㄴ")
    model.input("ㄱ")
    model.backspace()

    XCTAssertEqual(model.completedJamoCount, 0)
    XCTAssertEqual(model.enteredText, "")
    XCTAssertEqual(model.mistakeCount, 1)
    XCTAssertEqual(model.combo, 0)
  }

  func testCompletionResolutionTracksMistakeStateBeforeCardAdvances() {
    let model = FlowGameViewModel(targets: ["가", "나"], cardTravelDuration: 100)
    model.start(at: Date(timeIntervalSince1970: 0))
    model.input("ㄴ")
    type("ㄱㅏ", into: model)

    XCTAssertEqual(
      model.lastCompletedItem,
      SessionItemResolution(
        itemIndex: 0,
        hadMistake: true,
        mistakeCount: 1,
        mistakenJamoIndices: [0]
      )
    )

    type("ㄴㅏ", into: model)
    XCTAssertEqual(
      model.lastCompletedItem,
      SessionItemResolution(
        itemIndex: 1,
        hadMistake: false,
        mistakeCount: 0,
        mistakenJamoIndices: []
      )
    )
    XCTAssertEqual(model.reviewResolutionRevision, 2)
  }

  func testMistakenEscapedCardPublishesReviewResolution() {
    let origin = Date(timeIntervalSince1970: 4_000)
    let model = FlowGameViewModel(targets: ["가", "나"], cardTravelDuration: 1)
    model.start(at: origin)
    model.input("ㄴ")

    model.tick(at: origin.addingTimeInterval(1))

    XCTAssertEqual(
      model.lastCompletedItem,
      SessionItemResolution(
        itemIndex: 0,
        hadMistake: true,
        mistakeCount: 1,
        mistakenJamoIndices: [0]
      )
    )
    XCTAssertEqual(model.reviewResolutionRevision, 1)
    XCTAssertEqual(model.completionRevision, 0)
    XCTAssertEqual(model.currentTargetIndex, 1)
  }

  func testRankUsesSixtyFortyAccuracySpeedWeighting() {
    let tuning = FlowGameRankTuning()
    XCTAssertEqual(tuning.rank(accuracyPercent: 100, charactersPerMinute: 120), "S")
    XCTAssertEqual(tuning.rank(accuracyPercent: 90, charactersPerMinute: 90), "A")
    XCTAssertEqual(tuning.rank(accuracyPercent: 70, charactersPerMinute: 60), "B")
    XCTAssertEqual(tuning.rank(accuracyPercent: 50, charactersPerMinute: 30), "C")
  }

  func testOSIMESynchronizationAndConfirmedMistakeUseGameScoringRules() {
    let model = FlowGameViewModel(targets: ["가"], cardTravelDuration: 100)
    model.start(at: Date(timeIntervalSince1970: 0))

    model.recordConfirmedOSIMEMistake()
    model.synchronizeOSIME(acceptedSequence: Array("ㄱㅏ"))

    XCTAssertEqual(model.mistakeCount, 1)
    XCTAssertEqual(model.completedItemCount, 1)
    XCTAssertEqual(model.combo, 0)
    XCTAssertEqual(model.score, 20)
  }

  func testMascotEventsCoverTypingRhythmMistakesAndResume() {
    let model = FlowGameViewModel(targets: ["가"], cardTravelDuration: 100)
    model.start(at: Date(timeIntervalSince1970: 0))

    model.input("ㄱ")
    XCTAssertEqual(model.mascotEvent, .correctJamo)
    XCTAssertEqual(model.totalAcceptedInputCount, 1)
    model.input("ㅏ")
    XCTAssertEqual(model.mascotEvent, .wordCompleted)

    for _ in 0..<4 { type("ㄱㅏ", into: model) }
    XCTAssertEqual(model.combo, 5)
    XCTAssertEqual(model.mascotEvent, .rhythm(combo: 5))

    model.input("ㄴ")
    XCTAssertEqual(model.mascotEvent, .startle(expected: "ㄱ"))
    model.input("ㄴ")
    model.input("ㄴ")
    XCTAssertEqual(model.consecutiveMistakes, 3)
    XCTAssertEqual(model.mascotEvent, .dizzy(expected: "ㄱ"))

    model.publishStretchReaction()
    XCTAssertEqual(model.mascotEvent, .stretch)
    model.publishNewBestReaction()
    XCTAssertEqual(model.mascotEvent, .newBest)
  }

  #if DEBUG
    func testFrameRateCalculatorReportsP95AndOverBudgetFrames() throws {
      let timestamps: [CFTimeInterval] = [0, 0.016, 0.032, 0.057, 0.073]

      let snapshot = try XCTUnwrap(GameFrameRateCalculator.snapshot(timestamps: timestamps))

      XCTAssertEqual(snapshot.sampleCount, 4)
      XCTAssertEqual(snapshot.averageFPS, 4 / 0.073, accuracy: 0.001)
      XCTAssertEqual(snapshot.p95FrameDurationMilliseconds, 25, accuracy: 0.001)
      XCTAssertEqual(snapshot.worstFrameDurationMilliseconds, 25, accuracy: 0.001)
      XCTAssertEqual(snapshot.overBudgetFrameCount, 1)
    }
  #endif

  private func type(_ sequence: String, into model: FlowGameViewModel) {
    for key in sequence {
      model.input(key)
    }
  }
}
