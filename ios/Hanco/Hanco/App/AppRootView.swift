import SwiftUI
import UIKit

enum AppTourTarget: String, Hashable {
  case homePrimary
  case discoverSearch
  case practiceCurriculum
  case gameModes
  case myPageProfile
  case settings
}

struct AppTourTargetPreferenceKey: PreferenceKey {
  static var defaultValue: [AppTourTarget: CGRect] = [:]

  static func reduce(
    value: inout [AppTourTarget: CGRect],
    nextValue: () -> [AppTourTarget: CGRect]
  ) {
    value.merge(nextValue()) { _, latest in latest }
  }
}

extension View {
  func appTourTarget(_ target: AppTourTarget, enabled: Bool = true) -> some View {
    background {
      GeometryReader { proxy in
        Color.clear.preference(
          key: AppTourTargetPreferenceKey.self,
          value: enabled ? [target: proxy.frame(in: .global)] : [:]
        )
      }
    }
  }
}

private enum AppTab: Hashable {
  case home
  case discover
  case practice
  case game
  case myPage

  var analyticsValue: String {
    switch self {
    case .home: "home"
    case .discover: "discover"
    case .practice: "practice"
    case .game: "game"
    case .myPage: "my_page"
    }
  }
}

private enum AppTourStep: Int, CaseIterable {
  case home
  case discover
  case practice
  case game
  case myPage
  case settings

  var target: AppTourTarget {
    switch self {
    case .home: .homePrimary
    case .discover: .discoverSearch
    case .practice: .practiceCurriculum
    case .game: .gameModes
    case .myPage: .myPageProfile
    case .settings: .settings
    }
  }

  var tab: AppTab {
    switch self {
    case .home: .home
    case .discover: .discover
    case .practice: .practice
    case .game: .game
    case .myPage, .settings: .myPage
    }
  }

  var titleKey: String { "app_tour.\(target.rawValue).title" }
  var detailKey: String { "app_tour.\(target.rawValue).detail" }

  var systemImage: String {
    switch self {
    case .home: "bolt.fill"
    case .discover: "sparkle.magnifyingglass"
    case .practice: "keyboard.fill"
    case .game: "gamecontroller.fill"
    case .myPage: "person.crop.circle.fill"
    case .settings: "gearshape.fill"
    }
  }
}

struct AppRootView: View {
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var deckLibrary = DeckLibrary()
  @StateObject private var deckMakerPurchaseStore: DeckMakerPurchaseStore
  @StateObject private var piyoDeckDocumentCoordinator = PiyoDeckDocumentCoordinator()
  @StateObject private var discoverViewModel = DiscoverViewModel()
  @StateObject private var gameProgress = GameProgressLibrary()
  @StateObject private var reviewDeck = ReviewDeckLibrary()
  @StateObject private var curriculumProgress = CurriculumProgressLibrary()
  @StateObject private var retention = RetentionLibrary()
  @StateObject private var dailyReminder = DailyReminderLibrary()
  @StateObject private var onboarding = OnboardingLibrary()
  @StateObject private var mascotCompanion = MascotCompanionLibrary()
  @StateObject private var gameCenter = GameCenterService()
  #if DEBUG
    @StateObject private var audioDebugProbe = HancoAudioDebugProbe.shared
  #endif
  @AppStorage(SoundPreferenceKeys.effectsEnabled) private var soundEffectsEnabled = true
  @AppStorage(SettingsPreferenceKeys.fontScale) private var fontScale =
    HancoFontScale.standard.rawValue
  @AppStorage(SettingsPreferenceKeys.theme) private var theme = HancoTheme.light.rawValue
  @AppStorage(SettingsPreferenceKeys.language) private var language =
    AppLanguage.preferred.rawValue
  @AppStorage(OnboardingStore.appTourCompletedKey) private var appTourCompleted = false
  @AppStorage(SettingsPreferenceKeys.anonymousAnalyticsEnabled)
  private var anonymousAnalyticsEnabled = false
  @AppStorage(SettingsPreferenceKeys.crashDiagnosticsEnabled)
  private var crashDiagnosticsEnabled = false
  @AppStorage(SettingsPreferenceKeys.privacyNoticeVersion)
  private var privacyNoticeVersion = 0
  @State private var selectedTab: AppTab = .home
  @State private var showsSettings = false
  @State private var showsPrivacyConsent = false
  @State private var hatchGateIsActive: Bool?
  @State private var appTourStep: AppTourStep?

