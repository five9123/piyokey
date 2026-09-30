package app.piyokey.android.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.piyokey.android.platform.reminder.DailyReminder

/**
 * Daily reminder alarm + system re-arm receiver. The alarm action posts the notification and
 * schedules the next day; boot/time/timezone/locale changes recompute the next wall-clock trigger.
 */
class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      DailyReminder.ACTION_FIRE -> DailyReminder.onAlarm(context.applicationContext)
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_TIMEZONE_CHANGED,
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_LOCALE_CHANGED,
      -> DailyReminder.rescheduleIfEnabled()
    }
  }
}
