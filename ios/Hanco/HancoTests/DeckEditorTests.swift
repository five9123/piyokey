import DeckKit
import Foundation
import XCTest

@testable import Hanco

final class DeckEditorTests: XCTestCase {
  private let firstHex = "00000000000000000000000000000001"
  private let secondHex = "00000000000000000000000000000002"
  private let thirdHex = "00000000000000000000000000000003"

  func testNewDraftCreatesUserAndItemIdentifiersAndVersionOneDeck() {
    var generated = [firstHex, secondHex].makeIterator()
    var draft = UserDeckDraft(
      newAt: Date(timeIntervalSince1970: 100),
      uuidHexGenerator: { generated.next()! }
    )
    draft.name = "マイ単語"
    draft.authorNickname = "Piyo"
    draft.tags = ["custom"]
    draft.items[0].ko = "안녕"
    draft.items[0].readingJa = "アンニョン"
    draft.items[0].meaningJa = "こんにちは"

    let deck = draft.materializedDeck(at: Date(timeIntervalSince1970: 200))

    XCTAssertEqual(deck.deckId, "user_\(firstHex)")
    XCTAssertEqual(deck.items.map(\.id), ["item_\(secondHex)"])
    XCTAssertEqual(deck.version, 1)
    XCTAssertFalse(deck.official)
    XCTAssertNil(deck.items[0].audio)
    XCTAssertTrue(UserDeckValidator.validate(deck).isEmpty)
  }

  func testEditingPreservesDeckAndItemIdentifiersAndIncrementsVersion() {
    let original = makeUserDeck(version: 4)
    var draft = UserDeckDraft(editing: original)
    draft.name = "更新したデッキ"
    draft.items[0].meaningJa = "更新した意味"
    draft.addItem(uuidHexGenerator: { thirdHex })
    draft.items[1].ko = "학교"
    draft.items[1].readingJa = "ハッキョ"
    draft.items[1].meaningJa = "学校"

    let edited = draft.materializedDeck(at: Date(timeIntervalSince1970: 400))

    XCTAssertEqual(edited.deckId, original.deckId)
    XCTAssertEqual(edited.version, 5)
    XCTAssertEqual(edited.createdAt, original.createdAt)
    XCTAssertEqual(edited.items[0].id, original.items[0].id)
    XCTAssertEqual(edited.items[1].id, "item_\(thirdHex)")
    XCTAssertTrue(edited.items.allSatisfy { $0.audio == nil })
  }

  func testDraftFlowMatchesOnlyTheSameRequestedWork() {
    let original = makeUserDeck(version: 4)
    let editing = UserDeckDraft(editing: original)

    XCTAssertTrue(DeckMakerDraftFlow.editing(deckID: original.deckId).matches(editing))
    XCTAssertFalse(DeckMakerDraftFlow.new.matches(editing))
    XCTAssertFalse(
      DeckMakerDraftFlow.editing(deckID: "user_other").matches(editing)
    )
  }

  func testEditingCommitRefusesMissingOrChangedSourceVersion() throws {
    let editing = UserDeckDraft(editing: makeUserDeck(version: 4))

    XCTAssertNoThrow(
      try requireUnchangedUserDeckSource(for: editing, installedVersion: 4)
    )
    XCTAssertThrowsError(
      try requireUnchangedUserDeckSource(for: editing, installedVersion: 5)
    ) { error in
      XCTAssertEqual(error as? UserDeckEditCommitError, .sourceChanged)
    }
    XCTAssertThrowsError(
      try requireUnchangedUserDeckSource(for: editing, installedVersion: nil)
    ) { error in
      XCTAssertEqual(error as? UserDeckEditCommitError, .sourceChanged)
    }
  }

  func testNewAndOfficialCopyCommitsDoNotRequireInstalledSource() throws {
    var generated = [firstHex, secondHex].makeIterator()
    let newDraft = UserDeckDraft(uuidHexGenerator: { generated.next()! })
    XCTAssertNoThrow(
      try requireUnchangedUserDeckSource(for: newDraft, installedVersion: nil)
    )

    var copiedIDs = [firstHex, secondHex, thirdHex].makeIterator()
    let copied = UserDeckDraft(
      copyingOfficial: makeOfficialDeck(),
      uuidHexGenerator: { copiedIDs.next()! }
    )
    XCTAssertNoThrow(
      try requireUnchangedUserDeckSource(for: copied, installedVersion: nil)
    )
  }

