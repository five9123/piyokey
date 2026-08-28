package app.piyokey.core.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.piyokey.core.settings.AppLanguage
import java.io.File

data class ResultShareModel(
  val language: AppLanguage,
  val title: String,
  val levelOrDeck: String,
  val score: Int,
  val maxCombo: Int,
  val streak: Int,
  val scoreLabel: String,
  val comboLabel: String,
  val streakLabel: String,
  val downloadPrompt: String,
  val caption: String,
) {
  val brand: String get() = if (language == AppLanguage.JAPANESE) "ピヨキー" else "typee"
}

object ResultShareRenderer {
  const val SIZE = 1_200

  fun render(context: Context, model: ResultShareModel): Bitmap {
    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(Color.rgb(255, 246, 241))
    paint.color = Color.rgb(242, 235, 255)
    canvas.drawCircle(1_040f, 170f, 260f, paint)
    paint.color = Color.rgb(255, 226, 236)
    canvas.drawCircle(140f, 1_050f, 310f, paint)

    val logo = loadLogo(context)
    if (logo != null) {
      canvas.drawBitmap(logo, null, Rect(84, 78, 274, 268), paint)
    } else {
      drawFallbackPiyo(canvas, paint, 179f, 173f, 94f)
    }
    drawText(canvas, paint, model.brand, 306f, 170f, 72f, Color.rgb(48, 43, 59), true)
    drawText(canvas, paint, model.title, 86f, 382f, 84f, Color.rgb(48, 43, 59), true)
    drawText(canvas, paint, model.levelOrDeck, 90f, 456f, 38f, Color.rgb(111, 104, 121), false)

    paint.color = Color.WHITE
    canvas.drawRoundRect(82f, 520f, 1_118f, 950f, 56f, 56f, paint)
    drawText(canvas, paint, model.scoreLabel, 600f, 630f, 34f, Color.rgb(111, 104, 121), false, center = true)
    drawText(canvas, paint, model.score.toString(), 600f, 760f, 126f, Color.rgb(255, 95, 145), true, center = true)
    drawMetric(canvas, paint, model.comboLabel, model.maxCombo.toString(), 340f)
    drawMetric(canvas, paint, model.streakLabel, model.streak.toString(), 860f)
    drawText(canvas, paint, model.downloadPrompt, 600f, 1_080f, 34f, Color.rgb(70, 61, 79), true, center = true)
    return bitmap
  }

  private fun drawMetric(canvas: Canvas, paint: Paint, label: String, value: String, x: Float) {
    drawText(canvas, paint, value, x, 875f, 48f, Color.rgb(48, 43, 59), true, center = true)
    drawText(canvas, paint, label, x, 922f, 27f, Color.rgb(111, 104, 121), false, center = true)
  }

  private fun drawText(
    canvas: Canvas,
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
    size: Float,
    color: Int,
    bold: Boolean,
    center: Boolean = false,
  ) {
    paint.color = color
    paint.textSize = size
    paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
    val maxWidth = if (center) 1_000f else 1_030f - x
    var visible = text
    while (visible.length > 1 && paint.measureText(visible) > maxWidth) visible = visible.dropLast(1)
    if (visible != text) visible = visible.dropLast(1) + "…"
    canvas.drawText(visible, x, baseline, paint)
  }

  private fun loadLogo(context: Context): Bitmap? {
    val id = context.resources.getIdentifier("piyokey_logo", "drawable", context.packageName)
    return if (id == 0) null else BitmapFactory.decodeResource(context.resources, id)
  }

  private fun drawFallbackPiyo(canvas: Canvas, paint: Paint, x: Float, y: Float, radius: Float) {
    paint.color = Color.rgb(255, 216, 91); canvas.drawCircle(x, y, radius, paint)
    paint.color = Color.rgb(59, 48, 64)
    canvas.drawCircle(x - 28, y - 13, 8f, paint); canvas.drawCircle(x + 28, y - 13, 8f, paint)
    paint.color = Color.rgb(255, 159, 67)
    val beak = Path().apply { moveTo(x - 18, y + 8); lineTo(x + 18, y + 8); lineTo(x, y + 28); close() }
    canvas.drawPath(beak, paint)
  }
}

class ResultShareController(private val context: Context) {
  fun save(bitmap: Bitmap, displayName: String): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
      put(MediaStore.Images.Media.MIME_TYPE, "image/png")
      if (Build.VERSION.SDK_INT >= 29) {
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PIYOKEY")
        put(MediaStore.Images.Media.IS_PENDING, 1)
      }
    }
    val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    try {
      resolver.openOutputStream(uri, "w").use { stream ->
        requireNotNull(stream)
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
      }
      if (Build.VERSION.SDK_INT >= 29) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
      }
      return uri
    } catch (error: Throwable) {
      resolver.delete(uri, null, null)
      throw error
    }
  }

  fun share(bitmap: Bitmap, caption: String): Intent {
    val directory = File(context.cacheDir, "shared_results").apply { mkdirs() }
    directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    val file = File(directory, "piyokey-result.png")
    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    return Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_TEXT, caption)
      clipData = android.content.ClipData.newUri(context.contentResolver, "PIYOKEY result", uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }
}
