/// Logical keys used by Apple's Korean 10-Key/Cheonjiin layout.
///
/// This type contains no UI behavior. The app owns key rendering and input
/// feedback, while the shared engine owns the canonical jamo recipes.
public enum Korean10KeyKey: String, CaseIterable, Hashable, Sendable {
  case vertical
  case dot
  case horizontal
  case giyeok
  case nieun
  case digeut
  case bieup
  case siot
  case jieut
  case ieung
  case next
  case space
}

/// Canonical recipes shared by the in-app 10-key adapter and OS IME judging.
public enum Korean10KeyRecipe {
  public static func recipe(for jamo: Character) -> [Korean10KeyKey]? {
    recipes[jamo]
  }

  public static func jamo(forExactRecipe recipe: [Korean10KeyKey]) -> Character? {
    recipes.first(where: { $0.value == recipe })?.key
  }

  /// Returns whether `intermediate` is a completed vowel reachable before
  /// `target` while following the target's Cheonjiin stroke recipe.
  public static func isReachableIntermediateVowel(
    _ intermediate: Character,
    toward target: Character
  ) -> Bool {
    guard intermediate != target,
      vowelJamo.contains(intermediate),
      vowelJamo.contains(target),
      let intermediateRecipe = recipes[intermediate],
      let targetRecipe = recipes[target]
    else { return false }
    return targetRecipe.starts(with: intermediateRecipe)
  }

  /// Returns whether `intermediate` is a tap-cycle consonant reachable before
  /// `target` while following the target's Cheonjiin consonant recipe.
  public static func isReachableIntermediateConsonant(
    _ intermediate: Character,
    toward target: Character
  ) -> Bool {
    guard intermediate != target,
      consonantJamo.contains(intermediate),
      consonantJamo.contains(target),
      let intermediateRecipe = recipes[intermediate],
      let targetRecipe = recipes[target]
    else { return false }
    return targetRecipe.starts(with: intermediateRecipe)
  }

  /// Restricts the OS-IME exception to a differing final committed syllable.
  /// All earlier characters must already equal the target, the leading jamo
  /// must stay unchanged, and the candidate must not yet have a trailing jamo.
  static func committedDocumentEndsInReachableIntermediate(
    target: String,
    committedText: String
  ) -> Bool {
    let targetCharacters = Array(target)
    let committedCharacters = Array(committedText)
    guard let candidateCharacter = committedCharacters.last,
      committedCharacters.count <= targetCharacters.count
    else { return false }

    let lastIndex = committedCharacters.count - 1
    guard committedCharacters.dropLast().elementsEqual(targetCharacters.prefix(lastIndex)) else {
      return false
    }

    let targetCharacter = targetCharacters[lastIndex]
    guard let candidate = syllableComponents(of: candidateCharacter),
      let target = syllableComponents(of: targetCharacter),
      candidate.leading == target.leading,
      candidate.trailing == nil
    else { return false }

    return isReachableIntermediateVowel(candidate.medial, toward: target.medial)
  }

  /// Recognizes a committed Cheonjiin stroke prefix that cannot be decomposed
  /// as modern Hangul yet, such as the measured `ㄷㆍ` state on the way to
  /// `돼`. A normal Dubeolsik typo cannot contain these layout-only strokes.
  static func committedDocumentEndsInReachableRawVowelPrefix(
    target: String,
    committedText: String
  ) -> Bool {
    let targetCharacters = Array(target)
    let committedCharacters = Array(committedText)

    for targetIndex in targetCharacters.indices {
      let stablePrefix = targetCharacters.prefix(targetIndex)
      guard committedCharacters.starts(with: stablePrefix) else { continue }

      let active = committedCharacters.dropFirst(stablePrefix.count)
      guard let target = syllableComponents(of: targetCharacters[targetIndex]),
        let targetRecipe = recipes[target.medial]
      else { continue }

      let rawStrokes: ArraySlice<Character>
      if let leading = target.leading {
        guard active.count >= 2, active.first == leading else { continue }
        rawStrokes = active.dropFirst()
      } else {
        guard !active.isEmpty else { continue }
        rawStrokes = active
      }
      guard rawStrokes.allSatisfy({ rawVowelKey[$0] != nil }) else { continue }

      let rawRecipe = rawStrokes.compactMap { rawVowelKey[$0] }
      if rawRecipe.count < targetRecipe.count, targetRecipe.starts(with: rawRecipe) {
        return true
      }
    }

    return false
  }

