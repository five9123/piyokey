import Combine
import DeckKit
import Foundation
import HangulEngine

struct ReviewDeckItem: Codable, Equatable, Identifiable {
  let itemId: String
  let sourceDeckId: String
  var ko: String
  var readingJa: String
  var meaningJa: String
  var localizations: [String: DeckItemLocalization]?
  var missCount: Int
  var consecutivePerfect: Int
  let addedAt: Date
  var graduatedAt: Date?
  var isSourceAvailable: Bool?

  var id: String { Self.id(itemId: itemId, sourceDeckId: sourceDeckId) }
  var isActive: Bool { graduatedAt == nil && isSourceAvailable != false }
  var deckItem: DeckItem {
    DeckItem(
      id: itemId,
      ko: ko,
      readingJa: readingJa,
      meaningJa: meaningJa,
      audio: nil,
      localizations: localizations
    )
  }

  init(item: DeckItem, sourceDeckId: String, addedAt: Date, missCount: Int) {
    self.itemId = item.id
    self.sourceDeckId = sourceDeckId
    self.ko = item.ko
    self.readingJa = item.readingJa
    self.meaningJa = item.meaningJa
    self.localizations = item.localizations
    self.missCount = missCount
    self.consecutivePerfect = 0
    self.addedAt = addedAt
    self.graduatedAt = nil
    self.isSourceAvailable = true
  }

  static func id(itemId: String, sourceDeckId: String) -> String {
    "\(sourceDeckId)::\(itemId)"
  }

  private enum CodingKeys: String, CodingKey {
    case itemId = "item_id"
    case sourceDeckId = "source_deck_id"
    case ko
    case readingJa = "reading_ja"
    case meaningJa = "meaning_ja"
    case localizations
    case missCount = "miss_count"
    case consecutivePerfect = "consecutive_perfect"
    case addedAt = "added_at"
    case graduatedAt = "graduated_at"
    case isSourceAvailable = "is_source_available"
  }
}

struct SessionItemResolution: Equatable {
  let itemIndex: Int
  let hadMistake: Bool
  let mistakeCount: Int
  let mistakenJamoIndices: Set<Int>
}

struct SessionReviewItem: Equatable, Identifiable {
  let item: DeckItem
  let sourceDeckId: String
  var mistakeCount: Int
  var mistakenJamoIndices: Set<Int>

  var id: String {
    ReviewDeckItem.id(itemId: item.id, sourceDeckId: sourceDeckId)
  }

  init(
    item: DeckItem,
    sourceDeckId: String,
    resolution: SessionItemResolution
  ) {
    self.item = item
    self.sourceDeckId = sourceDeckId
    self.mistakeCount = resolution.mistakeCount
    self.mistakenJamoIndices = resolution.mistakenJamoIndices
  }

  mutating func merge(_ resolution: SessionItemResolution) {
    mistakeCount += resolution.mistakeCount
    mistakenJamoIndices.formUnion(resolution.mistakenJamoIndices)
  }
}

struct ReviewDeckSnapshot: Equatable {
  var items: [String: ReviewDeckItem]

  static let empty = ReviewDeckSnapshot(items: [:])

  var activeItems: [ReviewDeckItem] {
    items.values
      .filter(\.isActive)
      .sorted {
        if $0.addedAt == $1.addedAt { return $0.id < $1.id }
        return $0.addedAt > $1.addedAt
      }
  }
}

enum ReviewDeckMutation: Equatable {
  case added
  case updated
  case graduated
  case removed
  case unchanged
}

enum ReviewDeckStoreError: Error, Equatable {
  case unsupportedSchema(Int)
  case invalidItem
}

struct ReviewDeckStore {
  private struct Index: Codable {
    let schemaVersion: Int
    var items: [ReviewDeckItem]

    private enum CodingKeys: String, CodingKey {
      case schemaVersion = "schema_version"
      case items
    }
  }

  private static let currentSchemaVersion = 1
  fileprivate static let graduationThreshold = 3

  let rootURL: URL
  private let fileManager: FileManager

  init(rootURL: URL, fileManager: FileManager = .default) {
    self.rootURL = rootURL
    self.fileManager = fileManager
  }

  static var live: ReviewDeckStore {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    return ReviewDeckStore(
      rootURL:
        applicationSupport
        .appendingPathComponent("Hanco", isDirectory: true)
        .appendingPathComponent("Review", isDirectory: true)
    )
  }

  func loadSnapshot() throws -> ReviewDeckSnapshot {
    try RecoverableJSONFile.load(
      from: indexURL,
      fileManager: fileManager,
      shouldRecover: shouldRecover,
      decode: decodeSnapshot
    ) ?? .empty
  }

