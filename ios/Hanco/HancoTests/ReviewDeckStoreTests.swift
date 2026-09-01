import DeckKit
import XCTest

@testable import Hanco

final class ReviewDeckStoreTests: XCTestCase {
  private var rootURL: URL!
  private var store: ReviewDeckStore!

  override func setUpWithError() throws {
    rootURL = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    store = ReviewDeckStore(rootURL: rootURL)
  }

  override func tearDownWithError() throws {
    if FileManager.default.fileExists(atPath: rootURL.path) {
      try FileManager.default.removeItem(at: rootURL)
    }
    store = nil
    rootURL = nil
  }

  func testMistakeAddsPersistentSnapshotAndResetsPerfectProgress() throws {
    let firstDate = Date(timeIntervalSince1970: 1_000)
    XCTAssertEqual(
      try store.recordMistake(item: item, sourceDeckId: "deck", at: firstDate),
      .added
    )
    XCTAssertEqual(
      try store.recordPerfect(itemId: item.id, sourceDeckId: "deck"),
      .updated
    )
    XCTAssertEqual(
      try store.recordMistake(item: item, sourceDeckId: "deck"),
      .updated
    )

    let reloaded = try ReviewDeckStore(rootURL: rootURL).loadSnapshot()
    let reviewItem = try XCTUnwrap(reloaded.activeItems.first)
    XCTAssertEqual(reviewItem.ko, "회사")
    XCTAssertEqual(reviewItem.readingJa, "フェサ")
    XCTAssertEqual(reviewItem.meaningJa, "会社")
    XCTAssertEqual(reviewItem.missCount, 2)
    XCTAssertEqual(reviewItem.consecutivePerfect, 0)
    XCTAssertEqual(reviewItem.addedAt, firstDate)
  }

  func testThreeConsecutivePerfectCompletionsGraduateItem() throws {
    try store.recordMistake(item: item, sourceDeckId: "deck")

    XCTAssertEqual(try perfect(), .updated)
    XCTAssertEqual(try perfect(), .updated)
    let graduatedAt = Date(timeIntervalSince1970: 2_000)
    XCTAssertEqual(try perfect(at: graduatedAt), .graduated)

    let snapshot = try store.loadSnapshot()
    XCTAssertTrue(snapshot.activeItems.isEmpty)
    let reviewItem = try XCTUnwrap(snapshot.items.values.first)
    XCTAssertEqual(reviewItem.consecutivePerfect, 3)
    XCTAssertEqual(reviewItem.graduatedAt, graduatedAt)
  }

  func testPerfectDoesNotAddOrReactivateUntrackedItem() throws {
    XCTAssertEqual(try perfect(), .unchanged)
    XCTAssertTrue(try store.loadSnapshot().items.isEmpty)

    try store.recordMistake(item: item, sourceDeckId: "deck")
    _ = try perfect()
    _ = try perfect()
    _ = try perfect()

    XCTAssertEqual(try perfect(), .unchanged)
    XCTAssertTrue(try store.loadSnapshot().activeItems.isEmpty)
  }

  func testManualAddRemoveAndGraduatedReactivation() throws {
    XCTAssertEqual(try store.addManually(item: item, sourceDeckId: "deck"), .added)
    XCTAssertEqual(try store.removeManually(itemId: item.id, sourceDeckId: "deck"), .removed)
    XCTAssertEqual(try store.removeManually(itemId: item.id, sourceDeckId: "deck"), .unchanged)

    try store.recordMistake(item: item, sourceDeckId: "deck")
    _ = try perfect()
    _ = try perfect()
    _ = try perfect()
    XCTAssertEqual(try store.addManually(item: item, sourceDeckId: "deck"), .updated)

    let reactivated = try XCTUnwrap(store.loadSnapshot().activeItems.first)
    XCTAssertEqual(reactivated.consecutivePerfect, 0)
    XCTAssertNil(reactivated.graduatedAt)
  }

  func testMarkSourceUnavailablePersistsHistoryWithoutLeavingActiveItems() throws {
    let addedAt = Date(timeIntervalSince1970: 1_000)
    try store.recordMistake(item: item, sourceDeckId: "deck", at: addedAt)
    _ = try store.recordPerfect(itemId: item.id, sourceDeckId: "deck")

    XCTAssertEqual(try store.markSourceUnavailable(deckId: "deck"), .updated)
    XCTAssertEqual(try store.markSourceUnavailable(deckId: "deck"), .unchanged)

    let reloaded = try ReviewDeckStore(rootURL: rootURL).loadSnapshot()
    XCTAssertTrue(reloaded.activeItems.isEmpty)
    let retained = try XCTUnwrap(reloaded.items["deck::item_company"])
    XCTAssertEqual(retained.missCount, 1)
    XCTAssertEqual(retained.consecutivePerfect, 1)
    XCTAssertEqual(retained.addedAt, addedAt)
    XCTAssertEqual(retained.isSourceAvailable, false)
  }

