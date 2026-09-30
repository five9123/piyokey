package app.piyokey.android.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
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

  /**
   * Draws every entry, top last. Lower entries stay composed (hidden, no input, no semantics) so a
   * session or result under a pushed screen keeps its state and is not restarted on Back.
   */
  @Composable
  internal fun RenderStack(allowRootBack: Boolean = false) {
    val holder = rememberSaveableStateHolder()
    if (entries.isEmpty()) return
    BackHandler(enabled = entries.size > 1 || (allowRootBack && entries.isNotEmpty())) { pop() }
    Box(Modifier.fillMaxSize()) {
      val snapshot = entries.toList()
      snapshot.forEachIndexed { index, entry ->
        key(entry.id) {
          val isTop = index == snapshot.lastIndex
          // Pushed entries slide/fade in once; the root entry appears without animation.
          val appear = remember { Animatable(if (index == 0) 1f else 0f) }
          LaunchedEffect(Unit) { appear.animateTo(1f, tween(220)) }
          Box(
            Modifier
              .fillMaxSize()
              .graphicsLayer {
                alpha = if (isTop) appear.value else 0f
                translationX = (1f - appear.value) * size.width / 4f
              }
              .then(if (isTop) Modifier.blockPointersBelow() else Modifier.clearAndSetSemantics {}),
          ) {
            holder.SaveableStateProvider(entry.id) { entry.route.Content() }
          }
        }
      }
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

/**
 * Makes this layer the hit target for its whole area so touches never reach siblings drawn below
 * it (tabs under a full-screen session, or hidden stack entries). Children still receive events.
 */
fun Modifier.blockPointersBelow(): Modifier = pointerInput(Unit) {
  awaitPointerEventScope {
    while (true) awaitPointerEvent()
  }
}
