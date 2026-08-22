import CryptoKit
import DeckKit
import Foundation
import HangulEngine
import XCTest

final class DeckKitTests: XCTestCase {
  func testMockCatalogPassesJSONSchemasAndSwiftValidators() throws {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    let catalogData = try Data(
      contentsOf: root.appendingPathComponent("shared/mock_catalog/catalog.json"))
    let catalogSchema = try Data(
      contentsOf: root.appendingPathComponent("shared/schema/catalog.schema.json"))
    let deckSchema = try Data(
      contentsOf: root.appendingPathComponent("shared/schema/deck.schema.json"))

    XCTAssertEqual(
      try JSONSchemaValidator.validate(instanceData: catalogData, schemaData: catalogSchema), [])
    let catalog = try DeckKitJSON.decodeCatalog(from: catalogData)
    XCTAssertEqual(CatalogValidator.validate(catalog), [])
    XCTAssertEqual(catalog.decks.count, 26)
    XCTAssertEqual(catalog.decks.filter(\.official).count, 26)
    XCTAssertEqual(catalog.decks.filter { !$0.official }.count, 0)
    XCTAssertTrue(catalog.decks.allSatisfy { (1...10).contains($0.previewItems.count) })

    var decks: [Deck] = []
    for entry in catalog.decks {
      let url = root.appendingPathComponent("shared/mock_catalog").appendingPathComponent(
        entry.fileUrl)
      let data = try Data(contentsOf: url)
      XCTAssertEqual(data.count, entry.sizeBytes, entry.deckId)
      XCTAssertEqual(
        try JSONSchemaValidator.validate(instanceData: data, schemaData: deckSchema), [],
        entry.deckId)
      let deck = try DeckKitJSON.decodeDeck(from: data)
      XCTAssertEqual(DeckValidator.validate(deck), [], entry.deckId)
      for item in deck.items {
        XCTAssertNoThrow(
          try JamoDecomposer.keySequence(for: item.ko), "\(entry.deckId): \(item.id)")
      }
      decks.append(deck)
    }

    XCTAssertEqual(decks.count, 26)
    XCTAssertEqual(CatalogBundleValidator.validate(catalog: catalog, decks: decks), [])
    let expectedFileCount = try FileManager.default.contentsOfDirectory(
      at: root.appendingPathComponent("shared/mock_catalog/decks"),
      includingPropertiesForKeys: nil
    ).filter { $0.pathExtension == "json" }.count
    XCTAssertEqual(expectedFileCount, 26)
  }

  func testGamePresetDecksProvideTopikLevelsWithAtLeastTwentyFiveWords() throws {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    let deckSchema = try Data(
      contentsOf: root.appendingPathComponent("shared/schema/deck.schema.json"))
    let directories = ["flow", "acid_rain", "word_match", "choseong", "dictation"]
    let expectedLevels = Dictionary(uniqueKeysWithValues: directories.flatMap { directory in
      [
        ("\(directory)_topik_beginner", 1),
        ("\(directory)_topik_intermediate", 2),
        ("\(directory)_topik_advanced", 3),
      ]
    })

    var loadedIDs = Set<String>()
    for directory in directories {
      let presetRoot = root.appendingPathComponent("shared/mock_catalog/decks/\(directory)")
      let urls = try FileManager.default.contentsOfDirectory(
        at: presetRoot,
        includingPropertiesForKeys: nil
      ).filter { $0.pathExtension == "json" }

      XCTAssertEqual(urls.count, 3, directory)
      for url in urls {
        let data = try Data(contentsOf: url)
        XCTAssertEqual(
          try JSONSchemaValidator.validate(instanceData: data, schemaData: deckSchema),
          [],
          url.lastPathComponent
        )
        let deck = try DeckKitJSON.decodeDeck(from: data)
        XCTAssertEqual(DeckValidator.validate(deck), [], deck.deckId)
        XCTAssertEqual(deck.level, try XCTUnwrap(expectedLevels[deck.deckId]), deck.deckId)
        XCTAssertEqual(deck.type, .word, deck.deckId)
        XCTAssertGreaterThanOrEqual(deck.items.count, 25, deck.deckId)
        XCTAssertEqual(Set(deck.items.map(\.ko)).count, deck.items.count, deck.deckId)
        for item in deck.items {
          XCTAssertEqual(
            item.audio,
            canonicalPronunciationAudioPath(for: item.ko),
            "\(deck.deckId): \(item.id)"
          )
          XCTAssertNoThrow(try JamoDecomposer.keySequence(for: item.ko), deck.deckId)
        }
        loadedIDs.insert(deck.deckId)
      }
    }

    XCTAssertEqual(loadedIDs, Set(expectedLevels.keys))
  }

