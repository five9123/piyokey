package app.piyokey.android.feature.game

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.appName
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.progress.RetentionClock
import app.piyokey.android.feature.discover.DeckCard
import app.piyokey.android.feature.discover.DiscoverLauncher
import app.piyokey.android.feature.discover.rememberCatalogText
import app.piyokey.android.feature.library.LibraryLauncher
import app.piyokey.android.feature.practice.PracticeLauncher
import app.piyokey.android.feature.practice.PracticeSource
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.games.PlayGamesService
import app.piyokey.android.platform.games.SubmissionState
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.session.SessionMetricDivider
import app.piyokey.android.ui.session.SessionPrimaryAction
import app.piyokey.android.ui.session.SessionResultMetric
import app.piyokey.android.ui.session.SessionResultReviewSection
import app.piyokey.android.ui.session.SessionResultScaffold
import app.piyokey.android.ui.session.SessionShareButton
import app.piyokey.android.ui.session.SessionShareCardMetric
import app.piyokey.android.ui.session.SessionShareCardModel
import app.piyokey.android.ui.session.SessionSoftAction
import app.piyokey.android.ui.session.ShareMetricIcon
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.domain.GameRecordSaveOutcome
import app.piyokey.core.domain.SessionInputMode
import app.piyokey.core.domain.SessionReviewItem
import app.piyokey.core.domain.game.FlowGameResult
import app.piyokey.core.domain.game.GameResultPresentation
import app.piyokey.core.domain.game.RankTint
import app.piyokey.core.domain.practice.SessionResultRevealTiming
import kotlinx.coroutines.launch

internal val GameResultPresentation.modeTitleRes: Int
  get() = when (this) {
    GameResultPresentation.FLOW -> R.string.game_mode_flow
    GameResultPresentation.PIYO_CUP -> R.string.piyo_cup_title
    GameResultPresentation.ACID_RAIN -> R.string.game_mode_acid_rain
    GameResultPresentation.CHOSEONG -> R.string.game_mode_choseong
    GameResultPresentation.WORD_MATCH -> R.string.game_mode_word_match
    GameResultPresentation.DICTATION -> R.string.game_mode_dictation
  }

private val GameResultPresentation.clearRes: Int
  get() = when (this) {
    GameResultPresentation.FLOW -> R.string.game_result_clear
    GameResultPresentation.PIYO_CUP -> R.string.piyo_cup_result_clear
    GameResultPresentation.ACID_RAIN -> R.string.acid_rain_result_clear
    GameResultPresentation.CHOSEONG -> R.string.choseong_result_clear
    GameResultPresentation.WORD_MATCH -> R.string.word_match_result_clear
    GameResultPresentation.DICTATION -> R.string.dictation_result_clear
  }

private val GameResultPresentation.speedRes: Int
  get() = if (speedKey == "game.result.speed") R.string.game_result_speed else R.string.choseong_result_speed

private val GameResultPresentation.shareBadgeRes: Int
  get() = when (this) {
    GameResultPresentation.FLOW -> R.string.result_share_game_badge
    GameResultPresentation.PIYO_CUP -> R.string.piyo_cup_share_badge
    GameResultPresentation.ACID_RAIN -> R.string.acid_rain_share_badge
    GameResultPresentation.CHOSEONG -> R.string.choseong_share_badge
    GameResultPresentation.WORD_MATCH -> R.string.word_match_share_badge
    GameResultPresentation.DICTATION -> R.string.dictation_share_badge
  }

private val GameResultPresentation.shareCaptionRes: Int
  get() = when (this) {
    GameResultPresentation.FLOW -> R.string.result_share_game_caption
    GameResultPresentation.PIYO_CUP -> R.string.piyo_cup_share_caption
    GameResultPresentation.ACID_RAIN -> R.string.acid_rain_share_caption
    GameResultPresentation.CHOSEONG -> R.string.choseong_share_caption
    GameResultPresentation.WORD_MATCH -> R.string.word_match_share_caption
    GameResultPresentation.DICTATION -> R.string.dictation_share_caption
  }

