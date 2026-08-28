import Foundation
import XCTest

@testable import DeckKit

final class PiyoDeckPackageTests: XCTestCase {
  func testSharedFixturesPassSchemasAndPackageRoundTripsDeterministically() throws {
    let root = try repositoryRoot()
    let deckData = try fixture("valid/basic-deck.json", root: root)
    let manifestData = try fixture("valid/basic-manifest.json", root: root)
    let deckSchema = try schema("deck.schema.json", root: root)
    let manifestSchema = try schema("piyodeck-manifest-v1.schema.json", root: root)

    XCTAssertEqual(
      try JSONSchemaValidator.validate(instanceData: deckData, schemaData: deckSchema),
      []
    )
    XCTAssertEqual(
      try JSONSchemaValidator.validate(instanceData: manifestData, schemaData: manifestSchema),
      []
    )

    let fixtureManifest = try JSONDecoder().decode(PiyoDeckManifest.self, from: manifestData)
    XCTAssertEqual(fixtureManifest.deck.sizeBytes, deckData.count)
    XCTAssertEqual(fixtureManifest.deck.sha256, PiyoDeckDigest.sha256Hex(deckData))

    let deck = try DeckKitJSON.decodeDeck(from: deckData)
    XCTAssertEqual(UserDeckValidator.validate(deck), [])
    let package = try PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: deckSchema)
    XCTAssertEqual(
      package,
      try PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: deckSchema)
    )
    let crossPlatformGolden = try fixture("valid/basic.typedeck", root: root)
    XCTAssertEqual(package, crossPlatformGolden)

    let entries = try PiyoDeckZIP.read(package)
    XCTAssertEqual(Set(entries.keys), ["manifest.json", "deck.json"])
    let generatedManifest = try XCTUnwrap(entries["manifest.json"])
    let generatedDeck = try XCTUnwrap(entries["deck.json"])
    XCTAssertFalse(generatedManifest.contains(0x0A))
    XCTAssertFalse(generatedManifest.contains(0x0D))
    XCTAssertFalse(generatedDeck.contains(0x0A))
    XCTAssertFalse(generatedDeck.contains(0x0D))
    // Shared with tools/tests/test_piyodeck_tool.py to pin the canonical
    // bytes contract across the Swift and Python writers.
    XCTAssertEqual(generatedDeck.count, 580)
    XCTAssertEqual(
      PiyoDeckDigest.sha256Hex(generatedDeck),
      "7c5b6496007b98ef4ead02b9da6a2dbc560a351e1f214d0df49633ac6c42d90b"
    )
    XCTAssertEqual(generatedManifest.count, 311)
    XCTAssertEqual(
      PiyoDeckDigest.sha256Hex(generatedManifest),
      "fe7fc1ed3af0728582698bd3445d2af2eb68996c500322b1234f16f79f4ff20a"
    )
    XCTAssertEqual(package.count, 1_109)
    XCTAssertEqual(
      PiyoDeckDigest.sha256Hex(package),
      "025efa7a0584509fd892221a01c4b3cdf828472c3ffeb13a7eec420102061c31"
    )
    XCTAssertEqual(
      try JSONSchemaValidator.validate(
        instanceData: generatedManifest,
        schemaData: manifestSchema
      ),
      []
    )

    let imported = try PiyoDeckPackageReader.read(data: package, deckSchemaData: deckSchema)
    XCTAssertEqual(imported.deck, deck)
    XCTAssertEqual(imported.deckData, entries["deck.json"])
    XCTAssertEqual(imported.contentSHA256, PiyoDeckDigest.sha256Hex(imported.deckData))
    XCTAssertFalse(imported.deck.official)
    XCTAssertTrue(imported.deck.items.allSatisfy { $0.audio == nil })

    let importedGolden = try PiyoDeckPackageReader.read(
      data: crossPlatformGolden,
      deckSchemaData: deckSchema
    )
    XCTAssertEqual(importedGolden.deck, deck)
    XCTAssertEqual(importedGolden.deckData, generatedDeck)

    let prettyGolden = try fixture("valid/pretty-basic.typedeck", root: root)
    let importedPretty = try PiyoDeckPackageReader.read(
      data: prettyGolden,
      deckSchemaData: deckSchema
    )
    XCTAssertEqual(importedPretty.deck, deck)
    XCTAssertEqual(
      try PiyoDeckPackageWriter.write(deck: importedPretty.deck, deckSchemaData: deckSchema),
      package
    )
  }

  func testExpandedLanguagePackageMatchesCrossPlatformGolden() throws {
    let root = try repositoryRoot()
    let deckSchema = try schema("deck.schema.json", root: root)
    let deck = try DeckKitJSON.decodeDeck(from: fixture("valid/localized-deck.json", root: root))
    let golden = try fixture("valid/localized.typedeck", root: root)
    let exported = try PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: deckSchema)
    XCTAssertEqual(exported, golden)
    let imported = try PiyoDeckPackageReader.read(data: golden, deckSchemaData: deckSchema)
    XCTAssertEqual(imported.deck, deck)
    XCTAssertEqual(Set(try XCTUnwrap(imported.deck.localizations).keys), ["en", "ko", "es", "de", "fr"])
  }

  func testSharedBinaryCaseManifestDrivesEveryReaderExpectation() throws {
    let root = try repositoryRoot()
    let deckSchema = try schema("deck.schema.json", root: root)
    let manifestData = try fixture("cases.json", root: root)
    let manifest = try XCTUnwrap(
      JSONSerialization.jsonObject(with: manifestData) as? [String: Any]
    )
    XCTAssertEqual(manifest["schema_version"] as? Int, 1)
    let cases = try XCTUnwrap(manifest["cases"] as? [[String: Any]])
    XCTAssertGreaterThanOrEqual(cases.count, 15)
    let sourceDeck = try DeckKitJSON.decodeDeck(
      from: fixture("valid/basic-deck.json", root: root)
    )
    let canonical = try fixture("valid/basic.typedeck", root: root)

    for fixtureCase in cases {
      let identifier = try XCTUnwrap(fixtureCase["id"] as? String)
      let path = try XCTUnwrap(fixtureCase["path"] as? String)
      let expectedSize = try XCTUnwrap(fixtureCase["size_bytes"] as? Int)
      let expectedSHA = try XCTUnwrap(fixtureCase["sha256"] as? String)
      let valid = try XCTUnwrap(fixtureCase["valid"] as? Bool)
      let expectation = try XCTUnwrap(fixtureCase["expectation"] as? String)
      let data = try fixture(path, root: root)

      XCTAssertEqual(data.count, expectedSize, identifier)
      XCTAssertEqual(PiyoDeckDigest.sha256Hex(data), expectedSHA, identifier)
      if valid {
        let imported = try PiyoDeckPackageReader.read(data: data, deckSchemaData: deckSchema)
        XCTAssertEqual(imported.deck, sourceDeck, identifier)
        let normalized = try PiyoDeckPackageWriter.write(
          deck: imported.deck,
          deckSchemaData: deckSchema
        )
        XCTAssertEqual(normalized, canonical, identifier)
      } else {
        switch tryRead(data, schema: deckSchema) {
        case .success:
          XCTFail("Expected \(identifier) to fail")
        case .failure(let error):
          guard let importError = error as? PiyoDeckImportError else {
            return XCTFail("Unexpected error for \(identifier): \(error)")
          }
          XCTAssertEqual(errorFamily(importError), expectation, identifier)
        }
      }
    }
  }

  func testWriterAlwaysAppliesPinnedDeckSchema() throws {
    let root = try repositoryRoot()
    let deckSchema = try schema("deck.schema.json", root: root)
    let source = try DeckKitJSON.decodeDeck(
      from: fixture("valid/basic-deck.json", root: root)
    )
    let emptyLocalizations = Deck(
      deckId: source.deckId,
      version: source.version,
      name: source.name,
      author: source.author,
      official: source.official,
      type: source.type,
      level: source.level,
      tags: source.tags,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
      items: source.items,
      localizations: [:]
    )

    XCTAssertThrowsError(
      try PiyoDeckPackageWriter.write(
        deck: emptyLocalizations,
        deckSchemaData: deckSchema
      )
    ) { error in
      guard case PiyoDeckImportError.deckSchemaViolation(let issues) = error else {
        return XCTFail("Unexpected error: \(error)")
      }
      XCTAssertTrue(
        issues.contains {
          $0.code == "schema.minProperties" && $0.path == "$.localizations"
        }
      )
    }
  }

  func testCRC32MatchesStandardVector() {
    XCTAssertEqual(PiyoDeckCRC32.checksum(Data("123456789".utf8)), 0xCBF4_3926)
  }

  func testUserDeckValidatorRejectsReservedOfficialAudioAndMalformedItemIDs() throws {
    let root = try repositoryRoot()
    let official = try DeckKitJSON.decodeDeck(
      from: fixture("invalid/official-deck.json", root: root)
    )
    let officialIssues = UserDeckValidator.validate(official)
    XCTAssertTrue(officialIssues.contains { $0.code == "reserved_identifier" })
    XCTAssertTrue(officialIssues.contains { $0.code == "user_deck_official" })
    XCTAssertTrue(officialIssues.contains { $0.code == "user_deck_identifier" })

    let audio = try DeckKitJSON.decodeDeck(from: fixture("invalid/audio-deck.json", root: root))
    XCTAssertTrue(UserDeckValidator.validate(audio).contains { $0.code == "user_deck_audio" })

    let valid = try DeckKitJSON.decodeDeck(from: fixture("valid/basic-deck.json", root: root))
    let malformedItem = DeckItem(
      id: "i_001",
      ko: valid.items[0].ko,
      readingJa: valid.items[0].readingJa,
      meaningJa: valid.items[0].meaningJa,
      audio: nil
    )
    let malformed = replacingItems(in: valid, with: [malformedItem])
    XCTAssertTrue(
      UserDeckValidator.validate(malformed).contains {
        $0.code == "user_item_identifier" && $0.path == "items[0].id"
      }
    )
  }

  func testReaderRejectsEntitlementLikeUnknownFieldsUsingSharedDeckSchema() throws {
    let root = try repositoryRoot()
    let deckData = try fixture("invalid/unknown-field-deck.json", root: root)
    let package = try rawPackage(deckData: deckData)

    assertImportError(
      tryRead(package, schema: try schema("deck.schema.json", root: root))
    ) { error in
      guard case .deckSchemaViolation(let issues) = error else { return false }
      return issues.contains {
        $0.code == "schema.additionalProperties" && $0.path == "$.premium"
      }
    }
  }

  func testReaderRejectsDuplicateJSONKeysIncludingEscapedEquivalentKeys() throws {
    let root = try repositoryRoot()
    let source = try fixture("valid/basic-deck.json", root: root)
    let sourceString = try XCTUnwrap(String(data: source, encoding: .utf8))
    let duplicated = sourceString.replacingOccurrences(
      of: #""version": 1"#,
      with: #""version": 1, "\u0076ersion": 1"#
    )
    let package = try rawPackage(deckData: Data(duplicated.utf8))

    assertImportError(
      tryRead(package, schema: try schema("deck.schema.json", root: root))
    ) {
      guard case .duplicateJSONKey(let name, let path) = $0 else { return false }
      return name == "deck.json" && path == "$.version"
    }
  }

  func testReaderDistinguishesFuturePackageAndDeckSchemaVersions() throws {
    let root = try repositoryRoot()
    let deckData = try fixture("valid/basic-deck.json", root: root)
    let deckSchema = try schema("deck.schema.json", root: root)

    let futurePackage = try rawPackage(deckData: deckData, formatVersion: 2)
    assertImportError(tryRead(futurePackage, schema: deckSchema)) {
      $0 == .unsupportedFormatVersion(2)
    }

    let futureDeckSchema = try rawPackage(deckData: deckData, deckSchemaVersion: 2)
    assertImportError(tryRead(futureDeckSchema, schema: deckSchema)) {
      $0 == .unsupportedDeckSchemaVersion(2)
    }
  }

  func testReaderVerifiesSHAAndManifestMetadataAfterZIPCRC() throws {
    let root = try repositoryRoot()
    let deckData = try fixture("valid/basic-deck.json", root: root)
    let deckSchema = try schema("deck.schema.json", root: root)

    let wrongHashPackage = try rawPackage(
      deckData: deckData,
      sha256: String(repeating: "0", count: 64)
    )
    assertImportError(tryRead(wrongHashPackage, schema: deckSchema)) { $0 == .sha256Mismatch }

    let wrongCountPackage = try rawPackage(deckData: deckData, itemCount: 1)
    assertImportError(tryRead(wrongCountPackage, schema: deckSchema)) {
      $0 == .manifestMismatch(field: "deck.item_count")
    }
  }

  func testReaderRejectsCRCMutationBeforeDecodingJSON() throws {
    let root = try repositoryRoot()
    let deckSchema = try schema("deck.schema.json", root: root)
    let deck = try DeckKitJSON.decodeDeck(from: fixture("valid/basic-deck.json", root: root))
    let original = try PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: deckSchema)
    let storedDeck = try XCTUnwrap(try PiyoDeckZIP.read(original)["deck.json"])
    let deckRange = try XCTUnwrap(original.range(of: storedDeck))
    var bytes = [UInt8](original)
    bytes[deckRange.lowerBound] ^= 0x01

    assertImportError(tryRead(Data(bytes), schema: deckSchema)) {
      $0 == .crcMismatch(name: "deck.json")
    }
  }

  func testReaderRejectsUnsupportedAndUnsafeZIPFeatures() throws {
    let root = try repositoryRoot()
    let deckSchema = try schema("deck.schema.json", root: root)
    let deck = try DeckKitJSON.decodeDeck(from: fixture("valid/basic-deck.json", root: root))
    let package = try PiyoDeckPackageWriter.write(deck: deck, deckSchemaData: deckSchema)
    let centralOffset = try centralDirectoryOffset(in: package)

    var encrypted = [UInt8](package)
    encrypted[centralOffset + 8] |= 0x01
    assertImportError(tryRead(Data(encrypted), schema: deckSchema)) {
      $0 == .unsupportedArchiveFeature("encryption")
    }

    var deflated = [UInt8](package)
    deflated[centralOffset + 10] = 8
    assertImportError(tryRead(Data(deflated), schema: deckSchema)) {
      $0 == .unsupportedArchiveFeature("compression method 8")
    }

    var zip64 = [UInt8](package)
    for index in (centralOffset + 20)..<(centralOffset + 24) { zip64[index] = 0xFF }
    assertImportError(tryRead(Data(zip64), schema: deckSchema)) {
      $0 == .unsupportedArchiveFeature("ZIP64")
    }

    var symlink = [UInt8](package)
    symlink[centralOffset + 5] = 3
    symlink[centralOffset + 41] = 0xA0
    assertImportError(tryRead(Data(symlink), schema: deckSchema)) {
      $0 == .unsupportedArchiveFeature("symbolic link")
    }

    var windowsDirectory = [UInt8](package)
    windowsDirectory[centralOffset + 38] = 0x10
    assertImportError(tryRead(Data(windowsDirectory), schema: deckSchema)) {
      $0 == .unsupportedArchiveFeature("directory entry")
    }

    let unsafe = replaceAll(
      in: package,
      bytes: Array("manifest.json".utf8),
      with: Array("evil\\path.jsn".utf8)
    )
    assertImportError(tryRead(unsafe, schema: deckSchema)) {
      $0 == .unsafeEntryPath("evil\\path.jsn")
    }
  }

  func testReaderEnforcesPackageAndEntrySizeLimitsBeforeJSONDecoding() throws {
    let root = try repositoryRoot()
    let deckSchema = try schema("deck.schema.json", root: root)
    let oversizedPackage = Data(
      repeating: 0,
      count: PiyoDeckPackageLimits.maximumPackageBytes + 1
    )
    assertImportError(tryRead(oversizedPackage, schema: deckSchema)) {
      $0
        == .packageTooLarge(
          actual: PiyoDeckPackageLimits.maximumPackageBytes + 1,
          maximum: PiyoDeckPackageLimits.maximumPackageBytes
        )
    }

    let deckData = Data(repeating: 0x20, count: PiyoDeckPackageLimits.maximumDeckBytes + 1)
    let package = try PiyoDeckZIP.write(
      entries: [
        .init(name: "manifest.json", data: Data("{}".utf8)),
        .init(name: "deck.json", data: deckData),
      ]
    )
    assertImportError(tryRead(package, schema: deckSchema)) {
      $0
        == .entryTooLarge(
          name: "deck.json",
          actual: PiyoDeckPackageLimits.maximumDeckBytes + 1,
          maximum: PiyoDeckPackageLimits.maximumDeckBytes
        )
    }
  }

  func testReaderRejectsUnknownManifestFields() throws {
    let root = try repositoryRoot()
    let deckData = try fixture("valid/basic-deck.json", root: root)
    let validManifest = try makeManifest(deckData: deckData)
    var object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: validManifest) as? [String: Any]
    )
    object["license_key"] = "must-not-be-imported"
    let invalidManifest = try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    let package = try PiyoDeckZIP.write(
      entries: [
        .init(name: "manifest.json", data: invalidManifest),
        .init(name: "deck.json", data: deckData),
      ]
    )

    assertImportError(
      tryRead(package, schema: try schema("deck.schema.json", root: root))
    ) {
      guard case .invalidManifest(let issues) = $0 else { return false }
      return issues.contains {
        $0.code == "schema.additionalProperties" && $0.path == "$.license_key"
      }
    }
  }

  func testReaderRejectsNoncanonicalTimestampBytes() throws {
    let root = try repositoryRoot()
    let source = try fixture("valid/basic-deck.json", root: root)
    let sourceString = try XCTUnwrap(String(data: source, encoding: .utf8))
    let offsetTimestamp = sourceString.replacingOccurrences(
      of: #"2026-08-14T00:00:00Z"#,
      with: #"2026-08-14T09:00:00+09:00"#
    )
    let package = try rawPackage(deckData: Data(offsetTimestamp.utf8))

    assertImportError(
      tryRead(package, schema: try schema("deck.schema.json", root: root))
    ) {
      guard case .deckSchemaViolation(let issues) = $0 else { return false }
      return issues.contains {
        $0.code == "schema.pattern" && $0.path == "$.created_at"
      }
    }
  }

  func testStrictJSONRejectsUnpairedSurrogateEscapes() throws {
    let invalidDocuments = [
      #"{"name":"\ud800"}"#,
      #"{"name":"\udc00"}"#,
      #"{"name":"\ud800\u0041"}"#,
    ]
    for document in invalidDocuments {
      XCTAssertThrowsError(
        try PiyoDeckStrictJSON.validate(Data(document.utf8), name: "deck.json")
      ) { error in
        guard case PiyoDeckImportError.invalidJSON(let name, _) = error else {
          return XCTFail("Unexpected error: \(error)")
        }
        XCTAssertEqual(name, "deck.json")
      }
    }

    XCTAssertNoThrow(
      try PiyoDeckStrictJSON.validate(
        Data(#"{"name":"\ud835\udfd9"}"#.utf8),
        name: "deck.json"
      )
    )
  }

  private func tryRead(_ data: Data, schema: Data) -> Result<PiyoDeckPackage, Error> {
    Result { try PiyoDeckPackageReader.read(data: data, deckSchemaData: schema) }
  }

  private func errorFamily(_ error: PiyoDeckImportError) -> String {
    switch error {
    case .sha256Mismatch:
      return "sha256_mismatch"
    case .invalidJSON, .duplicateJSONKey:
      return "invalid_json"
    case .unsupportedFormatVersion, .unsupportedDeckSchemaVersion:
      return "unsupported_version"
    case .unsupportedArchiveFeature:
      return "unsupported_archive_feature"
    case .unsafeEntryPath:
      return "unsafe_entry_path"
    case .malformedArchive:
      return "malformed_archive"
    case .crcMismatch:
      return "crc_mismatch"
    default:
      return "unexpected"
    }
  }

  private func assertImportError(
    _ result: Result<PiyoDeckPackage, Error>,
    matches: (PiyoDeckImportError) -> Bool,
    file: StaticString = #filePath,
    line: UInt = #line
  ) {
    switch result {
    case .success:
      XCTFail("Expected import to fail", file: file, line: line)
    case .failure(let error):
      guard let importError = error as? PiyoDeckImportError else {
        return XCTFail("Unexpected error: \(error)", file: file, line: line)
      }
      XCTAssertTrue(
        matches(importError), "Unexpected error: \(importError)", file: file, line: line)
    }
  }

  private func rawPackage(
    deckData: Data,
    formatVersion: Int = 1,
    deckSchemaVersion: Int = 1,
    itemCount: Int? = nil,
    sha256: String? = nil
  ) throws -> Data {
    let manifestData = try makeManifest(
      deckData: deckData,
      formatVersion: formatVersion,
      deckSchemaVersion: deckSchemaVersion,
      itemCount: itemCount,
      sha256: sha256
    )
    return try PiyoDeckZIP.write(
      entries: [
        .init(name: "manifest.json", data: manifestData),
        .init(name: "deck.json", data: deckData),
      ]
    )
  }

  private func makeManifest(
    deckData: Data,
    formatVersion: Int = 1,
    deckSchemaVersion: Int = 1,
    itemCount: Int? = nil,
    sha256: String? = nil
  ) throws -> Data {
    let deck = try DeckKitJSON.decodeDeck(from: deckData)
    let manifest = PiyoDeckManifest(
      formatVersion: formatVersion,
      deckSchemaVersion: deckSchemaVersion,
      deck: .init(
        deckId: deck.deckId,
        deckVersion: deck.version,
        itemCount: itemCount ?? deck.items.count,
        sizeBytes: deckData.count,
        sha256: sha256 ?? PiyoDeckDigest.sha256Hex(deckData)
      )
    )
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    return try encoder.encode(manifest)
  }

  private func replacingItems(in deck: Deck, with items: [DeckItem]) -> Deck {
    Deck(
      deckId: deck.deckId,
      version: deck.version,
      name: deck.name,
      author: deck.author,
      official: deck.official,
      type: deck.type,
      level: deck.level,
      tags: deck.tags,
      createdAt: deck.createdAt,
      updatedAt: deck.updatedAt,
      items: items,
      localizations: deck.localizations
    )
  }

  private func centralDirectoryOffset(in data: Data) throws -> Int {
    guard data.count >= 22 else { throw CocoaError(.fileReadCorruptFile) }
    let offset = data.count - 22 + 16
    let bytes = [UInt8](data)
    return Int(bytes[offset]) | Int(bytes[offset + 1]) << 8
      | Int(bytes[offset + 2]) << 16 | Int(bytes[offset + 3]) << 24
  }

  private func replaceAll(in data: Data, bytes target: [UInt8], with replacement: [UInt8]) -> Data {
    precondition(target.count == replacement.count)
    var result = [UInt8](data)
    guard !target.isEmpty, result.count >= target.count else { return data }
    var index = 0
    while index <= result.count - target.count {
      if Array(result[index..<(index + target.count)]) == target {
        result.replaceSubrange(index..<(index + target.count), with: replacement)
        index += replacement.count
      } else {
        index += 1
      }
    }
    return Data(result)
  }

  private func fixture(_ name: String, root: URL) throws -> Data {
    try Data(
      contentsOf: root.appendingPathComponent("shared/piyodeck/fixtures/\(name)")
    )
  }

  private func schema(_ name: String, root: URL) throws -> Data {
    try Data(contentsOf: root.appendingPathComponent("shared/schema/\(name)"))
  }

  private func repositoryRoot() throws -> URL {
    var candidate = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
    for _ in 0..<8 {
      if FileManager.default.fileExists(atPath: candidate.appendingPathComponent("PRD.md").path) {
        return candidate
      }
      candidate.deleteLastPathComponent()
    }
    throw CocoaError(.fileNoSuchFile)
  }
}
