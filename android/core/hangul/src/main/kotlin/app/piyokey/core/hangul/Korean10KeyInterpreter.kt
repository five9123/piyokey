package app.piyokey.core.hangul

/** Flick direction on a 10-key keycap. */
enum class Korean10KeyFlickDirection { LEFT, RIGHT, UP, DOWN }

/** How a finished touch on a flick-capable 10-key keycap is interpreted. */
sealed interface Korean10KeyTouchInterpretation {
  data object Tap : Korean10KeyTouchInterpretation

  data class Flick(val direction: Korean10KeyFlickDirection) : Korean10KeyTouchInterpretation

  /** Slow, diagonal, or otherwise ambiguous motion. Counts as one mistake. */
  data object InvalidFlick : Korean10KeyTouchInterpretation
}

/**
 * Tap/flick classifier (PRD F2, v6.21 thresholds). Distances are in iOS points; pass Android `dp`.
 * Port of the iOS app's `Korean10KeyFlickGestureResolver`.
 */
object Korean10KeyFlickGestureResolver {
  /** Minimum dominant-axis travel (dp) for a flick; shorter motion is a tap. */
  const val MINIMUM_DISTANCE: Double = 24.0

  /** Maximum touch duration (seconds) for a flick; slower motion is an invalid flick. */
  const val MAXIMUM_DURATION_SECONDS: Double = 0.45

  /** The dominant axis must exceed the other axis by this factor. */
  const val AXIS_DOMINANCE: Double = 1.15

  /**
   * Classifies a finished touch. [translationX]/[translationY] are the finger travel in dp from
   * touch-down to touch-up (+x right, +y down); [durationSeconds] is touch-down to touch-up.
   */
  fun interpretation(
    translationX: Double,
    translationY: Double,
    durationSeconds: Double,
  ): Korean10KeyTouchInterpretation {
    val horizontalDistance = kotlin.math.abs(translationX)
    val verticalDistance = kotlin.math.abs(translationY)
    val distance = maxOf(horizontalDistance, verticalDistance)
    if (distance < MINIMUM_DISTANCE) return Korean10KeyTouchInterpretation.Tap
    if (durationSeconds > MAXIMUM_DURATION_SECONDS) return Korean10KeyTouchInterpretation.InvalidFlick

    if (horizontalDistance >= verticalDistance * AXIS_DOMINANCE) {
      return Korean10KeyTouchInterpretation.Flick(
        if (translationX < 0) Korean10KeyFlickDirection.LEFT else Korean10KeyFlickDirection.RIGHT,
      )
    }
    if (verticalDistance >= horizontalDistance * AXIS_DOMINANCE) {
      return Korean10KeyTouchInterpretation.Flick(
        if (translationY < 0) Korean10KeyFlickDirection.UP else Korean10KeyFlickDirection.DOWN,
      )
    }
    return Korean10KeyTouchInterpretation.InvalidFlick
  }
}

/** Flick direction -> completed jamo table (PRD F2 v6.21). Port of `Korean10KeyFlickMapping`. */
object Korean10KeyFlickMapping {
  /** The completed jamo selected by flicking [key] toward [direction], or null when unassigned. */
  fun completedJamo(key: Korean10KeyKey, direction: Korean10KeyFlickDirection): Char? =
    table[key]?.get(direction)

  private val table: Map<Korean10KeyKey, Map<Korean10KeyFlickDirection, Char>> = run {
    val l = Korean10KeyFlickDirection.LEFT
    val r = Korean10KeyFlickDirection.RIGHT
    val u = Korean10KeyFlickDirection.UP
    val d = Korean10KeyFlickDirection.DOWN
    mapOf(
      Korean10KeyKey.VERTICAL to mapOf(l to 'ㅓ', r to 'ㅏ', u to 'ㅕ', d to 'ㅑ'),
      Korean10KeyKey.DOT to mapOf(l to 'ㅓ', r to 'ㅏ', u to 'ㅗ', d to 'ㅜ'),
      Korean10KeyKey.HORIZONTAL to mapOf(l to 'ㅠ', r to 'ㅛ', u to 'ㅗ', d to 'ㅜ'),
      Korean10KeyKey.GIYEOK to mapOf(l to 'ㄱ', r to 'ㅋ', d to 'ㄲ'),
      Korean10KeyKey.NIEUN to mapOf(l to 'ㄴ', r to 'ㄹ'),
      Korean10KeyKey.DIGEUT to mapOf(l to 'ㄷ', r to 'ㅌ', d to 'ㄸ'),
      Korean10KeyKey.BIEUP to mapOf(l to 'ㅂ', r to 'ㅍ', d to 'ㅃ'),
      Korean10KeyKey.SIOT to mapOf(l to 'ㅅ', r to 'ㅎ', d to 'ㅆ'),
      Korean10KeyKey.JIEUT to mapOf(l to 'ㅈ', r to 'ㅊ', d to 'ㅉ'),
      Korean10KeyKey.IEUNG to mapOf(l to 'ㅇ', r to 'ㅁ'),
    )
  }
}

