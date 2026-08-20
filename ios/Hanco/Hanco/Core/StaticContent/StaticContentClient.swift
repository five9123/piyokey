import Foundation

struct StaticContentConfiguration: Equatable {
  let catalogURL: URL?

  static var live: StaticContentConfiguration {
    #if DEBUG
      if let environmentValue = ProcessInfo.processInfo.environment["HANCO_CATALOG_URL"],
        let url = validatedHTTPSURL(environmentValue)
      {
        return StaticContentConfiguration(catalogURL: url)
      }
    #endif

    let bundleValue = Bundle.main.object(forInfoDictionaryKey: "HancoCatalogURL") as? String
    return StaticContentConfiguration(catalogURL: validatedHTTPSURL(bundleValue))
  }

  func deckURL(for relativePath: String) -> URL? {
    guard let catalogURL,
      !relativePath.contains(".."),
      !relativePath.contains("%"),
      !relativePath.contains("\\"),
      !relativePath.contains("?"),
      !relativePath.contains("#"),
      relativePath.hasPrefix("decks/"),
      relativePath.split(separator: "/").count == 2,
      relativePath.hasSuffix(".json"),
      let resolved = URL(string: relativePath, relativeTo: catalogURL)?.absoluteURL,
      resolved.scheme == catalogURL.scheme,
      resolved.host == catalogURL.host,
      resolved.port == catalogURL.port
    else {
      return nil
    }

    let contentRoot = catalogURL.deletingLastPathComponent().standardized
    let standardized = resolved.standardized
    guard standardized.path.hasPrefix(contentRoot.path + "/") else { return nil }
    return standardized
  }

  private static func validatedHTTPSURL(_ rawValue: String?) -> URL? {
    guard let value = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines),
      !value.isEmpty,
      !value.contains("$("),
      let url = URL(string: value),
      url.scheme?.lowercased() == "https",
      url.host != nil
    else {
      return nil
    }
    return url
  }
}

protocol HTTPDataClient {
  func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

enum HTTPDataClientError: Error {
  case invalidResponse
}

struct URLSessionHTTPDataClient: HTTPDataClient {
  let session: URLSession

  init(session: URLSession = .shared) {
    self.session = session
  }

  func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
    let (data, response) = try await session.data(for: request)
    guard let httpResponse = response as? HTTPURLResponse else {
      throw HTTPDataClientError.invalidResponse
    }
    return (data, httpResponse)
  }
}

struct CatalogHTTPValidators: Equatable {
  let etag: String?
  let lastModified: String?
}

enum CatalogHTTPResult {
  case modified(data: Data, validators: CatalogHTTPValidators)
  case notModified(validators: CatalogHTTPValidators)
}

enum StaticContentHTTPError: Error {
  case statusCode(Int)
  case unsafeDeckURL(String)
}

struct CatalogHTTPClient {
  let dataClient: any HTTPDataClient

  init(dataClient: any HTTPDataClient = URLSessionHTTPDataClient()) {
    self.dataClient = dataClient
  }

  func fetchCatalog(
    at url: URL,
    validators: CatalogHTTPValidators?
  ) async throws -> CatalogHTTPResult {
    var request = URLRequest(
      url: url,
      cachePolicy: .reloadIgnoringLocalCacheData,
      timeoutInterval: 15
    )
    request.httpMethod = "GET"
    request.setValue("application/json", forHTTPHeaderField: "Accept")
    if let etag = validators?.etag {
      request.setValue(etag, forHTTPHeaderField: "If-None-Match")
    }
    if let lastModified = validators?.lastModified {
      request.setValue(lastModified, forHTTPHeaderField: "If-Modified-Since")
    }

    let (data, response) = try await dataClient.data(for: request)
    let responseValidators = CatalogHTTPValidators(
      etag: response.value(forHTTPHeaderField: "ETag") ?? validators?.etag,
      lastModified:
        response.value(forHTTPHeaderField: "Last-Modified") ?? validators?.lastModified
    )
    switch response.statusCode {
    case 200:
      return .modified(data: data, validators: responseValidators)
    case 304:
      return .notModified(validators: responseValidators)
    default:
      throw StaticContentHTTPError.statusCode(response.statusCode)
    }
  }
}
