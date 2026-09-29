package app.piyokey.android.platform.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.piyokey.android.R
import app.piyokey.android.Services
import app.piyokey.android.data.settings.BoolPref
import app.piyokey.android.data.settings.IntPref
import app.piyokey.android.data.settings.Prefs
import app.piyokey.android.platform.ReminderReceiver
import app.piyokey.android.ui.theme.L
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iOS `DailyReminderSettingsStore` keys. */
object DailyReminderPrefs {
  val enabled = BoolPref("retention.reminder.enabled", false)
  val hour = IntPref("retention.reminder.hour", ReminderSchedule.DEFAULT_HOUR)
  val minute = IntPref("retention.reminder.minute", ReminderSchedule.DEFAULT_MINUTE)

  /** Android-only guard so a late/early inexact alarm never notifies twice on one day. */
  internal const val LAST_FIRED_EPOCH_DAY = "retention.reminder.android_last_fired_epoch_day"
}

/**
 * One daily local notification at the user's hour/minute (iOS `DailyReminderLibrary` +
 * `SystemDailyReminderScheduler`). Uses an inexact `setAndAllowWhileIdle` alarm (no exact-alarm
 * permission) that is re-armed after each fire and on boot/time/zone/locale changes by
 * [ReminderReceiver], so the wall-clock time survives DST and travel.
 *
 * `POST_NOTIFICATIONS` (API 33+) can only be requested from UI: use
 * [rememberNotificationPermissionRequester] and pass it to [enableFromOnboarding]/[setEnabled].
 */
object DailyReminder {
  const val CHANNEL_ID = "daily_practice_reminder"
  const val NOTIFICATION_ID = 0x5059_4B01
  const val ACTION_FIRE = "app.piyokey.android.action.DAILY_REMINDER"
  private const val REQUEST_CODE = 4101

  private val preferenceState by lazy { MutableStateFlow(load()) }
  private val statusState = MutableStateFlow(DailyReminderStatus.IDLE)

  val preference: StateFlow<DailyReminderPreference> get() = preferenceState.asStateFlow()
  val status: StateFlow<DailyReminderStatus> = statusState.asStateFlow()

  val hasStoredEnabledPreference: Boolean get() = DailyReminderPrefs.enabled.isSet

  // Permission ---------------------------------------------------------------------------

  fun hasPermission(context: Context = Services.context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  /** True when a runtime `POST_NOTIFICATIONS` prompt is required before scheduling. */
  fun needsPermissionRequest(context: Context = Services.context): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

  /** Launches the system prompt with a launcher registered by the UI (no-op below API 33). */
  fun requestPermission(launcher: ActivityResultLauncher<String>) {
    if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  // Library API (iOS DailyReminderLibrary) ------------------------------------------------

  /**
   * Enables or disables the reminder. When enabling, [requestPermission] is awaited if the OS
   * prompt is needed (pass `null` from non-UI code; the result is then `DENIED`).
   */
  suspend fun setEnabled(enabled: Boolean, requestPermission: (suspend () -> Boolean)? = null): DailyReminderScheduleResult? {
    if (!enabled) {
      cancelAlarm()
      save(preferenceState.value.copy(isEnabled = false))
      statusState.value = DailyReminderStatus.IDLE
      return null
    }
    return scheduleAndApply(requestPermission)
  }

  /** iOS `enableFromOnboarding()`: asks for permission (if needed) and schedules. */
  suspend fun enableFromOnboarding(requestPermission: (suspend () -> Boolean)?): DailyReminderScheduleResult =
    scheduleAndApply(requestPermission)

  fun setTime(hour: Int, minute: Int) {
    if (!ReminderSchedule.isValid(hour, minute)) return
    val updated = preferenceState.value.copy(hour = hour, minute = minute)
    save(updated)
    if (updated.isEnabled) reschedule()
  }

  /** After an in-app language change: refresh channel name; content is localized at fire time. */
  fun refreshLocalizedContent() {
    if (!preferenceState.value.isEnabled) return
    ensureChannel(Services.context)
    reschedule()
  }

  /** Re-arms the alarm if enabled (app start, boot, time/zone/locale changes). */
  fun rescheduleIfEnabled() {
    preferenceState.value = load()
    if (preferenceState.value.isEnabled) reschedule()
  }

  private fun reschedule() {
    statusState.value = DailyReminderStatus.SCHEDULING
    apply(schedule(Services.context, preferenceState.value.hour, preferenceState.value.minute))
  }

  private suspend fun scheduleAndApply(requestPermission: (suspend () -> Boolean)?): DailyReminderScheduleResult {
    statusState.value = DailyReminderStatus.SCHEDULING
    val context = Services.context
    val granted = when {
      hasPermission(context) -> true
      needsPermissionRequest(context) && requestPermission != null ->
        runCatching { requestPermission() }.getOrDefault(false) && hasPermission(context)
      else -> false
    }
    val result = if (granted) {
      schedule(context, preferenceState.value.hour, preferenceState.value.minute)
    } else {
      DailyReminderScheduleResult.DENIED
    }
    apply(result)
    return result
  }

  private fun apply(result: DailyReminderScheduleResult) {
    val current = preferenceState.value
    when (result) {
      DailyReminderScheduleResult.SCHEDULED -> {
        save(current.copy(isEnabled = true))
        statusState.value = DailyReminderStatus.IDLE
      }
      DailyReminderScheduleResult.DENIED -> {
        cancelAlarm()
        save(current.copy(isEnabled = false))
        statusState.value = DailyReminderStatus.DENIED
      }
      DailyReminderScheduleResult.FAILED -> {
        cancelAlarm()
        save(current.copy(isEnabled = false))
        statusState.value = DailyReminderStatus.FAILED
      }
    }
  }

  // Scheduler ------------------------------------------------------------------------------

  private fun schedule(
    context: Context,
    hour: Int,
    minute: Int,
    afterFire: Boolean = false,
  ): DailyReminderScheduleResult {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return DailyReminderScheduleResult.FAILED
    ensureChannel(context)
    val trigger = nextTrigger(hour, minute, afterFire = afterFire)
    return runCatching {
      alarms.cancel(alarmIntent(context))
      alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), alarmIntent(context))
      DailyReminderScheduleResult.SCHEDULED
    }.getOrDefault(DailyReminderScheduleResult.FAILED)
  }

