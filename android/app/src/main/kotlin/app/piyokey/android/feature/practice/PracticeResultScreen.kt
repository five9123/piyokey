package app.piyokey.android.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowCircleRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.progress.RetentionClock
import app.piyokey.android.feature.discover.DeckCard
import app.piyokey.android.feature.discover.DiscoverLauncher
import app.piyokey.android.feature.discover.rememberCatalogText
import app.piyokey.android.feature.discover.rememberDeckLibraryState
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.session.SessionColors
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
import app.piyokey.android.ui.theme.L
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.core.domain.practice.PracticeRules
import java.text.NumberFormat

object PracticeResultTags {
  const val SCREEN = "practice.result.screen"
  const val SCORE = "practice.result.score.value"
  const val ACCURACY = "practice.result.accuracy.value"
  const val MISTAKES = "practice.result.mistakes.value"
  const val STARS = "practice.result.stars.value"
  const val SHARE = "practice.result.share"
  const val REVIEW = "practice.result.review"
  const val RECOMMENDATIONS = "practice.result.recommendations"
  const val RETRY = "practice.result.retry"
  const val HATCH_CONTINUE = "onboarding.hatch.result.continue"
  const val PERSISTENCE_SAVING = "onboarding.hatch.persistence.saving"
  const val PERSISTENCE_FAILURE = "onboarding.hatch.persistence.failure"
  const val PERSISTENCE_RETRY = "onboarding.hatch.persistence.retry"
  const val PERSISTENCE_BACK = "onboarding.hatch.persistence.back"
  fun recommendation(deckId: String) = "practice.result.recommendation.$deckId"
}