  func testTagCountsAndTrendingFormulaAreDerivedFromCatalogFields() throws {
    let catalog = try loadCatalog()
    for tag in catalog.tags {
      XCTAssertEqual(
        tag.deckCount, catalog.decks.filter { $0.tags.contains(tag.tag) }.count, tag.tag)
    }
    let deck = try XCTUnwrap(catalog.decks.first)
    let expected = Double(deck.downloads7d) / Double(max(deck.downloadsTotal - deck.downloads7d, 1))
    XCTAssertEqual(deck.trendingRatio, expected, accuracy: 0.000_001)
  }

  func testCatalogUpdateFixturePassesSchemasAndBundleValidation() throws {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    let fixtureRoot = root.appendingPathComponent("shared/mock_catalog/updates")
    let catalogData = try Data(contentsOf: fixtureRoot.appendingPathComponent("catalog.json"))
    let catalogSchema = try Data(
      contentsOf: root.appendingPathComponent("shared/schema/catalog.schema.json"))
    let deckSchema = try Data(
      contentsOf: root.appendingPathComponent("shared/schema/deck.schema.json"))

    XCTAssertEqual(
      try JSONSchemaValidator.validate(instanceData: catalogData, schemaData: catalogSchema), [])
    let catalog = try DeckKitJSON.decodeCatalog(from: catalogData)
    XCTAssertEqual(catalog.catalogVersion, 11)
    XCTAssertEqual(CatalogValidator.validate(catalog), [])

    var decks: [Deck] = []
    for entry in catalog.decks {
      let data = try Data(contentsOf: fixtureRoot.appendingPathComponent(entry.fileUrl))
      XCTAssertEqual(data.count, entry.sizeBytes, entry.deckId)
      XCTAssertEqual(
        try JSONSchemaValidator.validate(instanceData: data, schemaData: deckSchema), [],
        entry.deckId)
      let deck = try DeckKitJSON.decodeDeck(from: data)
      XCTAssertEqual(DeckValidator.validate(deck), [], entry.deckId)
      decks.append(deck)
    }

    XCTAssertEqual(CatalogBundleValidator.validate(catalog: catalog, decks: decks), [])
    let updated = try XCTUnwrap(decks.first { $0.deckId == "official_daily_words" })
    XCTAssertEqual(updated.version, 5)
    XCTAssertEqual(updated.items.last?.ko, "약속")
  }

  func testKPopAndDramaDecksMixWordsAndPhrasesAcrossTwoToEightSyllables() throws {
    let catalog = try loadCatalog()
    let enrichmentEntries = catalog.decks.filter {
      $0.deckId.hasPrefix("official_kpop_") || $0.deckId.hasPrefix("official_drama_")
    }

    XCTAssertEqual(enrichmentEntries.filter { $0.tags.contains("K-POP") }.count, 3)
    XCTAssertEqual(enrichmentEntries.filter { $0.tags.contains("Kドラマ") }.count, 3)

    for entry in enrichmentEntries {
      let deck = try loadDeck(for: entry)
      let syllableCounts = deck.items.map { item in
        item.ko.unicodeScalars.filter { (0xAC00...0xD7A3).contains(Int($0.value)) }.count
      }
      XCTAssertGreaterThanOrEqual(deck.items.count, 10, entry.deckId)
      XCTAssertTrue(syllableCounts.allSatisfy { (2...8).contains($0) }, entry.deckId)
      XCTAssertGreaterThanOrEqual(Set(syllableCounts).count, 4, entry.deckId)
      XCTAssertTrue(deck.items.contains { $0.ko.contains(" ") }, entry.deckId)
      XCTAssertTrue(deck.items.contains { !$0.ko.contains(" ") }, entry.deckId)
    }
  }

  func testFunPhraseDecksCoverNineCuratedThemes() throws {
    let catalog = try loadCatalog()
    let expectedIDs: Set<String> = [
      "official_fun_travel_moments",
      "official_fun_food_cafe",
      "official_fun_couple_conflict",
      "official_fun_couple_makeup",
      "official_fun_first_date",
      "official_fun_flirty_chat",
      "official_fun_friend_reactions",
      "official_fun_weekend_party",
      "official_fun_idol_live_comments",
    ]
    let entries = catalog.decks.filter { expectedIDs.contains($0.deckId) }

    XCTAssertEqual(Set(entries.map(\.deckId)), expectedIDs)
    XCTAssertTrue(entries.allSatisfy { $0.type == .sentence })
    XCTAssertTrue(entries.allSatisfy { $0.itemCount == 12 })

    for entry in entries {
      let deck = try loadDeck(for: entry)
      XCTAssertEqual(deck.items.count, 12, entry.deckId)
      XCTAssertTrue(deck.items.allSatisfy { $0.ko.count <= 10 }, entry.deckId)
      XCTAssertEqual(Set(deck.items.map(\.ko)).count, 12, entry.deckId)
    }
  }

