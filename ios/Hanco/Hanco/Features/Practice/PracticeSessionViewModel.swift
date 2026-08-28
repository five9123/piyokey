import Combine
import Foundation
import HangulEngine

struct TargetSyllableProgress: Equatable, Identifiable {
  enum State: Equatable {
    case pending
    case inProgress
    case completed
  }

  let offset: Int
  let character: Character
  let jamoRange: Range<Int>
  let state: State

  var id: Int { offset }
  var isHangul: Bool { JamoDecomposer.containsHangul(in: String(character)) }
}

enum PracticeReactionEvent: Equatable {
  case idle
  case correctJamo
  case syllableCompleted(index: Int)
  case wordCompleted
  case comboMilestone(Int)
  case mistake(expected: Character)
  case perfectSession
  case dizzy(expected: Character)
  case stretch
}

@MainActor
final class PracticeSessionViewModel: ObservableObject {
  enum Feedback: Equatable {
    case idle
    case correct
    case incorrect(expected: Character)
    case complete
  }

  let targets: [String]
  private let targetJamoCounts: [Int]
  private let now: () -> Date

  @Published private(set) var currentTargetIndex = 0
  @Published private(set) var target: String
  @Published private(set) var targetJamoSequence: [Character]
  @Published private(set) var composition = CompositionState()
  @Published private(set) var judgeState: JamoJudgeState
  @Published private(set) var feedback: Feedback = .idle
  @Published private(set) var feedbackRevision = 0
  @Published private(set) var compositionRevision = 0
  @Published private(set) var lastAcceptedKey: Character?
  @Published private(set) var shouldAnimateSyllableJoin = false
  @Published private(set) var mistakeCount = 0
  @Published private(set) var lastCompletedItem: SessionItemResolution?
  @Published private(set) var itemCompletionRevision = 0
  @Published private(set) var correctStreak = 0
  @Published private(set) var reactionEvent: PracticeReactionEvent = .idle
  @Published private(set) var reactionRevision = 0
  @Published private(set) var consecutiveMistakes = 0
  @Published private(set) var totalAcceptedInputCount = 0

  private var acceptedKeys: [Character] = []
  private var targetSyllableRanges: [(character: Character, range: Range<Int>)] = []
  private var currentTargetMistakeCount = 0
  private var currentTargetMistakenJamoIndices: Set<Int> = []
  private var accumulatedActiveDuration: TimeInterval = 0
  private var activeStartedAt: Date?
  private var hasStartedTiming = false

  convenience init(target: String) {
    self.init(targets: [target])
  }

  init(
    targets: [String],
    checkpoint: PracticeSessionCheckpoint? = nil,
    now: @escaping () -> Date = Date.init
  ) {
    guard let firstTarget = targets.first,
      let judge = try? JamoJudgeState(target: firstTarget)
    else {
      preconditionFailure("Practice targets must be decomposable by HangulEngine")
    }
    precondition(
      targets.allSatisfy { (try? JamoDecomposer.keySequence(for: $0)) != nil },
      "Every practice target must be decomposable by HangulEngine"
    )
    self.targets = targets
    self.now = now
    self.targetJamoCounts = targets.map {
      guard let sequence = try? JamoDecomposer.keySequence(for: $0) else {
        preconditionFailure("Validated practice target became invalid")
      }
      return sequence.count
    }
    self.target = firstTarget
    self.targetJamoSequence = judge.expectedSequence
    self.judgeState = judge
    self.targetSyllableRanges = Self.makeTargetSyllableRanges(for: firstTarget)
    if let checkpoint {
      restore(checkpoint)
    }
  }

