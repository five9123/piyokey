# `app.piyokey.android.data` — data layer

1:1 port of the iOS data layer (`ios/Hanco/Hanco/Core/{Persistence,DeckLibrary,StaticContent,Progress,Onboarding}`,
`Features/Discover/CatalogRepository.swift`). Pure rules live in `:core:domain`
(`app.piyokey.core.domain`); this package adds persistence, assets, networking and observable
libraries. `data/settings` (integrator) and `data/mascot` are documented elsewhere.

Conventions:

- Everything is reached through **`AppData`** (lazy singletons over `Services.app`).
- Libraries expose `StateFlow`s. Call their mutating functions on the **main thread**; file
  writes run on `Dispatchers.IO` in the background (`flush()`), or awaitably (`flushAndWait()`).
- Stores (`*Store`) are synchronous blocking file IO over a root `File` (JVM-testable).
- Files: `filesDir/Hanco/<Area>/…` with the iOS names, `schema_version`s and snake_case keys.
  Every JSON file is written atomically (temp + fsync + rename) with a `.backup`; a corrupt
  primary moves to `.corrupt` and is restored from the backup; a **future schema is never
  overwritten** (loads throw `…UnsupportedSchema`, background writes fail with `saveFailed`).
- Dates are ISO-8601 whole seconds (`2026-07-19T01:02:03Z`), like Swift `.iso8601`.
- Localization keys produced by `core/domain` (curriculum, retention, onboarding, piyodeck
  notices) resolve through `DomainText` / `DomainStringIds` (generated map, no `getIdentifier`).

## `AppData`

```kotlin
object AppData {
  var proAccess: () -> Boolean                     // integrator: { ProStore.hasAccess.value } (default false)
  var onDeckDownloaded: ((Deck, InstalledDeckSource) -> Unit)?   // analytics hook
  val scope: CoroutineScope                        // SupervisorJob + Main.immediate
  val root: File                                   // filesDir/Hanco
  val assets: AssetSource; val keyValues: KeyValueStore; val bundled: BundledContent
  val deckStore: DeckInstallationStore
  val deckLibrary: DeckLibrary
  val catalogCache: CatalogCacheStore
  val catalog: CatalogLibrary
  val gameProgress: GameProgressLibrary
  val curriculum: CurriculumProgressLibrary
  val review: ReviewDeckLibrary
  val retention: RetentionLibrary
  val onboarding: OnboardingLibrary
  val drafts: UserDeckDraftStore
  val pendingImports: PendingImportsStore
  val randomWordHistory: RandomWordPracticeHistory
  val quickPractice: QuickPracticeContent
  val recommendations: HomeRecommendations
  fun flushPendingProgress()                       // start writes (game, curriculum, review)
  suspend fun flushPendingProgressAndWait(): Boolean
  fun observeProcessLifecycle()                    // call once in Application.onCreate: flush on ON_STOP
}
```

## Decks — `data/decks`

```kotlin
class DeckLibrary {                                 // AppData.deckLibrary
  val installedDecks: StateFlow<Map<String, Deck>>
  val records: StateFlow<Map<String, InstalledDeckRecord>>
  val installedList: StateFlow<List<Deck>>          // last played (else installed) first; tie → appName
  val installed: List<Deck>                          // current installedList value
  val installingDeckIds: StateFlow<Set<String>>
  val failedDeckIds: StateFlow<Set<String>>
  val downloadHistory: StateFlow<Map<String, List<String>>>   // deckId → tags (survives removal)
  val installedUserDeckCount: Int
  fun canInstallNewUserDeck(hasPiyokeyProAccess: Boolean = proAccess()): Boolean  // free: 3 user decks
  fun isInstalled(deckId: String): Boolean
  fun installedDeck(deckId: String): Deck?
  fun record(deckId: String): InstalledDeckRecord?
  fun needsUpdate(entry: CatalogDeck): Boolean
  suspend fun install(entry: CatalogDeck)           // failures → failedDeckIds
  fun installInBackground(entry: CatalogDeck): Job
  suspend fun installUserDeck(data: ByteArray, source: InstalledDeckSource /* IMPORTED|CREATED */,
    contentSha256: String, packageFormatVersion: Int, isLocallyModified: Boolean,
    hasPiyokeyProAccess: Boolean = proAccess(), derivedFromDeckId: String? = null,
    expectedCurrentVersion: Int? = null): Deck
    // throws PiyoDeckImportError.InvalidUserDeck, PiyokeyProAccessException.FreeUserDeckLimitReached,
    //        DeckInstallationStoreException.SourceChanged(deckId, expectedVersion, actualVersion)
  suspend fun exportData(deckId: String): ByteArray // exact payload for PiyoDeckPackageWriter
  fun remove(deckId: String); suspend fun removeAndWait(deckId: String)
  fun markPlayed(deckId: String)                    // call when a session on the deck starts/ends
  fun reload()
}
enum class InstalledDeckSource { BUNDLE, REMOTE, IMPORTED, CREATED }  // .isUserDeck
data class InstalledDeckRecord(deckId, version, installedAt, lastPlayedAt?, source, contentSha256?,
  packageFormatVersion?, isLocallyModified, derivedFromDeckId?)
object UserDeckLimits { const val FREE_INSTALLED_USER_DECK_LIMIT = 3 }
```

