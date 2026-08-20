import Combine
import Foundation
import HangulEngine

struct FlowGameResult: Equatable {
  let score: Int
  let maxCombo: Int
  let accuracyPercent: Double
  let charactersPerMinute: Double
  let activeDuration: TimeInterval
  let completedItemCount: Int
  let missedCardCount: Int
  let rank: String
  var endedByLives = false
}

enum FlowGamePacing {
  static let defaultMaximumLives = 3
  static let flowMaximumSpeedMultiplier = 1.8
  static let acidRainMaximumSpeedMultiplier = 1.5
  static let secondsUntilMaximumSpeed: TimeInterval = 50
  static let maximumConcurrentAcidRainCards = 4

  static func flowSpeedMultiplier(activeElapsed: TimeInterval) -> Double {
    speedMultiplier(
      activeElapsed: activeElapsed,
      maximumSpeedMultiplier: flowMaximumSpeedMultiplier
    )
  }

  static func acidRainSpeedMultiplier(activeElapsed: TimeInterval) -> Double {
    speedMultiplier(
      activeElapsed: activeElapsed,
      maximumSpeedMultiplier: acidRainMaximumSpeedMultiplier
    )
  }

  private static func speedMultiplier(
    activeElapsed: TimeInterval,
    maximumSpeedMultiplier: Double
  ) -> Double {
    let progress = min(max(activeElapsed / secondsUntilMaximumSpeed, 0), 1)
    return 1 + (maximumSpeedMultiplier - 1) * progress
  }

  static func acidRainSpawnInterval(cardTravelDuration: TimeInterval) -> TimeInterval {
    min(max(1.4, cardTravelDuration * 0.42), 3.6, cardTravelDuration * 0.72)
  }
}

struct AcidRainFallingCard: Identifiable, Equatable {
  let id: Int
  let itemIndex: Int
  let laneIndex: Int
  var elapsed: TimeInterval
  let travelDuration: TimeInterval
}

struct AcidRainCardSnapshot: Identifiable, Equatable {
  let id: Int
  let itemIndex: Int
  let laneIndex: Int
  let progress: Double
  let isInputTarget: Bool
}

enum FlowGameMascotEvent: Equatable {
  case idle
  case correctJamo
  case wordCompleted
  case rhythm(combo: Int)
  case startle(expected: Character)
  case dizzy(expected: Character)
  case stretch
  case newBest
}

@MainActor
final class FlowGameViewModel: ObservableObject {
  enum Phase: Equatable {
    case ready
    case running
    case paused
    case finished
  }

  enum Feedback: Equatable {
    case ready
    case correct
    case incorrect(expected: Character)
    case completed(points: Int)
    case escaped
  }

  private(set) var targets: [String]
  let initialDuration: TimeInterval
  let flawlessBonus: TimeInterval
  let cardTravelDuration: TimeInterval
  let maximumLives: Int
  let allowsConcurrentCards: Bool

  @Published private(set) var phase: Phase = .ready
  @Published private(set) var remainingTime: TimeInterval
  @Published private(set) var score = 0
  @Published private(set) var combo = 0
  @Published private(set) var maxCombo = 0
  @Published private(set) var completedItemCount = 0
  @Published private(set) var missedCardCount = 0
  @Published private(set) var remainingLives: Int
  @Published private(set) var mistakeCount = 0
  @Published private(set) var currentTargetIndex = 0
  @Published private(set) var target: String
  @Published private(set) var targetJamoSequence: [Character]
  @Published private(set) var composition = CompositionState()
  @Published private(set) var judgeState: JamoJudgeState
  @Published private(set) var feedback: Feedback = .ready
  @Published private(set) var feedbackRevision = 0
  @Published private(set) var cardRevision = 0
  @Published private(set) var completionRevision = 0
  @Published private(set) var reviewResolutionRevision = 0
  @Published private(set) var lastCompletedItem: SessionItemResolution?
  @Published private(set) var consecutiveMistakes = 0
  @Published private(set) var totalAcceptedInputCount = 0
  @Published private(set) var mascotEvent: FlowGameMascotEvent = .idle
  @Published private(set) var mascotRevision = 0
  @Published private(set) var currentCardTravelDuration: TimeInterval
  @Published private(set) var acidRainCards: [AcidRainFallingCard] = []

