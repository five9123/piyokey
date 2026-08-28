import Foundation
import XCTest

@testable import Hanco

final class AppSettingsTests: XCTestCase {
  private let defaults = UserDefaults.standard
  private var originalLanguage: Any?

  override func setUp() {
    super.setUp()
    originalLanguage = defaults.object(forKey: SettingsPreferenceKeys.language)
  }

  override func tearDown() {
    if let originalLanguage {
      defaults.set(originalLanguage, forKey: SettingsPreferenceKeys.language)
    } else {
      defaults.removeObject(forKey: SettingsPreferenceKeys.language)
    }
    super.tearDown()
  }

  func testUnknownStoredValuesResolveToSafeDefaults() {
    XCTAssertEqual(HancoFontScale.resolved(from: "unexpected"), .standard)
    XCTAssertEqual(HancoTheme.resolved(from: "unexpected"), .light)
    XCTAssertEqual(AppLanguage.resolved(from: "unexpected"), .english)
    XCTAssertEqual(PracticeDisplayPreset.resolved(from: "unexpected"), .learning)
    XCTAssertEqual(PracticePromptOrder.resolved(from: "unexpected"), .targetMeaningReading)
  }

  func testPreferredLanguageUsesSupportedDeviceLanguageAndEnglishFallback() {
    XCTAssertEqual(AppLanguage.preferred(from: ["ja-JP", "en-US"]), .japanese)
    XCTAssertEqual(AppLanguage.preferred(from: ["ko-KR", "en-US"]), .korean)
    XCTAssertEqual(AppLanguage.preferred(from: ["en-GB"]), .english)
    XCTAssertEqual(
      AppLanguage.preferred(from: ["es-MX", "fr-FR", "zh-Hant", "ar-SA"]),
      .english
    )
  }

  func testPromptOrdersCoverAllSixUniquePermutations() {
    let permutations = PracticePromptOrder.allCases.map(\.fields)

    XCTAssertEqual(permutations.count, 6)
    XCTAssertEqual(Set(permutations.map { $0.map(\.rawValue).joined(separator: ",") }).count, 6)
    XCTAssertTrue(permutations.allSatisfy { Set($0) == Set(PracticePromptField.allCases) })
  }

  func testFontScaleHasThreeIncreasingLevels() {
    XCTAssertEqual(HancoFontScale.allCases.count, 3)
    XCTAssertLessThan(HancoFontScale.small.multiplier, HancoFontScale.standard.multiplier)
    XCTAssertLessThan(HancoFontScale.standard.multiplier, HancoFontScale.large.multiplier)
  }

  func testAllLanguageResourcesHaveIdenticalKeysAndFormatArguments() throws {
    let resources = try Dictionary(
      uniqueKeysWithValues: AppLanguage.allCases.map { language in
        (language, try localizedStrings(for: language))
      }
    )
    let reference = try XCTUnwrap(resources[.japanese])

    XCTAssertFalse(reference.isEmpty)
    for language in AppLanguage.allCases {
      let candidate = try XCTUnwrap(resources[language])
      XCTAssertEqual(
        Set(candidate.keys),
        Set(reference.keys),
        "\(language.rawValue) must contain the same localization keys as Japanese"
      )
      for key in reference.keys.sorted() {
        XCTAssertEqual(
          formatArguments(in: candidate[key, default: ""]),
          formatArguments(in: reference[key, default: ""]),
          "Format arguments differ for \(language.rawValue):\(key)"
        )
      }
    }
  }

  func testR11SafetyStringsAreTranslatedAndFormatCleanly() throws {
    let prefixes = [
      "deck_editor.draft.",
      "my_decks.delete.",
      "piyodeck.import.compare.",
      "piyodeck.import.action.",
    ]
    let japanese = try localizedStrings(for: .japanese)
    let keys = japanese.keys.filter { key in
      prefixes.contains { key.hasPrefix($0) }
    }

    XCTAssertFalse(keys.isEmpty)
    for language in AppLanguage.allCases {
      let strings = try localizedStrings(for: language)
      for key in keys {
        let value = try XCTUnwrap(strings[key])
        XCTAssertFalse(
          value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
          "Empty R1.1 translation for \(language.rawValue):\(key)"
        )
        XCTAssertNotEqual(value, key, "Localization key is exposed for \(language.rawValue):\(key)")
      }

      let deleteTitle = try XCTUnwrap(strings["my_decks.delete.confirm.title_format"])
      XCTAssertFalse(String(format: deleteTitle, "TOPIK").contains("%@"))

      let comparison = try XCTUnwrap(strings["piyodeck.import.compare.accessibility_value"])
      XCTAssertFalse(String(format: comparison, "Current", "Imported").contains("%@"))
    }
  }

