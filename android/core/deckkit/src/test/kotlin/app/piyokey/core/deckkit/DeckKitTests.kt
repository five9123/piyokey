package app.piyokey.core.deckkit

import app.piyokey.core.deckkit.JsonEdit.replacing
import app.piyokey.core.deckkit.JsonEdit.with
import app.piyokey.core.deckkit.JsonEdit.without
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Port of `ios/HangulEngine/Tests/DeckKitTests/DeckKitTests.swift`. */
class DeckKitTests {
  @Test
  fun expandedLocalesResolveRegionalCluesAndRejectIncompleteContent() {
    val catalog = Shared.loadCatalog()
    val entry = catalog.decks.first()
    val data = Shared.bytes("mock_catalog/${entry.fileUrl}")
    val deck = DeckJson.decodeDeck(data)
    val item = deck.items.first()
    for ((language, region) in listOf("es" to "es-MX", "de" to "de-AT", "fr" to "fr-CA")) {
      assertEquals(deck.localizations?.get(language)?.name, deck.localizedName(region))
      assertEquals(item.localizations?.get(language)?.meaning, item.localizedMeaning(region))
      assertEquals(item.localizations?.get("en")?.reading, item.localizedReading(region))
      assertEquals(
        item.localizedMeaning(region), entry.previewItems.first().localizedMeaning(region),
      )

      val obj = JsonEdit.parseObject(data)
      val items = obj["items"] as JsonArray
      val first = items[0] as JsonObject
      val localizations = (first["localizations"] as JsonObject).without(language)
      val broken = obj.with("items", items.replacing(0, first.with("localizations", localizations)))
      val brokenDeck = DeckJson.decodeDeck(JsonEdit.bytes(broken))
      assertTrue(
        DeckValidator.validate(brokenDeck).any {
          it.code == "missing_localization" && it.path == "items[0].localizations.$language"
        },
      )
    }
  }

  @Test
  fun mockCatalogPassesJsonSchemasAndValidators() {
    val catalogData = Shared.catalogData()
    val catalogSchema = Shared.schema("catalog.schema.json")
    assertEquals(emptyList(), JsonSchemaValidator.validate(catalogData, catalogSchema))
    val catalog = DeckJson.decodeCatalog(catalogData)
    assertEquals(emptyList(), CatalogValidator.validate(catalog))
    assertEquals(26, catalog.decks.size)
    assertEquals(26, catalog.decks.count { it.official })
    assertEquals(0, catalog.decks.count { !it.official })
    assertTrue(catalog.decks.all { it.previewItems.size in 1..10 })

    val decks = mutableListOf<Deck>()
    for (entry in catalog.decks) {
      val data = Shared.bytes("mock_catalog/${entry.fileUrl}")
      assertEquals(entry.sizeBytes, data.size, entry.deckId)
      assertEquals(emptyList(), JsonSchemaValidator.validate(data, Shared.deckSchema), entry.deckId)
      val deck = DeckJson.decodeDeck(data)
      assertEquals(emptyList(), DeckValidator.validate(deck), entry.deckId)
      for (item in deck.items) {
        assertTrue(DeckKitHangul.isDecomposable(item.ko), "${entry.deckId}: ${item.id}")
      }
      decks.add(deck)
    }
    assertEquals(26, decks.size)
    assertEquals(emptyList(), CatalogBundleValidator.validate(catalog, decks))
    val fileCount = File(Shared.root, "mock_catalog/decks").listFiles()!!
      .count { it.isFile && it.extension == "json" }
    assertEquals(26, fileCount)
  }

