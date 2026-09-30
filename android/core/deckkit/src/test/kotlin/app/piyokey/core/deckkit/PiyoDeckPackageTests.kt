package app.piyokey.core.deckkit

import app.piyokey.core.deckkit.JsonEdit.with
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean

/** Port of `ios/HangulEngine/Tests/DeckKitTests/PiyoDeckPackageTests.swift` plus fixture drivers. */
class PiyoDeckPackageTests {
  private val deckSchema get() = Shared.deckSchema

  @Test
  fun sharedFixturesPassSchemasAndPackageRoundTripsDeterministically() {
    val deckData = Shared.fixture("valid/basic-deck.json")
    val manifestData = Shared.fixture("valid/basic-manifest.json")
    val manifestSchema = Shared.schema("piyodeck-manifest-v1.schema.json")

    assertEquals(emptyList(), JsonSchemaValidator.validate(deckData, deckSchema))
    assertEquals(emptyList(), JsonSchemaValidator.validate(manifestData, manifestSchema))

    val fixtureManifest = PiyoDeckManifest.decode(StrictJsonParser.parse(manifestData))
    assertEquals(deckData.size, fixtureManifest.deck.sizeBytes)
    assertEquals(Shared.sha256(deckData), fixtureManifest.deck.sha256)

    val deck = DeckJson.decodeDeck(deckData)
    assertEquals(emptyList(), UserDeckValidator.validate(deck))
    val packageData = PiyoDeckPackageWriter.write(deck, deckSchema)
    assertContentEquals(packageData, PiyoDeckPackageWriter.write(deck, deckSchema))
    val golden = Shared.fixture("valid/basic.typedeck")
    assertContentEquals(golden, packageData)

    val entries = PiyoDeckZip.read(packageData)
    assertEquals(setOf("manifest.json", "deck.json"), entries.keys)
    val generatedManifest = entries.getValue("manifest.json")
    val generatedDeck = entries.getValue("deck.json")
    assertFalse(generatedManifest.contains(0x0A))
    assertFalse(generatedManifest.contains(0x0D))
    assertFalse(generatedDeck.contains(0x0A))
    assertFalse(generatedDeck.contains(0x0D))
    // Shared with tools/tests/test_piyodeck_tool.py and the Swift tests.
    assertEquals(580, generatedDeck.size)
    assertEquals(
      "7c5b6496007b98ef4ead02b9da6a2dbc560a351e1f214d0df49633ac6c42d90b",
      Shared.sha256(generatedDeck),
    )
    assertEquals(311, generatedManifest.size)
    assertEquals(
      "fe7fc1ed3af0728582698bd3445d2af2eb68996c500322b1234f16f79f4ff20a",
      Shared.sha256(generatedManifest),
    )
    assertEquals(1_109, packageData.size)
    assertEquals(
      "025efa7a0584509fd892221a01c4b3cdf828472c3ffeb13a7eec420102061c31",
      Shared.sha256(packageData),
    )
    assertEquals(emptyList(), JsonSchemaValidator.validate(generatedManifest, manifestSchema))

    val imported = PiyoDeckPackageReader.read(packageData, deckSchema)
    assertEquals(deck, imported.deck)
    assertContentEquals(generatedDeck, imported.deckData)
    assertEquals(Shared.sha256(imported.deckData), imported.contentSha256)
    assertFalse(imported.deck.official)
    assertTrue(imported.deck.items.all { it.audio == null })

    val importedGolden = PiyoDeckPackageReader.read(golden, deckSchema)
    assertEquals(deck, importedGolden.deck)
    assertContentEquals(generatedDeck, importedGolden.deckData)

    val importedPretty = PiyoDeckPackageReader.read(
      Shared.fixture("valid/pretty-basic.typedeck"), deckSchema,
    )
    assertEquals(deck, importedPretty.deck)
    assertContentEquals(packageData, PiyoDeckPackageWriter.write(importedPretty.deck, deckSchema))
  }

  @Test
  fun expandedLanguagePackageMatchesCrossPlatformGolden() {
    val deck = DeckJson.decodeDeck(Shared.fixture("valid/localized-deck.json"))
    val golden = Shared.fixture("valid/localized.typedeck")
    assertContentEquals(golden, PiyoDeckPackageWriter.write(deck, deckSchema))
    val imported = PiyoDeckPackageReader.read(golden, deckSchema)
    assertEquals(deck, imported.deck)
    assertEquals(setOf("en", "ko", "es", "de", "fr"), imported.deck.localizations!!.keys)
  }