  func testMascotEncouragementsUseLocalizedQuotationMarks() throws {
    for language in AppLanguage.allCases {
      let strings = try localizedStrings(for: language)
      let messages = try (0..<MascotDailyEncouragement.messageCount).map { index in
        try XCTUnwrap(strings["mascot.daily_encouragement.\(index)"])
      }

      XCTAssertEqual(Set(messages).count, MascotDailyEncouragement.messageCount)
      let quotes = language == .english ? ("“", "”") : ("「", "」")
      XCTAssertTrue(messages.allSatisfy { $0.hasPrefix(quotes.0) && $0.hasSuffix(quotes.1) })
      XCTAssertTrue(messages.allSatisfy { $0.count <= 42 })
    }
  }

  func testSelectedLanguageControlsDirectStringLookupAndLocale() {
    let expectations: [(AppLanguage, String, String, String, String)] = [
      (.japanese, "設定", "ピヨキー", "ピヨちゃん", "ja_JP"),
      (.english, "Settings", "typee", "Piyo", "en_US"),
      (.korean, "설정", "typee", "피요", "ko_KR"),
    ]

    for (language, title, brand, mascotName, localeIdentifier) in expectations {
      defaults.set(language.rawValue, forKey: SettingsPreferenceKeys.language)
      XCTAssertEqual(AppLocalization.string("settings.navigation_title"), title)
      XCTAssertEqual(AppLocalization.string("app.name"), brand)
      XCTAssertEqual(AppLocalization.string("mascot.default_name"), mascotName)
      XCTAssertEqual(AppLocalization.locale.identifier, localeIdentifier)
    }
  }

  func testLocalizedInfoPlistNamesFollowBrandPolicy() throws {
    let expectedBrands: [(AppLanguage, String)] = [
      (.japanese, "ピヨキー"),
      (.english, "typee"),
      (.korean, "typee"),
    ]

    for (language, brand) in expectedBrands {
      let bundle = AppLocalization.localizedBundle(for: language)
      let path = try XCTUnwrap(bundle.path(forResource: "InfoPlist", ofType: "strings"))
      let strings = try XCTUnwrap(NSDictionary(contentsOfFile: path) as? [String: String])

      XCTAssertEqual(strings["CFBundleDisplayName"], brand)
      XCTAssertEqual(strings["CFBundleName"], brand)
    }
  }

  func testPrivacyManifestDeclaresOptionalUnlinkedTelemetryWithoutTracking() throws {
    let url = try XCTUnwrap(Bundle.main.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"))
    let data = try Data(contentsOf: url)
    let manifest = try XCTUnwrap(
      PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any]
    )

    XCTAssertEqual(manifest["NSPrivacyTracking"] as? Bool, false)
    XCTAssertEqual(manifest["NSPrivacyTrackingDomains"] as? [String], [])
    let collectedTypes = try XCTUnwrap(
      manifest["NSPrivacyCollectedDataTypes"] as? [[String: Any]]
    )
    XCTAssertEqual(
      Set(collectedTypes.compactMap { $0["NSPrivacyCollectedDataType"] as? String }),
      [
        "NSPrivacyCollectedDataTypeCrashData",
        "NSPrivacyCollectedDataTypeProductInteraction",
        "NSPrivacyCollectedDataTypeDeviceID",
        "NSPrivacyCollectedDataTypeOtherUsageData",
        "NSPrivacyCollectedDataTypeGameplayContent",
        "NSPrivacyCollectedDataTypePurchaseHistory",
        "NSPrivacyCollectedDataTypeOtherDiagnosticData",
      ]
    )
    XCTAssertTrue(collectedTypes.allSatisfy { $0["NSPrivacyCollectedDataTypeLinked"] as? Bool == false })
    XCTAssertTrue(collectedTypes.allSatisfy { $0["NSPrivacyCollectedDataTypeTracking"] as? Bool == false })

    let accessedTypes = try XCTUnwrap(
      manifest["NSPrivacyAccessedAPITypes"] as? [[String: Any]]
    )
    let reasons = Dictionary(
      uniqueKeysWithValues: try accessedTypes.map { entry in
        (
          try XCTUnwrap(entry["NSPrivacyAccessedAPIType"] as? String),
          try XCTUnwrap(entry["NSPrivacyAccessedAPITypeReasons"] as? [String])
        )
      }
    )

    XCTAssertEqual(reasons["NSPrivacyAccessedAPICategoryUserDefaults"], ["CA92.1"])
    XCTAssertEqual(reasons["NSPrivacyAccessedAPICategoryActiveKeyboards"], ["54BD.1"])
  }

