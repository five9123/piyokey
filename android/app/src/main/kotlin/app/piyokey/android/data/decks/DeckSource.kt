package app.piyokey.android.data.decks

import app.piyokey.android.data.catalog.HttpDataClient
import app.piyokey.android.data.catalog.HttpRequest
import app.piyokey.android.data.catalog.StaticContentConfiguration
import app.piyokey.android.data.catalog.StaticContentHttpException
import app.piyokey.android.data.catalog.UrlConnectionHttpDataClient
import app.piyokey.android.data.persistence.AssetSource
import app.piyokey.android.data.persistence.BundledAssetPaths
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.ContentValidationIssue
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.DeckValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeckDownloadPayload(val data: ByteArray, val deck: Deck, val source: InstalledDeckSource)

/** Where catalog deck payloads come from (iOS `DeckSource`). */
fun interface DeckSource {
  suspend fun fetch(entry: CatalogDeck): DeckDownloadPayload
}

sealed class DeckSourceException(message: String) : Exception(message) {
  data class MissingResource(val path: String) : DeckSourceException("Missing $path")
  data class UnsafePath(val path: String) : DeckSourceException("Unsafe path $path")
  data class InvalidDeck(val issues: List<ContentValidationIssue>) : DeckSourceException("Invalid deck: $issues")
  data object MetadataMismatch : DeckSourceException("Deck payload does not match its catalog entry")
}

/** App assets (`catalog/decks/…`). Official → `bundle`, otherwise `remote` (mock community decks). */
class BundledDeckSource(private val assets: AssetSource) : DeckSource {
  override suspend fun fetch(entry: CatalogDeck): DeckDownloadPayload = withContext(Dispatchers.IO) {
    if (entry.fileUrl.contains("..") || !entry.fileUrl.startsWith("decks/")) throw DeckSourceException.UnsafePath(entry.fileUrl)
    val data = assets.read(BundledAssetPaths.catalogFile(entry.fileUrl)) ?: throw DeckSourceException.MissingResource(entry.fileUrl)
    validatedPayload(data, entry, if (entry.official) InstalledDeckSource.BUNDLE else InstalledDeckSource.REMOTE)
  }
}

/** Static HTTPS content next to the remote catalog. */
class RemoteDeckSource(
  val configuration: StaticContentConfiguration,
  private val client: HttpDataClient = UrlConnectionHttpDataClient(),
) : DeckSource {
  override suspend fun fetch(entry: CatalogDeck): DeckDownloadPayload {
    val url = configuration.deckUrl(entry.fileUrl) ?: throw StaticContentHttpException.UnsafeDeckUrl(entry.fileUrl)
    val response = client.execute(HttpRequest(url, mapOf("Accept" to "application/json"), timeoutMillis = 30_000))
    if (response.statusCode !in 200..299) throw StaticContentHttpException.StatusCode(response.statusCode)
    return withContext(Dispatchers.Default) { validatedPayload(response.body, entry, InstalledDeckSource.REMOTE) }
  }
}

/**
 * Production source (iOS `StaticDeckSource`): official decks from the bundle (falling back to the
 * remote on a missing/mismatched bundled payload); community decks from the remote when configured.
 */
class StaticDeckSource(
  private val bundled: BundledDeckSource,
  configuration: StaticContentConfiguration = StaticContentConfiguration.live,
  client: HttpDataClient = UrlConnectionHttpDataClient(),
) : DeckSource {
  private val remote: RemoteDeckSource? = configuration.catalogUrl?.let { RemoteDeckSource(configuration, client) }

  override suspend fun fetch(entry: CatalogDeck): DeckDownloadPayload {
    if (entry.official) {
      return try {
        bundled.fetch(entry)
      } catch (error: DeckSourceException.MetadataMismatch) {
        remote?.fetch(entry) ?: throw error
      } catch (error: DeckSourceException.MissingResource) {
        remote?.fetch(entry) ?: throw error
      }
    }
    return remote?.fetch(entry) ?: bundled.fetch(entry)
  }
}

internal fun validatedPayload(data: ByteArray, entry: CatalogDeck, source: InstalledDeckSource): DeckDownloadPayload {
  val deck = DeckJson.decodeDeck(data)
  val issues = DeckValidator.validate(deck)
  if (issues.isNotEmpty()) throw DeckSourceException.InvalidDeck(issues)
  if (!metadataMatches(entry, deck)) throw DeckSourceException.MetadataMismatch
  return DeckDownloadPayload(data, deck, source)
}

private fun metadataMatches(entry: CatalogDeck, deck: Deck): Boolean =
  entry.deckId == deck.deckId && entry.version == deck.version && entry.name == deck.name &&
    entry.authorNickname == deck.author.nickname && entry.official == deck.official && entry.type == deck.type &&
    entry.level == deck.level && entry.tags == deck.tags && entry.localizations == deck.localizations &&
    entry.itemCount == deck.items.size