  @Test
  fun multilingualWriterGoldenIsReproducedByteForByte() {
    val deck = DeckJson.decodeDeck(Shared.fixture("valid/multilingual-deck.json"))
    assertContentEquals(
      Shared.fixture("valid/multilingual.typedeck"), PiyoDeckPackageWriter.write(deck, deckSchema),
    )
  }

  @Test
  fun sharedBinaryCaseManifestDrivesEveryReaderExpectation() {
    val manifest = Json.parseToJsonElement(Shared.fixture("cases.json").decodeToString()) as JsonObject
    assertEquals(2, manifest.getValue("schema_version").jsonPrimitive.int)
    val cases = manifest.getValue("cases") as JsonArray
    assertTrue(cases.size >= 20)
    val sourceDeck = DeckJson.decodeDeck(Shared.fixture("valid/basic-deck.json"))
    val canonical = Shared.fixture("valid/basic.typedeck")
    var validCount = 0
    var invalidCount = 0

    for (element in cases) {
      val fixtureCase = element as JsonObject
      val identifier = fixtureCase.getValue("id").jsonPrimitive.content
      val path = fixtureCase.getValue("path").jsonPrimitive.content
      val expectedSize = fixtureCase.getValue("size_bytes").jsonPrimitive.int
      val expectedSha = fixtureCase.getValue("sha256").jsonPrimitive.content
      val valid = fixtureCase.getValue("valid").jsonPrimitive.boolean
      val expectation = fixtureCase.getValue("expectation").jsonPrimitive.content
      val data = Shared.fixture(path)

      assertEquals(expectedSize, data.size, identifier)
      assertEquals(expectedSha, Shared.sha256(data), identifier)
      if (valid) {
        validCount++
        val imported = PiyoDeckPackageReader.read(data, deckSchema)
        val multilingual = identifier == "multilingual-ar-zh-hant"
        val expectedDeck =
          if (multilingual) DeckJson.decodeDeck(Shared.fixture("valid/multilingual-deck.json"))
          else sourceDeck
        assertEquals(expectedDeck, imported.deck, identifier)
        val expectedPackage =
          if (multilingual) Shared.fixture("valid/multilingual.typedeck") else canonical
        assertContentEquals(
          expectedPackage, PiyoDeckPackageWriter.write(imported.deck, deckSchema), identifier,
        )
      } else {
        invalidCount++
        val error = readError(data)
        assertEquals(expectation, error.family.id, "$identifier: $error")
      }
    }
    assertEquals(cases.size, validCount + invalidCount)
    assertTrue(validCount >= 3 && invalidCount >= 17)
  }

  @Test
  fun writerAlwaysAppliesPinnedDeckSchema() {
    val source = DeckJson.decodeDeck(Shared.fixture("valid/basic-deck.json"))
    val emptyLocalizations = source.copy(localizations = emptyMap())
    val error = assertFailsWith<PiyoDeckImportError.DeckSchemaViolation> {
      PiyoDeckPackageWriter.write(emptyLocalizations, deckSchema)
    }
    assertTrue(
      error.issues.any { it.code == "schema.minProperties" && it.path == "$.localizations" },
    )
  }

  @Test
  fun crc32MatchesStandardVector() {
    assertEquals(0xCBF43926L, PiyoDeckCrc32.checksum("123456789".toByteArray()))
  }

  @Test
  fun userDeckValidatorRejectsReservedOfficialAudioAndMalformedItemIds() {
    val official = DeckJson.decodeDeck(Shared.fixture("invalid/official-deck.json"))
    val officialIssues = UserDeckValidator.validate(official)
    assertTrue(officialIssues.any { it.code == "reserved_identifier" })
    assertTrue(officialIssues.any { it.code == "user_deck_official" })
    assertTrue(officialIssues.any { it.code == "user_deck_identifier" })

    val audio = DeckJson.decodeDeck(Shared.fixture("invalid/audio-deck.json"))
    assertTrue(UserDeckValidator.validate(audio).any { it.code == "user_deck_audio" })

    val valid = DeckJson.decodeDeck(Shared.fixture("valid/basic-deck.json"))
    val malformedItem = valid.items[0].copy(id = "i_001", audio = null, localizations = null)
    val malformed = valid.copy(items = listOf(malformedItem))
    assertTrue(
      UserDeckValidator.validate(malformed).any {
        it.code == "user_item_identifier" && it.path == "items[0].id"
      },
    )
  }

