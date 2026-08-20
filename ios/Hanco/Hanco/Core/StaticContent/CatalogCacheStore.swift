import DeckKit
import Foundation

struct CatalogCacheRecord: Codable, Equatable {
  let schemaVersion: Int
  let catalogVersion: Int
  let fetchedAt: Date
  let etag: String?
  let lastModified: String?
  let jsonBlob: Data

  private enum CodingKeys: String, CodingKey {
    case schemaVersion = "schema_version"
    case catalogVersion = "catalog_version"
    case fetchedAt = "fetched_at"
    case etag
    case lastModified = "last_modified"
    case jsonBlob = "json_blob"
  }
}

struct CatalogCacheSnapshot: Equatable {
  let record: CatalogCacheRecord
  let catalog: Catalog
}

enum CatalogCacheStoreError: Error {
  case unsupportedSchema(Int)
  case catalogVersionMismatch
  case invalidCatalog([ContentValidationIssue])
}

struct CatalogCacheStore {
  private static let currentSchemaVersion = 1

  let fileURL: URL
  private let fileManager: FileManager

  init(fileURL: URL, fileManager: FileManager = .default) {
    self.fileURL = fileURL
    self.fileManager = fileManager
  }

  static var live: CatalogCacheStore {
    let applicationSupport = FileManager.default.urls(
      for: .applicationSupportDirectory,
      in: .userDomainMask
    )[0]
    return CatalogCacheStore(
      fileURL:
        applicationSupport
        .appendingPathComponent("Hanco", isDirectory: true)
        .appendingPathComponent("CatalogCache", isDirectory: true)
        .appendingPathComponent("catalog-cache.json")
    )
  }

  func load() throws -> CatalogCacheSnapshot? {
    try RecoverableJSONFile.load(
      from: fileURL,
      fileManager: fileManager,
      shouldRecover: { error in
        guard let error = error as? CatalogCacheStoreError else { return true }
        if case .unsupportedSchema = error { return false }
        return true
      },
      decode: decodeSnapshot
    )
  }

  private func decodeSnapshot(from data: Data) throws -> CatalogCacheSnapshot {
    let record = try decoder.decode(CatalogCacheRecord.self, from: data)
    guard record.schemaVersion == Self.currentSchemaVersion else {
      throw CatalogCacheStoreError.unsupportedSchema(record.schemaVersion)
    }
    let catalog = try validatedCatalog(from: record.jsonBlob)
    guard record.catalogVersion == catalog.catalogVersion else {
      throw CatalogCacheStoreError.catalogVersionMismatch
    }
    return CatalogCacheSnapshot(record: record, catalog: catalog)
  }

  @discardableResult
  func save(
    data: Data,
    validators: CatalogHTTPValidators,
    fetchedAt: Date = Date()
  ) throws -> CatalogCacheSnapshot {
    let catalog = try validatedCatalog(from: data)
    let record = CatalogCacheRecord(
      schemaVersion: Self.currentSchemaVersion,
      catalogVersion: catalog.catalogVersion,
      fetchedAt: fetchedAt,
      etag: validators.etag,
      lastModified: validators.lastModified,
      jsonBlob: data
    )
    try fileManager.createDirectory(
      at: fileURL.deletingLastPathComponent(),
      withIntermediateDirectories: true
    )
    try RecoverableJSONFile.write(
      encoder.encode(record),
      to: fileURL,
      fileManager: fileManager
    )
    return CatalogCacheSnapshot(record: record, catalog: catalog)
  }

  func reset() throws {
    let directory = fileURL.deletingLastPathComponent()
    if fileManager.fileExists(atPath: directory.path) {
      try fileManager.removeItem(at: directory)
    }
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

  private func validatedCatalog(from data: Data) throws -> Catalog {
    let catalog = try DeckKitJSON.decodeCatalog(from: data)
    let issues = CatalogValidator.validate(catalog)
    guard issues.isEmpty else {
      throw CatalogCacheStoreError.invalidCatalog(issues)
    }
    return catalog
  }
}
