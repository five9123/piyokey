package app.piyokey.android.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.piyokey.android.IncomingDocuments
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.data.settings.AppSettings
import app.piyokey.android.data.settings.PrivacyNoticePolicy
import app.piyokey.android.feature.home.rememberJstToday
import app.piyokey.android.feature.library.LibraryLauncher
import app.piyokey.android.feature.practice.HatchMissionFlow
import app.piyokey.android.platform.analytics.Telemetry
import app.piyokey.android.platform.reminder.DailyReminder
import app.piyokey.android.platform.reminder.rememberNotificationPermissionRequester
import app.piyokey.android.ui.nav.AppTab
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.MainTabs
import app.piyokey.android.ui.nav.TabController
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.CurriculumChapterCompletionPolicy
import app.piyokey.core.domain.RetentionCalendar
import app.piyokey.core.domain.home.AppTourStep
import app.piyokey.core.domain.home.OnboardingPrivacyAction
import app.piyokey.core.domain.home.OnboardingPrivacyFlowPolicy
import app.piyokey.core.domain.home.RootGatePolicy
import app.piyokey.core.domain.home.RootGateScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Switches tabs like iOS `selectedTab = …` (fires `onTabChanged`, keeps the tab's stack). */
internal fun TabController.switchTo(tab: AppTab) {
  if (selected == tab) return
  selected = tab
  onTabChanged(tab)
}

private fun AppTourStep.appTab(): AppTab = AppTab.entries.first { it.analyticsValue == tabRaw }

/**
 * Root gate (iOS `AppRootView`): onboarding → hatch missions (no tab bar) → main tabs, then the
 * six-step app tour, the one-time notification permission request and the privacy notice
 * (Home tab only, never over a cover/session). Also forwards `.typedeck` intents once the gate
 * is passed, keeps the My Page badge, sends `feature_viewed` + crash context per tab and pushes
 * the mascot growth signals (chapters, streak, installed decks) app-wide.
 *
 * [mainTabs] is kept for the integrator's signature; the gate renders [MainTabs] itself because it
 * owns the [TabController] (tour tab switching, badges, analytics).
 */
@Composable
fun RootGate(@Suppress("UNUSED_PARAMETER") mainTabs: @Composable () -> Unit) {
  val onboardingSnapshot by AppData.onboarding.snapshot.collectAsState()
  val stageProgress by AppData.curriculum.stageProgress.collectAsState()
  val controller = remember { TabController() }
  var hatchGateActive by rememberSaveable {
    mutableStateOf(RootGatePolicy.initialHatchGateActive(AppData.curriculum.completedStageIds))
  }
  val onboardingPresented = !onboardingSnapshot.isCompleted

  MascotSignalsSync()

  when (RootGatePolicy.screen(onboardingPresented, hatchGateActive)) {
    RootGateScreen.ONBOARDING -> OnboardingScreen()
    RootGateScreen.HATCH_MISSIONS -> Box(Modifier.fillMaxSize().testTag("onboarding.hatch.gate")) {
      HatchMissionFlow(
        onComplete = {
          if (RootGatePolicy.canFinishHatch(AppData.curriculum.completedStageIds)) {
            controller.switchTo(AppTab.HOME)
            hatchGateActive = false
          }
        },
      )
    }
    RootGateScreen.MAIN_TABS -> GatedMainTabs(
      controller = controller,
      completedStageIds = stageProgress.keys,
      onboardingPresented = onboardingPresented,
      hatchGateActive = hatchGateActive,
    )
  }
}

@Composable
private fun GatedMainTabs(
  controller: TabController,
  completedStageIds: Set<String>,
  onboardingPresented: Boolean,
  hatchGateActive: Boolean,
) {
  val appNavigator = LocalAppNavigator.current
  val scope = rememberCoroutineScope()
  var appTourCompleted by remember { mutableStateOf(AppData.onboarding.appTourCompleted) }
  var tourStep by rememberSaveable { mutableStateOf<AppTourStep?>(null) }

  // Analytics: current tab on appear, then every change (iOS mainTabs onAppear / onChange).
  DisposableEffect(controller) {
    controller.onTabChanged = { tab ->
      Telemetry.featureViewed(tab.analyticsValue)
      Telemetry.setCrashContext(tab.analyticsValue)
    }
    Telemetry.featureViewed(controller.selected.analyticsValue)
    Telemetry.setCrashContext(controller.selected.analyticsValue)
    onDispose { controller.onTabChanged = {} }
  }

  // App tour ------------------------------------------------------------------------------
  val shouldStartTour = RootGatePolicy.shouldStartAppTour(
    onboardingPresented = onboardingPresented,
    hatchGateActive = hatchGateActive,
    completedStageIds = completedStageIds,
    appTourCompleted = appTourCompleted,
    tourActive = tourStep != null,
  )
  val latestShouldStartTour by rememberUpdatedState(shouldStartTour)
  LaunchedEffect(shouldStartTour) {
    if (!shouldStartTour) return@LaunchedEffect
    // Let the first layout pass publish the spotlight bounds (iOS `Task.yield()`).
    withFrameNanos { }
    if (!latestShouldStartTour) return@LaunchedEffect
    controller.switchTo(AppTab.HOME)
    tourStep = AppTourStep.HOME
  }

  fun completeTour() {
    appTourCompleted = true
    AppData.onboarding.appTourCompleted = true
    Telemetry.onboardingStepCompleted("app_tour")
    controller.switchTo(AppTab.HOME)
    tourStep = null
  }

  fun advanceTour() {
    val current = tourStep ?: return
    val next = current.next ?: return completeTour()
    controller.switchTo(next.appTab())
    tourStep = next
  }

  // Notification permission → privacy notice ---------------------------------------------------
  val privacyVersion by AppSettings.privacyNoticeVersion.flow.collectAsState()
  var notificationRequested by remember { mutableStateOf(AppData.onboarding.notificationPermissionRequested) }
  var requestInFlight by remember { mutableStateOf(false) }
  var showsPrivacyConsent by remember { mutableStateOf(false) }
  val requestPermission = rememberNotificationPermissionRequester()
  val homeDepth = controller.navigator(AppTab.HOME).depth
  val shouldStartPrivacyFlow = PrivacyNoticePolicy.shouldPresent(
    reviewedVersion = privacyVersion,
    onboardingCompleted = !onboardingPresented && !hatchGateActive,
    appTourCompleted = appTourCompleted,
    hasBlockingPresentation = RootGatePolicy.privacyBlocked(
      coverPresented = appNavigator.depth > 0,
      tourActive = tourStep != null,
      homeTabSelected = controller.selected == AppTab.HOME,
      homeStackDepth = homeDepth,
    ),
  )
  LaunchedEffect(shouldStartPrivacyFlow, requestInFlight) {
    if (!shouldStartPrivacyFlow || requestInFlight || showsPrivacyConsent) return@LaunchedEffect
    when (
      OnboardingPrivacyFlowPolicy.nextAction(
        reviewedNoticeVersion = privacyVersion,
        notificationPermissionRequested = notificationRequested,
        hasStoredReminderPreference = DailyReminder.hasStoredEnabledPreference,
      )
    ) {
      OnboardingPrivacyAction.PRESENT_NOTICE -> showsPrivacyConsent = true
      OnboardingPrivacyAction.MARK_REQUESTED_THEN_PRESENT -> {
        AppData.onboarding.notificationPermissionRequested = true
        notificationRequested = true
        showsPrivacyConsent = true
      }
      OnboardingPrivacyAction.REQUEST_NOTIFICATION_PERMISSION -> {
        requestInFlight = true
        // Runs outside this effect so a tab switch cannot cancel the system prompt midway;
        // when it finishes the effect re-evaluates and shows the notice if still allowed.
        scope.launch {
          try {
            DailyReminder.enableFromOnboarding(requestPermission)
            AppData.onboarding.notificationPermissionRequested = true
            notificationRequested = true
          } catch (cancel: CancellationException) {
            throw cancel
          } catch (_: Exception) {
            AppData.onboarding.notificationPermissionRequested = true
            notificationRequested = true
          } finally {
            requestInFlight = false
          }
        }
      }
    }
  }

  // `.typedeck` documents: forward once the gate is passed; badge My Page while pending.
  val incoming by IncomingDocuments.pending.collectAsState()
  LaunchedEffect(incoming) {
    if (incoming != null) IncomingDocuments.consume()?.let(LibraryLauncher::handleIncomingIntent)
  }
  PendingDocumentBadge(controller)

  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(if (tourStep != null) Modifier.clearAndSetSemantics { } else Modifier)) {
      MainTabs(controller)
    }
    tourStep?.let { step ->
      AppTourOverlay(step = step, onAdvance = ::advanceTour, onSkip = ::completeTour)
    }
  }

  if (showsPrivacyConsent) {
    RootPrivacyConsentDialog(
      isAnalyticsUpdate = privacyVersion > 0,
      currentFeature = { controller.selected.analyticsValue },
      onFinished = { showsPrivacyConsent = false },
    )
  }
}