  func testDeckJSONRoundTripsWithSnakeCaseKeys() throws {
    let catalog = try loadCatalog()
    let deck = try loadDeck(for: XCTUnwrap(catalog.decks.first))
    let data = try DeckKitJSON.makeEncoder().encode(deck)
    let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    XCTAssertNotNil(object["deck_id"])
    XCTAssertNotNil(object["created_at"])
    XCTAssertNil(object["deckId"])
    XCTAssertEqual(try DeckKitJSON.decodeDeck(from: data), deck)
  }

  func testBundledContentProvidesGlobalMetadataAndEnglishItemCopy() throws {
    let catalog = try loadCatalog()

    for entry in catalog.decks {
      XCTAssertTrue(entry.hasLocalization(languageCode: "en"), entry.deckId)
      XCTAssertTrue(entry.hasLocalization(languageCode: "ko"), entry.deckId)
      XCTAssertFalse(entry.localizedName(languageCode: "en")?.isEmpty ?? true, entry.deckId)
      XCTAssertFalse(entry.localizedName(languageCode: "ko")?.isEmpty ?? true, entry.deckId)
      XCTAssertTrue(
        entry.previewItems.allSatisfy {
          !($0.localizedMeaning(languageCode: "en")?.isEmpty ?? true)
            && !($0.localizedMeaning(languageCode: "ko")?.isEmpty ?? true)
        },
        entry.deckId
      )

      let deck = try loadDeck(for: entry)
      XCTAssertTrue(deck.hasLocalization(languageCode: "en"), deck.deckId)
      XCTAssertTrue(deck.hasLocalization(languageCode: "ko"), deck.deckId)
      XCTAssertTrue(
        deck.items.allSatisfy {
          !($0.localizedMeaning(languageCode: "en")?.isEmpty ?? true)
            && !($0.localizedReading(languageCode: "en")?.isEmpty ?? true)
        },
        deck.deckId
      )
    }
  }

  func testUnsupportedLocalesFallBackToEnglishMetadataAndClues() throws {
    let catalog = try loadCatalog()
    let entry = try XCTUnwrap(catalog.decks.first)
    let deck = try loadDeck(for: entry)
    let preview = try XCTUnwrap(entry.previewItems.first)
    let item = try XCTUnwrap(deck.items.first)
    let tag = try XCTUnwrap(catalog.tags.first)

    XCTAssertTrue(entry.hasLocalization(languageCode: "fr-FR"))
    XCTAssertEqual(
      entry.localizedName(languageCode: "fr-FR"),
      entry.localizedName(languageCode: "en")
    )
    XCTAssertEqual(
      entry.localizedAuthorNickname(languageCode: "fr-FR"),
      entry.localizedAuthorNickname(languageCode: "en")
    )
    XCTAssertEqual(
      entry.localizedTags(languageCode: "fr-FR"),
      entry.localizedTags(languageCode: "en")
    )
    XCTAssertEqual(
      preview.localizedMeaning(languageCode: "fr-FR"),
      preview.localizedMeaning(languageCode: "en")
    )

    XCTAssertTrue(deck.hasLocalization(languageCode: "fr-FR"))
    XCTAssertEqual(
      deck.localizedName(languageCode: "fr-FR"),
      deck.localizedName(languageCode: "en")
    )
    XCTAssertEqual(
      deck.localizedAuthorNickname(languageCode: "fr-FR"),
      deck.localizedAuthorNickname(languageCode: "en")
    )
    XCTAssertEqual(
      deck.localizedTags(languageCode: "fr-FR"),
      deck.localizedTags(languageCode: "en")
    )
    XCTAssertEqual(
      item.localizedMeaning(languageCode: "fr-FR"),
      item.localizedMeaning(languageCode: "en")
    )
    XCTAssertEqual(
      item.localizedReading(languageCode: "fr-FR"),
      item.localizedReading(languageCode: "en")
    )
    XCTAssertEqual(
      tag.localizedTag(languageCode: "fr-FR"),
      tag.localizedTag(languageCode: "en")
    )
  }