  init() {
    #if DEBUG
      if let rawAccess = ProcessInfo.processInfo.environment["UITEST_DECK_MAKER_ACCESS"] {
        _deckMakerPurchaseStore = StateObject(
          wrappedValue: .debugPreview(unlocked: rawAccess == "1")
        )
      } else {
        _deckMakerPurchaseStore = StateObject(wrappedValue: DeckMakerPurchaseStore())
      }
    #else
      _deckMakerPurchaseStore = StateObject(wrappedValue: DeckMakerPurchaseStore())
    #endif
  }

  var body: some View {
    GeometryReader { proxy in
      let adaptiveMetrics = HancoAdaptiveMetrics(availableWidth: proxy.size.width)
      Group {
        if onboarding.shouldPresent {
          OnboardingView()
        } else if shouldPresentHatchGate {
          CurriculumMapView(
            catalog: nil,
            isHatchOnboarding: true,
            onHatchCompleted: finishHatchOnboarding
          )
        } else {
          mainTabs
        }
      }
      .environment(\.hancoAdaptiveMetrics, adaptiveMetrics)
    }
    .environmentObject(deckLibrary)
    .environmentObject(deckMakerPurchaseStore)
    .environmentObject(piyoDeckDocumentCoordinator)
    .environmentObject(discoverViewModel)
    .environmentObject(gameProgress)
    .environmentObject(reviewDeck)
    .environmentObject(curriculumProgress)
    .environmentObject(retention)
    .environmentObject(dailyReminder)
    .environmentObject(onboarding)
    .environmentObject(mascotCompanion)
    .environmentObject(gameCenter)
    .environment(
      \.locale,
      AppLanguage.resolved(from: language).locale
    )
    .environment(
      \.hancoFontScale,
      HancoFontScale.resolved(from: fontScale).multiplier
    )
    .preferredColorScheme(HancoTheme.resolved(from: theme).colorScheme)
    .environment(\.openRootSettings) {
      showsSettings = true
    }
    .id(language)
    .onChange(of: language) { _ in
      dailyReminder.refreshLocalizedContent()
    }
    .sheet(isPresented: $showsSettings) {
      SettingsView(showsCloseButton: true)
        .environmentObject(deckLibrary)
        .environmentObject(curriculumProgress)
        .environmentObject(retention)
        .environmentObject(dailyReminder)
        .environmentObject(mascotCompanion)
        .environmentObject(gameCenter)
        .environment(
          \.locale,
          AppLanguage.resolved(from: language).locale
        )
        .environment(
          \.hancoFontScale,
          HancoFontScale.resolved(from: fontScale).multiplier
        )
        .preferredColorScheme(HancoTheme.resolved(from: theme).colorScheme)
    }
    .sheet(isPresented: $showsPrivacyConsent) {
      PrivacyConsentView(
        initialAnalyticsEnabled: anonymousAnalyticsEnabled,
        initialDiagnosticsEnabled: crashDiagnosticsEnabled,
        onSave: applyPrivacyChoices,
        onContinueWithoutSharing: {
          applyPrivacyChoices(analytics: false, diagnostics: false)
        }
      )
      .environment(
        \.locale,
        AppLanguage.resolved(from: language).locale
      )
      .environment(
        \.hancoFontScale,
        HancoFontScale.resolved(from: fontScale).multiplier
      )
      .preferredColorScheme(HancoTheme.resolved(from: theme).colorScheme)
    }
    .task { discoverViewModel.loadIfNeeded() }
    .task { await deckMakerPurchaseStore.prepare() }
    .task { await piyoDeckDocumentCoordinator.resumePendingIfNeeded() }
    .onOpenURL { url in
      guard url.pathExtension.lowercased() == "typedeck" else { return }
      Task { await piyoDeckDocumentCoordinator.receive(url) }
    }
    .task(id: shouldStartAppTour) {
      guard shouldStartAppTour else { return }
      // Let the first preference pass publish the spotlight frame without leaving
      // the main tabs interactive long enough to enter a session underneath the tour.
      await Task.yield()
      guard !Task.isCancelled, shouldStartAppTour else { return }
      selectedTab = .home
      withAnimation(.easeOut(duration: 0.22)) {
        appTourStep = .home
      }
    }
    .task(id: shouldPresentPrivacyConsent) {
      guard shouldPresentPrivacyConsent else { return }
      await Task.yield()
      guard !Task.isCancelled, shouldPresentPrivacyConsent else { return }
      showsPrivacyConsent = true
    }
    .onAppear {
      HancoSoundEngine.shared.setEnabled(soundEffectsEnabled)
      gameCenter.updateSceneActivity(scenePhase == .active)
      cacheGameCenterLocalState()
      if hatchGateIsActive == nil {
        hatchGateIsActive = !HatchOnboardingPolicy.isComplete(
          completedStageIDs: curriculumProgress.completedStageIDs
        )
      }
    }
    .onChange(of: soundEffectsEnabled) { enabled in
      HancoSoundEngine.shared.setEnabled(enabled)
    }
    .onChange(of: gameProgress.records) { _ in
      cacheGameCenterLocalState()
    }
    .onChange(of: gameCenterGrowthFingerprint) { _ in
      cacheGameCenterLocalState()
    }
    .onChange(of: gameCenter.hasSubmittedScore) { hasSubmittedScore in
      if hasSubmittedScore {
        mascotCompanion.registerGameCenterScoreSubmission()
      }
    }
    .onChange(of: scenePhase) { phase in
      gameCenter.updateSceneActivity(phase == .active)
      if phase == .background {
        HancoSoundEngine.shared.suspendForInactivity()
      }
      if phase != .active {
        flushPendingProgress()
      }
    }
    .onReceive(NotificationCenter.default.publisher(for: UIApplication.willTerminateNotification)) {
      _ in
      flushPendingProgress()
    }
    .overlay(alignment: .topLeading) {
      debugAudioProbe
    }
    .hancoUITestDynamicTypeOverride()
  }

