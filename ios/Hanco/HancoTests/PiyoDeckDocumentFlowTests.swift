import DeckKit
import Foundation
import XCTest

@testable import Hanco

final class PiyoDeckDocumentFlowTests: XCTestCase {
  func testCollisionUsesContentHashBeforeVersion() throws {
    let package = try makePackage(version: 2, meaning: "更新")
    let candidate = PiyoDeckImportCandidate(
      package: package,
      stagedURL: URL(fileURLWithPath: "/tmp/unused.piyodeck")
    )

    XCTAssertEqual(candidate.collision(with: nil), .new)
    XCTAssertEqual(
      candidate.collision(
        with: makeRecord(version: 1, hash: package.contentSHA256)
      ),
      .identical
    )
    XCTAssertEqual(
      candidate.collision(with: makeRecord(version: 3, hash: String(repeating: "0", count: 64))),
      .different(existingVersion: 3, incomingVersion: 2)
    )
    XCTAssertTrue(
      candidate
        .collision(with: makeRecord(version: 3, hash: String(repeating: "0", count: 64)))
        .isDowngrade
    )
    XCTAssertFalse(
      candidate
        .collision(with: makeRecord(version: 3, hash: String(repeating: "0", count: 64)))
        .isSameVersionConflict
    )
    XCTAssertEqual(
      candidate.collision(
        with: makeRecord(version: 3, hash: String(repeating: "0", count: 64)),
        installedDeck: package.deck
      ),
      .identical
    )
  }

  func testCollisionRecognizesSameVersionWithDifferentContent() throws {
    let package = try makePackage(version: 2, meaning: "更新")
    let collision = PiyoDeckImportCandidate(
      package: package,
      stagedURL: URL(fileURLWithPath: "/tmp/unused.piyodeck")
    ).collision(with: makeRecord(version: 2, hash: String(repeating: "0", count: 64)))

    XCTAssertEqual(collision, .different(existingVersion: 2, incomingVersion: 2))
    XCTAssertFalse(collision.isDowngrade)
    XCTAssertTrue(collision.isSameVersionConflict)
  }

  func testCollisionComparisonIncludesAllUserDecisionFields() {
    let current = makeDeck(
      version: 5,
      name: "現在のデッキ",
      updatedAt: Date(timeIntervalSince1970: 500),
      itemCount: 2
    )
    let incoming = makeDeck(
      version: 4,
      name: "読み込むデッキ",
      updatedAt: Date(timeIntervalSince1970: 700),
      itemCount: 3
    )

    let comparison = PiyoDeckCollisionComparison(
      currentDeck: current,
      incomingDeck: incoming
    )

    XCTAssertEqual(comparison.current.name, "現在のデッキ")
    XCTAssertEqual(comparison.current.version, 5)
    XCTAssertEqual(comparison.current.updatedAt, Date(timeIntervalSince1970: 500))
    XCTAssertEqual(comparison.current.itemCount, 2)
    XCTAssertEqual(comparison.incoming.name, "読み込むデッキ")
    XCTAssertEqual(comparison.incoming.version, 4)
    XCTAssertEqual(comparison.incoming.updatedAt, Date(timeIntervalSince1970: 700))
    XCTAssertEqual(comparison.incoming.itemCount, 3)
  }

