import DeckKit
import Foundation
import SwiftUI

enum AppReleaseLinks {
  static let privacyPolicy = URL(string: "https://hancoweb.vercel.app/privacy")!
  static let support = URL(string: "https://hancoweb.vercel.app/support")!
}

enum ContentFeedbackKind: String {
  case general = "content_feedback"
  case deckSuggestion = "deck_suggestion"
  case contentReport = "content_report"
}

enum ContentFeedbackSource: String {
  case discover
  case deckDetail = "deck_detail"
  case settings
}

struct ContentFeedbackContext: Equatable {
  let kind: ContentFeedbackKind
  let source: ContentFeedbackSource
  let deckID: String?
  let deckVersion: Int?

  static func general(source: ContentFeedbackSource) -> ContentFeedbackContext {
    ContentFeedbackContext(
      kind: .general,
      source: source,
      deckID: nil,
      deckVersion: nil
    )
  }

  static func suggestion(source: ContentFeedbackSource) -> ContentFeedbackContext {
    ContentFeedbackContext(
      kind: .deckSuggestion,
      source: source,
      deckID: nil,
      deckVersion: nil
    )
  }

  static func report(
    deckID: String,
    deckVersion: Int,
    source: ContentFeedbackSource = .deckDetail
  ) -> ContentFeedbackContext {
    ContentFeedbackContext(
      kind: .contentReport,
      source: source,
      deckID: deckID,
      deckVersion: deckVersion
    )
  }
}

enum ContentFeedbackLinkBuilder {
  static let recipient = "contact@typee.app"

  static func makeURL(
    context: ContentFeedbackContext,
    language: AppLanguage = .current,
    appVersion: String = Bundle.main.object(
      forInfoDictionaryKey: "CFBundleShortVersionString"
    ) as? String ?? "unknown",
    buildNumber: String = Bundle.main.object(
      forInfoDictionaryKey: "CFBundleVersion"
    ) as? String ?? "unknown"
  ) -> URL {
    let localizedBundle = AppLocalization.localizedBundle(for: language)
    let subjectKey: String
    let promptKey: String
    switch context.kind {
    case .general:
      subjectKey = "content_feedback.email.subject.general"
      promptKey = "content_feedback.email.prompt.general"
    case .deckSuggestion:
      subjectKey = "content_feedback.email.subject.suggestion"
      promptKey = "content_feedback.email.prompt.suggestion"
    case .contentReport:
      subjectKey = "content_feedback.email.subject.report"
      promptKey = "content_feedback.email.prompt.report"
    }

    let subject = localizedBundle.localizedString(forKey: subjectKey, value: nil, table: nil)
    let prompt = localizedBundle.localizedString(forKey: promptKey, value: nil, table: nil)
    let caution = localizedBundle.localizedString(
      forKey: "content_feedback.email.caution",
      value: nil,
      table: nil
    )

    var metadata = [
      "type: \(context.kind.rawValue)",
      "source: \(context.source.rawValue)",
      "app_version: \(appVersion)",
      "build: \(buildNumber)",
      "language: \(language.rawValue)",
    ]
    if let deckID = context.deckID {
      metadata.append("deck_id: \(deckID)")
    }
    if let deckVersion = context.deckVersion {
      metadata.append("deck_version: \(deckVersion)")
    }

    var components = URLComponents()
    components.scheme = "mailto"
    components.path = recipient
    components.queryItems = [
      URLQueryItem(name: "subject", value: subject),
      URLQueryItem(
        name: "body",
        value: "\(prompt)\n\n---\n\(metadata.joined(separator: "\n"))\n\n\(caution)"
      ),
    ]
    return components.url ?? AppReleaseLinks.support
  }
}

struct ContentFeedbackLink<Label: View>: View {
  @Environment(\.openURL) private var openURL

  let context: ContentFeedbackContext
  let label: () -> Label

  init(
    context: ContentFeedbackContext,
    @ViewBuilder label: @escaping () -> Label
  ) {
    self.context = context
    self.label = label
  }

  var body: some View {
    Button {
      openURL(ContentFeedbackLinkBuilder.makeURL(context: context)) { accepted in
        if !accepted {
          openURL(AppReleaseLinks.support)
        }
      }
    } label: {
      label()
    }
  }
}