  /// Recognizes only recipe-backed Cheonjiin consonant cycle states. These can
  /// appear as a standalone next onset, temporarily attach to the preceding
  /// open syllable, or replace a target syllable's final consonant.
  static func committedDocumentEndsInReachableConsonantCycle(
    target: String,
    committedText: String
  ) -> Bool {
    let targetCharacters = Array(target)
    let committedCharacters = Array(committedText)
    guard let candidateCharacter = committedCharacters.last else { return false }

    // Standalone current consonant, including the onset before a syllable:
    // `ㄴ` -> `ㄹ`, `대ㅅ` -> `대ㅎ`.
    if committedCharacters.count <= targetCharacters.count {
      let targetIndex = committedCharacters.count - 1
      if committedCharacters.dropLast().elementsEqual(targetCharacters.prefix(targetIndex)),
        let candidateConsonant = standaloneConsonant(of: candidateCharacter),
        let targetConsonant = leadingConsonant(of: targetCharacters[targetIndex]),
        isReachableIntermediateConsonant(candidateConsonant, toward: targetConsonant)
      {
        return true
      }

      // A final consonant tap-cycle within the same syllable: `단` -> `달`.
      if committedCharacters.dropLast().elementsEqual(targetCharacters.prefix(targetIndex)),
        let candidate = syllableComponents(of: candidateCharacter),
        let target = syllableComponents(of: targetCharacters[targetIndex]),
        candidate.leading == target.leading,
        candidate.medial == target.medial,
        let candidateTrailing = candidate.trailing,
        let targetTrailing = target.trailing,
        isReachableIntermediateConsonant(candidateTrailing, toward: targetTrailing)
      {
        return true
      }
    }

    // The next onset may be provisionally absorbed as the previous syllable's
    // final consonant: `댓` -> `대형` while `ㅅ` cycles toward `ㅎ`.
    let nextTargetIndex = committedCharacters.count
    guard nextTargetIndex > 0, nextTargetIndex < targetCharacters.count,
      committedCharacters.dropLast().elementsEqual(targetCharacters.prefix(nextTargetIndex - 1)),
      let candidate = syllableComponents(of: candidateCharacter),
      let previousTarget = syllableComponents(of: targetCharacters[nextTargetIndex - 1]),
      candidate.leading == previousTarget.leading,
      candidate.medial == previousTarget.medial,
      previousTarget.trailing == nil,
      let candidateTrailing = candidate.trailing,
      let nextTargetLeading = leadingConsonant(of: targetCharacters[nextTargetIndex])
    else { return false }

    return isReachableIntermediateConsonant(candidateTrailing, toward: nextTargetLeading)
  }

  /// Recognizes a closed-syllable boundary where the next onset's tap-cycle
  /// precursor temporarily combines with the preceding syllable's original
  /// final. For example, `일해` may expose `잀` while `ㅅ` cycles toward `ㅎ`.
  /// The candidate compound final must split back to that exact original final
  /// and a real prefix of the exact next target onset recipe.
  static func committedDocumentEndsInReachableClosedSyllableBoundary(
    target: String,
    committedText: String
  ) -> Bool {
    let targetCharacters = Array(target)
    let committedCharacters = Array(committedText)
    let nextTargetIndex = committedCharacters.count
    guard nextTargetIndex > 0, nextTargetIndex < targetCharacters.count,
      let candidateCharacter = committedCharacters.last,
      committedCharacters.dropLast().elementsEqual(targetCharacters.prefix(nextTargetIndex - 1)),
      let candidate = syllableComponents(of: candidateCharacter),
      let previousTarget = syllableComponents(of: targetCharacters[nextTargetIndex - 1]),
      candidate.leading == previousTarget.leading,
      candidate.medial == previousTarget.medial,
      let originalTrailing = previousTarget.trailing,
      let candidateTrailing = candidate.trailing,
      let candidatePair = HangulTables.splitTrailing[candidateTrailing],
      candidatePair.first == originalTrailing,
      let nextTargetLeading = leadingConsonant(of: targetCharacters[nextTargetIndex])
    else { return false }

    return isReachableIntermediateConsonant(
      candidatePair.second,
      toward: nextTargetLeading
    )
  }

