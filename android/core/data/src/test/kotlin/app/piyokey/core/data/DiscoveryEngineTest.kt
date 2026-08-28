package app.piyokey.core.data

import app.piyokey.core.deckkit.DeckKitJson
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.settings.OnboardingGoal
import app.piyokey.core.settings.OnboardingLevel
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
  fun starterRecommendationsRespectLevelBeforeInterestsAndUseSafeFallback() {
    for (goal in OnboardingGoal.entries) {
      for (level in OnboardingLevel.entries) {
        val result = DiscoveryEngine.starterRecommendations(catalog, goal.preferredTags, level)
        assertEquals(3, result.size)
        assertTrue(result.all { it.official })
        val minimumRank = catalog.decks.filter { it.official }.minOf {
          level.recommendationRank(it.level, it.tags, it.type == DeckType.SENTENCE)
        }
        val first = result.first()
        assertEquals(minimumRank, level.recommendationRank(first.level, first.tags, first.type == DeckType.SENTENCE))
      }
    }
    val travel = DiscoveryEngine.starterRecommendations(catalog, OnboardingGoal.TRAVEL.preferredTags, OnboardingLevel.SENTENCES)
    assertTrue(travel.all { it.type == DeckType.SENTENCE && it.tags.any { tag -> tag in OnboardingGoal.TRAVEL.preferredTags } })
    assertEquals("official_topik_one", DiscoveryEngine.starterRecommendations(catalog, OnboardingGoal.TOPIK.preferredTags, OnboardingLevel.WORDS).first().deckId)
    assertTrue(DiscoveryEngine.starterRecommendations(catalog, emptySet(), null).all { it.level == 1 && "入門" in it.tags })
    assertTrue(DiscoveryEngine.starterRecommendations(catalog, emptySet(), null, limit = 0).isEmpty())
  }

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
