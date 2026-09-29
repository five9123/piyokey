package app.piyokey.core.domain

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.PiyoDeckPackage
import java.time.Instant

/** How an incoming `.typedeck` relates to an installed deck with the same id. */
sealed interface PiyoDeckImportCollision {
  data object New : PiyoDeckImportCollision
  data object Identical : PiyoDeckImportCollision
  data class Different(val existingVersion: Int, val incomingVersion: Int) : PiyoDeckImportCollision

  val isDowngrade: Boolean get() = this is Different && incomingVersion < existingVersion
  val isSameVersionConflict: Boolean get() = this is Different && incomingVersion == existingVersion

  companion object {
    /**
     * [existingVersion]/[existingSha256] come from the installed record (null = not installed);
     * identical when the content hash or the decoded deck matches.
     */
    fun of(
      existingVersion: Int?,
      existingSha256: String?,
      installedDeck: Deck?,
      incoming: PiyoDeckPackage,
    ): PiyoDeckImportCollision {
      if (existingVersion == null) return New
      if (existingSha256 == incoming.contentSha256 || installedDeck == incoming.deck) return Identical
      return Different(existingVersion, incoming.deck.version)
    }
  }
}

data class PiyoDeckComparisonSide(val name: String, val version: Int, val updatedAt: Instant, val itemCount: Int) {
  companion object {
    fun of(deck: Deck, displayName: String? = null) =
      PiyoDeckComparisonSide(displayName ?: deck.name, deck.version, deck.updatedAt, deck.items.size)
  }
}

data class PiyoDeckCollisionComparison(val current: PiyoDeckComparisonSide, val incoming: PiyoDeckComparisonSide)

/** User-visible import/export notices (`piyodeck.notice.<id>.title|message`). */
enum class PiyoDeckDocumentNotice(val id: String) {
  PACKAGE_TOO_LARGE("packageTooLarge"),
  UNSUPPORTED_VERSION("unsupportedVersion"),
  INVALID_DOCUMENT("invalidDocument"),
  CANNOT_READ("cannotRead"),
  CANNOT_SAVE("cannotSave"),
  CANNOT_EXPORT("cannotExport"),
  ALREADY_IMPORTED("alreadyImported"),
  IMPORTED("imported");

  val titleKey: String get() = "piyodeck.notice.$id.title"
  val messageKey: String get() = "piyodeck.notice.$id.message"
}

object PiyoDeckDocumentRules {
  /** A new user-owned copy (fresh `user_`/`item_` ids, version 1, local author). */
  fun makeUserCopy(source: Deck, at: Instant, uuidHex: () -> String = UserDeckDraft::randomUuidHex): Deck = Deck(
    deckId = UserDeckDraft.makeIdentifier("user_", uuidHex),
    version = 1,
    name = source.name,
    author = DeckAuthor(UserDeckDraft.LOCAL_AUTHOR_ID, source.author.nickname),
    official = false,
    type = source.type,
    level = source.level,
    tags = source.tags,
    createdAt = at,
    updatedAt = at,
    items = source.items.map {
      DeckItem(
        id = UserDeckDraft.makeIdentifier("item_", uuidHex),
        ko = it.ko,
        readingJa = it.readingJa,
        meaningJa = it.meaningJa,
        audio = null,
        localizations = it.localizations,
      )
    },
    localizations = source.localizations,
    defaultLocale = source.defaultLocale,
  )

  /** Export file stem: letters/digits/`-`/`_`, other runs collapsed to `-`, ≤ 80 chars. */
  fun safeFilename(value: String): String {
    val mapped = buildString {
      value.codePoints().forEach { cp ->
        if (Character.isLetterOrDigit(cp) || cp == '-'.code || cp == '_'.code) appendCodePoint(cp) else append('-')
      }
    }
    val collapsed = mapped.split('-').filter { it.isNotEmpty() }.joinToString("-")
    val result = collapsed.ifEmpty { "piyokey-deck" }
    return result.take(80)
  }
}
