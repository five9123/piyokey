import Foundation

public enum DeckType: String, Codable, Equatable, Hashable, Sendable {
  case word
  case sentence
}

public struct DeckAuthor: Codable, Equatable, Sendable {
  public let id: String
  public let nickname: String

  public init(id: String, nickname: String) {
    self.id = id
    self.nickname = nickname
  }
}

public struct DeckItemLocalization: Codable, Equatable, Sendable {
  public let meaning: String
  public let reading: String

  public init(meaning: String, reading: String) {
    self.meaning = meaning
    self.reading = reading
  }
}

public struct DeckMetadataLocalization: Codable, Equatable, Sendable {
  public let name: String
  public let authorNickname: String
  public let tags: [String]

  public init(name: String, authorNickname: String, tags: [String]) {
    self.name = name
    self.authorNickname = authorNickname
    self.tags = tags
  }

  private enum CodingKeys: String, CodingKey {
    case name, tags
    case authorNickname = "author_nickname"
  }
}

public struct DeckItem: Codable, Equatable, Sendable {
  public let id: String
  public let ko: String
  public let readingJa: String
  public let meaningJa: String
  public let audio: String?
  public let localizations: [String: DeckItemLocalization]?

  public init(
    id: String,
    ko: String,
    readingJa: String,
    meaningJa: String,
    audio: String?,
    localizations: [String: DeckItemLocalization]? = nil
  ) {
    self.id = id
    self.ko = ko
    self.readingJa = readingJa
    self.meaningJa = meaningJa
    self.audio = audio
    self.localizations = localizations
  }

  public func localizedMeaning(languageCode: String, defaultLocale: String? = nil) -> String? {
    localizedValue(languageCode: languageCode, defaultLocale: defaultLocale, keyPath: \.meaning)
      ?? meaningJa
  }

  public func localizedReading(languageCode: String, defaultLocale: String? = nil) -> String? {
    localizedValue(languageCode: languageCode, defaultLocale: defaultLocale, keyPath: \.reading)
      ?? readingJa
  }

  private func localizedValue(
    languageCode: String,
    defaultLocale: String?,
    keyPath: KeyPath<DeckItemLocalization, String>
  ) -> String? {
    for code in LocaleTag.lookupCandidates(requested: languageCode, defaultLocale: defaultLocale) {
      if let value = localizations?[code]?[keyPath: keyPath] { return value }
    }
    return nil
  }

  private enum CodingKeys: String, CodingKey {
    case id, ko, audio, localizations
    case readingJa = "reading_ja"
    case meaningJa = "meaning_ja"
  }
}

public struct Deck: Codable, Equatable, Sendable {
  public let deckId: String
  public let version: Int
  public let name: String
  public let author: DeckAuthor
  public let official: Bool
  public let type: DeckType
  public let level: Int
  public let tags: [String]
  public let defaultLocale: String?
  public let createdAt: Date
  public let updatedAt: Date
  public let items: [DeckItem]
  public let localizations: [String: DeckMetadataLocalization]?

  public init(
    deckId: String,
    version: Int,
    name: String,
    author: DeckAuthor,
    official: Bool,
    type: DeckType,
    level: Int,
    tags: [String],
    createdAt: Date,
    updatedAt: Date,
    items: [DeckItem],
    localizations: [String: DeckMetadataLocalization]? = nil,
    defaultLocale: String? = nil
  ) {
    self.deckId = deckId
    self.version = version
    self.name = name
    self.author = author
    self.official = official
    self.type = type
    self.level = level
    self.tags = tags
    self.createdAt = createdAt
    self.updatedAt = updatedAt
    self.items = items
    self.localizations = localizations
    self.defaultLocale = defaultLocale
  }

  public func localizedName(languageCode: String) -> String? {
    metadataLocalization(languageCode: languageCode)?.name ?? name
  }

  public func localizedAuthorNickname(languageCode: String) -> String? {
    metadataLocalization(languageCode: languageCode)?.authorNickname ?? author.nickname
  }

  public func localizedTags(languageCode: String) -> [String]? {
    metadataLocalization(languageCode: languageCode)?.tags ?? tags
  }

  public func hasLocalization(languageCode: String) -> Bool {
    localizedName(languageCode: languageCode) != nil
  }

  private func metadataLocalization(languageCode: String) -> DeckMetadataLocalization? {
    for code in LocaleTag.lookupCandidates(requested: languageCode, defaultLocale: defaultLocale) {
      if let value = localizations?[code] { return value }
    }
    return nil
  }

  private enum CodingKeys: String, CodingKey {
    case deckId = "deck_id"
    case version, name, author, official, type, level, tags, items, localizations
    case defaultLocale = "default_locale"
    case createdAt = "created_at"
    case updatedAt = "updated_at"
  }
}

public struct CatalogPreviewItem: Codable, Equatable, Sendable {
  public let ko: String
  public let meaningJa: String
  public let localizations: [String: CatalogPreviewItemLocalization]?

  public init(
    ko: String,
    meaningJa: String,
    localizations: [String: CatalogPreviewItemLocalization]? = nil
  ) {
    self.ko = ko
    self.meaningJa = meaningJa
    self.localizations = localizations
  }

  public func localizedMeaning(languageCode: String) -> String? {
    let code = normalizedLanguageCode(languageCode)
    guard code != "ja" else { return meaningJa }
    return localizations?[code]?.meaning ?? localizations?["en"]?.meaning
  }

  private enum CodingKeys: String, CodingKey {
    case ko, localizations
    case meaningJa = "meaning_ja"
  }
}