  func testOfficialCopyGetsFreshIdentifiersAndDoesNotMutateSourceIdentity() {
    let source = makeOfficialDeck()
    var generated = [firstHex, secondHex, thirdHex].makeIterator()
    let draft = UserDeckDraft(
      copyingOfficial: source,
      at: Date(timeIntervalSince1970: 500),
      uuidHexGenerator: { generated.next()! }
    )

    let copied = draft.materializedDeck(at: Date(timeIntervalSince1970: 600))

    XCTAssertEqual(draft.derivedFromDeckID, source.deckId)
    XCTAssertEqual(copied.deckId, "user_\(firstHex)")
    XCTAssertNotEqual(copied.deckId, source.deckId)
    XCTAssertEqual(copied.items.map(\.id), ["item_\(secondHex)", "item_\(thirdHex)"])
    XCTAssertEqual(copied.version, 1)
    XCTAssertFalse(copied.official)
    XCTAssertEqual(copied.items[0].localizations, source.items[0].localizations)
    XCTAssertTrue(copied.items.allSatisfy { $0.audio == nil })
  }

  func testAddRemoveAndReorderKeepStableItemIdentifiers() {
    var generated = [firstHex, secondHex].makeIterator()
    var draft = UserDeckDraft(
      uuidHexGenerator: { generated.next()! }
    )
    draft.addItem(uuidHexGenerator: { thirdHex })

    XCTAssertEqual(draft.items.map(\.id), ["item_\(secondHex)", "item_\(thirdHex)"])

    draft.moveItems(fromOffsets: IndexSet(integer: 1), toOffset: 0)
    XCTAssertEqual(draft.items.map(\.id), ["item_\(thirdHex)", "item_\(secondHex)"])

    draft.removeItems(at: IndexSet(integer: 1))
    XCTAssertEqual(draft.items.map(\.id), ["item_\(thirdHex)"])

    draft.removeItems(at: IndexSet(integer: 0))
    XCTAssertEqual(draft.items.map(\.id), ["item_\(thirdHex)"])
  }

  func testValidationSummaryUsesLocalizationKeysInsteadOfValidatorMessages() {
    var generated = [firstHex, secondHex].makeIterator()
    let draft = UserDeckDraft(uuidHexGenerator: { generated.next()! })

    let summary = draft.validationSummary()

    XCTAssertTrue(summary.localizationKeys.allSatisfy { $0.hasPrefix("deck_editor.") })
    XCTAssertTrue(summary.localizationKeys.contains("deck_editor.validation.name_required"))
    XCTAssertTrue(summary.localizationKeys.contains("deck_editor.validation.author_required"))
    XCTAssertTrue(summary.localizationKeys.contains("deck_editor.validation.tags"))
    XCTAssertTrue(summary.localizationKeys.contains("deck_editor.validation.required"))
    XCTAssertEqual(summary.firstField, .name)
  }

  func testValidationSummaryTargetsInvalidItemField() {
    var draft = UserDeckDraft(editing: makeUserDeck(version: 1))
    draft.items[0].ko = ""

    let summary = draft.validationSummary(language: .japanese)

    XCTAssertEqual(summary.firstField, .itemKorean(0))
  }

  func testLargeDraftKeepsStableValidationTargetAtLastItem() {
    var generatedIndex = 0
    let generator = {
      generatedIndex += 1
      return String(format: "%032x", generatedIndex)
    }
    var draft = UserDeckDraft(uuidHexGenerator: generator)
    draft.name = "大きなデッキ"
    draft.authorNickname = "Piyo"
    draft.tags = ["large"]
    draft.items[0].ko = "가"
    draft.items[0].readingJa = "カ"
    draft.items[0].meaningJa = "文字"
    for _ in 1..<PiyoDeckPackageLimits.maximumItemCount {
      draft.addItem(uuidHexGenerator: generator)
      let index = draft.items.count - 1
      draft.items[index].ko = "가"
      draft.items[index].readingJa = "カ"
      draft.items[index].meaningJa = "文字"
    }
    draft.items[PiyoDeckPackageLimits.maximumItemCount - 1].meaningJa = ""

    let summary = draft.validationSummary(language: .japanese)

    XCTAssertEqual(
      summary.firstField,
      .itemMeaning(PiyoDeckPackageLimits.maximumItemCount - 1)
    )
  }

