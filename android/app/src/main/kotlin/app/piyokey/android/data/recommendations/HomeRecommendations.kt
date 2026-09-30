package app.piyokey.android.data.recommendations

import app.piyokey.android.data.catalog.CatalogLibrary
import app.piyokey.android.data.decks.DeckLibrary
import app.piyokey.android.data.onboarding.OnboardingLibrary
import app.piyokey.android.data.progress.CurriculumProgressLibrary
import app.piyokey.android.data.progress.GameProgressLibrary
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.domain.DeckRecommendationEngine

/**
 * Recommendation queries over the live libraries (pure rules: `DeckRecommendationEngine`).
 * Call from Compose after collecting the relevant flows so results recompose.
 */
class HomeRecommendations(
  private val catalog: CatalogLibrary,
  private val decks: DeckLibrary,
  private val onboarding: OnboardingLibrary,
  private val games: GameProgressLibrary,
  private val curriculum: CurriculumProgressLibrary,
) {
  /** Home: personalized 3 + next-step 3 (disjoint); both empty without a catalog. */
  fun homeAreas(catalog: Catalog? = this.catalog.catalog.value): DeckRecommendationEngine.HomeAreas {
    val signal = catalog?.let {
      DeckRecommendationEngine.mostRecentLearningSignal(it, games.records.value, curriculum.stageProgress.value.values)
    }
    return DeckRecommendationEngine.homeAreas(
      catalog = catalog,
      downloadHistory = decks.downloadHistory.value,
      installedDeckIds = decks.installedDecks.value.keys,
      preferredTags = onboarding.preferredTags,
      preferredLevel = onboarding.selectedLevel,
      recentDeckId = signal?.deckId,
      recentAccuracy = signal?.accuracy,
    )
  }

  /** Whether the next-step subtitle is the cold-start copy (no learning signal yet). */
  fun hasLearningSignal(catalog: Catalog? = this.catalog.catalog.value): Boolean = catalog != null &&
    DeckRecommendationEngine.mostRecentLearningSignal(catalog, games.records.value, curriculum.stageProgress.value.values) != null

  /** First-home / onboarding starter deck(s) by level and goal. */
  fun starter(limit: Int = 1, catalog: Catalog? = this.catalog.catalog.value): List<CatalogDeck> =
    DeckRecommendationEngine.starterRecommendations(catalog, onboarding.preferredTags, onboarding.selectedLevel, limit)

  /** Onboarding goal step: 3 uninstalled (official first) decks by goal tags. */
  fun onboardingPicks(catalog: Catalog? = this.catalog.catalog.value): List<CatalogDeck> =
    DeckRecommendationEngine.onboardingRecommendations(catalog, onboarding.preferredTags, decks.installedDecks.value.keys)

  /** Result screen: up to 2 related decks sharing tags with the played deck. */
  fun related(sourceDeckId: String, sourceTags: List<String>, catalog: Catalog? = this.catalog.catalog.value): List<CatalogDeck> =
    DeckRecommendationEngine.relatedRecommendations(
      sourceDeckId, sourceTags, catalog?.decks ?: emptyList(), decks.installedDecks.value.keys,
    )
}
