import SwiftUI
import UIKit

enum KeyboardPreferenceKeys {
  static let showsKeyGuide = "keyboard.shows_key_guide"
  static let showsRomanHints = "keyboard.shows_roman_hints"
  static let hapticsEnabled = "keyboard.haptics_enabled"
  static let inputModeDefault = "keyboard.input_mode_default"
  static let showsPhysicalKeyboardGuide = "keyboard.shows_physical_keyboard_guide"
  static let builtInLayoutDefault = "keyboard.builtin_layout_default"

  static let all = [
    showsKeyGuide,
    showsRomanHints,
    hapticsEnabled,
    inputModeDefault,
    showsPhysicalKeyboardGuide,
    builtInLayoutDefault,
  ]
}

enum BuiltInKeyboardLayout: String, CaseIterable, Equatable {
  case dubeolsik
  case korean10Key = "korean_10key"

  static func resolved(from rawValue: String) -> Self {
    Self(rawValue: rawValue) ?? .dubeolsik
  }

  var gameRecordInputMode: SessionInputMode {
    switch self {
    case .dubeolsik: .builtIn
    case .korean10Key: .builtInKorean10Key
    }
  }
}

extension SessionInputMode {
  var resultLabelKey: LocalizedStringKey {
    switch self {
    case .builtIn: "input_mode.builtin"
    case .builtInKorean10Key: "input_mode.builtin_korean_10key"
    case .osIME: "input_mode.os_ime"
    }
  }

  var resultSystemImage: String {
    switch self {
    case .builtIn: "rectangle.grid.3x2.fill"
    case .builtInKorean10Key: "rectangle.grid.3x2"
    case .osIME: "keyboard"
    }
  }
}

enum Korean10KeyKey: String, CaseIterable, Hashable {
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

  var displayText: String {
    switch self {
    case .vertical: "ㅣ"
    case .dot: "ㆍ"
    case .horizontal: "ㅡ"
    case .giyeok: "ㄱㅋ"
    case .nieun: "ㄴㄹ"
    case .digeut: "ㄷㅌ"
    case .bieup: "ㅂㅍ"
    case .siot: "ㅅㅎ"
    case .jieut: "ㅈㅊ"
    case .ieung: "ㅇㅁ"
    case .next: "→"
    case .space: " "
    }
  }

  var accessibilityKey: LocalizedStringKey {
    switch self {
    case .vertical: "keyboard.10key.vertical"
    case .dot: "keyboard.10key.dot"
    case .horizontal: "keyboard.10key.horizontal"
    case .giyeok: "keyboard.10key.giyeok"
    case .nieun: "keyboard.10key.nieun"
    case .digeut: "keyboard.10key.digeut"
    case .bieup: "keyboard.10key.bieup"
    case .siot: "keyboard.10key.siot"
    case .jieut: "keyboard.10key.jieut"
    case .ieung: "keyboard.10key.ieung"
    case .next: "keyboard.10key.next"
    case .space: "keyboard.space"
    }
  }
}

enum Korean10KeyInterpretation: Equatable {
  case pending(display: String)
  case separatorAccepted
  case committed(Character)
  case incorrect(expected: Character?)
}

enum Korean10KeyBackspaceResult: Equatable {
  case pendingChanged(display: String?)
  case forwardToHangulEngine
}

/// Target-aware adapter for Apple's Korean 10-Key stroke order.
///
/// The adapter holds only an unfinished 10-key recipe. A standard compatibility
/// jamo is emitted once the recipe for the session's next expected jamo is
/// complete, so the shared Hangul composer and jamo judge remain unchanged.
struct Korean10KeyInterpreter: Equatable {
  private(set) var pendingKeys: [Korean10KeyKey] = []
  private var lastCommittedGroupedConsonantKey: Korean10KeyKey?
  private var didAcceptSeparator = false

  var pendingDisplay: String? { Self.display(for: pendingKeys) }

  mutating func input(
    _ key: Korean10KeyKey,
    expecting expected: Character?
  ) -> Korean10KeyInterpretation {
    guard let expected, let recipe = Self.recipe(for: expected) else {
      pendingKeys.removeAll(keepingCapacity: true)
      return .incorrect(expected: expected)
    }

    if key == .next {
      guard needsSeparator(before: recipe) else {
        return .incorrect(expected: expected)
      }
      didAcceptSeparator = true
      return .separatorAccepted
    }

    guard !needsSeparator(before: recipe) else {
      pendingKeys.removeAll(keepingCapacity: true)
      return .incorrect(expected: expected)
    }

    let proposed = pendingKeys + [key]
    guard recipe.starts(with: proposed) else {
      pendingKeys.removeAll(keepingCapacity: true)
      return .incorrect(expected: expected)
    }

    if proposed == recipe {
      pendingKeys.removeAll(keepingCapacity: true)
      lastCommittedGroupedConsonantKey = Self.groupedConsonantKeys.contains(key) ? key : nil
      didAcceptSeparator = false
      return .committed(expected)
    }

    pendingKeys = proposed
    return .pending(display: Self.display(for: proposed) ?? "")
  }

