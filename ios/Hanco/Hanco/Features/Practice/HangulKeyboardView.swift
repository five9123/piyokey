import SwiftUI
import UIKit

enum KeyboardPreferenceKeys {
  static let showsKeyGuide = "keyboard.shows_key_guide"
  static let showsRomanHints = "keyboard.shows_roman_hints"
  static let hapticsEnabled = "keyboard.haptics_enabled"
  static let inputModeDefault = "keyboard.input_mode_default"

  static let all = [showsKeyGuide, showsRomanHints, hapticsEnabled, inputModeDefault]
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

struct HangulKeyboardView: View {
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
        .frame(maxWidth: 180)
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
    }

    KeyHaptics.fire(if: options.hapticsEnabled)
    onKeyFeedback(soundRole)
  }

  private func commit(_ character: Character) {
    onKey(character)
    if isShifted { isShifted = false }
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
            .font(.system(size: 21 * fontScale, weight: .semibold, design: .rounded))
            .foregroundStyle(AppPalette.ink)
          if let romanHint {
            Text(verbatim: romanHint)
              .font(.system(size: 9 * fontScale, weight: .medium, design: .rounded))
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
    .frame(height: 50)
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