  @Test
  fun readerRejectsEntitlementLikeUnknownFieldsUsingSharedDeckSchema() {
    val packageData = rawPackage(Shared.fixture("invalid/unknown-field-deck.json"))
    val error = readError(packageData)
    assertTrue(
      error is PiyoDeckImportError.DeckSchemaViolation &&
        error.issues.any { it.code == "schema.additionalProperties" && it.path == "$.premium" },
      error.toString(),
    )
  }

  @Test
  fun readerRejectsDuplicateJsonKeysIncludingEscapedEquivalentKeys() {
    val source = Shared.fixture("valid/basic-deck.json").decodeToString()
    val duplicated = source.replace("\"version\": 1", "\"version\": 1, \"\\u0076ersion\": 1")
    assertTrue(duplicated != source)
    val error = readError(rawPackage(duplicated.toByteArray()))
    assertEquals(PiyoDeckImportError.DuplicateJsonKey("deck.json", "$.version"), error)
  }

  @Test
  fun readerDistinguishesFuturePackageAndDeckSchemaVersions() {
    val deckData = Shared.fixture("valid/basic-deck.json")
    assertEquals(
      PiyoDeckImportError.UnsupportedFormatVersion(2),
      readError(rawPackage(deckData, formatVersion = 2)),
    )
    assertEquals(
      PiyoDeckImportError.UnsupportedDeckSchemaVersion(3),
      readError(rawPackage(deckData, deckSchemaVersion = 3)),
    )
    assertTrue(readError(rawPackage(deckData, formatVersion = 2)).isUnsupportedFutureVersion)
  }

  @Test
  fun v2MultilingualFixtureRoundTripsAndRejectsInvalidLocaleContracts() {
    val packageData = Shared.fixture("valid/multilingual.typedeck")
    val imported = PiyoDeckPackageReader.read(packageData, deckSchema)
    assertEquals(2, imported.manifest.deckSchemaVersion)
    assertEquals("ar", imported.deck.defaultLocale)
    assertEquals(setOf("ar", "zh-Hant"), imported.deck.localizations!!.keys)
    assertContentEquals(packageData, PiyoDeckPackageWriter.write(imported.deck, deckSchema))
    for (name in listOf(
      "malformed-locale", "noncanonical-locale", "incomplete-locale", "v2-content-declared-as-v1",
    )) {
      val error = readError(Shared.fixture("invalid/$name.typedeck"))
      assertTrue(
        error is PiyoDeckImportError.DeckSchemaViolation ||
          error is PiyoDeckImportError.InvalidUserDeck,
        "$name: $error",
      )
    }
    assertTrue(LocaleTag.isCanonical("sl-rozaj-biske"))
    assertFalse(LocaleTag.isCanonical("fr_CA"))
    assertFalse(LocaleTag.isCanonical("fr-ca"))
  }

  @Test
  fun readerVerifiesShaAndManifestMetadataAfterZipCrc() {
    val deckData = Shared.fixture("valid/basic-deck.json")
    assertEquals(
      PiyoDeckImportError.Sha256Mismatch,
      readError(rawPackage(deckData, sha256 = "0".repeat(64))),
    )
    assertEquals(
      PiyoDeckImportError.ManifestMismatch("deck.item_count"),
      readError(rawPackage(deckData, itemCount = 1)),
    )
  }

  @Test
  fun readerRejectsCrcMutationBeforeDecodingJson() {
    val deck = DeckJson.decodeDeck(Shared.fixture("valid/basic-deck.json"))
    val original = PiyoDeckPackageWriter.write(deck, deckSchema)
    val storedDeck = PiyoDeckZip.read(original).getValue("deck.json")
    val start = indexOf(original, storedDeck)
    assertTrue(start >= 0)
    val bytes = original.copyOf()
    bytes[start] = (bytes[start].toInt() xor 0x01).toByte()
    assertEquals(PiyoDeckImportError.CrcMismatch("deck.json"), readError(bytes))
  }