/** iOS `GameResultPresentation.resultLevelTitle(for:)`. */
@Composable
internal fun GameResultPresentation.levelTitle(deck: Deck): String {
  if (this == GameResultPresentation.PIYO_CUP) return stringResource(R.string.piyo_cup_weekly_badge)
  val kind = presetGameKind
  val level = presetLevel(deck)
  return if (kind != null && level != null) stringResource(GameStrings.presetTitle(kind, level)) else deck.appName
}

@Composable
private fun rankColor(rank: String): Color = when (GameResultPresentation.rankTint(rank)) {
  RankTint.ORANGE -> GameColors.orange
  RankTint.ACCENT -> Piyo.colors.accent
  RankTint.SECONDARY -> Piyo.colors.secondary
  RankTint.MUTED -> Piyo.colors.mutedInk
}

internal fun resultTiming() = SessionResultRevealTiming(GameTestHooks.resultAnimationScale)

/**
 * iOS `FlowGameResultView`, shared by Flow, Piyo Cup, Acid Rain and the recall typing games:
 * rank card, score with new record / best, Play Games submission, metrics, review, share,
 * review practice, related decks and a one-tap retry.
 */
@Composable
internal fun GameResultScreen(
  deck: Deck,
  result: FlowGameResult,
  recordOutcome: GameRecordSaveOutcome?,
  reviewItems: List<SessionReviewItem>,
  presentation: GameResultPresentation,
  onRetry: () -> Unit,
  onFinish: () -> Unit,
) {
  val colors = Piyo.colors
  val appNav = LocalAppNavigator.current

  LaunchedEffect(Unit) {
    MascotStore.registerTOPIKSessionScore(result.score, deck.tags)
    Telemetry.gameResult(
      gameMode = presentation.analyticsValue,
      difficulty = presentation.analyticsDifficulty(deck),
      result = "completed",
      score = result.score,
      inputMode = recordOutcome?.record?.inputMode?.raw ?: SessionInputMode.BUILT_IN.raw,
      deckSource = GameDecks.analyticsDeckSource(deck, presentation),
    )
  }

  val playGames = rememberPlayGamesSubmission(recordOutcome)
  val modeTitle = stringResource(presentation.modeTitleRes)
  val levelTitle = presentation.levelTitle(deck)
  val shareModel = shareModel(deck, result, presentation, modeTitle, levelTitle)
  val recommendations = remember(deck.deckId) { AppData.recommendations.related(deck.deckId, deck.tags) }

  SessionResultScaffold(
    navigationTitle = stringResource(R.string.game_result_navigation_title),
    onFinish = onFinish,
    celebratesNewRecord = recordOutcome?.isNewBest == true,
    timing = resultTiming(),
    header = { RankCard(result, recordOutcome, presentation, modeTitle, levelTitle) },
    score = { reveal ->
      val displayed = (result.score * reveal.scoreProgress).toInt()
      val newRecordOpacity = minOf(reveal.newRecordBurstProgress * 5, 1.0).toFloat()
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.card).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(stringResource(R.string.game_score), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk))
        val scoreText = formatNumber(displayed)
        Text(
          scoreText,
          style = PiyoType.style(44f, FontWeight.Black).copy(color = colors.accent),
          modifier = Modifier.testTag("game.result.score.value").semantics { contentDescription = formatNumber(result.score) },
        )
        if (recordOutcome != null) {
          if (recordOutcome.isNewBest) {
            Row(
              Modifier.alpha(newRecordOpacity).scale(0.82f + newRecordOpacity * 0.18f).testTag("game.result.new_record"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = GameColors.orange, modifier = Modifier.size(14.dp))
              Text(
                stringResource(R.string.game_result_new_record),
                style = PiyoType.caption().copy(fontWeight = FontWeight.Black, color = GameColors.orange),
              )
            }
            recordOutcome.previousBestScore?.let {
              Text(
                stringResource(R.string.game_result_previous_best, formatNumber(it)),
                style = PiyoType.caption2().copy(color = colors.mutedInk),
                modifier = Modifier.alpha(newRecordOpacity),
              )
            }
          } else {
            Text(
              stringResource(R.string.game_result_best_score, formatNumber(recordOutcome.deckProgress.bestScore)),
              style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary),
              modifier = Modifier.testTag("game.result.best_score.value"),
            )
          }
        }
        if (reveal.actionsEnabled && playGames.isAvailable) {
          PlayGamesButton(playGames, Modifier.padding(top = 10.dp))
        }
      }
    },
    metrics = { reveal ->
      val accuracyProgress = reveal.metricProgress(0)
      val comboProgress = reveal.metricProgress(1)
      val speedProgress = reveal.metricProgress(2)
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.card).padding(horizontal = 8.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SessionResultMetric(
          value = stringResource(R.string.practice_result_accuracy_value, result.accuracyPercent * accuracyProgress),
          label = stringResource(R.string.practice_result_accuracy),
          progress = accuracyProgress * (result.accuracyPercent / 100).coerceIn(0.0, 1.0),
          testTag = "game.result.accuracy.value",
          tint = colors.success,
          modifier = Modifier.weight(1f),
        )
        SessionMetricDivider()
        SessionResultMetric(
          value = (result.maxCombo * comboProgress).toInt().toString(),
          label = stringResource(R.string.game_result_max_combo),
          progress = comboProgress * minOf(result.maxCombo / 20.0, 1.0),
          testTag = "game.result.combo.value",
          tint = GameColors.orange,
          modifier = Modifier.weight(1f),
        )
        SessionMetricDivider()
        SessionResultMetric(
          value = (result.charactersPerMinute * speedProgress).toInt().toString(),
          label = stringResource(presentation.speedRes),
          progress = speedProgress * minOf(result.charactersPerMinute / 120, 1.0),
          testTag = "game.result.speed.value",
          tint = colors.secondary,
          modifier = Modifier.weight(1f),
        )
      }
    },
    review = { SessionResultReviewSection(reviewItems, onSeeAll = { LibraryLauncher.reviewDeck(appNav) }) },
    actions = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SessionShareButton(shareModel, testTag = "game.result.share", analyticsGameMode = presentation.analyticsValue)
        if (reviewItems.isNotEmpty()) {
          val title = stringResource(R.string.review_deck_title)
          SessionSoftAction(stringResource(R.string.result_review_start), Icons.Rounded.Autorenew, "game.result.review", onClick = {
            PracticeLauncher.items(appNav, title, reviewItems.map { it.item }, PracticeSource.REVIEW)
          })
        }
        if (recommendations.isNotEmpty()) {
          val text = rememberCatalogText()
          Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.ViewCarousel, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
              Text(stringResource(R.string.recommendations_result_title), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
            }
            recommendations.forEach { recommended ->
              DeckCard(
                deck = recommended,
                text = text,
                onClick = { DiscoverLauncher.deckDetail(appNav, recommended.deckId) },
                isInstalled = AppData.deckLibrary.isInstalled(recommended.deckId),
                updateAvailable = AppData.deckLibrary.needsUpdate(recommended),
              )
            }
          }
        }
      }
    },
    bottomBar = {
      SessionPrimaryAction(stringResource(R.string.practice_result_retry), Icons.Rounded.Refresh, "game.result.retry", onRetry)
    },
  )
}