  private let rankTuning: FlowGameRankTuning
  private var acceptedKeys: [Character] = []
  private var completedAcceptedJamoCount = 0
  private var scoredJamoCount = 0
  private var currentWordMistakeCount = 0
  private var currentWordMistakenJamoIndices: Set<Int> = []
  private var lastTickDate: Date?
  private var activeCardElapsed: TimeInterval = 0
  private var activeElapsed: TimeInterval = 0
  private var activeAcidRainCardID: Int?
  private var nextAcidRainCardID = 0
  private var nextAcidRainItemIndex = 0
  private var acidRainSpawnElapsed: TimeInterval = 0
  private var acidRainSpawnInterval: TimeInterval = 0

  init(
    targets: [String],
    initialDuration: TimeInterval = 60,
    flawlessBonus: TimeInterval = 2,
    cardTravelDuration: TimeInterval = 8,
    maximumLives: Int = FlowGamePacing.defaultMaximumLives,
    allowsConcurrentCards: Bool = false,
    rankTuning: FlowGameRankTuning = FlowGameRankTuning()
  ) {
    precondition(!targets.isEmpty, "Flow game requires at least one target")
    precondition(initialDuration > 0, "Flow game duration must be positive")
    precondition(cardTravelDuration > 0, "Card travel duration must be positive")
    precondition(maximumLives > 0, "Flow game requires at least one life")
    precondition(
      targets.allSatisfy { (try? JamoDecomposer.keySequence(for: $0)) != nil },
      "Every flow game target must be decomposable by HangulEngine"
    )

    guard let judge = try? JamoJudgeState(target: targets[0]) else {
      preconditionFailure("Validated flow game target became invalid")
    }
    self.targets = targets
    self.initialDuration = initialDuration
    self.flawlessBonus = flawlessBonus
    self.cardTravelDuration = cardTravelDuration
    self.maximumLives = maximumLives
    self.allowsConcurrentCards = allowsConcurrentCards
    self.rankTuning = rankTuning
    self.remainingTime = initialDuration
    self.remainingLives = maximumLives
    self.currentCardTravelDuration = cardTravelDuration
    self.target = targets[0]
    self.targetJamoSequence = judge.expectedSequence
    self.judgeState = judge
    if allowsConcurrentCards {
      acidRainCards = [
        AcidRainFallingCard(
          id: 0,
          itemIndex: 0,
          laneIndex: 0,
          elapsed: 0,
          travelDuration: cardTravelDuration
        )
      ]
      activeAcidRainCardID = 0
      nextAcidRainCardID = 1
      nextAcidRainItemIndex = targets.count > 1 ? 1 : 0
      acidRainSpawnInterval = FlowGamePacing.acidRainSpawnInterval(
        cardTravelDuration: cardTravelDuration
      )
    }
  }

  var completedJamoCount: Int { judgeState.currentIndex }
  var acceptedKeySequence: [Character] { acceptedKeys }
  var nextExpectedKey: Character? { judgeState.expectedNext }
  var enteredText: String { composition.text }
  var composingPreview: String { composition.composingText }
  var acceptedJamoCount: Int { completedAcceptedJamoCount + acceptedKeys.count }
  var accuracyPercent: Double {
    let totalInputs = acceptedJamoCount + mistakeCount
    guard totalInputs > 0 else { return 0 }
    return Double(acceptedJamoCount) / Double(totalInputs) * 100
  }
  var charactersPerMinute: Double {
    guard activeElapsed > 0 else { return 0 }
    return Double(scoredJamoCount) / (activeElapsed / 60)
  }
  var result: FlowGameResult {
    FlowGameResult(
      score: score,
      maxCombo: maxCombo,
      accuracyPercent: accuracyPercent,
      charactersPerMinute: charactersPerMinute,
      activeDuration: activeElapsed,
      completedItemCount: completedItemCount,
      missedCardCount: missedCardCount,
      rank: rankTuning.rank(
        accuracyPercent: accuracyPercent,
        charactersPerMinute: charactersPerMinute
      ),
      endedByLives: remainingLives == 0
    )
  }

  func start(at date: Date = Date()) {
    guard phase == .ready else { return }
    phase = .running
    lastTickDate = date
  }

