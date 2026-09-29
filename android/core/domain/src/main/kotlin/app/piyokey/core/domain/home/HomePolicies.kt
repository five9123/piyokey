package app.piyokey.core.domain.home

import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.HatchOnboardingPolicy
import app.piyokey.core.domain.RetentionActivityKind
import app.piyokey.core.domain.RetentionDayRecord

/** What the app root shows (iOS `AppRootView.body`). */
enum class RootGateScreen { ONBOARDING, HATCH_MISSIONS, MAIN_TABS }

/** Pure root gating rules of iOS `AppRootView`. */
object RootGatePolicy {
  /** Onboarding first, then the ch1–ch3 hatch missions (no tab bar), then the tabs. */
  fun screen(onboardingPresented: Boolean, hatchGateActive: Boolean): RootGateScreen = when {
    onboardingPresented -> RootGateScreen.ONBOARDING
    hatchGateActive -> RootGateScreen.HATCH_MISSIONS
    else -> RootGateScreen.MAIN_TABS
  }

  /** Decided once when the root appears (iOS `hatchGateIsActive` in `onAppear`). */
  fun initialHatchGateActive(completedStageIds: Set<String>): Boolean =
    !HatchOnboardingPolicy.isComplete(completedStageIds)

  /** iOS `finishHatchOnboarding`: only closes the gate once all hatch stages are saved. */
  fun canFinishHatch(completedStageIds: Set<String>): Boolean =
    HatchOnboardingPolicy.isComplete(completedStageIds)

  /** iOS `shouldStartAppTour`. */
  fun shouldStartAppTour(
    onboardingPresented: Boolean,
    hatchGateActive: Boolean,
    completedStageIds: Set<String>,
    appTourCompleted: Boolean,
    tourActive: Boolean,
  ): Boolean = !onboardingPresented && !hatchGateActive &&
    HatchOnboardingPolicy.isComplete(completedStageIds) && !appTourCompleted && !tourActive

  /**
   * iOS `hasBlockingPresentation` for the privacy notice: settings (or any cover such as a
   * session) is open, the tour runs, the Home tab is not selected, or Home has pushed a screen.
   */
  fun privacyBlocked(
    coverPresented: Boolean,
    tourActive: Boolean,
    homeTabSelected: Boolean,
    homeStackDepth: Int = 1,
  ): Boolean = coverPresented || tourActive || !homeTabSelected || homeStackDepth > 1
}

/** Six spotlight steps of the first-run tour (iOS `AppTourStep`). */
enum class AppTourStep(val targetRaw: String, val tabRaw: String, val symbol: String) {
  HOME("homePrimary", "home", "bolt.fill"),
  DISCOVER("discoverSearch", "discover", "sparkle.magnifyingglass"),
  PRACTICE("practiceCurriculum", "practice", "keyboard.fill"),
  GAME("gameModes", "game", "gamecontroller.fill"),
  MY_PAGE("myPageProfile", "my_page", "person.crop.circle.fill"),
  SETTINGS("settings", "my_page", "gearshape.fill");

  val titleKey: String get() = "app_tour.$targetRaw.title"
  val detailKey: String get() = "app_tour.$targetRaw.detail"
  /** 1-based position for `app_tour.progress_format`. */
  val position: Int get() = ordinal + 1
  val isLast: Boolean get() = next == null
  val next: AppTourStep? get() = entries.getOrNull(ordinal + 1)
  val spotlightCornerRadius: Float get() = when (this) {
    DISCOVER -> 18f
    SETTINGS -> 26f
    else -> 28f
  }

  /** iOS `fallbackFrame(in:)` when the target has not published bounds (x, y, w, h in dp). */
  fun fallbackFrame(width: Float, height: Float): FloatArray {
    val contentWidth = maxOf(0f, width - 36f)
    return when (this) {
      HOME -> floatArrayOf(18f, height * 0.34f, contentWidth, 126f)
      DISCOVER -> floatArrayOf(16f, 54f, maxOf(0f, width - 32f), 64f)
      PRACTICE -> floatArrayOf(18f, 94f, contentWidth, 190f)
      GAME -> floatArrayOf(18f, height * 0.42f, contentWidth, 190f)
      MY_PAGE -> floatArrayOf(18f, 88f, contentWidth, 166f)
      SETTINGS -> floatArrayOf(maxOf(0f, width - 62f), 36f, 52f, 52f)
    }
  }

  companion object {
    val count: Int get() = entries.size
  }
}

