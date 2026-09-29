package app.piyokey.android.ui.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.piyokey.android.R
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.share.SaveImageResult
import app.piyokey.android.platform.share.ShareService
import app.piyokey.android.ui.theme.LightPalette
import app.piyokey.android.ui.theme.Piyo
import app.piyokey.android.ui.theme.PiyoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One metric tile on the share card (iOS `SessionShareCardMetric`). */
data class SessionShareCardMetric(val id: String, val label: String, val value: String, val icon: ShareMetricIcon)

enum class ShareMetricIcon { SCOPE, STAR, SEAL, SPEED, COMBO, TROPHY }

/** iOS `SessionShareCardModel`. */
data class SessionShareCardModel(
  val sessionTitle: String,
  val sessionSubtitle: String? = null,
  val achievement: String,
  val scoreLabel: String,
  val scoreValue: String,
  val metrics: List<SessionShareCardMetric>,
  val caption: String,
  val mascotName: String,
)

/**
 * 1200×1200 PNG share card (iOS `SessionShareCardRenderer`: 600pt × 2). Always drawn with the light
 * palette, brand logo, localized brand name and a download CTA.
 */
object SessionShareCardRenderer {
  const val LOGICAL_SIZE = 600f
  const val SCALE = 2f

  fun render(context: Context, model: SessionShareCardModel): Bitmap {
    val px = (LOGICAL_SIZE * SCALE).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(SCALE, SCALE)
    draw(canvas, context, model)
    return bitmap
  }

