import Foundation
import UIKit

#if canImport(FirebaseCore)
  import FirebaseCore
#endif
#if canImport(FirebaseCrashlytics)
  import FirebaseCrashlytics
#endif
#if canImport(PostHog)
  import PostHog
#endif

enum AnalyticsTransportPrivacy {
  private static let sdkOperationalProperties: Set<String> = [
    "$geoip_disable",
    "$process_person_profile",
    "$lib",
    "$lib_version",
  ]

  static func sanitizedProperties(
    eventName: String,
    properties: [String: Any]
  ) -> [String: Any]? {
    guard
      let event = AnalyticsEvent(rawValue: eventName),
      let eventProperties = AnalyticsContract.allowedProperties[event]
    else { return nil }

    let allowed = Set(eventProperties.map(\.rawValue)).union(sdkOperationalProperties)
    return properties.filter { allowed.contains($0.key) }
  }

  static func platform(
    idiom: UIUserInterfaceIdiom,
    deviceModel: String,
    simulatorModelIdentifier: String?
  ) -> String {
    let isIPad = idiom == .pad
      || deviceModel.lowercased().hasPrefix("ipad")
      || simulatorModelIdentifier?.lowercased().hasPrefix("ipad") == true
    return isIPad ? "ipados" : "ios"
  }

  static var currentPlatform: String {
    platform(
      idiom: UIDevice.current.userInterfaceIdiom,
      deviceModel: UIDevice.current.model,
      simulatorModelIdentifier: ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"]
    )
  }
}

struct AnalyticsAppOpenTracker: Equatable {
  private(set) var analyticsEnabled: Bool
  private(set) var capturedInCurrentForeground = false
  private(set) var currentEntryPoint = "cold_start"

  init(analyticsEnabled: Bool) {
    self.analyticsEnabled = analyticsEnabled
  }

  mutating func configure() -> String? {
    captureEntryPointIfNeeded()
  }

  mutating func updateConsent(_ enabled: Bool) -> String? {
    analyticsEnabled = enabled
    return captureEntryPointIfNeeded()
  }

  mutating func sceneDidBecomeActive() -> String? {
    captureEntryPointIfNeeded()
  }

  mutating func sceneDidEnterBackground() {
    capturedInCurrentForeground = false
    currentEntryPoint = "foreground"
  }

  private mutating func captureEntryPointIfNeeded() -> String? {
    guard analyticsEnabled, !capturedInCurrentForeground else { return nil }
    capturedInCurrentForeground = true
    return currentEntryPoint
  }
}

@MainActor
final class TelemetryService {
  static let shared = TelemetryService()

  private(set) var isProductAnalyticsConfigured = false
  private(set) var isCrashDiagnosticsConfigured = false
  private var appOpenTracker = AnalyticsAppOpenTracker(analyticsEnabled: false)

  private init() {}

  func configure() {
    #if !DEBUG
      appOpenTracker = AnalyticsAppOpenTracker(
        analyticsEnabled: UserDefaults.standard.bool(
          forKey: SettingsPreferenceKeys.anonymousAnalyticsEnabled
        )
      )
      if appOpenTracker.analyticsEnabled {
        configurePostHogIfAvailable()
      }
      configureCrashlyticsIfAvailable()
      captureAppOpened(entryPoint: appOpenTracker.configure())
    #endif
  }

  func updateConsent(productAnalytics: Bool, crashDiagnostics: Bool) {
    #if !DEBUG
      let appOpenEntryPoint = appOpenTracker.updateConsent(productAnalytics)
      if productAnalytics, !isProductAnalyticsConfigured {
        configurePostHogIfAvailable()
      }
      if isProductAnalyticsConfigured {
        #if canImport(PostHog)
          productAnalytics ? PostHogSDK.shared.optIn() : PostHogSDK.shared.optOut()
        #endif
      }
      captureAppOpened(entryPoint: appOpenEntryPoint)
      if isCrashDiagnosticsConfigured {
        #if canImport(FirebaseCrashlytics)
          let crashlytics = Crashlytics.crashlytics()
          crashlytics.setCrashlyticsCollectionEnabled(crashDiagnostics)
          if !crashDiagnostics { crashlytics.deleteUnsentReports() }
        #endif
      }
    #endif
  }

  func sceneDidBecomeActive() {
    #if !DEBUG
      captureAppOpened(entryPoint: appOpenTracker.sceneDidBecomeActive())
    #endif
  }

  func sceneDidEnterBackground() {
    #if !DEBUG
      appOpenTracker.sceneDidEnterBackground()
    #endif
  }

  func capture(
    _ event: AnalyticsEvent,
    properties eventProperties: [AnalyticsProperty: Any] = [:]
  ) {
    #if !DEBUG
      guard isProductAnalyticsConfigured, appOpenTracker.analyticsEnabled else { return }
      var properties = commonProperties()
      eventProperties.forEach { properties[$0] = $1 }
      guard AnalyticsContract.accepts(event: event, properties: properties) else {
        assertionFailure("Rejected analytics properties for \(event.rawValue)")
        return
      }
      #if canImport(PostHog)
        var sdkProperties = Dictionary(
          uniqueKeysWithValues: properties.map { ($0.key.rawValue, $0.value) }
        )
        sdkProperties["$geoip_disable"] = true
        PostHogSDK.shared.capture(
          event.rawValue,
          properties: sdkProperties
        )
      #endif
    #endif
  }

