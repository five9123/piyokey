package app.piyokey.android.feature.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.R
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.progress.RetentionSession
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.platform.audio.TypingSoundKeyRole
import app.piyokey.android.platform.audio.TypingSoundPreset
import app.piyokey.android.ui.mascot.GrowingMascot
import app.piyokey.android.ui.mascot.rememberMascotReduceMotion
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.session.SessionMetricDivider
import app.piyokey.android.ui.session.SessionResultMetric
import app.piyokey.android.ui.session.SessionResultScaffold
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.game.GamePhase
import app.piyokey.core.domain.game.GameResultPresentation
import app.piyokey.core.domain.game.SpacingGameEvaluation
import app.piyokey.core.domain.game.SpacingGameSession
import app.piyokey.core.domain.game.SpacingMistakeReview
import app.piyokey.core.domain.game.SpacingPassage
import app.piyokey.core.domain.game.SpacingPassageCatalog
import app.piyokey.core.domain.game.SpacingPlacementOutcome
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** The six bundled passages from `catalog/spacing_passages.json` (mirrors iOS strings). */
object SpacingPassages {
  private val cache by lazy {
    runCatching { SpacingPassageCatalog.decode(AppData.assets.read(SpacingPassageCatalog.ASSET_PATH)!!) }.getOrDefault(emptyList())
  }

  fun all(): List<SpacingPassage> = cache
}

internal val SpacingPassage.titleRes: Int
  get() = when (id) {
    "morning_commute" -> R.string.spacing_passage_morning
    "weekend_trip" -> R.string.spacing_passage_weekend
    "family_dinner" -> R.string.spacing_passage_cooking
    "library_afternoon" -> R.string.spacing_passage_library
    "language_practice" -> R.string.spacing_passage_practice
    else -> R.string.spacing_passage_judgment
  }

class SpacingPassageListRoute : Route {
  @Composable
  override fun Content() = SpacingPassageListScreen()
}

class SpacingGameRoute(val passage: SpacingPassage) : Route {
  @Composable
  override fun Content() = SpacingGameScreen(passage)
}

/** iOS `SpacingPassageListView`. */
@Composable
fun SpacingPassageListScreen() {
  val tabNav = LocalTabNavigator.current
  val appNav = LocalAppNavigator.current
  val colors = Piyo.colors
  val passages = remember { SpacingPassages.all() }
  Column(Modifier.fillMaxSize().testTag("spacing.selection.screen")) {
    GameNavigationBar(stringResource(R.string.game_mode_spacing), showsBack = tabNav.canPop, onBack = { tabNav.pop() })
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .centeredContent(Piyo.metrics.readableContentMaxWidth)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(stringResource(R.string.spacing_selection_intro), style = PiyoType.subheadline().copy(color = colors.mutedInk))
      passages.forEach { passage ->
        val shape = RoundedCornerShape(22.dp)
        Row(
          Modifier
            .fillMaxWidth()
            .shadow(9.dp, shape, ambientColor = colors.keyShadow, spotColor = colors.keyShadow)
            .clip(shape)
            .background(colors.card)
            .clickable(role = Role.Button) { appNav.push(SpacingGameRoute(passage)) }
            .padding(12.dp)
            .testTag("spacing.passage.${passage.id}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Box(
            Modifier.size(58.dp, 48.dp).clip(RoundedCornerShape(15.dp)).background(GameColors.teal.copy(alpha = 0.08f + passage.level * 0.025f)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              stringResource(R.string.spacing_selection_level, passage.level),
              style = PiyoType.caption().copy(fontWeight = FontWeight.ExtraBold, color = GameColors.teal),
            )
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(passage.titleRes), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
            Text(
              stringResource(R.string.spacing_selection_metadata, passage.characterCount, passage.spaceCount),
              style = PiyoType.caption().copy(color = colors.mutedInk),
            )
          }
          Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = GameColors.teal, modifier = Modifier.size(28.dp))
        }
      }
    }
  }
}

