import Foundation
import XCTest

@testable import HangulEngine

final class HangulEngineTests: XCTestCase {
  func testAllSharedCompositionCases() throws {
    let vectors = try loadVectors()
    XCTAssertEqual(vectors.compositionCases.count, 15)

    for testCase in vectors.compositionCases {
      let keys = try testCase.keySequence.map(singleCharacter)
      let state = HangulComposer.compose(keys)
      XCTAssertEqual(state.text, testCase.target, testCase.name)

      let decomposed = try JamoDecomposer.keySequence(for: testCase.target)
      XCTAssertEqual(decomposed, keys, "decomposition: \(testCase.name)")
      XCTAssertEqual(
        testCase.target.decomposedStringWithCanonicalMapping.unicodeScalars.count,
        testCase.nfdLength,
        "NFD fixture: \(testCase.name)"
      )

      var judge = try JamoJudgeState(target: testCase.target)
      for (index, key) in keys.enumerated() {
        let evaluation = JamoSequenceJudge.evaluate(key, state: judge)
        judge = evaluation.state
        guard case .correct(let completed) = evaluation.result else {
          return XCTFail("judge rejected correct input: \(testCase.name)[\(index)]")
        }
        XCTAssertEqual(completed, index == keys.count - 1, testCase.name)
      }
      XCTAssertTrue(judge.isComplete, testCase.name)
      XCTAssertEqual(judge.correctCount, keys.count, testCase.name)
      XCTAssertEqual(judge.errorCount, 0, testCase.name)
      XCTAssertEqual(judge.progress, 1, accuracy: 0.0001, testCase.name)

      for shifted in try testCase.usesShift.map(singleCharacter) {
        XCTAssertTrue(JamoDecomposer.isShiftJamo(shifted), "shift metadata: \(testCase.name)")
        XCTAssertTrue(judge.expectedSequence.contains(shifted), "shift sequence: \(testCase.name)")
      }
    }
  }

  func testAllSharedBackspaceCases() throws {
    let vectors = try loadVectors()
    XCTAssertEqual(vectors.backspaceCases.count, 10)

    for testCase in vectors.backspaceCases {
      var state = HangulComposer.compose(try testCase.typeKeys.map(singleCharacter))
      for _ in 0..<testCase.thenBackspaces {
        state = HangulComposer.reduce(state, event: .backspace)
      }
      XCTAssertEqual(state.text, testCase.expected, testCase.name)
    }
  }

  func testAutomatonPhasesAndBoundaryTransitions() {
    var state = CompositionState()
    XCTAssertEqual(state.phase, .empty)

    state = HangulComposer.reduce(state, event: .key("ㄱ"))
    XCTAssertEqual(state.phase, .cho)
    state = HangulComposer.reduce(state, event: .key("ㅏ"))
    XCTAssertEqual(state.phase, .choJung)
    state = HangulComposer.reduce(state, event: .key("ㄴ"))
    XCTAssertEqual(state.phase, .choJungJong)
    XCTAssertEqual(state.text, "간")

    state = HangulComposer.reduce(state, event: .backspace)
    XCTAssertEqual(state.phase, .choJung)
    XCTAssertEqual(state.text, "가")

    XCTAssertEqual(HangulComposer.compose(Array("ㄱㄴ")).text, "ㄱㄴ")
    XCTAssertEqual(HangulComposer.compose(Array("ㄱㅏㄸ")).text, "가ㄸ")
    XCTAssertEqual(HangulComposer.compose(Array("ㄱㅏㅓ")).text, "가ㅓ")
    XCTAssertEqual(HangulComposer.compose(Array("ㅗㅏ")).text, "ㅘ")
    XCTAssertEqual(HangulComposer.compose(Array("ㅏㄱ")).text, "ㅏㄱ")
    XCTAssertEqual(HangulComposer.compose(Array("ㄷㅏㄹㄱㅏ")).text, "달가")
    XCTAssertEqual(HangulComposer.compose(Array("ㄱㅏㄴㅏ")).text, "가나")
    XCTAssertEqual(HangulComposer.compose(Array("ㄱㅏ A")).text, "가 A")
  }

  func testBackspaceRestoresCarryoverAndIgnoresExcessDeletion() {
    var state = HangulComposer.compose(Array("ㄱㅏㄴㅏ"))
    XCTAssertEqual(state.text, "가나")
    state = HangulComposer.reduce(state, event: .backspace)
    XCTAssertEqual(state.text, "간")
    for _ in 0..<10 {
      state = HangulComposer.reduce(state, event: .backspace)
    }
    XCTAssertEqual(state.text, "")
    XCTAssertEqual(state.phase, .empty)
  }

