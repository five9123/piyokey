package app.piyokey.android.feature.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.piyokey.android.ui.session.starPath
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.core.domain.practice.PracticeRules
import app.piyokey.core.domain.practice.TargetSyllableProgress
import app.piyokey.core.domain.practice.TargetSyllableProgress.State
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Test tags = iOS accessibility identifiers of the practice session. */
object PracticeTags {
  const val END = "practice.end"
  const val SETTINGS = "practice.session_settings"
  const val OVERALL_PROGRESS = "practice.overall_progress"
  const val TARGET_CARD = "practice.target.card"
  const val TARGET_VALUE = "practice.target.value"
  const val MEANING = "practice.meaning.value"
  const val READING = "practice.reading.value"
  const val SPEAK = "practice.speak_target"
  const val JAMO_PROGRESS = "practice.jamo_progress.value"
  const val JAMO_ACTIVE = "practice.jamo.active"
  const val COMPOSITION_CARD = "practice.composition.card"
  const val ENTERED_TEXT = "practice.entered_text.value"
  const val MISTAKES = "practice.mistakes.value"
  const val SCREEN = "practice.screen"
  fun jamo(index: Int) = "practice.jamo.$index"
}

private val SwiftEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * iOS `TargetSyllableProgressView`: the target's syllables, green when completed, outlined while in
 * progress, spaces drawn as a space-key glyph; scaled down to fit the card width.
 */
