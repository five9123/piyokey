package app.piyokey.core.deckkit

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeckType {
  @SerialName("word") WORD,
  @SerialName("sentence") SENTENCE,
}

@Serializable
data class DeckAuthor(
  val id: String,
  val nickname: String,
)

@Serializable
data class DeckItemLocalization(
  val meaning: String,
  val reading: String,
)

@Serializable
data class DeckMetadataLocalization(
  val name: String,
  @SerialName("author_nickname") val authorNickname: String,
  val tags: List<String>,
)

@Serializable
data class DeckItem(
  val id: String,
  val ko: String,
  @SerialName("reading_ja") val readingJa: String,
  @SerialName("meaning_ja") val meaningJa: String,
  val audio: String? = null,
  val localizations: Map<String, DeckItemLocalization>? = null,
) {
  /**
   * Japanese requests (`ja`, `ja-JP`, ...) use the exact tag, then `ja`, then the legacy
   * `meaning_ja`. Other requests use exact tag -> primary subtag -> [defaultLocale] -> `en` and
   * never fall back to `meaning_ja` (returns `null`).
   */
  fun localizedMeaning(languageCode: String, defaultLocale: String? = null): String? =
    localizedValue(languageCode, defaultLocale, meaningJa) { it.meaning }

  /** Same lookup order as [localizedMeaning], using `reading` / `reading_ja`. */
  fun localizedReading(languageCode: String, defaultLocale: String? = null): String? =
    localizedValue(languageCode, defaultLocale, readingJa) { it.reading }

  private inline fun localizedValue(
    languageCode: String,
    defaultLocale: String?,
    japaneseBase: String,
    selector: (DeckItemLocalization) -> String,
  ): String? {
    if (LocaleTag.isJapanese(languageCode)) {
      for (code in LocaleTag.requestedCandidates(languageCode)) {
        localizations?.get(code)?.let { return selector(it) }
      }
      return japaneseBase
    }
    for (code in LocaleTag.lookupCandidates(languageCode, defaultLocale)) {
      localizations?.get(code)?.let { return selector(it) }
    }
    return null
  }
}

@Serializable
data class Deck(
  @SerialName("deck_id") val deckId: String,
  val version: Int,
  val name: String,
  val author: DeckAuthor,
  val official: Boolean,
  val type: DeckType,
  val level: Int,
  val tags: List<String>,
  @SerialName("created_at") @Serializable(with = Iso8601InstantSerializer::class)
  val createdAt: Instant,
  @SerialName("updated_at") @Serializable(with = Iso8601InstantSerializer::class)
  val updatedAt: Instant,
  val items: List<DeckItem>,
  val localizations: Map<String, DeckMetadataLocalization>? = null,
  @SerialName("default_locale") val defaultLocale: String? = null,
) {
  fun localizedName(languageCode: String): String? =
    metadataValue(languageCode, name) { it.name }

  fun localizedAuthorNickname(languageCode: String): String? =
    metadataValue(languageCode, author.nickname) { it.authorNickname }

  fun localizedTags(languageCode: String): List<String>? =
    metadataValue(languageCode, tags) { it.tags }

  fun hasLocalization(languageCode: String): Boolean = localizedName(languageCode) != null

  private inline fun <V> metadataValue(
    languageCode: String,
    japaneseBase: V,
    selector: (DeckMetadataLocalization) -> V,
  ): V? {
    if (LocaleTag.isJapanese(languageCode)) {
      for (code in LocaleTag.requestedCandidates(languageCode)) {
        localizations?.get(code)?.let { return selector(it) }
      }
      return japaneseBase
    }
    for (code in LocaleTag.lookupCandidates(languageCode, defaultLocale)) {
      localizations?.get(code)?.let { return selector(it) }
    }
    return null
  }
}

@Serializable
data class CatalogPreviewItemLocalization(
  val meaning: String,
)

@Serializable
data class CatalogPreviewItem(
  val ko: String,
  @SerialName("meaning_ja") val meaningJa: String,
  val localizations: Map<String, CatalogPreviewItemLocalization>? = null,
) {
  /** Exact -> primary subtag -> (`ja`: `meaning_ja`) -> `en`. */
  fun localizedMeaning(languageCode: String): String? {
    for (code in LocaleTag.requestedCandidates(languageCode)) {
      localizations?.get(code)?.let { return it.meaning }
    }
    if (LocaleTag.isJapanese(languageCode)) return meaningJa
    return localizations?.get("en")?.meaning
  }
}

@Serializable
data class CatalogDeck(
  @SerialName("deck_id") val deckId: String,
  val version: Int,
  val name: String,
  @SerialName("author_nickname") val authorNickname: String,
  val official: Boolean,
  val featured: Boolean,
  val type: DeckType,
  val level: Int,
  val tags: List<String>,
  @SerialName("item_count") val itemCount: Int,
  @SerialName("size_bytes") val sizeBytes: Int,
  @SerialName("downloads_total") val downloadsTotal: Int,
  @SerialName("downloads_7d") val downloads7d: Int,
  @SerialName("created_at") @Serializable(with = Iso8601InstantSerializer::class)
  val createdAt: Instant,
  @SerialName("preview_items") val previewItems: List<CatalogPreviewItem>,
  @SerialName("file_url") val fileUrl: String,
  val localizations: Map<String, DeckMetadataLocalization>? = null,
) {
  fun localizedName(languageCode: String): String? =
    metadataValue(languageCode, name) { it.name }

  fun localizedAuthorNickname(languageCode: String): String? =
    metadataValue(languageCode, authorNickname) { it.authorNickname }

  fun localizedTags(languageCode: String): List<String>? =
    metadataValue(languageCode, tags) { it.tags }

  fun hasLocalization(languageCode: String): Boolean = localizedName(languageCode) != null

  /** `downloads_7d / max(downloads_total - downloads_7d, 1)`. */
  val trendingRatio: Double
    get() = downloads7d.toDouble() / maxOf(downloadsTotal - downloads7d, 1).toDouble()

  private inline fun <V> metadataValue(
    languageCode: String,
    japaneseBase: V,
    selector: (DeckMetadataLocalization) -> V,
  ): V? {
    for (code in LocaleTag.requestedCandidates(languageCode)) {
      localizations?.get(code)?.let { return selector(it) }
    }
    if (LocaleTag.isJapanese(languageCode)) return japaneseBase
    return localizations?.get("en")?.let(selector)
  }
}

@Serializable
data class CatalogTag(
  val tag: String,
  @SerialName("deck_count") val deckCount: Int,
  val category: String,
  val localizations: Map<String, String>? = null,
) {
  /** `ja*` -> [tag]; otherwise the primary-language localization, then `en`. */
  fun localizedTag(languageCode: String): String? {
    val code = normalizedLanguageCode(languageCode)
    if (code == "ja") return tag
    return localizations?.get(code) ?: localizations?.get("en")
  }
}

private fun normalizedLanguageCode(identifier: String): String {
  val first = identifier.replace('_', '-').trimStart('-').substringBefore('-')
  return if (first.isEmpty()) identifier.lowercase() else first.lowercase()
}

@Serializable
data class Catalog(
  @SerialName("catalog_version") val catalogVersion: Int,
  @SerialName("generated_at") @Serializable(with = Iso8601InstantSerializer::class)
  val generatedAt: Instant,
  val decks: List<CatalogDeck>,
  val tags: List<CatalogTag>,
)
