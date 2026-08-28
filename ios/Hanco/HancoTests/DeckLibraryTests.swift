import AVFoundation
import CryptoKit
import DeckKit
import Foundation
import XCTest

@testable import Hanco

@MainActor
final class DeckLibraryTests: XCTestCase {
  private var temporaryRoot: URL!
  private var store: DeckInstallationStore!

  override func setUpWithError() throws {
    try super.setUpWithError()
    temporaryRoot = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    store = DeckInstallationStore(rootURL: temporaryRoot)
  }

  override func tearDownWithError() throws {
    try? FileManager.default.removeItem(at: temporaryRoot)
    store = nil
    temporaryRoot = nil
    try super.tearDownWithError()
  }

  func testBundledMockSourceValidatesCatalogMetadataAndDeckItems() async throws {
    let entry = try catalogEntry(id: "official_keyboard_start")
    let payload = try await BundledMockDeckSource().fetch(entry)

    XCTAssertEqual(payload.deck.deckId, entry.deckId)
    XCTAssertEqual(payload.deck.items.count, entry.itemCount)
    XCTAssertEqual(payload.source, .bundle)
    XCTAssertTrue(DeckValidator.validate(payload.deck).isEmpty)
  }

  func testAllAppProvidedPronunciationsUseCanonicalDecodableBundledMP3() async throws {
    let catalog = try BundleCatalogRepository().loadCatalog()
    let source = BundledMockDeckSource()
    let resourceRoot = try XCTUnwrap(Bundle.main.resourceURL)
    var targets = Set<String>()

    for entry in catalog.decks where entry.official {
      let payload = try await source.fetch(entry)
      for item in payload.deck.items {
        let relativePath = try XCTUnwrap(item.audio, "\(entry.deckId):\(item.id)")
        XCTAssertEqual(
          relativePath,
          BundledPronunciationAudio.relativePath(for: item.ko),
          "\(entry.deckId):\(item.id)"
        )
        targets.insert(item.ko)
      }
    }
    XCTAssertEqual(targets.count, 292)

    for gameKind in GameKind.allCases {
      let presets = GamePresetDeckLoader.load(gameKind: gameKind)
      XCTAssertEqual(presets.count, 3, gameKind.rawValue)
      for preset in presets {
        for item in preset.deck.items {
          XCTAssertEqual(
            item.audio,
            BundledPronunciationAudio.relativePath(for: item.ko),
            "\(preset.deck.deckId):\(item.id)"
          )
          targets.insert(item.ko)
        }
      }
    }
    XCTAssertEqual(targets.count, 548)

    for stage in CurriculumCatalog.stages {
      for curriculumItem in stage.items {
        let item = curriculumItem.deckItem
        XCTAssertEqual(
          item.audio,
          BundledPronunciationAudio.relativePath(for: item.ko),
          "\(stage.id):\(item.id)"
        )
        targets.insert(item.ko)
      }
    }
    XCTAssertEqual(targets.count, 580)

    for index in 1...3 {
      targets.insert(
        AppLocalization.string("practice.sample_target_\(index)", language: .japanese)
      )
    }
    XCTAssertEqual(targets.count, 581)

    for target in targets.sorted() {
      let audioURL = try XCTUnwrap(
        BundledPronunciationAudio.url(for: target),
        target
      )
      XCTAssertEqual(audioURL.pathExtension, "mp3", target)
      XCTAssertTrue(audioURL.path.hasPrefix(resourceRoot.path + "/"), target)
      let player = try AVAudioPlayer(contentsOf: audioURL)
      XCTAssertGreaterThan(player.duration, 0, target)
    }

    let bundledAudioURLs = try FileManager.default.contentsOfDirectory(
      at: resourceRoot.appendingPathComponent("audio", isDirectory: true),
      includingPropertiesForKeys: nil
    )
    XCTAssertEqual(bundledAudioURLs.filter { $0.pathExtension == "mp3" }.count, 581)
    XCTAssertTrue(bundledAudioURLs.allSatisfy { $0.pathExtension != "caf" })
  }

  func testTrendingMeaningsAreShortDirectTranslations() async throws {
    let entry = try catalogEntry(id: "official_trending_korean")
    let deck = try await BundledMockDeckSource().fetch(entry).deck

    XCTAssertEqual(deck.items.first(where: { $0.ko == "저점매수" })?.meaningJa, "底値買い")
    XCTAssertTrue(deck.items.allSatisfy { $0.meaningJa.count <= 12 })
  }

