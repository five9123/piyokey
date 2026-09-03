import HangulEngine
import UIKit
import XCTest

@testable import Hanco

@MainActor
final class PracticeSessionViewModelTests: XCTestCase {
  func testCorrectSequenceCompletesDokkaebiTarget() {
    let viewModel = PracticeSessionViewModel(target: "가나")

    for key in Array("ㄱㅏㄴ") {
      viewModel.input(key)
    }
    XCTAssertEqual(viewModel.enteredText, "간")
    XCTAssertFalse(viewModel.isComplete)

    viewModel.input("ㅏ")
    XCTAssertEqual(viewModel.enteredText, "가나")
    XCTAssertTrue(viewModel.isComplete)
    XCTAssertEqual(viewModel.feedback, .complete)
  }

  func testTargetSyllableProgressMarksOnlyFullyAcceptedSyllables() {
    let viewModel = PracticeSessionViewModel(target: "학교")

    XCTAssertEqual(viewModel.targetSyllableProgress.map(\.state), [.pending, .pending])
    XCTAssertEqual(viewModel.completedSyllableCount, 0)
    XCTAssertEqual(viewModel.targetSyllableCount, 2)

    viewModel.input("ㅎ")
    XCTAssertEqual(viewModel.targetSyllableProgress.map(\.state), [.inProgress, .pending])
    XCTAssertEqual(viewModel.reactionEvent, .correctJamo)

    viewModel.input("ㅏ")
    viewModel.input("ㄱ")
    XCTAssertEqual(viewModel.targetSyllableProgress.map(\.state), [.completed, .pending])
    XCTAssertEqual(viewModel.completedSyllableCount, 1)
    XCTAssertEqual(viewModel.reactionEvent, .syllableCompleted(index: 0))

    viewModel.input("ㄱ")
    XCTAssertEqual(viewModel.targetSyllableProgress.map(\.state), [.completed, .inProgress])
  }

  func testMascotReactionEventsCoverCombosMistakesWordsAndPerfectSession() {
    let comboModel = PracticeSessionViewModel(target: "가나다라마바사아자차카")
    let sequence = try! JamoDecomposer.keySequence(for: comboModel.target)

    for key in sequence.prefix(5) { comboModel.input(key) }
    XCTAssertEqual(comboModel.correctStreak, 5)
    XCTAssertEqual(comboModel.reactionEvent, .comboMilestone(5))

    for key in sequence.dropFirst(5).prefix(5) { comboModel.input(key) }
    XCTAssertEqual(comboModel.reactionEvent, .comboMilestone(10))

    for key in sequence.dropFirst(10).prefix(10) { comboModel.input(key) }
    XCTAssertEqual(comboModel.reactionEvent, .comboMilestone(20))

    comboModel.input("ㅂ")
    XCTAssertEqual(comboModel.correctStreak, 0)
    XCTAssertEqual(comboModel.reactionEvent, .mistake(expected: "ㅋ"))

    let perfect = PracticeSessionViewModel(target: "가")
    perfect.input("ㄱ")
    perfect.input("ㅏ")
    XCTAssertEqual(perfect.reactionEvent, .perfectSession)

    let recovered = PracticeSessionViewModel(target: "가")
    recovered.input("ㄴ")
    recovered.input("ㄱ")
    recovered.input("ㅏ")
    XCTAssertEqual(recovered.reactionEvent, .wordCompleted)
  }

  func testIncorrectInputIsCountedAndDoesNotPolluteComposition() {
    let viewModel = PracticeSessionViewModel(target: "가")

    viewModel.input("ㄴ")

    XCTAssertEqual(viewModel.enteredText, "")
    XCTAssertEqual(viewModel.completedJamoCount, 0)
    XCTAssertEqual(viewModel.mistakeCount, 1)
    XCTAssertEqual(viewModel.feedback, .incorrect(expected: "ㄱ"))
  }

  func testMascotTracksConsecutiveMistakesAcceptedInputsAndForegroundStretch() {
    let viewModel = PracticeSessionViewModel(target: "가")

    viewModel.input("ㄴ")
    viewModel.input("ㄴ")
    viewModel.input("ㄴ")
    XCTAssertEqual(viewModel.consecutiveMistakes, 3)
    XCTAssertEqual(viewModel.reactionEvent, .dizzy(expected: "ㄱ"))

    viewModel.input("ㄱ")
    XCTAssertEqual(viewModel.consecutiveMistakes, 0)
    XCTAssertEqual(viewModel.totalAcceptedInputCount, 1)

    viewModel.publishStretchReaction()
    XCTAssertEqual(viewModel.reactionEvent, .stretch)
  }

  func testBackspaceRewindsJudgeAcrossCarryover() {
    let viewModel = PracticeSessionViewModel(target: "가나")
    for key in Array("ㄱㅏㄴㅏ") {
      viewModel.input(key)
    }

    viewModel.backspace()

    XCTAssertEqual(viewModel.enteredText, "간")
    XCTAssertEqual(viewModel.completedJamoCount, 3)
    XCTAssertEqual(viewModel.nextExpectedKey, "ㅏ")
    XCTAssertFalse(viewModel.isComplete)
  }

  func testShiftJamoIsOneLogicalInput() {
    let viewModel = PracticeSessionViewModel(target: "꿀")

    XCTAssertEqual(viewModel.nextExpectedKey, "ㄲ")
    for key in Array("ㄲㅜㄹ") {
      viewModel.input(key)
    }

    XCTAssertTrue(viewModel.isComplete)
    XCTAssertEqual(viewModel.completedJamoCount, 3)
    XCTAssertEqual(viewModel.enteredText, "꿀")
  }