  func testTelemetryConsentsAreIndependentAndDefaultOff() throws {
    let suiteName = "AppSettingsTests.telemetry.\(UUID().uuidString)"
    let isolated = try XCTUnwrap(UserDefaults(suiteName: suiteName))
    defer { isolated.removePersistentDomain(forName: suiteName) }

    XCTAssertFalse(isolated.bool(forKey: SettingsPreferenceKeys.anonymousAnalyticsEnabled))
    XCTAssertFalse(isolated.bool(forKey: SettingsPreferenceKeys.crashDiagnosticsEnabled))
    XCTAssertEqual(isolated.integer(forKey: SettingsPreferenceKeys.privacyNoticeVersion), 0)
    isolated.set(true, forKey: SettingsPreferenceKeys.anonymousAnalyticsEnabled)
    XCTAssertTrue(isolated.bool(forKey: SettingsPreferenceKeys.anonymousAnalyticsEnabled))
    XCTAssertFalse(isolated.bool(forKey: SettingsPreferenceKeys.crashDiagnosticsEnabled))
  }

  func testAnalyticsAppOpenTrackerCapturesOncePerForegroundAfterConsent() {
    var tracker = AnalyticsAppOpenTracker(analyticsEnabled: false)

    XCTAssertNil(tracker.configure())
    XCTAssertEqual(tracker.updateConsent(true), "cold_start")
    XCTAssertNil(tracker.sceneDidBecomeActive())
    XCTAssertNil(tracker.updateConsent(true))

    tracker.sceneDidEnterBackground()
    XCTAssertEqual(tracker.sceneDidBecomeActive(), "foreground")
    XCTAssertNil(tracker.sceneDidBecomeActive())

    XCTAssertNil(tracker.updateConsent(false))
    tracker.sceneDidEnterBackground()
    XCTAssertNil(tracker.sceneDidBecomeActive())
    XCTAssertEqual(tracker.updateConsent(true), "foreground")
  }

  @MainActor
  func testDeckCategoryUsesOnlyClosedBuckets() {
    let telemetry = TelemetryService.shared

    XCTAssertEqual(telemetry.deckCategory(tags: ["TOPIK"], level: 5), "topik")
    XCTAssertEqual(telemetry.deckCategory(tags: ["韓国旅行"], level: 2), "travel")
    XCTAssertEqual(telemetry.deckCategory(tags: ["日常"], level: 2), "daily")
    XCTAssertEqual(telemetry.deckCategory(tags: ["K-POP"], level: 3), "trend")
    XCTAssertEqual(telemetry.deckCategory(tags: ["公式"], level: 1), "beginner")
    XCTAssertEqual(telemetry.deckCategory(tags: ["会話"], level: 3), "unknown")
  }