  @Test
  fun gamePresetDecksProvideTopikLevelsWithAtLeastTwentyFiveWords() {
    val directories = listOf("flow", "acid_rain", "word_match", "choseong", "dictation")
    val expectedLevels = directories.flatMap { directory ->
      listOf(
        "${directory}_topik_beginner" to 1,
        "${directory}_topik_intermediate" to 2,
        "${directory}_topik_advanced" to 3,
      )
    }.toMap()

    val loadedIds = mutableSetOf<String>()
    for (directory in directories) {
      val files = File(Shared.root, "mock_catalog/decks/$directory").listFiles()!!
        .filter { it.extension == "json" }
      assertEquals(3, files.size, directory)
      for (file in files) {
        val data = file.readBytes()
        assertEquals(emptyList(), JsonSchemaValidator.validate(data, Shared.deckSchema), file.name)
        val deck = DeckJson.decodeDeck(data)
        assertEquals(emptyList(), DeckValidator.validate(deck), deck.deckId)
        assertEquals(expectedLevels.getValue(deck.deckId), deck.level, deck.deckId)
        assertEquals(DeckType.WORD, deck.type, deck.deckId)
        assertTrue(deck.items.size >= 25, deck.deckId)
        assertEquals(deck.items.size, deck.items.map { it.ko }.toSet().size, deck.deckId)
        for (item in deck.items) {
          assertEquals(
            canonicalPronunciationAudioPath(item.ko), item.audio, "${deck.deckId}: ${item.id}",
          )
          assertTrue(DeckKitHangul.isDecomposable(item.ko), deck.deckId)
        }
        loadedIds.add(deck.deckId)
      }
    }
    assertEquals(expectedLevels.keys, loadedIds)
  }

  @Test
  fun tagCountsAndTrendingFormulaAreDerivedFromCatalogFields() {
    val catalog = Shared.loadCatalog()
    for (tag in catalog.tags) {
      assertEquals(catalog.decks.count { tag.tag in it.tags }, tag.deckCount, tag.tag)
    }
    val deck = catalog.decks.first()
    val expected =
      deck.downloads7d.toDouble() / maxOf(deck.downloadsTotal - deck.downloads7d, 1).toDouble()
    assertEquals(expected, deck.trendingRatio, 0.000_001)
  }

  @Test
  fun catalogUpdateFixturePassesSchemasAndBundleValidation() {
    val catalogData = Shared.bytes("mock_catalog/updates/catalog.json")
    assertEquals(
      emptyList(),
      JsonSchemaValidator.validate(catalogData, Shared.schema("catalog.schema.json")),
    )
    val catalog = DeckJson.decodeCatalog(catalogData)
    assertEquals(12, catalog.catalogVersion)
    assertEquals(emptyList(), CatalogValidator.validate(catalog))

    val decks = catalog.decks.map { entry ->
      val data = Shared.bytes("mock_catalog/updates/${entry.fileUrl}")
      assertEquals(entry.sizeBytes, data.size, entry.deckId)
      assertEquals(emptyList(), JsonSchemaValidator.validate(data, Shared.deckSchema), entry.deckId)
      DeckJson.decodeDeck(data).also {
        assertEquals(emptyList(), DeckValidator.validate(it), entry.deckId)
      }
    }
    assertEquals(emptyList(), CatalogBundleValidator.validate(catalog, decks))
    val updated = decks.first { it.deckId == "official_daily_words" }
    assertEquals(6, updated.version)
    assertEquals("약속", updated.items.last().ko)
  }

  @Test
  fun kpopAndDramaDecksMixWordsAndPhrasesAcrossTwoToEightSyllables() {
    val catalog = Shared.loadCatalog()
    val entries = catalog.decks.filter {
      it.deckId.startsWith("official_kpop_") || it.deckId.startsWith("official_drama_")
    }
    assertEquals(3, entries.count { "K-POP" in it.tags })
    assertEquals(3, entries.count { "Kドラマ" in it.tags })

    for (entry in entries) {
      val deck = Shared.loadDeck(entry)
      val syllableCounts = deck.items.map { item -> item.ko.count { it.code in 0xAC00..0xD7A3 } }
      assertTrue(deck.items.size >= 10, entry.deckId)
      assertTrue(syllableCounts.all { it in 2..8 }, entry.deckId)
      assertTrue(syllableCounts.toSet().size >= 4, entry.deckId)
      assertTrue(deck.items.any { it.ko.contains(" ") }, entry.deckId)
      assertTrue(deck.items.any { !it.ko.contains(" ") }, entry.deckId)
    }
  }