/** iOS `SpacingGameView`: boundary cursor + space toggle, then the spacing result. */
@Composable
fun SpacingGameScreen(passage: SpacingPassage) {
  val appNav = LocalAppNavigator.current
  val view = LocalView.current
  val reduceMotion = rememberMascotReduceMotion()
  val session = remember { SpacingGameSession(passage) }
  var version by remember { mutableIntStateOf(0) }
  var retention by remember { mutableStateOf(RetentionSession.start()) }
  var recorded by remember { mutableStateOf(false) }
  var capturedStart by remember { mutableStateOf(false) }
  var capturedCompletion by remember { mutableStateOf(false) }
  var capturedAbandonment by remember { mutableStateOf(false) }
  var shakeRevision by remember { mutableIntStateOf(0) }
  val difficulty = SpacingGameSession.analyticsDifficulty(passage.level)

  fun captureStart() {
    if (capturedStart) return
    capturedStart = true
    Telemetry.sessionStarted("game", "bundled", "not_applicable", "spacing", difficulty)
    Telemetry.setCrashContext("game", "game", "not_applicable", "spacing")
  }

  fun submit() {
    val result = session.submit(monotonicSeconds()) ?: return
    version++
    if (recorded) return
    recorded = true
    AppData.retention.record(RetentionActivityKind.GAME, retention)
    if (!capturedCompletion) {
      capturedCompletion = true
      Telemetry.gameResult("spacing", difficulty, "completed", result.score, "not_applicable", "bundled")
      Telemetry.sessionCompleted("game", "completed", result.activeDuration, result.totalBoundaryCount, "bundled", "not_applicable", "spacing", difficulty)
    }
  }

  LaunchedEffect(Unit) {
    session.start(monotonicSeconds())
    captureStart()
    version++
  }
  LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session.pause(monotonicSeconds()); version++ }
  LifecycleEventEffect(Lifecycle.Event.ON_START) { session.resume(monotonicSeconds()); version++ }
  DisposableEffect(Unit) {
    onDispose {
      if (capturedStart && !capturedCompletion && !capturedAbandonment) {
        capturedAbandonment = true
        Telemetry.sessionAbandoned("game", "user_closed", session.activeDuration(monotonicSeconds()), "bundled", "not_applicable", "spacing", difficulty)
      }
    }
  }

  version
  val result = session.result
  if (result != null) {
    SpacingResultScreen(
      passage,
      result,
      onRetry = {
        retention = RetentionSession.start()
        recorded = false
        capturedStart = false
        capturedCompletion = false
        capturedAbandonment = false
        session.restart(monotonicSeconds())
        captureStart()
        version++
      },
      onFinish = { appNav.pop() },
    )
    return
  }

  SpacingPlayContent(
    session = session,
    shakeRevision = shakeRevision,
    onClose = { appNav.pop() },
    onPrevious = { session.moveLeft(); version++ },
    onNext = { session.moveRight(); version++ },
    onToggle = {
      val outcome = session.toggleCurrentSpace()
      version++
      if (outcome == null || outcome.isCorrect) {
        SoundEngine.keyTap(TypingSoundKeyRole.CHARACTER, TypingSoundPreset.SOFT)
      } else {
        SoundEngine.mistake()
        if (!reduceMotion) shakeRevision++
      }
      if (outcome != null) {
        if (outcome.isCorrect) GameHaptics.success(view) else GameHaptics.error(view)
        announce(view, 
          app.piyokey.android.ui.theme.L.string(if (outcome.isCorrect) R.string.spacing_play_feedback_correct else R.string.spacing_play_feedback_incorrect),
        )
      }
    },
    onSubmit = ::submit,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@NonSkippableComposable
@Composable
private fun SpacingPlayContent(
  session: SpacingGameSession,
  shakeRevision: Int,
  onClose: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToggle: () -> Unit,
  onSubmit: () -> Unit,
) {
  val colors = Piyo.colors
  val metrics = Piyo.metrics
  val passage = session.passage
  val shake = remember { Animatable(0f) }
  LaunchedEffect(shakeRevision) {
    if (shakeRevision == 0) return@LaunchedEffect
    shake.snapTo(0f)
    shake.animateTo(1f, tween(360, easing = LinearEasing))
    shake.snapTo(0f)
  }
  val cursorRequester = remember { BringIntoViewRequester() }
  val finishRequester = remember { BringIntoViewRequester() }
  LaunchedEffect(session.currentBoundary) { runCatching { cursorRequester.bringIntoView() } }
  LaunchedEffect(session.isReadyToFinish) { if (session.isReadyToFinish) runCatching { finishRequester.bringIntoView() } }

  Column(
    Modifier
      .fillMaxSize()
      .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("spacing.play.screen"),
  ) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
      GameCloseButton(onClose, Modifier.align(Alignment.CenterStart))
      Text(stringResource(passage.titleRes), style = PiyoType.headline(), modifier = Modifier.align(Alignment.Center).padding(horizontal = 52.dp), maxLines = 1)
      val checkLabel = stringResource(R.string.spacing_play_check)
      Box(
        Modifier
          .align(Alignment.CenterEnd)
          .size(44.dp)
          .alpha(if (session.isReadyToFinish) 1f else 0.35f)
          .clickable(enabled = session.isReadyToFinish, role = Role.Button, onClick = onSubmit)
          .semantics { contentDescription = checkLabel }
          .testTag("spacing.submit"),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = GameColors.teal) }
    }
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .centeredContent(metrics.sessionLaneMaxWidth)
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SpaceBar, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.spacing_play_rule), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 6.dp).weight(1f))
        Stopwatch(session)
      }
      Text(stringResource(R.string.spacing_play_instruction), style = PiyoType.caption().copy(color = colors.mutedInk))
      Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
          val style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary)
          Text(
            stringResource(R.string.spacing_play_position, session.currentBoundary, session.totalBoundaryCount),
            style = style,
            modifier = Modifier.weight(1f).testTag("spacing.cursor.position"),
          )
          Text(
            stringResource(R.string.spacing_play_answered, session.answeredBoundaryCount, session.totalBoundaryCount),
            style = style,
            modifier = Modifier.testTag("spacing.progress.answered"),
          )
        }
        LinearProgressIndicator(
          progress = { session.answeredBoundaryCount.toFloat() / maxOf(session.totalBoundaryCount, 1) },
          color = GameColors.teal,
          trackColor = colors.accentSoft.copy(alpha = 0.4f),
          modifier = Modifier.fillMaxWidth().testTag("spacing.progress.bar"),
          drawStopIndicator = {},
        )
      }
      // Passage with boundary markers.
      val editorLabel = stringResource(R.string.spacing_play_editor_label)
      val draft = session.draft
      val passageShape = RoundedCornerShape(22.dp)
      FlowRow(
        Modifier
          .fillMaxWidth()
          .offset { IntOffset((sin(shake.value * PI.toFloat() * 8) * 7).dp.roundToPx(), 0) }
          .clip(passageShape)
          .background(colors.card)
          .border(1.5.dp, GameColors.teal.copy(alpha = 0.35f), passageShape)
          .padding(16.dp)
          .semantics { contentDescription = editorLabel; stateDescription = draft }
          .testTag("spacing.passage.display"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val characters = session.engine.compactCharacters
        characters.forEachIndexed { index, character ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (index == session.currentBoundary) Modifier.bringIntoViewRequester(cursorRequester) else Modifier,
          ) {
            if (index > 0) BoundaryMarker(session, index)
            Text(character, style = PiyoType.title3())
          }
        }
      }
      // Feedback + corrections.
      Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(colors.card.copy(alpha = 0.86f)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val (icon, key, tint, tag) = when (session.feedback) {
          SpacingPlacementOutcome.CORRECT -> Quad(Icons.Rounded.CheckCircle, R.string.spacing_play_feedback_correct, colors.success, "spacing.feedback.correct")
          SpacingPlacementOutcome.INCORRECT -> Quad(Icons.Rounded.Cancel, R.string.spacing_play_feedback_incorrect, colors.error, "spacing.feedback.incorrect")
          null -> Quad(Icons.Rounded.TouchApp, R.string.spacing_play_choose, colors.secondary, "spacing.feedback.ready")
        }
        Row(Modifier.weight(1f).testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
          Text(stringResource(key), style = PiyoType.body().copy(color = tint))
        }
        Text(
          stringResource(R.string.spacing_play_corrections, session.correctionCount),
          style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary),
          modifier = Modifier.testTag("spacing.corrections"),
        )
      }
      if (session.isReadyToFinish) {
        Column(
          Modifier.fillMaxWidth().bringIntoViewRequester(finishRequester).clip(RoundedCornerShape(20.dp)).background(colors.card).padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Flag, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.spacing_play_finish_title), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold))
          }
          Text(stringResource(R.string.spacing_play_finish_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
          Row(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(15.dp))
              .background(GameColors.teal)
              .clickable(role = Role.Button, onClick = onSubmit)
              .padding(vertical = 13.dp)
              .testTag("spacing.finish"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 6.dp).size(20.dp))
            Text(stringResource(R.string.spacing_play_finish), style = PiyoType.headline().copy(fontWeight = FontWeight.ExtraBold, color = Color.White))
          }
        }
      }
    }
    // Bottom controls (iOS safeAreaInset).
    Row(
      Modifier
        .fillMaxWidth()
        .background(colors.card.copy(alpha = 0.72f))
        .centeredContent(metrics.formContentMaxWidth)
        .padding(horizontal = 18.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      NavButton(Icons.Rounded.ChevronLeft, stringResource(R.string.spacing_play_previous), session.canMoveLeft, "spacing.control.previous", onPrevious, Modifier.weight(1f))
      val selected = session.currentBoundaryIsSelected
      Column(
        Modifier
          .weight(1f)
          .heightIn(min = 58.dp)
          .clip(RoundedCornerShape(17.dp))
          .background(if (selected) colors.secondary else GameColors.teal)
          .border(2.5.dp, Color.White.copy(alpha = if (selected) 0.95f else 0f), RoundedCornerShape(17.dp))
          .clickable(role = Role.Button, onClick = onToggle)
          .padding(vertical = 8.dp)
          .testTag("spacing.control.space"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
      ) {
        Icon(Icons.Rounded.SpaceBar, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
          stringResource(if (selected) R.string.spacing_play_remove_space else R.string.spacing_play_space),
          style = PiyoType.caption().copy(fontWeight = FontWeight.ExtraBold, color = Color.White),
          maxLines = 1,
        )
      }
      NavButton(Icons.Rounded.ChevronRight, stringResource(R.string.spacing_play_next), session.canMoveRight, "spacing.control.next", onNext, Modifier.weight(1f))
    }
  }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@NonSkippableComposable
@Composable
private fun BoundaryMarker(session: SpacingGameSession, boundary: Int) {
  val colors = Piyo.colors
  val isCurrent = boundary == session.currentBoundary
  val hasSpace = boundary in session.selectedBoundaries
  if (hasSpace) {
    val expected = session.engine.isCorrectBoundary(boundary)
    val shape = RoundedCornerShape(5.dp)
    Text(
      "␣",
      style = PiyoType.body().copy(fontWeight = FontWeight.Black, color = if (expected) colors.success else colors.error),
      modifier = Modifier
        .clip(shape)
        .background(if (isCurrent) GameColors.teal.copy(alpha = 0.15f) else Color.Transparent)
        .then(if (isCurrent) Modifier.border(1.5.dp, GameColors.teal, shape) else Modifier)
        .padding(horizontal = 2.dp),
    )
  } else if (isCurrent) {
    Box(Modifier.width(3.dp).height(24.dp).clip(CircleShape).background(GameColors.teal))
  }
}

@Composable
private fun NavButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  enabled: Boolean,
  tag: String,
  onClick: () -> Unit,
  modifier: Modifier,
) {
  val colors = Piyo.colors
  Column(
    modifier
      .heightIn(min = 58.dp)
      .alpha(if (enabled) 1f else 0.38f)
      .clip(RoundedCornerShape(17.dp))
      .background(colors.card)
      .border(1.dp, colors.accentSoft, RoundedCornerShape(17.dp))
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(vertical = 8.dp)
      .testTag(tag),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
  ) {
    Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
    Text(title, style = PiyoType.caption().copy(fontWeight = FontWeight.Bold))
  }
}

