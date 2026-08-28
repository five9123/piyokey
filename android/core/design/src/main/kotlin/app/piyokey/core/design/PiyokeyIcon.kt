package app.piyokey.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

enum class PiyokeyIconKind {
  HOME,
  DISCOVER,
  PRACTICE,
  GAMES,
  PROFILE,
  SETTINGS,
  LOCK,
  TROPHY,
  WARDROBE,
  PLAY,
  SPEAKER,
  BACK,
  CLOSE,
  CHECK,
  FLOW,
  RAIN,
  INITIALS,
  DICTATION,
  WORD_MATCH,
  SPACING,
  MOVE_UP,
  MOVE_DOWN,
}

@Composable
fun PiyokeyIcon(
  kind: PiyokeyIconKind,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = Color.Unspecified,
) {
  val resolvedTint = if (tint == Color.Unspecified) MaterialIconTint else tint
  Canvas(
    modifier = modifier.clearAndSetSemantics {
      contentDescription?.let { this.contentDescription = it }
    },
  ) {
    val unit = size.minDimension / 24f
    val stroke = 1.9f * unit
    val color = resolvedTint
    when (kind) {
      PiyokeyIconKind.HOME -> drawHome(color, unit, stroke)
      PiyokeyIconKind.DISCOVER -> drawDiscover(color, unit, stroke)
      PiyokeyIconKind.PRACTICE -> drawPractice(color, unit, stroke)
      PiyokeyIconKind.GAMES -> drawGames(color, unit, stroke)
      PiyokeyIconKind.PROFILE -> drawProfile(color, unit, stroke)
      PiyokeyIconKind.SETTINGS -> drawSettings(color, unit, stroke)
      PiyokeyIconKind.LOCK -> drawLock(color, unit, stroke)
      PiyokeyIconKind.TROPHY -> drawTrophy(color, unit, stroke)
      PiyokeyIconKind.WARDROBE -> drawWardrobe(color, unit, stroke)
      PiyokeyIconKind.PLAY -> drawPlay(color, unit)
      PiyokeyIconKind.SPEAKER -> drawSpeaker(color, unit, stroke)
      PiyokeyIconKind.BACK -> drawBack(color, unit, stroke)
      PiyokeyIconKind.CLOSE -> drawClose(color, unit, stroke)
      PiyokeyIconKind.CHECK -> drawCheck(color, unit, stroke)
      PiyokeyIconKind.FLOW -> drawArrow(color, unit, stroke, vertical = false)
      PiyokeyIconKind.RAIN -> drawRain(color, unit, stroke)
      PiyokeyIconKind.INITIALS -> drawInitials(color, unit, stroke)
      PiyokeyIconKind.DICTATION -> drawDictation(color, unit, stroke)
      PiyokeyIconKind.WORD_MATCH -> drawWord(color, unit, stroke)
      PiyokeyIconKind.SPACING -> drawSpacing(color, unit, stroke)
      PiyokeyIconKind.MOVE_UP -> drawMove(color, unit, stroke, up = true)
      PiyokeyIconKind.MOVE_DOWN -> drawMove(color, unit, stroke, up = false)
    }
  }
}

private val MaterialIconTint
  @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurface

private fun DrawScope.line(color: Color, unit: Float, stroke: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
  drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke, StrokeCap.Round)
}