  /// Restores a completed run to the pre-start state. The view owns the
  /// visible countdown and calls `start(at:)` only after it has finished.
  func restart(targets replacementTargets: [String]? = nil) {
    if let replacementTargets {
      precondition(!replacementTargets.isEmpty, "Flow game requires at least one target")
      precondition(
        replacementTargets.allSatisfy {
          (try? JamoDecomposer.keySequence(for: $0)) != nil
        },
        "Every flow game target must be decomposable by HangulEngine"
      )
      targets = replacementTargets
    }
    remainingTime = initialDuration
    score = 0
    combo = 0
    maxCombo = 0
    completedItemCount = 0
    missedCardCount = 0
    remainingLives = maximumLives
    mistakeCount = 0
    consecutiveMistakes = 0
    totalAcceptedInputCount = 0
    completedAcceptedJamoCount = 0
    scoredJamoCount = 0
    activeElapsed = 0
    currentTargetIndex = 0
    target = targets[0]
    lastCompletedItem = nil
    mascotEvent = .idle
    mascotRevision &+= 1
    if allowsConcurrentCards {
      resetAcidRainCards()
    } else {
      resetCurrentCard()
    }
    feedback = .ready
    feedbackRevision += 1
    cardRevision += 1
    phase = .ready
    lastTickDate = nil
  }

  func tick(at date: Date = Date()) {
    guard phase == .running, let lastTickDate else { return }
    var unconsumed = max(0, date.timeIntervalSince(lastTickDate))
    self.lastTickDate = date

    if allowsConcurrentCards {
      tickConcurrentCards(unconsumed: unconsumed)
      return
    }

    while unconsumed > 0, phase == .running {
      let untilTimeout = remainingTime
      let untilEscape = currentCardTravelDuration - activeCardElapsed
      let step = min(unconsumed, untilTimeout, untilEscape)
      guard step > 0 else {
        if remainingTime <= 0 {
          finish()
        } else {
          escapeCurrentCard()
        }
        continue
      }

      remainingTime -= step
      activeElapsed += step
      activeCardElapsed += step
      unconsumed -= step

      if remainingTime <= 0.000_001 {
        remainingTime = 0
        finish()
      } else if activeCardElapsed >= currentCardTravelDuration - 0.000_001 {
        escapeCurrentCard()
      }
    }
  }

  private func tickConcurrentCards(unconsumed initialUnconsumed: TimeInterval) {
    var unconsumed = initialUnconsumed
    let epsilon = 0.000_001

    while unconsumed > 0, phase == .running {
      let canSpawn = acidRainCards.count < maximumConcurrentAcidRainCardCount
      let untilSpawn = canSpawn
        ? max(0, acidRainSpawnInterval - acidRainSpawnElapsed)
        : TimeInterval.greatestFiniteMagnitude
      let untilEscape = acidRainCards
        .map { max(0, $0.travelDuration - $0.elapsed) }
        .min() ?? TimeInterval.greatestFiniteMagnitude
      let step = min(unconsumed, remainingTime, untilSpawn, untilEscape)

      if step > epsilon {
        remainingTime -= step
        activeElapsed += step
        acidRainSpawnElapsed += step
        acidRainCards = acidRainCards.map { card in
          var updatedCard = card
          updatedCard.elapsed += step
          return updatedCard
        }
        synchronizeActiveAcidRainTiming()
        unconsumed -= step
      }

      if remainingTime <= epsilon {
        remainingTime = 0
        finish()
        continue
      }

      let escapedCardIDs = acidRainCards
        .filter { $0.elapsed >= $0.travelDuration - epsilon }
        .sorted { $0.elapsed / $0.travelDuration > $1.elapsed / $1.travelDuration }
        .map(\.id)
      for cardID in escapedCardIDs where phase == .running {
        escapeAcidRainCard(id: cardID)
      }

      var didSpawnCard = false
      if phase == .running,
        acidRainCards.count < maximumConcurrentAcidRainCardCount,
        acidRainSpawnElapsed >= acidRainSpawnInterval - epsilon
      {
        spawnAcidRainCard()
        didSpawnCard = true
      }

      if step <= epsilon, escapedCardIDs.isEmpty, !didSpawnCard {
        break
      }
    }
  }