  mutating func backspace() -> Korean10KeyBackspaceResult {
    guard !pendingKeys.isEmpty else {
      lastCommittedGroupedConsonantKey = nil
      didAcceptSeparator = false
      return .forwardToHangulEngine
    }
    pendingKeys.removeLast()
    return .pendingChanged(display: pendingDisplay)
  }

  mutating func reset() {
    pendingKeys.removeAll(keepingCapacity: true)
    lastCommittedGroupedConsonantKey = nil
    didAcceptSeparator = false
  }

  func nextKey(for expected: Character?) -> Korean10KeyKey? {
    guard let expected, let recipe = Self.recipe(for: expected),
      recipe.starts(with: pendingKeys), pendingKeys.count < recipe.count
    else { return nil }
    if needsSeparator(before: recipe) { return .next }
    return recipe[pendingKeys.count]
  }

  static func recipe(for jamo: Character) -> [Korean10KeyKey]? {
    recipes[jamo]
  }

  private static func display(for keys: [Korean10KeyKey]) -> String? {
    guard !keys.isEmpty else { return nil }
    if let exact = recipes.first(where: { $0.value == keys })?.key {
      return String(exact)
    }
    return keys.map(\.displayText).joined()
  }

  private func needsSeparator(before recipe: [Korean10KeyKey]) -> Bool {
    guard !didAcceptSeparator, pendingKeys.isEmpty else { return false }
    return lastCommittedGroupedConsonantKey == recipe.first
  }

