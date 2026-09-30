package app.piyokey.android.feature.settings

import app.piyokey.android.data.settings.PracticeDisplayPreset
import app.piyokey.android.data.settings.PracticePromptField
import app.piyokey.android.data.settings.PracticePromptOrder
import app.piyokey.android.feature.input.SessionInputMode

/** Practice-display toggles written by a preset (iOS `SettingsView.applyPracticePreset`). */
data class PracticeDisplayToggles(
  val showsTarget: Boolean,
  val showsMeaning: Boolean,
  val showsReading: Boolean,
  val showsJamo: Boolean,
  val showsMascot: Boolean,
  val showsComposition: Boolean,
)

/** Pure rules of the settings screen, kept free of Android types for JVM tests. */
object SettingsRules {
  /** Section identifiers in display order (iOS `settingsContent`, PRD F10). */
  val sectionOrder = listOf(
    "settings.section.keyboard",
    "settings.section.sound",
    "settings.section.practice_display",
    "settings.section.display",
    "settings.section.reminder",
    "settings.section.mascot",
    "settings.section.privacy",
    "settings.section.app_information",
  )

  /** Minutes offered by the reminder picker (iOS `[0, 15, 30, 45]`). */
  val reminderMinutes = listOf(0, 15, 30, 45)

  /** Hours offered by the reminder picker. */
  val reminderHours = (0 until 24).toList()

  /** Opacity of a disabled control (iOS `.opacity(0.45)`). */
  const val DISABLED_ALPHA = 0.45f

  /** How long "Copied" stays visible after copying the version (iOS 1.5 s). */
  const val VERSION_COPIED_MILLIS = 1_500L

  fun toggles(preset: PracticeDisplayPreset): PracticeDisplayToggles = when (preset) {
    PracticeDisplayPreset.LEARNING -> PracticeDisplayToggles(
      showsTarget = true, showsMeaning = true, showsReading = false,
      showsJamo = true, showsMascot = true, showsComposition = true,
    )
    PracticeDisplayPreset.FOCUS -> PracticeDisplayToggles(
      showsTarget = true, showsMeaning = false, showsReading = false,
      showsJamo = false, showsMascot = false, showsComposition = true,
    )
  }

  /** The built-in layout control is disabled while the default input mode is the OS keyboard. */
  fun isLayoutEnabled(inputMode: SessionInputMode): Boolean = inputMode != SessionInputMode.OS_IME

  /** iOS `PracticePromptOrder.localizedLabel`: field names joined with " → ". */
  fun promptOrderLabel(order: PracticePromptOrder, fieldName: (PracticePromptField) -> String): String =
    order.fields.joinToString(" → ") { fieldName(it) }

  /** `%02d` for the reminder pickers. */
  fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

  /** Analytics value of the sound toggle. */
  fun enabledBucket(enabled: Boolean): String = if (enabled) "enabled" else "disabled"
}
