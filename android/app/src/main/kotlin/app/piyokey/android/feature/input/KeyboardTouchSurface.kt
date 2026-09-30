package app.piyokey.android.feature.input

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Per-keyboard registry of key bounds plus pressed-key counts (iOS `KeyboardKeyBoundsPreferenceKey`
 * + `pressedActionCounts`). Keys register their [LayoutCoordinates]; the touch surface resolves
 * touches against them in its own coordinate space.
 */
/** One rendered key: its current action (changes with shift) and its layout coordinates. */
internal class KeySlot(var action: KeyboardAction) {
  var coordinates: LayoutCoordinates? = null
}

/**
 * Per-keyboard registry of key bounds plus pressed-key counts (iOS `KeyboardKeyBoundsPreferenceKey`
 * + `pressedActionCounts`). Each rendered key owns a [KeySlot]; the touch surface resolves touches
 * against the slots' current actions in its own coordinate space.
 */
@Stable
internal class KeyboardTouchRegistry {
  private val slots = LinkedHashSet<KeySlot>()
  private var surface: LayoutCoordinates? = null
  private val pressedCounts = mutableStateMapOf<KeyboardAction, Int>()

  fun add(slot: KeySlot) {
    slots += slot
  }

  fun remove(slot: KeySlot) {
    slots -= slot
  }

  fun registerSurface(coordinates: LayoutCoordinates) {
    surface = coordinates
  }

  fun isPressed(action: KeyboardAction): Boolean = (pressedCounts[action] ?: 0) > 0

  fun press(action: KeyboardAction) {
    pressedCounts[action] = (pressedCounts[action] ?: 0) + 1
  }

  fun release(action: KeyboardAction) {
    val remaining = (pressedCounts[action] ?: 0) - 1
    if (remaining > 0) pressedCounts[action] = remaining else pressedCounts.remove(action)
  }

  /** Current key frames in the surface's local px space, sorted top-to-bottom then left-to-right. */
  fun targets(): List<KeyboardTouchTarget> {
    val surface = surface?.takeIf { it.isAttached } ?: return emptyList()
    return slots.mapNotNull { slot ->
      val coordinates = slot.coordinates?.takeIf { it.isAttached } ?: return@mapNotNull null
      val box = surface.localBoundingBoxOf(coordinates, clipBounds = false)
      KeyboardTouchTarget(slot.action, KeyRect(box.left, box.top, box.right, box.bottom))
    }.sortedWith(compareBy({ Math.round(it.frame.top * 2) }, { it.frame.left }))
  }
}

@Composable
internal fun rememberKeyboardTouchRegistry() = remember { KeyboardTouchRegistry() }

/** Registers this key (with its *current* action) as a touch target of [registry]. */
@Composable
internal fun Modifier.registerKey(registry: KeyboardTouchRegistry, action: KeyboardAction): Modifier {
  val slot = remember(registry) { KeySlot(action) }
  slot.action = action
  DisposableEffect(registry, slot) {
    registry.add(slot)
    onDispose { registry.remove(slot) }
  }
  return onGloballyPositioned { slot.coordinates = it }
}

private class TrackedTouch(val action: KeyboardAction, val x: Float, val y: Float, val uptimeMillis: Long)

/**
 * Multi-touch rollover surface (iOS `RolloverKeyboardTouchView`). Every pointer is tracked
 * independently: [onTouchBegan] fires on touch-down (with the event uptime) so immediate keys
 * commit without waiting for the previous finger to lift, and [onTouchEnded] reports the
 * translation (dp) / duration of that pointer for flick resolution.
 *
 * Compose delivers a system cancel (parent intercept, window focus loss) as a synthetic
 * pre-consumed "up"; that is reported as `wasCancelled = true`, like `touchesCancelled`.
 */
@Composable
internal fun Modifier.rolloverTouchSurface(
  registry: KeyboardTouchRegistry,
  onTouchBegan: (KeyboardAction, Long) -> Unit,
  onTouchEnded: (KeyboardAction, KeyboardTouchGesture) -> Unit,
): Modifier {
  val began by rememberUpdatedState(onTouchBegan)
  val ended by rememberUpdatedState(onTouchEnded)
  return this
    .onGloballyPositioned { registry.registerSurface(it) }
    .pointerInput(registry) {
      val tracked = HashMap<PointerId, TrackedTouch>()
      val outsetX = KeyboardTouchTargetResolver.DEFAULT_OUTSET_X_DP * density
      val outsetY = KeyboardTouchTargetResolver.DEFAULT_OUTSET_Y_DP * density
      try {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            for (change in event.changes) {
              when {
                change.changedToDownIgnoreConsumed() -> {
                  if (tracked.containsKey(change.id)) continue
                  val action = KeyboardTouchTargetResolver.action(
                    change.position.x, change.position.y, registry.targets(), outsetX, outsetY,
                  ) ?: continue
                  change.consume()
                  tracked[change.id] = TrackedTouch(action, change.position.x, change.position.y, change.uptimeMillis)
                  began(action, change.uptimeMillis)
                }
                change.changedToUpIgnoreConsumed() -> {
                  val touch = tracked.remove(change.id) ?: continue
                  val wasCancelled = change.isConsumed
                  change.consume()
                  ended(
                    touch.action,
                    KeyboardTouchGesture(
                      translationXDp = ((change.position.x - touch.x) / density).toDouble(),
                      translationYDp = ((change.position.y - touch.y) / density).toDouble(),
                      durationSeconds = maxOf(0L, change.uptimeMillis - touch.uptimeMillis) / 1000.0,
                      wasCancelled = wasCancelled,
                    ),
                  )
                }
                tracked.containsKey(change.id) -> change.consume()
              }
            }
          }
        }
      } finally {
        // Surface detached or restarted mid-touch: end every outstanding touch as cancelled.
        val remaining = tracked.values.toList()
        tracked.clear()
        remaining.forEach { ended(it.action, KeyboardTouchGesture(0.0, 0.0, 0.0, wasCancelled = true)) }
      }
    }
}

/** iOS `KeyHaptics` (light impact): a keyboard-tap haptic that follows the system touch-feedback setting. */
internal object KeyHaptics {
  fun fire(view: View, enabled: Boolean) {
    if (!enabled) return
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
  }
}

/** Uptime used for accessibility activations (no pointer event). */
internal fun nowUptimeMillis(): Long = SystemClock.uptimeMillis()
