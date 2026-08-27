package app.piyokey.core.settings

enum class AppLanguage(val tag: String) {
  JAPANESE("ja"),
  ENGLISH("en"),
  KOREAN("ko");

  companion object {
    fun resolve(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH

    fun preferred(tags: List<String>): AppLanguage = tags.firstNotNullOfOrNull { tag ->
      val language = tag.substringBefore('-').substringBefore('_').lowercase()
      entries.firstOrNull { it.tag == language }
    } ?: ENGLISH
  }
}

enum class AppTheme { LIGHT, DARK }
enum class FontScale(val multiplier: Float) { SMALL(0.88f), STANDARD(1f), LARGE(1.16f) }
enum class KeySoundStyle { DEFAULT, MECHANICAL, SOFT }
enum class InputMode { BUILTIN, OS_IME }

object InputModePolicy {
  fun weeklyCup(@Suppress("UNUSED_PARAMETER") preferred: InputMode): InputMode = InputMode.BUILTIN
}
enum class PracticeDisplayPreset { LEARNING, FOCUS }
enum class PracticePromptField { TARGET, MEANING, READING }

enum class PracticePromptOrder(val fields: List<PracticePromptField>) {
  TARGET_MEANING_READING(listOf(PracticePromptField.TARGET, PracticePromptField.MEANING, PracticePromptField.READING)),
  TARGET_READING_MEANING(listOf(PracticePromptField.TARGET, PracticePromptField.READING, PracticePromptField.MEANING)),
  MEANING_TARGET_READING(listOf(PracticePromptField.MEANING, PracticePromptField.TARGET, PracticePromptField.READING)),
  MEANING_READING_TARGET(listOf(PracticePromptField.MEANING, PracticePromptField.READING, PracticePromptField.TARGET)),
  READING_TARGET_MEANING(listOf(PracticePromptField.READING, PracticePromptField.TARGET, PracticePromptField.MEANING)),
  READING_MEANING_TARGET(listOf(PracticePromptField.READING, PracticePromptField.MEANING, PracticePromptField.TARGET)),
}

enum class OnboardingGoal(val preferredTags: Set<String>) {
  KEYBOARD(setOf("入門", "キーボード", "子音", "母音")),
  TRAVEL(setOf("韓国旅行", "旅行", "日常")),
  TOPIK(setOf("TOPIK", "検定", "基礎単語")),
  TRENDS(setOf("今どき", "SNS", "日常")),
}

enum class OnboardingIntroStep { GOAL, KEYBOARD, FIRST_INPUT, COMPLETE }
enum class PiyoGrowthStage { EGG, CRACKED_EGG, HATCHING, CHICK }
enum class PiyoAccessory {
  AUTO,
  NONE,
  STREAK_RIBBON,
  STAR_BERET,
  RAINBOW_BOW,
  TOPIK_GLASSES,
  CHAMPION_TROPHY,
}

data class PiyoSessionAppearance(
  val stage: PiyoGrowthStage,
  val accessory: PiyoAccessory?,
)

data class AppPreferences(
  val language: AppLanguage = AppLanguage.ENGLISH,
  val theme: AppTheme = AppTheme.LIGHT,
  val fontScale: FontScale = FontScale.STANDARD,
  val soundEffectsEnabled: Boolean = true,
  val keySoundStyle: KeySoundStyle = KeySoundStyle.DEFAULT,
  val hapticsEnabled: Boolean = true,
  val romanHintsEnabled: Boolean = true,
  val keyGuideEnabled: Boolean = true,
  val defaultInputMode: InputMode = InputMode.BUILTIN,
  val showsPhysicalKeyboardGuide: Boolean = false,
  val displayPreset: PracticeDisplayPreset = PracticeDisplayPreset.LEARNING,
  val showsTarget: Boolean = true,
  val showsMeaning: Boolean = true,
  val showsReading: Boolean = false,
  val promptOrder: PracticePromptOrder = PracticePromptOrder.TARGET_MEANING_READING,
  val showsJamo: Boolean = true,
  val showsComposition: Boolean = true,
  val showsMascot: Boolean = true,
  val autoPronouncesPractice: Boolean = false,
  val choseongShowsMeaning: Boolean = true,
  val anonymousAnalyticsEnabled: Boolean = false,
  val crashDiagnosticsEnabled: Boolean = false,
  val privacyNoticeVersion: Int = 0,
  val onboardingGoal: OnboardingGoal? = null,
  val onboardingIntroStep: OnboardingIntroStep = OnboardingIntroStep.GOAL,
  val onboardingIntroSkipped: Boolean = false,
  val firstInputCompleted: Boolean = false,
  val hatchHandoffCompleted: Boolean = false,
  val hatchChaptersCompleted: Int = 0,
  val pendingHatchResultChapter: Int = 0,
  val piyoNickname: String = "",
  val selectedPiyoAccessory: PiyoAccessory = PiyoAccessory.AUTO,
  val unlockedPiyoAccessories: Set<PiyoAccessory> = emptySet(),
  val appTourCompleted: Boolean = false,
  val onboardingMigrationChecked: Boolean = false,
  val recentQuickPracticeWords: List<String> = emptyList(),
) {
  init {
    require(hatchChaptersCompleted in 0..3)
    require(pendingHatchResultChapter in 0..3)
    require(privacyNoticeVersion >= 0)
  }

  val growthStage: PiyoGrowthStage
    get() = when {
      hatchChaptersCompleted >= 3 -> PiyoGrowthStage.CHICK
      hatchChaptersCompleted >= 1 -> PiyoGrowthStage.HATCHING
      firstInputCompleted -> PiyoGrowthStage.CRACKED_EGG
      else -> PiyoGrowthStage.EGG
    }

  val hatchGateComplete: Boolean get() = hatchChaptersCompleted >= 3

  val sessionAppearance: PiyoSessionAppearance
    get() = PiyoSessionAppearance(
      stage = growthStage,
      accessory = PiyoWardrobePolicy.resolvedAccessory(selectedPiyoAccessory, unlockedPiyoAccessories),
    )

  fun withDisplayPreset(preset: PracticeDisplayPreset): AppPreferences = when (preset) {
    PracticeDisplayPreset.LEARNING -> copy(
      displayPreset = preset,
      showsTarget = true,
      showsMeaning = true,
      showsReading = false,
      showsJamo = true,
      showsComposition = true,
      showsMascot = true,
    )
    PracticeDisplayPreset.FOCUS -> copy(
      displayPreset = preset,
      showsTarget = true,
      showsMeaning = false,
      showsReading = false,
      showsJamo = false,
      showsComposition = false,
      showsMascot = false,
    )
  }
}

object PrivacyNoticePolicy {
  const val currentVersion = 1