/** Result of feeding one 10-key stroke or flick to [Korean10KeyInterpreter]. */
sealed interface Korean10KeyInterpretation {
  /** The stroke is a valid recipe prefix; show [display] in the composition preview. Not judged yet. */
  data class Pending(val display: String) : Korean10KeyInterpretation

  /** `→` confirmed a same-group consonant boundary. Not judged. */
  data object SeparatorAccepted : Korean10KeyInterpretation

  /** The recipe completed; forward [jamo] to [HangulComposer] and [JamoSequenceJudge]. */
  data class Committed(val jamo: Char) : Korean10KeyInterpretation

  /** Wrong stroke; record one mistake. Pending strokes were cleared (except for a stray `→`). */
  data class Incorrect(val expected: Char?) : Korean10KeyInterpretation
}

/** Result of [Korean10KeyInterpreter.backspace]. */
sealed interface Korean10KeyBackspaceResult {
  /** One pending stroke was rewound; [display] is the new preview or null when none remain. */
  data class PendingChanged(val display: String?) : Korean10KeyBackspaceResult

  /** Nothing pending; apply [CompositionEvent.Backspace] to the Hangul engine instead. */
  data object ForwardToHangulEngine : Korean10KeyBackspaceResult
}

/** New interpreter state plus the interpretation of one input. */
data class Korean10KeyStep(
  val interpreter: Korean10KeyInterpreter,
  val interpretation: Korean10KeyInterpretation,
)

/** New interpreter state plus the backspace result. */
data class Korean10KeyBackspaceStep(
  val interpreter: Korean10KeyInterpreter,
  val result: Korean10KeyBackspaceResult,
)

/**
 * Target-aware, immutable adapter for the Korean 10-Key stroke order.
 *
 * It holds only an unfinished recipe. A standard compatibility jamo is emitted once the recipe for
 * the session's next expected jamo is complete, so the shared composer and jamo judge stay
 * unchanged. Port of the iOS app's `Korean10KeyInterpreter`.
 */