  private static let groupedConsonantKeys: Set<Korean10KeyKey> = [
    .giyeok, .nieun, .digeut, .bieup, .siot, .jieut, .ieung,
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

enum Korean10KeyGeometry {
  static func normalizedHorizontalPosition(for key: Korean10KeyKey?) -> CGFloat {
    switch key {
    case .vertical, .giyeok, .bieup, .next: -1
    case .dot, .nieun, .siot, .ieung: 0
    case .horizontal, .digeut, .jieut: 1
    case .space, nil: 0
    }
  }
}

struct HangulKeyboardOptions: Equatable {
  var showsKeyGuide = true
  var showsRomanHints = true
  var hapticsEnabled = true
}

enum HangulKeyboardGeometry {
  private static let rows = [
    Array("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ"),
    Array("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ"),
    Array("ㅋㅌㅊㅍㅠㅜㅡ"),
  ]
  private static let shiftedBase: [Character: Character] = [
    "ㅃ": "ㅂ", "ㅉ": "ㅈ", "ㄸ": "ㄷ", "ㄲ": "ㄱ", "ㅆ": "ㅅ",
    "ㅒ": "ㅐ", "ㅖ": "ㅔ",
  ]

  static func normalizedHorizontalPosition(for key: Character?) -> CGFloat {
    guard let key else { return 0 }
    let base = shiftedBase[key] ?? key
    for row in rows {
      guard let index = row.firstIndex(of: base), row.count > 1 else { continue }
      return CGFloat(index) / CGFloat(row.count - 1) * 2 - 1
    }
    return 0
  }
}

enum PhysicalKeyboardHand: Equatable {
  case left
  case right
  case both

  var localizationKey: String {
    switch self {
    case .left: "physical_keyboard.hand.left"
    case .right: "physical_keyboard.hand.right"
    case .both: "physical_keyboard.hand.both"
    }
  }

  var opposite: Self {
    switch self {
    case .left: .right
    case .right: .left
    case .both: .both
    }
  }
}

enum PhysicalKeyboardFinger: Equatable {
  case little
  case ring
  case middle
  case index
  case thumb

  var localizationKey: String {
    switch self {
    case .little: "physical_keyboard.finger.little"
    case .ring: "physical_keyboard.finger.ring"
    case .middle: "physical_keyboard.finger.middle"
    case .index: "physical_keyboard.finger.index"
    case .thumb: "physical_keyboard.finger.thumb"
    }
  }
}

struct PhysicalKeyboardKeySpec: Identifiable, Equatable {
  let latin: Character
  let baseJamo: Character
  let shiftedJamo: Character?
  let hand: PhysicalKeyboardHand
  let finger: PhysicalKeyboardFinger
  let isHomePosition: Bool

  var id: Character { latin }
}

struct PhysicalKeyboardTarget: Equatable {
  let key: PhysicalKeyboardKeySpec?
  let expected: Character
  let requiresShift: Bool
  let shiftHand: PhysicalKeyboardHand?

  var hand: PhysicalKeyboardHand { key?.hand ?? .both }
  var finger: PhysicalKeyboardFinger { key?.finger ?? .thumb }
}

enum PhysicalDubeolsikLayout {
  static let rows: [[PhysicalKeyboardKeySpec]] = [
    [
      key("Q", "ㅂ", shifted: "ㅃ", .left, .little),
      key("W", "ㅈ", shifted: "ㅉ", .left, .ring),
      key("E", "ㄷ", shifted: "ㄸ", .left, .middle),
      key("R", "ㄱ", shifted: "ㄲ", .left, .index),
      key("T", "ㅅ", shifted: "ㅆ", .left, .index),
      key("Y", "ㅛ", .right, .index),
      key("U", "ㅕ", .right, .index),
      key("I", "ㅑ", .right, .middle),
      key("O", "ㅐ", shifted: "ㅒ", .right, .ring),
      key("P", "ㅔ", shifted: "ㅖ", .right, .little),
    ],
    [
      key("A", "ㅁ", .left, .little),
      key("S", "ㄴ", .left, .ring),
      key("D", "ㅇ", .left, .middle),
      key("F", "ㄹ", .left, .index, home: true),
      key("G", "ㅎ", .left, .index),
      key("H", "ㅗ", .right, .index),
      key("J", "ㅓ", .right, .index, home: true),
      key("K", "ㅏ", .right, .middle),
      key("L", "ㅣ", .right, .ring),
    ],
    [
      key("Z", "ㅋ", .left, .little),
      key("X", "ㅌ", .left, .ring),
      key("C", "ㅊ", .left, .middle),
      key("V", "ㅍ", .left, .index),
      key("B", "ㅠ", .left, .index),
      key("N", "ㅜ", .right, .index),
      key("M", "ㅡ", .right, .index),
    ],
  ]

  static func target(for expected: Character?) -> PhysicalKeyboardTarget? {
    guard let expected else { return nil }
    if expected == " " {
      return PhysicalKeyboardTarget(
        key: nil,
        expected: expected,
        requiresShift: false,
        shiftHand: nil
      )
    }
    for key in rows.joined() {
      if key.baseJamo == expected {
        return PhysicalKeyboardTarget(
          key: key,
          expected: expected,
          requiresShift: false,
          shiftHand: nil
        )
      }
      if key.shiftedJamo == expected {
        return PhysicalKeyboardTarget(
          key: key,
          expected: expected,
          requiresShift: true,
          shiftHand: key.hand.opposite
        )
      }
    }
    return nil
  }

  private static func key(
    _ latin: Character,
    _ baseJamo: Character,
    shifted: Character? = nil,
    _ hand: PhysicalKeyboardHand,
    _ finger: PhysicalKeyboardFinger,
    home: Bool = false
  ) -> PhysicalKeyboardKeySpec {
    PhysicalKeyboardKeySpec(
      latin: latin,
      baseJamo: baseJamo,
      shiftedJamo: shifted,
      hand: hand,
      finger: finger,
      isHomePosition: home
    )
  }
}

struct PhysicalKeyboardGuideView: View {
  let nextExpectedKey: Character?

  private static let reservedTarget = PhysicalKeyboardTarget(
    key: nil,
    expected: " ",
    requiresShift: false,
    shiftHand: nil
  )

  private var target: PhysicalKeyboardTarget? {
    PhysicalDubeolsikLayout.target(for: nextExpectedKey)
  }

  var body: some View {
    VStack(spacing: 8) {
      guideHeader

      VStack(spacing: 6) {
        physicalRow(PhysicalDubeolsikLayout.rows[0], leadingInset: 0, trailingInset: 0)
        physicalRow(PhysicalDubeolsikLayout.rows[1], leadingInset: 14, trailingInset: 14)
        HStack(spacing: 5) {
          utilityKey(
            title: "⇧",
            identifier: "physical_keyboard.shift.left",
            highlighted: target?.shiftHand == .left
          )
          physicalKeys(PhysicalDubeolsikLayout.rows[2])
          utilityKey(
            title: "⇧",
            identifier: "physical_keyboard.shift.right",
            highlighted: target?.shiftHand == .right
          )
        }

        HStack(spacing: 7) {
          utilityKey(
            title: AppLocalization.string("physical_keyboard.space"),
            identifier: "physical_keyboard.space",
            highlighted: target?.expected == " ",
            width: 210
          )
          utilityKey(
            title: "⌫",
            identifier: "physical_keyboard.backspace",
            highlighted: false,
            width: 58
          )
        }
      }
    }
    .padding(.horizontal, 10)
    .padding(.vertical, 9)
    .background(AppPalette.card.opacity(0.96), in: RoundedRectangle(cornerRadius: 18))
    .overlay {
      RoundedRectangle(cornerRadius: 18)
        .stroke(AppPalette.keyShadow.opacity(0.8), lineWidth: 1)
    }
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("physical_keyboard.guide")
  }

  private var guideHeader: some View {
    ZStack {
      guideInstruction(target ?? Self.reservedTarget)
        .opacity(target == nil ? 0 : 1)
        .accessibilityHidden(target == nil)

      Text("physical_keyboard.ready")
        .font(.caption.weight(.bold))
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)
        .lineLimit(2)
        .minimumScaleFactor(0.7)
        .opacity(target == nil ? 1 : 0)
        .accessibilityHidden(target != nil)
        .accessibilityIdentifier("physical_keyboard.guide.ready")
    }
    .frame(maxWidth: .infinity)
  }

  private func guideInstruction(_ target: PhysicalKeyboardTarget) -> some View {
    let hand = AppLocalization.string(target.hand.localizationKey)
    let finger = AppLocalization.string(target.finger.localizationKey)
    return VStack(spacing: 2) {
      Text(
        AppLocalization.format("physical_keyboard.next_key_format",
          String(target.expected),
          target.key.map { String($0.latin) } ?? AppLocalization.string("physical_keyboard.space")
        )
      )
      .font(.caption.weight(.bold))
      .foregroundStyle(AppPalette.ink)
      .lineLimit(1)
      .minimumScaleFactor(0.7)

      Text(
        target.requiresShift
          ? AppLocalization.format("physical_keyboard.shift_finger_format",
            AppLocalization.string((target.shiftHand ?? .both).localizationKey),
            hand,
            finger
          )
          : AppLocalization.format("physical_keyboard.finger_format",
            hand,
            finger
          )
      )
      .font(.caption2.weight(.semibold))
      .foregroundStyle(AppPalette.secondary)
      .lineLimit(1)
      .minimumScaleFactor(0.7)
    }
    .accessibilityIdentifier("physical_keyboard.guide.instruction")
  }

  private func physicalRow(
    _ keys: [PhysicalKeyboardKeySpec],
    leadingInset: CGFloat,
    trailingInset: CGFloat
  ) -> some View {
    HStack(spacing: 5) {
      physicalKeys(keys)
    }
    .padding(.leading, leadingInset)
    .padding(.trailing, trailingInset)
  }

  private func physicalKeys(_ keys: [PhysicalKeyboardKeySpec]) -> some View {
    ForEach(keys) { key in
      physicalKey(key)
    }
  }

  private func physicalKey(_ key: PhysicalKeyboardKeySpec) -> some View {
    let highlighted = target?.key?.latin == key.latin
    let handColor = key.hand == .left ? AppPalette.secondary : AppPalette.accent
    return VStack(spacing: 0) {
      ZStack(alignment: .bottom) {
        Text(verbatim: String(key.baseJamo))
          .font(.system(size: 15, weight: .bold, design: .rounded))
          .minimumScaleFactor(0.7)
        if key.isHomePosition {
          Capsule()
            .fill(highlighted ? Color.white.opacity(0.85) : AppPalette.mutedInk.opacity(0.48))
            .frame(width: 10, height: 2)
            .padding(.bottom, 2)
        }
      }
      Text(verbatim: String(key.latin))
        .font(.system(size: 8, weight: .semibold, design: .rounded))
    }
    .foregroundStyle(highlighted ? Color.white : AppPalette.ink)
    .frame(maxWidth: .infinity, minHeight: 36)
    .background(
      highlighted ? handColor : AppPalette.backgroundTop,
      in: RoundedRectangle(cornerRadius: 8)
    )
    .overlay {
      RoundedRectangle(cornerRadius: 8)
        .stroke(highlighted ? handColor : AppPalette.keyShadow, lineWidth: highlighted ? 2 : 1)
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(
      Text(
        verbatim: AppLocalization.format("physical_keyboard.key_accessibility_format",
          String(key.baseJamo),
          String(key.latin),
          AppLocalization.string(key.hand.localizationKey),
          AppLocalization.string(key.finger.localizationKey)
        )
      )
    )
    .accessibilityAddTraits(highlighted ? .isSelected : [])
    .accessibilityIdentifier("physical_keyboard.key.\(key.latin)")
  }

  private func utilityKey(
    title: String,
    identifier: String,
    highlighted: Bool,
    width: CGFloat = 46
  ) -> some View {
    Text(verbatim: title)
      .font(.caption2.weight(.bold))
      .foregroundStyle(highlighted ? Color.white : AppPalette.mutedInk)
      .frame(maxWidth: width, minHeight: 34)
      .background(
        highlighted ? AppPalette.secondary : AppPalette.backgroundTop,
        in: RoundedRectangle(cornerRadius: 8)
      )
      .overlay {
        RoundedRectangle(cornerRadius: 8)
          .stroke(
            highlighted ? AppPalette.secondary : AppPalette.keyShadow,
            lineWidth: highlighted ? 2 : 1
          )
      }
      .accessibilityIdentifier(identifier)
      .accessibilityAddTraits(highlighted ? .isSelected : [])
  }
}

struct HangulKeyboardView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  let nextExpectedKey: Character?
  var options = HangulKeyboardOptions()
  var onInputStart: () -> Void = {}
  let onKeyFeedback: (TypingSoundKeyRole) -> Void
  let onKey: (Character) -> Void
  let onBackspace: () -> Void

  @State private var isShifted = false
  @State private var guidePulse = false
  @State private var pressedActionCounts: [HangulKeyboardAction: Int] = [:]

  private let topRow = [
    KeyDefinition(base: "ㅂ", shifted: "ㅃ", roman: "q"),
    KeyDefinition(base: "ㅈ", shifted: "ㅉ", roman: "w"),
    KeyDefinition(base: "ㄷ", shifted: "ㄸ", roman: "e"),
    KeyDefinition(base: "ㄱ", shifted: "ㄲ", roman: "r"),
    KeyDefinition(base: "ㅅ", shifted: "ㅆ", roman: "t"),
    KeyDefinition(base: "ㅛ", roman: "y"),
    KeyDefinition(base: "ㅕ", roman: "u"),
    KeyDefinition(base: "ㅑ", roman: "i"),
    KeyDefinition(base: "ㅐ", shifted: "ㅒ", roman: "o"),
    KeyDefinition(base: "ㅔ", shifted: "ㅖ", roman: "p"),
  ]

  private let homeRow = [
    KeyDefinition(base: "ㅁ", roman: "a"),
    KeyDefinition(base: "ㄴ", roman: "s"),
    KeyDefinition(base: "ㅇ", roman: "d"),
    KeyDefinition(base: "ㄹ", roman: "f"),
    KeyDefinition(base: "ㅎ", roman: "g"),
    KeyDefinition(base: "ㅗ", roman: "h"),
    KeyDefinition(base: "ㅓ", roman: "j"),
    KeyDefinition(base: "ㅏ", roman: "k"),
    KeyDefinition(base: "ㅣ", roman: "l"),
  ]

  private let bottomRow = [
    KeyDefinition(base: "ㅋ", roman: "z"),
    KeyDefinition(base: "ㅌ", roman: "x"),
    KeyDefinition(base: "ㅊ", roman: "c"),
    KeyDefinition(base: "ㅍ", roman: "v"),
    KeyDefinition(base: "ㅠ", roman: "b"),
    KeyDefinition(base: "ㅜ", roman: "n"),
    KeyDefinition(base: "ㅡ", roman: "m"),
  ]

  var body: some View {
    VStack(spacing: 7) {
      keyRow(topRow)
      keyRow(homeRow)
        .padding(.horizontal, 12)
      HStack(spacing: 5) {
        actionKey(
          action: .shift,
          systemImage: isShifted ? "shift.fill" : "shift",
          accessibilityLabel: Text("keyboard.shift"),
          highlighted: shiftShouldBeHighlighted
        )

        ForEach(bottomRow) { definition in
          characterKey(definition)
        }

        actionKey(
          action: .backspace,
          systemImage: "delete.left",
          accessibilityLabel: Text("keyboard.backspace"),
          highlighted: false
        )
      }

      HStack {
        Spacer(minLength: 56)
        Keycap(
          title: "",
          systemImage: nil,
          romanHint: nil,
          accessibilityLabel: Text("keyboard.space"),
          accessibilityIdentifier: "keyboard.space",
          highlighted: options.showsKeyGuide && nextExpectedKey == " ",
          guidePulse: guidePulse,
          keyAction: .character(" "),
          isPressed: isPressed(.character(" ")),
          onActivate: activate
        )
        .frame(maxWidth: adaptiveMetrics.isExpanded ? adaptiveMetrics.availableWidth * 0.4 : 180)
        .overlay {
          Capsule()
            .fill(AppPalette.mutedInk.opacity(0.24))
            .frame(width: 56, height: 4)
            .allowsHitTesting(false)
        }
        Spacer(minLength: 56)
      }
    }
    .coordinateSpace(name: KeyboardCoordinateSpace.name)
    .overlayPreferenceValue(KeyboardKeyBoundsPreferenceKey.self) { anchors in
      GeometryReader { proxy in
        RolloverKeyboardTouchSurface(
          targets: touchTargets(from: anchors, proxy: proxy),
          onTouchBegan: beginPress,
          onTouchEnded: endPress
        )
      }
    }
    .padding(.horizontal, 8)
    .padding(.top, 10)
    .padding(.bottom, 8)
    .background(.ultraThinMaterial)
    .accessibilityElement(children: .contain)
    .accessibilityLabel(Text("keyboard.accessibility_label"))
    .onAppear {
      guard options.showsKeyGuide else { return }
      withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) {
        guidePulse = true
      }
    }
  }

