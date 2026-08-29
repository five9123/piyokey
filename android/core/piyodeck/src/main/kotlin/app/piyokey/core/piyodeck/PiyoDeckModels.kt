package app.piyokey.core.piyodeck

import app.piyokey.core.deckkit.Deck

public object PiyoDeckPackageLimits {
  public const val MAXIMUM_PACKAGE_BYTES: Int = 8 * 1_024 * 1_024
  public const val MAXIMUM_MANIFEST_BYTES: Int = 16 * 1_024
  public const val MAXIMUM_DECK_BYTES: Int = 4 * 1_024 * 1_024
  public const val MAXIMUM_ITEM_COUNT: Int = 1_000
}

public data class PiyoDeckManifest(
  val format: String,
  val formatVersion: Int,
  val deckSchemaVersion: Int,
  val deck: DeckDescriptor,
) {
  public data class DeckDescriptor(
    val path: String,
    val mediaType: String,
    val deckId: String,
    val deckVersion: Int,
    val itemCount: Int,
    val sizeBytes: Int,
    val sha256: String,
  )

  public companion object {
    public const val FORMAT_IDENTIFIER: String = "piyokey.deck-package"
    public const val CURRENT_FORMAT_VERSION: Int = 1
    public const val CURRENT_DECK_SCHEMA_VERSION: Int = 2
    public val SUPPORTED_DECK_SCHEMA_VERSIONS: Set<Int> = setOf(1, 2)
    public const val DECK_PATH: String = "deck.json"
    public const val DECK_MEDIA_TYPE: String = "application/json"
  }
}

public data class PiyoDeckPackage(
  val manifest: PiyoDeckManifest,
  val deck: Deck,
  val deckData: ByteArray,
) {
  public val contentSha256: String
    get() = manifest.deck.sha256

  override fun equals(other: Any?): Boolean =
    other is PiyoDeckPackage &&
      manifest == other.manifest &&
      deck == other.deck &&
      deckData.contentEquals(other.deckData)

  override fun hashCode(): Int {
    var result = manifest.hashCode()
    result = 31 * result + deck.hashCode()
    result = 31 * result + deckData.contentHashCode()
    return result
  }
}

public data class PiyoDeckValidationIssue(
  val code: String,
  val path: String,
  val message: String,
)

public sealed class PiyoDeckImportException(message: String) : Exception(message) {
  public data class PackageTooLarge(val actual: Int, val maximum: Int) :
    PiyoDeckImportException("Package size $actual exceeds the $maximum-byte limit.")

  public data class MalformedArchive(val reason: String) :
    PiyoDeckImportException("The package is not a valid PIYOKEY archive: $reason")

  public data class UnsupportedArchiveFeature(val feature: String) :
    PiyoDeckImportException("The package uses an unsupported ZIP feature: $feature")

  public data class InvalidEntrySet(val entries: List<String>) :
    PiyoDeckImportException(
      "The package must contain only manifest.json and deck.json (found: $entries).",
    )

  public data class UnsafeEntryPath(val path: String) :
    PiyoDeckImportException("The package contains an unsafe entry path: $path")

  public data class EntryTooLarge(val name: String, val actual: Int, val maximum: Int) :
    PiyoDeckImportException("$name size $actual exceeds the $maximum-byte limit.")

  public data class CrcMismatch(val name: String) :
    PiyoDeckImportException("The ZIP CRC-32 check failed for $name.")

  public data class InvalidJson(val name: String, val reason: String) :
    PiyoDeckImportException("$name is not valid PIYOKEY JSON: $reason")

  public data class DuplicateJsonKey(val name: String, val path: String) :
    PiyoDeckImportException("$name contains a duplicate JSON key at $path.")

  public data class InvalidManifest(val issues: List<PiyoDeckValidationIssue>) :
    PiyoDeckImportException("The package manifest is invalid: $issues")

  public data class UnsupportedFormatVersion(val version: Int) :
    PiyoDeckImportException("PIYOKEY package format version $version is not supported.")

  public data class UnsupportedDeckSchemaVersion(val version: Int) :
    PiyoDeckImportException("PIYOKEY deck schema version $version is not supported.")

  public data class DeckSchemaViolation(val issues: List<PiyoDeckValidationIssue>) :
    PiyoDeckImportException("deck.json does not match the deck schema: $issues")

  public data class InvalidUserDeck(val issues: List<PiyoDeckValidationIssue>) :
    PiyoDeckImportException("deck.json is not a valid user deck: $issues")

  public data class ManifestMismatch(val field: String) :
    PiyoDeckImportException("The manifest does not match deck.json at $field.")

  public data object Sha256Mismatch :
    PiyoDeckImportException("The deck.json SHA-256 check failed.")
}
