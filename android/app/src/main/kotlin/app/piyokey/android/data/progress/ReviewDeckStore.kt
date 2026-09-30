package app.piyokey.android.data.progress

import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.android.data.persistence.StoreJson
import app.piyokey.android.data.persistence.deleteTree
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.domain.ReviewDeckChange
import app.piyokey.core.domain.ReviewDeckItem
import app.piyokey.core.domain.ReviewDeckMutation
import app.piyokey.core.domain.ReviewDeckRules
import app.piyokey.core.domain.ReviewDeckSnapshot
import app.piyokey.core.domain.ReviewDeckStoreException
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Hanco/Review/review-deck.json` (iOS `ReviewDeckStore`, schema 1). Blocking IO. */
class ReviewDeckStore(val rootDir: File) {
  @Serializable
  private data class Index(
    @SerialName("schema_version") val schemaVersion: Int,
    val items: List<ReviewDeckItem>,
  )

  private val indexFile get() = File(rootDir, "review-deck.json")

  fun loadSnapshot(): ReviewDeckSnapshot = RecoverableJsonFile.load(
    indexFile,
    shouldRecover = { it !is ReviewDeckStoreException.UnsupportedSchema },
    decode = ::decode,
  ) ?: ReviewDeckSnapshot.EMPTY

  fun recordMistake(item: DeckItem, sourceDeckId: String, at: Instant = Instant.now()): ReviewDeckMutation =
    commit(ReviewDeckRules.applyMistake(item, sourceDeckId, at, loadSnapshot()), always = true)

  fun recordPerfect(itemId: String, sourceDeckId: String, at: Instant = Instant.now()): ReviewDeckMutation =
    commit(ReviewDeckRules.applyPerfect(itemId, sourceDeckId, at, loadSnapshot()))

  fun addManually(item: DeckItem, sourceDeckId: String, at: Instant = Instant.now()): ReviewDeckMutation =
    commit(ReviewDeckRules.applyManualAdd(item, sourceDeckId, at, loadSnapshot()))

  fun removeManually(itemId: String, sourceDeckId: String): ReviewDeckMutation =
    commit(ReviewDeckRules.applyManualRemove(itemId, sourceDeckId, loadSnapshot()))

  /** Keeps review history but removes a deleted deck's items from the active queue. */
  fun markSourceUnavailable(deckId: String): ReviewDeckMutation =
    commit(ReviewDeckRules.applySourceUnavailable(deckId, loadSnapshot()))

  fun reset() = rootDir.deleteTree()

  internal fun persistSnapshot(snapshot: ReviewDeckSnapshot) {
    loadSnapshot()
    write(snapshot)
  }

  private fun commit(change: ReviewDeckChange, always: Boolean = false): ReviewDeckMutation {
    if (always || change.mutation != ReviewDeckMutation.UNCHANGED) write(change.snapshot)
    return change.mutation
  }

  private fun decode(data: ByteArray): ReviewDeckSnapshot {
    val index = StoreJson.decode(Index.serializer(), data)
    if (index.schemaVersion != ReviewDeckRules.CURRENT_SCHEMA_VERSION) throw ReviewDeckStoreException.UnsupportedSchema(index.schemaVersion)
    return ReviewDeckRules.validated(index.items)
  }

  private fun write(snapshot: ReviewDeckSnapshot) {
    val index = Index(ReviewDeckRules.CURRENT_SCHEMA_VERSION, snapshot.items.values.sortedBy { it.id })
    RecoverableJsonFile.write(StoreJson.encode(Index.serializer(), index), indexFile)
  }
}

/**
 * Observable review deck (iOS `ReviewDeckLibrary`). Per-jamo mistakes/perfects are debounced
 * (750 ms); manual edits and reconciliation flush immediately.
 */
class ReviewDeckLibrary(
  private val store: ReviewDeckStore,
  private val scope: CoroutineScope,
  io: CoroutineDispatcher = Dispatchers.IO,
  private val debounceMillis: Long = 750,
  private val clock: () -> Instant = Instant::now,
) {
  private val itemsState = MutableStateFlow<Map<String, ReviewDeckItem>>(emptyMap())
  private val activeItemsState = MutableStateFlow<List<ReviewDeckItem>>(emptyList())
  private val saveFailedState = MutableStateFlow(false)
  private val writer = RevisionWriter<ReviewDeckSnapshot>(io, store::persistSnapshot)
  private var retryAction: (() -> Unit)? = null
  private var debounceJob: Job? = null
  private var revision = 0

  /** Every item by `<sourceDeckId>::<itemId>`, including graduated/unavailable history. */
  val items: StateFlow<Map<String, ReviewDeckItem>> = itemsState.asStateFlow()

  /** Items to review, newest first. */
  val activeItems: StateFlow<List<ReviewDeckItem>> = activeItemsState.asStateFlow()
  val saveFailed: StateFlow<Boolean> = saveFailedState.asStateFlow()

  init {
    reload()
  }

  fun recordMistake(item: DeckItem, sourceDeckId: String): ReviewDeckMutation =
    mutate(debounced = true) { ReviewDeckRules.applyMistake(item, sourceDeckId, clock(), it) }

  fun recordPerfect(itemId: String, sourceDeckId: String): ReviewDeckMutation =
    mutate(debounced = true) { ReviewDeckRules.applyPerfect(itemId, sourceDeckId, clock(), it) }

  fun addManually(item: DeckItem, sourceDeckId: String): ReviewDeckMutation =
    mutate(debounced = false) { ReviewDeckRules.applyManualAdd(item, sourceDeckId, clock(), it) }

  fun removeManually(itemId: String, sourceDeckId: String): ReviewDeckMutation =
    mutate(debounced = false) { ReviewDeckRules.applyManualRemove(itemId, sourceDeckId, it) }

  fun isInReview(itemId: String, sourceDeckId: String): Boolean =
    itemsState.value[ReviewDeckItem.id(itemId, sourceDeckId)]?.isActive == true

  /** Refreshes stored copies from an updated/re-imported deck (call after install). */
  fun reconcile(deck: Deck): ReviewDeckMutation = mutate(debounced = false) { ReviewDeckRules.applyDeckContent(deck, it) }

  /** Applies deck deletion and waits for the retained history to reach disk. */
  suspend fun markSourceUnavailable(deckId: String): ReviewDeckMutation {
    val change = ReviewDeckRules.applySourceUnavailable(deckId, ReviewDeckSnapshot(itemsState.value))
    if (change.mutation == ReviewDeckMutation.UNCHANGED) return change.mutation
    debounceJob?.cancel()
    debounceJob = null
    revision += 1
    publish(change.snapshot)
    persist(change.snapshot, revision)
    return change.mutation
  }

  fun flush() {
    debounceJob?.cancel()
    debounceJob = null
    val snapshot = ReviewDeckSnapshot(itemsState.value)
    val requested = revision
    scope.launch { persist(snapshot, requested) }
  }

  suspend fun flushAndWait(): Boolean {
    debounceJob?.cancel()
    debounceJob = null
    return persist(ReviewDeckSnapshot(itemsState.value), revision)
  }

  fun retryLastSave() {
    retryAction?.invoke()
  }

  private fun mutate(debounced: Boolean, mutation: (ReviewDeckSnapshot) -> ReviewDeckChange): ReviewDeckMutation {
    val change = mutation(ReviewDeckSnapshot(itemsState.value))
    if (change.mutation == ReviewDeckMutation.UNCHANGED) return change.mutation
    revision += 1
    publish(change.snapshot)
    if (debounced) scheduleDebouncedFlush() else flush()
    return change.mutation
  }

  private fun publish(snapshot: ReviewDeckSnapshot) {
    itemsState.value = snapshot.items
    activeItemsState.value = snapshot.activeItems
  }

  private fun scheduleDebouncedFlush() {
    debounceJob?.cancel()
    val snapshot = ReviewDeckSnapshot(itemsState.value)
    val requested = revision
    debounceJob = scope.launch {
      delay(debounceMillis)
      if (requested != revision) return@launch
      debounceJob = null
      persist(snapshot, requested)
    }
  }

  private suspend fun persist(snapshot: ReviewDeckSnapshot, requested: Int): Boolean = try {
    writer.persist(snapshot, requested)
    if (requested == revision) {
      saveFailedState.value = false
      retryAction = null
    }
    true
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (_: Exception) {
    if (requested == revision) {
      saveFailedState.value = true
      retryAction = { flush() }
    }
    false
  }

  fun reload() {
    try {
      publish(store.loadSnapshot())
      // Keep the revision monotonic: RevisionWriter drops revisions it has already seen.
      saveFailedState.value = false
      retryAction = null
    } catch (_: Exception) {
      publish(ReviewDeckSnapshot.EMPTY)
      saveFailedState.value = true
      retryAction = { reload() }
    }
  }
}
