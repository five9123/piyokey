package app.piyokey.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A calendar day in Asia/Tokyo, stored as `YYYY-MM-DD` (iOS `JSTDay`). Every retention stamp,
 * streak and daily rotation uses this day, independent of the device time zone.
 */
@Serializable(with = JstDaySerializer::class)
class JstDay private constructor(val rawValue: String, val localDate: LocalDate) : Comparable<JstDay> {
  val id: String get() = rawValue

  fun adding(days: Int): JstDay = of(localDate.plusDays(days.toLong()))

  /** Whole JST days since 1970-01-01 (iOS `JSTDay.ordinal`). */
  val ordinal: Int get() = localDate.toEpochDay().toInt()

  override fun compareTo(other: JstDay): Int = rawValue.compareTo(other.rawValue)
  override fun equals(other: Any?): Boolean = other is JstDay && other.rawValue == rawValue
  override fun hashCode(): Int = rawValue.hashCode()
  override fun toString(): String = rawValue

  companion object {
    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    private val pattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Validated parse; `null` for malformed or impossible dates (`2026-02-30`). */
    fun parse(rawValue: String): JstDay? {
      if (!pattern.matches(rawValue)) return null
      val (year, month, day) = rawValue.split('-').map { it.toInt() }
      val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
      return JstDay(rawValue, date)
    }

    fun of(date: LocalDate): JstDay = JstDay(date.format(formatter), date)

    /** The JST day containing [instant]. */
    fun of(instant: Instant): JstDay = of(instant.atZone(RetentionCalendar.JST).toLocalDate())
  }
}

object JstDaySerializer : KSerializer<JstDay> {
  override val descriptor = PrimitiveSerialDescriptor("app.piyokey.JstDay", PrimitiveKind.STRING)
  override fun serialize(encoder: Encoder, value: JstDay) = encoder.encodeString(value.rawValue)
  override fun deserialize(decoder: Decoder): JstDay {
    val raw = decoder.decodeString()
    return JstDay.parse(raw) ?: throw SerializationException("Expected a valid YYYY-MM-DD JST day: $raw")
  }
}

/** iOS `RetentionCalendar`. */
object RetentionCalendar {
  val JST: ZoneId = ZoneId.of("Asia/Tokyo")

  /** The instant of [day] at [hour]:00 JST (noon by default). */
  fun date(day: JstDay, hour: Int = 12): Instant = day.localDate.atTime(hour, 0).atZone(JST).toInstant()

  fun days(endingAt: JstDay, count: Int): List<JstDay> =
    if (count <= 0) emptyList() else (count - 1 downTo 0).map { endingAt.adding(-it) }

  /** Monday…Sunday of the JST week containing [today]. */
  fun stampCardDays(asOf: JstDay): List<JstDay> {
    val daysSinceMonday = asOf.localDate.dayOfWeek.value - DayOfWeek.MONDAY.value
    val monday = asOf.adding(-daysSinceMonday)
    return (0 until 7).map { monday.adding(it) }
  }
}

enum class RetentionStampDayState(val localizationKey: String) {
  COMPLETED("retention.stamp.completed"),
  MISSED("retention.stamp.missed"),
  TODAY_PENDING("retention.stamp.today_pending"),
  UPCOMING("retention.stamp.upcoming");

  companion object {
    fun of(day: JstDay, today: JstDay, isStamped: Boolean): RetentionStampDayState = when {
      isStamped -> COMPLETED
      day < today -> MISSED
      day == today -> TODAY_PENDING
      else -> UPCOMING
    }
  }
}

/** iOS `MascotDailyEncouragement`: one cheer line per JST day, chosen by learning context. */
object MascotDailyEncouragement {
  enum class Context { BEFORE_STUDY, COMPLETED_TODAY, ACTIVE_STREAK, RETURNING }

  const val MESSAGE_COUNT = 10

  fun context(day: JstDay, completedDays: Set<JstDay>): Context {
    if (day in completedDays) return Context.COMPLETED_TODAY
    val mostRecent = completedDays.filter { it < day }.maxOrNull() ?: return Context.BEFORE_STUDY
    return if (mostRecent == day.adding(-1)) Context.ACTIVE_STREAK else Context.RETURNING
  }

  /** `mascot.daily_encouragement.<n>` */
  fun localizationKey(day: JstDay, completedDays: Set<JstDay>): String {
    val indices = messageIndices(context(day, completedDays))
    val remainder = day.ordinal % indices.size
    val position = if (remainder >= 0) remainder else remainder + indices.size
    return "mascot.daily_encouragement.${indices[position]}"
  }

