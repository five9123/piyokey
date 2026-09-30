package app.piyokey.core.deckkit

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object PiyoDeckPackageLimits {
  const val MAXIMUM_PACKAGE_BYTES = 8 * 1_024 * 1_024
  const val MAXIMUM_MANIFEST_BYTES = 16 * 1_024
  const val MAXIMUM_DECK_BYTES = 4 * 1_024 * 1_024
  const val MAXIMUM_ITEM_COUNT = 1_000
}

/** `manifest.json` of a `.typedeck` package (`shared/schema/piyodeck-manifest-v1.schema.json`). */
data class PiyoDeckManifest(
  val format: String = FORMAT_IDENTIFIER,
  val formatVersion: Int = CURRENT_FORMAT_VERSION,
  val deckSchemaVersion: Int = CURRENT_DECK_SCHEMA_VERSION,
  val deck: DeckDescriptor,
) {
  data class DeckDescriptor(
    val path: String = PATH,
    val mediaType: String = MEDIA_TYPE,
    val deckId: String,
    val deckVersion: Int,
    val itemCount: Int,
    val sizeBytes: Int,
    val sha256: String,
  ) {
    companion object {
      const val PATH = "deck.json"
      const val MEDIA_TYPE = "application/json"
    }
  }

  /** JSON tree with the manifest's snake_case keys. */
  fun toJsonElement(): JsonObject =
    JsonObject(
      mapOf(
        "format" to JsonPrimitive(format),
        "format_version" to JsonPrimitive(formatVersion),
        "deck_schema_version" to JsonPrimitive(deckSchemaVersion),
        "deck" to JsonObject(
          mapOf(
            "path" to JsonPrimitive(deck.path),
            "media_type" to JsonPrimitive(deck.mediaType),
            "deck_id" to JsonPrimitive(deck.deckId),
            "deck_version" to JsonPrimitive(deck.deckVersion),
            "item_count" to JsonPrimitive(deck.itemCount),
            "size_bytes" to JsonPrimitive(deck.sizeBytes),
            "sha256" to JsonPrimitive(deck.sha256),
          ),
        ),
      ),
    )

  /** Compact, sorted-key UTF-8 bytes (the canonical `.typedeck` form). */
  fun encode(pretty: Boolean = false): ByteArray =
    CanonicalJson.write(toJsonElement(), pretty).toByteArray(Charsets.UTF_8)

  companion object {
    const val FORMAT_IDENTIFIER = "piyokey.deck-package"
    const val CURRENT_FORMAT_VERSION = 1
    const val CURRENT_DECK_SCHEMA_VERSION = 2
    val SUPPORTED_DECK_SCHEMA_VERSIONS: Set<Int> = setOf(1, 2)

    /**
     * Strict typed decode equivalent to Swift `JSONDecoder().decode(PiyoDeckManifest.self)`:
     * every field required with the exact JSON type; unknown keys ignored here (the reader
     * rejects them separately). Throws [IllegalArgumentException] describing the problem.
     */
    fun decode(element: JsonElement): PiyoDeckManifest {
      val root = element as? JsonObject ?: throw IllegalArgumentException("root is not an object")
      val descriptor = root["deck"] as? JsonObject
        ?: throw IllegalArgumentException("keyNotFound or typeMismatch: deck")
      return PiyoDeckManifest(
        format = string(root, "format"),
        formatVersion = int(root, "format_version"),
        deckSchemaVersion = int(root, "deck_schema_version"),
        deck = DeckDescriptor(
          path = string(descriptor, "path"),
          mediaType = string(descriptor, "media_type"),
          deckId = string(descriptor, "deck_id"),
          deckVersion = int(descriptor, "deck_version"),
          itemCount = int(descriptor, "item_count"),
          sizeBytes = int(descriptor, "size_bytes"),
          sha256 = string(descriptor, "sha256"),
        ),
      )
    }

    private fun string(obj: JsonObject, key: String): String =
      JsonValues.stringOrNull(obj[key])
        ?: throw IllegalArgumentException("keyNotFound or typeMismatch: $key (String)")

    private fun int(obj: JsonObject, key: String): Int =
      JsonValues.intOrNull(obj[key])
        ?: throw IllegalArgumentException("keyNotFound or typeMismatch: $key (Int)")
  }
}

/** A successfully imported `.typedeck`: manifest, decoded deck and the exact `deck.json` bytes. */
class PiyoDeckPackage(
  val manifest: PiyoDeckManifest,
  val deck: Deck,
  deckData: ByteArray,
) {
  private val deckBytes = deckData.copyOf()

  /** A copy of the exact `deck.json` entry bytes. */
  val deckData: ByteArray get() = deckBytes.copyOf()

  val contentSha256: String get() = manifest.deck.sha256

  override fun equals(other: Any?): Boolean =
    other is PiyoDeckPackage && manifest == other.manifest && deck == other.deck &&
      deckBytes.contentEquals(other.deckBytes)

  override fun hashCode(): Int =
    (manifest.hashCode() * 31 + deck.hashCode()) * 31 + deckBytes.contentHashCode()

  override fun toString(): String =
    "PiyoDeckPackage(manifest=$manifest, deck=${deck.deckId}, deckData=${deckBytes.size} bytes)"
}
