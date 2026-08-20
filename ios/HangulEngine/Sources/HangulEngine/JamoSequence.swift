public enum JamoDecompositionError: Error, Equatable, Sendable {
  case emptyTarget
  case unsupportedCharacter(Character, offset: Int)
}

public enum JamoDecomposer {
  /// Converts composed Hangul into the exact two-beolsik key-jamo sequence.
  public static func keySequence(for target: String) throws -> [Character] {
    guard !target.isEmpty else { throw JamoDecompositionError.emptyTarget }

    var result: [Character] = []
    for (offset, character) in target.enumerated() {
      if let scalar = onlyScalar(in: character), (0xAC00...0xD7A3).contains(Int(scalar.value)) {
        let syllableIndex = Int(scalar.value) - 0xAC00
        let leading = HangulTables.leading[syllableIndex / (21 * 28)]
        let medial = HangulTables.medial[(syllableIndex % (21 * 28)) / 28]
        let trailing = HangulTables.trailing[syllableIndex % 28]
        result.append(leading)
        appendExpanded(medial, splitTable: HangulTables.splitMedial, to: &result)
        if let trailing {
          appendExpanded(trailing, splitTable: HangulTables.splitTrailing, to: &result)
        }
      } else if HangulTables.leadingIndex[character] != nil {
        result.append(character)
      } else if HangulTables.medialIndex[character] != nil {
        appendExpanded(character, splitTable: HangulTables.splitMedial, to: &result)
      } else if let split = HangulTables.splitTrailing[character] {
        result.append(contentsOf: [split.first, split.second])
      } else if isAllowedLiteral(character) {
        result.append(character)
      } else {
        throw JamoDecompositionError.unsupportedCharacter(character, offset: offset)
      }
    }
    return result
  }

  public static func isShiftJamo(_ character: Character) -> Bool {
    HangulTables.shiftedJamo.contains(character)
  }

  public static func containsHangul(in text: String) -> Bool {
    text.contains { character in
      if HangulTables.leadingIndex[character] != nil || HangulTables.medialIndex[character] != nil {
        return true
      }
      return onlyScalar(in: character).map { (0xAC00...0xD7A3).contains(Int($0.value)) } ?? false
    }
  }

  private static func appendExpanded(
    _ jamo: Character,
    splitTable: [Character: JamoPair],
    to result: inout [Character]
  ) {
    if let split = splitTable[jamo] {
      result.append(contentsOf: [split.first, split.second])
    } else {
      result.append(jamo)
    }
  }

  private static func isAllowedLiteral(_ character: Character) -> Bool {
    if HangulTables.allowedLiteralPunctuation.contains(character) { return true }
    return character.unicodeScalars.allSatisfy { scalar in
      scalar.properties.isWhitespace || scalar.properties.numericType != nil
    }
  }

  private static func onlyScalar(in character: Character) -> UnicodeScalar? {
    character.unicodeScalars.count == 1 ? character.unicodeScalars.first : nil
  }
}

public struct JamoJudgeState: Equatable, Sendable {
  public let target: String
  public let expectedSequence: [Character]
  public private(set) var currentIndex: Int
  public private(set) var correctCount: Int
  public private(set) var errorCount: Int

  public init(target: String) throws {
    self.target = target
    expectedSequence = try JamoDecomposer.keySequence(for: target)
    currentIndex = 0
    correctCount = 0
    errorCount = 0
  }

  public var expectedNext: Character? {
    currentIndex < expectedSequence.count ? expectedSequence[currentIndex] : nil
  }

  public var isComplete: Bool { currentIndex == expectedSequence.count }

  public var progress: Double {
    expectedSequence.isEmpty ? 1 : Double(currentIndex) / Double(expectedSequence.count)
  }

  fileprivate mutating func recordCorrect() {
    currentIndex += 1
    correctCount += 1
  }

  fileprivate mutating func recordError() {
    errorCount += 1
  }
}

public enum JamoJudgeResult: Equatable, Sendable {
  case correct(completed: Bool)
  case incorrect(expected: Character)
  case alreadyComplete
}

public enum JamoSequenceJudge {
  /// Compares one logical jamo event. Shift+jamo arrives as its resulting jamo and counts once.
  public static func evaluate(
    _ input: Character,
    state: JamoJudgeState
  ) -> (state: JamoJudgeState, result: JamoJudgeResult) {
    var next = state
    guard let expected = next.expectedNext else {
      return (next, .alreadyComplete)
    }
    guard input == expected else {
      next.recordError()
      return (next, .incorrect(expected: expected))
    }
    next.recordCorrect()
    return (next, .correct(completed: next.isComplete))
  }
}
