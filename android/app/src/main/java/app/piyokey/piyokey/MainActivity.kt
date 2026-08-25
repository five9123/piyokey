package app.piyokey.piyokey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.core.data.CatalogRefreshResult
import app.piyokey.core.data.DeckFilters
import app.piyokey.core.data.DeckLibrarySnapshot
import app.piyokey.core.data.DeckRepository
import app.piyokey.core.data.DiscoveryEngine
import app.piyokey.core.data.InstalledDeck
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.session.PracticeSessionState
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.game.FlowGameState
import app.piyokey.core.game.FlowRankTuning
import app.piyokey.core.game.toRecord
import app.piyokey.feature.discover.DeckCard
import app.piyokey.feature.discover.DeckDetailScreen
import app.piyokey.feature.discover.DiscoverScreen
import app.piyokey.feature.discover.MyDecksScreen
import app.piyokey.feature.discover.PracticeResultScreen
import app.piyokey.feature.discover.RecommendationHome
import app.piyokey.feature.practice.PracticeRoute
import app.piyokey.feature.game.FlowDeckSelectionScreen
import app.piyokey.feature.game.FlowGameRoute
import app.piyokey.feature.game.FlowResultScreen
import app.piyokey.feature.game.GameHubScreen
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme { PiyokeyApp() }
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
  val targets: List<String>?,
)

private data class PracticeResult(
  val practice: ActivePractice,
  val accuracyPercent: Double,
  val misses: Int,
  val completed: Int,
)

private enum class GameStage { HUB, FLOW_SELECT }

@Composable
private fun PiyokeyApp() {
  val context = LocalContext.current.applicationContext
  val repository = remember {
    DeckRepository.create(context, BuildConfig.CATALOG_URL.ifBlank { null })
  }
  val scope = rememberCoroutineScope()
  var snapshot by remember { mutableStateOf<DeckLibrarySnapshot?>(null) }
  var loadFailed by remember { mutableStateOf(false) }
  var operationFailed by remember { mutableStateOf(false) }
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

  LaunchedEffect(repository, reloadToken) {
    loadFailed = false
    try {
      val loadedSnapshot = repository.snapshot()
      val loadedPresets = repository.bundledFlowDecks()
      snapshot = loadedSnapshot
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
    } catch (_: Exception) {
      loadFailed = true
    }
  }

  val current = snapshot
  if (current == null) {
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
    activePractice = ActivePractice(installed, entry, installed.deck.items.map { it.ko })
    scope.launch { repository.markPlayed(installed.metadata.deckId) }
  }

  BackHandler(enabled = detailDeckId != null || activePractice != null || result != null) {
    when {
      result != null -> result = null
      activePractice != null -> activePractice = null
      else -> detailDeckId = null
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
            val saved = repository.saveFlowRecord(finished.toRecord(System.currentTimeMillis()))
            flowBestScores = flowBestScores + (runningFlow.deckId to saved.progress.bestScore)
            saved.isNewBest
          } catch (_: Exception) {
            operationFailed = true
            false
          }
          flowResult = finished
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
      Box(Modifier.fillMaxSize()) {
        PracticeRoute(
          targets = practice.targets,
          onSessionCompleted = { state ->
            result = state.toResult(practice)
            activePractice = null
          },
        )
        Surface(
          modifier = Modifier.align(Alignment.TopStart).padding(top = 4.dp, start = 4.dp),
          color = Color.White.copy(alpha = 0.92f),
        ) {
          TextButton(onClick = { activePractice = null }) { Text(stringResource(R.string.close_session)) }
        }
      }
      return
    }
    result != null -> {
      val completedResult = requireNotNull(result)
      val source = completedResult.practice.catalogEntry
      val recommendations = if (source == null) emptyList() else {
        DiscoveryEngine.sameTagRecommendations(current.catalog, source, current.installedDeckIds)
      }
      PracticeResultScreen(
        accuracyPercent = completedResult.accuracyPercent,
        misses = completedResult.misses,
        completed = completedResult.completed,
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
          ),
          installedDeckIds = current.installedDeckIds,
          onDeckClick = ::openDetail,
        )
        RootTab.DISCOVER -> DiscoverScreen(
          catalog = current.catalog,
          installedDeckIds = current.installedDeckIds,
          filters = filters,
          onFiltersChange = { filters = it },
          onDeckClick = ::openDetail,
        )
        RootTab.PRACTICE -> PracticeDeckChooser(
          installed = current.installed,
          onPlay = ::play,
          onSample = { activePractice = ActivePractice(null, null, null) },
          onFindDecks = { tab = RootTab.DISCOVER },
        )
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
        )
      }

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
            TextButton(onClick = { operationFailed = false }) { Text("×") }
          }
        }
      }
    }
  }
}

@Composable
private fun PracticeDeckChooser(
  installed: List<InstalledDeck>,
  onPlay: (InstalledDeck) -> Unit,
  onSample: () -> Unit,
  onFindDecks: () -> Unit,
) {
  val languageCode = LocalConfiguration.current.locales[0].language
  LazyColumn(
    modifier = Modifier.fillMaxSize().background(Color(0xFFFFF9F1)),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
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

private fun PracticeSessionState.toResult(practice: ActivePractice): PracticeResult = PracticeResult(
  practice = practice,
  accuracyPercent = accuracyPercent,
  misses = mistakeCount,
  completed = itemResolutions.size,
)
