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

@MainActor
final class TelemetryService {
  static let shared = TelemetryService()

  private(set) var isProductAnalyticsConfigured = false
  private(set) var isCrashDiagnosticsConfigured = false

  private init() {}

  func configure() {
    #if !DEBUG
      configurePostHogIfAvailable()
      configureCrashlyticsIfAvailable()
      capture(.appOpened, properties: [.entryPoint: "cold_start"])
    #endif
  }

  func updateConsent(productAnalytics: Bool, crashDiagnostics: Bool) {
    #if !DEBUG
      if isProductAnalyticsConfigured {
        #if canImport(PostHog)
          productAnalytics ? PostHogSDK.shared.optIn() : PostHogSDK.shared.optOut()
        #endif
      }
      if isCrashDiagnosticsConfigured {
        #if canImport(FirebaseCrashlytics)
          let crashlytics = Crashlytics.crashlytics()
          crashlytics.setCrashlyticsCollectionEnabled(crashDiagnostics)
          if !crashDiagnostics { crashlytics.deleteUnsentReports() }
        #endif
      }
    #endif
  }

  func capture(
    _ event: AnalyticsEvent,
    properties eventProperties: [AnalyticsProperty: Any] = [:]
  ) {
    #if !DEBUG
      guard isProductAnalyticsConfigured else { return }
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

  private func commonProperties() -> [AnalyticsProperty: Any] {
    let language = AppLanguage.current.rawValue
    return [
      .schemaVersion: AnalyticsContract.schemaVersion,
      .platform: UIDevice.current.userInterfaceIdiom == .pad ? "ipados" : "ios",
      .appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        ?? "unknown",
      .buildNumber: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String
        ?? "unknown",
      .locale: ["ja", "en", "ko"].contains(language) ? language : "other",
    ]
  }

  private func configurePostHogIfAvailable() {
    #if canImport(PostHog)
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
      PostHogSDK.shared.setup(config)
      isProductAnalyticsConfigured = true
    #endif
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
