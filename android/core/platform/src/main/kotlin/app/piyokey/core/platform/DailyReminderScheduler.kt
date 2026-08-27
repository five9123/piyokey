package app.piyokey.core.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.piyokey.core.data.PiyokeyDatabase
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyReminderScheduler(private val context: Context) {
  private val alarmManager = context.getSystemService(AlarmManager::class.java)

  fun schedule(hour: Int, minute: Int) {
    require(hour in 0..23 && minute in 0..59)
    val now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"))
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    alarmManager.setInexactRepeating(
      AlarmManager.RTC_WAKEUP,
      next.toInstant().toEpochMilli(),
      AlarmManager.INTERVAL_DAY,
      pendingIntent(),
    )
  }

  fun cancel() {
    alarmManager.cancel(pendingIntent())
  }

  private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
    context,
    REQUEST_CODE,
    Intent(context, DailyReminderReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
  )

  private companion object { const val REQUEST_CODE = 7912 }
}

class DailyReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.reminder_channel_name),
          NotificationManager.IMPORTANCE_DEFAULT,
        ),
      )
    }
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(context.getString(R.string.reminder_notification_title))
      .setContentText(context.getString(R.string.reminder_notification_body))
      .setContentIntent(
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
          PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        },
      )
      .setAutoCancel(true)
      .build()
    manager.notify(NOTIFICATION_ID, notification)
  }

  private companion object {
    const val CHANNEL_ID = "piyokey_daily_practice"
    const val NOTIFICATION_ID = 7912
  }
}

class DailyReminderBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        PiyokeyDatabase.open(context).dao().reminderPreference()?.takeIf { it.isEnabled }?.let { preference ->
          DailyReminderScheduler(context).schedule(preference.hour, preference.minute)
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
