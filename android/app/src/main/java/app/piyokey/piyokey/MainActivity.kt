package app.piyokey.piyokey

import android.Manifest
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import app.piyokey.core.data.CatalogRefreshResult
import app.piyokey.core.data.DeckFilters
import app.piyokey.core.data.DeckLibrarySnapshot
import app.piyokey.core.data.DeckRepository
import app.piyokey.core.data.DiscoveryEngine
import app.piyokey.core.data.InstalledDeck
import app.piyokey.core.data.LearningSnapshot
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.session.PracticeSessionState
import app.piyokey.core.session.PracticeSessionCheckpoint
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.game.FlowGameState
import app.piyokey.core.game.FlowRankTuning
import app.piyokey.core.game.toRecord
import app.piyokey.core.platform.DailyReminderScheduler
import app.piyokey.core.retention.CurriculumItem
import app.piyokey.core.retention.CurriculumCatalog
import app.piyokey.core.retention.CurriculumStage
import app.piyokey.core.retention.DailyChallengePolicy
import app.piyokey.core.retention.JstDay
import app.piyokey.core.retention.ReviewItem
import app.piyokey.core.settings.AppPreferences
import app.piyokey.core.settings.AppPreferencesStore
import app.piyokey.core.settings.AppTheme
import app.piyokey.core.settings.InputMode
import app.piyokey.core.settings.OnboardingPolicy
import app.piyokey.feature.discover.DeckCard
import app.piyokey.feature.discover.DeckDetailScreen
import app.piyokey.feature.discover.DiscoverScreen
import app.piyokey.feature.discover.MyDecksScreen
import app.piyokey.feature.discover.PracticeResultScreen
import app.piyokey.feature.discover.RecommendationHome
import app.piyokey.feature.practice.PracticeRoute
import app.piyokey.feature.practice.PracticeDisplayOptions
import app.piyokey.feature.practice.PracticeKeyboardOptions
import app.piyokey.feature.practice.PracticePrompt
import app.piyokey.feature.game.FlowDeckSelectionScreen
import app.piyokey.feature.game.FlowGameRoute
import app.piyokey.feature.game.FlowResultScreen
import app.piyokey.feature.game.GameHubScreen
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
import app.piyokey.feature.settings.SettingsSheet
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val preferencesStore = AppPreferencesStore.create(this) {
      resources.configuration.locales.let { locales ->
        (0 until locales.size()).map { locales[it].toLanguageTag() }
      }
    }
    setContent {
      val preferences by preferencesStore.values.collectAsStateWithLifecycle(initialValue = null)
      val scope = rememberCoroutineScope()
      val resolved = preferences
      if (resolved == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      } else {
        val testOverrides = remember {
          getSharedPreferences("piyokey_test_overrides", MODE_PRIVATE)
        }
        val freshOnboardingTest = BuildConfig.DEBUG && testOverrides.getBoolean("fresh_onboarding", false)
        val freshOnboardingPreferences = remember(freshOnboardingTest) {
          AppPreferences(language = resolved.language, onboardingMigrationChecked = true)
        }
        val launchPreferences = when {
          freshOnboardingTest -> freshOnboardingPreferences
          BuildConfig.DEBUG && testOverrides.getBoolean("skip_onboarding", false) -> resolved.copy(
            firstInputCompleted = true,
            hatchHandoffCompleted = true,
            hatchChaptersCompleted = 3,
            appTourCompleted = true,
            onboardingMigrationChecked = true,
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
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
          LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * optimistic.fontScale.multiplier),
        ) {
          MaterialTheme(colorScheme = if (optimistic.theme == AppTheme.DARK) darkColorScheme() else lightColorScheme()) {
            PiyokeyApp(
              preferences = optimistic,
              onPreferencesChange = { updated ->
                optimistic = updated
                scope.launch { preferencesStore.update { updated } }
              },
            )
          }
        }
      }
    }
  }
}

private enum class RootTab(val label: Int, val symbol: String) {
  HOME(R.string.nav_home, "⌂"),
  DISCOVER(R.string.nav_discover, "⌕"),
  PRACTICE(R.string.nav_practice, "⌨"),
  GAMES(R.string.nav_games, "★"),
  PROFILE(R.string.nav_profile, "●"),
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
)

private enum class PracticeKind { FREE, DECK, CURRICULUM, DAILY, REVIEW, ONBOARDING }

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

private enum class GameStage { HUB, FLOW_SELECT }

