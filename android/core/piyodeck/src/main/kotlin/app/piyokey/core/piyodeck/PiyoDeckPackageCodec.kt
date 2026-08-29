package app.piyokey.core.piyodeck

import app.piyokey.core.deckkit.ContentValidationIssue
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckKitJson
import app.piyokey.core.deckkit.DeckKitJsonException
import app.piyokey.core.deckkit.DeckValidator
import app.piyokey.core.deckkit.JsonSchemaValidator
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

public object PiyoDeckPackageReader {
  public fun read(data: ByteArray, deckSchemaSource: String): PiyoDeckPackage {
    if (data.size > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) {
      throw PiyoDeckImportException.PackageTooLarge(
        data.size,
        PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      )
    }
    val entries = PiyoDeckZip.read(data)
    val manifestData = entries["manifest.json"]
      ?: throw PiyoDeckImportException.InvalidEntrySet(entries.keys.sorted())
    val deckData = entries["deck.json"]
      ?: throw PiyoDeckImportException.InvalidEntrySet(entries.keys.sorted())
    // SPEC §5: after ZIP CRC, both JSON entries must pass the transport-level
    // UTF-8/BOM/duplicate-key checks before either document is trusted.
    val manifestObject = PiyoDeckStrictJson.decodeObject(manifestData, "manifest.json")
    val deckObject = PiyoDeckStrictJson.decodeObject(deckData, "deck.json")
    val manifest = decodeManifest(manifestObject)

    if (manifest.deck.sizeBytes != deckData.size) {
      throw PiyoDeckImportException.ManifestMismatch("deck.size_bytes")
    }
    if (PiyoDeckDigest.sha256Hex(deckData) != manifest.deck.sha256) {
      throw PiyoDeckImportException.Sha256Mismatch
    }

    val deckId = deckObject.string("deck_id")
      ?: throw PiyoDeckImportException.ManifestMismatch("deck.deck_id")
    if (manifest.deck.deckId != deckId) {
      throw PiyoDeckImportException.ManifestMismatch("deck.deck_id")
    }
    val deckVersion = deckObject.integer("version")
      ?: throw PiyoDeckImportException.ManifestMismatch("deck.deck_version")
    if (manifest.deck.deckVersion != deckVersion) {
      throw PiyoDeckImportException.ManifestMismatch("deck.deck_version")
    }
    val items = deckObject["items"] as? JsonArray
      ?: throw PiyoDeckImportException.ManifestMismatch("deck.item_count")
    if (manifest.deck.itemCount != items.size) {
      throw PiyoDeckImportException.ManifestMismatch("deck.item_count")
    }

    val deckSource = deckData.toString(Charsets.UTF_8)
    val schemaIssues = JsonSchemaValidator.validate(deckSource, deckSchemaSource)
    if (schemaIssues.isNotEmpty()) {
      throw PiyoDeckImportException.DeckSchemaViolation(schemaIssues.toPiyoDeckIssues())
    }
    val deck = try {
      DeckKitJson.decodeDeck(deckSource)
    } catch (error: DeckKitJsonException) {
      throw PiyoDeckImportException.InvalidJson("deck.json", error.message)
    }
    val semanticIssues = DeckValidator.validate(deck, manifest.deckSchemaVersion).toPiyoDeckIssues()
    if (semanticIssues.isNotEmpty()) {
      throw PiyoDeckImportException.InvalidUserDeck(semanticIssues)
    }
    val userIssues = PiyoDeckUserDeckValidator.validate(deck)
    if (userIssues.isNotEmpty()) throw PiyoDeckImportException.InvalidUserDeck(userIssues)
    return PiyoDeckPackage(manifest, deck, deckData.copyOf())
  }

