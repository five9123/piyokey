package app.piyokey.core.domain

import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.UserDeckValidator
import app.piyokey.core.domain.TestSupport.at
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDeckDraftTest {
  private fun sequence(vararg values: String): () -> String {
    val iterator = values.iterator()
    return { iterator.next() }
  }

  private val userDeck = Deck(
    deckId = "user_0123456789abcdef0123456789abcdef", version = 1, name = "私のデッキ",
    author = DeckAuthor("user_local", "Learner"), official = false, type = DeckType.WORD, level = 1,
    tags = listOf("個人"), createdAt = at(100), updatedAt = at(200),
    items = listOf(DeckItem(id = "item_0123456789abcdef0123456789abcdef", ko = "한글", readingJa = "ハングル", meaningJa = "編集前", audio = null)),
  )

  @Test
  fun editedUserDeckValidatesWithIdentityAndBumpedVersion() {
    val draft = UserDeckDraft.editing(userDeck)
      .copy(name = "保存済みProデッキ")
      .updatingItem(0) { it.copy(meaningJa = "編集後") }
      .addingItem { "22222222222222222222222222222222" }
      .updatingItem(1) { it.copy(ko = "학교", readingJa = "ハッキョ", meaningJa = "学校") }

    val deck = draft.validatedDeck(at(300), "ja")

    assertEquals(userDeck.deckId, deck.deckId)
    assertEquals(2, deck.version)
    assertEquals("保存済みProデッキ", deck.name)
    assertEquals(listOf(userDeck.items[0].id, "item_22222222222222222222222222222222"), deck.items.map { it.id })
    assertEquals(listOf("編集後", "学校"), deck.items.map { it.meaningJa })
    assertEquals("ja", deck.defaultLocale)
    assertEquals(at(300), deck.updatedAt)
    assertTrue(UserDeckValidator.validate(deck).isEmpty())
  }

  @Test
  fun newDraftStartsJapaneseAndReportsRequiredFields() {
    val draft = UserDeckDraft.new(at(1_000), sequence("1".repeat(32), "2".repeat(32)))
    assertEquals("user_" + "1".repeat(32), draft.deckId)
    assertEquals("item_" + "2".repeat(32), draft.items.single().id)
    assertEquals(UserDeckDraft.Origin.New, draft.origin)
    val summary = draft.validationSummary(at(1_000), "ja")
    assertTrue("deck_editor.validation.name_required" in summary.localizationKeys)
    assertTrue(!summary.isEmpty)
    assertFailsWith<UserDeckDraftValidationException> { draft.validatedDeck(at(1_000), "ja") }
  }

  @Test
  fun productBoundariesAndInputFilters() {
    assertEquals("가나", UserDeckDraft.acceptedKoreanInput("가", "가나"))
    assertEquals("가", UserDeckDraft.acceptedKoreanInput("가", "가a"))
    assertEquals("가나다라마바사아자", UserDeckDraft.acceptedKoreanInput("가나다라마바사아자", "가나다라마바사아자차"))
    assertEquals("가 나", UserDeckDraft.acceptedKoreanInput("가", "가 나"))
    assertEquals("a".repeat(20), UserDeckDraft.acceptedMeaningOrReadingInput("", "a".repeat(20)))
    assertEquals("", UserDeckDraft.acceptedMeaningOrReadingInput("", "a".repeat(21)))
    assertEquals(listOf("a", "b", "c"), UserDeckDraft.parseTags(" a、b,\n c ,"))

    val tooLong = UserDeckDraft.editing(userDeck).updatingItem(0) { it.copy(meaningJa = "あ".repeat(21)) }
    val issues = tooLong.validationIssues(at(300), "ja")
    assertTrue(issues.any { it.code == "user_deck_text_length" && it.path == "items[0].meaning_ja" })
    assertEquals(
      UserDeckValidationField(UserDeckValidationFieldKind.ITEM_MEANING, 0),
      UserDeckValidationSummary.field("items[0].meaning_ja"),
    )
  }

  @Test
  fun itemListEditsKeepAtLeastOneItemAndMove() {
    val draft = UserDeckDraft.new(at(1), sequence("1".repeat(32), "2".repeat(32), "3".repeat(32), "4".repeat(32)))
      .addingItem { "3".repeat(32) }
      .addingItem { "4".repeat(32) }
    assertEquals(3, draft.items.size)
    assertEquals(draft, draft.removingItems(setOf(0, 1, 2)))
    val moved = draft.movingItems(setOf(0), 3)
    assertEquals(listOf("item_" + "3".repeat(32), "item_" + "4".repeat(32), "item_" + "2".repeat(32)), moved.items.map { it.id })
  }

  @Test
  fun officialCopyAndUserCopyGetFreshIdentifiers() {
    val official = userDeck.copy(deckId = "official_source", official = true)
    val copy = UserDeckDraft.copyingOfficial(official, at(8_000), sequence("6".repeat(32), "7".repeat(32)))
    assertEquals(UserDeckDraft.Origin.OfficialCopy("official_source"), copy.origin)
    assertEquals("official_source", copy.derivedFromDeckId)
    assertEquals("item_" + "7".repeat(32), copy.items.single().id)
    assertEquals(1, copy.materializedDeck(at(9_000), "ja").version)

    val userCopy = PiyoDeckDocumentRules.makeUserCopy(official, at(5), sequence("a".repeat(32), "b".repeat(32)))
    assertEquals("user_" + "a".repeat(32), userCopy.deckId)
    assertEquals(UserDeckDraft.LOCAL_AUTHOR_ID, userCopy.author.id)
    assertEquals(false, userCopy.official)
    assertNull(userCopy.items.single().audio)
    assertEquals("My-deck", PiyoDeckDocumentRules.safeFilename("My deck!"))
    assertEquals("piyokey-deck", PiyoDeckDocumentRules.safeFilename("!!!"))
    assertTrue(UserDeckDraft.randomUuidHex().matches(Regex("^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$")))
  }
}
