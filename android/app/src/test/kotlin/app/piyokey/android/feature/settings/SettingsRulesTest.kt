package app.piyokey.android.feature.settings

import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.PracticeDisplayPreset
import app.piyokey.android.data.settings.PracticePromptOrder
import app.piyokey.android.feature.input.SessionInputMode
import app.piyokey.android.platform.analytics.AnalyticsContract
import app.piyokey.android.platform.analytics.AnalyticsProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRulesTest {
  @Test
  fun sectionOrderMatchesPrd() {
    assertEquals(
      listOf("keyboard", "sound", "practice_display", "display", "reminder", "mascot", "privacy", "app_information"),
      SettingsRules.sectionOrder.map { it.removePrefix("settings.section.") },
    )
  }

  @Test
  fun learningPresetMatchesIos() {
    val t = SettingsRules.toggles(PracticeDisplayPreset.LEARNING)
    assertEquals(PracticeDisplayToggles(true, true, false, true, true, true), t)
  }

  @Test
  fun focusPresetMatchesIos() {
    val t = SettingsRules.toggles(PracticeDisplayPreset.FOCUS)
    assertEquals(PracticeDisplayToggles(true, false, false, false, false, true), t)
  }

  @Test
  fun layoutControlDisabledOnlyForOsKeyboard() {
    assertTrue(SettingsRules.isLayoutEnabled(SessionInputMode.BUILT_IN))
    assertTrue(SettingsRules.isLayoutEnabled(SessionInputMode.BUILT_IN_KOREAN_10KEY))
    assertFalse(SettingsRules.isLayoutEnabled(SessionInputMode.OS_IME))
  }

  @Test
  fun promptOrderLabelJoinsFieldNamesWithArrow() {
    val label = SettingsRules.promptOrderLabel(PracticePromptOrder.MEANING_READING_TARGET) { it.raw }
    assertEquals("meaning → reading → target", label)
  }

  @Test
  fun reminderPickerValues() {
    assertEquals(listOf(0, 15, 30, 45), SettingsRules.reminderMinutes)
    assertEquals(24, SettingsRules.reminderHours.size)
    assertEquals("08", SettingsRules.twoDigits(8))
    assertEquals("20", SettingsRules.twoDigits(20))
  }

  @Test
  fun settingChangedValuesAreInAnalyticsContract() {
    val settings = AnalyticsContract.enumValues.getValue(AnalyticsProperty.SETTING)
    val buckets = AnalyticsContract.enumValues.getValue(AnalyticsProperty.VALUE_BUCKET)
    listOf("language", "theme", "input_mode", "practice_display", "sound", "analytics_consent", "diagnostics_consent")
      .forEach { assertTrue(it in settings, it) }
    val values = AppLanguage.entries.map { it.raw } + AppTheme.entries.map { it.raw } +
      PracticeDisplayPreset.entries.map { it.raw } +
      listOf(SessionInputMode.BUILT_IN.raw, SessionInputMode.OS_IME.raw) +
      listOf(SettingsRules.enabledBucket(true), SettingsRules.enabledBucket(false))
    values.forEach { assertTrue(it in buckets, it) }
  }
}
