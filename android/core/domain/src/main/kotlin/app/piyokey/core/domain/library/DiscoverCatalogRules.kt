package app.piyokey.core.domain.library

import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.DeckType
import java.text.Normalizer
import java.util.Locale

/** iOS `CatalogSortOrder`. */
enum class CatalogSortOrder(val raw: String) { POPULAR("popular"), NEWEST("newest"), TRENDING("trending") }

/**
 * How catalog entries read in the app language (iOS `appName` / `appAuthorNickname` / `appTags` /
 * `isAvailableInCurrentLanguage` / `CatalogTag.appTag`). [unavailableTitle] and [officialAuthor]
 * are the localized fallbacks the app shows.
 */
data class CatalogText(
  val languageCode: String,
  val unavailableTitle: String = "",
  val officialAuthor: String = "",
  val unavailableAuthor: String = "",
) {
  fun name(deck: CatalogDeck): String = deck.localizedName(languageCode) ?: unavailableTitle
  fun author(deck: CatalogDeck): String =
    deck.localizedAuthorNickname(languageCode) ?: if (deck.official) officialAuthor else unavailableAuthor
  fun tags(deck: CatalogDeck): List<String> = deck.localizedTags(languageCode) ?: emptyList()
  fun isAvailable(deck: CatalogDeck): Boolean = deck.hasLocalization(languageCode)
  fun tag(catalog: Catalog, rawTag: String): String? = catalog.tags.firstOrNull { it.tag == rawTag }?.localizedTag(languageCode)
}

/** Discover filter state (iOS `DiscoverViewModel` published filters). */
data class DiscoverFilters(
  val query: String = "",
  val selectedTags: Set<String> = emptySet(),
  val selectedType: DeckType? = null,
  val selectedLevel: Int? = null,
  val sortOrder: CatalogSortOrder = CatalogSortOrder.POPULAR,
) {
  val normalizedQuery: String get() = query.trim()

  /** Sort order alone is not a filter (iOS `hasActiveFilters`). */
  val hasActiveFilters: Boolean
    get() = normalizedQuery.isNotEmpty() || selectedTags.isNotEmpty() || selectedType != null || selectedLevel != null

  fun togglingTag(tag: String): DiscoverFilters =
    copy(selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag)

  /** iOS `resetFilters()` also resets the sort order. */
  fun reset(): DiscoverFilters = DiscoverFilters()
}

/** Pure Discover rules (iOS `DiscoverViewModel` + `DeckDetailView.relatedDecks`). */
object DiscoverCatalogRules {
  val PREFERRED_SHORTCUT_TAGS = listOf("入門", "子音", "母音", "パッチム", "日常", "韓国旅行", "TOPIK", "今どき", "K-POP", "Kドラマ")
  val BASIC_TAGS = setOf("入門", "キーボード", "子音", "母音", "組み立て", "パッチム")
  const val TRENDING_KOREAN_TAG = "今どき"
  val PURPOSE_TAGS = setOf("日常", "韓国旅行", "TOPIK", "基礎単語", "K-POP", "Kドラマ")
  const val SHORTCUT_LIMIT = 12
  const val RANKED_LIMIT = 10
  const val RELATED_LIMIT = 6

  fun availableDecks(catalog: Catalog?, text: CatalogText): List<CatalogDeck> =
    catalog?.decks.orEmpty().filter(text::isAvailable)

  fun shortcutTags(catalog: Catalog?, text: CatalogText): List<String> {
    catalog ?: return emptyList()
    val popularTopics = catalog.tags
      .sortedWith { lhs, rhs ->
        if (lhs.deckCount == rhs.deckCount) lhs.tag.compareTo(rhs.tag) else rhs.deckCount.compareTo(lhs.deckCount)
      }
      .map { it.tag }
    return (PREFERRED_SHORTCUT_TAGS + popularTopics).distinct()
      .filter { tag -> catalog.tags.any { it.tag == tag && !it.localizedTag(text.languageCode).isNullOrEmpty() } }
      .take(SHORTCUT_LIMIT)
  }

  fun filteredDecks(catalog: Catalog?, filters: DiscoverFilters, text: CatalogText): List<CatalogDeck> {
    catalog ?: return emptyList()
    val query = SearchFolding.fold(filters.normalizedQuery)
    val filtered = availableDecks(catalog, text).filter { deck ->
      matchesQuery(deck, query, text) &&
        deck.tags.toSet().containsAll(filters.selectedTags) &&
        (filters.selectedType == null || deck.type == filters.selectedType) &&
        (filters.selectedLevel == null || deck.level == filters.selectedLevel)
    }
    return sorted(filtered, filters.sortOrder, text)
  }

  fun trendingDecks(catalog: Catalog?, text: CatalogText) =
    sorted(availableDecks(catalog, text), CatalogSortOrder.TRENDING, text).take(RANKED_LIMIT)

