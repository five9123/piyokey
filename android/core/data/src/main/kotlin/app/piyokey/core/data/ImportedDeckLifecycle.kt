package app.piyokey.core.data

import app.piyokey.core.deckkit.Deck

const val PIYODECK_STAGING_DIRECTORY_NAME: String = "piyodeck-imports"

enum class ImportedDeckConflict {
  NEW,
  IDENTICAL,
  NEWER_VERSION,
  OLDER_VERSION,
  SAME_VERSION_DIFFERENT_CONTENT,
}

data class ImportedDeckVersionSummary(
  val version: Int,
  val contentSha256: String,
)

data class ImportedDeckPreview(
  val deck: Deck,
  val contentSha256: String,
  val packageSha256: String,
  val sourceDisplayName: String,
  val conflict: ImportedDeckConflict,
  val installed: InstalledDeck?,
)

sealed interface ImportedDeckCommitResult {
  data class Installed(val deck: InstalledDeck, val replacedExisting: Boolean) : ImportedDeckCommitResult
  data class AlreadyInstalled(val deck: InstalledDeck) : ImportedDeckCommitResult
}

object ImportedDeckConflictPolicy {
  fun classify(
    incoming: ImportedDeckVersionSummary,
    installed: ImportedDeckVersionSummary?,
  ): ImportedDeckConflict = when {
    installed == null -> ImportedDeckConflict.NEW
    incoming.contentSha256 == installed.contentSha256 -> ImportedDeckConflict.IDENTICAL
    incoming.version > installed.version -> ImportedDeckConflict.NEWER_VERSION
    incoming.version < installed.version -> ImportedDeckConflict.OLDER_VERSION
    else -> ImportedDeckConflict.SAME_VERSION_DIFFERENT_CONTENT
  }

  fun requiresDestructiveConfirmation(conflict: ImportedDeckConflict): Boolean = when (conflict) {
    ImportedDeckConflict.NEWER_VERSION,
    ImportedDeckConflict.OLDER_VERSION,
    ImportedDeckConflict.SAME_VERSION_DIFFERENT_CONTENT,
    -> true
    ImportedDeckConflict.NEW,
    ImportedDeckConflict.IDENTICAL,
    -> false
  }
}

sealed class ImportedDeckException(message: String) : Exception(message) {
  data object StagingFileRequired : ImportedDeckException("Import must use the app-private staging directory.")
  data object OfficialIdentifierCollision : ImportedDeckException("The user deck ID collides with official content.")
  data object InstalledSourceCollision : ImportedDeckException("The user deck ID collides with a non-user installation.")
  data class SourceChanged(val expectedSha256: String, val actualSha256: String) :
    ImportedDeckException("The staged import changed after preview.")
  data class ReplacementConfirmationRequired(val conflict: ImportedDeckConflict) :
    ImportedDeckException("Replacing the installed deck requires explicit confirmation: $conflict")
  data class SeparateCopyRequiresConflict(val conflict: ImportedDeckConflict) :
    ImportedDeckException("A separate copy requires an installed deck with different content: $conflict")
  data object ExportRequiresUserDeck : ImportedDeckException("Only imported or created user decks can be exported.")
  data object FreeUserDeckLimitReached : ImportedDeckException(
    "typee pro is required to install more than three user decks.",
  )
}

object PiyokeyProPolicy {
  const val FREE_INSTALLED_USER_DECK_LIMIT = 3
}
