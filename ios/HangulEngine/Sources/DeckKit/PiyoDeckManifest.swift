import Foundation

public enum PiyoDeckPackageLimits {
  public static let maximumPackageBytes = 8 * 1_024 * 1_024
  public static let maximumManifestBytes = 16 * 1_024
  public static let maximumDeckBytes = 4 * 1_024 * 1_024
  public static let maximumItemCount = 1_000
}

public struct PiyoDeckManifest: Codable, Equatable, Sendable {
  public static let formatIdentifier = "piyokey.deck-package"
  public static let currentFormatVersion = 1
  public static let currentDeckSchemaVersion = 2
  public static let supportedDeckSchemaVersions: Set<Int> = [1, 2]

  public let format: String
  public let formatVersion: Int
  public let deckSchemaVersion: Int
  public let deck: DeckDescriptor

  public init(
    format: String = Self.formatIdentifier,
    formatVersion: Int = Self.currentFormatVersion,
    deckSchemaVersion: Int = Self.currentDeckSchemaVersion,
    deck: DeckDescriptor
  ) {
    self.format = format
    self.formatVersion = formatVersion
    self.deckSchemaVersion = deckSchemaVersion
    self.deck = deck
  }

  public struct DeckDescriptor: Codable, Equatable, Sendable {
    public static let path = "deck.json"
    public static let mediaType = "application/json"

    public let path: String
    public let mediaType: String
    public let deckId: String
    public let deckVersion: Int
    public let itemCount: Int
    public let sizeBytes: Int
    public let sha256: String

    public init(
      path: String = Self.path,
      mediaType: String = Self.mediaType,
      deckId: String,
      deckVersion: Int,
      itemCount: Int,
      sizeBytes: Int,
      sha256: String
    ) {
      self.path = path
      self.mediaType = mediaType
      self.deckId = deckId
      self.deckVersion = deckVersion
      self.itemCount = itemCount
      self.sizeBytes = sizeBytes
      self.sha256 = sha256
    }

    private enum CodingKeys: String, CodingKey {
      case path
      case mediaType = "media_type"
      case deckId = "deck_id"
      case deckVersion = "deck_version"
      case itemCount = "item_count"
      case sizeBytes = "size_bytes"
      case sha256
    }
  }

  private enum CodingKeys: String, CodingKey {
    case format, deck
    case formatVersion = "format_version"
    case deckSchemaVersion = "deck_schema_version"
  }
}

public struct PiyoDeckPackage: Equatable, Sendable {
  public let manifest: PiyoDeckManifest
  public let deck: Deck
  public let deckData: Data

  public init(manifest: PiyoDeckManifest, deck: Deck, deckData: Data) {
    self.manifest = manifest
    self.deck = deck
    self.deckData = deckData
  }

  public var contentSHA256: String { manifest.deck.sha256 }
}
