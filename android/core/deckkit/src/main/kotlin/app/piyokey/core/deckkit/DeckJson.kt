package app.piyokey.core.deckkit

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * JSON codec for DeckKit models (the Kotlin counterpart of Swift `DeckKitJSON`).
 *
 * Decoding tolerates unknown keys (like Swift `JSONDecoder`) but requires every non-optional
 * field; dates use ISO 8601 internet date-time without fractional seconds. Structural strictness
 * (unknown fields, duplicate keys, canonical timestamps) is the job of [JsonSchemaValidator] and
 * the `.typedeck` reader.
 */
object DeckJson {
  /** The shared kotlinx `Json` configuration used by every DeckKit decode/encode call. */
  val json: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
  }

  /** @throws SerializationException / IllegalArgumentException on malformed or incomplete JSON. */
  fun decodeDeck(bytes: ByteArray): Deck = decodeDeck(bytes.toString(Charsets.UTF_8))

  fun decodeDeck(text: String): Deck = json.decodeFromString(Deck.serializer(), text)

  fun decodeCatalog(bytes: ByteArray): Catalog = decodeCatalog(bytes.toString(Charsets.UTF_8))

  fun decodeCatalog(text: String): Catalog = json.decodeFromString(Catalog.serializer(), text)

  fun decodeDeckItem(text: String): DeckItem = json.decodeFromString(DeckItem.serializer(), text)

  /**
   * Encodes a deck with snake_case keys sorted lexicographically. `pretty = true` (default)
   * mirrors Swift `DeckKitJSON.makeEncoder()` (pretty printed, sorted keys); `pretty = false`
   * yields compact bytes. `nil` optionals are omitted. This is not the `.typedeck` canonical
   * form; use [PiyoDeckPackageWriter] for that.
   */
  fun encodeDeck(deck: Deck, pretty: Boolean = true): String =
    CanonicalJson.write(json.encodeToJsonElement(Deck.serializer(), deck), pretty = pretty)

  fun encodeCatalog(catalog: Catalog, pretty: Boolean = true): String =
    CanonicalJson.write(json.encodeToJsonElement(Catalog.serializer(), catalog), pretty = pretty)

  fun encodeDeckToElement(deck: Deck): JsonElement =
    json.encodeToJsonElement(Deck.serializer(), deck)
}

/**
 * ISO 8601 internet date-time (`2026-08-14T00:00:00Z` or `...+09:00`), without fractional
 * seconds, matching Swift `JSONDecoder.DateDecodingStrategy.iso8601`. Encodes whole-second UTC
 * `yyyy-MM-ddTHH:mm:ssZ`.
 */
object Iso8601InstantSerializer : KSerializer<Instant> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("app.piyokey.core.deckkit.Iso8601Instant", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeString(format(value))
  }

  override fun deserialize(decoder: Decoder): Instant {
    val text = decoder.decodeString()
    return parse(text) ?: throw SerializationException("Expected ISO 8601 date-time: $text")
  }

  private val pattern =
    Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(Z|[+-][0-9]{2}:?[0-9]{2})$")
  private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")

  fun parse(text: String): Instant? {
    if (!pattern.matches(text)) return null
    val normalized =
      if (text.endsWith("Z")) text
      else {
        val offset = text.substring(19)
        if (offset.contains(':')) text
        else text.substring(0, 19) + offset.substring(0, 3) + ":" + offset.substring(3)
      }
    return try {
      OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (_: DateTimeParseException) {
      null
    }
  }

  fun format(value: Instant): String =
    formatter.format(value.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC))
}
