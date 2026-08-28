package app.piyokey.core.piyodeck

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
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
  fun expandedEditingLanguagesPreserveExistingKoreanAndJapaneseFields() {
    val source = sampleDeck(official = false, deckId = "user_00000000000000000000000000000001")
    for ((language, meaning) in mapOf(
      UserDeckLanguage.SPANISH to "Hola",
      UserDeckLanguage.GERMAN to "Hallo",
      UserDeckLanguage.FRENCH to "Bonjour",
    )) {
      val draft = UserDeckDraft.editing(source)
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
        assertEquals(original.meaningJa, item.meaningJa)
        assertEquals(original.readingJa, item.readingJa)
        assertEquals(meaning, item.localizations?.get(language.code)?.meaning)
      }
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