  internal fun nextTrigger(
    hour: Int,
    minute: Int,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
    afterFire: Boolean = false,
  ): ZonedDateTime {
    if (!afterFire) return ReminderSchedule.nextTrigger(now, hour, minute)
    val lastDay = Prefs.shared.getLong(DailyReminderPrefs.LAST_FIRED_EPOCH_DAY, Long.MIN_VALUE)
    val lastFired = if (lastDay == Long.MIN_VALUE) null else LocalDate.ofEpochDay(lastDay)
    return ReminderSchedule.nextTrigger(now, hour, minute, lastFired)
  }

  private fun cancelAlarm() {
    val context = Services.context
    context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
  }

  private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    REQUEST_CODE,
    Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
  )

  /** Called by [ReminderReceiver] when the alarm fires: post (once per day) and re-arm. */
  internal fun onAlarm(context: Context) {
    val preference = load()
    preferenceState.value = preference
    if (!preference.isEnabled) return
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val today = now.toLocalDate()
    val lastDay = Prefs.shared.getLong(DailyReminderPrefs.LAST_FIRED_EPOCH_DAY, Long.MIN_VALUE)
    if (lastDay != today.toEpochDay()) {
      if (post(context)) Prefs.shared.edit().putLong(DailyReminderPrefs.LAST_FIRED_EPOCH_DAY, today.toEpochDay()).apply()
    }
    schedule(context, preference.hour, preference.minute, afterFire = true)
  }

  @SuppressLint("MissingPermission") // checked by hasPermission()
  private fun post(context: Context): Boolean {
    if (!hasPermission(context)) return false
    ensureChannel(context)
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    val content = launch?.let {
      PendingIntent.getActivity(context, REQUEST_CODE, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(L.string(R.string.retention_reminder_notification_title))
      .setContentText(L.string(R.string.retention_reminder_notification_body))
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setAutoCancel(true)
      .setContentIntent(content)
      .build()
    return runCatching {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
      true
    }.getOrDefault(false)
  }

  private fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, L.string(R.string.retention_reminder_title), NotificationManager.IMPORTANCE_DEFAULT),
    )
  }

  // Persistence ----------------------------------------------------------------------------

  private fun load() = DailyReminderPreference(
    isEnabled = DailyReminderPrefs.enabled.value,
    hour = DailyReminderPrefs.hour.value,
    minute = DailyReminderPrefs.minute.value,
  )

  private fun save(preference: DailyReminderPreference) {
    DailyReminderPrefs.enabled.value = preference.isEnabled
    DailyReminderPrefs.hour.value = preference.hour
    DailyReminderPrefs.minute.value = preference.minute
    preferenceState.value = preference
  }

  /** For tests/debug: epoch millis of the next trigger for the stored time. */
  fun nextTriggerMillis(): Long = nextTrigger(preferenceState.value.hour, preferenceState.value.minute).toInstant().toEpochMilli()
}

/**
 * Compose helper returning a suspending permission request for [DailyReminder.setEnabled] /
 * [DailyReminder.enableFromOnboarding]. Must be called from a composable inside an Activity.
 */
@Composable
fun rememberNotificationPermissionRequester(): suspend () -> Boolean {
  val holder = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    holder[0]?.complete(granted)
    holder[0] = null
  }
  return remember(launcher) {
    suspend {
      if (!DailyReminder.needsPermissionRequest()) {
        DailyReminder.hasPermission()
      } else {
        val deferred = CompletableDeferred<Boolean>()
        holder[0]?.complete(false)
        holder[0] = deferred
        DailyReminder.requestPermission(launcher)
        deferred.await()
      }
    }
  }
}
