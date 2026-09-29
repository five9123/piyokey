package app.piyokey.core.domain

import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.DeckType
import java.io.File
import java.time.Instant
import java.time.LocalDate

internal object TestSupport {
  val sharedRoot: File = File(requireNotNull(System.getProperty("piyokey.sharedRoot")) { "piyokey.sharedRoot is not set" })

  val bundledCatalog: Catalog by lazy {
    DeckJson.decodeCatalog(File(sharedRoot, "mock_catalog/catalog.json").readBytes())
  }

  fun jstNoon(year: Int, month: Int, day: Int): Instant = RetentionCalendar.date(JstDay.of(LocalDate.of(year, month, day)))

  fun day(raw: String): JstDay = requireNotNull(JstDay.parse(raw))

  fun at(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

  fun item(id: String, ko: String, reading: String = "テスト", meaning: String = "テスト") =
    DeckItem(id = id, ko = ko, readingJa = reading, meaningJa = meaning, audio = null)

  fun wordDeck(id: String, tags: List<String>, words: List<String>, official: Boolean = true) = Deck(
    deckId = id,
    version = 1,
    name = id,
    author = DeckAuthor("official", "PIYOKEY"),
    official = official,
    type = DeckType.WORD,
    level = 1,
    tags = tags,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    items = words.mapIndexed { index, word -> item("$id-$index", word) },
  )
}
