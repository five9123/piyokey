package app.piyokey.core.deckkit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Fail-closed `.typedeck` importer following SPEC §5 validation order. */
object PiyoDeckPackageReader {
  /**
   * @param data the complete `.typedeck` file bytes.
   * @param deckSchemaData the pinned `shared/schema/deck.schema.json` bytes.
   * @throws PiyoDeckImportError for every failure (never another exception type).
   */
  @Throws(PiyoDeckImportError::class)
  fun read(data: ByteArray, deckSchemaData: ByteArray): PiyoDeckPackage {
    if (data.size > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) {
      throw PiyoDeckImportError.PackageTooLarge(
        data.size, PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      )
    }

    val entries = PiyoDeckZip.read(data)
    val manifestData = entries["manifest.json"]
    val deckData = entries["deck.json"]
    if (manifestData == null || deckData == null) {
      throw PiyoDeckImportError.InvalidEntrySet(entries.keys.sorted())
    }

    // Both JSON transports are untrusted archive input. Validate them before trusting version or
    // metadata from either entry.
    PiyoDeckStrictJson.validate(manifestData, "manifest.json")
    PiyoDeckStrictJson.validate(deckData, "deck.json")
    val manifest = decodeManifest(manifestData)

    if (manifest.deck.sizeBytes != deckData.size) {
      throw PiyoDeckImportError.ManifestMismatch("deck.size_bytes")
    }
    if (PiyoDeckDigest.sha256Hex(deckData) != manifest.deck.sha256) {
      throw PiyoDeckImportError.Sha256Mismatch
    }

    val deckObject = parseTree(deckData, "deck.json") as? JsonObject
      ?: throw PiyoDeckImportError.InvalidJson("deck.json", "root must be a JSON object")
    if (manifest.deck.deckId != JsonValues.stringOrNull(deckObject["deck_id"])) {
      throw PiyoDeckImportError.ManifestMismatch("deck.deck_id")
    }
    if (manifest.deck.deckVersion != JsonValues.intOrNull(deckObject["version"])) {
      throw PiyoDeckImportError.ManifestMismatch("deck.deck_version")
    }
    val rawItems = deckObject["items"] as? JsonArray
    if (rawItems == null || manifest.deck.itemCount != rawItems.size) {
      throw PiyoDeckImportError.ManifestMismatch("deck.item_count")
    }

    val schemaIssues = try {
      JsonSchemaValidator.validate(deckData, deckSchemaData)
    } catch (error: JsonSchemaValidationError) {
      throw PiyoDeckImportError.InvalidJson("deck.json", error.toString())
    }
    if (schemaIssues.isNotEmpty()) throw PiyoDeckImportError.DeckSchemaViolation(schemaIssues)

    val deck = try {
      DeckJson.decodeDeck(deckData)
    } catch (error: Exception) {
      throw PiyoDeckImportError.InvalidJson("deck.json", error.toString())
    }

    val semanticIssues = DeckValidator.validate(deck, schemaVersion = manifest.deckSchemaVersion)
    if (semanticIssues.isNotEmpty()) throw PiyoDeckImportError.InvalidUserDeck(semanticIssues)
    val userIssues = UserDeckValidator.validatePackageRules(deck)
    if (userIssues.isNotEmpty()) throw PiyoDeckImportError.InvalidUserDeck(userIssues)
    return PiyoDeckPackage(manifest, deck, deckData)
  }

  private fun parseTree(data: ByteArray, name: String): JsonElement =
    try {
      StrictJsonParser.parse(data)
    } catch (error: JsonSyntaxException) {
      throw PiyoDeckImportError.InvalidJson(name, error.message ?: "malformed JSON")
    }

  private fun manifestDecodeError(message: String) =
    PiyoDeckImportError.InvalidManifest(
      listOf(ContentValidationIssue("manifest.decode", "$", message)),
    )