  private val palette = LightPalette
  private val rounded: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  private val black: Typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 900, false) else rounded

  private fun paint(color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }

  private fun draw(canvas: Canvas, context: Context, model: SessionShareCardModel) {
    val size = LOGICAL_SIZE
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(0f, 0f, size, size, palette.backgroundTop.toArgb(), palette.backgroundBottom.toArgb(), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, size, size, bg)
    canvas.drawCircle(size / 2 + 250, size / 2 - 245, 150f, paint(palette.accentSoft.copy(alpha = 0.52f)))
    canvas.drawCircle(size / 2 - 260, size / 2 + 250, 135f, paint(palette.secondary.copy(alpha = 0.11f)))

    val left = 34f
    val right = size - 34f
    var y = 34f

    // Brand lockup + achievement pill.
    drawLogo(canvas, context, RectF(left, y, left + 44, y + 44), 12f)
    val brand = text(palette.ink, 27f, black)
    canvas.drawText(context.getString(R.string.app_name), left + 54, y + 22 + textCenterOffset(brand), brand)
    val pill = text(Color.White, 18f, black)
    val pillText = fitted(model.achievement, pill, 240f)
    val pillWidth = pill.measureText(pillText) + 30
    val pillRect = RectF(right - pillWidth, y + 22 - 18, right, y + 22 + 18)
    canvas.drawRoundRect(pillRect, 18f, 18f, paint(palette.accent))
    canvas.drawText(pillText, pillRect.left + 15, pillRect.centerY() + textCenterOffset(pill), pill)
    y += 44 + 22

    // Eyebrow + title (+ subtitle pill).
    val eyebrow = text(palette.secondary, 16f, rounded)
    canvas.drawText(context.getString(R.string.result_share_eyebrow), left, y + 16, eyebrow)
    y += 16 + 5
    val titlePaint = text(palette.ink, 31f, black)
    var subtitleWidth = 0f
    val subtitlePaint = text(palette.accent, 15f, black)
    val subtitle = model.sessionSubtitle?.let { fittedScaled(it, subtitlePaint, 180f, 0.68f) }
    if (subtitle != null) subtitleWidth = subtitlePaint.measureText(subtitle) + 22 + 10
    val title = fittedScaled(model.sessionTitle, titlePaint, right - left - subtitleWidth, 0.66f)
    canvas.drawText(title, left, y + 31, titlePaint)
    if (subtitle != null) {
      val x = left + titlePaint.measureText(title) + 10
      val rect = RectF(x, y + 31 - 20, x + subtitleWidth - 10, y + 31 + 6)
      canvas.drawRoundRect(rect, 13f, 13f, paint(palette.accentSoft.copy(alpha = 0.68f)))
      canvas.drawText(subtitle, rect.left + 11, rect.centerY() + textCenterOffset(subtitlePaint), subtitlePaint)
    }
    y += 40 + 22

    // Score + static mascot (logo with name tag).
    val scoreLabel = text(palette.mutedInk, 16f, rounded)
    canvas.drawText(model.scoreLabel, left, y + 22, scoreLabel)
    val scorePaint = text(palette.accent, 70f, black)
    val score = fittedScaled(model.scoreValue, scorePaint, right - left - 160, 0.65f)
    canvas.drawText(score, left, y + 22 + 80, scorePaint)
    val mascotCx = right - 75
    val mascotCy = y + 63
    canvas.drawCircle(mascotCx, mascotCy, 63f, paint(palette.accentSoft.copy(alpha = 0.72f)))
    drawLogo(canvas, context, RectF(mascotCx - 56, mascotCy - 56, mascotCx + 56, mascotCy + 56), 28f)
    drawSparkle(canvas, mascotCx + 54, mascotCy - 49, 10f, paint(palette.secondary))
    val namePaint = text(palette.ink, 12f, black)
    val name = fittedScaled(model.mascotName, namePaint, 130f, 0.7f)
    val nameWidth = namePaint.measureText(name) + 20
    val nameRect = RectF(mascotCx - nameWidth / 2, mascotCy + 68, mascotCx + nameWidth / 2, mascotCy + 90)
    canvas.drawRoundRect(nameRect, 11f, 11f, paint(Color.White.copy(alpha = 0.9f)))
    canvas.drawText(name, nameRect.left + 10, nameRect.centerY() + textCenterOffset(namePaint), namePaint)
    y += 155 + 22

    // Metric tiles.
    val metrics = model.metrics.take(3)
    if (metrics.isNotEmpty()) {
      val gap = 10f
      val tileWidth = (right - left - gap * (metrics.size - 1)) / metrics.size
      val tileHeight = 96f
      metrics.forEachIndexed { index, metric ->
        val x = left + index * (tileWidth + gap)
        val rect = RectF(x, y, x + tileWidth, y + tileHeight)
        canvas.drawRoundRect(rect, 18f, 18f, paint(Color.White.copy(alpha = 0.76f)))
        drawMetricIcon(canvas, metric.icon, rect.centerX(), y + 22, paint(palette.secondary))
        val value = text(palette.ink, 23f, black).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(fittedScaled(metric.value, value, tileWidth - 12, 0.7f), rect.centerX(), y + 62, value)
        val label = text(palette.mutedInk, 11f, rounded).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(fittedScaled(metric.label, label, tileWidth - 12, 0.7f), rect.centerX(), y + 82, label)
      }
      y += tileHeight + 22
    }

    // Download CTA.
    val ctaRect = RectF(left, y, right, y + 64)
    canvas.drawRoundRect(ctaRect, 19f, 19f, paint(Color.White.copy(alpha = 0.86f)))
    val phone = paint(palette.accent).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f }
    canvas.drawRoundRect(RectF(left + 20, y + 20, left + 34, y + 44), 3f, 3f, phone)
    val cta = text(palette.ink, 17f, black)
    canvas.drawText(fittedScaled(context.getString(R.string.result_share_download_cta), cta, right - left - 70, 0.7f), left + 48, y + 29, cta)
    val detail = text(palette.mutedInk, 12f, rounded)
    canvas.drawText(fittedScaled(context.getString(R.string.result_share_download_detail), detail, right - left - 70, 0.7f), left + 48, y + 48, detail)
  }

  private fun text(color: Color, size: Float, typeface: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    textSize = size
    this.typeface = typeface
  }

  private fun textCenterOffset(paint: Paint): Float = -(paint.descent() + paint.ascent()) / 2

  /** Shrinks [paint] down to [minScale] to fit, then ellipsizes (SwiftUI `minimumScaleFactor` + `lineLimit(1)`). */
  private fun fittedScaled(value: String, paint: Paint, maxWidth: Float, minScale: Float): String {
    val base = paint.textSize
    val width = paint.measureText(value)
    if (width > maxWidth) paint.textSize = maxOf(base * minScale, base * maxWidth / width)
    return fitted(value, paint, maxWidth)
  }

  private fun fitted(value: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(value) <= maxWidth) return value
    var end = value.length
    while (end > 0 && paint.measureText(value.substring(0, end) + "…") > maxWidth) end--
    return value.substring(0, end) + "…"
  }

  private fun drawLogo(canvas: Canvas, context: Context, rect: RectF, radius: Float) {
    canvas.save()
    val clip = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
    canvas.clipPath(clip)
    canvas.drawColor(0xFFFDE6CF.toInt())
    runCatching {
      ContextCompat.getDrawable(context, R.drawable.piyokey_logo)?.let {
        it.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        it.draw(canvas)
      }
    }
    canvas.restore()
  }

  private fun drawSparkle(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
    val path = android.graphics.Path().apply {
      moveTo(cx, cy - r)
      quadTo(cx, cy, cx + r, cy)
      quadTo(cx, cy, cx, cy + r)
      quadTo(cx, cy, cx - r, cy)
      quadTo(cx, cy, cx, cy - r)
      close()
    }
    canvas.drawPath(path, paint)
  }

  private fun drawMetricIcon(canvas: Canvas, icon: ShareMetricIcon, cx: Float, cy: Float, paint: Paint) {
    when (icon) {
      ShareMetricIcon.STAR, ShareMetricIcon.TROPHY -> {
        val path = android.graphics.Path()
        for (i in 0 until 10) {
          val r = if (i % 2 == 0) 10f else 4.5f
          val a = -Math.PI / 2 + i * Math.PI / 5
          val x = cx + (Math.cos(a) * r).toFloat()
          val y = cy + (Math.sin(a) * r).toFloat()
          if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
      }
      ShareMetricIcon.SCOPE -> {
        val stroke = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f }
        canvas.drawCircle(cx, cy, 9f, stroke)
        canvas.drawCircle(cx, cy, 4f, stroke)
        canvas.drawLine(cx - 12, cy, cx + 12, cy, stroke)
        canvas.drawLine(cx, cy - 12, cx, cy + 12, stroke)
      }
      ShareMetricIcon.SEAL -> canvas.drawCircle(cx, cy, 10f, paint)
      ShareMetricIcon.SPEED, ShareMetricIcon.COMBO -> drawSparkle(canvas, cx, cy, 10f, paint)
    }
  }
}

