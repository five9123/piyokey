package app.piyokey.android.feature.practice

import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.curriculum.CurriculumContent
import app.piyokey.android.data.decks.InstalledDeckSource
import app.piyokey.android.data.decks.appName
import app.piyokey.android.data.progress.RetentionClock
import app.piyokey.android.data.progress.RetentionSession
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.android.ui.theme.L
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.DailyChallenge
import app.piyokey.core.domain.RandomWordPracticeSession
import app.piyokey.core.domain.RetentionActivityKind
import java.util.UUID

/** Where a practice session's items come from (analytics + completion side effects). */
enum class PracticeSource { CURRICULUM, DECK, DAILY_CHALLENGE, RANDOM_WORDS, REVIEW, FREE }

/** One practice target with its review-deck source (iOS `PracticeReviewSource`). */
data class PracticeItemSource(val item: DeckItem, val sourceDeckId: String)

/**
 * Everything a practice session needs (the iOS `PracticeView` initializer arguments).
 * [id] keys the session UI: replacing the config (random words "shuffle" retry) recreates it.
 */
class PracticeSessionConfig(
  val sources: List<PracticeItemSource>,
  val title: String,
  val source: PracticeSource,
  val sourceTags: List<String> = emptyList(),
  /** Shows the "related decks" block on the result (iOS passes `catalogDecks`). */
  val showsRecommendations: Boolean = false,
  val curriculumStageId: String? = null,
  val allowsOsIme: Boolean = true,
  val allowsKorean10Key: Boolean = true,
  val analyticsSessionKind: String = "free_practice",
  val analyticsDeckSource: String = "unknown",
  val retryTitleRes: Int = R.string.practice_result_retry,
  val retryIsShuffle: Boolean = false,
  /** Non-curriculum completion hook (retention stamps for daily/random). */
  val onPracticeCompletion: ((accuracy: Double, cpm: Double) -> Unit)? = null,
  /** Retry hook; a returned config replaces the session (random words), `null` resets in place. */
  val onSessionRestart: (() -> PracticeSessionConfig?)? = null,
  val chainsHatchMissions: Boolean = false,
  val isFinalHatchMission: Boolean = false,
  val onResultFinished: (() -> Unit)? = null,
  val onPersistenceFailureExit: (() -> Unit)? = null,
  val onAppear: (() -> Unit)? = null,
  val id: String = UUID.randomUUID().toString(),
) {
  val targets: List<String> get() = sources.map { it.item.ko }
}

/** Cross-feature entry points into practice sessions (all full-screen on the app navigator). */
object PracticeLauncher {
  /** Opens the lesson session for a curriculum stage (resumes its checkpoint). */
  fun curriculumStage(nav: Navigator, stageId: String) {
    val config = curriculumConfig(stageId) ?: return
    nav.push(PracticeSessionRoute(config))
  }

  /** Deck practice for an installed deck (iOS `DeckPracticeDestination`). */
  fun deck(nav: Navigator, deckId: String) {
    val config = deckConfig(deckId) ?: return
    nav.push(PracticeSessionRoute(config))
  }

  /**
   * Practice over arbitrary items: daily challenge / random words / review / free practice.
   * Review items resolve their source deck from the review library.
   */
  fun items(nav: Navigator, title: String, items: List<DeckItem>, source: PracticeSource) {
    if (items.isEmpty()) return
    val sources = when (source) {
      PracticeSource.REVIEW -> {
        val active = AppData.review.activeItems.value
        items.map { item ->
          PracticeItemSource(item, active.firstOrNull { it.itemId == item.id && it.ko == item.ko }?.sourceDeckId ?: "review_deck")
        }
      }
      PracticeSource.DAILY_CHALLENGE -> items.map { PracticeItemSource(it, DailyChallenge.SOURCE_DECK_ID) }
      else -> items.map { PracticeItemSource(it, "free_practice") }
    }
    nav.push(PracticeSessionRoute(itemsConfig(title, sources, source)))
  }

  /** Daily 3-minute challenge (iOS `DailyChallengePracticeDestination`). */
  fun dailyChallenge(nav: Navigator, challenge: DailyChallenge) {
    val sources = challenge.items.map { PracticeItemSource(CurriculumContent.deckItem(it), DailyChallenge.SOURCE_DECK_ID) }
    nav.push(
      PracticeSessionRoute(
        PracticeSessionConfig(
          sources = sources,
          title = L.string(R.string.retention_daily_session_title),
          source = PracticeSource.DAILY_CHALLENGE,
          analyticsSessionKind = "daily",
          analyticsDeckSource = "daily",
          onPracticeCompletion = { _, _ -> AppData.retention.record(RetentionActivityKind.DAILY_CHALLENGE, challenge.day) },
        ),
      ),
    )
  }

  /** Random five words; "shuffle" retry draws the next session (iOS `RandomWordPracticeDestination`). */
  fun randomWords(nav: Navigator, session: RandomWordPracticeSession) {
    nav.push(PracticeSessionRoute(randomConfig(session)))
  }

