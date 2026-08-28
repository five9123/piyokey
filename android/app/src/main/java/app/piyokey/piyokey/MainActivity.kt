package app.piyokey.piyokey

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import app.piyokey.core.data.CatalogRefreshResult
import app.piyokey.core.analytics.AnalyticsEvent
import app.piyokey.core.analytics.AnalyticsProperty
import app.piyokey.core.design.PiyokeyIcon
import app.piyokey.core.design.PiyokeyIconKind
import app.piyokey.core.design.PiyokeyTheme
import app.piyokey.core.data.DeckFilters
import app.piyokey.core.data.DeckLibrarySnapshot
import app.piyokey.core.data.DeckRepository
import app.piyokey.core.data.ActiveUserDeckDraft
import app.piyokey.core.data.DeckMakerEntitlementCache
import app.piyokey.core.data.PiyokeyDatabase
import app.piyokey.core.data.PiyokeyProPolicy
import app.piyokey.core.data.RoomPlayGamesLocalData
import app.piyokey.core.data.UserDeckEditCommitException
import app.piyokey.core.data.DiscoveryEngine
import app.piyokey.core.data.InstalledDeck
import app.piyokey.core.data.ImportedDeckException
import app.piyokey.core.data.ImportedDeckCommitResult
import app.piyokey.core.data.ImportedDeckPreview
import app.piyokey.core.data.LearningSnapshot
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.session.PracticeSessionState
import app.piyokey.core.session.PracticeSessionCheckpoint
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.game.FlowGameState
import app.piyokey.core.game.FlowRankTuning
import app.piyokey.core.game.AcidRainState
import app.piyokey.core.game.SpacingGameState
import app.piyokey.core.game.SpacingPassage
import app.piyokey.core.game.TypingGameState
import app.piyokey.core.game.toRecord
import app.piyokey.core.game.PlayGamesPolicy
import app.piyokey.core.game.PlayGamesResultIdentity
import app.piyokey.core.platform.DailyReminderScheduler
import app.piyokey.core.platform.DailyReminderDefaults
import app.piyokey.core.platform.ContentFeedbackController
import app.piyokey.core.platform.ContentFeedbackDeck
import app.piyokey.core.platform.ContentFeedbackKind
import app.piyokey.core.platform.ContentFeedbackRequest
import app.piyokey.core.platform.ContentFeedbackSource
import app.piyokey.core.platform.DeckMakerBillingActivity
import app.piyokey.core.platform.DeckMakerAuthorizationException
import app.piyokey.core.platform.DeckMakerBillingManager
import app.piyokey.core.platform.DeckMakerBillingNotice
import app.piyokey.core.platform.PlayBillingDeckMakerGateway
import app.piyokey.core.platform.RoomDeckMakerEntitlementCacheStore
import app.piyokey.core.platform.PiyoDeckDocumentGateway
import app.piyokey.core.platform.ResultShareModel
import app.piyokey.core.platform.GooglePlayGamesGateway
import app.piyokey.core.platform.PlayGamesConnection
import app.piyokey.core.platform.PlayGamesManager
import app.piyokey.core.platform.PlayGamesState
import app.piyokey.core.platform.StagedPiyoDeckDocument
import app.piyokey.core.piyodeck.PiyoDeckImportException
import app.piyokey.core.piyodeck.UserDeckDraft
import app.piyokey.core.piyodeck.UserDeckDraftOrigin
import app.piyokey.core.piyodeck.UserDeckLanguage
import app.piyokey.core.retention.CurriculumItem
import app.piyokey.core.retention.CurriculumCatalog
import app.piyokey.core.retention.CurriculumStage
import app.piyokey.core.retention.DailyChallengePolicy
import app.piyokey.core.retention.JstDay
import app.piyokey.core.retention.QuickPracticePolicy
import app.piyokey.core.retention.QuickPracticeSession
import app.piyokey.core.retention.ReviewItem
import app.piyokey.core.retention.RetentionPolicy
import app.piyokey.core.settings.AppPreferences
import app.piyokey.core.settings.AppLanguage
import app.piyokey.core.settings.AppPreferencesStore
import app.piyokey.core.settings.AppTheme
import app.piyokey.core.settings.InputMode
import app.piyokey.core.settings.InputModePolicy
import app.piyokey.core.settings.OnboardingPolicy
import app.piyokey.core.settings.PiyoWardrobePolicy
import app.piyokey.core.settings.PiyoAccessory
import app.piyokey.core.settings.PrivacyNoticePolicy
import app.piyokey.feature.discover.DeckCard
import app.piyokey.feature.discover.DeckDetailScreen
import app.piyokey.feature.discover.DiscoverScreen
import app.piyokey.feature.discover.MyDecksScreen
import app.piyokey.feature.discover.DeckMakerPaywallScreen
import app.piyokey.feature.discover.DeckMakerUiNotice
import app.piyokey.feature.discover.UserDeckEditorScreen
import app.piyokey.feature.discover.PracticeResultScreen
import app.piyokey.feature.discover.RecommendationHome
import app.piyokey.feature.discover.UserDeckImportError
import app.piyokey.feature.discover.UserDeckImportScreen
import app.piyokey.feature.practice.PracticeRoute
import app.piyokey.feature.practice.PracticeDisplayOptions
import app.piyokey.feature.practice.PracticeKeyboardOptions
import app.piyokey.feature.practice.PracticePrompt
import app.piyokey.feature.game.FlowDeckSelectionScreen
import app.piyokey.feature.game.FlowGameRoute
import app.piyokey.feature.game.FlowResultScreen
import app.piyokey.feature.game.GameHubScreen
import app.piyokey.feature.game.GameKind
import app.piyokey.feature.game.GameDeckSelectionScreen
import app.piyokey.feature.game.TypingGameRoute
import app.piyokey.feature.game.AcidRainRoute
import app.piyokey.feature.game.SpacingSelectionScreen
import app.piyokey.feature.game.SpacingGameRoute
import app.piyokey.feature.game.SpacingResultScreen
import app.piyokey.feature.game.GenericGameResultScreen
import app.piyokey.feature.game.ResultShareActions
import app.piyokey.feature.retention.CurriculumMapScreen
import app.piyokey.feature.retention.ReminderControls
import app.piyokey.feature.retention.RetentionHomeCard
import app.piyokey.feature.retention.ReviewDeckSection
import app.piyokey.feature.retention.PiyoProfileSection
import app.piyokey.feature.retention.ManualReviewCandidate
import app.piyokey.feature.onboarding.OnboardingRoute
import app.piyokey.feature.onboarding.HatchMissionGateScreen
import app.piyokey.feature.onboarding.HatchMissionResultScreen
import app.piyokey.feature.onboarding.MainAppTourOverlay
import app.piyokey.feature.settings.CommonSettingsButton
import app.piyokey.feature.settings.PrivacyConsentDialog
import app.piyokey.feature.settings.SettingsSheet
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
  private val incomingPiyoDeck = MutableStateFlow<IncomingPiyoDeck?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    publishIncomingDocument(intent)
    val preferencesStore = AppPreferencesStore.create(this) {
      resources.configuration.locales.let { locales ->
        (0 until locales.size()).map { locales[it].toLanguageTag() }
      }
    }
    setContent {
      val preferences by preferencesStore.values.collectAsStateWithLifecycle(initialValue = null)
      val incomingDocument by incomingPiyoDeck.collectAsStateWithLifecycle()
      val scope = rememberCoroutineScope()
      val resolved = preferences
      if (resolved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      } else {
        val testOverrides = remember {
          getSharedPreferences("piyokey_test_overrides", MODE_PRIVATE)
        }
        val freshOnboardingTest = BuildConfig.DEBUG && testOverrides.getBoolean("fresh_onboarding", false)
        val privacyNoticeReviewed = BuildConfig.DEBUG && testOverrides.getBoolean("privacy_notice_reviewed", false)
        val freshOnboardingPreferences = remember(freshOnboardingTest, privacyNoticeReviewed) {
          AppPreferences(
            language = resolved.language,
            privacyNoticeVersion = if (privacyNoticeReviewed) PrivacyNoticePolicy.currentVersion else 0,
            onboardingMigrationChecked = true,
          )
        }
        val launchPreferences = when {
          freshOnboardingTest -> freshOnboardingPreferences
          BuildConfig.DEBUG && testOverrides.getBoolean("skip_onboarding", false) -> resolved.copy(
            firstInputCompleted = true,
            hatchHandoffCompleted = true,
            hatchChaptersCompleted = 3,
            appTourCompleted = true,
            privacyNoticeVersion = if (privacyNoticeReviewed) PrivacyNoticePolicy.currentVersion else resolved.privacyNoticeVersion,
            onboardingMigrationChecked = true,
            defaultInputMode = if (testOverrides.getBoolean("force_os_ime", false)) InputMode.OS_IME else resolved.defaultInputMode,
          )
          else -> resolved
        }
        var optimistic by remember(launchPreferences) { mutableStateOf(launchPreferences) }
        LaunchedEffect(launchPreferences) { optimistic = launchPreferences }
        LaunchedEffect(optimistic.language) {
          val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
          if (tags != optimistic.language.tag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(optimistic.language.tag))
          }
        }
        LaunchedEffect(optimistic.theme) {
          val light = optimistic.theme == AppTheme.LIGHT
          WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
          }
          @Suppress("DEPRECATION")
          window.navigationBarColor = if (light) {
            android.graphics.Color.rgb(255, 248, 243)
          } else {
            android.graphics.Color.rgb(24, 21, 29)
          }
        }
        LaunchedEffect(
          optimistic.anonymousAnalyticsEnabled,
          optimistic.crashDiagnosticsEnabled,
        ) {
          TelemetryRuntime.updateConsent(applicationContext, optimistic)
        }
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
          LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * optimistic.fontScale.multiplier),
        ) {
          PiyokeyTheme(darkTheme = optimistic.theme == AppTheme.DARK) {
            PiyokeyApp(
              preferences = optimistic,
              incomingDocument = incomingDocument,
              onIncomingDocumentConsumed = { token ->
                if (incomingPiyoDeck.value?.token == token) {
                  incomingPiyoDeck.value = null
                  setIntent(
                    Intent(intent).apply {
                      action = Intent.ACTION_MAIN
                      data = null
                      type = null
                      removeExtra(Intent.EXTRA_STREAM)
                    },
                  )
                }
              },
              onPreferencesChange = { updated ->
                val previous = optimistic
                optimistic = updated
                if (
                  previous.anonymousAnalyticsEnabled != updated.anonymousAnalyticsEnabled ||
                  previous.crashDiagnosticsEnabled != updated.crashDiagnosticsEnabled
                ) {
                  TelemetryRuntime.updateConsent(applicationContext, updated)
                }
                if (
                  previous.anonymousAnalyticsEnabled != updated.anonymousAnalyticsEnabled &&
                  updated.anonymousAnalyticsEnabled
                ) {
                  TelemetryRuntime.capture(
                    AnalyticsEvent.SETTING_CHANGED,
                    mapOf(
                      AnalyticsProperty.SETTING to "analytics_consent",
                      AnalyticsProperty.VALUE_BUCKET to "enabled",
                    ),
                  )
                }
                if (previous.crashDiagnosticsEnabled != updated.crashDiagnosticsEnabled) {
                  TelemetryRuntime.capture(
                    AnalyticsEvent.SETTING_CHANGED,
                    mapOf(
                      AnalyticsProperty.SETTING to "diagnostics_consent",
                      AnalyticsProperty.VALUE_BUCKET to if (updated.crashDiagnosticsEnabled) "enabled" else "disabled",
                    ),
                  )
                }
                scope.launch { preferencesStore.update { updated } }
              },
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    publishIncomingDocument(intent)
  }

  @Suppress("DEPRECATION")
  internal fun publishIncomingDocument(intent: Intent?) {
    val uri = when (intent?.action) {
      Intent.ACTION_VIEW -> intent.data
      Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
      else -> null
    } ?: return
    incomingPiyoDeck.value = IncomingPiyoDeck(
      uri = uri,
      sourceContext = if (intent?.action == Intent.ACTION_SEND) "send" else "view",
      token = System.nanoTime(),
    )
  }
}

private data class IncomingPiyoDeck(val uri: Uri, val sourceContext: String, val token: Long)

private sealed interface UserDeckImportUiState {
  data class Loading(val document: StagedPiyoDeckDocument? = null) : UserDeckImportUiState
  data class Ready(
    val document: StagedPiyoDeckDocument,
    val preview: ImportedDeckPreview,
    val isWorking: Boolean = false,
  ) : UserDeckImportUiState
  data class Failed(
    val error: UserDeckImportError,
    val document: StagedPiyoDeckDocument? = null,
  ) : UserDeckImportUiState
}

private data class PendingUserDeckExport(val deckId: String, val deleteAfterExport: Boolean)

private enum class RootTab(val label: Int, val icon: PiyokeyIconKind) {
  HOME(R.string.nav_home, PiyokeyIconKind.HOME),
  DISCOVER(R.string.nav_discover, PiyokeyIconKind.DISCOVER),
  PRACTICE(R.string.nav_practice, PiyokeyIconKind.PRACTICE),
  GAMES(R.string.nav_games, PiyokeyIconKind.GAMES),
  PROFILE(R.string.nav_profile, PiyokeyIconKind.PROFILE),
  ;

  val analyticsFeature: String
    get() = when (this) {
      HOME -> "home"
      DISCOVER -> "discover"
      PRACTICE -> "practice"
      GAMES -> "game"
      PROFILE -> "my_page"
    }
}

private data class ActivePractice(
  val installed: InstalledDeck?,
  val catalogEntry: CatalogDeck?,
  val targets: List<String>,
  val items: List<DeckItem>?,
  val sourceDeckId: String?,
  val kind: PracticeKind,
  val inputMode: InputMode = InputMode.BUILTIN,
  val stageId: String? = null,
  val sessionDay: JstDay,
  val checkpoint: PracticeSessionCheckpoint? = null,
  val reviewSourceDeckIds: List<String>? = null,
  val sessionId: String = UUID.randomUUID().toString(),
)

private enum class PracticeKind { FREE, DECK, CURRICULUM, DAILY, QUICK, REVIEW, ONBOARDING }

private val PracticeKind.analyticsSessionKind: String
  get() = when (this) {
    PracticeKind.CURRICULUM, PracticeKind.ONBOARDING -> "lesson"
    PracticeKind.DAILY -> "daily"
    PracticeKind.REVIEW -> "review"
    PracticeKind.FREE, PracticeKind.DECK, PracticeKind.QUICK -> "free_practice"
  }

private val InputMode.analyticsValue: String
  get() = if (this == InputMode.OS_IME) "os_ime" else "builtin"

private val ActivePractice.analyticsDeckSource: String
  get() = when (kind) {
    PracticeKind.CURRICULUM, PracticeKind.ONBOARDING -> "curriculum"
    PracticeKind.DAILY -> "daily"
    PracticeKind.REVIEW -> "review"
    PracticeKind.FREE -> "bundled"
    PracticeKind.QUICK -> "unknown"
    PracticeKind.DECK -> installed?.metadata?.source
      ?.takeIf { it in setOf("bundled", "catalog", "imported", "created") } ?: "unknown"
  }

private val GameKind.analyticsValue: String
  get() = when (this) {
    GameKind.FLOW -> "flow"
    GameKind.ACID_RAIN -> "acid_rain"
    GameKind.CHOSEONG -> "choseong"
    GameKind.WORD_MATCH -> "word_match"
    GameKind.DICTATION -> "dictation"
    GameKind.SPACING -> "spacing"
  }

private fun analyticsDifficulty(deckId: String): String = when {
  deckId.contains("beginner", ignoreCase = true) -> "beginner"
  deckId.contains("intermediate", ignoreCase = true) -> "intermediate"
  deckId.contains("advanced", ignoreCase = true) -> "advanced"
  else -> "custom"
}

private fun analyticsGameDeckSource(deck: Deck?): String = when {
  deck == null -> "unknown"
  deck.deckId.startsWith("user_") -> "unknown"
  deck.official -> "catalog"
  else -> "bundled"
}

private data class PracticeResult(
  val practice: ActivePractice,
  val accuracyPercent: Double,
  val misses: Int,
  val completed: Int,
  val charactersPerMinute: Double,
  val stars: Int?,
)

private data class PendingPracticeCompletion(
  val practice: ActivePractice,
  val state: PracticeSessionState,
  val activeDurationMillis: Long,
)

private fun QuickPracticeSession.toActivePractice(inputMode: InputMode): ActivePractice = ActivePractice(
  installed = null,
  catalogEntry = null,
  targets = sources.map { it.item.ko },
  items = sources.map { it.item },
  sourceDeckId = null,
  kind = PracticeKind.QUICK,
  inputMode = inputMode,
  sessionDay = JstDay.fromEpochMillis(System.currentTimeMillis()),
  reviewSourceDeckIds = sources.map { it.sourceDeckId },
)

private enum class GameStage { HUB, DECK_SELECT, SPACING_SELECT }

@Composable
private fun PiyokeyApp(
  preferences: AppPreferences,
  incomingDocument: IncomingPiyoDeck?,
  onIncomingDocumentConsumed: (Long) -> Unit,
  onPreferencesChange: (AppPreferences) -> Unit,
) {
  val hostContext = LocalContext.current
  val hostActivity = hostContext as? Activity
  val context = hostContext.applicationContext
  val hadExistingDatabase = remember { context.getDatabasePath("piyokey.db").exists() }
  val repository = remember {
    DeckRepository.create(context, BuildConfig.CATALOG_URL.ifBlank { null })
  }
  val scope = rememberCoroutineScope()
  val billingManager = remember {
    DeckMakerBillingManager(
      gateway = PlayBillingDeckMakerGateway(context),
      cache = RoomDeckMakerEntitlementCacheStore(
        DeckMakerEntitlementCache(PiyokeyDatabase.open(context)),
      ),
    )
  }
  val billingState by billingManager.state.collectAsStateWithLifecycle()
  var championUnlockRequested by remember { mutableStateOf(false) }
  val playGamesManager = remember(hostActivity) {
    hostActivity?.let { activity ->
      PlayGamesManager(
        gateway = GooglePlayGamesGateway(activity),
        localData = RoomPlayGamesLocalData(PiyokeyDatabase.open(context)),
        onChampionUnlocked = { championUnlockRequested = true },
      )
    }
  }
  val playGamesStateFlow = remember(playGamesManager) {
    playGamesManager?.state ?: MutableStateFlow(PlayGamesState(PlayGamesConnection.DISABLED))
  }
  val playGamesState by playGamesStateFlow.collectAsStateWithLifecycle()
  DisposableEffect(billingManager) { onDispose { billingManager.close() } }
  LaunchedEffect(billingManager) { billingManager.prepare() }
  LaunchedEffect(playGamesManager) { playGamesManager?.prepare() }
  LaunchedEffect(championUnlockRequested) {
    if (championUnlockRequested && PiyoAccessory.CHAMPION_TROPHY !in preferences.unlockedPiyoAccessories) {
      onPreferencesChange(
        preferences.copy(unlockedPiyoAccessories = preferences.unlockedPiyoAccessories + PiyoAccessory.CHAMPION_TROPHY),
      )
    }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    scope.launch {
      billingManager.onForeground()
      playGamesManager?.prepare()
    }
  }
  var snapshot by remember { mutableStateOf<DeckLibrarySnapshot?>(null) }
  var learning by remember { mutableStateOf<LearningSnapshot?>(null) }
  var loadFailed by remember { mutableStateOf(false) }
  var operationFailed by remember { mutableStateOf(false) }
  var pendingCompletion by remember { mutableStateOf<PendingPracticeCompletion?>(null) }
  var pendingCheckpointSave by remember { mutableStateOf<Pair<String, PracticeSessionCheckpoint>?>(null) }
  var filters by remember { mutableStateOf(DeckFilters()) }
  var tab by remember { mutableStateOf(RootTab.HOME) }
  var detailDeckId by remember { mutableStateOf<String?>(null) }
  var workingDeckId by remember { mutableStateOf<String?>(null) }
  var activePractice by remember { mutableStateOf<ActivePractice?>(null) }
  var result by remember { mutableStateOf<PracticeResult?>(null) }
  var reloadToken by remember { mutableStateOf(0) }
  var gameStage by remember { mutableStateOf(GameStage.HUB) }
  var selectedGameKind by remember { mutableStateOf(GameKind.FLOW) }
  var flowPresets by remember { mutableStateOf<List<Deck>>(emptyList()) }
  var gamePresets by remember { mutableStateOf<Map<GameKind, List<Deck>>>(emptyMap()) }
  var spacingPassages by remember { mutableStateOf<List<SpacingPassage>>(emptyList()) }
  var gameBestScores by remember { mutableStateOf<Map<GameKind, Map<String, Int>>>(emptyMap()) }
  var activeFlowDeck by remember { mutableStateOf<Deck?>(null) }
  var flowResult by remember { mutableStateOf<FlowGameState?>(null) }
  var flowIsNewBest by remember { mutableStateOf(false) }
  var flowSeed by remember { mutableStateOf(0L) }
  var flowIsWeeklyCup by remember { mutableStateOf(false) }
  var flowRankTuning by remember { mutableStateOf(FlowRankTuning()) }
  var activeGameDeck by remember { mutableStateOf<Deck?>(null) }
  var typingGameResult by remember { mutableStateOf<TypingGameState?>(null) }
  var acidRainResult by remember { mutableStateOf<AcidRainState?>(null) }
  var activeSpacingPassage by remember { mutableStateOf<SpacingPassage?>(null) }
  var spacingGameResult by remember { mutableStateOf<SpacingGameState?>(null) }
  var gameSeed by remember { mutableStateOf(0L) }
  var showFreePractice by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  var appTourStep by remember { mutableStateOf(0) }
  val reminderScheduler = remember { DailyReminderScheduler(context) }
  var pendingReminderEnable by remember { mutableStateOf(false) }
  val documentGateway = remember { PiyoDeckDocumentGateway(context) }
  val feedbackController = remember(context) { ContentFeedbackController(context, BuildConfig.SUPPORT_URL) }
  var userDeckImport by remember { mutableStateOf<UserDeckImportUiState?>(null) }
  var pendingUserDeckExport by remember { mutableStateOf<PendingUserDeckExport?>(null) }
  var storedDeckDraft by remember { mutableStateOf<ActiveUserDeckDraft?>(null) }
  var editorDraft by remember { mutableStateOf<ActiveUserDeckDraft?>(null) }
  var editorWorking by remember { mutableStateOf(false) }
  var editorSaveError by remember { mutableStateOf(false) }
  var showDeckMakerPaywall by remember { mutableStateOf(false) }
  var pendingPaidDraft by remember { mutableStateOf<UserDeckDraft?>(null) }
  var pendingPaidImport by remember { mutableStateOf<UserDeckImportUiState.Ready?>(null) }
  var pendingPaidImportCopy by remember { mutableStateOf<UserDeckImportUiState.Ready?>(null) }
  var pendingDifferentDraft by remember { mutableStateOf<UserDeckDraft?>(null) }
  var staleEditDraft by remember { mutableStateOf<ActiveUserDeckDraft?>(null) }

  LaunchedEffect(tab) {
    TelemetryRuntime.capture(
      AnalyticsEvent.FEATURE_VIEWED,
      mapOf(AnalyticsProperty.FEATURE to tab.analyticsFeature),
    )
    TelemetryRuntime.setCrashContext(tab.analyticsFeature)
  }
  LaunchedEffect(activePractice?.sessionId) {
    activePractice?.let { practice ->
      TelemetryRuntime.capture(
        AnalyticsEvent.SESSION_STARTED,
        mapOf(
          AnalyticsProperty.SESSION_KIND to practice.kind.analyticsSessionKind,
          AnalyticsProperty.DECK_SOURCE to practice.analyticsDeckSource,
          AnalyticsProperty.INPUT_MODE to practice.inputMode.analyticsValue,
        ),
      )
      TelemetryRuntime.setCrashContext(
        feature = "practice",
        sessionKind = practice.kind.analyticsSessionKind,
        inputMode = practice.inputMode.analyticsValue,
      )
    }
  }
  LaunchedEffect(activeFlowDeck?.deckId, flowSeed) {
    activeFlowDeck?.let { deck ->
      TelemetryRuntime.capture(
        AnalyticsEvent.SESSION_STARTED,
        mapOf(
          AnalyticsProperty.SESSION_KIND to "game",
          AnalyticsProperty.DECK_SOURCE to analyticsGameDeckSource(deck),
          AnalyticsProperty.INPUT_MODE to preferences.defaultInputMode.analyticsValue,
          AnalyticsProperty.GAME_MODE to "flow",
          AnalyticsProperty.DIFFICULTY to analyticsDifficulty(deck.deckId),
        ),
      )
      TelemetryRuntime.setCrashContext(
        feature = "game",
        sessionKind = "game",
        inputMode = preferences.defaultInputMode.analyticsValue,
        gameMode = "flow",
      )
    }
  }
  LaunchedEffect(activeGameDeck?.deckId, gameSeed) {
    activeGameDeck?.let { deck ->
      TelemetryRuntime.capture(
        AnalyticsEvent.SESSION_STARTED,
        mapOf(
          AnalyticsProperty.SESSION_KIND to "game",
          AnalyticsProperty.DECK_SOURCE to analyticsGameDeckSource(deck),
          AnalyticsProperty.INPUT_MODE to preferences.defaultInputMode.analyticsValue,
          AnalyticsProperty.GAME_MODE to selectedGameKind.analyticsValue,
          AnalyticsProperty.DIFFICULTY to analyticsDifficulty(deck.deckId),
        ),
      )
      TelemetryRuntime.setCrashContext(
        feature = "game",
        sessionKind = "game",
        inputMode = preferences.defaultInputMode.analyticsValue,
        gameMode = selectedGameKind.analyticsValue,
      )
    }
  }
  LaunchedEffect(activeSpacingPassage?.id, spacingGameResult) {
    if (activeSpacingPassage != null && spacingGameResult == null) {
      TelemetryRuntime.capture(
        AnalyticsEvent.SESSION_STARTED,
        mapOf(
          AnalyticsProperty.SESSION_KIND to "game",
          AnalyticsProperty.DECK_SOURCE to "bundled",
          AnalyticsProperty.INPUT_MODE to "not_applicable",
          AnalyticsProperty.GAME_MODE to "spacing",
          AnalyticsProperty.DIFFICULTY to "custom",
        ),
      )
      TelemetryRuntime.setCrashContext(
        feature = "game",
        sessionKind = "game",
        inputMode = "not_applicable",
        gameMode = "spacing",
      )
    }
  }
  LaunchedEffect(flowResult, typingGameResult, acidRainResult, spacingGameResult) {
    val game = when {
      flowResult != null -> "flow" to (flowResult?.score ?: 0)
      typingGameResult != null -> selectedGameKind.analyticsValue to (typingGameResult?.score ?: 0)
      acidRainResult != null -> "acid_rain" to (acidRainResult?.score ?: 0)
      spacingGameResult?.result != null -> "spacing" to (spacingGameResult?.result?.score ?: 0)
      else -> null
    }
    game?.let { (mode, score) ->
      TelemetryRuntime.capture(
        AnalyticsEvent.GAME_RESULT,
        mapOf(
          AnalyticsProperty.GAME_MODE to mode,
          AnalyticsProperty.DIFFICULTY to analyticsDifficulty(
            activeFlowDeck?.deckId ?: activeGameDeck?.deckId ?: "custom",
          ),
          AnalyticsProperty.RESULT to "completed",
          AnalyticsProperty.SCORE_BUCKET to TelemetryRuntime.scoreBucket(score),
          AnalyticsProperty.INPUT_MODE to if (mode == "spacing") "not_applicable" else preferences.defaultInputMode.analyticsValue,
          AnalyticsProperty.DECK_SOURCE to if (mode == "spacing") "bundled" else analyticsGameDeckSource(activeFlowDeck ?: activeGameDeck),
        ),
      )
    }
  }

  fun beginUserDeckImport(uri: Uri, sourceContext: String) {
    val previousDocument = when (val state = userDeckImport) {
      is UserDeckImportUiState.Loading -> state.document
      is UserDeckImportUiState.Ready -> state.document
      is UserDeckImportUiState.Failed -> state.document
      null -> null
    }
    documentGateway.discard(previousDocument)
    userDeckImport = UserDeckImportUiState.Loading()
    scope.launch {
      var staged: StagedPiyoDeckDocument? = null
      try {
        staged = documentGateway.stage(uri, sourceContext)
        userDeckImport = UserDeckImportUiState.Loading(staged)
        val preview = repository.previewImportedDeck(staged.file, staged.displayName)
        userDeckImport = UserDeckImportUiState.Ready(staged, preview)
      } catch (error: Exception) {
        documentGateway.discard(staged)
        userDeckImport = UserDeckImportUiState.Failed(error.toUserDeckImportError())
      }
    }
  }

  val openUserDeck = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { beginUserDeckImport(it, "picker") }
  }
  val createUserDeck = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument(PiyoDeckDocumentGateway.MIME_TYPE),
  ) { uri ->
    val request = pendingUserDeckExport
    if (uri == null || request == null) {
      pendingUserDeckExport = null
    } else {
      scope.launch {
        try {
          documentGateway.writeExport(uri, repository.exportUserDeck(request.deckId))
          if (request.deleteAfterExport) repository.delete(request.deckId)
          reloadToken += 1
        } catch (_: Exception) {
          operationFailed = true
        } finally {
          pendingUserDeckExport = null
        }
      }
    }
  }

  fun exportUserDeck(deck: InstalledDeck, deleteAfterExport: Boolean = false) {
    pendingUserDeckExport = PendingUserDeckExport(deck.metadata.deckId, deleteAfterExport)
    val safeName = (deck.deck.localizedName(preferences.language.tag) ?: "piyokey-deck")
      .replace(Regex("[\\\\/:*?\"<>|]"), "-")
      .take(80)
    createUserDeck.launch("$safeName.typedeck")
  }

  LaunchedEffect(incomingDocument?.token) {
    val incoming = incomingDocument ?: return@LaunchedEffect
    beginUserDeckImport(incoming.uri, incoming.sourceContext)
    onIncomingDocumentConsumed(incoming.token)
  }

  LaunchedEffect(documentGateway, incomingDocument?.token) {
    if (incomingDocument == null && userDeckImport == null) {
      documentGateway.recoverPending()?.let { recovered ->
        userDeckImport = UserDeckImportUiState.Loading(recovered)
        userDeckImport = try {
          UserDeckImportUiState.Ready(
            recovered,
            repository.previewImportedDeck(recovered.file, recovered.displayName),
          )
        } catch (error: Exception) {
          documentGateway.discard(recovered)
          UserDeckImportUiState.Failed(error.toUserDeckImportError())
        }
      }
    }
  }

  LaunchedEffect(preferences.onboardingMigrationChecked) {
    if (!preferences.onboardingMigrationChecked) {
      onPreferencesChange(
        if (hadExistingDatabase) {
          preferences.copy(
            firstInputCompleted = true,
            hatchHandoffCompleted = true,
            hatchChaptersCompleted = 3,
            appTourCompleted = true,
            onboardingMigrationChecked = true,
          )
        } else {
          preferences.copy(onboardingMigrationChecked = true)
        },
      )
    }
  }

  LaunchedEffect(repository, reloadToken, preferences.defaultInputMode) {
    loadFailed = false
    try {
      val loadedSnapshot = repository.snapshot()
      val loadedLearning = repository.learningSnapshot()
      val loadedPresets = repository.bundledFlowDecks()
      val loadedGamePresets = mapOf(
        GameKind.FLOW to loadedPresets,
        GameKind.ACID_RAIN to repository.bundledGameDecks("acid_rain"),
        GameKind.CHOSEONG to repository.bundledGameDecks("choseong"),
        GameKind.WORD_MATCH to repository.bundledGameDecks("word_match"),
        GameKind.DICTATION to repository.bundledGameDecks("dictation"),
      )
      val loadedSpacingPassages = repository.bundledSpacingPassages()
      val loadedRankTuning = repository.flowRankTuning()
      val loadedInputMode = if (preferences.defaultInputMode == InputMode.OS_IME) "os_ime" else "builtin"
      val installedDecks = loadedSnapshot.installed.map { it.deck }
      val loadedBestScores = loadedGamePresets.mapValues { (kind, presets) ->
        val mode = when (kind) {
          GameKind.FLOW -> "flow"
          GameKind.ACID_RAIN -> "acid_rain"
          GameKind.CHOSEONG -> "choseong"
          GameKind.WORD_MATCH -> "word_match"
          GameKind.DICTATION -> "dictation"
          GameKind.SPACING -> "spacing"
        }
        (presets + installedDecks).distinctBy(Deck::deckId).mapNotNull { deck ->
          repository.gameProgress(mode, deck.deckId, loadedInputMode)?.let { deck.deckId to it.bestScore }
        }.toMap()
      }
      snapshot = loadedSnapshot
      learning = loadedLearning
      storedDeckDraft = repository.loadUserDeckDraft()
      flowPresets = loadedPresets
      gamePresets = loadedGamePresets
      spacingPassages = loadedSpacingPassages
      flowRankTuning = loadedRankTuning
      gameBestScores = loadedBestScores
      if (reloadToken == 0) {
        when (repository.refreshCatalog()) {
          is CatalogRefreshResult.Updated -> snapshot = repository.snapshot()
          else -> Unit
        }
      }
      if (loadedLearning.reminder.isEnabled) {
        reminderScheduler.schedule(loadedLearning.reminder.hour, loadedLearning.reminder.minute)
      }
    } catch (_: Exception) {
      loadFailed = true
    }
  }

  fun finishOnboardingReminderChoice(enabled: Boolean) {
    if (!enabled) {
      onPreferencesChange(preferences.copy(hatchHandoffCompleted = true))
      return
    }
    scope.launch {
      try {
        reminderScheduler.schedule(
          DailyReminderDefaults.LOCAL_WORKDAY_END_HOUR,
          DailyReminderDefaults.MINUTE,
        )
        repository.saveReminderPreference(
          true,
          DailyReminderDefaults.LOCAL_WORKDAY_END_HOUR,
          DailyReminderDefaults.MINUTE,
        )
      } catch (_: Exception) {
        reminderScheduler.cancel()
        operationFailed = true
      } finally {
        onPreferencesChange(preferences.copy(hatchHandoffCompleted = true))
      }
    }
  }

  val onboardingNotificationPermission = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    finishOnboardingReminderChoice(granted)
  }

  if (!preferences.onboardingMigrationChecked) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }

  if (!preferences.firstInputCompleted || !preferences.hatchHandoffCompleted) {
    OnboardingRoute(
      preferences = preferences,
      onUpdate = onPreferencesChange,
      onOpenIMEHelp = {
        hostContext.startActivity(
          Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
      onEnableReminder = {
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          finishOnboardingReminderChoice(true)
        } else {
          onboardingNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      },
      onSkipReminder = { finishOnboardingReminderChoice(false) },
    )
    return
  }

  val current = snapshot
  val currentLearning = learning
  if (current == null || currentLearning == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (loadFailed) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(app.piyokey.feature.discover.R.string.error_generic))
          Button(onClick = { reloadToken += 1 }) { Text(stringResource(R.string.retry_load)) }
        }
      } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          CircularProgressIndicator()
          Text(stringResource(R.string.loading_content))
        }
      }
    }
    return
  }

  LaunchedEffect(currentLearning.unlockedRewards, preferences.unlockedPiyoAccessories) {
    val unlocked = PiyoWardrobePolicy.unlockedAfterStreakRewards(
      preferences.unlockedPiyoAccessories,
      currentLearning.unlockedRewards,
    )
    if (unlocked != preferences.unlockedPiyoAccessories) {
      onPreferencesChange(preferences.copy(unlockedPiyoAccessories = unlocked))
    }
  }

  fun unlockTopikReward(deck: Deck, score: Int) {
    val unlocked = PiyoWardrobePolicy.unlockedAfterTopikGame(
      preferences.unlockedPiyoAccessories,
      deck.tags.toSet(),
      score,
    )
    if (unlocked != preferences.unlockedPiyoAccessories) {
      onPreferencesChange(preferences.copy(unlockedPiyoAccessories = unlocked))
    }
  }

  fun reload() {
    reloadToken += 1
  }

  fun persistAndOpenDeckDraft(draft: UserDeckDraft) {
    scope.launch {
      try {
        val active = repository.saveUserDeckDraft(draft)
        storedDeckDraft = active
        editorDraft = active
        editorSaveError = false
      } catch (_: Exception) {
        operationFailed = true
      }
    }
  }

  fun requestDeckMakerDraft(draft: UserDeckDraft) {
    if (!billingState.hasAccess) {
      pendingPaidDraft = draft
      showDeckMakerPaywall = true
      return
    }
    val existing = storedDeckDraft
    when {
      existing == null -> persistAndOpenDeckDraft(draft)
      existing.draft.isSameDeckMakerFlow(draft) -> editorDraft = existing
      else -> pendingDifferentDraft = draft
    }
  }

  fun saveEditorDraft(draft: UserDeckDraft) {
    scope.launch {
      try {
        val saved = repository.saveUserDeckDraft(draft)
        storedDeckDraft = saved
        if (editorDraft?.draftId == saved.draftId) editorDraft = saved
        editorSaveError = false
      } catch (_: Exception) {
        editorSaveError = true
      }
    }
  }

  fun commitEditorDraft(draft: UserDeckDraft, language: UserDeckLanguage) {
    editorWorking = true
    editorSaveError = false
    scope.launch {
      try {
        billingManager.requireAccess()
        val active = repository.saveUserDeckDraft(draft)
        storedDeckDraft = active
        repository.commitUserDeckDraft(active, language)
        storedDeckDraft = null
        editorDraft = null
        tab = RootTab.PROFILE
        reload()
      } catch (error: UserDeckEditCommitException.SourceChanged) {
        staleEditDraft = repository.loadUserDeckDraft()
        editorSaveError = true
      } catch (_: Exception) {
        editorSaveError = true
        if (!billingState.hasAccess) {
          pendingPaidDraft = draft
          showDeckMakerPaywall = true
        }
      } finally {
        editorWorking = false
      }
    }
  }

  fun openDetail(deck: CatalogDeck) {
    detailDeckId = deck.deckId
    activePractice = null
    result = null
  }

  fun play(installed: InstalledDeck) {
    val entry = current.catalog.decks.firstOrNull { it.deckId == installed.metadata.deckId }
    detailDeckId = null
    result = null
    activePractice = ActivePractice(
      installed = installed,
      catalogEntry = entry,
      targets = installed.deck.items.map { it.ko },
      items = installed.deck.items,
      sourceDeckId = installed.deck.deckId,
      kind = PracticeKind.DECK,
      inputMode = preferences.defaultInputMode,
      sessionDay = JstDay.fromEpochMillis(System.currentTimeMillis()),
    )
    scope.launch { repository.markPlayed(installed.metadata.deckId) }
  }

  fun persistPracticeCompletion(completion: PendingPracticeCompletion) {
    scope.launch {
      val practice = completion.practice
      val state = completion.state
      val duration = completion.activeDurationMillis.coerceAtLeast(1)
      val cpm = state.acceptedJamoCount.toDouble() / duration * 60_000.0
      try {
        val items = practice.items
        if (items != null) {
          if (practice.reviewSourceDeckIds != null) {
            state.itemResolutions.forEach { resolution ->
              val item = items.getOrNull(resolution.itemIndex) ?: return@forEach
              val source = practice.reviewSourceDeckIds.getOrNull(resolution.itemIndex) ?: return@forEach
              repository.recordPracticeReview(
                sourceDeckId = source,
                items = listOf(item),
                resolutions = listOf(resolution.copy(itemIndex = 0)),
                isReviewSession = practice.kind == PracticeKind.REVIEW,
              )
            }
          } else if (practice.sourceDeckId != null) {
            repository.recordPracticeReview(
              sourceDeckId = practice.sourceDeckId,
              items = items,
              resolutions = state.itemResolutions,
              isReviewSession = false,
            )
          }
        }
        val stars = when (practice.kind) {
          PracticeKind.CURRICULUM, PracticeKind.ONBOARDING -> repository.finishCurriculumStage(
            stageId = requireNotNull(practice.stageId),
            accuracyPercent = state.accuracyPercent,
            charactersPerMinute = cpm,
            sessionDay = practice.sessionDay,
          )
          PracticeKind.DAILY -> {
            repository.recordDailyCompletion(practice.sessionDay)
            null
          }
          PracticeKind.QUICK -> {
            repository.recordQuickPracticeCompletion(practice.sessionDay)
            null
          }
          else -> null
        }
        repository.recordPracticeAcceptedJamo(practice.sessionId, state.acceptedJamoCount)
        TelemetryRuntime.capture(
          AnalyticsEvent.SESSION_COMPLETED,
          mapOf(
            AnalyticsProperty.SESSION_KIND to practice.kind.analyticsSessionKind,
            AnalyticsProperty.RESULT to "completed",
            AnalyticsProperty.DURATION_BUCKET to TelemetryRuntime.durationBucket(duration),
            AnalyticsProperty.ITEM_COUNT_BUCKET to TelemetryRuntime.itemCountBucket(state.itemResolutions.size),
            AnalyticsProperty.DECK_SOURCE to practice.analyticsDeckSource,
            AnalyticsProperty.INPUT_MODE to practice.inputMode.analyticsValue,
          ),
        )
        playGamesManager?.syncAfterLocalSave()
        pendingCompletion = null
        if (pendingCheckpointSave?.first == practice.stageId) pendingCheckpointSave = null
        operationFailed = false
        if (practice.kind == PracticeKind.ONBOARDING) {
          val chapter = requireNotNull(practice.stageId).let { id ->
            app.piyokey.core.retention.CurriculumCatalog.stage(id)?.chapterNumber
          } ?: error("Missing onboarding chapter")
          onPreferencesChange(preferences.copy(pendingHatchResultChapter = chapter))
        }
        result = state.toResult(practice, duration, stars)
        activePractice = null
        reload()
      } catch (_: Exception) {
        pendingCompletion = completion
        operationFailed = true
        result = state.toResult(practice, duration, null)
        activePractice = null
      }
    }
  }

  fun startQuickPractice() {
    scope.launch {
      try {
        val fallbackId = when (preferences.onboardingGoal) {
          app.piyokey.core.settings.OnboardingGoal.KEYBOARD -> "official_keyboard_start"
          app.piyokey.core.settings.OnboardingGoal.TRAVEL -> "official_daily_words"
          app.piyokey.core.settings.OnboardingGoal.TRENDS -> "official_trending_korean"
          app.piyokey.core.settings.OnboardingGoal.TOPIK, null -> "official_topik_one"
        }
        val fallbackDecks = buildList {
          current.catalog.decks.firstOrNull { it.deckId == fallbackId }?.let { entry ->
            runCatching { repository.bundledCatalogDeck(entry) }.getOrNull()?.let(::add)
          }
          flowPresets.firstOrNull { it.deckId == "flow_topik_beginner" }?.let { flowDeck ->
            if (none { it.deckId == flowDeck.deckId }) add(flowDeck)
          }
        }
        val session = QuickPracticePolicy.makeSession(
          installedDecks = current.installed.map { it.deck },
          fallbackDecks = fallbackDecks,
          preferredTags = preferences.onboardingGoal?.preferredTags.orEmpty(),
          recentWordKeys = preferences.recentQuickPracticeWords,
        ) ?: error("No eligible quick-practice words")
        onPreferencesChange(
          preferences.copy(
            recentQuickPracticeWords = QuickPracticePolicy.updatedHistory(
              preferences.recentQuickPracticeWords,
              session,
            ),
          ),
        )
        result = null
        activePractice = session.toActivePractice(preferences.defaultInputMode)
        operationFailed = false
      } catch (_: Exception) {
        operationFailed = true
      }
    }
  }

  fun startWeeklyCup() {
    scope.launch {
      try {
        val deck = flowPresets.firstOrNull { it.deckId == "flow_topik_beginner" }
          ?: repository.bundledFlowDecks().first { it.deckId == "flow_topik_beginner" }.also { loaded ->
            flowPresets = (flowPresets + loaded).distinctBy(Deck::deckId)
          }
        selectedGameKind = GameKind.FLOW
        flowIsWeeklyCup = true
        activeFlowDeck = deck
        flowSeed = kotlin.random.Random.nextLong()
        operationFailed = false
      } catch (_: Exception) {
        operationFailed = true
      }
    }
  }

  fun applyReminderEnabled(enabled: Boolean) {
    scope.launch {
      try {
        if (enabled) {
          val preference = requireNotNull(learning).reminder
          reminderScheduler.schedule(preference.hour, preference.minute)
          repository.saveReminderPreference(true, preference.hour, preference.minute)
        } else {
          reminderScheduler.cancel()
          val preference = requireNotNull(learning).reminder
          repository.saveReminderPreference(false, preference.hour, preference.minute)
        }
        reload()
      } catch (_: Exception) {
        operationFailed = true
      }
    }
  }

  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (pendingReminderEnable) {
      pendingReminderEnable = false
      if (granted) applyReminderEnabled(true)
    }
  }

  BackHandler(enabled = detailDeckId != null || activePractice != null || result != null) {
    when {
      result?.practice?.kind == PracticeKind.ONBOARDING -> Unit
      activePractice?.kind == PracticeKind.ONBOARDING -> Unit
      result != null -> result = null
      activePractice != null -> activePractice = null
      else -> detailDeckId = null
    }
  }

  if (!preferences.hatchGateComplete && activePractice == null && result == null) {
    val pendingChapter = preferences.pendingHatchResultChapter
    if (pendingChapter > 0) {
      val stage = CurriculumCatalog.chapters[pendingChapter - 1].stages.first()
      val saved = currentLearning.progress[stage.id]
      HatchMissionResultScreen(
        chapter = pendingChapter,
        accuracyPercent = saved?.bestAccuracyPercent ?: 100.0,
        stars = saved?.stars ?: 1,
        initialNickname = preferences.piyoNickname,
        onContinue = { nickname ->
          onPreferencesChange(
            preferences.copy(
              hatchChaptersCompleted = maxOf(preferences.hatchChaptersCompleted, pendingChapter),
              pendingHatchResultChapter = 0,
              piyoNickname = nickname.ifBlank { preferences.piyoNickname },
            ),
          )
          tab = RootTab.HOME
        },
      )
      return
    }
    val chapter = OnboardingPolicy.nextHatchChapter(preferences.hatchChaptersCompleted) ?: 3
    val stage = CurriculumCatalog.chapters[chapter - 1].stages.first()
    HatchMissionGateScreen(
      chapter = chapter,
      completed = preferences.hatchChaptersCompleted,
      onStart = {
        val items = stage.items.map(CurriculumItem::toDeckItem)
        activePractice = ActivePractice(
          installed = null,
          catalogEntry = null,
          targets = items.map(DeckItem::ko),
          items = items,
          sourceDeckId = "curriculum::${stage.id}",
          kind = PracticeKind.ONBOARDING,
          inputMode = preferences.defaultInputMode,
          stageId = stage.id,
          sessionDay = JstDay.fromEpochMillis(System.currentTimeMillis()),
          checkpoint = currentLearning.activeSession?.takeIf { it.stageId == stage.id }?.checkpoint,
        )
      },
    )
    return
  }

  LaunchedEffect(preferences.appTourCompleted, appTourStep) {
    if (!preferences.appTourCompleted) {
      tab = when (appTourStep) {
        0 -> RootTab.HOME
        1 -> RootTab.DISCOVER
        2 -> RootTab.PRACTICE
        3 -> RootTab.GAMES
        else -> RootTab.PROFILE
      }
      showSettings = false
    }
  }

  val inputModeKey = if (preferences.defaultInputMode == InputMode.OS_IME) "os_ime" else "builtin"
  val currentStreak = RetentionPolicy.streak(
    currentLearning.completedDays,
    JstDay.fromEpochMillis(System.currentTimeMillis()),
  ).current
  fun shareModel(title: String, levelOrDeck: String, score: Int, combo: Int): ResultShareModel = ResultShareModel(
    language = preferences.language,
    title = title,
    levelOrDeck = levelOrDeck,
    score = score,
    maxCombo = combo,
    streak = currentStreak,
    scoreLabel = context.getString(app.piyokey.feature.game.R.string.share_score),
    comboLabel = context.getString(app.piyokey.feature.game.R.string.share_combo),
    streakLabel = context.getString(app.piyokey.feature.game.R.string.share_streak),
    downloadPrompt = context.getString(app.piyokey.feature.game.R.string.share_download),
    caption = context.getString(app.piyokey.feature.game.R.string.share_caption, score, title),
  )
  val runningFlow = activeFlowDeck
  val completedFlow = flowResult
  if (runningFlow != null && completedFlow == null) {
    FlowGameRoute(
      deck = runningFlow,
      useOSIME = (if (flowIsWeeklyCup) {
        InputModePolicy.weeklyCup(preferences.defaultInputMode)
      } else {
        preferences.defaultInputMode
      }) == InputMode.OS_IME,
      piyoAppearance = preferences.sessionAppearance,
      soundEffectsEnabled = preferences.soundEffectsEnabled,
      keySoundStyle = preferences.keySoundStyle,
      hapticsEnabled = preferences.hapticsEnabled,
      seed = flowSeed,
      onClose = { activeFlowDeck = null },
      onFinished = { finished ->
        unlockTopikReward(runningFlow, finished.score)
        scope.launch {
          flowIsNewBest = try {
            val saved = repository.saveFlowRecord(
              record = finished.toRecord(System.currentTimeMillis()),
              deckItems = finished.cards,
              reviewMistakeCounts = finished.reviewMistakeCounts,
              inputMode = inputModeKey,
              isWeeklyCup = flowIsWeeklyCup,
            )
            if (!flowIsWeeklyCup) {
              gameBestScores = gameBestScores + (
                GameKind.FLOW to (gameBestScores[GameKind.FLOW].orEmpty() + (runningFlow.deckId to saved.progress.bestScore))
              )
            }
            playGamesManager?.syncAfterLocalSave()
            saved.isNewBest
          } catch (_: Exception) {
            operationFailed = true
            false
          }
          flowResult = finished
          reload()
        }
      },
    )
    return
  }
  if (completedFlow != null) {
    val leaderboard = runningFlow?.let { deck ->
      PlayGamesPolicy.leaderboard(
        PlayGamesResultIdentity("flow", deck.deckId, inputModeKey, isWeeklyCup = flowIsWeeklyCup),
      )
    }?.takeIf { playGamesState.connection != PlayGamesConnection.DISABLED }
    FlowResultScreen(
      state = completedFlow,
      rank = flowRankTuning.rank(completedFlow.accuracyPercent, completedFlow.charactersPerMinute),
      isNewBest = flowIsNewBest,
      shareModel = shareModel(
        context.getString(app.piyokey.feature.game.R.string.flow_title),
        runningFlow?.localizedName(preferences.language.tag) ?: completedFlow.deckId,
        completedFlow.score,
        completedFlow.maxCombo,
      ),
      onPlayGamesLeaderboard = leaderboard?.let { key ->
        { scope.launch { playGamesManager?.openLeaderboard(key) } }
      },
      onRetry = {
        flowResult = null
        flowSeed = kotlin.random.Random.nextLong()
      },
      onDone = {
        flowResult = null
        activeFlowDeck = null
        flowIsWeeklyCup = false
        gameStage = GameStage.DECK_SELECT
      },
    )
    return
  }

  val runningGameDeck = activeGameDeck
  if (runningGameDeck != null && typingGameResult == null && acidRainResult == null) {
    if (selectedGameKind == GameKind.ACID_RAIN) {
      AcidRainRoute(
        deck = runningGameDeck,
        useOSIME = preferences.defaultInputMode == InputMode.OS_IME,
        piyoAppearance = preferences.sessionAppearance,
        soundEffectsEnabled = preferences.soundEffectsEnabled,
        keySoundStyle = preferences.keySoundStyle,
        hapticsEnabled = preferences.hapticsEnabled,
        seed = gameSeed,
        onClose = { activeGameDeck = null },
        onFinished = { finished ->
          unlockTopikReward(runningGameDeck, finished.score)
          scope.launch {
            try {
              val saved = repository.saveGameRecord(
                finished.toRecord(inputModeKey, System.currentTimeMillis()),
                runningGameDeck.items,
                finished.reviewMistakeCounts,
              )
              gameBestScores = gameBestScores + (
                GameKind.ACID_RAIN to (gameBestScores[GameKind.ACID_RAIN].orEmpty() + (runningGameDeck.deckId to saved.progress.bestScore))
              )
              playGamesManager?.syncAfterLocalSave()
            } catch (_: Exception) { operationFailed = true }
            acidRainResult = finished
            reload()
          }
        },
      )
    } else {
      TypingGameRoute(
        kind = selectedGameKind,
        deck = runningGameDeck,
        useOSIME = preferences.defaultInputMode == InputMode.OS_IME,
        showChoseongMeaning = preferences.choseongShowsMeaning,
        piyoAppearance = preferences.sessionAppearance,
        soundEffectsEnabled = preferences.soundEffectsEnabled,
        keySoundStyle = preferences.keySoundStyle,
        hapticsEnabled = preferences.hapticsEnabled,
        seed = gameSeed,
        onClose = { activeGameDeck = null },
        onFinished = { finished ->
          unlockTopikReward(runningGameDeck, finished.score)
          scope.launch {
            try {
              val saved = repository.saveGameRecord(
                finished.toRecord(runningGameDeck.deckId, inputModeKey, System.currentTimeMillis()),
                runningGameDeck.items,
                finished.reviewMistakeCounts,
              )
              gameBestScores = gameBestScores + (
                selectedGameKind to (gameBestScores[selectedGameKind].orEmpty() + (runningGameDeck.deckId to saved.progress.bestScore))
              )
              playGamesManager?.syncAfterLocalSave()
            } catch (_: Exception) { operationFailed = true }
            typingGameResult = finished
            reload()
          }
        },
      )
    }
    return
  }
  typingGameResult?.let { completed ->
    val gameTitle = context.getString(
      when (selectedGameKind) {
        GameKind.CHOSEONG -> app.piyokey.feature.game.R.string.choseong_title
        GameKind.WORD_MATCH -> app.piyokey.feature.game.R.string.word_match_title
        GameKind.DICTATION -> app.piyokey.feature.game.R.string.dictation_title
        else -> app.piyokey.feature.game.R.string.games_title
      },
    )
    val leaderboard = runningGameDeck?.let { deck ->
      PlayGamesPolicy.leaderboard(
        PlayGamesResultIdentity(completed.mode.name.lowercase(), deck.deckId, inputModeKey),
      )
    }?.takeIf { playGamesState.connection != PlayGamesConnection.DISABLED }
    GenericGameResultScreen(
      score = completed.score,
      accuracy = completed.accuracyPercent,
      maxCombo = completed.maxCombo,
      completed = completed.completedItemCount,
      detailLabel = stringResource(app.piyokey.feature.game.R.string.questions_per_minute),
      detailValue = "%.1f".format(completed.questionsPerMinute),
      reviewWords = runningGameDeck?.items.orEmpty().filter { it.id in completed.reviewMistakeCounts }.map { it.ko },
      inputModeName = stringResource(
        if (preferences.defaultInputMode == InputMode.OS_IME) app.piyokey.feature.game.R.string.os_ime_input
        else app.piyokey.feature.game.R.string.builtin_input,
      ),
      shareModel = shareModel(
        gameTitle,
        runningGameDeck?.localizedName(preferences.language.tag) ?: completed.mode.name,
        completed.score,
        completed.maxCombo,
      ),
      onPlayGamesLeaderboard = leaderboard?.let { key ->
        { scope.launch { playGamesManager?.openLeaderboard(key) } }
      },
      onRetry = { typingGameResult = null; gameSeed = kotlin.random.Random.nextLong() },
      onDone = { typingGameResult = null; activeGameDeck = null; gameStage = GameStage.DECK_SELECT },
    )
    return
  }
  acidRainResult?.let { completed ->
    val leaderboard = runningGameDeck?.let { deck ->
      PlayGamesPolicy.leaderboard(PlayGamesResultIdentity("acid_rain", deck.deckId, inputModeKey))
    }?.takeIf { playGamesState.connection != PlayGamesConnection.DISABLED }
    GenericGameResultScreen(
      score = completed.score,
      accuracy = completed.accuracyPercent,
      maxCombo = completed.maxCombo,
      completed = completed.completedItemCount,
      reviewWords = runningGameDeck?.items.orEmpty().filter { it.id in completed.reviewMistakeCounts }.map { it.ko },
      inputModeName = stringResource(
        if (preferences.defaultInputMode == InputMode.OS_IME) app.piyokey.feature.game.R.string.os_ime_input
        else app.piyokey.feature.game.R.string.builtin_input,
      ),
      shareModel = shareModel(
        context.getString(app.piyokey.feature.game.R.string.acid_rain_title),
        runningGameDeck?.localizedName(preferences.language.tag) ?: completed.deckId,
        completed.score,
        completed.maxCombo,
      ),
      onPlayGamesLeaderboard = leaderboard?.let { key ->
        { scope.launch { playGamesManager?.openLeaderboard(key) } }
      },
      onRetry = { acidRainResult = null; gameSeed = kotlin.random.Random.nextLong() },
      onDone = { acidRainResult = null; activeGameDeck = null; gameStage = GameStage.DECK_SELECT },
    )
    return
  }

  val runningSpacing = activeSpacingPassage
  if (runningSpacing != null && spacingGameResult == null) {
    SpacingGameRoute(
      passage = runningSpacing,
      soundEffectsEnabled = preferences.soundEffectsEnabled,
      keySoundStyle = preferences.keySoundStyle,
      onClose = { activeSpacingPassage = null },
      onFinished = { finished ->
        scope.launch {
          try { repository.saveGameRecord(finished.toRecord(runningSpacing.id, System.currentTimeMillis())) }
          catch (_: Exception) { operationFailed = true }
          spacingGameResult = finished
          reload()
        }
      },
    )
    return
  }
  spacingGameResult?.let { completed ->
    SpacingResultScreen(
      state = completed,
      shareModel = shareModel(
        context.getString(app.piyokey.feature.game.R.string.spacing_title),
        context.getString(app.piyokey.feature.game.R.string.spacing_level, runningSpacing?.level ?: 1),
        requireNotNull(completed.result).score,
        0,
      ),
      onRetry = { spacingGameResult = null },
      onDone = { spacingGameResult = null; activeSpacingPassage = null; gameStage = GameStage.SPACING_SELECT },
    )
    return
  }

  fun closeUserDeckImport() {
    val document = when (val state = userDeckImport) {
      is UserDeckImportUiState.Loading -> state.document
      is UserDeckImportUiState.Ready -> state.document
      is UserDeckImportUiState.Failed -> state.document
      null -> null
    }
    documentGateway.discard(document)
    userDeckImport = null
  }

  fun commitUserDeckImport(
    ready: UserDeckImportUiState.Ready,
    replaceConfirmed: Boolean,
    hasPiyokeyProAccess: Boolean = billingState.hasAccess,
  ) {
    userDeckImport = ready.copy(isWorking = true)
    scope.launch {
      try {
        when (repository.commitImportedDeck(
          stagingFile = ready.document.file,
          expectedContentSha256 = ready.preview.contentSha256,
          replaceConfirmed = replaceConfirmed,
          hasPiyokeyProAccess = hasPiyokeyProAccess,
        )) {
          is ImportedDeckCommitResult.Installed,
          is ImportedDeckCommitResult.AlreadyInstalled,
          -> Unit
        }
        closeUserDeckImport()
        tab = RootTab.PROFILE
        reload()
      } catch (_: ImportedDeckException.FreeUserDeckLimitReached) {
        userDeckImport = ready
        pendingPaidImport = ready
        showDeckMakerPaywall = true
      } catch (error: Exception) {
        documentGateway.discard(ready.document)
        userDeckImport = UserDeckImportUiState.Failed(error.toUserDeckImportError())
      }
    }
  }

  fun commitUserDeckImportAsCopy(ready: UserDeckImportUiState.Ready) {
    userDeckImport = ready.copy(isWorking = true)
    scope.launch {
      try {
        billingManager.requireAccess()
        repository.commitImportedDeckAsSeparateCopy(
          stagingFile = ready.document.file,
          expectedContentSha256 = ready.preview.contentSha256,
          language = preferences.language.toUserDeckLanguage(),
        )
        closeUserDeckImport()
        tab = RootTab.PROFILE
        reload()
      } catch (_: DeckMakerAuthorizationException) {
        userDeckImport = ready
        pendingPaidImportCopy = ready
        showDeckMakerPaywall = true
      } catch (error: Exception) {
        documentGateway.discard(ready.document)
        userDeckImport = UserDeckImportUiState.Failed(error.toUserDeckImportError())
      }
    }
  }

  val importState = userDeckImport
  if (importState != null && activePractice == null && result == null && !showDeckMakerPaywall) {
    val ready = importState as? UserDeckImportUiState.Ready
    UserDeckImportScreen(
      preview = ready?.preview,
      error = (importState as? UserDeckImportUiState.Failed)?.error,
      isWorking = ready?.isWorking == true,
      onBack = ::closeUserDeckImport,
      onInstall = { ready?.let { commitUserDeckImport(it, replaceConfirmed = false) } },
      onKeepCurrent = ::closeUserDeckImport,
      onReplace = { ready?.let { commitUserDeckImport(it, replaceConfirmed = true) } },
      onImportAsCopy = {
        ready?.let {
          if (billingState.hasAccess) commitUserDeckImportAsCopy(it) else {
            pendingPaidImportCopy = it
            showDeckMakerPaywall = true
          }
        }
      },
      hasDeckMakerAccess = billingState.hasAccess,
      canInstallNewDeck = billingState.hasAccess || current.installed.count {
        it.metadata.source == "imported" || it.metadata.source == "created"
      } < PiyokeyProPolicy.FREE_INSTALLED_USER_DECK_LIMIT,
      onExportCurrent = { ready?.preview?.installed?.let { exportUserDeck(it) } },
    )
    return
  }

  val replacementDraft = pendingDifferentDraft
  if (replacementDraft != null) {
    AlertDialog(
      onDismissRequest = { pendingDifferentDraft = null },
      title = { Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_existing_title)) },
      text = { Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_existing_body)) },
      confirmButton = {
        TextButton(onClick = {
          pendingDifferentDraft = null
          storedDeckDraft?.let { editorDraft = it }
        }) { Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_resume_draft)) }
      },
      dismissButton = {
        Column(horizontalAlignment = Alignment.End) {
          TextButton(onClick = {
            pendingDifferentDraft = null
            scope.launch {
              try {
                repository.discardUserDeckDraft()
                storedDeckDraft = null
                persistAndOpenDeckDraft(replacementDraft)
              } catch (_: Exception) {
                operationFailed = true
              }
            }
          }) { Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_discard_and_start)) }
          TextButton(onClick = { pendingDifferentDraft = null }) {
            Text(stringResource(app.piyokey.feature.discover.R.string.action_cancel))
          }
        }
      },
    )
  }

  val staleDraft = staleEditDraft
  if (staleDraft != null) {
    AlertDialog(
      onDismissRequest = { staleEditDraft = null },
      title = { Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_source_changed_title)) },
      text = { Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_source_changed_body)) },
      confirmButton = {
        TextButton(onClick = {
          staleEditDraft = null
          val separate = staleDraft.draft.asSeparateCopy(java.time.Instant.now())
          editorWorking = true
          scope.launch {
            try {
              billingManager.requireAccess()
              val active = repository.saveUserDeckDraft(separate)
              repository.commitUserDeckDraft(active, preferences.language.toUserDeckLanguage())
              storedDeckDraft = null
              editorDraft = null
              reload()
            } catch (_: Exception) {
              editorSaveError = true
            } finally {
              editorWorking = false
            }
          }
        }) { Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_save_separate)) }
      },
      dismissButton = {
        TextButton(onClick = { staleEditDraft = null }) {
          Text(stringResource(app.piyokey.feature.discover.R.string.deck_editor_keep_draft))
        }
      },
    )
  }

  if (showDeckMakerPaywall && activePractice == null && result == null) {
    DeckMakerPaywallScreen(
      formattedPrice = billingState.product?.formattedPrice,
      hasAccess = billingState.hasAccess,
      isWorking = billingState.activity != DeckMakerBillingActivity.IDLE,
      notice = billingState.notice?.toDeckMakerUiNotice(),
      onPurchase = {
        hostActivity?.let { activity ->
          scope.launch {
            if (billingManager.purchase(activity)) {
              showDeckMakerPaywall = false
              val paidImport = pendingPaidImport
              pendingPaidImport = null
              val paidImportCopy = pendingPaidImportCopy
              pendingPaidImportCopy = null
              if (paidImport != null) {
                commitUserDeckImport(paidImport, replaceConfirmed = false, hasPiyokeyProAccess = true)
              } else if (paidImportCopy != null) commitUserDeckImportAsCopy(paidImportCopy)
              else pendingPaidDraft?.let(::requestDeckMakerDraft)
              pendingPaidDraft = null
            }
          }
        }
      },
      onRestore = {
        scope.launch {
          if (billingManager.restore()) {
            showDeckMakerPaywall = false
            val paidImport = pendingPaidImport
            pendingPaidImport = null
            val paidImportCopy = pendingPaidImportCopy
            pendingPaidImportCopy = null
            if (paidImport != null) {
              commitUserDeckImport(paidImport, replaceConfirmed = false, hasPiyokeyProAccess = true)
            } else if (paidImportCopy != null) commitUserDeckImportAsCopy(paidImportCopy)
            else pendingPaidDraft?.let(::requestDeckMakerDraft)
            pendingPaidDraft = null
          }
        }
      },
      onDismissNotice = billingManager::dismissNotice,
      onClose = {
        showDeckMakerPaywall = false
        pendingPaidDraft = null
        pendingPaidImport = null
        pendingPaidImportCopy = null
      },
    )
    return
  }

  val currentEditor = editorDraft
  if (currentEditor != null && activePractice == null && result == null) {
    UserDeckEditorScreen(
      initialDraft = currentEditor.draft,
      isWorking = editorWorking,
      saveError = editorSaveError,
      onDraftChanged = ::saveEditorDraft,
      onSave = ::commitEditorDraft,
      onClose = { draft ->
        saveEditorDraft(draft)
        editorDraft = null
      },
    )
    return
  }

  val detail = detailDeckId?.let { id -> current.catalog.decks.firstOrNull { it.deckId == id } }
  when {
    detail != null -> {
      val installed = current.installed.firstOrNull { it.metadata.deckId == detail.deckId }
      DeckDetailScreen(
        deck = detail,
        catalog = current.catalog,
        installedVersion = installed?.metadata?.version,
        isWorking = workingDeckId == detail.deckId,
        onBack = { detailDeckId = null },
        onInstall = {
          workingDeckId = detail.deckId
          scope.launch {
            try {
              repository.install(detail)
              reload()
            } catch (_: Exception) {
              operationFailed = true
            } finally {
              workingDeckId = null
            }
          }
        },
        onPlay = { installed?.let(::play) },
        onReportDeck = {
          feedbackController.open(
            ContentFeedbackRequest(
              kind = ContentFeedbackKind.REPORT,
              source = ContentFeedbackSource.DECK_DETAIL,
              appVersion = BuildConfig.VERSION_NAME,
              appBuild = BuildConfig.VERSION_CODE.toLong(),
              language = preferences.language.tag,
              deck = ContentFeedbackDeck(detail.deckId, detail.version),
            ),
          )
        },
        onDeckClick = ::openDetail,
      )
      return
    }
    activePractice != null -> {
      val practice = requireNotNull(activePractice)
      val curriculumChapter = practice.stageId?.let { CurriculumCatalog.stage(it)?.chapterNumber }
      val inputLocked = practice.kind == PracticeKind.ONBOARDING ||
        (practice.kind == PracticeKind.CURRICULUM && curriculumChapter != null && curriculumChapter <= 4)
      val prompts = practice.items?.map { item ->
        PracticePrompt(
          target = item.ko,
          meaning = item.localizedMeaning(preferences.language.tag),
          reading = item.localizedReading(preferences.language.tag),
          audio = item.audio,
          isBundledFixedContent = practice.installed?.deck?.official ?: true,
        )
      }
      Box(Modifier.fillMaxSize()) {
        PracticeRoute(
          targets = practice.targets,
          prompts = prompts,
          initialCheckpoint = practice.checkpoint,
          inputMode = practice.inputMode,
          inputModeLocked = inputLocked,
          onInputModeChange = { mode ->
            activePractice = practice.copy(inputMode = mode)
            onPreferencesChange(preferences.copy(defaultInputMode = mode))
          },
          onOpenIMEHelp = {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          },
          keyboardOptions = PracticeKeyboardOptions(
            showsKeyGuide = preferences.keyGuideEnabled,
            showsRomanHints = preferences.romanHintsEnabled,
            hapticsEnabled = preferences.hapticsEnabled,
            showsPhysicalKeyboardGuide = preferences.showsPhysicalKeyboardGuide,
          ),
          displayOptions = PracticeDisplayOptions(
            showsTarget = preferences.showsTarget,
            showsMeaning = preferences.showsMeaning,
            showsReading = preferences.showsReading,
            promptOrder = preferences.promptOrder,
            showsJamo = preferences.showsJamo,
            showsComposition = preferences.showsComposition,
            showsMascot = preferences.showsMascot,
          ),
          piyoAppearance = preferences.sessionAppearance,
          soundEffectsEnabled = preferences.soundEffectsEnabled,
          keySoundStyle = preferences.keySoundStyle,
          autoPronounce = preferences.autoPronouncesPractice,
          onCheckpointChanged = { checkpoint ->
            if (practice.kind == PracticeKind.CURRICULUM || practice.kind == PracticeKind.ONBOARDING) {
              scope.launch {
                try {
                  repository.saveCurriculumCheckpoint(requireNotNull(practice.stageId), checkpoint)
                  if (pendingCheckpointSave?.first == practice.stageId) pendingCheckpointSave = null
                } catch (_: Exception) {
                  pendingCheckpointSave = requireNotNull(practice.stageId) to checkpoint
                  operationFailed = true
                }
              }
            }
          },
          onSessionCompleted = { state, duration ->
            persistPracticeCompletion(PendingPracticeCompletion(practice, state, duration))
          },
        )
        if (practice.kind != PracticeKind.ONBOARDING) {
          Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 4.dp),
            color = Color.White.copy(alpha = 0.92f),
          ) {
            TextButton(
              onClick = { activePractice = null },
              modifier = Modifier.testTag("close-practice-session"),
            ) { Text(stringResource(R.string.close_session)) }
          }
        }
      }
      return
    }
    result != null -> {
      val completedResult = requireNotNull(result)
      if (completedResult.practice.kind == PracticeKind.ONBOARDING) {
        val chapter = requireNotNull(completedResult.practice.stageId).let { id ->
          CurriculumCatalog.stage(id)?.chapterNumber
        } ?: error("Missing onboarding chapter")
        HatchMissionResultScreen(
          chapter = chapter,
          accuracyPercent = completedResult.accuracyPercent,
          stars = completedResult.stars ?: 1,
          initialNickname = preferences.piyoNickname,
          onContinue = { nickname ->
            onPreferencesChange(
              preferences.copy(
                hatchChaptersCompleted = maxOf(preferences.hatchChaptersCompleted, chapter),
                pendingHatchResultChapter = 0,
                piyoNickname = nickname.ifBlank { preferences.piyoNickname },
              ),
            )
            result = null
            tab = RootTab.HOME
          },
        )
        return
      }
      val source = completedResult.practice.catalogEntry
      val recommendations = if (source == null) emptyList() else {
        DiscoveryEngine.sameTagRecommendations(current.catalog, source, current.installedDeckIds)
      }
      PracticeResultScreen(
        accuracyPercent = completedResult.accuracyPercent,
        misses = completedResult.misses,
        completed = completedResult.completed,
        charactersPerMinute = completedResult.charactersPerMinute,
        stars = completedResult.stars,
        recommendations = recommendations,
        installedDeckIds = current.installedDeckIds,
        onRetry = {
          if (completedResult.practice.kind == PracticeKind.QUICK) {
            startQuickPractice()
          } else {
            result = null
            activePractice = completedResult.practice
          }
        },
        retryLabel = if (completedResult.practice.kind == PracticeKind.QUICK) {
          context.getString(app.piyokey.feature.retention.R.string.home_quick_random_retry)
        } else {
          null
        },
        onDeckClick = ::openDetail,
        onBack = { result = null },
        shareActions = {
          val title = context.getString(R.string.practice_result_share_title)
          val level = completedResult.practice.installed?.deck?.localizedName(preferences.language.tag)
            ?: completedResult.practice.stageId
            ?: context.getString(R.string.practice_result_share_level)
          ResultShareActions(
            shareModel(
              title,
              level,
              completedResult.accuracyPercent.toInt() * 10,
              0,
            ),
          )
        },
      )
      return
    }
  }

  Scaffold(
    bottomBar = {
      NavigationBar {
        RootTab.entries.forEach { item ->
          NavigationBarItem(
            modifier = Modifier.testTag("nav-${item.name.lowercase(Locale.ROOT)}"),
            selected = tab == item,
            onClick = { tab = item },
            icon = {
              PiyokeyIcon(
                kind = item.icon,
                contentDescription = stringResource(item.label),
                modifier = Modifier.size(24.dp),
                tint = if (tab == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            },
            label = { Text(stringResource(item.label)) },
          )
        }
      }
    },
  ) { padding ->
    Box(Modifier.padding(padding)) {
      when (tab) {
        RootTab.HOME -> RecommendationHome(
          recommendations = DiscoveryEngine.homeRecommendations(
            current.catalog,
            current.installedDeckIds,
            current.downloadHistoryTags,
            preferences.onboardingGoal?.preferredTags.orEmpty(),
          ),
          installedDeckIds = current.installedDeckIds,
          onDeckClick = ::openDetail,
          brandHeader = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Image(
                painter = painterResource(R.drawable.piyokey_logo),
                contentDescription = null,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
              )
              Text(
                stringResource(app.piyokey.feature.discover.R.string.brand_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
              )
            }
          },
          header = {
            RetentionHomeCard(
              today = JstDay.fromEpochMillis(System.currentTimeMillis()),
              completedDays = currentLearning.completedDays,
              appearance = preferences.sessionAppearance,
              onOpenProfile = { tab = RootTab.PROFILE },
              onDailyChallenge = {
                val day = JstDay.fromEpochMillis(System.currentTimeMillis())
                val items = DailyChallengePolicy.items(
                  day,
                  goalSalt = preferences.onboardingGoal?.ordinal ?: 0,
                ).map(CurriculumItem::toDeckItem)
                activePractice = ActivePractice(
                  installed = null,
                  catalogEntry = null,
                  targets = items.map(DeckItem::ko),
                  items = items,
                  sourceDeckId = "daily::${day.value}",
                  kind = PracticeKind.DAILY,
                  inputMode = preferences.defaultInputMode,
                  sessionDay = day,
                )
              },
              onWeeklyCup = ::startWeeklyCup,
              onQuickPractice = ::startQuickPractice,
            )
          },
        )
        RootTab.DISCOVER -> DiscoverScreen(
          catalog = current.catalog,
          installedDeckIds = current.installedDeckIds,
          filters = filters,
          onFiltersChange = { filters = it },
          onDeckClick = ::openDetail,
          onProposeDeck = {
            feedbackController.open(
              ContentFeedbackRequest(
                kind = ContentFeedbackKind.PROPOSAL,
                source = ContentFeedbackSource.DISCOVER,
                appVersion = BuildConfig.VERSION_NAME,
                appBuild = BuildConfig.VERSION_CODE.toLong(),
                language = preferences.language.tag,
              ),
            )
          },
        )
        RootTab.PRACTICE -> if (showFreePractice) {
          PracticeDeckChooser(
            installed = current.installed,
            onPlay = ::play,
            onSample = {
              val targets = listOf(
                context.getString(app.piyokey.feature.practice.R.string.practice_sample_target_1),
                context.getString(app.piyokey.feature.practice.R.string.practice_sample_target_2),
                context.getString(app.piyokey.feature.practice.R.string.practice_sample_target_3),
              )
              activePractice = ActivePractice(
                installed = null,
                catalogEntry = null,
                targets = targets,
                items = null,
                sourceDeckId = null,
                kind = PracticeKind.FREE,
                inputMode = preferences.defaultInputMode,
                sessionDay = JstDay.fromEpochMillis(System.currentTimeMillis()),
              )
            },
            onFindDecks = { tab = RootTab.DISCOVER },
            onBack = { showFreePractice = false },
          )
        } else {
          CurriculumMapScreen(
            progress = currentLearning.progress,
            activeStageId = currentLearning.activeSession?.stageId,
            onStage = { stage ->
              val items = stage.items.map(CurriculumItem::toDeckItem)
              activePractice = ActivePractice(
                installed = null,
                catalogEntry = null,
                targets = items.map(DeckItem::ko),
                items = items,
                sourceDeckId = "curriculum::${stage.id}",
                kind = PracticeKind.CURRICULUM,
                inputMode = OnboardingPolicy.resolvedInputMode(preferences.defaultInputMode, stage.chapterNumber),
                stageId = stage.id,
                sessionDay = JstDay.fromEpochMillis(System.currentTimeMillis()),
                checkpoint = currentLearning.activeSession?.takeIf { it.stageId == stage.id }?.checkpoint,
              )
            },
            onFreePractice = { showFreePractice = true },
          )
        }
        RootTab.GAMES -> when (gameStage) {
          GameStage.HUB -> GameHubScreen(
            onGame = { kind ->
              selectedGameKind = kind
              gameStage = if (kind == GameKind.SPACING) GameStage.SPACING_SELECT else GameStage.DECK_SELECT
            },
            onWeeklyCup = {
              startWeeklyCup()
            },
          )
          GameStage.DECK_SELECT -> GameDeckSelectionScreen(
            kind = selectedGameKind,
            presets = gamePresets[selectedGameKind].orEmpty(),
            installed = current.installed.map { it.deck },
            bestScores = gameBestScores[selectedGameKind].orEmpty(),
            onBack = { gameStage = GameStage.HUB },
            onSelect = {
              if (selectedGameKind == GameKind.FLOW) {
                flowIsWeeklyCup = false
                activeFlowDeck = it
                flowSeed = kotlin.random.Random.nextLong()
              } else {
                activeGameDeck = it
                gameSeed = kotlin.random.Random.nextLong()
              }
            },
            onFindDeck = {
              gameStage = GameStage.HUB
              tab = RootTab.DISCOVER
            },
          )
          GameStage.SPACING_SELECT -> SpacingSelectionScreen(
            passages = spacingPassages,
            onBack = { gameStage = GameStage.HUB },
            onSelect = { activeSpacingPassage = it },
          )
        }
        RootTab.PROFILE -> MyDecksScreen(
          installed = current.installed,
          catalog = current.catalog,
          onFindDecks = { tab = RootTab.DISCOVER },
          onPlay = ::play,
          onUpdate = { deck ->
            workingDeckId = deck.deckId
            scope.launch {
              try {
                repository.install(deck)
                reload()
              } catch (_: Exception) {
                operationFailed = true
              } finally {
                workingDeckId = null
              }
            }
          },
          onDelete = { deck ->
            scope.launch {
              try {
                repository.delete(deck.metadata.deckId)
                reload()
              } catch (_: Exception) {
                operationFailed = true
              }
            }
          },
          onImport = {
            openUserDeck.launch(
              arrayOf(
                PiyoDeckDocumentGateway.MIME_TYPE,
                "application/zip",
                "application/octet-stream",
              ),
            )
          },
          onExport = { deck -> exportUserDeck(deck) },
          onExportThenDelete = { deck -> exportUserDeck(deck, deleteAfterExport = true) },
          hasDeckMakerAccess = billingState.hasAccess,
          hasActiveDraft = storedDeckDraft != null,
          onNewDeck = {
            requestDeckMakerDraft(UserDeckDraft.new(java.time.Instant.now()))
          },
          onResumeDraft = {
            val active = storedDeckDraft
            if (active == null) Unit
            else if (billingState.hasAccess) editorDraft = active
            else {
              pendingPaidDraft = active.draft
              showDeckMakerPaywall = true
            }
          },
          onEditDeck = { installed -> requestDeckMakerDraft(UserDeckDraft.editing(installed.deck)) },
          onCopyOfficial = { installed ->
            requestDeckMakerDraft(
              UserDeckDraft.copyingOfficial(installed.deck, java.time.Instant.now()),
            )
          },
          header = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
              PiyoProfileSection(
                today = JstDay.fromEpochMillis(System.currentTimeMillis()),
                completedDays = currentLearning.completedDays,
                unlockedRewards = currentLearning.unlockedRewards,
                appearance = preferences.sessionAppearance,
                selectedAccessory = preferences.selectedPiyoAccessory,
                unlockedAccessories = preferences.unlockedPiyoAccessories,
                onAccessorySelected = { accessory ->
                  onPreferencesChange(preferences.copy(selectedPiyoAccessory = accessory))
                },
              )
              ReviewDeckSection(
                reviewItems = currentLearning.reviewItems,
                manualCandidates = current.installed.flatMap { installed ->
                  val language = LocalConfiguration.current.locales[0].language
                  installed.deck.items.map { item ->
                    ManualReviewCandidate(
                      sourceDeckId = installed.deck.deckId,
                      deckName = installed.deck.localizedName(language).orEmpty(),
                      item = item,
                    )
                  }
                }.filter { candidate ->
                  currentLearning.reviewItems.none { it.id == "${candidate.sourceDeckId}::${candidate.item.id}" && it.isActive }
                },
                onStartReview = { reviewItems ->
                  activePractice = ActivePractice(
                    installed = null,
                    catalogEntry = null,
                    targets = reviewItems.map { it.item.ko },
                    items = reviewItems.map(ReviewItem::item),
                    sourceDeckId = null,
                    kind = PracticeKind.REVIEW,
                    inputMode = preferences.defaultInputMode,
                    sessionDay = JstDay.fromEpochMillis(System.currentTimeMillis()),
                    reviewSourceDeckIds = reviewItems.map(ReviewItem::sourceDeckId),
                  )
                },
                onManualAdd = { candidate ->
                  scope.launch {
                    try {
                      repository.addReviewItemManually(candidate.item, candidate.sourceDeckId)
                      reload()
                    } catch (_: Exception) { operationFailed = true }
                  }
                },
                onRemove = { review ->
                  scope.launch {
                    try {
                      repository.removeReviewItemManually(review.id)
                      reload()
                    } catch (_: Exception) { operationFailed = true }
                  }
                },
              )
              ReminderControls(
                preference = currentLearning.reminder,
                onEnabledChange = { enabled ->
                  if (!enabled || Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                  ) {
                    applyReminderEnabled(enabled)
                  } else {
                    pendingReminderEnable = true
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                },
                onTimeChange = { hour, minute ->
                  scope.launch {
                    try {
                      repository.saveReminderPreference(currentLearning.reminder.isEnabled, hour, minute)
                      if (currentLearning.reminder.isEnabled) reminderScheduler.schedule(hour, minute)
                      reload()
                    } catch (_: Exception) { operationFailed = true }
                  }
                },
              )
            }
          },
        )
      }

      CommonSettingsButton(
        onClick = { showSettings = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
      )

      if (operationFailed) {
        Surface(
          modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp).testTag("operation-error"),
          color = MaterialTheme.colorScheme.errorContainer,
          shadowElevation = 5.dp,
        ) {
          Row(modifier = Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              stringResource(app.piyokey.feature.discover.R.string.error_generic),
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
              when {
                pendingCompletion != null -> persistPracticeCompletion(requireNotNull(pendingCompletion))
                pendingCheckpointSave != null -> {
                  val pending = requireNotNull(pendingCheckpointSave)
                  scope.launch {
                    try {
                      repository.saveCurriculumCheckpoint(pending.first, pending.second)
                      pendingCheckpointSave = null
                      operationFailed = false
                      reload()
                    } catch (_: Exception) { operationFailed = true }
                  }
                }
                else -> operationFailed = false
              }
            }) { Text(stringResource(if (pendingCompletion == null && pendingCheckpointSave == null) R.string.dismiss_error else R.string.retry_save)) }
          }
        }
      }

      if (!preferences.appTourCompleted) {
        MainAppTourOverlay(
          step = appTourStep,
          onAdvance = {
            if (appTourStep >= 5) {
              onPreferencesChange(preferences.copy(appTourCompleted = true))
            } else {
              appTourStep += 1
            }
          },
          onSkip = { onPreferencesChange(preferences.copy(appTourCompleted = true)) },
        )
      }
    }
  }

  if (showSettings) {
    SettingsSheet(
      preferences = preferences,
      reminderEnabled = currentLearning.reminder.isEnabled,
      reminderHour = currentLearning.reminder.hour,
      reminderMinute = currentLearning.reminder.minute,
      onPreferencesChange = onPreferencesChange,
      onReminderEnabled = { enabled ->
        if (!enabled || Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
          ) == PackageManager.PERMISSION_GRANTED
        ) {
          applyReminderEnabled(enabled)
        } else {
          pendingReminderEnable = true
          notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      },
      onReminderTime = { hour, minute ->
        scope.launch {
          try {
            repository.saveReminderPreference(currentLearning.reminder.isEnabled, hour, minute)
            if (currentLearning.reminder.isEnabled) reminderScheduler.schedule(hour, minute)
            reload()
          } catch (_: Exception) { operationFailed = true }
        }
      },
      onOpenPrivacy = {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
      onOpenFeedback = {
        feedbackController.open(
          ContentFeedbackRequest(
            kind = ContentFeedbackKind.COMBINED,
            source = ContentFeedbackSource.SETTINGS,
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toLong(),
            language = preferences.language.tag,
          ),
        )
      },
      onOpenSupport = {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SUPPORT_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
      onDismiss = { showSettings = false },
    )
  }

  if (
    tab == RootTab.HOME &&
    PrivacyNoticePolicy.shouldPresent(
      reviewedVersion = preferences.privacyNoticeVersion,
      onboardingCompleted = preferences.hatchGateComplete,
      appTourCompleted = preferences.appTourCompleted,
      sessionIsActive = activePractice != null || activeFlowDeck != null ||
        activeGameDeck != null || activeSpacingPassage != null,
      hasBlockingPresentation = showSettings,
    )
  ) {
    PrivacyConsentDialog(
      initialAnalyticsEnabled = preferences.anonymousAnalyticsEnabled,
      initialDiagnosticsEnabled = preferences.crashDiagnosticsEnabled,
      onOpenPrivacy = {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
      onSave = { analytics, diagnostics ->
        onPreferencesChange(
          preferences.copy(
            anonymousAnalyticsEnabled = analytics,
            crashDiagnosticsEnabled = diagnostics,
            privacyNoticeVersion = PrivacyNoticePolicy.currentVersion,
          ),
        )
      },
      onContinueWithoutSharing = {
        onPreferencesChange(
          preferences.copy(
            anonymousAnalyticsEnabled = false,
            crashDiagnosticsEnabled = false,
            privacyNoticeVersion = PrivacyNoticePolicy.currentVersion,
          ),
        )
      },
    )
  }
}

@Composable
private fun PracticeDeckChooser(
  installed: List<InstalledDeck>,
  onPlay: (InstalledDeck) -> Unit,
  onSample: () -> Unit,
  onFindDecks: () -> Unit,
  onBack: () -> Unit,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_curriculum)) }
      Text(
        stringResource(R.string.practice_choose_deck),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
      )
    }
    if (installed.isEmpty()) {
      item { Button(onClick = onFindDecks) { Text(stringResource(app.piyokey.feature.discover.R.string.my_decks_find)) } }
    } else {
      items(installed, key = { it.metadata.deckId }) { item ->
        val synthetic = CatalogDeck(
          deckId = item.deck.deckId,
          version = item.deck.version,
          name = item.deck.name,
          authorNickname = item.deck.author.nickname,
          official = item.deck.official,
          featured = false,
          type = item.deck.type,
          level = item.deck.level,
          tags = item.deck.tags,
          itemCount = item.deck.items.size,
          sizeBytes = 1,
          downloadsTotal = 0,
          downloads7d = 0,
          createdAt = item.deck.createdAt,
          previewItems = emptyList(),
          fileUrl = "decks/${item.deck.deckId}.json",
          localizations = item.deck.localizations,
        )
        DeckCard(synthetic, true, languageCode, { onPlay(item) })
      }
    }
    item { Button(onClick = onSample, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.practice_sample)) } }
  }
}

