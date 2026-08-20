import DeckKit
import Foundation

struct DeckDownloadPayload {
  let data: Data
  let deck: Deck
  let source: InstalledDeckSource
}

protocol DeckSource {
  func fetch(_ catalogDeck: CatalogDeck) async throws -> DeckDownloadPayload
}

enum DeckSourceError: Error {
  case missingResource(String)
  case unsafePath(String)
  case invalidDeck([ContentValidationIssue])
  case metadataMismatch
}

struct BundledMockDeckSource: DeckSource {
  private let bundle: Bundle

  init(bundle: Bundle = .main) {
    self.bundle = bundle
  }

  func fetch(_ catalogDeck: CatalogDeck) async throws -> DeckDownloadPayload {
    guard !catalogDeck.fileUrl.contains(".."), catalogDeck.fileUrl.hasPrefix("decks/") else {
      throw DeckSourceError.unsafePath(catalogDeck.fileUrl)
    }
    guard let resourceRoot = bundle.resourceURL else {
      throw DeckSourceError.missingResource(catalogDeck.fileUrl)
    }

    let url = resourceRoot.appendingPathComponent(catalogDeck.fileUrl).standardizedFileURL
    guard url.path.hasPrefix(resourceRoot.standardizedFileURL.path + "/") else {
      throw DeckSourceError.unsafePath(catalogDeck.fileUrl)
    }
    guard FileManager.default.fileExists(atPath: url.path) else {
      throw DeckSourceError.missingResource(catalogDeck.fileUrl)
    }

    let data = try Data(contentsOf: url)
    return try validatedPayload(
      data: data,
      catalogDeck: catalogDeck,
      source: catalogDeck.official ? .bundle : .remote
    )
  }
}

struct RemoteDeckSource: DeckSource {
  let configuration: StaticContentConfiguration
  let dataClient: any HTTPDataClient

  init(
    configuration: StaticContentConfiguration,
    dataClient: any HTTPDataClient = URLSessionHTTPDataClient()
  ) {
    self.configuration = configuration
    self.dataClient = dataClient
  }

  func fetch(_ catalogDeck: CatalogDeck) async throws -> DeckDownloadPayload {
    guard let url = configuration.deckURL(for: catalogDeck.fileUrl) else {
      throw StaticContentHTTPError.unsafeDeckURL(catalogDeck.fileUrl)
    }
    var request = URLRequest(
      url: url,
      cachePolicy: .reloadIgnoringLocalCacheData,
      timeoutInterval: 30
    )
    request.httpMethod = "GET"
    request.setValue("application/json", forHTTPHeaderField: "Accept")

    let (data, response) = try await dataClient.data(for: request)
    guard (200...299).contains(response.statusCode) else {
      throw StaticContentHTTPError.statusCode(response.statusCode)
    }
    return try validatedPayload(data: data, catalogDeck: catalogDeck, source: .remote)
  }
}

struct StaticDeckSource: DeckSource {
  private let bundled: BundledMockDeckSource
  private let remote: RemoteDeckSource?

  init(
    bundled: BundledMockDeckSource = BundledMockDeckSource(),
    configuration: StaticContentConfiguration = .live,
    dataClient: any HTTPDataClient = URLSessionHTTPDataClient()
  ) {
    self.bundled = bundled
    if configuration.catalogURL != nil {
      self.remote = RemoteDeckSource(configuration: configuration, dataClient: dataClient)
    } else {
      self.remote = nil
    }
  }

  func fetch(_ catalogDeck: CatalogDeck) async throws -> DeckDownloadPayload {
    if catalogDeck.official {
      do {
        return try await bundled.fetch(catalogDeck)
      } catch DeckSourceError.metadataMismatch {
        guard let remote else { throw DeckSourceError.metadataMismatch }
        return try await remote.fetch(catalogDeck)
      } catch DeckSourceError.missingResource(let path) {
        guard let remote else { throw DeckSourceError.missingResource(path) }
        return try await remote.fetch(catalogDeck)
      }
    }
    if let remote {
      return try await remote.fetch(catalogDeck)
    }
    return try await bundled.fetch(catalogDeck)
  }
}

private func validatedPayload(
  data: Data,
  catalogDeck: CatalogDeck,
  source: InstalledDeckSource
) throws -> DeckDownloadPayload {
  let deck = try DeckKitJSON.decodeDeck(from: data)
  let issues = DeckValidator.validate(deck)
  guard issues.isEmpty else {
    throw DeckSourceError.invalidDeck(issues)
  }
  guard metadataMatches(catalogDeck, deck: deck) else {
    throw DeckSourceError.metadataMismatch
  }
  return DeckDownloadPayload(data: data, deck: deck, source: source)
}

private func metadataMatches(_ entry: CatalogDeck, deck: Deck) -> Bool {
  entry.deckId == deck.deckId
    && entry.version == deck.version
    && entry.name == deck.name
    && entry.authorNickname == deck.author.nickname
    && entry.official == deck.official
    && entry.type == deck.type
    && entry.level == deck.level
    && entry.tags == deck.tags
    && entry.localizations == deck.localizations
    && entry.itemCount == deck.items.count
}