  func testValidationAddsDeckSchemaTextLengthBoundaries() {
    let original = makeUserDeck(version: 1)
    var draft = UserDeckDraft(editing: original)
    draft.name = String(repeating: "a", count: 121)

    XCTAssertThrowsError(try draft.validatedDeck()) { error in
      let validationError = error as? UserDeckDraftValidationError
      XCTAssertTrue(
        validationError?.issues.contains(where: {
          $0.code == "max_length" && $0.path == "name"
        }) == true)
    }
  }

  func testExistingLocalizationsArePreservedUntilAChangeMakesMetadataIncomplete() {
    let original = makeUserDeck(version: 2, includesEnglishLocalization: true)
    var draft = UserDeckDraft(editing: original)

    XCTAssertEqual(
      draft.materializedDeck(language: .japanese).localizations,
      original.localizations
    )
    XCTAssertEqual(
      draft.materializedDeck(language: .japanese).items[0].localizations,
      original.items[0].localizations
    )

    draft.addItem(uuidHexGenerator: { thirdHex })
    let withUntranslatedItem = draft.materializedDeck(language: .japanese)

    XCTAssertNil(withUntranslatedItem.localizations?["en"])
    XCTAssertEqual(withUntranslatedItem.items[0].localizations, original.items[0].localizations)
  }

  func testTagParserAcceptsWesternAndJapaneseSeparators() {
    XCTAssertEqual(
      UserDeckDraft.parseTags(" travel, TOPIK、daily\n beginner "),
      ["travel", "TOPIK", "daily", "beginner"]
    )
  }

  func testNewEnglishDraftStoresCurrentLanguageMetadataAndMirrorsRequiredBaseFields() throws {
    var generated = [firstHex, secondHex].makeIterator()
    var draft = UserDeckDraft(
      newAt: Date(timeIntervalSince1970: 100),
      uuidHexGenerator: { generated.next()! }
    )
    draft.setName("My Korean Deck", for: .english)
    draft.setAuthorNickname("Piyo", for: .english)
    draft.setTags(["travel"], for: .english)
    draft.items[0].ko = "여행"
    draft.items[0].setReading("yeohaeng", for: .english)
    draft.items[0].setMeaning("travel", for: .english)

    let deck = try draft.validatedDeck(
      at: Date(timeIntervalSince1970: 200),
      language: .english
    )

    XCTAssertEqual(deck.name, "My Korean Deck")
    XCTAssertEqual(deck.author.nickname, "Piyo")
    XCTAssertEqual(deck.tags, ["travel"])
    XCTAssertEqual(deck.items[0].readingJa, "yeohaeng")
    XCTAssertEqual(deck.items[0].meaningJa, "travel")
    XCTAssertEqual(deck.localizations?["en"]?.name, "My Korean Deck")
    XCTAssertEqual(deck.localizations?["en"]?.authorNickname, "Piyo")
    XCTAssertEqual(deck.localizations?["en"]?.tags, ["travel"])
    XCTAssertEqual(deck.items[0].localizations?["en"]?.reading, "yeohaeng")
    XCTAssertEqual(deck.items[0].localizations?["en"]?.meaning, "travel")
    XCTAssertEqual(deck.localizedName(languageCode: "en"), "My Korean Deck")
  }

  func testEditingKoreanLocalizationPreservesJapaneseBaseAndEnglishLocalization() throws {
    let original = makeMultilingualUserDeck()
    var draft = UserDeckDraft(editing: original)

    XCTAssertEqual(draft.name(for: .korean), "내 단어")
    XCTAssertEqual(draft.authorNickname(for: .korean), "피요")
    XCTAssertEqual(draft.tags(for: .korean), ["일상"])
    XCTAssertEqual(draft.items[0].reading(for: .korean), "안녕")
    XCTAssertEqual(draft.items[0].meaning(for: .korean), "인사")

    draft.setName("내 여행 단어", for: .korean)
    draft.setAuthorNickname("병아리", for: .korean)
    draft.setTags(["여행"], for: .korean)
    draft.items[0].setReading("안녕하세요", for: .korean)
    draft.items[0].setMeaning("정중한 인사", for: .korean)

    let edited = try draft.validatedDeck(language: .korean)

    XCTAssertEqual(edited.name, original.name)
    XCTAssertEqual(edited.author.nickname, original.author.nickname)
    XCTAssertEqual(edited.tags, original.tags)
    XCTAssertEqual(edited.items[0].readingJa, original.items[0].readingJa)
    XCTAssertEqual(edited.items[0].meaningJa, original.items[0].meaningJa)
    XCTAssertEqual(edited.localizations?["ko"]?.name, "내 여행 단어")
    XCTAssertEqual(edited.items[0].localizations?["ko"]?.meaning, "정중한 인사")
    XCTAssertEqual(edited.localizations?["en"], original.localizations?["en"])
    XCTAssertEqual(edited.items[0].localizations?["en"], original.items[0].localizations?["en"])
  }

