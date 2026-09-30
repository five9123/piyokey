package app.piyokey.android.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.piyokey.android.R
import app.piyokey.android.data.decks.appMeaning
import app.piyokey.android.data.decks.appReading
import app.piyokey.android.ui.mascot.LocalMascotAnimationsEnabled
import app.piyokey.android.ui.mascot.rememberMascotReduceMotion
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import app.piyokey.android.ui.theme.centeredContent
import app.piyokey.core.domain.SessionReviewItem
import app.piyokey.core.domain.practice.PracticeRules
import app.piyokey.core.domain.practice.SessionResultRevealState
import app.piyokey.core.domain.practice.SessionResultRevealTiming
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** iOS `Color.yellow` / `Color.orange` (system colours used for stars and warnings). */
object SessionColors {
  val Yellow = Color(0xFFFFCC00)
  val Orange = Color(0xFFFF9500)
}

/** Test tags matching the iOS accessibility identifiers of the shared result screen. */
object SessionResultTags {
  const val ANIMATION_STATE = "result.animation.state"
  const val DONE = "result.done"
  const val DONE_BOTTOM = "result.done.bottom"
  const val REVIEW_PERFECT = "result.review.perfect"
  const val REVIEW_LIST = "result.review.list"
  const val REVIEW_SEE_ALL = "result.review.see_all"
  fun reviewItem(itemId: String) = "result.review.item.$itemId"
}

/**
 * Drives the staged reveal (iOS `TimelineView` + `SessionResultRevealTiming`). Tapping anywhere
 * skips to the final state; actions stay disabled until the reveal ends or is skipped.
 */
@Stable
class SessionResultRevealController(val timing: SessionResultRevealTiming) {
  var elapsed by mutableDoubleStateOf(0.0)
    internal set
  var isSkipped by mutableStateOf(false)
    private set
  var actionsEnabled by mutableStateOf(false)
    private set

  val state: SessionResultRevealState get() = timing.state(elapsed, isSkipped)

  fun skip() {
    if (actionsEnabled) return
    isSkipped = true
    actionsEnabled = true
  }

  internal fun finish() {
    if (!isSkipped) actionsEnabled = true
  }
}

@Composable
fun rememberSessionResultRevealController(
  timing: SessionResultRevealTiming = SessionResultRevealTiming(),
): SessionResultRevealController {
  val controller = remember { SessionResultRevealController(timing) }
  val reduceMotion = rememberMascotReduceMotion() || !LocalMascotAnimationsEnabled.current
  LaunchedEffect(controller) {
    if (reduceMotion) {
      controller.skip()
      return@LaunchedEffect
    }
    val start = withFrameNanos { it }
    while (!controller.actionsEnabled) {
      val now = withFrameNanos { it }
      controller.elapsed = (now - start) / 1_000_000_000.0
      if (controller.elapsed >= controller.timing.totalDuration) controller.finish()
    }
  }
  return controller
}

/**
 * The F12 five-zone result screen (iOS `SessionResultView`): header, score, metrics, review and
 * actions revealed over ~2.5 s, plus a pinned bottom bar with the finish button.
 * Practice and every game result use it with their own zone contents.
 */
