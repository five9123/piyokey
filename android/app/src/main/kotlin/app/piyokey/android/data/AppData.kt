package app.piyokey.android.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.piyokey.android.Services
import app.piyokey.android.data.catalog.BundleCatalogRepository
import app.piyokey.android.data.catalog.CachedRemoteCatalogRepository
import app.piyokey.android.data.catalog.CatalogCacheStore
import app.piyokey.android.data.catalog.CatalogLibrary
import app.piyokey.android.data.decks.BundledContent
import app.piyokey.android.data.decks.BundledDeckSource
import app.piyokey.android.data.decks.DeckInstallationStore
import app.piyokey.android.data.decks.DeckLibrary
import app.piyokey.android.data.decks.InstalledDeckSource
import app.piyokey.android.data.decks.PendingImportsStore
import app.piyokey.android.data.decks.StaticDeckSource
import app.piyokey.android.data.decks.UserDeckDraftStore
import app.piyokey.android.data.decks.appName
import app.piyokey.android.data.onboarding.OnboardingLegacyDataDetector
import app.piyokey.android.data.onboarding.OnboardingLibrary
import app.piyokey.android.data.onboarding.OnboardingStore
import app.piyokey.android.data.persistence.AndroidAssetSource
import app.piyokey.android.data.persistence.AssetSource
import app.piyokey.android.data.persistence.HancoPaths
import app.piyokey.android.data.persistence.KeyValueStore
import app.piyokey.android.data.persistence.SharedPreferencesKeyValueStore
import app.piyokey.android.data.progress.CurriculumProgressLibrary
import app.piyokey.android.data.progress.CurriculumProgressStore
import app.piyokey.android.data.progress.GameProgressLibrary
import app.piyokey.android.data.progress.GameProgressStore
import app.piyokey.android.data.progress.RetentionLibrary
import app.piyokey.android.data.progress.RetentionStore
import app.piyokey.android.data.progress.ReviewDeckLibrary
import app.piyokey.android.data.progress.ReviewDeckStore
import app.piyokey.android.data.recommendations.HomeRecommendations
import app.piyokey.android.data.retention.QuickPracticeContent
import app.piyokey.android.data.retention.RandomWordPracticeHistory
import app.piyokey.android.data.settings.Prefs
import app.piyokey.core.deckkit.Deck
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Process-wide data singletons, created lazily on first use from [Services.app]. Construct/observe
 * them on the main thread; they load their small JSON files synchronously on first access (as iOS).
 */
object AppData {
  /** PIYOKEY Pro entitlement for the free user-deck limit. Integrator: `AppData.proAccess = { ProStore.hasAccess.value }`. */
  @Volatile var proAccess: () -> Boolean = { false }

  /** Analytics hook for catalog installs (iOS `deck_downloaded`). */
  @Volatile var onDeckDownloaded: ((Deck, InstalledDeckSource) -> Unit)? = null

  /** Main-thread scope for background persistence completions. */
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /** `filesDir/Hanco`. */
  val root: File by lazy { HancoPaths.root(Services.app.filesDir) }
  val assets: AssetSource by lazy { AndroidAssetSource(Services.app.assets) }
  val keyValues: KeyValueStore by lazy { SharedPreferencesKeyValueStore { Prefs.shared } }
  val bundled: BundledContent by lazy { BundledContent(assets) }

  val deckStore: DeckInstallationStore by lazy { DeckInstallationStore(File(root, HancoPaths.INSTALLED_DECKS)) }
  private val deckLibraryLazy = lazy {
    DeckLibrary(
      source = StaticDeckSource(BundledDeckSource(assets)),
      store = deckStore,
      scope = scope,
      hasProAccess = { proAccess() },
      displayName = { it.appName },
      onDeckDownloaded = { deck, source -> onDeckDownloaded?.invoke(deck, source) },
    )
  }
  val deckLibrary: DeckLibrary by deckLibraryLazy

  val catalogCache: CatalogCacheStore by lazy { CatalogCacheStore(File(root, "${HancoPaths.CATALOG_CACHE}/catalog-cache.json")) }
  val catalog: CatalogLibrary by lazy {
    CatalogLibrary(CachedRemoteCatalogRepository(BundleCatalogRepository(assets), catalogCache), scope)
  }

  private val gameProgressLazy = lazy { GameProgressLibrary(GameProgressStore(File(root, HancoPaths.PROGRESS)), scope) }
  val gameProgress: GameProgressLibrary by gameProgressLazy

  private val curriculumLazy = lazy { CurriculumProgressLibrary(CurriculumProgressStore(File(root, HancoPaths.CURRICULUM)), scope) }
  val curriculum: CurriculumProgressLibrary by curriculumLazy

  private val reviewLazy = lazy { ReviewDeckLibrary(ReviewDeckStore(File(root, HancoPaths.REVIEW)), scope) }
  val review: ReviewDeckLibrary by reviewLazy

  val retention: RetentionLibrary by lazy { RetentionLibrary(RetentionStore(File(root, HancoPaths.RETENTION))) }

  val onboarding: OnboardingLibrary by lazy {
    OnboardingLibrary(OnboardingStore(keyValues)) { OnboardingLegacyDataDetector.hasExistingData(keyValues, root) }
  }

  /** Deck Maker's single autosaved draft. */
  val drafts: UserDeckDraftStore by lazy { UserDeckDraftStore(File(root, HancoPaths.DECK_MAKER)) }

  /** Staged `.typedeck` documents awaiting confirmation. */
  val pendingImports: PendingImportsStore by lazy { PendingImportsStore(File(root, HancoPaths.PENDING_IMPORTS)) }

  val randomWordHistory: RandomWordPracticeHistory by lazy { RandomWordPracticeHistory(keyValues) }
  val quickPractice: QuickPracticeContent by lazy { QuickPracticeContent(bundled, randomWordHistory) }

  val recommendations: HomeRecommendations by lazy {
    HomeRecommendations(catalog, deckLibrary, onboarding, gameProgress, curriculum)
  }

  /** Starts background writes of debounced progress (iOS flushes on background/inactive/terminate). */
  fun flushPendingProgress() {
    if (gameProgressLazy.isInitialized()) gameProgress.flush()
    if (curriculumLazy.isInitialized()) curriculum.flush()
    if (reviewLazy.isInitialized()) review.flush()
  }

  /** Awaits every pending progress write; `true` when all succeeded. */
  suspend fun flushPendingProgressAndWait(): Boolean = coroutineScope {
    var ok = true
    if (gameProgressLazy.isInitialized()) ok = gameProgress.flushAndWait() && ok
    if (curriculumLazy.isInitialized()) ok = curriculum.flushAndWait() && ok
    if (reviewLazy.isInitialized()) ok = review.flushAndWait() && ok
    ok
  }

  /** Flushes progress whenever the app goes to the background. Call once from `Application.onCreate`. */
  fun observeProcessLifecycle() {
    scope.launch {
      ProcessLifecycleOwner.get().lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          override fun onStop(owner: LifecycleOwner) = flushPendingProgress()
        },
      )
    }
  }
}