  func testEditingMissingNonJapaneseLocalizationStartsEmptyAndRequiresCompleteTranslation() {
    let original = makeUserDeck(version: 2)
    var draft = UserDeckDraft(editing: original)

    XCTAssertEqual(draft.name(for: .english), "")
    XCTAssertEqual(draft.authorNickname(for: .english), "")
    XCTAssertEqual(draft.tags(for: .english), [])
    XCTAssertEqual(draft.items[0].reading(for: .english), "")
    XCTAssertEqual(draft.items[0].meaning(for: .english), "")

    draft.setName("My edited words", for: .english)
    draft.items[0].setMeaning("greeting", for: .english)

    let partial = draft.materializedDeck(language: .english)

    XCTAssertEqual(partial.name, original.name)
    XCTAssertEqual(partial.author.nickname, original.author.nickname)
    XCTAssertEqual(partial.tags, original.tags)
    XCTAssertEqual(partial.items[0].readingJa, original.items[0].readingJa)
    XCTAssertEqual(partial.items[0].meaningJa, original.items[0].meaningJa)
    XCTAssertEqual(partial.localizations?["en"]?.name, "My edited words")
    XCTAssertEqual(partial.localizations?["en"]?.authorNickname, "")
    XCTAssertEqual(partial.localizations?["en"]?.tags, [])
    XCTAssertEqual(partial.items[0].localizations?["en"]?.reading, "")
    XCTAssertEqual(partial.items[0].localizations?["en"]?.meaning, "greeting")
    XCTAssertThrowsError(try draft.validatedDeck(language: .english))
  }

  func testKoreanMetadataWithoutKoreanItemsIsPreservedUsingValidatorFallbackContract() throws {
    let original = makeKoreanMetadataWithEnglishItemsDeck()
    let draft = UserDeckDraft(editing: original)

    XCTAssertEqual(draft.name(for: .korean), "내 단어")
    XCTAssertEqual(draft.items[0].reading(for: .korean), "")
    XCTAssertEqual(draft.items[0].meaning(for: .korean), "")

    let edited = try draft.validatedDeck(language: .korean)

    XCTAssertEqual(edited.localizations?["ko"], original.localizations?["ko"])
    XCTAssertEqual(edited.localizations?["en"], original.localizations?["en"])
    XCTAssertNil(edited.items[0].localizations?["ko"])
    XCTAssertEqual(edited.items[0].localizations?["en"], original.items[0].localizations?["en"])
  }

  func testClearingCurrentLanguageMetadataFailsInsteadOfDroppingLocalization() {
    var draft = UserDeckDraft(editing: makeMultilingualUserDeck())
    draft.setName("", for: .korean)

    XCTAssertThrowsError(try draft.validatedDeck(language: .korean)) { error in
      let validationError = error as? UserDeckDraftValidationError
      XCTAssertTrue(
        validationError?.issues.contains(where: {
          $0.code == "required" && $0.path == "localizations.ko.name"
        }) == true)
    }
  }

  func testJapaneseEditingUsesBaseFieldsWithoutAddingLocalization() throws {
    var generated = [firstHex, secondHex].makeIterator()
    var draft = UserDeckDraft(
      uuidHexGenerator: { generated.next()! }
    )
    draft.setName("私の単語", for: .japanese)
    draft.setAuthorNickname("ピヨ", for: .japanese)
    draft.setTags(["日常"], for: .japanese)
    draft.items[0].ko = "안녕"
    draft.items[0].setReading("アンニョン", for: .japanese)
    draft.items[0].setMeaning("こんにちは", for: .japanese)

    let deck = try draft.validatedDeck(language: .japanese)

    XCTAssertEqual(deck.name, "私の単語")
    XCTAssertEqual(deck.items[0].readingJa, "アンニョン")
    XCTAssertEqual(deck.items[0].meaningJa, "こんにちは")
    XCTAssertNil(deck.localizations)
    XCTAssertNil(deck.items[0].localizations)
  }