@Composable
fun SessionResultScaffold(
  navigationTitle: String,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
  finishTitle: String = stringResource(R.string.result_back),
  finishIcon: ImageVector? = null,
  finishTestTag: String = SessionResultTags.DONE_BOTTOM,
  finishIsPrimary: Boolean = false,
  finishIsEnabled: Boolean = true,
  showsToolbarFinish: Boolean = true,
  celebratesNewRecord: Boolean = false,
  timing: SessionResultRevealTiming = SessionResultRevealTiming(),
  header: @Composable (SessionResultRevealState) -> Unit,
  score: @Composable (SessionResultRevealState) -> Unit,
  metrics: @Composable (SessionResultRevealState) -> Unit,
  review: @Composable (SessionResultRevealState) -> Unit,
  actions: @Composable (SessionResultRevealState) -> Unit,
  bottomBar: @Composable ColumnScope.(actionsEnabled: Boolean) -> Unit = {},
) {
  val colors = Piyo.colors
  val metricsLayout = Piyo.metrics
  val reveal = rememberSessionResultRevealController(timing)
  val state = reveal.state
  val readyText = stringResource(R.string.result_animation_ready)
  val playingText = stringResource(R.string.result_animation_playing)
  val actionsEnabled = reveal.actionsEnabled

  Box(
    modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
      .pointerInput(reveal) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed && !it.previousPressed }) reveal.skip()
          }
        }
      },
  ) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      ResultTopBar(navigationTitle, showsToolbarFinish, finishIsEnabled, onFinish)
      Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .centeredContent(metricsLayout.resultContentMaxWidth)
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Box(Modifier.fillMaxWidth().scale(state.headerScale.toFloat()).alpha(state.headerOpacity.toFloat())) { header(state) }
          Box(
            Modifier
              .fillMaxWidth()
              .alpha(state.scoreProgress.toFloat())
              .padding(top = ((1 - state.scoreProgress) * 10).dp),
          ) { score(state) }
          Box(Modifier.fillMaxWidth().alpha(state.metricProgress(0).toFloat())) { metrics(state) }
          Box(Modifier.fillMaxWidth().alpha(state.lowerZoneOpacity.toFloat())) { review(state) }
          Box(Modifier.fillMaxWidth().alpha(state.lowerZoneOpacity.toFloat())) {
            actions(state)
            if (!actionsEnabled) InteractionBlocker()
          }
          Spacer(Modifier.height(24.dp))
        }
        SessionResultCelebrationCanvas(state, celebratesNewRecord, Modifier.fillMaxSize())
        if (!actionsEnabled) {
          Text(
            stringResource(R.string.result_animation_skip_hint),
            style = PiyoType.caption().copy(fontWeight = FontWeight.Bold, color = colors.mutedInk),
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 12.dp)
              .clip(CircleShape)
              .background(colors.card.copy(alpha = 0.85f))
              .padding(horizontal = 12.dp, vertical = 7.dp),
          )
        }
      }
      Column(
        Modifier
          .fillMaxWidth()
          .background(colors.card.copy(alpha = 0.72f))
          .navigationBarsPadding()
          .alpha(state.lowerZoneOpacity.toFloat()),
      ) {
        Box {
          Column(
            Modifier.centeredContent(metricsLayout.resultContentMaxWidth).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            bottomBar(actionsEnabled)
            SessionFinishButton(finishTitle, finishIcon, finishIsPrimary, finishIsEnabled, finishTestTag, onFinish)
          }
          if (!actionsEnabled) InteractionBlocker()
        }
      }
    }
    Text(
      "",
      modifier = Modifier
        .size(1.dp)
        .alpha(0.01f)
        .testTag(SessionResultTags.ANIMATION_STATE)
        .semantics {
          contentDescription = if (actionsEnabled) readyText else playingText
        },
    )
  }
}

@Composable
private fun BoxScope.InteractionBlocker() {
  Box(
    Modifier
      .matchParentSize()
      .semantics { invisibleToUser() }
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
          }
        }
      },
  )
}

@Composable
private fun ResultTopBar(title: String, showsFinish: Boolean, finishEnabled: Boolean, onFinish: () -> Unit) {
  val colors = Piyo.colors
  Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
    Text(
      title,
      style = PiyoType.headline().copy(fontWeight = FontWeight.Bold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
    )
    if (showsFinish) {
      Text(
        stringResource(R.string.result_done),
        style = PiyoType.headline().copy(
          color = if (finishEnabled) colors.accent else colors.mutedInk.copy(alpha = 0.5f),
          fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable(enabled = finishEnabled, role = Role.Button, onClick = onFinish)
          .padding(horizontal = 10.dp, vertical = 11.dp)
          .testTag(SessionResultTags.DONE),
      )
    }
  }
}

/** Full-width result CTA (iOS finish button: filled accent when primary, outlined card otherwise). */
@Composable
fun SessionFinishButton(
  title: String,
  icon: ImageVector?,
  isPrimary: Boolean,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(17.dp)
  val foreground = if (isPrimary) Color.White else colors.accent
  var m = modifier
    .fillMaxWidth()
    .defaultMinSize(minHeight = 50.dp)
    .alpha(if (enabled) 1f else 0.45f)
    .clip(shape)
    .background(if (isPrimary) colors.accent else colors.card)
  if (!isPrimary) m = m.border(1.dp, colors.accent.copy(alpha = 0.28f), shape)
  Row(
    m.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 14.dp).testTag(testTag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.padding(end = 6.dp).size(20.dp))
    Text(title, style = PiyoType.headline().copy(color = foreground, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
  }
}

/** Filled accent action (e.g. retry) used in result bottom bars. */
@Composable
fun SessionPrimaryAction(title: String, icon: ImageVector?, testTag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  SessionFinishButton(title, icon, isPrimary = true, enabled = true, testTag = testTag, onClick = onClick, modifier = modifier)
}

/** Soft accent action (e.g. "Review") used in result action zones. */
@Composable
fun SessionSoftAction(title: String, icon: ImageVector?, testTag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  Row(
    modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 48.dp)
      .clip(RoundedCornerShape(17.dp))
      .background(colors.accentSoft.copy(alpha = 0.55f))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 13.dp)
      .testTag(testTag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = 6.dp).size(20.dp))
    Text(title, style = PiyoType.headline().copy(color = colors.accent, fontWeight = FontWeight.Bold))
  }
}