@Composable
private fun RankCard(
  result: FlowGameResult,
  outcome: GameRecordSaveOutcome?,
  presentation: GameResultPresentation,
  modeTitle: String,
  levelTitle: String,
) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(28.dp)
  val newBest = outcome?.isNewBest == true
  Row(
    Modifier
      .fillMaxWidth()
      .shadow(14.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
      .clip(shape)
      .background(colors.card)
      .padding(20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(Modifier.width(84.dp), contentAlignment = Alignment.Center) {
      GrowingMascot(
        mood = if (newBest) MascotMood.SURPRISE else MascotMood.CHEER,
        reaction = if (newBest) MascotReaction.NewBest else MascotReaction.WordCompleted,
        reactionRevision = 1,
        showsNameTag = true,
        size = 64.dp,
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
          modeTitle,
          style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold, color = colors.secondary),
          maxLines = 1,
          modifier = Modifier.testTag("game.result.mode"),
        )
        Text(
          levelTitle,
          style = PiyoType.caption2().copy(fontWeight = FontWeight.Black, color = colors.accent),
          maxLines = 1,
          modifier = Modifier
            .clip(CircleShape)
            .background(colors.accentSoft.copy(alpha = 0.62f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("game.result.level"),
        )
      }
      Text(
        stringResource(if (result.endedByLives) R.string.game_result_game_over else presentation.clearRes),
        style = PiyoType.title2().copy(fontWeight = FontWeight.ExtraBold),
        modifier = Modifier.testTag("game.result.screen"),
      )
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          result.rank,
          style = PiyoType.style(58f, FontWeight.Black).copy(color = rankColor(result.rank)),
          modifier = Modifier.testTag("game.result.rank"),
        )
        Text(
          stringResource(R.string.game_result_rank),
          style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk),
          modifier = Modifier.padding(bottom = 12.dp),
        )
      }
      val inputMode = outcome?.record?.inputMode
      if (presentation.showsInputMode && inputMode != null) {
        Row(
          Modifier.testTag("game.result.input_mode"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            when (inputMode) {
              SessionInputMode.BUILT_IN -> Icons.Rounded.GridView
              SessionInputMode.BUILT_IN_KOREAN_10KEY -> Icons.Rounded.Dialpad
              SessionInputMode.OS_IME -> Icons.Rounded.Keyboard
            },
            contentDescription = null,
            tint = colors.secondary,
            modifier = Modifier.size(13.dp),
          )
          Text(
            stringResource(
              when (inputMode) {
                SessionInputMode.BUILT_IN -> R.string.input_mode_builtin
                SessionInputMode.BUILT_IN_KOREAN_10KEY -> R.string.input_mode_builtin_korean_10key
                SessionInputMode.OS_IME -> R.string.input_mode_os_ime
              },
            ),
            style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = colors.secondary),
          )
        }
      }
    }
  }
}

