package app.piyokey.core.retention

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import java.text.Normalizer
import kotlin.random.Random

data class QuickPracticeSource(
  val item: DeckItem,
  val sourceDeckId: String,
  val sourceTags: List<String>,
) {
  val wordKey: String = Normalizer.normalize(item.ko, Normalizer.Form.NFC)
}

data class QuickPracticeSession(val sources: List<QuickPracticeSource>) {
  val wordKeys: List<String> get() = sources.map(QuickPracticeSource::wordKey)
}

object QuickPracticePolicy {
  const val itemCount = 5
  const val historyLimit = 20

  fun makeSession(
    installedDecks: List<Deck>,
    fallbackDecks: List<Deck>,
    preferredTags: Set<String>,
    recentWordKeys: List<String>,
    limit: Int = itemCount,
    random: Random = Random.Default,
  ): QuickPracticeSession? {
    if (limit <= 0) return null
    val eligibleInstalled = installedDecks.filter { it.official && it.type == DeckType.WORD }
    val preferred = eligibleInstalled.filter { deck -> deck.tags.any(preferredTags::contains) }
    val preferredIds = preferred.mapTo(mutableSetOf(), Deck::deckId)
    val otherInstalled = eligibleInstalled.filterNot { it.deckId in preferredIds }
    val installedIds = eligibleInstalled.mapTo(mutableSetOf(), Deck::deckId)
    val eligibleFallback = fallbackDecks.filter {
      it.official && it.type == DeckType.WORD && it.deckId !in installedIds
    }
    val recent = recentWordKeys.toSet()
    val selected = mutableListOf<QuickPracticeSource>()
    val selectedKeys = mutableSetOf<String>()
    val deferredRecent = mutableListOf<List<QuickPracticeSource>>()

    listOf(preferred, otherInstalled, eligibleFallback).forEach { decks ->
      val shuffled = decks.flatMap(::sources).shuffled(random)
      deferredRecent += shuffled.filter { it.wordKey in recent }
      appendUnique(shuffled.filterNot { it.wordKey in recent }, selected, selectedKeys, limit)
    }
    if (selected.size < limit) {
      deferredRecent.forEach { appendUnique(it, selected, selectedKeys, limit) }
    }
    return selected.takeIf { it.size == limit }?.let(::QuickPracticeSession)
  }

  fun updatedHistory(
    current: List<String>,
    session: QuickPracticeSession,
    limit: Int = historyLimit,
  ): List<String> {
    require(limit > 0)
    val updated = current.toMutableList()
    session.wordKeys.forEach { key ->
      updated.removeAll { it == key }
      updated += key
    }
    return updated.takeLast(limit)
  }

  private fun sources(deck: Deck): List<QuickPracticeSource> = deck.items.mapNotNull { item ->
    item.takeIf { it.ko.isNotEmpty() && it.ko.none(Char::isWhitespace) }?.let {
      QuickPracticeSource(it, deck.deckId, deck.tags)
    }
  }

  private fun appendUnique(
    candidates: List<QuickPracticeSource>,
    selected: MutableList<QuickPracticeSource>,
    selectedKeys: MutableSet<String>,
    limit: Int,
  ) {
    candidates.forEach { candidate ->
      if (selected.size < limit && selectedKeys.add(candidate.wordKey)) selected += candidate
    }
  }
}
