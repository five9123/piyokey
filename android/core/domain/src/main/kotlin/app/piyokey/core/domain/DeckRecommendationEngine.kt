package app.piyokey.core.domain

import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.DeckType
import kotlin.math.abs

/**
 * iOS `DeckRecommendationEngine`. Home shows two areas: [homeRecommendations] (personalized 3) and
 * [nextStepRecommendations] (3 more, excluding the personalized ids, never duplicated). See
 * [homeAreas] for the combined rule.
 */
object DeckRecommendationEngine {
  val officialLearningPath: List<String> = listOf(
    "official_keyboard_start",
    "official_consonants",
    "official_vowels",
    "official_syllable_building",
    "official_batchim",
    "official_daily_words",
    "official_verbs_adjectives",
    "official_daily_phrases",
  )

  /** Onboarding/first-home "start here": official decks ranked by learner level, then goal tags. */
  fun starterRecommendations(
    catalog: Catalog?,
    preferredTags: List<String>,
    level: OnboardingLevel?,
    limit: Int = 3,
  ): List<CatalogDeck> {
    if (catalog == null || limit <= 0) return emptyList()
    val learner = level ?: OnboardingLevel.BEGINNER
    val preferred = preferredTags.toSet()
    return catalog.decks.filter { it.official }.sortedWith { lhs, rhs ->
      val leftRank = learner.recommendationRank(lhs.level, lhs.tags, lhs.type == DeckType.SENTENCE)
      val rightRank = learner.recommendationRank(rhs.level, rhs.tags, rhs.type == DeckType.SENTENCE)
      when {
        leftRank != rightRank -> leftRank.compareTo(rightRank)
        matches(preferred, lhs) != matches(preferred, rhs) -> matches(preferred, rhs).compareTo(matches(preferred, lhs))
        lhs.featured != rhs.featured -> if (lhs.featured) -1 else 1
        lhs.downloadsTotal != rhs.downloadsTotal -> rhs.downloadsTotal.compareTo(lhs.downloadsTotal)
        else -> lhs.deckId.compareTo(rhs.deckId)
      }
    }.take(limit)
  }

  /** Personalized: tag affinity from download history (×2) and goal tags (×1), then featured/popular. */
  fun homeRecommendations(
    catalog: Catalog?,
    downloadHistory: Map<String, List<String>>,
    installedDeckIds: Set<String>,
    preferredTags: List<String> = emptyList(),
    limit: Int = 3,
  ): List<CatalogDeck> {
    if (catalog == null || limit <= 0) return emptyList()
    val weights = HashMap<String, Int>()
    for (tags in downloadHistory.values) for (tag in tags.toSet()) weights[tag] = (weights[tag] ?: 0) + 2
    for (tag in preferredTags.toSet()) weights[tag] = (weights[tag] ?: 0) + 1
    return catalog.decks.filter { it.deckId !in installedDeckIds }.sortedWith { lhs, rhs ->
      val left = affinity(lhs, weights)
      val right = affinity(rhs, weights)
      when {
        left != right -> right.compareTo(left)
        lhs.featured != rhs.featured -> if (lhs.featured) -1 else 1
        lhs.downloadsTotal != rhs.downloadsTotal -> rhs.downloadsTotal.compareTo(lhs.downloadsTotal)
        else -> lhs.name.compareTo(rhs.name)
      }
    }.take(limit)
  }

