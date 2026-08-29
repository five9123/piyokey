import SwiftUI

enum HancoAdaptiveWidthClass: String, Equatable {
  case compact
  case medium
  case wide
}

struct HancoAdaptiveMetrics: Equatable {
  static let compactUpperBound: CGFloat = 600
  static let wideLowerBound: CGFloat = 900

  let availableWidth: CGFloat
  let availableHeight: CGFloat

  init(availableWidth: CGFloat, availableHeight: CGFloat = 0) {
    self.availableWidth = max(0, availableWidth)
    self.availableHeight = max(0, availableHeight)
  }

  var widthClass: HancoAdaptiveWidthClass {
    if availableWidth < Self.compactUpperBound { return .compact }
    if availableWidth < Self.wideLowerBound { return .medium }
    return .wide
  }

  var horizontalPadding: CGFloat {
    switch widthClass {
    case .compact: 18
    case .medium: 24
    case .wide: 32
    }
  }

  let formContentMaxWidth: CGFloat = 680
  let readableContentMaxWidth: CGFloat = 720
  let resultContentMaxWidth: CGFloat = 760
  let hubContentMaxWidth: CGFloat = 1_120
  var isExpanded: Bool { widthClass != .compact }
  var isTall: Bool { isExpanded && availableHeight > availableWidth }
  var typographyScale: CGFloat { isExpanded ? 1.2 : 1 }
  var learningScale: CGFloat {
    guard isExpanded else { return 1 }
    if isTall { return 1.5 }
    // Grow within the available height while keeping question → typing → keys stacked.
    return min(1.6, max(1.1, 1.1 + (availableHeight - 700) / 450))
  }
  var keyboardScale: CGFloat {
    guard isExpanded else { return 1 }
    // Leave room for the question in short landscape / Stage Manager windows.
    if availableHeight < 600 { return 1.15 }
    return isTall ? 1.6 : min(1.5, max(1.3, 1.3 + (availableHeight - 700) / 1_000))
  }
  var sessionLaneMaxWidth: CGFloat { isExpanded ? max(920, availableWidth - 48) : 920 }
  var keyboardMaxWidth: CGFloat { max(availableWidth, 320) }

  var hubColumnCount: Int {
    switch widthClass {
    case .compact: 2
    case .medium: 3
    case .wide: 4
    }
  }

  var usesTwoColumnDashboard: Bool { widthClass == .wide && !isTall }
}

/// Measure the current window (or presented sheet), never the physical screen.
private struct HancoAdaptiveLayoutModifier: ViewModifier {
  @Environment(\.hancoFontScale) private var fontScale

  func body(content: Content) -> some View {
    GeometryReader { proxy in
      let metrics = HancoAdaptiveMetrics(
        availableWidth: proxy.size.width, availableHeight: proxy.size.height
      )
      content
        .environment(\.hancoAdaptiveMetrics, metrics)
        .environment(\.hancoFontScale, fontScale * metrics.typographyScale)
        .dynamicTypeSize((metrics.isExpanded ? DynamicTypeSize.xxLarge : .xSmall)...)
    }
  }
}

private struct HancoAdaptiveMetricsKey: EnvironmentKey {
  static let defaultValue = HancoAdaptiveMetrics(availableWidth: 390)
}

extension EnvironmentValues {
  var hancoAdaptiveMetrics: HancoAdaptiveMetrics {
    get { self[HancoAdaptiveMetricsKey.self] }
    set { self[HancoAdaptiveMetricsKey.self] = newValue }
  }
}

extension View {
  func hancoAdaptiveLayout() -> some View {
    modifier(HancoAdaptiveLayoutModifier())
  }

  func hancoCenteredContent(maxWidth: CGFloat, alignment: Alignment = .center) -> some View {
    frame(maxWidth: maxWidth, alignment: alignment)
      .frame(maxWidth: .infinity, alignment: alignment)
  }

  @ViewBuilder
  func hancoAdaptiveDebugValue(_ metrics: HancoAdaptiveMetrics) -> some View {
    #if DEBUG
      accessibilityValue(Text(verbatim: metrics.widthClass.rawValue))
    #else
      self
    #endif
  }
}
