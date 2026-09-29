package app.piyokey.core.domain

import app.piyokey.core.deckkit.Iso8601InstantSerializer
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class SessionMode(val raw: String) {
  @SerialName("lesson") LESSON("lesson"),
  @SerialName("game") GAME("game"),
}

@Serializable
enum class GameCompetition(val raw: String) {
  @SerialName("official_deck_v1") OFFICIAL_DECK("official_deck_v1"),
  @SerialName("weekly_piyo_cup_v1") WEEKLY_PIYO_CUP("weekly_piyo_cup_v1"),
}

/** How the player typed. Legacy raw values `built_in` / `os_keyboard` still decode. */
@Serializable(with = SessionInputModeSerializer::class)
enum class SessionInputMode(val raw: String) {
  BUILT_IN("builtin"),
  BUILT_IN_KOREAN_10KEY("builtin_korean_10key"),
  OS_IME("os_ime");

  companion object {
    fun fromRaw(value: String): SessionInputMode? = when (value) {
      "builtin", "built_in" -> BUILT_IN
      "builtin_korean_10key" -> BUILT_IN_KOREAN_10KEY
      "os_ime", "os_keyboard" -> OS_IME
      else -> null
    }
  }
}

object SessionInputModeSerializer : KSerializer<SessionInputMode> {
  override val descriptor = PrimitiveSerialDescriptor("app.piyokey.SessionInputMode", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: SessionInputMode) = encoder.encodeString(value.raw)
  override fun deserialize(decoder: Decoder): SessionInputMode {
    val raw = decoder.decodeString()
    return SessionInputMode.fromRaw(raw) ?: throw SerializationException("Unsupported input mode: $raw")
  }
}

@Serializable
enum class GameKind(val raw: String) {
  @SerialName("flow") FLOW("flow"),
  @SerialName("acid_rain") ACID_RAIN("acid_rain"),
  @SerialName("choseong") CHOSEONG("choseong"),
  @SerialName("dictation") DICTATION("dictation"),
  @SerialName("word_match") WORD_MATCH("word_match");

  companion object {
    /** `GameRecord.course` → kind; unknown courses (`word`, `sentence`, …) are Flow. */
    fun fromCourse(course: String): GameKind = when (course) {
      ACID_RAIN.raw -> ACID_RAIN
      CHOSEONG.raw -> CHOSEONG
      WORD_MATCH.raw -> WORD_MATCH
      DICTATION.raw -> DICTATION
      else -> FLOW
    }
  }
}

/** One finished lesson or game (iOS `GameRecord`, `Progress/game-progress.json`). */
@Serializable
data class GameRecord(
  /** UUID string (iOS encodes uppercase). */
  val id: String,
  val mode: SessionMode,
  @SerialName("deck_id") val deckId: String,
  @SerialName("deck_version") val deckVersion: Int? = null,
  val competition: GameCompetition? = null,
  val course: String,
  val score: Int,
  @SerialName("max_combo") val maxCombo: Int,
  val accuracy: Double,
  @SerialName("characters_per_minute") val charactersPerMinute: Double,
  /** Seconds. */
  @SerialName("active_duration") val activeDuration: Double,
  @SerialName("completed_item_count") val completedItemCount: Int,
  @SerialName("missed_item_count") val missedItemCount: Int,
  @SerialName("input_mode") val inputMode: SessionInputMode,
  @SerialName("played_at") @Serializable(with = Iso8601InstantSerializer::class) val playedAt: Instant,
) {
  val gameKind: GameKind get() = GameKind.fromCourse(course)

  companion object {
    fun newId(): String = UUID.randomUUID().toString().uppercase()
  }
}

/** Best/plays summary per deck × game × input mode × (cup) key. */
@Serializable
data class DeckProgress(
  @SerialName("deck_id") val deckId: String,
  @SerialName("game_kind") val gameKind: GameKind = GameKind.FLOW,
  @SerialName("input_mode") val inputMode: SessionInputMode = SessionInputMode.BUILT_IN,
  /** Only [GameCompetition.WEEKLY_PIYO_CUP] is kept; anything else is normalized to `null`. */
  val competition: GameCompetition? = null,
  val plays: Int,
  @SerialName("best_score") val bestScore: Int,
  @SerialName("best_accuracy") val bestAccuracy: Double,
  @SerialName("last_played_at") @Serializable(with = Iso8601InstantSerializer::class) val lastPlayedAt: Instant,
) {
  val id: String get() = GameProgressRules.progressKey(deckId, gameKind, inputMode, competition)

  fun normalized(): DeckProgress =
    if (competition == null || competition == GameCompetition.WEEKLY_PIYO_CUP) this else copy(competition = null)
}

