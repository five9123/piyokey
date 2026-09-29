package app.piyokey.core.domain

import app.piyokey.core.deckkit.ContentValidationIssue
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckMetadataLocalization
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.LocaleTag
import app.piyokey.core.deckkit.PiyoDeckPackageLimits
import app.piyokey.core.deckkit.UserDeckValidator
import java.security.SecureRandom
import java.time.Instant

/** Content language of a user deck (includes Korean, unlike the UI language list). */
enum class DeckContentLanguage(val raw: String) {
  JAPANESE("ja"), ENGLISH("en"), KOREAN("ko"), SPANISH("es"), GERMAN("de"), FRENCH("fr");

  companion object {
    /** Maps an app UI language code (`ja`, `en`, …) to a content language; English otherwise. */
    fun of(appLanguageCode: String): DeckContentLanguage = entries.firstOrNull { it.raw == appLanguageCode } ?: ENGLISH
  }
}

/** One editable item (iOS `UserDeckItemDraft`). Japanese is the legacy base (`reading_ja`/`meaning_ja`). */
data class UserDeckItemDraft(
  val id: String,
  val ko: String = "",
  val readingJa: String = "",
  val meaningJa: String = "",
  val localizations: Map<String, DeckItemLocalization>? = null,
) {
  fun reading(localeCode: String): String = if (localeCode == "ja") readingJa else localizations?.get(localeCode)?.reading ?: ""
  fun meaning(localeCode: String): String = if (localeCode == "ja") meaningJa else localizations?.get(localeCode)?.meaning ?: ""

  fun withReading(value: String, localeCode: String): UserDeckItemDraft =
    if (localeCode == "ja") copy(readingJa = value) else updatingLocalization(localeCode, reading = value)

  fun withMeaning(value: String, localeCode: String): UserDeckItemDraft =
    if (localeCode == "ja") copy(meaningJa = value) else updatingLocalization(localeCode, meaning = value)

  private fun updatingLocalization(localeCode: String, reading: String? = null, meaning: String? = null): UserDeckItemDraft {
    val existing = localizations?.get(localeCode)
    val updated = (localizations ?: emptyMap()) + (
      localeCode to DeckItemLocalization(
        meaning = meaning ?: existing?.meaning ?: "",
        reading = reading ?: existing?.reading ?: "",
      )
      )
    return copy(localizations = updated)
  }
}

enum class UserDeckValidationFieldKind { LANGUAGE, NAME, AUTHOR, TAGS, ITEMS, ITEM, ITEM_KOREAN, ITEM_READING, ITEM_MEANING }

/** Editor field to focus for the first issue; [index] is set for item fields. */
data class UserDeckValidationField(val kind: UserDeckValidationFieldKind, val index: Int? = null)

/** Issues plus de-duplicated `deck_editor.validation.*` message keys, in order. */
data class UserDeckValidationSummary(val issues: List<ContentValidationIssue>) {
  val localizationKeys: List<String> = issues.map(::localizationKey).distinct()
  val isEmpty: Boolean get() = localizationKeys.isEmpty()
  val firstIssue: ContentValidationIssue? get() = issues.firstOrNull()
  val firstField: UserDeckValidationField? get() = issues.firstNotNullOfOrNull { field(it.path) }

