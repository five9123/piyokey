package app.piyokey.android.feature.library

import app.piyokey.android.data.AppData
import app.piyokey.android.data.decks.DeckInstallationStoreException
import app.piyokey.android.data.decks.InstalledDeckSource
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.billing.ProStore
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.PiyoDeckPackage
import app.piyokey.core.deckkit.PiyoDeckPackageReader
import app.piyokey.core.deckkit.PiyoDeckPackageWriter
import app.piyokey.core.domain.PiyoDeckDocumentRules
import app.piyokey.core.domain.UserDeckDraft
import app.piyokey.core.domain.library.DeckMakerCommitRules
import app.piyokey.core.domain.library.UserDeckSourceChangedException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How an editor session commits (iOS `DeckEditorPresentation`). */
data class DeckEditorPresentation(
  val draftId: String,
  val draft: UserDeckDraft,
  val source: InstalledDeckSource,
  val derivedFromDeckId: String?,
  val deletesDeckId: String?,
)

/** Deck Maker persistence (iOS `MyPageView.saveUserDeck` / `saveChangedSourceDraftAsCopy` / delete). */
object DeckMakerCommits {
  suspend fun canonicalPackage(deck: Deck): PiyoDeckPackage = withContext(Dispatchers.Default) {
    val schema = AppData.bundled.deckSchema
    PiyoDeckPackageReader.read(PiyoDeckPackageWriter.write(deck, schema), schema)
  }

  /** `.typedeck` bytes for export (iOS `PiyoDeckDocumentService.exportArtifact`). */
  suspend fun exportBytes(deck: Deck): ByteArray = withContext(Dispatchers.Default) {
    PiyoDeckPackageWriter.write(deck, AppData.bundled.deckSchema)
  }

  fun exportFilename(deck: Deck): String = "${PiyoDeckDocumentRules.safeFilename(deck.name)}.typedeck"

  private fun deckSourceValue(source: InstalledDeckSource) = if (source == InstalledDeckSource.IMPORTED) "imported" else "created"

  suspend fun save(deck: Deck, presentation: DeckEditorPresentation, draftId: String) {
    val library = AppData.deckLibrary
    ProStore.requireAccess()
    DeckMakerCommitRules.requireUnchangedSource(presentation.draft, library.installedDeck(presentation.draft.deckId)?.version)
    val pkg = canonicalPackage(deck)
    ProStore.requireAccess()
    DeckMakerCommitRules.requireUnchangedSource(presentation.draft, library.installedDeck(presentation.draft.deckId)?.version)
    val expected = if (presentation.draft.origin == UserDeckDraft.Origin.Editing) presentation.draft.baseVersion else null
    val installed = try {
      library.installUserDeck(
        data = pkg.deckData,
        source = presentation.source,
        contentSha256 = pkg.contentSha256,
        packageFormatVersion = pkg.manifest.formatVersion,
        isLocallyModified = true,
        hasPiyokeyProAccess = ProStore.hasAccess.value,
        derivedFromDeckId = presentation.derivedFromDeckId,
        expectedCurrentVersion = expected,
      )
    } catch (_: DeckInstallationStoreException.SourceChanged) {
      throw UserDeckSourceChangedException()
    }
    AppData.review.reconcile(installed)
    runCatching { AppData.drafts.clear(draftId) }
    Telemetry.deckMakerAction(DeckMakerCommitRules.action(presentation.draft), installed.items.size, deckSourceValue(presentation.source))
  }

  suspend fun saveAsCopy(deck: Deck, presentation: DeckEditorPresentation, draftId: String) {
    ProStore.requireAccess()
    val copy = PiyoDeckDocumentRules.makeUserCopy(deck, Instant.now())
    val pkg = canonicalPackage(copy)
    ProStore.requireAccess()
    val installed = AppData.deckLibrary.installUserDeck(
      data = pkg.deckData,
      source = InstalledDeckSource.CREATED,
      contentSha256 = pkg.contentSha256,
      packageFormatVersion = pkg.manifest.formatVersion,
      isLocallyModified = true,
      hasPiyokeyProAccess = ProStore.hasAccess.value,
      derivedFromDeckId = presentation.draft.deckId,
    )
    AppData.review.reconcile(installed)
    runCatching { AppData.drafts.clear(draftId) }
    Telemetry.deckMakerAction("copied", installed.items.size, "created")
  }

  suspend fun deleteFromEditor(deckId: String, presentation: DeckEditorPresentation, draftId: String) {
    AppData.deckLibrary.removeAndWait(deckId)
    AppData.review.markSourceUnavailable(deckId)
    runCatching { AppData.drafts.clear(draftId) }
    Telemetry.deckMakerAction("deleted", presentation.draft.items.size, deckSourceValue(presentation.source))
  }

  /** My Decks delete (iOS `MyPageView.delete`). */
  suspend fun delete(deck: Deck) {
    val source = AppData.deckLibrary.record(deck.deckId)?.source
    AppData.deckLibrary.removeAndWait(deck.deckId)
    AppData.review.markSourceUnavailable(deck.deckId)
    if (source == InstalledDeckSource.CREATED || source == InstalledDeckSource.IMPORTED) {
      Telemetry.deckMakerAction("deleted", deck.items.size, deckSourceValue(source))
    }
  }
}
