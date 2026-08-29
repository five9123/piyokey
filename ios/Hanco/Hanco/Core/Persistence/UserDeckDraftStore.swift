import DeckKit
import Foundation

/// The one unfinished Deck Maker document retained by the app.
///
/// R1.1 deliberately keeps a single active draft: starting another create,
/// edit, or official-copy flow replaces the previous active draft only after
/// the replacement has been written successfully.
struct ActiveUserDeckDraft: Equatable {
  let draftID: String
  let draft: UserDeckDraft
  let createdAt: Date
  let updatedAt: Date

  var baseDeckID: String? {
    switch draft.origin {
    case .new: return nil
    case .editing: return draft.deckID
    case .officialCopy(let sourceDeckID): return sourceDeckID
    }
  }

  var baseVersion: Int? {
    guard case .editing = draft.origin else { return nil }
    return draft.baseVersion
  }
}

enum UserDeckDraftStoreError: Error, Equatable {
  case unsupportedSchema(Int)
  case invalidEnvelope
  case invalidPayload
}

struct UserDeckDraftStore {
  typealias FileWriter = (Data, URL, FileManager) throws -> Void

  private struct Envelope: Codable {
    let schemaVersion: Int
    let draftID: String
    let baseDeckID: String?
    let baseVersion: Int?
    let createdAt: Date
    let updatedAt: Date
    let validatedJSONBlob: Data

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case draftID = "draft_id"
      case baseDeckID = "base_deck_id"
      case baseVersion = "base_version"
      case createdAt = "created_at"
      case updatedAt = "updated_at"
      case validatedJSONBlob = "validated_json_blob"
    }
  }

  fileprivate struct Payload: Codable {
    enum Origin: String, Codable {
      case new
      case editing
      case officialCopy = "official_copy"
    }

    let schemaVersion: Int
    let origin: Origin
    let sourceDeckID: String?
    let deckID: String
    let deckCreatedAt: Date
    let baseVersion: Int
    let authorID: String
    let metadataLocalizations: [String: DeckMetadataLocalization]?
    let defaultLocale: String?
    let name: String
    let authorNickname: String
    let type: DeckType
    let level: Int
    let tags: [String]
    let items: [Item]

    struct Item: Codable {
      let id: String
      let ko: String
      let readingJa: String
      let meaningJa: String
      let localizations: [String: DeckItemLocalization]?

      private enum CodingKeys: String, CodingKey {
        case id, ko, localizations
        case readingJa = "reading_ja"
        case meaningJa = "meaning_ja"
      }
    }

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case origin
      case sourceDeckID = "source_deck_id"
      case deckID = "deck_id"
      case deckCreatedAt = "deck_created_at"
      case baseVersion = "base_version"
      case authorID = "author_id"
      case metadataLocalizations = "metadata_localizations"
      case defaultLocale = "default_locale"
      case name
      case authorNickname = "author_nickname"
      case type, level, tags, items
    }
  }

  private static let currentSchemaVersion = 1
  private static let identifierPattern = "^[a-z]+_[0-9a-f]{32}$"

  let rootURL: URL
  private let fileManager: FileManager
  private let fileWriter: FileWriter

  init(
    rootURL: URL,
    fileManager: FileManager = .default,
    fileWriter: @escaping FileWriter = { data, url, fileManager in
      try RecoverableJSONFile.write(data, to: url, fileManager: fileManager)
    }
  ) {
    self.rootURL = rootURL
    self.fileManager = fileManager
    self.fileWriter = fileWriter
  }

  static var live: UserDeckDraftStore {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    return UserDeckDraftStore(
      rootURL:
        applicationSupport
        .appendingPathComponent("Hanco", isDirectory: true)
        .appendingPathComponent("DeckMaker", isDirectory: true)
    )
  }

  /// Loads the active draft, recovering a corrupt primary from its latest
  /// valid backup. A future schema is returned as an error and left untouched.
  func load() throws -> ActiveUserDeckDraft? {
    try RecoverableJSONFile.load(
      from: fileURL,
      fileManager: fileManager,
      shouldRecover: shouldRecover,
      decode: decode
    )
  }

  /// Atomically persists a complete, potentially not-yet-valid editor model.
  /// Repeated saves of the same editing flow retain the draft identity and its
  /// first-created timestamp. A different flow becomes the sole active draft.
  @discardableResult
  func save(
    _ draft: UserDeckDraft,
    at date: Date = Date()
  ) throws -> ActiveUserDeckDraft {
    // Reading first is intentional: it prevents an older build from replacing
    // an unsupported future-schema primary or backup.
    let current = try load()
    let isSameFlow =
      current.map { flowIdentity(of: $0.draft) == flowIdentity(of: draft) }
      ?? false
    let draftID = isSameFlow ? current!.draftID : Self.makeDraftID()
    let createdAt = isSameFlow ? current!.createdAt : date
    let updatedAt = max(date, createdAt)
    let active = ActiveUserDeckDraft(
      draftID: draftID,
      draft: draft,
      createdAt: createdAt,
      updatedAt: updatedAt
    )
    let payloadData = try encoder.encode(Payload(draft))
    let envelope = Envelope(
      schemaVersion: Self.currentSchemaVersion,
      draftID: active.draftID,
      baseDeckID: active.baseDeckID,
      baseVersion: active.baseVersion,
      createdAt: active.createdAt,
      updatedAt: active.updatedAt,
      validatedJSONBlob: payloadData
    )
    let envelopeData = try encoder.encode(envelope)
    // Return the same representation that a subsequent process launch will
    // decode (ISO-8601 persistence may normalize sub-second timestamps).
    let persisted = try decode(envelopeData)
    try fileWriter(envelopeData, fileURL, fileManager)
    return persisted
  }

  /// Clears only the draft that was actually committed as an installed deck.
  /// This avoids an older save completion deleting a newer editing flow.
  @discardableResult
  func clear(draftID: String) throws -> Bool {
    guard let active = try load(), active.draftID == draftID else { return false }
    try RecoverableJSONFile.removeArtifacts(for: fileURL, fileManager: fileManager)
    return true
  }

  /// Explicit user discard/reset. Save cancellation, entitlement changes, and
  /// app backgrounding must not call this overload.
  func clear() throws {
    try RecoverableJSONFile.removeArtifacts(for: fileURL, fileManager: fileManager)
  }

  private var fileURL: URL {
    rootURL.appendingPathComponent("active-user-deck-draft.json")
  }

  private var encoder: JSONEncoder {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    return encoder
  }

  private var decoder: JSONDecoder {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601
    return decoder
  }

  private func decode(_ data: Data) throws -> ActiveUserDeckDraft {
    let envelope = try decoder.decode(Envelope.self, from: data)
    guard envelope.schemaVersion == Self.currentSchemaVersion else {
      throw UserDeckDraftStoreError.unsupportedSchema(envelope.schemaVersion)
    }
    guard Self.isIdentifier(envelope.draftID, prefix: "draft_"),
      envelope.updatedAt >= envelope.createdAt
    else {
      throw UserDeckDraftStoreError.invalidEnvelope
    }

    let payload = try decoder.decode(Payload.self, from: envelope.validatedJSONBlob)
    guard payload.schemaVersion == Self.currentSchemaVersion else {
      throw UserDeckDraftStoreError.unsupportedSchema(payload.schemaVersion)
    }
    let draft = try payload.materialize()
    let active = ActiveUserDeckDraft(
      draftID: envelope.draftID,
      draft: draft,
      createdAt: envelope.createdAt,
      updatedAt: envelope.updatedAt
    )
    guard envelope.baseDeckID == active.baseDeckID,
      envelope.baseVersion == active.baseVersion
    else {
      throw UserDeckDraftStoreError.invalidEnvelope
    }
    return active
  }

  private func shouldRecover(_ error: Error) -> Bool {
    guard let error = error as? UserDeckDraftStoreError else { return true }
    if case .unsupportedSchema = error { return false }
    return true
  }

  private func flowIdentity(of draft: UserDeckDraft) -> String {
    switch draft.origin {
    case .new: return "new:\(draft.deckID)"
    case .editing: return "editing:\(draft.deckID)"
    case .officialCopy(let sourceDeckID):
      return "official-copy:\(sourceDeckID):\(draft.deckID)"
    }
  }

  private static func makeDraftID() -> String {
    "draft_" + UserDeckDraft.randomUUIDHex()
  }

  fileprivate static func isIdentifier(_ value: String, prefix: String) -> Bool {
    value.hasPrefix(prefix)
      && value.range(of: identifierPattern, options: .regularExpression) != nil
  }
}

