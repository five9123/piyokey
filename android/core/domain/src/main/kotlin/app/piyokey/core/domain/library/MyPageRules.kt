package app.piyokey.core.domain.library

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.domain.DailyLearningActivity
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.LearningInsights
import app.piyokey.core.domain.UserDeckDraft
import java.time.Instant

/** iOS `MyDeckSortOrder`. */
enum class MyDeckSortOrder(val raw: String) { RECENT("recent"), NAME("name"), INSTALLED("installed") }

/** Installation dates the My Decks sort needs (from `InstalledDeckRecord`). */
data class DeckInstallDates(val installedAt: Instant, val lastPlayedAt: Instant?)

object MyDecksRules {
  /**
   * iOS `MyPageView.sortedDecks`: recent = last played (else installed) first; installed = newest
   * install first; ties → name. Name uses [nameComparator] (iOS `localizedStandardCompare`).
   */
  fun sorted(
    decks: List<Deck>,
    order: MyDeckSortOrder,
    dates: (String) -> DeckInstallDates?,
    name: (Deck) -> String,
    nameComparator: Comparator<String> = naturalOrder(),
  ): List<Deck> = decks.sortedWith { lhs, rhs ->
    when (order) {
      MyDeckSortOrder.RECENT -> {
        val l = dates(lhs.deckId).let { it?.lastPlayedAt ?: it?.installedAt } ?: Instant.MIN
        val r = dates(rhs.deckId).let { it?.lastPlayedAt ?: it?.installedAt } ?: Instant.MIN
        if (l == r) name(lhs).compareTo(name(rhs)) else r.compareTo(l)
      }
      MyDeckSortOrder.NAME -> nameComparator.compare(name(lhs), name(rhs))
      MyDeckSortOrder.INSTALLED -> {
        val l = dates(lhs.deckId)?.installedAt ?: Instant.MIN
        val r = dates(rhs.deckId)?.installedAt ?: Instant.MIN
        if (l == r) name(lhs).compareTo(name(rhs)) else r.compareTo(l)
      }
    }
  }

  /** First three review targets joined with "・" (iOS `reviewPreview`). */
  fun reviewPreview(koreanTargets: List<String>): String = koreanTargets.take(3).joinToString("・")
}

/** iOS `DeckMakerDraftFlow`: which Deck Maker work a draft belongs to. */
sealed interface DeckMakerDraftFlow {
  data object New : DeckMakerDraftFlow
  data class Editing(val deckId: String) : DeckMakerDraftFlow
  data class OfficialCopy(val sourceDeckId: String) : DeckMakerDraftFlow

  fun matches(draft: UserDeckDraft): Boolean = this == of(draft)

  companion object {
    fun of(draft: UserDeckDraft): DeckMakerDraftFlow = when (val origin = draft.origin) {
      UserDeckDraft.Origin.New -> New
      UserDeckDraft.Origin.Editing -> Editing(draft.deckId)
      is UserDeckDraft.Origin.OfficialCopy -> OfficialCopy(origin.sourceDeckId)
    }
  }
}

/** iOS `UserDeckEditCommitError.sourceChanged`. */
class UserDeckSourceChangedException : Exception("The edited deck changed since the draft was opened")

object DeckMakerCommitRules {
  /** iOS `requireUnchangedUserDeckSource`: an edit commits only onto the version it was opened from. */
  fun requireUnchangedSource(draft: UserDeckDraft, installedVersion: Int?) {
    if (draft.origin != UserDeckDraft.Origin.Editing) return
    if (installedVersion != draft.baseVersion) throw UserDeckSourceChangedException()
  }

  /** `deck_maker_action.action` for a committed draft. */
  fun action(draft: UserDeckDraft): String = when (draft.origin) {
    UserDeckDraft.Origin.New -> "created"
    UserDeckDraft.Origin.Editing -> "edited"
    is UserDeckDraft.Origin.OfficialCopy -> "copied"
  }

  /** Initially expanded editor items: all when ≤ 8, else the first. */
  fun initiallyExpandedItemIds(draft: UserDeckDraft): Set<String> =
    if (draft.items.size <= 8) draft.items.map { it.id }.toSet() else draft.items.take(1).map { it.id }.toSet()

  /** Korean syllables excluding spaces (editor counter). */
  fun koreanSyllableCount(value: String): Int = value.filter { it != ' ' }.graphemeLength()
}

/** My Page insight chart/text helpers (iOS `MyPageView` private helpers). */
object InsightsFormatting {
  /** Bar height in dp for the activity chart (max 58, idle 5). */
  fun activityBarHeight(activity: DailyLearningActivity, insights: LearningInsights): Double {
    val maximum = insights.dailyActivities.maxOfOrNull { it.activeDuration } ?: 0.0
    if (maximum <= 0) return if (activity.isActive) 15.0 else 5.0
    if (activity.activeDuration <= 0) return if (activity.isActive) 12.0 else 5.0
    return maxOf(8.0, 58.0 * (activity.activeDuration / maximum))
  }

  /** `M/d` from a JST day (iOS `shortDay`). */
  fun shortDay(day: JstDay): String {
    val raw = day.rawValue
    val parts = raw.split("-")
    if (parts.size != 3) return raw
    val month = parts[1].toIntOrNull() ?: return raw
    val date = parts[2].toIntOrNull() ?: return raw
    return "$month/$date"
  }

  /** Minutes rounded, or null when under a minute (show "less than a minute"). */
  fun roundedMinutes(duration: Double): Int? = if (duration < 60) null else Math.round(duration / 60).toInt()

  /** Weak jamo bar fraction (≥ 8dp is applied by the view). */
  fun weakJamoFraction(count: Int, maximum: Int): Float = count.toFloat() / maxOf(1, maximum).toFloat()
}