enum SettingsPreferenceKeys {
  static let fontScale = "settings.font_scale"
  static let theme = "settings.theme"
  static let language = "settings.language"
  static let practiceDisplayPreset = "settings.practice_display_preset"
  static let practiceShowsTarget = "settings.practice_shows_target"
  static let practiceShowsMeaning = "settings.practice_shows_meaning"
  static let practiceShowsReading = "settings.practice_shows_reading"
  static let practicePromptOrder = "settings.practice_prompt_order"
  static let practiceShowsJamo = "settings.practice_shows_jamo"
  static let practiceAutoSpeaks = "settings.practice_auto_speaks"
  static let practiceShowsMascot = "settings.practice_shows_mascot"
  static let practiceShowsComposition = "settings.practice_shows_composition"
  static let choseongShowsMeaning = "settings.choseong_shows_meaning"
  static let anonymousAnalyticsEnabled = "settings.anonymous_analytics_enabled"
  static let crashDiagnosticsEnabled = "settings.crash_diagnostics_enabled"
  static let privacyNoticeVersion = "settings.privacy_notice_version"

  static let all = [
    fontScale,
    theme,
    language,
    practiceDisplayPreset,
    practiceShowsTarget,
    practiceShowsMeaning,
    practiceShowsReading,
    practicePromptOrder,
    practiceShowsJamo,
    practiceAutoSpeaks,
    practiceShowsMascot,
    practiceShowsComposition,
    choseongShowsMeaning,
    anonymousAnalyticsEnabled,
    crashDiagnosticsEnabled,
    privacyNoticeVersion,
  ]
}

enum PrivacyNoticePolicy {
  static let currentVersion = 1

  static func shouldPresent(
    reviewedVersion: Int,
    onboardingCompleted: Bool,
    appTourCompleted: Bool,
    sessionIsActive: Bool = false,
    hasBlockingPresentation: Bool = false
  ) -> Bool {
    reviewedVersion < currentVersion
      && onboardingCompleted
      && appTourCompleted
      && !sessionIsActive
      && !hasBlockingPresentation
  }
}

enum PracticePromptField: String, CaseIterable, Identifiable {
  case target
  case meaning
  case reading

  var id: String { rawValue }

  var localizationKey: String {
    switch self {
    case .target: "settings.practice_target"
    case .meaning: "settings.practice_meaning"
    case .reading: "settings.practice_reading"
    }
  }
}

enum PracticePromptOrder: String, CaseIterable, Identifiable {
  case targetMeaningReading = "target_meaning_reading"
  case targetReadingMeaning = "target_reading_meaning"
  case meaningTargetReading = "meaning_target_reading"
  case meaningReadingTarget = "meaning_reading_target"
  case readingTargetMeaning = "reading_target_meaning"
  case readingMeaningTarget = "reading_meaning_target"

  var id: String { rawValue }

  var fields: [PracticePromptField] {
    switch self {
    case .targetMeaningReading: [.target, .meaning, .reading]
    case .targetReadingMeaning: [.target, .reading, .meaning]
    case .meaningTargetReading: [.meaning, .target, .reading]
    case .meaningReadingTarget: [.meaning, .reading, .target]
    case .readingTargetMeaning: [.reading, .target, .meaning]
    case .readingMeaningTarget: [.reading, .meaning, .target]
    }
  }

  var localizedLabel: String {
    fields
      .map { AppLocalization.string($0.localizationKey) }
      .joined(separator: " → ")
  }

  static func resolved(from rawValue: String) -> PracticePromptOrder {
    PracticePromptOrder(rawValue: rawValue) ?? .targetMeaningReading
  }
}

enum PracticeDisplayPreset: String, CaseIterable, Identifiable {
  case learning
  case focus

  var id: String { rawValue }

  static func resolved(from rawValue: String) -> PracticeDisplayPreset {
    PracticeDisplayPreset(rawValue: rawValue) ?? .learning
  }
}

enum HancoFontScale: String, CaseIterable, Identifiable {
  case small
  case standard
  case large

  var id: String { rawValue }

  var multiplier: CGFloat {
    switch self {
    case .small: 0.88
    case .standard: 1
    case .large: 1.16
    }
  }

  static func resolved(from rawValue: String) -> HancoFontScale {
    HancoFontScale(rawValue: rawValue) ?? .standard
  }
}

enum HancoTheme: String, CaseIterable, Identifiable {
  case light
  case dark

  var id: String { rawValue }

  var colorScheme: ColorScheme {
    self == .dark ? .dark : .light
  }