  private var shiftShouldBeHighlighted: Bool {
    guard options.showsKeyGuide else { return false }
    guard let expected = nextExpectedKey else { return false }
    return !isShifted && KeyDefinition.shiftedCharacters.contains(expected)
  }

  private func keyRow(_ row: [KeyDefinition]) -> some View {
    HStack(spacing: 5) {
      ForEach(row) { definition in
        characterKey(definition)
      }
    }
  }

  private func characterKey(_ definition: KeyDefinition) -> some View {
    let character = definition.output(isShifted: isShifted)
    let needsShift = KeyDefinition.shiftedCharacters.contains(nextExpectedKey ?? " ")
    let highlighted =
      options.showsKeyGuide && nextExpectedKey == character && (!needsShift || isShifted)

    return Keycap(
      title: String(character),
      systemImage: nil,
      romanHint: options.showsRomanHints ? definition.roman : nil,
      accessibilityLabel: Text(verbatim: String(character)),
      accessibilityIdentifier: "keyboard.key.\(character)",
      highlighted: highlighted,
      guidePulse: guidePulse,
      keyAction: .character(character),
      isPressed: isPressed(.character(character)),
      onActivate: activate
    )
  }

  private func actionKey(
    action: HangulKeyboardAction,
    systemImage: String,
    accessibilityLabel: Text,
    highlighted: Bool
  ) -> some View {
    Keycap(
      title: nil,
      systemImage: systemImage,
      romanHint: nil,
      accessibilityLabel: accessibilityLabel,
      accessibilityIdentifier: systemImage.contains("shift")
        ? "keyboard.shift" : "keyboard.backspace",
      highlighted: highlighted,
      guidePulse: guidePulse,
      keyAction: action,
      isPressed: isPressed(action),
      onActivate: activate
    )
  }

