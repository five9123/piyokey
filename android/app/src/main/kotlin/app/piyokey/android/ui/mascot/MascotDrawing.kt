package app.piyokey.android.ui.mascot

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import app.piyokey.android.data.mascot.MascotEggPattern
import app.piyokey.android.data.mascot.MascotFanColor
import app.piyokey.android.data.mascot.MascotGrowthAppearance
import app.piyokey.android.data.mascot.MascotMood
import app.piyokey.android.data.mascot.MascotMotionPolicy
import app.piyokey.android.data.mascot.MascotPose
import app.piyokey.android.data.mascot.MascotProp
import app.piyokey.android.data.mascot.MascotReaction
import app.piyokey.android.data.mascot.MascotStage
import kotlin.math.max
import kotlin.math.min

/** Fixed mascot colours (iOS `MascotView` private palette + `MascotPalette.sunny`). */
internal object MascotColors {
  private fun rgb(r: Double, g: Double, b: Double) = Color(r.toFloat(), g.toFloat(), b.toFloat())
  val sunny = rgb(1.00, 0.76, 0.18)
  val yellowTop = rgb(1.00, 0.88, 0.48)
  val yellowBottom = rgb(1.00, 0.75, 0.26)
  val line = rgb(0.91, 0.59, 0.05)
  val deep = rgb(0.91, 0.45, 0.05)
  val beak = rgb(1.00, 0.54, 0.24)
  val blush = rgb(1.00, 0.42, 0.29)
  val cream = rgb(1.00, 0.95, 0.77)
  val shell = rgb(1.00, 0.97, 0.90)
  val ink = rgb(0.23, 0.14, 0.19)
  val charcoal = rgb(0.29, 0.27, 0.38)
  val sweatBlue = rgb(0.43, 0.78, 0.94)
  val mintDark = rgb(0.12, 0.64, 0.49)
  val friendTop = rgb(0.86, 0.78, 1.0)
  val lightstickStroke = rgb(0.88, 0.23, 0.46)
  val capStroke = rgb(0.21, 0.19, 0.29)
  val suitcase = rgb(0.45, 0.72, 0.96)
  val coffee = rgb(0.66, 0.42, 0.28)
  val ketchup = rgb(0.89, 0.19, 0.15)
  val fanSky = rgb(0.36, 0.68, 0.96)
  val fanCoral = rgb(1.0, 0.42, 0.34)
  /** SwiftUI `Color.orange` / `Color.yellow`. */
  val systemOrange = rgb(1.0, 0.584, 0.0)
  val systemYellow = rgb(1.0, 0.8, 0.0)
}

/** Theme colours the mascot borrows from `AppPalette`. */
internal data class MascotThemeColors(
  val accent: Color,
  val secondary: Color,
  val success: Color,
  val error: Color,
  val mutedInk: Color,
)

/** Everything one frame needs. Offsets are in design units (`u`), angles in degrees. */
internal class MascotDrawInput(
  val u: Float,
  val sizePx: Float,
  val mood: MascotMood,
  val stage: MascotStage,
  val prop: MascotProp,
  val eggPattern: MascotEggPattern,
  val pose: MascotPose,
  val intensity: Float,
  val growth: MascotGrowthAppearance,
  val fanColor: MascotFanColor,
  val nameTag: String?,
  val speech: String?,
  val showsFriend: Boolean,
  val gazeX: Float,
  val gazeY: Float,
  val blink: Float,
  val lovePulseA: Float,
  val lovePulseB: Float,
  val leftWingAngle: Float,
  val rightWingAngle: Float,
  val flap: Float,
  val tuftAngle: Float,
  val beakWidth: Float,
  val beakHeight: Float,
  val beakIsGrit: Boolean,
  val footMaskWidth: Float,
  val extraCrack: Boolean,
  val glow: Float,
  val propRotation: Float,
  val capTossOffsetU: Float,
  val capTossRotation: Float,
  val headphoneBeat: Float,
  val reactionTilt: Float,
  val displayedReaction: MascotReaction,
  val featherProgress: Float?,
  val theme: MascotThemeColors,
  val painters: Map<ImageVector, VectorPainter>,
  val textMeasurer: TextMeasurer,
)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Draws the mascot centred in the scope (the scope is the iOS `size*1.9 × size*1.8` canvas). */
internal fun DrawScope.drawMascot(d: MascotDrawInput) {
  val c = center
  translate(c.x, c.y) {
    MascotPainterScope(this, d).drawAll()
  }
}

private class MascotPainterScope(val s: DrawScope, val d: MascotDrawInput) {
  val u = d.u
  val m = d.mood
  val t = d.theme
  val faceOffsetX = when (d.pose) {
    MascotPose.FRONT -> 0f
    MascotPose.THREE_QUARTER_LEFT -> -9f
    MascotPose.THREE_QUARTER_RIGHT -> 9f
  }

