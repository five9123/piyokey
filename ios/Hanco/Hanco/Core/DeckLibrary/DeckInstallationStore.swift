import CryptoKit
import DeckKit
import Foundation

enum InstalledDeckSource: String, Codable, Equatable {
  case bundle
  case remote
  case imported
  case created
}

struct InstalledDeckRecord: Codable, Equatable, Identifiable {
  let deckId: String
  let version: Int
  let installedAt: Date
  var lastPlayedAt: Date?
  let source: InstalledDeckSource
  let contentSHA256: String?
  let packageFormatVersion: Int?
  let isLocallyModified: Bool
  let derivedFromDeckId: String?

  var id: String { deckId }

  private enum CodingKeys: String, CodingKey {
    case deckId = "deck_id"
    case version
    case installedAt = "installed_at"
    case lastPlayedAt = "last_played_at"
    case source
    case contentSHA256 = "content_sha256"
    case packageFormatVersion = "package_format_version"
    case isLocallyModified = "is_locally_modified"
    case derivedFromDeckId = "derived_from_deck_id"
  }

  init(
    deckId: String,
    version: Int,
    installedAt: Date,
    lastPlayedAt: Date?,
    source: InstalledDeckSource,
    contentSHA256: String? = nil,
    packageFormatVersion: Int? = nil,
    isLocallyModified: Bool = false,
    derivedFromDeckId: String? = nil
  ) {
    self.deckId = deckId
    self.version = version
    self.installedAt = installedAt
    self.lastPlayedAt = lastPlayedAt
    self.source = source
    self.contentSHA256 = contentSHA256
    self.packageFormatVersion = packageFormatVersion
    self.isLocallyModified = isLocallyModified
    self.derivedFromDeckId = derivedFromDeckId
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    deckId = try container.decode(String.self, forKey: .deckId)
    version = try container.decode(Int.self, forKey: .version)
    installedAt = try container.decode(Date.self, forKey: .installedAt)
    lastPlayedAt = try container.decodeIfPresent(Date.self, forKey: .lastPlayedAt)
    source = try container.decode(InstalledDeckSource.self, forKey: .source)
    contentSHA256 = try container.decodeIfPresent(String.self, forKey: .contentSHA256)
    packageFormatVersion =
      try container.decodeIfPresent(Int.self, forKey: .packageFormatVersion)
    isLocallyModified =
      try container.decodeIfPresent(Bool.self, forKey: .isLocallyModified) ?? false
    derivedFromDeckId =
      try container.decodeIfPresent(String.self, forKey: .derivedFromDeckId)
  }
}

struct DeckInstallationSnapshot {
  var records: [String: InstalledDeckRecord]
  let decks: [String: Deck]
  var downloadHistory: [String: [String]]
  var removedRecords: [String: InstalledDeckRecord]
}

enum DeckInstallationStoreError: Error {
  case invalidDeck([ContentValidationIssue])
  case invalidIndex(String)
  case missingDeckFile(String)
  case sourceChanged(deckId: String, expectedVersion: Int, actualVersion: Int?)
  case unsupportedSchema(Int)
}

struct DeckInstallationStore {
  private enum TransactionOperation: String, Codable {
    case install
    case remove
  }

