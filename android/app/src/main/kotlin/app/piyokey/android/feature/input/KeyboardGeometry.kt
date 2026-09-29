package app.piyokey.android.feature.input

import app.piyokey.core.hangul.Korean10KeyKey

/** iOS `HangulKeyboardAction`: what a key on either built-in keyboard does. */
sealed interface KeyboardAction {
  data class Character(val jamo: Char) : KeyboardAction
  data object Shift : KeyboardAction
  data object Backspace : KeyboardAction
  data class TenKey(val key: Korean10KeyKey) : KeyboardAction
}

/** Axis-aligned rectangle in px (pure, JVM-testable stand-in for `CGRect`). */
data class KeyRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val centerX: Float get() = (left + right) / 2f
  val centerY: Float get() = (top + bottom) / 2f

  fun outset(dx: Float, dy: Float) = KeyRect(left - dx, top - dy, right + dx, bottom + dy)

  fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

data class KeyboardTouchTarget(val action: KeyboardAction, val frame: KeyRect)

/** Finished touch: translation in dp (+y down), duration in seconds. iOS `KeyboardTouchGesture`. */
data class KeyboardTouchGesture(
  val translationXDp: Double,
  val translationYDp: Double,
  val durationSeconds: Double,
  val wasCancelled: Boolean,
)

/**
 * iOS `KeyboardTouchTargetResolver`: picks the key under a touch with a small outset so the gaps
 * between keys still hit the nearest key (closest edge, then closest center).
 */
object KeyboardTouchTargetResolver {
  /** iOS `defaultTouchOutset` (4 × 5 pt). Multiply by density for px. */
  const val DEFAULT_OUTSET_X_DP = 4f
  const val DEFAULT_OUTSET_Y_DP = 5f

  fun action(x: Float, y: Float, targets: List<KeyboardTouchTarget>, outsetX: Float, outsetY: Float): KeyboardAction? {
    var best: KeyboardTouchTarget? = null
    var bestEdge = Float.MAX_VALUE
    var bestCenter = Float.MAX_VALUE
    for (target in targets) {
      if (!target.frame.outset(maxOf(0f, outsetX), maxOf(0f, outsetY)).contains(x, y)) continue
      val edge = squaredDistanceToRect(x, y, target.frame)
      val cx = x - target.frame.centerX
      val cy = y - target.frame.centerY
      val center = cx * cx + cy * cy
      if (edge < bestEdge || (edge == bestEdge && center < bestCenter)) {
        best = target
        bestEdge = edge
        bestCenter = center
      }
    }
    return best?.action
  }

  private fun squaredDistanceToRect(x: Float, y: Float, rect: KeyRect): Float {
    val dx = maxOf(rect.left - x, 0f, x - rect.right)
    val dy = maxOf(rect.top - y, 0f, y - rect.bottom)
    return dx * dx + dy * dy
  }
}

/** iOS `HangulKeyboardGeometry`: normalized x (-1…1) of a dubeolsik key (mascot gaze etc.). */
object HangulKeyboardGeometry {
  private val rows = listOf("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ")
  private val shiftedBase = mapOf('ㅃ' to 'ㅂ', 'ㅉ' to 'ㅈ', 'ㄸ' to 'ㄷ', 'ㄲ' to 'ㄱ', 'ㅆ' to 'ㅅ', 'ㅒ' to 'ㅐ', 'ㅖ' to 'ㅔ')

  fun normalizedHorizontalPosition(key: Char?): Float {
    if (key == null) return 0f
    val base = shiftedBase[key] ?: key
    for (row in rows) {
      val index = row.indexOf(base)
      if (index < 0 || row.length <= 1) continue
      return index.toFloat() / (row.length - 1) * 2f - 1f
    }
    return 0f
  }
}

/** iOS `Korean10KeyGeometry`. */
object Korean10KeyGeometry {
  fun normalizedHorizontalPosition(key: Korean10KeyKey?): Float = when (key) {
    Korean10KeyKey.VERTICAL, Korean10KeyKey.GIYEOK, Korean10KeyKey.BIEUP, Korean10KeyKey.NEXT -> -1f
    Korean10KeyKey.DOT, Korean10KeyKey.NIEUN, Korean10KeyKey.SIOT, Korean10KeyKey.IEUNG -> 0f
    Korean10KeyKey.HORIZONTAL, Korean10KeyKey.DIGEUT, Korean10KeyKey.JIEUT -> 1f
    Korean10KeyKey.SPACE, null -> 0f
  }
}

/** Dubeolsik key definition (iOS private `KeyDefinition`). */
data class DubeolsikKey(val base: Char, val shifted: Char? = null, val roman: String) {
  fun output(isShifted: Boolean): Char = if (isShifted) shifted ?: base else base