  /// Recognizes only target-derived intermediate states while assembling a
  /// complex final. A simple candidate may be a recipe prefix of the target's
  /// first component (`단` -> `닭`), or a compound candidate may contain the
  /// exact first component plus a prefix of the second (`앐` -> `앓`).
  static func committedDocumentEndsInReachableComplexTrailingAssembly(
    target: String,
    committedText: String
  ) -> Bool {
    let targetCharacters = Array(target)
    let committedCharacters = Array(committedText)
    guard let candidateCharacter = committedCharacters.last,
      committedCharacters.count <= targetCharacters.count
    else { return false }

    let targetIndex = committedCharacters.count - 1
    guard committedCharacters.dropLast().elementsEqual(targetCharacters.prefix(targetIndex)),
      let candidate = syllableComponents(of: candidateCharacter),
      let target = syllableComponents(of: targetCharacters[targetIndex]),
      candidate.leading == target.leading,
      candidate.medial == target.medial,
      let candidateTrailing = candidate.trailing,
      let targetTrailing = target.trailing,
      let targetPair = HangulTables.splitTrailing[targetTrailing]
    else { return false }

    if HangulTables.splitTrailing[candidateTrailing] == nil {
      return isReachableIntermediateConsonant(
        candidateTrailing,
        toward: targetPair.first
      )
    }

    guard let candidatePair = HangulTables.splitTrailing[candidateTrailing],
      candidatePair.first == targetPair.first
    else { return false }
    return isReachableIntermediateConsonant(
      candidatePair.second,
      toward: targetPair.second
    )
  }

  private struct SyllableComponents {
    let leading: Character?
    let medial: Character
    let trailing: Character?
  }

  private static func syllableComponents(of character: Character) -> SyllableComponents? {
    if let medialIndex = HangulTables.medialIndex[character] {
      return SyllableComponents(
        leading: nil,
        medial: HangulTables.medial[medialIndex],
        trailing: nil
      )
    }

    guard character.unicodeScalars.count == 1,
      let scalar = character.unicodeScalars.first,
      (0xAC00...0xD7A3).contains(Int(scalar.value))
    else { return nil }

    let syllableIndex = Int(scalar.value) - 0xAC00
    return SyllableComponents(
      leading: HangulTables.leading[syllableIndex / (21 * 28)],
      medial: HangulTables.medial[(syllableIndex % (21 * 28)) / 28],
      trailing: HangulTables.trailing[syllableIndex % 28]
    )
  }

  private static func standaloneConsonant(of character: Character) -> Character? {
    HangulTables.leadingIndex[character] == nil ? nil : character
  }

  private static func leadingConsonant(of character: Character) -> Character? {
    standaloneConsonant(of: character) ?? syllableComponents(of: character)?.leading
  }

  private static let vowelJamo = Set(HangulTables.medial)
  private static let consonantJamo = Set(HangulTables.leading)
  private static let rawVowelKey: [Character: Korean10KeyKey] = [
    "ㅣ": .vertical,
    "ㆍ": .dot,
    "ㅡ": .horizontal,
  ]

  // Golden recipe contract approved for the Apple Korean 10-Key layout in
  // Issue #11. Grouped consonants cycle in label order and then to the tense
  // consonant; vowels retain the visible ㅣ·ㅡ stroke order.
  private static let recipes: [Character: [Korean10KeyKey]] = [
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
}
