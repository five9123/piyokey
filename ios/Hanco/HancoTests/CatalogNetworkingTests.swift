import DeckKit
import Foundation
import XCTest

@testable import Hanco

@MainActor
final class CatalogNetworkingTests: XCTestCase {
  private var temporaryDirectory: URL!
  private var cache: CatalogCacheStore!

  override func setUpWithError() throws {
    try super.setUpWithError()
    temporaryDirectory = FileManager.default.temporaryDirectory
      .appendingPathComponent(UUID().uuidString, isDirectory: true)
    cache = CatalogCacheStore(
      fileURL: temporaryDirectory.appendingPathComponent("catalog-cache.json")
    )
  }

  override func tearDownWithError() throws {
    try? FileManager.default.removeItem(at: temporaryDirectory)
    cache = nil
    temporaryDirectory = nil
    try super.tearDownWithError()
  }

  func testCatalogCacheRoundTripsValidatedJSONAndRejectsUnknownSchema() throws {
    let data = try BundleCatalogRepository().loadData()
    let fetchedAt = Date(timeIntervalSince1970: 1_700_000_000)
    let saved = try cache.save(
      data: data,
      validators: CatalogHTTPValidators(
        etag: "\"catalog-v1\"",
        lastModified: "Fri, 17 Jul 2026 00:00:00 GMT"
      ),
      fetchedAt: fetchedAt
    )

    XCTAssertEqual(saved.catalog.decks.count, 26)
    XCTAssertEqual(try cache.load(), saved)

    var object = try XCTUnwrap(
      JSONSerialization.jsonObject(with: Data(contentsOf: cache.fileURL))
        as? [String: Any]
    )
    object["schema_version"] = 999
    try JSONSerialization.data(withJSONObject: object).write(to: cache.fileURL)

    XCTAssertThrowsError(try cache.load()) { error in
      guard case CatalogCacheStoreError.unsupportedSchema(999) = error else {
        return XCTFail("Unexpected error: \(error)")
      }
    }
  }

