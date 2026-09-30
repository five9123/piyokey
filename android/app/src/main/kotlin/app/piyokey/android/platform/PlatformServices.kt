package app.piyokey.android.platform

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.audio.Pronunciation
import app.piyokey.android.platform.audio.SoundEngine
import app.piyokey.android.platform.billing.ProStore
import app.piyokey.android.platform.reminder.DailyReminder

/**
 * One-call wiring for the platform layer. Call from `PiyokeyApplication.onCreate()` after
 * `Services.install(this)`:
 *
 * ```
 * PlatformServices.install()
 * ```
 *
 * Foreground: `app_opened` (once per foreground), billing entitlement re-query.
 * Background: stop pronunciation immediately, release the sound pool, reset the app-open tracker.
 */
object PlatformServices {
  @Volatile private var installed = false

  fun install() {
    if (installed) return
    installed = true
    Telemetry.configure()
    app.piyokey.android.ui.mascot.MascotFeedbackHooks.chirp = { excited ->
      app.piyokey.android.platform.audio.SoundEngine.completion(if (excited) 10 else 2)
    }
    DailyReminder.rescheduleIfEnabled()
    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
      private var started = false

      override fun onStart(owner: LifecycleOwner) {
        Telemetry.sceneDidBecomeActive()
        if (started) {
          ProStore.refreshOnForeground()
        } else {
          // Keep Play Billing class loading and service binding off the first frame.
          started = true
          android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ ProStore.refreshOnForeground() }, 1_500)
        }
      }

      override fun onStop(owner: LifecycleOwner) {
        Pronunciation.stop()
        SoundEngine.suspendForInactivity()
        Telemetry.sceneDidEnterBackground()
      }
    })
  }
}
