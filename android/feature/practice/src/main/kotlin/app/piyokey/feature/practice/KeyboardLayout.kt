package app.piyokey.feature.practice

import kotlin.math.max

/** One physical action on the built-in two-beolsik keyboard. */
internal sealed interface KeyboardAction {
  data class JamoKey(val base: Char) : KeyboardAction
  data object Shift : KeyboardAction
  data object Backspace : KeyboardAction
  data object Space : KeyboardAction
}

internal data class JamoKeyDefinition(
  val base: Char,
  val roman: String,
  val shifted: Char? = null,
) {
  fun output(isShifted: Boolean): Char = if (isShifted) shifted ?: base else base
}

internal object DubeolsikLayout {
  val topRow = listOf(
    JamoKeyDefinition('ㅂ', "q", 'ㅃ'),
    JamoKeyDefinition('ㅈ', "w", 'ㅉ'),
    JamoKeyDefinition('ㄷ', "e", 'ㄸ'),
    JamoKeyDefinition('ㄱ', "r", 'ㄲ'),
    JamoKeyDefinition('ㅅ', "t", 'ㅆ'),
    JamoKeyDefinition('ㅛ', "y"),
    JamoKeyDefinition('ㅕ', "u"),
    JamoKeyDefinition('ㅑ', "i"),
    JamoKeyDefinition('ㅐ', "o", 'ㅒ'),
    JamoKeyDefinition('ㅔ', "p", 'ㅖ'),
  )

  val homeRow = listOf(
    JamoKeyDefinition('ㅁ', "a"),
    JamoKeyDefinition('ㄴ', "s"),
    JamoKeyDefinition('ㅇ', "d"),
    JamoKeyDefinition('ㄹ', "f"),
    JamoKeyDefinition('ㅎ', "g"),
    JamoKeyDefinition('ㅗ', "h"),
    JamoKeyDefinition('ㅓ', "j"),
    JamoKeyDefinition('ㅏ', "k"),
    JamoKeyDefinition('ㅣ', "l"),
  )

  val bottomRow = listOf(
    JamoKeyDefinition('ㅋ', "z"),
    JamoKeyDefinition('ㅌ', "x"),
    JamoKeyDefinition('ㅊ', "c"),
    JamoKeyDefinition('ㅍ', "v"),
    JamoKeyDefinition('ㅠ', "b"),
    JamoKeyDefinition('ㅜ', "n"),
    JamoKeyDefinition('ㅡ', "m"),
  )

  val rows = listOf(topRow, homeRow, bottomRow)
  val shiftedCharacters = rows.flatten().mapNotNull(JamoKeyDefinition::shifted).toSet()

  private val definitionsByOutput = buildMap {
    rows.flatten().forEach { definition ->
      put(definition.base, definition)
      definition.shifted?.let { put(it, definition) }
    }
  }

  fun definitionFor(output: Char): JamoKeyDefinition? = definitionsByOutput[output]
}

internal data class KeyboardGuideState(
  val highlightedAction: KeyboardAction? = null,
)

internal object KeyboardGuideResolver {
  fun resolve(
    expected: Char?,
    isShifted: Boolean,
    enabled: Boolean,
  ): KeyboardGuideState {
    if (!enabled || expected == null) return KeyboardGuideState()
    if (expected == ' ') return KeyboardGuideState(KeyboardAction.Space)

    val definition = DubeolsikLayout.definitionFor(expected) ?: return KeyboardGuideState()
    if (expected in DubeolsikLayout.shiftedCharacters && !isShifted) {
      return KeyboardGuideState(KeyboardAction.Shift)
    }

    val visibleOutput = definition.output(isShifted)
    return if (visibleOutput == expected) {
      KeyboardGuideState(KeyboardAction.JamoKey(definition.base))
    } else {
      KeyboardGuideState()
    }
  }
}

internal object KeyboardKeycapMetrics {
  const val BaseHeightDp = 50f
  const val MainLineHeightSp = 24f
  const val RomanLineHeightSp = 11f
  private const val VerticalSafetyDp = 10f

  /** Keeps both fixed text lines visible while letting each keyboard row grow intrinsically. */
  fun heightDp(fontScale: Float): Float = max(
    BaseHeightDp,
    (MainLineHeightSp + RomanLineHeightSp) * fontScale + VerticalSafetyDp,
  )
}

/** One-shot Shift latch with synchronous semantics for same-frame rollover input. */
internal class KeyboardShiftLatch {
  var isShifted: Boolean = false
    private set

  fun toggle(): Boolean {
    isShifted = !isShifted
    return isShifted
  }

  fun consume(definition: JamoKeyDefinition): Char {
    val output = definition.output(isShifted)
    isShifted = false
    return output
  }

  fun consumeSpace() {
    isShifted = false
  }
}

/** Tracks independent pointers so a second key can be accepted before the first finger is lifted. */
internal class RolloverTouchTracker {
  private val actionsByPointer = mutableMapOf<Long, KeyboardAction>()

  fun begin(pointerId: Long, action: KeyboardAction): Boolean {
    if (actionsByPointer.containsKey(pointerId)) return false
    actionsByPointer[pointerId] = action
    return true
  }

  fun end(pointerId: Long): KeyboardAction? = actionsByPointer.remove(pointerId)

  fun pressedCount(action: KeyboardAction): Int = actionsByPointer.values.count { it == action }

  fun clear(): Set<KeyboardAction> {
    val pressed = actionsByPointer.values.toSet()
    actionsByPointer.clear()
    return pressed
  }
}