  fun shouldPresent(
    reviewedVersion: Int,
    onboardingCompleted: Boolean,
    appTourCompleted: Boolean,
    sessionIsActive: Boolean = false,
    hasBlockingPresentation: Boolean = false,
  ): Boolean = reviewedVersion < currentVersion &&
    onboardingCompleted &&
    appTourCompleted &&
    !sessionIsActive &&
    !hasBlockingPresentation
}

object PiyoWardrobePolicy {
  private val streakRewards = mapOf(
    3 to PiyoAccessory.STREAK_RIBBON,
    5 to PiyoAccessory.STAR_BERET,
    7 to PiyoAccessory.RAINBOW_BOW,
  )

  fun unlockedAfterStreakRewards(
    current: Set<PiyoAccessory>,
    rewardThresholds: Set<Int>,
  ): Set<PiyoAccessory> = current + rewardThresholds.mapNotNull(streakRewards::get)

  fun unlockedAfterTopikGame(
    current: Set<PiyoAccessory>,
    deckTags: Set<String>,
    score: Int,
  ): Set<PiyoAccessory> = if (score >= 0 && deckTags.any { it.contains("TOPIK", ignoreCase = true) }) {
    current + PiyoAccessory.TOPIK_GLASSES
  } else {
    current
  }

  fun resolvedAccessory(
    selected: PiyoAccessory,
    unlocked: Set<PiyoAccessory>,
  ): PiyoAccessory? = when {
    selected == PiyoAccessory.AUTO || selected == PiyoAccessory.NONE -> null
    selected in unlocked -> selected
    else -> null
  }

  fun selectableAccessories(unlocked: Set<PiyoAccessory>): List<PiyoAccessory> =
    listOf(PiyoAccessory.AUTO, PiyoAccessory.NONE) + PiyoAccessory.entries.filter { it in unlocked }
}

object OnboardingPolicy {
  const val requiredHatchChapters = 3

  fun canUseFullApp(preferences: AppPreferences): Boolean = preferences.hatchGateComplete

  fun resolvedInputMode(
    preferred: InputMode,
    curriculumChapterNumber: Int?,
  ): InputMode = preferred

  fun nextHatchChapter(completed: Int): Int? =
    if (completed in 0 until requiredHatchChapters) completed + 1 else null
}