  func testUnsupportedSchemaIsRejected() throws {
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    try Data("{\"schema_version\":99,\"items\":[]}".utf8)
      .write(to: rootURL.appendingPathComponent("review-deck.json"))

    XCTAssertThrowsError(try store.loadSnapshot()) { error in
      XCTAssertEqual(error as? ReviewDeckStoreError, .unsupportedSchema(99))
    }
  }

  func testLegacyReviewSnapshotWithoutLocalizationsUsesJapaneseBaseFallback() throws {
    try FileManager.default.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let legacy = """
      {
        "schema_version": 1,
        "items": [{
          "item_id": "item_company",
          "source_deck_id": "deck",
          "ko": "회사",
          "reading_ja": "フェサ",
          "meaning_ja": "会社",
          "miss_count": 2,
          "consecutive_perfect": 1,
          "added_at": "1970-01-01T00:16:40Z",
          "graduated_at": null
        }]
      }
      """
    try Data(legacy.utf8).write(to: rootURL.appendingPathComponent("review-deck.json"))

    let loaded = try XCTUnwrap(store.loadSnapshot().activeItems.first)
    XCTAssertNil(loaded.localizations)
    XCTAssertNil(loaded.deckItem.localizedMeaning(languageCode: "en"))
    XCTAssertNil(loaded.deckItem.localizedReading(languageCode: "en"))
    XCTAssertEqual(loaded.deckItem.localizedMeaning(languageCode: "ja"), "会社")
    XCTAssertEqual(loaded.deckItem.localizedReading(languageCode: "ja-JP"), "フェサ")
    XCTAssertEqual(loaded.missCount, 2)
    XCTAssertEqual(loaded.consecutivePerfect, 1)
  }

  func testRecordingTranslatedItemEnrichesLegacyReviewProgress() throws {
    let addedAt = Date(timeIntervalSince1970: 1_000)
    try store.recordMistake(item: item, sourceDeckId: "deck", at: addedAt)
    _ = try perfect()

    let translated = DeckItem(
      id: item.id,
      ko: item.ko,
      readingJa: item.readingJa,
      meaningJa: item.meaningJa,
      audio: nil,
      localizations: [
        "en": DeckItemLocalization(meaning: "company", reading: "hoesa")
      ]
    )
    XCTAssertEqual(
      try store.recordMistake(item: translated, sourceDeckId: "deck"),
      .updated
    )

    let enriched = try XCTUnwrap(store.loadSnapshot().activeItems.first)
    XCTAssertEqual(enriched.deckItem.localizedMeaning(languageCode: "en"), "company")
    XCTAssertEqual(enriched.deckItem.localizedReading(languageCode: "en"), "hoesa")
    XCTAssertEqual(enriched.missCount, 2)
    XCTAssertEqual(enriched.consecutivePerfect, 0)
    XCTAssertEqual(enriched.addedAt, addedAt)
  }

  @MainActor
  func testDeleteThenSameIDReconcileReactivatesStableItemsAndPreservesHistory() async throws {
    let library = ReviewDeckLibrary(
      store: store,
      debounceNanoseconds: 60_000_000_000
    )
    XCTAssertEqual(library.recordMistake(item: item, sourceDeckId: "deck"), .added)
    XCTAssertEqual(
      library.recordMistake(
        item: DeckItem(
          id: "item_removed",
          ko: "학교",
          readingJa: "ハッキョ",
          meaningJa: "学校",
          audio: nil
        ),
        sourceDeckId: "deck"
      ),
      .added
    )
    XCTAssertEqual(library.recordPerfect(itemId: item.id, sourceDeckId: "deck"), .updated)

    let deletionMutation = await library.markSourceUnavailable(deckId: "deck")
    XCTAssertEqual(deletionMutation, .updated)
    XCTAssertTrue(library.activeItems.isEmpty)
    let unavailable = try XCTUnwrap(store.loadSnapshot().items["deck::item_company"])
    XCTAssertEqual(unavailable.missCount, 1)
    XCTAssertEqual(unavailable.consecutivePerfect, 1)
    XCTAssertEqual(unavailable.isSourceAvailable, false)

    let replacement = Deck(
      deckId: "deck",
      version: 2,
      name: "更新デッキ",
      author: DeckAuthor(id: "user", nickname: "User"),
      official: false,
      type: .word,
      level: 1,
      tags: ["単語"],
      createdAt: Date(timeIntervalSince1970: 1_000),
      updatedAt: Date(timeIntervalSince1970: 2_000),
      items: [
        DeckItem(
          id: item.id,
          ko: "회사원",
          readingJa: "フェサウォン",
          meaningJa: "会社員",
          audio: nil
        )
      ]
    )

    XCTAssertEqual(library.reconcile(with: replacement), .updated)

    let updated = try XCTUnwrap(library.items["deck::item_company"])
    XCTAssertEqual(updated.ko, "회사원")
    XCTAssertEqual(updated.missCount, 1)
    XCTAssertEqual(updated.consecutivePerfect, 1)
    XCTAssertEqual(updated.isSourceAvailable, true)

    let removed = try XCTUnwrap(library.items["deck::item_removed"])
    XCTAssertEqual(removed.missCount, 1)
    XCTAssertEqual(removed.isSourceAvailable, false)
    XCTAssertFalse(library.activeItems.contains(where: { $0.itemId == "item_removed" }))

    let persisted = await library.flushAndWait()
    XCTAssertTrue(persisted)
    let reloaded = try store.loadSnapshot()
    XCTAssertEqual(reloaded.items["deck::item_company"]?.missCount, 1)
    XCTAssertEqual(reloaded.items["deck::item_company"]?.consecutivePerfect, 1)
    XCTAssertEqual(reloaded.items["deck::item_company"]?.isSourceAvailable, true)
    XCTAssertEqual(reloaded.items["deck::item_removed"]?.missCount, 1)
    XCTAssertEqual(reloaded.items["deck::item_removed"]?.isSourceAvailable, false)
  }