  fun popularDecks(catalog: Catalog?, text: CatalogText) =
    sorted(availableDecks(catalog, text), CatalogSortOrder.POPULAR, text).take(RANKED_LIMIT)

  fun newestDecks(catalog: Catalog?, text: CatalogText) =
    sorted(availableDecks(catalog, text), CatalogSortOrder.NEWEST, text).take(RANKED_LIMIT)

  fun featuredDecks(catalog: Catalog?, text: CatalogText) =
    sorted(availableDecks(catalog, text).filter { it.featured }, CatalogSortOrder.POPULAR, text)

  fun basicDecks(catalog: Catalog?, text: CatalogText) =
    availableDecks(catalog, text).filter { deck -> deck.official && deck.tags.any { it in BASIC_TAGS } }

  fun trendingKoreanDecks(catalog: Catalog?, text: CatalogText) =
    availableDecks(catalog, text).filter { it.official && TRENDING_KOREAN_TAG in it.tags }

  fun purposeDecks(catalog: Catalog?, text: CatalogText) =
    availableDecks(catalog, text).filter { deck -> deck.official && deck.tags.any { it in PURPOSE_TAGS } }

  fun officialDecks(catalog: Catalog?, text: CatalogText) = availableDecks(catalog, text).filter { it.official }

  fun sorted(decks: List<CatalogDeck>, order: CatalogSortOrder, text: CatalogText): List<CatalogDeck> =
    decks.sortedWith { lhs, rhs ->
      when (order) {
        CatalogSortOrder.POPULAR ->
          if (lhs.downloadsTotal == rhs.downloadsTotal) text.name(lhs).compareTo(text.name(rhs))
          else rhs.downloadsTotal.compareTo(lhs.downloadsTotal)
        CatalogSortOrder.NEWEST ->
          if (lhs.createdAt == rhs.createdAt) text.name(lhs).compareTo(text.name(rhs))
          else rhs.createdAt.compareTo(lhs.createdAt)
        CatalogSortOrder.TRENDING ->
          if (lhs.trendingRatio == rhs.trendingRatio) rhs.downloads7d.compareTo(lhs.downloads7d)
          else rhs.trendingRatio.compareTo(lhs.trendingRatio)
      }
    }

  /** Deck detail "related decks": shared tags first, then downloads; at most 6. */
  fun relatedDecks(deck: CatalogDeck, catalogDecks: List<CatalogDeck>, text: CatalogText): List<CatalogDeck> {
    val tags = deck.tags.toSet()
    return catalogDecks
      .filter { it.deckId != deck.deckId && text.isAvailable(it) && it.tags.any(tags::contains) }
      .sortedWith { lhs, rhs ->
        val lhsMatches = lhs.tags.toSet().intersect(tags).size
        val rhsMatches = rhs.tags.toSet().intersect(tags).size
        if (lhsMatches == rhsMatches) rhs.downloadsTotal.compareTo(lhs.downloadsTotal) else rhsMatches.compareTo(lhsMatches)
      }
      .take(RELATED_LIMIT)
  }

  /** Average Korean length of the preview items ("—" when there are none), iOS `%.1f`. */
  fun averagePreviewLength(deck: CatalogDeck): Double? {
    if (deck.previewItems.isEmpty()) return null
    val total = deck.previewItems.sumOf { it.ko.graphemeLength() }
    return total.toDouble() / deck.previewItems.size
  }

  private fun matchesQuery(deck: CatalogDeck, foldedQuery: String, text: CatalogText): Boolean {
    if (foldedQuery.isEmpty()) return true
    val fields = listOf(text.name(deck), text.author(deck)) + text.tags(deck)
    return fields.any { SearchFolding.fold(it).contains(foldedQuery) }
  }
}

/**
 * Case-insensitive search normalization. Android additionally folds Latin accents and ß/œ/æ for
 * keyboards without accented keys (DECISIONS 2026-08-28); Japanese voicing marks and Hangul
 * composition are preserved (only marks following a Latin letter are removed).
 */
object SearchFolding {
  private val latinMarks = Regex("(?<=\\p{IsLatin})\\p{M}+")

  fun fold(value: String): String = Normalizer.normalize(
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
      .lowercase(Locale.ROOT)
      .replace(latinMarks, "")
      .replace("ß", "ss")
      .replace("œ", "oe")
      .replace("æ", "ae"),
    Normalizer.Form.NFC,
  )
}

/** Swift `String.count` (grapheme clusters). */
internal fun String.graphemeLength(): Int {
  val iterator = java.text.BreakIterator.getCharacterInstance()
  iterator.setText(this)
  var count = 0
  while (iterator.next() != java.text.BreakIterator.DONE) count++
  return count
}