@NonSkippableComposable
@Composable
private fun Stopwatch(session: SpacingGameSession) {
  var tick by remember { mutableLongStateOf(0L) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(1_000)
      tick++
    }
  }
  tick
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    Icon(Icons.Rounded.Timer, contentDescription = null, tint = Piyo.colors.secondary, modifier = Modifier.size(14.dp))
    Text(
      SpacingGameSession.durationLabel(session.activeDuration(monotonicSeconds())),
      style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = Piyo.colors.secondary),
    )
  }
}

@Composable
private fun SpacingResultScreen(passage: SpacingPassage, result: SpacingGameEvaluation, onRetry: () -> Unit, onFinish: () -> Unit) {
  val colors = Piyo.colors
  SessionResultScaffold(
    navigationTitle = stringResource(R.string.spacing_result_navigation_title),
    onFinish = onFinish,
    timing = resultTiming(),
    header = {
      val shape = RoundedCornerShape(28.dp)
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
        Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
          GrowingMascot(
            mood = if (result.isPerfect) MascotMood.CHEER else MascotMood.OOPS,
            reaction = if (result.isPerfect) MascotReaction.WordCompleted else MascotReaction.Mistake,
            reactionRevision = 1,
            showsNameTag = true,
            size = 62.dp,
          )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
          Text(stringResource(passage.titleRes), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.secondary))
          Text(
            stringResource(if (result.isPerfect) R.string.spacing_result_perfect else R.string.spacing_result_complete),
            style = PiyoType.title2().copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.testTag("spacing.result.screen"),
          )
          Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val rankColor = when (GameResultPresentation.rankTint(result.rank)) {
              app.piyokey.core.domain.game.RankTint.ORANGE -> GameColors.orange
              app.piyokey.core.domain.game.RankTint.ACCENT -> colors.accent
              app.piyokey.core.domain.game.RankTint.SECONDARY -> colors.secondary
              app.piyokey.core.domain.game.RankTint.MUTED -> colors.mutedInk
            }
            Text(result.rank, style = PiyoType.style(58f, FontWeight.Black).copy(color = rankColor), modifier = Modifier.testTag("spacing.result.rank"))
            Text(
              stringResource(R.string.game_result_rank),
              style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk),
              modifier = Modifier.padding(bottom = 12.dp),
            )
          }
        }
      }
    },
    score = { reveal ->
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.card).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(stringResource(R.string.game_score), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk))
        Text(
          formatNumber((result.score * reveal.scoreProgress).toInt()),
          style = PiyoType.style(44f, FontWeight.Black).copy(color = GameColors.teal),
          modifier = Modifier.testTag("spacing.result.score").semantics { contentDescription = formatNumber(result.score) },
        )
      }
    },
    metrics = { reveal ->
      val first = reveal.metricProgress(0)
      val final = reveal.metricProgress(1)
      val corrections = reveal.metricProgress(2)
      val time = reveal.metricProgress(3)
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.card).padding(horizontal = 8.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          SessionResultMetric(
            "${(result.firstAttemptAccuracyPercent * first).roundToInt()}%", stringResource(R.string.spacing_result_first_accuracy),
            first * result.firstAttemptAccuracyPercent / 100, "spacing.result.first_accuracy", Modifier.weight(1f), colors.accent,
          )
          SessionMetricDivider()
          SessionResultMetric(
            "${(result.accuracyPercent * final).roundToInt()}%", stringResource(R.string.spacing_result_final_accuracy),
            final * result.accuracyPercent / 100, "spacing.result.final_accuracy", Modifier.weight(1f), colors.success,
          )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.mutedInk.copy(alpha = 0.2f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
          SessionResultMetric(
            (result.correctionCount * corrections).toInt().toString(), stringResource(R.string.spacing_result_corrections),
            corrections * minOf(result.correctionCount / 10.0, 1.0), "spacing.result.corrections", Modifier.weight(1f), colors.error,
          )
          SessionMetricDivider()
          SessionResultMetric(
            (result.activeDuration * time).toInt().toString(), stringResource(R.string.spacing_result_seconds),
            time * minOf(result.activeDuration / 180, 1.0), "spacing.result.duration", Modifier.weight(1f), colors.secondary,
          )
        }
      }
    },
    review = { AnswerCard(result) },
    actions = {},
    bottomBar = {
      Row(
        Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 50.dp)
          .clip(RoundedCornerShape(17.dp))
          .background(GameColors.teal)
          .clickable(role = Role.Button, onClick = onRetry)
          .padding(vertical = 14.dp)
          .testTag("spacing.result.retry"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 6.dp).size(20.dp))
        Text(stringResource(R.string.practice_result_retry), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = Color.White))
      }
    },
  )
}