  @Test
  fun readerRejectsUnsupportedAndUnsafeZipFeatures() {
    val deck = DeckJson.decodeDeck(Shared.fixture("valid/basic-deck.json"))
    val packageData = PiyoDeckPackageWriter.write(deck, deckSchema)
    val centralOffset = centralDirectoryOffset(packageData)

    val encrypted = packageData.copyOf()
    encrypted[centralOffset + 8] = (encrypted[centralOffset + 8].toInt() or 0x01).toByte()
    assertEquals(PiyoDeckImportError.UnsupportedArchiveFeature("encryption"), readError(encrypted))

    val deflated = packageData.copyOf()
    deflated[centralOffset + 10] = 8
    assertEquals(
      PiyoDeckImportError.UnsupportedArchiveFeature("compression method 8"), readError(deflated),
    )

    val zip64 = packageData.copyOf()
    for (index in (centralOffset + 20) until (centralOffset + 24)) zip64[index] = 0xFF.toByte()
    assertEquals(PiyoDeckImportError.UnsupportedArchiveFeature("ZIP64"), readError(zip64))

    val symlink = packageData.copyOf()
    symlink[centralOffset + 5] = 3
    symlink[centralOffset + 41] = 0xA0.toByte()
    assertEquals(PiyoDeckImportError.UnsupportedArchiveFeature("symbolic link"), readError(symlink))

    val windowsDirectory = packageData.copyOf()
    windowsDirectory[centralOffset + 38] = 0x10
    assertEquals(
      PiyoDeckImportError.UnsupportedArchiveFeature("directory entry"),
      readError(windowsDirectory),
    )

    val unsafe = replaceAll(
      packageData, "manifest.json".toByteArray(), "evil\\path.jsn".toByteArray(),
    )
    assertEquals(PiyoDeckImportError.UnsafeEntryPath("evil\\path.jsn"), readError(unsafe))
  }

  @Test
  fun readerEnforcesPackageAndEntrySizeLimitsBeforeJsonDecoding() {
    val maxPackage = PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES
    assertEquals(
      PiyoDeckImportError.PackageTooLarge(maxPackage + 1, maxPackage),
      readError(ByteArray(maxPackage + 1)),
    )

    val maxDeck = PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES
    val deckData = ByteArray(maxDeck + 1) { 0x20 }
    val packageData = PiyoDeckZip.write(
      listOf(
        PiyoDeckZipEntry("manifest.json", "{}".toByteArray()),
        PiyoDeckZipEntry("deck.json", deckData),
      ),
    )
    assertEquals(
      PiyoDeckImportError.EntryTooLarge("deck.json", maxDeck + 1, maxDeck),
      readError(packageData),
    )
  }

  @Test
  fun readerRejectsUnknownManifestFields() {
    val deckData = Shared.fixture("valid/basic-deck.json")
    val manifest = JsonEdit.parseObject(makeManifest(deckData))
      .with("license_key", JsonPrimitive("must-not-be-imported"))
    val packageData = PiyoDeckZip.write(
      listOf(
        PiyoDeckZipEntry("manifest.json", JsonEdit.bytes(manifest)),
        PiyoDeckZipEntry("deck.json", deckData),
      ),
    )
    val error = readError(packageData)
    assertTrue(
      error is PiyoDeckImportError.InvalidManifest &&
        error.issues.any {
          it.code == "schema.additionalProperties" && it.path == "$.license_key"
        },
      error.toString(),
    )
  }

  @Test
  fun readerRejectsNoncanonicalTimestampBytes() {
    val source = Shared.fixture("valid/basic-deck.json").decodeToString()
    val offsetTimestamp = source.replace("2026-08-14T00:00:00Z", "2026-08-14T09:00:00+09:00")
    val error = readError(rawPackage(offsetTimestamp.toByteArray()))
    assertTrue(
      error is PiyoDeckImportError.DeckSchemaViolation &&
        error.issues.any { it.code == "schema.pattern" && it.path == "$.created_at" },
      error.toString(),
    )
  }

  @Test
  fun strictJsonRejectsUnpairedSurrogateEscapes() {
    val invalidDocuments = listOf(
      """{"name":"\ud800"}""",
      """{"name":"\udc00"}""",
      """{"name":"\ud800\u0041"}""",
    )
    for (document in invalidDocuments) {
      val error = assertFailsWith<PiyoDeckImportError.InvalidJson>(document) {
        PiyoDeckStrictJson.validate(document.toByteArray(), "deck.json")
      }
      assertEquals("deck.json", error.name)
    }
    PiyoDeckStrictJson.validate("""{"name":"\ud835\udfd9"}""".toByteArray(), "deck.json")
  }

  // Additional strict-transport coverage beyond the Swift suite.