  static func resolved(from rawValue: String) -> HancoTheme {
    HancoTheme(rawValue: rawValue) ?? .light
  }
}

enum AppLanguage: String, CaseIterable, Identifiable {
  case japanese = "ja"
  case english = "en"
  case spanish = "es"

  var id: String { rawValue }

  var locale: Locale {
    switch self {
    case .japanese: Locale(identifier: "ja_JP")
    case .english: Locale(identifier: "en_US")
    case .spanish: Locale(identifier: "es_ES")
    }
  }

  /// The first-run language follows the device language. English is the
  /// global fallback for languages that do not yet have an app translation.
  static var preferred: AppLanguage {
    preferred(from: Locale.preferredLanguages)
  }

  static func preferred(from languageIdentifiers: [String]) -> AppLanguage {
    for identifier in languageIdentifiers {
      let languageCode = Locale(identifier: identifier).language.languageCode?.identifier
        ?? identifier.split(separator: "-").first.map(String.init)
      if let languageCode, let language = AppLanguage(rawValue: languageCode) {
        return language
      }
    }
    return .english
  }

  static var current: AppLanguage {
    resolved(
      from: UserDefaults.standard.string(forKey: SettingsPreferenceKeys.language)
        ?? AppLanguage.preferred.rawValue
    )
  }

  static func resolved(from rawValue: String) -> AppLanguage {
    AppLanguage(rawValue: rawValue) ?? .english
  }

  /// Change only the retired UI preference; never touch decks or learning history.
  static func migrateLegacyPreference(in defaults: UserDefaults = .standard) {
    guard defaults.string(forKey: SettingsPreferenceKeys.language) == "ko" else { return }
    defaults.set(AppLanguage.english.rawValue, forKey: SettingsPreferenceKeys.language)
  }
}

enum AppLocalization {
  static var locale: Locale { AppLanguage.current.locale }

  static func string(_ key: String) -> String {
    string(key, language: AppLanguage.current)
  }

  static func string(_ key: String, language: AppLanguage) -> String {
    localizedBundle(for: language).localizedString(
      forKey: key,
      value: nil,
      table: nil
    )
  }

  static func localizedBundle(for language: AppLanguage) -> Bundle {
    guard let path = Bundle.main.path(forResource: language.rawValue, ofType: "lproj"),
      let bundle = Bundle(path: path)
    else { return .main }
    return bundle
  }
}

extension DeckItem {
  var appMeaning: String? {
    localizedMeaning(languageCode: AppLanguage.current.rawValue)
  }

  var appReading: String? {
    localizedReading(languageCode: AppLanguage.current.rawValue)
  }
}

extension Deck {
  var appName: String {
    localizedName(languageCode: AppLanguage.current.rawValue)
      ?? AppLocalization.string("deck.localized_title_unavailable")
  }

  var appAuthorNickname: String {
    localizedAuthorNickname(languageCode: AppLanguage.current.rawValue)
      ?? AppLocalization.string(official ? "deck.official_author" : "deck.author_unavailable")
  }

  var appTags: [String] {
    localizedTags(languageCode: AppLanguage.current.rawValue) ?? []
  }
}

extension CatalogDeck {
  var appName: String {
    localizedName(languageCode: AppLanguage.current.rawValue)
      ?? AppLocalization.string("deck.localized_title_unavailable")
  }

  var appAuthorNickname: String {
    localizedAuthorNickname(languageCode: AppLanguage.current.rawValue)
      ?? AppLocalization.string(official ? "deck.official_author" : "deck.author_unavailable")
  }

  var appTags: [String] {
    localizedTags(languageCode: AppLanguage.current.rawValue) ?? []
  }

  var isAvailableInCurrentLanguage: Bool {
    hasLocalization(languageCode: AppLanguage.current.rawValue)
  }
}

extension CatalogPreviewItem {
  var appMeaning: String? {
    localizedMeaning(languageCode: AppLanguage.current.rawValue)
  }
}

extension CatalogTag {
  var appTag: String? {
    localizedTag(languageCode: AppLanguage.current.rawValue)
  }
}

private struct HancoFontScaleEnvironmentKey: EnvironmentKey {
  static let defaultValue: CGFloat = 1
}

extension EnvironmentValues {
  var hancoFontScale: CGFloat {
    get { self[HancoFontScaleEnvironmentKey.self] }
    set { self[HancoFontScaleEnvironmentKey.self] = newValue }
  }
}