  companion object {
    fun localizationKey(issue: ContentValidationIssue): String = when {
      issue.code == "single_deck_language" -> "deck_editor.validation.single_language"
      issue.path == "name" && issue.code == "required" -> "deck_editor.validation.name_required"
      issue.path == "author.nickname" && issue.code == "required" -> "deck_editor.validation.author_required"
      issue.path == "tags" && issue.code == "duplicate" -> "deck_editor.validation.tags_duplicate"
      issue.path == "tags" -> "deck_editor.validation.tags"
      issue.path == "items" && issue.code == "min_items" -> "deck_editor.validation.items_required"
      issue.code == "max_target_length" || issue.code == "user_deck_korean_length" -> "deck_editor.validation.korean_length"
      issue.code == "undecomposable_ko" || issue.code == "missing_hangul" || issue.code == "user_deck_korean_input" ->
        "deck_editor.validation.korean_input"
      issue.code == "user_deck_item_limit" -> "deck_editor.validation.item_limit"
      issue.code == "max_length" || issue.code == "user_deck_text_length" -> "deck_editor.validation.text_length"
      issue.code == "duplicate" -> "deck_editor.validation.duplicate"
      issue.code == "required" -> "deck_editor.validation.required"
      else -> "deck_editor.validation.invalid"
    }

    fun field(path: String): UserDeckValidationField? {
      fun kind(kind: UserDeckValidationFieldKind, index: Int? = null) = UserDeckValidationField(kind, index)
      if (path == "default_locale") return kind(UserDeckValidationFieldKind.LANGUAGE)
      if (path == "name" || (path.startsWith("localizations.") && path.endsWith(".name"))) {
        return kind(UserDeckValidationFieldKind.NAME)
      }
      if (path == "author.nickname" || (path.startsWith("localizations.") && path.endsWith(".author_nickname"))) {
        return kind(UserDeckValidationFieldKind.AUTHOR)
      }
      if (path == "tags" || path.startsWith("tags[") || (path.startsWith("localizations.") && path.contains(".tags"))) {
        return kind(UserDeckValidationFieldKind.TAGS)
      }
      if (path == "items") return kind(UserDeckValidationFieldKind.ITEMS)
      if (!path.startsWith("items[")) return null
      val closing = path.indexOf(']')
      if (closing < 0) return null
      val index = path.substring(6, closing).toIntOrNull() ?: return null
      val fieldPath = path.substring(closing + 1)
      return when {
        fieldPath.startsWith(".ko") -> kind(UserDeckValidationFieldKind.ITEM_KOREAN, index)
        fieldPath.endsWith(".meaning") || fieldPath.startsWith(".meaning_ja") -> kind(UserDeckValidationFieldKind.ITEM_MEANING, index)
        fieldPath.endsWith(".reading") || fieldPath.startsWith(".reading_ja") -> kind(UserDeckValidationFieldKind.ITEM_READING, index)
        fieldPath.contains(".localizations.") -> kind(UserDeckValidationFieldKind.ITEM_READING, index)
        else -> kind(UserDeckValidationFieldKind.ITEM, index)
      }
    }
  }
}

class UserDeckDraftValidationException(val issues: List<ContentValidationIssue>) :
  Exception("User deck draft is invalid: ${issues.joinToString()}")

/**
 * Deck Maker document (iOS `UserDeckDraft`). Immutable: each edit returns a new draft. The
 * one autosaved active draft is persisted by `UserDeckDraftStore` in the app.
 */
