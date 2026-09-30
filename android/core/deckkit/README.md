# `:core:deckkit` — PIYOKEY deck models, validation and `.typedeck`

Pure Kotlin/JVM (Java 17) port of the iOS `DeckKit` library
(`ios/HangulEngine/Sources/DeckKit`). No Android or Compose dependencies. Package:
`app.piyokey.core.deckkit`. Dependencies: `kotlinx-serialization-json` (exposed as `api`),
`:core:hangul` (declared; DeckKit currently uses a private copy of the few Hangul rules it needs,
see "Notes").

Contracts implemented: `shared/piyodeck/SPEC.md`, `shared/schema/*.json`, PRD §8 / F5.9.

Run tests: `cd android && ./gradlew :core:deckkit:test` (tests read `../shared` through the
`piyokey.sharedRoot` system property set in `build.gradle.kts`).

---

## 1. Models (`Models.kt`)

All are `@Serializable` data classes with snake_case `@SerialName`s. Dates are
`java.time.Instant` (ISO 8601 internet date-time, whole seconds, see `Iso8601InstantSerializer`).
Maps preserve JSON document order.

```kotlin
enum class DeckType { WORD /* "word" */, SENTENCE /* "sentence" */ }

data class DeckAuthor(val id: String, val nickname: String)

data class DeckItemLocalization(val meaning: String, val reading: String)

data class DeckMetadataLocalization(
  val name: String,
  val authorNickname: String,          // "author_nickname"
  val tags: List<String>,
)

data class DeckItem(
  val id: String,
  val ko: String,                      // typing target (Hangul)
  val readingJa: String,               // "reading_ja"  legacy base field (Japanese / default-locale mirror)
  val meaningJa: String,               // "meaning_ja"  legacy base field
  val audio: String? = null,           // bundled "audio/ko_<hash>.mp3"; always null in user decks
  val localizations: Map<String, DeckItemLocalization>? = null,  // BCP 47 key -> clue
) {
  fun localizedMeaning(languageCode: String, defaultLocale: String? = null): String?
  fun localizedReading(languageCode: String, defaultLocale: String? = null): String?
}

data class Deck(
  val deckId: String,                  // "deck_id"
  val version: Int,
  val name: String,
  val author: DeckAuthor,
  val official: Boolean,
  val type: DeckType,
  val level: Int,                      // 1...3
  val tags: List<String>,
  val createdAt: Instant,              // "created_at"
  val updatedAt: Instant,              // "updated_at"
  val items: List<DeckItem>,
  val localizations: Map<String, DeckMetadataLocalization>? = null,
  val defaultLocale: String? = null,   // "default_locale"; non-null => deck schema v2
) {
  fun localizedName(languageCode: String): String?
  fun localizedAuthorNickname(languageCode: String): String?
  fun localizedTags(languageCode: String): List<String>?
  fun hasLocalization(languageCode: String): Boolean   // localizedName(...) != null
}

data class CatalogPreviewItemLocalization(val meaning: String)

data class CatalogPreviewItem(
  val ko: String,
  val meaningJa: String,               // "meaning_ja"
  val localizations: Map<String, CatalogPreviewItemLocalization>? = null,
) {
  fun localizedMeaning(languageCode: String): String?
}

data class CatalogDeck(
  val deckId: String, val version: Int, val name: String,
  val authorNickname: String, val official: Boolean, val featured: Boolean,
  val type: DeckType, val level: Int, val tags: List<String>,
  val itemCount: Int, val sizeBytes: Int, val downloadsTotal: Int, val downloads7d: Int,
  val createdAt: Instant,
  val previewItems: List<CatalogPreviewItem>,   // "preview_items", 1...10
  val fileUrl: String,                          // "file_url", e.g. "decks/official_x_v5.json"
  val localizations: Map<String, DeckMetadataLocalization>? = null,
) {
  fun localizedName(languageCode: String): String?
  fun localizedAuthorNickname(languageCode: String): String?
  fun localizedTags(languageCode: String): List<String>?
  fun hasLocalization(languageCode: String): Boolean
  val trendingRatio: Double            // downloads7d / max(downloadsTotal - downloads7d, 1)
}

data class CatalogTag(
  val tag: String, val deckCount: Int, val category: String,   // "deck_count"
  val localizations: Map<String, String>? = null,
) {
  fun localizedTag(languageCode: String): String?
}

data class Catalog(
  val catalogVersion: Int,             // "catalog_version"
  val generatedAt: Instant,            // "generated_at"
  val decks: List<CatalogDeck>,
  val tags: List<CatalogTag>,
)
```

### Localized lookup rules