  private fun decodeManifest(data: ByteArray): PiyoDeckManifest {
    PiyoDeckStrictJson.validate(data, "manifest.json")
    val tree = try {
      StrictJsonParser.parse(data)
    } catch (error: JsonSyntaxException) {
      throw manifestDecodeError(error.message ?: "malformed JSON")
    }

    val manifest = try {
      PiyoDeckManifest.decode(tree)
    } catch (error: IllegalArgumentException) {
      throw manifestDecodeError(error.message ?: "decode failed")
    }

    if (manifest.format != PiyoDeckManifest.FORMAT_IDENTIFIER) {
      throw PiyoDeckImportError.InvalidManifest(
        listOf(ContentValidationIssue("schema.enum", "$.format", "piyokey.deck-package여야 합니다")),
      )
    }
    if (manifest.formatVersion != PiyoDeckManifest.CURRENT_FORMAT_VERSION) {
      throw PiyoDeckImportError.UnsupportedFormatVersion(manifest.formatVersion)
    }
    if (manifest.deckSchemaVersion !in PiyoDeckManifest.SUPPORTED_DECK_SCHEMA_VERSIONS) {
      throw PiyoDeckImportError.UnsupportedDeckSchemaVersion(manifest.deckSchemaVersion)
    }

    val issues = mutableListOf<ContentValidationIssue>()
    val root = tree as? JsonObject
      ?: throw PiyoDeckImportError.InvalidManifest(
        listOf(ContentValidationIssue("schema.type", "$", "object 타입이어야 합니다")),
      )
    appendUnknownKeys(
      root, setOf("format", "format_version", "deck_schema_version", "deck"), "$", issues,
    )
    (root["deck"] as? JsonObject)?.let { descriptor ->
      appendUnknownKeys(
        descriptor,
        setOf(
          "path", "media_type", "deck_id", "deck_version", "item_count", "size_bytes", "sha256",
        ),
        "$.deck",
        issues,
      )
    }

    val maxItems = PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT
    val maxDeckBytes = PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES
    require(
      manifest.deck.path == PiyoDeckManifest.DeckDescriptor.PATH,
      "schema.enum", "$.deck.path", "deck.json이어야 합니다", issues,
    )
    require(
      manifest.deck.mediaType == PiyoDeckManifest.DeckDescriptor.MEDIA_TYPE,
      "schema.enum", "$.deck.media_type", "application/json이어야 합니다", issues,
    )
    require(
      USER_DECK_ID.containsMatchIn(manifest.deck.deckId),
      "schema.pattern", "$.deck.deck_id", "사용자 덱 ID 형식과 일치해야 합니다", issues,
    )
    require(
      manifest.deck.deckVersion >= 1,
      "schema.minimum", "$.deck.deck_version", "1 이상이어야 합니다", issues,
    )
    require(
      manifest.deck.itemCount in 1..maxItems,
      "schema.range", "$.deck.item_count", "1...$maxItems 범위여야 합니다", issues,
    )
    require(
      manifest.deck.sizeBytes in 1..maxDeckBytes,
      "schema.range", "$.deck.size_bytes", "1...$maxDeckBytes 범위여야 합니다", issues,
    )
    require(
      SHA256_PATTERN.containsMatchIn(manifest.deck.sha256),
      "schema.pattern", "$.deck.sha256", "소문자 SHA-256 형식이어야 합니다", issues,
    )

    if (issues.isNotEmpty()) throw PiyoDeckImportError.InvalidManifest(issues)
    return manifest
  }

  private val USER_DECK_ID = Regex("^user_[0-9a-f]{32}$")
  private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

  private fun appendUnknownKeys(
    obj: JsonObject,
    allowed: Set<String>,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    for (key in obj.keys) {
      if (key !in allowed) {
        issues.add(
          ContentValidationIssue("schema.additionalProperties", "$path.$key", "정의되지 않은 필드입니다"),
        )
      }
    }
  }

  private fun require(
    condition: Boolean,
    code: String,
    path: String,
    message: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    if (!condition) issues.add(ContentValidationIssue(code, path, message))
  }
}