  func pause(at date: Date = Date()) {
    guard phase == .running else { return }
    tick(at: date)
    guard phase == .running else { return }
    phase = .paused
    lastTickDate = nil
  }

  func resume(at date: Date = Date()) {
    guard phase == .paused else { return }
    phase = .running
    lastTickDate = date
  }

  func projectedCardProgress(at date: Date = Date()) -> Double {
    if allowsConcurrentCards,
      let snapshot = projectedAcidRainCards(at: date).first(where: \.isInputTarget)
    {
      return snapshot.progress
    }
    var elapsed = activeCardElapsed
    if phase == .running, let lastTickDate {
      elapsed += max(0, date.timeIntervalSince(lastTickDate))
    }
    return min(max(elapsed / currentCardTravelDuration, 0), 1)
  }

  func projectedAcidRainCards(at date: Date = Date()) -> [AcidRainCardSnapshot] {
    let additionalElapsed: TimeInterval
    if phase == .running, let lastTickDate {
      additionalElapsed = max(0, date.timeIntervalSince(lastTickDate))
    } else {
      additionalElapsed = 0
    }

    return acidRainCards.map { card in
      AcidRainCardSnapshot(
        id: card.id,
        itemIndex: card.itemIndex,
        laneIndex: card.laneIndex,
        progress: min(max((card.elapsed + additionalElapsed) / card.travelDuration, 0), 1),
        isInputTarget: card.id == activeAcidRainCardID
      )
    }
  }

  func input(_ key: Character) {
    guard phase == .running else { return }
    let evaluation = JamoSequenceJudge.evaluate(key, state: judgeState)
    judgeState = evaluation.state

    switch evaluation.result {
    case .correct(let completed):
      acceptedKeys.append(key)
      totalAcceptedInputCount += 1
      consecutiveMistakes = 0
      composition = HangulComposer.reduce(composition, event: .key(key))
      if completed {
        completeCurrentCard()
      } else {
        feedback = .correct
        publishMascot(.correctJamo)
      }

    case .incorrect(let expected):
      recordMistake(expected: expected)

    case .alreadyComplete:
      return
    }
    feedbackRevision += 1
  }

  func backspace() {
    guard phase == .running, !acceptedKeys.isEmpty else { return }
    acceptedKeys.removeLast()
    composition = HangulComposer.reduce(composition, event: .backspace)
    rebuildJudgeFromAcceptedKeys()
    feedback = .ready
    feedbackRevision += 1
  }

  func synchronizeOSIME(acceptedSequence: [Character]) {
    guard phase == .running,
      acceptedSequence.count <= targetJamoSequence.count,
      Array(targetJamoSequence.prefix(acceptedSequence.count)) == acceptedSequence
    else { return }

    while acceptedKeys.count > acceptedSequence.count {
      backspace()
    }
    guard acceptedKeys.count < acceptedSequence.count else { return }
    for key in acceptedSequence.dropFirst(acceptedKeys.count) {
      input(key)
    }
  }

  func synchronizeAcidRainOSIME(
    matchingTarget: String,
    acceptedSequence: [Character]
  ) {
    guard allowsConcurrentCards, phase == .running,
      let matchingCard = acidRainCards
        .filter({ targets[$0.itemIndex] == matchingTarget })
        .max(by: {
          $0.elapsed / $0.travelDuration < $1.elapsed / $1.travelDuration
        })
    else { return }

    if matchingCard.id != activeAcidRainCardID {
      selectAcidRainCard(id: matchingCard.id, publishesCardChange: false)
    }
    synchronizeOSIME(acceptedSequence: acceptedSequence)
  }

  func recordConfirmedOSIMEMistake() {
    guard phase == .running, let expected = judgeState.expectedNext else { return }
    recordMistake(expected: expected)
    feedbackRevision += 1
  }

  func publishStretchReaction() {
    guard phase == .running else { return }
    publishMascot(.stretch)
  }

  func publishNewBestReaction() {
    guard phase == .running else { return }
    publishMascot(.newBest)
  }

  static func comboMultiplier(for combo: Int) -> Double {
    if combo >= 20 { return 2.0 }
    if combo >= 10 { return 1.5 }
    if combo >= 5 { return 1.2 }
    return 1.0
  }