`languageCode` may be any BCP 47-ish identifier (`ja`, `ja_JP`, `fr-CA`, `zh-Hant`); `_` is
accepted. Malformed requests produce no exact/base candidate.

| API | Japanese request (`ja`, `ja-*`) | Any other request |
| --- | --- | --- |
| `Deck.localized*`, `DeckItem.localized*` | exact tag -> `ja` -> legacy base field (`name`, `author.nickname`, `tags`, `meaning_ja`, `reading_ja`) | exact -> primary subtag -> `defaultLocale` -> `en` -> **`null`** (never the `*_ja` base) |
| `CatalogDeck.localized*`, `CatalogPreviewItem.localizedMeaning` | exact -> `ja` -> legacy base | exact -> primary subtag -> `en` -> `null` |
| `CatalogTag.localizedTag` | `tag` | primary subtag -> `en` -> `null` |

For `DeckItem` pass the owning deck's `defaultLocale`:
`item.localizedMeaning(code, defaultLocale = deck.defaultLocale)`.
UI fallback when a lookup returns `null` is the caller's choice (the iOS app shows the base field).

### `LocaleTag`

```kotlin
object LocaleTag {
  fun canonicalize(value: String): String?          // "ZH-hant" -> "zh-Hant"; malformed -> null ("_" is malformed)
  fun isCanonical(value: String): Boolean
  fun requestedCandidates(requested: String): List<String>   // "fr_ca" -> ["fr-CA", "fr"]
  fun isJapanese(requested: String): Boolean
  fun lookupCandidates(requested: String, defaultLocale: String?): List<String> // + default + "en", de-duplicated
}
```

## 2. JSON (`DeckJson.kt`)

```kotlin
object DeckJson {
  val json: kotlinx.serialization.json.Json  // ignoreUnknownKeys = true, explicitNulls = false
  fun decodeDeck(bytes: ByteArray): Deck
  fun decodeDeck(text: String): Deck
  fun decodeCatalog(bytes: ByteArray): Catalog
  fun decodeCatalog(text: String): Catalog
  fun decodeDeckItem(text: String): DeckItem
  fun encodeDeck(deck: Deck, pretty: Boolean = true): String      // sorted keys, nulls omitted
  fun encodeCatalog(catalog: Catalog, pretty: Boolean = true): String
  fun encodeDeckToElement(deck: Deck): JsonElement
}

object Iso8601InstantSerializer : KSerializer<Instant> {
  fun parse(text: String): Instant?      // "…Z" or "…+09:00"; no fractional seconds
  fun format(value: Instant): String     // "yyyy-MM-ddTHH:mm:ssZ" (UTC, truncated to seconds)
}
```

Decoding mirrors Swift `JSONDecoder`: unknown keys are ignored; every non-optional field is
required (`SerializationException` / `IllegalArgumentException` otherwise). Decoding alone does
**not** validate — call the validators below. Bundled content should additionally be checked
with `JsonSchemaValidator` in tests (see `BundledContentTests`).

```kotlin
val catalog = DeckJson.decodeCatalog(assets.open("catalog.json").readBytes())
val deck = DeckJson.decodeDeck(assets.open(catalog.decks[0].fileUrl).readBytes())
val title = deck.localizedName(locale.toLanguageTag()) ?: deck.name
```

## 3. Validation (`Validation.kt`, `UserDeckValidator.kt`, `JsonSchemaValidator.kt`)

All validators return issues instead of throwing; an empty list means valid.

```kotlin
data class ContentValidationIssue(val code: String, val path: String, val message: String)
// toString(): "[code] path: message". `code` is stable; `message` is Korean developer text.

object DeckValidator {
  fun validate(deck: Deck, schemaVersion: Int? = null): List<ContentValidationIssue>
}
object CatalogValidator {
  fun validate(catalog: Catalog): List<ContentValidationIssue>
}
object CatalogBundleValidator {
  fun validate(catalog: Catalog, decks: List<Deck>): List<ContentValidationIssue>
}
object UserDeckValidator {
  fun validate(deck: Deck): List<ContentValidationIssue>             // DeckValidator + package rules
  fun validatePackageRules(deck: Deck): List<ContentValidationIssue> // user-document rules only
  const val MAX_NAME_LENGTH = 120; const val MAX_NICKNAME_LENGTH = 40; const val MAX_TAG_LENGTH = 40
  const val MAX_READING_LENGTH = 300; const val MAX_MEANING_LENGTH = 500   // grapheme clusters
}
object JsonSchemaValidator {
  @Throws(JsonSchemaValidationError::class)
  fun validate(instanceData: ByteArray, schemaData: ByteArray): List<ContentValidationIssue>
  @Throws(JsonSchemaValidationError::class)
  fun validate(instance: JsonElement, schema: JsonObject): List<ContentValidationIssue>
}
sealed class JsonSchemaValidationError : Exception {
  data object InvalidSchemaRoot
  data class InvalidReference(val reference: String)
  data class InvalidJson(val reason: String)
}
```

