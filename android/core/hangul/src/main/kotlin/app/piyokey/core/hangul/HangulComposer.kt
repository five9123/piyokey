package app.piyokey.core.hangul

/** The externally visible phases of the two-beolsik composition automaton. */
enum class CompositionPhase {
  EMPTY,
  CHO,
  JUNG,
  CHO_JUNG,
  CHO_JUNG_JONG,
}

sealed interface CompositionEvent {
  data class Key(val key: Char) : CompositionEvent
  data object Backspace : CompositionEvent
}

private data class CompositionBuffer(
  val leading: Char? = null,
  val medial: Char? = null,
  val trailing: Char? = null,
)

private data class CompositionSnapshot(
  val committed: String = "",
  val buffer: CompositionBuffer = CompositionBuffer(),
)

/** Immutable value state. Every transition returns a new state. */
class CompositionState private constructor(
  private val snapshot: CompositionSnapshot = CompositionSnapshot(),
  private val undoHistory: List<CompositionSnapshot> = emptyList(),
) {
  constructor() : this(CompositionSnapshot(), emptyList())

  val committedText: String
    get() = snapshot.committed

  val composingText: String
    get() = render(snapshot.buffer)

  val text: String
    get() = committedText + composingText

  val phase: CompositionPhase
    get() = when (Triple(snapshot.buffer.leading, snapshot.buffer.medial, snapshot.buffer.trailing)) {
      Triple(null, null, null) -> CompositionPhase.EMPTY
      Triple(snapshot.buffer.leading, null, null) -> CompositionPhase.CHO
      Triple(null, snapshot.buffer.medial, null) -> CompositionPhase.JUNG
      Triple(snapshot.buffer.leading, snapshot.buffer.medial, null) -> CompositionPhase.CHO_JUNG
      else -> CompositionPhase.CHO_JUNG_JONG
    }

  internal fun apply(event: CompositionEvent): CompositionState = when (event) {
    is CompositionEvent.Key -> accept(event.key)
    CompositionEvent.Backspace -> eraseLastInput()
  }

  private fun accept(key: Char): CompositionState {
    val updatedSnapshot = when {
      key in HangulTables.medialIndex -> acceptVowel(snapshot, key)
      key in HangulTables.leadingIndex -> acceptConsonant(snapshot, key)
      else -> flushBuffer(snapshot).let { flushed ->
        flushed.copy(committed = flushed.committed + key)
      }
    }
    return CompositionState(
      snapshot = updatedSnapshot,
      undoHistory = undoHistory + snapshot,
    )
  }

  private fun acceptConsonant(
    current: CompositionSnapshot,
    consonant: Char,
  ): CompositionSnapshot {
    val buffer = current.buffer

    if (buffer.leading == null && buffer.medial == null) {
      return current.copy(buffer = buffer.copy(leading = consonant))
    }
    if (buffer.leading == null) {
      return flushBuffer(current).copy(buffer = CompositionBuffer(leading = consonant))
    }
    if (buffer.medial == null) {
      return flushBuffer(current).copy(buffer = CompositionBuffer(leading = consonant))
    }
    val trailing = buffer.trailing
    if (trailing == null) {
      return if (consonant in HangulTables.trailingIndex) {
        current.copy(buffer = buffer.copy(trailing = consonant))
      } else {
        flushBuffer(current).copy(buffer = CompositionBuffer(leading = consonant))
      }
    }

    val compound = HangulTables.compoundTrailing[JamoPair(trailing, consonant)]
    return if (compound != null) {
      current.copy(buffer = buffer.copy(trailing = compound))
    } else {
      flushBuffer(current).copy(buffer = CompositionBuffer(leading = consonant))
    }
  }

  private fun acceptVowel(
    current: CompositionSnapshot,
    vowel: Char,
  ): CompositionSnapshot {
    val buffer = current.buffer

    if (buffer.leading == null && buffer.medial == null) {
      return current.copy(buffer = buffer.copy(medial = vowel))
    }

    if (buffer.leading == null) {
      val medial = requireNotNull(buffer.medial)
      val compound = HangulTables.compoundMedial[JamoPair(medial, vowel)]
      return if (compound != null) {
        current.copy(buffer = buffer.copy(medial = compound))
      } else {
        flushBuffer(current).copy(buffer = CompositionBuffer(medial = vowel))
      }
    }

    val medial = buffer.medial
      ?: return current.copy(buffer = buffer.copy(medial = vowel))

    val trailing = buffer.trailing
    if (trailing == null) {
      val compound = HangulTables.compoundMedial[JamoPair(medial, vowel)]
      return if (compound != null) {
        current.copy(buffer = buffer.copy(medial = compound))
      } else {
        flushBuffer(current).copy(buffer = CompositionBuffer(medial = vowel))
      }
    }

    val split = HangulTables.splitTrailing[trailing]
    val carried: Char
    val beforeCarry: CompositionSnapshot
    if (split != null) {
      beforeCarry = current.copy(buffer = buffer.copy(trailing = split.first))
      carried = split.second
    } else {
      beforeCarry = current.copy(buffer = buffer.copy(trailing = null))
      carried = trailing
    }
    return flushBuffer(beforeCarry).copy(
      buffer = CompositionBuffer(leading = carried, medial = vowel),
    )
  }

  private fun eraseLastInput(): CompositionState {
    val previous = undoHistory.lastOrNull() ?: return this
    return CompositionState(
      snapshot = previous,
      undoHistory = undoHistory.dropLast(1),
    )
  }

  override fun equals(other: Any?): Boolean =
    other is CompositionState && snapshot == other.snapshot && undoHistory == other.undoHistory

  override fun hashCode(): Int = 31 * snapshot.hashCode() + undoHistory.hashCode()

  override fun toString(): String = "CompositionState(text=$text, phase=$phase)"

  private companion object {
    fun render(buffer: CompositionBuffer): String {
      val leading = buffer.leading ?: return buffer.medial?.toString().orEmpty()
      val medial = buffer.medial ?: return leading.toString()
      // Both values can only enter the private buffer through the validated tables above.
      val leadingIndex = HangulTables.leadingIndex.getValue(leading)
      val medialIndex = HangulTables.medialIndex.getValue(medial)
      val trailingIndex = buffer.trailing?.let(HangulTables.trailingIndex::get) ?: 0
      val scalarValue = 0xAC00 + (leadingIndex * 21 + medialIndex) * 28 + trailingIndex
      return scalarValue.toChar().toString()
    }

    fun flushBuffer(snapshot: CompositionSnapshot): CompositionSnapshot = snapshot.copy(
      committed = snapshot.committed + render(snapshot.buffer),
      buffer = CompositionBuffer(),
    )
  }
}

object HangulComposer {
  /** Pure reducer for one keyboard event. */
  fun reduce(state: CompositionState, event: CompositionEvent): CompositionState =
    state.apply(event)

  fun compose(keys: Iterable<Char>): CompositionState = keys.fold(CompositionState()) { state, key ->
    reduce(state, CompositionEvent.Key(key))
  }

  fun compose(keys: Sequence<Char>): CompositionState = compose(keys.asIterable())
}