  @Test
  fun funPhraseDecksCoverNineCuratedThemes() {
    val catalog = Shared.loadCatalog()
    val expectedIds = setOf(
      "official_fun_travel_moments",
      "official_fun_food_cafe",
      "official_fun_couple_conflict",
      "official_fun_couple_makeup",
      "official_fun_first_date",
      "official_fun_flirty_chat",
      "official_fun_friend_reactions",
      "official_fun_weekend_party",
      "official_fun_idol_live_comments",
    )
    val entries = catalog.decks.filter { it.deckId in expectedIds }
    assertEquals(expectedIds, entries.map { it.deckId }.toSet())
    assertTrue(entries.all { it.type == DeckType.SENTENCE })
    assertTrue(entries.all { it.itemCount == 12 })
    for (entry in entries) {
      val deck = Shared.loadDeck(entry)
      assertEquals(12, deck.items.size, entry.deckId)
      assertTrue(deck.items.all { DeckKitText.graphemeCount(it.ko) <= 10 }, entry.deckId)
      assertEquals(12, deck.items.map { it.ko }.toSet().size, entry.deckId)
    }
  }

  @Test
  fun deckJsonRoundTripsWithSnakeCaseKeys() {
    val catalog = Shared.loadCatalog()
    val deck = Shared.loadDeck(catalog.decks.first())
    val encoded = DeckJson.encodeDeck(deck)
    val obj = JsonEdit.parseObject(encoded.toByteArray())
    assertNotNull(obj["deck_id"])
    assertNotNull(obj["created_at"])
    assertNull(obj["deckId"])
    assertEquals(deck, DeckJson.decodeDeck(encoded))
  }

  @Test
  fun bundledContentProvidesGlobalMetadataAndEnglishItemCopy() {
    val catalog = Shared.loadCatalog()
    for (entry in catalog.decks) {
      assertTrue(entry.hasLocalization("en"), entry.deckId)
      assertTrue(entry.hasLocalization("ko"), entry.deckId)
      assertFalse(entry.localizedName("en").isNullOrEmpty(), entry.deckId)
      assertFalse(entry.localizedName("ko").isNullOrEmpty(), entry.deckId)
      assertTrue(
        entry.previewItems.all {
          !it.localizedMeaning("en").isNullOrEmpty() && !it.localizedMeaning("ko").isNullOrEmpty()
        },
        entry.deckId,
      )
      val deck = Shared.loadDeck(entry)
      assertTrue(deck.hasLocalization("en"), deck.deckId)
      assertTrue(deck.hasLocalization("ko"), deck.deckId)
      assertTrue(
        deck.items.all {
          !it.localizedMeaning("en").isNullOrEmpty() && !it.localizedReading("en").isNullOrEmpty()
        },
        deck.deckId,
      )
    }
  }

  @Test
  fun unsupportedLocalesFallBackToEnglishMetadataAndClues() {
    val catalog = Shared.loadCatalog()
    val entry = catalog.decks.first()
    val deck = Shared.loadDeck(entry)
    val preview = entry.previewItems.first()
    val item = deck.items.first()
    val tag = catalog.tags.first()

    assertTrue(entry.hasLocalization("zh-Hant"))
    assertEquals(entry.localizedName("en"), entry.localizedName("zh-Hant"))
    assertEquals(entry.localizedAuthorNickname("en"), entry.localizedAuthorNickname("zh-Hant"))
    assertEquals(entry.localizedTags("en"), entry.localizedTags("zh-Hant"))
    assertEquals(preview.localizedMeaning("en"), preview.localizedMeaning("zh-Hant"))

    assertTrue(deck.hasLocalization("zh-Hant"))
    assertEquals(deck.localizedName("en"), deck.localizedName("zh-Hant"))
    assertEquals(deck.localizedAuthorNickname("en"), deck.localizedAuthorNickname("zh-Hant"))
    assertEquals(deck.localizedTags("en"), deck.localizedTags("zh-Hant"))
    assertEquals(item.localizedMeaning("en"), item.localizedMeaning("zh-Hant"))
    assertEquals(item.localizedReading("en"), item.localizedReading("zh-Hant"))
    assertEquals(tag.localizedTag("en"), tag.localizedTag("zh-Hant"))
  }