  func testLegacyContentNeverFallsBackToJapaneseForGlobalLocales() throws {
    let data = Data(
      #"{"id":"legacy","ko":"학교","reading_ja":"ハッキョ","meaning_ja":"学校","audio":null}"#
        .utf8
    )
    let item = try DeckKitJSON.makeDecoder().decode(DeckItem.self, from: data)

    XCTAssertEqual(item.localizedMeaning(languageCode: "ja"), "学校")
    XCTAssertEqual(item.localizedReading(languageCode: "ja"), "ハッキョ")
    XCTAssertNil(item.localizedMeaning(languageCode: "en"))
    XCTAssertNil(item.localizedReading(languageCode: "ko"))
  }

  func testEnglishDeckMetadataRequiresEveryItemTranslation() {
    let date = Date(timeIntervalSince1970: 100)
    let deck = Deck(
      deckId: "official_missing_english",
      version: 1,
      name: "英語不足",
      author: .init(id: "official_hanco", nickname: "ピヨキー 公式"),
      official: true,
      type: .word,
      level: 1,
      tags: ["公式"],
      createdAt: date,
      updatedAt: date,
      items: [
        DeckItem(
          id: "i_001",
          ko: "학교",
          readingJa: "ハッキョ",
          meaningJa: "学校",
          audio: nil
        )
      ],
      localizations: [
        "en": DeckMetadataLocalization(
          name: "School",
          authorNickname: "typee Official",
          tags: ["Official"]
        )
      ]
    )

    XCTAssertTrue(
      DeckValidator.validate(deck).contains {
        $0.code == "missing_localization"
          && $0.path == "items[0].localizations.en"
      }
    )
  }

  func testPublishedDeckLocaleRequiresLocalizedCatalogTag() throws {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    let source = try Data(
      contentsOf: root.appendingPathComponent("shared/mock_catalog/catalog.json")
    )
    var object = try XCTUnwrap(JSONSerialization.jsonObject(with: source) as? [String: Any])
    var tags = try XCTUnwrap(object["tags"] as? [[String: Any]])
    var firstTag = try XCTUnwrap(tags.first)
    var localizations = try XCTUnwrap(firstTag["localizations"] as? [String: Any])
    localizations.removeValue(forKey: "en")
    firstTag["localizations"] = localizations
    tags[0] = firstTag
    object["tags"] = tags

    let catalog = try DeckKitJSON.decodeCatalog(
      from: JSONSerialization.data(withJSONObject: object)
    )
    XCTAssertTrue(
      CatalogValidator.validate(catalog).contains {
        $0.code == "missing_localization" && $0.path == "tags[0].localizations.en"
      }
    )
  }

  func testJSONSchemaValidatorRejectsUnknownAndMissingFields() throws {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    let schema = try Data(
      contentsOf: root.appendingPathComponent("shared/schema/catalog.schema.json"))
    let source = try Data(
      contentsOf: root.appendingPathComponent("shared/mock_catalog/catalog.json"))
    var object = try XCTUnwrap(JSONSerialization.jsonObject(with: source) as? [String: Any])
    object["unexpected"] = true
    object.removeValue(forKey: "generated_at")
    let malformed = try JSONSerialization.data(withJSONObject: object)
    let issues = try JSONSchemaValidator.validate(instanceData: malformed, schemaData: schema)
    XCTAssertTrue(
      issues.contains { $0.code == "schema.additionalProperties" && $0.path == "$.unexpected" })
    XCTAssertTrue(issues.contains { $0.code == "schema.required" && $0.path == "$.generated_at" })
  }

