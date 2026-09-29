package app.piyokey.core.domain

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.Iso8601InstantSerializer
import app.piyokey.core.hangul.JamoDecomposer
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One item in the automatic review deck (iOS `ReviewDeckItem`, `Review/review-deck.json`). */
@Serializable
data class ReviewDeckItem(
  @SerialName("item_id") val itemId: String,
  @SerialName("source_deck_id") val sourceDeckId: String,
  val ko: String,
  @SerialName("reading_ja") val readingJa: String,
  @SerialName("meaning_ja") val meaningJa: String,
  val localizations: Map<String, DeckItemLocalization>? = null,
  @SerialName("miss_count") val missCount: Int,
  @SerialName("consecutive_perfect") val consecutivePerfect: Int,
  @SerialName("added_at") @Serializable(with = Iso8601InstantSerializer::class) val addedAt: Instant,
  @SerialName("graduated_at") @Serializable(with = Iso8601InstantSerializer::class)
  val graduatedAt: Instant? = null,
  @SerialName("is_source_available") val isSourceAvailable: Boolean? = null,
) {
  val id: String get() = id(itemId, sourceDeckId)
  val isActive: Boolean get() = graduatedAt == null && isSourceAvailable != false
  val deckItem: DeckItem
    get() = DeckItem(id = itemId, ko = ko, readingJa = readingJa, meaningJa = meaningJa, audio = null, localizations = localizations)

  companion object {
    fun id(itemId: String, sourceDeckId: String) = "$sourceDeckId::$itemId"

    fun of(item: DeckItem, sourceDeckId: String, addedAt: Instant, missCount: Int) = ReviewDeckItem(
      itemId = item.id,
      sourceDeckId = sourceDeckId,
      ko = item.ko,
      readingJa = item.readingJa,
      meaningJa = item.meaningJa,
      localizations = item.localizations,
      missCount = missCount,
      consecutivePerfect = 0,
      addedAt = addedAt,
      graduatedAt = null,
      isSourceAvailable = true,
    )
  }
}

data class SessionItemResolution(
  val itemIndex: Int,
  val hadMistake: Boolean,
  val mistakeCount: Int,
  val mistakenJamoIndices: Set<Int>,
)

data class SessionReviewItem(
  val item: DeckItem,
  val sourceDeckId: String,
  val mistakeCount: Int,
  val mistakenJamoIndices: Set<Int>,
) {
  val id: String get() = ReviewDeckItem.id(item.id, sourceDeckId)

  fun merge(resolution: SessionItemResolution) = copy(
    mistakeCount = mistakeCount + resolution.mistakeCount,
    mistakenJamoIndices = mistakenJamoIndices + resolution.mistakenJamoIndices,
  )

  companion object {
    fun of(item: DeckItem, sourceDeckId: String, resolution: SessionItemResolution) =
      SessionReviewItem(item, sourceDeckId, resolution.mistakeCount, resolution.mistakenJamoIndices)
  }
}

data class ReviewDeckSnapshot(val items: Map<String, ReviewDeckItem>) {
  /** Active items, newest first (ties by id). */
  val activeItems: List<ReviewDeckItem>
    get() = items.values.filter { it.isActive }
      .sortedWith(compareByDescending<ReviewDeckItem> { it.addedAt }.thenBy { it.id })

  companion object {
    val EMPTY = ReviewDeckSnapshot(emptyMap())
  }
}

enum class ReviewDeckMutation { ADDED, UPDATED, GRADUATED, REMOVED, UNCHANGED }

sealed class ReviewDeckStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) : ReviewDeckStoreException("Unsupported review schema $version")
  data object InvalidItem : ReviewDeckStoreException("Invalid review item")
}

data class ReviewDeckChange(val snapshot: ReviewDeckSnapshot, val mutation: ReviewDeckMutation)

/**
 * Review deck rules (iOS `ReviewDeckStore.apply*`): a mistake adds/re-activates an item, three
 * consecutive perfect completions graduate it, manual add/remove, deck reconciliation.
 */
object ReviewDeckRules {
  const val CURRENT_SCHEMA_VERSION = 1
  const val GRADUATION_THRESHOLD = 3

  fun isValid(item: ReviewDeckItem): Boolean =
    item.itemId.isNotEmpty() && item.sourceDeckId.isNotEmpty() &&
      item.ko.graphemeCount() <= 10 && JamoDecomposer.containsHangul(item.ko) &&
      runCatching { JamoDecomposer.keySequence(item.ko) }.isSuccess &&
      item.missCount >= 0 && item.consecutivePerfect in 0..GRADUATION_THRESHOLD &&
      (item.graduatedAt == null || item.consecutivePerfect == GRADUATION_THRESHOLD)

  /** Throws [ReviewDeckStoreException.InvalidItem] for semantic violations or duplicate ids. */
  fun validated(items: List<ReviewDeckItem>): ReviewDeckSnapshot {
    val result = LinkedHashMap<String, ReviewDeckItem>()
    for (item in items) {
      if (!isValid(item) || result.containsKey(item.id)) throw ReviewDeckStoreException.InvalidItem
      result[item.id] = item
    }
    return ReviewDeckSnapshot(result)
  }

