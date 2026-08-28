package app.piyokey.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPreferencesTest {
  @Test
  fun preferredLocaleUsesFirstSupportedLanguageAndEnglishFallback() {
    assertEquals(AppLanguage.JAPANESE, AppLanguage.preferred(listOf("fr-FR", "ko-KR", "ja-JP")))
    assertEquals(AppLanguage.ENGLISH, AppLanguage.preferred(listOf("ko-KR")))
    assertEquals(AppLanguage.ENGLISH, AppLanguage.preferred(listOf("ko-KR", "en-US")))
    assertEquals(AppLanguage.ENGLISH, AppLanguage.preferred(listOf("de-DE")))
  }

  @Test
  fun retiredKoreanSettingFallsBackToEnglishWithoutRemovingContentLanguage() {
    assertEquals(listOf("ja", "en"), AppLanguage.entries.map { it.tag })
    for (legacy in listOf("KOREAN", "ko")) {
      assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStored(legacy, listOf("ja-JP")))
      assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(legacy))
    }
    assertEquals(AppLanguage.JAPANESE, AppLanguage.fromStored(null, listOf("ja-JP")))
    assertEquals(AppLanguage.JAPANESE, AppLanguage.fromStored("JAPANESE", listOf("en-US")))
    assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStored("ENGLISH", listOf("ja-JP")))
  }

  @Test
  fun displayPresetsApplyWholeLearningAndFocusContracts() {
    val learning = AppPreferences().withDisplayPreset(PracticeDisplayPreset.LEARNING)
    assertTrue(learning.showsTarget)
    assertTrue(learning.showsMeaning)
    assertTrue(learning.showsJamo)
    assertTrue(learning.showsComposition)
    assertFalse(learning.showsReading)

    val focus = learning.withDisplayPreset(PracticeDisplayPreset.FOCUS)
    assertTrue(focus.showsTarget)
    assertFalse(focus.showsMeaning)
    assertFalse(focus.showsJamo)
    assertFalse(focus.showsComposition)
    assertFalse(focus.showsMascot)
  }

  @Test
  fun privacyChoicesDefaultOffAndNoticeWaitsForAnIdlePostOnboardingScreen() {
    val defaults = AppPreferences()
    assertFalse(defaults.anonymousAnalyticsEnabled)
    assertFalse(defaults.crashDiagnosticsEnabled)
    assertEquals(0, defaults.privacyNoticeVersion)

    assertFalse(PrivacyNoticePolicy.shouldPresent(0, onboardingCompleted = false, appTourCompleted = true))
    assertFalse(PrivacyNoticePolicy.shouldPresent(0, onboardingCompleted = true, appTourCompleted = false))
    assertFalse(PrivacyNoticePolicy.shouldPresent(0, true, true, sessionIsActive = true))
    assertFalse(PrivacyNoticePolicy.shouldPresent(0, true, true, hasBlockingPresentation = true))
    assertTrue(PrivacyNoticePolicy.shouldPresent(0, true, true))
    assertFalse(PrivacyNoticePolicy.shouldPresent(PrivacyNoticePolicy.currentVersion, true, true))
  }

  @Test
  fun hatchGateAndGrowthNeverSkipRequiredThreeChapters() {
    val cracked = AppPreferences(firstInputCompleted = true)
    assertEquals(PiyoGrowthStage.CRACKED_EGG, cracked.growthStage)
    assertFalse(OnboardingPolicy.canUseFullApp(cracked))
    assertEquals(1, OnboardingPolicy.nextHatchChapter(0))

    val hatching = cracked.copy(hatchChaptersCompleted = 2)
    assertEquals(PiyoGrowthStage.HATCHING, hatching.growthStage)
    assertFalse(OnboardingPolicy.canUseFullApp(hatching))

    val chick = hatching.copy(hatchChaptersCompleted = 3)
    assertEquals(PiyoGrowthStage.CHICK, chick.growthStage)
    assertTrue(OnboardingPolicy.canUseFullApp(chick))
    assertEquals(null, OnboardingPolicy.nextHatchChapter(3))
  }

  @Test
  fun chaptersOneThroughFourForceBuiltinWithoutChangingPreference() {
    assertEquals(InputMode.BUILTIN, OnboardingPolicy.resolvedInputMode(InputMode.OS_IME, 1))
    assertEquals(InputMode.BUILTIN, OnboardingPolicy.resolvedInputMode(InputMode.OS_IME, 4))
    assertEquals(InputMode.OS_IME, OnboardingPolicy.resolvedInputMode(InputMode.OS_IME, 5))
    assertEquals(InputMode.OS_IME, OnboardingPolicy.resolvedInputMode(InputMode.OS_IME, null))
  }

  @Test
  fun weeklyCupAlwaysUsesBuiltinForCompetitiveParity() {
    assertEquals(InputMode.BUILTIN, InputModePolicy.weeklyCup(InputMode.BUILTIN))
    assertEquals(InputMode.BUILTIN, InputModePolicy.weeklyCup(InputMode.OS_IME))
  }

  @Test
  fun wardrobeUnlocksAreMonotonicAndAutoNeverAddsContextAccessories() {
    val streak = PiyoWardrobePolicy.unlockedAfterStreakRewards(emptySet(), setOf(3, 7))
    assertEquals(setOf(PiyoAccessory.STREAK_RIBBON, PiyoAccessory.RAINBOW_BOW), streak)
    val afterTopik = PiyoWardrobePolicy.unlockedAfterTopikGame(streak, setOf("TOPIK II"), 0)
    assertTrue(PiyoAccessory.TOPIK_GLASSES in afterTopik)
    assertEquals(null, PiyoWardrobePolicy.resolvedAccessory(PiyoAccessory.AUTO, afterTopik))
    assertEquals(PiyoAccessory.TOPIK_GLASSES, PiyoWardrobePolicy.resolvedAccessory(PiyoAccessory.TOPIK_GLASSES, afterTopik))
    assertEquals(null, PiyoWardrobePolicy.resolvedAccessory(PiyoAccessory.CHAMPION_TROPHY, afterTopik))
  }

  @Test
  fun sessionAppearanceIsAnImmutableSnapshot() {
    val preferences = AppPreferences(
      firstInputCompleted = true,
      hatchChaptersCompleted = 3,
      selectedPiyoAccessory = PiyoAccessory.TOPIK_GLASSES,
      unlockedPiyoAccessories = setOf(PiyoAccessory.TOPIK_GLASSES),
    )
    val appearance = preferences.sessionAppearance
    val changed = preferences.copy(selectedPiyoAccessory = PiyoAccessory.NONE)
    assertEquals(PiyoAccessory.TOPIK_GLASSES, appearance.accessory)
    assertEquals(null, changed.sessionAppearance.accessory)
  }

  @Test
  fun quickPracticeHistoryDefaultsEmptyAndSurvivesPreferenceCopies() {
    val preferences = AppPreferences(recentQuickPracticeWords = listOf("가", "나"))
    assertEquals(listOf("가", "나"), preferences.copy(theme = AppTheme.DARK).recentQuickPracticeWords)
    assertTrue(AppPreferences().recentQuickPracticeWords.isEmpty())
  }
}