  private func flushPendingProgress() {
    curriculumProgress.flush()
    reviewDeck.flush()
    gameProgress.flush()
  }

  @ViewBuilder
  private var debugAudioProbe: some View {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UITEST_AUDIO_PROBE"] == "1" {
        VStack(spacing: 0) {
          Text(verbatim: String(audioDebugProbe.pronunciationPlaybackStartCount))
            .font(.system(size: 1))
            .opacity(0.01)
            .accessibilityIdentifier("debug.pronunciation.start_count")
          Text(verbatim: String(audioDebugProbe.pronunciationPlaybackExplicitBundledCount))
            .font(.system(size: 1))
            .opacity(0.01)
            .accessibilityIdentifier("debug.pronunciation.explicit_count")
          Text(verbatim: String(audioDebugProbe.pronunciationPlaybackCanonicalBundledCount))
            .font(.system(size: 1))
            .opacity(0.01)
            .accessibilityIdentifier("debug.pronunciation.canonical_count")
          Text(verbatim: String(audioDebugProbe.pronunciationPlaybackSynthesizedFallbackCount))
            .font(.system(size: 1))
            .opacity(0.01)
            .accessibilityIdentifier("debug.pronunciation.synthesized_count")
        }
      }
    #endif
  }

  private var shouldPresentHatchGate: Bool {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UITEST_SKIP_HATCH_ONBOARDING"] == "1" {
        return false
      }
    #endif
    return hatchGateIsActive
      ?? !HatchOnboardingPolicy.isComplete(
        completedStageIDs: curriculumProgress.completedStageIDs
      )
  }

  private func finishHatchOnboarding() {
    guard
      HatchOnboardingPolicy.isComplete(
        completedStageIDs: curriculumProgress.completedStageIDs
      )
    else { return }
    selectedTab = .home
    hatchGateIsActive = false
  }

  private var shouldStartAppTour: Bool {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UITEST_FORCE_APP_TOUR"] == "1" {
        return !onboarding.shouldPresent
          && !shouldPresentHatchGate
          && !appTourCompleted
          && appTourStep == nil
      }
    #endif
    return !onboarding.shouldPresent
      && !shouldPresentHatchGate
      && HatchOnboardingPolicy.isComplete(
        completedStageIDs: curriculumProgress.completedStageIDs
      )
      && !appTourCompleted
      && appTourStep == nil
  }

  private var shouldPresentPrivacyConsent: Bool {
    PrivacyNoticePolicy.shouldPresent(
      reviewedVersion: privacyNoticeVersion,
      onboardingCompleted: !onboarding.shouldPresent && !shouldPresentHatchGate,
      appTourCompleted: appTourCompleted,
      hasBlockingPresentation: showsSettings || appTourStep != nil || selectedTab != .home
    )
  }

  private func applyPrivacyChoices(analytics: Bool, diagnostics: Bool) {
    anonymousAnalyticsEnabled = analytics
    crashDiagnosticsEnabled = diagnostics
    privacyNoticeVersion = PrivacyNoticePolicy.currentVersion
    TelemetryService.shared.updateConsent(
      productAnalytics: analytics,
      crashDiagnostics: diagnostics
    )
    if analytics {
      TelemetryService.shared.capture(
        .featureViewed,
        properties: [.feature: selectedTab.analyticsValue]
      )
    }
    showsPrivacyConsent = false
  }

  private func advanceAppTour() {
    guard let currentStep = appTourStep else { return }
    guard let nextStep = AppTourStep(rawValue: currentStep.rawValue + 1) else {
      completeAppTour()
      return
    }
    selectedTab = nextStep.tab
    withAnimation(.easeInOut(duration: 0.2)) {
      appTourStep = nextStep
    }
  }

  private func completeAppTour() {
    appTourCompleted = true
    TelemetryService.shared.capture(
      .onboardingStepCompleted,
      properties: [.onboardingStep: "app_tour"]
    )
    selectedTab = .home
    withAnimation(.easeOut(duration: 0.2)) {
      appTourStep = nil
    }
  }

  private var gameCenterGrowthSnapshot: GameCenterGrowthSnapshot {
    GameCenterGrowthSnapshot(
      completedChapterCount: curriculumProgress.completedChapterCount,
      typedJamoCount: mascotCompanion.typedJamoCount,
      longestStreak: retention.streak(asOf: JSTDay(date: RetentionClock.now())).longest
    )
  }

  private var gameCenterGrowthFingerprint: String {
    let snapshot = gameCenterGrowthSnapshot
    return "\(snapshot.completedChapterCount):\(snapshot.typedJamoCount):\(snapshot.longestStreak)"
  }

  private func cacheGameCenterLocalState() {
    gameCenter.updateLocalState(
      records: gameProgress.records,
      growth: gameCenterGrowthSnapshot
    )
  }

  private var mainTabs: some View {
    TabView(selection: $selectedTab) {
      HomeView(catalog: discoverViewModel.catalog)
        .tabItem {
          Label("tab.home", systemImage: "house.fill")
        }
        .tag(AppTab.home)
        .accessibilityIdentifier("tab.home")

      DiscoverView(viewModel: discoverViewModel)
        .tabItem {
          Label("tab.discover", systemImage: "sparkle.magnifyingglass")
        }
        .tag(AppTab.discover)
        .accessibilityIdentifier("tab.discover")

      CurriculumMapView(catalog: discoverViewModel.catalog)
        .tabItem {
          Label("tab.practice", systemImage: "book.closed.fill")
        }
        .tag(AppTab.practice)
        .accessibilityIdentifier("tab.practice")

      GameDeckSelectionView {
        selectedTab = .discover
      }
      .tabItem {
        Label("tab.game", systemImage: "gamecontroller.fill")
      }
      .tag(AppTab.game)
      .accessibilityIdentifier("tab.game")

      MyPageView(
        catalog: discoverViewModel.catalog,
        isActive: selectedTab == .myPage && !showsSettings && appTourStep == nil
      ) {
        selectedTab = .discover
      }
      .badge(
        piyoDeckDocumentCoordinator.candidate != nil
          || piyoDeckDocumentCoordinator.notice != nil ? 1 : 0
      )
      .tabItem {
        Label("tab.my_page", systemImage: "person.crop.circle.fill")
      }
      .tag(AppTab.myPage)
      .accessibilityIdentifier("tab.my_page")
    }
    .tint(AppPalette.accent)
    .onAppear {
      TelemetryService.shared.capture(
        .featureViewed,
        properties: [.feature: selectedTab.analyticsValue]
      )
      TelemetryService.shared.setCrashContext(feature: selectedTab.analyticsValue)
    }
    .onChange(of: selectedTab) { tab in
      TelemetryService.shared.capture(.featureViewed, properties: [.feature: tab.analyticsValue])
      TelemetryService.shared.setCrashContext(feature: tab.analyticsValue)
    }
    .accessibilityHidden(appTourStep != nil)
    .overlayPreferenceValue(AppTourTargetPreferenceKey.self) { targets in
      GeometryReader { proxy in
        if let step = appTourStep {
          AppTourOverlay(
            step: step,
            highlightedFrame: targets[step.target] ?? step.fallbackFrame(in: proxy.size),
            onAdvance: advanceAppTour,
            onSkip: completeAppTour
          )
        }
      }
    }
  }
}