  func testDecomposerExpandsCompoundJamoAndRejectsUntypeableText() throws {
    XCTAssertEqual(
      try JamoDecomposer.keySequence(for: "ㅘㄳ 2!"),
      Array("ㅗㅏㄱㅅ 2!")
    )
    XCTAssertTrue(JamoDecomposer.containsHangul(in: "한글 2"))
    XCTAssertTrue(JamoDecomposer.containsHangul(in: "ㄱ"))
    XCTAssertFalse(JamoDecomposer.containsHangul(in: "123"))
    XCTAssertThrowsError(try JamoDecomposer.keySequence(for: "")) { error in
      XCTAssertEqual(error as? JamoDecompositionError, .emptyTarget)
    }
    XCTAssertThrowsError(try JamoDecomposer.keySequence(for: "ABC")) { error in
      XCTAssertEqual(error as? JamoDecompositionError, .unsupportedCharacter("A", offset: 0))
    }
  }

  func testJudgeCountsMistakeWithoutAdvancingOrPollutingTarget() throws {
    var state = try JamoJudgeState(target: "가")
    var evaluation = JamoSequenceJudge.evaluate("ㄴ", state: state)
    state = evaluation.state
    XCTAssertEqual(evaluation.result, .incorrect(expected: "ㄱ"))
    XCTAssertEqual(state.currentIndex, 0)
    XCTAssertEqual(state.correctCount, 0)
    XCTAssertEqual(state.errorCount, 1)

    evaluation = JamoSequenceJudge.evaluate("ㄱ", state: state)
    state = evaluation.state
    XCTAssertEqual(evaluation.result, .correct(completed: false))
    evaluation = JamoSequenceJudge.evaluate("ㅏ", state: state)
    state = evaluation.state
    XCTAssertEqual(evaluation.result, .correct(completed: true))
    XCTAssertEqual(state.correctCount, 2)
    XCTAssertEqual(state.errorCount, 1)
    XCTAssertEqual(JamoSequenceJudge.evaluate("ㅏ", state: state).result, .alreadyComplete)
  }

  func testOSIMEConfirmedTextPassesEverySharedCompositionVector() throws {
    let vectors = try loadVectors()
    XCTAssertEqual(vectors.compositionCases.count, 15)

    for testCase in vectors.compositionCases {
      let evaluation = try OSIMETextJudge.evaluate(
        target: testCase.target,
        committedText: testCase.target
      )
      XCTAssertEqual(
        evaluation.status,
        .matching(completed: true, isComposing: false),
        testCase.name
      )
      XCTAssertEqual(
        evaluation.acceptedSequence,
        try testCase.keySequence.map(singleCharacter),
        testCase.name
      )
    }
  }

  func testOSIMETextDiffUsesJamoPrefixesAcrossCarryoverAndShift() throws {
    let carryover = try OSIMETextJudge.evaluate(target: "달가", committedText: "닭")
    XCTAssertEqual(
      carryover,
      OSIMETextEvaluation(
        status: .matching(completed: false, isComposing: false),
        acceptedSequence: Array("ㄷㅏㄹㄱ")
      )
    )

    let shifted = try OSIMETextJudge.evaluate(target: "꿀", committedText: "꿀")
    XCTAssertEqual(shifted.acceptedSequence, Array("ㄲㅜㄹ"))
    XCTAssertEqual(shifted.status, .matching(completed: true, isComposing: false))
  }

  func testOSIMEMarkedMismatchDoesNotCountUntilConfirmed() throws {
    let composing = try OSIMETextJudge.evaluate(
      target: "가나",
      committedText: "가",
      markedText: "다"
    )
    XCTAssertEqual(composing.status, .composingMismatch)
    XCTAssertEqual(composing.acceptedSequence, Array("ㄱㅏ"))

    let confirmed = try OSIMETextJudge.evaluate(target: "가나", committedText: "가다")
    XCTAssertEqual(confirmed.status, .confirmedMismatch(expectedIndex: 2))
    XCTAssertEqual(confirmed.acceptedSequence, Array("ㄱㅏ"))
  }

