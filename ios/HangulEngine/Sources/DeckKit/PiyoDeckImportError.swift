import Foundation

public enum PiyoDeckImportError: Error, Equatable, Sendable {
  case packageTooLarge(actual: Int, maximum: Int)
  case malformedArchive(String)
  case unsupportedArchiveFeature(String)
  case invalidEntrySet([String])
  case unsafeEntryPath(String)
  case entryTooLarge(name: String, actual: Int, maximum: Int)
  case crcMismatch(name: String)
  case invalidJSON(name: String, reason: String)
  case duplicateJSONKey(name: String, path: String)
  case invalidManifest([ContentValidationIssue])
  case unsupportedFormatVersion(Int)
  case unsupportedDeckSchemaVersion(Int)
  case deckSchemaViolation([ContentValidationIssue])
  case invalidUserDeck([ContentValidationIssue])
  case manifestMismatch(field: String)
  case sha256Mismatch
}

extension PiyoDeckImportError: LocalizedError {
  public var errorDescription: String? {
    switch self {
    case .packageTooLarge(let actual, let maximum):
      return "Package size \(actual) exceeds the \(maximum)-byte limit."
    case .malformedArchive(let reason):
      return "The package is not a valid PIYOKEY archive: \(reason)"
    case .unsupportedArchiveFeature(let feature):
      return "The package uses an unsupported ZIP feature: \(feature)"
    case .invalidEntrySet(let entries):
      return "The package must contain only manifest.json and deck.json (found: \(entries))."
    case .unsafeEntryPath(let path):
      return "The package contains an unsafe entry path: \(path)"
    case .entryTooLarge(let name, let actual, let maximum):
      return "\(name) size \(actual) exceeds the \(maximum)-byte limit."
    case .crcMismatch(let name):
      return "The ZIP CRC-32 check failed for \(name)."
    case .invalidJSON(let name, let reason):
      return "\(name) is not valid PIYOKEY JSON: \(reason)"
    case .duplicateJSONKey(let name, let path):
      return "\(name) contains a duplicate JSON key at \(path)."
    case .invalidManifest(let issues):
      return "The package manifest is invalid: \(issues.map(\.description).joined(separator: ", "))"
    case .unsupportedFormatVersion(let version):
      return "PIYOKEY package format version \(version) is not supported."
    case .unsupportedDeckSchemaVersion(let version):
      return "PIYOKEY deck schema version \(version) is not supported."
    case .deckSchemaViolation(let issues):
      let details = issues.map(\.description).joined(separator: ", ")
      return "deck.json does not match the deck schema: \(details)"
    case .invalidUserDeck(let issues):
      let details = issues.map(\.description).joined(separator: ", ")
      return "deck.json is not a valid user deck: \(details)"
    case .manifestMismatch(let field):
      return "The manifest does not match deck.json at \(field)."
    case .sha256Mismatch:
      return "The deck.json SHA-256 check failed."
    }
  }
}
