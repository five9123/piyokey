package app.piyokey.core.hangul

/** The externally visible phases of the two-beolsik composition automaton. */
enum class CompositionPhase(val rawValue: String) {
  EMPTY("empty"),
  CHO("cho"),
  JUNG("jung"),
  CHO_JUNG("choJung"),
  CHO_JUNG_JONG("choJungJong"),
}

/** One keyboard event fed to [HangulComposer.reduce]. */
sealed interface CompositionEvent {
  /** A logical key. Shift+jamo arrives as its resulting jamo (`ㄲ`, `ㅒ`, ...). Non-jamo is committed literally. */
  data class Key(val key: Char) : CompositionEvent

  /** Jamo-level backspace: restores the state before the last accepted key. */
  data object Backspace : CompositionEvent
}

internal data class CompositionBuffer(
  val leading: Char? = null,
  val medial: Char? = null,
  val trailing: Char? = null,
)

internal data class CompositionSnapshot(
  val committed: String = "",
  val buffer: CompositionBuffer = CompositionBuffer(),
)

/**
 * Immutable value state for the composition automaton. All transitions return a new value via
 * [HangulComposer.reduce].
 */
class CompositionState private constructor(
  private val snapshot: CompositionSnapshot,
  private val undoHistory: List<CompositionSnapshot>,
) {
  /** Creates an empty state. */
  constructor() : this(CompositionSnapshot(), emptyList())

  /** Text that is final and will not change on further keys (except via backspace). */
  val committedText: String get() = snapshot.committed

  /** The syllable (or lone jamo) still being composed; empty when nothing is composing. */
  val composingText: String get() = render(snapshot.buffer)

  /** Full preview text: [committedText] + [composingText]. */
  val text: String get() = committedText + composingText

  /** Current automaton phase of the composing buffer. */
  val phase: CompositionPhase
    get() {
      val buffer = snapshot.buffer
      return when (Triple(buffer.leading != null, buffer.medial != null, buffer.trailing != null)) {
        Triple(false, false, false) -> CompositionPhase.EMPTY
        Triple(true, false, false) -> CompositionPhase.CHO
        Triple(false, true, false) -> CompositionPhase.JUNG
        Triple(true, true, false) -> CompositionPhase.CHO_JUNG
        Triple(true, true, true) -> CompositionPhase.CHO_JUNG_JONG
        else -> CompositionPhase.EMPTY
      }
    }

  internal fun applying(event: CompositionEvent): CompositionState =
    when (event) {
      is CompositionEvent.Key ->
        CompositionState(Work(snapshot).apply { accept(event.key) }.snapshot(), undoHistory + snapshot)
      CompositionEvent.Backspace ->
        if (undoHistory.isEmpty()) this
        else CompositionState(undoHistory.last(), undoHistory.dropLast(1))
    }

  override fun equals(other: Any?): Boolean =
    other is CompositionState && snapshot == other.snapshot && undoHistory == other.undoHistory

  override fun hashCode(): Int = 31 * snapshot.hashCode() + undoHistory.hashCode()

  override fun toString(): String = "CompositionState(text=$text, phase=$phase)"

  /** Mutable scratch copy used to compute one transition; never escapes. */
  private class Work(snapshot: CompositionSnapshot) {
    val committed = StringBuilder(snapshot.committed)
    var leading: Char? = snapshot.buffer.leading
    var medial: Char? = snapshot.buffer.medial
    var trailing: Char? = snapshot.buffer.trailing

    fun snapshot() = CompositionSnapshot(committed.toString(), CompositionBuffer(leading, medial, trailing))

    fun flushBuffer() {
      committed.append(render(CompositionBuffer(leading, medial, trailing)))
      leading = null
      medial = null
      trailing = null
    }

    fun accept(key: Char) {
      if (HangulTables.medialIndex.containsKey(key)) {
        acceptVowel(key)
      } else if (HangulTables.leadingIndex.containsKey(key)) {
        acceptConsonant(key)
      } else {
        flushBuffer()
        committed.append(key)
      }
    }

    private fun startLeading(consonant: Char) {
      flushBuffer()
      leading = consonant
    }

    private fun acceptConsonant(consonant: Char) {
      if (leading == null && medial == null) {
        leading = consonant
        return
      }
      if (leading == null || medial == null) {
        startLeading(consonant)
        return
      }
      val currentTrailing = trailing
      if (currentTrailing == null) {
        if (HangulTables.trailingIndex.containsKey(consonant)) {
          trailing = consonant
        } else {
          startLeading(consonant)
        }
        return
      }
      val compound = HangulTables.compoundTrailing[JamoPair(currentTrailing, consonant)]
      if (compound != null) trailing = compound else startLeading(consonant)
    }

    private fun combineOrRestartMedial(current: Char, vowel: Char) {
      val compound = HangulTables.compoundMedial[JamoPair(current, vowel)]
      if (compound != null) {
        medial = compound
      } else {
        flushBuffer()
        medial = vowel
      }
    }

    private fun acceptVowel(vowel: Char) {
      val currentMedial = medial
      if (leading == null) {
        if (currentMedial == null) medial = vowel else combineOrRestartMedial(currentMedial, vowel)
        return
      }
      if (currentMedial == null) {
        medial = vowel
        return
      }
      val currentTrailing = trailing
      if (currentTrailing == null) {
        combineOrRestartMedial(currentMedial, vowel)
        return
      }
      // Dokkaebi carry-over: the (last component of the) final moves to the next syllable.
      val split = HangulTables.splitTrailing[currentTrailing]
      val carried: Char
      if (split != null) {
        trailing = split.first
        carried = split.second
      } else {
        trailing = null
        carried = currentTrailing
      }
      flushBuffer()
      leading = carried
      medial = vowel
    }
  }

  private companion object {
    fun render(buffer: CompositionBuffer): String {
      val leading = buffer.leading ?: return buffer.medial?.toString() ?: ""
      val medial = buffer.medial ?: return leading.toString()
      // The automaton only stores table jamo, so the Swift `leading + medial` fallback is unreachable.
      val leadingIndex = HangulTables.leadingIndex.getValue(leading)
      val medialIndex = HangulTables.medialIndex.getValue(medial)
      val trailingIndex = buffer.trailing?.let { HangulTables.trailingIndex[it] } ?: 0
      val scalar = HangulTables.SYLLABLE_FIRST + (leadingIndex * 21 + medialIndex) * 28 + trailingIndex
      return scalar.toChar().toString()
    }
  }
}

/** Pure two-beolsik Hangul composer. Port of `HangulComposer.swift`. */
object HangulComposer {
  /** Pure reducer for one keyboard event. */
  fun reduce(state: CompositionState, event: CompositionEvent): CompositionState = state.applying(event)

  /** Feeds every key from an empty state. */
  fun compose(keys: Iterable<Char>): CompositionState =
    keys.fold(CompositionState()) { state, key -> reduce(state, CompositionEvent.Key(key)) }

  /** Convenience overload that feeds each UTF-16 char of [keys] as one key. */
  fun compose(keys: CharSequence): CompositionState = compose(keys.asIterable())
}
