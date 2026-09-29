package app.piyokey.core.deckkit

/**
 * Every `.typedeck` read/write failure. Cases mirror Swift `PiyoDeckImportError`; use [family]
 * for the coarse, cross-platform error family used by `shared/piyodeck/fixtures/cases.json`.
 */
sealed class PiyoDeckImportError(message: String) : Exception(message) {
  data class PackageTooLarge(val actual: Int, val maximum: Int) :
    PiyoDeckImportError("Package size $actual exceeds the $maximum-byte limit.")

  data class MalformedArchive(val reason: String) :
    PiyoDeckImportError("The package is not a valid PIYOKEY archive: $reason")

  data class UnsupportedArchiveFeature(val feature: String) :
    PiyoDeckImportError("The package uses an unsupported ZIP feature: $feature")

  data class InvalidEntrySet(val entries: List<String>) :
    PiyoDeckImportError(
      "The package must contain only manifest.json and deck.json (found: $entries).",
    )

  data class UnsafeEntryPath(val path: String) :
    PiyoDeckImportError("The package contains an unsafe entry path: $path")

  data class EntryTooLarge(val name: String, val actual: Int, val maximum: Int) :
    PiyoDeckImportError("$name size $actual exceeds the $maximum-byte limit.")

  data class CrcMismatch(val name: String) :
    PiyoDeckImportError("The ZIP CRC-32 check failed for $name.")

  data class InvalidJson(val name: String, val reason: String) :
    PiyoDeckImportError("$name is not valid PIYOKEY JSON: $reason")

  data class DuplicateJsonKey(val name: String, val path: String) :
    PiyoDeckImportError("$name contains a duplicate JSON key at $path.")

  data class InvalidManifest(val issues: List<ContentValidationIssue>) :
    PiyoDeckImportError("The package manifest is invalid: ${issues.joinToString(", ")}")

  data class UnsupportedFormatVersion(val version: Int) :
    PiyoDeckImportError("PIYOKEY package format version $version is not supported.")

  data class UnsupportedDeckSchemaVersion(val version: Int) :
    PiyoDeckImportError("PIYOKEY deck schema version $version is not supported.")

  data class DeckSchemaViolation(val issues: List<ContentValidationIssue>) :
    PiyoDeckImportError("deck.json does not match the deck schema: ${issues.joinToString(", ")}")

  data class InvalidUserDeck(val issues: List<ContentValidationIssue>) :
    PiyoDeckImportError("deck.json is not a valid user deck: ${issues.joinToString(", ")}")

  data class ManifestMismatch(val field: String) :
    PiyoDeckImportError("The manifest does not match deck.json at $field.")

  data object Sha256Mismatch : PiyoDeckImportError("The deck.json SHA-256 check failed.")

  /** Coarse family names shared with Swift/Python fixture expectations. */
  val family: Family
    get() = when (this) {
      is Sha256Mismatch -> Family.SHA256_MISMATCH
      is InvalidJson, is DuplicateJsonKey -> Family.INVALID_JSON
      is UnsupportedFormatVersion, is UnsupportedDeckSchemaVersion -> Family.UNSUPPORTED_VERSION
      is UnsupportedArchiveFeature -> Family.UNSUPPORTED_ARCHIVE_FEATURE
      is UnsafeEntryPath -> Family.UNSAFE_ENTRY_PATH
      is MalformedArchive -> Family.MALFORMED_ARCHIVE
      is CrcMismatch -> Family.CRC_MISMATCH
      is DeckSchemaViolation, is InvalidUserDeck -> Family.INVALID_CONTENT
      is PackageTooLarge, is EntryTooLarge -> Family.TOO_LARGE
      is InvalidEntrySet -> Family.INVALID_ENTRY_SET
      is InvalidManifest, is ManifestMismatch -> Family.INVALID_MANIFEST
    }

  /** `true` when the app should suggest updating PIYOKEY instead of reporting damage. */
  val isUnsupportedFutureVersion: Boolean
    get() = family == Family.UNSUPPORTED_VERSION

  enum class Family(val id: String) {
    SHA256_MISMATCH("sha256_mismatch"),
    INVALID_JSON("invalid_json"),
    UNSUPPORTED_VERSION("unsupported_version"),
    UNSUPPORTED_ARCHIVE_FEATURE("unsupported_archive_feature"),
    UNSAFE_ENTRY_PATH("unsafe_entry_path"),
    MALFORMED_ARCHIVE("malformed_archive"),
    CRC_MISMATCH("crc_mismatch"),
    INVALID_CONTENT("invalid_content"),
    TOO_LARGE("too_large"),
    INVALID_ENTRY_SET("invalid_entry_set"),
    INVALID_MANIFEST("invalid_manifest"),
  }
}