private fun DrawScope.drawHome(color: Color, unit: Float, stroke: Float) {
  val path = Path().apply {
    moveTo(3f * unit, 11f * unit); lineTo(12f * unit, 3.5f * unit); lineTo(21f * unit, 11f * unit)
    moveTo(5.5f * unit, 9.5f * unit); lineTo(5.5f * unit, 20f * unit); lineTo(18.5f * unit, 20f * unit); lineTo(18.5f * unit, 9.5f * unit)
    moveTo(10f * unit, 20f * unit); lineTo(10f * unit, 14f * unit); lineTo(14f * unit, 14f * unit); lineTo(14f * unit, 20f * unit)
  }
  drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawDiscover(color: Color, unit: Float, stroke: Float) {
  drawCircle(color, 6.5f * unit, Offset(10f * unit, 10f * unit), style = Stroke(stroke))
  line(color, unit, stroke, 14.8f, 14.8f, 21f, 21f)
}

private fun DrawScope.drawPractice(color: Color, unit: Float, stroke: Float) {
  drawRoundRect(
    color,
    Offset(2.5f * unit, 5f * unit),
    Size(19f * unit, 14f * unit),
    CornerRadius(2.5f * unit),
    style = Stroke(stroke),
  )
  listOf(6f, 10f, 14f, 18f).forEach { x -> drawCircle(color, 0.9f * unit, Offset(x * unit, 10f * unit)) }
  listOf(7.5f, 12f, 16.5f).forEach { x -> drawCircle(color, 0.9f * unit, Offset(x * unit, 14f * unit)) }
  line(color, unit, stroke, 8f, 17f, 16f, 17f)
}

private fun DrawScope.drawGames(color: Color, unit: Float, stroke: Float) {
  val path = Path().apply {
    moveTo(12f * unit, 2.8f * unit)
    lineTo(14.7f * unit, 8.3f * unit); lineTo(20.8f * unit, 9.2f * unit)
    lineTo(16.4f * unit, 13.5f * unit); lineTo(17.4f * unit, 19.7f * unit)
    lineTo(12f * unit, 16.8f * unit); lineTo(6.6f * unit, 19.7f * unit)
    lineTo(7.6f * unit, 13.5f * unit); lineTo(3.2f * unit, 9.2f * unit)
    lineTo(9.3f * unit, 8.3f * unit); close()
  }
  drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
}

private fun DrawScope.drawProfile(color: Color, unit: Float, stroke: Float) {
  drawCircle(color, 4.2f * unit, Offset(12f * unit, 7.5f * unit), style = Stroke(stroke))
  drawArc(
    color,
    205f,
    130f,
    false,
    topLeft = Offset(4f * unit, 11f * unit),
    size = Size(16f * unit, 13f * unit),
    style = Stroke(stroke, cap = StrokeCap.Round),
  )
}

private fun DrawScope.drawSettings(color: Color, unit: Float, stroke: Float) {
  drawCircle(color, 4.2f * unit, Offset(12f * unit, 12f * unit), style = Stroke(stroke))
  drawCircle(color, 1.2f * unit, Offset(12f * unit, 12f * unit))
  repeat(8) { index ->
    val angle = Math.PI * index / 4.0
    val sx = 12f + kotlin.math.cos(angle).toFloat() * 6.3f
    val sy = 12f + kotlin.math.sin(angle).toFloat() * 6.3f
    val ex = 12f + kotlin.math.cos(angle).toFloat() * 9f
    val ey = 12f + kotlin.math.sin(angle).toFloat() * 9f
    line(color, unit, stroke, sx, sy, ex, ey)
  }
}

private fun DrawScope.drawLock(color: Color, unit: Float, stroke: Float) {
  drawRoundRect(
    color,
    Offset(5f * unit, 10f * unit),
    Size(14f * unit, 11f * unit),
    CornerRadius(2f * unit),
    style = Stroke(stroke),
  )
  drawArc(
    color,
    180f,
    180f,
    false,
    topLeft = Offset(7.5f * unit, 3f * unit),
    size = Size(9f * unit, 12f * unit),
    style = Stroke(stroke, cap = StrokeCap.Round),
  )
}

private fun DrawScope.drawTrophy(color: Color, unit: Float, stroke: Float) {
  drawRoundRect(
    color,
    Offset(7f * unit, 3f * unit),
    Size(10f * unit, 11f * unit),
    CornerRadius(2f * unit),
    style = Stroke(stroke),
  )
  drawArc(color, 90f, 180f, false, Offset(2.5f * unit, 5f * unit), Size(7.5f * unit, 8f * unit), style = Stroke(stroke))
  drawArc(color, 270f, 180f, false, Offset(14f * unit, 5f * unit), Size(7.5f * unit, 8f * unit), style = Stroke(stroke))
  line(color, unit, stroke, 12f, 14f, 12f, 19f)
  line(color, unit, stroke, 8f, 20f, 16f, 20f)
}

private fun DrawScope.drawWardrobe(color: Color, unit: Float, stroke: Float) {
  drawArc(color, 200f, 250f, false, Offset(9f * unit, 2.5f * unit), Size(6f * unit, 6.5f * unit), style = Stroke(stroke))
  val path = Path().apply {
    moveTo(12f * unit, 8f * unit); lineTo(3f * unit, 18f * unit); lineTo(21f * unit, 18f * unit); close()
  }
  drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
}

private fun DrawScope.drawPlay(color: Color, unit: Float) {
  val path = Path().apply { moveTo(7f * unit, 4f * unit); lineTo(20f * unit, 12f * unit); lineTo(7f * unit, 20f * unit); close() }
  drawPath(path, color)
}

private fun DrawScope.drawSpeaker(color: Color, unit: Float, stroke: Float) {
  val body = Path().apply {
    moveTo(3f * unit, 9f * unit); lineTo(7.5f * unit, 9f * unit)
    lineTo(13f * unit, 4.5f * unit); lineTo(13f * unit, 19.5f * unit)
    lineTo(7.5f * unit, 15f * unit); lineTo(3f * unit, 15f * unit); close()
  }
  drawPath(body, color, style = Stroke(stroke, join = StrokeJoin.Round))
  drawArc(
    color,
    -58f,
    116f,
    false,
    topLeft = Offset(11f * unit, 7f * unit),
    size = Size(7f * unit, 10f * unit),
    style = Stroke(stroke, cap = StrokeCap.Round),
  )
  drawArc(
    color,
    -52f,
    104f,
    false,
    topLeft = Offset(10f * unit, 4f * unit),
    size = Size(12f * unit, 16f * unit),
    style = Stroke(stroke, cap = StrokeCap.Round),
  )
}

private fun DrawScope.drawBack(color: Color, unit: Float, stroke: Float) {
  line(color, unit, stroke, 19f, 12f, 5f, 12f); line(color, unit, stroke, 5f, 12f, 11f, 6f); line(color, unit, stroke, 5f, 12f, 11f, 18f)
}

private fun DrawScope.drawClose(color: Color, unit: Float, stroke: Float) {
  line(color, unit, stroke, 5f, 5f, 19f, 19f); line(color, unit, stroke, 19f, 5f, 5f, 19f)
}

private fun DrawScope.drawCheck(color: Color, unit: Float, stroke: Float) {
  line(color, unit, stroke, 4f, 12f, 9.5f, 17.5f); line(color, unit, stroke, 9.5f, 17.5f, 20f, 6.5f)
}

private fun DrawScope.drawArrow(color: Color, unit: Float, stroke: Float, vertical: Boolean) {
  if (vertical) {
    line(color, unit, stroke, 12f, 3f, 12f, 21f); line(color, unit, stroke, 12f, 21f, 7f, 16f); line(color, unit, stroke, 12f, 21f, 17f, 16f)
  } else {
    line(color, unit, stroke, 3f, 12f, 21f, 12f); line(color, unit, stroke, 21f, 12f, 16f, 7f); line(color, unit, stroke, 21f, 12f, 16f, 17f)
  }
}

private fun DrawScope.drawRain(color: Color, unit: Float, stroke: Float) {
  drawArrow(color, unit, stroke, vertical = true)
  drawCircle(color, 1.3f * unit, Offset(5f * unit, 5f * unit)); drawCircle(color, 1.3f * unit, Offset(19f * unit, 6f * unit))
}

private fun DrawScope.drawInitials(color: Color, unit: Float, stroke: Float) {
  line(color, unit, stroke, 5f, 6f, 19f, 6f); line(color, unit, stroke, 12f, 6f, 12f, 11f)
  line(color, unit, stroke, 7f, 11f, 17f, 11f); line(color, unit, stroke, 9f, 11f, 6f, 18f); line(color, unit, stroke, 15f, 11f, 18f, 18f)
}

private fun DrawScope.drawDictation(color: Color, unit: Float, stroke: Float) {
  line(color, unit, stroke, 10f, 4f, 10f, 17f); line(color, unit, stroke, 10f, 4f, 19f, 2.5f); line(color, unit, stroke, 19f, 2.5f, 19f, 14f)
  drawCircle(color, 3.2f * unit, Offset(6.8f * unit, 18f * unit), style = Stroke(stroke)); drawCircle(color, 3.2f * unit, Offset(15.8f * unit, 15f * unit), style = Stroke(stroke))
}

private fun DrawScope.drawWord(color: Color, unit: Float, stroke: Float) {
  drawRoundRect(
    color,
    Offset(3f * unit, 4f * unit),
    Size(18f * unit, 15f * unit),
    CornerRadius(3f * unit),
    style = Stroke(stroke),
  )
  line(color, unit, stroke, 7f, 9f, 17f, 9f); line(color, unit, stroke, 7f, 14f, 14f, 14f)
}

private fun DrawScope.drawSpacing(color: Color, unit: Float, stroke: Float) {
  line(color, unit, stroke, 3f, 12f, 10f, 12f); line(color, unit, stroke, 3f, 12f, 6f, 9f); line(color, unit, stroke, 3f, 12f, 6f, 15f)
  line(color, unit, stroke, 21f, 12f, 14f, 12f); line(color, unit, stroke, 21f, 12f, 18f, 9f); line(color, unit, stroke, 21f, 12f, 18f, 15f)
}

private fun DrawScope.drawMove(color: Color, unit: Float, stroke: Float, up: Boolean) {
  val tip = if (up) 4f else 20f
  val tail = if (up) 20f else 4f
  val wing = if (up) 9f else 15f
  line(color, unit, stroke, 12f, tail, 12f, tip)
  line(color, unit, stroke, 12f, tip, 6f, wing)
  line(color, unit, stroke, 12f, tip, 18f, wing)
}