private fun PracticeSessionState.toResult(
  practice: ActivePractice,
  activeDurationMillis: Long,
  stars: Int?,
): PracticeResult = PracticeResult(
  practice = practice,
  accuracyPercent = accuracyPercent,
  misses = mistakeCount,
  completed = itemResolutions.size,
  charactersPerMinute = acceptedJamoCount.toDouble() / activeDurationMillis.coerceAtLeast(1) * 60_000.0,
  stars = stars,
)

private fun CurriculumItem.toDeckItem(): DeckItem = DeckItem(
  id = id,
  ko = ko,
  readingJa = "",
  meaningJa = "",
  audio = null,
  localizations = null,
)

private fun Throwable.toUserDeckImportError(): UserDeckImportError = when (this) {
  is PiyoDeckImportException.PackageTooLarge,
  is PiyoDeckImportException.EntryTooLarge,
  -> UserDeckImportError.TOO_LARGE
  is PiyoDeckImportException.UnsupportedFormatVersion,
  is PiyoDeckImportException.UnsupportedDeckSchemaVersion,
  -> UserDeckImportError.UPDATE_REQUIRED
  is PiyoDeckImportException,
  is ImportedDeckException.OfficialIdentifierCollision,
  is ImportedDeckException.InstalledSourceCollision,
  -> UserDeckImportError.INVALID
  else -> UserDeckImportError.READ_FAILED
}