After deleting a deck call `AppData.review.markSourceUnavailable(deckId)`; after installing an
update/re-import call `AppData.review.reconcile(deck)` (iOS does this in the views).

`DeckInstallationStore(rootDir)` — `InstalledDecks/installed-decks.json` (schema 3) +
`<deckId>.json` + `PendingTransaction/` crash recovery: `loadSnapshot()`, `install(...)`,
`remove(id)`, `markPlayed(id, at)`, `data(id)`, `reset()`.

Sources: `DeckSource { suspend fun fetch(entry): DeckDownloadPayload }`, `BundledDeckSource(assets)`,
`RemoteDeckSource(configuration, client)`, `StaticDeckSource(bundled)` (production).

```kotlin
class BundledContent(assets) {                      // AppData.bundled
  val deckSchema: ByteArray                         // for PiyoDeckPackageReader/Writer
  val rankTuning: FlowGameRankTuning                // tuning/game_rank_tuning.json → rank(accuracy, cpm)
  fun deck(relativePath: String): Deck?             // "decks/…json", validated
  fun officialDeck(entry: CatalogDeck): Deck?
  fun piyoCupDeck(): Deck?                          // decks/flow/flow_topik_beginner_v3.json
  fun gamePresetDeck(gameFolder: String, deckId: String, version: Int = 3): Deck?
  fun randomWordFallbackDecks(goal: OnboardingGoal?, catalog: Catalog?): List<Deck>
}
```

Deck Maker draft (`DeckMaker/active-user-deck-draft.json`, one active draft):

```kotlin
class UserDeckDraftStore(rootDir) {                 // AppData.drafts
  fun load(): ActiveUserDeckDraft?                  // throws UserDeckDraftStoreException.UnsupportedSchema
  fun save(draft: UserDeckDraft, at: Instant = now()): ActiveUserDeckDraft  // same flow keeps draftId/createdAt
  fun clear(draftId: String): Boolean               // only the committed draft
  fun clear()
}
data class ActiveUserDeckDraft(draftId, draft, createdAt, updatedAt) { val baseDeckId: String?; val baseVersion: Int? }
```

Save flow: `draft.validatedDeck(now, localeCode)` → `DeckJson.encodeDeck` bytes → `sha256Hex` →
`deckLibrary.installUserDeck(..., source = CREATED, isLocallyModified = true,
expectedCurrentVersion = active.baseVersion, derivedFromDeckId = draft.derivedFromDeckId)` →
`drafts.clear(active.draftId)`.

`.typedeck` imports (`PendingImports/`):

```kotlin
class PendingImportsStore(rootDir) {                // AppData.pendingImports
  fun stage(input: InputStream): File               // ≤ 8 MiB, via .partial; PiyoDeckFileTooLargeException
  fun pending(): List<File>                         // cleans *.partial
  fun read(file: File, deckSchemaData: ByteArray): PiyoDeckPackage   // PiyoDeckImportError
  fun remove(file: File); fun reset()
}
```

Collision/copy rules are pure: `PiyoDeckImportCollision.of(record?.version, record?.contentSha256,
installedDeck, package)` (`New`/`Identical`/`Different`, `isDowngrade`, `isSameVersionConflict`),
`PiyoDeckDocumentRules.makeUserCopy/safeFilename`, `PiyoDeckDocumentNotice.titleKey/messageKey`.

Content in the app language (`ContentLocalization.kt`, iOS `Core/Settings` extensions):

```kotlin
val DeckItem.appMeaning: String?; val DeckItem.appReading: String?
fun DeckItem.appMeaning(deck: Deck): String?        // honours deck.defaultLocale (v2 decks)
val Deck.appName / appAuthorNickname: String; val Deck.appTags: List<String>
val CatalogDeck.appName / appAuthorNickname: String; val CatalogDeck.appTags: List<String>
val CatalogDeck.isAvailableInCurrentLanguage: Boolean
val CatalogPreviewItem.appMeaning: String?; val CatalogTag.appTag: String?
object ContentLocale { /* same rules with explicit languageCode + fallback text (JVM-testable) */ }
```

