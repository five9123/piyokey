package app.piyokey.core.data

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StaticContentResponse(
  val statusCode: Int,
  val body: ByteArray?,
  val etag: String?,
  val lastModified: String?,
)

interface StaticContentClient {
  suspend fun get(
    url: URL,
    etag: String? = null,
    lastModified: String? = null,
  ): StaticContentResponse
}

class HttpStaticContentClient : StaticContentClient {
  override suspend fun get(
    url: URL,
    etag: String?,
    lastModified: String?,
  ): StaticContentResponse = withContext(Dispatchers.IO) {
    require(url.protocol == "https") { "Only HTTPS static content is allowed" }
    val connection = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 8_000
      readTimeout = 12_000
      useCaches = false
      setRequestProperty("Accept", "application/json")
      etag?.let { setRequestProperty("If-None-Match", it) }
      lastModified?.let { setRequestProperty("If-Modified-Since", it) }
    }
    try {
      val status = connection.responseCode
      StaticContentResponse(
        statusCode = status,
        body = if (status == HttpURLConnection.HTTP_OK) {
          connection.inputStream.use { it.readBytes() }
        } else {
          null
        },
        etag = connection.getHeaderField("ETag"),
        lastModified = connection.getHeaderField("Last-Modified"),
      )
    } finally {
      connection.disconnect()
    }
  }
}

object StaticContentUrlPolicy {
  fun parseCatalogUrl(rawValue: String?): URL? {
    if (rawValue.isNullOrBlank()) return null
    val uri = URI(rawValue).normalize()
    require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
      "Catalog URL must be an absolute HTTPS URL"
    }
    require(uri.query == null && uri.fragment == null) {
      "Catalog URL cannot contain a query or fragment"
    }
    return uri.toURL()
  }

  fun resolveDeckUrl(catalogUrl: URL, relativePath: String): URL {
    require(relativePath.startsWith("decks/") && !relativePath.contains("..")) {
      "Deck path must stay below decks/"
    }
    val catalogUri = catalogUrl.toURI()
    val contentRoot = catalogUri.resolve(".")
    val resolved = contentRoot.resolve(relativePath).normalize()
    require(
      resolved.scheme == contentRoot.scheme &&
        resolved.host == contentRoot.host &&
        effectivePort(resolved) == effectivePort(contentRoot) &&
        resolved.path.startsWith(contentRoot.path) &&
        resolved.query == null &&
        resolved.fragment == null,
    ) { "Deck URL escaped the configured static content root" }
    return resolved.toURL()
  }

  private fun effectivePort(uri: URI): Int = when {
    uri.port >= 0 -> uri.port
    uri.scheme == "https" -> 443
    else -> -1
  }
}