  /**
   * Next step: after ≥ 80% on an official path deck, advance along [officialLearningPath]; with no
   * recent deck, beginners follow the path; otherwise level distance (90%+ steps up one level),
   * shared tags and same type decide.
   */
  fun nextStepRecommendations(
    catalog: Catalog?,
    installedDeckIds: Set<String>,
    excludingDeckIds: Set<String> = emptySet(),
    preferredLevel: OnboardingLevel?,
    recentDeckId: String? = null,
    recentAccuracy: Double? = null,
    limit: Int = 3,
  ): List<CatalogDeck> {
    if (catalog == null || limit <= 0) return emptyList()
    val excluded = installedDeckIds + excludingDeckIds
    val candidates = catalog.decks.filter { it.official && it.deckId !in excluded }
    if (candidates.isEmpty()) return emptyList()

    val source = recentDeckId?.let { id -> catalog.decks.firstOrNull { it.deckId == id } }
    val accuracy = recentAccuracy ?: 0.0
    val sourcePathIndex = source?.let { officialLearningPath.indexOf(it.deckId) }?.takeIf { it >= 0 }
    val advanceFrom = sourcePathIndex?.takeIf { accuracy >= 80 }
    val usesStarterPath = source == null &&
      (preferredLevel == null || preferredLevel == OnboardingLevel.BEGINNER || preferredLevel == OnboardingLevel.JAMO)

    return candidates.sortedWith { lhs, rhs ->
      if (advanceFrom != null) {
        val left = pathProgressionRank(lhs.deckId, advanceFrom)
        val right = pathProgressionRank(rhs.deckId, advanceFrom)
        if (left != right) return@sortedWith left.compareTo(right)
      } else if (usesStarterPath) {
        val left = starterPathRank(lhs.deckId)
        val right = starterPathRank(rhs.deckId)
        if (left != right) return@sortedWith left.compareTo(right)
      }
      val leftRank = nextStepRank(lhs, source, accuracy, preferredLevel)
      val rightRank = nextStepRank(rhs, source, accuracy, preferredLevel)
      val ranked = compareLexicographically(leftRank, rightRank)
      when {
        ranked != 0 -> ranked
        lhs.featured != rhs.featured -> if (lhs.featured) -1 else 1
        lhs.downloadsTotal != rhs.downloadsTotal -> rhs.downloadsTotal.compareTo(lhs.downloadsTotal)
        else -> lhs.deckId.compareTo(rhs.deckId)
      }
    }.take(limit)
  }

  /** The two Home areas: personalized and next step (disjoint; next step empty without personal). */
  data class HomeAreas(val personal: List<CatalogDeck>, val nextStep: List<CatalogDeck>)

  fun homeAreas(
    catalog: Catalog?,
    downloadHistory: Map<String, List<String>>,
    installedDeckIds: Set<String>,
    preferredTags: List<String>,
    preferredLevel: OnboardingLevel?,
    recentDeckId: String?,
    recentAccuracy: Double?,
  ): HomeAreas {
    val personal = homeRecommendations(catalog, downloadHistory, installedDeckIds, preferredTags)
    if (personal.isEmpty() || catalog == null) return HomeAreas(personal, emptyList())
    val next = nextStepRecommendations(
      catalog = catalog,
      installedDeckIds = installedDeckIds,
      excludingDeckIds = personal.mapTo(mutableSetOf()) { it.deckId },
      preferredLevel = preferredLevel,
      recentDeckId = recentDeckId,
      recentAccuracy = recentAccuracy,
    )
    return HomeAreas(personal, next)
  }

  /** Most recent practice evidence feeding [nextStepRecommendations]. */
  data class LearningSignal(val deckId: String, val accuracy: Double, val date: java.time.Instant)

  /**
   * Latest game/lesson on a catalog deck vs. the latest cleared curriculum stage (mapped to its
   * learning-path deck); the newer one wins, ties go to the deck record (iOS Home).
   */
  fun mostRecentLearningSignal(
    catalog: Catalog,
    records: List<GameRecord>,
    stageProgress: Collection<CurriculumStageProgress>,
  ): LearningSignal? {
    val catalogIds = catalog.decks.mapTo(HashSet()) { it.deckId }
    val deckSignal = records.filter { it.deckId in catalogIds }.maxByOrNull { it.playedAt }
      ?.let { LearningSignal(it.deckId, it.accuracy, it.playedAt) }
    val curriculumSignal = stageProgress.maxByOrNull { it.completedAt }?.let { progress ->
      val stage = CurriculumCatalog.stage(progress.stageId) ?: return@let null
      val deckId = learningPathDeckId(stage.chapterNumber) ?: return@let null
      LearningSignal(deckId, progress.bestAccuracy, progress.completedAt)
    }
    return when {
      deckSignal != null && curriculumSignal != null -> if (deckSignal.date >= curriculumSignal.date) deckSignal else curriculumSignal
      else -> deckSignal ?: curriculumSignal
    }
  }