  private fun decodeManifest(root: JsonObject): PiyoDeckManifest {
    root.integer("format_version")?.let { version ->
      if (version != PiyoDeckManifest.CURRENT_FORMAT_VERSION) {
        throw PiyoDeckImportException.UnsupportedFormatVersion(version)
      }
    }
    root.integer("deck_schema_version")?.let { version ->
      if (version !in PiyoDeckManifest.SUPPORTED_DECK_SCHEMA_VERSIONS) {
        throw PiyoDeckImportException.UnsupportedDeckSchemaVersion(version)
      }
    }

    val issues = mutableListOf<PiyoDeckValidationIssue>()
    checkKeys(
      root,
      setOf("format", "format_version", "deck_schema_version", "deck"),
      "$",
      issues,
    )
    checkExactString(root["format"], "$.format", PiyoDeckManifest.FORMAT_IDENTIFIER, issues)
    checkExactInteger(
      root["format_version"],
      "$.format_version",
      PiyoDeckManifest.CURRENT_FORMAT_VERSION,
      issues,
    )
    checkExactInteger(
      root["deck_schema_version"],
      "$.deck_schema_version",
      root.integer("deck_schema_version") ?: PiyoDeckManifest.CURRENT_DECK_SCHEMA_VERSION,
      issues,
    )
    val descriptor = root["deck"] as? JsonObject
    if (descriptor == null) {
      issues += PiyoDeckValidationIssue("schema.type", "$.deck", "expected object")
    } else {
      checkKeys(
        descriptor,
        setOf(
          "path", "media_type", "deck_id", "deck_version", "item_count", "size_bytes",
          "sha256",
        ),
        "$.deck",
        issues,
      )
      checkExactString(descriptor["path"], "$.deck.path", PiyoDeckManifest.DECK_PATH, issues)
      checkExactString(
        descriptor["media_type"],
        "$.deck.media_type",
        PiyoDeckManifest.DECK_MEDIA_TYPE,
        issues,
      )
      checkPatternString(
        descriptor["deck_id"],
        "$.deck.deck_id",
        Regex("^user_[0-9a-f]{32}$"),
        issues,
      )
      checkIntegerRange(descriptor["deck_version"], "$.deck.deck_version", 1, null, issues)
      checkIntegerRange(
        descriptor["item_count"],
        "$.deck.item_count",
        1,
        PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT,
        issues,
      )
      checkIntegerRange(
        descriptor["size_bytes"],
        "$.deck.size_bytes",
        1,
        PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES,
        issues,
      )
      checkPatternString(
        descriptor["sha256"],
        "$.deck.sha256",
        Regex("^[0-9a-f]{64}$"),
        issues,
      )
    }
    if (issues.isNotEmpty()) throw PiyoDeckImportException.InvalidManifest(issues)

    descriptor!!
    return PiyoDeckManifest(
      format = root.string("format")!!,
      formatVersion = root.integer("format_version")!!,
      deckSchemaVersion = root.integer("deck_schema_version")!!,
      deck = PiyoDeckManifest.DeckDescriptor(
        path = descriptor.string("path")!!,
        mediaType = descriptor.string("media_type")!!,
        deckId = descriptor.string("deck_id")!!,
        deckVersion = descriptor.integer("deck_version")!!,
        itemCount = descriptor.integer("item_count")!!,
        sizeBytes = descriptor.integer("size_bytes")!!,
        sha256 = descriptor.string("sha256")!!,
      ),
    )
  }
}

public object PiyoDeckPackageWriter {
  public fun write(deckData: ByteArray, deckSchemaSource: String): ByteArray {
    PiyoDeckStrictJson.decodeObject(deckData, "deck.json")
    val source = deckData.toString(Charsets.UTF_8)
    val schemaIssues = JsonSchemaValidator.validate(source, deckSchemaSource)
    if (schemaIssues.isNotEmpty()) {
      throw PiyoDeckImportException.DeckSchemaViolation(schemaIssues.toPiyoDeckIssues())
    }
    val deck = try {
      DeckKitJson.decodeDeck(source)
    } catch (error: DeckKitJsonException) {
      throw PiyoDeckImportException.InvalidJson("deck.json", error.message)
    }
    return write(deck, deckSchemaSource)
  }

