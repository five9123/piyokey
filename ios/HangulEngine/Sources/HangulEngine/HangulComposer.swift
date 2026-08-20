/// The externally visible phases of the two-beolsik composition automaton.
public enum CompositionPhase: String, Equatable, Sendable {
  case empty
  case cho
  case jung
  case choJung
  case choJungJong
}

public enum CompositionEvent: Equatable, Sendable {
  case key(Character)
  case backspace
}

private struct CompositionBuffer: Equatable, Sendable {
  var leading: Character?
  var medial: Character?
  var trailing: Character?
}

private struct CompositionSnapshot: Equatable, Sendable {
  var committed = ""
  var buffer = CompositionBuffer()
}

/// Value state for the composition automaton. All transitions return a new value.
public struct CompositionState: Equatable, Sendable {
  private var snapshot = CompositionSnapshot()
  private var undoHistory: [CompositionSnapshot] = []

  public init() {}

  public var committedText: String { snapshot.committed }

  public var composingText: String { Self.render(snapshot.buffer) }

  public var text: String { committedText + composingText }

  public var phase: CompositionPhase {
    switch (snapshot.buffer.leading, snapshot.buffer.medial, snapshot.buffer.trailing) {
    case (nil, nil, nil): .empty
    case (.some, nil, nil): .cho
    case (nil, .some, nil): .jung
    case (.some, .some, nil): .choJung
    case (.some, .some, .some): .choJungJong
    default: .empty
    }
  }

  private static func render(_ buffer: CompositionBuffer) -> String {
    guard let leading = buffer.leading else {
      return buffer.medial.map(String.init) ?? ""
    }
    guard let medial = buffer.medial else {
      return String(leading)
    }
    guard
      let leadingIndex = HangulTables.leadingIndex[leading],
      let medialIndex = HangulTables.medialIndex[medial]
    else {
      return String(leading) + String(medial)
    }
    let trailingIndex = buffer.trailing.flatMap { HangulTables.trailingIndex[$0] } ?? 0
    let scalarValue = 0xAC00 + (leadingIndex * 21 + medialIndex) * 28 + trailingIndex
    return UnicodeScalar(scalarValue).map { String(Character($0)) } ?? ""
  }

  private mutating func flushBuffer() {
    snapshot.committed += Self.render(snapshot.buffer)
    snapshot.buffer = CompositionBuffer()
  }

  private mutating func accept(_ key: Character) {
    undoHistory.append(snapshot)

    if HangulTables.medialIndex[key] != nil {
      acceptVowel(key)
    } else if HangulTables.leadingIndex[key] != nil {
      acceptConsonant(key)
    } else {
      flushBuffer()
      snapshot.committed.append(key)
    }
  }

  private mutating func acceptConsonant(_ consonant: Character) {
    let buffer = snapshot.buffer

    guard buffer.leading != nil || buffer.medial != nil else {
      snapshot.buffer.leading = consonant
      return
    }

    guard buffer.leading != nil else {
      flushBuffer()
      snapshot.buffer.leading = consonant
      return
    }

    guard buffer.medial != nil else {
      flushBuffer()
      snapshot.buffer.leading = consonant
      return
    }

    guard let trailing = buffer.trailing else {
      if HangulTables.trailingIndex[consonant] != nil {
        snapshot.buffer.trailing = consonant
      } else {
        flushBuffer()
        snapshot.buffer.leading = consonant
      }
      return
    }

    if let compound = HangulTables.compoundTrailing[JamoPair(trailing, consonant)] {
      snapshot.buffer.trailing = compound
    } else {
      flushBuffer()
      snapshot.buffer.leading = consonant
    }
  }

  private mutating func acceptVowel(_ vowel: Character) {
    let buffer = snapshot.buffer

    guard buffer.leading != nil || buffer.medial != nil else {
      snapshot.buffer.medial = vowel
      return
    }

    if buffer.leading == nil, let medial = buffer.medial {
      if let compound = HangulTables.compoundMedial[JamoPair(medial, vowel)] {
        snapshot.buffer.medial = compound
      } else {
        flushBuffer()
        snapshot.buffer.medial = vowel
      }
      return
    }

    guard let medial = buffer.medial else {
      snapshot.buffer.medial = vowel
      return
    }

    guard let trailing = buffer.trailing else {
      if let compound = HangulTables.compoundMedial[JamoPair(medial, vowel)] {
        snapshot.buffer.medial = compound
      } else {
        flushBuffer()
        snapshot.buffer.medial = vowel
      }
      return
    }

    let carried: Character
    if let split = HangulTables.splitTrailing[trailing] {
      snapshot.buffer.trailing = split.first
      carried = split.second
    } else {
      snapshot.buffer.trailing = nil
      carried = trailing
    }
    flushBuffer()
    snapshot.buffer.leading = carried
    snapshot.buffer.medial = vowel
  }

  private mutating func eraseLastInput() {
    guard let previous = undoHistory.popLast() else { return }
    snapshot = previous
  }

  fileprivate mutating func apply(_ event: CompositionEvent) {
    switch event {
    case .key(let key): accept(key)
    case .backspace: eraseLastInput()
    }
  }
}

public enum HangulComposer {
  /// Pure reducer for one keyboard event.
  public static func reduce(_ state: CompositionState, event: CompositionEvent) -> CompositionState
  {
    var next = state
    next.apply(event)
    return next
  }

  public static func compose<S: Sequence>(_ keys: S) -> CompositionState
  where S.Element == Character {
    keys.reduce(into: CompositionState()) { state, key in
      state = reduce(state, event: .key(key))
    }
  }
}