## Catalog — `data/catalog`

```kotlin
class CatalogLibrary {                              // AppData.catalog (shared by Home/Discover)
  enum class LoadState { IDLE, LOADED, FAILED }
  val catalog: StateFlow<Catalog?>
  val loadState: StateFlow<LoadState>
  fun loadIfNeeded(): Job?                          // local (bundled or newer cache) now, remote refresh in bg
  fun retry(): Job?
}
interface CatalogRepository { fun loadCatalog(): Catalog; suspend fun refreshCatalog(): Catalog? }
class CachedRemoteCatalogRepository(bundled, cache, client, catalogUrl = StaticContentConfiguration.live.catalogUrl, now)
class CatalogCacheStore(file)                       // CatalogCache/catalog-cache.json (schema 1, json_blob base64)
data class StaticContentConfiguration(catalogUrl: URI?) { fun deckUrl(relativePath): URI? }  // BuildConfig.CATALOG_URL
fun interface HttpDataClient { suspend fun execute(request: HttpRequest): HttpResponse }    // UrlConnectionHttpDataClient
```

Remote refresh sends `If-None-Match`/`If-Modified-Since`; 304 keeps the cache; the cache is used
only when its `catalog_version` ≥ the bundled one, and a refreshed catalog is applied only if not
older than the one shown. Empty `CATALOG_URL` → bundled only.

## Progress — `data/progress`

```kotlin
class GameProgressLibrary {                         // AppData.gameProgress — Progress/game-progress.json (schema 3)
  val records: StateFlow<List<GameRecord>>          // lessons + games, ≤ 2,048 (leaderboard bests kept)
  val deckProgress: StateFlow<Map<String, DeckProgress>>
  val saveFailed: StateFlow<Boolean>
  fun append(record: GameRecord): GameRecordSaveOutcome?   // isNewBest, previousBestScore, deckProgress
  fun appendLesson(record: GameRecord): Boolean            // mode = LESSON only
  fun progress(deckId, gameKind = FLOW, inputMode = BUILT_IN, competition: GameCompetition? = null): DeckProgress?
  fun bestCombo(deckId, gameKind = FLOW, inputMode = BUILT_IN, competition = null): Int
  fun flush(); suspend fun flushAndWait(): Boolean; fun retryLastSave(); fun reload()
}
// GameRecord(id = GameRecord.newId(), mode, deckId, deckVersion?, competition?, course /* "word", "acid_rain", … */,
//   score, maxCombo, accuracy /* 0…100 */, charactersPerMinute, activeDuration /* s */, completedItemCount,
//   missedItemCount, inputMode: SessionInputMode, playedAt)
// LearningInsights.make(period, asOf, records, retention.records.value, review.items.value.values, mistakeCounts)

class CurriculumProgressLibrary {                   // AppData.curriculum — Curriculum/curriculum-progress.json
  val stageProgress: StateFlow<Map<String, CurriculumStageProgress>>
  val activeSession: StateFlow<CurriculumActiveSession?>
  val saveFailed: StateFlow<Boolean>
  val completedStageIds: Set<String>
  fun progress(stageId): CurriculumStageProgress?
  fun checkpoint(stageId): PracticeSessionCheckpoint?
  fun save(stageId, checkpoint)                     // debounced 750 ms
  fun finish(stageId, stars, accuracy)
  suspend fun finishAndWait(stageId, stars, accuracy): Boolean   // durable + verified, rolls back on failure
  fun flush(); suspend fun flushAndWait(): Boolean; fun retryLastSave(); fun reload()
}
// stars: CurriculumStarRating.stars(accuracy, cpm)

class ReviewDeckLibrary {                           // AppData.review — Review/review-deck.json
  val items: StateFlow<Map<String, ReviewDeckItem>> // "<sourceDeckId>::<itemId>"
  val activeItems: StateFlow<List<ReviewDeckItem>>  // newest first; item.deckItem for practice
  val saveFailed: StateFlow<Boolean>
  fun recordMistake(item: DeckItem, sourceDeckId: String): ReviewDeckMutation   // debounced
  fun recordPerfect(itemId: String, sourceDeckId: String): ReviewDeckMutation   // 3 in a row → GRADUATED
  fun addManually(item, sourceDeckId); fun removeManually(itemId, sourceDeckId)
  fun isInReview(itemId, sourceDeckId): Boolean
  fun reconcile(deck: Deck): ReviewDeckMutation
  suspend fun markSourceUnavailable(deckId: String): ReviewDeckMutation
  fun flush(); suspend fun flushAndWait(): Boolean; fun retryLastSave(); fun reload()
}
// Curriculum mistakes use stage.sourceDeckId ("curriculum::<stage>"); daily challenge DailyChallenge.SOURCE_DECK_ID.

class RetentionLibrary {                            // AppData.retention — Retention/retention-progress.json
  val records: StateFlow<Map<JstDay, RetentionDayRecord>>
  val saveFailed: StateFlow<Boolean>
  val completedDays: Set<JstDay>
  fun record(activity: RetentionActivityKind, session: RetentionSession)   // CURRICULUM, GAME, DAILY_CHALLENGE, QUICK_PRACTICE
  fun record(activity, day: JstDay)
  fun isStamped(day); fun completedDailyChallenge(day)
  fun streak(asOf: JstDay): RetentionStreak         // current (yesterday counts until today ends), longest
  fun stampedDaysThisWeek(today: JstDay): Int       // Mon–Sun JST; StampReward.THREE/FIVE/SEVEN.isEarned(n)
  fun dailyEncouragementKey(today: JstDay): String  // "mascot.daily_encouragement.N" → DomainText.string(key)
  fun retryLastSave(); fun reload()
}
data class RetentionSession(val day: JstDay) { companion object { fun start(at = RetentionClock.now()) } }
object RetentionClock { var override: Instant?; fun now(); fun today(): JstDay }
```