/** iOS `PracticeResultView` on the shared five-zone result scaffold. */
@Composable
fun PracticeResultScreen(runner: PracticeSessionRunner) {
  val config = runner.config
  val model = runner.model
  val colors = Piyo.colors
  val appNavigator = LocalAppNavigator.current
  val accuracy = model.accuracyPercent
  val mistakes = model.mistakeCount
  val items = model.completedItemCount
  val stars = PracticeRules.displayedStars(if (config.curriculumStageId != null) runner.curriculumStars else null, accuracy)
  val chains = config.chainsHatchMissions
  val finishEnabled = !chains || runner.completionPersistenceState == PracticeCompletionPersistenceState.SAVED
  val reviewItems = runner.sessionReviewItems

  BackHandler { if (finishEnabled && !chains) runner.finishFromResult() }

  SessionResultScaffold(
    navigationTitle = stringResource(R.string.practice_result_navigation_title),
    onFinish = runner::finishFromResult,
    finishTitle = stringResource(
      when {
        !chains -> R.string.result_back
        config.isFinalHatchMission -> R.string.onboarding_hatch_open_app
        else -> R.string.onboarding_hatch_next_mission
      },
    ),
    finishIcon = if (chains) Icons.Rounded.ArrowCircleRight else Icons.AutoMirrored.Rounded.ArrowBack,
    finishTestTag = if (chains) PracticeResultTags.HATCH_CONTINUE else "result.done.bottom",
    finishIsPrimary = chains,
    finishIsEnabled = finishEnabled,
    showsToolbarFinish = !chains,
    header = { reveal ->
      Row(
        Modifier
          .fillMaxWidth()
          .shadow(14.dp, RoundedCornerShape(28.dp), ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
          .clip(RoundedCornerShape(28.dp))
          .background(colors.card)
          .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(78.dp, 118.dp), contentAlignment = Alignment.Center) {
          GrowingMascot(
            mood = when {
              stars == 0 -> MascotMood.SULK
              mistakes == 0 -> MascotMood.PROUD
              stars >= 2 -> MascotMood.SATISFIED
              else -> MascotMood.HAPPY
            },
            reaction = if (mistakes == 0) MascotReaction.PerfectSession else MascotReaction.WordCompleted,
            reactionRevision = 1,
            showsNameTag = true,
            size = 60.dp,
          )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Text(
            config.title,
            style = PiyoType.caption().copy(color = colors.secondary, fontWeight = FontWeight.Bold),
            modifier = Modifier.clip(CircleShape).background(colors.accentSoft.copy(alpha = 0.52f)).padding(horizontal = 10.dp, vertical = 5.dp),
          )
          Text(
            stringResource(if (stars == 0) R.string.practice_result_try_again else R.string.practice_result_clear),
            style = PiyoType.title2().copy(fontWeight = FontWeight.Black),
            modifier = Modifier.testTag(PracticeResultTags.SCREEN),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(3) { index ->
              val progress = reveal.starProgress(index).toFloat()
              Icon(
                if (index < stars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = if (index < stars) SessionColors.Yellow else colors.mutedInk.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp).scale(0.45f + progress * 0.55f).alpha(progress.coerceIn(0f, 1f)),
              )
            }
          }
        }
      }
    },
    score = { reveal ->
      val shown = (items * reveal.scoreProgress).toInt()
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.card).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(stringResource(R.string.practice_result_items), style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.Bold))
        val formatted = remember(shown) { NumberFormat.getIntegerInstance(L.locale).format(shown) }
        Text(
          formatted,
          style = PiyoType.style(44f, FontWeight.Black).copy(color = colors.accent),
          modifier = Modifier.testTag(PracticeResultTags.SCORE).semantics { contentDescription = formatted },
        )
      }
    },
    metrics = { reveal ->
      val a = reveal.metricProgress(0)
      val m = reveal.metricProgress(1)
      val s = reveal.metricProgress(2)
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.card).padding(horizontal = 8.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SessionResultMetric(
          value = stringResource(R.string.practice_result_accuracy_value, accuracy * a),
          label = stringResource(R.string.practice_result_accuracy),
          progress = a * (accuracy / 100).coerceIn(0.0, 1.0),
          testTag = PracticeResultTags.ACCURACY,
          tint = colors.success,
          modifier = Modifier.weight(1f),
        )
        SessionMetricDivider()
        SessionResultMetric(
          value = (mistakes * m).toInt().toString(),
          label = stringResource(R.string.practice_mistakes),
          progress = m * minOf(mistakes / 10.0, 1.0),
          testTag = PracticeResultTags.MISTAKES,
          tint = if (mistakes == 0) colors.success else colors.error,
          modifier = Modifier.weight(1f),
        )
        SessionMetricDivider()
        SessionResultMetric(
          value = (stars * s).toInt().toString(),
          label = stringResource(R.string.practice_result_stars),
          progress = s * stars / 3.0,
          testTag = PracticeResultTags.STARS,
          tint = SessionColors.Yellow,
          modifier = Modifier.weight(1f),
        )
      }
    },
    review = {
      SessionResultReviewSection(reviewItems, onSeeAll = PracticeResultNavigation.openReviewDeck?.let { open -> { open(appNavigator) } })
    },
    actions = {
      if (!chains) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          SessionShareButton(shareModel(runner, stars), testTag = PracticeResultTags.SHARE)
          if (reviewItems.isNotEmpty()) {
            SessionSoftAction(
              stringResource(R.string.result_review_start),
              Icons.Rounded.Autorenew,
              PracticeResultTags.REVIEW,
              onClick = {
                appNavigator.push(
                  PracticeSessionRoute(PracticeLauncher.reviewConfig(reviewItems.map { PracticeItemSource(it.item, it.sourceDeckId) })),
                )
              },
            )
          }
          if (config.showsRecommendations) Recommendations(runner)
        }
      }
    },
    bottomBar = {
      PersistenceStatus(runner)
      if (!chains) {
        SessionPrimaryAction(
          stringResource(config.retryTitleRes),
          if (config.retryIsShuffle) Icons.Rounded.Shuffle else Icons.Rounded.Replay,
          PracticeResultTags.RETRY,
          onClick = runner::retryFromResult,
        )
      }
    },
  )
}

/** Hook for "see all" on the review list (iOS pushes `ReviewDeckView`); set by the library owner. */
object PracticeResultNavigation {
  var openReviewDeck: ((app.piyokey.android.ui.nav.Navigator) -> Unit)? = null
}