/** My Page badge while an incoming `.typedeck` awaits review (iOS `.badge(candidate ?? notice)`). */
@Composable
private fun PendingDocumentBadge(controller: TabController) {
  val pending by LibraryLauncher.hasPendingDocument.collectAsState()
  LaunchedEffect(pending) { controller.badges[AppTab.MY_PAGE] = pending }
}

/**
 * Pushes the mascot's external growth signals (iOS derives them from environment objects):
 * cleared chapters, streak + active days in the last week, installed decks and their tags.
 */
@Composable
private fun MascotSignalsSync() {
  val stageProgress by AppData.curriculum.stageProgress.collectAsState()
  val records by AppData.retention.records.collectAsState()
  val installed by AppData.deckLibrary.installedDecks.collectAsState()
  val today = rememberJstToday()
  LaunchedEffect(stageProgress) {
    val completed = stageProgress.keys
    MascotStore.updateClearedChapters(
      CurriculumCatalog.chapters.count { CurriculumChapterCompletionPolicy.isCompleted(it, completed) },
    )
  }
  LaunchedEffect(records, today) {
    val streak = AppData.retention.streak(today)
    val active = RetentionCalendar.days(today, 7).count { AppData.retention.isStamped(it) }
    MascotStore.updateStreak(streak.current, streak.longest, active)
  }
  LaunchedEffect(installed) {
    MascotStore.updateInstalledDecks(installed.size, installed.values.flatMap { it.tags }.toSet())
  }
}
