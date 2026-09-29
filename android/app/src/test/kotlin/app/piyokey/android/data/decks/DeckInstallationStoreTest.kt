package app.piyokey.android.data.decks

import app.piyokey.android.data.DataTestSupport
import app.piyokey.android.data.DataTestSupport.at
import app.piyokey.android.data.DataTestSupport.encode
import app.piyokey.android.data.DataTestSupport.hash
import app.piyokey.android.data.DataTestSupport.userDeck
import app.piyokey.android.data.persistence.RecoverableJsonFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeckInstallationStoreTest {
  private val root = File(DataTestSupport.tempDir(), "InstalledDecks")
  private val store = DeckInstallationStore(root)
  private val source = BundledDeckSource(DataTestSupport.assets)
  private val indexFile = File(root, "installed-decks.json")

  private fun payload(id: String) = runBlocking { source.fetch(DataTestSupport.catalogEntry(id)) }

  private fun editIndex(transform: (MutableMap<String, JsonElement>) -> Unit) = editJson(indexFile, transform)

  private fun editJson(file: File, transform: (MutableMap<String, JsonElement>) -> Unit, target: File = file) {
    val map = Json.parseToJsonElement(file.readText()).jsonObject.toMutableMap()
    transform(map)
    target.parentFile.mkdirs()
    target.writeText(JsonObject(map).toString())
  }

  private fun records(map: Map<String, JsonElement>) = map.getValue("records").jsonArray.map { it.jsonObject.toMutableMap() }

  @Test
  fun bundledSourceValidatesCatalogMetadataAndItems() {
    val entry = DataTestSupport.catalogEntry("official_keyboard_start")
    val payload = payload("official_keyboard_start")
    assertEquals(entry.deckId, payload.deck.deckId)
    assertEquals(entry.itemCount, payload.deck.items.size)
    assertEquals(InstalledDeckSource.BUNDLE, payload.source)
    assertFailsWith<DeckSourceException.UnsafePath> {
      runBlocking { source.fetch(DataTestSupport.catalogEntry("official_keyboard_start").copy(fileUrl = "../catalog.json")) }
    }
    assertFailsWith<DeckSourceException.MetadataMismatch> {
      runBlocking { source.fetch(DataTestSupport.catalogEntry("official_keyboard_start").copy(name = "renamed")) }
    }
  }

  @Test
  fun installReloadAndRemovePersistFullDeckForOfflineUse() {
    val entry = DataTestSupport.catalogEntry("official_daily_words")
    val payload = payload(entry.deckId)
    val record = store.install(payload.data, payload.source, now = at(100))
    assertEquals(InstalledDeckSource.BUNDLE, record.source)

    val reloaded = store.loadSnapshot()
    assertEquals(payload.deck.items.first().ko, reloaded.decks[entry.deckId]?.items?.first()?.ko)
    assertEquals(at(100), reloaded.records[entry.deckId]?.installedAt)
    assertEquals(entry.tags, reloaded.downloadHistory[entry.deckId])
    assertEquals(3, Json.parseToJsonElement(indexFile.readText()).jsonObject["schema_version"].toString().toInt())

    store.remove(entry.deckId)
    val removed = store.loadSnapshot()
    assertTrue(removed.decks.isEmpty())
    assertEquals(entry.tags, removed.downloadHistory[entry.deckId])
    assertFalse(File(root, "${entry.deckId}.json").exists())
  }

  @Test
  fun unsupportedIndexSchemaIsRejectedAndPreserved() {
    root.mkdirs()
    indexFile.writeText("""{"schema_version":999,"records":[]}""")
    assertEquals(999, assertFailsWith<DeckInstallationStoreException.UnsupportedSchema> { store.loadSnapshot() }.version)
    assertTrue(indexFile.exists())
  }

  @Test
  fun legacyIndexWithoutHistoryOrUserMetadataMigrates() {
    val entry = DataTestSupport.catalogEntry("official_keyboard_start")
    store.install(payload(entry.deckId).data, InstalledDeckSource.BUNDLE)
    editIndex { index ->
      index.remove("download_history")
      index["schema_version"] = JsonPrimitive(1)
      val records = records(index)
      records[0].remove("is_locally_modified")
      records[0].remove("content_sha256")
      index["records"] = JsonArray(records.map { JsonObject(it) })
    }
    val migrated = store.loadSnapshot()
    assertEquals(entry.tags, migrated.downloadHistory[entry.deckId])
    assertNull(migrated.records[entry.deckId]?.contentSha256)
    assertEquals(false, migrated.records[entry.deckId]?.isLocallyModified)
  }

  @Test
  fun replacingUserDeckPreservesDatesAndPersistsDocumentMetadata() {
    val first = encode(userDeck(version = 1, meaning = "最初", updatedAt = at(100)))
    store.install(first, InstalledDeckSource.IMPORTED, hash(first), derivedFromDeckId = "official_keyboard_start", now = at(100))
    store.markPlayed("user_0123456789abcdef0123456789abcdef", at(150))

    val second = encode(userDeck(version = 2, meaning = "更新", updatedAt = at(200)))
    val record = store.install(second, InstalledDeckSource.CREATED, hash(second), isLocallyModified = true, now = at(200))

    assertEquals(at(100), record.installedAt)
    assertEquals(at(150), record.lastPlayedAt)
    assertEquals(hash(second), record.contentSha256)
    assertTrue(record.isLocallyModified)
    assertEquals(InstalledDeckSource.CREATED, record.source)
    assertEquals("official_keyboard_start", record.derivedFromDeckId)
    assertEquals("更新", store.loadSnapshot().decks.values.single().items[0].meaningJa)
    assertContentEquals(second, store.data("user_0123456789abcdef0123456789abcdef"))
  }

  @Test
  fun expectedCurrentVersionCommitsOrLeavesStoreUntouched() {
    val id = "user_0123456789abcdef0123456789abcdef"
    val first = encode(userDeck(version = 1, meaning = "最初", updatedAt = at(100)))
    store.install(first, InstalledDeckSource.CREATED, hash(first), packageFormatVersion = 1)
    val indexBefore = indexFile.readBytes()
    val deckBefore = File(root, "$id.json").readBytes()
    val second = encode(userDeck(version = 2, meaning = "更新", updatedAt = at(200)))

    val error = assertFailsWith<DeckInstallationStoreException.SourceChanged> {
      store.install(second, InstalledDeckSource.CREATED, hash(second), 1, true, expectedCurrentVersion = 9)
    }
    assertEquals(DeckInstallationStoreException.SourceChanged(id, 9, 1), error)
    assertContentEquals(indexBefore, indexFile.readBytes())
    assertContentEquals(deckBefore, File(root, "$id.json").readBytes())
    assertFalse(File(root, "PendingTransaction").exists())

    assertEquals(2, store.install(second, InstalledDeckSource.CREATED, hash(second), 1, true, expectedCurrentVersion = 1).version)
    assertContentEquals(second, store.data(id))
    assertFalse(File(root, "PendingTransaction").exists())
  }

  private fun stagePendingInstall(deckData: ByteArray, deckId: String, version: Int) {
    val transaction = File(root, "PendingTransaction")
    editJson(indexFile, { index ->
      val records = records(index)
      records[0]["version"] = JsonPrimitive(version)
      records[0]["content_sha256"] = JsonPrimitive(hash(deckData))
      index["records"] = JsonArray(records.map { JsonObject(it) })
    }, target = File(transaction, "index.json"))
    File(transaction, "deck.json").writeBytes(deckData)
    File(transaction, "transaction.json").writeText("""{"schema_version":1,"operation":"install","deck_id":"$deckId"}""")
  }

  @Test
  fun interruptedInstallTransactionCompletesPayloadAndIndexTogether() {
    val id = "user_0123456789abcdef0123456789abcdef"
    val first = encode(userDeck(version = 1, meaning = "最初", updatedAt = at(100)))
    store.install(first, InstalledDeckSource.IMPORTED, hash(first), 1)
    val second = encode(userDeck(version = 2, meaning = "更新", updatedAt = at(200)))
    stagePendingInstall(second, id, 2)
    File(root, "$id.json").writeBytes(second) // crash after payload swap, before index swap

    val recovered = store.loadSnapshot()
    assertEquals(2, recovered.records[id]?.version)
    assertEquals("更新", recovered.decks[id]?.items?.get(0)?.meaningJa)
    assertFalse(File(root, "PendingTransaction").exists())
  }

  @Test
  fun expectedVersionIsComparedAfterPendingTransactionRecovery() {
    val id = "user_0123456789abcdef0123456789abcdef"
    val first = encode(userDeck(version = 1, meaning = "最初", updatedAt = at(100)))
    store.install(first, InstalledDeckSource.CREATED, hash(first), 1)
    stagePendingInstall(encode(userDeck(version = 2, meaning = "先行更新", updatedAt = at(200))), id, 2)
    val third = encode(userDeck(version = 3, meaning = "競合更新", updatedAt = at(300)))

    val error = assertFailsWith<DeckInstallationStoreException.SourceChanged> {
      store.install(third, InstalledDeckSource.CREATED, hash(third), 1, true, expectedCurrentVersion = 1)
    }
    assertEquals(2, error.actualVersion)
    assertEquals("先行更新", store.loadSnapshot().decks[id]?.items?.get(0)?.meaningJa)
    assertFalse(File(root, "PendingTransaction").exists())
  }

  @Test
  fun stagingWithoutMarkerIsDiscarded() {
    val first = encode(userDeck(version = 1, meaning = "最初", updatedAt = at(100)))
    store.install(first, InstalledDeckSource.CREATED, hash(first), 1)
    File(root, "PendingTransaction").mkdirs()
    File(root, "PendingTransaction/index.json").writeText("{}")
    assertEquals(1, store.loadSnapshot().records.size)
    assertFalse(File(root, "PendingTransaction").exists())
  }

  @Test
  fun deletingAndReimportingUserDeckReconnectsHistory() {
    val id = "user_0123456789abcdef0123456789abcdef"
    val data = encode(userDeck(version = 1, meaning = "文字", updatedAt = at(100)))
    store.install(data, InstalledDeckSource.IMPORTED, hash(data), 1, derivedFromDeckId = "official_keyboard_start", now = at(100))
    store.markPlayed(id, at(150))
    store.remove(id)
    val removed = store.loadSnapshot()
    assertNull(removed.records[id])
    assertEquals(at(150), removed.removedRecords[id]?.lastPlayedAt)

    val restored = store.install(data, InstalledDeckSource.IMPORTED, hash(data), 1, now = at(500))
    assertEquals(at(100), restored.installedAt)
    assertEquals(at(150), restored.lastPlayedAt)
    assertEquals("official_keyboard_start", restored.derivedFromDeckId)
    assertNull(store.loadSnapshot().removedRecords[id])
  }

  @Test
  fun payloadHashMismatchIsQuarantinedAndBadHashRejectedBeforeWriting() {
    val id = "user_0123456789abcdef0123456789abcdef"
    val data = encode(userDeck(version = 1, meaning = "文字", updatedAt = at(100)))
    assertFailsWith<DeckInstallationStoreException.InvalidIndex> {
      store.install(data, InstalledDeckSource.IMPORTED, "0".repeat(64), 1)
    }
    assertFalse(root.exists())

    store.install(data, InstalledDeckSource.IMPORTED, hash(data), 1)
    val tampered = encode(userDeck(version = 1, meaning = "文字", updatedAt = at(100)).copy(name = "改ざんされたデッキ"))
    File(root, "$id.json").writeBytes(tampered)
    RecoverableJsonFile.backupFile(File(root, "$id.json")).writeBytes(tampered)
    val snapshot = store.loadSnapshot()
    assertNull(snapshot.records[id])
    assertNull(snapshot.decks[id])
    assertEquals(hash(data), snapshot.removedRecords[id]?.contentSha256)
  }

  @Test
  fun corruptIndexRestoresFromBackupAndOneBrokenDeckDoesNotClearOthers() {
    val good = DataTestSupport.catalogEntry("official_keyboard_start")
    val broken = DataTestSupport.catalogEntry("official_daily_words")
    store.install(payload(good.deckId).data, InstalledDeckSource.BUNDLE)
    store.install(payload(broken.deckId).data, InstalledDeckSource.BUNDLE)

    indexFile.writeText("not-json")
    assertEquals(2, store.loadSnapshot().decks.size)
    assertTrue(RecoverableJsonFile.corruptFile(indexFile).exists())

    val brokenFile = File(root, "${broken.deckId}.json")
    brokenFile.writeText("broken-primary")
    RecoverableJsonFile.backupFile(brokenFile).writeText("broken-backup")
    val recovered = store.loadSnapshot()
    assertNotNull(recovered.decks[good.deckId])
    assertNull(recovered.decks[broken.deckId])
    assertEquals(broken.tags, recovered.downloadHistory[broken.deckId])
    assertEquals(listOf(good.deckId), store.loadSnapshot().decks.keys.toList())
  }

  @Test
  fun markPlayedUpdatesOnlyTheIndex() {
    val played = DataTestSupport.catalogEntry("official_keyboard_start")
    val unrelated = DataTestSupport.catalogEntry("official_daily_words")
    store.install(payload(played.deckId).data, InstalledDeckSource.BUNDLE)
    store.install(payload(unrelated.deckId).data, InstalledDeckSource.BUNDLE)
    val unrelatedFile = File(root, "${unrelated.deckId}.json")
    unrelatedFile.writeText("broken-primary")
    RecoverableJsonFile.backupFile(unrelatedFile).writeText("broken-backup")

    assertEquals(at(2_000), store.markPlayed(played.deckId, at(2_000))?.lastPlayedAt)
    assertEquals(2, Json.parseToJsonElement(indexFile.readText()).jsonObject["records"]!!.jsonArray.size)
    assertTrue(unrelatedFile.exists())
    assertNull(store.markPlayed("official_missing", at(1)))
    assertFailsWith<DeckInstallationStoreException.InvalidIndex> { store.markPlayed("../x", at(1)) }
  }
}