data class GameProgressSnapshot(
  val records: List<GameRecord>,
  val deckProgress: Map<String, DeckProgress>,
  /** Schema 1/2 summaries that may contain compacted cup runs; preserved but never used as a best. */
  val legacyMixedProgress: Map<String, DeckProgress> = emptyMap(),
) {
  companion object {
    val EMPTY = GameProgressSnapshot(emptyList(), emptyMap())
  }
}

data class GameRecordSaveOutcome(
  val record: GameRecord,
  val previousBestScore: Int?,
  val isNewBest: Boolean,
  val deckProgress: DeckProgress,
)

sealed class GameProgressStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) : GameProgressStoreException("Unsupported game progress schema $version")
  data object InvalidProgress : GameProgressStoreException("Invalid game progress")
}

/** A leaderboard a record counts toward (Play Games / Game Center), used only by compaction. */
data class LeaderboardRef(val id: String, val isWeekly: Boolean)

/** iOS `PiyoCupWeek`: weeks start Monday 00:00 Asia/Tokyo. */
object JstWeek {
  fun start(containing: Instant): Instant = containing.atZone(RetentionCalendar.JST).toLocalDate()
    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    .atStartOfDay(RetentionCalendar.JST).toInstant()

  fun contains(date: Instant, weekContaining: Instant): Boolean {
    val start = start(weekContaining)
    val end = start.atZone(RetentionCalendar.JST).plusDays(7).toInstant()
    return !date.isBefore(start) && date.isBefore(end)
  }
}

object GameProgressRules {
  const val CURRENT_SCHEMA_VERSION = 3
  const val MAXIMUM_RETAINED_RECORDS = 2_048
  const val PIYO_CUP_DECK_ID = "flow_topik_beginner"

  fun progressKey(
    deckId: String,
    gameKind: GameKind = GameKind.FLOW,
    inputMode: SessionInputMode,
    competition: GameCompetition? = null,
  ): String {
    val base = "$deckId::${gameKind.raw}::${inputMode.raw}"
    return if (competition == GameCompetition.WEEKLY_PIYO_CUP) "$base::weekly_piyo_cup_v1" else base
  }

  private val uuidPattern = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")

  fun isValid(record: GameRecord): Boolean =
    uuidPattern.matches(record.id) && record.deckId.isNotEmpty() && record.course.isNotEmpty() && record.score >= 0 &&
      record.maxCombo >= 0 && record.accuracy.isFinite() && record.accuracy in 0.0..100.0 &&
      record.charactersPerMinute.isFinite() && record.charactersPerMinute >= 0 &&
      record.activeDuration.isFinite() && record.activeDuration >= 0 &&
      record.completedItemCount >= 0 && record.missedItemCount >= 0

  fun validate(record: GameRecord) {
    if (!isValid(record)) throw GameProgressStoreException.InvalidProgress
  }

  fun validatedProgress(values: List<DeckProgress>): Map<String, DeckProgress> {
    val result = LinkedHashMap<String, DeckProgress>()
    for (raw in values) {
      val progress = raw.normalized()
      if (progress.deckId.isEmpty() || progress.plays < 0 || progress.bestScore < 0 ||
        !progress.bestAccuracy.isFinite() || progress.bestAccuracy !in 0.0..100.0 || result.containsKey(progress.id)
      ) {
        throw GameProgressStoreException.InvalidProgress
      }
      result[progress.id] = progress
    }
    return result
  }