  func testResetClearsProgressAndMistakes() {
    let viewModel = PracticeSessionViewModel(target: "가")
    viewModel.input("ㄴ")
    viewModel.input("ㄱ")

    viewModel.reset()

    XCTAssertEqual(viewModel.enteredText, "")
    XCTAssertEqual(viewModel.completedJamoCount, 0)
    XCTAssertEqual(viewModel.mistakeCount, 0)
    XCTAssertEqual(viewModel.feedback, .idle)
  }

  func testResetRestoresFirstTargetSyllableProgressAfterLastCard() throws {
    let viewModel = PracticeSessionViewModel(targets: ["가", "학교"])

    for key in try JamoDecomposer.keySequence(for: "가") {
      viewModel.input(key)
    }
    viewModel.advance()
    for key in try JamoDecomposer.keySequence(for: "학교") {
      viewModel.input(key)
    }

    XCTAssertEqual(viewModel.targetSyllableProgress.map(\.character), Array("학교"))

    viewModel.reset()

    XCTAssertEqual(viewModel.target, "가")
    XCTAssertEqual(viewModel.targetSyllableProgress.map(\.character), Array("가"))
    XCTAssertEqual(viewModel.targetSyllableProgress.map(\.state), [.pending])
    XCTAssertEqual(viewModel.targetSyllableCount, 1)
    XCTAssertEqual(viewModel.completedSyllableCount, 0)
  }

  func testCompletedProblemAdvancesWithoutClearingLessonMistakes() {
    let viewModel = PracticeSessionViewModel(targets: ["가", "나"])
    viewModel.input("ㄴ")
    for key in Array("ㄱㅏ") {
      viewModel.input(key)
    }

    XCTAssertTrue(viewModel.canAdvance)
    viewModel.advance()

    XCTAssertEqual(viewModel.currentTargetIndex, 1)
    XCTAssertEqual(viewModel.target, "나")
    XCTAssertEqual(viewModel.enteredText, "")
    XCTAssertEqual(viewModel.nextExpectedKey, "ㄴ")
    XCTAssertEqual(viewModel.mistakeCount, 1)
  }

  func testNextTargetCheckpointCanBePersistedBeforeVisibleAdvance() {
    let viewModel = PracticeSessionViewModel(targets: ["가", "나"])
    viewModel.input("ㄷ")
    for key in Array("ㄱㅏ") {
      viewModel.input(key)
    }

    let restored = PracticeSessionViewModel(
      targets: ["가", "나"],
      checkpoint: viewModel.checkpointForNextTarget()
    )

    XCTAssertEqual(viewModel.currentTargetIndex, 0)
    XCTAssertEqual(restored.currentTargetIndex, 1)
    XCTAssertEqual(restored.target, "나")
    XCTAssertEqual(restored.enteredText, "")
    XCTAssertEqual(restored.mistakeCount, 1)
  }

  func testCompletedSessionReportsJamoAccuracyAndItemCount() {
    let viewModel = PracticeSessionViewModel(targets: ["가", "나"])
    viewModel.input("ㄴ")
    for key in Array("ㄱㅏ") {
      viewModel.input(key)
    }
    viewModel.advance()
    for key in Array("ㄴㅏ") {
      viewModel.input(key)
    }

    XCTAssertTrue(viewModel.isLessonComplete)
    XCTAssertEqual(viewModel.acceptedJamoCount, 4)
    XCTAssertEqual(viewModel.accuracyPercent, 80, accuracy: 0.001)
    XCTAssertEqual(viewModel.completedItemCount, 2)
  }

  func testCompletionResolutionTracksMistakeStatePerItem() {
    let viewModel = PracticeSessionViewModel(targets: ["가", "나"])
    viewModel.input("ㄴ")
    for key in Array("ㄱㅏ") {
      viewModel.input(key)
    }

    XCTAssertEqual(
      viewModel.lastCompletedItem,
      SessionItemResolution(
        itemIndex: 0,
        hadMistake: true,
        mistakeCount: 1,
        mistakenJamoIndices: [0]
      )
    )
    XCTAssertEqual(viewModel.itemCompletionRevision, 1)

    viewModel.advance()
    for key in Array("ㄴㅏ") {
      viewModel.input(key)
    }

    XCTAssertEqual(
      viewModel.lastCompletedItem,
      SessionItemResolution(
        itemIndex: 1,
        hadMistake: false,
        mistakeCount: 0,
        mistakenJamoIndices: []
      )
    )
    XCTAssertEqual(viewModel.itemCompletionRevision, 2)
  }

  func testInputLatencyMonitorCalculatesP95AndKeepsSampleLimit() {
    let monitor = InputLatencyMonitor(sampleLimit: 4)

    for milliseconds in [10.0, 20.0, 30.0, 40.0, 50.0] {
      monitor.beginInput(timestamp: 1)
      monitor.finishPendingInputs(timestamp: 1 + milliseconds / 1_000)
    }

    XCTAssertEqual(monitor.sampleCount, 4)
    XCTAssertEqual(monitor.p95Milliseconds ?? 0, 50, accuracy: 0.001)
  }

  func testLatencyMonitorCompletesConcurrentRolloverInputsOnSameFrame() {
    let monitor = InputLatencyMonitor()

    monitor.beginInput(timestamp: 1)
    monitor.beginInput(timestamp: 1.005)
    monitor.finishPendingInputs(timestamp: 1.020)

    XCTAssertEqual(monitor.sampleCount, 2)
    XCTAssertEqual(monitor.p95Milliseconds ?? 0, 20, accuracy: 0.001)
  }

  func testRolloverTouchViewAllowsNonexclusiveMultitouch() {
    let control = RolloverKeyboardTouchView(frame: .zero)

    XCTAssertTrue(control.isMultipleTouchEnabled)
    XCTAssertFalse(control.isExclusiveTouch)
  }

