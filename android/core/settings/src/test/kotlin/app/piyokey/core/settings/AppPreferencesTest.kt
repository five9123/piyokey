package app.piyokey.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPreferencesTest {
  @Test
  fun preferredLocaleUsesFirstSupportedLanguageAndEnglishFallback() {
    assertEquals(AppLanguage.KOREAN, AppLanguage.preferred(listOf("fr-FR", "ko-KR", "ja-JP")))
    assertEquals(AppLanguage.ENGLISH, AppLanguage.preferred(listOf("de-DE")))
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
}
