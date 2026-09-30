package app.piyokey.core.domain.library

import app.piyokey.core.deckkit.PiyoDeckImportError
import app.piyokey.core.domain.PiyoDeckDocumentNotice
import app.piyokey.core.domain.PiyoDeckImportCollision

/** Error → notice mapping and preview decisions of the `.typedeck` import flow (iOS `PiyoDeckDocumentCoordinator`). */
object PiyoDeckImportRules {
  /**
   * iOS `PiyoDeckDocumentCoordinator.notice(for:)`. [isFileTooLarge] marks the app's bounded-read
   * failure (over 8 MiB before parsing).
   */
  fun notice(error: Throwable, isFileTooLarge: Boolean = false): PiyoDeckDocumentNotice {
    if (isFileTooLarge) return PiyoDeckDocumentNotice.PACKAGE_TOO_LARGE
    return when (error) {
      is PiyoDeckImportError.PackageTooLarge, is PiyoDeckImportError.EntryTooLarge -> PiyoDeckDocumentNotice.PACKAGE_TOO_LARGE
      is PiyoDeckImportError.UnsupportedFormatVersion, is PiyoDeckImportError.UnsupportedDeckSchemaVersion ->
        PiyoDeckDocumentNotice.UNSUPPORTED_VERSION
      is PiyoDeckImportError -> PiyoDeckDocumentNotice.INVALID_DOCUMENT
      else -> PiyoDeckDocumentNotice.CANNOT_READ
    }
  }

  /** Primary action of the preview for a collision. */
  enum class PrimaryAction { IMPORT, UNLOCK_PRO, DONE, KEEP }

  fun primaryAction(collision: PiyoDeckImportCollision, canInstallNewDeck: Boolean): PrimaryAction = when (collision) {
    PiyoDeckImportCollision.New -> if (canInstallNewDeck) PrimaryAction.IMPORT else PrimaryAction.UNLOCK_PRO
    PiyoDeckImportCollision.Identical -> PrimaryAction.DONE
    is PiyoDeckImportCollision.Different -> PrimaryAction.KEEP
  }

  /** iOS status accessibility identifier suffix / message key id. */
  fun statusKey(collision: PiyoDeckImportCollision): String = when {
    collision == PiyoDeckImportCollision.New -> "new"
    collision == PiyoDeckImportCollision.Identical -> "identical"
    collision.isDowngrade -> "downgrade"
    collision.isSameVersionConflict -> "same_version"
    else -> "different"
  }

  /** A replacement keeps the installed deck's lineage; a new import has none. */
  fun derivedFromDeckIdForInstall(replacing: Boolean, installedDerivedFrom: String?): String? =
    if (replacing) installedDerivedFrom else null
}