  private func decodeSnapshot(from data: Data) throws -> ReviewDeckSnapshot {
    let index = try decoder.decode(Index.self, from: data)
    guard index.schemaVersion == Self.currentSchemaVersion else {
      throw ReviewDeckStoreError.unsupportedSchema(index.schemaVersion)
    }

    var items: [String: ReviewDeckItem] = [:]
    for item in index.items {
      guard !item.itemId.isEmpty, !item.sourceDeckId.isEmpty,
        item.ko.count <= 10, JamoDecomposer.containsHangul(in: item.ko),
        (try? JamoDecomposer.keySequence(for: item.ko)) != nil,
        item.missCount >= 0, (0...Self.graduationThreshold).contains(item.consecutivePerfect),
        item.graduatedAt == nil || item.consecutivePerfect == Self.graduationThreshold,
        items[item.id] == nil
      else { throw ReviewDeckStoreError.invalidItem }
      items[item.id] = item
    }
    return ReviewDeckSnapshot(items: items)
  }

  @discardableResult
  func recordMistake(
    item: DeckItem,
    sourceDeckId: String,
    at date: Date = Date()
  ) throws -> ReviewDeckMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applyMistake(
      item: item,
      sourceDeckId: sourceDeckId,
      at: date,
      to: &snapshot
    )
    try write(snapshot)
    return mutation
  }

  @discardableResult
  func recordPerfect(
    itemId: String,
    sourceDeckId: String,
    at date: Date = Date()
  ) throws -> ReviewDeckMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applyPerfect(
      itemId: itemId,
      sourceDeckId: sourceDeckId,
      at: date,
      to: &snapshot
    )
    guard mutation != .unchanged else { return mutation }
    try write(snapshot)
    return mutation
  }

  @discardableResult
  func addManually(
    item: DeckItem,
    sourceDeckId: String,
    at date: Date = Date()
  ) throws -> ReviewDeckMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applyManualAdd(
      item: item,
      sourceDeckId: sourceDeckId,
      at: date,
      to: &snapshot
    )
    guard mutation != .unchanged else { return mutation }
    try write(snapshot)
    return mutation
  }

  @discardableResult
  func removeManually(itemId: String, sourceDeckId: String) throws -> ReviewDeckMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applyManualRemove(
      itemId: itemId,
      sourceDeckId: sourceDeckId,
      to: &snapshot
    )
    guard mutation != .unchanged else { return mutation }
    try write(snapshot)
    return mutation
  }

  /// Keeps review history while removing every item from the active queue
  /// after its source deck payload has been deleted.
  @discardableResult
  func markSourceUnavailable(deckId: String) throws -> ReviewDeckMutation {
    var snapshot = try loadSnapshot()
    let mutation = Self.applySourceUnavailable(deckId: deckId, to: &snapshot)
    guard mutation != .unchanged else { return mutation }
    try write(snapshot)
    return mutation
  }

  func reset() throws {
    if fileManager.fileExists(atPath: rootURL.path) {
      try fileManager.removeItem(at: rootURL)
    }
  }

  private var indexURL: URL {
    rootURL.appendingPathComponent("review-deck.json")
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

  private func shouldRecover(_ error: Error) -> Bool {
    guard let error = error as? ReviewDeckStoreError else { return true }
    switch error {
    case .unsupportedSchema: return false
    case .invalidItem: return true
    }
  }

  fileprivate func persistSnapshot(_ snapshot: ReviewDeckSnapshot) throws {
    // Refuse to replace a primary or backup created by a newer app schema.
    _ = try loadSnapshot()
    try write(snapshot)
  }

  private func write(_ snapshot: ReviewDeckSnapshot) throws {
    try fileManager.createDirectory(at: rootURL, withIntermediateDirectories: true)
    let index = Index(
      schemaVersion: Self.currentSchemaVersion,
      items: snapshot.items.values.sorted { $0.id < $1.id }
    )
    try RecoverableJSONFile.write(
      encoder.encode(index),
      to: indexURL,
      fileManager: fileManager
    )
  }

  fileprivate static func applyMistake(
    item: DeckItem,
    sourceDeckId: String,
    at date: Date,
    to snapshot: inout ReviewDeckSnapshot
  ) -> ReviewDeckMutation {
    let id = ReviewDeckItem.id(itemId: item.id, sourceDeckId: sourceDeckId)
    if var existing = snapshot.items[id] {
      existing.ko = item.ko
      existing.readingJa = item.readingJa
      existing.meaningJa = item.meaningJa
      if let incoming = item.localizations {
        existing.localizations = (existing.localizations ?? [:]).merging(incoming) {
          _, latest in latest
        }
      }
      existing.missCount += 1
      existing.consecutivePerfect = 0
      existing.graduatedAt = nil
      existing.isSourceAvailable = true
      snapshot.items[id] = existing
      return .updated
    }

    snapshot.items[id] = ReviewDeckItem(
      item: item,
      sourceDeckId: sourceDeckId,
      addedAt: date,
      missCount: 1
    )
    return .added
  }

  fileprivate static func applyPerfect(
    itemId: String,
    sourceDeckId: String,
    at date: Date,
    to snapshot: inout ReviewDeckSnapshot
  ) -> ReviewDeckMutation {
    let id = ReviewDeckItem.id(itemId: itemId, sourceDeckId: sourceDeckId)
    guard var existing = snapshot.items[id], existing.isActive else { return .unchanged }

    existing.consecutivePerfect += 1
    let mutation: ReviewDeckMutation
    if existing.consecutivePerfect >= graduationThreshold {
      existing.consecutivePerfect = graduationThreshold
      existing.graduatedAt = date
      mutation = .graduated
    } else {
      mutation = .updated
    }
    snapshot.items[id] = existing
    return mutation
  }

  fileprivate static func applyManualAdd(
    item: DeckItem,
    sourceDeckId: String,
    at date: Date,
    to snapshot: inout ReviewDeckSnapshot
  ) -> ReviewDeckMutation {
    let id = ReviewDeckItem.id(itemId: item.id, sourceDeckId: sourceDeckId)
    if var existing = snapshot.items[id] {
      guard !existing.isActive else { return .unchanged }
      existing.consecutivePerfect = 0
      existing.graduatedAt = nil
      existing.isSourceAvailable = true
      snapshot.items[id] = existing
      return .updated
    }

    snapshot.items[id] = ReviewDeckItem(
      item: item,
      sourceDeckId: sourceDeckId,
      addedAt: date,
      missCount: 0
    )
    return .added
  }

  fileprivate static func applyManualRemove(
    itemId: String,
    sourceDeckId: String,
    to snapshot: inout ReviewDeckSnapshot
  ) -> ReviewDeckMutation {
    let id = ReviewDeckItem.id(itemId: itemId, sourceDeckId: sourceDeckId)
    guard snapshot.items.removeValue(forKey: id) != nil else { return .unchanged }
    return .removed
  }

  fileprivate static func applyDeckContent(
    _ deck: Deck,
    to snapshot: inout ReviewDeckSnapshot
  ) -> ReviewDeckMutation {
    let itemsByID = Dictionary(uniqueKeysWithValues: deck.items.map { ($0.id, $0) })
    var didChange = false
    for (id, stored) in snapshot.items where stored.sourceDeckId == deck.deckId {
      var updated = stored
      if let latest = itemsByID[stored.itemId] {
        updated.ko = latest.ko
        updated.readingJa = latest.readingJa
        updated.meaningJa = latest.meaningJa
        updated.localizations = latest.localizations
        updated.isSourceAvailable = true
      } else {
        updated.isSourceAvailable = false
      }
      guard updated != stored else { continue }
      snapshot.items[id] = updated
      didChange = true
    }
    return didChange ? .updated : .unchanged
  }

  fileprivate static func applySourceUnavailable(
    deckId: String,
    to snapshot: inout ReviewDeckSnapshot
  ) -> ReviewDeckMutation {
    var didChange = false
    for (id, stored) in snapshot.items
    where stored.sourceDeckId == deckId && stored.isSourceAvailable != false {
      var updated = stored
      updated.isSourceAvailable = false
      snapshot.items[id] = updated
      didChange = true
    }
    return didChange ? .updated : .unchanged
  }
}