  func testOSIMEReachableCheonjiinCommittedVowelsRemainCompositionInProgress() throws {
    struct Snapshot {
      let target: String
      let committed: String
      let acceptedSequence: [Character]
    }

    let snapshots = [
      Snapshot(target: "돼지", committed: "되", acceptedSequence: Array("ㄷㅗ")),
      Snapshot(target: "돼지", committed: "돠", acceptedSequence: Array("ㄷㅗ")),
      Snapshot(target: "과자", committed: "괴", acceptedSequence: Array("ㄱㅗ")),
      Snapshot(target: "웨딩", committed: "워", acceptedSequence: Array("ㅇㅜ")),
      Snapshot(target: "대형", committed: "다", acceptedSequence: Array("ㄷ")),
      Snapshot(target: "세계", committed: "서", acceptedSequence: Array("ㅅ")),
      Snapshot(target: "얘", committed: "야", acceptedSequence: Array("ㅇ")),
      Snapshot(target: "예", committed: "여", acceptedSequence: Array("ㅇ")),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(evaluation.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(
        evaluation.acceptedSequence,
        snapshot.acceptedSequence,
        snapshot.target
      )
    }
  }

  func testOSIMEMeasuredCommittedCheonjiinStrokePrefixDoesNotRollback() throws {
    struct Snapshot {
      let committed: String
      let status: OSIMETextJudgeStatus
      let acceptedSequence: [Character]
    }

    let allTapSnapshots = [
      Snapshot(
        committed: "ㄷ",
        status: .matching(completed: false, isComposing: false),
        acceptedSequence: Array("ㄷ")
      ),
      Snapshot(
        committed: "ㄷㆍ",
        status: .composingMismatch,
        acceptedSequence: Array("ㄷ")
      ),
      Snapshot(
        committed: "도",
        status: .matching(completed: false, isComposing: false),
        acceptedSequence: Array("ㄷㅗ")
      ),
      Snapshot(
        committed: "되",
        status: .composingMismatch,
        acceptedSequence: Array("ㄷㅗ")
      ),
      Snapshot(
        committed: "돠",
        status: .composingMismatch,
        acceptedSequence: Array("ㄷㅗ")
      ),
      Snapshot(
        committed: "돼",
        status: .matching(completed: true, isComposing: false),
        acceptedSequence: Array("ㄷㅗㅐ")
      ),
    ]

    for snapshot in allTapSnapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: "돼",
        committedText: snapshot.committed
      )
      XCTAssertEqual(evaluation.status, snapshot.status, snapshot.committed)
      XCTAssertEqual(
        evaluation.acceptedSequence,
        snapshot.acceptedSequence,
        snapshot.committed
      )
      if case .confirmedMismatch = evaluation.status {
        XCTFail("reachable all-tap snapshot would cause a rollback: \(snapshot.committed)")
      }
    }

    for committed in ["ㄷ", "도", "돠", "돼"] {
      let evaluation = try OSIMETextJudge.evaluate(target: "돼", committedText: committed)
      if case .confirmedMismatch = evaluation.status {
        XCTFail("reachable shortcut snapshot would cause a rollback: \(committed)")
      }
    }

    let afterAcceptedWordPrefix = try OSIMETextJudge.evaluate(
      target: "줘도 돼",
      committedText: "줘도 ㄷㆍ"
    )
    XCTAssertEqual(afterAcceptedWordPrefix.status, .composingMismatch)
    XCTAssertEqual(afterAcceptedWordPrefix.acceptedSequence, Array("ㅈㅜㅓㄷㅗ ㄷ"))

    let unreachableDot = try OSIMETextJudge.evaluate(target: "뒤", committedText: "ㄷㆍ")
    XCTAssertEqual(unreachableDot.status, .confirmedMismatch(expectedIndex: 1))
    XCTAssertEqual(unreachableDot.acceptedSequence, Array("ㄷ"))

