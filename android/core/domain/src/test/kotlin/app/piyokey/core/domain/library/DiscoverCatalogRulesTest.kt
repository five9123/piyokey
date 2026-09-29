package app.piyokey.core.domain.library

import app.piyokey.core.deckkit.CatalogValidator
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.domain.TestSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of iOS `DiscoverViewModelTests` plus the Android search-folding decision. */
class DiscoverCatalogRulesTest {
  private val catalog = TestSupport.bundledCatalog
  private val ja = CatalogText("ja")

  @Test
  fun bundledCatalogLoadsAndValidatesOfficialLaunchDecks() {
    assertEquals(26, catalog.decks.size)
    assertTrue(catalog.decks.all { it.official })
    assertTrue(catalog.tags.none { it.category == "artist" })
    assertTrue(CatalogValidator.validate(catalog).isEmpty())
  }

  @Test
  fun searchMatchesNameTagAndAuthor() {
    val results = DiscoverCatalogRules.filteredDecks(catalog, DiscoverFilters(query = "TOPIK"), ja)
    assertFalse(results.isEmpty())
    assertTrue(
      results.all { deck ->
        deck.name.contains("TOPIK", ignoreCase = true) || deck.authorNickname.contains("TOPIK", ignoreCase = true) ||
          deck.tags.any { it.contains("TOPIK", ignoreCase = true) }
      },
    )
    // Lower-case and surrounding whitespace do not matter.
    assertEquals(results, DiscoverCatalogRules.filteredDecks(catalog, DiscoverFilters(query = "  topik "), ja))
  }

  @Test
  fun multipleTagAndTypeFiltersUseIntersection() {
    val filters = DiscoverFilters(selectedTags = setOf("公式", "単語"), selectedType = DeckType.WORD)
    val results = DiscoverCatalogRules.filteredDecks(catalog, filters, ja)
    assertFalse(results.isEmpty())
    assertTrue(results.all { it.official && it.type == DeckType.WORD && "公式" in it.tags && "単語" in it.tags })
    assertTrue(filters.hasActiveFilters)
    assertFalse(filters.reset().hasActiveFilters)
    assertEquals(setOf("公式"), filters.togglingTag("単語").selectedTags)
  }

  @Test
  fun levelFilterAndSortOrderAloneIsNotAFilter() {
    assertFalse(DiscoverFilters(sortOrder = CatalogSortOrder.NEWEST).hasActiveFilters)
    val levelOne = DiscoverCatalogRules.filteredDecks(catalog, DiscoverFilters(selectedLevel = 1), ja)
    assertTrue(levelOne.isNotEmpty() && levelOne.all { it.level == 1 })
  }

  @Test
  fun trendingSortUsesCatalogRatioFormula() {
    val decks = DiscoverCatalogRules.trendingDecks(catalog, ja)
    assertEquals(10, decks.size)
    decks.zipWithNext().forEach { (a, b) -> assertTrue(a.trendingRatio >= b.trendingRatio) }
  }

  @Test
  fun popularAndNewestSortsBreakTiesByName() {
    val popular = DiscoverCatalogRules.sorted(catalog.decks, CatalogSortOrder.POPULAR, ja)
    popular.zipWithNext().forEach { (a, b) ->
      assertTrue(a.downloadsTotal > b.downloadsTotal || (a.downloadsTotal == b.downloadsTotal && ja.name(a) <= ja.name(b)))
    }
    val newest = DiscoverCatalogRules.sorted(catalog.decks, CatalogSortOrder.NEWEST, ja)
    newest.zipWithNext().forEach { (a, b) -> assertTrue(a.createdAt >= b.createdAt) }
  }

  @Test
  fun launchSectionsSeparateBasicsAndTrendingKorean() {
    assertFalse(DiscoverCatalogRules.basicDecks(catalog, ja).isEmpty())
    assertEquals(
      listOf("official_trending_korean", "official_fun_friend_reactions"),
      DiscoverCatalogRules.trendingKoreanDecks(catalog, ja).map { it.deckId },
    )
    val official = DiscoverCatalogRules.officialDecks(catalog, ja)
    assertEquals(26, official.size)
    assertEquals(9, official.count { it.deckId.startsWith("official_fun_") })
    assertEquals(4, official.count { "K-POP" in it.tags })
    assertEquals(3, official.count { "Kドラマ" in it.tags })
    val purpose = DiscoverCatalogRules.purposeDecks(catalog, ja)
    assertTrue(purpose.any { "K-POP" in it.tags })
    assertTrue(purpose.any { "Kドラマ" in it.tags })
  }

