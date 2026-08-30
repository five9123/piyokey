public enum OSIMETextJudgeStatus: Equatable, Sendable {
  /// The full visible text is a valid target prefix. Marked text may still change.
  case matching(completed: Bool, isComposing: Bool)
  /// Only the marked (unconfirmed) portion differs, so no mistake is recorded.
  case composingMismatch
  /// An ASCII key arrived from a non-Korean input source and must not affect the session.
  case unsupportedASCIIInput
  /// Confirmed text differs from the target and counts as one mistake.
  case confirmedMismatch(expectedIndex: Int)
}

public struct OSIMETextEvaluation: Equatable, Sendable {
  public let status: OSIMETextJudgeStatus
  /// The longest safe target prefix that the session may reflect immediately.
  public let acceptedSequence: [Character]

  public init(status: OSIMETextJudgeStatus, acceptedSequence: [Character]) {
    self.status = status
    self.acceptedSequence = acceptedSequence
  }
}

public enum OSIMETextJudge {
  /// Judges text received from an OS IME against a target's two-beolsik jamo sequence.
  ///
  /// `committedText` excludes the active marked range. `markedText` is the active
  /// composition at the end of that text, if any. A mismatching marked range is
  /// intentionally ignored until the IME confirms it.
  public static func evaluate(
    target: String,
    committedText: String,
    markedText: String? = nil
  ) throws -> OSIMETextEvaluation {
    let expected = try JamoDecomposer.keySequence(for: target)
    if containsUnsupportedASCII(in: committedText) || containsUnsupportedASCII(in: markedText ?? "") {
      let validPrefix = longestDecomposablePrefix(of: committedText)
      if validPrefix.count >= expected.count,
        Array(validPrefix.prefix(expected.count)) == expected
      {
        return OSIMETextEvaluation(
          status: .matching(completed: true, isComposing: false),
          acceptedSequence: expected
        )
      }
      let acceptedCount = mismatchIndex(candidate: validPrefix, expected: expected)
        ?? min(validPrefix.count, expected.count)
      return OSIMETextEvaluation(
        status: .unsupportedASCIIInput,
        acceptedSequence: Array(expected.prefix(acceptedCount))
      )
    }

    guard let committed = try? keySequenceAllowingEmpty(for: committedText) else {
      let validPrefix = longestDecomposablePrefix(of: committedText)
      if validPrefix.count >= expected.count,
        Array(validPrefix.prefix(expected.count)) == expected
      {
        return OSIMETextEvaluation(
          status: .matching(completed: true, isComposing: false),
          acceptedSequence: expected
        )
      }
      let mismatch = mismatchIndex(candidate: validPrefix, expected: expected) ?? validPrefix.count
      return OSIMETextEvaluation(
        status: .confirmedMismatch(expectedIndex: mismatch),
        acceptedSequence: Array(expected.prefix(mismatch))
      )
    }

    if committed.count >= expected.count,
      Array(committed.prefix(expected.count)) == expected
    {
      return OSIMETextEvaluation(
        status: .matching(completed: true, isComposing: false),
        acceptedSequence: expected
      )
    }

    guard let committedMismatch = mismatchIndex(candidate: committed, expected: expected) else {
      if let markedText, !markedText.isEmpty {
        guard let marked = try? keySequenceAllowingEmpty(for: markedText) else {
          return OSIMETextEvaluation(
            status: .composingMismatch,
            acceptedSequence: committed
          )
        }
        let visible = committed + marked
        guard mismatchIndex(candidate: visible, expected: expected) == nil else {
          return OSIMETextEvaluation(
            status: .composingMismatch,
            acceptedSequence: committed
          )
        }
        return OSIMETextEvaluation(
          status: .matching(completed: visible.count == expected.count, isComposing: true),
          acceptedSequence: visible
        )
      }

      return OSIMETextEvaluation(
        status: .matching(completed: committed.count == expected.count, isComposing: false),
        acceptedSequence: committed
      )
    }

    return OSIMETextEvaluation(
      status: .confirmedMismatch(expectedIndex: committedMismatch),
      acceptedSequence: Array(expected.prefix(committedMismatch))
    )
  }

  private static func keySequenceAllowingEmpty(for text: String) throws -> [Character] {
    text.isEmpty ? [] : try JamoDecomposer.keySequence(for: text)
  }

  private static func containsUnsupportedASCII(in text: String) -> Bool {
    text.unicodeScalars.contains { scalar in
      scalar.isASCII && scalar.value != 0x20
    }
  }

  private static func mismatchIndex(
    candidate: [Character],
    expected: [Character]
  ) -> Int? {
    for (index, input) in candidate.enumerated() {
      guard index < expected.count, input == expected[index] else { return index }
    }
    return nil
  }

  private static func longestDecomposablePrefix(of text: String) -> [Character] {
    var result: [Character] = []
    for character in text {
      guard let sequence = try? JamoDecomposer.keySequence(for: String(character)) else {
        break
      }
      result.append(contentsOf: sequence)
    }
    return result
  }
}