Pure helpers: `JstDay` (`parse`, `of(instant)`, `adding`, `ordinal`), `RetentionCalendar`
(`stampCardDays`, `days`), `RetentionStampDayState.of(day, today, stamped).localizationKey`.

## Onboarding — `data/onboarding`

```kotlin
class OnboardingLibrary {                           // AppData.onboarding (prefs: onboarding.state[.backup|.corrupt])
  val snapshot: StateFlow<OnboardingSnapshot>
  val saveFailed: StateFlow<Boolean>
  val shouldPresent: Boolean
  val selectedGoal: OnboardingGoal?; val selectedLevel: OnboardingLevel?; val preferredTags: List<String>
  var appTourCompleted: Boolean                     // onboarding.app_tour.completed
  var homeLearningStarted: Boolean                  // onboarding.home_learning_started
  var notificationPermissionRequested: Boolean      // onboarding.notification_permission_requested
  fun select(goal); fun selectLevel(level); fun move(step: OnboardingStep); fun complete(skipped: Boolean)
  fun retryLastSave(); fun reset()
}
```

Legacy users (keyboard prefs or any file under `filesDir/Hanco`) are migrated as completed+skipped.
Hatching: `HatchOnboardingPolicy.requiredStages / nextRequiredStage(completed) / isComplete(completed)`.

## Curriculum text — `data/curriculum`

```kotlin
object CurriculumContent {
  fun title(stage) / detail(stage) / title(chapter): String
  fun deckItem(item: CurriculumItem): DeckItem      // ja base + es/de/fr/en meanings (en reading) + ko; bundled audio
  fun deckItems(stage: CurriculumStage): List<DeckItem>
}
object DomainText { fun string(key, language = current): String; fun korean(key): String; fun id(key): Int? }
```

Pure catalog: `CurriculumCatalog.chapters / stages / stage(id)`, `CurriculumUnlockPolicy`,
`CurriculumChapterCompletionPolicy`. `DomainStringIds` is a generated `when` map from every key
the domain produces (curriculum titles/details/readings/meanings, `mascot.daily_encouragement.*`,
`retention.rewards.*`, `retention.stamp.*`, `onboarding.goal|level.*`, `piyodeck.notice.*`) to
`R.string` ids; extend it when a new domain key scheme is added.

## Home quick actions & recommendations

```kotlin
class QuickPracticeContent {                        // AppData.quickPractice
  fun dailyChallenge(today: JstDay, goal: OnboardingGoal?): DailyChallenge   // 5 items, stride 3
  fun nextRandomWordSession(installedDecks: List<Deck>, goal, catalog, random = Random.Default): RandomWordPracticeSession?
}
class RandomWordPracticeHistory(defaults) { val recentWordKeys: List<String>; fun record(session); fun reset() }

class HomeRecommendations {                         // AppData.recommendations
  fun homeAreas(catalog = current): DeckRecommendationEngine.HomeAreas  // personal (3) + nextStep (3), disjoint
  fun hasLearningSignal(catalog = current): Boolean // choose the cold-start subtitle
  fun starter(limit = 1, catalog = current): List<CatalogDeck>
  fun onboardingPicks(catalog = current): List<CatalogDeck>
  fun related(sourceDeckId, sourceTags, catalog = current): List<CatalogDeck>   // result screen (2)
}
```

## Tests

`app/src/test/kotlin/app/piyokey/android/data/**` (JVM, no Android runtime; assets are read from
`../../shared` through `DirectoryAssetSource`) and `core/domain/src/test/**`.