  private func makeUserDeck(
    version: Int,
    includesEnglishLocalization: Bool = false
  ) -> Deck {
    let itemLocalizations: [String: DeckItemLocalization]? =
      includesEnglishLocalization
      ? ["en": DeckItemLocalization(meaning: "hello", reading: "annyeong")]
      : nil
    let metadataLocalizations: [String: DeckMetadataLocalization]? =
      includesEnglishLocalization
      ? [
        "en": DeckMetadataLocalization(
          name: "My words",
          authorNickname: "Piyo",
          tags: ["custom"]
        )
      ]
      : nil
    return Deck(
      deckId: "user_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      version: version,
      name: "マイ単語",
      author: DeckAuthor(id: UserDeckDraft.localAuthorID, nickname: "Piyo"),
      official: false,
      type: .word,
      level: 1,
      tags: ["custom"],
      createdAt: Date(timeIntervalSince1970: 100),
      updatedAt: Date(timeIntervalSince1970: 200),
      items: [
        DeckItem(
          id: "item_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          ko: "안녕",
          readingJa: "アンニョン",
          meaningJa: "こんにちは",
          audio: nil,
          localizations: itemLocalizations
        )
      ],
      localizations: metadataLocalizations
    )
  }

  private func makeOfficialDeck() -> Deck {
    Deck(
      deckId: "official_daily_words",
      version: 7,
      name: "毎日の単語",
      author: DeckAuthor(id: "official_piyokey", nickname: "PIYOKEY"),
      official: true,
      type: .word,
      level: 1,
      tags: ["daily"],
      createdAt: Date(timeIntervalSince1970: 10),
      updatedAt: Date(timeIntervalSince1970: 20),
      items: [
        DeckItem(
          id: "daily_001",
          ko: "안녕",
          readingJa: "アンニョン",
          meaningJa: "こんにちは",
          audio: "audio/daily_001.m4a",
          localizations: [
            "en": DeckItemLocalization(meaning: "hello", reading: "annyeong")
          ]
        ),
        DeckItem(
          id: "daily_002",
          ko: "학교",
          readingJa: "ハッキョ",
          meaningJa: "学校",
          audio: "audio/daily_002.m4a"
        ),
      ]
    )
  }

  private func makeMultilingualUserDeck() -> Deck {
    Deck(
      deckId: "user_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      version: 2,
      name: "私の単語",
      author: DeckAuthor(id: UserDeckDraft.localAuthorID, nickname: "ピヨ"),
      official: false,
      type: .word,
      level: 1,
      tags: ["日常"],
      createdAt: Date(timeIntervalSince1970: 100),
      updatedAt: Date(timeIntervalSince1970: 200),
      items: [
        DeckItem(
          id: "item_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          ko: "안녕",
          readingJa: "アンニョン",
          meaningJa: "こんにちは",
          audio: nil,
          localizations: [
            "en": DeckItemLocalization(meaning: "hello", reading: "annyeong"),
            "ko": DeckItemLocalization(meaning: "인사", reading: "안녕"),
          ]
        )
      ],
      localizations: [
        "en": DeckMetadataLocalization(name: "My Words", authorNickname: "Piyo", tags: ["daily"]),
        "ko": DeckMetadataLocalization(name: "내 단어", authorNickname: "피요", tags: ["일상"]),
      ]
    )
  }

  private func makeKoreanMetadataWithEnglishItemsDeck() -> Deck {
    Deck(
      deckId: "user_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      version: 2,
      name: "私の単語",
      author: DeckAuthor(id: UserDeckDraft.localAuthorID, nickname: "ピヨ"),
      official: false,
      type: .word,
      level: 1,
      tags: ["日常"],
      createdAt: Date(timeIntervalSince1970: 100),
      updatedAt: Date(timeIntervalSince1970: 200),
      items: [
        DeckItem(
          id: "item_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          ko: "안녕",
          readingJa: "アンニョン",
          meaningJa: "こんにちは",
          audio: nil,
          localizations: [
            "en": DeckItemLocalization(meaning: "hello", reading: "annyeong")
          ]
        )
      ],
      localizations: [
        "en": DeckMetadataLocalization(name: "My Words", authorNickname: "Piyo", tags: ["daily"]),
        "ko": DeckMetadataLocalization(name: "내 단어", authorNickname: "피요", tags: ["일상"]),
      ]
    )
  }
}