  private func touchTargets(
    from anchors: [HangulKeyboardAction: Anchor<CGRect>],
    proxy: GeometryProxy
  ) -> [KeyboardTouchTarget] {
    anchors.map { action, anchor in
      KeyboardTouchTarget(action: action, frame: proxy[anchor])
    }
    .sorted {
      if abs($0.frame.minY - $1.frame.minY) > 0.5 {
        return $0.frame.minY < $1.frame.minY
      }
      return $0.frame.minX < $1.frame.minX
    }
  }

  private func isPressed(_ action: HangulKeyboardAction) -> Bool {
    pressedActionCounts[action, default: 0] > 0
  }

  private func beginPress(_ action: HangulKeyboardAction) {
    pressedActionCounts[action, default: 0] += 1
    activate(action)
  }

  private func endPress(_ action: HangulKeyboardAction) {
    let remainingCount = pressedActionCounts[action, default: 0] - 1
    if remainingCount > 0 {
      pressedActionCounts[action] = remainingCount
    } else {
      pressedActionCounts.removeValue(forKey: action)
    }
  }

  private func activate(_ action: HangulKeyboardAction) {
    onInputStart()

    let soundRole: TypingSoundKeyRole
    switch action {
    case .character(let character):
      commit(character)
      soundRole = .character
    case .shift:
      isShifted.toggle()
      soundRole = .shift
    case .backspace:
      onBackspace()
      soundRole = .backspace
    case .korean10Key:
      return
    }

    KeyHaptics.fire(if: options.hapticsEnabled)
    onKeyFeedback(soundRole)
  }