  private func completeCurrentCard() {
    let flawless = currentWordMistakeCount == 0
    lastCompletedItem = SessionItemResolution(
      itemIndex: currentTargetIndex,
      hadMistake: !flawless,
      mistakeCount: currentWordMistakeCount,
      mistakenJamoIndices: currentWordMistakenJamoIndices
    )
    if flawless {
      combo += 1
      maxCombo = max(maxCombo, combo)
      remainingTime += flawlessBonus
    }

    let baseScore = targetJamoSequence.count * 10
    let points = Int(Double(baseScore) * Self.comboMultiplier(for: combo))
    score += points
    completedItemCount += 1
    scoredJamoCount += targetJamoSequence.count
    completedAcceptedJamoCount += acceptedKeys.count
    feedback = .completed(points: points)
    if combo > 0, combo.isMultiple(of: 5) {
      publishMascot(.rhythm(combo: combo))
    } else {
      publishMascot(.wordCompleted)
    }
    completionRevision += 1
    reviewResolutionRevision += 1
    if allowsConcurrentCards {
      removeCompletedAcidRainCard()
    } else {
      advanceToNextCard()
    }
  }

  private func escapeCurrentCard() {
    if currentWordMistakeCount > 0 {
      lastCompletedItem = SessionItemResolution(
        itemIndex: currentTargetIndex,
        hadMistake: true,
        mistakeCount: currentWordMistakeCount,
        mistakenJamoIndices: currentWordMistakenJamoIndices
      )
      reviewResolutionRevision += 1
    }
    completedAcceptedJamoCount += acceptedKeys.count
    missedCardCount += 1
    remainingLives -= 1
    combo = 0
    feedback = .escaped
    feedbackRevision += 1
    if remainingLives == 0 {
      finish()
    } else {
      advanceToNextCard()
    }
  }

  private func advanceToNextCard() {
    currentTargetIndex = (currentTargetIndex + 1) % targets.count
    target = targets[currentTargetIndex]
    resetCurrentCard()
    cardRevision += 1
  }

  private var maximumConcurrentAcidRainCardCount: Int {
    min(FlowGamePacing.maximumConcurrentAcidRainCards, targets.count)
  }

  private func resetAcidRainCards() {
    let travelDuration = cardTravelDuration
      / FlowGamePacing.acidRainSpeedMultiplier(activeElapsed: activeElapsed)
    acidRainCards = [
      AcidRainFallingCard(
        id: 0,
        itemIndex: 0,
        laneIndex: 0,
        elapsed: 0,
        travelDuration: travelDuration
      )
    ]
    activeAcidRainCardID = 0
    nextAcidRainCardID = 1
    nextAcidRainItemIndex = targets.count > 1 ? 1 : 0
    acidRainSpawnElapsed = 0
    acidRainSpawnInterval = FlowGamePacing.acidRainSpawnInterval(
      cardTravelDuration: travelDuration
    )
    currentTargetIndex = 0
    target = targets[0]
    resetCurrentInput()
    synchronizeActiveAcidRainTiming()
  }

  private func spawnAcidRainCard() {
    guard acidRainCards.count < maximumConcurrentAcidRainCardCount else { return }
    let travelDuration = cardTravelDuration
      / FlowGamePacing.acidRainSpeedMultiplier(activeElapsed: activeElapsed)
    let card = AcidRainFallingCard(
      id: nextAcidRainCardID,
      itemIndex: nextAcidRainItemIndex,
      laneIndex: nextAcidRainCardID % 3,
      elapsed: 0,
      travelDuration: travelDuration
    )
    acidRainCards.append(card)
    nextAcidRainCardID += 1
    nextAcidRainItemIndex = (nextAcidRainItemIndex + 1) % targets.count
    acidRainSpawnElapsed = max(0, acidRainSpawnElapsed - acidRainSpawnInterval)
    acidRainSpawnInterval = FlowGamePacing.acidRainSpawnInterval(
      cardTravelDuration: travelDuration
    )

    if activeAcidRainCardID == nil {
      selectMostUrgentAcidRainCard()
    }
  }