  /** Decoded index → validated snapshot; schema < 3 separates mixed cup summaries. */
  fun validatedSnapshot(
    schemaVersion: Int,
    records: List<GameRecord>,
    deckProgress: List<DeckProgress>,
    legacyMixedProgress: List<DeckProgress>?,
  ): GameProgressSnapshot {
    if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) throw GameProgressStoreException.UnsupportedSchema(schemaVersion)
    val progress = validatedProgress(deckProgress)
    val legacy = validatedProgress(legacyMixedProgress ?: emptyList())
    if (records.map { it.id }.toSet().size != records.size || !records.all(::isValid)) {
      throw GameProgressStoreException.InvalidProgress
    }
    val snapshot = GameProgressSnapshot(records, progress, legacy)
    return if (schemaVersion < 3) separatingLegacyCupProgress(snapshot) else snapshot
  }

  fun separatingLegacyCupProgress(snapshot: GameProgressSnapshot): GameProgressSnapshot {
    val deckProgress = LinkedHashMap(snapshot.deckProgress)
    val legacy = LinkedHashMap(snapshot.legacyMixedProgress)
    val affected = snapshot.deckProgress.values.filter {
      it.deckId == PIYO_CUP_DECK_ID && it.gameKind == GameKind.FLOW && it.inputMode != SessionInputMode.BUILT_IN_KOREAN_10KEY
    }.mapTo(mutableSetOf()) { it.id }
    for (record in snapshot.records) {
      if (record.mode == SessionMode.GAME && record.competition == GameCompetition.WEEKLY_PIYO_CUP) {
        affected += progressKey(record.deckId, record.gameKind, record.inputMode)
      }
    }
    for (key in affected) {
      deckProgress.remove(key)?.let { legacy[key] = it }
    }
    for (record in snapshot.records.sortedBy { it.playedAt }) {
      if (record.mode != SessionMode.GAME) continue
      if (progressKey(record.deckId, record.gameKind, record.inputMode) !in affected) continue
      val key = progressKey(record.deckId, record.gameKind, record.inputMode, record.competition)
      deckProgress[key] = updatedProgress(record, deckProgress[key])
    }
    return snapshot.copy(deckProgress = deckProgress, legacyMixedProgress = legacy)
  }

  fun updatedProgress(record: GameRecord, previous: DeckProgress?): DeckProgress = DeckProgress(
    deckId = record.deckId,
    gameKind = record.gameKind,
    inputMode = record.inputMode,
    competition = record.competition,
    plays = (previous?.plays ?: 0) + 1,
    bestScore = maxOf(previous?.bestScore ?: record.score, record.score),
    bestAccuracy = maxOf(previous?.bestAccuracy ?: record.accuracy, record.accuracy),
    lastPlayedAt = record.playedAt,
  ).normalized()

  /** Appends a game result and updates its best summary. Throws on invalid metrics. */
  fun applyGameRecord(record: GameRecord, snapshot: GameProgressSnapshot): Pair<GameProgressSnapshot, GameRecordSaveOutcome> {
    validate(record)
    val key = progressKey(record.deckId, record.gameKind, record.inputMode, record.competition)
    val previous = snapshot.deckProgress[key]
    val previousBest = previous?.bestScore
    val isNewBest = previousBest == null || record.score > previousBest
    val progress = updatedProgress(record, previous)
    val updated = snapshot.copy(records = snapshot.records + record, deckProgress = snapshot.deckProgress + (key to progress))
    return updated to GameRecordSaveOutcome(record, previousBest, isNewBest, progress)
  }

  /** Lesson records are logged for insights but never change game bests. */
  fun applyLessonRecord(record: GameRecord, snapshot: GameProgressSnapshot): GameProgressSnapshot {
    if (record.mode != SessionMode.LESSON) throw GameProgressStoreException.InvalidProgress
    validate(record)
    return snapshot.copy(records = snapshot.records + record)
  }

  /**
   * Bounds the raw log to [limit] records while keeping, for every leaderboard, the record that
   * reproduces the all-time best (weekly boards: only this JST week), then the newest records.
   */
  fun compacted(
    snapshot: GameProgressSnapshot,
    asOf: Instant,
    limit: Int = MAXIMUM_RETAINED_RECORDS,
    leaderboards: (GameRecord) -> List<LeaderboardRef>,
  ): GameProgressSnapshot {
    if (limit <= 0 || snapshot.records.size <= limit) return snapshot
    val best = HashMap<String, GameRecord>()
    for (record in snapshot.records) {
      for (board in leaderboards(record)) {
        if (board.isWeekly && !JstWeek.contains(record.playedAt, asOf)) continue
        val existing = best[board.id]
        if (existing != null &&
          (existing.score > record.score || (existing.score == record.score && existing.playedAt >= record.playedAt))
        ) {
          continue
        }
        best[board.id] = record
      }
    }
    val retained = best.values.mapTo(HashSet()) { it.id }
    for (record in snapshot.records.asReversed()) {
      if (retained.size >= limit) break
      retained += record.id
    }
    return snapshot.copy(records = snapshot.records.filter { it.id in retained })
  }

  fun bestCombo(
    records: List<GameRecord>,
    deckId: String,
    gameKind: GameKind = GameKind.FLOW,
    inputMode: SessionInputMode = SessionInputMode.BUILT_IN,
    competition: GameCompetition? = null,
  ): Int = records.asSequence().filter {
    it.mode == SessionMode.GAME && it.deckId == deckId && it.gameKind == gameKind && it.inputMode == inputMode &&
      (it.competition == GameCompetition.WEEKLY_PIYO_CUP) == (competition == GameCompetition.WEEKLY_PIYO_CUP)
  }.maxOfOrNull { it.maxCombo } ?: 0
}