private fun UserDeckDraft.isSameDeckMakerFlow(other: UserDeckDraft): Boolean {
  val left = origin
  val right = other.origin
  return when {
    left == UserDeckDraftOrigin.New && right == UserDeckDraftOrigin.New -> deckId == other.deckId
    left == UserDeckDraftOrigin.Editing && right == UserDeckDraftOrigin.Editing -> deckId == other.deckId
    left is UserDeckDraftOrigin.OfficialCopy && right is UserDeckDraftOrigin.OfficialCopy ->
      left.sourceDeckId == right.sourceDeckId && deckId == other.deckId
    else -> false
  }
}

private fun AppLanguage.toUserDeckLanguage(): UserDeckLanguage = when (this) {
  AppLanguage.JAPANESE -> UserDeckLanguage.JAPANESE
  AppLanguage.ENGLISH -> UserDeckLanguage.ENGLISH
  AppLanguage.SPANISH -> UserDeckLanguage.SPANISH
  AppLanguage.GERMAN -> UserDeckLanguage.GERMAN
  AppLanguage.FRENCH -> UserDeckLanguage.FRENCH
}

private fun DeckMakerBillingNotice.toDeckMakerUiNotice(): DeckMakerUiNotice = when (this) {
  DeckMakerBillingNotice.PURCHASE_PENDING -> DeckMakerUiNotice.PURCHASE_PENDING
  DeckMakerBillingNotice.PURCHASE_FAILED -> DeckMakerUiNotice.PURCHASE_FAILED
  DeckMakerBillingNotice.RESTORE_SUCCEEDED -> DeckMakerUiNotice.RESTORE_SUCCEEDED
  DeckMakerBillingNotice.NOTHING_TO_RESTORE -> DeckMakerUiNotice.NOTHING_TO_RESTORE
  DeckMakerBillingNotice.RESTORE_FAILED -> DeckMakerUiNotice.RESTORE_FAILED
  DeckMakerBillingNotice.PRODUCT_UNAVAILABLE -> DeckMakerUiNotice.PRODUCT_UNAVAILABLE
}