data class UserDeckDraft(
  val origin: Origin,
  val deckId: String,
  val createdAt: Instant,
  val baseVersion: Int,
  val authorId: String,
  val metadataLocalizations: Map<String, DeckMetadataLocalization>?,
  val defaultLocale: String?,
  val name: String,
  val authorNickname: String,
  val type: DeckType,
  val level: Int,
  val tags: List<String>,
  val items: List<UserDeckItemDraft>,
) {
  sealed interface Origin {
    data object New : Origin
    data object Editing : Origin
    data class OfficialCopy(val sourceDeckId: String) : Origin
  }

  val derivedFromDeckId: String? get() = (origin as? Origin.OfficialCopy)?.sourceDeckId

  /** Locale keys with independent content bundles; the Japanese base counts when it has content. */
  val contentLocaleCodes: List<String>
    get() {
      val codes = HashSet<String>()
      metadataLocalizations?.keys?.let(codes::addAll)
      for (item in items) item.localizations?.keys?.let(codes::addAll)
      if ((defaultLocale == null || defaultLocale == "ja") && hasLegacyBaseContent) codes += "ja"
      if (codes.isNotEmpty()) return codes.sorted()
      return if (hasLegacyBaseContent) listOf("ja") else emptyList()
    }

  val requiresContentBundleSelection: Boolean get() = contentLocaleCodes.size > 1

  /** Collapses a legacy multilingual deck to one existing bundle (after explicit confirmation). */
  fun selectingContentBundle(localeCode: String): UserDeckDraft {
    require(canonicalLocale(localeCode) == localeCode) { "Content locale must be a canonical BCP 47 tag." }
    require(localeCode in contentLocaleCodes) { "Content bundle selection must use an existing locale." }
    val metadata = metadataForContent(localeCode)
    return copy(
      name = metadata.name,
      authorNickname = metadata.authorNickname,
      tags = metadata.tags,
      metadataLocalizations = mapOf(localeCode to metadata),
      defaultLocale = localeCode,
      items = items.map { item ->
        val localized = localizationForContent(item, localeCode)
        item.copy(meaningJa = localized.meaning, readingJa = localized.reading, localizations = mapOf(localeCode to localized))
      },
    )
  }

  /** Moves the sole content bundle to another deck language without changing values. */
  fun retaggingDeckLanguage(localeCode: String): UserDeckDraft {
    require(canonicalLocale(localeCode) == localeCode) { "Deck language must be a canonical BCP 47 tag." }
    require(!requiresContentBundleSelection) { "Select one legacy content bundle before changing the deck language." }
    val source = contentLocaleCodes.firstOrNull()
    val metadata = source?.let(::metadataForContent) ?: DeckMetadataLocalization(name, authorNickname, tags)
    return copy(
      name = metadata.name,
      authorNickname = metadata.authorNickname,
      tags = metadata.tags,
      metadataLocalizations = mapOf(localeCode to metadata),
      defaultLocale = localeCode,
      items = items.map { item ->
        val localized = source?.let { localizationForContent(item, it) } ?: DeckItemLocalization(item.meaningJa, item.readingJa)
        item.copy(meaningJa = localized.meaning, readingJa = localized.reading, localizations = mapOf(localeCode to localized))
      },
    )
  }

  fun name(localeCode: String): String = if (localeCode == "ja") name else metadataLocalizations?.get(localeCode)?.name ?: ""
  fun authorNickname(localeCode: String): String =
    if (localeCode == "ja") authorNickname else metadataLocalizations?.get(localeCode)?.authorNickname ?: ""
  fun tags(localeCode: String): List<String> = if (localeCode == "ja") tags else metadataLocalizations?.get(localeCode)?.tags ?: emptyList()

  fun withName(value: String, localeCode: String) =
    if (localeCode == "ja") copy(name = value) else updatingMetadata(localeCode, name = value)
  fun withAuthorNickname(value: String, localeCode: String) =
    if (localeCode == "ja") copy(authorNickname = value) else updatingMetadata(localeCode, authorNickname = value)
  fun withTags(value: List<String>, localeCode: String) =
    if (localeCode == "ja") copy(tags = value) else updatingMetadata(localeCode, tags = value)

  fun updatingItem(index: Int, transform: (UserDeckItemDraft) -> UserDeckItemDraft): UserDeckDraft =
    copy(items = items.mapIndexed { i, item -> if (i == index) transform(item) else item })

  fun addingItem(uuidHex: () -> String = ::randomUuidHex): UserDeckDraft {
    if (items.size >= PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT) return this
    return copy(items = items + UserDeckItemDraft(id = makeIdentifier("item_", uuidHex)))
  }

  /** Removes the given indices but always keeps at least one item. */
  fun removingItems(indices: Set<Int>): UserDeckDraft {
    val valid = indices.filter { it in items.indices }
    if (items.size - valid.size < 1) return this
    return copy(items = items.filterIndexed { index, _ -> index !in valid })
  }

  /** SwiftUI `move(fromOffsets:toOffset:)` semantics ([destination] is in pre-move coordinates). */
  fun movingItems(fromIndices: Set<Int>, destination: Int): UserDeckDraft {
    val moving = items.filterIndexed { index, _ -> index in fromIndices }
    val before = items.filterIndexed { index, _ -> index !in fromIndices && index < destination }
    val after = items.filterIndexed { index, _ -> index !in fromIndices && index >= destination }
    return copy(items = before + moving + after)
  }

  fun materializedDeck(at: Instant, localeCode: String): Deck {
    val displayName = name(localeCode)
    val displayAuthor = authorNickname(localeCode)
    val displayTags = tags(localeCode)
    val resolvedDefault = defaultLocale
      ?: if (localeCode == "ja" || metadataLocalizations?.get(localeCode) == null) "ja" else localeCode
    val outputMetadata = (compatibleMetadataLocalizations(if (tags.isEmpty()) displayTags else tags, localeCode) ?: emptyMap())
      .toMutableMap()
    if (localeCode == "ja" || resolvedDefault == "ja") {
      outputMetadata["ja"] = DeckMetadataLocalization(name, authorNickname, tags)
    }
    val defaultMetadata = outputMetadata[resolvedDefault]
    val japanese = outputMetadata["ja"]
    val declared = outputMetadata.keys.toSet()
    return Deck(
      deckId = deckId,
      version = if (origin == Origin.Editing) baseVersion + 1 else 1,
      name = japanese?.name ?: defaultMetadata?.name ?: displayName,
      author = DeckAuthor(authorId, japanese?.authorNickname ?: defaultMetadata?.authorNickname ?: displayAuthor),
      official = false,
      type = type,
      level = level,
      tags = japanese?.tags ?: defaultMetadata?.tags ?: displayTags,
      createdAt = createdAt,
      updatedAt = maxOf(at, createdAt),
      items = items.map { item ->
        val itemLocalizations = (item.localizations ?: emptyMap()).toMutableMap()
        if (localeCode == "ja" || resolvedDefault == "ja") {
          itemLocalizations["ja"] = DeckItemLocalization(item.meaningJa, item.readingJa)
        }
        val filtered = itemLocalizations.filterKeys { it in declared }
        val ja = filtered["ja"]
        val fallback = filtered[resolvedDefault]
        DeckItem(
          id = item.id,
          ko = item.ko,
          readingJa = ja?.reading ?: fallback?.reading ?: item.reading(localeCode),
          meaningJa = ja?.meaning ?: fallback?.meaning ?: item.meaning(localeCode),
          audio = null,
          localizations = filtered,
        )
      },
      localizations = outputMetadata,
      defaultLocale = resolvedDefault,
    )
  }

  fun validationIssues(at: Instant, localeCode: String): List<ContentValidationIssue> {
    val deck = materializedDeck(at, localeCode)
    return singleDeckLanguageIssues(deck) + UserDeckValidator.validate(deck) +
      schemaBoundaryIssues(deck) + productBoundaryIssues(deck)
  }

  fun validationSummary(at: Instant, localeCode: String) = UserDeckValidationSummary(validationIssues(at, localeCode))

  /** The deck to save, or throws [UserDeckDraftValidationException]. */
  fun validatedDeck(at: Instant, localeCode: String): Deck {
    val deck = materializedDeck(at, localeCode)
    val issues = singleDeckLanguageIssues(deck) + UserDeckValidator.validate(deck) +
      schemaBoundaryIssues(deck) + productBoundaryIssues(deck)
    if (issues.isNotEmpty()) throw UserDeckDraftValidationException(issues)
    return deck
  }

  private fun updatingMetadata(
    localeCode: String,
    name: String? = null,
    authorNickname: String? = null,
    tags: List<String>? = null,
  ): UserDeckDraft {
    val existing = metadataLocalizations?.get(localeCode)
    return copy(
      metadataLocalizations = (metadataLocalizations ?: emptyMap()) + (
        localeCode to DeckMetadataLocalization(
          name = name ?: existing?.name ?: "",
          authorNickname = authorNickname ?: existing?.authorNickname ?: "",
          tags = tags ?: existing?.tags ?: emptyList(),
        )
        ),
    )
  }

  private fun compatibleMetadataLocalizations(baseTags: List<String>, currentLocaleCode: String): Map<String, DeckMetadataLocalization>? {
    val metadata = metadataLocalizations ?: return null
    val filtered = metadata.filter { (code, localization) ->
      if (currentLocaleCode != "ja" && code == currentLocaleCode) return@filter true
      if (localization.name.trimmedWhitespace().isEmpty() || localization.authorNickname.trimmedWhitespace().isEmpty()) {
        return@filter false
      }
      if (localization.tags.size != baseTags.size) return@filter false
      items.all { item ->
        val itemLocalization = item.localizations?.get(code) ?: return@all false
        itemLocalization.meaning.trimmedWhitespace().isNotEmpty() && itemLocalization.reading.trimmedWhitespace().isNotEmpty()
      }
    }
    return filtered.ifEmpty { null }
  }

  private fun metadataForContent(localeCode: String): DeckMetadataLocalization =
    metadataLocalizations?.get(localeCode)
      ?: if (localeCode == "ja") DeckMetadataLocalization(name, authorNickname, tags) else DeckMetadataLocalization("", "", emptyList())

  private fun localizationForContent(item: UserDeckItemDraft, localeCode: String): DeckItemLocalization =
    item.localizations?.get(localeCode)
      ?: if (localeCode == "ja") DeckItemLocalization(item.meaningJa, item.readingJa) else DeckItemLocalization("", "")

  private val hasLegacyBaseContent: Boolean
    get() = name.trimmedWhitespace().isNotEmpty() || authorNickname.trimmedWhitespace().isNotEmpty() || tags.isNotEmpty() ||
      items.any { it.meaningJa.trimmedWhitespace().isNotEmpty() || it.readingJa.trimmedWhitespace().isNotEmpty() }

  private fun singleDeckLanguageIssues(deck: Deck): List<ContentValidationIssue> {
    val codes = deck.localizations?.keys ?: emptySet()
    val valid = contentLocaleCodes.size == 1 && !requiresContentBundleSelection && codes.size == 1 &&
      deck.defaultLocale != null && deck.defaultLocale in codes
    return if (valid) {
      emptyList()
    } else {
      listOf(ContentValidationIssue("single_deck_language", "default_locale", "A user deck must declare exactly one deck language"))
    }
  }

  private fun schemaBoundaryIssues(deck: Deck): List<ContentValidationIssue> {
    val issues = ArrayList<ContentValidationIssue>()
    fun check(value: String, maximum: Int, path: String) {
      if (value.graphemeCount() > maximum) issues += ContentValidationIssue("max_length", path, "Maximum length exceeded")
    }
    check(deck.name, 120, "name")
    check(deck.author.nickname, 40, "author.nickname")
    deck.tags.forEachIndexed { index, tag -> check(tag, 40, "tags[$index]") }
    deck.items.forEachIndexed { index, item ->
      check(item.readingJa, 300, "items[$index].reading_ja")
      check(item.meaningJa, 500, "items[$index].meaning_ja")
    }
    return issues
  }

  private fun productBoundaryIssues(deck: Deck): List<ContentValidationIssue> {
    val issues = ArrayList<ContentValidationIssue>()
    deck.items.forEachIndexed { index, item ->
      if (item.ko.isNotEmpty() && !isKoreanInput(item.ko)) {
        issues += ContentValidationIssue(
          "user_deck_korean_input", "items[$index].ko", "Korean must contain only Hangul syllables and spaces",
        )
      }
      if (item.ko.graphemeCount() > MAXIMUM_KOREAN_CHARACTER_COUNT ||
        item.ko.filter { it != ' ' }.graphemeCount() > MAXIMUM_KOREAN_SYLLABLE_COUNT
      ) {
        issues += ContentValidationIssue(
          "user_deck_korean_length", "items[$index].ko",
          "Korean is limited to 9 syllables excluding spaces and 10 characters including spaces",
        )
      }
      if (productCharacterCount(item.readingJa) > MAXIMUM_MEANING_OR_READING_COUNT) {
        issues += ContentValidationIssue("user_deck_text_length", "items[$index].reading_ja", "Reading is limited to 20 characters")
      }
      if (productCharacterCount(item.meaningJa) > MAXIMUM_MEANING_OR_READING_COUNT) {
        issues += ContentValidationIssue("user_deck_text_length", "items[$index].meaning_ja", "Meaning is limited to 20 characters")
      }
    }
    return issues
  }

  companion object {
    const val LOCAL_AUTHOR_ID = "user_local"
    const val MAXIMUM_KOREAN_SYLLABLE_COUNT = 9
    const val MAXIMUM_KOREAN_CHARACTER_COUNT = 10
    const val MAXIMUM_MEANING_OR_READING_COUNT = 20

    private val secureRandom = SecureRandom()
    private val hexPattern = Regex("^[0-9a-f]{32}$")

    /** A blank Japanese-language draft with one empty item. */
    fun new(at: Instant = Instant.now(), uuidHex: () -> String = ::randomUuidHex): UserDeckDraft {
      val deckId = makeIdentifier("user_", uuidHex)
      return UserDeckDraft(
        origin = Origin.New,
        deckId = deckId,
        createdAt = at,
        baseVersion = 0,
        authorId = LOCAL_AUTHOR_ID,
        metadataLocalizations = mapOf("ja" to DeckMetadataLocalization("", "", emptyList())),
        defaultLocale = "ja",
        name = "",
        authorNickname = "",
        type = DeckType.WORD,
        level = 1,
        tags = emptyList(),
        items = listOf(
          UserDeckItemDraft(
            id = makeIdentifier("item_", uuidHex),
            localizations = mapOf("ja" to DeckItemLocalization("", "")),
          ),
        ),
      )
    }

    /** Edits a user deck in place (official decks must be copied). */
    fun editing(deck: Deck): UserDeckDraft {
      require(!deck.official) { "Official decks must be copied instead of edited in place." }
      return UserDeckDraft(
        origin = Origin.Editing,
        deckId = deck.deckId,
        createdAt = deck.createdAt,
        baseVersion = deck.version,
        authorId = deck.author.id,
        metadataLocalizations = deck.localizations,
        defaultLocale = deck.defaultLocale ?: "ja",
        name = deck.name,
        authorNickname = deck.author.nickname,
        type = deck.type,
        level = deck.level,
        tags = deck.tags,
        items = deck.items.map { UserDeckItemDraft(it.id, it.ko, it.readingJa, it.meaningJa, it.localizations) },
      )
    }

    fun copyingOfficial(deck: Deck, at: Instant = Instant.now(), uuidHex: () -> String = ::randomUuidHex): UserDeckDraft {
      require(deck.official) { "Only official decks use the copy workflow." }
      val deckId = makeIdentifier("user_", uuidHex)
      return UserDeckDraft(
        origin = Origin.OfficialCopy(deck.deckId),
        deckId = deckId,
        createdAt = at,
        baseVersion = 0,
        authorId = LOCAL_AUTHOR_ID,
        metadataLocalizations = deck.localizations,
        defaultLocale = deck.defaultLocale ?: "ja",
        name = deck.name,
        authorNickname = deck.author.nickname,
        type = deck.type,
        level = deck.level,
        tags = deck.tags,
        items = deck.items.map {
          UserDeckItemDraft(makeIdentifier("item_", uuidHex), it.ko, it.readingJa, it.meaningJa, it.localizations)
        },
      )
    }

    /** Splits on `,`, `、` and newlines; trims; drops empties. */
    fun parseTags(text: String): List<String> =
      text.split(',', '、', '\n').map { it.trimmedWhitespace() }.filter { it.isNotEmpty() }

    fun canonicalLocale(input: String): String? = LocaleTag.canonicalize(input.replace('_', '-'))

    fun isKoreanInput(value: String): Boolean = value.codePoints().allMatch { it == ' '.code || it in 0xAC00..0xD7A3 }

    /** Text-field filter for the Korean target (Hangul syllables and spaces, 9 syllables / 10 chars). */
    fun acceptedKoreanInput(current: String, proposed: String): String {
      val withinLimits = proposed.graphemeCount() <= MAXIMUM_KOREAN_CHARACTER_COUNT &&
        proposed.filter { it != ' ' }.graphemeCount() <= MAXIMUM_KOREAN_SYLLABLE_COUNT
      if (isKoreanInput(proposed) && withinLimits) return proposed
      return if (proposed.graphemeCount() < current.graphemeCount()) proposed else current
    }

    fun acceptedMeaningOrReadingInput(current: String, proposed: String): String {
      if (productCharacterCount(proposed) <= MAXIMUM_MEANING_OR_READING_COUNT) return proposed
      return if (productCharacterCount(proposed) < productCharacterCount(current)) proposed else current
    }

    fun productCharacterCount(value: String): Int = value.scalarCount()

    fun makeIdentifier(prefix: String, uuidHex: () -> String): String {
      val hex = uuidHex()
      require(hexPattern.matches(hex)) { "UUID generator must return 32 lowercase hexadecimal characters." }
      return prefix + hex
    }

    /** RFC 4122 v4 UUID as 32 lowercase hex characters (CSPRNG). */
    fun randomUuidHex(): String {
      val bytes = ByteArray(16)
      secureRandom.nextBytes(bytes)
      bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
      bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
      return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
  }
}