  fun drawAll() {
    val grown = d.stage == MascotStage.CHICK || d.stage == MascotStage.ROOSTER
    if (d.showsFriend && grown) friend()
    floatingBits()
    when (d.stage) {
      MascotStage.EGG -> egg(cracked = false)
      MascotStage.CRACKING -> egg(cracked = true)
      MascotStage.HATCHING -> hatching()
      MascotStage.CHICK, MascotStage.ROOSTER -> chick()
    }
    if (grown) prop()
    d.featherProgress?.let { feathers(it) }
    reactionBadge()
    speechBubble()
    nameTag()
  }

  // MARK: primitives (all coordinates in u, relative to the mascot centre)

  fun px(v: Float) = v * u

  fun topLeft(cx: Float, cy: Float, w: Float, h: Float) = Offset(px(cx - w / 2), px(cy - h / 2))

  fun oval(cx: Float, cy: Float, w: Float, h: Float, color: Color, alpha: Float = 1f) {
    s.drawOval(color, topLeft(cx, cy, w, h), Size(px(w), px(h)), alpha = alpha.coerceIn(0f, 1f))
  }

  fun oval(cx: Float, cy: Float, w: Float, h: Float, brush: Brush) {
    s.drawOval(brush, topLeft(cx, cy, w, h), Size(px(w), px(h)))
  }

  /** SwiftUI `strokeBorder`: the stroke sits inside the frame. */
  fun ovalBorder(cx: Float, cy: Float, w: Float, h: Float, color: Color, lineWidth: Float) {
    s.drawOval(
      color,
      topLeft(cx, cy, w - lineWidth, h - lineWidth),
      Size(px(w - lineWidth), px(h - lineWidth)),
      style = Stroke(px(lineWidth)),
    )
  }

  /** SwiftUI `stroke`: centred on the outline. */
  fun ovalStroke(cx: Float, cy: Float, w: Float, h: Float, color: Color, lineWidth: Float, alpha: Float = 1f) {
    s.drawOval(color, topLeft(cx, cy, w, h), Size(px(w), px(h)), alpha = alpha, style = Stroke(px(lineWidth)))
  }

  fun circle(cx: Float, cy: Float, diameter: Float, color: Color, alpha: Float = 1f) =
    oval(cx, cy, diameter, diameter, color, alpha)

  fun roundRect(cx: Float, cy: Float, w: Float, h: Float, r: Float, color: Color, alpha: Float = 1f) {
    s.drawRoundRect(color, topLeft(cx, cy, w, h), Size(px(w), px(h)), CornerRadius(px(r)), alpha = alpha)
  }

  fun roundRectStroke(cx: Float, cy: Float, w: Float, h: Float, r: Float, color: Color, lineWidth: Float, border: Boolean = false) {
    val inset = if (border) lineWidth else 0f
    s.drawRoundRect(
      color,
      topLeft(cx, cy, w - inset, h - inset),
      Size(px(w - inset), px(h - inset)),
      CornerRadius(px(r - inset / 2).coerceAtLeast(0f)),
      style = Stroke(px(lineWidth)),
    )
  }

  /** Runs [block] with the origin at the top-left of a `w × h` frame centred at (cx, cy). */
  inline fun inFrame(cx: Float, cy: Float, w: Float, h: Float, block: DrawScope.(fw: Float, fh: Float) -> Unit) {
    val tl = topLeft(cx, cy, w, h)
    s.translate(tl.x, tl.y) { block(px(w), px(h)) }
  }

  fun fillPath(path: Path, color: Color, alpha: Float = 1f) = s.drawPath(path, color, alpha = alpha)

  fun strokePath(path: Path, color: Color, lineWidth: Float, cap: StrokeCap = StrokeCap.Butt, join: StrokeJoin = StrokeJoin.Miter, alpha: Float = 1f) =
    s.drawPath(path, color, alpha = alpha, style = Stroke(width = px(lineWidth), cap = cap, join = join))

  fun heart(cx: Float, cy: Float, w: Float, h: Float, color: Color, alpha: Float = 1f, scale: Float = 1f) {
    s.scale(scale, pivot = Offset(px(cx), px(cy))) {
      inFrame(cx, cy, w, h) { fw, fh -> drawPath(MascotShapes.heart(fw, fh), color, alpha = alpha) }
    }
  }

  fun starburst(cx: Float, cy: Float, size: Float, color: Color, alpha: Float = 1f) {
    inFrame(cx, cy, size, size) { fw, fh -> drawPath(MascotShapes.starburst(fw, fh), color, alpha = alpha) }
  }

  fun sparkle(size: Float, x: Float, y: Float) = starburst(x, y, size, MascotColors.sunny)

  /** SF Symbol stand-in: a Material icon whose glyph roughly matches a `fontSize` symbol. */
  fun icon(vector: ImageVector, cx: Float, cy: Float, fontSize: Float, color: Color, alpha: Float = 1f) {
    val painter = d.painters[vector] ?: return
    val box = fontSize * 1.2f
    val tl = topLeft(cx, cy, box, box)
    s.translate(tl.x, tl.y) {
      with(painter) { draw(Size(px(box), px(box)), alpha = alpha, colorFilter = ColorFilter.tint(color)) }
    }
  }

