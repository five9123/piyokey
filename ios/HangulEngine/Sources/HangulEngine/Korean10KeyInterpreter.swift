/// One physical key on Apple's Korean 10-key layout.
public enum Korean10KeyKey: String, CaseIterable, Hashable, Sendable {
  case vowelI
  case vowelDot
  case vowelEu
  case giyeokKieuk
  case nieunRieul
  case digeutTieut
  case bieupPieup
  case siotHieut
  case jieutChieut
  case ieungMieum
  case commit
  case space
  case backspace
}

/// Value state for the Korean 10-key multi-tap interpreter.
public struct Korean10KeyState: Equatable, Sendable {
  fileprivate var pendingConsonantKey: Korean10KeyKey?
  fileprivate var consonantTapCount = 0
  fileprivate var pendingVowelKeys: [Korean10KeyKey] = []

  public init() {}

  public var hasPendingInput: Bool {
    pendingConsonantKey != nil || !pendingVowelKeys.isEmpty
  }
}

public enum Korean10KeyOutput: Equatable, Sendable {
  case character(Character)
  case backspace
}

public struct Korean10KeyTransition: Equatable, Sendable {
  public let state: Korean10KeyState
  public let outputs: [Korean10KeyOutput]

  public init(state: Korean10KeyState, outputs: [Korean10KeyOutput]) {
    self.state = state
    self.outputs = outputs
  }
}

/// Pure reducer that translates Apple-style Korean 10-key taps into the logical
/// two-beolsik jamo events already consumed by `HangulComposer` and the jamo judge.
public enum Korean10KeyInterpreter {
  private struct KeySequence: Hashable, Sendable {
    let keys: [Korean10KeyKey]
  }

  private static let consonantCycles: [Korean10KeyKey: [Character]] = [
    .giyeokKieuk: Array("ㄱㅋㄲ"),
    .nieunRieul: Array("ㄴㄹ"),
    .digeutTieut: Array("ㄷㅌㄸ"),
    .bieupPieup: Array("ㅂㅍㅃ"),
    .siotHieut: Array("ㅅㅎㅆ"),
    .jieutChieut: Array("ㅈㅊㅉ"),
    .ieungMieum: Array("ㅇㅁ"),
  ]

  private static let vowelBySequence: [KeySequence: Character] = [
    KeySequence(keys: [.vowelI]): "ㅣ",
    KeySequence(keys: [.vowelEu]): "ㅡ",
    KeySequence(keys: [.vowelI, .vowelDot]): "ㅏ",
    KeySequence(keys: [.vowelI, .vowelDot, .vowelDot]): "ㅑ",
    KeySequence(keys: [.vowelDot, .vowelI]): "ㅓ",
    KeySequence(keys: [.vowelDot, .vowelDot, .vowelI]): "ㅕ",
    KeySequence(keys: [.vowelDot, .vowelEu]): "ㅗ",
    KeySequence(keys: [.vowelDot, .vowelDot, .vowelEu]): "ㅛ",
    KeySequence(keys: [.vowelEu, .vowelDot]): "ㅜ",
    KeySequence(keys: [.vowelEu, .vowelDot, .vowelDot]): "ㅠ",
    KeySequence(keys: [.vowelI, .vowelDot, .vowelI]): "ㅐ",
    KeySequence(keys: [.vowelI, .vowelDot, .vowelDot, .vowelI]): "ㅒ",
    KeySequence(keys: [.vowelDot, .vowelI, .vowelI]): "ㅔ",
    KeySequence(keys: [.vowelDot, .vowelDot, .vowelI, .vowelI]): "ㅖ",
  ]

  private static let sequenceByVowel: [Character: [Korean10KeyKey]] = Dictionary(
    uniqueKeysWithValues: vowelBySequence.map { ($0.value, $0.key.keys) }
  )

  public static func reduce(
    _ state: Korean10KeyState,
    key: Korean10KeyKey
  ) -> Korean10KeyTransition {
    var next = state
    var outputs: [Korean10KeyOutput] = []

    switch key {
    case .backspace:
      if next.hasPendingInput {
        clearPending(&next)
      } else {
        outputs.append(.backspace)
      }

    case .commit:
      flushPending(&next, into: &outputs)

    case .space:
      flushPending(&next, into: &outputs)
      outputs.append(.character(" "))

    case .vowelI, .vowelDot, .vowelEu:
      flushConsonant(&next, into: &outputs)
      appendVowel(key, to: &next, outputs: &outputs)

    case .giyeokKieuk, .nieunRieul, .digeutTieut, .bieupPieup,
      .siotHieut, .jieutChieut, .ieungMieum:
      flushVowel(&next, into: &outputs)
      if next.pendingConsonantKey == key {
        next.consonantTapCount += 1
      } else {
        flushConsonant(&next, into: &outputs)
        next.pendingConsonantKey = key
        next.consonantTapCount = 1
      }
    }

    return Korean10KeyTransition(state: next, outputs: outputs)
  }

  /// Resolves the currently selected consonant or complete vowel after the tap window expires.
  public static func timeout(_ state: Korean10KeyState) -> Korean10KeyTransition {
    var next = state
    var outputs: [Korean10KeyOutput] = []
    flushPending(&next, into: &outputs)
    return Korean10KeyTransition(state: next, outputs: outputs)
  }

