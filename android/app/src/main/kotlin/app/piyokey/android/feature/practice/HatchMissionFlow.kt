package app.piyokey.android.feature.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.piyokey.android.data.AppData
import app.piyokey.android.data.mascot.MascotStage
import app.piyokey.android.data.mascot.MascotStore
import app.piyokey.android.ui.mascot.GrowthCelebration
import app.piyokey.android.ui.nav.LocalAppNavigator
import app.piyokey.android.ui.nav.LocalTabNavigator
import app.piyokey.android.ui.nav.Route
import app.piyokey.android.ui.theme.PiyoBackground
import app.piyokey.core.domain.CurriculumCatalog
import app.piyokey.core.domain.HatchOnboardingPolicy
import app.piyokey.core.domain.practice.HatchMissionTransitionCoordinator
import app.piyokey.core.domain.practice.HatchMissionTransitionCoordinator.Action
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The hatch onboarding shown by the root gate before the tabs (iOS
 * `CurriculumMapView(isHatchOnboarding: true)`): chapters 1–3 as missions. [onComplete] runs once
 * the third mission's result (and any growth celebration) has been dismissed, or from "open app".
 */
@Composable
fun HatchMissionFlow(onComplete: () -> Unit) {
  PiyoBackground {
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
      CurriculumMapScreen(isHatchOnboarding = true, onHatchCompleted = onComplete)
    }
  }
}

/**
 * iOS `HatchMissionSequenceDestination`: plays the required missions back to back. Each result's
 * "next mission" waits for the result to settle, shows a pending growth celebration, then swaps
 * in the next mission (TYP-102/105: every step exactly once, paused in the background).
 */
class HatchMissionSequenceRoute(private val initialStageId: String, private val onHatchCompleted: () -> Unit) : Route {
  @Composable
  override fun Content() {
    val appNavigator = LocalAppNavigator.current
    val scope = rememberCoroutineScope()
    var activeStageId by remember { mutableStateOf(initialStageId) }
    val transition = remember { HatchMissionTransitionCoordinator<MascotStage>(initialStageId) }
    var celebrating by remember { mutableStateOf<MascotStage?>(null) }
    var transitionJob by remember { mutableStateOf<Job?>(null) }
    var isForeground by remember { mutableStateOf(true) }

    fun currentStage() = MascotStage.forClearedChapters(PracticeGrowth.clearedChapterCount())

    lateinit var perform: (Action<MascotStage>) -> Unit

    fun scheduleTransitionIfNeeded() {
      if (!transition.isWaitingForPresentationTransition || !isForeground) return
      transitionJob?.cancel()
      transitionJob = scope.launch {
        delay(HatchMissionTransitionCoordinator.PRESENTATION_SETTLEMENT_MILLIS)
        if (!isForeground) return@launch
        transitionJob = null
        perform(transition.presentationTransitionDidFinish())
      }
    }

    perform = { action ->
      when (action) {
        Action.None -> Unit
        Action.WaitForPresentationTransition -> scheduleTransitionIfNeeded()
        is Action.PresentCelebration -> celebrating = action.stage
        is Action.AdvanceToMission -> {
          activeStageId = action.stageId
          transition.missionDidActivate(action.stageId)
        }
        Action.CompleteHatch -> {
          appNavigator.pop()
          onHatchCompleted()
        }
      }
    }

    fun settleAfterResult(completedStageId: String) {
      val next = HatchOnboardingPolicy.nextRequiredStage(AppData.curriculum.completedStageIds)?.id
      perform(transition.resultDidDismiss(completedStageId, next, MascotStore.pendingCelebration(currentStage())))
    }

    LaunchedEffect(Unit) {
      if (celebrating == null && !transition.ownsCelebration(activeStageId)) {
        celebrating = MascotStore.pendingCelebration(currentStage())
      }
      scheduleTransitionIfNeeded()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
      isForeground = true
      scheduleTransitionIfNeeded()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
      isForeground = false
      transitionJob?.cancel()
      transitionJob = null
    }

    CompositionLocalProvider(LocalTabNavigator provides appNavigator) {
      Box(Modifier.fillMaxSize()) {
        val stage = CurriculumCatalog.stage(activeStageId)
        if (stage != null) {
          key(stage.id) {
            val config = remember {
              PracticeLauncher.curriculumConfig(
                stageId = stage.id,
                chainsHatchMissions = true,
                isFinalHatchMission = stage.id == HatchOnboardingPolicy.requiredStages.last().id,
                onResultFinished = { settleAfterResult(stage.id) },
                onPersistenceFailureExit = { appNavigator.pop() },
              )
            }
            if (config != null) {
              PracticeSessionScreen(config, onReplaceConfig = {}, onDismiss = { appNavigator.pop() })
            }
          }
        }
        celebrating?.let { shown ->
          GrowthCelebration(
            newStage = shown,
            onDone = {
              MascotStore.markCelebrated(shown)
              celebrating = null
              perform(transition.celebrationDidDismiss())
            },
          )
        }
      }
    }
  }
}