  func testKeyboardTouchResolverRoutesVisualGapsToNearestKey() {
    let targets = [
      KeyboardTouchTarget(
        action: .character("ㄱ"),
        frame: CGRect(x: 0, y: 0, width: 30, height: 50)
      ),
      KeyboardTouchTarget(
        action: .character("ㅏ"),
        frame: CGRect(x: 35, y: 0, width: 30, height: 50)
      ),
    ]

    XCTAssertEqual(
      KeyboardTouchTargetResolver.action(at: CGPoint(x: 32, y: 25), targets: targets),
      .character("ㄱ")
    )
    XCTAssertEqual(
      KeyboardTouchTargetResolver.action(at: CGPoint(x: 33, y: 25), targets: targets),
      .character("ㅏ")
    )
  }

  func testKeyboardTouchResolverRejectsTouchesOutsideSlop() {
    let targets = [
      KeyboardTouchTarget(
        action: .character("ㄱ"),
        frame: CGRect(x: 10, y: 10, width: 30, height: 50)
      )
    ]

    XCTAssertNil(
      KeyboardTouchTargetResolver.action(at: CGPoint(x: 0, y: 30), targets: targets)
    )
    XCTAssertEqual(
      KeyboardTouchTargetResolver.action(at: CGPoint(x: 7, y: 30), targets: targets),
      .character("ㄱ")
    )
  }

  func testCompositionAnimationMetadataTracksOnlyAcceptedStateChanges() {
    let viewModel = PracticeSessionViewModel(target: "가")

    viewModel.input("ㄴ")
    XCTAssertEqual(viewModel.compositionRevision, 0)
    XCTAssertNil(viewModel.lastAcceptedKey)

    viewModel.input("ㄱ")
    XCTAssertEqual(viewModel.compositionRevision, 1)
    XCTAssertEqual(viewModel.lastAcceptedKey, "ㄱ")
    XCTAssertFalse(viewModel.shouldAnimateSyllableJoin)

    viewModel.input("ㅏ")
    XCTAssertEqual(viewModel.compositionRevision, 2)
    XCTAssertEqual(viewModel.lastAcceptedKey, "ㅏ")
    XCTAssertTrue(viewModel.shouldAnimateSyllableJoin)

    viewModel.backspace()
    XCTAssertEqual(viewModel.compositionRevision, 3)
    XCTAssertEqual(viewModel.lastAcceptedKey, "ㄱ")
    XCTAssertFalse(viewModel.shouldAnimateSyllableJoin)
  }

  func testKeyboardOptionsDefaultOnForEveryDeviceAndAllowGraduationMode() {
    let defaults = HangulKeyboardOptions()
    XCTAssertTrue(defaults.showsKeyGuide)
    XCTAssertTrue(defaults.showsRomanHints)
    XCTAssertTrue(defaults.hapticsEnabled)

    let graduationMode = HangulKeyboardOptions(
      showsKeyGuide: false,
      showsRomanHints: false,
      hapticsEnabled: false
    )
    XCTAssertFalse(graduationMode.showsKeyGuide)
    XCTAssertFalse(graduationMode.showsRomanHints)
    XCTAssertFalse(graduationMode.hapticsEnabled)
  }

  func testOSIMEInputPanelPolicyKeepsRequestedRecoveryChromeIPadOnly() {
    XCTAssertTrue(OSIMEInputPanelPolicy.showsVisibleFocusRecovery(requested: true, on: .pad))
    XCTAssertFalse(OSIMEInputPanelPolicy.showsVisibleFocusRecovery(requested: true, on: .phone))
    XCTAssertFalse(OSIMEInputPanelPolicy.showsVisibleFocusRecovery(requested: false, on: .pad))
  }

  func testPhysicalKeyboardGuidePolicyIsIPadOnly() {
    XCTAssertTrue(PhysicalKeyboardGuidePolicy.isVisible(on: .pad))
    XCTAssertFalse(PhysicalKeyboardGuidePolicy.isVisible(on: .phone))
  }

  func testCompositionAnimationMetadataCoversCompoundVowelAndCarryover() {
    let compoundVowel = PracticeSessionViewModel(target: "외")
    for key in Array("ㅇㅗ") {
      compoundVowel.input(key)
    }
    compoundVowel.input("ㅣ")
    XCTAssertEqual(compoundVowel.enteredText, "외")
    XCTAssertTrue(compoundVowel.shouldAnimateSyllableJoin)

    let carryover = PracticeSessionViewModel(target: "가나")
    for key in Array("ㄱㅏㄴ") {
      carryover.input(key)
    }
    carryover.input("ㅏ")
    XCTAssertEqual(carryover.enteredText, "가나")
    XCTAssertTrue(carryover.shouldAnimateSyllableJoin)
  }

  func testCheckpointRestoresCurrentProblemInputMistakesAndDuration() {
    var now = Date(timeIntervalSince1970: 100)
    let model = PracticeSessionViewModel(targets: ["가", "나"], now: { now })
    model.input("ㄴ")
    model.input("ㄱ")
    now = now.addingTimeInterval(4)
    model.pauseTiming()

    let restored = PracticeSessionViewModel(
      targets: ["가", "나"],
      checkpoint: model.checkpoint(),
      now: { now }
    )

    XCTAssertEqual(restored.currentTargetIndex, 0)
    XCTAssertEqual(restored.enteredText, "ㄱ")
    XCTAssertEqual(restored.mistakeCount, 1)
    XCTAssertEqual(restored.completedJamoCount, 1)
    XCTAssertEqual(restored.activeDuration, 4, accuracy: 0.001)
    XCTAssertTrue(restored.hasResumableProgress)
  }