  func testJapaneseMeaningDisplayUsesPlainCopyWithoutDecorativeKakko() {
    XCTAssertEqual(JapaneseMeaningDisplayText.format("底値買い"), "底値買い")
    XCTAssertEqual(JapaneseMeaningDisplayText.format(" 「会社」 \n"), "会社")
    XCTAssertEqual(JapaneseMeaningDisplayText.format("『何様？』"), "何様？")
  }

  func testInstallReloadAndRemovePersistFullDeckForOfflineUse() async throws {
    let entry = try catalogEntry(id: "official_daily_words")
    let payload = try await BundledMockDeckSource().fetch(entry)

    let record = try store.install(
      data: payload.data,
      source: payload.source,
      now: Date(timeIntervalSince1970: 100)
    )
    XCTAssertEqual(record.source, .bundle)

    let reloaded = try store.loadSnapshot()
    XCTAssertEqual(reloaded.decks[entry.deckId]?.items.first?.ko, payload.deck.items.first?.ko)
    XCTAssertEqual(reloaded.records[entry.deckId]?.installedAt, Date(timeIntervalSince1970: 100))
    XCTAssertEqual(reloaded.downloadHistory[entry.deckId], entry.tags)

    try store.remove(deckId: entry.deckId)
    let removed = try store.loadSnapshot()
    XCTAssertTrue(removed.decks.isEmpty)
    XCTAssertEqual(removed.downloadHistory[entry.deckId], entry.tags)
  }

  func testUnsupportedInstallationIndexSchemaIsRejected() throws {
    try FileManager.default.createDirectory(
      at: temporaryRoot,
      withIntermediateDirectories: true
    )
    let indexData = try JSONSerialization.data(
      withJSONObject: ["schema_version": 999, "records": []]
    )
    try indexData.write(
      to: temporaryRoot.appendingPathComponent("installed-decks.json")
    )

    XCTAssertThrowsError(try store.loadSnapshot()) { error in
      guard case DeckInstallationStoreError.unsupportedSchema(999) = error else {
        return XCTFail("Unexpected error: \(error)")
      }
    }
  }