  fun messageIndices(context: Context): List<Int> = when (context) {
    Context.BEFORE_STUDY -> listOf(0, 1, 2, 4)
    Context.COMPLETED_TODAY -> listOf(5, 6)
    Context.ACTIVE_STREAK -> listOf(3, 7)
    Context.RETURNING -> listOf(8, 9)
  }
}

@Serializable
enum class RetentionActivityKind(val raw: String) {
  @SerialName("curriculum") CURRICULUM("curriculum"),
  @SerialName("game") GAME("game"),
  @SerialName("daily_challenge") DAILY_CHALLENGE("daily_challenge"),
  @SerialName("quick_practice") QUICK_PRACTICE("quick_practice"),
}

@Serializable
data class RetentionDayRecord(
  val day: JstDay,
  val activities: List<RetentionActivityKind>,
) {
  val id: JstDay get() = day
  val isStamped: Boolean get() = activities.isNotEmpty()
  val completedDailyChallenge: Boolean get() = RetentionActivityKind.DAILY_CHALLENGE in activities
}

data class RetentionSnapshot(val records: Map<JstDay, RetentionDayRecord>) {
  val completedDays: Set<JstDay> get() = records.values.filter { it.isStamped }.mapTo(mutableSetOf()) { it.day }

  companion object {
    val EMPTY = RetentionSnapshot(emptyMap())
  }
}

data class RetentionStreak(val current: Int, val longest: Int) {
  companion object {
    fun calculate(completedDays: Set<JstDay>, asOf: JstDay): RetentionStreak {
      val anchor = when {
        asOf in completedDays -> asOf
        asOf.adding(-1) in completedDays -> asOf.adding(-1)
        else -> null
      }
      var current = 0
      var cursor = anchor
      while (cursor != null && cursor in completedDays) {
        current++
        cursor = cursor.adding(-1)
      }
      var longest = 0
      var running = 0
      var previous: JstDay? = null
      for (day in completedDays.sorted()) {
        running = if (previous?.adding(1) == day) running + 1 else 1
        longest = maxOf(longest, running)
        previous = day
      }
      return RetentionStreak(current, longest)
    }
  }
}

enum class RetentionMutation { INSERTED, UNCHANGED }

sealed class RetentionStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) : RetentionStoreException("Unsupported retention schema $version")
  data object InvalidRecord : RetentionStoreException("Invalid retention record")
}

object RetentionRules {
  const val CURRENT_SCHEMA_VERSION = 1

  /** Validates decoded records: non-empty, unique activities, unique days. */
  fun validated(records: List<RetentionDayRecord>): RetentionSnapshot {
    val result = LinkedHashMap<JstDay, RetentionDayRecord>()
    for (record in records) {
      if (record.activities.isEmpty() || record.activities.toSet().size != record.activities.size ||
        result.containsKey(record.day)
      ) {
        throw RetentionStoreException.InvalidRecord
      }
      result[record.day] = record
    }
    return RetentionSnapshot(result)
  }

  /** Adds [activity] to [day] (idempotent); activities are kept sorted by raw value. */
  fun record(
    snapshot: RetentionSnapshot,
    activity: RetentionActivityKind,
    day: JstDay,
  ): Pair<RetentionSnapshot, RetentionMutation> {
    val existing = snapshot.records[day] ?: RetentionDayRecord(day, emptyList())
    if (activity in existing.activities) return snapshot to RetentionMutation.UNCHANGED
    val updated = existing.copy(activities = (existing.activities + activity).sortedBy { it.raw })
    return RetentionSnapshot(snapshot.records + (day to updated)) to RetentionMutation.INSERTED
  }
}

/** Weekly (Mon–Sun JST) stamp-card rewards: 3, 5 and 7 stamped days unlock mascot props. */
enum class StampReward(val requiredDays: Int, val propRaw: String, val symbol: String) {
  THREE(3, "lightstick", "wand.and.stars"),
  FIVE(5, "ribbon", "rosette"),
  SEVEN(7, "headphones", "headphones");

  val conditionKey: String get() = "retention.rewards.$requiredDays.condition"
  val titleKey: String get() = "retention.rewards.$requiredDays.title"

  fun isEarned(stampedDaysThisWeek: Int): Boolean = stampedDaysThisWeek >= requiredDays

  companion object {
    /** Stamped days in the Monday–Sunday card containing [today]. */
    fun stampedDaysThisWeek(today: JstDay, isStamped: (JstDay) -> Boolean): Int =
      RetentionCalendar.stampCardDays(today).count(isStamped)
  }
}
