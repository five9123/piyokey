import SwiftUI

enum DeckMakerLegalLinks {
  static let termsOfUse = URL(
    string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"
  )!
  static let privacyPolicy = AppReleaseLinks.privacyPolicy
}

private enum DeckMakerPaywallFeature: String, CaseIterable {
  case create
  case edit
  case copyOfficial
  case lifetime

  var systemImage: String {
    switch self {
    case .create: "rectangle.stack.badge.plus"
    case .edit: "pencil.and.list.clipboard"
    case .copyOfficial: "doc.on.doc.fill"
    case .lifetime: "infinity"
    }
  }

  var titleLocalizationKey: String {
    "deck_maker.paywall.feature.\(rawValue).title"
  }

  var detailLocalizationKey: String {
    "deck_maker.paywall.feature.\(rawValue).detail"
  }
}

struct DeckMakerPaywallView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.hancoFontScale) private var fontScale
  @Environment(\.hancoAdaptiveMetrics) private var adaptiveMetrics
  @ObservedObject private var purchaseStore: DeckMakerPurchaseStore
  @ScaledMetric(relativeTo: .largeTitle) private var headerSymbolSize: CGFloat = 42
  @ScaledMetric(relativeTo: .largeTitle) private var headerFrameSize: CGFloat = 86
  @ScaledMetric(relativeTo: .title) private var titleSize: CGFloat = 28
  @ScaledMetric(relativeTo: .body) private var subtitleSize: CGFloat = 15
  @ScaledMetric(relativeTo: .body) private var featureTitleSize: CGFloat = 15
  @ScaledMetric(relativeTo: .caption) private var featureDetailSize: CGFloat = 12.5
  @ScaledMetric(relativeTo: .caption) private var noteSize: CGFloat = 13
  @ScaledMetric(relativeTo: .body) private var purchaseTitleSize: CGFloat = 17
  @ScaledMetric(relativeTo: .subheadline) private var secondaryActionSize: CGFloat = 14
  @ScaledMetric(relativeTo: .caption) private var legalSize: CGFloat = 12
  @ScaledMetric(relativeTo: .body) private var featureIconSize: CGFloat = 18
  @ScaledMetric(relativeTo: .body) private var featureIconFrame: CGFloat = 36

  private let onAccessGranted: () -> Void

  init(
    purchaseStore: DeckMakerPurchaseStore,
    onAccessGranted: @escaping () -> Void = {}
  ) {
    _purchaseStore = ObservedObject(wrappedValue: purchaseStore)
    self.onAccessGranted = onAccessGranted
  }

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: 22) {
          header
          featureCard
          freeImportNote
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 20)
        .hancoCenteredContent(maxWidth: adaptiveMetrics.formContentMaxWidth)
      }
      .background(background)
      .safeAreaInset(edge: .bottom, spacing: 0) {
        purchaseControls
      }
      .navigationTitle(Text("deck_maker.paywall.navigation_title"))
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("common.close") { dismiss() }
            .accessibilityIdentifier("deck_maker.paywall.close")
        }
      }
    }
    .task {
      await purchaseStore.prepare()
    }
    .alert(item: noticeBinding) { notice in
      Alert(
        title: Text(AppLocalization.string(notice.titleLocalizationKey)),
        message: Text(AppLocalization.string(notice.messageLocalizationKey)),
        dismissButton: .default(Text("deck_maker.alert.ok")) {
          purchaseStore.dismissNotice()
        }
      )
    }
    .accessibilityIdentifier("deck_maker.paywall.screen")
  }

  private var header: some View {
    VStack(spacing: 12) {
      Image(systemName: "rectangle.stack.badge.plus")
        .font(.system(size: headerSymbolSize * fontScale, weight: .bold))
        .foregroundStyle(AppPalette.accent)
        .frame(width: headerFrameSize, height: headerFrameSize)
        .background(
          AppPalette.accentSoft.opacity(0.55),
          in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
        .accessibilityHidden(true)

      Text("deck_maker.paywall.title")
        .font(.system(size: titleSize * fontScale, weight: .heavy, design: .rounded))
        .foregroundStyle(AppPalette.ink)
        .multilineTextAlignment(.center)

      Text("deck_maker.paywall.subtitle")
        .font(.system(size: subtitleSize * fontScale, weight: .medium))
        .foregroundStyle(AppPalette.mutedInk)
        .multilineTextAlignment(.center)
        .fixedSize(horizontal: false, vertical: true)
    }
  }

  private var featureCard: some View {
    VStack(spacing: 0) {
      ForEach(Array(DeckMakerPaywallFeature.allCases.enumerated()), id: \.element) {
        index, feature in
        if index > 0 {
          Divider().opacity(0.45)
        }
        featureRow(feature)
      }
    }
    .padding(.horizontal, 18)
    .background(AppPalette.card, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
  }

  private func featureRow(_ feature: DeckMakerPaywallFeature) -> some View {
    HStack(alignment: .top, spacing: 14) {
      Image(systemName: feature.systemImage)
        .font(.system(size: featureIconSize, weight: .semibold))
        .foregroundStyle(AppPalette.accent)
        .frame(width: featureIconFrame, height: featureIconFrame)
        .background(
          AppPalette.accentSoft.opacity(0.48),
          in: RoundedRectangle(cornerRadius: 11, style: .continuous)
        )
        .accessibilityHidden(true)

      VStack(alignment: .leading, spacing: 3) {
        Text(AppLocalization.string(feature.titleLocalizationKey))
          .font(.system(size: featureTitleSize * fontScale, weight: .bold))
          .foregroundStyle(AppPalette.ink)
        Text(AppLocalization.string(feature.detailLocalizationKey))
          .font(.system(size: featureDetailSize * fontScale, weight: .medium))
          .foregroundStyle(AppPalette.mutedInk)
          .fixedSize(horizontal: false, vertical: true)
      }
      Spacer(minLength: 0)
    }
    .padding(.vertical, 14)
  }

  private var freeImportNote: some View {
    Label {
      Text("deck_maker.paywall.free_import_note")
        .font(.system(size: noteSize * fontScale, weight: .semibold))
        .foregroundStyle(AppPalette.mutedInk)
        .fixedSize(horizontal: false, vertical: true)
    } icon: {
      Image(systemName: "checkmark.seal.fill")
        .foregroundStyle(AppPalette.successText)
    }
    .padding(14)
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(
      AppPalette.card.opacity(0.76),
      in: RoundedRectangle(cornerRadius: 18, style: .continuous)
    )
  }

  private var purchaseControls: some View {
    VStack(spacing: 10) {
      Button {
        Task { await primaryAction() }
      } label: {
        HStack(spacing: 9) {
          if purchaseStore.activity == .purchasing || purchaseStore.activity == .loading {
            ProgressView()
              .tint(AppPalette.onAccent)
          } else if purchaseStore.hasAccess {
            Image(systemName: "checkmark.circle.fill")
          }
          Text(primaryButtonTitle)
            .font(.system(size: purchaseTitleSize * fontScale, weight: .heavy))
            .lineLimit(2)
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(AppPalette.onAccent)
        .frame(maxWidth: .infinity, minHeight: 54)
        .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 18))
      }
      .buttonStyle(.plain)
      .disabled(purchaseStore.isBusy)
      .opacity(purchaseStore.isBusy && purchaseStore.activity == .restoring ? 0.55 : 1)
      .accessibilityIdentifier("deck_maker.paywall.purchase")

      Button {
        Task {
          if await purchaseStore.restore() {
            purchaseStore.dismissNotice()
            onAccessGranted()
            dismiss()
          }
        }
      } label: {
        HStack(spacing: 7) {
          if purchaseStore.activity == .restoring {
            ProgressView()
          }
          Text("deck_maker.paywall.restore")
            .font(.system(size: secondaryActionSize * fontScale, weight: .semibold))
        }
        .foregroundStyle(AppPalette.ink)
      }
      .buttonStyle(.plain)
      .disabled(purchaseStore.isBusy)
      .accessibilityIdentifier("deck_maker.paywall.restore")

      ViewThatFits(in: .horizontal) {
        HStack(spacing: 18) {
          legalLinks
        }
        VStack(spacing: 8) {
          legalLinks
        }
      }
      .font(.system(size: legalSize * fontScale, weight: .medium))
      .foregroundStyle(AppPalette.mutedInk)
    }
    .padding(.horizontal, 20)
    .padding(.top, 14)
    .padding(.bottom, 8)
    .hancoCenteredContent(maxWidth: adaptiveMetrics.formContentMaxWidth)
    .background(.ultraThinMaterial)
  }

  @ViewBuilder
  private var legalLinks: some View {
    Link("deck_maker.paywall.terms", destination: DeckMakerLegalLinks.termsOfUse)
    Link("deck_maker.paywall.privacy", destination: DeckMakerLegalLinks.privacyPolicy)
  }

  private var primaryButtonTitle: String {
    if purchaseStore.hasAccess {
      return AppLocalization.string("deck_maker.paywall.already_owned")
    }
    guard let displayPrice = purchaseStore.displayPrice else {
      if purchaseStore.activity == .loading {
        return AppLocalization.string("deck_maker.paywall.loading")
      }
      return AppLocalization.string("deck_maker.paywall.retry")
    }
    return String(
      format: AppLocalization.string("deck_maker.paywall.purchase_format"),
      locale: AppLocalization.locale,
      displayPrice
    )
  }

  private var noticeBinding: Binding<DeckMakerPurchaseNotice?> {
    Binding(
      get: { purchaseStore.notice },
      set: { notice in
        if notice == nil { purchaseStore.dismissNotice() }
      }
    )
  }

  private var background: some View {
    LinearGradient(
      colors: [AppPalette.backgroundTop, AppPalette.backgroundBottom],
      startPoint: .topLeading,
      endPoint: .bottomTrailing
    )
    .ignoresSafeArea()
  }

  private func primaryAction() async {
    if purchaseStore.hasAccess {
      onAccessGranted()
      dismiss()
      return
    }
    guard purchaseStore.product != nil else {
      await purchaseStore.prepare(forceReload: true)
      return
    }
    if await purchaseStore.purchase() {
      onAccessGranted()
      dismiss()
    }
  }
}
