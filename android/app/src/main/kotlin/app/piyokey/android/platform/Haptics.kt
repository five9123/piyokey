package app.piyokey.android.platform

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import app.piyokey.android.Services
import app.piyokey.android.data.settings.BoolPref

/** iOS `KeyboardPreferenceKeys.hapticsEnabled` — a single shared cache with [KeyboardPreferences]. */
object HapticsPrefs {
  val enabled: BoolPref get() = app.piyokey.android.feature.input.KeyboardPreferences.hapticsEnabled
}

/**
 * Light impact feedback (iOS `UIImpactFeedbackGenerator(style: .light)`), gated by
 * `keyboard.haptics_enabled`. Prefer [light] with a [View] (uses the system haptic setting and needs
 * no permission); the view-less variant uses the vibrator (`VIBRATE` is declared).
 */
object Haptics {
  val isEnabled: Boolean get() = HapticsPrefs.enabled.value

  fun light(view: View) {
    if (!isEnabled) return
    val constant = if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
    view.performHapticFeedback(constant)
  }

  /** Soft/rigid impacts map to the same short tick on Android. */
  fun light() {
    if (!isEnabled || !Services.isInstalled) return
    val vibrator = vibrator() ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
      if (Build.VERSION.SDK_INT >= 29) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
      } else {
        vibrator.vibrate(VibrationEffect.createOneShot(10, 40))
      }
    }
  }

  private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
    Services.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
  } else {
    @Suppress("DEPRECATION")
    Services.context.getSystemService(Vibrator::class.java)
  }
}