  func testPrivacyNoticeAppearsOnlyAfterOnboardingOutsideSessionsAndOncePerVersion() {
    XCTAssertFalse(
      PrivacyNoticePolicy.shouldPresent(
        reviewedVersion: 0,
        onboardingCompleted: false,
        appTourCompleted: true
      )
    )
    XCTAssertFalse(
      PrivacyNoticePolicy.shouldPresent(
        reviewedVersion: 0,
        onboardingCompleted: true,
        appTourCompleted: false
      )
    )
    XCTAssertFalse(
      PrivacyNoticePolicy.shouldPresent(
        reviewedVersion: 0,
        onboardingCompleted: true,
        appTourCompleted: true,
        sessionIsActive: true
      )
    )
    XCTAssertFalse(
      PrivacyNoticePolicy.shouldPresent(
        reviewedVersion: 0,
        onboardingCompleted: true,
        appTourCompleted: true,
        hasBlockingPresentation: true
      )
    )
    XCTAssertTrue(
      PrivacyNoticePolicy.shouldPresent(
        reviewedVersion: 0,
        onboardingCompleted: true,
        appTourCompleted: true
      )
    )
    XCTAssertFalse(
      PrivacyNoticePolicy.shouldPresent(
        reviewedVersion: PrivacyNoticePolicy.currentVersion,
        onboardingCompleted: true,
        appTourCompleted: true
      )
    )
  }

  func testAnalyticsContractRequiresFieldsAndClosedEnumValues() {
    let common: [AnalyticsProperty: Any] = [
      .schemaVersion: 1,
      .platform: "ios",
      .appVersion: "1.1.0",
      .buildNumber: "8",
      .locale: "ja",
    ]
    XCTAssertFalse(AnalyticsContract.accepts(event: .featureViewed, properties: common))
    XCTAssertTrue(
      AnalyticsContract.accepts(
        event: .featureViewed,
        properties: common.merging([.feature: "practice"]) { _, new in new }
      )
    )
    XCTAssertFalse(
      AnalyticsContract.accepts(
        event: .featureViewed,
        properties: common.merging([.feature: "typed free text"]) { _, new in new }
      )
    )
  }

  func testIOSAnalyticsPayloadShapesMatchSharedContract() {
    let common: [AnalyticsProperty: Any] = [
      .schemaVersion: 1,
      .platform: "ipados",
      .appVersion: "1.1",
      .buildNumber: "8",
      .locale: "ja",
    ]
    let samples: [(AnalyticsEvent, [AnalyticsProperty: Any])] = [
      (.appOpened, [.entryPoint: "foreground"]),
      (.featureViewed, [.feature: "deck_maker"]),
      (.onboardingStepCompleted, [.onboardingStep: "hatch_3"]),
      (.deckDownloaded, [.deckSource: "catalog", .deckCategory: "travel"]),
      (
        .sessionStarted,
        [
          .sessionKind: "game", .deckSource: "bundled", .inputMode: "builtin",
          .gameMode: "flow", .difficulty: "beginner",
        ]
      ),
      (
        .sessionCompleted,
        [
          .sessionKind: "game", .result: "completed", .durationBucket: "1_to_3m",
          .itemCountBucket: "4_to_10", .deckSource: "bundled", .inputMode: "builtin",
          .gameMode: "flow", .difficulty: "beginner",
        ]
      ),
      (
        .sessionAbandoned,
        [
          .sessionKind: "free_practice", .reason: "user_closed",
          .durationBucket: "under_1m", .deckSource: "created", .inputMode: "os_ime",
        ]
      ),
      (
        .gameResult,
        [
          .gameMode: "spacing", .difficulty: "advanced", .result: "completed",
          .scoreBucket: "1000_to_4999", .inputMode: "not_applicable",
          .deckSource: "bundled",
        ]
      ),
      (.reviewCompleted, [.itemCountBucket: "1_to_3", .result: "completed"]),
      (
        .deckMakerAction,
        [.action: "imported", .itemCountBucket: "11_to_30", .deckSource: "imported"]
      ),
      (.purchaseFlow, [.purchaseState: "pending"]),
      (.settingChanged, [.setting: "theme", .valueBucket: "dark"]),
      (.shareCompleted, [.action: "saved_image", .gameMode: "dictation"]),
    ]

    for (event, properties) in samples {
      XCTAssertTrue(
        AnalyticsContract.accepts(
          event: event,
          properties: common.merging(properties) { _, new in new }
        ),
        "Rejected iOS sample for \(event.rawValue)"
      )
    }
  }