  func testLegacyIndexWithoutDownloadHistoryBackfillsInstalledDeckTags() async throws {
    let entry = try catalogEntry(id: "official_keyboard_start")
    let payload = try await BundledMockDeckSource().fetch(entry)
    _ = try store.install(data: payload.data, source: .bundle)

    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    var object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: indexURL)) as? [String: Any]
    )
    object.removeValue(forKey: "download_history")
    try JSONSerialization.data(withJSONObject: object).write(to: indexURL)

    XCTAssertEqual(try store.loadSnapshot().downloadHistory[entry.deckId], entry.tags)
  }

  func testSchemaOneRecordMigratesWithoutUserDeckMetadata() async throws {
    let entry = try catalogEntry(id: "official_keyboard_start")
    let payload = try await BundledMockDeckSource().fetch(entry)
    _ = try store.install(data: payload.data, source: .bundle)

    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    var object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: indexURL)) as? [String: Any]
    )
    object["schema_version"] = 1
    var records = try XCTUnwrap(object["records"] as? [[String: Any]])
    records[0].removeValue(forKey: "content_sha256")
    records[0].removeValue(forKey: "is_locally_modified")
    object["records"] = records
    try JSONSerialization.data(withJSONObject: object).write(to: indexURL)

    let migrated = try store.loadSnapshot()

    XCTAssertNil(migrated.records[entry.deckId]?.contentSHA256)
    XCTAssertEqual(migrated.records[entry.deckId]?.isLocallyModified, false)
  }

  func testReplacingUserDeckPreservesInstallAndPlayDatesAndPersistsDocumentMetadata()
    throws
  {
    let createdAt = Date(timeIntervalSince1970: 100)
    let first = makeUserDeck(version: 1, meaning: "最初", updatedAt: createdAt)
    let firstData = try DeckKitJSON.makeEncoder().encode(first)
    _ = try store.install(
      data: firstData,
      source: .imported,
      contentSHA256: sha256Hex(firstData),
      derivedFromDeckId: "official_keyboard_start",
      now: createdAt
    )
    let playedAt = Date(timeIntervalSince1970: 150)
    _ = try store.markPlayed(deckId: first.deckId, at: playedAt)

    let second = makeUserDeck(
      version: 2,
      meaning: "更新",
      updatedAt: Date(timeIntervalSince1970: 200)
    )
    let secondData = try DeckKitJSON.makeEncoder().encode(second)
    let record = try store.install(
      data: secondData,
      source: .created,
      contentSHA256: sha256Hex(secondData),
      isLocallyModified: true,
      now: Date(timeIntervalSince1970: 200)
    )

    XCTAssertEqual(record.installedAt, createdAt)
    XCTAssertEqual(record.lastPlayedAt, playedAt)
    XCTAssertEqual(record.contentSHA256, sha256Hex(secondData))
    XCTAssertTrue(record.isLocallyModified)
    XCTAssertEqual(record.source, .created)
    XCTAssertEqual(record.derivedFromDeckId, "official_keyboard_start")
    XCTAssertEqual(try store.loadSnapshot().decks[first.deckId]?.items[0].meaningJa, "更新")
    XCTAssertEqual(try store.data(for: first.deckId), secondData)
  }

  func testInstallWithMatchingExpectedCurrentVersionCommitsReplacement() throws {
    let first = makeUserDeck(
      version: 1,
      meaning: "最初",
      updatedAt: Date(timeIntervalSince1970: 100)
    )
    let firstData = try DeckKitJSON.makeEncoder().encode(first)
    _ = try store.install(
      data: firstData,
      source: .created,
      contentSHA256: sha256Hex(firstData),
      packageFormatVersion: 1
    )
    let second = makeUserDeck(
      version: 2,
      meaning: "更新",
      updatedAt: Date(timeIntervalSince1970: 200)
    )
    let secondData = try DeckKitJSON.makeEncoder().encode(second)

    let installed = try store.install(
      data: secondData,
      source: .created,
      contentSHA256: sha256Hex(secondData),
      packageFormatVersion: 1,
      isLocallyModified: true,
      expectedCurrentVersion: 1
    )

    XCTAssertEqual(installed.version, 2)
    XCTAssertEqual(try store.data(for: first.deckId), secondData)
    XCTAssertEqual(try store.loadSnapshot().decks[first.deckId]?.items[0].meaningJa, "更新")
    XCTAssertFalse(
      FileManager.default.fileExists(
        atPath: temporaryRoot.appendingPathComponent("PendingTransaction").path
      )
    )
  }

  func testInstallWithMismatchedExpectedCurrentVersionDoesNotMutateStore() throws {
    let first = makeUserDeck(
      version: 1,
      meaning: "最初",
      updatedAt: Date(timeIntervalSince1970: 100)
    )
    let firstData = try DeckKitJSON.makeEncoder().encode(first)
    _ = try store.install(
      data: firstData,
      source: .created,
      contentSHA256: sha256Hex(firstData),
      packageFormatVersion: 1
    )
    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    let deckURL = temporaryRoot.appendingPathComponent("\(first.deckId).json")
    let indexBefore = try Data(contentsOf: indexURL)
    let deckBefore = try Data(contentsOf: deckURL)
    let second = makeUserDeck(
      version: 2,
      meaning: "更新",
      updatedAt: Date(timeIntervalSince1970: 200)
    )
    let secondData = try DeckKitJSON.makeEncoder().encode(second)

    XCTAssertThrowsError(
      try store.install(
        data: secondData,
        source: .created,
        contentSHA256: sha256Hex(secondData),
        packageFormatVersion: 1,
        isLocallyModified: true,
        expectedCurrentVersion: 9
      )
    ) { error in
      guard
        let storeError = error as? DeckInstallationStoreError,
        case .sourceChanged(let deckId, let expectedVersion, let actualVersion) = storeError
      else {
        return XCTFail("Unexpected error: \(error)")
      }
      XCTAssertEqual(deckId, first.deckId)
      XCTAssertEqual(expectedVersion, 9)
      XCTAssertEqual(actualVersion, 1)
    }

    XCTAssertEqual(try Data(contentsOf: indexURL), indexBefore)
    XCTAssertEqual(try Data(contentsOf: deckURL), deckBefore)
    XCTAssertEqual(try store.loadSnapshot().records[first.deckId]?.version, 1)
    XCTAssertFalse(
      FileManager.default.fileExists(
        atPath: temporaryRoot.appendingPathComponent("PendingTransaction").path
      )
    )
  }

  func testExpectedVersionComparisonRunsAfterPendingTransactionRecovery() throws {
    let first = makeUserDeck(
      version: 1,
      meaning: "最初",
      updatedAt: Date(timeIntervalSince1970: 100)
    )
    let firstData = try DeckKitJSON.makeEncoder().encode(first)
    _ = try store.install(
      data: firstData,
      source: .created,
      contentSHA256: sha256Hex(firstData),
      packageFormatVersion: 1
    )
    let second = makeUserDeck(
      version: 2,
      meaning: "先行更新",
      updatedAt: Date(timeIntervalSince1970: 200)
    )
    let secondData = try DeckKitJSON.makeEncoder().encode(second)
    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    var index = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: indexURL)) as? [String: Any]
    )
    var records = try XCTUnwrap(index["records"] as? [[String: Any]])
    records[0]["version"] = 2
    records[0]["content_sha256"] = sha256Hex(secondData)
    index["records"] = records
    let transactionURL = temporaryRoot.appendingPathComponent(
      "PendingTransaction",
      isDirectory: true
    )
    try FileManager.default.createDirectory(at: transactionURL, withIntermediateDirectories: true)
    try JSONSerialization.data(withJSONObject: index).write(
      to: transactionURL.appendingPathComponent("index.json")
    )
    try secondData.write(to: transactionURL.appendingPathComponent("deck.json"))
    try JSONSerialization.data(
      withJSONObject: [
        "schema_version": 1,
        "operation": "install",
        "deck_id": first.deckId,
      ]
    ).write(to: transactionURL.appendingPathComponent("transaction.json"))
    let third = makeUserDeck(
      version: 3,
      meaning: "競合更新",
      updatedAt: Date(timeIntervalSince1970: 300)
    )
    let thirdData = try DeckKitJSON.makeEncoder().encode(third)

    XCTAssertThrowsError(
      try store.install(
        data: thirdData,
        source: .created,
        contentSHA256: sha256Hex(thirdData),
        packageFormatVersion: 1,
        isLocallyModified: true,
        expectedCurrentVersion: 1
      )
    ) { error in
      guard
        let storeError = error as? DeckInstallationStoreError,
        case .sourceChanged(_, let expectedVersion, let actualVersion) = storeError
      else {
        return XCTFail("Unexpected error: \(error)")
      }
      XCTAssertEqual(expectedVersion, 1)
      XCTAssertEqual(actualVersion, 2)
    }

    let recovered = try store.loadSnapshot()
    XCTAssertEqual(recovered.records[first.deckId]?.version, 2)
    XCTAssertEqual(recovered.decks[first.deckId]?.items[0].meaningJa, "先行更新")
    XCTAssertFalse(FileManager.default.fileExists(atPath: transactionURL.path))
  }

  func testDeletingAndReimportingUserDeckReconnectsInstallationHistory() throws {
    let installedAt = Date(timeIntervalSince1970: 100)
    let playedAt = Date(timeIntervalSince1970: 150)
    let deck = makeUserDeck(version: 1, meaning: "文字", updatedAt: installedAt)
    let data = try DeckKitJSON.makeEncoder().encode(deck)
    _ = try store.install(
      data: data,
      source: .imported,
      contentSHA256: sha256Hex(data),
      packageFormatVersion: 1,
      derivedFromDeckId: "official_keyboard_start",
      now: installedAt
    )
    _ = try store.markPlayed(deckId: deck.deckId, at: playedAt)

    try store.remove(deckId: deck.deckId)
    let removed = try store.loadSnapshot()
    XCTAssertNil(removed.records[deck.deckId])
    XCTAssertEqual(removed.removedRecords[deck.deckId]?.lastPlayedAt, playedAt)

    let restored = try store.install(
      data: data,
      source: .imported,
      contentSHA256: sha256Hex(data),
      packageFormatVersion: 1,
      now: Date(timeIntervalSince1970: 500)
    )
    XCTAssertEqual(restored.installedAt, installedAt)
    XCTAssertEqual(restored.lastPlayedAt, playedAt)
    XCTAssertEqual(restored.derivedFromDeckId, "official_keyboard_start")
    XCTAssertNil(try store.loadSnapshot().removedRecords[deck.deckId])
  }

  func testInterruptedInstallTransactionCompletesPayloadAndIndexTogetherOnLoad() throws {
    let first = makeUserDeck(
      version: 1,
      meaning: "最初",
      updatedAt: Date(timeIntervalSince1970: 100)
    )
    let firstData = try DeckKitJSON.makeEncoder().encode(first)
    _ = try store.install(
      data: firstData,
      source: .imported,
      contentSHA256: sha256Hex(firstData),
      packageFormatVersion: 1
    )

    let second = makeUserDeck(
      version: 2,
      meaning: "更新",
      updatedAt: Date(timeIntervalSince1970: 200)
    )
    let secondData = try DeckKitJSON.makeEncoder().encode(second)
    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    var index = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: indexURL)) as? [String: Any]
    )
    var records = try XCTUnwrap(index["records"] as? [[String: Any]])
    records[0]["version"] = 2
    records[0]["content_sha256"] = sha256Hex(secondData)
    index["records"] = records

    let transactionURL = temporaryRoot.appendingPathComponent(
      "PendingTransaction",
      isDirectory: true
    )
    try FileManager.default.createDirectory(
      at: transactionURL,
      withIntermediateDirectories: true
    )
    try JSONSerialization.data(withJSONObject: index).write(
      to: transactionURL.appendingPathComponent("index.json")
    )
    try secondData.write(to: transactionURL.appendingPathComponent("deck.json"))
    try JSONSerialization.data(
      withJSONObject: [
        "schema_version": 1,
        "operation": "install",
        "deck_id": first.deckId,
      ]
    ).write(to: transactionURL.appendingPathComponent("transaction.json"))

    // Simulate termination after the live payload swap but before the index swap.
    try secondData.write(
      to: temporaryRoot.appendingPathComponent("\(first.deckId).json")
    )
    let recovered = try store.loadSnapshot()

    XCTAssertEqual(recovered.records[first.deckId]?.version, 2)
    XCTAssertEqual(recovered.decks[first.deckId]?.items[0].meaningJa, "更新")
    XCTAssertFalse(FileManager.default.fileExists(atPath: transactionURL.path))
  }

  func testPayloadHashMismatchIsQuarantinedInsteadOfLoaded() throws {
    let deck = makeUserDeck(
      version: 1,
      meaning: "文字",
      updatedAt: Date(timeIntervalSince1970: 100)
    )
    let data = try DeckKitJSON.makeEncoder().encode(deck)
    _ = try store.install(
      data: data,
      source: .imported,
      contentSHA256: sha256Hex(data),
      packageFormatVersion: 1
    )

    var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    object["name"] = "改ざんされたデッキ"
    let tampered = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    let deckURL = temporaryRoot.appendingPathComponent("\(deck.deckId).json")
    try tampered.write(to: deckURL)
    try tampered.write(to: RecoverableJSONFile.backupURL(for: deckURL))

    let snapshot = try store.loadSnapshot()

    XCTAssertNil(snapshot.records[deck.deckId])
    XCTAssertNil(snapshot.decks[deck.deckId])
    XCTAssertEqual(snapshot.removedRecords[deck.deckId]?.contentSHA256, sha256Hex(data))
  }

  func testInstallRejectsContentHashThatDoesNotMatchPayload() throws {
    let deck = makeUserDeck(
      version: 1,
      meaning: "文字",
      updatedAt: Date(timeIntervalSince1970: 100)
    )
    let data = try DeckKitJSON.makeEncoder().encode(deck)

    XCTAssertThrowsError(
      try store.install(
        data: data,
        source: .imported,
        contentSHA256: String(repeating: "0", count: 64),
        packageFormatVersion: 1
      )
    )
    XCTAssertFalse(FileManager.default.fileExists(atPath: temporaryRoot.path))
  }

  func testCorruptIndexRestoresInstalledDeckFromBackup() async throws {
    let entry = try catalogEntry(id: "official_keyboard_start")
    let payload = try await BundledMockDeckSource().fetch(entry)
    _ = try store.install(data: payload.data, source: .bundle)
    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    try Data("not-json".utf8).write(to: indexURL)

    let recovered = try store.loadSnapshot()

    XCTAssertEqual(recovered.decks[entry.deckId]?.items.count, entry.itemCount)
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: indexURL).path
      )
    )
  }

  func testOneUnrecoverableDeckIsQuarantinedWithoutClearingOtherDecksOrHistory()
    async throws
  {
    let goodEntry = try catalogEntry(id: "official_keyboard_start")
    let brokenEntry = try catalogEntry(id: "official_daily_words")
    let source = BundledMockDeckSource()
    let goodPayload = try await source.fetch(goodEntry)
    let brokenPayload = try await source.fetch(brokenEntry)
    _ = try store.install(data: goodPayload.data, source: .bundle)
    _ = try store.install(data: brokenPayload.data, source: .bundle)

    let brokenURL = temporaryRoot.appendingPathComponent("\(brokenEntry.deckId).json")
    try Data("broken-primary".utf8).write(to: brokenURL)
    try Data("broken-backup".utf8).write(
      to: RecoverableJSONFile.backupURL(for: brokenURL)
    )

    let recovered = try store.loadSnapshot()
    let reloaded = try store.loadSnapshot()

    XCTAssertNotNil(recovered.decks[goodEntry.deckId])
    XCTAssertNil(recovered.decks[brokenEntry.deckId])
    XCTAssertEqual(recovered.downloadHistory[brokenEntry.deckId], brokenEntry.tags)
    XCTAssertEqual(reloaded.decks.keys.sorted(), [goodEntry.deckId])
  }

  func testMarkPlayedUpdatesOnlyIndexWithoutRevalidatingEveryDeckPayload() async throws {
    let playedEntry = try catalogEntry(id: "official_keyboard_start")
    let unrelatedEntry = try catalogEntry(id: "official_daily_words")
    let source = BundledMockDeckSource()
    _ = try store.install(data: try await source.fetch(playedEntry).data, source: .bundle)
    _ = try store.install(data: try await source.fetch(unrelatedEntry).data, source: .bundle)
    let unrelatedURL = temporaryRoot.appendingPathComponent("\(unrelatedEntry.deckId).json")
    try Data("broken-primary".utf8).write(to: unrelatedURL)
    try Data("broken-backup".utf8).write(
      to: RecoverableJSONFile.backupURL(for: unrelatedURL)
    )
    let playedAt = Date(timeIntervalSince1970: 2_000)

    let updated = try store.markPlayed(deckId: playedEntry.deckId, at: playedAt)

    XCTAssertEqual(updated?.lastPlayedAt, playedAt)
    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    let object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: indexURL)) as? [String: Any]
    )
    let records = try XCTUnwrap(object["records"] as? [[String: Any]])
    XCTAssertEqual(records.count, 2)
    XCTAssertTrue(FileManager.default.fileExists(atPath: unrelatedURL.path))
  }

  func testLibraryReloadsInstalledDeckWithoutCallingSourceAndDetectsUpdate() async throws {
    let entry = try catalogEntry(id: "official_keyboard_start")
    let library = DeckLibrary(source: BundledMockDeckSource(), store: store)
    await library.install(entry)
    XCTAssertTrue(library.isInstalled(entry.deckId))

    let offlineLibrary = DeckLibrary(source: FailingDeckSource(), store: store)
    XCTAssertEqual(offlineLibrary.installedDeck(entry.deckId)?.items.count, entry.itemCount)

    let newer = CatalogDeck(
      deckId: entry.deckId,
      version: entry.version + 1,
      name: entry.name,
      authorNickname: entry.authorNickname,
      official: entry.official,
      featured: entry.featured,
      type: entry.type,
      level: entry.level,
      tags: entry.tags,
      itemCount: entry.itemCount,
      sizeBytes: entry.sizeBytes,
      downloadsTotal: entry.downloadsTotal,
      downloads7d: entry.downloads7d,
      createdAt: entry.createdAt,
      previewItems: entry.previewItems,
      fileUrl: entry.fileUrl
    )
    XCTAssertTrue(offlineLibrary.needsUpdate(newer))
  }

  func testLibraryInstallUserDeckAppliesExpectedVersionAndKeepsStateOnMismatch() async throws {
    let library = DeckLibrary(source: FailingDeckSource(), store: store)
    let first = makeUserDeck(
      version: 1,
      meaning: "最初",
      updatedAt: Date(timeIntervalSince1970: 100)
    )
    let firstData = try DeckKitJSON.makeEncoder().encode(first)
    _ = try await library.installUserDeck(
      data: firstData,
      source: .created,
      contentSHA256: sha256Hex(firstData),
      packageFormatVersion: 1,
      isLocallyModified: true,
      hasPiyokeyProAccess: true
    )
    let second = makeUserDeck(
      version: 2,
      meaning: "更新",
      updatedAt: Date(timeIntervalSince1970: 200)
    )
    let secondData = try DeckKitJSON.makeEncoder().encode(second)
    _ = try await library.installUserDeck(
      data: secondData,
      source: .created,
      contentSHA256: sha256Hex(secondData),
      packageFormatVersion: 1,
      isLocallyModified: true,
      hasPiyokeyProAccess: true,
      expectedCurrentVersion: 1
    )
    let indexURL = temporaryRoot.appendingPathComponent("installed-decks.json")
    let indexBeforeConflict = try Data(contentsOf: indexURL)
    let third = makeUserDeck(
      version: 3,
      meaning: "競合更新",
      updatedAt: Date(timeIntervalSince1970: 300)
    )
    let thirdData = try DeckKitJSON.makeEncoder().encode(third)

    do {
      _ = try await library.installUserDeck(
        data: thirdData,
        source: .created,
        contentSHA256: sha256Hex(thirdData),
        packageFormatVersion: 1,
        isLocallyModified: true,
        hasPiyokeyProAccess: true,
        expectedCurrentVersion: 1
      )
      XCTFail("Expected sourceChanged")
    } catch let storeError as DeckInstallationStoreError {
      guard case .sourceChanged(_, let expectedVersion, let actualVersion) = storeError else {
        return XCTFail("Unexpected error: \(storeError)")
      }
      XCTAssertEqual(expectedVersion, 1)
      XCTAssertEqual(actualVersion, 2)
    }

    XCTAssertEqual(library.installedDeck(first.deckId)?.version, 2)
    XCTAssertEqual(library.installedDeck(first.deckId)?.items[0].meaningJa, "更新")
    XCTAssertEqual(try Data(contentsOf: indexURL), indexBeforeConflict)
    XCTAssertEqual(try store.data(for: first.deckId), secondData)
  }

  func testFreeUserDeckLimitBlocksOnlyFourthNewDeckAndAllowsReplacement() async throws {
    let library = DeckLibrary(source: FailingDeckSource(), store: store)
    for index in 1...3 {
      let deck = makeUserDeck(
        id: String(format: "user_%032x", index),
        version: 1,
        meaning: "無料\(index)",
        updatedAt: Date(timeIntervalSince1970: TimeInterval(100 + index))
      )
      let data = try DeckKitJSON.makeEncoder().encode(deck)
      _ = try await library.installUserDeck(
        data: data,
        source: .imported,
        contentSHA256: sha256Hex(data),
        packageFormatVersion: 1,
        isLocallyModified: false,
        hasPiyokeyProAccess: false
      )
    }

    XCTAssertEqual(library.installedUserDeckCount, 3)
    XCTAssertFalse(library.canInstallNewUserDeck(hasPiyokeyProAccess: false))
    XCTAssertTrue(library.canInstallNewUserDeck(hasPiyokeyProAccess: true))

    let fourth = makeUserDeck(
      id: "user_ffffffffffffffffffffffffffffffff",
      version: 1,
      meaning: "四つ目",
      updatedAt: Date(timeIntervalSince1970: 104)
    )
    let fourthData = try DeckKitJSON.makeEncoder().encode(fourth)
    do {
      _ = try await library.installUserDeck(
        data: fourthData,
        source: .imported,
        contentSHA256: sha256Hex(fourthData),
        packageFormatVersion: 1,
        isLocallyModified: false,
        hasPiyokeyProAccess: false
      )
      XCTFail("Expected the free user-deck limit")
    } catch let error as PiyokeyProAccessError {
      XCTAssertEqual(error, .freeUserDeckLimitReached)
    }

    let replacement = makeUserDeck(
      id: "user_00000000000000000000000000000001",
      version: 2,
      meaning: "無料置換",
      updatedAt: Date(timeIntervalSince1970: 105)
    )
    let replacementData = try DeckKitJSON.makeEncoder().encode(replacement)
    _ = try await library.installUserDeck(
      data: replacementData,
      source: .imported,
      contentSHA256: sha256Hex(replacementData),
      packageFormatVersion: 1,
      isLocallyModified: false,
      hasPiyokeyProAccess: false
    )
    XCTAssertEqual(library.installedDeck(replacement.deckId)?.version, 2)

    _ = try await library.installUserDeck(
      data: fourthData,
      source: .imported,
      contentSHA256: sha256Hex(fourthData),
      packageFormatVersion: 1,
      isLocallyModified: false,
      hasPiyokeyProAccess: true
    )
    XCTAssertEqual(library.installedUserDeckCount, 4)
  }

  func testPiyokeyProAllowsManyDistinctUserDecksAndReloadsThem() async throws {
    let library = DeckLibrary(source: FailingDeckSource(), store: store)
    let deckCount = 20

    for index in 1...deckCount {
      let deck = makeUserDeck(
        id: String(format: "user_%032x", index),
        version: 1,
        meaning: "Pro\(index)",
        updatedAt: Date(timeIntervalSince1970: TimeInterval(200 + index))
      )
      let data = try DeckKitJSON.makeEncoder().encode(deck)
      _ = try await library.installUserDeck(
        data: data,
        source: .created,
        contentSHA256: sha256Hex(data),
        packageFormatVersion: 1,
        isLocallyModified: true,
        hasPiyokeyProAccess: true
      )
    }

    XCTAssertEqual(library.installedUserDeckCount, deckCount)
    XCTAssertTrue(library.canInstallNewUserDeck(hasPiyokeyProAccess: true))

    let reloaded = DeckLibrary(source: FailingDeckSource(), store: store)
    XCTAssertEqual(reloaded.installedUserDeckCount, deckCount)
    for index in 1...deckCount {
      let deckID = String(format: "user_%032x", index)
      XCTAssertEqual(reloaded.installedDeck(deckID)?.items[0].meaningJa, "Pro\(index)")
    }
  }

  func testPiyokeyProEditedDeckPersistsContentAndIdentityAfterReload() async throws {
    let library = DeckLibrary(source: FailingDeckSource(), store: store)
    let original = makeUserDeck(
      version: 1,
      meaning: "編集前",
      updatedAt: Date(timeIntervalSince1970: 200)
    )
    let originalData = try DeckKitJSON.makeEncoder().encode(original)
    _ = try await library.installUserDeck(
      data: originalData,
      source: .created,
      contentSHA256: sha256Hex(originalData),
      packageFormatVersion: 1,
      isLocallyModified: true,
      hasPiyokeyProAccess: true
    )

    var draft = UserDeckDraft(editing: original)
    draft.name = "保存済みProデッキ"
    draft.items[0].meaningJa = "編集後"
    draft.addItem(uuidHexGenerator: { "22222222222222222222222222222222" })
    draft.items[1].ko = "학교"
    draft.items[1].readingJa = "ハッキョ"
    draft.items[1].meaningJa = "学校"
    let edited = try draft.validatedDeck(
      at: Date(timeIntervalSince1970: 300),
      language: .japanese
    )
    let editedData = try DeckKitJSON.makeEncoder().encode(edited)

    _ = try await library.installUserDeck(
      data: editedData,
      source: .created,
      contentSHA256: sha256Hex(editedData),
      packageFormatVersion: 1,
      isLocallyModified: true,
      hasPiyokeyProAccess: true,
      expectedCurrentVersion: original.version
    )

    let reloaded = DeckLibrary(source: FailingDeckSource(), store: store)
    let persisted = try XCTUnwrap(reloaded.installedDeck(original.deckId))
    XCTAssertEqual(persisted.deckId, original.deckId)
    XCTAssertEqual(persisted.version, 2)
    XCTAssertEqual(persisted.name, "保存済みProデッキ")
    XCTAssertEqual(persisted.items.map(\.id), [
      original.items[0].id,
      "item_22222222222222222222222222222222",
    ])
    XCTAssertEqual(persisted.items.map(\.meaningJa), ["編集後", "学校"])
    XCTAssertTrue(reloaded.records[original.deckId]?.isLocallyModified == true)
  }

  private func catalogEntry(id: String) throws -> CatalogDeck {
    let catalog = try BundleCatalogRepository().loadCatalog()
    return try XCTUnwrap(catalog.decks.first { $0.deckId == id })
  }

  private func makeUserDeck(
    id: String = "user_0123456789abcdef0123456789abcdef",
    version: Int,
    meaning: String,
    updatedAt: Date
  ) -> Deck {
    Deck(
      deckId: id,
      version: version,
      name: "私のデッキ",
      author: DeckAuthor(id: "user_local", nickname: "Learner"),
      official: false,
      type: .word,
      level: 1,
      tags: ["個人"],
      createdAt: Date(timeIntervalSince1970: 100),
      updatedAt: updatedAt,
      items: [
        DeckItem(
          id: "item_0123456789abcdef0123456789abcdef",
          ko: "한글",
          readingJa: "ハングル",
          meaningJa: meaning,
          audio: nil
        )
      ]
    )
  }

  private func sha256Hex(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
  }
}

private struct FailingDeckSource: DeckSource {
  struct OfflineError: Error {}

  func fetch(_ catalogDeck: CatalogDeck) async throws -> DeckDownloadPayload {
    throw OfflineError()
  }
}
