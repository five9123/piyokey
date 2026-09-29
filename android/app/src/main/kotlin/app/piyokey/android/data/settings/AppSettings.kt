package app.piyokey.android.data.settings

import java.util.Locale

/** Mirrors iOS `SettingsPreferenceKeys` + enums in `Core/Settings/AppSettings.swift`. */
object AppSettings {
  val fontScale = StringPref("settings.font_scale", FontScale.STANDARD.raw)
  val theme = StringPref("settings.theme", AppTheme.LIGHT.raw)
  val language = StringPref("settings.language", AppLanguage.preferred().raw)
  val practiceDisplayPreset = StringPref("settings.practice_display_preset", PracticeDisplayPreset.LEARNING.raw)
  val practiceShowsTarget = BoolPref("settings.practice_shows_target", true)
  val practiceShowsMeaning = BoolPref("settings.practice_shows_meaning", true)
  val practiceShowsReading = BoolPref("settings.practice_shows_reading", false)
  val practicePromptOrder = StringPref("settings.practice_prompt_order", PracticePromptOrder.TARGET_MEANING_READING.raw)
  val practiceShowsJamo = BoolPref("settings.practice_shows_jamo", true)
  val practiceAutoSpeaks = BoolPref("settings.practice_auto_speaks", false)
  val practiceShowsMascot = BoolPref("settings.practice_shows_mascot", true)
  val practiceShowsComposition = BoolPref("settings.practice_shows_composition", true)
  val choseongShowsMeaning = BoolPref("settings.choseong_shows_meaning", true)
  val anonymousAnalyticsEnabled = BoolPref("settings.anonymous_analytics_enabled", false)
  val crashDiagnosticsEnabled = BoolPref("settings.crash_diagnostics_enabled", false)
  val privacyNoticeVersion = IntPref("settings.privacy_notice_version", 0)

  val currentLanguage: AppLanguage get() = AppLanguage.resolved(language.value)

  /** Stored `ko` UI preference (retired) becomes English; learning data is untouched. */
  fun migrateLegacyLanguage() {
    if (language.isSet && language.value == "ko") language.value = AppLanguage.ENGLISH.raw
    if (!language.isSet) language.value = AppLanguage.preferred().raw
  }
}

object PrivacyNoticePolicy {
  const val CURRENT_VERSION = 2

  fun allowsUsageContext(analyticsEnabled: Boolean, reviewedVersion: Int) =
    analyticsEnabled && reviewedVersion >= CURRENT_VERSION

  fun diagnosticsAfterNotice(participate: Boolean, reviewedVersion: Int, previousDiagnostics: Boolean) =
    if (reviewedVersion > 0) previousDiagnostics else participate

  fun shouldPresent(
    reviewedVersion: Int,
    onboardingCompleted: Boolean,
    appTourCompleted: Boolean,
    sessionIsActive: Boolean = false,
    hasBlockingPresentation: Boolean = false,
  ) = reviewedVersion < CURRENT_VERSION && onboardingCompleted && appTourCompleted &&
    !sessionIsActive && !hasBlockingPresentation
}

enum class PracticePromptField(val raw: String) { TARGET("target"), MEANING("meaning"), READING("reading") }

enum class PracticePromptOrder(val raw: String, val fields: List<PracticePromptField>) {
  TARGET_MEANING_READING("target_meaning_reading", listOf(PracticePromptField.TARGET, PracticePromptField.MEANING, PracticePromptField.READING)),
  TARGET_READING_MEANING("target_reading_meaning", listOf(PracticePromptField.TARGET, PracticePromptField.READING, PracticePromptField.MEANING)),
  MEANING_TARGET_READING("meaning_target_reading", listOf(PracticePromptField.MEANING, PracticePromptField.TARGET, PracticePromptField.READING)),
  MEANING_READING_TARGET("meaning_reading_target", listOf(PracticePromptField.MEANING, PracticePromptField.READING, PracticePromptField.TARGET)),
  READING_TARGET_MEANING("reading_target_meaning", listOf(PracticePromptField.READING, PracticePromptField.TARGET, PracticePromptField.MEANING)),
  READING_MEANING_TARGET("reading_meaning_target", listOf(PracticePromptField.READING, PracticePromptField.MEANING, PracticePromptField.TARGET));

  companion object {
    fun resolved(raw: String) = entries.firstOrNull { it.raw == raw } ?: TARGET_MEANING_READING
  }
}

enum class PracticeDisplayPreset(val raw: String) {
  LEARNING("learning"), FOCUS("focus");

  companion object {
    fun resolved(raw: String) = entries.firstOrNull { it.raw == raw } ?: LEARNING
  }
}

enum class FontScale(val raw: String, val multiplier: Float) {
  SMALL("small", 0.88f), STANDARD("standard", 1f), LARGE("large", 1.16f);

  companion object {
    fun resolved(raw: String) = entries.firstOrNull { it.raw == raw } ?: STANDARD
  }
}

enum class AppTheme(val raw: String) {
  LIGHT("light"), DARK("dark");

  companion object {
    fun resolved(raw: String) = entries.firstOrNull { it.raw == raw } ?: LIGHT
  }
}

enum class AppLanguage(val raw: String, val locale: Locale) {
  JAPANESE("ja", Locale.JAPAN),
  ENGLISH("en", Locale.US),
  SPANISH("es", Locale.forLanguageTag("es-ES")),
  GERMAN("de", Locale.GERMANY),
  FRENCH("fr", Locale.FRANCE);

  /** Localization key of the language's display name (e.g. `settings.language.japanese`). */
  val nameKey: String
    get() = when (this) {
      JAPANESE -> "settings.language.japanese"
      ENGLISH -> "settings.language.english"
      SPANISH -> "settings.language.spanish"
      GERMAN -> "settings.language.german"
      FRENCH -> "settings.language.french"
    }

  companion object {
    fun resolved(raw: String) = entries.firstOrNull { it.raw == raw } ?: ENGLISH

    /** First supported device language, English otherwise. */
    fun preferred(identifiers: List<String> = deviceLanguageTags()): AppLanguage {
      for (identifier in identifiers) {
        val code = Locale.forLanguageTag(identifier).language.ifEmpty { identifier.substringBefore('-') }
        entries.firstOrNull { it.raw == code }?.let { return it }
      }
      return ENGLISH
    }

    private fun deviceLanguageTags(): List<String> {
      val list = android.os.LocaleList.getDefault()
      return (0 until list.size()).map { list[it].toLanguageTag() }
    }
  }
}
