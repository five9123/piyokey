package app.piyokey.android.feature.input

import app.piyokey.android.data.settings.BoolPref
import app.piyokey.android.data.settings.StringPref

/**
 * Keyboard preferences shared by Settings and every session. Keys and defaults are 1:1 with
 * iOS `KeyboardPreferenceKeys` / `@AppStorage` defaults (`HangulKeyboardView.swift`).
 */
object KeyboardPreferences {
  const val SHOWS_KEY_GUIDE = "keyboard.shows_key_guide"
  const val SHOWS_ROMAN_HINTS = "keyboard.shows_roman_hints"
  const val HAPTICS_ENABLED = "keyboard.haptics_enabled"
  const val INPUT_MODE_DEFAULT = "keyboard.input_mode_default"
  const val SHOWS_PHYSICAL_KEYBOARD_GUIDE = "keyboard.shows_physical_keyboard_guide"
  const val BUILT_IN_LAYOUT_DEFAULT = "keyboard.builtin_layout_default"

  val ALL_KEYS = listOf(
    SHOWS_KEY_GUIDE,
    SHOWS_ROMAN_HINTS,
    HAPTICS_ENABLED,
    INPUT_MODE_DEFAULT,
    SHOWS_PHYSICAL_KEYBOARD_GUIDE,
    BUILT_IN_LAYOUT_DEFAULT,
  )

  val showsKeyGuide = BoolPref(SHOWS_KEY_GUIDE, true)
  val showsRomanHints = BoolPref(SHOWS_ROMAN_HINTS, true)
  val hapticsEnabled = BoolPref(HAPTICS_ENABLED, true)

  /** Raw stored value; read through [defaultInputMode]. Only `builtin` or `os_ime` are persisted. */
  val inputModeDefault = StringPref(INPUT_MODE_DEFAULT, SessionInputMode.BUILT_IN.raw)

  /** iOS `defaults.bool(forKey:)` → false when unset. Only shown on tablets (see [PhysicalKeyboardGuidePolicy]). */
  val showsPhysicalKeyboardGuide = BoolPref(SHOWS_PHYSICAL_KEYBOARD_GUIDE, false)

  /** Raw stored value; read through [builtInLayout]. */
  val builtInLayoutDefault = StringPref(BUILT_IN_LAYOUT_DEFAULT, BuiltInKeyboardLayout.DUBEOLSIK.raw)

  /** iOS `KeyboardPreferenceStore.snapshot`. */
  val snapshot: KeyboardPreferenceSnapshot
    get() = KeyboardPreferenceSnapshot(
      defaultInputMode = defaultInputMode,
      builtInLayout = builtInLayout,
      showsPhysicalKeyboardGuide = showsPhysicalKeyboardGuide.value,
    )

  val defaultInputMode: SessionInputMode
    get() = SessionInputMode.resolvedDefault(inputModeDefault.value)

  val builtInLayout: BuiltInKeyboardLayout
    get() = BuiltInKeyboardLayout.resolved(builtInLayoutDefault.value)

  /** Current [HangulKeyboardOptions] (key guide, roman hints, haptics). */
  val options: HangulKeyboardOptions
    get() = HangulKeyboardOptions(showsKeyGuide.value, showsRomanHints.value, hapticsEnabled.value)

  /** iOS `setDefaultInputMode`: `builtin_korean_10key` is record-only and persists as `builtin`. */
  fun setDefaultInputMode(mode: SessionInputMode) {
    inputModeDefault.value = SessionInputMode.persistedDefault(mode).raw
  }

  fun setBuiltInLayout(layout: BuiltInKeyboardLayout) {
    builtInLayoutDefault.value = layout.raw
  }

  fun setShowsPhysicalKeyboardGuide(enabled: Boolean) {
    showsPhysicalKeyboardGuide.value = enabled
  }

  /** iOS `repairInvalidValues()`: rewrites unknown raw values with their resolved defaults. */
  fun repairInvalidValues(): KeyboardPreferenceSnapshot {
    val resolved = snapshot
    setDefaultInputMode(resolved.defaultInputMode)
    setBuiltInLayout(resolved.builtInLayout)
    return resolved
  }
}

data class KeyboardPreferenceSnapshot(
  val defaultInputMode: SessionInputMode,
  val builtInLayout: BuiltInKeyboardLayout,
  val showsPhysicalKeyboardGuide: Boolean,
)

/** iOS `HangulKeyboardOptions`. */
data class HangulKeyboardOptions(
  val showsKeyGuide: Boolean = true,
  val showsRomanHints: Boolean = true,
  val hapticsEnabled: Boolean = true,
)

/** iOS `BuiltInKeyboardLayout`. */
enum class BuiltInKeyboardLayout(val raw: String) {
  DUBEOLSIK("dubeolsik"),
  KOREAN_10KEY("korean_10key");

  /** Input mode written to game records / analytics for this built-in layout. */
  val gameRecordInputMode: SessionInputMode
    get() = when (this) {
      DUBEOLSIK -> SessionInputMode.BUILT_IN
      KOREAN_10KEY -> SessionInputMode.BUILT_IN_KOREAN_10KEY
    }

  companion object {
    fun resolved(raw: String?): BuiltInKeyboardLayout = entries.firstOrNull { it.raw == raw } ?: DUBEOLSIK
  }
}

/**
 * iOS `SessionInputMode` (`Core/Progress/GameProgressStore.swift`). `raw` is the persisted /
 * analytics value. [BUILT_IN_KOREAN_10KEY] is record-only: sessions run in [BUILT_IN] with the
 * 10-key layout, and the user default never stores it.
 */
enum class SessionInputMode(val raw: String) {
  BUILT_IN("builtin"),
  BUILT_IN_KOREAN_10KEY("builtin_korean_10key"),
  OS_IME("os_ime");

  /** iOS `resultLabelKey` (`input_mode.*`). */
  val labelKey: String get() = "input_mode.$raw"

  companion object {
    /** Strict raw lookup (iOS `init?(rawValue:)`). */
    fun fromRaw(raw: String?): SessionInputMode? = entries.firstOrNull { it.raw == raw }

    /**
     * Decodes stored records, accepting legacy `built_in` / `os_keyboard` (iOS `init(from:)`).
     * Returns null for unknown values (iOS throws `DecodingError`).
     */
    fun decode(raw: String?): SessionInputMode? = when (raw) {
      BUILT_IN.raw, "built_in" -> BUILT_IN
      BUILT_IN_KOREAN_10KEY.raw -> BUILT_IN_KOREAN_10KEY
      OS_IME.raw, "os_keyboard" -> OS_IME
      else -> null
    }

    /** iOS `KeyboardPreferenceStore.resolvedDefaultInputMode`: only exact `builtin`/`os_ime`. */
    fun resolvedDefault(raw: String?): SessionInputMode = when (fromRaw(raw)) {
      OS_IME -> OS_IME
      else -> BUILT_IN
    }

    fun persistedDefault(mode: SessionInputMode): SessionInputMode = if (mode == OS_IME) OS_IME else BUILT_IN
  }
}