  func testOnlyStartedUnfinishedSessionsHaveResumableProgress() {
    let model = PracticeSessionViewModel(targets: ["가", "나"])
    XCTAssertFalse(model.hasResumableProgress)
    model.input("ㄴ")
    XCTAssertTrue(model.hasResumableProgress)
    model.input("ㄱ")
    model.input("ㅏ")
    XCTAssertTrue(model.hasResumableProgress)
    model.advance()
    model.input("ㄴ")
    model.input("ㅏ")
    XCTAssertFalse(model.hasResumableProgress)
    model.reset()
    XCTAssertFalse(model.hasResumableProgress)
  }

  func testPausedTimingExcludesBackgroundTimeAndResumesFromForeground() {
    var now = Date(timeIntervalSince1970: 200)
    let model = PracticeSessionViewModel(targets: ["가"], now: { now })
    model.input("ㄱ")
    now = now.addingTimeInterval(2)
    model.pauseTiming()

    now = now.addingTimeInterval(30)
    XCTAssertEqual(model.activeDuration, 2, accuracy: 0.001)

    model.resumeTiming()
    now = now.addingTimeInterval(3)
    model.input("ㅏ")
    XCTAssertEqual(model.activeDuration, 5, accuracy: 0.001)
    XCTAssertEqual(model.charactersPerMinute, 24, accuracy: 0.001)
  }

  func testOSIMESynchronizationCanAdvanceAndRewindWithoutLosingSessionState() {
    let model = PracticeSessionViewModel(targets: ["가나"])

    model.synchronizeOSIME(acceptedSequence: Array("ㄱㅏㄴ"))
    XCTAssertEqual(model.enteredText, "간")
    XCTAssertEqual(model.completedJamoCount, 3)

    model.synchronizeOSIME(acceptedSequence: Array("ㄱㅏ"))
    XCTAssertEqual(model.enteredText, "가")
    XCTAssertEqual(model.completedJamoCount, 2)

    model.synchronizeOSIME(acceptedSequence: Array("ㄱㅏㄴㅏ"))
    XCTAssertTrue(model.isComplete)
    XCTAssertEqual(model.enteredText, "가나")
  }

  func testOSIMEPhysicalKeyboardSnapshotsDoNotDuplicateMarkedCommitOrBackspace() {
    let model = PracticeSessionViewModel(targets: ["한국 사람"])
    let markedSnapshot = Array("ㅎㅏㄴㄱㅜㄱ ㅅㅏ")

    model.synchronizeOSIME(acceptedSequence: markedSnapshot)
    XCTAssertEqual(model.enteredText, "한국 사")
    XCTAssertEqual(model.totalAcceptedInputCount, markedSnapshot.count)

    model.synchronizeOSIME(acceptedSequence: markedSnapshot)
    XCTAssertEqual(model.enteredText, "한국 사")
    XCTAssertEqual(model.totalAcceptedInputCount, markedSnapshot.count)

    model.synchronizeOSIME(acceptedSequence: Array("ㅎㅏㄴㄱㅜㄱ "))
    XCTAssertEqual(model.enteredText, "한국 ")
    XCTAssertEqual(model.mistakeCount, 0)

    model.synchronizeOSIME(acceptedSequence: Array("ㅎㅏㄴㄱㅜㄱ ㅅㅏㄹㅏㅁ"))
    XCTAssertTrue(model.isComplete)
    XCTAssertEqual(model.enteredText, "한국 사람")
    XCTAssertEqual(model.mistakeCount, 0)
  }

  func testOSIMETextJudgeReplaysCheonjiinMarkedAndCommittedSnapshotsWithoutASCIIWarning()
    throws
  {
    struct Snapshot {
      let label: String
      let committed: String
      let marked: String?
      let expectedScalars: [UInt32]
      let expectedStatus: OSIMETextJudgeStatus
      let expectedAcceptedSequence: [Character]
    }

    // TYP-73's iPhone probe observed the Korean 10-Key replacement path as
    // ㄴ -> ㄹ -> ㄹㆍ -> 러 -> 레. UITextField may expose the in-flight
    // document as marked text before delivering the final committed syllable.
    let snapshots = [
      Snapshot(
        label: "grouped consonant precursor",
        committed: "",
        marked: "ㄴ",
        expectedScalars: [0x3134],
        expectedStatus: .composingMismatch,
        expectedAcceptedSequence: []
      ),
      Snapshot(
        label: "target consonant",
        committed: "",
        marked: "ㄹ",
        expectedScalars: [0x3139],
        expectedStatus: .matching(completed: false, isComposing: true),
        expectedAcceptedSequence: Array("ㄹ")
      ),
      Snapshot(
        label: "cheonjiin dot",
        committed: "",
        marked: "ㄹㆍ",
        expectedScalars: [0x3139, 0x318D],
        expectedStatus: .composingMismatch,
        expectedAcceptedSequence: []
      ),
      Snapshot(
        label: "intermediate eo",
        committed: "",
        marked: "러",
        expectedScalars: [0xB7EC],
        expectedStatus: .composingMismatch,
        expectedAcceptedSequence: []
      ),
      Snapshot(
        label: "marked final syllable",
        committed: "",
        marked: "레",
        expectedScalars: [0xB808],
        expectedStatus: .matching(completed: false, isComposing: true),
        expectedAcceptedSequence: Array("ㄹㅔ")
      ),
      Snapshot(
        label: "committed final syllable",
        committed: "레",
        marked: nil,
        expectedScalars: [0xB808],
        expectedStatus: .matching(completed: false, isComposing: false),
        expectedAcceptedSequence: Array("ㄹㅔ")
      ),
    ]

    for snapshot in snapshots {
      XCTAssertEqual(
        (snapshot.committed + (snapshot.marked ?? "")).unicodeScalars.map(\.value),
        snapshot.expectedScalars,
        snapshot.label
      )
      let evaluation = try OSIMETextJudge.evaluate(
        target: "레전드",
        committedText: snapshot.committed,
        markedText: snapshot.marked
      )
      XCTAssertEqual(evaluation.status, snapshot.expectedStatus, snapshot.label)
      XCTAssertEqual(
        evaluation.acceptedSequence,
        snapshot.expectedAcceptedSequence,
        snapshot.label
      )
    }
  }

