package app.piyokey.core.data

import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.settings.OnboardingLevel
import java.text.Normalizer
import java.util.Locale

enum class DeckSort {
  POPULAR,
  NEWEST,
  TRENDING,
  ITEM_COUNT,
}

data class DeckFilters(
  val query: String = "",
  val type: DeckType? = null,
  val level: Int? = null,
  val canonicalTags: Set<String> = emptySet(),
  val minimumItems: Int? = null,
  val maximumItems: Int? = null,
  val sort: DeckSort = DeckSort.POPULAR,
)

object DiscoveryEngine {
  fun starterRecommendations(
    catalog: Catalog,
    preferredTags: Set<String>,
    level: OnboardingLevel?,
    limit: Int = 3,
  ): List<CatalogDeck> = catalog.decks.asSequence()
    .filter(CatalogDeck::official)
    .sortedWith(
      compareBy<CatalogDeck> {
        (level ?: OnboardingLevel.BEGINNER).recommendationRank(it.level, it.tags, it.type == DeckType.SENTENCE)
      }.thenByDescending { deck -> deck.tags.count { it in preferredTags } }
        .thenByDescending(CatalogDeck::featured)
        .thenByDescending(CatalogDeck::downloadsTotal)
        .thenBy(CatalogDeck::deckId),
    )
    .take(limit.coerceAtLeast(0))
    .toList()

  fun filterAndSort(
    catalog: Catalog,
    filters: DeckFilters,
    languageCode: String,
  ): List<CatalogDeck> {
    val normalizedQuery = normalize(filters.query)
    return catalog.decks.asSequence()
      .filter(CatalogDeck::official)
      .filter { filters.type == null || it.type == filters.type }
      .filter { filters.level == null || it.level == filters.level }
      .filter { it.tags.containsAll(filters.canonicalTags) }
      .filter { filters.minimumItems == null || it.itemCount >= filters.minimumItems }
      .filter { filters.maximumItems == null || it.itemCount <= filters.maximumItems }
      .filter { deck ->
        normalizedQuery.isEmpty() || listOfNotNull(
          deck.localizedName(languageCode),
          deck.localizedAuthorNickname(languageCode),
        ).plus(deck.localizedTags(languageCode).orEmpty()).any { value ->
          normalize(value).contains(normalizedQuery)
        }
      }
      .sortedWith(comparator(filters.sort, languageCode))
      .toList()
  }

  fun homeRecommendations(
    catalog: Catalog,
    installedDeckIds: Set<String>,
    downloadHistoryTags: List<List<String>>,
    onboardingGoalTags: Set<String> = emptySet(),
    limit: Int = 3,
  ): List<CatalogDeck> {
    val historyWeights = downloadHistoryTags.flatten().groupingBy { it }.eachCount()
    return catalog.decks.asSequence()
      .filter(CatalogDeck::official)
      .filterNot { it.deckId in installedDeckIds }
      .sortedWith(
        compareByDescending<CatalogDeck> { deck ->
          deck.tags.sumOf { historyWeights[it] ?: 0 } * 10 +
            deck.tags.count { it in onboardingGoalTags }
        }.thenByDescending(CatalogDeck::featured)
          .thenByDescending(CatalogDeck::downloadsTotal)
          .thenBy(CatalogDeck::deckId),
      )
      .take(limit)
      .toList()
  }

  fun sameTagRecommendations(
    catalog: Catalog,
    source: CatalogDeck,
    installedDeckIds: Set<String>,
    limit: Int = 2,
  ): List<CatalogDeck> = catalog.decks.asSequence()
    .filter(CatalogDeck::official)
    .filterNot { it.deckId == source.deckId }
    .filter { candidate -> candidate.tags.any { it in source.tags } }
    .sortedWith(
      compareBy<CatalogDeck> { it.deckId in installedDeckIds }
        .thenByDescending { candidate -> candidate.tags.count { it in source.tags } }
        .thenByDescending(CatalogDeck::downloadsTotal)
        .thenBy(CatalogDeck::deckId),
    )
    .take(limit)
    .toList()

  fun canonicalTag(catalog: Catalog, displayedTag: String, languageCode: String): String? {
    val wanted = normalize(displayedTag)
    return catalog.tags.firstOrNull { tag ->
      normalize(tag.tag) == wanted || normalize(tag.localizedTag(languageCode).orEmpty()) == wanted
    }?.tag
  }

  private fun comparator(sort: DeckSort, languageCode: String): Comparator<CatalogDeck> {
    val nameComparator = compareBy<CatalogDeck> {
      normalize(it.localizedName(languageCode).orEmpty())
    }
    return when (sort) {
      DeckSort.POPULAR -> compareByDescending<CatalogDeck>(CatalogDeck::downloadsTotal)
        .thenByDescending(CatalogDeck::featured)
        .then(nameComparator)
      DeckSort.NEWEST -> compareByDescending<CatalogDeck>(CatalogDeck::createdAt)
        .then(nameComparator)
      DeckSort.TRENDING -> compareByDescending<CatalogDeck>(CatalogDeck::trendingRatio)
        .thenByDescending(CatalogDeck::downloads7d)
        .then(nameComparator)
      DeckSort.ITEM_COUNT -> compareByDescending<CatalogDeck>(CatalogDeck::itemCount)
        .then(nameComparator)
    }
  }

  // Fold Latin accents for keyboards without accented keys, without removing
  // Korean jamo or Japanese voicing marks from canonical tags and searches.
  private val latinMarks = Regex("(?<=\\p{IsLatin})\\p{M}+")

  private fun normalize(value: String): String = Normalizer.normalize(
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
      .lowercase(Locale.ROOT)
      .replace(latinMarks, "")
      .replace("ß", "ss")
      .replace("œ", "oe")
      .replace("æ", "ae"),
    Normalizer.Form.NFC,
  )
}
