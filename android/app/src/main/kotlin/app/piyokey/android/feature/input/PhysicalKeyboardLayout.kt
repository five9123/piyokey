package app.piyokey.android.feature.input

import androidx.annotation.StringRes
import app.piyokey.android.R
import app.piyokey.android.ui.theme.AdaptiveMetrics

/** iOS `PhysicalKeyboardHand`. */
enum class PhysicalKeyboardHand(val localizationKey: String, @StringRes val labelRes: Int) {
  LEFT("physical_keyboard.hand.left", R.string.physical_keyboard_hand_left),
  RIGHT("physical_keyboard.hand.right", R.string.physical_keyboard_hand_right),
  BOTH("physical_keyboard.hand.both", R.string.physical_keyboard_hand_both);

  val opposite: PhysicalKeyboardHand
    get() = when (this) {
      LEFT -> RIGHT
      RIGHT -> LEFT
      BOTH -> BOTH
    }
}

/** iOS `PhysicalKeyboardFinger`. */
enum class PhysicalKeyboardFinger(val localizationKey: String, @StringRes val labelRes: Int) {
  LITTLE("physical_keyboard.finger.little", R.string.physical_keyboard_finger_little),
  RING("physical_keyboard.finger.ring", R.string.physical_keyboard_finger_ring),
  MIDDLE("physical_keyboard.finger.middle", R.string.physical_keyboard_finger_middle),
  INDEX("physical_keyboard.finger.index", R.string.physical_keyboard_finger_index),
  THUMB("physical_keyboard.finger.thumb", R.string.physical_keyboard_finger_thumb),
}

data class PhysicalKeyboardKeySpec(
  val latin: Char,
  val baseJamo: Char,
  val shiftedJamo: Char?,
  val hand: PhysicalKeyboardHand,
  val finger: PhysicalKeyboardFinger,
  val isHomePosition: Boolean,
)

data class PhysicalKeyboardTarget(
  val key: PhysicalKeyboardKeySpec?,
  val expected: Char,
  val requiresShift: Boolean,
  val shiftHand: PhysicalKeyboardHand?,
) {
  val hand: PhysicalKeyboardHand get() = key?.hand ?: PhysicalKeyboardHand.BOTH
  val finger: PhysicalKeyboardFinger get() = key?.finger ?: PhysicalKeyboardFinger.THUMB
}

/** iOS `PhysicalDubeolsikLayout`: hardware 2-set layout with recommended hand/finger. */
object PhysicalDubeolsikLayout {
  private val L = PhysicalKeyboardHand.LEFT
  private val R = PhysicalKeyboardHand.RIGHT

  val rows: List<List<PhysicalKeyboardKeySpec>> = listOf(
    listOf(
      key('Q', 'ㅂ', 'ㅃ', L, PhysicalKeyboardFinger.LITTLE),
      key('W', 'ㅈ', 'ㅉ', L, PhysicalKeyboardFinger.RING),
      key('E', 'ㄷ', 'ㄸ', L, PhysicalKeyboardFinger.MIDDLE),
      key('R', 'ㄱ', 'ㄲ', L, PhysicalKeyboardFinger.INDEX),
      key('T', 'ㅅ', 'ㅆ', L, PhysicalKeyboardFinger.INDEX),
      key('Y', 'ㅛ', null, R, PhysicalKeyboardFinger.INDEX),
      key('U', 'ㅕ', null, R, PhysicalKeyboardFinger.INDEX),
      key('I', 'ㅑ', null, R, PhysicalKeyboardFinger.MIDDLE),
      key('O', 'ㅐ', 'ㅒ', R, PhysicalKeyboardFinger.RING),
      key('P', 'ㅔ', 'ㅖ', R, PhysicalKeyboardFinger.LITTLE),
    ),
    listOf(
      key('A', 'ㅁ', null, L, PhysicalKeyboardFinger.LITTLE),
      key('S', 'ㄴ', null, L, PhysicalKeyboardFinger.RING),
      key('D', 'ㅇ', null, L, PhysicalKeyboardFinger.MIDDLE),
      key('F', 'ㄹ', null, L, PhysicalKeyboardFinger.INDEX, home = true),
      key('G', 'ㅎ', null, L, PhysicalKeyboardFinger.INDEX),
      key('H', 'ㅗ', null, R, PhysicalKeyboardFinger.INDEX),
      key('J', 'ㅓ', null, R, PhysicalKeyboardFinger.INDEX, home = true),
      key('K', 'ㅏ', null, R, PhysicalKeyboardFinger.MIDDLE),
      key('L', 'ㅣ', null, R, PhysicalKeyboardFinger.RING),
    ),
    listOf(
      key('Z', 'ㅋ', null, L, PhysicalKeyboardFinger.LITTLE),
      key('X', 'ㅌ', null, L, PhysicalKeyboardFinger.RING),
      key('C', 'ㅊ', null, L, PhysicalKeyboardFinger.MIDDLE),
      key('V', 'ㅍ', null, L, PhysicalKeyboardFinger.INDEX),
      key('B', 'ㅠ', null, L, PhysicalKeyboardFinger.INDEX),
      key('N', 'ㅜ', null, R, PhysicalKeyboardFinger.INDEX),
      key('M', 'ㅡ', null, R, PhysicalKeyboardFinger.INDEX),
    ),
  )

  fun target(expected: Char?): PhysicalKeyboardTarget? {
    if (expected == null) return null
    if (expected == ' ') return PhysicalKeyboardTarget(null, expected, requiresShift = false, shiftHand = null)
    for (key in rows.flatten()) {
      if (key.baseJamo == expected) return PhysicalKeyboardTarget(key, expected, requiresShift = false, shiftHand = null)
      if (key.shiftedJamo == expected) {
        return PhysicalKeyboardTarget(key, expected, requiresShift = true, shiftHand = key.hand.opposite)
      }
    }
    return null
  }

  private fun key(
    latin: Char,
    base: Char,
    shifted: Char?,
    hand: PhysicalKeyboardHand,
    finger: PhysicalKeyboardFinger,
    home: Boolean = false,
  ) = PhysicalKeyboardKeySpec(latin, base, shifted, hand, finger, home)
}

/**
 * iOS `PhysicalKeyboardGuidePolicy` shows the guide only on iPad. Android has no idiom, so the
 * tablet test is the adaptive width class (`isExpanded`, ≥ 600dp window).
 */
object PhysicalKeyboardGuidePolicy {
  fun isVisible(metrics: AdaptiveMetrics): Boolean = metrics.isExpanded
}

/**
 * iOS `OSIMEInputPanelPolicy.showsVisibleFocusRecovery`: the visible "tap to type" strip is
 * iPad-only; phones keep an invisible full-area field. Tablet == `isExpanded` on Android.
 */
object OsImeInputPanelPolicy {
  fun showsVisibleFocusRecovery(requested: Boolean, metrics: AdaptiveMetrics): Boolean = requested && metrics.isExpanded
}