public struct CatalogPreviewItemLocalization: Codable, Equatable, Sendable {
  public let meaning: String

  public init(meaning: String) {
    self.meaning = meaning
  }
}

public struct CatalogDeck: Codable, Equatable, Sendable {
  public let deckId: String
  public let version: Int
  public let name: String
  public let authorNickname: String
  public let official: Bool
  public let featured: Bool
  public let type: DeckType
  public let level: Int
  public let tags: [String]
  public let itemCount: Int
  public let sizeBytes: Int
  public let downloadsTotal: Int
  public let downloads7d: Int
  public let createdAt: Date
  public let previewItems: [CatalogPreviewItem]
  public let fileUrl: String
  public let localizations: [String: DeckMetadataLocalization]?

  public init(
    deckId: String,
    version: Int,
    name: String,
    authorNickname: String,
    official: Bool,
    featured: Bool,
    type: DeckType,
    level: Int,
    tags: [String],
    itemCount: Int,
    sizeBytes: Int,
    downloadsTotal: Int,
    downloads7d: Int,
    createdAt: Date,
    previewItems: [CatalogPreviewItem],
    fileUrl: String,
    localizations: [String: DeckMetadataLocalization]? = nil
  ) {
    self.deckId = deckId
    self.version = version
    self.name = name
    self.authorNickname = authorNickname
    self.official = official
    self.featured = featured
    self.type = type
    self.level = level
    self.tags = tags
    self.itemCount = itemCount
    self.sizeBytes = sizeBytes
    self.downloadsTotal = downloadsTotal
    self.downloads7d = downloads7d
    self.createdAt = createdAt
    self.previewItems = previewItems
    self.fileUrl = fileUrl
    self.localizations = localizations
  }

  public func localizedName(languageCode: String) -> String? {
    let code = normalizedLanguageCode(languageCode)
    if code == "ja" { return name }
    return metadataLocalization(languageCode: code)?.name
  }

  public func localizedAuthorNickname(languageCode: String) -> String? {
    let code = normalizedLanguageCode(languageCode)
    if code == "ja" { return authorNickname }
    return metadataLocalization(languageCode: code)?.authorNickname
  }

  public func localizedTags(languageCode: String) -> [String]? {
    let code = normalizedLanguageCode(languageCode)
    if code == "ja" { return tags }
    return metadataLocalization(languageCode: code)?.tags
  }

  public func hasLocalization(languageCode: String) -> Bool {
    let code = normalizedLanguageCode(languageCode)
    return code == "ja" || localizations?[code] != nil || localizations?["en"] != nil
  }

  private func metadataLocalization(languageCode: String) -> DeckMetadataLocalization? {
    let code = normalizedLanguageCode(languageCode)
    guard code != "ja" else { return nil }
    return localizations?[code] ?? localizations?["en"]
  }

  public var trendingRatio: Double {
    Double(downloads7d) / Double(max(downloadsTotal - downloads7d, 1))
  }

  private enum CodingKeys: String, CodingKey {
    case deckId = "deck_id"
    case version, name, official, featured, type, level, tags, localizations
    case authorNickname = "author_nickname"
    case itemCount = "item_count"
    case sizeBytes = "size_bytes"
    case downloadsTotal = "downloads_total"
    case downloads7d = "downloads_7d"
    case createdAt = "created_at"
    case previewItems = "preview_items"
    case fileUrl = "file_url"
  }
}

public struct CatalogTag: Codable, Equatable, Sendable {
  public let tag: String
  public let deckCount: Int
  public let category: String
  public let localizations: [String: String]?

  public init(
    tag: String,
    deckCount: Int,
    category: String,
    localizations: [String: String]? = nil
  ) {
    self.tag = tag
    self.deckCount = deckCount
    self.category = category
    self.localizations = localizations
  }

  public func localizedTag(languageCode: String) -> String? {
    let code = normalizedLanguageCode(languageCode)
    guard code != "ja" else { return tag }
    return localizations?[code] ?? localizations?["en"]
  }

  private enum CodingKeys: String, CodingKey {
    case tag, category, localizations
    case deckCount = "deck_count"
  }
}

private func normalizedLanguageCode(_ identifier: String) -> String {
  identifier
    .replacingOccurrences(of: "_", with: "-")
    .split(separator: "-", maxSplits: 1)
    .first
    .map { String($0).lowercased() } ?? identifier.lowercased()
}

public struct Catalog: Codable, Equatable, Sendable {
  public let catalogVersion: Int
  public let generatedAt: Date
  public let decks: [CatalogDeck]
  public let tags: [CatalogTag]

  public init(catalogVersion: Int, generatedAt: Date, decks: [CatalogDeck], tags: [CatalogTag]) {
    self.catalogVersion = catalogVersion
    self.generatedAt = generatedAt
    self.decks = decks
    self.tags = tags
  }

  private enum CodingKeys: String, CodingKey {
    case catalogVersion = "catalog_version"
    case generatedAt = "generated_at"
    case decks, tags
  }
}

public enum DeckKitJSON {
  public static func makeDecoder() -> JSONDecoder {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601
    return decoder
  }

  public static func makeEncoder() -> JSONEncoder {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    return encoder
  }

  public static func decodeDeck(from data: Data) throws -> Deck {
    try makeDecoder().decode(Deck.self, from: data)
  }

  public static func decodeCatalog(from data: Data) throws -> Catalog {
    try makeDecoder().decode(Catalog.self, from: data)
  }
}
