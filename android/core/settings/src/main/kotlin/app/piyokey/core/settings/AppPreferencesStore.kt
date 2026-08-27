package app.piyokey.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.piyokeyPreferences by preferencesDataStore(name = "piyokey_preferences")

class AppPreferencesStore private constructor(
  private val context: Context,
  private val preferredLanguages: () -> List<String>,
) {
  val values: Flow<AppPreferences> = context.piyokeyPreferences.data
    .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
    .map(::decode)

  suspend fun update(transform: (AppPreferences) -> AppPreferences) {
    context.piyokeyPreferences.edit { mutable -> encode(transform(decode(mutable)), mutable) }
  }

  suspend fun resetOnboarding() = update {
    it.copy(
      onboardingGoal = null,
      onboardingIntroStep = OnboardingIntroStep.GOAL,
      onboardingIntroSkipped = false,
      firstInputCompleted = false,
      hatchHandoffCompleted = false,
      hatchChaptersCompleted = 0,
      pendingHatchResultChapter = 0,
      piyoNickname = "",
      appTourCompleted = false,
      onboardingMigrationChecked = true,
    )
  }

  private fun decode(preferences: Preferences): AppPreferences = AppPreferences(
    language = enumValue(preferences[Keys.language], AppLanguage.preferred(preferredLanguages())),
    theme = enumValue(preferences[Keys.theme], AppTheme.LIGHT),
    fontScale = enumValue(preferences[Keys.fontScale], FontScale.STANDARD),
    soundEffectsEnabled = preferences[Keys.soundEffects] ?: true,
    keySoundStyle = enumValue(preferences[Keys.keySoundStyle], KeySoundStyle.DEFAULT),
    hapticsEnabled = preferences[Keys.haptics] ?: true,
    romanHintsEnabled = preferences[Keys.romanHints] ?: true,
    keyGuideEnabled = preferences[Keys.keyGuide] ?: true,
    defaultInputMode = enumValue(preferences[Keys.defaultInputMode], InputMode.BUILTIN),
    showsPhysicalKeyboardGuide = preferences[Keys.showsPhysicalKeyboardGuide] ?: false,
    displayPreset = enumValue(preferences[Keys.displayPreset], PracticeDisplayPreset.LEARNING),
    showsTarget = preferences[Keys.showsTarget] ?: true,
    showsMeaning = preferences[Keys.showsMeaning] ?: true,
    showsReading = preferences[Keys.showsReading] ?: false,
    promptOrder = enumValue(preferences[Keys.promptOrder], PracticePromptOrder.TARGET_MEANING_READING),
    showsJamo = preferences[Keys.showsJamo] ?: true,
    showsComposition = preferences[Keys.showsComposition] ?: true,
    showsMascot = preferences[Keys.showsMascot] ?: true,
    autoPronouncesPractice = preferences[Keys.autoPronouncesPractice] ?: false,
    choseongShowsMeaning = preferences[Keys.choseongShowsMeaning] ?: true,
    anonymousAnalyticsEnabled = preferences[Keys.anonymousAnalyticsEnabled] ?: false,
    crashDiagnosticsEnabled = preferences[Keys.crashDiagnosticsEnabled] ?: false,
    privacyNoticeVersion = (preferences[Keys.privacyNoticeVersion] ?: 0).coerceAtLeast(0),
    onboardingGoal = preferences[Keys.onboardingGoal]?.let { raw -> enumValueOrNull<OnboardingGoal>(raw) },
    onboardingIntroStep = enumValue(preferences[Keys.onboardingIntroStep], OnboardingIntroStep.GOAL),
    onboardingIntroSkipped = preferences[Keys.onboardingIntroSkipped] ?: false,
    firstInputCompleted = preferences[Keys.firstInputCompleted] ?: false,
    hatchHandoffCompleted = preferences[Keys.hatchHandoffCompleted] ?: false,
    hatchChaptersCompleted = (preferences[Keys.hatchChaptersCompleted] ?: 0).coerceIn(0, 3),
    pendingHatchResultChapter = (preferences[Keys.pendingHatchResultChapter] ?: 0).coerceIn(0, 3),
    piyoNickname = preferences[Keys.piyoNickname].orEmpty(),
    selectedPiyoAccessory = enumValue(preferences[Keys.selectedPiyoAccessory], PiyoAccessory.AUTO),
    unlockedPiyoAccessories = preferences[Keys.unlockedPiyoAccessories].orEmpty()
      .mapNotNull { enumValueOrNull<PiyoAccessory>(it) }.toSet(),
    appTourCompleted = preferences[Keys.appTourCompleted] ?: false,
    onboardingMigrationChecked = preferences[Keys.onboardingMigrationChecked] ?: false,
    recentQuickPracticeWords = preferences[Keys.recentQuickPracticeWords]
      .orEmpty()
      .lineSequence()
      .filter(String::isNotBlank)
      .distinct()
      .toList()
      .takeLast(20),
  )

  private fun encode(value: AppPreferences, preferences: MutablePreferences) {
    preferences[Keys.language] = value.language.name
    preferences[Keys.theme] = value.theme.name
    preferences[Keys.fontScale] = value.fontScale.name
    preferences[Keys.soundEffects] = value.soundEffectsEnabled
    preferences[Keys.keySoundStyle] = value.keySoundStyle.name
    preferences[Keys.haptics] = value.hapticsEnabled
    preferences[Keys.romanHints] = value.romanHintsEnabled
    preferences[Keys.keyGuide] = value.keyGuideEnabled
    preferences[Keys.defaultInputMode] = value.defaultInputMode.name
    preferences[Keys.showsPhysicalKeyboardGuide] = value.showsPhysicalKeyboardGuide
    preferences[Keys.displayPreset] = value.displayPreset.name
    preferences[Keys.showsTarget] = value.showsTarget
    preferences[Keys.showsMeaning] = value.showsMeaning
    preferences[Keys.showsReading] = value.showsReading
    preferences[Keys.promptOrder] = value.promptOrder.name
    preferences[Keys.showsJamo] = value.showsJamo
    preferences[Keys.showsComposition] = value.showsComposition
    preferences[Keys.showsMascot] = value.showsMascot
    preferences[Keys.autoPronouncesPractice] = value.autoPronouncesPractice
    preferences[Keys.choseongShowsMeaning] = value.choseongShowsMeaning
    preferences[Keys.anonymousAnalyticsEnabled] = value.anonymousAnalyticsEnabled
    preferences[Keys.crashDiagnosticsEnabled] = value.crashDiagnosticsEnabled
    preferences[Keys.privacyNoticeVersion] = value.privacyNoticeVersion
    value.onboardingGoal?.let { preferences[Keys.onboardingGoal] = it.name }
      ?: preferences.remove(Keys.onboardingGoal)
    preferences[Keys.onboardingIntroStep] = value.onboardingIntroStep.name
    preferences[Keys.onboardingIntroSkipped] = value.onboardingIntroSkipped
    preferences[Keys.firstInputCompleted] = value.firstInputCompleted
    preferences[Keys.hatchHandoffCompleted] = value.hatchHandoffCompleted
    preferences[Keys.hatchChaptersCompleted] = value.hatchChaptersCompleted
    preferences[Keys.pendingHatchResultChapter] = value.pendingHatchResultChapter
    preferences[Keys.piyoNickname] = value.piyoNickname
    preferences[Keys.selectedPiyoAccessory] = value.selectedPiyoAccessory.name
    preferences[Keys.unlockedPiyoAccessories] = value.unlockedPiyoAccessories.mapTo(mutableSetOf()) { it.name }
    preferences[Keys.appTourCompleted] = value.appTourCompleted
    preferences[Keys.onboardingMigrationChecked] = value.onboardingMigrationChecked
    preferences[Keys.recentQuickPracticeWords] = value.recentQuickPracticeWords
      .filter { it.isNotBlank() && it.none(Char::isWhitespace) }
      .distinct()
      .takeLast(20)
      .joinToString("\n")
  }

  companion object {
    fun create(
      context: Context,
      preferredLanguages: () -> List<String> = { Locale.getDefault().let { listOf(it.toLanguageTag()) } },
    ): AppPreferencesStore = AppPreferencesStore(context.applicationContext, preferredLanguages)
  }

  private object Keys {
    val language = stringPreferencesKey("settings.language")
    val theme = stringPreferencesKey("settings.theme")
    val fontScale = stringPreferencesKey("settings.font_scale")
    val soundEffects = booleanPreferencesKey("settings.sound_effects")
    val keySoundStyle = stringPreferencesKey("settings.key_sound_style")
    val haptics = booleanPreferencesKey("settings.haptics")
    val romanHints = booleanPreferencesKey("settings.roman_hints")
    val keyGuide = booleanPreferencesKey("settings.key_guide")
    val defaultInputMode = stringPreferencesKey("settings.input_mode")
    val showsPhysicalKeyboardGuide = booleanPreferencesKey("settings.physical_keyboard_guide")
    val displayPreset = stringPreferencesKey("settings.practice_display_preset")
    val showsTarget = booleanPreferencesKey("settings.practice_shows_target")
    val showsMeaning = booleanPreferencesKey("settings.practice_shows_meaning")
    val showsReading = booleanPreferencesKey("settings.practice_shows_reading")
    val promptOrder = stringPreferencesKey("settings.practice_prompt_order")
    val showsJamo = booleanPreferencesKey("settings.practice_shows_jamo")
    val showsComposition = booleanPreferencesKey("settings.practice_shows_composition")
    val showsMascot = booleanPreferencesKey("settings.practice_shows_mascot")
    val autoPronouncesPractice = booleanPreferencesKey("settings.practice_auto_pronounce")
    val choseongShowsMeaning = booleanPreferencesKey("settings.choseong_shows_meaning")
    val anonymousAnalyticsEnabled = booleanPreferencesKey("settings.anonymous_analytics_enabled")
    val crashDiagnosticsEnabled = booleanPreferencesKey("settings.crash_diagnostics_enabled")
    val privacyNoticeVersion = intPreferencesKey("settings.privacy_notice_version")
    val onboardingGoal = stringPreferencesKey("onboarding.goal")
    val onboardingIntroStep = stringPreferencesKey("onboarding.intro_step")
    val onboardingIntroSkipped = booleanPreferencesKey("onboarding.intro_skipped")
    val firstInputCompleted = booleanPreferencesKey("onboarding.first_input_completed")
    val hatchHandoffCompleted = booleanPreferencesKey("onboarding.hatch_handoff_completed")
    val hatchChaptersCompleted = intPreferencesKey("onboarding.hatch_chapters_completed")
    val pendingHatchResultChapter = intPreferencesKey("onboarding.pending_hatch_result_chapter")
    val piyoNickname = stringPreferencesKey("onboarding.piyo_nickname")
    val selectedPiyoAccessory = stringPreferencesKey("piyo.selected_accessory")
    val unlockedPiyoAccessories = stringSetPreferencesKey("piyo.unlocked_accessories")
    val appTourCompleted = booleanPreferencesKey("onboarding.app_tour_completed")
    val onboardingMigrationChecked = booleanPreferencesKey("onboarding.migration_checked")
    val recentQuickPracticeWords = stringPreferencesKey("retention.random_word_practice.recent_words")
  }
}

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
  raw?.let { enumValueOrNull<T>(it) } ?: fallback

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? =
  enumValues<T>().firstOrNull { it.name == raw }
