package app.piyokey.android.data.retention

import app.piyokey.android.data.decks.BundledContent
import app.piyokey.android.data.persistence.KeyValueStore
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.domain.DailyChallenge
import app.piyokey.core.domain.DailyChallengeCatalog
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.OnboardingGoal
import app.piyokey.core.domain.RandomWordPracticeCatalog
import app.piyokey.core.domain.RandomWordPracticeHistoryRules
import app.piyokey.core.domain.RandomWordPracticeSession
import kotlin.random.Random
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * `retention.random_word_practice.recent_words` (latest 20 NFC words, iOS `RandomWordPracticeHistory`).
 * Stored as a JSON string array (same format as `data/settings` `StringListPref`).
 */
class RandomWordPracticeHistory(private val defaults: KeyValueStore) {
  val recentWordKeys: List<String>
    get() = defaults.getString(RandomWordPracticeHistoryRules.STORAGE_KEY)
      ?.let { runCatching { Json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }
      ?: emptyList()

  fun record(session: RandomWordPracticeSession) {
    val updated = RandomWordPracticeHistoryRules.recorded(recentWordKeys, session)
    defaults.putString(RandomWordPracticeHistoryRules.STORAGE_KEY, Json.encodeToString(ListSerializer(String.serializer()), updated))
  }

  fun reset() = defaults.remove(RandomWordPracticeHistoryRules.STORAGE_KEY)
}

/** Home quick actions: daily challenge and random word practice (iOS `RetentionViews` catalogs). */
class QuickPracticeContent(
  private val bundled: BundledContent,
  private val history: RandomWordPracticeHistory,
) {
  fun dailyChallenge(today: JstDay, goal: OnboardingGoal?): DailyChallenge = DailyChallengeCatalog.challenge(today, goal)

  /**
   * Next random 5-word session from installed official word decks (goal tags first), bundled
   * fallbacks otherwise; records it in the recent-words history. `null` when fewer than 5 words.
   */
  fun nextRandomWordSession(
    installedDecks: List<Deck>,
    goal: OnboardingGoal?,
    catalog: Catalog?,
    random: Random = Random.Default,
  ): RandomWordPracticeSession? {
    val session = RandomWordPracticeCatalog.makeSession(
      installedDecks = installedDecks,
      fallbackDecks = bundled.randomWordFallbackDecks(goal, catalog),
      preferredTags = goal?.preferredTags ?: emptyList(),
      recentWordKeys = history.recentWordKeys,
      random = random,
    ) ?: return null
    history.record(session)
    return session
  }
}
