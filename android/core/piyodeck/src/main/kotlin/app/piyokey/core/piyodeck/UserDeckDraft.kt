package app.piyokey.core.piyodeck

import app.piyokey.core.deckkit.ContentValidationIssue
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckMetadataLocalization
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.DeckValidator
import app.piyokey.core.deckkit.LocaleTag
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

public enum class UserDeckLanguage(public val code: String) {
  JAPANESE("ja"),
  ENGLISH("en"),
  KOREAN("ko"),
  SPANISH("es"),
  GERMAN("de"),
  FRENCH("fr"),
}

public sealed interface UserDeckDraftOrigin {
  public data object New : UserDeckDraftOrigin
  public data object Editing : UserDeckDraftOrigin
  public data class OfficialCopy(val sourceDeckId: String) : UserDeckDraftOrigin
}

public data class UserDeckItemDraft(
  val id: String,
  val ko: String = "",
  val readingJa: String = "",
  val meaningJa: String = "",
  val localizations: Map<String, DeckItemLocalization>? = null,
) {
  public fun reading(language: UserDeckLanguage): String = reading(language.code)
  public fun reading(localeCode: String): String =
    if (localeCode == "ja") readingJa else localizations?.get(localeCode)?.reading.orEmpty()

  public fun meaning(language: UserDeckLanguage): String = meaning(language.code)
  public fun meaning(localeCode: String): String =
    if (localeCode == "ja") meaningJa else localizations?.get(localeCode)?.meaning.orEmpty()

  public fun withReading(value: String, language: UserDeckLanguage): UserDeckItemDraft =
    withReading(value, language.code)

  public fun withReading(value: String, localeCode: String): UserDeckItemDraft =
    if (localeCode == "ja") copy(readingJa = value) else {
      val current = localizations?.get(localeCode)
      copy(
        localizations = localizations.orEmpty() + (
          localeCode to DeckItemLocalization(
            meaning = current?.meaning.orEmpty(),
            reading = value,
          )
        ),
      )
    }

  public fun withMeaning(value: String, language: UserDeckLanguage): UserDeckItemDraft =
    withMeaning(value, language.code)

  public fun withMeaning(value: String, localeCode: String): UserDeckItemDraft =
    if (localeCode == "ja") copy(meaningJa = value) else {
      val current = localizations?.get(localeCode)
      copy(
        localizations = localizations.orEmpty() + (
          localeCode to DeckItemLocalization(
            meaning = value,
            reading = current?.reading.orEmpty(),
          )
        ),
      )
    }
}

public sealed interface UserDeckValidationField {
  public data object Name : UserDeckValidationField
  public data object Author : UserDeckValidationField
  public data object Tags : UserDeckValidationField
  public data object Items : UserDeckValidationField
  public data class Item(val index: Int) : UserDeckValidationField
  public data class ItemKorean(val index: Int) : UserDeckValidationField
  public data class ItemReading(val index: Int) : UserDeckValidationField
  public data class ItemMeaning(val index: Int) : UserDeckValidationField
}

public data class UserDeckValidationSummary(
  val issues: List<ContentValidationIssue>,
) {
  public val firstIssue: ContentValidationIssue?
    get() = issues.firstOrNull()

  public val firstField: UserDeckValidationField?
    get() = issues.firstNotNullOfOrNull { fieldFor(it.path) }

  private fun fieldFor(path: String): UserDeckValidationField? {
    if (path == "name" || (path.startsWith("localizations.") && path.endsWith(".name"))) {
      return UserDeckValidationField.Name
    }
    if (path == "author.nickname" || path.endsWith(".author_nickname")) {
      return UserDeckValidationField.Author
    }
    if (path == "tags" || path.startsWith("tags[") || path.contains(".tags")) {
      return UserDeckValidationField.Tags
    }
    if (path == "items") return UserDeckValidationField.Items
    val match = ITEM_PATH.matchEntire(path) ?: return null
    val index = match.groupValues[1].toInt()
    val field = match.groupValues[2]
    return when {
      field.startsWith("ko") -> UserDeckValidationField.ItemKorean(index)
      field.contains("meaning") -> UserDeckValidationField.ItemMeaning(index)
      field.contains("reading") || field.contains("localizations") ->
        UserDeckValidationField.ItemReading(index)
      else -> UserDeckValidationField.Item(index)
    }
  }

  private companion object {
    val ITEM_PATH = Regex("items\\[(\\d+)]\\.?(.*)")
  }
}

