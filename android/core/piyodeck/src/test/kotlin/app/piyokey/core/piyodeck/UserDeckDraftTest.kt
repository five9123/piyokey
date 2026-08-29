package app.piyokey.core.piyodeck

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckMetadataLocalization
import app.piyokey.core.deckkit.DeckType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDeckDraftTest {
  private val now = Instant.parse("2026-08-25T01:02:03Z")
  private var sequence = 0
  private val nextHex = { (++sequence).toString(16).padStart(32, '0') }

  @Test
  fun expandedEditingLanguagesCreateOneToOneUserDecks() {
    val source = sampleDeck(official = false, deckId = "user_00000000000000000000000000000001")
    for ((language, meaning) in mapOf(
      UserDeckLanguage.SPANISH to "Hola",
      UserDeckLanguage.GERMAN to "Hallo",
      UserDeckLanguage.FRENCH to "Bonjour",
    )) {
      val draft = UserDeckDraft.editing(source)
        .retagDeckLanguage(language.code)
        .withName("Test", language)
        .withAuthorNickname("Piyo", language)
        .withTags(source.tags, language)
        .let { current -> current.copy(items = current.items.map {
          it.withReading("annyeong", language).withMeaning(meaning, language)
        }) }
      val saved = draft.validatedDeck(now.plusSeconds(10), language)
      assertEquals(source.deckId, saved.deckId)
      saved.items.zip(source.items).forEach { (item, original) ->
        assertEquals(original.id, item.id)
        assertEquals(original.ko, item.ko)
        assertEquals(meaning, item.meaningJa)
        assertEquals("annyeong", item.readingJa)
        assertEquals(meaning, item.localizations?.get(language.code)?.meaning)
        assertEquals(setOf(language.code), item.localizations.orEmpty().keys)
      }
      assertEquals(language.code, saved.defaultLocale)
      assertEquals(setOf(language.code), saved.localizations.orEmpty().keys)
    }
  }

  @Test
  fun newDraftCreatesStableUserIdentifiersAndOneEditableItem() {
    val draft = UserDeckDraft.new(now, nextHex)

    assertEquals("user_00000000000000000000000000000001", draft.deckId)
    assertEquals("item_00000000000000000000000000000002", draft.items.single().id)
    assertEquals(UserDeckDraftOrigin.New, draft.origin)
    assertEquals(0, draft.baseVersion)
    assertNull(draft.derivedFromDeckId)
  }

  @Test
  fun officialCopyGetsNewDeckAndItemIdentifiersAndProvenance() {
    val official = sampleDeck(official = true, deckId = "official_basic")
    val draft = UserDeckDraft.copyingOfficial(official, now, nextHex)
    val materialized = draft.validatedDeck(now, UserDeckLanguage.JAPANESE)

    assertEquals(official.deckId, draft.derivedFromDeckId)
    assertTrue(materialized.deckId.startsWith("user_"))
    assertTrue(materialized.items.single().id.startsWith("item_"))
    assertEquals(1, materialized.version)
    assertEquals(false, materialized.official)
    assertEquals("ja", materialized.defaultLocale)
    assertEquals("行く", materialized.items.single().localizations?.get("ja")?.meaning)
  }

  @Test
  fun editingPreservesIdentifiersAndIncrementsVersion() {
    val deck = sampleDeck(official = false, deckId = "user_00000000000000000000000000000001")
      .copy(version = 7)
    val materialized = UserDeckDraft.editing(deck)
      .copy(name = "Updated")
      .validatedDeck(now.plusSeconds(10), UserDeckLanguage.JAPANESE)

    assertEquals(deck.deckId, materialized.deckId)
    assertEquals(deck.items.single().id, materialized.items.single().id)
    assertEquals(8, materialized.version)
  }

  @Test
  fun currentNonJapaneseFieldsMirrorIntoRequiredJapaneseBase() {
    val draft = UserDeckDraft.new(now, nextHex)
      .retagDeckLanguage(UserDeckLanguage.ENGLISH.code)
      .withName("My deck", UserDeckLanguage.ENGLISH)
      .withAuthorNickname("Me", UserDeckLanguage.ENGLISH)
      .withTags(listOf("daily"), UserDeckLanguage.ENGLISH)
      .let { current ->
        current.copy(
          items = current.items.map {
            it.copy(ko = "가")
              .withReading("ga", UserDeckLanguage.ENGLISH)
              .withMeaning("go", UserDeckLanguage.ENGLISH)
          },
        )
      }
    val deck = draft.validatedDeck(now, UserDeckLanguage.ENGLISH)

    assertEquals("My deck", deck.name)
    assertEquals("Me", deck.author.nickname)
    assertEquals("ga", deck.items.single().readingJa)
    assertEquals("go", deck.items.single().meaningJa)
    assertEquals("en", deck.defaultLocale)
  }

  @Test
  fun unknownCanonicalLocaleSurvivesLegacySelectionAndRoundTrip() {
    val unknownLocale = "sl-rozaj-biske"
    val source = sampleDeck(false, "user_00000000000000000000000000000001").copy(
      defaultLocale = "ar",
      localizations = mapOf(
        "ar" to DeckMetadataLocalization("كلمات", "Piyo", listOf("daily")),
        unknownLocale to DeckMetadataLocalization("Besede", "Piyo", listOf("daily")),
      ),
      items = listOf(
        sampleDeck(false, "user_00000000000000000000000000000001").items.single().copy(
          localizations = mapOf(
            "ar" to DeckItemLocalization("مرحبا", "annyeong"),
            unknownLocale to DeckItemLocalization("pozdrav", "annyeong"),
          ),
        ),
      ),
    )

    val multilingual = UserDeckDraft.editing(source)
    assertTrue(multilingual.requiresContentBundleSelection)
    assertEquals(setOf("ar", unknownLocale), multilingual.contentLocaleCodes.toSet())
    assertFailsWith<UserDeckDraftValidationException> {
      multilingual.validatedDeck(now, "ar")
    }
    val edited = multilingual
      .selectContentBundle(unknownLocale)
      .validatedDeck(now, unknownLocale)

    assertEquals(unknownLocale, edited.defaultLocale)
    assertEquals(source.localizations?.get(unknownLocale), edited.localizations?.get(unknownLocale))
    assertEquals(
      source.items.single().localizations?.get(unknownLocale),
      edited.items.single().localizations?.get(unknownLocale),
    )
    assertEquals(setOf(unknownLocale), edited.localizations.orEmpty().keys)
    assertEquals(setOf(unknownLocale), edited.items.single().localizations.orEmpty().keys)
  }

  @Test
  fun invalidDraftReportsFirstFocusableField() {
    val summary = UserDeckDraft.new(now, nextHex)
      .validationSummary(now, UserDeckLanguage.JAPANESE)

    assertTrue(summary.issues.isNotEmpty())
    assertIs<UserDeckValidationField.Name>(summary.firstField)
    assertFailsWith<UserDeckDraftValidationException> {
      UserDeckDraft.new(now, nextHex).validatedDeck(now, UserDeckLanguage.JAPANESE)
    }
  }

  @Test
  fun itemMutationNeverDropsLastItemAndHonorsLimit() {
    val draft = UserDeckDraft.new(now, nextHex)
    assertEquals(draft, draft.removeItem(0))
    assertEquals(2, draft.addItem(nextHex).items.size)

    val full = draft.copy(items = List(PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT) { draft.items.single() })
    assertEquals(PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT, full.addItem(nextHex).items.size)
  }

  @Test
  fun parseTagsAcceptsSupportedSeparators() {
    assertEquals(listOf("one", "two", "three"), UserDeckDraft.parseTags(" one, two、three\n"))
  }

  @Test
  fun deckLanguageRetagPreservesMetadataMeaningAndReading() {
    val original = sampleDeck(false, "user_00000000000000000000000000000001")
    val draft = UserDeckDraft.editing(original).retagDeckLanguage("fr-CA")
    val saved = draft.validatedDeck(now, "fr-CA")

    assertEquals("fr-CA", saved.defaultLocale)
    assertEquals(setOf("fr-CA"), saved.localizations.orEmpty().keys)
    assertEquals(original.name, saved.name)
    assertEquals(original.author.nickname, saved.author.nickname)
    assertEquals(original.items.single().meaningJa, saved.items.single().meaningJa)
    assertEquals(original.items.single().readingJa, saved.items.single().readingJa)
    assertEquals(
      DeckItemLocalization(original.items.single().meaningJa, original.items.single().readingJa),
      saved.items.single().localizations?.get("fr-CA"),
    )
  }

  @Test
  fun officialFiveLanguageBundleAndKoreanMetadataStayIntactUntilSelection() {
    val localeCodes = setOf("ja", "en", "es", "de", "fr", "ko")
    val metadata = localeCodes.associateWith { code ->
      DeckMetadataLocalization("name-$code", "Piyo", listOf(code))
    }
    val itemLocalizations = localeCodes.associateWith { code ->
      DeckItemLocalization("meaning-$code", "reading-$code")
    }
    val source = sampleDeck(true, "official_basic").copy(
      localizations = metadata,
      defaultLocale = "ja",
      items = sampleDeck(true, "official_basic").items.map { item ->
        item.copy(localizations = itemLocalizations)
      },
    )

    val untouched = UserDeckDraft.copyingOfficial(source, now, nextHex)
    assertTrue(untouched.requiresContentBundleSelection)
    assertEquals(localeCodes, untouched.contentLocaleCodes.toSet())
    assertEquals(source.localizations, untouched.metadataLocalizations)
    assertEquals(source.items.single().localizations, untouched.items.single().localizations)

    val selected = untouched.selectContentBundle("fr")
    assertEquals(mapOf("fr" to metadata.getValue("fr")), selected.metadataLocalizations)
    assertEquals(mapOf("fr" to itemLocalizations.getValue("fr")), selected.items.single().localizations)
    assertEquals(localeCodes, source.localizations.orEmpty().keys)
    assertEquals(metadata.getValue("ko"), source.localizations?.get("ko"))
  }

  @Test
  fun productInputBoundariesAndLegacyValidationPreservation() {
    val nineSyllablesAndOneSpace = "가가가가 나나나나나"
    assertEquals(10, nineSyllablesAndOneSpace.length)
    assertEquals(
      nineSyllablesAndOneSpace,
      UserDeckDraft.acceptedKoreanInput("", nineSyllablesAndOneSpace),
    )
    assertEquals(
      nineSyllablesAndOneSpace,
      UserDeckDraft.acceptedKoreanInput(nineSyllablesAndOneSpace, "가가가가가나나나나나"),
    )
    assertEquals("가", UserDeckDraft.acceptedKoreanInput("가", "가a"))
    val twenty = "a".repeat(20)
    assertEquals(twenty, UserDeckDraft.acceptedMeaningOrReadingInput("", twenty))
    assertEquals(twenty, UserDeckDraft.acceptedMeaningOrReadingInput(twenty, twenty + "a"))

    val source = sampleDeck(false, "user_00000000000000000000000000000001")
    val draft = UserDeckDraft.editing(source).copy(
      items = UserDeckDraft.editing(source).items.map { item ->
        item.copy(
          ko = "가가가가가나나나나나",
          readingJa = "r".repeat(21),
          meaningJa = "m".repeat(21),
        )
      },
    )
    val unchanged = draft.materialize(now, "ja")
    assertEquals(draft.items.single().ko, unchanged.items.single().ko)
    assertEquals(draft.items.single().readingJa, unchanged.items.single().readingJa)
    assertEquals(draft.items.single().meaningJa, unchanged.items.single().meaningJa)
    val error = assertFailsWith<UserDeckDraftValidationException> { draft.validatedDeck(now, "ja") }
    assertTrue(error.issues.any { it.code == "user_deck_korean_length" })
    assertTrue(error.issues.any { it.code == "user_deck_text_length" })
  }

  private fun sampleDeck(official: Boolean, deckId: String): Deck = Deck(
    deckId = deckId,
    version = 1,
    name = "기본",
    author = DeckAuthor(if (official) "official_team" else UserDeckDraft.LOCAL_AUTHOR_ID, "피요키"),
    official = official,
    type = DeckType.WORD,
    level = 1,
    tags = listOf("기초"),
    createdAt = Instant.parse("2026-08-20T00:00:00Z"),
    updatedAt = Instant.parse("2026-08-20T00:00:00Z"),
    items = listOf(
      DeckItem(
        id = if (official) "official_item" else "item_00000000000000000000000000000002",
        ko = "가",
        readingJa = "カ",
        meaningJa = "行く",
        audio = null,
      ),
    ),
  )
}