extension View {
  @ViewBuilder
  func hancoUITestDynamicTypeOverride() -> some View {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UITEST_DYNAMIC_TYPE_ACCESSIBILITY"] == "1" {
        dynamicTypeSize(.accessibility5)
      } else {
        self
      }
    #else
      self
    #endif
  }
}

extension AppTourStep {
  fileprivate func fallbackFrame(in size: CGSize) -> CGRect {
    let contentWidth = max(0, size.width - 36)
    switch self {
    case .home:
      return CGRect(x: 18, y: size.height * 0.34, width: contentWidth, height: 126)
    case .discover:
      return CGRect(x: 16, y: 54, width: max(0, size.width - 32), height: 64)
    case .practice:
      return CGRect(x: 18, y: 94, width: contentWidth, height: 190)
    case .game:
      return CGRect(x: 18, y: size.height * 0.42, width: contentWidth, height: 190)
    case .myPage:
      return CGRect(x: 18, y: 88, width: contentWidth, height: 166)
    case .settings:
      return CGRect(x: max(0, size.width - 62), y: 36, width: 52, height: 52)
    }
  }
}

private struct AppTourOverlay: View {
  let step: AppTourStep
  let highlightedFrame: CGRect
  let onAdvance: () -> Void
  let onSkip: () -> Void

