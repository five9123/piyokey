package app.piyokey.core.deckkit

import app.piyokey.core.hangul.JamoDecomposer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class DeckKitContractTest {
  private val repositoryRoot: Path = Path.of(
    checkNotNull(System.getProperty("piyokey.repositoryRoot")) {
      "piyokey.repositoryRoot test property is required"
    },
  ).normalize()
  private val mockCatalogRoot = repositoryRoot.resolve("shared/mock_catalog")
  private val fixtureRoot = repositoryRoot.resolve("shared/piyodeck/fixtures")
  private val deckSchemaSource = repositoryRoot.resolve("shared/schema/deck.schema.json").readText()
  private val catalogSchemaSource = repositoryRoot.resolve("shared/schema/catalog.schema.json").readText()

  @Test
  fun officialCatalogAndTwentySixDecksPassSharedAndSemanticContracts() {
    val catalog = loadValidatedCatalog(mockCatalogRoot)
    assertEquals(11, catalog.catalogVersion)
    assertEquals(26, catalog.decks.size)
    assertTrue(catalog.decks.all(CatalogDeck::official))
    assertTrue(catalog.decks.all { it.previewItems.size in 1..10 })

    val decks = catalog.decks.map { entry ->
      val deckPath = mockCatalogRoot.resolve(entry.fileUrl)
      val source = deckPath.readText()
      assertEquals(Files.size(deckPath), entry.sizeBytes.toLong(), entry.deckId)
      val deck = DeckKitJson.decodeValidatedDeck(source, deckSchemaSource)
      deck.items.forEach { item -> JamoDecomposer.keySequenceFor(item.ko) }
      deck
    }

    assertEquals(26, decks.size)
    assertEquals(emptyList(), CatalogBundleValidator.validate(catalog, decks))
    assertEquals(
      26,
      directJsonFiles(mockCatalogRoot.resolve("decks")).size,
      "The official catalog owns exactly the 26 top-level deck files",
    )
  }

  @Test
  fun gamePresetsProvideFiveModesAndThreeExactHundredWordLevels() {
    val modes = listOf("flow", "acid_rain", "word_match", "choseong", "dictation")
    val expectedLevels = modes.flatMap { mode ->
      listOf(
        "${mode}_topik_beginner" to 1,
        "${mode}_topik_intermediate" to 2,
        "${mode}_topik_advanced" to 3,
      )
    }.toMap()

    val loadedIds = mutableSetOf<String>()
    modes.forEach { mode ->
      val paths = directJsonFiles(mockCatalogRoot.resolve("decks/$mode"))
      assertEquals(3, paths.size, mode)
      paths.forEach { path ->
        val source = path.readText()
        assertEquals(emptyList(), JsonSchemaValidator.validate(source, deckSchemaSource), path.name)
        val deck = DeckKitJson.decodeDeck(source)
        assertEquals(emptyList(), DeckValidator.validate(deck), deck.deckId)
        assertEquals(expectedLevels.getValue(deck.deckId), deck.level, deck.deckId)
        assertEquals(3, deck.version, deck.deckId)
        assertTrue(deck.official, deck.deckId)
        assertEquals(DeckType.WORD, deck.type, deck.deckId)
        assertEquals(100, deck.items.size, deck.deckId)
        assertEquals(100, deck.items.map(DeckItem::ko).toSet().size, deck.deckId)
        deck.items.forEach { item ->
          assertEquals(canonicalPronunciationPath(item.ko), item.audio, "${deck.deckId}: ${item.id}")
          JamoDecomposer.keySequenceFor(item.ko)
        }
        loadedIds += deck.deckId
      }
    }

    assertEquals(expectedLevels.keys, loadedIds)
  }

  @Test
  fun updateCatalogIsACompleteSnapshotWithOnlyDailyWordsContentUpdate() {
    val updateRoot = mockCatalogRoot.resolve("updates")
    val baseCatalog = loadValidatedCatalog(mockCatalogRoot)
    val updateCatalog = loadValidatedCatalog(updateRoot)
    assertEquals(12, updateCatalog.catalogVersion)
    assertEquals(26, updateCatalog.decks.size)
    assertEquals(baseCatalog.decks.map(DeckKitContractTest::catalogDeckId).toSet(), updateCatalog.decks.map(DeckKitContractTest::catalogDeckId).toSet())

    val decks = updateCatalog.decks.map { entry ->
      val path = updateRoot.resolve(entry.fileUrl)
      assertEquals(Files.size(path), entry.sizeBytes.toLong(), entry.deckId)
      DeckKitJson.decodeValidatedDeck(path.readText(), deckSchemaSource)
    }
    assertEquals(emptyList(), CatalogBundleValidator.validate(updateCatalog, decks))

    val baseDaily = baseCatalog.decks.single { it.deckId == "official_daily_words" }
    val updateDaily = updateCatalog.decks.single { it.deckId == "official_daily_words" }
    assertEquals(5, baseDaily.version)
    assertEquals(12, baseDaily.itemCount)
    assertEquals(6, updateDaily.version)
    assertEquals(13, updateDaily.itemCount)
    assertEquals("약속", decks.single { it.deckId == "official_daily_words" }.items.last().ko)
    updateCatalog.decks.filterNot { it.deckId == "official_daily_words" }.forEach { updated ->
      val original = baseCatalog.decks.single { it.deckId == updated.deckId }
      assertEquals(original.version, updated.version, updated.deckId)
      assertEquals(original.itemCount, updated.itemCount, updated.deckId)
    }
  }

  @Test
  fun strictCodecRejectsUnknownFieldsAndSchemaEnforcesMissingAndEmptyObjects() {
    val source = mockCatalogRoot.resolve("catalog.json").readText()
    val sourceObject = Json.parseToJsonElement(source).jsonObject
    val malformed = JsonObject(
      sourceObject
        .minus("generated_at")
        .plus("unexpected" to JsonPrimitive(true)),
    ).toString()
    val issues = JsonSchemaValidator.validate(malformed, catalogSchemaSource)
    assertTrue(issues.any { it.code == "schema.additionalProperties" && it.path == "$.unexpected" })
    assertTrue(issues.any { it.code == "schema.required" && it.path == "$.generated_at" })
    assertFailsWith<DeckKitJsonException> { DeckKitJson.decodeCatalog(malformed) }

    val nonCanonicalTimestamp = JsonObject(
      sourceObject.plus("generated_at" to JsonPrimitive("2026-08-21T00:00:00.000Z")),
    ).toString()
    assertTrue(
      JsonSchemaValidator.validate(nonCanonicalTimestamp, catalogSchemaSource).any {
        it.code == "schema.pattern" && it.path == "$.generated_at"
      },
    )
    assertFailsWith<ContentValidationException> {
      DeckKitJson.decodeValidatedCatalog(nonCanonicalTimestamp, catalogSchemaSource)
    }

    val emptyLocalizationDeck = """
      {
        "deck_id":"user_test","version":1,"name":"test",
        "author":{"id":"user_author","nickname":"tester"},
        "official":false,"type":"word","level":1,"tags":["test"],
        "localizations":{},
        "created_at":"2026-08-21T00:00:00Z","updated_at":"2026-08-21T00:00:00Z",
        "items":[{"id":"item_001","ko":"가","reading_ja":"カ","meaning_ja":"仮","audio":null}]
      }
    """.trimIndent()
    assertTrue(
      JsonSchemaValidator.validate(emptyLocalizationDeck, deckSchemaSource).any {
        it.code == "schema.minProperties" && it.path == "$.localizations"
      },
    )
  }

  @Test
  fun expandedContentLocalesResolveRegionsAndRequireCompleteMeanings() {
    val catalog = loadValidatedCatalog(mockCatalogRoot)
    val entry = catalog.decks.first()
    val deck = DeckKitJson.decodeValidatedDeck(
      mockCatalogRoot.resolve(entry.fileUrl).readText(), deckSchemaSource,
    )
    for ((code, region) in mapOf("es" to "es-MX", "de" to "de-AT", "fr" to "fr-CA")) {
      val item = deck.items.first()
      assertEquals(deck.localizations?.get(code)?.name, deck.localizedName(region))
      assertEquals(item.localizations?.get(code)?.meaning, item.localizedMeaning(region))
      assertEquals(item.localizations?.get("en")?.reading, item.localizedReading(region))
      assertEquals(entry.previewItems.first().localizedMeaning(region), item.localizedMeaning(region))
      val broken = deck.copy(items = deck.items.mapIndexed { index, value ->
        if (index == 0) value.copy(localizations = value.localizations.orEmpty() - code) else value
      })
      assertTrue(DeckValidator.validate(broken).any {
        it.code == "missing_localization" && it.path == "items[0].localizations.$code"
      })
    }
  }

  @Test
  fun localizedAccessUsesExactBaseDefaultEnglishThenLegacyJapaneseBase() {
    val catalog = loadValidatedCatalog(mockCatalogRoot)
    val catalogDeck = catalog.decks.first()
    val deck = DeckKitJson.decodeValidatedDeck(
      mockCatalogRoot.resolve(catalogDeck.fileUrl).readText(),
      deckSchemaSource,
    )
    val item = deck.items.first()
    val preview = catalogDeck.previewItems.first()
    val tag = catalog.tags.first()

    assertEquals(deck.localizations?.get("en")?.name, deck.localizedName("ja-JP"))
    assertEquals(deck.localizations?.get("ko")?.name, deck.localizedName("ko_KR"))
    assertEquals(deck.localizations?.get("en")?.name, deck.localizedName("zh-Hant"))
    assertEquals(catalogDeck.localizations?.get("en")?.name, catalogDeck.localizedName("it"))
    assertEquals(item.localizations?.get("en")?.meaning, item.localizedMeaning("ja"))
    assertEquals(item.localizations?.get("en")?.meaning, item.localizedMeaning("ko"))
    assertEquals(item.localizations?.get("en")?.reading, item.localizedReading("fr"))
    assertEquals(preview.localizations?.get("en")?.meaning, preview.localizedMeaning("zh-Hant"))
    assertEquals(tag.localizations?.get("en"), tag.localizedTag("pt"))

    val explicitKorean = item.copy(
      localizations = item.localizations.orEmpty() + (
        "ko" to DeckItemLocalization(meaning = "한국어 뜻", reading = "한국어 읽기")
      ),
    )
    assertEquals("한국어 뜻", explicitKorean.localizedMeaning("ko-KR"))
    assertEquals("한국어 읽기", explicitKorean.localizedReading("ko-KR"))

    val englishOnlyDeck = deck.copy(
      localizations = mapOf("en" to assertNotNull(deck.localizations?.get("en"))),
    )
    val englishOnlyCatalogDeck = catalogDeck.copy(
      localizations = mapOf("en" to assertNotNull(catalogDeck.localizations?.get("en"))),
    )
    assertTrue(englishOnlyDeck.hasLocalization("ko"))
    assertEquals(englishOnlyDeck.localizedName("en"), englishOnlyDeck.localizedName("ko"))
    assertTrue(englishOnlyCatalogDeck.hasLocalization("ko"))
    assertEquals(
      englishOnlyCatalogDeck.localizedName("en"),
      englishOnlyCatalogDeck.localizedName("ko"),
    )
  }

  @Test
  fun arbitraryCanonicalLocalesAndFrCaBaseFallbackArePreserved() {
    val multilingual = DeckKitJson.decodeValidatedDeck(
      fixtureRoot.resolve("valid/multilingual-deck.json").readText(),
      deckSchemaSource,
    )
    assertEquals("ar", multilingual.defaultLocale)
    assertEquals(setOf("ar", "zh-Hant"), multilingual.localizations.orEmpty().keys)
    assertEquals("韓語詞卡", multilingual.localizedName("zh-Hant"))
    assertEquals("كلمات كورية", multilingual.localizedName("de-DE"))

    val item = multilingual.items.first().copy(
      localizations = multilingual.items.first().localizations.orEmpty() + mapOf(
        "fr" to DeckItemLocalization("bonjour", "fr-reading"),
        "en" to DeckItemLocalization("hello", "en-reading"),
      ),
    )
    assertEquals("bonjour", item.localizedMeaning("fr-CA", "en"))
    assertEquals("fr-reading", item.localizedReading("fr-CA", "en"))
    assertTrue(LocaleTag.isCanonical("sl-rozaj-biske"))
    assertFalse(LocaleTag.isCanonical("fr_CA"))
    assertFalse(LocaleTag.isCanonical("fr-ca"))
  }

  @Test
  fun codecRoundTripsSnakeCaseDeckAndCatalogModels() {
    val catalog = loadValidatedCatalog(mockCatalogRoot)
    val deck = DeckKitJson.decodeValidatedDeck(
      mockCatalogRoot.resolve(catalog.decks.first().fileUrl).readText(),
      deckSchemaSource,
    )

    val deckEncoded = DeckKitJson.encodeDeck(deck)
    assertTrue("\"deck_id\"" in deckEncoded)
    assertTrue("\"created_at\"" in deckEncoded)
    assertFalse("\"deckId\"" in deckEncoded)
    assertEquals(deck, DeckKitJson.decodeDeck(deckEncoded))

    val catalogEncoded = DeckKitJson.encodeCatalog(catalog)
    assertTrue("\"catalog_version\"" in catalogEncoded)
    assertEquals(catalog, DeckKitJson.decodeCatalog(catalogEncoded))
  }

  @Test
  fun semanticAndBundleValidatorsReportStableCodesAndPaths() {
    val date = Instant.ofEpochSecond(100)
    val invalidItem = DeckItem(
      id = "item_001",
      ko = "ABC",
      readingJa = "",
      meaningJa = "",
      audio = "",
    )
    val invalidDeck = Deck(
      deckId = "Bad ID",
      version = 0,
      name = " ",
      author = DeckAuthor("", ""),
      official = false,
      type = DeckType.WORD,
      level = 4,
      tags = listOf("重複", "重複"),
      createdAt = date,
      updatedAt = date.minusSeconds(1),
      items = listOf(invalidItem, invalidItem),
    )
    val codes = DeckValidator.validate(invalidDeck).map(ContentValidationIssue::code).toSet()
    assertTrue(
      codes.containsAll(
        setOf("identifier", "range", "required", "duplicate", "date_order", "undecomposable_ko"),
      ),
    )

    val catalog = loadValidatedCatalog(mockCatalogRoot)
    val decks = catalog.decks.drop(1).map { entry ->
      DeckKitJson.decodeValidatedDeck(mockCatalogRoot.resolve(entry.fileUrl).readText(), deckSchemaSource)
    }
    assertTrue(CatalogBundleValidator.validate(catalog, decks).any { it.code == "missing_deck" })
  }

  @Test
  fun tagCountsAndTrendingRatioAreDerivedFromCatalogFields() {
    val catalog = loadValidatedCatalog(mockCatalogRoot)
    catalog.tags.forEach { tag ->
      assertEquals(tag.deckCount, catalog.decks.count { tag.tag in it.tags }, tag.tag)
    }
    val deck = catalog.decks.first()
    val expected = deck.downloads7d.toDouble() /
      (deck.downloadsTotal - deck.downloads7d).coerceAtLeast(1).toDouble()
    assertEquals(expected, deck.trendingRatio, absoluteTolerance = 0.000_001)
  }

  private fun loadValidatedCatalog(root: Path): Catalog = DeckKitJson.decodeValidatedCatalog(
    root.resolve("catalog.json").readText(),
    catalogSchemaSource,
  )

  private fun directJsonFiles(directory: Path): List<Path> = Files.list(directory).use { stream ->
    stream.filter { path -> path.isRegularFile() && path.name.endsWith(".json") }
      .sorted()
      .toList()
  }

  private fun canonicalPronunciationPath(korean: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(korean.toByteArray(Charsets.UTF_8))
    val prefix = digest.joinToString(separator = "") { byte ->
      (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }.take(20)
    return "audio/ko_$prefix.mp3"
  }

  private companion object {
    fun catalogDeckId(deck: CatalogDeck): String = deck.deckId
  }
}
