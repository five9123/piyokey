package app.piyokey.core.domain.practice

/**
 * iOS `HatchMissionTransitionCoordinator` (TYP-102/TYP-105): serializes "result dismissed →
 * navigation settled → optional growth celebration → next mission / home" so each step happens
 * exactly once, even when views reappear or the app is backgrounded mid-transition.
 *
 * [C] is the celebration payload (the app's `MascotStage`).
 */
class HatchMissionTransitionCoordinator<C : Any>(activeStageId: String) {
  sealed interface Destination {
    data class Mission(val stageId: String) : Destination
    data object Home : Destination
  }

  sealed interface State<out C> {
    data class AwaitingResultDismissal(val stageId: String) : State<Nothing>
    data class WaitingForPresentationTransition<C>(
      val completedStageId: String,
      val destination: Destination,
      val pendingCelebration: C?,
    ) : State<C>
    data class Celebrating<C>(val completedStageId: String, val destination: Destination, val stage: C) : State<C>
    data class Advancing(val completedStageId: String, val destination: Destination) : State<Nothing>
    data object Completed : State<Nothing>
  }

  sealed interface Action<out C> {
    data object None : Action<Nothing>
    data object WaitForPresentationTransition : Action<Nothing>
    data class PresentCelebration<C>(val stage: C) : Action<C>
    data class AdvanceToMission(val stageId: String) : Action<Nothing>
    data object CompleteHatch : Action<Nothing>
  }

  var state: State<C> = State.AwaitingResultDismissal(activeStageId)
    private set

  val isWaitingForPresentationTransition: Boolean
    get() = state is State.WaitingForPresentationTransition<*>

  fun ownsCelebration(stageId: String): Boolean = when (val current = state) {
    is State.WaitingForPresentationTransition<*> -> current.completedStageId == stageId
    is State.Celebrating<*> -> current.completedStageId == stageId
    else -> false
  }

  fun resultDidDismiss(completedStageId: String, nextStageId: String?, pendingCelebration: C?): Action<C> {
    val current = state as? State.AwaitingResultDismissal ?: return Action.None
    if (current.stageId != completedStageId) return Action.None
    val destination = nextStageId?.let { Destination.Mission(it) } ?: Destination.Home
    state = State.WaitingForPresentationTransition(completedStageId, destination, pendingCelebration)
    return Action.WaitForPresentationTransition
  }

  fun presentationTransitionDidFinish(): Action<C> {
    @Suppress("UNCHECKED_CAST")
    val current = state as? State.WaitingForPresentationTransition<C> ?: return Action.None
    val celebration = current.pendingCelebration
    if (celebration != null) {
      state = State.Celebrating(current.completedStageId, current.destination, celebration)
      return Action.PresentCelebration(celebration)
    }
    return beginAdvance(current.completedStageId, current.destination)
  }

  fun celebrationDidDismiss(): Action<C> {
    val current = state as? State.Celebrating<*> ?: return Action.None
    return beginAdvance(current.completedStageId, current.destination)
  }

  fun missionDidActivate(stageId: String): Boolean {
    val current = state as? State.Advancing ?: return false
    val destination = current.destination as? Destination.Mission ?: return false
    if (destination.stageId != stageId) return false
    state = State.AwaitingResultDismissal(stageId)
    return true
  }

  private fun beginAdvance(completedStageId: String, destination: Destination): Action<C> = when (destination) {
    is Destination.Mission -> {
      state = State.Advancing(completedStageId, destination)
      Action.AdvanceToMission(destination.stageId)
    }
    Destination.Home -> {
      state = State.Completed
      Action.CompleteHatch
    }
  }

  companion object {
    /** Wait for the result pop to settle before celebrating / advancing (iOS 650 ms). */
    const val PRESENTATION_SETTLEMENT_MILLIS = 650L
  }
}