  fun text(value: String, cx: Float, cy: Float, fontSize: Float, color: Color, weight: FontWeight = FontWeight.Black) {
    val layout = d.textMeasurer.measure(
      value,
      style = TextStyle(fontSize = with(s) { px(fontSize).toSp() }, fontWeight = weight, color = color),
    )
    s.drawText(layout, topLeft = Offset(px(cx) - layout.size.width / 2f, px(cy) - layout.size.height / 2f))
  }

  // MARK: ambient bits

  fun floatingBits() {
    when (m) {
      MascotMood.CHEER -> { sparkle(16f, -58f, -52f); sparkle(11f, 58f, -38f) }
      MascotMood.PROUD -> sparkle(13f, 50f, -46f)
      MascotMood.LOVE -> {
        heart(-56f, -46f, 15f, 13f, t.accent, 0.85f, d.lovePulseA)
        heart(56f, -32f, 10f, 9f, t.accent, 0.6f, d.lovePulseB)
      }
      MascotMood.OOPS -> text("?", 52f, -56f, 24f, t.secondary)
      MascotMood.SLEEPY -> {
        text("z", 48f, -52f, 18f, t.secondary)
        text("z", 62f, -66f, 12f, t.secondary.copy(alpha = t.secondary.alpha * 0.7f))
      }
      MascotMood.FOCUS -> inFrame(48f, -34f, 9f, 13f) { fw, fh -> drawPath(MascotShapes.sweatDrop(fw, fh), MascotColors.sweatBlue) }
      MascotMood.SULK -> text("…", 48f, -50f, 18f, t.mutedInk)
      MascotMood.SURPRISE -> text("!", 50f, -56f, 25f, t.error)
      MascotMood.GRIT -> icon(MascotIcons.flame, 50f, -48f, 16f, MascotColors.systemOrange)
      MascotMood.DIZZY -> icon(MascotIcons.tornado, 48f, -50f, 18f, t.secondary)
      MascotMood.WINK -> heart(50f, -48f, 12f, 11f, t.accent)
      MascotMood.SHY -> sparkle(9f, -50f, -42f)
      MascotMood.EUREKA -> icon(MascotIcons.lightbulb, 48f, -54f, 18f, MascotColors.sunny)
      MascotMood.SATISFIED -> icon(MascotIcons.checkCircle, 48f, -50f, 16f, t.success)
      else -> Unit
    }
  }

  // MARK: chick