  func testPostHogTransportStripsSDKDeviceAndSessionProperties() throws {
    let sanitized = try XCTUnwrap(
      AnalyticsTransportPrivacy.sanitizedProperties(
        eventName: AnalyticsEvent.appOpened.rawValue,
        properties: [
          AnalyticsProperty.schemaVersion.rawValue: AnalyticsContract.schemaVersion,
          AnalyticsProperty.platform.rawValue: "ios",
          AnalyticsProperty.appVersion.rawValue: "1.1",
          AnalyticsProperty.buildNumber.rawValue: "7",
          AnalyticsProperty.locale.rawValue: "ja",
          AnalyticsProperty.entryPoint.rawValue: "cold_start",
          "$geoip_disable": true,
          "$process_person_profile": false,
          "$lib": "posthog-ios",
          "$lib_version": "3.69.5",
          "$device_model": "iPhone18,3",
          "$os_version": "26.5",
          "$network_wifi": true,
          "$session_id": "must-not-leave-device",
          "$timezone": "Asia/Tokyo",
        ]
      )
    )

    XCTAssertEqual(sanitized[AnalyticsProperty.platform.rawValue] as? String, "ios")
    XCTAssertEqual(sanitized["$geoip_disable"] as? Bool, true)
    XCTAssertEqual(sanitized["$process_person_profile"] as? Bool, false)
    XCTAssertEqual(sanitized["$lib"] as? String, "posthog-ios")
    XCTAssertNil(sanitized["$device_model"])
    XCTAssertNil(sanitized["$os_version"])
    XCTAssertNil(sanitized["$network_wifi"])
    XCTAssertNil(sanitized["$session_id"])
    XCTAssertNil(sanitized["$timezone"])
    XCTAssertNil(
      AnalyticsTransportPrivacy.sanitizedProperties(
        eventName: "$screen",
        properties: ["$screen_name": "Home"]
      )
    )
  }

  func testAnalyticsPlatformDetectsIPadCompatibilityMode() {
    XCTAssertEqual(
      AnalyticsTransportPrivacy.platform(
        idiom: .phone,
        deviceModel: "iPad",
        simulatorModelIdentifier: nil
      ),
      "ipados"
    )
    XCTAssertEqual(
      AnalyticsTransportPrivacy.platform(
        idiom: .phone,
        deviceModel: "iPhone",
        simulatorModelIdentifier: "iPad16,6"
      ),
      "ipados"
    )
    XCTAssertEqual(
      AnalyticsTransportPrivacy.platform(
        idiom: .phone,
        deviceModel: "iPhone",
        simulatorModelIdentifier: "iPhone18,3"
      ),
      "ios"
    )
  }

  func testReleaseLinksUsePublicHTTPSPages() {
    XCTAssertEqual(
      AppReleaseLinks.privacyPolicy.absoluteString, "https://hancoweb.vercel.app/privacy")
    XCTAssertEqual(AppReleaseLinks.support.absoluteString, "https://hancoweb.vercel.app/support")
    XCTAssertEqual(AppReleaseLinks.privacyPolicy.scheme, "https")
    XCTAssertEqual(AppReleaseLinks.support.scheme, "https")
  }

  func testAdaptiveMetricsUseActualAvailableWidthBoundaries() {
    XCTAssertEqual(HancoAdaptiveMetrics(availableWidth: 599).widthClass, .compact)
    XCTAssertEqual(HancoAdaptiveMetrics(availableWidth: 600).widthClass, .medium)
    XCTAssertEqual(HancoAdaptiveMetrics(availableWidth: 899).widthClass, .medium)
    XCTAssertEqual(HancoAdaptiveMetrics(availableWidth: 900).widthClass, .wide)
  }