@Composable
private fun shareModel(
  deck: Deck,
  result: FlowGameResult,
  presentation: GameResultPresentation,
  modeTitle: String,
  levelTitle: String,
): SessionShareCardModel {
  val streak = remember { AppData.retention.streak(RetentionClock.today()).current }
  val defaultName = stringResource(R.string.mascot_default_name)
  val mascotName by MascotStore.name.collectAsState()
  val caption = if (presentation == GameResultPresentation.PIYO_CUP) {
    stringResource(presentation.shareCaptionRes, result.score)
  } else {
    stringResource(presentation.shareCaptionRes, deck.appName, result.score)
  }
  return SessionShareCardModel(
    sessionTitle = modeTitle,
    sessionSubtitle = levelTitle,
    achievement = stringResource(presentation.shareBadgeRes, result.rank),
    scoreLabel = stringResource(R.string.game_score),
    scoreValue = formatNumber(result.score),
    metrics = listOf(
      SessionShareCardMetric(
        "accuracy",
        stringResource(R.string.practice_result_accuracy),
        stringResource(R.string.practice_result_accuracy_value, result.accuracyPercent),
        ShareMetricIcon.SCOPE,
      ),
      SessionShareCardMetric("combo", stringResource(R.string.game_result_max_combo), formatNumber(result.maxCombo), ShareMetricIcon.COMBO),
      SessionShareCardMetric(
        "streak",
        stringResource(R.string.result_share_streak),
        pluralStringResource(R.plurals.result_share_streak_value, streak, streak),
        ShareMetricIcon.SEAL,
      ),
    ),
    caption = caption,
    mascotName = MascotStore.displayName(mascotName, defaultName),
  )
}

/** Play Games state for one result (iOS `GameCenterService` bindings on the result screen). */
internal class PlayGamesSubmissionUi(
  val isAvailable: Boolean,
  val isAuthenticated: Boolean,
  val state: SubmissionState?,
  val rank: Long?,
  val isBusy: Boolean,
  val onShowLeaderboard: () -> Unit,
  val onRetry: () -> Unit,
)