  func testJSONSchemaValidatorEnforcesObjectAndUnicodeScalarLengths() throws {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    let deckSchema = try Data(
      contentsOf: root.appendingPathComponent("shared/schema/deck.schema.json"))
    let source = try Data(
      contentsOf: root.appendingPathComponent(
        "shared/piyodeck/fixtures/valid/basic-deck.json"
      ))
    var object = try XCTUnwrap(JSONSerialization.jsonObject(with: source) as? [String: Any])
    object["localizations"] = [String: Any]()
    let malformed = try JSONSerialization.data(withJSONObject: object)
    let deckIssues = try JSONSchemaValidator.validate(
      instanceData: malformed,
      schemaData: deckSchema
    )
    XCTAssertTrue(
      deckIssues.contains {
        $0.code == "schema.minProperties" && $0.path == "$.localizations"
      }
    )

    let scalarSchema = Data(#"{"type":"string","maxLength":1}"#.utf8)
    let twoScalarGrapheme = Data(#""e\u0301""#.utf8)
    let scalarIssues = try JSONSchemaValidator.validate(
      instanceData: twoScalarGrapheme,
      schemaData: scalarSchema
    )
    XCTAssertTrue(scalarIssues.contains { $0.code == "schema.maxLength" })
  }

  func testSemanticValidatorRejectsUntypeableKoreanAndDuplicateItems() {
    let date = Date(timeIntervalSince1970: 100)
    let item = DeckItem(id: "i_001", ko: "ABC", readingJa: "", meaningJa: "", audio: "")
    let deck = Deck(
      deckId: "Bad ID",
      version: 0,
      name: " ",
      author: .init(id: "", nickname: ""),
      official: false,
      type: .word,
      level: 4,
      tags: ["重複", "重複"],
      createdAt: date,
      updatedAt: date.addingTimeInterval(-1),
      items: [item, item]
    )
    let issues = DeckValidator.validate(deck)
    let codes = Set(issues.map(\.code))
    XCTAssertTrue(
      codes.isSuperset(of: [
        "identifier", "range", "required", "duplicate", "date_order", "undecomposable_ko",
      ]))
  }

  func testSemanticValidatorRejectsKoreanTargetLongerThanTenCharacters() {
    let date = Date(timeIntervalSince1970: 100)
    let item = DeckItem(
      id: "i_001",
      ko: "가나다라마바사아자차카",
      readingJa: "カナダラマバサアジャチャカ",
      meaningJa: "長すぎるお題",
      audio: nil
    )
    let deck = Deck(
      deckId: "official_length_test",
      version: 1,
      name: "長さテスト",
      author: .init(id: "official_hanco", nickname: "ピヨキー 公式"),
      official: true,
      type: .word,
      level: 1,
      tags: ["テスト"],
      createdAt: date,
      updatedAt: date,
      items: [item]
    )

    XCTAssertTrue(
      DeckValidator.validate(deck).contains {
        $0.code == "max_target_length" && $0.path == "items[0].ko"
      })
  }

  func testBundleValidatorDetectsMissingMismatchedAndUnindexedDecks() throws {
    let catalog = try loadCatalog()
    var decks = try catalog.decks.map(loadDeck)
    decks.removeFirst()
    XCTAssertTrue(
      CatalogBundleValidator.validate(catalog: catalog, decks: decks).contains {
        $0.code == "missing_deck"
      })

    var first = try loadDeck(for: XCTUnwrap(catalog.decks.first))
    let renamed = Deck(
      deckId: first.deckId,
      version: first.version,
      name: "別名",
      author: first.author,
      official: first.official,
      type: first.type,
      level: first.level,
      tags: first.tags,
      createdAt: first.createdAt,
      updatedAt: first.updatedAt,
      items: first.items
    )
    decks = try catalog.decks.dropFirst().map(loadDeck)
    decks.append(renamed)
    XCTAssertTrue(
      CatalogBundleValidator.validate(catalog: catalog, decks: decks).contains {
        $0.code == "metadata_mismatch"
      })

    first = Deck(
      deckId: "extra_deck",
      version: first.version,
      name: first.name,
      author: first.author,
      official: first.official,
      type: first.type,
      level: first.level,
      tags: first.tags,
      createdAt: first.createdAt,
      updatedAt: first.updatedAt,
      items: first.items
    )
    decks = try catalog.decks.map(loadDeck)
    decks.append(first)
    XCTAssertTrue(
      CatalogBundleValidator.validate(catalog: catalog, decks: decks).contains {
        $0.code == "unindexed_deck"
      })
  }

  private func loadCatalog() throws -> Catalog {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    return try DeckKitJSON.decodeCatalog(
      from: Data(contentsOf: root.appendingPathComponent("shared/mock_catalog/catalog.json"))
    )
  }

  private func loadDeck(for entry: CatalogDeck) throws -> Deck {
    let root = try RepositoryFixtureLocator.root(from: #filePath)
    let url = root.appendingPathComponent("shared/mock_catalog").appendingPathComponent(
      entry.fileUrl)
    return try DeckKitJSON.decodeDeck(from: Data(contentsOf: url))
  }

  private func canonicalPronunciationAudioPath(for target: String) -> String {
    let digest = SHA256.hash(data: Data(target.utf8))
    let prefix = digest.prefix(10).map { String(format: "%02x", Int($0)) }.joined()
    return "audio/ko_\(prefix).mp3"
  }
}

private enum RepositoryFixtureLocator {
  static func root(from sourceFile: String) throws -> URL {
    var candidate = URL(fileURLWithPath: sourceFile).deletingLastPathComponent()
    for _ in 0..<8 {
      if FileManager.default.fileExists(atPath: candidate.appendingPathComponent("PRD.md").path) {
        return candidate
      }
      candidate.deleteLastPathComponent()
    }
    throw CocoaError(.fileNoSuchFile)
  }
}