  func testAdaptiveMetricsGrowPaddingAndHubColumnsWithoutChangingContentCaps() {
    let compact = HancoAdaptiveMetrics(availableWidth: 390)
    let medium = HancoAdaptiveMetrics(availableWidth: 744)
    let wide = HancoAdaptiveMetrics(availableWidth: 1_180)

    XCTAssertEqual(
      [compact.horizontalPadding, medium.horizontalPadding, wide.horizontalPadding],
      [18, 24, 32]
    )
    XCTAssertEqual(
      [compact.hubColumnCount, medium.hubColumnCount, wide.hubColumnCount],
      [2, 3, 4]
    )
    XCTAssertFalse(compact.usesTwoColumnDashboard)
    XCTAssertFalse(medium.usesTwoColumnDashboard)
    XCTAssertTrue(wide.usesTwoColumnDashboard)
    XCTAssertEqual(wide.formContentMaxWidth, 680)
    XCTAssertEqual(wide.readableContentMaxWidth, 720)
    XCTAssertEqual(wide.resultContentMaxWidth, 760)
    XCTAssertEqual(wide.hubContentMaxWidth, 1_120)
    XCTAssertEqual(wide.sessionLaneMaxWidth, 920)
    XCTAssertEqual(wide.keyboardMaxWidth, 820)
    XCTAssertLessThanOrEqual(wide.keyboardMaxWidth, wide.sessionLaneMaxWidth)
    XCTAssertLessThanOrEqual(wide.formContentMaxWidth, wide.readableContentMaxWidth)
  }

  func testContentReportEmailIncludesOnlyRequiredContext() throws {
    let url = ContentFeedbackLinkBuilder.makeURL(
      context: .report(deckID: "official_daily_words", deckVersion: 4),
      language: .japanese,
      appVersion: "1.0.1",
      buildNumber: "4"
    )
    let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
    let query = Dictionary(
      uniqueKeysWithValues: try XCTUnwrap(components.queryItems).map {
        ($0.name, try XCTUnwrap($0.value))
      }
    )

    XCTAssertEqual(components.scheme, "mailto")
    XCTAssertEqual(components.path, ContentFeedbackLinkBuilder.recipient)
    XCTAssertEqual(query["subject"], "[ピヨキー] デッキ内容の報告")

    let body = try XCTUnwrap(query["body"])
    XCTAssertTrue(body.contains("type: content_report"))
    XCTAssertTrue(body.contains("source: deck_detail"))
    XCTAssertTrue(body.contains("app_version: 1.0.1"))
    XCTAssertTrue(body.contains("build: 4"))
    XCTAssertTrue(body.contains("language: ja"))
    XCTAssertTrue(body.contains("deck_id: official_daily_words"))
    XCTAssertTrue(body.contains("deck_version: 4"))
    XCTAssertFalse(body.localizedCaseInsensitiveContains("device_id"))
    XCTAssertFalse(body.localizedCaseInsensitiveContains("user_id"))
  }

  func testDeckSuggestionEmailDoesNotInventDeckContext() throws {
    let url = ContentFeedbackLinkBuilder.makeURL(
      context: .suggestion(source: .discover),
      language: .korean,
      appVersion: "1.0.1",
      buildNumber: "4"
    )
    let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
    let body = try XCTUnwrap(
      components.queryItems?.first(where: { $0.name == "body" })?.value
    )

    XCTAssertTrue(body.contains("type: deck_suggestion"))
    XCTAssertTrue(body.contains("source: discover"))
    XCTAssertFalse(body.contains("deck_id:"))
    XCTAssertFalse(body.contains("deck_version:"))
  }

  private func localizedStrings(for language: AppLanguage) throws -> [String: String] {
    let bundle = AppLocalization.localizedBundle(for: language)
    let path = try XCTUnwrap(bundle.path(forResource: "Localizable", ofType: "strings"))
    return try XCTUnwrap(NSDictionary(contentsOfFile: path) as? [String: String])
  }

  private func formatArguments(in value: String) -> [String] {
    let pattern = #"%(?:[0-9]+\$)?(?:[-+#0 ]*[0-9]*)?(?:\.[0-9]+)?[@a-zA-Z%]"#
    guard let expression = try? NSRegularExpression(pattern: pattern) else { return [] }
    let range = NSRange(value.startIndex..., in: value)
    return expression.matches(in: value, range: range).compactMap { match in
      guard let swiftRange = Range(match.range, in: value) else { return nil }
      return String(value[swiftRange])
    }
  }
}