  fun learningPathDeckId(curriculumChapter: Int): String? = when (curriculumChapter) {
    1 -> "official_consonants"
    2 -> "official_vowels"
    3 -> "official_syllable_building"
    4 -> "official_batchim"
    5 -> "official_daily_words"
    6 -> "official_daily_phrases"
    else -> null
  }

  fun onboardingRecommendations(
    catalog: Catalog?,
    preferredTags: List<String>,
    installedDeckIds: Set<String>,
    limit: Int = 3,
  ): List<CatalogDeck> {
    if (catalog == null || limit <= 0) return emptyList()
    val candidates = catalog.decks.filter { it.deckId !in installedDeckIds }
    val official = candidates.filter { it.official }
    val pool = if (official.size >= limit) official else candidates
    val preferred = preferredTags.toSet()
    return pool.sortedWith { lhs, rhs ->
      val left = matches(preferred, lhs)
      val right = matches(preferred, rhs)
      when {
        left != right -> right.compareTo(left)
        lhs.featured != rhs.featured -> if (lhs.featured) -1 else 1
        lhs.downloadsTotal != rhs.downloadsTotal -> rhs.downloadsTotal.compareTo(lhs.downloadsTotal)
        else -> lhs.name.compareTo(rhs.name)
      }
    }.take(limit)
  }

  /** Result screen: decks sharing a tag with the played deck, uninstalled first. */
  fun relatedRecommendations(
    sourceDeckId: String,
    sourceTags: List<String>,
    catalogDecks: List<CatalogDeck>,
    installedDeckIds: Set<String>,
    limit: Int = 2,
  ): List<CatalogDeck> {
    if (limit <= 0) return emptyList()
    val sourceSet = sourceTags.toSet()
    return catalogDecks.filter { it.deckId != sourceDeckId && it.tags.any { tag -> tag in sourceSet } }
      .sortedWith { lhs, rhs ->
        val lhsInstalled = lhs.deckId in installedDeckIds
        val rhsInstalled = rhs.deckId in installedDeckIds
        val left = matches(sourceSet, lhs)
        val right = matches(sourceSet, rhs)
        when {
          lhsInstalled != rhsInstalled -> if (!lhsInstalled) -1 else 1
          left != right -> right.compareTo(left)
          lhs.downloadsTotal != rhs.downloadsTotal -> rhs.downloadsTotal.compareTo(lhs.downloadsTotal)
          else -> lhs.name.compareTo(rhs.name)
        }
      }.take(limit)
  }

  private fun matches(preferred: Set<String>, deck: CatalogDeck): Int = deck.tags.toSet().count { it in preferred }

  private fun affinity(deck: CatalogDeck, weights: Map<String, Int>): Int = deck.tags.toSet().sumOf { weights[it] ?: 0 }

  private fun pathProgressionRank(deckId: String, sourceIndex: Int): Int {
    val index = officialLearningPath.indexOf(deckId)
    return if (index < 0 || index <= sourceIndex) officialLearningPath.size + 1 else index - sourceIndex - 1
  }

  private fun starterPathRank(deckId: String): Int =
    officialLearningPath.indexOf(deckId).takeIf { it >= 0 } ?: (officialLearningPath.size + 1)

  private fun nextStepRank(
    deck: CatalogDeck,
    source: CatalogDeck?,
    accuracy: Double,
    preferredLevel: OnboardingLevel?,
  ): List<Int> {
    if (source != null) {
      val targetLevel = if (accuracy >= 90) minOf(3, source.level + 1) else source.level
      val meaningful = source.tags.toSet() - "公式"
      return listOf(
        abs(deck.level - targetLevel),
        -deck.tags.toSet().count { it in meaningful },
        if (deck.type == source.type) 0 else 1,
      )
    }
    val learner = preferredLevel ?: OnboardingLevel.BEGINNER
    return listOf(
      learner.recommendationRank(deck.level, deck.tags, deck.type == DeckType.SENTENCE),
      deck.level,
      if (deck.type == DeckType.WORD) 0 else 1,
    )
  }
}
