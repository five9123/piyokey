import DeckKit
import XCTest

@testable import Hanco

final class UserDeckDraftStoreTests: XCTestCase {
  private var rootURL: URL!
  private var store: UserDeckDraftStore!

  override func setUpWithError() throws {
    rootURL = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    store = UserDeckDraftStore(rootURL: rootURL)
  }

  override func tearDownWithError() throws {
    if FileManager.default.fileExists(atPath: rootURL.path) {
      try FileManager.default.removeItem(at: rootURL)
    }
    store = nil
    rootURL = nil
  }

  func testIncompleteNewDraftRoundTripsWithoutFinalDeckValidation() throws {
    let createdAt = Date(timeIntervalSince1970: 1_000)
    var draft = UserDeckDraft(
      newAt: createdAt,
      uuidHexGenerator: sequenceGenerator([
        String(repeating: "1", count: 32),
        String(repeating: "2", count: 32),
        String(repeating: "3", count: 32),
      ])
    )
    draft.setName("My unfinished deck", for: .english)
    draft.defaultLocale = "fr-CA"
    draft.setTags(["draft"], for: .english)
    draft.items[0].ko = "아직"
    draft.items[0].setMeaning("", for: .english)
    draft.addItem(uuidHexGenerator: { String(repeating: "4", count: 32) })
    draft.items[1].ko = ""

    let active = try store.save(draft, at: Date(timeIntervalSince1970: 2_000))
    let loaded = try XCTUnwrap(store.load())

    XCTAssertEqual(loaded, active)
    XCTAssertEqual(loaded.draft, draft)
    XCTAssertEqual(loaded.createdAt, Date(timeIntervalSince1970: 2_000))
    XCTAssertEqual(loaded.updatedAt, Date(timeIntervalSince1970: 2_000))
    XCTAssertNil(loaded.baseDeckID)
    XCTAssertNil(loaded.baseVersion)
  }

  func testRepeatedSaveOfSameFlowKeepsDraftIdentityAndCreationTime() throws {
    var draft = makeNewDraft(deckHex: "1", itemHex: "2")
    let first = try store.save(draft, at: Date(timeIntervalSince1970: 3_000))

    draft.name = "변경 중"
    let second = try store.save(draft, at: Date(timeIntervalSince1970: 4_000))

    XCTAssertEqual(second.draftID, first.draftID)
    XCTAssertEqual(second.createdAt, first.createdAt)
    XCTAssertEqual(second.updatedAt, Date(timeIntervalSince1970: 4_000))
    XCTAssertEqual(try store.load()?.draft.name, "변경 중")
  }

  func testDifferentFlowAtomicallyReplacesTheSingleActiveDraft() throws {
    let first = try store.save(
      makeNewDraft(deckHex: "1", itemHex: "2"),
      at: Date(timeIntervalSince1970: 5_000)
    )
    let replacementDraft = makeNewDraft(deckHex: "3", itemHex: "4")
    let replacement = try store.save(
      replacementDraft,
      at: Date(timeIntervalSince1970: 6_000)
    )

    XCTAssertNotEqual(replacement.draftID, first.draftID)
    XCTAssertEqual(try store.load()?.draft, replacementDraft)
    XCTAssertEqual(try store.load()?.createdAt, Date(timeIntervalSince1970: 6_000))
  }

  func testEditingAndOfficialCopyOriginsRoundTripWithBaseIdentity() throws {
    let userDeck = makeDeck(
      id: "user_\(String(repeating: "5", count: 32))",
      version: 7,
      official: false
    )
    let editing = UserDeckDraft(editing: userDeck)
    let editingActive = try store.save(editing, at: Date(timeIntervalSince1970: 7_000))

    XCTAssertEqual(editingActive.baseDeckID, userDeck.deckId)
    XCTAssertEqual(editingActive.baseVersion, 7)
    XCTAssertEqual(try store.load()?.draft.origin, .editing)

    let official = makeDeck(id: "official_source", version: 8, official: true)
    let copied = UserDeckDraft(
      copyingOfficial: official,
      at: Date(timeIntervalSince1970: 8_000),
      uuidHexGenerator: sequenceGenerator([
        String(repeating: "6", count: 32),
        String(repeating: "7", count: 32),
      ])
    )
    let copyActive = try store.save(copied, at: Date(timeIntervalSince1970: 9_000))

    XCTAssertEqual(copyActive.baseDeckID, official.deckId)
    XCTAssertNil(copyActive.baseVersion)
    XCTAssertEqual(
      try store.load()?.draft.origin,
      .officialCopy(sourceDeckID: official.deckId)
    )
  }

