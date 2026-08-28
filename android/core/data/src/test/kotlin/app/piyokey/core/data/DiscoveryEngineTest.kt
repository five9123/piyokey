package app.piyokey.core.data

import app.piyokey.core.deckkit.DeckKitJson
import app.piyokey.core.deckkit.DeckType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryEngineTest {
  private val root = File(requireNotNull(System.getProperty("piyokey.repositoryRoot")))
  private val catalog = DeckKitJson.decodeValidatedCatalog(
    File(root, "shared/mock_catalog/catalog.json").readText(),
    File(root, "shared/schema/catalog.schema.json").readText(),
  )

  @Test
  fun searchUsesLocalizedNameTagsAndAuthorWithEnglishFallback() {
    val english = DiscoveryEngine.filterAndSort(
      catalog,
      DeckFilters(query = "travel"),
      "fr",
    )
    assertTrue(english.any { it.deckId == "official_travel_phrases" })
    assertFalse(english.any { it.localizedName("fr").orEmpty().contains("韓国旅行") })

    val korean = DiscoveryEngine.filterAndSort(catalog, DeckFilters(query = "여행"), "ko")
    assertTrue(korean.any { it.deckId == "official_travel_phrases" })
  }

  @Test
  fun filtersIntersectTagsTypeLevelAndItemRange() {
    val results = DiscoveryEngine.filterAndSort(
      catalog,
      DeckFilters(
        type = DeckType.SENTENCE,
        level = 2,
        canonicalTags = setOf("恋愛", "会話"),
        minimumItems = 11,
        maximumItems = 12,
        sort = DeckSort.ITEM_COUNT,
      ),
      "ja",
    )
    assertTrue(results.isNotEmpty())
    assertTrue(results.all { it.type == DeckType.SENTENCE && it.level == 2 })
    assertTrue(results.all { it.tags.containsAll(setOf("恋愛", "会話")) })
    assertTrue(results.all { it.itemCount in 11..12 })
  }

  @Test
  fun homeRecommendationsPreserveDeletedDownloadHistory() {
    val recommendations = DiscoveryEngine.homeRecommendations(
      catalog = catalog,
      installedDeckIds = emptySet(),
      downloadHistoryTags = listOf(listOf("K-POP"), listOf("K-POP", "推し活")),
    )
    assertEquals(3, recommendations.size)
    assertTrue("K-POP" in recommendations.first().tags)
  }

  @Test
  fun sameTagRecommendationsPreferUninstalledThenMatchCount() {
    val source = catalog.decks.first { it.deckId == "official_fun_idol_live_comments" }
    val recommendations = DiscoveryEngine.sameTagRecommendations(
      catalog,
      source,
      installedDeckIds = setOf("official_kpop_spark"),
    )
    assertEquals(2, recommendations.size)
    assertFalse(recommendations.first().deckId == "official_kpop_spark")
    assertTrue(recommendations.all { candidate -> candidate.tags.any { it in source.tags } })
  }

  @Test
  fun staticUrlPolicyRejectsNonHttpsAndPathEscape() {
    assertFailsWith<IllegalArgumentException> {
      StaticContentUrlPolicy.parseCatalogUrl("http://example.com/catalog.json")
    }
    val catalogUrl = requireNotNull(
      StaticContentUrlPolicy.parseCatalogUrl("https://cdn.example.com/piyokey/catalog.json"),
    )
    assertEquals(
      "https://cdn.example.com/piyokey/decks/example.json",
      StaticContentUrlPolicy.resolveDeckUrl(catalogUrl, "decks/example.json").toString(),
    )
    assertFailsWith<IllegalArgumentException> {
      StaticContentUrlPolicy.resolveDeckUrl(catalogUrl, "decks/../secret.json")
    }
  }
}