enum class LearningInsightPeriod(val days: Int) { WEEK(7), MONTH(30) }

data class DailyLearningActivity(
  val day: JstDay,
  val sessionCount: Int,
  val activeDuration: Double,
  val isActive: Boolean,
)

data class WeakJamoInsight(val jamo: String, val mistakeCount: Int)

/** My Page learning report (iOS `LearningInsights`). */
data class LearningInsights(
  val period: LearningInsightPeriod,
  val dailyActivities: List<DailyLearningActivity>,
  val activeDays: Int,
  val sessionCount: Int,
  val totalActiveDuration: Double,
  val averageAccuracy: Double?,
  val averageCharactersPerMinute: Double?,
  val accuracyChange: Double?,
  val speedChange: Double?,
  val completedItemCount: Int,
  val activeReviewCount: Int,
  val addedReviewCount: Int,
  val graduatedReviewCount: Int,
  val weakJamo: List<WeakJamoInsight>,
) {
  companion object {
    fun make(
      period: LearningInsightPeriod,
      asOf: Instant,
      records: List<GameRecord>,
      retentionRecords: Map<JstDay, RetentionDayRecord>,
      reviewItems: Collection<ReviewDeckItem>,
      mistakeCounts: Map<String, Int>,
    ): LearningInsights {
      val today = JstDay.of(asOf)
      val days = RetentionCalendar.days(today, period.days)
      val daySet = days.toHashSet()
      val previousDays = RetentionCalendar.days(today.adding(-period.days), period.days).toHashSet()
      val current = ArrayList<GameRecord>()
      val previous = ArrayList<GameRecord>()
      val byDay = HashMap<JstDay, MutableList<GameRecord>>()
      for (record in records) {
        val day = JstDay.of(record.playedAt)
        if (day in daySet) {
          current += record
          byDay.getOrPut(day) { mutableListOf() } += record
        } else if (day in previousDays) {
          previous += record
        }
      }
      val currentMetrics = metrics(current)
      val previousMetrics = metrics(previous)
      val activities = days.map { day ->
        val dayRecords = byDay[day].orEmpty()
        DailyLearningActivity(
          day = day,
          sessionCount = dayRecords.size,
          activeDuration = dayRecords.sumOf { it.activeDuration },
          isActive = retentionRecords[day]?.isStamped == true || dayRecords.isNotEmpty(),
        )
      }
      var active = 0
      var added = 0
      var graduated = 0
      for (item in reviewItems) {
        if (item.isActive) active++
        if (JstDay.of(item.addedAt) in daySet) added++
        if (item.graduatedAt?.let { JstDay.of(it) in daySet } == true) graduated++
      }
      return LearningInsights(
        period = period,
        dailyActivities = activities,
        activeDays = activities.count { it.isActive },
        sessionCount = current.size,
        totalActiveDuration = currentMetrics.totalDuration,
        averageAccuracy = currentMetrics.averageAccuracy,
        averageCharactersPerMinute = currentMetrics.averageCpm,
        accuracyChange = change(currentMetrics.averageAccuracy, previousMetrics.averageAccuracy),
        speedChange = change(currentMetrics.averageCpm, previousMetrics.averageCpm),
        completedItemCount = current.sumOf { it.completedItemCount },
        activeReviewCount = active,
        addedReviewCount = added,
        graduatedReviewCount = graduated,
        weakJamo = mistakeCounts.filter { it.value > 0 }
          .map { WeakJamoInsight(it.key, it.value) }
          .sortedWith(compareByDescending<WeakJamoInsight> { it.mistakeCount }.thenBy { it.jamo })
          .take(5),
      )
    }

    private class Metrics(val totalDuration: Double, val averageAccuracy: Double?, val averageCpm: Double?)

    private fun metrics(records: List<GameRecord>): Metrics {
      if (records.isEmpty()) return Metrics(0.0, null, null)
      val totalDuration = records.sumOf { it.activeDuration }
      val averageAccuracy = records.sumOf { it.accuracy } / records.size
      val timed = records.filter { it.activeDuration > 0 }
      val cpm = if (totalDuration > 0 && timed.isNotEmpty()) {
        timed.sumOf { it.charactersPerMinute * (it.activeDuration / 60) } / (totalDuration / 60)
      } else {
        records.sumOf { it.charactersPerMinute } / records.size
      }
      return Metrics(totalDuration, averageAccuracy, cpm)
    }

    private fun change(current: Double?, previous: Double?): Double? =
      if (current == null || previous == null) null else current - previous
  }
}