extension UserDeckDraftStore.Payload {
  init(_ draft: UserDeckDraft) {
    let origin: Origin
    let sourceDeckID: String?
    switch draft.origin {
    case .new:
      origin = .new
      sourceDeckID = nil
    case .editing:
      origin = .editing
      sourceDeckID = nil
    case .officialCopy(let sourceID):
      origin = .officialCopy
      sourceDeckID = sourceID
    }
    self.init(
      schemaVersion: 1,
      origin: origin,
      sourceDeckID: sourceDeckID,
      deckID: draft.deckID,
      deckCreatedAt: draft.createdAt,
      baseVersion: draft.baseVersion,
      authorID: draft.authorID,
      metadataLocalizations: draft.metadataLocalizations,
      defaultLocale: draft.defaultLocale,
      name: draft.name,
      authorNickname: draft.authorNickname,
      type: draft.type,
      level: draft.level,
      tags: draft.tags,
      items: draft.items.map(Item.init)
    )
  }

  func materialize() throws -> UserDeckDraft {
    guard UserDeckDraftStore.isIdentifier(deckID, prefix: "user_"),
      !authorID.isEmpty,
      (1...PiyoDeckPackageLimits.maximumItemCount).contains(items.count),
      Set(items.map(\.id)).count == items.count,
      items.allSatisfy({ UserDeckDraftStore.isIdentifier($0.id, prefix: "item_") })
    else {
      throw UserDeckDraftStoreError.invalidPayload
    }

    let draftOrigin: UserDeckDraft.Origin
    switch origin {
    case .new:
      guard sourceDeckID == nil, baseVersion == 0 else {
        throw UserDeckDraftStoreError.invalidPayload
      }
      draftOrigin = .new
    case .editing:
      guard sourceDeckID == nil, baseVersion >= 1 else {
        throw UserDeckDraftStoreError.invalidPayload
      }
      draftOrigin = .editing
    case .officialCopy:
      guard let sourceDeckID, !sourceDeckID.isEmpty, baseVersion == 0 else {
        throw UserDeckDraftStoreError.invalidPayload
      }
      draftOrigin = .officialCopy(sourceDeckID: sourceDeckID)
    }

    return UserDeckDraft(
      persistedOrigin: draftOrigin,
      deckID: deckID,
      createdAt: deckCreatedAt,
      baseVersion: baseVersion,
      authorID: authorID,
      metadataLocalizations: metadataLocalizations,
      defaultLocale: defaultLocale,
      name: name,
      authorNickname: authorNickname,
      type: type,
      level: level,
      tags: tags,
      items: items.map(\.materialized)
    )
  }
}