  companion object {
    val SHIFTED_CHARACTERS: Set<Char> = "ㅃㅉㄸㄲㅆㅒㅖ".toSet()

    val TOP_ROW = listOf(
      DubeolsikKey('ㅂ', 'ㅃ', "q"), DubeolsikKey('ㅈ', 'ㅉ', "w"), DubeolsikKey('ㄷ', 'ㄸ', "e"),
      DubeolsikKey('ㄱ', 'ㄲ', "r"), DubeolsikKey('ㅅ', 'ㅆ', "t"), DubeolsikKey('ㅛ', roman = "y"),
      DubeolsikKey('ㅕ', roman = "u"), DubeolsikKey('ㅑ', roman = "i"), DubeolsikKey('ㅐ', 'ㅒ', "o"),
      DubeolsikKey('ㅔ', 'ㅖ', "p"),
    )
    val HOME_ROW = listOf(
      DubeolsikKey('ㅁ', roman = "a"), DubeolsikKey('ㄴ', roman = "s"), DubeolsikKey('ㅇ', roman = "d"),
      DubeolsikKey('ㄹ', roman = "f"), DubeolsikKey('ㅎ', roman = "g"), DubeolsikKey('ㅗ', roman = "h"),
      DubeolsikKey('ㅓ', roman = "j"), DubeolsikKey('ㅏ', roman = "k"), DubeolsikKey('ㅣ', roman = "l"),
    )
    val BOTTOM_ROW = listOf(
      DubeolsikKey('ㅋ', roman = "z"), DubeolsikKey('ㅌ', roman = "x"), DubeolsikKey('ㅊ', roman = "c"),
      DubeolsikKey('ㅍ', roman = "v"), DubeolsikKey('ㅠ', roman = "b"), DubeolsikKey('ㅜ', roman = "n"),
      DubeolsikKey('ㅡ', roman = "m"),
    )
  }
}

/** Key-guide highlight rules of iOS `HangulKeyboardView` (pure so they are unit-tested). */
object DubeolsikGuide {
  /** Shift glows when the expected jamo is a shifted one and shift is not yet on. */
  fun shiftHighlighted(showsKeyGuide: Boolean, expected: Char?, isShifted: Boolean): Boolean =
    showsKeyGuide && expected != null && !isShifted && expected in DubeolsikKey.SHIFTED_CHARACTERS

  /** A character key glows when it currently outputs the expected jamo (after shift if needed). */
  fun keyHighlighted(showsKeyGuide: Boolean, expected: Char?, output: Char, isShifted: Boolean): Boolean {
    val needsShift = (expected ?: ' ') in DubeolsikKey.SHIFTED_CHARACTERS
    return showsKeyGuide && expected == output && (!needsShift || isShifted)
  }

  fun spaceHighlighted(showsKeyGuide: Boolean, expected: Char?): Boolean = showsKeyGuide && expected == ' '
}

/** 10-key layout (rows of flick keys; bottom rows are `→ ㅇㅁ ⌫` and space). */
object Korean10KeyLayout {
  val ROWS = listOf(
    listOf(Korean10KeyKey.VERTICAL, Korean10KeyKey.DOT, Korean10KeyKey.HORIZONTAL),
    listOf(Korean10KeyKey.GIYEOK, Korean10KeyKey.NIEUN, Korean10KeyKey.DIGEUT),
    listOf(Korean10KeyKey.BIEUP, Korean10KeyKey.SIOT, Korean10KeyKey.JIEUT),
  )

  /** Localization key of the key's accessibility label (iOS `accessibilityKey`). */
  fun accessibilityKey(key: Korean10KeyKey): String =
    if (key == Korean10KeyKey.SPACE) "keyboard.space" else "keyboard.10key.${key.rawValue}"

  /** Arguments of the flick accessibility hint (iOS `flickAccessibilityHint`). */
  sealed interface FlickHint {
    data class Fixed(val key: String) : FlickHint
    data class Three(val left: String, val right: String, val down: String) : FlickHint
    data class Two(val left: String, val right: String) : FlickHint
    data object None : FlickHint
  }

  fun flickHint(key: Korean10KeyKey): FlickHint = when (key) {
    Korean10KeyKey.VERTICAL -> FlickHint.Fixed("keyboard.10key.flick.vertical")
    Korean10KeyKey.DOT -> FlickHint.Fixed("keyboard.10key.flick.dot")
    Korean10KeyKey.HORIZONTAL -> FlickHint.Fixed("keyboard.10key.flick.horizontal")
    Korean10KeyKey.GIYEOK -> FlickHint.Three("ㄱ", "ㅋ", "ㄲ")
    Korean10KeyKey.DIGEUT -> FlickHint.Three("ㄷ", "ㅌ", "ㄸ")
    Korean10KeyKey.BIEUP -> FlickHint.Three("ㅂ", "ㅍ", "ㅃ")
    Korean10KeyKey.SIOT -> FlickHint.Three("ㅅ", "ㅎ", "ㅆ")
    Korean10KeyKey.JIEUT -> FlickHint.Three("ㅈ", "ㅊ", "ㅉ")
    Korean10KeyKey.NIEUN -> FlickHint.Two("ㄴ", "ㄹ")
    Korean10KeyKey.IEUNG -> FlickHint.Two("ㅇ", "ㅁ")
    Korean10KeyKey.NEXT, Korean10KeyKey.SPACE -> FlickHint.None
  }
}