  private func commit(_ character: Character) {
    onKey(character)
    if isShifted { isShifted = false }
  }
}

struct Korean10KeyKeyboardView: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  let nextExpectedKey: Korean10KeyKey?
  var options = HangulKeyboardOptions()
  var onInputStart: () -> Void = {}
  let onKeyFeedback: (TypingSoundKeyRole) -> Void
  let onKey: (Korean10KeyKey) -> Void
  let onBackspace: () -> Void

  @State private var guidePulse = false
  @State private var pressedActionCounts: [HangulKeyboardAction: Int] = [:]

  private let rows: [[Korean10KeyKey]] = [
    [.vertical, .dot, .horizontal],
    [.giyeok, .nieun, .digeut],
    [.bieup, .siot, .jieut],
  ]

  var body: some View {
    VStack(spacing: 7) {
      ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
        HStack(spacing: 7) {
          ForEach(row, id: \.self) { key in
            tenKey(key)
          }
        }
      }

      HStack(spacing: 7) {
        tenKey(.next)
        tenKey(.ieung)
        actionKey(
          action: .backspace,
          systemImage: "delete.left",
          accessibilityLabel: Text("keyboard.backspace")
        )
      }

      tenKey(.space, systemImage: "space")
        .padding(.horizontal, 56)
    }
    .coordinateSpace(name: KeyboardCoordinateSpace.name)
    .overlayPreferenceValue(KeyboardKeyBoundsPreferenceKey.self) { anchors in
      GeometryReader { proxy in
        RolloverKeyboardTouchSurface(
          targets: touchTargets(from: anchors, proxy: proxy),
          onTouchBegan: beginPress,
          onTouchEnded: endPress
        )
      }
    }
    .frame(maxWidth: adaptiveMetrics.isExpanded ? 600 : .infinity)
    .frame(maxWidth: .infinity)
    .padding(.horizontal, 12)
    .padding(.top, 10)
    .padding(.bottom, 8)
    .background(.ultraThinMaterial)
    .accessibilityElement(children: .contain)
    .accessibilityLabel(Text("keyboard.10key.accessibility_label"))
    .onAppear {
      guard options.showsKeyGuide else { return }
      withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) {
        guidePulse = true
      }
    }
  }

  private func tenKey(
    _ key: Korean10KeyKey,
    systemImage: String? = nil
  ) -> some View {
    let action = HangulKeyboardAction.korean10Key(key)
    return Keycap(
      title: systemImage == nil ? key.displayText : nil,
      systemImage: systemImage,
      romanHint: nil,
      accessibilityLabel: Text(key.accessibilityKey),
      accessibilityIdentifier: "keyboard.10key.\(key.rawValue)",
      highlighted: options.showsKeyGuide && nextExpectedKey == key,
      guidePulse: guidePulse,
      keyAction: action,
      isPressed: isPressed(action),
      onActivate: activate,
      height: 52
    )
  }

  private func actionKey(
    action: HangulKeyboardAction,
    systemImage: String,
    accessibilityLabel: Text
  ) -> some View {
    Keycap(
      title: nil,
      systemImage: systemImage,
      romanHint: nil,
      accessibilityLabel: accessibilityLabel,
      accessibilityIdentifier: "keyboard.backspace",
      highlighted: false,
      guidePulse: guidePulse,
      keyAction: action,
      isPressed: isPressed(action),
      onActivate: activate,
      height: 52
    )
  }

  private func touchTargets(
    from anchors: [HangulKeyboardAction: Anchor<CGRect>],
    proxy: GeometryProxy
  ) -> [KeyboardTouchTarget] {
    anchors.map { action, anchor in
      KeyboardTouchTarget(action: action, frame: proxy[anchor])
    }
    .sorted {
      if abs($0.frame.minY - $1.frame.minY) > 0.5 {
        return $0.frame.minY < $1.frame.minY
      }
      return $0.frame.minX < $1.frame.minX
    }
  }

  private func isPressed(_ action: HangulKeyboardAction) -> Bool {
    pressedActionCounts[action, default: 0] > 0
  }

  private func beginPress(_ action: HangulKeyboardAction) {
    pressedActionCounts[action, default: 0] += 1
    activate(action)
  }

  private func endPress(_ action: HangulKeyboardAction) {
    let remainingCount = pressedActionCounts[action, default: 0] - 1
    if remainingCount > 0 {
      pressedActionCounts[action] = remainingCount
    } else {
      pressedActionCounts.removeValue(forKey: action)
    }
  }

  private func activate(_ action: HangulKeyboardAction) {
    onInputStart()

    let soundRole: TypingSoundKeyRole
    switch action {
    case .korean10Key(let key):
      onKey(key)
      soundRole = .character
    case .backspace:
      onBackspace()
      soundRole = .backspace
    case .character, .shift:
      return
    }

    KeyHaptics.fire(if: options.hapticsEnabled)
    onKeyFeedback(soundRole)
  }
}