    let arbitraryJamo = try OSIMETextJudge.evaluate(target: "돼", committedText: "ㄷㆍㄱ")
    XCTAssertEqual(arbitraryJamo.status, .confirmedMismatch(expectedIndex: 1))
    XCTAssertEqual(arbitraryJamo.acceptedSequence, Array("ㄷ"))
  }

  func testOSIMEStandaloneDotFirstVowelPrefixesRemainCompositionInProgress() throws {
    let snapshots = [
      (target: "ㅓ", committed: "ㆍ"),
      (target: "ㅗ", committed: "ㆍ"),
      (target: "ㅔ", committed: "ㆍ"),
      (target: "ㅔ", committed: "ㆍㅣ"),
      (target: "ㅕ", committed: "ㆍ"),
      (target: "ㅕ", committed: "ㆍㆍ"),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(
        evaluation.status,
        .composingMismatch,
        "\(snapshot.target): \(snapshot.committed)"
      )
      XCTAssertEqual(
        evaluation.acceptedSequence,
        [],
        "\(snapshot.target): \(snapshot.committed)"
      )
    }

    let wrongStroke = try OSIMETextJudge.evaluate(target: "ㅗ", committedText: "ㆍㅣ")
    XCTAssertEqual(wrongStroke.status, .confirmedMismatch(expectedIndex: 0))
    XCTAssertEqual(wrongStroke.acceptedSequence, [])
  }

  func testOSIMECheonjiinConsonantCyclesRemainCompositionInProgress() throws {
    let snapshots: [(target: String, committed: String, accepted: [Character])] = [
      ("ㄹ", "ㄴ", []),
      ("하", "ㅅ", []),
      ("대형", "대ㅅ", Array("ㄷㅐ")),
      ("대형", "댓", Array("ㄷㅐ")),
      ("달", "단", Array("ㄷㅏ")),
      ("밤", "방", Array("ㅂㅏ")),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(
        evaluation.status,
        .composingMismatch,
        "\(snapshot.target): \(snapshot.committed)"
      )
      XCTAssertEqual(
        evaluation.acceptedSequence,
        snapshot.accepted,
        "\(snapshot.target): \(snapshot.committed)"
      )
    }

    let unrelatedCycle = try OSIMETextJudge.evaluate(target: "하", committedText: "ㅈ")
    XCTAssertEqual(unrelatedCycle.status, .confirmedMismatch(expectedIndex: 0))

    let completedTypo = try OSIMETextJudge.evaluate(target: "대형", committedText: "대성")
    XCTAssertEqual(completedTypo.status, .confirmedMismatch(expectedIndex: 2))

    let wrongFinal = try OSIMETextJudge.evaluate(target: "달", committedText: "담")
    XCTAssertEqual(wrongFinal.status, .confirmedMismatch(expectedIndex: 2))
  }

  func testOSIMEClosedBatchimBoundariesFollowOnlyTheNextOnsetRecipe() throws {
    let snapshots: [(target: String, committed: String, accepted: [Character])] = [
      ("일해", "잀", Array("ㅇㅣㄹ")),
      ("말해", "맔", Array("ㅁㅏㄹ")),
      ("말했다", "맔", Array("ㅁㅏㄹ")),
      ("급해", "긊", Array("ㄱㅡㅂ")),
      ("입학", "잆", Array("ㅇㅣㅂ")),
      ("번째", "벉", Array("ㅂㅓㄴ")),
      ("각하", "갃", Array("ㄱㅏㄱ")),
    ]

    for snapshot in snapshots {
      let intermediate = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(intermediate.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(intermediate.acceptedSequence, snapshot.accepted, snapshot.target)

      let completed = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.target
      )
      XCTAssertEqual(
        completed.status,
        .matching(completed: true, isComposing: false),
        snapshot.target
      )
    }
  }

  func testOSIMEComplexBatchimAssemblyUsesOnlyExactTargetComponents() throws {
    let snapshots: [(target: String, committed: String, accepted: [Character])] = [
      ("읽어", "인", Array("ㅇㅣ")),
      ("닭", "단", Array("ㄷㅏ")),
      ("삶", "산", Array("ㅅㅏ")),
      ("앓다", "안", Array("ㅇㅏ")),
      ("앓다", "앐", Array("ㅇㅏㄹ")),
      ("읊다", "은", Array("ㅇㅡ")),
      ("읊다", "읇", Array("ㅇㅡㄹ")),
    ]

    for snapshot in snapshots {
      let intermediate = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(intermediate.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(intermediate.acceptedSequence, snapshot.accepted, snapshot.target)
    }

    for target in ["읽어", "닭", "삶", "많이", "앓다", "읊다"] {
      let completed = try OSIMETextJudge.evaluate(target: target, committedText: target)
      XCTAssertEqual(
        completed.status,
        .matching(completed: true, isComposing: false),
        target
      )
    }
  }

  func testOSIMEReachableDanglingComplexBatchimPrefixesRemainCompositionInProgress() throws {
    let snapshots: [(target: String, committed: String, stableText: String)] = [
      ("괜찮아", "괜찬ㅅ", "괜찬"),
      ("찮아", "찬ㅅ", "찬"),
      ("않아", "안ㅅ", "안"),
      ("많이", "만ㅅ", "만"),
      ("삶", "살ㅇ", "살"),
      ("핥다", "할ㄷ", "할"),
      ("읊다", "을ㅂ", "을"),
      ("앓다", "알ㅅ", "알"),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(
        evaluation.status,
        .composingMismatch,
        "\(snapshot.target): \(snapshot.committed)"
      )
      XCTAssertEqual(
        evaluation.acceptedSequence,
        try JamoDecomposer.keySequence(for: snapshot.stableText),
        "\(snapshot.target): \(snapshot.committed)"
      )
    }
  }

  func testOSIMEBatchimBoundaryNegativeMatrixRemainsConfirmed() throws {
    let snapshots: [(target: String, committed: String, mismatch: Int)] = [
      ("일해", "읽", 3),
      ("급해", "긁", 2),
      ("닭", "닮", 3),
      ("앓다", "앎", 3),
      ("읊다", "읅", 3),
      ("삶", "살ㅅ", 3),
      ("많이", "만ㅈ", 3),
      ("않아", "안ㅈ", 3),
      ("읽어", "읽아", 5),
      ("일해", "일개", 3),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(
        evaluation.status,
        .confirmedMismatch(expectedIndex: snapshot.mismatch),
        "\(snapshot.target): \(snapshot.committed)"
      )
    }
  }

  func testOSIMESameRecipeBatchimBoundaryCyclesPreserveTheClosedSyllable() throws {
    let snapshots: [(target: String, committed: String, accepted: [Character])] = [
      ("학교", "핰", Array("ㅎㅏㄱ")),
      ("학교", "핚", Array("ㅎㅏㄱ")),
      ("각각", "갘", Array("ㄱㅏㄱ")),
      ("닫다", "닽", Array("ㄷㅏㄷ")),
      ("십분", "싶", Array("ㅅㅣㅂ")),
      ("옷사", "옿", Array("ㅇㅗㅅ")),
      ("잊지", "잋", Array("ㅇㅣㅈ")),
      ("만나", "말", Array("ㅁㅏㄴ")),
      ("공원", "곰", Array("ㄱㅗㅇ")),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(evaluation.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(evaluation.acceptedSequence, snapshot.accepted, snapshot.target)
    }

    let confirmedBoundary = try OSIMETextJudge.evaluate(
      target: "학교",
      committedText: "학ㄱ"
    )
    XCTAssertEqual(
      confirmedBoundary.status,
      .matching(completed: false, isComposing: false)
    )
    XCTAssertEqual(confirmedBoundary.acceptedSequence, Array("ㅎㅏㄱㄱ"))

    let completed = try OSIMETextJudge.evaluate(target: "학교", committedText: "학교")
    XCTAssertEqual(completed.status, .matching(completed: true, isComposing: false))
    XCTAssertEqual(completed.acceptedSequence, Array("ㅎㅏㄱㄱㅛ"))
  }

  func testOSIMESameRecipeBatchimBoundaryCycleDoesNotAcceptWrongBoundaries() throws {
    let negatives: [(target: String, committed: String, mismatch: Int)] = [
      ("학교", "핱", 2),
      ("학코", "핰", 2),
      ("학교", "학고", 4),
      ("학교", "학쿄", 3),
      ("학교", "하교", 3),
    ]

    for negative in negatives {
      let evaluation = try OSIMETextJudge.evaluate(
        target: negative.target,
        committedText: negative.committed
      )
      XCTAssertEqual(
        evaluation.status,
        .confirmedMismatch(expectedIndex: negative.mismatch),
        "\(negative.target): \(negative.committed)"
      )
    }
  }

  func testOSIMEDoubleDotScalarsExpandToStrictRecipePrefixes() throws {
    let oneDotScalars = ["\u{318D}", "\u{119E}"]
    for dot in oneDotScalars {
      let evaluation = try OSIMETextJudge.evaluate(
        target: "어",
        committedText: "ㅇ\(dot)"
      )
      XCTAssertEqual(evaluation.status, .composingMismatch, dot)
      XCTAssertEqual(evaluation.acceptedSequence, Array("ㅇ"), dot)
    }

    let doubleDotSnapshots: [(target: String, committed: String, accepted: [Character])] = [
      ("요", "ㅇ\u{11A2}", Array("ㅇ")),
      ("여자", "ㅇ\u{11A2}", Array("ㅇ")),
      ("예", "ㅇ\u{11A2}", Array("ㅇ")),
      ("예", "ㅇ\u{11A2}ㅣ", Array("ㅇ")),
      ("교", "ㄱ\u{11A2}", Array("ㄱ")),
      ("며칠", "ㅁ\u{11A2}", Array("ㅁ")),
      ("표", "ㅍ\u{11A2}", Array("ㅍ")),
      ("효", "ㅎ\u{11A2}", Array("ㅎ")),
      ("요", "ㅇ\u{318D}\u{318D}", Array("ㅇ")),
      ("요", "ㅇ\u{119E}\u{119E}", Array("ㅇ")),
    ]

    for snapshot in doubleDotSnapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(evaluation.status, .composingMismatch, snapshot.target)
      XCTAssertEqual(evaluation.acceptedSequence, snapshot.accepted, snapshot.target)
    }
  }

  func testOSIMEWordPrefixDoubleDotVowelStatesRemainCompositionInProgress() throws {
    let snapshots: [(target: String, committed: String, stableText: String)] = [
      ("어요", "어ㅇ\u{11A2}", "어ㅇ"),
      ("와요", "와ㅇ\u{11A2}", "와ㅇ"),
      ("해요", "해ㅇ\u{11A2}", "해ㅇ"),
      ("좋아요", "좋아ㅇ\u{11A2}", "좋아ㅇ"),
    ]

    for snapshot in snapshots {
      let evaluation = try OSIMETextJudge.evaluate(
        target: snapshot.target,
        committedText: snapshot.committed
      )
      XCTAssertEqual(
        evaluation.status,
        .composingMismatch,
        "\(snapshot.target): \(snapshot.committed)"
      )
      XCTAssertEqual(
        evaluation.acceptedSequence,
        try JamoDecomposer.keySequence(for: snapshot.stableText),
        "\(snapshot.target): \(snapshot.committed)"
      )
    }
  }

  func testOSIMEDoubleDotStrictPrefixesRejectUnrelatedAndWrongFollowingStrokes() throws {
    let negatives: [(target: String, committed: String, mismatch: Int)] = [
      ("어", "ㅇ\u{11A2}", 1),
      ("뒤", "ㄷ\u{11A2}", 1),
      ("요", "ㅇ\u{11A2}ㅣ", 1),
      ("여", "ㅇ\u{11A2}ㅡ", 1),
      ("교", "ㄱ\u{11A2}ㅣ", 1),
      ("요", "ㅇ\u{11A2}ㅡ", 1),
    ]

    for negative in negatives {
      let evaluation = try OSIMETextJudge.evaluate(
        target: negative.target,
        committedText: negative.committed
      )
      XCTAssertEqual(
        evaluation.status,
        .confirmedMismatch(expectedIndex: negative.mismatch),
        "\(negative.target): \(negative.committed)"
      )
    }
  }

  func testOSIMECheonjiinRepresentativeVowelsCompleteWithoutWeakeningRealTypos() throws {
    let representativeTargets = [
      "과자", "돼지", "회사", "원", "웨딩", "귀", "의사", "대형", "세계", "얘", "예",
    ]

    for target in representativeTargets {
      let evaluation = try OSIMETextJudge.evaluate(target: target, committedText: target)
      XCTAssertEqual(
        evaluation.status,
        .matching(completed: true, isComposing: false),
        target
      )
    }

    let dubeolsikTypo = try OSIMETextJudge.evaluate(target: "돼지", committedText: "뒤")
    XCTAssertEqual(dubeolsikTypo.status, .confirmedMismatch(expectedIndex: 1))
    XCTAssertEqual(dubeolsikTypo.acceptedSequence, Array("ㄷ"))

    let wrongTrailing = try OSIMETextJudge.evaluate(target: "과자", committedText: "괸")
    XCTAssertEqual(wrongTrailing.status, .confirmedMismatch(expectedIndex: 2))
    XCTAssertEqual(wrongTrailing.acceptedSequence, Array("ㄱㅗ"))
  }

  func testKorean10KeyRecipesExposeOnlyTargetReachableIntermediateVowels() throws {
    XCTAssertEqual(Korean10KeyRecipe.recipe(for: "ㅙ"), [
      .dot, .horizontal, .vertical, .dot, .vertical,
    ])
    XCTAssertEqual(
      Korean10KeyRecipe.jamo(forExactRecipe: [.horizontal, .dot, .dot, .vertical]),
      "ㅝ"
    )
    XCTAssertTrue(Korean10KeyRecipe.isReachableIntermediateVowel("ㅚ", toward: "ㅙ"))
    XCTAssertTrue(Korean10KeyRecipe.isReachableIntermediateVowel("ㅘ", toward: "ㅙ"))
    XCTAssertTrue(Korean10KeyRecipe.isReachableIntermediateVowel("ㅝ", toward: "ㅞ"))
    XCTAssertTrue(Korean10KeyRecipe.isReachableIntermediateVowel("ㅏ", toward: "ㅐ"))
    XCTAssertFalse(Korean10KeyRecipe.isReachableIntermediateVowel("ㅟ", toward: "ㅙ"))
    XCTAssertFalse(Korean10KeyRecipe.isReachableIntermediateVowel("ㅙ", toward: "ㅙ"))
    XCTAssertTrue(Korean10KeyRecipe.isReachableIntermediateConsonant("ㄴ", toward: "ㄹ"))
    XCTAssertTrue(Korean10KeyRecipe.isReachableIntermediateConsonant("ㅅ", toward: "ㅎ"))
    XCTAssertFalse(Korean10KeyRecipe.isReachableIntermediateConsonant("ㅈ", toward: "ㅎ"))
    XCTAssertFalse(Korean10KeyRecipe.isReachableIntermediateConsonant("ㅎ", toward: "ㅎ"))
  }

  func testOSIMEASCIIInputDoesNotAdvanceOrBecomeAMistake() throws {
    let initial = try OSIMETextJudge.evaluate(target: "가나", committedText: "q")
    XCTAssertEqual(initial.status, .unsupportedASCIIInput)
    XCTAssertEqual(initial.acceptedSequence, [])

    let afterAcceptedPrefix = try OSIMETextJudge.evaluate(
      target: "가나",
      committedText: "가s"
    )
    XCTAssertEqual(afterAcceptedPrefix.status, .unsupportedASCIIInput)
    XCTAssertEqual(afterAcceptedPrefix.acceptedSequence, Array("ㄱㅏ"))

    let resumed = try OSIMETextJudge.evaluate(target: "가나", committedText: "가나")
    XCTAssertEqual(resumed.status, .matching(completed: true, isComposing: false))
    XCTAssertEqual(resumed.acceptedSequence, Array("ㄱㅏㄴㅏ"))
  }

  func testOSIMETextJudgeReplaysCheonjiinMarkedAndCommittedSnapshots() throws {
    struct Snapshot {
      let committed: String
      let marked: String?
      let expectedScalars: [UInt32]
      let expectedStatus: OSIMETextJudgeStatus
      let expectedAcceptedSequence: [Character]
    }

    let snapshots = [
      Snapshot(
        committed: "",
        marked: "ㄴ",
        expectedScalars: [0x3134],
        expectedStatus: .composingMismatch,
        expectedAcceptedSequence: []
      ),
      Snapshot(
        committed: "",
        marked: "ㄹ",
        expectedScalars: [0x3139],
        expectedStatus: .matching(completed: false, isComposing: true),
        expectedAcceptedSequence: Array("ㄹ")
      ),
      Snapshot(
        committed: "",
        marked: "ㄹㆍ",
        expectedScalars: [0x3139, 0x318D],
        expectedStatus: .composingMismatch,
        expectedAcceptedSequence: []
      ),
      Snapshot(
        committed: "",
        marked: "러",
        expectedScalars: [0xB7EC],
        expectedStatus: .composingMismatch,
        expectedAcceptedSequence: []
      ),
      Snapshot(
        committed: "",
        marked: "레",
        expectedScalars: [0xB808],
        expectedStatus: .matching(completed: false, isComposing: true),
        expectedAcceptedSequence: Array("ㄹㅔ")
      ),
      Snapshot(
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
        snapshot.expectedScalars
      )
      let evaluation = try OSIMETextJudge.evaluate(
        target: "레전드",
        committedText: snapshot.committed,
        markedText: snapshot.marked
      )
      XCTAssertEqual(evaluation.status, snapshot.expectedStatus)
      XCTAssertEqual(evaluation.acceptedSequence, snapshot.expectedAcceptedSequence)
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

  func testOSIMEAllowsCorrectMarkedProgressDeletionAndRejectsExtraText() throws {
    let composing = try OSIMETextJudge.evaluate(
      target: "가나",
      committedText: "가",
      markedText: "ㄴ"
    )
    XCTAssertEqual(composing.status, .matching(completed: false, isComposing: true))
    XCTAssertEqual(composing.acceptedSequence, Array("ㄱㅏㄴ"))

    let deletion = try OSIMETextJudge.evaluate(target: "가나", committedText: "")
    XCTAssertEqual(deletion.status, .matching(completed: false, isComposing: false))
    XCTAssertEqual(deletion.acceptedSequence, [])

    let extraAfterCompletion = try OSIMETextJudge.evaluate(target: "가", committedText: "가가")
    XCTAssertEqual(
      extraAfterCompletion.status,
      .matching(completed: true, isComposing: false)
    )
    XCTAssertEqual(extraAfterCompletion.acceptedSequence, Array("ㄱㅏ"))

    let unsupportedAfterCompletion = try OSIMETextJudge.evaluate(
      target: "가",
      committedText: "가A"
    )
    XCTAssertEqual(
      unsupportedAfterCompletion.status,
      .matching(completed: true, isComposing: false)
    )

    let unsupported = try OSIMETextJudge.evaluate(target: "가나", committedText: "가A")
    XCTAssertEqual(unsupported.status, .unsupportedASCIIInput)
    XCTAssertEqual(unsupported.acceptedSequence, Array("ㄱㅏ"))
  }

  func testOSIMEPhysicalKeyboardSnapshotsPreserveSpaceBackspaceAndMarkedCommit() throws {
    let composing = try OSIMETextJudge.evaluate(
      target: "한국 사람",
      committedText: "한국 ",
      markedText: "사"
    )
    XCTAssertEqual(
      composing,
      OSIMETextEvaluation(
        status: .matching(completed: false, isComposing: true),
        acceptedSequence: Array("ㅎㅏㄴㄱㅜㄱ ㅅㅏ")
      )
    )

    let committed = try OSIMETextJudge.evaluate(
      target: "한국 사람",
      committedText: "한국 사"
    )
    XCTAssertEqual(committed.acceptedSequence, composing.acceptedSequence)
    XCTAssertEqual(committed.status, .matching(completed: false, isComposing: false))

    let backspaced = try OSIMETextJudge.evaluate(
      target: "한국 사람",
      committedText: "한국 "
    )
    XCTAssertEqual(
      backspaced,
      OSIMETextEvaluation(
        status: .matching(completed: false, isComposing: false),
        acceptedSequence: Array("ㅎㅏㄴㄱㅜㄱ ")
      )
    )

    let completed = try OSIMETextJudge.evaluate(
      target: "한국 사람",
      committedText: "한국 사람"
    )
    XCTAssertEqual(completed.status, .matching(completed: true, isComposing: false))
  }

  private func loadVectors() throws -> SharedTestVectors {
    let url = try RepositoryFixtureLocator.file("shared/test_vectors.json", from: #filePath)
    return try JSONDecoder().decode(SharedTestVectors.self, from: Data(contentsOf: url))
  }

  private func singleCharacter(_ value: String) throws -> Character {
    try XCTUnwrap(value.count == 1 ? value.first : nil, "Expected exactly one character: \(value)")
  }
}

private struct SharedTestVectors: Decodable {
  let compositionCases: [CompositionCase]
  let backspaceCases: [BackspaceCase]

  private enum CodingKeys: String, CodingKey {
    case compositionCases = "composition_cases"
    case backspaceCases = "backspace_cases"
  }
}

private struct CompositionCase: Decodable {
  let name: String
  let target: String
  let keySequence: [String]
  let usesShift: [String]
  let nfdLength: Int

  private enum CodingKeys: String, CodingKey {
    case name, target
    case keySequence = "key_sequence"
    case usesShift = "uses_shift"
    case nfdLength = "nfd_length"
  }
}

private struct BackspaceCase: Decodable {
  let name: String
  let typeKeys: [String]
  let thenBackspaces: Int
  let expected: String

  private enum CodingKeys: String, CodingKey {
    case name, expected
    case typeKeys = "type_keys"
    case thenBackspaces = "then_backspaces"
  }
}

private enum RepositoryFixtureLocator {
  static func file(_ relativePath: String, from sourceFile: String) throws -> URL {
    var candidate = URL(fileURLWithPath: sourceFile).deletingLastPathComponent()
    for _ in 0..<8 {
      let file = candidate.appendingPathComponent(relativePath)
      if FileManager.default.fileExists(atPath: file.path) { return file }
      candidate.deleteLastPathComponent()
    }
    throw CocoaError(.fileNoSuchFile)
  }
}