extension UserDeckDraftStore.Payload.Item {
  init(_ item: UserDeckItemDraft) {
    self.init(
      id: item.id,
      ko: item.ko,
      readingJa: item.readingJa,
      meaningJa: item.meaningJa,
      localizations: item.localizations
    )
  }

  var materialized: UserDeckItemDraft {
    UserDeckItemDraft(
      id: id,
      ko: ko,
      readingJa: readingJa,
      meaningJa: meaningJa,
      localizations: localizations
    )
  }
}

extension UserDeckDraft {
  fileprivate init(
    persistedOrigin: Origin,
    deckID: String,
    createdAt: Date,
    baseVersion: Int,
    authorID: String,
    metadataLocalizations: [String: DeckMetadataLocalization]?,
    defaultLocale: String?,
    name: String,
    authorNickname: String,
    type: DeckType,
    level: Int,
    tags: [String],
    items: [UserDeckItemDraft]
  ) {
    origin = persistedOrigin
    self.deckID = deckID
    self.createdAt = createdAt
    self.baseVersion = baseVersion
    self.authorID = authorID
    self.metadataLocalizations = metadataLocalizations
    self.defaultLocale = defaultLocale
    self.name = name
    self.authorNickname = authorNickname
    self.type = type
    self.level = level
    self.tags = tags
    self.items = items
  }
}