`DeckValidator.validate(deck, schemaVersion)`: `null` validates in-memory/catalog content (a
deck with `defaultLocale` is treated as v2). Pass `1` or `2` to apply the package contract
(v1: no `default_locale`, only `en`/`ko` keys; v2: canonical `default_locale` declared in
`localizations`, every item has every declared locale and no undeclared locale).

Issue codes — deck/catalog: `identifier`, `range`, `required`, `tag_count`, `duplicate`,
`localized_tag_count`, `malformed_locale`, `noncanonical_locale`, `unexpected_default_locale`,
`unsupported_locale`, `invalid_locale`, `missing_default_locale`, `missing_localization`,
`undeclared_localization`, `date_order`, `min_items`, `max_target_length` (`ko` > 10
graphemes), `undecomposable_ko`, `missing_hangul`, `preview_count`, `file_url` (catalog `tag_count` also flags a wrong `deck_count`);
bundle: `missing_deck`, `metadata_mismatch`, `unindexed_deck`; user deck:
`max_length`, `reserved_identifier` (`official_` prefix), `user_deck_identifier`
(`^user_[0-9a-f]{32}$`), `user_item_identifier` (`^item_[0-9a-f]{32}$`), `user_deck_official`,
`user_deck_item_limit` (> 1,000), `user_deck_audio`; schema: `schema.<keyword>` (e.g.
`schema.additionalProperties`, `schema.required`, `schema.pattern`, `schema.minProperties`).

## 4. `.typedeck` packages

```kotlin
object PiyoDeckPackageLimits {
  const val MAXIMUM_PACKAGE_BYTES = 8_388_608
  const val MAXIMUM_MANIFEST_BYTES = 16_384
  const val MAXIMUM_DECK_BYTES = 4_194_304
  const val MAXIMUM_ITEM_COUNT = 1_000
}

object PiyoDeckPackageReader {
  @Throws(PiyoDeckImportError::class)
  fun read(data: ByteArray, deckSchemaData: ByteArray): PiyoDeckPackage
}

object PiyoDeckPackageWriter {
  @Throws(PiyoDeckImportError::class)
  fun write(deck: Deck, deckSchemaData: ByteArray): ByteArray   // deterministic bytes
  fun canonicalDeckBytes(deck: Deck): ByteArray                  // the deck.json entry only
}

class PiyoDeckPackage(val manifest: PiyoDeckManifest, val deck: Deck, deckData: ByteArray) {
  val deckData: ByteArray      // copy of the exact deck.json bytes
  val contentSha256: String    // manifest.deck.sha256
}  // equals/hashCode compare deckData by content

data class PiyoDeckManifest(
  val format: String = FORMAT_IDENTIFIER,                 // "piyokey.deck-package"
  val formatVersion: Int = CURRENT_FORMAT_VERSION,        // 1
  val deckSchemaVersion: Int = CURRENT_DECK_SCHEMA_VERSION, // 2 (reader accepts 1 and 2)
  val deck: DeckDescriptor,
) {
  data class DeckDescriptor(
    val path: String = "deck.json", val mediaType: String = "application/json",
    val deckId: String, val deckVersion: Int, val itemCount: Int, val sizeBytes: Int,
    val sha256: String,
  )
  fun toJsonElement(): JsonObject
  fun encode(pretty: Boolean = false): ByteArray
  companion object {
    const val FORMAT_IDENTIFIER: String; const val CURRENT_FORMAT_VERSION: Int
    const val CURRENT_DECK_SCHEMA_VERSION: Int; val SUPPORTED_DECK_SCHEMA_VERSIONS: Set<Int>
    fun decode(element: JsonElement): PiyoDeckManifest   // throws IllegalArgumentException
  }
}
```

`deckSchemaData` is the pinned `shared/schema/deck.schema.json` bytes (bundle it as an app asset).

Reader order (SPEC §5, identical to Swift): package size -> EOCD/central directory topology ->
entry set, paths, flags, method, attributes, entry size limits -> local/central header
cross-check -> CRC-32 -> BOM / UTF-8 / RFC 8259 (incl. unpaired surrogates) / nesting ≤ 64 /
duplicate keys -> manifest decode, `format`, `format_version`, `deck_schema_version`, unknown
manifest keys and field rules -> `size_bytes`, SHA-256, `deck_id`, `version`, item count ->
deck JSON Schema -> `DeckValidator.validate(deck, manifest.deckSchemaVersion)` ->
`UserDeckValidator.validatePackageRules`.