  func testOSIMETextJudgeIgnoresUnconfirmedASCIIWhileCommittedEnglishStillWarns() throws {
    let markedIntermediate = try OSIMETextJudge.evaluate(
      target: "레전드",
      committedText: "",
      markedText: "1"
    )
    XCTAssertEqual(markedIntermediate.status, .composingMismatch)
    XCTAssertEqual(markedIntermediate.acceptedSequence, [])

    let committedEnglish = try OSIMETextJudge.evaluate(
      target: "레전드",
      committedText: "q"
    )
    XCTAssertEqual(committedEnglish.status, .unsupportedASCIIInput)
    XCTAssertEqual(committedEnglish.acceptedSequence, [])
  }

  func testConfirmedOSIMEMistakeCountsOnceWithoutPollutingComposition() {
    let model = PracticeSessionViewModel(target: "가")

    model.recordConfirmedOSIMEMistake()

    XCTAssertEqual(model.mistakeCount, 1)
    XCTAssertEqual(model.completedJamoCount, 0)
    XCTAssertEqual(model.enteredText, "")
    XCTAssertEqual(model.feedback, .incorrect(expected: "ㄱ"))
  }

  func testOSIMEReachableCheonjiinCommittedVowelsDoNotCountMistakesOrRollback() throws {
    let model = PracticeSessionViewModel(targets: ["돼지"])

    let snapshots = [
      (committed: "ㄷㆍ", acceptedText: "ㄷ"),
      (committed: "도", acceptedText: "도"),
      (committed: "되", acceptedText: "도"),
      (committed: "돠", acceptedText: "도"),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: model.target,
        committedText: snapshot.committed
      )
      model.synchronizeOSIME(acceptedSequence: evaluation.acceptedSequence)
      if case .confirmedMismatch = evaluation.status {
        XCTFail("reachable snapshot would trigger rollback: \(snapshot.committed)")
      }
      XCTAssertEqual(model.enteredText, snapshot.acceptedText, snapshot.committed)
      XCTAssertEqual(model.mistakeCount, 0, snapshot.committed)
    }

