package app.piyokey.android.data.catalog

import app.piyokey.android.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional HTTPS static catalog (iOS `StaticContentConfiguration`). The URL comes from
 * `BuildConfig.CATALOG_URL` (`PIYOKEY_CATALOG_URL`); empty/invalid → bundled content only.
 */
data class StaticContentConfiguration(val catalogUrl: URI?) {
  /** Safe `decks/<file>.json` URL next to the catalog, or `null` for anything suspicious. */
  fun deckUrl(relativePath: String): URI? {
    val catalog = catalogUrl ?: return null
    if (listOf("..", "%", "\\", "?", "#").any { it in relativePath }) return null
    if (!relativePath.startsWith("decks/") || !relativePath.endsWith(".json")) return null
    if (relativePath.split('/').filter { it.isNotEmpty() }.size != 2) return null
    val resolved = runCatching { catalog.resolve(relativePath).normalize() }.getOrNull() ?: return null
    if (resolved.scheme != catalog.scheme || resolved.host != catalog.host || resolved.port != catalog.port) return null
    val catalogPath = catalog.normalize().path ?: return null
    val contentRoot = catalogPath.substringBeforeLast('/', "")
    val path = resolved.path ?: return null
    if (!path.startsWith("$contentRoot/")) return null
    return resolved
  }

  companion object {
    val live: StaticContentConfiguration by lazy { StaticContentConfiguration(validatedHttpsUrl(BuildConfig.CATALOG_URL)) }

    fun validatedHttpsUrl(raw: String?): URI? {
      val value = raw?.trim().orEmpty()
      if (value.isEmpty() || value.contains("$(")) return null
      val uri = runCatching { URI(value) }.getOrNull() ?: return null
      if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty()) return null
      return uri
    }
  }
}

data class HttpRequest(
  val url: URI,
  val headers: Map<String, String> = emptyMap(),
  val timeoutMillis: Int = 30_000,
)

class HttpResponse(val statusCode: Int, headers: Map<String, String>, val body: ByteArray) {
  private val headers = headers.mapKeys { it.key.lowercase() }

  /** Case-insensitive header lookup. */
  fun header(name: String): String? = headers[name.lowercase()]
}

/** Swappable transport (tests stub it). Implementations must not run network IO on the caller thread. */
fun interface HttpDataClient {
  suspend fun execute(request: HttpRequest): HttpResponse
}

/** `HttpURLConnection` GET without HTTP caching (iOS `.reloadIgnoringLocalCacheData`). */
class UrlConnectionHttpDataClient(private val maximumBodyBytes: Int = 16 * 1024 * 1024) : HttpDataClient {
  override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
    val connection = request.url.toURL().openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "GET"
      connection.useCaches = false
      connection.instanceFollowRedirects = true
      connection.connectTimeout = request.timeoutMillis
      connection.readTimeout = request.timeoutMillis
      request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
      val status = connection.responseCode
      val headers = connection.headerFields.orEmpty()
        .filterKeys { it != null }
        .mapNotNull { (name, values) -> values?.lastOrNull()?.let { name to it } }
        .toMap()
      val stream = if (status >= 400) connection.errorStream else connection.inputStream
      val body = stream?.use { readLimited(it) } ?: ByteArray(0)
      HttpResponse(status, headers, body)
    } finally {
      connection.disconnect()
    }
  }

  private fun readLimited(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      output.write(buffer, 0, count)
      if (output.size() > maximumBodyBytes) throw IOException("Response is too large")
    }
    return output.toByteArray()
  }
}

data class CatalogHttpValidators(val etag: String?, val lastModified: String?)

sealed interface CatalogHttpResult {
  data class Modified(val data: ByteArray, val validators: CatalogHttpValidators) : CatalogHttpResult
  data class NotModified(val validators: CatalogHttpValidators) : CatalogHttpResult
}

sealed class StaticContentHttpException(message: String) : Exception(message) {
  data class StatusCode(val code: Int) : StaticContentHttpException("HTTP $code")
  data class UnsafeDeckUrl(val path: String) : StaticContentHttpException("Unsafe deck path $path")
}

/** Conditional catalog GET: `If-None-Match` / `If-Modified-Since`; 200 → modified, 304 → not modified. */
class CatalogHttpClient(private val client: HttpDataClient = UrlConnectionHttpDataClient()) {
  suspend fun fetchCatalog(url: URI, validators: CatalogHttpValidators?): CatalogHttpResult {
    val headers = buildMap {
      put("Accept", "application/json")
      validators?.etag?.let { put("If-None-Match", it) }
      validators?.lastModified?.let { put("If-Modified-Since", it) }
    }
    val response = client.execute(HttpRequest(url, headers, timeoutMillis = 15_000))
    val responseValidators = CatalogHttpValidators(
      etag = response.header("ETag") ?: validators?.etag,
      lastModified = response.header("Last-Modified") ?: validators?.lastModified,
    )
    return when (response.statusCode) {
      200 -> CatalogHttpResult.Modified(response.body, responseValidators)
      304 -> CatalogHttpResult.NotModified(responseValidators)
      else -> throw StaticContentHttpException.StatusCode(response.statusCode)
    }
  }
}