  private struct PendingTransaction: Codable {
    let schemaVersion: Int
    let operation: TransactionOperation
    let deckId: String

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case operation
      case deckId = "deck_id"
    }
  }

  private struct SchemaVersionProbe: Decodable {
    let schemaVersion: Int

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
    }
  }

  private struct Index: Codable {
    let schemaVersion: Int
    var records: [InstalledDeckRecord]
    var downloadHistory: [String: [String]]
    var removedRecords: [InstalledDeckRecord]

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case records
      case downloadHistory = "download_history"
      case removedRecords = "removed_records"
    }

    init(
      schemaVersion: Int,
      records: [InstalledDeckRecord],
      downloadHistory: [String: [String]],
      removedRecords: [InstalledDeckRecord]
    ) {
      self.schemaVersion = schemaVersion
      self.records = records
      self.downloadHistory = downloadHistory
      self.removedRecords = removedRecords
    }

    init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      schemaVersion = try container.decode(Int.self, forKey: .schemaVersion)
      records = try container.decode([InstalledDeckRecord].self, forKey: .records)
      downloadHistory =
        try container.decodeIfPresent([String: [String]].self, forKey: .downloadHistory) ?? [:]
      removedRecords =
        try container.decodeIfPresent([InstalledDeckRecord].self, forKey: .removedRecords) ?? []
    }
  }

  private static let currentSchemaVersion = 3

  let rootURL: URL
  private let fileManager: FileManager

  init(rootURL: URL, fileManager: FileManager = .default) {
    self.rootURL = rootURL
    self.fileManager = fileManager
  }

  static var live: DeckInstallationStore {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    return DeckInstallationStore(
      rootURL:
        applicationSupport
        .appendingPathComponent("Hanco", isDirectory: true)
        .appendingPathComponent("InstalledDecks", isDirectory: true)
    )
  }

  func loadSnapshot() throws -> DeckInstallationSnapshot {
    try recoverPendingTransactionIfNeeded()
    guard
      let index = try RecoverableJSONFile.load(
        from: indexURL,
        fileManager: fileManager,
        shouldRecover: shouldRecoverIndex,
        decode: decodeIndex
      )
    else {
      return DeckInstallationSnapshot(
        records: [:],
        decks: [:],
        downloadHistory: [:],
        removedRecords: [:]
      )
    }
    var records: [String: InstalledDeckRecord] = [:]
    var decks: [String: Deck] = [:]
    var removedCorruptDeck = false
    var removedRecords = Dictionary(
      uniqueKeysWithValues: index.removedRecords.map { ($0.deckId, $0) })

    for record in index.records {
      let url = deckURL(for: record.deckId)
      do {
        guard
          let deck = try RecoverableJSONFile.load(
            from: url,
            fileManager: fileManager,
            decode: { data in
              try decodeInstalledDeck(from: data, matching: record)
            }
          )
        else {
          removedRecords[record.deckId] = record
          removedCorruptDeck = true
          continue
        }
        records[record.deckId] = record
        decks[record.deckId] = deck
      } catch {
        removedRecords[record.deckId] = record
        removedCorruptDeck = true
      }
    }
    var history = index.downloadHistory
    for deck in decks.values where history[deck.deckId] == nil {
      history[deck.deckId] = deck.tags
    }
    if removedCorruptDeck {
      try writeIndex(
        Array(records.values),
        downloadHistory: history,
        removedRecords: Array(removedRecords.values)
      )
    }
    return DeckInstallationSnapshot(
      records: records,
      decks: decks,
      downloadHistory: history,
      removedRecords: removedRecords
    )
  }

  @discardableResult
  func install(
    data: Data,
    source: InstalledDeckSource,
    contentSHA256: String? = nil,
    packageFormatVersion: Int? = nil,
    isLocallyModified: Bool = false,
    derivedFromDeckId: String? = nil,
    expectedCurrentVersion: Int? = nil,
    now: Date = Date()
  ) throws
    -> InstalledDeckRecord
  {
    let deck = try DeckKitJSON.decodeDeck(from: data)
    let issues =
      source == .imported || source == .created
      ? UserDeckValidator.validate(deck)
      : DeckValidator.validate(deck)
    guard issues.isEmpty else {
      throw DeckInstallationStoreError.invalidDeck(issues)
    }
    if let contentSHA256, contentSHA256 != sha256Hex(data) {
      throw DeckInstallationStoreError.invalidIndex(
        "provided content SHA-256 does not match deck payload"
      )
    }

    var snapshot = try loadSnapshot()
    if let expectedCurrentVersion {
      let actualVersion = snapshot.records[deck.deckId]?.version
      guard actualVersion == expectedCurrentVersion else {
        throw DeckInstallationStoreError.sourceChanged(
          deckId: deck.deckId,
          expectedVersion: expectedCurrentVersion,
          actualVersion: actualVersion
        )
      }
    }
    try createRootIfNeeded()
    let previous = snapshot.records[deck.deckId] ?? snapshot.removedRecords[deck.deckId]
    let record = InstalledDeckRecord(
      deckId: deck.deckId,
      version: deck.version,
      installedAt: previous?.installedAt ?? now,
      lastPlayedAt: previous?.lastPlayedAt,
      source: source,
      contentSHA256: contentSHA256,
      packageFormatVersion: packageFormatVersion,
      isLocallyModified: isLocallyModified,
      derivedFromDeckId: derivedFromDeckId ?? previous?.derivedFromDeckId
    )
    snapshot.records[deck.deckId] = record
    snapshot.removedRecords.removeValue(forKey: deck.deckId)
    snapshot.downloadHistory[deck.deckId] = deck.tags
    let indexData = try encodeIndex(
      Array(snapshot.records.values),
      downloadHistory: snapshot.downloadHistory,
      removedRecords: Array(snapshot.removedRecords.values)
    )
    try stageTransaction(
      operation: .install,
      deckId: deck.deckId,
      deckData: data,
      indexData: indexData
    )
    try recoverPendingTransactionIfNeeded()
    return record
  }

  func remove(deckId: String) throws {
    try validateIdentifier(deckId)
    var snapshot = try loadSnapshot()
    if let removedRecord = snapshot.records.removeValue(forKey: deckId) {
      snapshot.removedRecords[deckId] = removedRecord
    }
    try createRootIfNeeded()
    let indexData = try encodeIndex(
      Array(snapshot.records.values),
      downloadHistory: snapshot.downloadHistory,
      removedRecords: Array(snapshot.removedRecords.values)
    )
    try stageTransaction(
      operation: .remove,
      deckId: deckId,
      deckData: nil,
      indexData: indexData
    )
    try recoverPendingTransactionIfNeeded()
  }

  @discardableResult
  func markPlayed(deckId: String, at date: Date = Date()) throws -> InstalledDeckRecord? {
    try validateIdentifier(deckId)
    try recoverPendingTransactionIfNeeded()
    // This hot path only changes index metadata. Loading the full snapshot would
    // decode and validate every installed deck payload twice around each session.
    guard
      var index = try RecoverableJSONFile.load(
        from: indexURL,
        fileManager: fileManager,
        shouldRecover: shouldRecoverIndex,
        decode: decodeIndex
      ),
      let recordIndex = index.records.firstIndex(where: { $0.deckId == deckId })
    else { return nil }
    var record = index.records[recordIndex]
    record.lastPlayedAt = date
    index.records[recordIndex] = record
    try writeIndex(
      index.records,
      downloadHistory: index.downloadHistory,
      removedRecords: index.removedRecords
    )
    return record
  }

  func reset() throws {
    if fileManager.fileExists(atPath: rootURL.path) {
      try fileManager.removeItem(at: rootURL)
    }
  }

  func data(for deckId: String) throws -> Data {
    try validateIdentifier(deckId)
    try recoverPendingTransactionIfNeeded()
    guard
      let index = try RecoverableJSONFile.load(
        from: indexURL,
        fileManager: fileManager,
        shouldRecover: shouldRecoverIndex,
        decode: decodeIndex
      ),
      let record = index.records.first(where: { $0.deckId == deckId })
    else {
      throw DeckInstallationStoreError.missingDeckFile(deckId)
    }
    let url = deckURL(for: deckId)
    guard
      let data = try RecoverableJSONFile.load(
        from: url,
        fileManager: fileManager,
        decode: { data in
          _ = try decodeInstalledDeck(from: data, matching: record)
          return data
        }
      )
    else {
      throw DeckInstallationStoreError.missingDeckFile(deckId)
    }
    return data
  }

  private var indexURL: URL {
    rootURL.appendingPathComponent("installed-decks.json")
  }

  private var transactionRootURL: URL {
    rootURL.appendingPathComponent("PendingTransaction", isDirectory: true)
  }

  private var transactionMarkerURL: URL {
    transactionRootURL.appendingPathComponent("transaction.json")
  }

  private var transactionDeckURL: URL {
    transactionRootURL.appendingPathComponent("deck.json")
  }

  private var transactionIndexURL: URL {
    transactionRootURL.appendingPathComponent("index.json")
  }

  private func deckURL(for deckId: String) -> URL {
    rootURL.appendingPathComponent("\(deckId).json")
  }

  private func decodeIndex(from data: Data) throws -> Index {
    let schemaVersion = try decoder.decode(SchemaVersionProbe.self, from: data).schemaVersion
    guard (1...Self.currentSchemaVersion).contains(schemaVersion) else {
      throw DeckInstallationStoreError.unsupportedSchema(schemaVersion)
    }
    let index = try decoder.decode(Index.self, from: data)
    let recordIDs = index.records.map(\.deckId)
    let removedIDs = index.removedRecords.map(\.deckId)
    guard Set(recordIDs).count == recordIDs.count else {
      throw DeckInstallationStoreError.invalidIndex("duplicate active deck IDs")
    }
    guard Set(removedIDs).count == removedIDs.count else {
      throw DeckInstallationStoreError.invalidIndex("duplicate removed deck IDs")
    }
    guard Set(recordIDs).isDisjoint(with: Set(removedIDs)) else {
      throw DeckInstallationStoreError.invalidIndex("active and removed deck IDs overlap")
    }
    for record in index.records + index.removedRecords {
      try validateIdentifier(record.deckId)
      guard record.version >= 1 else {
        throw DeckInstallationStoreError.invalidIndex("invalid deck version")
      }
      if let hash = record.contentSHA256,
        hash.range(of: "^[0-9a-f]{64}$", options: .regularExpression) == nil
      {
        throw DeckInstallationStoreError.invalidIndex("invalid content SHA-256")
      }
    }
    return index
  }

  private func decodeDeck(from data: Data) throws -> Deck {
    let deck = try DeckKitJSON.decodeDeck(from: data)
    let issues = DeckValidator.validate(deck)
    guard issues.isEmpty else {
      throw DeckInstallationStoreError.invalidDeck(issues)
    }
    return deck
  }

  private func decodeInstalledDeck(
    from data: Data,
    matching record: InstalledDeckRecord
  ) throws -> Deck {
    let deck = try decodeDeck(from: data)
    guard deck.deckId == record.deckId, deck.version == record.version else {
      throw DeckInstallationStoreError.invalidDeck([
        .init(
          code: "installation_metadata_mismatch",
          path: record.deckId,
          message: "설치 인덱스와 덱 ID 또는 버전이 일치하지 않습니다"
        )
      ])
    }
    if let expectedHash = record.contentSHA256, sha256Hex(data) != expectedHash {
      throw DeckInstallationStoreError.invalidDeck([
        .init(
          code: "installation_content_hash_mismatch",
          path: record.deckId,
          message: "설치 인덱스와 덱 콘텐츠 해시가 일치하지 않습니다"
        )
      ])
    }
    if record.source == .imported || record.source == .created {
      let userIssues = UserDeckValidator.validate(deck)
      guard userIssues.isEmpty else {
        throw DeckInstallationStoreError.invalidDeck(userIssues)
      }
    }
    return deck
  }

  private func sha256Hex(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
  }

  private var decoder: JSONDecoder {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601
    return decoder
  }

  private var encoder: JSONEncoder {
    let encoder = JSONEncoder()
    encoder.dateEncodingStrategy = .iso8601
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    return encoder
  }

  private func createRootIfNeeded() throws {
    try fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
  }

  private func shouldRecoverIndex(_ error: Error) -> Bool {
    guard let storeError = error as? DeckInstallationStoreError else { return true }
    if case .unsupportedSchema = storeError { return false }
    return true
  }

  private func validateIdentifier(_ value: String) throws {
    guard
      value.range(
        of: "^[a-z0-9][a-z0-9_-]{2,63}$",
        options: .regularExpression
      ) != nil
    else {
      throw DeckInstallationStoreError.invalidIndex("unsafe deck identifier")
    }
  }

  /// Persists the intended payload and index before touching either live file.
  /// The marker is written last, so an interrupted commit can be completed
  /// idempotently on the next store access without accepting a payload/index
  /// version split.
  private func stageTransaction(
    operation: TransactionOperation,
    deckId: String,
    deckData: Data?,
    indexData: Data
  ) throws {
    try validateIdentifier(deckId)
    guard !fileManager.fileExists(atPath: transactionRootURL.path) else {
      throw DeckInstallationStoreError.invalidIndex("another deck transaction is pending")
    }
    do {
      try fileManager.createDirectory(
        at: transactionRootURL,
        withIntermediateDirectories: true
      )
      try indexData.write(to: transactionIndexURL, options: .atomic)
      if let deckData {
        try deckData.write(to: transactionDeckURL, options: .atomic)
      }
      let marker = PendingTransaction(
        schemaVersion: 1,
        operation: operation,
        deckId: deckId
      )
      try encoder.encode(marker).write(to: transactionMarkerURL, options: .atomic)
    } catch {
      // No marker means no live file has been changed, so incomplete staging
      // is safe to discard immediately.
      if !fileManager.fileExists(atPath: transactionMarkerURL.path) {
        try? fileManager.removeItem(at: transactionRootURL)
      }
      throw error
    }
  }

  private func recoverPendingTransactionIfNeeded() throws {
    guard fileManager.fileExists(atPath: transactionRootURL.path) else { return }
    guard fileManager.fileExists(atPath: transactionMarkerURL.path) else {
      // The crash happened before the commit marker; live state was untouched.
      try fileManager.removeItem(at: transactionRootURL)
      return
    }

    let markerData = try Data(contentsOf: transactionMarkerURL)
    let transaction = try decoder.decode(PendingTransaction.self, from: markerData)
    guard transaction.schemaVersion == 1 else {
      throw DeckInstallationStoreError.invalidIndex("unsupported transaction schema")
    }
    try validateIdentifier(transaction.deckId)
    let indexData = try Data(contentsOf: transactionIndexURL)
    let index = try decodeIndex(from: indexData)

    switch transaction.operation {
    case .install:
      let deckData = try Data(contentsOf: transactionDeckURL)
      guard let record = index.records.first(where: { $0.deckId == transaction.deckId }) else {
        throw DeckInstallationStoreError.invalidIndex(
          "pending install has no active index record"
        )
      }
      _ = try decodeInstalledDeck(from: deckData, matching: record)
      try RecoverableJSONFile.write(
        deckData,
        to: deckURL(for: transaction.deckId),
        fileManager: fileManager
      )
      try RecoverableJSONFile.write(indexData, to: indexURL, fileManager: fileManager)

    case .remove:
      guard !index.records.contains(where: { $0.deckId == transaction.deckId }) else {
        throw DeckInstallationStoreError.invalidIndex(
          "pending removal still contains an active record"
        )
      }
      try RecoverableJSONFile.write(indexData, to: indexURL, fileManager: fileManager)
      try RecoverableJSONFile.removeArtifacts(
        for: deckURL(for: transaction.deckId),
        fileManager: fileManager
      )
    }

    try fileManager.removeItem(at: transactionRootURL)
  }

  private func encodeIndex(
    _ records: [InstalledDeckRecord],
    downloadHistory: [String: [String]],
    removedRecords: [InstalledDeckRecord]
  ) throws -> Data {
    let sorted = records.sorted { $0.deckId < $1.deckId }
    return try encoder.encode(
      Index(
        schemaVersion: Self.currentSchemaVersion,
        records: sorted,
        downloadHistory: downloadHistory,
        removedRecords: removedRecords.sorted { $0.deckId < $1.deckId }
      )
    )
  }

  private func writeIndex(
    _ records: [InstalledDeckRecord],
    downloadHistory: [String: [String]],
    removedRecords: [InstalledDeckRecord] = []
  ) throws {
    let data = try encodeIndex(
      records,
      downloadHistory: downloadHistory,
      removedRecords: removedRecords
    )
    try RecoverableJSONFile.write(data, to: indexURL, fileManager: fileManager)
  }
}
