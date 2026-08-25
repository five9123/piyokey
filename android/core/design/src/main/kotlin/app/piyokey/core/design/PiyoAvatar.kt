package app.piyokey.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.piyokey.core.settings.PiyoAccessory
import app.piyokey.core.settings.PiyoSessionAppearance

private val Shell = Color(0xFFFFFBF1)
private val ShellShadow = Color(0xFFE9D7C2)
private val Chick = Color(0xFFFFD85B)
private val ChickShade = Color(0xFFF3B83E)
private val Ink = Color(0xFF3B3040)
private val Pink = Color(0xFFFF7FA3)

@Composable
fun PiyoAvatar(
  appearance: PiyoSessionAppearance,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
    when (appearance.stage) {
      app.piyokey.core.settings.PiyoGrowthStage.EGG -> drawEgg(crack = false, hatch = false)
      app.piyokey.core.settings.PiyoGrowthStage.CRACKED_EGG -> drawEgg(crack = true, hatch = false)
      app.piyokey.core.settings.PiyoGrowthStage.HATCHING -> drawEgg(crack = true, hatch = true)
      app.piyokey.core.settings.PiyoGrowthStage.CHICK -> drawChick()
    }
    appearance.accessory?.let(::drawAccessory)
  }
}

private fun DrawScope.drawEgg(crack: Boolean, hatch: Boolean) {
  val unit = size.minDimension / 100f
  drawOval(ShellShadow, topLeft = Offset(21 * unit, 13 * unit), size = Size(60 * unit, 78 * unit))
  drawOval(Shell, topLeft = Offset(19 * unit, 9 * unit), size = Size(60 * unit, 78 * unit))
  if (crack) {
    val path = Path().apply {
      moveTo(35 * unit, 43 * unit); lineTo(45 * unit, 50 * unit)
      lineTo(53 * unit, 39 * unit); lineTo(64 * unit, 48 * unit)
    }
    drawPath(path, Ink, style = Stroke(3 * unit, cap = StrokeCap.Round))
  }
  if (hatch) {
    drawCircle(Chick, 19 * unit, Offset(50 * unit, 45 * unit))
    drawCircle(Ink, 2.5f * unit, Offset(43 * unit, 41 * unit))
    drawCircle(Ink, 2.5f * unit, Offset(57 * unit, 41 * unit))
    drawBeak(unit, Offset(50 * unit, 48 * unit))
  }
}

private fun DrawScope.drawChick() {
  val unit = size.minDimension / 100f
  drawOval(ChickShade, Offset(20 * unit, 26 * unit), Size(64 * unit, 62 * unit))
  drawOval(Chick, Offset(17 * unit, 21 * unit), Size(64 * unit, 62 * unit))
  drawCircle(Chick, 26 * unit, Offset(49 * unit, 34 * unit))
  drawCircle(Ink, 3 * unit, Offset(40 * unit, 31 * unit))
  drawCircle(Ink, 3 * unit, Offset(58 * unit, 31 * unit))
  drawCircle(Color.White, 1.1f * unit, Offset(39 * unit, 30 * unit))
  drawCircle(Color.White, 1.1f * unit, Offset(57 * unit, 30 * unit))
  drawBeak(unit, Offset(49 * unit, 41 * unit))
  drawOval(Pink.copy(alpha = .45f), Offset(29 * unit, 40 * unit), Size(8 * unit, 4 * unit))
  drawOval(Pink.copy(alpha = .45f), Offset(61 * unit, 40 * unit), Size(8 * unit, 4 * unit))
  drawOval(ChickShade, Offset(8 * unit, 48 * unit), Size(24 * unit, 14 * unit))
  drawOval(ChickShade, Offset(67 * unit, 48 * unit), Size(24 * unit, 14 * unit))
}

private fun DrawScope.drawBeak(unit: Float, center: Offset) {
  val path = Path().apply {
    moveTo(center.x - 6 * unit, center.y)
    lineTo(center.x + 6 * unit, center.y)
    lineTo(center.x, center.y + 7 * unit)
    close()
  }
  drawPath(path, Color(0xFFFF9F43))
}

private fun DrawScope.drawAccessory(accessory: PiyoAccessory) {
  val unit = size.minDimension / 100f
  when (accessory) {
    PiyoAccessory.STREAK_RIBBON -> {
      drawCircle(Pink, 8 * unit, Offset(76 * unit, 59 * unit))
      drawRect(Pink, Offset(72 * unit, 63 * unit), Size(8 * unit, 18 * unit))
    }
    PiyoAccessory.STAR_BERET -> {
      drawOval(Color(0xFFA88AF4), Offset(27 * unit, 7 * unit), Size(45 * unit, 18 * unit))
      drawCircle(Color(0xFFA88AF4), 4 * unit, Offset(50 * unit, 7 * unit))
    }
    PiyoAccessory.RAINBOW_BOW -> {
      drawOval(Pink, Offset(20 * unit, 18 * unit), Size(18 * unit, 13 * unit))
      drawOval(Color(0xFFA88AF4), Offset(37 * unit, 18 * unit), Size(18 * unit, 13 * unit))
      drawCircle(Color(0xFFFFCB54), 5 * unit, Offset(37 * unit, 25 * unit))
    }
    PiyoAccessory.TOPIK_GLASSES -> {
      drawCircle(Ink, 10 * unit, Offset(39 * unit, 32 * unit), style = Stroke(3 * unit))
      drawCircle(Ink, 10 * unit, Offset(59 * unit, 32 * unit), style = Stroke(3 * unit))
      drawLine(Ink, Offset(49 * unit, 32 * unit), Offset(50 * unit, 32 * unit), 3 * unit)
    }
    PiyoAccessory.CHAMPION_TROPHY -> {
      drawRect(Color(0xFFFFCB54), Offset(69 * unit, 51 * unit), Size(17 * unit, 20 * unit))
      drawRect(Ink, Offset(75 * unit, 70 * unit), Size(5 * unit, 12 * unit))
    }
    PiyoAccessory.AUTO, PiyoAccessory.NONE -> Unit
  }
}