@Composable
private fun AnswerCard(result: SpacingGameEvaluation) {
  val colors = Piyo.colors
  val perfect = result.firstAttemptMistakes.isEmpty()
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(colors.card).padding(17.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(
        if (perfect) Icons.Rounded.WorkspacePremium else Icons.Rounded.Autorenew,
        contentDescription = null,
        tint = if (perfect) GameColors.orange else colors.ink,
        modifier = Modifier.size(20.dp),
      )
      Text(
        stringResource(if (perfect) R.string.spacing_result_answer_perfect else R.string.spacing_result_review_title),
        style = PiyoType.headline().copy(fontWeight = FontWeight.Bold, color = if (perfect) GameColors.orange else colors.ink),
      )
    }
    if (!perfect) {
      Column(Modifier.testTag("spacing.result.review"), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        result.firstAttemptMistakes.take(8).forEach { MistakeRow(it) }
        if (result.firstAttemptMistakes.size > 8) {
          Text(
            stringResource(R.string.spacing_result_more_mistakes, result.firstAttemptMistakes.size - 8),
            style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk),
          )
        }
      }
    }
    if (result.missedSpaceCount > 0 || result.extraSpaceCount > 0) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ResultPill(stringResource(R.string.spacing_result_missed), result.missedSpaceCount)
        ResultPill(stringResource(R.string.spacing_result_extra), result.extraSpaceCount)
      }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.mutedInk.copy(alpha = 0.2f)))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(Icons.Rounded.Spellcheck, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
      Text(stringResource(R.string.spacing_result_answer_title), style = PiyoType.subheadline().copy(fontWeight = FontWeight.Bold))
    }
    Text(result.answer, style = PiyoType.body().copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.testTag("spacing.result.answer"))
  }
}

@Composable
private fun MistakeRow(mistake: SpacingMistakeReview) {
  val colors = Piyo.colors
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.accentSoft.copy(alpha = 0.18f)).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
      Text(
        mistake.attemptedText,
        style = PiyoType.body().copy(fontWeight = FontWeight.SemiBold, color = colors.error, textDecoration = TextDecoration.LineThrough),
      )
      Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(14.dp))
      Text(mistake.answerText, style = PiyoType.body().copy(fontWeight = FontWeight.SemiBold, color = colors.success))
    }
    Text(
      stringResource(if (mistake.expectedSpace) R.string.spacing_result_expected_space else R.string.spacing_result_expected_attach),
      style = PiyoType.caption2().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk),
    )
  }
}

@Composable
private fun ResultPill(label: String, value: Int) {
  val colors = Piyo.colors
  Row(
    Modifier.clip(CircleShape).background(colors.error.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Text(label, style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.error))
    Text(formatNumber(value), style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.error))
  }
}