  var completedJamoCount: Int { judgeState.currentIndex }
  var acceptedKeySequence: [Character] { acceptedKeys }
  var nextExpectedKey: Character? { judgeState.expectedNext }
  var isComplete: Bool { judgeState.isComplete }
  var canAdvance: Bool { isComplete && currentTargetIndex + 1 < targets.count }
  var isLessonComplete: Bool { isComplete && currentTargetIndex == targets.count - 1 }
  var hasResumableProgress: Bool { hasStartedTiming && !isLessonComplete }
  var enteredText: String { composition.text }
  var composingPreview: String { composition.composingText }
  var targetSyllableProgress: [TargetSyllableProgress] {
    targetSyllableRanges.enumerated().map { offset, unit in
      let state: TargetSyllableProgress.State
      if completedJamoCount >= unit.range.upperBound {
        state = .completed
      } else if completedJamoCount > unit.range.lowerBound {
        state = .inProgress
      } else {
        state = .pending
      }
      return TargetSyllableProgress(
        offset: offset,
        character: unit.character,
        jamoRange: unit.range,
        state: state
      )
    }
  }
  var completedSyllableCount: Int {
    targetSyllableProgress.filter { $0.isHangul && $0.state == .completed }.count
  }
  var targetSyllableCount: Int {
    targetSyllableProgress.filter(\.isHangul).count
  }
  var acceptedJamoCount: Int {
    targetJamoCounts.prefix(currentTargetIndex).reduce(0, +) + judgeState.currentIndex
  }
  var accuracyPercent: Double {
    let totalInputs = acceptedJamoCount + mistakeCount
    guard totalInputs > 0 else { return 0 }
    return Double(acceptedJamoCount) / Double(totalInputs) * 100
  }
  var completedItemCount: Int {
    currentTargetIndex + (isComplete ? 1 : 0)
  }
  var activeDuration: TimeInterval {
    accumulatedActiveDuration
      + (activeStartedAt.map { max(0, now().timeIntervalSince($0)) } ?? 0)
  }
  var charactersPerMinute: Double {
    guard activeDuration > 0 else { return 0 }
    return Double(acceptedJamoCount) / (activeDuration / 60)
  }

  func input(_ key: Character) {
    guard !judgeState.isComplete else { return }
    startTimingIfNeeded()

    let evaluation = JamoSequenceJudge.evaluate(key, state: judgeState)
    judgeState = evaluation.state

    switch evaluation.result {
    case .correct(let completed):
      let previousPhase = composition.phase
      acceptedKeys.append(key)
      totalAcceptedInputCount += 1
      consecutiveMistakes = 0
      correctStreak += 1
      composition = HangulComposer.reduce(composition, event: .key(key))
      lastAcceptedKey = key
      shouldAnimateSyllableJoin = Self.isSyllableJoin(
        from: previousPhase,
        to: composition.phase
      )
      compositionRevision += 1
      feedback = completed ? .complete : .correct
      if completed {
        lastCompletedItem = SessionItemResolution(
          itemIndex: currentTargetIndex,
          hadMistake: currentTargetMistakeCount > 0,
          mistakeCount: currentTargetMistakeCount,
          mistakenJamoIndices: currentTargetMistakenJamoIndices
        )
        itemCompletionRevision += 1
        if isLessonComplete {
          pauseTiming()
        }
      }
      publishReaction(
        reactionForAcceptedInput(completedItem: completed)
      )
    case .incorrect(let expected):
      mistakeCount += 1
      consecutiveMistakes += 1
      currentTargetMistakeCount += 1
      currentTargetMistakenJamoIndices.insert(judgeState.currentIndex)
      correctStreak = 0
      feedback = .incorrect(expected: expected)
      publishReaction(
        consecutiveMistakes >= 3 ? .dizzy(expected: expected) : .mistake(expected: expected)
      )
    case .alreadyComplete:
      feedback = .complete
    }
    feedbackRevision += 1
  }

  func backspace() {
    guard !acceptedKeys.isEmpty else { return }
    acceptedKeys.removeLast()
    composition = HangulComposer.reduce(composition, event: .backspace)
    lastAcceptedKey = acceptedKeys.last
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    rebuildJudgeFromAcceptedKeys()
    correctStreak = 0
    feedback = .idle
    publishReaction(.idle)
    feedbackRevision += 1
  }

