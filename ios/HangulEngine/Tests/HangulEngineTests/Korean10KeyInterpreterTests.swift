import XCTest

@testable import HangulEngine

final class Korean10KeyInterpreterTests: XCTestCase {
  func testConsonantCyclesResolveOnTimeout() {
    var state = Korean10KeyState()
    state = Korean10KeyInterpreter.reduce(state, key: .giyeokKieuk).state
    XCTAssertEqual(Korean10KeyInterpreter.pendingCharacters(in: state), Array("ㄱ"))

    state = Korean10KeyInterpreter.reduce(state, key: .giyeokKieuk).state
    XCTAssertEqual(Korean10KeyInterpreter.pendingCharacters(in: state), Array("ㅋ"))

    state = Korean10KeyInterpreter.reduce(state, key: .giyeokKieuk).state
    XCTAssertEqual(Korean10KeyInterpreter.pendingCharacters(in: state), Array("ㄲ"))

    let wrapped = Korean10KeyInterpreter.reduce(state, key: .giyeokKieuk)
    XCTAssertEqual(Korean10KeyInterpreter.pendingCharacters(in: wrapped.state), Array("ㄱ"))
    XCTAssertEqual(
      Korean10KeyInterpreter.timeout(wrapped.state).outputs,
      [.character("ㄱ")]
    )
  }

  func testVowelsResolveByAppleTenKeyStrokes() {
    let cases: [([Korean10KeyKey], Character)] = [
      ([.vowelI], "ㅣ"),
      ([.vowelEu], "ㅡ"),
      ([.vowelI, .vowelDot], "ㅏ"),
      ([.vowelI, .vowelDot, .vowelDot], "ㅑ"),
      ([.vowelDot, .vowelI], "ㅓ"),
      ([.vowelDot, .vowelDot, .vowelI], "ㅕ"),
      ([.vowelDot, .vowelEu], "ㅗ"),
      ([.vowelDot, .vowelDot, .vowelEu], "ㅛ"),
      ([.vowelEu, .vowelDot], "ㅜ"),
      ([.vowelEu, .vowelDot, .vowelDot], "ㅠ"),
      ([.vowelI, .vowelDot, .vowelI], "ㅐ"),
      ([.vowelI, .vowelDot, .vowelDot, .vowelI], "ㅒ"),
      ([.vowelDot, .vowelI, .vowelI], "ㅔ"),
      ([.vowelDot, .vowelDot, .vowelI, .vowelI], "ㅖ"),
    ]

    for (keys, expected) in cases {
      var state = Korean10KeyState()
      for key in keys {
        state = Korean10KeyInterpreter.reduce(state, key: key).state
      }
      XCTAssertEqual(
        Korean10KeyInterpreter.timeout(state).outputs,
        [.character(expected)],
        String(expected)
      )
      XCTAssertEqual(Korean10KeyInterpreter.keySequence(for: expected), keys, String(expected))
    }
  }

  func testVowelBoundaryFlushesPriorCompleteVowel() {
    var state = Korean10KeyState()
    var outputs: [Korean10KeyOutput] = []
    for key in [
      Korean10KeyKey.vowelDot,
      .vowelEu,
      .vowelI,
      .vowelDot,
    ] {
      let transition = Korean10KeyInterpreter.reduce(state, key: key)
      state = transition.state
      outputs.append(contentsOf: transition.outputs)
    }
    let timeout = Korean10KeyInterpreter.timeout(state)
    outputs.append(contentsOf: timeout.outputs)

    XCTAssertEqual(outputs, [.character("ㅗ"), .character("ㅏ")])
    XCTAssertEqual(HangulComposer.compose(outputs.characters).text, "ㅘ")
  }

  func testExplicitCommitSeparatesRepeatedGroups() {
    var state = Korean10KeyState()
    var outputs: [Korean10KeyOutput] = []
    for key in [
      Korean10KeyKey.giyeokKieuk,
      .commit,
      .giyeokKieuk,
      .commit,
    ] {
      let transition = Korean10KeyInterpreter.reduce(state, key: key)
      state = transition.state
      outputs.append(contentsOf: transition.outputs)
    }

    XCTAssertEqual(outputs, [.character("ㄱ"), .character("ㄱ")])
  }