  /** The whole active review deck (iOS `ReviewDeckView` start button). */
  fun reviewDeck(nav: Navigator) {
    val active = AppData.review.activeItems.value
    if (active.isEmpty()) return
    nav.push(PracticeSessionRoute(reviewConfig(active.map { PracticeItemSource(it.deckItem, it.sourceDeckId) })))
  }

  internal fun reviewConfig(sources: List<PracticeItemSource>) = PracticeSessionConfig(
    sources = sources,
    title = L.string(R.string.review_deck_title),
    source = PracticeSource.REVIEW,
    analyticsSessionKind = "review",
    analyticsDeckSource = "review",
  )

  internal fun itemsConfig(title: String, sources: List<PracticeItemSource>, source: PracticeSource): PracticeSessionConfig =
    when (source) {
      PracticeSource.REVIEW -> reviewConfig(sources)
      PracticeSource.DAILY_CHALLENGE -> PracticeSessionConfig(
        sources = sources, title = title, source = source,
        analyticsSessionKind = "daily", analyticsDeckSource = "daily",
        onPracticeCompletion = { _, _ -> AppData.retention.record(RetentionActivityKind.DAILY_CHALLENGE, RetentionClock.today()) },
      )
      PracticeSource.RANDOM_WORDS -> {
        val retention = RetentionSession.start()
        PracticeSessionConfig(
          sources = sources, title = title, source = source,
          onPracticeCompletion = { _, _ -> AppData.retention.record(RetentionActivityKind.QUICK_PRACTICE, retention) },
        )
      }
      else -> PracticeSessionConfig(sources = sources, title = title, source = source)
    }

  internal fun randomConfig(session: RandomWordPracticeSession): PracticeSessionConfig {
    val retention = RetentionSession.start()
    return PracticeSessionConfig(
      sources = session.sources.map { PracticeItemSource(it.item, it.sourceDeckId) },
      title = L.string(R.string.home_quick_random_session_title),
      source = PracticeSource.RANDOM_WORDS,
      sourceTags = session.sources.flatMap { it.sourceTags }.toSet().sorted(),
      showsRecommendations = true,
      retryTitleRes = R.string.home_quick_random_retry,
      retryIsShuffle = true,
      onPracticeCompletion = { _, _ -> AppData.retention.record(RetentionActivityKind.QUICK_PRACTICE, retention) },
      onSessionRestart = {
        AppData.quickPractice.nextRandomWordSession(
          installedDecks = AppData.deckLibrary.installed,
          goal = AppData.onboarding.selectedGoal,
          catalog = AppData.catalog.catalog.value,
        )?.let { randomConfig(it) }
      },
    )
  }

  internal fun deckConfig(deckId: String): PracticeSessionConfig? {
    val library = AppData.deckLibrary
    val deck = library.installedDeck(deckId) ?: return null
    val analyticsSource = when (library.record(deckId)?.source) {
      InstalledDeckSource.BUNDLE -> "bundled"
      InstalledDeckSource.REMOTE -> "catalog"
      InstalledDeckSource.IMPORTED -> "imported"
      InstalledDeckSource.CREATED -> "created"
      null -> "unknown"
    }
    AppData.catalog.loadIfNeeded()
    return PracticeSessionConfig(
      sources = deck.items.map { PracticeItemSource(it, deck.deckId) },
      title = deck.appName,
      source = PracticeSource.DECK,
      sourceTags = deck.tags,
      showsRecommendations = true,
      analyticsDeckSource = analyticsSource,
      onAppear = { library.markPlayed(deckId) },
    )
  }

  /**
   * Curriculum lesson (iOS `CurriculumPracticeDestination`): checkpoints, durable completion,
   * retention stamp, dubeolsik only (chapters are always built-in or OS keyboard).
   */
  internal fun curriculumConfig(
    stageId: String,
    chainsHatchMissions: Boolean = false,
    isFinalHatchMission: Boolean = false,
    onResultFinished: (() -> Unit)? = null,
    onPersistenceFailureExit: (() -> Unit)? = null,
  ): PracticeSessionConfig? {
    val stage = CurriculumCatalog.stage(stageId) ?: return null
    return PracticeSessionConfig(
      sources = CurriculumContent.deckItems(stage).map { PracticeItemSource(it, stage.sourceDeckId) },
      title = CurriculumContent.title(stage),
      source = PracticeSource.CURRICULUM,
      curriculumStageId = stage.id,
      allowsOsIme = true,
      allowsKorean10Key = false,
      analyticsSessionKind = "lesson",
      analyticsDeckSource = "curriculum",
      chainsHatchMissions = chainsHatchMissions,
      isFinalHatchMission = isFinalHatchMission,
      onResultFinished = onResultFinished,
      onPersistenceFailureExit = onPersistenceFailureExit,
    )
  }
}