@Composable
private fun PersistenceStatus(runner: PracticeSessionRunner) {
  val colors = Piyo.colors
  when (runner.completionPersistenceState) {
    PracticeCompletionPersistenceState.SAVING -> Row(
      Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(PracticeResultTags.PERSISTENCE_SAVING),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.mutedInk)
      Spacer(Modifier.size(9.dp))
      Text(stringResource(R.string.persistence_saving), style = PiyoType.subheadline().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold))
    }
    PracticeCompletionPersistenceState.FAILED -> Column(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(17.dp))
        .background(SessionColors.Orange.copy(alpha = 0.11f))
        .border(1.dp, SessionColors.Orange.copy(alpha = 0.3f), RoundedCornerShape(17.dp))
        .padding(13.dp),
      verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Row(Modifier.testTag(PracticeResultTags.PERSISTENCE_FAILURE), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = SessionColors.Orange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.persistence_failure_title), style = PiyoType.subheadline().copy(color = SessionColors.Orange, fontWeight = FontWeight.Bold))
      }
      Text(stringResource(R.string.persistence_failure_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(
          onClick = runner::retryCompletionPersistence,
          modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.accent)
            .testTag(PracticeResultTags.PERSISTENCE_RETRY),
        ) { Text(stringResource(R.string.persistence_failure_retry), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
        TextButton(
          onClick = runner::exitAfterPersistenceFailure,
          modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.mutedInk.copy(alpha = 0.12f))
            .testTag(PracticeResultTags.PERSISTENCE_BACK),
        ) { Text(stringResource(R.string.result_back), color = colors.accent, fontWeight = FontWeight.Bold) }
      }
    }
    else -> Unit
  }
}

@Composable
private fun Recommendations(runner: PracticeSessionRunner) {
  val config = runner.config
  val sourceDeckId = config.sources.firstOrNull()?.sourceDeckId ?: return
  val catalog by AppData.catalog.catalog.collectAsState()
  val library = rememberDeckLibraryState()
  val decks = remember(catalog, library.installed.keys) { AppData.recommendations.related(sourceDeckId, config.sourceTags, catalog) }
  if (decks.isEmpty()) return
  val colors = Piyo.colors
  val text = rememberCatalogText()
  val appNavigator = LocalAppNavigator.current
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
    Row(Modifier.testTag(PracticeResultTags.RECOMMENDATIONS), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
      Spacer(Modifier.size(6.dp))
      Text(stringResource(R.string.recommendations_result_title), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
    }
    Text(stringResource(R.string.recommendations_result_subtitle), style = PiyoType.caption().copy(color = colors.mutedInk))
    decks.forEach { deck ->
      DeckCard(
        deck = deck,
        text = text,
        onClick = { DiscoverLauncher.deckDetail(appNavigator, deck.deckId) },
        isInstalled = library.isInstalled(deck.deckId),
        updateAvailable = library.needsUpdate(deck),
        modifier = Modifier.testTag(PracticeResultTags.recommendation(deck.deckId)),
      )
    }
  }
}

private fun shareModel(runner: PracticeSessionRunner, stars: Int): SessionShareCardModel {
  val config = runner.config
  val model = runner.model
  val streak = AppData.retention.streak(RetentionClock.today()).current
  val items = model.completedItemCount
  return SessionShareCardModel(
    sessionTitle = config.title,
    achievement = L.string(R.string.result_share_practice_badge),
    scoreLabel = L.string(R.string.result_share_practice_score),
    scoreValue = L.plural(R.plurals.result_share_items_value, items, items),
    metrics = listOf(
      SessionShareCardMetric("accuracy", L.string(R.string.practice_result_accuracy), L.string(R.string.practice_result_accuracy_value, model.accuracyPercent), ShareMetricIcon.SCOPE),
      SessionShareCardMetric("stars", L.string(R.string.practice_result_stars), L.string(R.string.result_share_stars_value, stars), ShareMetricIcon.STAR),
      SessionShareCardMetric("streak", L.string(R.string.result_share_streak), L.plural(R.plurals.result_share_streak_value, streak, streak), ShareMetricIcon.SEAL),
    ),
    caption = L.string(R.string.result_share_practice_caption, config.title, items),
    mascotName = MascotStore.displayName(L.string(R.string.mascot_default_name)),
  )
}