  func testCorruptReviewFileRestoresMistakeHistoryFromBackup() throws {
    try store.recordMistake(item: item, sourceDeckId: "deck")
    let fileURL = rootURL.appendingPathComponent("review-deck.json")
    try Data("{".utf8).write(to: fileURL)

    let recovered = try XCTUnwrap(store.loadSnapshot().activeItems.first)

    XCTAssertEqual(recovered.ko, item.ko)
    XCTAssertEqual(recovered.missCount, 1)
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: fileURL).path
      )
    )
  }

  @MainActor
  func testLibraryBatchesPerJamoMistakesUntilExplicitFlush() async throws {
    let library = ReviewDeckLibrary(
      store: store,
      debounceNanoseconds: 60_000_000_000
    )

    XCTAssertEqual(library.recordMistake(item: item, sourceDeckId: "deck"), .added)
    XCTAssertEqual(library.recordMistake(item: item, sourceDeckId: "deck"), .updated)
    XCTAssertEqual(library.recordMistake(item: item, sourceDeckId: "deck"), .updated)

    XCTAssertEqual(library.activeItems.first?.missCount, 3)
    XCTAssertFalse(
      FileManager.default.fileExists(
        atPath: rootURL.appendingPathComponent("review-deck.json").path
      )
    )
    let persisted = await library.flushAndWait()
    XCTAssertTrue(persisted)
    XCTAssertEqual(try store.loadSnapshot().activeItems.first?.missCount, 3)
  }

  func testSemanticallyInvalidReviewTargetRestoresValidatedBackup() throws {
    try store.recordMistake(item: item, sourceDeckId: "deck")
    let fileURL = rootURL.appendingPathComponent("review-deck.json")
    var object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: fileURL)) as? [String: Any]
    )
    var items = try XCTUnwrap(object["items"] as? [[String: Any]])
    items[0]["ko"] = "🙂"
    object["items"] = items
    try JSONSerialization.data(withJSONObject: object).write(to: fileURL)

    let recovered = try XCTUnwrap(store.loadSnapshot().activeItems.first)

    XCTAssertEqual(recovered.ko, item.ko)
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: fileURL).path
      )
    )
  }

  func testSessionReviewItemMergeKeepsUniqueJamoLocationsAndAddsMistakeCounts() {
    var reviewItem = SessionReviewItem(
      item: item,
      sourceDeckId: "deck",
      resolution: SessionItemResolution(
        itemIndex: 0,
        hadMistake: true,
        mistakeCount: 2,
        mistakenJamoIndices: [0, 1]
      )
    )

    reviewItem.merge(
      SessionItemResolution(
        itemIndex: 0,
        hadMistake: true,
        mistakeCount: 2,
        mistakenJamoIndices: [1, 3]
      )
    )

    XCTAssertEqual(reviewItem.mistakeCount, 4)
    XCTAssertEqual(reviewItem.mistakenJamoIndices, [0, 1, 3])
  }

  private var item: DeckItem {
    DeckItem(
      id: "item_company",
      ko: "회사",
      readingJa: "フェサ",
      meaningJa: "会社",
      audio: nil
    )
  }

  private func perfect(at date: Date = Date()) throws -> ReviewDeckMutation {
    try store.recordPerfect(itemId: item.id, sourceDeckId: "deck", at: date)
  }
}