  @State private var isHandlingAdvance = false

  var body: some View {
    GeometryReader { geometry in
      let spotlight = clampedSpotlight(in: geometry.size)
      let calloutBelow = spotlight.midY < geometry.size.height * 0.44

      ZStack {
        Color.black.opacity(0.78)
          .mask {
            Rectangle()
              .overlay {
                RoundedRectangle(cornerRadius: step.spotlightCornerRadius, style: .continuous)
                  .frame(width: spotlight.width, height: spotlight.height)
                  .position(x: spotlight.midX, y: spotlight.midY)
                  .blendMode(.destinationOut)
              }
              .compositingGroup()
          }
          .ignoresSafeArea()

        RoundedRectangle(cornerRadius: step.spotlightCornerRadius, style: .continuous)
          .stroke(AppPalette.accentSoft, lineWidth: 3)
          .shadow(color: AppPalette.accent.opacity(0.9), radius: 14)
          .frame(width: spotlight.width, height: spotlight.height)
          .position(x: spotlight.midX, y: spotlight.midY)
          .allowsHitTesting(false)

        Image(systemName: calloutBelow ? "arrow.up" : "arrow.down")
          .font(.system(size: 30, weight: .black))
          .foregroundStyle(.white)
          .position(
            x: min(max(spotlight.midX, 38), geometry.size.width - 38),
            y: calloutBelow ? spotlight.maxY + 30 : spotlight.minY - 30
          )
          .accessibilityHidden(true)

        callout
          .frame(width: min(max(0, geometry.size.width - 48), 430))
          .position(
            x: geometry.size.width / 2,
            y: calloutY(
              in: geometry.size,
              spotlight: spotlight,
              appearsBelow: calloutBelow
            )
          )
      }
      .contentShape(Rectangle())
      .onTapGesture(perform: advanceOnce)
      .accessibilityElement(children: .contain)
      .accessibilityIdentifier("app_tour.step.\(step.target.rawValue)")
    }
    .ignoresSafeArea()
  }