private enum class ShareWork { SAVE, SHARE }

/**
 * iOS `SessionShareButton`: "Save image" + "Share" side by side, a progress line while the card
 * renders, and the save/failed status. Renders once and caches the PNG.
 */
@Composable
fun SessionShareButton(
  model: SessionShareCardModel,
  testTag: String,
  modifier: Modifier = Modifier,
  analyticsGameMode: String? = null,
) {
  val context = LocalContext.current
  val colors = Piyo.colors
  val scope = rememberCoroutineScope()
  var activeWork by remember { mutableStateOf<ShareWork?>(null) }
  var cached by remember(model) { mutableStateOf<ByteArray?>(null) }
  var renderingFailed by remember { mutableStateOf(false) }
  var saveResult by remember { mutableStateOf<SaveImageResult?>(null) }
  var job by remember { mutableStateOf<Job?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      job?.cancel()
      activeWork = null
    }
  }

  fun capture(action: String) = Telemetry.shareCompleted(action, analyticsGameMode)

  fun begin(work: ShareWork) {
    if (activeWork != null) return
    activeWork = work
    renderingFailed = false
    if (work == ShareWork.SAVE) saveResult = null
    job = scope.launch {
      try {
        delay(20)
        val png = cached ?: withContext(Dispatchers.Default) {
          runCatching { ShareService.pngBytes(SessionShareCardRenderer.render(context, model)) }.getOrNull()
        }
        if (png == null) {
          renderingFailed = true
          return@launch
        }
        cached = png
        val fileName = "piyokey-result.png"
        when (work) {
          ShareWork.SAVE -> {
            val result = ShareService.savePngToPictures(context, png, fileName)
            saveResult = result
            if (result == SaveImageResult.SAVED) capture("saved_image")
          }
          ShareWork.SHARE -> {
            val shared = ShareService.sharePng(
              context, png, fileName,
              chooserTitle = app.piyokey.android.ui.theme.L.string(R.string.result_share_preview_title),
              text = model.caption,
            )
            if (shared) capture("shared")
          }
        }
      } finally {
        activeWork = null
      }
    }
  }

  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ShareActionButton(
        title = stringResource(if (activeWork == ShareWork.SAVE) R.string.result_share_saving else R.string.result_share_save_image),
        icon = if (activeWork == ShareWork.SAVE) Icons.Rounded.HourglassTop else Icons.Rounded.Download,
        foreground = colors.accent,
        background = colors.accentSoft.copy(alpha = 0.55f),
        enabled = activeWork == null,
        testTag = "$testTag.save_image",
        onClick = { begin(ShareWork.SAVE) },
        modifier = Modifier.weight(1f),
      )
      ShareActionButton(
        title = stringResource(R.string.result_share_action),
        icon = Icons.Rounded.IosShare,
        foreground = colors.secondary,
        background = colors.secondary.copy(alpha = 0.12f),
        enabled = activeWork == null,
        testTag = testTag,
        onClick = { begin(ShareWork.SHARE) },
        modifier = Modifier.weight(1f),
      )
    }
    val work = activeWork
    when {
      work != null -> Row(
        Modifier.testTag("result.share.progress"),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.mutedInk)
        Text(
          stringResource(if (work == ShareWork.SAVE) R.string.result_share_saving else R.string.result_share_action),
          style = PiyoType.caption().copy(color = colors.mutedInk, fontWeight = FontWeight.SemiBold),
        )
      }
      renderingFailed -> Text(
        stringResource(R.string.result_share_failed),
        style = PiyoType.caption().copy(color = colors.error),
        modifier = Modifier.testTag("result.share.error"),
      )
      saveResult != null -> Text(
        stringResource(
          when (saveResult) {
            SaveImageResult.SAVED -> R.string.result_share_saved
            else -> R.string.result_share_save_failed
          },
        ),
        style = PiyoType.caption().copy(
          color = if (saveResult == SaveImageResult.SAVED) colors.success else colors.error,
          fontWeight = FontWeight.SemiBold,
        ),
        modifier = Modifier.testTag("result.share.save_status"),
      )
    }
  }
}

@Composable
private fun ShareActionButton(
  title: String,
  icon: ImageVector,
  foreground: Color,
  background: Color,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier
      .defaultMinSize(minHeight = 48.dp)
      .alpha(if (enabled) 1f else 0.6f)
      .clip(RoundedCornerShape(17.dp))
      .background(background)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(vertical = 13.dp, horizontal = 8.dp)
      .testTag(testTag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.padding(end = 6.dp).size(18.dp))
    Text(title, style = PiyoType.subheadline().copy(color = foreground, fontWeight = FontWeight.Bold), maxLines = 1)
  }
}
