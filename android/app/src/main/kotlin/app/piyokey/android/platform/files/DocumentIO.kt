package app.piyokey.android.platform.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import app.piyokey.android.BuildConfig
import app.piyokey.android.R
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.ui.theme.L
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of reading an incoming document. */
sealed interface DocumentReadResult {
  data class Success(val bytes: ByteArray, val displayName: String?) : DocumentReadResult
  data object TooLarge : DocumentReadResult
  data class Failed(val error: Throwable?) : DocumentReadResult
}

/**
 * SAF / VIEW / SEND document helpers: bounded reads (≤ [MAX_BYTES]), create-document export,
 * content-feedback mail and URL opening. No storage permission is ever needed.
 */
object DocumentIO {
  /** Hard cap for an imported `.typedeck` (8 MiB). */
  const val MAX_BYTES: Long = 8L * 1024 * 1024

  const val TYPEDECK_MIME = "application/vnd.piyokey.deck+zip"

  /** Uri carried by a VIEW (`data`) or SEND (`EXTRA_STREAM`) intent. */
  fun uri(intent: Intent?): Uri? = when (intent?.action) {
    Intent.ACTION_VIEW -> intent.data
    Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
    else -> null
  }

  /** Reads [uri] fully on IO, rejecting anything over [maxBytes] before and while reading. */
  suspend fun readBounded(context: Context, uri: Uri, maxBytes: Long = MAX_BYTES): DocumentReadResult = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var displayName: String? = null
    var declaredSize: Long? = null
    runCatching {
      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
          if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
        }
      }
    }
    if (displayName == null) displayName = uri.lastPathSegment?.substringAfterLast('/')
    if ((declaredSize ?: 0) > maxBytes) return@withContext DocumentReadResult.TooLarge
    try {
      val stream = resolver.openInputStream(uri) ?: return@withContext DocumentReadResult.Failed(null)
      val bytes = stream.use { readCapped(it, maxBytes) } ?: return@withContext DocumentReadResult.TooLarge
      DocumentReadResult.Success(bytes, displayName)
    } catch (error: SecurityException) {
      DocumentReadResult.Failed(error)
    } catch (error: IOException) {
      DocumentReadResult.Failed(error)
    } catch (error: IllegalArgumentException) {
      DocumentReadResult.Failed(error)
    }
  }

  /** Reads at most [maxBytes]; returns null when the stream is longer. JVM-testable. */
  fun readCapped(input: InputStream, maxBytes: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      total += read
      if (total > maxBytes) return null
      out.write(buffer, 0, read)
    }
    return out.toByteArray()
  }

  /**
   * Contract for "save as" export; register with `rememberLauncherForActivityResult(DocumentIO.exportContract())`
   * and launch with the suggested file name, then [writeBytes] to the returned Uri.
   */
  fun exportContract(mimeType: String = TYPEDECK_MIME) = ActivityResultContracts.CreateDocument(mimeType)

  /** Contract for picking a document to import. Launch with a wildcard MIME array (typedeck MIME is rarely registered). */
  fun importContract() = ActivityResultContracts.OpenDocument()

  suspend fun writeBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("No output stream")
    }.isSuccess
  }

  /** Opens a web/app URL; returns false when nothing can handle it. */
  fun openUrl(context: Context, url: String): Boolean = openUri(context, Uri.parse(url))

  fun openUri(context: Context, uri: Uri): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }

  /** Localized feedback `mailto:` for the in-app language (iOS `ContentFeedbackLinkBuilder`). */
  fun contentFeedbackMailto(context: ContentFeedbackContext, language: AppLanguage = AppSettings.currentLanguage): String {
    val resources = L.localizedContext(app.piyokey.android.Services.context, language).resources
    val (subjectRes, promptRes) = when (context.kind) {
      ContentFeedbackKind.GENERAL -> R.string.content_feedback_email_subject_general to R.string.content_feedback_email_prompt_general
      ContentFeedbackKind.DECK_SUGGESTION -> R.string.content_feedback_email_subject_suggestion to R.string.content_feedback_email_prompt_suggestion
      ContentFeedbackKind.CONTENT_REPORT -> R.string.content_feedback_email_subject_report to R.string.content_feedback_email_prompt_report
    }
    return ContentFeedbackLinkBuilder.mailto(
      subject = resources.getString(subjectRes),
      prompt = resources.getString(promptRes),
      caution = resources.getString(R.string.content_feedback_email_caution),
      context = context,
      appVersion = BuildConfig.VERSION_NAME.removeSuffix(".dev"),
      buildNumber = BuildConfig.VERSION_CODE.toString(),
      language = language.raw,
    )
  }

  /** Opens the feedback mail; falls back to the support URL when no mail app handles it (iOS parity). */
  fun openContentFeedback(activityContext: Context, context: ContentFeedbackContext): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(contentFeedbackMailto(context)))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
      activityContext.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      openUrl(activityContext, BuildConfig.SUPPORT_URL)
    }
  }
}
