package app.piyokey.android.data.decks

import app.piyokey.android.data.DataTestSupport
import app.piyokey.android.data.DataTestSupport.at
import app.piyokey.android.data.DataTestSupport.encode
import app.piyokey.android.data.DataTestSupport.hash
import app.piyokey.android.data.DataTestSupport.userDeck
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.PiyoDeckImportError
import app.piyokey.core.domain.UserDeckDraft
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DeckLibraryTest {
  private val root = File(DataTestSupport.tempDir(), "InstalledDecks")
  private val store = DeckInstallationStore(root)
  private val failing = DeckSource { throw IllegalStateException("offline") }

  private fun TestScope.library(
    source: DeckSource = failing,
    downloads: MutableList<String>? = null,
    pro: () -> Boolean = { false },
    clock: () -> Instant = { at(1_000) },
  ): DeckLibrary {
    val dispatcher = StandardTestDispatcher(testScheduler)
    return DeckLibrary(
      source = source,
      store = store,
      scope = CoroutineScope(dispatcher),
      io = dispatcher,
      hasProAccess = pro,
      onDeckDownloaded = { deck, _ -> downloads?.add(deck.deckId) },
      clock = clock,
    )
  }

  private suspend fun DeckLibrary.installUser(id: String, version: Int, meaning: String, pro: Boolean, expected: Int? = null) {
    val data = encode(userDeck(id = id, version = version, meaning = meaning, updatedAt = at(100L + version)))
    installUserDeck(data, InstalledDeckSource.IMPORTED, hash(data), 1, false, pro, expectedCurrentVersion = expected)
  }

  @Test
  fun installsReloadsOfflineAndDetectsUpdates() = runTest {
    val entry = DataTestSupport.catalogEntry("official_keyboard_start")
    val downloads = mutableListOf<String>()
    val library = library(BundledDeckSource(DataTestSupport.assets), downloads)
    library.install(entry)
    assertTrue(library.isInstalled(entry.deckId))
    assertEquals(listOf(entry.deckId), downloads)
    assertEquals(entry.tags, library.downloadHistory.value[entry.deckId])
    assertTrue(library.installingDeckIds.value.isEmpty())

    val offline = library()
    assertEquals(entry.itemCount, offline.installedDeck(entry.deckId)?.items?.size)
    assertTrue(offline.needsUpdate(entry.copy(version = entry.version + 1)))
    assertFalse(offline.needsUpdate(entry))
    assertFalse(offline.needsUpdate(DataTestSupport.catalogEntry("official_daily_words")))
  }

  @Test
  fun failedInstallIsReportedAndRemoveUpdatesState() = runTest {
    val library = library(BundledDeckSource(DataTestSupport.assets))
    val entry = DataTestSupport.catalogEntry("official_keyboard_start")
    library.install(entry.copy(name = "mismatch"))
    assertTrue(entry.deckId in library.failedDeckIds.value)
    library.install(entry)
    assertFalse(entry.deckId in library.failedDeckIds.value)

    library.remove(entry.deckId)
    advanceUntilIdle()
    assertFalse(library.isInstalled(entry.deckId))
    assertTrue(library.installed.isEmpty())
    assertEquals(entry.tags, library.downloadHistory.value[entry.deckId])
  }

  @Test
  fun installedListIsOrderedByLastPlayedThenInstallDate() = runTest {
    var now = at(1_000)
    val library = library(BundledDeckSource(DataTestSupport.assets), clock = { now })
    val first = DataTestSupport.catalogEntry("official_keyboard_start")
    val second = DataTestSupport.catalogEntry("official_daily_words")
    library.install(first)
    now = at(2_000)
    library.install(second)
    assertEquals(listOf(second.deckId, first.deckId), library.installed.map { it.deckId })
    now = at(3_000)
    library.markPlayed(first.deckId)
    advanceUntilIdle()
    assertEquals(listOf(first.deckId, second.deckId), library.installedList.value.map { it.deckId })
    assertEquals(at(3_000), library.record(first.deckId)?.lastPlayedAt)
  }

  @Test
  fun installUserDeckAppliesExpectedVersionAndKeepsStateOnMismatch() = runTest {
    val library = library()
    val id = "user_0123456789abcdef0123456789abcdef"
    library.installUser(id, 1, "最初", pro = true)
    library.installUser(id, 2, "更新", pro = true, expected = 1)
    val indexBefore = File(root, "installed-decks.json").readBytes()

    val error = assertFailsWith<DeckInstallationStoreException.SourceChanged> { library.installUser(id, 3, "競合更新", pro = true, expected = 1) }
    assertEquals(2, error.actualVersion)
    assertEquals(2, library.installedDeck(id)?.version)
    assertEquals("更新", library.installedDeck(id)?.items?.get(0)?.meaningJa)
    assertContentEquals(indexBefore, File(root, "installed-decks.json").readBytes())
  }

  @Test
  fun invalidUserDeckIsRejectedBeforeWriting() = runTest {
    val library = library()
    val official = encode(userDeck(version = 1, meaning = "x", updatedAt = at(1)).copy(deckId = "official_fake", official = true))
    assertFailsWith<PiyoDeckImportError.InvalidUserDeck> {
      library.installUserDeck(official, InstalledDeckSource.IMPORTED, hash(official), 1, false, true)
    }
    assertFalse(root.exists())
  }

  @Test
  fun freeUserDeckLimitBlocksOnlyFourthNewDeckAndAllowsReplacement() = runTest {
    var pro = false
    val library = library(pro = { pro })
    for (index in 1..3) library.installUser("user_%032x".format(index), 1, "無料$index", pro = false)
    assertEquals(3, library.installedUserDeckCount)
    assertFalse(library.canInstallNewUserDeck(false))
    assertTrue(library.canInstallNewUserDeck(true))
    assertFalse(library.canInstallNewUserDeck())

    val fourth = "user_" + "f".repeat(32)
    assertFailsWith<PiyokeyProAccessException.FreeUserDeckLimitReached> { library.installUser(fourth, 1, "四つ目", pro = false) }
    library.installUser("user_%032x".format(1), 2, "無料置換", pro = false)
    assertEquals(2, library.installedDeck("user_%032x".format(1))?.version)

    pro = true
    assertTrue(library.canInstallNewUserDeck())
    library.installUser(fourth, 1, "四つ目", pro = true)
    assertEquals(4, library.installedUserDeckCount)
  }

  @Test
  fun proAllowsManyUserDecksAndEditedDeckSurvivesReload() = runTest {
    val library = library()
    for (index in 1..20) library.installUser("user_%032x".format(index), 1, "Pro$index", pro = true)
    val reloaded = library()
    assertEquals(20, reloaded.installedUserDeckCount)
    for (index in 1..20) assertEquals("Pro$index", reloaded.installedDeck("user_%032x".format(index))?.items?.get(0)?.meaningJa)

    val original = userDeck(version = 1, meaning = "編集前", updatedAt = at(200))
    val originalData = encode(original)
    library.installUserDeck(originalData, InstalledDeckSource.CREATED, hash(originalData), 1, true, true)
    val edited = UserDeckDraft.editing(original)
      .copy(name = "保存済みProデッキ")
      .updatingItem(0) { it.copy(meaningJa = "編集後") }
      .addingItem { "2".repeat(32) }
      .updatingItem(1) { it.copy(ko = "학교", readingJa = "ハッキョ", meaningJa = "学校") }
      .validatedDeck(at(300), "ja")
    val editedData = encode(edited)
    library.installUserDeck(editedData, InstalledDeckSource.CREATED, hash(editedData), 1, true, true, expectedCurrentVersion = 1)

    val persisted = library().installedDeck(original.deckId)!!
    assertEquals(2, persisted.version)
    assertEquals("保存済みProデッキ", persisted.name)
    assertEquals(listOf(original.items[0].id, "item_" + "2".repeat(32)), persisted.items.map { it.id })
    assertEquals(listOf("編集後", "学校"), persisted.items.map { it.meaningJa })
    assertEquals(true, library().record(original.deckId)?.isLocallyModified)
    assertContentEquals(editedData, library.exportData(original.deckId))
  }

  @Test
  fun staticSourceFallsBackToRemoteOnlyWhenBundledPayloadIsMissing() = runTest {
    val entry: CatalogDeck = DataTestSupport.catalogEntry("official_keyboard_start")
    val bundled = BundledDeckSource(DataTestSupport.assets)
    val noRemote = StaticDeckSource(bundled, app.piyokey.android.data.catalog.StaticContentConfiguration(null)) { error("no network") }
    assertEquals(InstalledDeckSource.BUNDLE, noRemote.fetch(entry).source)
    assertFailsWith<DeckSourceException.MissingResource> { noRemote.fetch(entry.copy(fileUrl = "decks/missing.json")) }
  }
}