  fun applyMistake(item: DeckItem, sourceDeckId: String, at: Instant, snapshot: ReviewDeckSnapshot): ReviewDeckChange {
    val id = ReviewDeckItem.id(item.id, sourceDeckId)
    val existing = snapshot.items[id]
    if (existing != null) {
      val localizations = item.localizations?.let { (existing.localizations ?: emptyMap()) + it } ?: existing.localizations
      val updated = existing.copy(
        ko = item.ko,
        readingJa = item.readingJa,
        meaningJa = item.meaningJa,
        localizations = localizations,
        missCount = existing.missCount + 1,
        consecutivePerfect = 0,
        graduatedAt = null,
        isSourceAvailable = true,
      )
      return ReviewDeckChange(snapshot.with(id, updated), ReviewDeckMutation.UPDATED)
    }
    val added = ReviewDeckItem.of(item, sourceDeckId, at, missCount = 1)
    return ReviewDeckChange(snapshot.with(id, added), ReviewDeckMutation.ADDED)
  }

  fun applyPerfect(itemId: String, sourceDeckId: String, at: Instant, snapshot: ReviewDeckSnapshot): ReviewDeckChange {
    val id = ReviewDeckItem.id(itemId, sourceDeckId)
    val existing = snapshot.items[id]
    if (existing == null || !existing.isActive) return ReviewDeckChange(snapshot, ReviewDeckMutation.UNCHANGED)
    val perfect = existing.consecutivePerfect + 1
    val updated = if (perfect >= GRADUATION_THRESHOLD) {
      existing.copy(consecutivePerfect = GRADUATION_THRESHOLD, graduatedAt = at)
    } else {
      existing.copy(consecutivePerfect = perfect)
    }
    val mutation = if (perfect >= GRADUATION_THRESHOLD) ReviewDeckMutation.GRADUATED else ReviewDeckMutation.UPDATED
    return ReviewDeckChange(snapshot.with(id, updated), mutation)
  }

  fun applyManualAdd(item: DeckItem, sourceDeckId: String, at: Instant, snapshot: ReviewDeckSnapshot): ReviewDeckChange {
    val id = ReviewDeckItem.id(item.id, sourceDeckId)
    val existing = snapshot.items[id]
    if (existing != null) {
      if (existing.isActive) return ReviewDeckChange(snapshot, ReviewDeckMutation.UNCHANGED)
      val updated = existing.copy(consecutivePerfect = 0, graduatedAt = null, isSourceAvailable = true)
      return ReviewDeckChange(snapshot.with(id, updated), ReviewDeckMutation.UPDATED)
    }
    return ReviewDeckChange(
      snapshot.with(id, ReviewDeckItem.of(item, sourceDeckId, at, missCount = 0)),
      ReviewDeckMutation.ADDED,
    )
  }

  fun applyManualRemove(itemId: String, sourceDeckId: String, snapshot: ReviewDeckSnapshot): ReviewDeckChange {
    val id = ReviewDeckItem.id(itemId, sourceDeckId)
    if (!snapshot.items.containsKey(id)) return ReviewDeckChange(snapshot, ReviewDeckMutation.UNCHANGED)
    return ReviewDeckChange(ReviewDeckSnapshot(snapshot.items - id), ReviewDeckMutation.REMOVED)
  }

  /** Refreshes stored copies from the latest [deck]; items missing from it become unavailable. */
  fun applyDeckContent(deck: Deck, snapshot: ReviewDeckSnapshot): ReviewDeckChange {
    val itemsById = deck.items.associateBy { it.id }
    val result = LinkedHashMap(snapshot.items)
    var changed = false
    for ((id, stored) in snapshot.items) {
      if (stored.sourceDeckId != deck.deckId) continue
      val latest = itemsById[stored.itemId]
      val updated = if (latest != null) {
        stored.copy(
          ko = latest.ko,
          readingJa = latest.readingJa,
          meaningJa = latest.meaningJa,
          localizations = latest.localizations,
          isSourceAvailable = true,
        )
      } else {
        stored.copy(isSourceAvailable = false)
      }
      if (updated == stored) continue
      result[id] = updated
      changed = true
    }
    return ReviewDeckChange(
      if (changed) ReviewDeckSnapshot(result) else snapshot,
      if (changed) ReviewDeckMutation.UPDATED else ReviewDeckMutation.UNCHANGED,
    )
  }

  /** Keeps history but removes every item of [deckId] from the active queue. */
  fun applySourceUnavailable(deckId: String, snapshot: ReviewDeckSnapshot): ReviewDeckChange {
    val result = LinkedHashMap(snapshot.items)
    var changed = false
    for ((id, stored) in snapshot.items) {
      if (stored.sourceDeckId == deckId && stored.isSourceAvailable != false) {
        result[id] = stored.copy(isSourceAvailable = false)
        changed = true
      }
    }
    return ReviewDeckChange(
      if (changed) ReviewDeckSnapshot(result) else snapshot,
      if (changed) ReviewDeckMutation.UPDATED else ReviewDeckMutation.UNCHANGED,
    )
  }

  private fun ReviewDeckSnapshot.with(id: String, item: ReviewDeckItem) = ReviewDeckSnapshot(items + (id to item))
}
