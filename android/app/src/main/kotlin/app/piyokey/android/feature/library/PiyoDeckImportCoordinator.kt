package app.piyokey.android.feature.library

import app.piyokey.android.data.decks.PendingImportsStore
import app.piyokey.android.data.decks.PiyoDeckFileTooLargeException
import app.piyokey.core.deckkit.PiyoDeckPackage
import app.piyokey.core.domain.PiyoDeckDocumentNotice
import app.piyokey.core.domain.library.PiyoDeckImportRules
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A validated `.typedeck` staged in `PendingImports/`, awaiting the user's decision. */
class PiyoDeckImportCandidate(val pkg: PiyoDeckPackage, internal val stagedFile: File) {
  val id: String = UUID.randomUUID().toString()
}

/**
 * iOS `PiyoDeckDocumentCoordinator`: every incoming document (SAF picker, VIEW/SEND intent) is
 * first staged (bounded 8 MiB copy, survives process death), then read one at a time into a
 * [candidate] for the preview; failures become a [notice]. Documents that arrive while a preview
 * or notice is showing are queued; staged files left from a previous run are resumed.
 *
 * Call everything on the main thread; file IO runs on [io].
 */
class PiyoDeckImportCoordinator(
  private val store: PendingImportsStore,
  private val deckSchema: () -> ByteArray,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher = Dispatchers.IO,
) {
  private val candidateState = MutableStateFlow<PiyoDeckImportCandidate?>(null)
  private val noticeState = MutableStateFlow<PiyoDeckDocumentNotice?>(null)
  private val readingState = MutableStateFlow(false)
  private val pendingState = MutableStateFlow(false)

  val candidate: StateFlow<PiyoDeckImportCandidate?> = candidateState.asStateFlow()
  val notice: StateFlow<PiyoDeckDocumentNotice?> = noticeState.asStateFlow()
  val isReading: StateFlow<Boolean> = readingState.asStateFlow()

  /** My Page badge: a preview or notice is waiting (iOS `.badge(candidate != nil || notice != nil)`). */
  val hasPendingDocument: StateFlow<Boolean> = pendingState.asStateFlow()

  private val queue = ArrayDeque<File>()
  private var activeReadCount = 0
  private var isPreparingCandidate = false

  /** Re-offers documents staged before the app was killed (iOS `resumePendingIfNeeded`). */
  suspend fun resumePendingIfNeeded() {
    if (candidateState.value != null || queue.isNotEmpty() || isPreparingCandidate || activeReadCount > 0) return
    val files = withContext(io) { runCatching { store.pending() }.getOrDefault(emptyList()) }
    // A document may have started staging while the directory was listed.
    if (candidateState.value != null || isPreparingCandidate || activeReadCount > 0) return
    files.filterNot { file -> queue.any { it.canonicalPath == file.canonicalPath } }.forEach(queue::addLast)
    prepareNextIfPossible()
  }

  /** Stages then previews one document. [open] returns the source stream (null = unreadable). */
  suspend fun receive(open: () -> InputStream?) {
    beginReading()
    try {
      val staged = withContext(io) {
        val input = open() ?: throw java.io.IOException("No input stream")
        input.use { store.stage(it) }
      }
      queue.addLast(staged)
      prepareNextIfPossible()
    } catch (error: Exception) {
      if (noticeState.value == null) {
        noticeState.value = PiyoDeckImportRules.notice(error, isFileTooLarge = error is PiyoDeckFileTooLargeException)
      }
      publishPending()
    } finally {
      endReading()
    }
  }

  /** Closes the preview (the staged copy is deleted); a repeated call is a no-op. */
  fun dismissCandidate(showNext: Boolean = true) {
    val current = candidateState.value ?: return
    runCatching { store.remove(current.stagedFile) }
    candidateState.value = null
    publishPending()
    if (showNext) scheduleNext()
  }

  fun finishImport() {
    dismissCandidate(showNext = false)
    noticeState.value = PiyoDeckDocumentNotice.IMPORTED
    publishPending()
  }

  fun markAlreadyImported() {
    dismissCandidate(showNext = false)
    noticeState.value = PiyoDeckDocumentNotice.ALREADY_IMPORTED
    publishPending()
  }

  fun dismissNotice() {
    noticeState.value = null
    publishPending()
    scheduleNext()
  }

  fun reportReadFailure() = report(PiyoDeckDocumentNotice.CANNOT_READ)
  fun reportSaveFailure() = report(PiyoDeckDocumentNotice.CANNOT_SAVE)
  fun reportExportFailure() = report(PiyoDeckDocumentNotice.CANNOT_EXPORT)

  private fun report(notice: PiyoDeckDocumentNotice) {
    noticeState.value = notice
    publishPending()
  }

  private suspend fun prepareNextIfPossible() {
    if (candidateState.value != null || noticeState.value != null || isPreparingCandidate || queue.isEmpty()) return
    val staged = queue.removeFirst()
    isPreparingCandidate = true
    beginReading()
    try {
      val pkg = withContext(io) { store.read(staged, deckSchema()) }
      candidateState.value = PiyoDeckImportCandidate(pkg, staged)
    } catch (error: Exception) {
      withContext(io) { runCatching { store.remove(staged) } }
      noticeState.value = PiyoDeckImportRules.notice(error, isFileTooLarge = error is PiyoDeckFileTooLargeException)
    } finally {
      endReading()
      isPreparingCandidate = false
      publishPending()
    }
  }

  private fun scheduleNext() {
    scope.launch { prepareNextIfPossible() }
  }

  private fun beginReading() {
    activeReadCount += 1
    readingState.value = true
  }

  private fun endReading() {
    activeReadCount = maxOf(0, activeReadCount - 1)
    readingState.value = activeReadCount > 0
  }

  private fun publishPending() {
    pendingState.value = candidateState.value != null || noticeState.value != null
  }
}
