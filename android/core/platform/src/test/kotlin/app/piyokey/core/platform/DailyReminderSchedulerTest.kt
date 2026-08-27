package app.piyokey.core.platform

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyReminderSchedulerTest {
  @Test
  fun nextReminderUsesTheCurrentLocalWallClock() {
    val tokyo = ZoneId.of("Asia/Tokyo")
    val beforeWorkdayEnd = ZonedDateTime.of(2026, 8, 27, 19, 59, 30, 0, tokyo)
    val afterWorkdayEnd = ZonedDateTime.of(2026, 8, 27, 20, 1, 0, 0, tokyo)

    assertEquals(
      ZonedDateTime.of(2026, 8, 27, 20, 0, 0, 0, tokyo),
      nextDailyReminder(beforeWorkdayEnd, 20, 0),
    )
    assertEquals(
      ZonedDateTime.of(2026, 8, 28, 20, 0, 0, 0, tokyo),
      nextDailyReminder(afterWorkdayEnd, 20, 0),
    )
  }

  @Test
  fun nextReminderKeepsTwentyHundredAcrossDaylightSavingChange() {
    val newYork = ZoneId.of("America/New_York")
    val beforeSpringForward = ZonedDateTime.of(2026, 3, 7, 20, 1, 0, 0, newYork)

    val next = nextDailyReminder(beforeSpringForward, 20, 0)

    assertEquals(20, next.hour)
    assertEquals(0, next.minute)
    assertEquals(ZonedDateTime.of(2026, 3, 8, 20, 0, 0, 0, newYork), next)
  }
}
