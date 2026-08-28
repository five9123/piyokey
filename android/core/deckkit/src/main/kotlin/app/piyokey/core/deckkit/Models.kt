package app.piyokey.core.deckkit

import java.time.Instant

enum class DeckType {
  WORD,
  SENTENCE,
}

data class DeckAuthor(
  val id: String,
  val nickname: String,
)

data class DeckItemLocalization(
  val meaning: String,
  val reading: String,
)

data class DeckMetadataLocalization(
  val name: String,
  val authorNickname: String,
  val tags: List<String>,
)

data class DeckItem(
  val id: String,
  val ko: String,
  val readingJa: String,
  val meaningJa: String,
  val audio: String?,
  val localizations: Map<String, DeckItemLocalization>? = null,
) {
  fun localizedMeaning(languageCode: String): String? = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> meaningJa
    else -> localizations?.get(code)?.meaning ?: localizations?.get("en")?.meaning
  }

  fun localizedReading(languageCode: String): String? = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> readingJa
    else -> localizations?.get(code)?.reading ?: localizations?.get("en")?.reading
  }
}

data class Deck(
  val deckId: String,
  val version: Int,
  val name: String,
  val author: DeckAuthor,
  val official: Boolean,
  val type: DeckType,
  val level: Int,
  val tags: List<String>,
  val createdAt: Instant,
  val updatedAt: Instant,
  val items: List<DeckItem>,
  val localizations: Map<String, DeckMetadataLocalization>? = null,
) {
  fun localizedName(languageCode: String): String? = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> name
    else -> metadataLocalization(code)?.name ?: metadataLocalization("en")?.name
  }

  fun localizedAuthorNickname(languageCode: String): String? =
    when (val code = normalizeLanguageCode(languageCode)) {
      "ja" -> author.nickname
      else -> metadataLocalization(code)?.authorNickname
        ?: metadataLocalization("en")?.authorNickname
    }

  fun localizedTags(languageCode: String): List<String>? =
    when (val code = normalizeLanguageCode(languageCode)) {
      "ja" -> tags
      else -> metadataLocalization(code)?.tags ?: metadataLocalization("en")?.tags
    }

  fun hasLocalization(languageCode: String): Boolean = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> true
    else -> localizations?.containsKey(code) == true || localizations?.containsKey("en") == true
  }

  private fun metadataLocalization(languageCode: String): DeckMetadataLocalization? =
    localizations?.get(languageCode)
}

data class CatalogPreviewItemLocalization(
  val meaning: String,
)

data class CatalogPreviewItem(
  val ko: String,
  val meaningJa: String,
  val localizations: Map<String, CatalogPreviewItemLocalization>? = null,
) {
  fun localizedMeaning(languageCode: String): String? = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> meaningJa
    else -> localizations?.get(code)?.meaning ?: localizations?.get("en")?.meaning
  }
}

data class CatalogDeck(
  val deckId: String,
  val version: Int,
  val name: String,
  val authorNickname: String,
  val official: Boolean,
  val featured: Boolean,
  val type: DeckType,
  val level: Int,
  val tags: List<String>,
  val itemCount: Int,
  val sizeBytes: Int,
  val downloadsTotal: Int,
  val downloads7d: Int,
  val createdAt: Instant,
  val previewItems: List<CatalogPreviewItem>,
  val fileUrl: String,
  val localizations: Map<String, DeckMetadataLocalization>? = null,
) {
  fun localizedName(languageCode: String): String? = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> name
    else -> metadataLocalization(code)?.name ?: metadataLocalization("en")?.name
  }

  fun localizedAuthorNickname(languageCode: String): String? =
    when (val code = normalizeLanguageCode(languageCode)) {
      "ja" -> authorNickname
      else -> metadataLocalization(code)?.authorNickname
        ?: metadataLocalization("en")?.authorNickname
    }

  fun localizedTags(languageCode: String): List<String>? =
    when (val code = normalizeLanguageCode(languageCode)) {
      "ja" -> tags
      else -> metadataLocalization(code)?.tags ?: metadataLocalization("en")?.tags
    }

  fun hasLocalization(languageCode: String): Boolean = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> true
    else -> localizations?.containsKey(code) == true || localizations?.containsKey("en") == true
  }

  val trendingRatio: Double
    get() = downloads7d.toDouble() / (downloadsTotal - downloads7d).coerceAtLeast(1).toDouble()

  private fun metadataLocalization(languageCode: String): DeckMetadataLocalization? =
    localizations?.get(languageCode)
}

data class CatalogTag(
  val tag: String,
  val deckCount: Int,
  val category: String,
  val localizations: Map<String, String>? = null,
) {
  fun localizedTag(languageCode: String): String? = when (val code = normalizeLanguageCode(languageCode)) {
    "ja" -> tag
    else -> localizations?.get(code) ?: localizations?.get("en")
  }
}

data class Catalog(
  val catalogVersion: Int,
  val generatedAt: Instant,
  val decks: List<CatalogDeck>,
  val tags: List<CatalogTag>,
)

internal fun normalizeLanguageCode(identifier: String): String =
  identifier.replace('_', '-').substringBefore('-').lowercase()
