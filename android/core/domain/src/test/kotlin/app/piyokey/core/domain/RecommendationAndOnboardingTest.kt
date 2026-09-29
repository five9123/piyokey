package app.piyokey.core.domain

import app.piyokey.core.deckkit.DeckType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class RecommendationAndOnboardingTest {
  private val catalog = TestSupport.bundledCatalog

  @Test
  fun starterRecommendationsRespectEachLevelBeforeInterests() {
    for (goal in OnboardingGoal.entries) {
      for (level in OnboardingLevel.entries) {
        val recommendations = DeckRecommendationEngine.starterRecommendations(catalog, goal.preferredTags, level)
        assertEquals(3, recommendations.size)
        assertTrue(recommendations.all { it.official })
        val minimum = catalog.decks.filter { it.official }
          .minOf { level.recommendationRank(it.level, it.tags, it.type == DeckType.SENTENCE) }
        val first = recommendations.first()
        assertEquals(minimum, level.recommendationRank(first.level, first.tags, first.type == DeckType.SENTENCE))
      }
    }
    val travel = DeckRecommendationEngine.starterRecommendations(catalog, OnboardingGoal.TRAVEL.preferredTags, OnboardingLevel.SENTENCES)
    assertTrue(travel.all { deck -> deck.type == DeckType.SENTENCE && deck.tags.any { it in OnboardingGoal.TRAVEL.preferredTags } })
    val topik = DeckRecommendationEngine.starterRecommendations(catalog, OnboardingGoal.TOPIK.preferredTags, OnboardingLevel.WORDS)
    assertEquals("official_topik_one", topik.first().deckId)
    val beginner = DeckRecommendationEngine.starterRecommendations(catalog, emptyList(), null)
    assertTrue(beginner.all { it.level == 1 && "入門" in it.tags })
    assertTrue(DeckRecommendationEngine.starterRecommendations(null, emptyList(), null).isEmpty())
    assertTrue(DeckRecommendationEngine.starterRecommendations(catalog, emptyList(), null, limit = 0).isEmpty())
  }

  @Test
  fun homeRecommendationsUseDownloadTagAffinityThenPopularity() {
    val installed = "official_daily_words"
    val tags = listOf("日常", "公式", "単語")
    val recommendations = DeckRecommendationEngine.homeRecommendations(catalog, mapOf(installed to tags), setOf(installed))
    assertEquals(3, recommendations.size)
    assertFalse(recommendations.any { it.deckId == installed })
    val scores = recommendations.map { deck -> deck.tags.toSet().count { it in tags } }
    assertEquals(scores.sortedDescending(), scores)
  }

  @Test
  fun homeColdStartReturnsFeaturedPopularUninstalledDecks() {
    val mostPopularFeatured = catalog.decks.filter { it.featured }.maxBy { it.downloadsTotal }
    val recommendations = DeckRecommendationEngine.homeRecommendations(catalog, emptyMap(), setOf(mostPopularFeatured.deckId))
    assertEquals(3, recommendations.size)
    assertFalse(mostPopularFeatured in recommendations)
    assertTrue(recommendations.first().featured)
  }

  @Test
  fun nextStepColdStartUsesOfficialPathWithoutDuplicatingPersonalPicks() {
    val areas = DeckRecommendationEngine.homeAreas(
      catalog, emptyMap(), emptySet(), OnboardingGoal.TRAVEL.preferredTags, OnboardingLevel.BEGINNER, null, null,
    )
    val personal = areas.personal.map { it.deckId }
    assertEquals(3, areas.nextStep.size)
    assertTrue(areas.nextStep.all { it.official })
    assertTrue(areas.nextStep.none { it.deckId in personal })
    val expected = DeckRecommendationEngine.officialLearningPath.filter { it !in personal }.take(3)
    assertEquals(expected, areas.nextStep.map { it.deckId })
  }

  @Test
  fun nextStepAdvancesPathAfterEightyPercentAndReinforcesAfterLowAccuracy() {
    val advanced = DeckRecommendationEngine.nextStepRecommendations(
      catalog, setOf("official_daily_words"), preferredLevel = OnboardingLevel.WORDS,
      recentDeckId = "official_daily_words", recentAccuracy = 96.0,
    )
    assertEquals(3, advanced.size)
    assertEquals("official_verbs_adjectives", advanced.first().deckId)
    assertFalse(advanced.any { it.deckId == "official_daily_words" })

    val reinforced = DeckRecommendationEngine.nextStepRecommendations(
      catalog, setOf("official_daily_words"), preferredLevel = OnboardingLevel.WORDS,
      recentDeckId = "official_daily_words", recentAccuracy = 72.0,
    )
    assertEquals(3, reinforced.size)
    assertEquals(1, reinforced.first().level)
    assertTrue(DeckRecommendationEngine.nextStepRecommendations(catalog, emptySet(), preferredLevel = null, limit = 0).isEmpty())
  }

  @Test
  fun curriculumChaptersMapToLearningPathDecks() {
    assertEquals("official_consonants", DeckRecommendationEngine.learningPathDeckId(1))
    assertEquals("official_daily_words", DeckRecommendationEngine.learningPathDeckId(5))
    assertEquals("official_daily_phrases", DeckRecommendationEngine.learningPathDeckId(6))
    assertNull(DeckRecommendationEngine.learningPathDeckId(7))
  }

  @Test
  fun relatedRecommendationsPreferUninstalledSameTagDecks() {
    val source = catalog.decks.first { it.deckId == "official_daily_words" }
    val installedRelated = catalog.decks.first { deck -> deck.deckId != source.deckId && deck.tags.any { it in source.tags } }
    val recommendations = DeckRecommendationEngine.relatedRecommendations(
      source.deckId, source.tags, catalog.decks, setOf(source.deckId, installedRelated.deckId),
    )
    assertEquals(2, recommendations.size)
    assertTrue(recommendations.all { deck -> deck.deckId != source.deckId && deck.tags.any { it in source.tags } })
    if (installedRelated in recommendations) assertFalse(recommendations.first().deckId == installedRelated.deckId)
  }

  @Test
  fun onboardingAndGoalPersonalizedColdStart() {
    val onboarding = DeckRecommendationEngine.onboardingRecommendations(catalog, OnboardingGoal.TRAVEL.preferredTags, emptySet())
    assertEquals(3, onboarding.size)
    assertTrue(onboarding.all { it.official })
    assertTrue(onboarding[0].tags.any { it in OnboardingGoal.TRAVEL.preferredTags })
    val home = DeckRecommendationEngine.homeRecommendations(catalog, emptyMap(), emptySet(), OnboardingGoal.TOPIK.preferredTags)
    assertEquals(3, home.size)
    assertTrue(home[0].tags.any { it in OnboardingGoal.TOPIK.preferredTags })
  }

  @Test
  fun onboardingSnapshotDecodesLegacyStepsGoalsAndRejectsUnknown() {
    val json = Json { ignoreUnknownKeys = true }
    for (step in listOf(1, 2, 3)) {
      for (completed in listOf(false, true)) {
        val snapshot = json.decodeFromString(
          OnboardingSnapshot.serializer(),
          """{"schema_version":1,"is_completed":$completed,"was_skipped":false,"selected_goal":"travel","step":$step}""",
        )
        assertNull(snapshot.selectedLevel)
        assertEquals(step, snapshot.step.raw)
        assertEquals(completed, snapshot.isCompleted)
      }
    }
    assertEquals(listOf(1, 2, 3, 4), OnboardingStep.entries.map { it.position })
    assertEquals(OnboardingGoal.KEYBOARD, json.decodeFromString(OnboardingGoalSerializer, "\"casual\""))
    assertEquals(OnboardingGoal.TRENDS, json.decodeFromString(OnboardingGoalSerializer, "\"oshi\""))
    assertFailsWith<Exception> { json.decodeFromString(OnboardingGoalSerializer, "\"unknown\"") }
    val full = OnboardingSnapshot.EMPTY.copy(selectedGoal = OnboardingGoal.TRAVEL, selectedLevel = OnboardingLevel.WORDS, step = OnboardingStep.LEVEL)
    val encoded = json.encodeToString(OnboardingSnapshot.serializer(), full)
    assertTrue("\"step\":4" in encoded)
    assertEquals(full, json.decodeFromString(OnboardingSnapshot.serializer(), encoded))
  }
}
