package app.piyokey.core.deckkit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Deterministic, byte-stable `.typedeck` writer (reproduces the shared goldens byte-for-byte). */
object PiyoDeckPackageWriter {
  /**
   * Validates [deck] with [UserDeckValidator] and the pinned deck schema, then writes
   * `manifest.json` + `deck.json` (compact, sorted keys, `audio: null` explicit) into a STORE
   * ZIP with the 1980-01-01 DOS timestamp. Schema v2 is declared iff `defaultLocale != null`.
   *
   * @throws PiyoDeckImportError on invalid content or exceeded limits.
   */
  @Throws(PiyoDeckImportError::class)
  fun write(deck: Deck, deckSchemaData: ByteArray): ByteArray {
    val userIssues = UserDeckValidator.validate(deck)
    if (userIssues.isNotEmpty()) throw PiyoDeckImportError.InvalidUserDeck(userIssues)

    val deckData = canonicalDeckBytes(deck)
    val schemaIssues = try {
      JsonSchemaValidator.validate(deckData, deckSchemaData)
    } catch (error: JsonSchemaValidationError) {
      throw PiyoDeckImportError.InvalidJson("deck.json", error.toString())
    }
    if (schemaIssues.isNotEmpty()) throw PiyoDeckImportError.DeckSchemaViolation(schemaIssues)
    if (deckData.size > PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES) {
      throw PiyoDeckImportError.EntryTooLarge(
        "deck.json", deckData.size, PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES,
      )
    }

    val manifest = PiyoDeckManifest(
      deckSchemaVersion = if (deck.defaultLocale == null) 1 else 2,
      deck = PiyoDeckManifest.DeckDescriptor(
        deckId = deck.deckId,
        deckVersion = deck.version,
        itemCount = deck.items.size,
        sizeBytes = deckData.size,
        sha256 = PiyoDeckDigest.sha256Hex(deckData),
      ),
    )
    val manifestData = manifest.encode()
    if (manifestData.size > PiyoDeckPackageLimits.MAXIMUM_MANIFEST_BYTES) {
      throw PiyoDeckImportError.EntryTooLarge(
        "manifest.json", manifestData.size, PiyoDeckPackageLimits.MAXIMUM_MANIFEST_BYTES,
      )
    }

    val packageData = PiyoDeckZip.write(
      listOf(
        PiyoDeckZipEntry("manifest.json", manifestData),
        PiyoDeckZipEntry("deck.json", deckData),
      ),
    )
    if (packageData.size > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) {
      throw PiyoDeckImportError.PackageTooLarge(
        packageData.size, PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      )
    }
    return packageData
  }

  /** The canonical `deck.json` entry bytes for [deck] (no validation). */
  fun canonicalDeckBytes(deck: Deck): ByteArray {
    val encoded = DeckJson.encodeDeckToElement(deck) as? JsonObject
      ?: throw PiyoDeckImportError.InvalidJson("deck.json", "encoded deck root is not an object")
    val items = encoded["items"] as? JsonArray
      ?: throw PiyoDeckImportError.InvalidJson("deck.json", "encoded deck items are not an array")
    val normalizedItems = JsonArray(
      items.map { item ->
        val obj = item as? JsonObject
          ?: throw PiyoDeckImportError.InvalidJson(
            "deck.json", "encoded deck root or items are not JSON objects",
          )
        if (obj.containsKey("audio")) obj else JsonObject(obj + ("audio" to JsonNull))
      },
    )
    return CanonicalJson.bytes(JsonObject(encoded + ("items" to normalizedItems)))
  }
}
