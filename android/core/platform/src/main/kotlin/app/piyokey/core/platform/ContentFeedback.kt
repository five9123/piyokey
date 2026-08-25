package app.piyokey.core.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

enum class ContentFeedbackKind { PROPOSAL, REPORT, COMBINED }

enum class ContentFeedbackSource(val wireValue: String) {
  DISCOVER("discover"),
  DECK_DETAIL("deck_detail"),
  SETTINGS("settings"),
}

data class ContentFeedbackDeck(val deckId: String, val version: Int)

data class ContentFeedbackRequest(
  val kind: ContentFeedbackKind,
  val source: ContentFeedbackSource,
  val appVersion: String,
  val appBuild: Long,
  val language: String,
  val deck: ContentFeedbackDeck? = null,
)

data class ContentFeedbackMessage(val subject: String, val body: String)

data class ContentFeedbackCopy(
  val proposalSubject: String,
  val reportSubject: String,
  val combinedSubject: String,
  val proposalBody: String,
  val reportBody: String,
  val combinedBody: String,
  val metadataHeading: String,
  val typeLabel: String,
  val sourceLabel: String,
  val appLabel: String,
  val languageLabel: String,
  val deckLabel: String,
)

object ContentFeedbackComposer {
  fun compose(request: ContentFeedbackRequest, copy: ContentFeedbackCopy): ContentFeedbackMessage {
    val subject = when (request.kind) {
      ContentFeedbackKind.PROPOSAL -> copy.proposalSubject
      ContentFeedbackKind.REPORT -> copy.reportSubject
      ContentFeedbackKind.COMBINED -> copy.combinedSubject
    }
    val body = when (request.kind) {
      ContentFeedbackKind.PROPOSAL -> copy.proposalBody
      ContentFeedbackKind.REPORT -> copy.reportBody
      ContentFeedbackKind.COMBINED -> copy.combinedBody
    }
    val metadata = buildList {
      add(copy.metadataHeading)
      add("${copy.typeLabel}: ${request.kind.name.lowercase()}")
      add("${copy.sourceLabel}: ${request.source.wireValue}")
      add("${copy.appLabel}: ${request.appVersion} (${request.appBuild})")
      add("${copy.languageLabel}: ${request.language}")
      request.deck?.let { add("${copy.deckLabel}: ${it.deckId} / v${it.version}") }
    }
    return ContentFeedbackMessage(subject, "$body\n\n${metadata.joinToString("\n")}")
  }
}

class ContentFeedbackController(
  private val context: Context,
  private val supportUrl: String = DEFAULT_SUPPORT_URL,
) {
  fun open(request: ContentFeedbackRequest): Boolean {
    val message = ContentFeedbackComposer.compose(request, localizedCopy())
    val mailUri = Uri.parse("mailto:$CONTACT_ADDRESS").buildUpon()
      .appendQueryParameter("subject", message.subject)
      .appendQueryParameter("body", message.body)
      .build()
    val mail = Intent(Intent.ACTION_SENDTO, mailUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      context.startActivity(mail)
      return true
    } catch (_: ActivityNotFoundException) {
      // A public HTTPS route remains available on devices without a configured mail handler.
    } catch (_: SecurityException) {
      // Treat OEM intent restrictions like an unavailable mail handler.
    }
    return try {
      context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
      true
    } catch (_: ActivityNotFoundException) {
      false
    } catch (_: SecurityException) {
      false
    }
  }

  private fun localizedCopy(): ContentFeedbackCopy = ContentFeedbackCopy(
    proposalSubject = context.getString(R.string.feedback_proposal_subject),
    reportSubject = context.getString(R.string.feedback_report_subject),
    combinedSubject = context.getString(R.string.feedback_combined_subject),
    proposalBody = context.getString(R.string.feedback_proposal_body),
    reportBody = context.getString(R.string.feedback_report_body),
    combinedBody = context.getString(R.string.feedback_combined_body),
    metadataHeading = context.getString(R.string.feedback_metadata_heading),
    typeLabel = context.getString(R.string.feedback_metadata_type),
    sourceLabel = context.getString(R.string.feedback_metadata_source),
    appLabel = context.getString(R.string.feedback_metadata_app),
    languageLabel = context.getString(R.string.feedback_metadata_language),
    deckLabel = context.getString(R.string.feedback_metadata_deck),
  )

  companion object {
    const val CONTACT_ADDRESS: String = "contact@typee.app"
    const val DEFAULT_SUPPORT_URL: String = "https://hancoweb.vercel.app/support"
  }
}