  @Test
  fun strictJsonRejectsBomInvalidUtf8NestingAndCanonicallyEquivalentDuplicateKeys() {
    assertFailsWith<PiyoDeckImportError.InvalidJson> {
      PiyoDeckStrictJson.validate(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
        "{}".toByteArray(), "deck.json")
    }
    assertFailsWith<PiyoDeckImportError.InvalidJson> {
      PiyoDeckStrictJson.validate(byteArrayOf('"'.code.toByte(), 0xC3.toByte(), '"'.code.toByte()),
        "deck.json")
    }
    val deep = "[".repeat(65) + "1" + "]".repeat(65)
    val nesting = assertFailsWith<PiyoDeckImportError.InvalidJson> {
      PiyoDeckStrictJson.validate(deep.toByteArray(), "deck.json")
    }
    assertEquals("nesting exceeds 64 levels", nesting.reason)
    PiyoDeckStrictJson.validate(("[".repeat(64) + "1" + "]".repeat(64)).toByteArray(), "deck.json")
    assertEquals(
      PiyoDeckImportError.DuplicateJsonKey("deck.json", "$.a[1].caf\u0065\u0301"),
      assertFailsWith<PiyoDeckImportError.DuplicateJsonKey> {
        PiyoDeckStrictJson.validate(
          "{\"a\":[{},{\"caf\\u00e9\":1,\"cafe\\u0301\":2}]}".toByteArray(), "deck.json",
        )
      },
    )
    for (malformed in listOf("{\"a\":01}", "[1,]", "{\"a\":\"\t\"}", "tru", "{} {}", "\"\\x\"")) {
      assertFailsWith<PiyoDeckImportError.InvalidJson>(malformed) {
        PiyoDeckStrictJson.validate(malformed.toByteArray(), "deck.json")
      }
    }
  }

  @Test
  fun readerRejectsTrailingDataPreambleAndWrongEntrySet() {
    val deck = DeckJson.decodeDeck(Shared.fixture("valid/basic-deck.json"))
    val packageData = PiyoDeckPackageWriter.write(deck, deckSchema)
    assertEquals(
      PiyoDeckImportError.Family.MALFORMED_ARCHIVE, readError(packageData + byteArrayOf(0)).family,
    )
    assertEquals(
      PiyoDeckImportError.Family.MALFORMED_ARCHIVE, readError(byteArrayOf(1, 2, 3)).family,
    )
    val renamed = replaceAll(packageData, "deck.json".toByteArray(), "deck.jsox".toByteArray())
    assertEquals(
      PiyoDeckImportError.InvalidEntrySet(listOf("manifest.json", "deck.jsox")), readError(renamed),
    )
    assertFailsWith<PiyoDeckImportError.InvalidEntrySet> {
      PiyoDeckZip.write(listOf(PiyoDeckZipEntry("deck.json", byteArrayOf())))
    }
  }

  @Test
  fun writerRejectsInvalidUserDecksBeforeEncoding() {
    val official = DeckJson.decodeDeck(Shared.fixture("invalid/official-deck.json"))
    val error = assertFailsWith<PiyoDeckImportError.InvalidUserDeck> {
      PiyoDeckPackageWriter.write(official, deckSchema)
    }
    assertEquals(PiyoDeckImportError.Family.INVALID_CONTENT, error.family)
  }

  private fun readError(data: ByteArray): PiyoDeckImportError =
    try {
      PiyoDeckPackageReader.read(data, deckSchema)
      fail("Expected import to fail")
    } catch (error: PiyoDeckImportError) {
      error
    }

  private fun rawPackage(
    deckData: ByteArray,
    formatVersion: Int = 1,
    deckSchemaVersion: Int = 1,
    itemCount: Int? = null,
    sha256: String? = null,
  ): ByteArray = PiyoDeckZip.write(
    listOf(
      PiyoDeckZipEntry(
        "manifest.json",
        makeManifest(deckData, formatVersion, deckSchemaVersion, itemCount, sha256),
      ),
      PiyoDeckZipEntry("deck.json", deckData),
    ),
  )

  private fun makeManifest(
    deckData: ByteArray,
    formatVersion: Int = 1,
    deckSchemaVersion: Int = 1,
    itemCount: Int? = null,
    sha256: String? = null,
  ): ByteArray {
    val deck = DeckJson.decodeDeck(deckData)
    return PiyoDeckManifest(
      formatVersion = formatVersion,
      deckSchemaVersion = deckSchemaVersion,
      deck = PiyoDeckManifest.DeckDescriptor(
        deckId = deck.deckId,
        deckVersion = deck.version,
        itemCount = itemCount ?: deck.items.size,
        sizeBytes = deckData.size,
        sha256 = sha256 ?: Shared.sha256(deckData),
      ),
    ).encode(pretty = true)
  }

  private fun centralDirectoryOffset(data: ByteArray): Int {
    val offset = data.size - 22 + 16
    return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8) or
      ((data[offset + 2].toInt() and 0xFF) shl 16) or ((data[offset + 3].toInt() and 0xFF) shl 24)
  }
}
