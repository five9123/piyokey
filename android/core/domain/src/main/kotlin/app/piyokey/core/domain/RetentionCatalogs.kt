package app.piyokey.core.domain

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import java.util.UUID
import kotlin.random.Random

data class DailyChallenge(val day: JstDay, val items: List<CurriculumItem>) {
  val id: JstDay get() = day

  companion object {
    /** Review source id used for daily-challenge mistakes. */
    const val SOURCE_DECK_ID = "official_daily_challenge"
  }
}

/** iOS `DailyChallengeCatalog`: goal → curriculum pool, start = ordinal % pool, stride 3, 5 items. */
object DailyChallengeCatalog {
  const val ITEM_COUNT = 5

  fun stageId(goal: OnboardingGoal?): String = when (goal) {
    OnboardingGoal.KEYBOARD -> "chapter_3_syllable_building"
    OnboardingGoal.TRAVEL, OnboardingGoal.TRENDS -> "chapter_6_sentences"
    OnboardingGoal.TOPIK, null -> "chapter_5_words"
  }

  fun challenge(day: JstDay, goal: OnboardingGoal? = null): DailyChallenge {
    val pool = CurriculumCatalog.stage(stageId(goal))?.items
    check(pool != null && pool.size >= ITEM_COUNT) { "Daily challenge word pool is missing" }
    val start = Math.floorMod(day.ordinal, pool.size)
    return DailyChallenge(day, (0 until ITEM_COUNT).map { offset -> pool[(start + offset * 3) % pool.size] })
  }
}

data class RandomWordPracticeSource(
  val item: DeckItem,
  val sourceDeckId: String,
  val sourceTags: List<String>,
) {
  /** NFC form of the target; used for de-duplication and the recent-words history. */
  val wordKey: String get() = item.ko.nfc()
}

data class RandomWordPracticeSession(
  val sources: List<RandomWordPracticeSource>,
  val id: String = UUID.randomUUID().toString(),
) {
  val wordKeys: List<String> get() = sources.map { it.wordKey }
}

/** Pure rules for `retention.random_word_practice.recent_words` (latest 20 unique words). */
object RandomWordPracticeHistoryRules {
  const val STORAGE_KEY = "retention.random_word_practice.recent_words"
  const val MAXIMUM_COUNT = 20

  fun recorded(recentWordKeys: List<String>, session: RandomWordPracticeSession): List<String> {
    val updated = recentWordKeys.toMutableList()
    for (key in session.wordKeys) {
      updated.removeAll { it == key }
      updated += key
    }
    return updated.takeLast(MAXIMUM_COUNT)
  }
}

/**
 * iOS `RandomWordPracticeCatalog`: 5 unique single-word targets from installed official word decks
 * (goal-tagged decks first, then other installed, then bundled fallbacks), avoiding recent words
 * unless there are not enough others.
 */
object RandomWordPracticeCatalog {
  const val ITEM_COUNT = 5

  fun fallbackDeckId(goal: OnboardingGoal?): String = when (goal) {
    OnboardingGoal.KEYBOARD -> "official_keyboard_start"
    OnboardingGoal.TRAVEL -> "official_daily_words"
    OnboardingGoal.TOPIK, null -> "official_topik_one"
    OnboardingGoal.TRENDS -> "official_trending_korean"
  }

  fun makeSession(
    installedDecks: List<Deck>,
    fallbackDecks: List<Deck>,
    preferredTags: List<String>,
    recentWordKeys: List<String>,
    limit: Int = ITEM_COUNT,
    random: Random = Random.Default,
  ): RandomWordPracticeSession? {
    if (limit <= 0) return null
    val preferredTagSet = preferredTags.toSet()
    val eligibleInstalled = installedDecks.filter { it.official && it.type == DeckType.WORD }
    val preferredDecks = eligibleInstalled.filter { deck -> deck.tags.any { it in preferredTagSet } }
    val preferredIds = preferredDecks.mapTo(HashSet()) { it.deckId }
    val otherInstalled = eligibleInstalled.filter { it.deckId !in preferredIds }
    val installedIds = eligibleInstalled.mapTo(HashSet()) { it.deckId }
    val eligibleFallback = fallbackDecks.filter { it.official && it.type == DeckType.WORD && it.deckId !in installedIds }

    val tiers = listOf(preferredDecks, otherInstalled, eligibleFallback).map(::sources)
    val recent = recentWordKeys.toSet()
    val selected = ArrayList<RandomWordPracticeSource>()
    val selectedKeys = HashSet<String>()
    val deferredRecent = ArrayList<List<RandomWordPracticeSource>>()

    for (tier in tiers) {
      val shuffled = tier.shuffled(random)
      deferredRecent += shuffled.filter { it.wordKey in recent }
      appendUnique(shuffled.filter { it.wordKey !in recent }, selected, selectedKeys, limit)
      if (selected.size == limit) break
    }
    if (selected.size < limit) {
      for (tier in deferredRecent) {
        appendUnique(tier, selected, selectedKeys, limit)
        if (selected.size == limit) break
      }
    }
    return if (selected.size == limit) RandomWordPracticeSession(selected) else null
  }

  private fun sources(decks: List<Deck>): List<RandomWordPracticeSource> = decks.flatMap { deck ->
    deck.items.filter { isSingleWord(it.ko) }.map { RandomWordPracticeSource(it, deck.deckId, deck.tags) }
  }

  private fun isSingleWord(target: String): Boolean = target.isNotEmpty() && target.none { it.isWhitespace() }

  private fun appendUnique(
    candidates: List<RandomWordPracticeSource>,
    selected: MutableList<RandomWordPracticeSource>,
    selectedKeys: MutableSet<String>,
    limit: Int,
  ) {
    for (candidate in candidates) {
      if (selected.size >= limit) return
      if (selectedKeys.add(candidate.wordKey)) selected += candidate
    }
  }
}
