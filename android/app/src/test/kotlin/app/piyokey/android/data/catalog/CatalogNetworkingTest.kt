package app.piyokey.android.data.catalog

import app.piyokey.android.data.DataTestSupport
import app.piyokey.android.data.DataTestSupport.at
import app.piyokey.android.data.decks.BundledDeckSource
import app.piyokey.android.data.decks.DeckInstallationStore
import app.piyokey.android.data.decks.DeckLibrary
import app.piyokey.android.data.decks.InstalledDeckSource
import app.piyokey.android.data.decks.RemoteDeckSource
import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.core.deckkit.CatalogValidator
import app.piyokey.core.deckkit.DeckJson
import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogNetworkingTest {
  private val directory = DataTestSupport.tempDir()
  private val cache = CatalogCacheStore(File(directory, "catalog-cache.json"))
  private val catalogData = DataTestSupport.bundledRepository.loadData()
  private val catalogUrl = URI("https://static.example/hanco/catalog.json")

  private class StubClient(private val handler: (HttpRequest) -> HttpResponse) : HttpDataClient {
    val requests = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse {
      requests += request
      return handler(request)
    }
  }

  @Test
  fun cacheRoundTripsValidatedJsonAndRejectsUnknownSchema() {
    val saved = cache.save(catalogData, CatalogHttpValidators("\"catalog-v1\"", "Fri, 17 Jul 2026 00:00:00 GMT"), at(1_700_000_000))
    assertEquals(26, saved.catalog.decks.size)
    assertEquals(saved, cache.load())

    val edited = Json.parseToJsonElement(cache.file.readText()).jsonObject.toMutableMap()
    edited["schema_version"] = JsonPrimitive(999)
    cache.file.writeText(JsonObject(edited).toString())
    assertEquals(999, assertFailsWith<CatalogCacheStoreException.UnsupportedSchema> { cache.load() }.version)
    assertFailsWith<CatalogCacheStoreException.InvalidCatalog> {
      cache.save("""{"catalog_version":1,"generated_at":"2026-01-01T00:00:00Z","decks":[],"tags":[{"tag":"x","deck_count":5,"category":"c"}]}""".toByteArray(), CatalogHttpValidators(null, null))
    }
  }

  @Test
  fun corruptCacheRestoresLastValidatedCatalog() {
    cache.save(catalogData, CatalogHttpValidators("\"cached\"", null))
    cache.file.writeText("broken")
    val recovered = assertNotNull(cache.load())
    assertEquals(26, recovered.catalog.decks.size)
    assertEquals("\"cached\"", recovered.record.etag)
    assertTrue(RecoverableJsonFile.corruptFile(cache.file).exists())
  }

  @Test
  fun remoteRefreshPersistsCatalogAndSendsConditionalHeadersFor304() = runTest {
    val first = StubClient { HttpResponse(200, mapOf("ETag" to "\"catalog-v1\"", "Last-Modified" to "Fri, 17 Jul 2026 00:00:00 GMT"), catalogData) }
    val refreshed = CachedRemoteCatalogRepository(DataTestSupport.bundledRepository, cache, CatalogHttpClient(first), catalogUrl) { at(1_700_000_000) }
      .refreshCatalog()
    assertEquals(26, refreshed?.decks?.size)
    assertEquals("\"catalog-v1\"", cache.load()?.record?.etag)
    assertEquals("application/json", first.requests.single().headers["Accept"])

    val validation = StubClient { HttpResponse(304, emptyMap(), ByteArray(0)) }
    val result = CachedRemoteCatalogRepository(DataTestSupport.bundledRepository, cache, CatalogHttpClient(validation), catalogUrl) { at(1_700_000_600) }
      .refreshCatalog()
    assertNull(result)
    val request = validation.requests.single()
    assertEquals("\"catalog-v1\"", request.headers["If-None-Match"])
    assertEquals("Fri, 17 Jul 2026 00:00:00 GMT", request.headers["If-Modified-Since"])
    assertEquals(at(1_700_000_600), cache.load()?.record?.fetchedAt)
  }

  @Test
  fun notModifiedWithoutCacheAndServerErrorsSurface() = runTest {
    val notModified = CachedRemoteCatalogRepository(
      DataTestSupport.bundledRepository, cache, CatalogHttpClient { HttpResponse(304, emptyMap(), ByteArray(0)) }, catalogUrl,
    )
    assertFailsWith<CatalogRepositoryException.NotModifiedWithoutCache> { notModified.refreshCatalog() }
    val failing = CachedRemoteCatalogRepository(
      DataTestSupport.bundledRepository, cache, CatalogHttpClient { HttpResponse(503, emptyMap(), ByteArray(0)) }, catalogUrl,
    )
    assertEquals(503, assertFailsWith<StaticContentHttpException.StatusCode> { failing.refreshCatalog() }.code)
    assertNull(CachedRemoteCatalogRepository(DataTestSupport.bundledRepository, cache, catalogUrl = null).refreshCatalog())
  }

  @Test
  fun cachedCatalogKeepsLibraryLoadedWhenServerFails() = runTest {
    cache.save(catalogData, CatalogHttpValidators("\"cached\"", null))
    val repository = CachedRemoteCatalogRepository(
      DataTestSupport.bundledRepository, cache, CatalogHttpClient { HttpResponse(503, emptyMap(), ByteArray(0)) }, catalogUrl,
    )
    val library = CatalogLibrary(repository, CoroutineScope(StandardTestDispatcher(testScheduler)))
    library.loadIfNeeded()?.join()
    assertEquals(CatalogLibrary.LoadState.LOADED, library.loadState.value)
    assertEquals(26, library.catalog.value?.decks?.size)
  }

  @Test
  fun newerBundledCatalogReplacesOlderPersistentCache() {
    val bundled = DataTestSupport.bundledRepository.loadCatalog()
    val older = Json.parseToJsonElement(String(catalogData)).jsonObject.toMutableMap()
    older["catalog_version"] = JsonPrimitive(bundled.catalogVersion - 1)
    cache.save(JsonObject(older).toString().toByteArray(), CatalogHttpValidators("\"older\"", null))
    val repository = CachedRemoteCatalogRepository(DataTestSupport.bundledRepository, cache, catalogUrl = null)
    assertEquals(bundled.catalogVersion, repository.loadCatalog().catalogVersion)

    val newer = older.also { it["catalog_version"] = JsonPrimitive(bundled.catalogVersion + 1) }
    cache.save(JsonObject(newer).toString().toByteArray(), CatalogHttpValidators("\"newer\"", null))
    assertEquals(bundled.catalogVersion + 1, repository.loadCatalog().catalogVersion)
  }

  @Test
  fun deckUrlResolvesOnlySafeRelativePathsAndConfigurationValidatesHttps() {
    val configuration = StaticContentConfiguration(catalogUrl)
    assertEquals("https://static.example/hanco/decks/official_daily_words_v5.json", configuration.deckUrl("decks/official_daily_words_v5.json").toString())
    assertNull(configuration.deckUrl("decks/%2E%2E/secret.json"))
    assertNull(configuration.deckUrl("decks/valid.json?redirect=https://evil.example"))
    assertNull(configuration.deckUrl("decks/../secret.json"))
    assertNull(configuration.deckUrl("decks/flow/nested.json"))
    assertNull(configuration.deckUrl("other/x.json"))
    assertNull(StaticContentConfiguration(null).deckUrl("decks/x.json"))
    assertNull(StaticContentConfiguration.validatedHttpsUrl(""))
    assertNull(StaticContentConfiguration.validatedHttpsUrl("http://example.org/catalog.json"))
    assertNull(StaticContentConfiguration.validatedHttpsUrl("$(PIYOKEY_CATALOG_URL)"))
    assertEquals(catalogUrl, StaticContentConfiguration.validatedHttpsUrl(" https://static.example/hanco/catalog.json "))
  }

  @Test
  fun remoteDeckSourceValidatesPayloadAndNewerFixtureDrivesOneTapUpdate() = runTest {
    val entry = DataTestSupport.catalogEntry("official_daily_words")
    val bundledData = DataTestSupport.assets.read("catalog/${entry.fileUrl}")!!
    val client = StubClient { HttpResponse(200, emptyMap(), bundledData) }
    val source = RemoteDeckSource(StaticContentConfiguration(catalogUrl), client)
    val payload = source.fetch(entry)
    assertEquals(entry.deckId, payload.deck.deckId)
    assertEquals(InstalledDeckSource.REMOTE, payload.source)
    assertEquals("https://static.example/hanco/${entry.fileUrl}", client.requests.single().url.toString())
    assertEquals("application/json", client.requests.single().headers["Accept"])

    val store = DeckInstallationStore(File(directory, "installed-decks"))
    store.install(BundledDeckSource(DataTestSupport.assets).fetch(entry).data, InstalledDeckSource.REMOTE)
    val updateCatalog = DeckJson.decodeCatalog(DataTestSupport.assets.read("catalog/updates/catalog.json")!!)
    assertTrue(CatalogValidator.validate(updateCatalog).isEmpty())
    val updateEntry = updateCatalog.decks.first { it.deckId == entry.deckId }
    val updateData = DataTestSupport.assets.read("catalog/updates/${updateEntry.fileUrl}")!!
    val dispatcher = StandardTestDispatcher(testScheduler)
    val library = DeckLibrary(
      RemoteDeckSource(StaticContentConfiguration(catalogUrl)) { HttpResponse(200, emptyMap(), updateData) },
      store, CoroutineScope(dispatcher), dispatcher,
    )
    assertEquals(entry.version + 1, updateEntry.version)
    assertTrue(library.needsUpdate(updateEntry))
    library.install(updateEntry)
    assertEquals(updateEntry.version, library.installedDeck(entry.deckId)?.version)
    assertEquals(13, library.installedDeck(entry.deckId)?.items?.size)
    assertEquals(InstalledDeckSource.REMOTE, library.records.value[entry.deckId]?.source)
    assertFalse(library.needsUpdate(updateEntry))
  }
}