  @MainActor
  func testRepeatedSheetDismissCallbackDoesNotSkipQueuedDocument() async throws {
    let schema = try PiyoDeckDocumentService.deckSchemaData()
    let first = try PiyoDeckPackageWriter.write(
      deck: makeDeck(
        id: "user_11111111111111111111111111111111",
        itemID: "item_11111111111111111111111111111111"
      ),
      deckSchemaData: schema
    )
    let second = try PiyoDeckPackageWriter.write(
      deck: makeDeck(
        id: "user_22222222222222222222222222222222",
        itemID: "item_22222222222222222222222222222222"
      ),
      deckSchemaData: schema
    )
    let directory = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: directory) }
    let firstURL = directory.appendingPathComponent("first.piyodeck")
    let secondURL = directory.appendingPathComponent("second.piyodeck")
    try first.write(to: firstURL)
    try second.write(to: secondURL)

    let coordinator = PiyoDeckDocumentCoordinator()
    await coordinator.receive(firstURL)
    await coordinator.receive(secondURL)
    XCTAssertEqual(
      coordinator.candidate?.package.deck.deckId,
      "user_11111111111111111111111111111111"
    )

    coordinator.markAlreadyImported()
    coordinator.dismissCandidate()  // Mirrors SwiftUI's second binding callback.
    XCTAssertNil(coordinator.candidate)
    XCTAssertEqual(coordinator.notice, .alreadyImported)
    coordinator.dismissNotice()
    await waitForCandidate(in: coordinator)

    XCTAssertEqual(
      coordinator.candidate?.package.deck.deckId,
      "user_22222222222222222222222222222222"
    )
    coordinator.dismissCandidate(showNext: false)
  }

  @MainActor
  func testInvalidPendingDocumentContinuesWithNextAfterNoticeDismissal() async throws {
    let schema = try PiyoDeckDocumentService.deckSchemaData()
    let valid = try PiyoDeckPackageWriter.write(deck: makeDeck(), deckSchemaData: schema)
    let directory = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: directory) }
    try Data("not-a-package".utf8).write(
      to: directory.appendingPathComponent("01-invalid.piyodeck")
    )
    try valid.write(to: directory.appendingPathComponent("02-valid.piyodeck"))

    let coordinator = PiyoDeckDocumentCoordinator()
    await coordinator.resumePendingIfNeeded(pendingRootURL: directory)
    XCTAssertNil(coordinator.candidate)
    XCTAssertEqual(coordinator.notice, .invalidDocument)

    coordinator.dismissNotice()
    await waitForCandidate(in: coordinator)

    XCTAssertEqual(coordinator.candidate?.package.deck, makeDeck())
    coordinator.dismissCandidate(showNext: false)
  }

  @MainActor
  func testConcurrentOpenRequestsAreQueuedWithoutDroppingEitherDocument() async throws {
    let schema = try PiyoDeckDocumentService.deckSchemaData()
    let firstDeck = makeDeck(
      id: "user_33333333333333333333333333333333",
      itemID: "item_33333333333333333333333333333333"
    )
    let secondDeck = makeDeck(
      id: "user_44444444444444444444444444444444",
      itemID: "item_44444444444444444444444444444444"
    )
    let directory = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: directory) }
    let firstURL = directory.appendingPathComponent("first.piyodeck")
    let secondURL = directory.appendingPathComponent("second.piyodeck")
    try PiyoDeckPackageWriter.write(deck: firstDeck, deckSchemaData: schema).write(to: firstURL)
    try PiyoDeckPackageWriter.write(deck: secondDeck, deckSchemaData: schema).write(to: secondURL)

    let coordinator = PiyoDeckDocumentCoordinator()
    async let receiveFirst: Void = coordinator.receive(firstURL)
    async let receiveSecond: Void = coordinator.receive(secondURL)
    _ = await (receiveFirst, receiveSecond)

    await waitForCandidate(in: coordinator)
    let firstPresentedID = try XCTUnwrap(coordinator.candidate?.package.deck.deckId)
    coordinator.dismissCandidate()
    await waitForCandidate(in: coordinator)
    let secondPresentedID = try XCTUnwrap(coordinator.candidate?.package.deck.deckId)

    XCTAssertEqual(
      Set([firstPresentedID, secondPresentedID]),
      Set([firstDeck.deckId, secondDeck.deckId])
    )
    coordinator.dismissCandidate(showNext: false)
  }

  @MainActor
  private func waitForCandidate(
    in coordinator: PiyoDeckDocumentCoordinator,
    attempts: Int = 100
  ) async {
    for _ in 0..<attempts where coordinator.candidate == nil && coordinator.notice == nil {
      try? await Task.sleep(nanoseconds: 10_000_000)
    }
  }

  func testUserCopyGetsFreshIdentifiersAndTextOnlyItems() {
    let source = makeDeck(
      id: "official_daily_words",
      version: 9,
      official: true,
      itemID: "daily_001",
      audio: "audio/daily.caf"
    )

    let copy = PiyoDeckDocumentService.makeUserCopy(
      of: source,
      at: Date(timeIntervalSince1970: 500)
    )

    XCTAssertNotEqual(copy.deckId, source.deckId)
    XCTAssertNotEqual(copy.items[0].id, source.items[0].id)
    XCTAssertNotNil(copy.deckId.range(of: "^user_[0-9a-f]{32}$", options: .regularExpression))
    XCTAssertNotNil(copy.items[0].id.range(of: "^item_[0-9a-f]{32}$", options: .regularExpression))
    XCTAssertEqual(copy.version, 1)
    XCTAssertFalse(copy.official)
    XCTAssertNil(copy.items[0].audio)
    XCTAssertTrue(UserDeckValidator.validate(copy).isEmpty)
  }

  func testExportArtifactRoundTripsThroughReader() throws {
    let deck = makeDeck()
    let schema = try PiyoDeckDocumentService.deckSchemaData()

    let artifact = try PiyoDeckDocumentService.exportArtifact(for: deck, schemaData: schema)
    defer { try? FileManager.default.removeItem(at: artifact.directoryURL) }
    let imported = try PiyoDeckDocumentService.readPackage(
      at: artifact.url,
      schemaData: schema
    )

    XCTAssertEqual(artifact.url.pathExtension, "piyodeck")
    XCTAssertEqual(imported.deck, deck)
  }

  private func makePackage(version: Int, meaning: String) throws -> PiyoDeckPackage {
    let deck = makeDeck(version: version, meaning: meaning)
    let schema = try PiyoDeckDocumentService.deckSchemaData()
    return try PiyoDeckDocumentService.canonicalPackage(for: deck, schemaData: schema)
  }

  private func makeRecord(version: Int, hash: String) -> InstalledDeckRecord {
    InstalledDeckRecord(
      deckId: "user_0123456789abcdef0123456789abcdef",
      version: version,
      installedAt: Date(timeIntervalSince1970: 1),
      lastPlayedAt: nil,
      source: .imported,
      contentSHA256: hash,
      packageFormatVersion: 1
    )
  }

  private func makeDeck(
    id: String = "user_0123456789abcdef0123456789abcdef",
    version: Int = 1,
    name: String = "私のデッキ",
    official: Bool = false,
    itemID: String = "item_0123456789abcdef0123456789abcdef",
    audio: String? = nil,
    meaning: String = "文字",
    updatedAt: Date = Date(timeIntervalSince1970: 200),
    itemCount: Int = 1
  ) -> Deck {
    var items: [DeckItem] = []
    for index in 0..<itemCount {
      let generatedID = String(format: "item_%032x", index)
      let generatedKorean = "한글\(index)"
      let generatedReading = "ハングル\(index)"
      let generatedMeaning = "\(meaning)\(index)"
      items.append(
        DeckItem(
          id: index == 0 ? itemID : generatedID,
          ko: index == 0 ? "한글" : generatedKorean,
          readingJa: index == 0 ? "ハングル" : generatedReading,
          meaningJa: index == 0 ? meaning : generatedMeaning,
          audio: audio
        )
      )
    }
    return Deck(
      deckId: id,
      version: version,
      name: name,
      author: DeckAuthor(id: official ? "official_piyokey" : "user_local", nickname: "Piyo"),
      official: official,
      type: .word,
      level: 1,
      tags: ["custom"],
      createdAt: Date(timeIntervalSince1970: 100),
      updatedAt: updatedAt,
      items: items
    )
  }
}
