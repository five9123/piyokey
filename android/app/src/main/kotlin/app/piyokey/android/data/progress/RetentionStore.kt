package app.piyokey.android.data.progress

import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.android.data.persistence.StoreJson
import app.piyokey.android.data.persistence.deleteTree
import app.piyokey.core.domain.JstDay
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.RetentionDayRecord
import app.piyokey.core.domain.RetentionMutation
import app.piyokey.core.domain.RetentionRules
import app.piyokey.core.domain.RetentionSnapshot
import app.piyokey.core.domain.RetentionStoreException
import app.piyokey.core.domain.RetentionStreak
import app.piyokey.core.domain.StampReward
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/** `Hanco/Retention/retention-progress.json` (iOS `RetentionStore`, schema 1). Blocking IO. */
class RetentionStore(val rootDir: File) {
  @Serializable
  private data class Index(
    @SerialName("schema_version") val schemaVersion: Int,
    val records: List<RetentionDayRecord>,
  )

  private val indexFile get() = File(rootDir, "retention-progress.json")

  fun loadSnapshot(): RetentionSnapshot = RecoverableJsonFile.load(
    indexFile,
    shouldRecover = { it !is RetentionStoreException.UnsupportedSchema },
    decode = ::decode,
  ) ?: RetentionSnapshot.EMPTY

  fun record(activity: RetentionActivityKind, day: JstDay): RetentionMutation {
    val (snapshot, mutation) = RetentionRules.record(loadSnapshot(), activity, day)
    if (mutation == RetentionMutation.INSERTED) write(snapshot)
    return mutation
  }

  fun reset() = rootDir.deleteTree()

  private fun decode(data: ByteArray): RetentionSnapshot {
    val index = StoreJson.decode(Index.serializer(), data)
    if (index.schemaVersion != RetentionRules.CURRENT_SCHEMA_VERSION) throw RetentionStoreException.UnsupportedSchema(index.schemaVersion)
    return RetentionRules.validated(index.records)
  }

  private fun write(snapshot: RetentionSnapshot) {
    val index = Index(RetentionRules.CURRENT_SCHEMA_VERSION, snapshot.records.values.sortedBy { it.day })
    RecoverableJsonFile.write(StoreJson.encode(Index.serializer(), index), indexFile)
  }
}

/**
 * Daily stamps / streak (iOS `RetentionLibrary`). Writes are synchronous (one tiny file) like iOS.
 * A session records the JST day it *started* on (see [RetentionSession]).
 */
class RetentionLibrary(private val store: RetentionStore) {
  private val recordsState = MutableStateFlow<Map<JstDay, RetentionDayRecord>>(emptyMap())
  private val saveFailedState = MutableStateFlow(false)
  private var retryAction: (() -> Unit)? = null

  val records: StateFlow<Map<JstDay, RetentionDayRecord>> = recordsState.asStateFlow()
  val saveFailed: StateFlow<Boolean> = saveFailedState.asStateFlow()

  init {
    reload()
  }

  val completedDays: Set<JstDay> get() = RetentionSnapshot(recordsState.value).completedDays

  fun record(activity: RetentionActivityKind, session: RetentionSession) = record(activity, session.day)

  fun record(activity: RetentionActivityKind, day: JstDay) {
    try {
      store.record(activity, day)
      reload()
    } catch (_: Exception) {
      saveFailedState.value = true
      retryAction = { record(activity, day) }
    }
  }

  fun isStamped(day: JstDay): Boolean = recordsState.value[day]?.isStamped == true

  fun completedDailyChallenge(day: JstDay): Boolean = recordsState.value[day]?.completedDailyChallenge == true

  fun streak(asOf: JstDay): RetentionStreak = RetentionStreak.calculate(completedDays, asOf)

  /** Stamped days in this Monday–Sunday card (drives [StampReward] unlocks). */
  fun stampedDaysThisWeek(today: JstDay): Int = StampReward.stampedDaysThisWeek(today, ::isStamped)

  /** Home/My Piyo cheer line key for today (`mascot.daily_encouragement.N`). */
  fun dailyEncouragementKey(today: JstDay): String =
    app.piyokey.core.domain.MascotDailyEncouragement.localizationKey(today, completedDays)

  fun retryLastSave() {
    retryAction?.invoke()
  }

  fun reload() {
    try {
      recordsState.value = store.loadSnapshot().records
      saveFailedState.value = false
      retryAction = null
    } catch (_: Exception) {
      recordsState.value = emptyMap()
      saveFailedState.value = true
      retryAction = { reload() }
    }
  }
}

/** Captures the JST day when a lesson/game starts (iOS `RetentionSessionContext`). */
data class RetentionSession(val day: JstDay) {
  companion object {
    fun start(at: Instant = RetentionClock.now()) = RetentionSession(JstDay.of(at))
  }
}

/** Overridable clock for retention (tests/UI tests pin a JST day). */
object RetentionClock {
  @Volatile var override: Instant? = null

  fun now(): Instant = override ?: Instant.now()

  fun today(): JstDay = JstDay.of(now())
}
