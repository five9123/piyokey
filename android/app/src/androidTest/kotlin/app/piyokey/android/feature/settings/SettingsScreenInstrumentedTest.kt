package app.piyokey.android.feature.settings

import android.Manifest
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.piyokey.android.R
import app.piyokey.android.data.settings.AppLanguage
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.AppTheme
import app.piyokey.android.data.settings.FontScale
import app.piyokey.android.data.settings.Prefs
import app.piyokey.android.data.settings.PrivacyNoticePolicy
import app.piyokey.android.feature.input.KeyboardPreferences
import app.piyokey.android.feature.input.SessionInputMode
import app.piyokey.android.platform.reminder.DailyReminder
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.Navigator
import app.piyokey.android.ui.theme.L
import app.piyokey.android.ui.theme.LocalizedApp
import app.piyokey.android.ui.theme.PiyokeyTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenInstrumentedTest {
  @get:Rule val rule = createComposeRule()

  private lateinit var saved: Map<String, *>

  @Before
  fun setUp() {
    saved = HashMap(Prefs.shared.all)
    AppSettings.language.value = AppLanguage.ENGLISH.raw
    AppSettings.anonymousAnalyticsEnabled.value = false
    AppSettings.crashDiagnosticsEnabled.value = false
    AppSettings.privacyNoticeVersion.value = 0
    KeyboardPreferences.setDefaultInputMode(SessionInputMode.BUILT_IN)
    runBlocking { DailyReminder.setEnabled(false) }
  }

  @After
  fun tearDown() {
    runBlocking { DailyReminder.setEnabled(false) }
    val editor = Prefs.shared.edit().clear()
    saved.forEach { (key, value) ->
      when (value) {
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        is String -> editor.putString(key, value)
        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
      }
    }
    editor.commit()
    listOf(
      AppSettings.language, AppSettings.anonymousAnalyticsEnabled, AppSettings.crashDiagnosticsEnabled,
      AppSettings.privacyNoticeVersion, KeyboardPreferences.inputModeDefault, KeyboardPreferences.builtInLayoutDefault,
      AppSettings.practiceDisplayPreset, AppSettings.practiceShowsTarget, AppSettings.practiceShowsMeaning,
      AppSettings.practiceShowsReading, AppSettings.practiceShowsJamo, AppSettings.practiceShowsMascot,
      AppSettings.practiceShowsComposition,
    ).forEach { it.reload() }
    DailyReminder.rescheduleIfEnabled()
  }

  private fun setSettings() {
    rule.setContent {
      val languageRaw by AppSettings.language.flow.collectAsState()
      val navigator = remember { Navigator() }
      CompositionLocalProvider(LocalAppNavigator provides navigator) {
        LocalizedApp(AppLanguage.resolved(languageRaw)) {
          PiyokeyTheme(AppTheme.LIGHT, FontScale.STANDARD) {
            SettingsScreen(showsCloseButton = true)
          }
        }
      }
    }
  }

  @Test
  fun sectionsAppearInIosOrder() {
    setSettings()
    val tops = SettingsRules.sectionOrder.map { tag ->
      rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
    }
    assertEquals(tops.sorted(), tops)
    assertEquals(tops.size, tops.toSet().size)
    rule.onNodeWithTag("settings.done").assertIsDisplayed()
  }

  @Test
  fun osKeyboardDisablesLayoutControl() {
    setSettings()
    rule.onNodeWithTag("settings.builtin_keyboard_layout.korean_10key").assertIsEnabled()
    rule.onNodeWithTag("input_mode.os_ime").performClick()
    rule.runOnIdle { assertEquals(SessionInputMode.OS_IME, KeyboardPreferences.defaultInputMode) }
    rule.onNodeWithTag("settings.builtin_keyboard_layout.korean_10key", useUnmergedTree = true).assertIsNotEnabled()
    rule.onNodeWithTag("settings.builtin_keyboard_layout.dubeolsik", useUnmergedTree = true).assertIsNotEnabled()
  }

  @Test
  fun layoutSelectionPersists() {
    setSettings()
    rule.onNodeWithTag("settings.builtin_keyboard_layout.korean_10key").performClick()
    rule.onNodeWithTag("settings.builtin_keyboard_layout.korean_10key").assertIsSelected()
    assertEquals("korean_10key", Prefs.shared.getString(KeyboardPreferences.BUILT_IN_LAYOUT_DEFAULT, null))
  }

  @Test
  fun changingLanguageToJapaneseRelocalizesUi() {
    setSettings()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val english = L.localizedContext(context, AppLanguage.ENGLISH).getString(R.string.settings_navigation_title)
    val japanese = L.localizedContext(context, AppLanguage.JAPANESE).getString(R.string.settings_navigation_title)
    rule.onNodeWithText(english).assertIsDisplayed()
    rule.onNodeWithTag("settings.language.ja").performScrollTo().performClick()
    rule.onNodeWithText(japanese).assertIsDisplayed()
    rule.onNodeWithTag("settings.language.ja").assertIsSelected()
    assertEquals("ja", Prefs.shared.getString("settings.language", null))
  }

  @Test
  fun privacyTogglesAreIndependentAndPersist() {
    setSettings()
    rule.onNodeWithTag("settings.anonymous_analytics").performScrollTo().assertIsOff().performClick()
    rule.onNodeWithTag("settings.anonymous_analytics").assertIsOn()
    rule.onNodeWithTag("settings.crash_diagnostics").performScrollTo().assertIsOff()
    assertTrue(Prefs.shared.getBoolean("settings.anonymous_analytics_enabled", false))
    assertFalse(Prefs.shared.getBoolean("settings.crash_diagnostics_enabled", false))
    assertEquals(PrivacyNoticePolicy.CURRENT_VERSION, Prefs.shared.getInt("settings.privacy_notice_version", 0))

    rule.onNodeWithTag("settings.crash_diagnostics").performClick()
    rule.onNodeWithTag("settings.crash_diagnostics").assertIsOn()
    assertTrue(Prefs.shared.getBoolean("settings.crash_diagnostics_enabled", false))
    rule.onNodeWithTag("settings.anonymous_analytics").performClick()
    rule.onNodeWithTag("settings.anonymous_analytics").assertIsOff()
    rule.onNodeWithTag("settings.crash_diagnostics").assertIsOn()
    assertFalse(Prefs.shared.getBoolean("settings.anonymous_analytics_enabled", true))
  }

  @Test
  fun reminderToggleShowsTimePickers() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    if (Build.VERSION.SDK_INT >= 33) {
      instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
    runBlocking { DailyReminder.setTime(20, 0) }
    setSettings()
    rule.onNodeWithTag("retention.reminder.toggle").performScrollTo().assertIsOff().performClick()
    rule.waitUntil(5_000) { DailyReminder.preference.value.isEnabled }
    rule.onNodeWithTag("retention.reminder.toggle").assertIsOn()
    rule.onNodeWithTag("retention.reminder.hour").performScrollTo().assertIsDisplayed().assertTextContains("20")
    rule.onNodeWithTag("retention.reminder.minute").assertIsDisplayed().assertTextContains("00")

    rule.onNodeWithTag("retention.reminder.hour").performClick()
    rule.onNodeWithTag("retention.reminder.hour.7").performClick()
    rule.runOnIdle { assertEquals(7, DailyReminder.preference.value.hour) }
    rule.onNodeWithTag("retention.reminder.hour").assertTextContains("07")
    runBlocking { DailyReminder.setTime(20, 0) }
  }

  @Test
  fun practicePresetFocusAppliesToggles() {
    setSettings()
    rule.onNodeWithTag("settings.practice_display_preset.focus").performScrollTo().performClick()
    rule.onNodeWithTag("settings.practice_meaning").assertIsOff()
    rule.onNodeWithTag("settings.practice_jamo").assertIsOff()
    rule.onNodeWithTag("settings.practice_mascot").assertIsOff()
    rule.onNodeWithTag("settings.practice_composition").assertIsOn()
    rule.onNodeWithTag("settings.practice_display_preset.learning").performClick()
    rule.onNodeWithTag("settings.practice_meaning").assertIsOn()
  }

  /** Smoke: renders the screen (top, keyboard, display) to PNGs for visual review. */
  @Test
  fun smokeScreenshots() {
    setSettings()
    val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
    fun shot(name: String) {
      val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
      java.io.File(dir, "settings_$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    shot("top")
    rule.onNodeWithTag("settings.section.display").performScrollTo()
    shot("display")
    rule.onNodeWithTag("settings.section.privacy").performScrollTo()
    shot("privacy")
    rule.onNodeWithTag("settings.version").performScrollTo()
    shot("info")
  }
}