  private func removeCompletedAcidRainCard() {
    guard let activeAcidRainCardID else { return }
    acidRainCards.removeAll { $0.id == activeAcidRainCardID }
    self.activeAcidRainCardID = nil
    if acidRainCards.isEmpty {
      acidRainSpawnElapsed = 0
      spawnAcidRainCard()
    }
    selectMostUrgentAcidRainCard()
  }

  private func escapeAcidRainCard(id: Int) {
    guard let cardIndex = acidRainCards.firstIndex(where: { $0.id == id }) else { return }
    let wasInputTarget = id == activeAcidRainCardID

    if wasInputTarget {
      if currentWordMistakeCount > 0 {
        lastCompletedItem = SessionItemResolution(
          itemIndex: currentTargetIndex,
          hadMistake: true,
          mistakeCount: currentWordMistakeCount,
          mistakenJamoIndices: currentWordMistakenJamoIndices
        )
        reviewResolutionRevision += 1
      }
      completedAcceptedJamoCount += acceptedKeys.count
      activeAcidRainCardID = nil
    }

    acidRainCards.remove(at: cardIndex)
    missedCardCount += 1
    remainingLives -= 1
    combo = 0
    feedback = .escaped
    feedbackRevision += 1

    if remainingLives == 0 {
      finish()
      return
    }

    if wasInputTarget {
      if acidRainCards.isEmpty {
        acidRainSpawnElapsed = 0
        spawnAcidRainCard()
      }
      selectMostUrgentAcidRainCard()
    }
  }

  private func selectMostUrgentAcidRainCard() {
    guard let nextCard = acidRainCards.max(by: {
      $0.elapsed / $0.travelDuration < $1.elapsed / $1.travelDuration
    }) else {
      activeAcidRainCardID = nil
      return
    }

    selectAcidRainCard(id: nextCard.id, publishesCardChange: true)
  }

  private func selectAcidRainCard(id: Int, publishesCardChange: Bool) {
    guard let card = acidRainCards.first(where: { $0.id == id }) else { return }
    activeAcidRainCardID = card.id
    currentTargetIndex = card.itemIndex
    target = targets[card.itemIndex]
    resetCurrentInput()
    synchronizeActiveAcidRainTiming()
    if publishesCardChange {
      cardRevision += 1
    }
  }

  private func synchronizeActiveAcidRainTiming() {
    guard let activeAcidRainCardID,
      let activeCard = acidRainCards.first(where: { $0.id == activeAcidRainCardID })
    else { return }
    activeCardElapsed = activeCard.elapsed
    currentCardTravelDuration = activeCard.travelDuration
  }

  private func resetCurrentCard() {
    resetCurrentInput()
    activeCardElapsed = 0
    currentCardTravelDuration = cardTravelDuration
      / FlowGamePacing.flowSpeedMultiplier(activeElapsed: activeElapsed)
  }

  private func resetCurrentInput() {
    guard let judge = try? JamoJudgeState(target: target) else {
      preconditionFailure("Validated flow game target became invalid")
    }
    judgeState = judge
    targetJamoSequence = judge.expectedSequence
    acceptedKeys.removeAll(keepingCapacity: true)
    composition = CompositionState()
    currentWordMistakeCount = 0
    currentWordMistakenJamoIndices.removeAll(keepingCapacity: true)
  }

  private func rebuildJudgeFromAcceptedKeys() {
    guard var rebuilt = try? JamoJudgeState(target: target) else {
      preconditionFailure("Validated flow game target became invalid")
    }
    for key in acceptedKeys {
      rebuilt = JamoSequenceJudge.evaluate(key, state: rebuilt).state
    }
    judgeState = rebuilt
  }

  private func finish() {
    phase = .finished
    lastTickDate = nil
  }

  private func recordMistake(expected: Character) {
    mistakeCount += 1
    consecutiveMistakes += 1
    currentWordMistakeCount += 1
    currentWordMistakenJamoIndices.insert(judgeState.currentIndex)
    combo = 0
    feedback = .incorrect(expected: expected)
    publishMascot(
      consecutiveMistakes >= 3 ? .dizzy(expected: expected) : .startle(expected: expected)
    )
  }

  private func publishMascot(_ event: FlowGameMascotEvent) {
    mascotEvent = event
    mascotRevision &+= 1
  }
}