  func testCorruptCatalogCacheRestoresLastValidatedCatalog() throws {
    let data = try BundleCatalogRepository().loadData()
    _ = try cache.save(
      data: data,
      validators: CatalogHTTPValidators(etag: "\"cached\"", lastModified: nil)
    )
    try Data("broken".utf8).write(to: cache.fileURL)

    let recovered = try XCTUnwrap(cache.load())

    XCTAssertEqual(recovered.catalog.decks.count, 26)
    XCTAssertEqual(recovered.record.etag, "\"cached\"")
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: RecoverableJSONFile.corruptURL(for: cache.fileURL).path
      )
    )
  }

  func testRemoteRefreshPersistsCatalogAndSendsConditionalHeadersFor304() async throws {
    let catalogURL = try XCTUnwrap(URL(string: "https://static.example/hanco/catalog.json"))
    let catalogData = try BundleCatalogRepository().loadData()
    let firstClient = StubHTTPDataClient { request in
      Self.response(
        for: request,
        statusCode: 200,
        headers: [
          "ETag": "\"catalog-v1\"",
          "Last-Modified": "Fri, 17 Jul 2026 00:00:00 GMT",
        ],
        data: catalogData
      )
    }
    let firstRepository = CachedRemoteCatalogRepository(
      cache: cache,
      client: CatalogHTTPClient(dataClient: firstClient),
      catalogURL: catalogURL,
      now: { Date(timeIntervalSince1970: 1_700_000_000) }
    )

    let refreshed = try await firstRepository.refreshCatalog()
    XCTAssertEqual(refreshed?.decks.count, 26)
    XCTAssertEqual(try cache.load()?.record.etag, "\"catalog-v1\"")

    let validationClient = StubHTTPDataClient { request in
      Self.response(
        for: request,
        statusCode: 304,
        headers: [:],
        data: Data()
      )
    }
    let validationDate = Date(timeIntervalSince1970: 1_700_000_600)
    let validationRepository = CachedRemoteCatalogRepository(
      cache: cache,
      client: CatalogHTTPClient(dataClient: validationClient),
      catalogURL: catalogURL,
      now: { validationDate }
    )

    let validationResult = try await validationRepository.refreshCatalog()
    XCTAssertNil(validationResult)
    let request = try XCTUnwrap(validationClient.requests.first)
    XCTAssertEqual(request.value(forHTTPHeaderField: "If-None-Match"), "\"catalog-v1\"")
    XCTAssertEqual(
      request.value(forHTTPHeaderField: "If-Modified-Since"),
      "Fri, 17 Jul 2026 00:00:00 GMT"
    )
    XCTAssertEqual(try cache.load()?.record.fetchedAt, validationDate)
  }

  func testCachedCatalogKeepsDiscoverLoadedWhenServerFails() async throws {
    let data = try BundleCatalogRepository().loadData()
    _ = try cache.save(
      data: data,
      validators: CatalogHTTPValidators(etag: "\"cached\"", lastModified: nil)
    )
    let client = StubHTTPDataClient { request in
      Self.response(for: request, statusCode: 503, headers: [:], data: Data())
    }
    let repository = CachedRemoteCatalogRepository(
      cache: cache,
      client: CatalogHTTPClient(dataClient: client),
      catalogURL: URL(string: "https://static.example/hanco/catalog.json")
    )
    let viewModel = DiscoverViewModel(repository: repository)

    let refreshTask = viewModel.loadIfNeeded()
    await refreshTask?.value

    XCTAssertEqual(viewModel.loadState, .loaded)
    XCTAssertEqual(viewModel.totalDeckCount, 26)
  }

  func testNewerBundledCatalogReplacesOlderPersistentCacheOnAppUpdate() throws {
    let bundledRepository = BundleCatalogRepository()
    let bundled = try bundledRepository.loadCatalog()
    var olderObject = try XCTUnwrap(
      JSONSerialization.jsonObject(with: bundledRepository.loadData()) as? [String: Any]
    )
    olderObject["catalog_version"] = bundled.catalogVersion - 1
    let olderData = try JSONSerialization.data(withJSONObject: olderObject)
    _ = try cache.save(
      data: olderData,
      validators: CatalogHTTPValidators(etag: "\"older\"", lastModified: nil)
    )

    let repository = CachedRemoteCatalogRepository(
      bundled: bundledRepository,
      cache: cache,
      catalogURL: nil
    )

    XCTAssertEqual(try repository.loadCatalog().catalogVersion, bundled.catalogVersion)
  }

  func testRemoteDeckSourceResolvesSafeRelativeURLAndValidatesPayload() async throws {
    let entry = try catalogEntry(id: "official_daily_words")
    let resourceRoot = try XCTUnwrap(Bundle.main.resourceURL)
    let data = try Data(contentsOf: resourceRoot.appendingPathComponent(entry.fileUrl))
    let client = StubHTTPDataClient { request in
      Self.response(for: request, statusCode: 200, headers: [:], data: data)
    }
    let source = RemoteDeckSource(
      configuration: StaticContentConfiguration(
        catalogURL: URL(string: "https://static.example/hanco/catalog.json")
      ),
      dataClient: client
    )

    let payload = try await source.fetch(entry)

    XCTAssertEqual(payload.deck.deckId, entry.deckId)
    XCTAssertEqual(payload.source, .remote)
    XCTAssertEqual(
      client.requests.first?.url?.absoluteString,
      "https://static.example/hanco/\(entry.fileUrl)"
    )
    XCTAssertEqual(
      client.requests.first?.value(forHTTPHeaderField: "Accept"),
      "application/json"
    )
    XCTAssertNil(
      source.configuration.deckURL(for: "decks/%2E%2E/secret.json")
    )
    XCTAssertNil(
      source.configuration.deckURL(for: "decks/valid.json?redirect=https://evil.example")
    )
  }

  func testNewerFixtureDrivesOneTapInstalledDeckUpdate() async throws {
    let bundledEntry = try catalogEntry(id: "official_daily_words")
    let bundledPayload = try await BundledMockDeckSource().fetch(bundledEntry)
    let installationStore = DeckInstallationStore(
      rootURL: temporaryDirectory.appendingPathComponent("installed-decks", isDirectory: true)
    )
    _ = try installationStore.install(data: bundledPayload.data, source: .remote)

    let updateRoot = try XCTUnwrap(Bundle.main.resourceURL)
      .appendingPathComponent("updates", isDirectory: true)
    let updateCatalog = try DeckKitJSON.decodeCatalog(
      from: Data(contentsOf: updateRoot.appendingPathComponent("catalog.json"))
    )
    XCTAssertTrue(CatalogValidator.validate(updateCatalog).isEmpty)
    let updateEntry = try XCTUnwrap(
      updateCatalog.decks.first { $0.deckId == bundledEntry.deckId }
    )
    let updateData = try Data(
      contentsOf: updateRoot.appendingPathComponent(updateEntry.fileUrl)
    )
    let client = StubHTTPDataClient { request in
      Self.response(for: request, statusCode: 200, headers: [:], data: updateData)
    }
    let source = RemoteDeckSource(
      configuration: StaticContentConfiguration(
        catalogURL: URL(string: "https://static.example/hanco/catalog.json")
      ),
      dataClient: client
    )
    let library = DeckLibrary(source: source, store: installationStore)

    XCTAssertEqual(updateEntry.version, bundledEntry.version + 1)
    XCTAssertTrue(library.needsUpdate(updateEntry))
    await library.install(updateEntry)

    XCTAssertEqual(library.installedDeck(bundledEntry.deckId)?.version, updateEntry.version)
    XCTAssertEqual(library.installedDeck(bundledEntry.deckId)?.items.count, 13)
    XCTAssertEqual(library.records[bundledEntry.deckId]?.source, .remote)
    XCTAssertFalse(library.needsUpdate(updateEntry))
  }

  private func catalogEntry(id: String) throws -> CatalogDeck {
    let catalog = try BundleCatalogRepository().loadCatalog()
    return try XCTUnwrap(catalog.decks.first { $0.deckId == id })
  }

  private static func response(
    for request: URLRequest,
    statusCode: Int,
    headers: [String: String],
    data: Data
  ) -> (Data, HTTPURLResponse) {
    let response = HTTPURLResponse(
      url: request.url!,
      statusCode: statusCode,
      httpVersion: "HTTP/1.1",
      headerFields: headers
    )!
    return (data, response)
  }
}

private final class StubHTTPDataClient: HTTPDataClient {
  private let handler: (URLRequest) throws -> (Data, HTTPURLResponse)
  private(set) var requests: [URLRequest] = []

  init(handler: @escaping (URLRequest) throws -> (Data, HTTPURLResponse)) {
    self.handler = handler
  }

  func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
    requests.append(request)
    return try handler(request)
  }
}