  func setCrashContext(
    feature: String,
    sessionKind: String? = nil,
    inputMode: String? = nil,
    gameMode: String? = nil
  ) {
    #if !DEBUG && canImport(FirebaseCrashlytics)
      guard isCrashDiagnosticsConfigured else { return }
      let crashlytics = Crashlytics.crashlytics()
      crashlytics.setCustomValue(feature, forKey: AnalyticsProperty.feature.rawValue)
      sessionKind.map { crashlytics.setCustomValue($0, forKey: AnalyticsProperty.sessionKind.rawValue) }
      inputMode.map { crashlytics.setCustomValue($0, forKey: AnalyticsProperty.inputMode.rawValue) }
      gameMode.map { crashlytics.setCustomValue($0, forKey: AnalyticsProperty.gameMode.rawValue) }
    #endif
  }

  func durationBucket(_ duration: TimeInterval) -> String {
    switch duration {
    case ..<60: "under_1m"
    case ..<180: "1_to_3m"
    case ..<420: "3_to_7m"
    default: "over_7m"
    }
  }

  func itemCountBucket(_ count: Int) -> String {
    switch count {
    case ...3: "1_to_3"
    case ...10: "4_to_10"
    case ...30: "11_to_30"
    default: "over_30"
    }
  }

  func scoreBucket(_ score: Int) -> String {
    switch score {
    case ...0: "0"
    case ..<1_000: "1_to_999"
    case ..<5_000: "1000_to_4999"
    default: "5000_plus"
    }
  }

  func deckCategory(tags: [String], level: Int) -> String {
    let normalizedTags = Set(tags.map { $0.lowercased() })
    if normalizedTags.contains(where: { $0.contains("topik") || $0.contains("검정") }) {
      return "topik"
    }
    if normalizedTags.contains(where: {
      $0.contains("여행") || $0.contains("travel") || $0.contains("旅行")
    }) {
      return "travel"
    }
    if normalizedTags.contains(where: {
      $0.contains("일상") || $0.contains("daily") || $0.contains("日常")
    }) {
      return "daily"
    }
    if normalizedTags.contains(where: {
      $0.contains("k-pop") || $0.contains("kドラマ") || $0.contains("今どき")
        || $0.contains("sns") || $0.contains("트렌드") || $0.contains("trend")
    }) {
      return "trend"
    }
    if level <= 1 || normalizedTags.contains(where: {
      $0.contains("입문") || $0.contains("beginner") || $0.contains("入門")
    }) {
      return "beginner"
    }
    return "unknown"
  }

  private func commonProperties() -> [AnalyticsProperty: Any] {
    let language = AppLanguage.current.rawValue
    return [
      .schemaVersion: AnalyticsContract.schemaVersion,
      .platform: AnalyticsTransportPrivacy.currentPlatform,
      .appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        ?? "unknown",
      .buildNumber: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String
        ?? "unknown",
      .locale: ["ja", "en", "ko"].contains(language) ? language : "other",
    ]
  }

  private func configurePostHogIfAvailable() {
    #if canImport(PostHog)
      guard appOpenTracker.analyticsEnabled, !isProductAnalyticsConfigured else { return }
      guard
        let token = Bundle.main.object(forInfoDictionaryKey: "PiyokeyPostHogProjectToken") as? String,
        !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
        !token.contains("$(")
      else { return }
      let host = Bundle.main.object(forInfoDictionaryKey: "PiyokeyPostHogHost") as? String
        ?? "https://eu.i.posthog.com"
      let config = PostHogConfig(projectToken: token, host: host)
      config.optOut = !UserDefaults.standard.bool(
        forKey: SettingsPreferenceKeys.anonymousAnalyticsEnabled
      )
      config.personProfiles = .never
      config.setDefaultPersonProperties = false
      config.captureApplicationLifecycleEvents = false
      config.captureScreenViews = false
      config.enableSwizzling = false
      config.captureElementInteractions = false
      config.capturePushNotificationSubscriptions = false
      config.capturePushNotificationOpened = false
      config.sessionReplay = false
      config.errorTrackingConfig.autoCapture = false
      config.surveys = false
      config.preloadFeatureFlags = false
      config.sendFeatureFlagEvent = false
      config.setBeforeSend { event in
        guard
          let properties = AnalyticsTransportPrivacy.sanitizedProperties(
            eventName: event.event,
            properties: event.properties
          )
        else { return nil }
        event.properties = properties
        return event
      }
      PostHogSDK.shared.setup(config)
      isProductAnalyticsConfigured = true
    #endif
  }

  private func captureAppOpened(entryPoint: String?) {
    guard let entryPoint else { return }
    capture(.appOpened, properties: [.entryPoint: entryPoint])
  }

  private func configureCrashlyticsIfAvailable() {
    #if canImport(FirebaseCore) && canImport(FirebaseCrashlytics)
      guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
        return
      }
      if FirebaseApp.app() == nil { FirebaseApp.configure() }
      Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(
        UserDefaults.standard.bool(forKey: SettingsPreferenceKeys.crashDiagnosticsEnabled)
      )
      isCrashDiagnosticsConfigured = true
    #endif
  }
}