  @Test
  fun japaneseRequestsPreferLegacyBaseBeforeUniversalEnglishFallback() {
    val itemLocalizations = mapOf(
      "en" to DeckItemLocalization(meaning = "to go", reading = "gada"),
      "es" to DeckItemLocalization(meaning = "ir", reading = "gada"),
      "de" to DeckItemLocalization(meaning = "gehen", reading = "gada"),
      "fr" to DeckItemLocalization(meaning = "aller", reading = "gada"),
    )
    val metadataLocalizations = mapOf(
      "en" to DeckMetadataLocalization("Daily Korean", "typee Official", listOf("Daily")),
      "es" to DeckMetadataLocalization("Coreano diario", "typee Oficial", listOf("Diario")),
      "de" to DeckMetadataLocalization("Alltagskoreanisch", "typee Offiziell", listOf("Alltag")),
      "fr" to DeckMetadataLocalization("Coréen quotidien", "typee Officiel", listOf("Quotidien")),
    )
    val item = DeckItem(
      id = "go", ko = "가다", readingJa = "カダ", meaningJa = "行く", audio = null,
      localizations = itemLocalizations,
    )
    val deck = Deck(
      deckId = "official_daily", version = 1, name = "毎日韓国語",
      author = DeckAuthor("official_hanco", "ピヨキー 公式"), official = true,
      type = DeckType.WORD, level = 1, tags = listOf("日常"), createdAt = epoch100,
      updatedAt = epoch100, items = listOf(item), localizations = metadataLocalizations,
    )
    val preview = CatalogPreviewItem(
      ko = item.ko, meaningJa = item.meaningJa,
      localizations = itemLocalizations.mapValues { CatalogPreviewItemLocalization(it.value.meaning) },
    )
    val catalogDeck = CatalogDeck(
      deckId = deck.deckId, version = deck.version, name = deck.name,
      authorNickname = deck.author.nickname, official = true, featured = true, type = deck.type,
      level = deck.level, tags = deck.tags, itemCount = 1, sizeBytes = 1, downloadsTotal = 0,
      downloads7d = 0, createdAt = epoch100, previewItems = listOf(preview),
      fileUrl = "decks/official_daily.json", localizations = metadataLocalizations,
    )

    for (languageCode in listOf("ja", "ja-JP")) {
      assertEquals("行く", item.localizedMeaning(languageCode))
      assertEquals("カダ", item.localizedReading(languageCode))
      assertEquals("毎日韓国語", deck.localizedName(languageCode))
      assertEquals("ピヨキー 公式", deck.localizedAuthorNickname(languageCode))
      assertEquals(listOf("日常"), deck.localizedTags(languageCode))
      assertEquals("毎日韓国語", catalogDeck.localizedName(languageCode))
      assertEquals("ピヨキー 公式", catalogDeck.localizedAuthorNickname(languageCode))
      assertEquals(listOf("日常"), catalogDeck.localizedTags(languageCode))
      assertEquals("行く", preview.localizedMeaning(languageCode))
    }
    assertEquals("to go", item.localizedMeaning("en"))
    assertEquals("gada", item.localizedReading("en"))
  }

  @Test
  fun japaneseExactAndBaseLocalizationsPrecedeLegacyBase() {
    val item = DeckItem(
      id = "go", ko = "가다", readingJa = "カダ", meaningJa = "行く", audio = null,
      localizations = mapOf(
        "ja" to DeckItemLocalization(meaning = "進む", reading = "カダ・共通"),
        "ja-JP" to DeckItemLocalization(meaning = "向かう", reading = "カダ・日本"),
        "en" to DeckItemLocalization(meaning = "to go", reading = "gada"),
      ),
    )
    assertEquals("向かう", item.localizedMeaning("ja-JP"))
    assertEquals("カダ・日本", item.localizedReading("ja-JP"))
    assertEquals("進む", item.localizedMeaning("ja-Hira"))
    assertEquals("カダ・共通", item.localizedReading("ja-Hira"))
  }