@Composable
private fun PiyokeyApp(
  preferences: AppPreferences,
  onPreferencesChange: (AppPreferences) -> Unit,
) {
  val context = LocalContext.current.applicationContext
  val hadExistingDatabase = remember { context.getDatabasePath("piyokey.db").exists() }
  val repository = remember {
    DeckRepository.create(context, BuildConfig.CATALOG_URL.ifBlank { null })
  }
  val scope = rememberCoroutineScope()
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
  var flowPresets by remember { mutableStateOf<List<Deck>>(emptyList()) }
  var flowBestScores by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
  var activeFlowDeck by remember { mutableStateOf<Deck?>(null) }
  var flowResult by remember { mutableStateOf<FlowGameState?>(null) }
  var flowIsNewBest by remember { mutableStateOf(false) }
  var flowSeed by remember { mutableStateOf(0L) }
  var flowRankTuning by remember { mutableStateOf(FlowRankTuning()) }
  var showFreePractice by remember { mutableStateOf(false) }
  var showSettings by remember { mutableStateOf(false) }
  var appTourStep by remember { mutableStateOf(0) }
  val reminderScheduler = remember { DailyReminderScheduler(context) }
  var pendingReminderEnable by remember { mutableStateOf(false) }

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

  LaunchedEffect(repository, reloadToken) {
    loadFailed = false
    try {
      val loadedSnapshot = repository.snapshot()
      val loadedLearning = repository.learningSnapshot()
      val loadedPresets = repository.bundledFlowDecks()
      snapshot = loadedSnapshot
      learning = loadedLearning
      flowPresets = loadedPresets
      flowRankTuning = repository.flowRankTuning()
      val ids = loadedPresets.map(Deck::deckId) + loadedSnapshot.installed.map { it.metadata.deckId }
      flowBestScores = ids.distinct().mapNotNull { id ->
        repository.flowProgress(id)?.let { id to it.bestScore }
      }.toMap()
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

  if (!preferences.onboardingMigrationChecked) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }

  if (!preferences.firstInputCompleted || !preferences.hatchHandoffCompleted) {
    OnboardingRoute(preferences = preferences, onUpdate = onPreferencesChange)
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

  fun reload() {
    reloadToken += 1
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
          if (practice.kind == PracticeKind.REVIEW && practice.reviewSourceDeckIds != null) {
            state.itemResolutions.forEach { resolution ->
              val item = items.getOrNull(resolution.itemIndex) ?: return@forEach
              val source = practice.reviewSourceDeckIds.getOrNull(resolution.itemIndex) ?: return@forEach
              repository.recordPracticeReview(
                sourceDeckId = source,
                items = listOf(item),
                resolutions = listOf(resolution.copy(itemIndex = 0)),
                isReviewSession = true,
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
          else -> null
        }
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
          inputMode = InputMode.BUILTIN,
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

  val runningFlow = activeFlowDeck
  val completedFlow = flowResult
  if (runningFlow != null && completedFlow == null) {
    FlowGameRoute(
      deck = runningFlow,
      seed = flowSeed,
      onClose = { activeFlowDeck = null },
      onFinished = { finished ->
        scope.launch {
          flowIsNewBest = try {
            val saved = repository.saveFlowRecord(
              record = finished.toRecord(System.currentTimeMillis()),
              deckItems = finished.cards,
              reviewMistakeCounts = finished.reviewMistakeCounts,
            )
            flowBestScores = flowBestScores + (runningFlow.deckId to saved.progress.bestScore)
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
    FlowResultScreen(
      state = completedFlow,
      rank = flowRankTuning.rank(completedFlow.accuracyPercent, completedFlow.charactersPerMinute),
      isNewBest = flowIsNewBest,
      onRetry = {
        flowResult = null
        flowSeed = kotlin.random.Random.nextLong()
      },
      onDone = {
        flowResult = null
        activeFlowDeck = null
        gameStage = GameStage.FLOW_SELECT
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
        )
      }
      Box(Modifier.fillMaxSize()) {
        PracticeRoute(
          targets = practice.targets,
          prompts = prompts,
          initialCheckpoint = practice.checkpoint,
          inputMode = if (inputLocked) InputMode.BUILTIN else practice.inputMode,
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
            TextButton(onClick = { activePractice = null }) { Text(stringResource(R.string.close_session)) }
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
          result = null
          activePractice = completedResult.practice
        },
        onDeckClick = ::openDetail,
        onBack = { result = null },
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
            icon = { Text(item.symbol, fontWeight = FontWeight.Black) },
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
          header = {
            RetentionHomeCard(
              today = JstDay.fromEpochMillis(System.currentTimeMillis()),
              completedDays = currentLearning.completedDays,
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
            )
          },
        )
        RootTab.DISCOVER -> DiscoverScreen(
          catalog = current.catalog,
          installedDeckIds = current.installedDeckIds,
          filters = filters,
          onFiltersChange = { filters = it },
          onDeckClick = ::openDetail,
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
            onFlow = { gameStage = GameStage.FLOW_SELECT },
            onWeeklyCup = {
              flowPresets.firstOrNull { it.deckId == "flow_topik_beginner" }?.let {
                activeFlowDeck = it
                flowSeed = kotlin.random.Random.nextLong()
              }
            },
          )
          GameStage.FLOW_SELECT -> FlowDeckSelectionScreen(
            presets = flowPresets,
            installed = current.installed.map { it.deck },
            bestScores = flowBestScores,
            onBack = { gameStage = GameStage.HUB },
            onSelect = {
              activeFlowDeck = it
              flowSeed = kotlin.random.Random.nextLong()
            },
            onFindDeck = {
              gameStage = GameStage.HUB
              tab = RootTab.DISCOVER
            },
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
          header = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
              PiyoProfileSection(
                today = JstDay.fromEpochMillis(System.currentTimeMillis()),
                completedDays = currentLearning.completedDays,
                unlockedRewards = currentLearning.unlockedRewards,
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
          modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
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
          Intent(Intent.ACTION_VIEW, Uri.parse("https://hancoweb.vercel.app/privacy"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
      onOpenFeedback = {
        context.startActivity(
          Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact@typee.app"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
      onOpenSupport = {
        context.startActivity(
          Intent(Intent.ACTION_VIEW, Uri.parse("https://hancoweb.vercel.app/support"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
      },
      onDismiss = { showSettings = false },
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
    modifier = Modifier.fillMaxSize().background(Color(0xFFFFF9F1)),
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