/** iOS `SessionResultMetric`: value, label and a thin progress capsule. */
@Composable
fun SessionResultMetric(
  value: String,
  label: String,
  progress: Double,
  testTag: String,
  modifier: Modifier = Modifier,
  tint: Color = Piyo.colors.accent,
) {
  val colors = Piyo.colors
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text(
      value,
      style = PiyoType.title3().copy(fontWeight = FontWeight.ExtraBold),
      maxLines = 1,
      modifier = Modifier.testTag(testTag).semantics { contentDescription = value },
    )
    Text(label, style = PiyoType.caption2().copy(color = colors.mutedInk), maxLines = 1, textAlign = TextAlign.Center)
    Box(
      Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(CircleShape)
        .background(colors.accentSoft.copy(alpha = 0.34f))
        .semantics { invisibleToUser() },
    ) {
      Box(
        Modifier
          .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
          .height(4.dp)
          .clip(CircleShape)
          .background(tint),
      )
    }
  }
}

/** Thin vertical divider between metrics (iOS `Divider().frame(height: 64)`). */
@Composable
fun SessionMetricDivider() {
  Box(Modifier.width(1.dp).height(64.dp).background(Piyo.colors.mutedInk.copy(alpha = 0.2f)))
}

/**
 * iOS `SessionResultReviewSection`: a perfect banner when nothing was missed, otherwise up to five
 * collected items with the missed syllables underlined and "see all" when there are more.
 */
@Composable
fun SessionResultReviewSection(
  items: List<SessionReviewItem>,
  modifier: Modifier = Modifier,
  onSeeAll: (() -> Unit)? = null,
) {
  val colors = Piyo.colors
  val shape = RoundedCornerShape(21.dp)
  if (items.isEmpty()) {
    Row(
      modifier.fillMaxWidth().clip(shape).background(colors.card).padding(17.dp).testTag(SessionResultTags.REVIEW_PERFECT),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = SessionColors.Yellow, modifier = Modifier.size(30.dp))
      Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.practice_result_perfect), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
        Text(stringResource(R.string.practice_result_perfect_detail), style = PiyoType.caption().copy(color = colors.mutedInk))
      }
    }
    return
  }
  Column(
    modifier.fillMaxWidth().clip(shape).background(colors.card).padding(17.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      Modifier.testTag(SessionResultTags.REVIEW_LIST),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Rounded.Autorenew, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
      Text(stringResource(R.string.result_review_title), style = PiyoType.headline().copy(fontWeight = FontWeight.Bold))
    }
    items.take(5).forEach { ReviewRow(it) }
    if (items.size > 5 && onSeeAll != null) {
      Text(
        stringResource(R.string.result_review_see_all),
        style = PiyoType.subheadline().copy(color = colors.accent, fontWeight = FontWeight.Bold),
        textAlign = TextAlign.End,
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 44.dp)
          .clickable(role = Role.Button, onClick = onSeeAll)
          .padding(vertical = 12.dp)
          .testTag(SessionResultTags.REVIEW_SEE_ALL),
      )
    }
  }
}

