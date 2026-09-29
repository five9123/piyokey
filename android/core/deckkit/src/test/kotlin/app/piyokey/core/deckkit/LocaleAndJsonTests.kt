package app.piyokey.core.deckkit

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocaleAndJsonTests {
  @Test
  fun localeTagCanonicalizesStructurallyValidTags() {
    assertEquals("zh-Hant-TW", LocaleTag.canonicalize("ZH-hant-tw"))
    assertEquals("es-419", LocaleTag.canonicalize("es-419"))
    assertEquals("sl-rozaj-biske", LocaleTag.canonicalize("SL-Rozaj-BISKE"))
    assertEquals("en-a-bbb-x-priv", LocaleTag.canonicalize("en-A-BBB-X-PRIV"))
    assertEquals("x-whatever", LocaleTag.canonicalize("X-Whatever"))
    for (bad in listOf("", "e", "fr_CA", "fr--CA", "-fr", "fr-", "de-1901-1901", "en-a", "en-x",
      "en-a-bb-a-cc", "123", "toolongtag", "ar-العربية")) {
      assertNull(LocaleTag.canonicalize(bad), bad)
    }
    assertEquals(listOf("fr-CA", "fr"), LocaleTag.requestedCandidates("fr_ca"))
    assertEquals(emptyList(), LocaleTag.requestedCandidates("fr__ca"))
    assertEquals(listOf("pt-BR", "pt", "ar", "en"), LocaleTag.lookupCandidates("pt-BR", "ar"))
    assertEquals(listOf("en-GB", "en"), LocaleTag.lookupCandidates("en-GB", "en"))
    assertTrue(LocaleTag.isJapanese("ja_JP"))
  }

  @Test
  fun catalogTagUsesPrimaryLanguageThenEnglish() {
    val tag = CatalogTag("日常", 1, "topic", mapOf("en" to "Daily", "fr" to "Quotidien"))
    assertEquals("日常", tag.localizedTag("ja-JP"))
    assertEquals("Quotidien", tag.localizedTag("fr_CA"))
    assertEquals("Daily", tag.localizedTag("de"))
  }

  @Test
  fun deckJsonToleratesUnknownKeysButRequiresFields() {
    val deck = DeckJson.decodeDeck(
      """{"deck_id":"abc","version":1,"name":"n","author":{"id":"abc","nickname":"n","x":1},
        |"official":false,"type":"sentence","level":2,"tags":["t"],"future":true,
        |"created_at":"2026-01-01T09:00:00+09:00","updated_at":"2026-01-01T00:00:00Z",
        |"items":[{"id":"i","ko":"가","reading_ja":"カ","meaning_ja":"か"}]}""".trimMargin(),
    )
    assertEquals(DeckType.SENTENCE, deck.type)
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), deck.createdAt)
    assertNull(deck.items.single().audio)
    assertNull(deck.defaultLocale)
    assertTrue(DeckJson.encodeDeck(deck).contains("\"created_at\": \"2026-01-01T00:00:00Z\""))

    assertFailsWith<Exception> { DeckJson.decodeDeck("""{"deck_id":"abc"}""") }
    assertFailsWith<Exception> {
      DeckJson.decodeDeck(
        """{"deck_id":"abc","version":1,"name":"n","author":{"id":"abc","nickname":"n"},
          |"official":false,"type":"poem","level":2,"tags":["t"],
          |"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z",
          |"items":[]}""".trimMargin(),
      )
    }
    assertNull(Iso8601InstantSerializer.parse("2026-01-01T00:00:00.5Z"))
    assertNull(Iso8601InstantSerializer.parse("2026-01-01T00:00:00"))
  }

  @Test
  fun hangulHelperMatchesJamoDecomposerRules() {
    assertTrue(DeckKitHangul.isDecomposable("안녕하세요!"))
    assertTrue(DeckKitHangul.isDecomposable("ㄳ ㅘ 123"))
    assertEquals("unsupportedCharacter(\"A\", offset: 0)", DeckKitHangul.decompositionError("ABC"))
    assertTrue(DeckKitHangul.containsHangul("ㅏ"))
    assertTrue(!DeckKitHangul.containsHangul("ㄳ"))
    assertEquals(1, DeckKitText.graphemeCount("é"))
    assertTrue(DeckKitText.isBlank(" 　\n"))
  }
}
