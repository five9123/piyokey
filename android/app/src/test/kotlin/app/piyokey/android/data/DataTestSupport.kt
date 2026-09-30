package app.piyokey.android.data

import app.piyokey.android.data.catalog.BundleCatalogRepository
import app.piyokey.android.data.persistence.DirectoryAssetSource
import app.piyokey.android.data.persistence.sha256Hex
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.DeckType
import java.io.File
import java.nio.file.Files
import java.time.Instant

internal object DataTestSupport {
  /** Gradle runs app unit tests from `android/app`. */
  val sharedRoot: File = listOf(File("../../shared"), File("../shared"), File("shared")).first { File(it, "mock_catalog").isDirectory }

  val assets = DirectoryAssetSource(
    mapOf(
      "catalog/" to File(sharedRoot, "mock_catalog"),
      "tuning/" to File(sharedRoot, "tuning"),
      "schema/" to File(sharedRoot, "schema"),
    ),
  )

  val bundledRepository = BundleCatalogRepository(assets)

  fun catalogEntry(id: String): CatalogDeck = bundledRepository.loadCatalog().decks.first { it.deckId == id }

  fun tempDir(prefix: String = "piyokey-data"): File = Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }

  fun at(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

  fun userDeck(
    id: String = "user_0123456789abcdef0123456789abcdef",
    version: Int,
    meaning: String,
    updatedAt: Instant,
  ) = Deck(
    deckId = id,
    version = version,
    name = "私のデッキ",
    author = DeckAuthor("user_local", "Learner"),
    official = false,
    type = DeckType.WORD,
    level = 1,
    tags = listOf("個人"),
    createdAt = at(100),
    updatedAt = updatedAt,
    items = listOf(DeckItem(id = "item_0123456789abcdef0123456789abcdef", ko = "한글", readingJa = "ハングル", meaningJa = meaning, audio = null)),
  )

  fun encode(deck: Deck): ByteArray = DeckJson.encodeDeck(deck).toByteArray()

  fun hash(data: ByteArray) = sha256Hex(data)
}