  @Test
  fun shortcutTagsPreferCuratedOrderAndStopAtTwelve() {
    val tags = DiscoverCatalogRules.shortcutTags(catalog, ja)
    assertEquals(12, tags.size)
    assertEquals(DiscoverCatalogRules.PREFERRED_SHORTCUT_TAGS, tags.take(10))
    assertEquals(tags.distinct(), tags)
    assertTrue(DiscoverCatalogRules.shortcutTags(null, ja).isEmpty())
  }

  @Test
  fun supportedLanguageSearchFoldsLatinAccents() {
    for (language in listOf("es", "fr", "de")) {
      val text = CatalogText(language)
      val cafeDeck = catalog.decks.first { it.deckId == "official_fun_food_cafe" }
      val name = text.name(cafeDeck)
      val accented = listOf(name).plus(text.tags(cafeDeck)).firstOrNull { SearchFolding.fold(it) != it.lowercase() }
      if (accented != null) {
        val plain = SearchFolding.fold(accented)
        val results = DiscoverCatalogRules.filteredDecks(catalog, DiscoverFilters(query = plain), text)
        assertTrue(results.any { it.deckId == cafeDeck.deckId }, "$language: $plain")
      }
    }
    val spanish = CatalogText("es")
    val results = DiscoverCatalogRules.filteredDecks(catalog, DiscoverFilters(query = "conversacion"), spanish)
    assertTrue(results.isNotEmpty() && results.all { "会話" in it.tags })
  }

  @Test
  fun foldingPreservesJapaneseVoicingAndHangul() {
    assertEquals("strasse", SearchFolding.fold("Straße"))
    assertEquals("coeur", SearchFolding.fold("cœur"))
    assertEquals("ae", SearchFolding.fold("Æ"))
    assertEquals("cafe", SearchFolding.fold("Café"))
    assertEquals("が", SearchFolding.fold("が"))
    assertFalse(SearchFolding.fold("が").contains("か"))
    assertEquals("한국", SearchFolding.fold("한국"))
    assertEquals("한", SearchFolding.fold("한"))
  }

  @Test
  fun unavailableLanguageHidesDecks() {
    val withoutLocalization = catalog.copy(decks = catalog.decks.map { it.copy(localizations = null) })
    val text = CatalogText("fr")
    assertTrue(DiscoverCatalogRules.availableDecks(withoutLocalization, text).isEmpty())
    assertTrue(DiscoverCatalogRules.filteredDecks(withoutLocalization, DiscoverFilters(), text).isEmpty())
    assertEquals(26, DiscoverCatalogRules.availableDecks(withoutLocalization, CatalogText("ja")).size)
    assertEquals(26, DiscoverCatalogRules.availableDecks(catalog, CatalogText("fr-CA")).size)
  }

  @Test
  fun relatedDecksShareTagsRankByOverlapAndCapAtSix() {
    val source = catalog.decks.first { it.deckId == "official_drama_daily" }
    val related = DiscoverCatalogRules.relatedDecks(source, catalog.decks, ja)
    assertEquals(6, related.size)
    assertTrue(related.none { it.deckId == source.deckId })
    val overlaps = related.map { it.tags.toSet().intersect(source.tags.toSet()).size }
    assertEquals(overlaps.sortedDescending(), overlaps)
    assertTrue(overlaps.all { it > 0 })
  }

  @Test
  fun averagePreviewLengthCountsGraphemes() {
    val deck = catalog.decks.first()
    val average = DiscoverCatalogRules.averagePreviewLength(deck)!!
    assertEquals(deck.previewItems.sumOf { it.ko.length }.toDouble() / deck.previewItems.size, average, 0.0001)
    assertEquals(null, DiscoverCatalogRules.averagePreviewLength(deck.copy(previewItems = emptyList())))
  }
}
