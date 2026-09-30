package app.piyokey.android.data.persistence

import android.content.SharedPreferences
import android.content.res.AssetManager
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Layout under `filesDir/Hanco` (iOS `Application Support/Hanco`). */
object HancoPaths {
  const val ROOT = "Hanco"
  const val INSTALLED_DECKS = "InstalledDecks"
  const val CATALOG_CACHE = "CatalogCache"
  const val PROGRESS = "Progress"
  const val CURRICULUM = "Curriculum"
  const val REVIEW = "Review"
  const val RETENTION = "Retention"
  const val DECK_MAKER = "DeckMaker"
  const val PENDING_IMPORTS = "PendingImports"

  fun root(filesDir: File): File = File(filesDir, ROOT)
}

/** JSON for app persistence: mirrors Swift `Codable` (unknown keys ignored, nil omitted). */
object StoreJson {
  val json: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
    json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)

  fun <T> decode(serializer: KSerializer<T>, data: ByteArray): T =
    json.decodeFromString(serializer, data.toString(Charsets.UTF_8))

  /** Reads `schema_version` without decoding the rest (iOS `SchemaVersionProbe`). */
  fun schemaVersion(data: ByteArray): Int =
    json.parseToJsonElement(data.toString(Charsets.UTF_8)).jsonObject["schema_version"]?.jsonPrimitive?.intOrNull
      ?: throw SerializationException("Missing schema_version")
}

/** Swift `Data` Codable representation: a base64 string. */
object Base64ByteArraySerializer : KSerializer<ByteArray> {
  override val descriptor = PrimitiveSerialDescriptor("app.piyokey.Base64Data", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.getEncoder().encodeToString(value))
  override fun deserialize(decoder: Decoder): ByteArray = try {
    Base64.getDecoder().decode(decoder.decodeString())
  } catch (error: IllegalArgumentException) {
    throw SerializationException("Invalid base64 data", error)
  }
}

fun sha256Hex(data: ByteArray): String =
  MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Deletes a directory tree (iOS `FileManager.removeItem`). */
fun File.deleteTree() {
  if (exists() && !deleteRecursively()) throw java.io.IOException("Could not delete $this")
}

/**
 * Minimal `UserDefaults` abstraction so stores are testable without Android. Production uses
 * [SharedPreferencesKeyValueStore] over `Prefs.shared` (the same file as `data/settings`).
 */
interface KeyValueStore {
  fun getString(key: String): String?
  fun putString(key: String, value: String?)
  fun getBoolean(key: String, default: Boolean = false): Boolean
  fun putBoolean(key: String, value: Boolean)
  fun contains(key: String): Boolean
  fun remove(vararg keys: String)
}

class SharedPreferencesKeyValueStore(private val prefs: () -> SharedPreferences) : KeyValueStore {
  override fun getString(key: String): String? = runCatching { prefs().getString(key, null) }.getOrNull()
  override fun putString(key: String, value: String?) {
    prefs().edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
  }
  override fun getBoolean(key: String, default: Boolean): Boolean = runCatching { prefs().getBoolean(key, default) }.getOrDefault(default)
  override fun putBoolean(key: String, value: Boolean) = prefs().edit().putBoolean(key, value).apply()
  override fun contains(key: String): Boolean = prefs().contains(key)
  override fun remove(vararg keys: String) = prefs().edit().apply { keys.forEach(::remove) }.apply()
}

class InMemoryKeyValueStore : KeyValueStore {
  val values = LinkedHashMap<String, Any>()
  override fun getString(key: String): String? = values[key] as? String
  override fun putString(key: String, value: String?) {
    if (value == null) values.remove(key) else values[key] = value
  }
  override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
  override fun putBoolean(key: String, value: Boolean) {
    values[key] = value
  }
  override fun contains(key: String): Boolean = values.containsKey(key)
  override fun remove(vararg keys: String) {
    keys.forEach { values.remove(it) }
  }
}

/**
 * Read-only bundled content (`app/build/generated/piyokeyAssets`): `catalog/catalog.json`,
 * `catalog/decks/…`, `catalog/updates/…`, `catalog/audio/…`, `tuning/…`, `schema/deck.schema.json`.
 */
fun interface AssetSource {
  /** File bytes, or `null` when the asset does not exist. */
  fun read(path: String): ByteArray?
}

class AndroidAssetSource(private val assets: AssetManager) : AssetSource {
  override fun read(path: String): ByteArray? = try {
    assets.open(path).use { it.readBytes() }
  } catch (_: FileNotFoundException) {
    null
  }
}

/** Maps asset path prefixes to directories (tests: `catalog/` → `shared/mock_catalog`). */
class DirectoryAssetSource(private val roots: Map<String, File>) : AssetSource {
  override fun read(path: String): ByteArray? {
    if (path.contains("..")) return null
    for ((prefix, root) in roots) {
      if (path.startsWith(prefix)) {
        val file = File(root, path.removePrefix(prefix))
        return if (file.isFile) file.readBytes() else null
      }
    }
    return null
  }
}

object BundledAssetPaths {
  const val CATALOG = "catalog/catalog.json"
  const val DECK_SCHEMA = "schema/deck.schema.json"
  const val RANK_TUNING = "tuning/game_rank_tuning.json"
  const val SPACING_PASSAGES = "catalog/spacing_passages.json"
  const val UPDATES_CATALOG = "catalog/updates/catalog.json"

  /** Catalog `file_url` (`decks/x.json`) → asset path. */
  fun catalogFile(relativePath: String) = "catalog/$relativePath"
}