@Composable
fun TargetSyllableProgressView(units: List<TargetSyllableProgress>, fontScale: Float, progressValue: String, modifier: Modifier = Modifier) {
  val displayed = units.joinToString("") { it.character }
  val baseWidth = units.sumOf { (if (it.isWhitespace) 28 else 42).toDouble() }.toFloat() + max(units.size - 1, 0) * 5f + 8f
  val activeId = units.firstOrNull { it.state != State.COMPLETED }?.id
  BoxWithConstraints(
    modifier
      .fillMaxWidth()
      .height((54 * fontScale).dp)
      .testTag(PracticeTags.TARGET_VALUE)
      .semantics {
        contentDescription = displayed
        stateDescription = progressValue
      },
    contentAlignment = Alignment.Center,
  ) {
    val scale = if (baseWidth > 0) min(fontScale, max(0.01f, maxWidth.value / baseWidth)) else fontScale
    Row(
      Modifier.padding(horizontal = (4 * scale).dp, vertical = (3 * scale).dp),
      horizontalArrangement = Arrangement.spacedBy((5 * scale).dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      units.forEach { unit ->
        if (unit.isWhitespace) SpaceIndicator(unit.state, unit.id == activeId, scale)
        else SyllableChip(unit, scale)
      }
    }
  }
}

@Composable
private fun SyllableChip(unit: TargetSyllableProgress, scale: Float) {
  val colors = Piyo.colors
  val background by animateColorAsState(
    when (unit.state) {
      State.PENDING -> Color.Transparent
      State.IN_PROGRESS -> colors.accentSoft.copy(alpha = 0.58f)
      State.COMPLETED -> colors.success
    },
    spring(dampingRatio = 0.62f, stiffness = 800f),
    label = "syllable",
  )
  val shape = RoundedCornerShape((13 * scale).dp)
  var m = Modifier
    .defaultMinSize(minWidth = (38 * scale).dp, minHeight = (48 * scale).dp)
    .background(background, shape)
  if (unit.state == State.IN_PROGRESS) m = m.border((2.5f * scale).dp, colors.accent, shape)
  Box(m.padding(horizontal = (2 * scale).dp), contentAlignment = Alignment.Center) {
    Text(
      unit.character,
      fontSize = (34 * scale).sp,
      fontWeight = FontWeight.Bold,
      color = if (unit.state == State.COMPLETED) Color.White else colors.ink,
      maxLines = 1,
      modifier = Modifier.semantics { invisibleToUser() },
    )
  }
}

@Composable
private fun SpaceIndicator(state: State, isActive: Boolean, scale: Float) {
  val colors = Piyo.colors
  val completed = state == State.COMPLETED
  Box(Modifier.width((28 * scale).dp).height((48 * scale).dp), contentAlignment = Alignment.Center) {
    val shape = RoundedCornerShape((7 * scale).dp)
    Box(
      Modifier
        .size((24 * scale).dp, (20 * scale).dp)
        .background(if (completed) colors.success else colors.accentSoft.copy(alpha = if (isActive) 0.58f else 0.32f), shape)
        .border(((if (isActive) 2.5f else 1f) * scale).dp, if (isActive) colors.accent else colors.mutedInk.copy(alpha = 0.18f), shape),
      contentAlignment = Alignment.Center,
    ) {
      val stroke = if (completed) Color.White else colors.mutedInk.copy(alpha = if (isActive) 0.9f else 0.58f)
      Canvas(Modifier.size((15 * scale).dp, (7 * scale).dp)) {
        val path = Path().apply {
          moveTo(0f, 0f); lineTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width, 0f)
        }
        drawPath(path, stroke, style = Stroke(width = 2.dp.toPx() * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
      }
    }
  }
}

/** iOS `JamoProgressTrack`: chips for every jamo, filled when typed, the active one outlined, auto-scrolled. */
@Composable
fun JamoProgressTrack(sequence: List<Char>, completedCount: Int, progressValue: String, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val typography = Piyo.metrics.typographyScale
  val chipWidth = 30f * typography
  BoxWithConstraints(
    modifier
      .fillMaxWidth()
      .height((38 * typography).dp)
      .clipToBounds()
      .testTag(PracticeTags.JAMO_PROGRESS)
      .semantics { stateDescription = progressValue },
  ) {
    val available = maxWidth.value
    val target = PracticeRules.jamoTrackOffset(available, sequence.size, completedCount, chipWidth)
    val offset by animateFloatAsState(target, tween(180, easing = SwiftEaseOut), label = "track")
    val overflows = PracticeRules.jamoTrackContentWidth(sequence.size, chipWidth) > available
    val density = LocalDensity.current
    Row(
      Modifier
        .wrapContentWidth(Alignment.Start, unbounded = true)
        .align(Alignment.CenterStart)
        .offset { IntOffset(with(density) { offset.dp.roundToPx() }, 0) }
        .padding(horizontal = PracticeRules.JAMO_TRACK_SAFE_INSET.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(PracticeRules.JAMO_CHIP_SPACING.dp),
    ) {
      sequence.forEachIndexed { index, jamo ->
        val done = index < completedCount
        val shape = RoundedCornerShape(9.dp)
        var m = Modifier
          .size(chipWidth.dp, (34 * typography).dp)
          .background(if (done) colors.accent else colors.accentSoft.copy(alpha = 0.38f), shape)
        if (index == completedCount) m = m.border(2.dp, colors.accent, shape)
        Box(m.testTag(if (index == completedCount) PracticeTags.JAMO_ACTIVE else PracticeTags.jamo(index)), contentAlignment = Alignment.Center) {
          Text(jamo.toString(), fontSize = Piyo.sp(17f), fontWeight = FontWeight.Bold, color = if (done) Color.White else colors.ink)
        }
      }
    }
    if (overflows) {
      val card = colors.card
      Box(
        Modifier.align(Alignment.CenterStart).width(22.dp).fillMaxSize()
          .alpha(if (completedCount > 0) 1f else 0f)
          .background(Brush.horizontalGradient(listOf(card, card.copy(alpha = 0f)))),
      )
      Box(
        Modifier.align(Alignment.CenterEnd).width(22.dp).fillMaxSize()
          .alpha(if (completedCount < sequence.size) 1f else 0f)
          .background(Brush.horizontalGradient(listOf(card.copy(alpha = 0f), card))),
      )
    }
  }
}

/**
 * iOS `SyllableAssemblyPreview`: the composing syllable in a soft circle. A join (e.g. ㄱ+ㅏ→가)
 * briefly shows the parts sliding together before the merged syllable pops.
 */
@Composable
fun SyllableAssemblyPreview(
  text: String,
  incomingJamo: Char?,
  revision: Int,
  shouldAnimateJoin: Boolean,
  displayScale: Float,
  modifier: Modifier = Modifier,
) {
  val colors = Piyo.colors
  var previousText by remember { mutableStateOf(text) }
  var parts by remember { mutableStateOf<Pair<String, String>?>(null) }
  val merge = remember { Animatable(0f) }
  val resultScale = remember { Animatable(1f) }

  LaunchedEffect(revision) {
    val prior = previousText
    previousText = text
    if (text.isEmpty()) {
      parts = null
      resultScale.snapTo(1f)
      return@LaunchedEffect
    }
    if (!shouldAnimateJoin || prior.isEmpty() || incomingJamo == null) {
      parts = null
      resultScale.snapTo(0.9f)
      resultScale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 1500f))
      return@LaunchedEffect
    }
    parts = prior to incomingJamo.toString()
    merge.snapTo(0f)
    merge.animateTo(1f, tween(100, easing = LinearEasing))
    parts = null
    resultScale.snapTo(0.88f)
    resultScale.animateTo(1.08f, spring(dampingRatio = 0.54f, stiffness = 6000f))
    resultScale.animateTo(1f, tween(30))
  }

  Box(
    modifier.size((112 * displayScale).dp, (100 * displayScale).dp).semantics { invisibleToUser() },
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size((96 * displayScale).dp).background(colors.accentSoft.copy(alpha = 0.66f), CircleShape))
    val size = (46 * displayScale * Piyo.fontScale).sp
    val shown = parts
    if (shown != null) {
      val m = merge.value
      Row(Modifier.alpha(1f - m * 0.84f)) {
        Text(shown.first, fontSize = size, fontWeight = FontWeight.Bold, color = colors.ink, maxLines = 1,
          modifier = Modifier.offset(x = (-3 + 12 * m).dp))
        Text(shown.second, fontSize = size, fontWeight = FontWeight.Bold, color = colors.ink, maxLines = 1,
          modifier = Modifier.offset(x = (3 - 12 * m).dp))
      }
    } else {
      Text(
        text.ifEmpty { "…" },
        fontSize = size,
        fontWeight = FontWeight.Bold,
        color = colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.scale(resultScale.value),
      )
    }
  }
}

