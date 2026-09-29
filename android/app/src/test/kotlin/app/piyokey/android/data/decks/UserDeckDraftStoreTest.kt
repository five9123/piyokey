package app.piyokey.android.data.decks

import app.piyokey.android.data.DataTestSupport
import app.piyokey.android.data.DataTestSupport.at
import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckAuthor
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.PiyoDeckPackageLimits
import app.piyokey.core.deckkit.PiyoDeckPackageWriter
import app.piyokey.core.domain.PiyoDeckImportCollision
import app.piyokey.core.domain.UserDeckDraft
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDeckDraftStoreTest {
  private val root = File(DataTestSupport.tempDir(), "DeckMaker")
  private val store = UserDeckDraftStore(root)
  private val file = File(root, "active-user-deck-draft.json")

  private fun sequence(vararg values: String): () -> String {
    val iterator = values.iterator()
    return { iterator.next() }
  }

  private fun newDraft(deck: Char, item: Char) = UserDeckDraft.new(at(100), sequence(deck.toString().repeat(32), item.toString().repeat(32)))

  private fun deck(id: String, version: Int, official: Boolean) = Deck(
    deckId = id, version = version, name = "単語", author = DeckAuthor("author", "Piyo"), official = official,
    type = DeckType.WORD, level = 1, tags = listOf("daily"), createdAt = at(100), updatedAt = at(200),
    items = listOf(DeckItem(id = "item_" + "8".repeat(32), ko = "안녕", readingJa = "アンニョン", meaningJa = "こんにちは", audio = null)),
  )

  @Test
  fun incompleteNewDraftRoundTripsWithoutFinalValidation() {
    val draft = UserDeckDraft.new(at(1_000), sequence("1".repeat(32), "2".repeat(32)))
      .withName("My unfinished deck", "en")
      .copy(defaultLocale = "fr-CA")
      .withTags(listOf("draft"), "en")
      .updatingItem(0) { it.copy(ko = "아직").withMeaning("", "en") }
      .addingItem { "4".repeat(32) }
    val active = store.save(draft, at(2_000))
    val loaded = assertNotNull(store.load())
    assertEquals(active, loaded)
    assertEquals(draft, loaded.draft)
    assertEquals(at(2_000), loaded.createdAt)
    assertEquals(at(2_000), loaded.updatedAt)
    assertNull(loaded.baseDeckId)
    assertNull(loaded.baseVersion)
  }

  @Test
  fun repeatedSaveKeepsIdentityAndDifferentFlowReplacesDraft() {
    val draft = newDraft('1', '2')
    val first = store.save(draft, at(3_000))
    val second = store.save(draft.copy(name = "변경 중"), at(4_000))
    assertEquals(first.draftId, second.draftId)
    assertEquals(first.createdAt, second.createdAt)
    assertEquals(at(4_000), second.updatedAt)
    assertEquals("변경 중", store.load()?.draft?.name)

    val replacement = store.save(newDraft('3', '4'), at(6_000))
    assertNotEquals(first.draftId, replacement.draftId)
    assertEquals(at(6_000), store.load()?.createdAt)
  }

  @Test
  fun editingAndOfficialCopyOriginsRoundTripWithBaseIdentity() {
    val userDeck = deck("user_" + "5".repeat(32), 7, official = false)
    val editing = store.save(UserDeckDraft.editing(userDeck), at(7_000))
    assertEquals(userDeck.deckId, editing.baseDeckId)
    assertEquals(7, editing.baseVersion)
    assertEquals(UserDeckDraft.Origin.Editing, store.load()?.draft?.origin)

    val official = deck("official_source", 8, official = true)
    val copy = store.save(UserDeckDraft.copyingOfficial(official, at(8_000), sequence("6".repeat(32), "7".repeat(32))), at(9_000))
    assertEquals(official.deckId, copy.baseDeckId)
    assertNull(copy.baseVersion)
    assertEquals(UserDeckDraft.Origin.OfficialCopy(official.deckId), store.load()?.draft?.origin)
  }

  @Test
  fun writeFailureKeepsPreviousDraftAndCorruptPrimaryRecovers() {
    val original = newDraft('1', '2')
    val expected = store.save(original, at(10_000))
    val failing = UserDeckDraftStore(root, fileWriter = { _, _ -> throw IOException("write") })
    assertFailsWith<IOException> { failing.save(original.copy(name = "must not replace"), at(11_000)) }
    assertEquals(original, store.load()?.draft)

    file.writeText("not-json")
    assertEquals(expected, store.load())
    assertTrue(RecoverableJsonFile.corruptFile(file).exists())
  }

  @Test
  fun futureSchemaIsPreservedAndBlocksOlderWriter() {
    val original = newDraft('1', '2')
    store.save(original, at(13_000))
    val future = file.readText().replace("\"schema_version\": 1,", "\"schema_version\": 99,")
    file.writeText(future)
    RecoverableJsonFile.backupFile(file).writeText(future)
    assertEquals(99, assertFailsWith<UserDeckDraftStoreException.UnsupportedSchema> { store.load() }.version)
    assertFailsWith<UserDeckDraftStoreException.UnsupportedSchema> { store.save(original) }
    assertEquals(future, file.readText())
    assertFalse(RecoverableJsonFile.corruptFile(file).exists())
  }

  @Test
  fun clearRequiresMatchingCommittedDraftId() {
    val active = store.save(newDraft('1', '2'), at(14_000))
    assertFalse(store.clear("draft_" + "f".repeat(32)))
    assertNotNull(store.load())
    assertTrue(store.clear(active.draftId))
    assertNull(store.load())
  }

  @Test
  fun pendingImportsStageListReadAndCleanPartials() {
    val pending = PendingImportsStore(File(DataTestSupport.tempDir(), "PendingImports"))
    val schema = File(DataTestSupport.sharedRoot, "schema/deck.schema.json").readBytes()
    val deck = DataTestSupport.userDeck(version = 1, meaning = "文字", updatedAt = at(100))
    val packageBytes = PiyoDeckPackageWriter.write(deck, schema)
    val staged = pending.stage(ByteArrayInputStream(packageBytes))
    assertTrue(staged.name.endsWith(".typedeck"))
    File(pending.rootDir, "stale.typedeck.partial").writeText("x")
    assertEquals(listOf(staged), pending.pending())
    assertFalse(File(pending.rootDir, "stale.typedeck.partial").exists())

    val read = pending.read(staged, schema)
    assertEquals(deck, read.deck)
    assertEquals(PiyoDeckImportCollision.New, PiyoDeckImportCollision.of(null, null, null, read))
    val data = DeckJson.encodeDeck(deck).toByteArray()
    assertEquals(PiyoDeckImportCollision.Identical, PiyoDeckImportCollision.of(1, DataTestSupport.hash(data), deck, read))
    val older = PiyoDeckImportCollision.of(2, "0".repeat(64), deck.copy(version = 2), read)
    assertTrue(older.isDowngrade)
    pending.remove(staged)
    assertTrue(pending.pending().isEmpty())

    val tooLarge = ByteArray(PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES + 1)
    assertFailsWith<PiyoDeckFileTooLargeException> { pending.stage(ByteArrayInputStream(tooLarge)) }
    assertTrue(pending.pending().isEmpty())
    assertContentEquals(packageBytes, PiyoDeckPackageWriter.write(deck, schema))
  }
}