private actor ReviewDeckWriter {
  private let store: ReviewDeckStore
  private var latestRequestedRevision = 0
  private var persistedRevision = 0

  init(store: ReviewDeckStore) {
    self.store = store
  }

  func persist(snapshot: ReviewDeckSnapshot, revision: Int) throws {
    guard revision >= latestRequestedRevision else { return }
    latestRequestedRevision = revision
    guard revision > persistedRevision else { return }
    try store.persistSnapshot(snapshot)
    persistedRevision = revision
  }
}

@MainActor
final class ReviewDeckLibrary: ObservableObject {
  @Published private(set) var items: [String: ReviewDeckItem] = [:]
  @Published private(set) var saveFailed = false

  private let store: ReviewDeckStore
  private let writer: ReviewDeckWriter
  private let debounceNanoseconds: UInt64
  private var retryAction: (() -> Void)?
  private var debounceTask: Task<Void, Never>?
  private var revision = 0

  init(
    store: ReviewDeckStore = .live,
    debounceNanoseconds: UInt64 = 750_000_000
  ) {
    self.store = store
    writer = ReviewDeckWriter(store: store)
    self.debounceNanoseconds = debounceNanoseconds
    reload()
  }

  var activeItems: [ReviewDeckItem] {
    ReviewDeckSnapshot(items: items).activeItems
  }