@Composable
private fun ReviewRow(reviewItem: SessionReviewItem) {
  val colors = Piyo.colors
  val reading = reviewItem.item.appReading
  val meaning = reviewItem.item.appMeaning
  val collected = stringResource(R.string.result_review_collected)
  Row(
    Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(SessionResultTags.reviewItem(reviewItem.item.id)),
    horizontalArrangement = Arrangement.spacedBy(11.dp),
  ) {
    Icon(Icons.Rounded.Verified, contentDescription = collected, tint = colors.success, modifier = Modifier.size(22.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row {
        PracticeRules.markedCharacters(reviewItem.item.ko, reviewItem.mistakenJamoIndices).forEach { (character, mistaken) ->
          Text(
            character,
            style = PiyoType.title3().copy(
              fontWeight = FontWeight.ExtraBold,
              textDecoration = if (mistaken) TextDecoration.Underline else null,
              color = colors.ink,
            ),
          )
        }
      }
      val clue = listOfNotNull(reading, meaning).joinToString(" · ")
      if (clue.isNotEmpty()) Text(clue, style = PiyoType.caption().copy(color = colors.mutedInk))
    }
    Text(
      pluralStringResource(R.plurals.result_review_mistake_count_format, reviewItem.mistakeCount, reviewItem.mistakeCount),
      style = PiyoType.caption().copy(color = colors.error, fontWeight = FontWeight.Bold),
    )
  }
}

/** Star/heart bursts behind the header (and the new-record zone), iOS `SessionResultCelebrationCanvas`. */
@Composable
fun SessionResultCelebrationCanvas(
  reveal: SessionResultRevealState,
  celebratesNewRecord: Boolean,
  modifier: Modifier = Modifier,
) {
  val accent = Piyo.colors.accent
  val secondary = Piyo.colors.secondary
  Canvas(modifier.semantics { invisibleToUser() }) {
    drawBurst(reveal.headerParticleProgress, Offset(size.width * 0.5f, size.height * 0.19f), 12, 82.dp, SessionColors.Yellow, secondary)
    if (celebratesNewRecord) {
      drawBurst(reveal.newRecordBurstProgress, Offset(size.width * 0.5f, size.height * 0.43f), 20, 126.dp, accent, secondary)
    }
  }
}

private fun DrawScope.drawBurst(progress: Double, center: Offset, count: Int, distance: Dp, tint: Color, alternate: Color) {
  if (progress <= 0 || progress >= 1) return
  val particle = 16.dp.toPx()
  for (index in 0 until count) {
    val angle = index.toDouble() / count * PI * 2 - PI / 2
    val stagger = (index % 3) * 0.05
    val p = ((progress - stagger) / (1 - stagger)).coerceIn(0.0, 1.0)
    val radius = 14.dp.toPx() + p.toFloat() * distance.toPx()
    val point = Offset(center.x + (cos(angle) * radius).toFloat(), center.y + (sin(angle) * radius).toFloat())
    val color = (if (index % 2 == 0) tint else alternate).copy(alpha = (1 - p).toFloat())
    val path = if (index % 3 == 0) heartPath(Size(particle, particle)) else starPath(Size(particle, particle))
    translate(point.x - particle / 2, point.y - particle / 2) { drawPath(path, color) }
  }
}

/** Five-point star in [size] (SF `star.fill` stand-in). */
fun starPath(size: Size): Path {
  val path = Path()
  val cx = size.width / 2
  val cy = size.height / 2
  val outer = size.minDimension / 2
  val inner = outer * 0.45f
  for (i in 0 until 10) {
    val r = if (i % 2 == 0) outer else inner
    val a = -PI / 2 + i * PI / 5
    val x = cx + (cos(a) * r).toFloat()
    val y = cy + (sin(a) * r).toFloat()
    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  path.close()
  return path
}

/** Heart in [size] (SF `heart.fill` stand-in). */
fun heartPath(size: Size): Path {
  val w = size.width
  val h = size.height
  return Path().apply {
    moveTo(w / 2, h * 0.9f)
    cubicTo(w * -0.1f, h * 0.5f, w * 0.15f, h * -0.05f, w / 2, h * 0.28f)
    cubicTo(w * 0.85f, h * -0.05f, w * 1.1f, h * 0.5f, w / 2, h * 0.9f)
    close()
  }
}

/**
 * iOS `sessionExitCovered`: while a session is closing behind its result, cover it with the
 * background gradient so the session never flashes back.
 */
@Composable
fun SessionExitCover(isActive: Boolean, modifier: Modifier = Modifier) {
  if (!isActive) return
  val colors = Piyo.colors
  Box(
    modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(colors.backgroundTop, colors.backgroundBottom)))
      .semantics { invisibleToUser() }
      .pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
      },
  )
}