/** iOS `CompletionCelebrationView`: 18 sparkles/stars/dots bursting from the centre for ~0.6 s. */
@Composable
fun CompletionCelebration(revision: Int, modifier: Modifier = Modifier) {
  val colors = Piyo.colors
  val burst = remember(revision) { Animatable(0f) }
  val opacity = remember(revision) { Animatable(1f) }
  LaunchedEffect(revision) {
    burst.animateTo(1f, tween(420, easing = SwiftEaseOut))
  }
  LaunchedEffect(revision) {
    kotlinx.coroutines.delay(410)
    opacity.animateTo(0f, tween(160))
  }
  val tints = listOf(colors.accent, colors.secondary, Color(0xFFFF9500), colors.success)
  Canvas(modifier.fillMaxSize().semantics { invisibleToUser() }) {
    val progress = burst.value
    if (opacity.value <= 0f) return@Canvas
    val shortest = min(size.width, size.height) / density
    val base = min(max(shortest * 0.23f, 92f), 148f).dp.toPx()
    val count = 18
    for (index in 0 until count) {
      val angle = index * 2 * PI / count - PI / 2 + if (index % 2 == 0) 0.08 else -0.05
      val distance = base * when (index % 3) { 0 -> 1f; 1 -> 0.78f; else -> 0.58f }
      val radius = progress * distance
      val particle = when (index % 3) { 0 -> 22f; 1 -> 16f; else -> 9f }.dp.toPx() *
        (0.25f + progress * (if (index % 3 == 0) 1.05f else 0.82f))
      val center = Offset(size.width / 2 + (cos(angle) * radius).toFloat(), size.height / 2 + (sin(angle) * radius).toFloat())
      val color = tints[index % 4].copy(alpha = opacity.value)
      translate(center.x - particle / 2, center.y - particle / 2) {
        rotate(progress * index * 29f, Offset(particle / 2, particle / 2)) {
          when (index % 3) {
            0 -> drawPath(sparklePath(Size(particle, particle)), color)
            1 -> drawPath(starPath(Size(particle, particle)), color)
            else -> drawCircle(color, particle / 2, Offset(particle / 2, particle / 2))
          }
        }
      }
    }
  }
}

internal fun sparklePath(size: Size): Path {
  val cx = size.width / 2
  val cy = size.height / 2
  val r = size.minDimension / 2
  return Path().apply {
    moveTo(cx, cy - r)
    quadraticTo(cx, cy, cx + r, cy)
    quadraticTo(cx, cy, cx, cy + r)
    quadraticTo(cx, cy, cx - r, cy)
    quadraticTo(cx, cy, cx, cy - r)
    close()
  }
}

/** Horizontal shake offset (iOS `ShakeEffect`: 7 pt × sin(t·π·3)). */
fun shakeOffset(step: Float): Float = 7f * sin(step * PI.toFloat() * 3f)

/** Draws a translucent error flash over a rounded card. */
fun Modifier.errorFlash(alpha: Float, color: Color, cornerRadius: Float): Modifier = drawWithContent {
  drawContent()
  if (alpha > 0f) {
    drawRoundRect(color.copy(alpha = alpha), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.dp.toPx()))
  }
}