    let completed = try OSIMETextJudge.evaluate(target: "돼지", committedText: "돼지")
    model.synchronizeOSIME(acceptedSequence: completed.acceptedSequence)
    XCTAssertTrue(model.isComplete)
    XCTAssertEqual(model.enteredText, "돼지")
    XCTAssertEqual(model.mistakeCount, 0)
  }

  func testOSIMEStandaloneDotFirstVowelsDoNotCountMistakesOrRollback() throws {
    for (target, committed) in [("ㅓ", "ㆍ"), ("ㅔ", "ㆍㅣ"), ("ㅕ", "ㆍㆍ")] {
      let model = PracticeSessionViewModel(target: target)
      let evaluation = try OSIMETextJudge.evaluate(target: target, committedText: committed)

      model.synchronizeOSIME(acceptedSequence: evaluation.acceptedSequence)

      XCTAssertEqual(evaluation.status, .composingMismatch, target)
      XCTAssertEqual(model.enteredText, "", target)
      XCTAssertEqual(model.mistakeCount, 0, target)
    }
  }

  func testOSIMEReachableCheonjiinConsonantCyclesDoNotCountMistakesOrRollback() throws {
    let snapshots = [
      (target: "하", committed: "ㅅ", acceptedText: ""),
      (target: "대형", committed: "대ㅅ", acceptedText: "대"),
      (target: "대형", committed: "댓", acceptedText: "대"),
      (target: "달", committed: "단", acceptedText: "다"),
      (target: "밤", committed: "방", acceptedText: "바"),
    ]

    for snapshot in snapshots {
      let model = PracticeSessionViewModel(target: snapshot.target)
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )

      model.synchronizeOSIME(acceptedSequence: evaluation.acceptedSequence)

      XCTAssertEqual(evaluation.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(model.enteredText, snapshot.acceptedText, snapshot.target)
      XCTAssertEqual(model.mistakeCount, 0, snapshot.target)
    }
  }

  func testOSIMEBatchimBoundaryIntermediatesDoNotCountMistakesOrRollback() throws {
    let snapshots = [
      (target: "일해", committed: "잀", acceptedText: "일"),
      (target: "급해", committed: "긊", acceptedText: "급"),
      (target: "번째", committed: "벉", acceptedText: "번"),
      (target: "앓다", committed: "앐", acceptedText: "알"),
      (target: "읊다", committed: "읇", acceptedText: "을"),
      (target: "괜찮아", committed: "괜찬ㅅ", acceptedText: "괜찬"),
      (target: "많이", committed: "만ㅅ", acceptedText: "만"),
      (target: "삶", committed: "살ㅇ", acceptedText: "살"),
    ]

    for snapshot in snapshots {
      let model = PracticeSessionViewModel(target: snapshot.target)
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )

      model.synchronizeOSIME(acceptedSequence: evaluation.acceptedSequence)

      XCTAssertEqual(evaluation.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(model.enteredText, snapshot.acceptedText, snapshot.target)
      XCTAssertEqual(model.mistakeCount, 0, snapshot.target)
    }
  }

  func testOSIMESameRecipeBoundaryCyclesAndDoubleDotPrefixesDoNotRollback() throws {
    let snapshots = [
      (target: "학교", committed: "핰", acceptedText: "학"),
      (target: "학교", committed: "핚", acceptedText: "학"),
      (target: "요", committed: "ㅇ\u{11A2}", acceptedText: "ㅇ"),
      (target: "예", committed: "ㅇ\u{11A2}ㅣ", acceptedText: "ㅇ"),
      (target: "교", committed: "ㄱ\u{11A2}", acceptedText: "ㄱ"),
    ]

    for snapshot in snapshots {
      let model = PracticeSessionViewModel(target: snapshot.target)
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )

      model.synchronizeOSIME(acceptedSequence: evaluation.acceptedSequence)

      XCTAssertEqual(evaluation.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(model.enteredText, snapshot.acceptedText, snapshot.target)
      XCTAssertEqual(model.mistakeCount, 0, snapshot.target)
    }

    let wrongCompleted = try OSIMETextJudge.evaluate(
      target: "학교",
      committedText: "학고"
    )
    XCTAssertEqual(wrongCompleted.status, .confirmedMismatch(expectedIndex: 4))

    let wrongRawStroke = try OSIMETextJudge.evaluate(
      target: "요",
      committedText: "ㅇ\u{11A2}ㅣ"
    )
    XCTAssertEqual(wrongRawStroke.status, .confirmedMismatch(expectedIndex: 1))
  }

  func testOSIMEWordPrefixDoubleDotStateCanCompleteEoyoWithoutRollback() throws {
    let model = PracticeSessionViewModel(target: "어요")
    let intermediate = try OSIMETextJudge.evaluate(
      target: "어요",
      committedText: "어ㅇ\u{11A2}"
    )

    model.synchronizeOSIME(acceptedSequence: intermediate.acceptedSequence)

    XCTAssertEqual(intermediate.status, .composingMismatch)
    XCTAssertEqual(model.enteredText, "엉")
    XCTAssertEqual(model.mistakeCount, 0)

    let completed = try OSIMETextJudge.evaluate(target: "어요", committedText: "어요")
    model.synchronizeOSIME(acceptedSequence: completed.acceptedSequence)

    XCTAssertTrue(model.isComplete)
    XCTAssertEqual(model.enteredText, "어요")
    XCTAssertEqual(model.mistakeCount, 0)
  }

  func testKoreanKeyboardAvailabilityMatchesOnlyKoreanLanguageModes() {
    XCTAssertTrue(
      KoreanKeyboardAvailability.containsKorean(languages: ["ja-JP", "ko-KR", "en-US"])
    )
    XCTAssertTrue(KoreanKeyboardAvailability.containsKorean(languages: ["ko"]))
    XCTAssertFalse(
      KoreanKeyboardAvailability.containsKorean(languages: ["ja-JP", nil, "en-US"])
    )
  }

  func testKorean10KeyGoldenRecipesCoverEveryCompatibilityJamo() {
    let expected: [Character: [Korean10KeyKey]] = [
      "ㄱ": [.giyeok], "ㅋ": [.giyeok, .giyeok],
      "ㄲ": [.giyeok, .giyeok, .giyeok],
      "ㄴ": [.nieun], "ㄹ": [.nieun, .nieun],
      "ㄷ": [.digeut], "ㅌ": [.digeut, .digeut],
      "ㄸ": [.digeut, .digeut, .digeut],
      "ㅂ": [.bieup], "ㅍ": [.bieup, .bieup],
      "ㅃ": [.bieup, .bieup, .bieup],
      "ㅅ": [.siot], "ㅎ": [.siot, .siot],
      "ㅆ": [.siot, .siot, .siot],
      "ㅈ": [.jieut], "ㅊ": [.jieut, .jieut],
      "ㅉ": [.jieut, .jieut, .jieut],
      "ㅇ": [.ieung], "ㅁ": [.ieung, .ieung],
      "ㅣ": [.vertical], "ㅡ": [.horizontal],
      "ㅏ": [.vertical, .dot], "ㅑ": [.vertical, .dot, .dot],
      "ㅓ": [.dot, .vertical], "ㅕ": [.dot, .dot, .vertical],
      "ㅗ": [.dot, .horizontal], "ㅛ": [.dot, .dot, .horizontal],
      "ㅜ": [.horizontal, .dot], "ㅠ": [.horizontal, .dot, .dot],
      "ㅐ": [.vertical, .dot, .vertical],
      "ㅒ": [.vertical, .dot, .dot, .vertical],
      "ㅔ": [.dot, .vertical, .vertical],
      "ㅖ": [.dot, .dot, .vertical, .vertical],
      "ㅘ": [.dot, .horizontal, .vertical, .dot],
      "ㅙ": [.dot, .horizontal, .vertical, .dot, .vertical],
      "ㅚ": [.dot, .horizontal, .vertical],
      "ㅝ": [.horizontal, .dot, .dot, .vertical],
      "ㅞ": [.horizontal, .dot, .dot, .vertical, .vertical],
      "ㅟ": [.horizontal, .dot, .vertical],
      "ㅢ": [.horizontal, .vertical],
      " ": [.space],
    ]

    XCTAssertEqual(expected.count, 41)
    for (jamo, recipe) in expected {
      XCTAssertEqual(Korean10KeyInterpreter.recipe(for: jamo), recipe, "recipe for \(jamo)")
    }
    XCTAssertNil(Korean10KeyInterpreter.recipe(for: "A"))
  }

  func testKorean10KeyFlickMappingCoversVowelsAndConsonantAlternatives() {
    let expected: [(Korean10KeyKey, Korean10KeyFlickDirection, Character?)] = [
      (.vertical, .left, "ㅓ"), (.vertical, .right, "ㅏ"),
      (.vertical, .up, "ㅕ"), (.vertical, .down, "ㅑ"),
      (.dot, .left, "ㅓ"), (.dot, .right, "ㅏ"),
      (.dot, .up, "ㅗ"), (.dot, .down, "ㅜ"),
      (.horizontal, .left, "ㅠ"), (.horizontal, .right, "ㅛ"),
      (.horizontal, .up, "ㅗ"), (.horizontal, .down, "ㅜ"),
      (.giyeok, .left, "ㄱ"), (.giyeok, .right, "ㅋ"), (.giyeok, .down, "ㄲ"),
      (.nieun, .left, "ㄴ"), (.nieun, .right, "ㄹ"), (.nieun, .down, nil),
      (.digeut, .left, "ㄷ"), (.digeut, .right, "ㅌ"), (.digeut, .down, "ㄸ"),
      (.bieup, .left, "ㅂ"), (.bieup, .right, "ㅍ"), (.bieup, .down, "ㅃ"),
      (.siot, .left, "ㅅ"), (.siot, .right, "ㅎ"), (.siot, .down, "ㅆ"),
      (.jieut, .left, "ㅈ"), (.jieut, .right, "ㅊ"), (.jieut, .down, "ㅉ"),
      (.ieung, .left, "ㅇ"), (.ieung, .right, "ㅁ"), (.ieung, .down, nil),
    ]

    for (key, direction, jamo) in expected {
      XCTAssertEqual(
        Korean10KeyFlickMapping.completedJamo(for: key, direction: direction),
        jamo,
        "\(key) \(direction)"
      )
    }
    for key in Korean10KeyKey.allCases.filter(\.supportsFlick) {
      if key != .vertical, key != .dot, key != .horizontal {
        XCTAssertNil(Korean10KeyFlickMapping.completedJamo(for: key, direction: .up))
      }
    }
  }

  func testKorean10KeyFlickGestureThresholdSeparatesTapFlickAndInvalidMotion() {
    XCTAssertEqual(
      Korean10KeyFlickGestureResolver.interpretation(
        translation: CGSize(width: 23.9, height: 0),
        duration: 0.1
      ),
      .tap
    )
    XCTAssertEqual(
      Korean10KeyFlickGestureResolver.interpretation(
        translation: .zero,
        duration: 1
      ),
      .tap,
      "A stationary long press must preserve the existing tap behavior"
    )
    XCTAssertEqual(
      Korean10KeyFlickGestureResolver.interpretation(
        translation: CGSize(width: -30, height: 2),
        duration: 0.2
      ),
      .flick(.left)
    )
    XCTAssertEqual(
      Korean10KeyFlickGestureResolver.interpretation(
        translation: CGSize(width: 2, height: -30),
        duration: 0.2
      ),
      .flick(.up)
    )
    XCTAssertEqual(
      Korean10KeyFlickGestureResolver.interpretation(
        translation: CGSize(width: 30, height: 30),
        duration: 0.2
      ),
      .invalidFlick
    )
    XCTAssertEqual(
      Korean10KeyFlickGestureResolver.interpretation(
        translation: CGSize(width: 30, height: 0),
        duration: 0.451
      ),
      .invalidFlick
    )
  }

  func testKorean10KeyCompletedFlickUsesOnlyGoldenRecipePrefixes() throws {
    let flickVowels: [Character] = ["ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ"]
    let vowels: [Character] = [
      "ㅣ", "ㅡ", "ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅐ", "ㅒ", "ㅔ", "ㅖ",
      "ㅘ", "ㅙ", "ㅚ", "ㅝ", "ㅞ", "ㅟ", "ㅢ",
    ]

    for expected in vowels {
      var interpreter = Korean10KeyInterpreter()
      let recipe = try XCTUnwrap(Korean10KeyInterpreter.recipe(for: expected))
      let shortcut = flickVowels
        .compactMap { jamo -> (Character, [Korean10KeyKey])? in
          guard let candidate = Korean10KeyInterpreter.recipe(for: jamo),
            recipe.starts(with: candidate)
          else { return nil }
          return (jamo, candidate)
        }
        .max { $0.1.count < $1.1.count }

      var consumed = 0
      if let shortcut {
        let result = interpreter.inputCompletedJamo(shortcut.0, expecting: expected)
        consumed = shortcut.1.count
        if consumed == recipe.count {
          XCTAssertEqual(result, .committed(expected), String(expected))
          continue
        }
        guard case .pending = result else {
          return XCTFail("Expected pending flick prefix for \(expected), got \(result)")
        }
      }

      for (index, key) in recipe.dropFirst(consumed).enumerated() {
        let result = interpreter.input(key, expecting: expected)
        if index == recipe.count - consumed - 1 {
          XCTAssertEqual(result, .committed(expected), String(expected))
        }
      }
    }

    var invalidSplice = Korean10KeyInterpreter()
    XCTAssertEqual(
      invalidSplice.input(.vertical, expecting: "ㅒ"),
      .pending(display: "ㅣ")
    )
    XCTAssertEqual(
      invalidSplice.inputCompletedJamo("ㅓ", expecting: "ㅒ"),
      .incorrect(expected: "ㅒ")
    )
    XCTAssertEqual(invalidSplice.nextKey(for: "ㅒ"), .vertical)
  }

  func testKorean10KeyCompletedConsonantFlickCanForceSameGroupBoundary() {
    var interpreter = Korean10KeyInterpreter()

    XCTAssertEqual(interpreter.inputCompletedJamo("ㄴ", expecting: "ㄴ"), .committed("ㄴ"))
    XCTAssertEqual(interpreter.nextKey(for: "ㄴ"), .next)
    XCTAssertEqual(interpreter.inputCompletedJamo("ㄴ", expecting: "ㄴ"), .committed("ㄴ"))
    XCTAssertEqual(interpreter.inputCompletedJamo(nil, expecting: "ㅏ"), .incorrect(expected: "ㅏ"))
  }

  func testKorean10KeyEmitsOnlyCompletedJamoIntoSharedJudge() throws {
    for target in ["가나", "꽤", "뼈", "휘", "의자", "언니", "띄어 쓰기", "외국"] {
      let model = PracticeSessionViewModel(target: target)
      var interpreter = Korean10KeyInterpreter()
      for jamo in try JamoDecomposer.keySequence(for: target) {
        let recipe = try XCTUnwrap(Korean10KeyInterpreter.recipe(for: jamo))
        if interpreter.nextKey(for: model.nextExpectedKey) == .next {
          XCTAssertEqual(
            interpreter.input(.next, expecting: model.nextExpectedKey),
            .separatorAccepted
          )
        }
        for (index, key) in recipe.enumerated() {
          let result = interpreter.input(key, expecting: model.nextExpectedKey)
          if index == recipe.count - 1 {
            guard case .committed(let emitted) = result else {
              return XCTFail("Expected committed \(jamo), got \(result)")
            }
            XCTAssertEqual(emitted, jamo)
            model.input(emitted)
          } else if case .pending = result {
            XCTAssertEqual(model.nextExpectedKey, jamo)
          } else {
            XCTFail("Recipe for \(jamo) committed too early")
          }
        }
      }
      XCTAssertTrue(model.isComplete, target)
      XCTAssertEqual(model.enteredText, target)
      XCTAssertEqual(model.mistakeCount, 0)
    }
  }

  func testKorean10KeyBackspaceRewindsPendingStrokeBeforeHangul() {
    var interpreter = Korean10KeyInterpreter()

    XCTAssertEqual(
      interpreter.input(.vertical, expecting: "ㅑ"),
      .pending(display: "ㅣ")
    )
    XCTAssertEqual(
      interpreter.input(.dot, expecting: "ㅑ"),
      .pending(display: "ㅏ")
    )
    XCTAssertEqual(interpreter.backspace(), .pendingChanged(display: "ㅣ"))
    XCTAssertEqual(interpreter.input(.dot, expecting: "ㅑ"), .pending(display: "ㅏ"))
    XCTAssertEqual(interpreter.input(.dot, expecting: "ㅑ"), .committed("ㅑ"))
    XCTAssertEqual(interpreter.backspace(), .forwardToHangulEngine)
  }

  func testKorean10KeyRequiresAdvanceBetweenConsecutiveConsonantsInSameGroup() {
    let model = PracticeSessionViewModel(target: "언니")
    var interpreter = Korean10KeyInterpreter()

    XCTAssertEqual(interpreter.input(.ieung, expecting: model.nextExpectedKey), .committed("ㅇ"))
    model.input("ㅇ")
    XCTAssertEqual(
      interpreter.input(.dot, expecting: model.nextExpectedKey),
      .pending(display: "ㆍ")
    )
    XCTAssertEqual(interpreter.input(.vertical, expecting: model.nextExpectedKey), .committed("ㅓ"))
    model.input("ㅓ")
    XCTAssertEqual(interpreter.input(.nieun, expecting: model.nextExpectedKey), .committed("ㄴ"))
    model.input("ㄴ")

    XCTAssertEqual(model.nextExpectedKey, "ㄴ")
    XCTAssertEqual(interpreter.nextKey(for: model.nextExpectedKey), .next)
    XCTAssertEqual(
      interpreter.input(.nieun, expecting: model.nextExpectedKey),
      .incorrect(expected: "ㄴ")
    )
    model.input("ㄹ")
    XCTAssertEqual(model.mistakeCount, 1)
    XCTAssertEqual(interpreter.nextKey(for: model.nextExpectedKey), .next)
    XCTAssertEqual(
      interpreter.input(.next, expecting: model.nextExpectedKey),
      .separatorAccepted
    )
    XCTAssertEqual(interpreter.nextKey(for: model.nextExpectedKey), .nieun)
    XCTAssertEqual(
      interpreter.input(.nieun, expecting: model.nextExpectedKey),
      .committed("ㄴ")
    )
  }

  func testKorean10KeyWrongGroupCountsOneMistakeAndResetsPendingRecipe() {
    let model = PracticeSessionViewModel(target: "카")
    var interpreter = Korean10KeyInterpreter()

    XCTAssertEqual(
      interpreter.input(.giyeok, expecting: model.nextExpectedKey),
      .pending(display: "ㄱ")
    )
    XCTAssertEqual(
      interpreter.input(.nieun, expecting: model.nextExpectedKey),
      .incorrect(expected: "ㅋ")
    )
    model.input("ㄴ")
    XCTAssertEqual(model.mistakeCount, 1)
    XCTAssertEqual(interpreter.nextKey(for: model.nextExpectedKey), .giyeok)
  }

  func testBuiltInKeyboardLayoutUnknownValueFallsBackToDubeolsik() {
    XCTAssertEqual(BuiltInKeyboardLayout.resolved(from: "future-layout"), .dubeolsik)
    XCTAssertEqual(
      BuiltInKeyboardLayout.resolved(from: BuiltInKeyboardLayout.korean10Key.rawValue),
      .korean10Key
    )
    XCTAssertEqual(BuiltInKeyboardLayout.dubeolsik.gameRecordInputMode, .builtIn)
    XCTAssertEqual(
      BuiltInKeyboardLayout.korean10Key.gameRecordInputMode,
      .builtInKorean10Key
    )
  }
}