  fun chick() {
    tuftOrComb()
    wings()
    feet(62f)
    val bodyW = if (d.pose == MascotPose.FRONT) 108f else 98f
    val bodyH = 108f
    oval(0f, 0f, bodyW, bodyH, Brush.verticalGradient(listOf(MascotColors.yellowTop, MascotColors.yellowBottom), startY = px(-bodyH / 2), endY = px(bodyH / 2)))
    oval(
      0f, 0f, bodyW, bodyH,
      Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.34f * d.growth.featherSheen), Color.Transparent),
        start = Offset(px(-bodyW / 2), px(-bodyH / 2)),
        end = Offset(px(bodyW / 2), px(bodyH / 2)),
      ),
    )
    ovalBorder(0f, 0f, bodyW, bodyH, MascotColors.line, 3.4f)
    oval(-24f + faceOffsetX * 0.35f, -22f, 28f, 17f, MascotColors.cream, 0.55f + 0.35f * d.growth.featherSheen)
    s.translate(px(faceOffsetX), 0f) { face() }
  }

  fun tuftOrComb() {
    if (d.stage == MascotStage.ROOSTER) {
      val cp = d.growth.combProgress
      val w = 36f + 8f * cp
      val h = 17f + 7f * cp
      val color = Color(1f, 0.42f - 0.18f * cp, 0.29f - 0.12f * cp)
      inFrame(0f, -62f, w, h) { fw, fh ->
        val path = MascotShapes.comb(fw, fh)
        drawPath(path, color)
        drawPath(path, MascotColors.deep, style = Stroke(px(2.2f)))
      }
    } else if (d.prop != MascotProp.GRAD_CAP) {
      s.rotate(d.tuftAngle, pivot = Offset(0f, px(-64f + 11f))) {
        inFrame(0f, -64f, 14f, 22f) { fw, fh ->
          drawPath(MascotShapes.tuft(fw, fh), MascotColors.line, style = Stroke(px(3.2f), cap = StrokeCap.Round))
        }
      }
    }
  }

  fun wings() {
    val flap = d.flap
    wing(-50f, -(d.leftWingAngle + flap))
    wing(50f, d.rightWingAngle + flap)
  }

  fun wing(cx: Float, degrees: Float) {
    s.rotate(degrees, pivot = Offset(px(cx), px(4f - 19f))) {
      oval(cx, 4f, 20f, 38f, MascotColors.yellowBottom)
      ovalBorder(cx, 4f, 20f, 38f, MascotColors.line, 2.8f)
    }
  }

  fun feet(centerY: Float) {
    val left = px(-20f)
    s.clipRect(left = left, top = px(centerY - 7f), right = left + px(d.footMaskWidth), bottom = px(centerY + 7f)) {
      inFrame(0f, centerY, 40f, 14f) { fw, fh ->
        drawPath(MascotShapes.feet(fw, fh), MascotColors.deep, style = Stroke(px(3f), cap = StrokeCap.Round))
      }
    }
  }

  // MARK: face

  fun face() {
    eyes()
    if (m == MascotMood.FOCUS || m == MascotMood.GRIT) brows()
    beak()
    blush(-30f, 6f)
    blush(30f, 6f)
  }

  fun eyes() {
    val dx = 16f
    val dy = -8f
    when (m) {
      MascotMood.IDLE -> { dotEye(-dx, dy, 1f); dotEye(dx, dy, 1f) }
      MascotMood.FOCUS -> { dotEye(-dx, dy + 1, 0.9f); dotEye(dx, dy + 1, 0.9f) }
      MascotMood.OOPS -> { dotEye(-dx, dy, 1f); dotEye(dx, dy, 0.65f) }
      MascotMood.HAPPY, MascotMood.CHEER, MascotMood.SATISFIED -> { happyEye(-dx, dy); happyEye(dx, dy) }
      MascotMood.SLEEPY -> { closedEye(-dx, dy); closedEye(dx, dy) }
      MascotMood.LOVE -> { heart(-dx, dy, 15f, 13f, t.accent); heart(dx, dy, 15f, 13f, t.accent) }
      MascotMood.PROUD -> { starburst(-dx, dy, 13f, MascotColors.ink); starburst(dx, dy, 13f, MascotColors.ink) }
      MascotMood.SULK -> { closedEye(-dx, dy + 2); dotEye(dx, dy + 2, 0.72f) }
      MascotMood.SURPRISE -> { dotEye(-dx, dy, 1.32f); dotEye(dx, dy, 1.32f) }
      MascotMood.GRIT -> { dotEye(-dx, dy + 2, 0.82f); dotEye(dx, dy + 2, 0.82f) }
      MascotMood.DIZZY -> { xEye(-dx, dy); xEye(dx, dy) }
      MascotMood.WINK -> { happyEye(-dx, dy); dotEye(dx, dy, 1f) }
      MascotMood.SHY -> { dotEye(-dx, dy + 3, 0.72f); dotEye(dx, dy + 3, 0.72f) }
      MascotMood.EUREKA -> { starburst(-dx, dy, 12f, MascotColors.ink); dotEye(dx, dy, 1.08f) }
    }
  }

  /** Dot eye: follows the gaze and blinks (scaleY → 0.15). */
  fun dotEye(x: Float, y: Float, scale: Float) {
    val diameter = 9f * scale * (1 + d.intensity * 0.08f)
    val cx = x + d.gazeX.coerceIn(-1f, 1f) * 3f
    val cy = y + d.gazeY.coerceIn(-1f, 1f) * 2.5f
    val sy = lerp(1f, 0.15f, d.blink)
    s.scale(1f, sy, pivot = Offset(px(cx), px(cy))) { circle(cx, cy, diameter, MascotColors.ink) }
  }

  fun arcStroke(cx: Float, cy: Float, w: Float, h: Float, start: Float, end: Float, color: Color, lineWidth: Float) {
    inFrame(cx, cy, w, h) { fw, fh ->
      drawPath(MascotShapes.arc(fw, fh, start, end), color, style = Stroke(px(lineWidth), cap = StrokeCap.Round))
    }
  }

  fun happyEye(x: Float, y: Float) = arcStroke(x, y, 13f, 9f, 200f, 340f, MascotColors.ink, 3.4f)

  fun closedEye(x: Float, y: Float) = arcStroke(x, y, 13f, 9f, 20f, 160f, MascotColors.ink, 3.4f)

  fun xEye(x: Float, y: Float) {
    for (angle in floatArrayOf(45f, -45f)) {
      s.rotate(angle, pivot = Offset(px(x), px(y))) {
        roundRect(x, y, 12f, 2.8f, 1.4f, MascotColors.ink)
      }
    }
  }

  fun brows() {
    inFrame(-16f, -18f, 14f, 5f) { fw, fh ->
      drawPath(MascotShapes.brow(fw, fh), MascotColors.ink, style = Stroke(px(3.2f), cap = StrokeCap.Round))
    }
    s.scale(-1f, 1f, pivot = Offset(px(16f), px(-18f))) {
      inFrame(16f, -18f, 14f, 5f) { fw, fh ->
        drawPath(MascotShapes.brow(fw, fh), MascotColors.ink, style = Stroke(px(3.2f), cap = StrokeCap.Round))
      }
    }
  }

  fun beak() {
    inFrame(0f, 7f, d.beakWidth, d.beakHeight) { fw, fh ->
      val path = MascotShapes.diamond(fw, fh)
      drawPath(path, if (d.beakIsGrit) Color.White else MascotColors.beak)
      drawPath(path, MascotColors.deep, style = Stroke(px(1.8f)))
    }
  }

  fun blush(x: Float, y: Float) {
    val alpha = when (m) {
      MascotMood.SHY -> 0.72f
      MascotMood.LOVE, MascotMood.HAPPY, MascotMood.CHEER -> 0.5f
      else -> 0.4f
    }
    oval(x, y, 15f, 9f, MascotColors.blush, alpha)
  }

  // MARK: growth stages

  fun egg(cracked: Boolean) {
    if (cracked) feet(66f)
    inFrame(0f, 0f, 80f, 120f) { fw, fh ->
      val path = MascotShapes.egg(fw, fh)
      drawPath(path, Brush.verticalGradient(listOf(Color.White, MascotColors.shell), startY = 0f, endY = fh))
      clipPath(path) { translate(fw / 2, fh / 2) { eggPattern() } }
      drawPath(path, MascotColors.line, style = Stroke(px(3.4f)))
    }
    oval(-18f, -34f, 22f, 30f, Color.White, 0.85f)
    if (cracked || d.extraCrack) {
      crack(-4f, -4f, 34f, 34f, 1f, 2.8f, mirrored = false, rotation = 0f)
      if (d.growth.crackProgress >= 2) crack(19f, 20f, 24f, 26f, 0.86f, 2.3f, mirrored = true, rotation = 0f)
      if (d.growth.crackProgress >= 3) crack(-22f, -29f, 19f, 20f, 0.72f, 2f, mirrored = false, rotation = 90f)
    }
  }

  fun crack(cx: Float, cy: Float, w: Float, h: Float, alpha: Float, lineWidth: Float, mirrored: Boolean, rotation: Float) {
    val pivot = Offset(px(cx), px(cy))
    s.rotate(rotation, pivot) {
      scale(if (mirrored) -1f else 1f, 1f, pivot) {
        inFrame(cx, cy, w, h) { fw, fh ->
          drawPath(
            MascotShapes.crack(fw, fh), MascotColors.line, alpha = alpha,
            style = Stroke(px(lineWidth), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }
      }
    }
  }

  /** Egg pattern overlay; the caller has already moved the origin to the egg centre. */
  fun eggPattern() {
    when (d.eggPattern) {
      MascotEggPattern.PLAIN -> Unit
      MascotEggPattern.HEARTS -> {
        heart(14f, -18f, 16f, 14f, t.accent, 0.25f)
        heart(-14f, 16f, 11f, 10f, t.accent, 0.18f)
        heart(18f, 30f, 9f, 8f, t.accent, 0.2f)
      }
      MascotEggPattern.STARS -> {
        starburst(14f, -16f, 13f, MascotColors.sunny, 0.5f)
        starburst(-14f, 14f, 9f, MascotColors.sunny, 0.38f)
        starburst(16f, 32f, 7f, MascotColors.sunny, 0.42f)
      }
      MascotEggPattern.POLKA -> {
        circle(14f, -18f, 10f, t.success, 0.3f)
        circle(-15f, 10f, 8f, t.secondary, 0.28f)
        circle(12f, 30f, 7f, t.success, 0.25f)
      }
    }
  }

  fun hatching() {
    oval(0f, -8f, 94f, 94f, Brush.verticalGradient(listOf(MascotColors.yellowTop, MascotColors.yellowBottom), startY = px(-55f), endY = px(39f)))
    ovalBorder(0f, -8f, 94f, 94f, MascotColors.line, 3.4f)
    dotEye(-14f, -16f, 1f)
    dotEye(14f, -16f, 1f)
    inFrame(0f, -4f, 14f, 11f) { fw, fh ->
      val path = MascotShapes.diamond(fw, fh)
      drawPath(path, MascotColors.beak)
      drawPath(path, MascotColors.deep, style = Stroke(px(1.8f)))
    }
    blush(-26f, -4f)
    blush(26f, -4f)
    inFrame(0f, 34f, 88f, 52f) { fw, fh ->
      val path = MascotShapes.shellCup(fw, fh)
      drawPath(path, Brush.verticalGradient(listOf(Color.White, MascotColors.shell), startY = 0f, endY = fh))
      drawPath(path, MascotColors.line, style = Stroke(px(3.2f), join = StrokeJoin.Round))
    }
  }

  // MARK: props

  val fanAccent: Color
    get() = when (d.fanColor) {
      MascotFanColor.PINK -> t.accent
      MascotFanColor.LAVENDER -> t.secondary
      MascotFanColor.MINT -> t.success
      MascotFanColor.SKY -> MascotColors.fanSky
      MascotFanColor.CORAL -> MascotColors.fanCoral
      MascotFanColor.GOLD -> MascotColors.sunny
    }

  val origin = Offset.Zero

  fun prop() {
    when (d.prop) {
      MascotProp.NONE -> Unit
      MascotProp.LIGHTSTICK -> lightstick()
      MascotProp.GRAD_CAP -> gradCap()
      MascotProp.HEADPHONES -> headphones()
      MascotProp.TRAVEL_CASE -> travelCase()
      MascotProp.COFFEE_CUP -> s.rotate(d.propRotation * 0.35f, origin) {
        icon(MascotIcons.coffee, 57f, 38f, 32f, MascotColors.coffee)
      }
      MascotProp.MICROPHONE -> s.rotate(-18f + d.propRotation, origin) {
        icon(MascotIcons.mic, 57f, -6f, 36f, t.secondary)
      }
      MascotProp.HEART_BALLOON -> heartBalloon()
      MascotProp.FOOD_PLATE -> foodPlate()
      MascotProp.RIBBON -> s.scale(lerp(1f, 1.14f, d.glow), origin) {
        icon(MascotIcons.rosette, 0f, 42f, 31f, t.accent)
      }
      MascotProp.GLASSES -> s.rotate(d.reactionTilt * 0.18f, origin) { glasses(faceOffsetX, -8f) }
      MascotProp.GAME_CENTER_TROPHY -> trophy()
    }
  }

  fun lightstick() {
    val g = d.glow
    s.scale(lerp(1f, 1.14f, g), origin) {
      rotate((if (m == MascotMood.CHEER) -14f else 8f) + d.propRotation, origin) {
        val cx = 58f
        val cy = -46f
        if (g > 0f) {
          // SwiftUI shadow(radius: 10u) approximated by a soft halo.
          val radius = px(17f + 10f)
          drawCircle(
            Brush.radialGradient(
              listOf(fanAccent.copy(alpha = 0.8f * g), Color.Transparent),
              center = Offset(px(cx), px(cy)),
              radius = radius,
            ),
            radius = radius,
            center = Offset(px(cx), px(cy)),
          )
        }
        circle(cx, cy, 34f, fanAccent, lerp(0.22f, 0.58f, g))
        inFrame(cx, cy, 20f, 18f) { fw, fh ->
          val path = MascotShapes.heart(fw, fh)
          drawPath(path, fanAccent)
          drawPath(path, MascotColors.lightstickStroke, style = Stroke(px(1.8f)))
        }
        roundRect(cx, cy + 22f, 6f, 26f, 3f, MascotColors.charcoal)
      }
    }
  }

  fun gradCap() {
    val offsetPx = MascotMotionPolicy.capOffset(px(d.capTossOffsetU), d.sizePx)
    s.rotate(d.capTossRotation, origin) {
      translate(0f, offsetPx) {
        inFrame(18f, -58f, 22f, 20f) { _, _ ->
          val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(px(22f), px(9f))
            lineTo(px(22f), px(20f))
          }
          drawPath(path, MascotColors.sunny, style = Stroke(px(2.6f), cap = StrokeCap.Round))
        }
        circle(30f, -46f, 7f, MascotColors.sunny)
        roundRect(0f, -56f, 30f, 12f, 4f, MascotColors.charcoal)
        inFrame(0f, -64f, 62f, 26f) { fw, fh ->
          val path = MascotShapes.diamond(fw, fh)
          drawPath(path, MascotColors.charcoal)
          drawPath(path, MascotColors.capStroke, style = Stroke(px(2f)))
        }
      }
    }
  }

  fun headphones() {
    s.scale(d.headphoneBeat, origin) {
      arcStroke(0f, -12f, 112f, 96f, 195f, 345f, MascotColors.charcoal, 5.5f)
      for (x in floatArrayOf(-55f, 55f)) {
        roundRect(x, -8f, 16f, 26f, 8f, t.success)
        roundRectStroke(x, -8f, 16f, 26f, 8f, MascotColors.mintDark, 2.4f, border = true)
      }
      text("♪", 56f, -54f, 17f, t.secondary)
    }
  }

  fun travelCase() {
    s.rotate(d.reactionTilt * 0.25f, origin) {
      val cx = -58f
      val cy = 36f
      roundRect(cx, cy, 35f, 43f, 7f, MascotColors.suitcase)
      roundRectStroke(cx, cy, 35f, 43f, 7f, MascotColors.charcoal, 2f)
      roundRectStroke(cx, cy - 27f, 15f, 13f, 2f, MascotColors.charcoal, 2f)
      circle(cx - 10f, cy + 24f, 5f, MascotColors.charcoal)
      circle(cx + 10f, cy + 24f, 5f, MascotColors.charcoal)
      icon(MascotIcons.airplane, cx, cy, 12f, Color.White)
    }
  }

  fun heartBalloon() {
    // iOS: the string Path is greedy, so its ZStack spans the whole canvas; the string is drawn
    // in canvas top-left coordinates and the rotation anchor is the canvas bottom centre.
    val canvasW = s.size.width
    val canvasH = s.size.height
    val anchor = Offset(0f, canvasH / 2)
    s.rotate(d.propRotation * 0.4f, anchor) {
      val ox = -canvasW / 2 + px(58f)
      val oy = -canvasH / 2 + px(-54f)
      val string = Path().apply {
        moveTo(ox + px(22f), oy + px(26f))
        quadraticTo(ox + px(34f), oy + px(54f), ox, oy + px(74f))
      }
      drawPath(string, MascotColors.charcoal, alpha = 0.55f, style = Stroke(px(1.8f)))
      inFrame(58f, -54f, 38f, 34f) { fw, fh ->
        val path = MascotShapes.heart(fw, fh)
        drawPath(path, t.accent)
        drawPath(path, Color.White, alpha = 0.7f, style = Stroke(px(1.4f)))
      }
    }
  }

  fun foodPlate() {
    s.rotate(d.propRotation * 0.25f, origin) {
      val cx = 56f
      val cy = 42f
      oval(cx, cy, 48f, 24f, Color.White)
      ovalStroke(cx, cy, 48f, 24f, MascotColors.charcoal, 2f, alpha = 0.35f)
      roundRect(cx, cy, 27f, 11f, 4f, MascotColors.ketchup)
      icon(MascotIcons.forkKnife, cx, cy - 15f, 11f, MascotColors.charcoal)
    }
  }

  /** `eyeglasses` symbol stand-in: two rounded lenses, a bridge and short temples. */
  fun glasses(cx: Float, cy: Float) {
    val color = MascotColors.charcoal
    val lw = 3f
    for (x in floatArrayOf(-16f, 16f)) {
      roundRectStroke(cx + x, cy, 20f, 15f, 6.5f, color, lw)
    }
    val bridge = Path().apply {
      moveTo(px(cx - 6f), px(cy - 2f))
      quadraticTo(px(cx), px(cy - 6f), px(cx + 6f), px(cy - 2f))
    }
    strokePath(bridge, color, lw, cap = StrokeCap.Round)
    val temples = Path().apply {
      moveTo(px(cx - 26f), px(cy - 3f)); lineTo(px(cx - 31f), px(cy - 6f))
      moveTo(px(cx + 26f), px(cy - 3f)); lineTo(px(cx + 31f), px(cy - 6f))
    }
    strokePath(temples, color, lw, cap = StrokeCap.Round)
  }

  fun trophy() {
    s.scale(lerp(1f, 1.13f, d.glow), origin) {
      rotate(d.reactionTilt * 0.22f, origin) {
        val cx = -57f
        val cy = 34f
        icon(MascotIcons.trophy, cx, cy, 31f, MascotColors.sunny)
        icon(MascotIcons.trophyOutline, cx, cy, 31f, MascotColors.charcoal, alpha = 0.65f)
        icon(MascotIcons.star, cx, cy - 4f, 8f, Color.White)
      }
    }
  }

  fun friend() {
    s.scale(0.76f, origin) {
      val cx = -74f
      val cy = 30f
      oval(cx, cy, 65f, 65f, Brush.verticalGradient(listOf(MascotColors.friendTop, t.secondary), startY = px(cy - 32.5f), endY = px(cy + 32.5f)))
      ovalStroke(cx, cy, 65f, 65f, t.secondary, 2f, alpha = 0.75f)
      circle(cx - 10f, cy - 6f, 6f, MascotColors.ink)
      circle(cx + 10f, cy - 6f, 6f, MascotColors.ink)
      inFrame(cx, cy + 7f, 11f, 8f) { fw, fh -> drawPath(MascotShapes.diamond(fw, fh), MascotColors.beak) }
    }
  }

  // MARK: overlays

  fun feathers(progress: Float) {
    val seeds = listOf(-44f to -160f, 50f to 200f, -12f to 140f)
    for ((x, spin) in seeds) {
      val cx = x * lerp(0.4f, 1.3f, progress)
      val cy = lerp(-30f, 42f, progress)
      val alpha = lerp(0.95f, 0f, progress)
      s.rotate(spin * progress, Offset(px(cx), px(cy))) {
        oval(cx, cy, 10f, 5f, MascotColors.yellowBottom, alpha)
      }
    }
  }

  fun reactionBadge() {
    val r = d.displayedReaction
    val x = -50f
    val y = -58f
    fun symbol(vector: ImageVector, color: Color, scale: Float = 1f) = icon(vector, x, y, 18f * scale, color)
    when (r) {
      MascotReaction.None -> Unit
      MascotReaction.CorrectJamo -> symbol(MascotIcons.check, t.success, 0.76f)
      MascotReaction.SyllableCompleted -> symbol(MascotIcons.seal, t.success)
      MascotReaction.WordCompleted -> symbol(MascotIcons.star, MascotColors.sunny, 1.08f)
      is MascotReaction.ComboMilestone -> comboBadge(r.count, x, y)
      MascotReaction.Mistake -> symbol(MascotIcons.arrowDownCircle, t.secondary, 0.88f)
      MascotReaction.PerfectSession -> {
        val size = 18f * 1.18f
        inFrame(x, y, size, size) { fw, fh -> drawPath(MascotShapes.crown(fw, fh), MascotColors.sunny) }
      }
      is MascotReaction.Rhythm -> symbol(if (r.count >= 15) MascotIcons.bolt else MascotIcons.musicNote, t.secondary)
      MascotReaction.Startle -> symbol(MascotIcons.exclamation, t.error, 1.12f)
      MascotReaction.GrowthTransition -> symbol(MascotIcons.sparkles, MascotColors.sunny, 1.2f)
      MascotReaction.Stretch -> symbol(MascotIcons.sun, MascotColors.systemOrange, 0.92f)
      MascotReaction.EggKnock -> symbol(MascotIcons.waveform, t.secondary, 0.9f)
      MascotReaction.ReviewGraduated -> symbol(MascotIcons.seal, t.success, 1.12f)
      MascotReaction.NewBest -> symbol(MascotIcons.trophy, MascotColors.sunny, 1.16f)
      is MascotReaction.LessonStreak -> if (r.count >= 3) {
        circle(x, y, 18f * 1.05f, t.accent)
        text("3", x, y, 12f, Color.White)
      } else {
        symbol(MascotIcons.star, t.accent)
      }
      MascotReaction.Eureka -> symbol(MascotIcons.lightbulb, MascotColors.sunny, 1.15f)
    }
  }

  fun comboBadge(count: Int, x: Float, y: Float) {
    val layout = d.textMeasurer.measure(
      count.toString(),
      style = TextStyle(fontSize = with(s) { px(18f).toSp() }, fontWeight = FontWeight.Black, color = Color.White),
    )
    val w = layout.size.width + px(12f)
    val h = layout.size.height + px(12f)
    val tl = Offset(px(x) - w / 2, px(y) - h / 2)
    s.drawRoundRect(t.accent, tl, Size(w, h), CornerRadius(h / 2))
    s.drawText(layout, topLeft = Offset(tl.x + px(6f), tl.y + px(6f)))
  }

  fun speechBubble() {
    val speech = d.speech?.takeIf { it.isNotEmpty() } ?: return
    val maxText = px(92f - 16f)
    // `.lineLimit(2).minimumScaleFactor(0.7)`: shrink from 10u down to 7u until it fits.
    var font = 10f
    var layout = measureBubble(speech, font, maxText)
    while (layout.hasVisualOverflow && font > 7f) {
      font -= 0.5f
      layout = measureBubble(speech, font, maxText)
    }
    val w = layout.size.width + px(16f)
    val h = layout.size.height + px(12f)
    val tl = Offset(px(58f) - w / 2, px(-73f) - h / 2)
    s.drawRoundRect(Color.White.copy(alpha = 0.94f), tl, Size(w, h), CornerRadius(px(10f)))
    s.drawRoundRect(t.secondary.copy(alpha = 0.32f), tl, Size(w, h), CornerRadius(px(10f)), style = Stroke(px(1.4f)))
    s.drawText(layout, topLeft = Offset(tl.x + px(8f), tl.y + px(6f)))
  }

  fun measureBubble(text: String, font: Float, maxWidth: Float) = d.textMeasurer.measure(
    text,
    style = TextStyle(
      fontSize = with(s) { px(font).toSp() },
      fontWeight = FontWeight.Bold,
      color = MascotColors.charcoal,
      textAlign = TextAlign.Center,
    ),
    overflow = TextOverflow.Ellipsis,
    maxLines = 2,
    constraints = Constraints(maxWidth = max(1, maxWidth.toInt())),
  )

  fun nameTag() {
    val tag = d.nameTag?.takeIf { it.isNotEmpty() } ?: return
    val layout = d.textMeasurer.measure(
      tag,
      style = TextStyle(fontSize = with(s) { px(10f).toSp() }, fontWeight = FontWeight.Black, color = MascotColors.charcoal),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      constraints = Constraints(maxWidth = max(1, (s.size.width - px(18f)).toInt())),
    )
    val w = layout.size.width + px(18f)
    val h = layout.size.height + px(8f)
    val tl = Offset(-w / 2, px(75f) - h / 2)
    val radius = CornerRadius(min(w, h) / 2)
    s.drawRoundRect(MascotColors.cream.copy(alpha = 0.96f), tl, Size(w, h), radius)
    s.drawRoundRect(MascotColors.line.copy(alpha = 0.55f), tl, Size(w, h), radius, style = Stroke(px(1.4f)))
    s.drawText(layout, topLeft = Offset(tl.x + px(9f), tl.y + px(4f)))
  }
}