public data class UserDeckDraftValidationException(
  val issues: List<ContentValidationIssue>,
) : IllegalArgumentException(issues.joinToString("\n"))

public data class UserDeckDraft(
  val origin: UserDeckDraftOrigin,
  val deckId: String,
  val createdAt: Instant,
  val baseVersion: Int,
  val authorId: String,
  val metadataLocalizations: Map<String, DeckMetadataLocalization>? = null,
  val name: String,
  val authorNickname: String,
  val type: DeckType,
  val level: Int,
  val tags: List<String>,
  val items: List<UserDeckItemDraft>,
  val defaultLocale: String? = null,
) {
  public val derivedFromDeckId: String?
    get() = (origin as? UserDeckDraftOrigin.OfficialCopy)?.sourceDeckId

  public fun name(language: UserDeckLanguage): String = name(language.code)
  public fun name(localeCode: String): String =
    if (localeCode == "ja") name else metadataLocalizations?.get(localeCode)?.name.orEmpty()

  public fun authorNickname(language: UserDeckLanguage): String = authorNickname(language.code)
  public fun authorNickname(localeCode: String): String =
    if (localeCode == "ja") authorNickname else metadataLocalizations?.get(localeCode)?.authorNickname.orEmpty()

  public fun tags(language: UserDeckLanguage): List<String> = tags(language.code)
  public fun tags(localeCode: String): List<String> =
    if (localeCode == "ja") tags else metadataLocalizations?.get(localeCode)?.tags.orEmpty()

  public fun withName(value: String, language: UserDeckLanguage): UserDeckDraft =
    withName(value, language.code)
  public fun withName(value: String, localeCode: String): UserDeckDraft =
    updateMetadata(localeCode) { it.copy(name = value) }

  public fun withAuthorNickname(value: String, language: UserDeckLanguage): UserDeckDraft =
    withAuthorNickname(value, language.code)
  public fun withAuthorNickname(value: String, localeCode: String): UserDeckDraft =
    updateMetadata(localeCode) { it.copy(authorNickname = value) }

  public fun withTags(value: List<String>, language: UserDeckLanguage): UserDeckDraft =
    withTags(value, language.code)
  public fun withTags(value: List<String>, localeCode: String): UserDeckDraft =
    updateMetadata(localeCode) { it.copy(tags = value) }

  public fun addItem(hexGenerator: () -> String = ::randomUuidHex): UserDeckDraft =
    if (items.size >= PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT) this else copy(
      items = items + UserDeckItemDraft(makeIdentifier("item_", hexGenerator)),
    )

  public fun removeItem(index: Int): UserDeckDraft =
    if (items.size <= 1 || index !in items.indices) this else copy(
      items = items.filterIndexed { itemIndex, _ -> itemIndex != index },
    )

  public fun moveItem(fromIndex: Int, toIndex: Int): UserDeckDraft {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return this
    val mutable = items.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return copy(items = mutable)
  }

  /** Preserves the user's work after an edit base-version conflict. */
  public fun asSeparateCopy(
    now: Instant,
    hexGenerator: () -> String = ::randomUuidHex,
  ): UserDeckDraft = copy(
    origin = UserDeckDraftOrigin.New,
    deckId = makeIdentifier("user_", hexGenerator),
    createdAt = now.truncatedTo(ChronoUnit.SECONDS),
    baseVersion = 0,
    authorId = LOCAL_AUTHOR_ID,
    items = items.map { item -> item.copy(id = makeIdentifier("item_", hexGenerator)) },
  )

  public fun materialize(
    now: Instant,
    language: UserDeckLanguage,
  ): Deck = materialize(now, language.code)

  public fun materialize(now: Instant, localeCode: String): Deck {
    val canonicalNow = maxOf(now.truncatedTo(ChronoUnit.SECONDS), createdAt)
    val displayName = name(localeCode)
    val displayAuthor = authorNickname(localeCode)
    val displayTags = tags(localeCode)
    val resolvedDefaultLocale = defaultLocale
      ?: if (localeCode == "ja" || metadataLocalizations?.get(localeCode) == null) "ja" else localeCode
    var outputMetadata = compatibleMetadataLocalizations(baseTags = tags.ifEmpty { displayTags }, localeCode)
      .orEmpty()
    if (localeCode == "ja" || resolvedDefaultLocale == "ja") {
      outputMetadata = outputMetadata + (
        UserDeckLanguage.JAPANESE.code to DeckMetadataLocalization(name, authorNickname, tags)
      )
    }
    val defaultMetadata = outputMetadata[resolvedDefaultLocale]
    val japaneseMetadata = outputMetadata[UserDeckLanguage.JAPANESE.code]
    val baseName = japaneseMetadata?.name ?: name.ifBlank { defaultMetadata?.name ?: displayName }
    val baseAuthor = japaneseMetadata?.authorNickname
      ?: authorNickname.ifBlank { defaultMetadata?.authorNickname ?: displayAuthor }
    val baseTags = japaneseMetadata?.tags ?: tags.ifEmpty { defaultMetadata?.tags ?: displayTags }
    val declaredCodes = outputMetadata.keys
    return Deck(
      deckId = deckId,
      version = if (origin == UserDeckDraftOrigin.Editing) baseVersion + 1 else 1,
      name = baseName,
      author = DeckAuthor(authorId, baseAuthor),
      official = false,
      type = type,
      level = level,
      tags = baseTags,
      createdAt = createdAt,
      updatedAt = canonicalNow,
      items = items.map { item ->
        var itemLocalizations = item.localizations.orEmpty()
        if (localeCode == "ja" || resolvedDefaultLocale == "ja") {
          itemLocalizations = itemLocalizations + (
            UserDeckLanguage.JAPANESE.code to DeckItemLocalization(item.meaningJa, item.readingJa)
          )
        }
        itemLocalizations = itemLocalizations.filterKeys(declaredCodes::contains)
        val japanese = itemLocalizations[UserDeckLanguage.JAPANESE.code]
        val fallback = itemLocalizations[resolvedDefaultLocale]
        DeckItem(
          id = item.id,
          ko = item.ko,
          readingJa = japanese?.reading ?: item.readingJa.ifBlank { fallback?.reading ?: item.reading(localeCode) },
          meaningJa = japanese?.meaning ?: item.meaningJa.ifBlank { fallback?.meaning ?: item.meaning(localeCode) },
          audio = null,
          localizations = itemLocalizations,
        )
      },
      localizations = outputMetadata,
      defaultLocale = resolvedDefaultLocale,
    )
  }

  public fun validationSummary(now: Instant, language: UserDeckLanguage): UserDeckValidationSummary =
    validationSummary(now, language.code)
  public fun validationSummary(now: Instant, localeCode: String): UserDeckValidationSummary =
    UserDeckValidationSummary(validationIssues(materialize(now, localeCode)))

  public fun validatedDeck(now: Instant, language: UserDeckLanguage): Deck {
    return validatedDeck(now, language.code)
  }

  public fun validatedDeck(now: Instant, localeCode: String): Deck {
    val deck = materialize(now, localeCode)
    val issues = validationIssues(deck)
    if (issues.isNotEmpty()) throw UserDeckDraftValidationException(issues)
    return deck
  }

  private fun validationIssues(deck: Deck): List<ContentValidationIssue> = buildList {
    addAll(DeckValidator.validate(deck))
    addAll(PiyoDeckUserDeckValidator.validate(deck).map { ContentValidationIssue(it.code, it.path, it.message) })
    maximumLength(deck.name, 120, "name")
    maximumLength(deck.author.nickname, 40, "author.nickname")
    deck.tags.forEachIndexed { index, tag -> maximumLength(tag, 40, "tags[$index]") }
    deck.items.forEachIndexed { index, item ->
      maximumLength(item.readingJa, 300, "items[$index].reading_ja")
      maximumLength(item.meaningJa, 500, "items[$index].meaning_ja")
    }
  }

  private fun MutableList<ContentValidationIssue>.maximumLength(
    value: String,
    maximum: Int,
    path: String,
  ) {
    if (value.length > maximum) {
      add(ContentValidationIssue("max_length", path, "Maximum length exceeded"))
    }
  }

  private fun updateMetadata(
    localeCode: String,
    transform: (DeckMetadataLocalization) -> DeckMetadataLocalization,
  ): UserDeckDraft {
    if (localeCode == "ja") {
      val value = transform(DeckMetadataLocalization(name, authorNickname, tags))
      return copy(name = value.name, authorNickname = value.authorNickname, tags = value.tags)
    }
    val current = metadataLocalizations?.get(localeCode)
      ?: DeckMetadataLocalization("", "", emptyList())
    return copy(metadataLocalizations = metadataLocalizations.orEmpty() + (localeCode to transform(current)))
  }

  private fun compatibleMetadataLocalizations(
    baseTags: List<String>,
    localeCode: String,
  ): Map<String, DeckMetadataLocalization>? {
    val filtered = metadataLocalizations.orEmpty().filter { (code, localization) ->
      if (localeCode != "ja" && code == localeCode) return@filter true
      if (localization.name.isBlank() || localization.authorNickname.isBlank()) return@filter false
      if (localization.tags.size != baseTags.size) return@filter false
      items.all { item ->
        item.localizations?.get(code)?.let { it.meaning.isNotBlank() && it.reading.isNotBlank() } == true
      }
    }
    return filtered.ifEmpty { null }
  }

  public companion object {
    public const val LOCAL_AUTHOR_ID: String = "user_local"

    public fun new(
      now: Instant,
      hexGenerator: () -> String = ::randomUuidHex,
    ): UserDeckDraft = UserDeckDraft(
      origin = UserDeckDraftOrigin.New,
      deckId = makeIdentifier("user_", hexGenerator),
      createdAt = now.truncatedTo(ChronoUnit.SECONDS),
      baseVersion = 0,
      authorId = LOCAL_AUTHOR_ID,
      name = "",
      authorNickname = "",
      type = DeckType.WORD,
      level = 1,
      tags = emptyList(),
      items = listOf(UserDeckItemDraft(makeIdentifier("item_", hexGenerator))),
    )

    public fun editing(deck: Deck): UserDeckDraft {
      require(!deck.official) { "Official decks must be copied instead of edited in place." }
      return fromDeck(deck, UserDeckDraftOrigin.Editing, deck.deckId, deck.createdAt, deck.version)
    }

    public fun copyingOfficial(
      deck: Deck,
      now: Instant,
      hexGenerator: () -> String = ::randomUuidHex,
    ): UserDeckDraft {
      require(deck.official) { "Only official decks use the copy workflow." }
      val deckId = makeIdentifier("user_", hexGenerator)
      return UserDeckDraft(
        origin = UserDeckDraftOrigin.OfficialCopy(deck.deckId),
        deckId = deckId,
        createdAt = now.truncatedTo(ChronoUnit.SECONDS),
        baseVersion = 0,
        authorId = LOCAL_AUTHOR_ID,
        metadataLocalizations = deck.localizations,
        name = deck.name,
        authorNickname = deck.author.nickname,
        type = deck.type,
        level = deck.level,
        tags = deck.tags,
        items = deck.items.map { item ->
          UserDeckItemDraft(
            id = makeIdentifier("item_", hexGenerator),
            ko = item.ko,
            readingJa = item.readingJa,
            meaningJa = item.meaningJa,
            localizations = item.localizations,
          )
        },
        defaultLocale = deck.defaultLocale ?: UserDeckLanguage.JAPANESE.code,
      )
    }

    private fun fromDeck(
      deck: Deck,
      origin: UserDeckDraftOrigin,
      deckId: String,
      createdAt: Instant,
      baseVersion: Int,
    ): UserDeckDraft = UserDeckDraft(
      origin = origin,
      deckId = deckId,
      createdAt = createdAt,
      baseVersion = baseVersion,
      authorId = deck.author.id,
      metadataLocalizations = deck.localizations,
      name = deck.name,
      authorNickname = deck.author.nickname,
      type = deck.type,
      level = deck.level,
      tags = deck.tags,
      items = deck.items.map { item ->
        UserDeckItemDraft(item.id, item.ko, item.readingJa, item.meaningJa, item.localizations)
      },
      defaultLocale = deck.defaultLocale ?: UserDeckLanguage.JAPANESE.code,
    )

    public fun canonicalLocale(input: String): String? =
      LocaleTag.canonicalize(input.replace('_', '-'))

    public fun parseTags(text: String): List<String> = text
      .split(',', '、', '\n')
      .map(String::trim)
      .filter(String::isNotEmpty)

    private fun makeIdentifier(prefix: String, generator: () -> String): String {
      val hex = generator()
      require(HEX_IDENTIFIER.matches(hex)) { "UUID generator must return 32 lowercase hexadecimal characters." }
      return prefix + hex
    }

    private fun randomUuidHex(): String = UUID.randomUUID().toString().replace("-", "")
    private val HEX_IDENTIFIER = Regex("^[0-9a-f]{32}$")
  }
}
