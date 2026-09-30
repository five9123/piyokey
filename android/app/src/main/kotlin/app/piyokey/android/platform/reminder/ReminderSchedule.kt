package app.piyokey.android.platform.reminder

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** iOS `DailyReminderPreference`. */
data class DailyReminderPreference(val isEnabled: Boolean, val hour: Int, val minute: Int)

/** iOS `DailyReminderScheduleResult`. */
enum class DailyReminderScheduleResult { SCHEDULED, DENIED, FAILED }

/** iOS `DailyReminderStatus`. */
enum class DailyReminderStatus { IDLE, SCHEDULING, DENIED, FAILED }

/**
 * Pure next-trigger computation. Like iOS `UNCalendarNotificationTrigger(dateMatching: hour/minute,
 * repeats: true)` the reminder follows the *wall clock* of the current zone, so it is recomputed
 * in the device zone every time (after each fire, boot, time or zone change).
 */
object ReminderSchedule {
  const val DEFAULT_HOUR = 20
  const val DEFAULT_MINUTE = 0

  fun isValid(hour: Int, minute: Int) = hour in 0..23 && minute in 0..59

  /**
   * First occurrence of [hour]:[minute] strictly after [now] in `now.zone`. A time inside a DST
   * gap resolves to the instant just after the gap (java.time rules); an ambiguous overlap time
   * uses the earlier offset, so the reminder fires once.
   */
  fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    require(isValid(hour, minute))
    val time = LocalTime.of(hour, minute)
    val today = at(now.toLocalDate(), time, now)
    return if (today.isAfter(now)) today else at(now.toLocalDate().plusDays(1), time, now)
  }

  /** Same as [nextTrigger] but skips a day that has already been notified ([lastFiredDate]). */
  fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int, lastFiredDate: LocalDate?): ZonedDateTime {
    var next = nextTrigger(now, hour, minute)
    while (lastFiredDate != null && !next.toLocalDate().isAfter(lastFiredDate)) {
      next = at(next.toLocalDate().plusDays(1), LocalTime.of(hour, minute), now)
    }
    return next
  }

  private fun at(date: LocalDate, time: LocalTime, now: ZonedDateTime): ZonedDateTime =
    ZonedDateTime.ofLocal(date.atTime(time), now.zone, null)
}
