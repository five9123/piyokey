package app.piyokey.core.deckkit

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object Shared {
  val root: File by lazy {
    val path = System.getProperty("piyokey.sharedRoot")
      ?: error("piyokey.sharedRoot system property is not set")
    File(path).also { check(it.isDirectory) { "shared root missing: $it" } }
  }

  fun bytes(relative: String): ByteArray = File(root, relative).readBytes()

  fun fixture(name: String): ByteArray = bytes("piyodeck/fixtures/$name")

  fun schema(name: String): ByteArray = bytes("schema/$name")

  val deckSchema: ByteArray by lazy { schema("deck.schema.json") }

  fun catalogData(): ByteArray = bytes("mock_catalog/catalog.json")

  fun loadCatalog(): Catalog = DeckJson.decodeCatalog(catalogData())

  fun loadDeck(entry: CatalogDeck, base: String = "mock_catalog"): Deck =
    DeckJson.decodeDeck(bytes("$base/${entry.fileUrl}"))

  fun sha256(data: ByteArray): String = PiyoDeckDigest.sha256Hex(data)
}

/** Minimal mutable-JSON helpers for the "decode, mutate, re-encode" tests. */
internal object JsonEdit {
  fun parseObject(data: ByteArray): JsonObject =
    Json.parseToJsonElement(data.toString(Charsets.UTF_8)) as JsonObject

  fun bytes(element: JsonElement): ByteArray = CanonicalJson.bytes(element)

  fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))

  fun JsonObject.without(key: String): JsonObject = JsonObject(this - key)

  fun JsonArray.replacing(index: Int, value: JsonElement): JsonArray =
    JsonArray(toMutableList().also { it[index] = value })
}

internal fun replaceAll(data: ByteArray, target: ByteArray, replacement: ByteArray): ByteArray {
  require(target.size == replacement.size)
  val result = data.copyOf()
  var index = 0
  while (index <= result.size - target.size) {
    if ((target.indices).all { result[index + it] == target[it] }) {
      replacement.copyInto(result, index)
      index += replacement.size
    } else {
      index += 1
    }
  }
  return result
}

internal fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
  outer@ for (i in 0..haystack.size - needle.size) {
    for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
    return i
  }
  return -1
}

internal val epoch100: java.time.Instant = java.time.Instant.ofEpochSecond(100)