  func testBackspaceCancelsPendingBeforeDeletingAcceptedInput() {
    var state = Korean10KeyInterpreter.reduce(
      Korean10KeyState(),
      key: .bieupPieup
    ).state

    var transition = Korean10KeyInterpreter.reduce(state, key: .backspace)
    XCTAssertFalse(transition.state.hasPendingInput)
    XCTAssertTrue(transition.outputs.isEmpty)

    state = transition.state
    transition = Korean10KeyInterpreter.reduce(state, key: .backspace)
    XCTAssertEqual(transition.outputs, [.backspace])
  }

  func testAppleSequenceComposesPaebeoriksyopa() throws {
    let physicalKeys: [Korean10KeyKey] = [
      .bieupPieup, .bieupPieup,
      .vowelI, .vowelDot, .vowelI,
      .bieupPieup,
      .vowelEu,
      .nieunRieul, .nieunRieul,
      .vowelI,
      .giyeokKieuk,
      .siotHieut,
      .vowelDot, .vowelDot, .vowelEu,
      .bieupPieup, .bieupPieup,
      .vowelI, .vowelDot,
    ]

    let output = try interpret(physicalKeys, target: "패브릭쇼파")
    XCTAssertEqual(output, try JamoDecomposer.keySequence(for: "패브릭쇼파"))
    XCTAssertEqual(HangulComposer.compose(output).text, "패브릭쇼파")
  }

  func testAppleSequenceComposesDaehyeong() throws {
    let physicalKeys: [Korean10KeyKey] = [
      .digeutTieut,
      .vowelI, .vowelDot, .vowelI,
      .siotHieut, .siotHieut,
      .vowelDot, .vowelDot, .vowelI,
      .ieungMieum,
    ]

    let output = try interpret(physicalKeys, target: "대형")
    XCTAssertEqual(output, try JamoDecomposer.keySequence(for: "대형"))
    XCTAssertEqual(HangulComposer.compose(output).text, "대형")
  }

  func testGuideAdvancesWithinMultiTapAndVowelSequences() {
    var state = Korean10KeyState()
    XCTAssertEqual(
      Korean10KeyInterpreter.nextKey(for: "ㅍ", state: state),
      .bieupPieup
    )

    state = Korean10KeyInterpreter.reduce(state, key: .bieupPieup).state
    XCTAssertEqual(
      Korean10KeyInterpreter.nextKey(for: "ㅍ", state: state),
      .bieupPieup
    )

    state = Korean10KeyInterpreter.reduce(state, key: .bieupPieup).state
    XCTAssertEqual(Korean10KeyInterpreter.nextKey(for: "ㅍ", state: state), .commit)

    state = Korean10KeyState()
    state = Korean10KeyInterpreter.reduce(state, key: .vowelI).state
    XCTAssertEqual(Korean10KeyInterpreter.nextKey(for: "ㅐ", state: state), .vowelDot)
    state = Korean10KeyInterpreter.reduce(state, key: .vowelDot).state
    XCTAssertEqual(Korean10KeyInterpreter.nextKey(for: "ㅐ", state: state), .vowelI)
  }

  private func interpret(
    _ physicalKeys: [Korean10KeyKey],
    target: String
  ) throws -> [Character] {
    let expected = try JamoDecomposer.keySequence(for: target)
    var state = Korean10KeyState()
    var output: [Character] = []

    for key in physicalKeys {
      let transition = Korean10KeyInterpreter.reduce(state, key: key)
      state = transition.state
      output.append(contentsOf: transition.outputs.characters)

      if output.count < expected.count,
        Korean10KeyInterpreter.pendingCharacters(in: state) == [expected[output.count]]
      {
        let commit = Korean10KeyInterpreter.timeout(state)
        state = commit.state
        output.append(contentsOf: commit.outputs.characters)
      }
    }

    let final = Korean10KeyInterpreter.timeout(state)
    output.append(contentsOf: final.outputs.characters)
    return output
  }
}

extension Array where Element == Korean10KeyOutput {
  fileprivate var characters: [Character] {
    compactMap { output in
      guard case .character(let character) = output else { return nil }
      return character
    }
  }
}
