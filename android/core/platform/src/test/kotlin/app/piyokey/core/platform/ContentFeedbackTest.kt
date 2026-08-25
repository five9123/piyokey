package app.piyokey.core.platform

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContentFeedbackTest {
  private val copy = ContentFeedbackCopy(
    proposalSubject = "proposal",
    reportSubject = "report",
    combinedSubject = "combined",
    proposalBody = "proposal fields and privacy notice",
    reportBody = "report fields and review notice",
    combinedBody = "combined fields and review notice",
    metadataHeading = "metadata",
    typeLabel = "type",
    sourceLabel = "source",
    appLabel = "app",
    languageLabel = "language",
    deckLabel = "deck",
  )

  @Test
  fun proposalContainsOnlyApprovedAutomaticMetadata() {
    val message = ContentFeedbackComposer.compose(
      ContentFeedbackRequest(
        ContentFeedbackKind.PROPOSAL,
        ContentFeedbackSource.DISCOVER,
        "1.1",
        8,
        "ja",
      ),
      copy,
    )

    assertEquals("proposal", message.subject)
    assertContains(message.body, "type: proposal")
    assertContains(message.body, "source: discover")
    assertContains(message.body, "app: 1.1 (8)")
    assertContains(message.body, "language: ja")
    assertFalse("device" in message.body.lowercase())
    assertFalse("history" in message.body.lowercase())
  }

  @Test
  fun contextualReportIncludesOnlyDeckIdentifierAndVersion() {
    val message = ContentFeedbackComposer.compose(
      ContentFeedbackRequest(
        ContentFeedbackKind.REPORT,
        ContentFeedbackSource.DECK_DETAIL,
        "1.1",
        8,
        "ko",
        ContentFeedbackDeck("official_daily_words", 11),
      ),
      copy,
    )

    assertEquals("report", message.subject)
    assertContains(message.body, "deck: official_daily_words / v11")
    assertContains(message.body, "source: deck_detail")
  }
}
