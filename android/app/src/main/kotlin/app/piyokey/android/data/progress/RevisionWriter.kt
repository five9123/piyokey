package app.piyokey.android.data.progress

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serialized, revision-ordered background writer (iOS `*Writer` actors): an older revision never
 * overwrites a newer one and an already persisted revision is skipped.
 */
internal class RevisionWriter<S>(
  private val io: CoroutineDispatcher,
  private val persistBlock: (S) -> Unit,
) {
  private val mutex = Mutex()
  private var latestRequestedRevision = 0
  private var persistedRevision = 0

  suspend fun persist(snapshot: S, revision: Int) = mutex.withLock {
    if (revision < latestRequestedRevision) return@withLock
    latestRequestedRevision = revision
    if (revision <= persistedRevision) return@withLock
    withContext(io) { persistBlock(snapshot) }
    persistedRevision = revision
  }

  /** Runs [block] under the writer lock; [block] returns whether [revision] is now durable. */
  suspend fun persistVerified(revision: Int, block: suspend () -> Boolean): Boolean = mutex.withLock {
    if (revision < latestRequestedRevision) return@withLock false
    latestRequestedRevision = revision
    val verified = block()
    if (verified) persistedRevision = maxOf(persistedRevision, revision)
    verified
  }
}
