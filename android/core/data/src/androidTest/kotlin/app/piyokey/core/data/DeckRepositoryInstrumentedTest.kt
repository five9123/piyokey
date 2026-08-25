package app.piyokey.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.URL
import app.piyokey.core.game.FlowGameRecord
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckRepositoryInstrumentedTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var database: PiyokeyDatabase
  private lateinit var repository: DeckRepository
  private var now = 1_000L

  @Before
  fun setUp() {
    File(context.filesDir, "piyokey/content").deleteRecursively()
    database = Room.inMemoryDatabaseBuilder(context, PiyokeyDatabase::class.java).build()
    repository = DeckRepository.createForTesting(context, database, clock = { now++ })
  }

  @After
  fun tearDown() {
    database.close()
    File(context.filesDir, "piyokey/content").deleteRecursively()
  }

  @Test
  fun bundledCatalogInstallOfflineReadAndDeletePreserveHistory() = runTest {
    val initial = repository.snapshot()
    assertEquals(26, initial.catalog.decks.size)
    assertTrue(initial.installed.isEmpty())

    val entry = initial.catalog.decks.first { it.deckId == "official_daily_words" }
    val installed = repository.install(entry)
    assertEquals(entry.deckId, installed.deck.deckId)
    assertEquals(entry.itemCount, installed.deck.items.size)

    val offline = repository.snapshot()
    assertEquals(setOf(entry.deckId), offline.installedDeckIds)
    assertTrue(offline.downloadHistoryTags.flatten().containsAll(entry.tags))

    repository.delete(entry.deckId)
    val deleted = repository.snapshot()
    assertFalse(entry.deckId in deleted.installedDeckIds)
    assertTrue(deleted.downloadHistoryTags.flatten().containsAll(entry.tags))
  }

  @Test
  fun corruptedInstalledPayloadIsQuarantinedWithoutBreakingCatalog() = runTest {
    val initial = repository.snapshot()
    val entry = initial.catalog.decks.first()
    val installed = repository.install(entry)
    File(context.filesDir, "piyokey/content/${installed.metadata.payloadName}").writeText("broken")

    val recovered = repository.snapshot()
    assertEquals(26, recovered.catalog.decks.size)
    assertTrue(recovered.installed.isEmpty())
    assertTrue(File(context.filesDir, "piyokey/content/quarantine").listFiles().orEmpty().isNotEmpty())
  }

  @Test
  fun corruptedInstalledPayloadRollsBackToLastVerifiedBackup() = runTest {
    val entry = repository.snapshot().catalog.decks.first()
    repository.install(entry)
    val updated = repository.install(entry)
    File(context.filesDir, "piyokey/content/${updated.metadata.payloadName}").writeText("broken")

    val recovered = repository.snapshot()
    assertEquals(setOf(entry.deckId), recovered.installedDeckIds)
    assertEquals(entry.version, recovered.installed.single().metadata.version)
    assertEquals("recovered", recovered.installed.single().metadata.source)
  }

  @Test
  fun conditionalCatalogRefreshPersistsValidatedV11AndUsesValidators() = runTest {
    val updateBytes = context.assets.open("updates/catalog.json").use { it.readBytes() }
    val requests = mutableListOf<Pair<String?, String?>>()
    var requestCount = 0
    val client = object : StaticContentClient {
      override suspend fun get(
        url: URL,
        etag: String?,
        lastModified: String?,
      ): StaticContentResponse {
        requests += etag to lastModified
        return if (requestCount++ == 0) {
          StaticContentResponse(200, updateBytes, "\"catalog-v11\"", "Sat, 22 Aug 2026 00:00:00 GMT")
        } else {
          StaticContentResponse(304, null, "\"catalog-v11\"", "Sat, 22 Aug 2026 00:00:00 GMT")
        }
      }
    }
    repository = DeckRepository.createForTesting(
      context = context,
      database = database,
      remoteClient = client,
      catalogUrl = URL("https://cdn.example.com/piyokey/catalog.json"),
      clock = { now++ },
    )

    assertEquals(CatalogRefreshResult.Updated(11), repository.refreshCatalog())
    assertEquals(11, repository.snapshot().catalog.catalogVersion)
    assertEquals(CatalogRefreshResult.NotModified, repository.refreshCatalog())
    assertEquals(null to null, requests.first())
    assertEquals(
      "\"catalog-v11\"" to "Sat, 22 Aug 2026 00:00:00 GMT",
      requests.last(),
    )
  }

  @Test
  fun corruptedCatalogCacheRollsBackToLastVerifiedCache() = runTest {
    val baseBytes = context.assets.open("catalog.json").use { it.readBytes() }
    val updateBytes = context.assets.open("updates/catalog.json").use { it.readBytes() }
    var requestCount = 0
    val client = object : StaticContentClient {
      override suspend fun get(
        url: URL,
        etag: String?,
        lastModified: String?,
      ): StaticContentResponse = when (requestCount++) {
        0 -> StaticContentResponse(200, baseBytes, "\"v10\"", "Fri, 21 Aug 2026 00:00:00 GMT")
        else -> StaticContentResponse(200, updateBytes, "\"v11\"", "Sat, 22 Aug 2026 00:00:00 GMT")
      }
    }
    repository = DeckRepository.createForTesting(
      context,
      database,
      client,
      URL("https://cdn.example.com/piyokey/catalog.json"),
      clock = { now++ },
    )
    assertEquals(CatalogRefreshResult.Updated(10), repository.refreshCatalog())
    assertEquals(CatalogRefreshResult.Updated(11), repository.refreshCatalog())
    val current = requireNotNull(database.dao().catalogState())
    File(context.filesDir, "piyokey/content/${current.payloadName}").writeText("broken")

    assertEquals(10, repository.snapshot().catalog.catalogVersion)
    assertEquals(10, database.dao().catalogState()?.catalogVersion)
  }

  @Test
  fun bundledFlowPresetsContainThreeDisjointOneHundredWordCourses() = runTest {
    val presets = repository.bundledFlowDecks()
    assertEquals(
      listOf("flow_topik_beginner", "flow_topik_intermediate", "flow_topik_advanced"),
      presets.map { it.deckId },
    )
    assertTrue(presets.all { it.items.size == 100 })
    assertTrue(presets.all { deck -> deck.items.map { it.ko }.distinct().size == 100 })
    assertEquals(300, presets.flatMap { deck -> deck.items.map { it.ko } }.distinct().size)
    assertEquals("S", repository.flowRankTuning().rank(100.0, 120.0))
  }

  @Test
  fun gameRecordsAreAppendOnlyWhileDeckProgressKeepsBestAndPlayCount() = runTest {
    val first = sampleFlowRecord(score = 800, accuracy = 92.5, playedAt = 2_000)
    val second = sampleFlowRecord(score = 500, accuracy = 98.0, playedAt = 3_000)

    assertTrue(repository.saveFlowRecord(first).isNewBest)
    val saved = repository.saveFlowRecord(second)

    assertFalse(saved.isNewBest)
    assertEquals(2, saved.progress.plays)
    assertEquals(800, saved.progress.bestScore)
    assertEquals(98.0, saved.progress.bestAccuracyPercent, 0.001)
    assertEquals(2, database.dao().gameRecords(first.deckId, "flow", "builtin").size)
  }

  private fun sampleFlowRecord(score: Int, accuracy: Double, playedAt: Long) = FlowGameRecord(
    deckId = "flow_topik_beginner",
    course = "beginner",
    score = score,
    maxCombo = 7,
    accuracyPercent = accuracy,
    correctJamoCount = 20,
    charactersPerMinute = 64.0,
    mistakeCount = 2,
    completedItemCount = 5,
    missedItemCount = 1,
    playDurationMillis = 60_000,
    playedAtEpochMillis = playedAt,
  )
}