private struct KeyDefinition: Identifiable {
  static let shiftedCharacters = Set(Array("ㅃㅉㄸㄲㅆㅒㅖ"))

  let base: Character
  let shifted: Character?
  let roman: String

  var id: Character { base }

  init(base: Character, shifted: Character? = nil, roman: String) {
    self.base = base
    self.shifted = shifted
    self.roman = roman
  }

  func output(isShifted: Bool) -> Character {
    isShifted ? shifted ?? base : base
  }
}

private struct Keycap: View {
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @Environment(\.hancoFontScale) private var fontScale

  let title: String?
  let systemImage: String?
  let romanHint: String?
  let accessibilityLabel: Text
  let accessibilityIdentifier: String
  let highlighted: Bool
  let guidePulse: Bool
  let keyAction: HangulKeyboardAction
  let isPressed: Bool
  let onActivate: (HangulKeyboardAction) -> Void
  var height: CGFloat = 50

  var body: some View {
    ZStack {
      RoundedRectangle(cornerRadius: 11, style: .continuous)
        .fill(highlighted ? AppPalette.accentSoft : AppPalette.key)
        .shadow(
          color: highlighted
            ? AppPalette.accent.opacity(guidePulse ? 0.72 : 0.32) : AppPalette.keyShadow,
          radius: highlighted && guidePulse ? 12 : 3,
          y: 2
        )

      if let title {
        VStack(spacing: 0) {
          Text(verbatim: title)
            .font(.system(size: 21 * fontScale * (adaptiveMetrics.isExpanded ? 1.15 : 1), weight: .semibold, design: .rounded))
            .foregroundStyle(AppPalette.ink)
          if let romanHint {
            Text(verbatim: romanHint)
              .font(.system(size: 9 * fontScale * (adaptiveMetrics.isExpanded ? 1.15 : 1), weight: .medium, design: .rounded))
              .foregroundStyle(AppPalette.mutedInk)
          }
        }
      } else if let systemImage {
        Image(systemName: systemImage)
          .font(.system(size: 18 * fontScale, weight: .semibold))
          .foregroundStyle(highlighted ? AppPalette.accent : AppPalette.ink)
      }
    }
    .frame(maxWidth: .infinity)
    .frame(height: height * adaptiveMetrics.keyboardScale)
    .scaleEffect(isPressed ? 0.95 : 1)
    .animation(.easeOut(duration: 0.06), value: isPressed)
    .anchorPreference(key: KeyboardKeyBoundsPreferenceKey.self, value: .bounds) {
      [keyAction: $0]
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(accessibilityLabel)
    .accessibilityValue(Text(verbatim: romanHint ?? ""))
    .accessibilityIdentifier(accessibilityIdentifier)
    .accessibilityAddTraits(.isButton)
    .accessibilityAction {
      onActivate(keyAction)
    }
  }
}

enum HangulKeyboardAction: Hashable {
  case character(Character)
  case shift
  case backspace
  case korean10Key(Korean10KeyKey)
}

struct KeyboardTouchTarget: Equatable {
  let action: HangulKeyboardAction
  let frame: CGRect
}

enum KeyboardTouchTargetResolver {
  static let defaultTouchOutset = CGSize(width: 4, height: 5)

