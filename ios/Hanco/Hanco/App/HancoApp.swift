import DeckKit
import Foundation
import SwiftUI

@main
struct HancoApp: App {
  @Environment(\.scenePhase) private var scenePhase

  init() {
    #if DEBUG
      if ProcessInfo.processInfo.environment["UITEST_RESET_KEYBOARD_PREFERENCES"] == "1" {
        KeyboardPreferenceKeys.all.forEach(UserDefaults.standard.removeObject(forKey:))
        SoundPreferenceKeys.all.forEach(UserDefaults.standard.removeObject(forKey:))
        SettingsPreferenceKeys.all.forEach(UserDefaults.standard.removeObject(forKey:))
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_DECK_LIBRARY"] == "1" {
        try? DeckInstallationStore.live.reset()
        try? UserDeckDraftStore.live.clear()
        resetPiyoDeckPendingImports()
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_CATALOG_CACHE"] == "1" {
        try? CatalogCacheStore.live.reset()
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_GAME_PROGRESS"] == "1" {
        try? GameProgressStore.live.reset()
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_REVIEW_DECK"] == "1" {
        try? ReviewDeckStore.live.reset()
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_CURRICULUM_PROGRESS"] == "1" {
        try? CurriculumProgressStore.live.reset()
      }
      if ProcessInfo.processInfo.environment["UITEST_SEED_FUTURE_CURRICULUM_SCHEMA"] == "1" {
        seedFutureCurriculumSchema()
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_RETENTION"] == "1" {
        try? RetentionStore.live.reset()
        DailyReminderSettingsStore().reset()
        UserDefaults.standard.removeObject(forKey: RandomWordPracticeHistory.storageKey)
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_ONBOARDING"] == "1" {
        OnboardingStore().reset()
      }
      if ProcessInfo.processInfo.environment["UITEST_RESET_MASCOT"] == "1" {
        MascotCompanionLibrary.allKeys.forEach(UserDefaults.standard.removeObject(forKey:))
      }
      if ProcessInfo.processInfo.environment["UITEST_SEED_APP_STORE_CAPTURE"] == "1" {
        seedAppStoreCaptureState()
      }
      if ProcessInfo.processInfo.environment["UITEST_SEED_PIYODECK_CAPTURE"] == "1" {
        seedPiyoDeckCaptureDocument()
      }
      if ProcessInfo.processInfo.environment["UITEST_SEED_USER_DECK_DELETE"] == "1" {
        seedUserDeckDeleteFixture()
      }
      if ProcessInfo.processInfo.environment["UITEST_SEED_PIYODECK_DOWNGRADE"] == "1" {
        seedPiyoDeckDowngradeFixture()
      }
      if ProcessInfo.processInfo.environment["UITEST_SEED_USER_DECK_DRAFT"] == "1" {
        seedUserDeckDraftFixture()
      }
    #endif
    TelemetryService.shared.configure()
  }

  var body: some Scene {
    WindowGroup {
      AppRootView()
        .onChange(of: scenePhase) { phase in
          switch phase {
          case .active:
            TelemetryService.shared.sceneDidBecomeActive()
          case .background:
            TelemetryService.shared.sceneDidEnterBackground()
          case .inactive:
            break
          @unknown default:
            break
          }
        }
    }
  }
}