@Composable
private fun rememberPlayGamesSubmission(outcome: GameRecordSaveOutcome?): PlayGamesSubmissionUi {
  val activity = LocalActivity.current
  val scope = rememberCoroutineScope()
  // Observed so the button reflects sign-in, submission and rank changes.
  PlayGamesService.connection.collectAsState().value
  PlayGamesService.submissionStates.collectAsState().value
  PlayGamesService.ranks.collectAsState().value
  var busy by remember { mutableStateOf(false) }
  val record = outcome?.record
  val ranked = remember(record?.id) { record?.toRanked() }

  suspend fun submit() {
    val act = activity ?: return
    val rankedRecord = ranked ?: return
    val history = AppData.gameProgress.records.value.map { it.toRanked() }
    PlayGamesService.submit(act, rankedRecord, history).collect { state ->
      if (state == SubmissionState.CONFIRMED) MascotStore.registerGameCenterScoreSubmission()
    }
  }

  LaunchedEffect(record?.id) { if (record != null) runCatching { submit() } }

  val available = ranked != null && PlayGamesService.isLeaderboardAvailable(ranked)
  return PlayGamesSubmissionUi(
    isAvailable = available,
    isAuthenticated = PlayGamesService.isAuthenticated,
    state = ranked?.let { PlayGamesService.submissionState(it) },
    rank = ranked?.let { PlayGamesService.rank(it) },
    isBusy = busy,
    onShowLeaderboard = {
      val act = activity
      if (act != null && ranked != null && !busy) {
        busy = true
        scope.launch {
          runCatching { PlayGamesService.showLeaderboard(act, ranked) }
          busy = false
          if (PlayGamesService.isAuthenticated) runCatching { submit() }
        }
      }
    },
    onRetry = { scope.launch { runCatching { submit() } } },
  )
}

@Composable
private fun PlayGamesButton(ui: PlayGamesSubmissionUi, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      Modifier
        .fillMaxWidth()
        .alpha(if (ui.isBusy) 0.64f else 1f)
        .clip(RoundedCornerShape(17.dp))
        .background(GameColors.orange.copy(alpha = 0.11f))
        .clickable(enabled = !ui.isBusy, role = Role.Button, onClick = ui.onShowLeaderboard)
        .defaultMinSize(minHeight = 48.dp)
        .padding(horizontal = 15.dp, vertical = 13.dp)
        .testTag("game.result.game_center"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      if (ui.isBusy) {
        CircularProgressIndicator(color = GameColors.orange, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
      } else {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = GameColors.orange, modifier = Modifier.size(20.dp))
      }
      Text(
        ui.rank?.let { stringResource(R.string.game_play_games_result_rank_format, it.toInt()) }
          ?: stringResource(R.string.game_play_games_result_action),
        style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = GameColors.orange),
        modifier = Modifier.weight(1f),
      )
      if (!ui.isBusy) Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = GameColors.orange)
    }
    if (!ui.isAuthenticated) {
      Text(stringResource(R.string.game_play_games_submission_sign_in), style = PiyoType.caption().copy(color = colors.mutedInk))
    } else if (ui.state != null) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          stringResource(
            when (ui.state) {
              SubmissionState.IDLE -> R.string.game_center_submission_pending
              SubmissionState.SUBMITTING -> R.string.game_center_submission_submitting
              SubmissionState.CONFIRMING -> R.string.game_center_submission_confirming
              SubmissionState.CONFIRMED -> R.string.game_center_submission_submitted
              SubmissionState.FAILED -> R.string.game_center_submission_failed
              SubmissionState.UNCONFIRMED -> R.string.game_center_submission_unconfirmed
            },
          ),
          style = PiyoType.caption().copy(color = colors.mutedInk),
          modifier = Modifier.testTag("game.result.game_center_status"),
        )
        if (ui.state == SubmissionState.FAILED || ui.state == SubmissionState.UNCONFIRMED) {
          Text(
            stringResource(R.string.game_center_submission_retry),
            style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.accent),
            modifier = Modifier
              .defaultMinSize(minHeight = 44.dp)
              .clickable(role = Role.Button, onClick = ui.onRetry)
              .padding(8.dp)
              .testTag("game.result.game_center_retry"),
          )
        }
      }
    }
    Spacer(Modifier.size(0.dp))
  }
}