Writer: `UserDeckValidator.validate` -> canonical `deck.json` (compact, keys sorted, non-ASCII
raw, `/` unescaped, explicit `"audio":null`) -> deck schema -> size limit -> manifest
(`deck_schema_version` = `2` iff `defaultLocale != null`, else `1`) -> STORE ZIP
(`manifest.json`, `deck.json`; version 20, UTF-8 flag, DOS date 1980-01-01). Output reproduces
`shared/piyodeck/fixtures/valid/{basic,localized,multilingual}.typedeck` byte-for-byte.

### Errors

```kotlin
sealed class PiyoDeckImportError(message: String) : Exception(message) {
  data class PackageTooLarge(val actual: Int, val maximum: Int)
  data class MalformedArchive(val reason: String)
  data class UnsupportedArchiveFeature(val feature: String)   // "encryption", "ZIP64", "compression method 8", ...
  data class InvalidEntrySet(val entries: List<String>)
  data class UnsafeEntryPath(val path: String)
  data class EntryTooLarge(val name: String, val actual: Int, val maximum: Int)
  data class CrcMismatch(val name: String)
  data class InvalidJson(val name: String, val reason: String)
  data class DuplicateJsonKey(val name: String, val path: String)   // path like "$.items[0].id"
  data class InvalidManifest(val issues: List<ContentValidationIssue>)
  data class UnsupportedFormatVersion(val version: Int)
  data class UnsupportedDeckSchemaVersion(val version: Int)
  data class DeckSchemaViolation(val issues: List<ContentValidationIssue>)
  data class InvalidUserDeck(val issues: List<ContentValidationIssue>)
  data class ManifestMismatch(val field: String)                    // "deck.size_bytes", "deck.item_count", ...
  data object Sha256Mismatch

  val family: Family                     // coarse family, see below
  val isUnsupportedFutureVersion: Boolean  // show "update PIYOKEY" instead of "damaged file"
  enum class Family(val id: String) {
    SHA256_MISMATCH, INVALID_JSON, UNSUPPORTED_VERSION, UNSUPPORTED_ARCHIVE_FEATURE,
    UNSAFE_ENTRY_PATH, MALFORMED_ARCHIVE, CRC_MISMATCH, INVALID_CONTENT,
    TOO_LARGE, INVALID_ENTRY_SET, INVALID_MANIFEST
  }
}
```

`Family.id` values (`"invalid_json"`, `"unsupported_version"`, ...) match the `expectation`
strings in `shared/piyodeck/fixtures/cases.json`. `TOO_LARGE`, `INVALID_ENTRY_SET` and
`INVALID_MANIFEST` are Kotlin-only groupings for cases the Swift test helper maps to
`"unexpected"`.

```kotlin
val result = runCatching { PiyoDeckPackageReader.read(fileBytes, deckSchemaBytes) }
result.onFailure { e ->
  val error = e as PiyoDeckImportError       // the reader only throws PiyoDeckImportError
  if (error.isUnsupportedFutureVersion) showUpdateAppMessage() else showInvalidFileMessage()
}
val exported: ByteArray = PiyoDeckPackageWriter.write(editedDeck, deckSchemaBytes)
```

## 5. Notes and deviations from Swift

- Hangul checks (`undecomposable_ko`, `missing_hangul`) use a private internal copy of
  `JamoDecomposer.keySequence` / `containsHangul` rules (`DeckKitHangul` in `DeckKitText.kt`)
  instead of `:core:hangul`, which was being ported concurrently. "Numeric" literals are
  approximated with Unicode general categories Nd/Nl/No (Swift uses ICU `Numeric_Type`, which
  also covers Unihan numerals such as `一`).
- Swift `String` semantics are reproduced where they affect results: lengths/caps count
  grapheme clusters (`java.text.BreakIterator`), duplicate tags/IDs and duplicate JSON keys use
  canonical equivalence (NFC). `JsonSchemaValidator` lengths count Unicode scalars, as in Swift.
- `JsonSchemaValidator` throws `JsonSchemaValidationError.InvalidJson` for unparsable input
  (Swift surfaces a Foundation `NSError`); the reader/writer wrap it as
  `PiyoDeckImportError.InvalidJson`, as Swift does.
- kotlinx decoding of models accepts quoted numbers for `Int` fields, which Swift rejects;
  package imports are unaffected because the JSON Schema check runs first.
- Kotlin `Int` is 32-bit: manifest integers outside the `Int` range fail as
  `InvalidManifest("manifest.decode")` instead of a range/version error.