  @Test
  fun legacyJapaneseBaseFieldsAreUsedOnlyForJapaneseRequests() {
    val item = DeckJson.decodeDeckItem(
      """{"id":"legacy","ko":"학교","reading_ja":"ハッキョ","meaning_ja":"学校","audio":null}""",
    )
    assertEquals("学校", item.localizedMeaning("ja"))
    assertEquals("ハッキョ", item.localizedReading("ja"))
    assertEquals("学校", item.localizedMeaning("ja-JP"))
    assertEquals("ハッキョ", item.localizedReading("ja-JP"))
    val nonJapanese = listOf("en", "es-MX", "de-AT", "fr-CA", "zh-Hant", "ko")
    for (languageCode in nonJapanese) {
      assertNull(item.localizedMeaning(languageCode), languageCode)
      assertNull(item.localizedReading(languageCode), languageCode)
    }

    val deck = Deck(
      deckId = "legacy", version = 1, name = "学校", author = DeckAuthor("legacy", "日本語作者"),
      official = false, type = DeckType.WORD, level = 1, tags = listOf("日本語"),
      createdAt = epoch100, updatedAt = epoch100, items = listOf(item),
    )
    assertEquals("学校", deck.localizedName("ja-JP"))
    assertEquals("日本語作者", deck.localizedAuthorNickname("ja"))
    assertEquals(listOf("日本語"), deck.localizedTags("ja"))
    for (languageCode in nonJapanese) {
      assertNull(deck.localizedName(languageCode), languageCode)
      assertNull(deck.localizedAuthorNickname(languageCode), languageCode)
      assertNull(deck.localizedTags(languageCode), languageCode)
    }
  }

  @Test
  fun arbitraryLocaleFallbackUsesExactBaseDefaultEnglishWithoutJapaneseBase() {
    val deck = DeckJson.decodeDeck(Shared.fixture("valid/multilingual-deck.json"))
    val item = deck.items.first()
    assertEquals("韓語詞卡", deck.localizedName("zh-Hant"))
    assertEquals("كلمات كورية", deck.localizedName("de-DE"))
    assertEquals("مرحبًا", item.localizedMeaning("de-DE", defaultLocale = deck.defaultLocale))

    val frItem = item.copy(
      audio = null,
      localizations = mapOf(
        "fr" to DeckItemLocalization(meaning = "bonjour", reading = "fr-reading"),
        "en" to DeckItemLocalization(meaning = "hello", reading = "en-reading"),
      ),
    )
    assertEquals("bonjour", frItem.localizedMeaning("fr-CA", defaultLocale = "en"))
    assertEquals("fr-reading", frItem.localizedReading("fr-CA", defaultLocale = "en"))
  }

  @Test
  fun englishDeckMetadataRequiresEveryItemTranslation() {
    val deck = Deck(
      deckId = "official_missing_english", version = 1, name = "英語不足",
      author = DeckAuthor("official_hanco", "ピヨキー 公式"), official = true,
      type = DeckType.WORD, level = 1, tags = listOf("公式"), createdAt = epoch100,
      updatedAt = epoch100,
      items = listOf(DeckItem("i_001", "학교", "ハッキョ", "学校", null)),
      localizations = mapOf(
        "en" to DeckMetadataLocalization("School", "typee Official", listOf("Official")),
      ),
    )
    assertTrue(
      DeckValidator.validate(deck).any {
        it.code == "missing_localization" && it.path == "items[0].localizations.en"
      },
    )
  }

  @Test
  fun publishedDeckLocaleRequiresLocalizedCatalogTag() {
    val obj = JsonEdit.parseObject(Shared.catalogData())
    val tags = obj["tags"] as JsonArray
    val firstTag = tags[0] as JsonObject
    val localizations = (firstTag["localizations"] as JsonObject).without("en")
    val edited = obj.with("tags", tags.replacing(0, firstTag.with("localizations", localizations)))
    val catalog = DeckJson.decodeCatalog(JsonEdit.bytes(edited))
    assertTrue(
      CatalogValidator.validate(catalog).any {
        it.code == "missing_localization" && it.path == "tags[0].localizations.en"
      },
    )
  }

