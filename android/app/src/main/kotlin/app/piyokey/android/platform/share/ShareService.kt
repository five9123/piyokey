package app.piyokey.android.platform.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of saving an image to the photo library. */
enum class SaveImageResult { SAVED, UNSUPPORTED, FAILED }

/**
 * Result-card sharing (iOS share sheet + "save image"). PNGs are written to `cache/share/` and
 * exposed through the `${applicationId}.files` FileProvider. Saving to Pictures uses MediaStore on
 * API 29+ without any storage permission; API 26–28 is share-only ([canSaveToPictures] = false).
 */
object ShareService {
  private const val SHARE_DIR = "share"
  const val PICTURES_SUBDIR = "typee"

  val canSaveToPictures: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

  fun authority(context: Context) = "${context.packageName}.files"

  fun pngBytes(bitmap: Bitmap): ByteArray =
    ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

  /** Writes [png] to the share cache and returns its content Uri. */
  suspend fun cachePng(context: Context, png: ByteArray, fileName: String): Uri = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > STALE_MILLIS }?.forEach { it.delete() }
    val file = File(dir, safeName(fileName, "png"))
    file.writeBytes(png)
    FileProvider.getUriForFile(context, authority(context), file)
  }

  /** Opens the system share sheet for a PNG. [context] should be an Activity context. */
  suspend fun sharePng(context: Context, png: ByteArray, fileName: String, chooserTitle: CharSequence? = null, text: String? = null): Boolean {
    val uri = cachePng(context, png, fileName)
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      text?.let { putExtra(Intent.EXTRA_TEXT, it) }
      clipData = ClipData.newRawUri(null, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(context, send, chooserTitle)
  }

  /** Shares an arbitrary cached file (e.g. an exported `.typedeck`) with [mimeType]. */
  fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: CharSequence? = null): Boolean {
    val uri = FileProvider.getUriForFile(context, authority(context), file)
    val send = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      clipData = ClipData.newRawUri(null, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return startChooser(context, send, chooserTitle)
  }

  /** Saves [png] to `Pictures/typee` (API 29+). Returns [SaveImageResult.UNSUPPORTED] below API 29. */
  suspend fun savePngToPictures(context: Context, png: ByteArray, displayName: String): SaveImageResult {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return SaveImageResult.UNSUPPORTED
    return withContext(Dispatchers.IO) {
      val resolver = context.contentResolver
      val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, safeName(displayName, "png"))
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$PICTURES_SUBDIR")
        put(MediaStore.Images.Media.IS_PENDING, 1)
      }
      val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
      val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return@withContext SaveImageResult.FAILED
      val written = runCatching {
        resolver.openOutputStream(uri)?.use { it.write(png) } ?: error("No output stream")
      }.isSuccess
      if (!written) {
        runCatching { resolver.delete(uri, null, null) }
        return@withContext SaveImageResult.FAILED
      }
      values.clear()
      values.put(MediaStore.Images.Media.IS_PENDING, 0)
      runCatching { resolver.update(uri, values, null, null) }
      SaveImageResult.SAVED
    }
  }

  private fun startChooser(context: Context, send: Intent, title: CharSequence?): Boolean {
    val chooser = Intent.createChooser(send, title).apply {
      if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
      context.startActivity(chooser)
      true
    } catch (_: ActivityNotFoundException) {
      false
    }
  }

  private const val STALE_MILLIS = 24L * 60 * 60 * 1000

  /** Keeps a file name inside the share dir (no separators) and ensures the extension. */
  internal fun safeName(name: String, extension: String): String {
    val base = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trim('.').ifEmpty { "piyokey" }.take(80)
    return if (base.endsWith(".$extension", ignoreCase = true)) base else "$base.$extension"
  }
}
