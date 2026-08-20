import DeckKit
import Foundation

protocol CatalogRepository {
  func loadCatalog() throws -> Catalog
  func refreshCatalog() async throws -> Catalog?
}

enum CatalogRepositoryError: Error {
  case missingBundledCatalog
  case invalidCatalog([ContentValidationIssue])
  case notModifiedWithoutCache
}

struct BundleCatalogRepository: CatalogRepository {
  private let bundle: Bundle

  init(bundle: Bundle = .main) {
    self.bundle = bundle
  }

  func loadCatalog() throws -> Catalog {
    let data = try loadData()
    let catalog = try DeckKitJSON.decodeCatalog(from: data)
    let issues = CatalogValidator.validate(catalog)
    guard issues.isEmpty else {
      throw CatalogRepositoryError.invalidCatalog(issues)
    }
    return catalog
  }

  func refreshCatalog() async throws -> Catalog? {
    nil
  }

  func loadData() throws -> Data {
    guard let url = bundle.url(forResource: "catalog", withExtension: "json") else {
      throw CatalogRepositoryError.missingBundledCatalog
    }
    return try Data(contentsOf: url)
  }
}

struct CachedRemoteCatalogRepository: CatalogRepository {
  private let bundled: BundleCatalogRepository
  private let cache: CatalogCacheStore
  private let client: CatalogHTTPClient
  private let catalogURL: URL?
  private let now: () -> Date

  init(
    bundled: BundleCatalogRepository = BundleCatalogRepository(),
    cache: CatalogCacheStore = .live,
    client: CatalogHTTPClient = CatalogHTTPClient(),
    catalogURL: URL? = StaticContentConfiguration.live.catalogURL,
    now: @escaping () -> Date = Date.init
  ) {
    self.bundled = bundled
    self.cache = cache
    self.client = client
    self.catalogURL = catalogURL
    self.now = now
  }

  func loadCatalog() throws -> Catalog {
    let bundledCatalog = try bundled.loadCatalog()
    guard let cached = try? cache.load() else { return bundledCatalog }
    return cached.catalog.catalogVersion >= bundledCatalog.catalogVersion
      ? cached.catalog
      : bundledCatalog
  }

  func refreshCatalog() async throws -> Catalog? {
    guard let catalogURL else { return nil }
    let cached = try? cache.load()
    let validators = cached.map {
      CatalogHTTPValidators(
        etag: $0.record.etag,
        lastModified: $0.record.lastModified
      )
    }
    let response = try await client.fetchCatalog(at: catalogURL, validators: validators)

    switch response {
    case .modified(let data, let responseValidators):
      return try cache.save(
        data: data,
        validators: responseValidators,
        fetchedAt: now()
      ).catalog
    case .notModified(let responseValidators):
      guard let cached else {
        throw CatalogRepositoryError.notModifiedWithoutCache
      }
      _ = try cache.save(
        data: cached.record.jsonBlob,
        validators: responseValidators,
        fetchedAt: now()
      )
      return nil
    }
  }
}

enum CatalogRepositoryFactory {
  static func live() -> any CatalogRepository {
    CachedRemoteCatalogRepository()
  }
}
