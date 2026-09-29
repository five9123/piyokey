package app.piyokey.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.curriculum.CurriculumContent
import app.piyokey.android.data.decks.appName
import app.piyokey.android.feature.discover.DeckCard
import app.piyokey.android.feature.discover.DeckCardMetrics
import app.piyokey.android.feature.discover.DiscoverLauncher
import app.piyokey.android.feature.discover.InlineTopBar
import app.piyokey.android.feature.discover.rememberCatalogText
import app.piyokey.android.feature.game.GameLauncher
import app.piyokey.android.feature.onboarding.AppTourTarget
import app.piyokey.android.feature.onboarding.appTourTarget
import app.piyokey.android.feature.practice.PracticeLauncher
import app.piyokey.android.feature.settings.SettingsGearButton
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.home.HomePrimaryAction
import app.piyokey.core.domain.home.HomePrimaryActionPolicy

private val SystemOrange = Color(0xFFFF9500)

/** iOS `HomeView`: stamp card, primary action, quick actions and two recommendation rows. */
@Composable
fun HomeTabRoot() {
  val metrics = Piyo.metrics
  val today = rememberJstToday()
  LaunchedEffect(Unit) { AppData.catalog.loadIfNeeded() }
  val accessibilityFont = LocalDensity.current.fontScale >= 1.5f
  val landscape = HomePrimaryActionPolicy.usesLandscapeDashboard(
    metrics.availableWidth.value, metrics.isTall, accessibilityFont,
  )

  Column(Modifier.fillMaxSize()) {
    InlineTopBar(stringResource(R.string.home_navigation_title), onBack = null, trailing = { SettingsGearButton() })
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .testTag("home.screen"),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        Modifier
          .widthIn(max = if (landscape) metrics.hubContentMaxWidth else metrics.readableContentMaxWidth)
          .fillMaxWidth()
          .padding(horizontal = metrics.horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        PersistenceRecoveryBannerIfNeeded()
        if (landscape) {
          Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
            RetentionHomeCard(today, Modifier.weight(1f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              HomePrimaryActionCard(today)
              HomeQuickActions(accessibilityFont)
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RetentionHomeCard(today)
            HomePrimaryActionCard(today)
            HomeQuickActions(accessibilityFont)
          }
        }
        HomeRecommendationsSection()
      }
    }
  }
}

// MARK: persistence banner

@Composable
private fun PersistenceRecoveryBannerIfNeeded() {
  val game by AppData.gameProgress.saveFailed.collectAsState()
  val review by AppData.review.saveFailed.collectAsState()
  val curriculum by AppData.curriculum.saveFailed.collectAsState()
  val retention by AppData.retention.saveFailed.collectAsState()
  val onboarding by AppData.onboarding.saveFailed.collectAsState()
  if (!(game || review || curriculum || retention || onboarding)) return
  PersistenceRecoveryBanner(onRetry = {
    if (AppData.gameProgress.saveFailed.value) AppData.gameProgress.retryLastSave()
    if (AppData.review.saveFailed.value) AppData.review.retryLastSave()
    if (AppData.curriculum.saveFailed.value) AppData.curriculum.retryLastSave()
    if (AppData.retention.saveFailed.value) AppData.retention.retryLastSave()
    if (AppData.onboarding.saveFailed.value) AppData.onboarding.retryLastSave()
  })
}

