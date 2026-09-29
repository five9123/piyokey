package app.piyokey.android.feature.input

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * iOS `KoreanKeyboardAvailability`. Android cannot reliably tell whether a keyboard can type
 * Korean (Samsung Keyboard, for example, keeps its languages private), so this is a **hint only**:
 * it drives the "no Korean keyboard detected" banner and must never block OS-IME mode.
 */
object KoreanKeyboardAvailability {
  /** Test/debug override (iOS `UITEST_KOREAN_KEYBOARD_AVAILABLE`). `null` = detect. */
  @Volatile
  var overrideForTesting: Boolean? = null

  /** Keyboards that ship Korean layouts without exposing them as IME subtypes. */
  private val KOREAN_CAPABLE_PACKAGES = setOf(
    "com.samsung.android.honeyboard", // Samsung Keyboard
    "com.sec.android.inputmethod", // Samsung Keyboard (legacy)
    "com.google.android.inputmethod.korean", // Google Korean Input (legacy)
    "com.nhn.android.keyboard", // Naver SmartBoard
    "com.lge.ime", // LG Keyboard
  )

  fun isAvailable(context: Context): Boolean {
    overrideForTesting?.let { return it }
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
    return runCatching {
      imm.enabledInputMethodList.any { info ->
        if (info.packageName in KOREAN_CAPABLE_PACKAGES) return@any true
        val subtypes = imm.getEnabledInputMethodSubtypeList(info, true)
        containsKorean(subtypes.map { subtype -> subtype.languageTag.ifEmpty { @Suppress("DEPRECATION") subtype.locale } })
      }
    }.getOrDefault(false)
  }

  /** iOS `containsKorean(languages:)`: any language tag / locale starting with `ko`. */
  fun containsKorean(languages: List<String?>): Boolean =
    languages.any { it?.lowercase()?.startsWith("ko") == true }

  /** System screen listing on-screen keyboards (Settings → System → Languages & input). */
  fun keyboardSettingsIntent(): Intent =
    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
