package app.piyokey.android.data.decks

import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.PiyoDeckImportError
import app.piyokey.core.deckkit.UserDeckValidator
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Free plan: at most 3 installed user decks (imported + created). */
object UserDeckLimits {
  const val FREE_INSTALLED_USER_DECK_LIMIT = 3
}

sealed class PiyokeyProAccessException(message: String) : Exception(message) {
  data object FreeUserDeckLimitReached : PiyokeyProAccessException("The free plan allows 3 user decks")
}

/**
 * Installed decks (official, community, imported and created) — iOS `DeckLibrary`.
 *
 * Observe the `StateFlow`s from the UI; call mutating functions from the main thread. Store work is
 * serialized on [io]. Install/remove are `suspend`; [remove] and [markPlayed] are fire-and-forget
 * variants launched in [scope].
 *
 * @param hasProAccess PIYOKEY Pro entitlement (wire `ProStore.hasAccess.value` in the app).
 * @param displayName localized deck title used for tie-break sorting of [installed].
 * @param onDeckDownloaded analytics hook after a successful catalog install.
 */
class DeckLibrary(
  private val source: DeckSource,
  private val store: DeckInstallationStore,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher = Dispatchers.IO,
  private val hasProAccess: () -> Boolean = { false },
  private val displayName: (Deck) -> String = { it.name },
  private val onDeckDownloaded: ((Deck, InstalledDeckSource) -> Unit)? = null,
  private val clock: () -> Instant = Instant::now,
) {
  private val decksState = MutableStateFlow<Map<String, Deck>>(emptyMap())
  private val recordsState = MutableStateFlow<Map<String, InstalledDeckRecord>>(emptyMap())
  private val installingState = MutableStateFlow<Set<String>>(emptySet())
  private val failedState = MutableStateFlow<Set<String>>(emptySet())
  private val historyState = MutableStateFlow<Map<String, List<String>>>(emptyMap())
  private val installedListState = MutableStateFlow<List<Deck>>(emptyList())
  private val installingNewUserDeckIds = HashSet<String>()
  private val writer = Mutex()

  val installedDecks: StateFlow<Map<String, Deck>> = decksState.asStateFlow()
  val records: StateFlow<Map<String, InstalledDeckRecord>> = recordsState.asStateFlow()
  val installingDeckIds: StateFlow<Set<String>> = installingState.asStateFlow()
  val failedDeckIds: StateFlow<Set<String>> = failedState.asStateFlow()

  /** deck id → tags at download time (kept after removal; drives Home recommendations). */
  val downloadHistory: StateFlow<Map<String, List<String>>> = historyState.asStateFlow()

  /** Installed decks, most recently played (or installed) first. */
  val installedList: StateFlow<List<Deck>> = installedListState.asStateFlow()

  init {
    reload()
  }

  val installed: List<Deck> get() = installedListState.value

  val installedUserDeckCount: Int get() = recordsState.value.values.count { it.source.isUserDeck }

  fun canInstallNewUserDeck(hasPiyokeyProAccess: Boolean = hasProAccess()): Boolean =
    hasPiyokeyProAccess || installedUserDeckCount + installingNewUserDeckIds.size < UserDeckLimits.FREE_INSTALLED_USER_DECK_LIMIT

  fun isInstalled(deckId: String): Boolean = decksState.value.containsKey(deckId)

  fun installedDeck(deckId: String): Deck? = decksState.value[deckId]

  fun record(deckId: String): InstalledDeckRecord? = recordsState.value[deckId]

  /** Catalog entry is newer than the installed copy. */
  fun needsUpdate(entry: CatalogDeck): Boolean {
    val installed = decksState.value[entry.deckId] ?: return false
    return installed.version < entry.version
  }

  /** Downloads/updates a catalog deck. Failures land in [failedDeckIds]. */
  suspend fun install(entry: CatalogDeck) {
    if (entry.deckId in installingState.value) return
    installingState.value += entry.deckId
    failedState.value -= entry.deckId
    try {
      val payload = source.fetch(entry)
      val record = write { store.install(payload.data, payload.source, now = clock()) }
      decksState.value += (entry.deckId to payload.deck)
      recordsState.value += (entry.deckId to record)
      historyState.value += (entry.deckId to payload.deck.tags)
      publishInstalledList()
      onDeckDownloaded?.invoke(payload.deck, payload.source)
    } catch (cancellation: CancellationException) {
      installingState.value -= entry.deckId
      throw cancellation
    } catch (_: Exception) {
      failedState.value += entry.deckId
    }
    installingState.value -= entry.deckId
  }

  /** Fire-and-forget [install] in the library scope (UI "download"/"update" buttons). */
  fun installInBackground(entry: CatalogDeck): Job = scope.launch { install(entry) }

  /**
   * Commits an imported (`.typedeck`) or Deck Maker deck after re-validating it with the user-deck
   * rules. A new user deck needs a free slot unless [hasPiyokeyProAccess]; replacing an installed
   * id never counts. [expectedCurrentVersion] guards against a concurrent replacement.
   *
   * @throws PiyoDeckImportError.InvalidUserDeck, PiyokeyProAccessException.FreeUserDeckLimitReached,
   *   DeckInstallationStoreException (e.g. `SourceChanged`)
   */
  suspend fun installUserDeck(
    data: ByteArray,
    source: InstalledDeckSource,
    contentSha256: String,
    packageFormatVersion: Int,
    isLocallyModified: Boolean,
    hasPiyokeyProAccess: Boolean = hasProAccess(),
    derivedFromDeckId: String? = null,
    expectedCurrentVersion: Int? = null,
  ): Deck {
    require(source.isUserDeck) { "installUserDeck requires an imported or created source" }
    val deck = withContext(io) { DeckJson.decodeDeck(data) }
    val issues = UserDeckValidator.validate(deck)
    if (issues.isNotEmpty()) throw PiyoDeckImportError.InvalidUserDeck(issues)
    val reservesSlot = !recordsState.value.containsKey(deck.deckId) && deck.deckId !in installingNewUserDeckIds
    if (reservesSlot && !canInstallNewUserDeck(hasPiyokeyProAccess)) throw PiyokeyProAccessException.FreeUserDeckLimitReached
    if (reservesSlot) installingNewUserDeckIds += deck.deckId
    try {
      val record = write {
        store.install(
          data = data,
          source = source,
          contentSha256 = contentSha256,
          packageFormatVersion = packageFormatVersion,
          isLocallyModified = isLocallyModified,
          derivedFromDeckId = derivedFromDeckId,
          expectedCurrentVersion = expectedCurrentVersion,
          now = clock(),
        )
      }
      decksState.value += (deck.deckId to deck)
      recordsState.value += (deck.deckId to record)
      historyState.value += (deck.deckId to deck.tags)
      failedState.value -= deck.deckId
      publishInstalledList()
      return deck
    } finally {
      if (reservesSlot) installingNewUserDeckIds -= deck.deckId
    }
  }

  /** Exact installed payload for `.typedeck` export. */
  suspend fun exportData(deckId: String): ByteArray = write { store.data(deckId) }

  fun remove(deckId: String) {
    scope.launch {
      try {
        removeAndWait(deckId)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Exception) {
        failedState.value += deckId
      }
    }
  }

  suspend fun removeAndWait(deckId: String) {
    write { store.remove(deckId) }
    decksState.value -= deckId
    recordsState.value -= deckId
    failedState.value -= deckId
    publishInstalledList()
  }

  /** Records that [deckId] was just played (drives "recent" ordering). */
  fun markPlayed(deckId: String) {
    scope.launch {
      try {
        val updated = write { store.markPlayed(deckId, clock()) }
        if (updated != null) {
          recordsState.value += (deckId to updated)
          publishInstalledList()
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Exception) {
        failedState.value += deckId
      }
    }
  }

  /** Re-reads the store (called at init; also after a data reset). */
  fun reload() {
    try {
      val snapshot = store.loadSnapshot()
      recordsState.value = snapshot.records
      decksState.value = snapshot.decks
      historyState.value = snapshot.downloadHistory
    } catch (_: Exception) {
      recordsState.value = emptyMap()
      decksState.value = emptyMap()
      historyState.value = emptyMap()
    }
    publishInstalledList()
  }

  private fun publishInstalledList() {
    val records = recordsState.value
    fun dateOf(deck: Deck): Instant = records[deck.deckId]?.let { it.lastPlayedAt ?: it.installedAt } ?: Instant.MIN
    installedListState.value = decksState.value.values.sortedWith { lhs, rhs ->
      val left = dateOf(lhs)
      val right = dateOf(rhs)
      if (left == right) displayName(lhs).compareTo(displayName(rhs)) else right.compareTo(left)
    }
  }

  // A committed write must also update memory: never let caller cancellation drop the result.
  private suspend fun <T> write(block: () -> T): T =
    withContext(kotlinx.coroutines.NonCancellable) { writer.withLock { withContext(io) { block() } } }
}
