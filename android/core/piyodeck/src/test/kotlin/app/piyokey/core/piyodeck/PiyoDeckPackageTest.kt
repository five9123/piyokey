package app.piyokey.core.piyodeck

import app.piyokey.core.deckkit.DeckKitJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class PiyoDeckPackageTest {
  @Test
  fun sharedFixtureRoundTripsToCrossPlatformCanonicalPackage() {
    val source = fixture("valid/basic-deck.json")
    val deck = DeckKitJson.decodeDeck(source.decodeToString())

    val first = PiyoDeckPackageWriter.write(deck, deckSchemaSource)
    val second = writePackage(source)
    assertContentEquals(first, second)
    val sharedGolden = fixture("valid/basic.typedeck")
    assertContentEquals(sharedGolden, first)
    assertEquals(1_109, first.size)
    assertEquals(
      "025efa7a0584509fd892221a01c4b3cdf828472c3ffeb13a7eec420102061c31",
      PiyoDeckDigest.sha256Hex(first),
    )

    val entries = PiyoDeckZip.read(first)
    assertEquals(listOf("manifest.json", "deck.json"), entries.keys.toList())
    val generatedManifest = entries.getValue("manifest.json")
    val generatedDeck = entries.getValue("deck.json")
    assertEquals(311, generatedManifest.size)
    assertEquals(580, generatedDeck.size)
    assertEquals(
      "fe7fc1ed3af0728582698bd3445d2af2eb68996c500322b1234f16f79f4ff20a",
      PiyoDeckDigest.sha256Hex(generatedManifest),
    )
    assertEquals(
      "7c5b6496007b98ef4ead02b9da6a2dbc560a351e1f214d0df49633ac6c42d90b",
      PiyoDeckDigest.sha256Hex(generatedDeck),
    )
    assertTrue(0x0A.toByte() !in generatedManifest)
    assertTrue(0x0D.toByte() !in generatedManifest)
    assertTrue(0x0A.toByte() !in generatedDeck)
    assertTrue(0x0D.toByte() !in generatedDeck)

    val imported = readPackage(first)
    assertEquals(deck, imported.deck)
    assertContentEquals(generatedDeck, imported.deckData)
    assertEquals(PiyoDeckDigest.sha256Hex(imported.deckData), imported.contentSha256)
    assertEquals("user_00000000000000000000000000000001", imported.manifest.deck.deckId)
    assertEquals(2, imported.manifest.deck.itemCount)
    assertEquals(deck, readPackage(sharedGolden).deck)
    val prettyGolden = fixture("valid/pretty-basic.typedeck")
    val importedPretty = readPackage(prettyGolden)
    assertEquals(deck, importedPretty.deck)
    assertContentEquals(sharedGolden, PiyoDeckPackageWriter.write(importedPretty.deck, deckSchemaSource))
  }

  @Test
  fun sharedMaliciousBinaryGoldensFailClosed() {
    assertIs<PiyoDeckImportException.Sha256Mismatch>(
      assertFailsWith { readPackage(fixture("invalid/wrong-sha.typedeck")) },
    )
    val unicodeError = assertFailsWith<PiyoDeckImportException.InvalidJson> {
      readPackage(fixture("invalid/unpaired-surrogate.typedeck"))
    }
    assertEquals("deck.json", unicodeError.name)
    assertTrue(unicodeError.reason.contains("invalid Unicode scalar"))
  }

  @Test
  fun sharedManifestDescribesPrettyFixtureBytes() {
    val manifest = PiyoDeckStrictJson.decodeObject(
      fixture("valid/basic-manifest.json"),
      "basic-manifest.json",
    )
    val deck = fixture("valid/basic-deck.json")
    val descriptor = manifest.getValue("deck").jsonObject
    assertEquals(deck.size, descriptor.getValue("size_bytes").jsonPrimitive.int)
    assertEquals(PiyoDeckDigest.sha256Hex(deck), descriptor.getValue("sha256").jsonPrimitive.content)
  }

  @Test
  fun invalidSharedDeckFixturesFailClosed() {
    val official = assertFailsWith<PiyoDeckImportException.InvalidUserDeck> {
      writePackage(fixture("invalid/official-deck.json"))
    }
    assertTrue(official.issues.any { it.code == "reserved_identifier" })
    assertTrue(official.issues.any { it.code == "user_deck_official" })

    val audio = assertFailsWith<PiyoDeckImportException.InvalidUserDeck> {
      writePackage(fixture("invalid/audio-deck.json"))
    }
    assertTrue(audio.issues.any { it.code == "user_deck_audio" })

    val unknown = assertFailsWith<PiyoDeckImportException.DeckSchemaViolation> {
      writePackage(fixture("invalid/unknown-field-deck.json"))
    }
    assertTrue(unknown.issues.any { it.code == "schema.additionalProperties" && it.path == "$.premium" })
  }

  @Test
  fun duplicateJsonKeysIncludingEscapedEquivalentsAreRejected() {
    val source = fixture("valid/basic-deck.json").decodeToString()
    val duplicate = source.replace(
      "\"version\": 1",
      "\"version\": 1, \"\\u0076ersion\": 1",
    ).encodeToByteArray()
    val packageData = rawPackage(duplicate)

    val error = assertFailsWith<PiyoDeckImportException.DuplicateJsonKey> {
      readPackage(packageData)
    }
    assertEquals("deck.json", error.name)
    assertEquals("$.version", error.path)

    val duplicateManifest = """{"format":"piyokey.deck-package","format":"piyokey.deck-package"}"""
    val manifestError = assertFailsWith<PiyoDeckImportException.DuplicateJsonKey> {
      PiyoDeckStrictJson.decodeObject(duplicateManifest.encodeToByteArray(), "manifest.json")
    }
    assertEquals("$.format", manifestError.path)
  }

  @Test
  fun futurePackageAndDeckSchemaVersionsHaveDedicatedErrors() {
    val deckData = fixture("valid/basic-deck.json")
    assertEquals(
      PiyoDeckImportException.UnsupportedFormatVersion(2),
      assertFailsWith { readPackage(rawPackage(deckData, formatVersion = 2)) },
    )
    assertEquals(
      PiyoDeckImportException.UnsupportedDeckSchemaVersion(2),
      assertFailsWith { readPackage(rawPackage(deckData, deckSchemaVersion = 2)) },
    )
  }

  @Test
  fun readerChecksShaAndManifestMetadata() {
    val deckData = fixture("valid/basic-deck.json")
    assertIs<PiyoDeckImportException.Sha256Mismatch>(
      assertFailsWith {
        readPackage(rawPackage(deckData, sha256 = "0".repeat(64)))
      },
    )
    assertEquals(
      PiyoDeckImportException.ManifestMismatch("deck.item_count"),
      assertFailsWith { readPackage(rawPackage(deckData, itemCount = 1)) },
    )
    assertEquals(
      PiyoDeckImportException.ManifestMismatch("deck.deck_version"),
      assertFailsWith { readPackage(rawPackage(deckData, deckVersion = 2)) },
    )
  }

  @Test
  fun missingOrWrongTypeDeckMetadataFailsAsManifestMismatchBeforeSchema() {
    val validDeck = PiyoDeckStrictJson.decodeObject(fixture("valid/basic-deck.json"), "deck.json")
    val cases = listOf(
      MetadataMutation("deck_id", null, "deck.deck_id"),
      MetadataMutation("deck_id", JsonPrimitive(7), "deck.deck_id"),
      MetadataMutation("version", null, "deck.deck_version"),
      MetadataMutation("version", JsonPrimitive("1"), "deck.deck_version"),
      MetadataMutation("items", null, "deck.item_count"),
      MetadataMutation("items", JsonPrimitive("not-an-array"), "deck.item_count"),
    )

    for (case in cases) {
      val values = validDeck.toMutableMap()
      if (case.replacement == null) {
        values.remove(case.field)
      } else {
        values[case.field] = case.replacement
      }
      val deckData = PiyoDeckStrictJson.canonicalData(JsonObject(values))
      val packageData = rawPackage(
        deckData,
        deckId = "user_00000000000000000000000000000001",
        deckVersion = 1,
        itemCount = 2,
      )

      assertEquals(
        PiyoDeckImportException.ManifestMismatch(case.manifestField),
        assertFailsWith { readPackage(packageData) },
        "${case.field} mutation ${case.replacement} must fail before deck schema validation",
      )
    }
  }

  @Test
  fun crcMutationIsRejectedBeforeJsonDecoding() {
    val packageData = writePackage(fixture("valid/basic-deck.json"))
    val storedDeck = PiyoDeckZip.read(packageData).getValue("deck.json")
    val payloadOffset = packageData.indexOf(storedDeck)
    assertTrue(payloadOffset >= 0)
    val mutated = packageData.copyOf()
    mutated[payloadOffset] = (mutated[payloadOffset].toInt() xor 0x01).toByte()

    assertEquals(
      PiyoDeckImportException.CrcMismatch("deck.json"),
      assertFailsWith { readPackage(mutated) },
    )
  }

  @Test
  fun unsupportedZipFeaturesAreRejected() {
    val packageData = writePackage(fixture("valid/basic-deck.json"))
    val centralOffset = packageData.centralDirectoryOffset()

    packageData.mutated(centralOffset + 8) { it or 0x01 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("encryption"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.mutated(centralOffset + 8) { it or 0x08 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("general-purpose flag 0x808"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.mutated(centralOffset + 10) { 8 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("compression method 8"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.copyOf().also { zip64 ->
      for (index in centralOffset + 20 until centralOffset + 24) zip64[index] = 0xFF.toByte()
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("ZIP64"),
        assertFailsWith { readPackage(zip64) },
      )
    }
    packageData.copyOf().also { symlink ->
      symlink[centralOffset + 5] = 3
      symlink[centralOffset + 41] = 0xA0.toByte()
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("symbolic link"),
        assertFailsWith { readPackage(symlink) },
      )
    }
    packageData.mutated(centralOffset + 38) { 0x10 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("directory entry"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.mutated(centralOffset + 30) { 1 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("entry extra field"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.mutated(centralOffset + 32) { 1 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("entry comment"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.mutated(28) { 1 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("local entry extra field"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.mutated(6) { it or 0x01 }.also {
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("encryption"),
        assertFailsWith { readPackage(it) },
      )
    }
    packageData.copyOf().also { localZip64 ->
      for (index in 18 until 22) localZip64[index] = 0xFF.toByte()
      assertEquals(
        PiyoDeckImportException.UnsupportedArchiveFeature("ZIP64"),
        assertFailsWith { readPackage(localZip64) },
      )
    }
  }

  @Test
  fun unsafePathsEntrySetsAndHiddenBytesAreRejected() {
    val packageData = writePackage(fixture("valid/basic-deck.json"))
    val unsafe = packageData.replaceAll(
      "manifest.json".encodeToByteArray(),
      "evil\\path.jsn".encodeToByteArray(),
    )
    assertEquals(
      PiyoDeckImportException.UnsafeEntryPath("evil\\path.jsn"),
      assertFailsWith { readPackage(unsafe) },
    )

    val wrongEntry = packageData.replaceAll(
      "manifest.json".encodeToByteArray(),
      "unknown00.jsn".encodeToByteArray(),
    )
    assertIs<PiyoDeckImportException.InvalidEntrySet>(
      assertFailsWith { readPackage(wrongEntry) },
    )

    val trailing = packageData + byteArrayOf(0)
    assertIs<PiyoDeckImportException.MalformedArchive>(
      assertFailsWith { readPackage(trailing) },
    )
    val preamble = byteArrayOf(0) + packageData
    assertIs<PiyoDeckImportException.MalformedArchive>(
      assertFailsWith { readPackage(preamble) },
    )
  }

  @Test
  fun packageAndMetadataEntryLimitsAreCheckedBeforeJson() {
    val oversizedPackage = ByteArray(PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES + 1)
    assertEquals(
      PiyoDeckImportException.PackageTooLarge(
        PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES + 1,
        PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      ),
      assertFailsWith { readPackage(oversizedPackage) },
    )

    val oversizedDeck = ByteArray(PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES + 1) { 0x20 }
    val packageData = PiyoDeckZip.write(
      listOf(
        PiyoDeckZipEntry("manifest.json", "{}".encodeToByteArray()),
        PiyoDeckZipEntry("deck.json", oversizedDeck),
      ),
    )
    assertEquals(
      PiyoDeckImportException.EntryTooLarge(
        "deck.json",
        PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES + 1,
        PiyoDeckPackageLimits.MAXIMUM_DECK_BYTES,
      ),
      assertFailsWith { readPackage(packageData) },
    )
  }

  @Test
  fun bomInvalidUtf8DeepNestingAndUnknownManifestFieldsAreRejected() {
    val deckData = fixture("valid/basic-deck.json")
    val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + deckData
    assertEquals(
      PiyoDeckImportException.InvalidJson("deck.json", "UTF-8 BOM is not permitted"),
      assertFailsWith { readPackage(rawPackage(bom)) },
    )
    assertEquals(
      PiyoDeckImportException.InvalidJson("deck.json", "invalid UTF-8"),
      assertFailsWith { readPackage(rawPackage(byteArrayOf(0xFF.toByte()))) },
    )

    val nested = ("[".repeat(65) + "0" + "]".repeat(65)).encodeToByteArray()
    val nestedJson = "{\"nested\":${nested.decodeToString()}}".encodeToByteArray()
    assertEquals(
      PiyoDeckImportException.InvalidJson("deck.json", "nesting exceeds 64 levels"),
      assertFailsWith { readPackage(rawPackage(nestedJson)) },
    )

    val manifest = makeManifest(deckData).toMutableMap()
    manifest["license_key"] = kotlinx.serialization.json.JsonPrimitive("forbidden")
    val packageWithUnknownManifest = PiyoDeckZip.write(
      listOf(
        PiyoDeckZipEntry("manifest.json", PiyoDeckStrictJson.canonicalData(JsonObject(manifest))),
        PiyoDeckZipEntry("deck.json", deckData),
      ),
    )
    val manifestError = assertFailsWith<PiyoDeckImportException.InvalidManifest> {
      readPackage(packageWithUnknownManifest)
    }
    assertTrue(manifestError.issues.any { it.path == "$.license_key" })
  }

  @Test
  fun escapedUnpairedUnicodeSurrogatesAreRejected() {
    val source = fixture("valid/basic-deck.json").decodeToString()
    for (escape in listOf("\\ud800", "\\udc00", "\\ud800\\u0041")) {
      val invalid = source.replace("はじめてのマイデッキ", escape).encodeToByteArray()
      val readerError = assertFailsWith<PiyoDeckImportException.InvalidJson> {
        readPackage(rawPackage(invalid))
      }
      assertEquals("deck.json", readerError.name)
      assertTrue(readerError.reason.contains("invalid Unicode scalar"))
      assertFailsWith<PiyoDeckImportException.InvalidJson> { writePackage(invalid) }
    }

    val validPair = source.replace("はじめてのマイデッキ", "\\uD835\\uDFD9").encodeToByteArray()
    assertEquals("𝟙", readPackage(rawPackage(validPair)).deck.name)
    assertEquals("𝟙", readPackage(writePackage(validPair)).deck.name)

    val rawUnpaired = kotlinx.serialization.json.JsonPrimitive(String(charArrayOf('\uD800')))
    assertFailsWith<PiyoDeckImportException.InvalidJson> {
      PiyoDeckStrictJson.canonicalData(rawUnpaired)
    }
  }

  @Test
  fun itemLimitDuplicateIdsAndAudioPolicyAreEnforced() {
    val deck = PiyoDeckStrictJson.decodeObject(fixture("valid/basic-deck.json"), "deck.json")
    val firstItem = deck.getValue("items").jsonArray.first()
    val tooMany = JsonObject(deck + ("items" to JsonArray(List(1_001) { firstItem })))
    val issues = PiyoDeckUserDeckValidator.validate(
      DeckKitJson.decodeDeck(PiyoDeckStrictJson.canonicalData(tooMany).decodeToString()),
    )
    assertTrue(issues.any { it.code == "user_deck_item_limit" })
    assertTrue(issues.any { it.code == "duplicate" })
  }

  @Test
  fun timestampsRequireCanonicalUtcWholeSeconds() {
    val deck = PiyoDeckStrictJson.decodeObject(fixture("valid/basic-deck.json"), "deck.json")
    for (timestamp in listOf("2026-08-14T09:00:00+09:00", "2026-08-14T00:00:00.000Z")) {
      val changed = JsonObject(deck + ("updated_at" to kotlinx.serialization.json.JsonPrimitive(timestamp)))
      assertFailsWith<PiyoDeckImportException.DeckSchemaViolation> {
        writePackage(PiyoDeckStrictJson.canonicalData(changed))
      }
      assertFailsWith<PiyoDeckImportException.DeckSchemaViolation> {
        readPackage(rawPackage(PiyoDeckStrictJson.canonicalData(changed)))
      }
    }
  }

  private fun rawPackage(
    deckData: ByteArray,
    formatVersion: Int = 1,
    deckSchemaVersion: Int = 1,
    deckId: String? = null,
    deckVersion: Int? = null,
    itemCount: Int? = null,
    sha256: String? = null,
  ): ByteArray {
    val manifest = makeManifest(
      deckData,
      formatVersion,
      deckSchemaVersion,
      deckId,
      deckVersion,
      itemCount,
      sha256,
    )
    return PiyoDeckZip.write(
      listOf(
        PiyoDeckZipEntry("manifest.json", PiyoDeckStrictJson.canonicalData(manifest)),
        PiyoDeckZipEntry("deck.json", deckData),
      ),
    )
  }

  private fun makeManifest(
    deckData: ByteArray,
    formatVersion: Int = 1,
    deckSchemaVersion: Int = 1,
    deckId: String? = null,
    deckVersion: Int? = null,
    itemCount: Int? = null,
    sha256: String? = null,
  ): JsonObject {
    val deck = runCatching { Json.parseToJsonElement(deckData.decodeToString()).jsonObject }.getOrNull()
    return buildJsonObject {
      put("format", PiyoDeckManifest.FORMAT_IDENTIFIER)
      put("format_version", formatVersion)
      put("deck_schema_version", deckSchemaVersion)
      put("deck", buildJsonObject {
        put("path", PiyoDeckManifest.DECK_PATH)
        put("media_type", PiyoDeckManifest.DECK_MEDIA_TYPE)
        put(
          "deck_id",
          deckId
            ?: deck?.get("deck_id")?.jsonPrimitive?.content
            ?: "user_00000000000000000000000000000001",
        )
        put("deck_version", deckVersion ?: deck?.get("version")?.jsonPrimitive?.int ?: 1)
        put("item_count", itemCount ?: deck?.get("items")?.jsonArray?.size ?: 1)
        put("size_bytes", deckData.size)
        put("sha256", sha256 ?: PiyoDeckDigest.sha256Hex(deckData))
      })
    }
  }

  private fun fixture(path: String): ByteArray =
    File(repositoryRoot(), "shared/piyodeck/fixtures/$path").readBytes()

  private fun repositoryRoot(): File =
    File(checkNotNull(System.getProperty("piyokey.repositoryRoot")))

  private val deckSchemaSource: String
    get() = File(repositoryRoot(), "shared/schema/deck.schema.json").readText()

  private fun readPackage(data: ByteArray): PiyoDeckPackage =
    PiyoDeckPackageReader.read(data, deckSchemaSource)

  private fun writePackage(data: ByteArray): ByteArray =
    PiyoDeckPackageWriter.write(data, deckSchemaSource)

  private data class MetadataMutation(
    val field: String,
    val replacement: JsonElement?,
    val manifestField: String,
  )

  private fun ByteArray.centralDirectoryOffset(): Int {
    val offset = size - 22 + 16
    return (this[offset].toInt() and 0xFF) or
      ((this[offset + 1].toInt() and 0xFF) shl 8) or
      ((this[offset + 2].toInt() and 0xFF) shl 16) or
      ((this[offset + 3].toInt() and 0xFF) shl 24)
  }

  private fun ByteArray.mutated(index: Int, mutation: (Int) -> Int): ByteArray =
    copyOf().also { it[index] = mutation(it[index].toInt() and 0xFF).toByte() }

  private fun ByteArray.indexOf(target: ByteArray): Int {
    if (target.isEmpty()) return 0
    outer@ for (index in 0..size - target.size) {
      for (targetIndex in target.indices) {
        if (this[index + targetIndex] != target[targetIndex]) continue@outer
      }
      return index
    }
    return -1
  }

  private fun ByteArray.replaceAll(target: ByteArray, replacement: ByteArray): ByteArray {
    require(target.size == replacement.size)
    val result = copyOf()
    var index = 0
    while (index <= result.size - target.size) {
      var matches = true
      for (targetIndex in target.indices) {
        if (result[index + targetIndex] != target[targetIndex]) {
          matches = false
          break
        }
      }
      if (matches) {
        replacement.copyInto(result, index)
        index += replacement.size
      } else {
        index += 1
      }
    }
    return result
  }
}