#if DEBUG
  @MainActor
  private func seedAppStoreCaptureState() {
    let curriculum = CurriculumProgressStore.live
    let retention = RetentionStore.live
    try? curriculum.reset()
    try? retention.reset()

    let completedAt =
      RetentionCalendar.date(
        for: JSTDay(rawValue: "2026-07-16")!
      ) ?? Date()
    for stageID in [
      "chapter_1_basic_consonants",
      "chapter_2_basic_vowels",
      "chapter_3_syllable_building",
    ] {
      _ = try? curriculum.finishStage(
        stageId: stageID,
        stars: 3,
        accuracy: 100,
        at: completedAt
      )
    }

    for rawDay in ["2026-07-16", "2026-07-17", "2026-07-18"] {
      guard let day = JSTDay(rawValue: rawDay) else { continue }
      _ = try? retention.record(.dailyChallenge, on: day)
    }

    let defaults = UserDefaults.standard
    defaults.set(true, forKey: OnboardingStore.appTourCompletedKey)
    defaults.set("ピヨ", forKey: MascotCompanionLibrary.nameKey)
    defaults.set("none", forKey: MascotCompanionLibrary.propSelectionKey)
    defaults.set(
      MascotStage.chick.growthRank,
      forKey: MascotCompanionLibrary.celebratedStageKey
    )
    defaults.set(1_200, forKey: MascotCompanionLibrary.typedJamoKey)
  }

  private func seedFutureCurriculumSchema() {
    let rootURL = CurriculumProgressStore.live.rootURL
    try? FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    try? Data("{\"schema_version\":99,\"stage_progress\":[]}".utf8)
      .write(
        to: rootURL.appendingPathComponent("curriculum-progress.json"),
        options: .atomic
      )
  }

  private func seedPiyoDeckCaptureDocument() {
    let date = Date(timeIntervalSince1970: 1_786_588_800)
    let deck = Deck(
      deckId: "user_0123456789abcdef0123456789abcdef",
      version: 1,
      name: "旅行で使う韓国語",
      author: DeckAuthor(id: "user_local", nickname: "ピヨキー学習者"),
      official: false,
      type: .word,
      level: 1,
      tags: ["旅行", "あいさつ"],
      createdAt: date,
      updatedAt: date,
      items: [
        DeckItem(
          id: "item_00000000000000000000000000000001",
          ko: "안녕하세요",
          readingJa: "アンニョンハセヨ",
          meaningJa: "こんにちは",
          audio: nil
        ),
        DeckItem(
          id: "item_00000000000000000000000000000002",
          ko: "감사합니다",
          readingJa: "カムサハムニダ",
          meaningJa: "ありがとうございます",
          audio: nil
        ),
        DeckItem(
          id: "item_00000000000000000000000000000003",
          ko: "맛있어요",
          readingJa: "マシッソヨ",
          meaningJa: "おいしいです",
          audio: nil
        ),
      ]
    )

    guard
      let schemaData = try? PiyoDeckDocumentService.deckSchemaData(),
      let package = try? PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: schemaData)
    else { return }
    let fileManager = FileManager.default
    let rootURL = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("Hanco", isDirectory: true)
      .appendingPathComponent("PendingImports", isDirectory: true)
    try? fileManager.removeItem(at: rootURL)
    try? fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
    try? package.write(
      to: rootURL.appendingPathComponent("r11-capture.piyodeck"),
      options: .atomic
    )
  }

  private func seedUserDeckDeleteFixture() {
    installUITestDeck(
      makeUITestDeck(
        version: 2,
        name: "削除確認デッキ",
        updatedAt: Date(timeIntervalSince1970: 1_786_675_200),
        itemCount: 2
      )
    )
  }

  private func seedPiyoDeckDowngradeFixture() {
    let currentDeck = makeUITestDeck(
      version: 2,
      name: "現在の安全デッキ",
      updatedAt: Date(timeIntervalSince1970: 1_786_675_200),
      itemCount: 2
    )
    let incomingDeck = makeUITestDeck(
      version: 1,
      name: "古い読み込みデッキ",
      updatedAt: Date(timeIntervalSince1970: 1_783_996_800),
      itemCount: 1
    )
    installUITestDeck(currentDeck)

    guard
      let schemaData = try? PiyoDeckDocumentService.deckSchemaData(),
      let package = try? PiyoDeckPackageWriter.write(
        deck: incomingDeck,
        deckSchemaData: schemaData
      )
    else { return }
    let rootURL = piyoDeckPendingImportsRootURL()
    try? FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    try? package.write(
      to: rootURL.appendingPathComponent("r11-downgrade.piyodeck"),
      options: .atomic
    )
  }

  private func seedUserDeckDraftFixture() {
    var draft = UserDeckDraft(
      newAt: Date(timeIntervalSince1970: 1_786_675_200),
      uuidHexGenerator: { "cccccccccccccccccccccccccccccccc" }
    )
    draft.name = "復元する下書き"
    draft.authorNickname = "テスト学習者"
    draft.tags = ["復元"]
    draft.items[0].ko = "안녕"
    draft.items[0].readingJa = "アンニョン"
    draft.items[0].meaningJa = "こんにちは"
    _ = try? UserDeckDraftStore.live.save(
      draft,
      at: Date(timeIntervalSince1970: 1_786_675_200)
    )
  }

  private func makeUITestDeck(
    version: Int,
    name: String,
    updatedAt: Date,
    itemCount: Int
  ) -> Deck {
    let items = [
      DeckItem(
        id: "item_11111111111111111111111111111111",
        ko: "안녕하세요",
        readingJa: "アンニョンハセヨ",
        meaningJa: "こんにちは",
        audio: nil
      ),
      DeckItem(
        id: "item_22222222222222222222222222222222",
        ko: "감사합니다",
        readingJa: "カムサハムニダ",
        meaningJa: "ありがとうございます",
        audio: nil
      ),
    ]
    return Deck(
      deckId: "user_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      version: version,
      name: name,
      author: DeckAuthor(id: "user_local", nickname: "テスト学習者"),
      official: false,
      type: .word,
      level: 1,
      tags: ["安全確認"],
      createdAt: Date(timeIntervalSince1970: 1_775_750_400),
      updatedAt: updatedAt,
      items: Array(items.prefix(itemCount))
    )
  }

  private func installUITestDeck(_ deck: Deck) {
    guard let data = try? DeckKitJSON.makeEncoder().encode(deck) else { return }
    _ = try? DeckInstallationStore.live.install(
      data: data,
      source: .imported,
      packageFormatVersion: 1,
      isLocallyModified: false,
      now: Date(timeIntervalSince1970: 1_786_675_200)
    )
  }

  private func resetPiyoDeckPendingImports() {
    try? FileManager.default.removeItem(at: piyoDeckPendingImportsRootURL())
  }

  private func piyoDeckPendingImportsRootURL() -> URL {
    FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
      .appendingPathComponent("Hanco", isDirectory: true)
      .appendingPathComponent("PendingImports", isDirectory: true)
  }
#endif
