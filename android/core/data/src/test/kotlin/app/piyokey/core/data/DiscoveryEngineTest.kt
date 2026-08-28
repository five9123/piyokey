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
      "ar",
    )
    assertTrue(english.any { it.deckId == "official_travel_phrases" })
    assertFalse(english.any { it.localizedName("ar").orEmpty().contains("韓国旅行") })

    val korean = DiscoveryEngine.filterAndSort(catalog, DeckFilters(query = "여행"), "ko")
    assertTrue(korean.any { it.deckId == "official_travel_phrases" })
  }

  @Test
  fun supportedLanguageSearchFoldsLatinAccentsAndPreservesCanonicalTags() {
    for ((language, query) in listOf("fr" to "cafe", "es" to "cafe", "de" to "cafe")) {
      val results = DiscoveryEngine.filterAndSort(catalog, DeckFilters(query = query), language)
      assertTrue(results.any { it.deckId == "official_fun_food_cafe" }, language)
    }
    val french = DiscoveryEngine.filterAndSort(catalog, DeckFilters(query = "voyage"), "fr-CA")
    assertTrue(french.any { it.deckId == "official_travel_phrases" })
    assertEquals("韓国旅行", DiscoveryEngine.canonicalTag(catalog, "韓国旅行", "fr"))
    assertEquals("会話", DiscoveryEngine.canonicalTag(catalog, "conversacion", "es"))
    val voicedTag = catalog.copy(tags = listOf(catalog.tags.first().copy(tag = "が", localizations = null)))
    assertEquals(null, DiscoveryEngine.canonicalTag(voicedTag, "か", "ja"))
    assertEquals("が", DiscoveryEngine.canonicalTag(voicedTag, "か\u3099", "ja"))
    for ((tag, query) in listOf("Straße" to "strasse", "cœur" to "coeur", "æ" to "ae", "한" to "\u1112\u1161\u11ab")) {
      val taggedCatalog = catalog.copy(tags = listOf(catalog.tags.first().copy(tag = tag, localizations = null)))
      assertEquals(tag, DiscoveryEngine.canonicalTag(taggedCatalog, query, "de"))
    }
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