  @Test
  fun jsonSchemaValidatorRejectsUnknownAndMissingFields() {
    val schema = Shared.schema("catalog.schema.json")
    val obj = JsonEdit.parseObject(Shared.catalogData())
      .with("unexpected", JsonPrimitive(true))
      .without("generated_at")
    val issues = JsonSchemaValidator.validate(JsonEdit.bytes(obj), schema)
    assertTrue(issues.any { it.code == "schema.additionalProperties" && it.path == "$.unexpected" })
    assertTrue(issues.any { it.code == "schema.required" && it.path == "$.generated_at" })
  }

  @Test
  fun jsonSchemaValidatorEnforcesObjectAndUnicodeScalarLengths() {
    val obj = JsonEdit.parseObject(Shared.fixture("valid/basic-deck.json"))
      .with("localizations", JsonObject(emptyMap()))
    val deckIssues = JsonSchemaValidator.validate(JsonEdit.bytes(obj), Shared.deckSchema)
    assertTrue(deckIssues.any { it.code == "schema.minProperties" && it.path == "$.localizations" })

    val scalarSchema = """{"type":"string","maxLength":1}""".toByteArray()
    val twoScalarGrapheme = "\"e\\u0301\"".toByteArray()
    val scalarIssues = JsonSchemaValidator.validate(twoScalarGrapheme, scalarSchema)
    assertTrue(scalarIssues.any { it.code == "schema.maxLength" })
  }

  @Test
  fun semanticValidatorRejectsUntypeableKoreanAndDuplicateItems() {
    val item = DeckItem(id = "i_001", ko = "ABC", readingJa = "", meaningJa = "", audio = "")
    val deck = Deck(
      deckId = "Bad ID", version = 0, name = " ", author = DeckAuthor("", ""), official = false,
      type = DeckType.WORD, level = 4, tags = listOf("重複", "重複"), createdAt = epoch100,
      updatedAt = epoch100.minusSeconds(1), items = listOf(item, item),
    )
    val codes = DeckValidator.validate(deck).map { it.code }.toSet()
    assertTrue(
      codes.containsAll(
        listOf("identifier", "range", "required", "duplicate", "date_order", "undecomposable_ko"),
      ),
      codes.toString(),
    )
  }

  @Test
  fun semanticValidatorRejectsKoreanTargetLongerThanTenCharacters() {
    val item = DeckItem(
      id = "i_001", ko = "가나다라마바사아자차카", readingJa = "カナダラマバサアジャチャカ",
      meaningJa = "長すぎるお題", audio = null,
    )
    val deck = Deck(
      deckId = "official_length_test", version = 1, name = "長さテスト",
      author = DeckAuthor("official_hanco", "ピヨキー 公式"), official = true,
      type = DeckType.WORD, level = 1, tags = listOf("テスト"), createdAt = epoch100,
      updatedAt = epoch100, items = listOf(item),
    )
    assertTrue(
      DeckValidator.validate(deck).any { it.code == "max_target_length" && it.path == "items[0].ko" },
    )
  }

  @Test
  fun bundleValidatorDetectsMissingMismatchedAndUnindexedDecks() {
    val catalog = Shared.loadCatalog()
    val all = catalog.decks.map { Shared.loadDeck(it) }
    assertTrue(
      CatalogBundleValidator.validate(catalog, all.drop(1)).any { it.code == "missing_deck" },
    )

    val first = all.first()
    val renamed = first.copy(name = "別名", localizations = null)
    assertTrue(
      CatalogBundleValidator.validate(catalog, all.drop(1) + renamed)
        .any { it.code == "metadata_mismatch" },
    )

    val extra = first.copy(deckId = "extra_deck", localizations = null)
    assertTrue(
      CatalogBundleValidator.validate(catalog, all + extra).any { it.code == "unindexed_deck" },
    )
  }

  private fun canonicalPronunciationAudioPath(target: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
      .digest(target.toByteArray(Charsets.UTF_8))
    val prefix = digest.take(10).joinToString("") { "%02x".format(it) }
    return "audio/ko_$prefix.mp3"
  }
}
