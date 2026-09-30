package app.piyokey.core.deckkit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every bundled JSON deck and catalog the Android app ships must parse and validate. */
class BundledContentTests {
  private fun deckFiles(relative: String): List<File> =
    File(Shared.root, relative).walkTopDown()
      .filter { it.isFile && it.extension == "json" }
      .sortedBy { it.path }
      .toList()

  private fun assertDeckFileIsValid(file: File) {
    val data = file.readBytes()
    val label = file.relativeTo(Shared.root).path
    assertEquals(emptyList(), JsonSchemaValidator.validate(data, Shared.deckSchema), label)
    PiyoDeckStrictJson.validate(data, label)
    val deck = DeckJson.decodeDeck(data)
    assertEquals(emptyList(), DeckValidator.validate(deck), label)
    assertTrue(deck.items.all { DeckKitHangul.isDecomposable(it.ko) }, label)
    // Encoding and decoding again is lossless.
    assertEquals(deck, DeckJson.decodeDeck(DeckJson.encodeDeck(deck)), label)
    assertEquals(deck, DeckJson.decodeDeck(DeckJson.encodeDeck(deck, pretty = false)), label)
  }

  @Test
  fun everyBundledDeckUnderDecksParsesAndValidates() {
    val files = deckFiles("mock_catalog/decks")
    // 26 official catalog decks + 5 game directories x 3 TOPIK levels.
    assertEquals(41, files.size)
    files.forEach(::assertDeckFileIsValid)
  }

  @Test
  fun everyUpdateFixtureDeckParsesAndValidates() {
    val files = deckFiles("mock_catalog/updates/decks")
    assertEquals(26, files.size)
    files.forEach(::assertDeckFileIsValid)
  }

  @Test
  fun bothCatalogsParseValidateAndMatchTheirDecks() {
    val catalogSchema = Shared.schema("catalog.schema.json")
    for (base in listOf("mock_catalog", "mock_catalog/updates")) {
      val data = Shared.bytes("$base/catalog.json")
      assertEquals(emptyList(), JsonSchemaValidator.validate(data, catalogSchema), base)
      PiyoDeckStrictJson.validate(data, "$base/catalog.json")
      val catalog = DeckJson.decodeCatalog(data)
      assertEquals(emptyList(), CatalogValidator.validate(catalog), base)
      val decks = catalog.decks.map { Shared.loadDeck(it, base) }
      assertEquals(emptyList(), CatalogBundleValidator.validate(catalog, decks), base)
      assertEquals(catalog, DeckJson.decodeCatalog(DeckJson.encodeCatalog(catalog)), base)
    }
  }

  @Test
  fun sourceFixtureDecksDecode() {
    for (name in listOf(
      "valid/basic-deck.json", "valid/localized-deck.json", "valid/multilingual-deck.json",
    )) {
      val deck = DeckJson.decodeDeck(Shared.fixture(name))
      assertEquals(emptyList(), UserDeckValidator.validate(deck), name)
    }
  }
}
