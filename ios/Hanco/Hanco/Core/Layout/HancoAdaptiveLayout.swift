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

  init(availableWidth: CGFloat) {
    self.availableWidth = max(0, availableWidth)
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
  let sessionLaneMaxWidth: CGFloat = 920
  let keyboardMaxWidth: CGFloat = 820

  var hubColumnCount: Int {
    switch widthClass {
    case .compact: 2
    case .medium: 3
    case .wide: 4
    }
  }

  var usesTwoColumnDashboard: Bool { widthClass == .wide }
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