  func testWriteFailureKeepsPreviouslyPersistedDraft() throws {
    enum ExpectedFailure: Error { case write }

    let original = makeNewDraft(deckHex: "1", itemHex: "2")
    try store.save(original, at: Date(timeIntervalSince1970: 10_000))
    let failingStore = UserDeckDraftStore(
      rootURL: rootURL,
      fileWriter: { _, _, _ in throw ExpectedFailure.write }
    )
    var changed = original
    changed.name = "must not replace primary"

    XCTAssertThrowsError(
      try failingStore.save(changed, at: Date(timeIntervalSince1970: 11_000))
    )
    XCTAssertEqual(try store.load()?.draft, original)
  }

  func testCorruptPrimaryRecoversLatestValidatedBackup() throws {
    let draft = makeNewDraft(deckHex: "1", itemHex: "2")
    let expected = try store.save(draft, at: Date(timeIntervalSince1970: 12_000))
    try Data("not-json".utf8).write(to: fileURL, options: .atomic)

    XCTAssertEqual(try store.load(), expected)
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: fileURL).path
      )
    )
  }

  func testFutureSchemaIsPreservedAndBlocksOlderWriter() throws {
    let original = makeNewDraft(deckHex: "1", itemHex: "2")
    try store.save(original, at: Date(timeIntervalSince1970: 13_000))
    var object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: fileURL)) as? [String: Any]
    )
    object["schema_version"] = 99
    let futureData = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    try futureData.write(to: fileURL, options: .atomic)
    try futureData.write(
      to: RecoverableJSONFile.backupURL(for: fileURL),
      options: .atomic
    )

    XCTAssertThrowsError(try store.load()) { error in
      XCTAssertEqual(error as? UserDeckDraftStoreError, .unsupportedSchema(99))
    }
    XCTAssertThrowsError(try store.save(original)) { error in
      XCTAssertEqual(error as? UserDeckDraftStoreError, .unsupportedSchema(99))
    }
    XCTAssertEqual(try Data(contentsOf: fileURL), futureData)
    XCTAssertFalse(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: fileURL).path
      )
    )
  }

  func testClearRequiresMatchingCommittedDraftID() throws {
    let active = try store.save(
      makeNewDraft(deckHex: "1", itemHex: "2"),
      at: Date(timeIntervalSince1970: 14_000)
    )

    XCTAssertFalse(try store.clear(draftID: "draft_\(String(repeating: "f", count: 32))"))
    XCTAssertNotNil(try store.load())
    XCTAssertTrue(try store.clear(draftID: active.draftID))
    XCTAssertNil(try store.load())
  }

  private var fileURL: URL {
    rootURL.appendingPathComponent("active-user-deck-draft.json")
  }

  private func makeNewDraft(deckHex: Character, itemHex: Character) -> UserDeckDraft {
    UserDeckDraft(
      newAt: Date(timeIntervalSince1970: 100),
      uuidHexGenerator: sequenceGenerator([
        String(repeating: String(deckHex), count: 32),
        String(repeating: String(itemHex), count: 32),
      ])
    )
  }

  private func makeDeck(id: String, version: Int, official: Bool) -> Deck {
    Deck(
      deckId: id,
      version: version,
      name: "単語",
      author: DeckAuthor(id: "author", nickname: "Piyo"),
      official: official,
      type: .word,
      level: 1,
      tags: ["daily"],
      createdAt: Date(timeIntervalSince1970: 100),
      updatedAt: Date(timeIntervalSince1970: 200),
      items: [
        DeckItem(
          id: "item_\(String(repeating: "8", count: 32))",
          ko: "안녕",
          readingJa: "アンニョン",
          meaningJa: "こんにちは",
          audio: nil
        )
      ]
    )
  }

  private func sequenceGenerator(_ values: [String]) -> () -> String {
    var iterator = values.makeIterator()
    return { iterator.next()! }
  }
}
