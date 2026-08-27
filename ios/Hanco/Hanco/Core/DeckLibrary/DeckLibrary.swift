import DeckKit
import Foundation

enum PiyokeyProAccessError: Error, Equatable {
  case freeUserDeckLimitReached
}

enum PiyokeyProPolicy {
  static let freeInstalledUserDeckLimit = 3
}

private actor DeckInstallationWriter {
  private let store: DeckInstallationStore

  init(store: DeckInstallationStore) {
    self.store = store
  }

  func install(
    data: Data,
    source: InstalledDeckSource,
    contentSHA256: String? = nil,
    packageFormatVersion: Int? = nil,
    isLocallyModified: Bool = false,
    derivedFromDeckId: String? = nil,
    expectedCurrentVersion: Int? = nil
  ) throws -> InstalledDeckRecord {
    try store.install(
      data: data,
      source: source,
      contentSHA256: contentSHA256,
      packageFormatVersion: packageFormatVersion,
      isLocallyModified: isLocallyModified,
      derivedFromDeckId: derivedFromDeckId,
      expectedCurrentVersion: expectedCurrentVersion
    )
  }

  func data(for deckId: String) throws -> Data {
    try store.data(for: deckId)
  }

  func remove(deckId: String) throws {
    try store.remove(deckId: deckId)
  }

  func markPlayed(deckId: String) throws -> InstalledDeckRecord? {
    try store.markPlayed(deckId: deckId)
  }
}

@MainActor
final class DeckLibrary: ObservableObject {
  @Published private(set) var installedDecks: [String: Deck] = [:]
  @Published private(set) var records: [String: InstalledDeckRecord] = [:]
  @Published private(set) var installingDeckIDs: Set<String> = []
  @Published private(set) var failedDeckIDs: Set<String> = []
  @Published private(set) var downloadHistory: [String: [String]] = [:]

  private var installingNewUserDeckIDs: Set<String> = []

  private let source: any DeckSource
  private let store: DeckInstallationStore
  private let writer: DeckInstallationWriter

  init(
    source: any DeckSource = StaticDeckSource(),
    store: DeckInstallationStore = .live
  ) {
    self.source = source
    self.store = store
    writer = DeckInstallationWriter(store: store)
    reload()
  }

  var installed: [Deck] {
    installedDecks.values.sorted { lhs, rhs in
      let lhsDate =
        records[lhs.deckId]?.lastPlayedAt ?? records[lhs.deckId]?.installedAt ?? .distantPast
      let rhsDate =
        records[rhs.deckId]?.lastPlayedAt ?? records[rhs.deckId]?.installedAt ?? .distantPast
      if lhsDate == rhsDate { return lhs.appName < rhs.appName }
      return lhsDate > rhsDate
    }
  }

  var installedUserDeckCount: Int {
    records.values.filter { $0.source == .imported || $0.source == .created }.count
  }

  func canInstallNewUserDeck(hasPiyokeyProAccess: Bool) -> Bool {
    hasPiyokeyProAccess
      || installedUserDeckCount + installingNewUserDeckIDs.count
        < PiyokeyProPolicy.freeInstalledUserDeckLimit
  }

  func isInstalled(_ deckId: String) -> Bool {
    installedDecks[deckId] != nil
  }

  func installedDeck(_ deckId: String) -> Deck? {
    installedDecks[deckId]
  }

  func needsUpdate(_ entry: CatalogDeck) -> Bool {
    guard let installed = installedDecks[entry.deckId] else { return false }
    return installed.version < entry.version
  }

  func install(_ entry: CatalogDeck) async {
    guard !installingDeckIDs.contains(entry.deckId) else { return }
    installingDeckIDs.insert(entry.deckId)
    failedDeckIDs.remove(entry.deckId)
    await Task.yield()

    do {
      let payload = try await source.fetch(entry)
      let record = try await writer.install(data: payload.data, source: payload.source)
      installedDecks[entry.deckId] = payload.deck
      records[entry.deckId] = record
      downloadHistory[entry.deckId] = payload.deck.tags
    } catch {
      failedDeckIDs.insert(entry.deckId)
    }
    installingDeckIDs.remove(entry.deckId)
  }

  @discardableResult
  func installUserDeck(
    data: Data,
    source: InstalledDeckSource,
    contentSHA256: String,
    packageFormatVersion: Int,
    isLocallyModified: Bool,
    hasPiyokeyProAccess: Bool,
    derivedFromDeckId: String? = nil,
    expectedCurrentVersion: Int? = nil
  ) async throws -> Deck {
    precondition(source == .imported || source == .created)
    let deck = try DeckKitJSON.decodeDeck(from: data)
    let userDeckIssues = UserDeckValidator.validate(deck)
    guard userDeckIssues.isEmpty else {
      throw PiyoDeckImportError.invalidUserDeck(userDeckIssues)
    }
    let reservesNewUserDeckSlot = records[deck.deckId] == nil
      && !installingNewUserDeckIDs.contains(deck.deckId)
    if reservesNewUserDeckSlot,
      !canInstallNewUserDeck(hasPiyokeyProAccess: hasPiyokeyProAccess)
    {
      throw PiyokeyProAccessError.freeUserDeckLimitReached
    }
    if reservesNewUserDeckSlot { installingNewUserDeckIDs.insert(deck.deckId) }
    defer {
      if reservesNewUserDeckSlot { installingNewUserDeckIDs.remove(deck.deckId) }
    }
    let record = try await writer.install(
      data: data,
      source: source,
      contentSHA256: contentSHA256,
      packageFormatVersion: packageFormatVersion,
      isLocallyModified: isLocallyModified,
      derivedFromDeckId: derivedFromDeckId,
      expectedCurrentVersion: expectedCurrentVersion
    )
    installedDecks[deck.deckId] = deck
    records[deck.deckId] = record
    downloadHistory[deck.deckId] = deck.tags
    failedDeckIDs.remove(deck.deckId)
    return deck
  }

  func exportData(for deckId: String) async throws -> Data {
    try await writer.data(for: deckId)
  }

  func remove(_ deckId: String) {
    Task { @MainActor [weak self] in
      guard let self else { return }
      do {
        try await removeAndWait(deckId)
      } catch {
        failedDeckIDs.insert(deckId)
      }
    }
  }

  func removeAndWait(_ deckId: String) async throws {
    try await writer.remove(deckId: deckId)
    installedDecks.removeValue(forKey: deckId)
    records.removeValue(forKey: deckId)
    failedDeckIDs.remove(deckId)
  }

  func markPlayed(_ deckId: String) {
    Task { @MainActor [weak self] in
      guard let self else { return }
      do {
        if let updatedRecord = try await writer.markPlayed(deckId: deckId) {
          records[deckId] = updatedRecord
        }
      } catch {
        failedDeckIDs.insert(deckId)
      }
    }
  }

  private func reload() {
    do {
      let snapshot = try store.loadSnapshot()
      records = snapshot.records
      installedDecks = snapshot.decks
      downloadHistory = snapshot.downloadHistory
    } catch {
      records = [:]
      installedDecks = [:]
      downloadHistory = [:]
    }
  }
}