class Korean10KeyInterpreter private constructor(
  /** Strokes of the unfinished recipe. */
  val pendingKeys: List<Korean10KeyKey>,
  private val lastCommittedGroupedConsonantKey: Korean10KeyKey?,
  private val didAcceptSeparator: Boolean,
) {
  /** Creates an interpreter with nothing pending. */
  constructor() : this(emptyList(), null, false)

  /** Preview text for the pending strokes (exact jamo if any, else raw keycap labels), or null. */
  val pendingDisplay: String? get() = display(pendingKeys)

  /** Feeds one tapped key while the judge expects [expecting] (usually `JamoJudgeState.expectedNext`). */
  fun input(key: Korean10KeyKey, expecting: Char?): Korean10KeyStep {
    val recipe = expecting?.let(::recipe) ?: return incorrect(expecting)

    if (key == Korean10KeyKey.NEXT) {
      if (!needsSeparator(recipe)) return Korean10KeyStep(this, Korean10KeyInterpretation.Incorrect(expecting))
      return Korean10KeyStep(copy(didAcceptSeparator = true), Korean10KeyInterpretation.SeparatorAccepted)
    }

    if (needsSeparator(recipe)) return incorrect(expecting)

    val proposed = pendingKeys + key
    if (!recipe.startsWith(proposed)) return incorrect(expecting)

    if (proposed == recipe) {
      return Korean10KeyStep(
        Korean10KeyInterpreter(
          pendingKeys = emptyList(),
          lastCommittedGroupedConsonantKey = key.takeIf { it in groupedConsonantKeys },
          didAcceptSeparator = false,
        ),
        Korean10KeyInterpretation.Committed(expecting),
      )
    }

    return Korean10KeyStep(copy(pendingKeys = proposed), Korean10KeyInterpretation.Pending(display(proposed) ?: ""))
  }

  /**
   * Accepts a jamo selected directly by a flick (see [Korean10KeyFlickMapping]). A flick may replace
   * only the leading portion of a golden recipe; once raw taps are pending, another completed flick
   * is rejected so unrelated recipe fragments cannot splice.
   */
  fun inputCompletedJamo(jamo: Char?, expecting: Char?): Korean10KeyStep {
    val expectedRecipe = expecting?.let(::recipe)
    val completedRecipe = jamo?.let(::recipe)
    if (expecting == null || expectedRecipe == null || jamo == null || completedRecipe == null ||
      pendingKeys.isNotEmpty() || !expectedRecipe.startsWith(completedRecipe)
    ) {
      return incorrect(expecting)
    }

    if (completedRecipe == expectedRecipe) {
      val groupedKey = completedRecipe.first()
      return Korean10KeyStep(
        Korean10KeyInterpreter(
          pendingKeys = emptyList(),
          lastCommittedGroupedConsonantKey = groupedKey.takeIf { it in groupedConsonantKeys },
          didAcceptSeparator = false,
        ),
        Korean10KeyInterpretation.Committed(expecting),
      )
    }

    return Korean10KeyStep(
      copy(pendingKeys = completedRecipe),
      Korean10KeyInterpretation.Pending(display(completedRecipe) ?: jamo.toString()),
    )
  }

  /** Rewinds one pending stroke, or tells the caller to backspace the Hangul engine. */
  fun backspace(): Korean10KeyBackspaceStep {
    if (pendingKeys.isEmpty()) {
      return Korean10KeyBackspaceStep(
        Korean10KeyInterpreter(emptyList(), null, false),
        Korean10KeyBackspaceResult.ForwardToHangulEngine,
      )
    }
    val next = copy(pendingKeys = pendingKeys.dropLast(1))
    return Korean10KeyBackspaceStep(next, Korean10KeyBackspaceResult.PendingChanged(next.pendingDisplay))
  }

  /** Returns a fresh interpreter (use when the target/question changes). */
  fun reset(): Korean10KeyInterpreter = Korean10KeyInterpreter()

  /** The next key to highlight as a guide for [expected], `NEXT` when a boundary is needed, or null. */
  fun nextKey(expected: Char?): Korean10KeyKey? {
    val recipe = expected?.let(::recipe) ?: return null
    if (!recipe.startsWith(pendingKeys) || pendingKeys.size >= recipe.size) return null
    if (needsSeparator(recipe)) return Korean10KeyKey.NEXT
    return recipe[pendingKeys.size]
  }

  private fun incorrect(expected: Char?) =
    Korean10KeyStep(copy(pendingKeys = emptyList()), Korean10KeyInterpretation.Incorrect(expected))

  private fun needsSeparator(recipe: List<Korean10KeyKey>): Boolean {
    if (didAcceptSeparator || pendingKeys.isNotEmpty()) return false
    return lastCommittedGroupedConsonantKey == recipe.first()
  }

  private fun copy(
    pendingKeys: List<Korean10KeyKey> = this.pendingKeys,
    didAcceptSeparator: Boolean = this.didAcceptSeparator,
  ) = Korean10KeyInterpreter(pendingKeys, lastCommittedGroupedConsonantKey, didAcceptSeparator)

  override fun equals(other: Any?): Boolean =
    other is Korean10KeyInterpreter &&
      pendingKeys == other.pendingKeys &&
      lastCommittedGroupedConsonantKey == other.lastCommittedGroupedConsonantKey &&
      didAcceptSeparator == other.didAcceptSeparator

  override fun hashCode(): Int =
    listOf(pendingKeys, lastCommittedGroupedConsonantKey, didAcceptSeparator).hashCode()

  override fun toString(): String = "Korean10KeyInterpreter(pendingKeys=$pendingKeys)"

  companion object {
    /** Same as [Korean10KeyRecipe.recipe]. */
    fun recipe(jamo: Char): List<Korean10KeyKey>? = Korean10KeyRecipe.recipe(jamo)

    private val groupedConsonantKeys: Set<Korean10KeyKey> = setOf(
      Korean10KeyKey.GIYEOK,
      Korean10KeyKey.NIEUN,
      Korean10KeyKey.DIGEUT,
      Korean10KeyKey.BIEUP,
      Korean10KeyKey.SIOT,
      Korean10KeyKey.JIEUT,
      Korean10KeyKey.IEUNG,
    )

    private fun display(keys: List<Korean10KeyKey>): String? {
      if (keys.isEmpty()) return null
      Korean10KeyRecipe.jamoForExactRecipe(keys)?.let { return it.toString() }
      return keys.joinToString("") { it.displayText }
    }

    private fun <T> List<T>.startsWith(prefix: List<T>): Boolean =
      prefix.size <= size && subList(0, prefix.size) == prefix
  }
}