  public fun write(deck: Deck, deckSchemaSource: String): ByteArray {
    val semanticIssues = DeckValidator.validate(deck).toPiyoDeckIssues()
    if (semanticIssues.isNotEmpty()) {
      throw PiyoDeckImportException.InvalidUserDeck(semanticIssues)
    }
    val userIssues = PiyoDeckUserDeckValidator.validate(deck)
    if (userIssues.isNotEmpty()) throw PiyoDeckImportException.InvalidUserDeck(userIssues)

    val deckObject = PiyoDeckStrictJson.decodeObject(
      DeckKitJson.encodeDeck(deck).toByteArray(Charsets.UTF_8),
      "deck.json",
    )
    val canonicalDeckData = try {
      PiyoDeckStrictJson.canonicalData(deckObject)
    } catch (error: IllegalArgumentException) {
      throw PiyoDeckImportException.InvalidJson("deck.json", error.message ?: "cannot encode JSON")
    }
    if (canonicalDeckData.size > PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES) {
      throw PiyoDeckImportException.EntryTooLarge(
        "deck.json",
        canonicalDeckData.size,
        PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES,
      )
    }

    val canonicalSource = canonicalDeckData.toString(Charsets.UTF_8)
    val schemaIssues = JsonSchemaValidator.validate(canonicalSource, deckSchemaSource)
    if (schemaIssues.isNotEmpty()) {
      throw PiyoDeckImportException.DeckSchemaViolation(schemaIssues.toPiyoDeckIssues())
    }
    val manifestObject = buildJsonObject {
      put("format", PiyoDeckManifest.FORMAT_IDENTIFIER)
      put("format_version", PiyoDeckManifest.CURRENT_FORMAT_VERSION)
      put("deck_schema_version", if (deck.defaultLocale == null) 1 else 2)
      put("deck", buildJsonObject {
        put("path", PiyoDeckManifest.DECK_PATH)
        put("media_type", PiyoDeckManifest.DECK_MEDIA_TYPE)
        put("deck_id", deck.deckId)
        put("deck_version", deck.version)
        put("item_count", deck.items.size)
        put("size_bytes", canonicalDeckData.size)
        put("sha256", PiyoDeckDigest.sha256Hex(canonicalDeckData))
      })
    }
    val manifestData = PiyoDeckStrictJson.canonicalData(manifestObject)
    if (manifestData.size > PiyoDeckPackageLimits.MAXIMUM_MANIFEST_BYTES) {
      throw PiyoDeckImportException.EntryTooLarge(
        "manifest.json",
        manifestData.size,
        PiyoDeckPackageLimits.MAXIMUM_MANIFEST_BYTES,
      )
    }

    val packageData = PiyoDeckZip.write(
      listOf(
        PiyoDeckZipEntry("manifest.json", manifestData),
        PiyoDeckZipEntry("deck.json", canonicalDeckData),
      ),
    )
    if (packageData.size > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) {
      throw PiyoDeckImportException.PackageTooLarge(
        packageData.size,
        PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      )
    }
    return packageData
  }
}

public object PiyoDeckDigest {
  public fun sha256Hex(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}

private fun checkKeys(
  value: JsonObject,
  allowedAndRequired: Set<String>,
  path: String,
  issues: MutableList<PiyoDeckValidationIssue>,
) {
  allowedAndRequired.forEach { key ->
    if (!value.containsKey(key)) {
      issues += PiyoDeckValidationIssue("schema.required", "$path.$key", "required property is missing")
    }
  }
  value.keys.filterNot { it in allowedAndRequired }.forEach { key ->
    issues += PiyoDeckValidationIssue(
      "schema.additionalProperties",
      "$path.$key",
      "additional property is forbidden",
    )
  }
}

private fun checkExactString(
  value: JsonElement?,
  path: String,
  expected: String,
  issues: MutableList<PiyoDeckValidationIssue>,
) {
  val primitive = value as? JsonPrimitive
  if (primitive == null || !primitive.isString) {
    issues += PiyoDeckValidationIssue("schema.type", path, "expected string")
  } else if (primitive.content != expected) {
    issues += PiyoDeckValidationIssue("schema.enum", path, "value is not in enum")
  }
}

private fun checkExactInteger(
  value: JsonElement?,
  path: String,
  expected: Int,
  issues: MutableList<PiyoDeckValidationIssue>,
) {
  val integer = value.strictInt()
  if (integer == null) {
    issues += PiyoDeckValidationIssue("schema.type", path, "expected integer")
  } else if (integer != expected) {
    issues += PiyoDeckValidationIssue("schema.enum", path, "value is not in enum")
  }
}

private fun checkIntegerRange(
  value: JsonElement?,
  path: String,
  minimum: Int,
  maximum: Int?,
  issues: MutableList<PiyoDeckValidationIssue>,
) {
  val integer = value.strictInt()
  if (integer == null) {
    issues += PiyoDeckValidationIssue("schema.type", path, "expected integer")
    return
  }
  if (integer < minimum) issues += PiyoDeckValidationIssue("schema.minimum", path, "below minimum")
  if (maximum != null && integer > maximum) {
    issues += PiyoDeckValidationIssue("schema.maximum", path, "above maximum")
  }
}

private fun checkPatternString(
  value: JsonElement?,
  path: String,
  pattern: Regex,
  issues: MutableList<PiyoDeckValidationIssue>,
) {
  val primitive = value as? JsonPrimitive
  if (primitive == null || !primitive.isString) {
    issues += PiyoDeckValidationIssue("schema.type", path, "expected string")
  } else if (!pattern.matches(primitive.content)) {
    issues += PiyoDeckValidationIssue("schema.pattern", path, "does not match ${pattern.pattern}")
  }
}

private fun JsonElement?.strictInt(): Int? =
  (this as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

private fun JsonObject.string(key: String): String? =
  (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.integer(key: String): Int? = get(key).strictInt()

private fun List<ContentValidationIssue>.toPiyoDeckIssues(): List<PiyoDeckValidationIssue> =
  map { PiyoDeckValidationIssue(it.code, it.path, it.message) }