  @discardableResult
  func recordMistake(item: DeckItem, sourceDeckId: String) -> ReviewDeckMutation? {
    mutate(debounced: true) { snapshot in
      ReviewDeckStore.applyMistake(
        item: item,
        sourceDeckId: sourceDeckId,
        at: Date(),
        to: &snapshot
      )
    }
  }

  @discardableResult
  func recordPerfect(itemId: String, sourceDeckId: String) -> ReviewDeckMutation? {
    mutate(debounced: true) { snapshot in
      ReviewDeckStore.applyPerfect(
        itemId: itemId,
        sourceDeckId: sourceDeckId,
        at: Date(),
        to: &snapshot
      )
    }
  }

  @discardableResult
  func addManually(item: DeckItem, sourceDeckId: String) -> ReviewDeckMutation? {
    mutate(debounced: false) { snapshot in
      ReviewDeckStore.applyManualAdd(
        item: item,
        sourceDeckId: sourceDeckId,
        at: Date(),
        to: &snapshot
      )
    }
  }

  @discardableResult
  func removeManually(itemId: String, sourceDeckId: String) -> ReviewDeckMutation? {
    mutate(debounced: false) { snapshot in
      ReviewDeckStore.applyManualRemove(
        itemId: itemId,
        sourceDeckId: sourceDeckId,
        to: &snapshot
      )
    }
  }

  @discardableResult
  func reconcile(with deck: Deck) -> ReviewDeckMutation {
    mutate(debounced: false) { snapshot in
      ReviewDeckStore.applyDeckContent(deck, to: &snapshot)
    }
  }

  /// Applies deletion to the in-memory queue and waits for the retained
  /// history to reach disk. A failed write remains retryable through the
  /// library's existing save-failure state.
  @discardableResult
  func markSourceUnavailable(deckId: String) async -> ReviewDeckMutation {
    var snapshot = ReviewDeckSnapshot(items: items)
    let result = ReviewDeckStore.applySourceUnavailable(deckId: deckId, to: &snapshot)
    guard result != .unchanged else { return result }
    debounceTask?.cancel()
    debounceTask = nil
    revision += 1
    items = snapshot.items
    _ = await persist(snapshot: snapshot, revision: revision)
    return result
  }

  func flush() {
    debounceTask?.cancel()
    debounceTask = nil
    let snapshot = ReviewDeckSnapshot(items: items)
    let requestedRevision = revision
    Task { @MainActor [weak self] in
      _ = await self?.persist(snapshot: snapshot, revision: requestedRevision)
    }
  }

  @discardableResult
  func flushAndWait() async -> Bool {
    debounceTask?.cancel()
    debounceTask = nil
    return await persist(
      snapshot: ReviewDeckSnapshot(items: items),
      revision: revision
    )
  }

  func retryLastSave() {
    retryAction?()
  }

  private func mutate(
    debounced: Bool,
    mutation: (inout ReviewDeckSnapshot) -> ReviewDeckMutation
  ) -> ReviewDeckMutation {
    var snapshot = ReviewDeckSnapshot(items: items)
    let result = mutation(&snapshot)
    guard result != .unchanged else { return result }
    revision += 1
    items = snapshot.items
    if debounced {
      scheduleDebouncedFlush()
    } else {
      flush()
    }
    return result
  }

  private func scheduleDebouncedFlush() {
    debounceTask?.cancel()
    let snapshot = ReviewDeckSnapshot(items: items)
    let requestedRevision = revision
    let delay = debounceNanoseconds
    debounceTask = Task { @MainActor [weak self] in
      do {
        try await Task.sleep(nanoseconds: delay)
      } catch {
        return
      }
      guard let self, requestedRevision == self.revision else { return }
      self.debounceTask = nil
      _ = await self.persist(snapshot: snapshot, revision: requestedRevision)
    }
  }

  private func persist(snapshot: ReviewDeckSnapshot, revision requestedRevision: Int) async
    -> Bool
  {
    do {
      try await writer.persist(snapshot: snapshot, revision: requestedRevision)
      if requestedRevision == revision {
        saveFailed = false
        retryAction = nil
      }
      return true
    } catch {
      if requestedRevision == revision {
        saveFailed = true
        retryAction = { [weak self] in self?.flush() }
      }
      return false
    }
  }

  private func reload() {
    do {
      items = try store.loadSnapshot().items
      revision = 0
      saveFailed = false
      retryAction = nil
    } catch {
      items = [:]
      saveFailed = true
      retryAction = { [weak self] in self?.reload() }
    }
  }
}