  public static func reset(_: Korean10KeyState) -> Korean10KeyState {
    Korean10KeyState()
  }

  /// The logical jamo currently selected by the pending tap sequence, when complete.
  public static func pendingCharacters(in state: Korean10KeyState) -> [Character] {
    if let consonant = selectedConsonant(in: state) {
      return [consonant]
    }
    if let vowel = vowelBySequence[KeySequence(keys: state.pendingVowelKeys)] {
      return [vowel]
    }
    return []
  }

  /// A compact glyph preview for the uncommitted selection.
  public static func pendingPreview(in state: Korean10KeyState) -> String {
    if let character = selectedConsonant(in: state) {
      return String(character)
    }
    if let vowel = vowelBySequence[KeySequence(keys: state.pendingVowelKeys)] {
      return String(vowel)
    }
    return state.pendingVowelKeys.map(displayLabel).joined()
  }

  /// Canonical physical key sequence for one logical two-beolsik jamo.
  public static func keySequence(for character: Character) -> [Korean10KeyKey]? {
    if character == " " { return [.space] }
    if let vowelSequence = sequenceByVowel[character] { return vowelSequence }
    for (key, cycle) in consonantCycles {
      guard let index = cycle.firstIndex(of: character) else { continue }
      return Array(repeating: key, count: index + 1)
    }
    return nil
  }

  /// Recommended next physical key for the current target and pending selection.
  public static func nextKey(
    for expectedCharacter: Character?,
    state: Korean10KeyState
  ) -> Korean10KeyKey? {
    guard let expectedCharacter,
      let desiredSequence = keySequence(for: expectedCharacter),
      let first = desiredSequence.first
    else { return nil }

    if let pendingKey = state.pendingConsonantKey {
      guard pendingKey == first else { return .backspace }
      if selectedConsonant(in: state) == expectedCharacter { return .commit }
      return pendingKey
    }

    if !state.pendingVowelKeys.isEmpty {
      guard state.pendingVowelKeys.count <= desiredSequence.count,
        desiredSequence.starts(with: state.pendingVowelKeys)
      else { return .backspace }
      if state.pendingVowelKeys.count == desiredSequence.count { return .commit }
      return desiredSequence[state.pendingVowelKeys.count]
    }

    return first
  }

  public static func displayLabel(for key: Korean10KeyKey) -> String {
    switch key {
    case .vowelI: "ㅣ"
    case .vowelDot: "ㆍ"
    case .vowelEu: "ㅡ"
    case .giyeokKieuk: "ㄱㅋ"
    case .nieunRieul: "ㄴㄹ"
    case .digeutTieut: "ㄷㅌ"
    case .bieupPieup: "ㅂㅍ"
    case .siotHieut: "ㅅㅎ"
    case .jieutChieut: "ㅈㅊ"
    case .ieungMieum: "ㅇㅁ"
    case .commit: "→"
    case .space: ""
    case .backspace: ""
    }
  }

  private static func appendVowel(
    _ key: Korean10KeyKey,
    to state: inout Korean10KeyState,
    outputs: inout [Korean10KeyOutput]
  ) {
    let candidate = state.pendingVowelKeys + [key]
    if isVowelPrefix(candidate) {
      state.pendingVowelKeys = candidate
      return
    }

    flushVowel(&state, into: &outputs)
    if isVowelPrefix([key]) {
      state.pendingVowelKeys = [key]
    }
  }

  private static func isVowelPrefix(_ keys: [Korean10KeyKey]) -> Bool {
    vowelBySequence.keys.contains { $0.keys.starts(with: keys) }
  }

  private static func selectedConsonant(in state: Korean10KeyState) -> Character? {
    guard let key = state.pendingConsonantKey,
      let cycle = consonantCycles[key],
      !cycle.isEmpty,
      state.consonantTapCount > 0
    else { return nil }
    return cycle[(state.consonantTapCount - 1) % cycle.count]
  }

  private static func flushPending(
    _ state: inout Korean10KeyState,
    into outputs: inout [Korean10KeyOutput]
  ) {
    flushConsonant(&state, into: &outputs)
    flushVowel(&state, into: &outputs)
  }

  private static func flushConsonant(
    _ state: inout Korean10KeyState,
    into outputs: inout [Korean10KeyOutput]
  ) {
    if let consonant = selectedConsonant(in: state) {
      outputs.append(.character(consonant))
    }
    state.pendingConsonantKey = nil
    state.consonantTapCount = 0
  }

  private static func flushVowel(
    _ state: inout Korean10KeyState,
    into outputs: inout [Korean10KeyOutput]
  ) {
    if let vowel = vowelBySequence[KeySequence(keys: state.pendingVowelKeys)] {
      outputs.append(.character(vowel))
    }
    state.pendingVowelKeys.removeAll(keepingCapacity: true)
  }

  private static func clearPending(_ state: inout Korean10KeyState) {
    state.pendingConsonantKey = nil
    state.consonantTapCount = 0
    state.pendingVowelKeys.removeAll(keepingCapacity: true)
  }
}