/** Spotlight geometry of iOS `AppTourOverlay` (all values in dp). */
object AppTourGeometry {
  data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val midX: Float get() = (left + right) / 2
    val midY: Float get() = (top + bottom) / 2
  }

  /** Pads the target by 9 and clamps it 8 inside the screen (iOS `clampedSpotlight`). */
  fun clampedSpotlight(target: Box, width: Float, height: Float): Box {
    val minX = (target.left - 9f).coerceAtLeast(8f).coerceAtMost(maxOf(8f, width - 8f))
    val minY = (target.top - 9f).coerceAtLeast(8f).coerceAtMost(maxOf(8f, height - 8f))
    val maxX = (target.right + 9f).coerceAtLeast(minX).coerceAtMost(maxOf(minX, width - 8f))
    val maxY = (target.bottom + 9f).coerceAtLeast(minY).coerceAtMost(maxOf(minY, height - 8f))
    return Box(minX, minY, maxX, maxY)
  }

  fun calloutBelow(spotlight: Box, height: Float): Boolean = spotlight.midY < height * 0.44f

  /** Centre Y of the callout card (iOS `calloutY`). */
  fun calloutCenterY(spotlight: Box, height: Float, below: Boolean): Float {
    val half = 86f
    return if (below) {
      minOf(maxOf(spotlight.bottom + half + 48f, 150f), height - 210f)
    } else {
      maxOf(minOf(spotlight.top - half - 48f, height - 210f), 140f)
    }
  }
}

/** Next step of the post-tour notification → privacy flow (iOS `shouldStartOnboardingPrivacyFlow` task). */
enum class OnboardingPrivacyAction {
  /** Show the privacy notice now. */
  PRESENT_NOTICE,
  /** A reminder preference already exists: mark the permission request done, then show the notice. */
  MARK_REQUESTED_THEN_PRESENT,
  /** Ask for notification permission once (`enableFromOnboarding`), then show the notice. */
  REQUEST_NOTIFICATION_PERMISSION,
}

object OnboardingPrivacyFlowPolicy {
  fun nextAction(
    reviewedNoticeVersion: Int,
    notificationPermissionRequested: Boolean,
    hasStoredReminderPreference: Boolean,
  ): OnboardingPrivacyAction = when {
    reviewedNoticeVersion != 0 || notificationPermissionRequested -> OnboardingPrivacyAction.PRESENT_NOTICE
    hasStoredReminderPreference -> OnboardingPrivacyAction.MARK_REQUESTED_THEN_PRESENT
    else -> OnboardingPrivacyAction.REQUEST_NOTIFICATION_PERMISSION
  }
}

/** Home primary card (iOS `HomePrimaryActionView`). */
sealed interface HomePrimaryAction {
  data class ResumeCurriculum(val stageId: String) : HomePrimaryAction
  data class ResumeDeck(val deckId: String) : HomePrimaryAction
  data class RecommendDeck(val deckId: String) : HomePrimaryAction
  data object DailyChallenge : HomePrimaryAction
}

object HomePrimaryActionPolicy {
  /**
   * Resume the interrupted curriculum stage → resume the last played installed deck →
   * (first home only) the starter recommendation → the personalized daily challenge.
   */
  fun resolve(
    activeStageId: String?,
    recentPlayedDeckId: String?,
    starterDeckId: String?,
    catalogAvailable: Boolean,
    hasStartedLearning: Boolean,
    hasPriorHomeActivity: Boolean,
  ): HomePrimaryAction {
    val stage = activeStageId?.let { CurriculumCatalog.stage(it) }
    if (stage != null) return HomePrimaryAction.ResumeCurriculum(stage.id)
    if (recentPlayedDeckId != null) return HomePrimaryAction.ResumeDeck(recentPlayedDeckId)
    if (!hasStartedLearning && !hasPriorHomeActivity && starterDeckId != null && catalogAvailable) {
      return HomePrimaryAction.RecommendDeck(starterDeckId)
    }
    return HomePrimaryAction.DailyChallenge
  }

  /** Hatch missions (ch1–3) are onboarding, not prior home learning. */
  fun hasPriorHomeActivity(completedStageIds: Set<String>, records: Collection<RetentionDayRecord>): Boolean =
    completedStageIds.any { (CurriculumCatalog.stage(it)?.chapterNumber ?: 0) > 3 } ||
      records.any { record -> record.activities.any { it != RetentionActivityKind.CURRICULUM } }

  /** iOS `rememberLearningHistory`: sets `onboarding.home_learning_started`. */
  fun shouldRememberLearningHistory(
    activeStageId: String?,
    recentPlayedDeckId: String?,
    hasPriorHomeActivity: Boolean,
  ): Boolean = (activeStageId?.let { CurriculumCatalog.stage(it) } != null) ||
    recentPlayedDeckId != null || hasPriorHomeActivity

  /** iOS `usesLandscapeDashboard`. */
  fun usesLandscapeDashboard(availableWidthDp: Float, isTall: Boolean, accessibilityFontSize: Boolean): Boolean =
    availableWidthDp >= 1_100f && !isTall && !accessibilityFontSize
}
