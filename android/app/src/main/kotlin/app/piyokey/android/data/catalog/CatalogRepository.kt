package app.piyokey.android.data.catalog

import app.piyokey.android.data.persistence.AssetSource
import app.piyokey.android.data.persistence.Base64ByteArraySerializer
import app.piyokey.android.data.persistence.BundledAssetPaths
import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.android.data.persistence.StoreJson
import app.piyokey.android.data.persistence.deleteTree
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogValidator
import app.piyokey.core.deckkit.ContentValidationIssue
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.Iso8601InstantSerializer
import java.io.File
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `CatalogCache/catalog-cache.json` (iOS `CatalogCacheRecord`, schema 1). */
@Serializable
class CatalogCacheRecord(
  @SerialName("schema_version") val schemaVersion: Int,
  @SerialName("catalog_version") val catalogVersion: Int,
  @SerialName("fetched_at") @Serializable(with = Iso8601InstantSerializer::class) val fetchedAt: Instant,
  val etag: String? = null,
  @SerialName("last_modified") val lastModified: String? = null,
  /** Exact validated catalog bytes (base64 in JSON, like Swift `Data`). */
  @SerialName("json_blob") @Serializable(with = Base64ByteArraySerializer::class) val jsonBlob: ByteArray,
) {
  override fun equals(other: Any?): Boolean = other is CatalogCacheRecord &&
    schemaVersion == other.schemaVersion && catalogVersion == other.catalogVersion && fetchedAt == other.fetchedAt &&
    etag == other.etag && lastModified == other.lastModified && jsonBlob.contentEquals(other.jsonBlob)

  override fun hashCode(): Int = listOf(schemaVersion, catalogVersion, fetchedAt, etag, lastModified, jsonBlob.contentHashCode()).hashCode()
}

data class CatalogCacheSnapshot(val record: CatalogCacheRecord, val catalog: Catalog)

sealed class CatalogCacheStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) : CatalogCacheStoreException("Unsupported catalog cache schema $version")
  data object CatalogVersionMismatch : CatalogCacheStoreException("Catalog version mismatch")
  data class InvalidCatalog(val issues: List<ContentValidationIssue>) : CatalogCacheStoreException("Invalid catalog: $issues")
}

/** Last validated remote catalog with its HTTP validators. Blocking file IO. */
class CatalogCacheStore(val file: File) {
  fun load(): CatalogCacheSnapshot? = RecoverableJsonFile.load(
    file,
    shouldRecover = { it !is CatalogCacheStoreException.UnsupportedSchema },
    decode = ::decodeSnapshot,
  )

  fun save(data: ByteArray, validators: CatalogHttpValidators, fetchedAt: Instant = Instant.now()): CatalogCacheSnapshot {
    val catalog = validatedCatalog(data)
    val record = CatalogCacheRecord(
      schemaVersion = CURRENT_SCHEMA_VERSION,
      catalogVersion = catalog.catalogVersion,
      fetchedAt = fetchedAt,
      etag = validators.etag,
      lastModified = validators.lastModified,
      jsonBlob = data,
    )
    RecoverableJsonFile.write(StoreJson.encode(CatalogCacheRecord.serializer(), record), file)
    return CatalogCacheSnapshot(record, catalog)
  }

  fun reset() = file.absoluteFile.parentFile?.deleteTree()

  private fun decodeSnapshot(data: ByteArray): CatalogCacheSnapshot {
    val record = StoreJson.decode(CatalogCacheRecord.serializer(), data)
    if (record.schemaVersion != CURRENT_SCHEMA_VERSION) throw CatalogCacheStoreException.UnsupportedSchema(record.schemaVersion)
    val catalog = validatedCatalog(record.jsonBlob)
    if (record.catalogVersion != catalog.catalogVersion) throw CatalogCacheStoreException.CatalogVersionMismatch
    return CatalogCacheSnapshot(record, catalog)
  }

  companion object {
    const val CURRENT_SCHEMA_VERSION = 1

    fun validatedCatalog(data: ByteArray): Catalog {
      val catalog = DeckJson.decodeCatalog(data)
      val issues = CatalogValidator.validate(catalog)
      if (issues.isNotEmpty()) throw CatalogCacheStoreException.InvalidCatalog(issues)
      return catalog
    }
  }
}

