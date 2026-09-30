package app.piyokey.android.feature.onboarding

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Spotlight targets of the first-run app tour (iOS `AppTourTarget`). */
enum class AppTourTarget(val rawValue: String) {
  HOME_PRIMARY("homePrimary"),
  DISCOVER_SEARCH("discoverSearch"),
  PRACTICE_CURRICULUM("practiceCurriculum"),
  GAME_MODES("gameModes"),
  MY_PAGE_PROFILE("myPageProfile"),
  SETTINGS("settings"),
}

/**
 * Global registry of the latest on-screen bounds (root coordinates, px) of each tour target —
 * the Compose equivalent of iOS `AppTourTargetPreferenceKey`. Several elements may carry the same
 * target (e.g. the settings gear on every tab root); the most recently positioned one wins and a
 * disposed element only clears the entry it owns.
 */
object AppTourTargets {
  private class Entry(val owner: Any, val rect: Rect)

  private val entries: SnapshotStateMap<AppTourTarget, Entry> = mutableStateMapOf()

  /** Current bounds of [target], or null when no element with that target is on screen. */
  fun bounds(target: AppTourTarget): Rect? = entries[target]?.rect

  internal fun update(target: AppTourTarget, owner: Any, rect: Rect) {
    val current = entries[target]
    if (current == null || current.owner !== owner || current.rect != rect) entries[target] = Entry(owner, rect)
  }

  internal fun clear(target: AppTourTarget, owner: Any) {
    if (entries[target]?.owner === owner) entries.remove(target)
  }

  /** Test/debug helper. */
  fun reset() = entries.clear()
}

/**
 * Marks this element as the spotlight for [target] (iOS `.appTourTarget(_:enabled:)`).
 * Apply to the same elements iOS does; bounds are reported in root coordinates.
 */
fun Modifier.appTourTarget(target: AppTourTarget, enabled: Boolean = true): Modifier =
  if (!enabled) this
  else composed {
    val owner = remember { Any() }
    DisposableEffect(target, owner) { onDispose { AppTourTargets.clear(target, owner) } }
    onGloballyPositioned { coordinates ->
      if (coordinates.isAttached) AppTourTargets.update(target, owner, coordinates.boundsInRoot())
    }
  }