  static func action(
    at point: CGPoint,
    targets: [KeyboardTouchTarget],
    touchOutset: CGSize = defaultTouchOutset
  ) -> HangulKeyboardAction? {
    var bestTarget: KeyboardTouchTarget?
    var bestEdgeDistance = CGFloat.greatestFiniteMagnitude
    var bestCenterDistance = CGFloat.greatestFiniteMagnitude

    for target in targets {
      let hitFrame = target.frame.insetBy(
        dx: -max(0, touchOutset.width),
        dy: -max(0, touchOutset.height)
      )
      guard hitFrame.contains(point) else { continue }

      let edgeDistance = squaredDistance(from: point, to: target.frame)
      let centerDistance = squaredDistance(
        from: point,
        to: CGPoint(x: target.frame.midX, y: target.frame.midY)
      )
      if edgeDistance < bestEdgeDistance
        || (edgeDistance == bestEdgeDistance && centerDistance < bestCenterDistance)
      {
        bestTarget = target
        bestEdgeDistance = edgeDistance
        bestCenterDistance = centerDistance
      }
    }

    return bestTarget?.action
  }

  private static func squaredDistance(from point: CGPoint, to rect: CGRect) -> CGFloat {
    let dx = max(rect.minX - point.x, 0, point.x - rect.maxX)
    let dy = max(rect.minY - point.y, 0, point.y - rect.maxY)
    return dx * dx + dy * dy
  }

  private static func squaredDistance(from lhs: CGPoint, to rhs: CGPoint) -> CGFloat {
    let dx = lhs.x - rhs.x
    let dy = lhs.y - rhs.y
    return dx * dx + dy * dy
  }
}

private enum KeyboardCoordinateSpace {
  static let name = "hangul-keyboard"
}

private struct KeyboardKeyBoundsPreferenceKey: PreferenceKey {
  static var defaultValue: [HangulKeyboardAction: Anchor<CGRect>] = [:]

  static func reduce(
    value: inout [HangulKeyboardAction: Anchor<CGRect>],
    nextValue: () -> [HangulKeyboardAction: Anchor<CGRect>]
  ) {
    value.merge(nextValue()) { _, latest in latest }
  }
}

private struct RolloverKeyboardTouchSurface: UIViewRepresentable {
  let targets: [KeyboardTouchTarget]
  let onTouchBegan: (HangulKeyboardAction) -> Void
  let onTouchEnded: (HangulKeyboardAction) -> Void

  func makeUIView(context: Context) -> RolloverKeyboardTouchView {
    let view = RolloverKeyboardTouchView(frame: .zero)
    view.backgroundColor = .clear
    view.isAccessibilityElement = false
    return view
  }

  func updateUIView(_ uiView: RolloverKeyboardTouchView, context: Context) {
    uiView.targets = targets
    uiView.onTouchBegan = onTouchBegan
    uiView.onTouchEnded = onTouchEnded
  }
}

final class RolloverKeyboardTouchView: UIView {
  var targets: [KeyboardTouchTarget] = []
  var onTouchBegan: ((HangulKeyboardAction) -> Void)?
  var onTouchEnded: ((HangulKeyboardAction) -> Void)?

  private var trackedActions: [ObjectIdentifier: HangulKeyboardAction] = [:]

  override init(frame: CGRect) {
    super.init(frame: frame)
    isMultipleTouchEnabled = true
    isExclusiveTouch = false
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    isMultipleTouchEnabled = true
    isExclusiveTouch = false
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesBegan(touches, with: event)
    for touch in touches {
      let identifier = ObjectIdentifier(touch)
      guard trackedActions[identifier] == nil,
        let action = KeyboardTouchTargetResolver.action(
          at: touch.location(in: self),
          targets: targets
        )
      else { continue }
      trackedActions[identifier] = action
      onTouchBegan?(action)
    }
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesEnded(touches, with: event)
    finish(touches)
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    super.touchesCancelled(touches, with: event)
    finish(touches)
  }

  private func finish(_ touches: Set<UITouch>) {
    for touch in touches {
      guard let action = trackedActions.removeValue(forKey: ObjectIdentifier(touch)) else {
        continue
      }
      onTouchEnded?(action)
    }
  }
}

@MainActor
private enum KeyHaptics {
  private static let generator = UIImpactFeedbackGenerator(style: .light)

  static func fire(if enabled: Bool) {
    guard enabled else { return }
    generator.impactOccurred(intensity: 0.7)
    generator.prepare()
  }
}