sealed class CatalogRepositoryException(message: String) : Exception(message) {
  data object MissingBundledCatalog : CatalogRepositoryException("Missing bundled catalog.json")
  data class InvalidCatalog(val issues: List<ContentValidationIssue>) : CatalogRepositoryException("Invalid catalog: $issues")
  data object NotModifiedWithoutCache : CatalogRepositoryException("304 without a cached catalog")
}

interface CatalogRepository {
  /** Synchronous best local catalog (bundled or newer cache). */
  fun loadCatalog(): Catalog

  /** Remote refresh; `null` when there is nothing new (or no remote configured). */
  suspend fun refreshCatalog(): Catalog?
}

class BundleCatalogRepository(private val assets: AssetSource) : CatalogRepository {
  override fun loadCatalog(): Catalog {
    val catalog = DeckJson.decodeCatalog(loadData())
    val issues = CatalogValidator.validate(catalog)
    if (issues.isNotEmpty()) throw CatalogRepositoryException.InvalidCatalog(issues)
    return catalog
  }

  override suspend fun refreshCatalog(): Catalog? = null

  fun loadData(): ByteArray = assets.read(BundledAssetPaths.CATALOG) ?: throw CatalogRepositoryException.MissingBundledCatalog
}

/**
 * Bundled catalog + optional remote HTTPS catalog with ETag/If-Modified-Since and a persistent
 * cache. The cache wins only when its `catalog_version` ≥ the bundled one (an app update with a
 * newer bundle replaces an older cache).
 */
class CachedRemoteCatalogRepository(
  private val bundled: BundleCatalogRepository,
  private val cache: CatalogCacheStore,
  private val client: CatalogHttpClient = CatalogHttpClient(),
  private val catalogUrl: URI? = StaticContentConfiguration.live.catalogUrl,
  private val now: () -> Instant = Instant::now,
) : CatalogRepository {
  override fun loadCatalog(): Catalog {
    val bundledCatalog = bundled.loadCatalog()
    val cached = runCatching { cache.load() }.getOrNull() ?: return bundledCatalog
    return if (cached.catalog.catalogVersion >= bundledCatalog.catalogVersion) cached.catalog else bundledCatalog
  }

  override suspend fun refreshCatalog(): Catalog? {
    val url = catalogUrl ?: return null
    val cached = runCatching { cache.load() }.getOrNull()
    val validators = cached?.let { CatalogHttpValidators(it.record.etag, it.record.lastModified) }
    return when (val response = client.fetchCatalog(url, validators)) {
      is CatalogHttpResult.Modified -> cache.save(response.data, response.validators, now()).catalog
      is CatalogHttpResult.NotModified -> {
        cached ?: throw CatalogRepositoryException.NotModifiedWithoutCache
        cache.save(cached.record.jsonBlob, response.validators, now())
        null
      }
    }
  }
}

/**
 * Shared catalog state for Home/Discover (mirrors iOS `DiscoverViewModel.loadIfNeeded`): load the
 * local catalog synchronously, then refresh remotely in the background. A refreshed catalog is
 * applied only when its `catalog_version` is not older than the one shown.
 */
class CatalogLibrary(private val repository: CatalogRepository, private val scope: CoroutineScope) {
  enum class LoadState { IDLE, LOADED, FAILED }

  private val catalogState = MutableStateFlow<Catalog?>(null)
  private val loadStateFlow = MutableStateFlow(LoadState.IDLE)
  private var refreshJob: Job? = null

  val catalog: StateFlow<Catalog?> = catalogState.asStateFlow()
  val loadState: StateFlow<LoadState> = loadStateFlow.asStateFlow()

  fun loadIfNeeded(): Job? {
    if (loadStateFlow.value != LoadState.IDLE) return refreshJob
    try {
      catalogState.value = repository.loadCatalog()
      loadStateFlow.value = LoadState.LOADED
    } catch (_: Exception) {
      catalogState.value = null
      loadStateFlow.value = LoadState.FAILED
    }
    val job = scope.launch {
      try {
        val refreshed = repository.refreshCatalog()
        val current = catalogState.value
        if (refreshed != null && (current == null || refreshed.catalogVersion >= current.catalogVersion)) {
          catalogState.value = refreshed
          loadStateFlow.value = LoadState.LOADED
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Exception) {
        if (catalogState.value == null) loadStateFlow.value = LoadState.FAILED
      }
    }
    refreshJob = job
    return job
  }

  fun retry(): Job? {
    refreshJob?.cancel()
    refreshJob = null
    loadStateFlow.value = LoadState.IDLE
    return loadIfNeeded()
  }
}
