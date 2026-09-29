package app.piyokey.android.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.atomic.AtomicLong

/**
 * A destination. Implementations are plain classes owned by each feature, e.g.
 * `class DeckDetailRoute(val deckId: String) : Route { @Composable override fun Content() { ... } }`.
 */
interface Route {
  @Composable
  fun Content()
}

@Stable
class Navigator(root: Route? = null) {
  private data class Entry(val id: Long, val route: Route)

  private val entries = mutableStateListOf<Entry>()

  init {
    root?.let { entries += Entry(next(), it) }
  }

  val routes: List<Route> get() = entries.map { it.route }
  val depth: Int get() = entries.size
  val top: Route? get() = entries.lastOrNull()?.route
  val canPop: Boolean get() = entries.size > 1

  fun push(route: Route) {
    entries += Entry(next(), route)
  }

  fun pop(): Boolean {
    if (entries.isEmpty()) return false
    entries.removeAt(entries.lastIndex)
    return true
  }

  /** Removes every destination above the root (iOS `popToRoot`). */
  fun popToRoot() {
    while (entries.size > 1) entries.removeAt(entries.lastIndex)
  }

  fun clear() = entries.clear()

  /** Replaces the top destination (used for "next mission" / retry flows). */
  fun replaceTop(route: Route) {
    if (entries.isNotEmpty()) entries.removeAt(entries.lastIndex)
    push(route)
  }

  @Composable
  internal fun RenderStack(allowRootBack: Boolean = false) {
    val holder = rememberSaveableStateHolder()
    val current = entries.lastOrNull() ?: return
    BackHandler(enabled = entries.size > 1 || (allowRootBack && entries.isNotEmpty())) { pop() }
    AnimatedContent(
      targetState = current,
      transitionSpec = {
        val forward = targetState.id > initialState.id
        (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
          (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
      },
      label = "nav",
    ) { entry ->
      holder.SaveableStateProvider(entry.id) { entry.route.Content() }
    }
  }

  private companion object {
    val counter = AtomicLong()
    fun next() = counter.incrementAndGet()
  }
}

/** Navigator of the currently visible tab (pushes stay inside the tab, tab bar visible). */
val LocalTabNavigator = staticCompositionLocalOf<Navigator> { error("No tab navigator") }

/**
 * App-level cover navigator: sessions, games, onboarding flows and full-screen sheets
 * are pushed here and render above the tab bar (iOS `fullScreenCover`).
 */
val LocalAppNavigator = staticCompositionLocalOf<Navigator> { error("No app navigator") }