  func synchronizeOSIME(acceptedSequence: [Character]) {
    guard acceptedSequence.count <= targetJamoSequence.count,
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

  func recordConfirmedOSIMEMistake() {
    guard !judgeState.isComplete, let expected = judgeState.expectedNext else { return }
    startTimingIfNeeded()
    mistakeCount += 1
    consecutiveMistakes += 1
    currentTargetMistakeCount += 1
    currentTargetMistakenJamoIndices.insert(judgeState.currentIndex)
    correctStreak = 0
    feedback = .incorrect(expected: expected)
    publishReaction(
      consecutiveMistakes >= 3 ? .dizzy(expected: expected) : .mistake(expected: expected)
    )
    feedbackRevision += 1
  }

  func reset() {
    currentTargetIndex = 0
    target = targets[0]
    targetJamoSequence = makeJudge(for: target).expectedSequence
    acceptedKeys.removeAll(keepingCapacity: true)
    composition = CompositionState()
    lastAcceptedKey = nil
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    rebuildJudgeFromAcceptedKeys()
    feedback = .idle
    mistakeCount = 0
    consecutiveMistakes = 0
    totalAcceptedInputCount = 0
    currentTargetMistakeCount = 0
    currentTargetMistakenJamoIndices.removeAll(keepingCapacity: true)
    lastCompletedItem = nil
    correctStreak = 0
    reactionEvent = .idle
    reactionRevision += 1
    accumulatedActiveDuration = 0
    activeStartedAt = nil
    hasStartedTiming = false
    feedbackRevision += 1
  }

  func advance() {
    guard canAdvance else { return }
    currentTargetIndex += 1
    target = targets[currentTargetIndex]
    targetSyllableRanges = Self.makeTargetSyllableRanges(for: target)
    targetJamoSequence = makeJudge(for: target).expectedSequence
    acceptedKeys.removeAll(keepingCapacity: true)
    composition = CompositionState()
    lastAcceptedKey = nil
    shouldAnimateSyllableJoin = false
    compositionRevision += 1
    rebuildJudgeFromAcceptedKeys()
    feedback = .idle
    currentTargetMistakeCount = 0
    currentTargetMistakenJamoIndices.removeAll(keepingCapacity: true)
    feedbackRevision += 1
  }

  func pauseTiming() {
    guard let activeStartedAt else { return }
    accumulatedActiveDuration += max(0, now().timeIntervalSince(activeStartedAt))
    self.activeStartedAt = nil
  }

  func resumeTiming() {
    guard hasStartedTiming, activeStartedAt == nil, !isLessonComplete else { return }
    activeStartedAt = now()
  }

  func publishStretchReaction() {
    publishReaction(.stretch)
  }

  func checkpoint() -> PracticeSessionCheckpoint {
    PracticeSessionCheckpoint(
      currentTargetIndex: currentTargetIndex,
      acceptedKeys: String(acceptedKeys),
      mistakeCount: mistakeCount,
      currentTargetMistakeCount: currentTargetMistakeCount,
      currentTargetMistakenJamoIndices: currentTargetMistakenJamoIndices.sorted(),
      activeDuration: activeDuration
    )
  }

  func checkpointForNextTarget() -> PracticeSessionCheckpoint {
    guard canAdvance else { return checkpoint() }
    return PracticeSessionCheckpoint(
      currentTargetIndex: currentTargetIndex + 1,
      acceptedKeys: "",
      mistakeCount: mistakeCount,
      currentTargetMistakeCount: 0,
      currentTargetMistakenJamoIndices: [],
      activeDuration: activeDuration
    )
  }

  private func rebuildJudgeFromAcceptedKeys() {
    var rebuilt = makeJudge(for: target)
    for key in acceptedKeys {
      rebuilt = JamoSequenceJudge.evaluate(key, state: rebuilt).state
    }
    judgeState = rebuilt
  }

  private func makeJudge(for target: String) -> JamoJudgeState {
    guard let judge = try? JamoJudgeState(target: target) else {
      preconditionFailure("Validated practice target became invalid")
    }
    return judge
  }

  private func startTimingIfNeeded() {
    guard activeStartedAt == nil, !isLessonComplete else { return }
    hasStartedTiming = true
    activeStartedAt = now()
  }

  private func restore(_ checkpoint: PracticeSessionCheckpoint) {
    guard targets.indices.contains(checkpoint.currentTargetIndex),
      checkpoint.mistakeCount >= 0,
      checkpoint.currentTargetMistakeCount >= 0,
      checkpoint.activeDuration >= 0
    else { return }

    currentTargetIndex = checkpoint.currentTargetIndex
    target = targets[currentTargetIndex]
    targetSyllableRanges = Self.makeTargetSyllableRanges(for: target)
    targetJamoSequence = makeJudge(for: target).expectedSequence
    let candidateKeys = Array(checkpoint.acceptedKeys)
    acceptedKeys = candidateKeys

    var restoredJudge = makeJudge(for: target)
    var restoredComposition = CompositionState()
    var isValidPrefix = true
    for key in candidateKeys {
      let evaluation = JamoSequenceJudge.evaluate(key, state: restoredJudge)
      guard case .correct = evaluation.result else {
        isValidPrefix = false
        break
      }
      restoredJudge = evaluation.state
      restoredComposition = HangulComposer.reduce(restoredComposition, event: .key(key))
    }
    if !isValidPrefix {
      acceptedKeys.removeAll()
      restoredJudge = makeJudge(for: target)
      restoredComposition = CompositionState()
    }

    judgeState = restoredJudge
    composition = restoredComposition
    lastAcceptedKey = acceptedKeys.last
    mistakeCount = checkpoint.mistakeCount
    currentTargetMistakeCount = checkpoint.currentTargetMistakeCount
    currentTargetMistakenJamoIndices = Set(
      checkpoint.currentTargetMistakenJamoIndices.filter {
        targetJamoSequence.indices.contains($0)
      }
    )
    accumulatedActiveDuration = checkpoint.activeDuration
    hasStartedTiming =
      currentTargetIndex > 0 || !acceptedKeys.isEmpty || mistakeCount > 0
      || accumulatedActiveDuration > 0
    feedback = judgeState.isComplete ? .complete : .idle
    correctStreak = 0
    reactionEvent = .idle
  }

  private func reactionForAcceptedInput(completedItem: Bool) -> PracticeReactionEvent {
    if completedItem {
      return isLessonComplete && mistakeCount == 0 ? .perfectSession : .wordCompleted
    }

    if [5, 10, 20].contains(correctStreak) {
      return .comboMilestone(correctStreak)
    }

    if let syllableIndex = targetSyllableRanges.firstIndex(where: {
      $0.range.upperBound == completedJamoCount
    }) {
      return .syllableCompleted(index: syllableIndex)
    }

    return .correctJamo
  }

  private func publishReaction(_ event: PracticeReactionEvent) {
    reactionEvent = event
    reactionRevision += 1
  }

  private static func makeTargetSyllableRanges(
    for target: String
  ) -> [(character: Character, range: Range<Int>)] {
    var cursor = 0
    return target.map { character in
      guard let count = try? JamoDecomposer.keySequence(for: String(character)).count else {
        preconditionFailure("Validated target character became invalid")
      }
      let range = cursor..<(cursor + count)
      cursor += count
      return (character, range)
    }
  }

  private static func isSyllableJoin(
    from previousPhase: CompositionPhase,
    to nextPhase: CompositionPhase
  ) -> Bool {
    switch (previousPhase, nextPhase) {
    case (.cho, .choJung), (.choJung, .choJung), (.choJungJong, .choJung):
      true
    default:
      false
    }
  }
}