/** iOS `PersistenceRecoveryBanner`. */
@Composable
fun PersistenceRecoveryBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(18.dp)
  Row(
    modifier
      .fillMaxWidth()
      .background(SystemOrange.copy(alpha = 0.11f), shape)
      .border(1.dp, SystemOrange.copy(alpha = 0.3f), shape)
      .padding(14.dp)
      .testTag("persistence.failure.banner"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Rounded.Sync, contentDescription = null, tint = SystemOrange, modifier = Modifier.size(24.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(stringResource(R.string.persistence_failure_title), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
      Text(stringResource(R.string.persistence_failure_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
    }
    Box(
      Modifier
        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
        .clickable(role = Role.Button, onClick = onRetry),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        stringResource(R.string.persistence_failure_retry),
        style = PiyoType.subheadline().copy(color = colors.accent, fontWeight = FontWeight.Bold),
      )
    }
  }
}

// MARK: primary action

@Composable
private fun HomePrimaryActionCard(today: JstDay) {
  val appNavigator = LocalAppNavigator.current
  val tabNavigator = LocalTabNavigator.current
  val activeSession by AppData.curriculum.activeSession.collectAsState()
  val stageProgress by AppData.curriculum.stageProgress.collectAsState()
  val installedList by AppData.deckLibrary.installedList.collectAsState()
  val deckRecords by AppData.deckLibrary.records.collectAsState()
  val retentionRecords by AppData.retention.records.collectAsState()
  val catalog by AppData.catalog.catalog.collectAsState()
  val onboardingSnapshot by AppData.onboarding.snapshot.collectAsState()
  var hasStartedLearning by remember { mutableStateOf(AppData.onboarding.homeLearningStarted) }

  val activeStageId = activeSession?.stageId
  val recentPlayedDeck = installedList.firstOrNull { deckRecords[it.deckId]?.lastPlayedAt != null }
  val hasPriorHomeActivity = HomePrimaryActionPolicy.hasPriorHomeActivity(stageProgress.keys, retentionRecords.values)
  val starter = remember(catalog, onboardingSnapshot) { AppData.recommendations.starter(1, catalog).firstOrNull() }
  val action = HomePrimaryActionPolicy.resolve(
    activeStageId = activeStageId,
    recentPlayedDeckId = recentPlayedDeck?.deckId,
    starterDeckId = starter?.deckId,
    catalogAvailable = catalog != null,
    hasStartedLearning = hasStartedLearning,
    hasPriorHomeActivity = hasPriorHomeActivity,
  )

  fun markStarted() {
    hasStartedLearning = true
    AppData.onboarding.homeLearningStarted = true
  }
  LaunchedEffect(activeStageId, recentPlayedDeck?.deckId, hasPriorHomeActivity) {
    if (HomePrimaryActionPolicy.shouldRememberLearningHistory(activeStageId, recentPlayedDeck?.deckId, hasPriorHomeActivity)) {
      markStarted()
    }
  }

  val modifier = Modifier.appTourTarget(AppTourTarget.HOME_PRIMARY)
  when (action) {
    is HomePrimaryAction.ResumeCurriculum -> {
      val stage = CurriculumCatalog.stage(action.stageId)
      PrimaryCard(
        eyebrow = stringResource(R.string.home_primary_resume_eyebrow),
        title = stage?.let { CurriculumContent.title(it) }.orEmpty(),
        detail = stringResource(R.string.home_primary_resume_detail),
        icon = Icons.Rounded.Replay,
        completed = false,
        tag = "home.primary.resume_curriculum",
        modifier = modifier,
      ) { PracticeLauncher.curriculumStage(appNavigator, action.stageId) }
    }
    is HomePrimaryAction.ResumeDeck -> PrimaryCard(
      eyebrow = stringResource(R.string.home_primary_resume_eyebrow),
      title = recentPlayedDeck?.appName.orEmpty(),
      detail = stringResource(R.string.home_primary_deck_detail),
      icon = Icons.Rounded.PlayCircle,
      completed = false,
      tag = "home.primary.resume_deck",
      modifier = modifier,
    ) { PracticeLauncher.deck(appNavigator, action.deckId) }
    is HomePrimaryAction.RecommendDeck -> PrimaryCard(
      eyebrow = stringResource(R.string.home_primary_recommend_eyebrow),
      title = starter?.appName.orEmpty(),
      detail = stringResource(R.string.home_primary_recommend_detail),
      icon = Icons.Rounded.AutoAwesome,
      completed = false,
      tag = "home.primary.recommend_deck",
      modifier = modifier,
    ) { DiscoverLauncher.deckDetail(tabNavigator, action.deckId) }
    HomePrimaryAction.DailyChallenge -> {
      val completed = retentionRecords[today]?.completedDailyChallenge == true
      PrimaryCard(
        eyebrow = stringResource(R.string.retention_daily_eyebrow),
        title = stringResource(if (completed) R.string.retention_daily_completed else R.string.retention_daily_title),
        detail = stringResource(R.string.home_primary_daily_personalized),
        icon = if (completed) Icons.Rounded.Verified else Icons.Rounded.Bolt,
        completed = completed,
        tag = "retention.daily_challenge",
        modifier = modifier,
      ) {
        markStarted()
        val challenge = AppData.quickPractice.dailyChallenge(today, onboardingSnapshot.selectedGoal)
        PracticeLauncher.dailyChallenge(appNavigator, challenge)
      }
    }
  }
}

@Composable
private fun PrimaryCard(
  eyebrow: String,
  title: String,
  detail: String,
  icon: ImageVector,
  completed: Boolean,
  tag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(26.dp)
  Row(
    modifier
      .fillMaxWidth()
      .shadow(14.dp, shape, ambientColor = colors.accent.copy(alpha = 0.25f), spotColor = colors.accent.copy(alpha = 0.25f))
      .background(if (completed) colors.success else colors.accent, shape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(18.dp)
      .testTag(tag),
    horizontalArrangement = Arrangement.spacedBy(15.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier.size(58.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(eyebrow, style = PiyoType.caption().copy(color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Black))
      Text(title, style = PiyoType.title3().copy(color = Color.White, fontWeight = FontWeight.ExtraBold))
      Text(detail, style = PiyoType.caption().copy(color = Color.White.copy(alpha = 0.86f)), maxLines = 2)
    }
    Icon(Icons.Rounded.ArrowCircleRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
  }
}

// MARK: quick actions

@Composable
private fun HomeQuickActions(stacked: Boolean) {
  val appNavigator = LocalAppNavigator.current
  val catalog by AppData.catalog.catalog.collectAsState()
  val piyoCupDeck = remember { AppData.bundled.piyoCupDeck() }

  val piyoCup: @Composable (Modifier) -> Unit = { m ->
    if (piyoCupDeck != null) {
      QuickActionCard(
        title = stringResource(R.string.home_quick_piyo_cup_title),
        detail = stringResource(R.string.home_quick_piyo_cup_detail),
        icon = Icons.Rounded.MilitaryTech,
        tint = SystemOrange,
        tag = "home.quick.piyo_cup",
        modifier = m,
      ) { GameLauncher.piyoCup(appNavigator) }
    } else {
      QuickActionCard(
        title = stringResource(R.string.home_quick_piyo_cup_title),
        detail = stringResource(R.string.piyo_cup_unavailable),
        icon = Icons.Rounded.ErrorOutline,
        tint = SystemOrange,
        tag = "home.quick.piyo_cup.unavailable",
        modifier = m.alpha(0.55f),
        onClick = null,
      )
    }
  }
  val random: @Composable (Modifier) -> Unit = { m ->
    QuickActionCard(
      title = stringResource(R.string.home_quick_random_title),
      detail = stringResource(R.string.home_quick_random_detail),
      icon = Icons.Rounded.Shuffle,
      tint = Piyo.colors.secondary,
      tag = "home.quick.random",
      modifier = m,
    ) {
      val session = AppData.quickPractice.nextRandomWordSession(
        installedDecks = AppData.deckLibrary.installed,
        goal = AppData.onboarding.selectedGoal,
        catalog = catalog,
      ) ?: return@QuickActionCard
      PracticeLauncher.randomWords(appNavigator, session)
    }
  }
  if (stacked) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      piyoCup(Modifier.fillMaxWidth())
      random(Modifier.fillMaxWidth())
    }
  } else {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      piyoCup(Modifier.weight(1f).fillMaxHeight())
      random(Modifier.weight(1f).fillMaxHeight())
    }
  }
}

@Composable
private fun QuickActionCard(
  title: String,
  detail: String,
  icon: ImageVector,
  tint: Color,
  tag: String,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)?,
) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(22.dp)
  Column(
    modifier
      .defaultMinSize(minHeight = 128.dp)
      .background(colors.card, shape)
      .border(1.dp, tint.copy(alpha = 0.18f), shape)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
      .padding(15.dp)
      .testTag(tag),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(42.dp).background(tint.copy(alpha = 0.13f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
    Text(title, style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold), maxLines = 2)
    Text(detail, style = PiyoType.caption().copy(color = colors.mutedInk), maxLines = 2)
  }
}

// MARK: recommendations

@Composable
private fun HomeRecommendationsSection() {
  val catalog by AppData.catalog.catalog.collectAsState()
  val history by AppData.deckLibrary.downloadHistory.collectAsState()
  val installed by AppData.deckLibrary.installedDecks.collectAsState()
  val records by AppData.gameProgress.records.collectAsState()
  val stageProgress by AppData.curriculum.stageProgress.collectAsState()
  val onboarding by AppData.onboarding.snapshot.collectAsState()
  val areas = remember(catalog, history, installed, records, stageProgress, onboarding) {
    AppData.recommendations.homeAreas(catalog)
  }
  val hasSignal = remember(catalog, records, stageProgress) { AppData.recommendations.hasLearningSignal(catalog) }
  if (areas.personal.isEmpty() || catalog == null) return

  Column(
    Modifier.fillMaxWidth().testTag("home.recommendations"),
    verticalArrangement = Arrangement.spacedBy(22.dp),
  ) {
    RecommendationRow(
      title = stringResource(R.string.recommendations_home_title),
      subtitle = stringResource(
        if (history.isEmpty()) R.string.recommendations_home_cold_start_subtitle else R.string.recommendations_home_subtitle,
      ),
      icon = Icons.Rounded.AutoAwesome,
      decks = areas.personal,
      tag = "home.recommendations.personal",
      itemPrefix = "home.recommendation",
    )
    if (areas.nextStep.isNotEmpty()) {
      RecommendationRow(
        title = stringResource(R.string.recommendations_home_next_step_title),
        subtitle = stringResource(
          if (hasSignal) R.string.recommendations_home_next_step_subtitle else R.string.recommendations_home_next_step_cold_start_subtitle,
        ),
        icon = Icons.Rounded.ArrowOutward,
        decks = areas.nextStep,
        tag = "home.recommendations.next_step",
        itemPrefix = "home.next_step",
      )
    }
  }
}

@Composable
private fun RecommendationRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  decks: List<CatalogDeck>,
  tag: String,
  itemPrefix: String,
) {
  val colors = Piyo.colors
  val metrics = Piyo.metrics
  val tabNavigator = LocalTabNavigator.current
  val text = rememberCatalogText()
  val fontScale = Piyo.fontScale
  Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
      Text(title, style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
    }
    Text(subtitle, style = PiyoType.caption().copy(color = colors.mutedInk))
    val card: @Composable (CatalogDeck, Boolean, Modifier) -> Unit = { deck, compact, m ->
      val minimum = if (compact) DeckCardMetrics.compactMinimumHeight else DeckCardMetrics.regularMinimumHeight
      Box(m.testTag("$itemPrefix.${deck.deckId}")) {
        DeckCard(
          deck = deck,
          text = text,
          onClick = { DiscoverLauncher.deckDetail(tabNavigator, deck.deckId) },
          compact = compact,
          fixedHeight = minimum * fontScale,
          limitsTitleToOneLine = true,
        )
      }
    }
    if (metrics.isExpanded) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        decks.chunked(3).forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { deck -> card(deck, true, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
      }
    } else {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp, top = 4.dp),
      ) {
        items(decks, key = { it.deckId }) { deck -> card(deck, false, Modifier.width(282.dp)) }
      }
    }
  }
}
