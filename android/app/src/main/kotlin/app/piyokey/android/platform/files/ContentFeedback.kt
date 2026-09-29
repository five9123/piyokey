package app.piyokey.android.platform.files

import java.net.URLEncoder

/** iOS `ContentFeedbackKind`. */
enum class ContentFeedbackKind(val raw: String) {
  GENERAL("content_feedback"), DECK_SUGGESTION("deck_suggestion"), CONTENT_REPORT("content_report");

  /** iOS localization keys → Android `R.string` names use `_` for `.`. */
  val subjectKey: String get() = when (this) {
    GENERAL -> "content_feedback.email.subject.general"
    DECK_SUGGESTION -> "content_feedback.email.subject.suggestion"
    CONTENT_REPORT -> "content_feedback.email.subject.report"
  }
  val promptKey: String get() = when (this) {
    GENERAL -> "content_feedback.email.prompt.general"
    DECK_SUGGESTION -> "content_feedback.email.prompt.suggestion"
    CONTENT_REPORT -> "content_feedback.email.prompt.report"
  }
}

/** iOS `ContentFeedbackSource`. */
enum class ContentFeedbackSource(val raw: String) { DISCOVER("discover"), DECK_DETAIL("deck_detail"), SETTINGS("settings") }

/** iOS `ContentFeedbackContext`. */
data class ContentFeedbackContext(
  val kind: ContentFeedbackKind,
  val source: ContentFeedbackSource,
  val deckId: String? = null,
  val deckVersion: Int? = null,
) {
  companion object {
    fun general(source: ContentFeedbackSource) = ContentFeedbackContext(ContentFeedbackKind.GENERAL, source)
    fun suggestion(source: ContentFeedbackSource) = ContentFeedbackContext(ContentFeedbackKind.DECK_SUGGESTION, source)
    fun report(deckId: String, deckVersion: Int, source: ContentFeedbackSource = ContentFeedbackSource.DECK_DETAIL) =
      ContentFeedbackContext(ContentFeedbackKind.CONTENT_REPORT, source, deckId, deckVersion)
  }
}

/** Pure `mailto:` builder matching iOS `ContentFeedbackLinkBuilder.makeURL`. */
object ContentFeedbackLinkBuilder {
  const val RECIPIENT = "contact@typee.app"

  fun body(prompt: String, caution: String, context: ContentFeedbackContext, appVersion: String, buildNumber: String, language: String): String {
    val metadata = buildList {
      add("type: ${context.kind.raw}")
      add("source: ${context.source.raw}")
      add("app_version: $appVersion")
      add("build: $buildNumber")
      add("language: $language")
      context.deckId?.let { add("deck_id: $it") }
      context.deckVersion?.let { add("deck_version: $it") }
    }
    return "$prompt\n\n---\n${metadata.joinToString("\n")}\n\n$caution"
  }

  fun mailto(
    subject: String,
    prompt: String,
    caution: String,
    context: ContentFeedbackContext,
    appVersion: String,
    buildNumber: String,
    language: String,
  ): String {
    val body = body(prompt, caution, context, appVersion, buildNumber, language)
    return "mailto:$RECIPIENT?subject=${encode(subject)}&body=${encode(body)}"
  }

  /** RFC 3986 percent-encoding (spaces as `%20`, never `+`). */
  fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    .replace("+", "%20")
    .replace("%7E", "~")
}
