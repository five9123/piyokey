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
    XCTAssertEqual(vectors.backspaceCases.count, 6)

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
    XCTAssertEqual(unsupported.status, .confirmedMismatch(expectedIndex: 2))
    XCTAssertEqual(unsupported.acceptedSequence, Array("ㄱㅏ"))
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