  private var callout: some View {
    VStack(spacing: 14) {
      HStack(alignment: .top, spacing: 14) {
        Image(systemName: step.systemImage)
          .font(.title2.weight(.black))
          .foregroundStyle(.white)
          .frame(width: 48, height: 48)
          .background(AppPalette.accent, in: RoundedRectangle(cornerRadius: 15))

        VStack(alignment: .leading, spacing: 7) {
          Text(LocalizedStringKey(step.titleKey))
            .font(.system(.title3, design: .rounded, weight: .heavy))
            .foregroundStyle(.white)
          Text(LocalizedStringKey(step.detailKey))
            .font(.subheadline)
            .foregroundStyle(Color.white.opacity(0.82))
            .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
      }

      Divider()
        .overlay(Color.white.opacity(0.15))

      HStack(spacing: 10) {
        Button(action: onSkip) {
          Label("app_tour.skip", systemImage: "xmark")
            .font(.caption.weight(.bold))
            .foregroundStyle(Color.white.opacity(0.82))
            .frame(minHeight: 44)
        }
        .accessibilityIdentifier("app_tour.skip")

        Spacer(minLength: 4)

        HStack(spacing: 6) {
          ForEach(AppTourStep.allCases, id: \.rawValue) { item in
            Capsule()
              .fill(item == step ? Color.white : Color.white.opacity(0.34))
              .frame(width: item == step ? 20 : 6, height: 6)
          }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
          Text(
            verbatim: AppLocalization.format("app_tour.progress_format",
              step.rawValue + 1,
              AppTourStep.allCases.count
            )
          )
        )

        Spacer(minLength: 4)

        Button(action: advanceOnce) {
          Image(systemName: step == .settings ? "house.fill" : "arrow.right")
            .font(.headline.weight(.black))
            .foregroundStyle(.white)
            .frame(width: 44, height: 44)
            .background(AppPalette.accent, in: Circle())
        }
        .accessibilityLabel(
          Text(
            LocalizedStringKey(
              step == .settings ? "app_tour.finish" : "app_tour.continue"
            )
          )
        )
        .accessibilityIdentifier("app_tour.next")
      }
    }
    .padding(18)
    .background(Color(red: 0.10, green: 0.09, blue: 0.16).opacity(0.97))
    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    .overlay {
      RoundedRectangle(cornerRadius: 24, style: .continuous)
        .strokeBorder(Color.white.opacity(0.16), lineWidth: 1)
    }
    .shadow(color: .black.opacity(0.36), radius: 18, y: 10)
  }

  private func advanceOnce() {
    guard !isHandlingAdvance else { return }
    isHandlingAdvance = true
    onAdvance()
    Task { @MainActor in
      await Task.yield()
      isHandlingAdvance = false
    }
  }

  private func clampedSpotlight(in size: CGSize) -> CGRect {
    let padded = highlightedFrame.insetBy(dx: -9, dy: -9)
    let minX = min(max(padded.minX, 8), max(8, size.width - 8))
    let minY = min(max(padded.minY, 8), max(8, size.height - 8))
    let maxX = min(max(padded.maxX, minX), max(minX, size.width - 8))
    let maxY = min(max(padded.maxY, minY), max(minY, size.height - 8))
    return CGRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
  }

  private func calloutY(
    in size: CGSize,
    spotlight: CGRect,
    appearsBelow: Bool
  ) -> CGFloat {
    let estimatedHalfHeight: CGFloat = 86
    if appearsBelow {
      return min(
        max(spotlight.maxY + estimatedHalfHeight + 48, 150),
        size.height - 210
      )
    }
    return max(
      min(spotlight.minY - estimatedHalfHeight - 48, size.height - 210),
      140
    )
  }
}

extension AppTourStep {
  fileprivate var spotlightCornerRadius: CGFloat {
    switch self {
    case .discover: 18
    case .settings: 26
    default: 28
    }
  }
}

private struct RootSettingsToolbarModifier: ViewModifier {
  @Environment(\.openRootSettings) private var openSettings

  func body(content: Content) -> some View {
    content.toolbar {
      ToolbarItem(placement: .topBarTrailing) {
        Button(action: openSettings) {
          ZStack {
            Circle()
              .fill(AppPalette.card.opacity(0.9))
              .frame(width: 32, height: 32)
            Image(systemName: "gearshape.fill")
              .font(.subheadline.weight(.bold))
              .foregroundStyle(AppPalette.accent)
          }
          .frame(width: 44, height: 44)
          .contentShape(Rectangle())
        }
        .accessibilityLabel(Text("settings.navigation_title"))
        .accessibilityIdentifier("root.settings")
        .appTourTarget(.settings)
      }
    }
  }
}

private struct OpenRootSettingsKey: EnvironmentKey {
  static let defaultValue: () -> Void = {}
}

extension EnvironmentValues {
  var openRootSettings: () -> Void {
    get { self[OpenRootSettingsKey.self] }
    set { self[OpenRootSettingsKey.self] = newValue }
  }
}

extension View {
  func rootSettingsToolbar() -> some View {
    modifier(RootSettingsToolbarModifier())
  }
}
