package app.piyokey.core.hangul

/**
 * Logical keys used by the Korean 10-Key/Cheonjiin layout. Contains no UI behavior.
 *
 * [rawValue] matches the iOS `Korean10KeyKey.rawValue`.
 */
enum class Korean10KeyKey(val rawValue: String) {
  VERTICAL("vertical"),
  DOT("dot"),
  HORIZONTAL("horizontal"),
  GIYEOK("giyeok"),
  NIEUN("nieun"),
  DIGEUT("digeut"),
  BIEUP("bieup"),
  SIOT("siot"),
  JIEUT("jieut"),
  IEUNG("ieung"),
  NEXT("next"),
  SPACE("space");

  /** Keycap label (`ㅣ`, `ㆍ`, `ㅡ`, `ㄱㅋ`, ..., `→`, space). Also used for raw pending-stroke previews. */
  val displayText: String
    get() = when (this) {
      VERTICAL -> "ㅣ"
      DOT -> "ㆍ"
      HORIZONTAL -> "ㅡ"
      GIYEOK -> "ㄱㅋ"
      NIEUN -> "ㄴㄹ"
      DIGEUT -> "ㄷㅌ"
      BIEUP -> "ㅂㅍ"
      SIOT -> "ㅅㅎ"
      JIEUT -> "ㅈㅊ"
      IEUNG -> "ㅇㅁ"
      NEXT -> "→"
      SPACE -> " "
    }

  /** Whether the key accepts flick gestures (all except [NEXT] and [SPACE]). */
  val supportsFlick: Boolean get() = this != NEXT && this != SPACE
}

/** Canonical Cheonjiin recipes shared by the in-app 10-key adapter and OS IME judging. */
object Korean10KeyRecipe {
  /** Golden tap recipe for [jamo] (19 consonants, 21 vowels, space), or null. */
  fun recipe(jamo: Char): List<Korean10KeyKey>? = recipes[jamo]

  /** The jamo whose recipe equals [recipe] exactly, or null. */
  fun jamoForExactRecipe(recipe: List<Korean10KeyKey>): Char? =
    recipes.entries.firstOrNull { it.value == recipe }?.key

  /**
   * Returns whether [intermediate] is a completed vowel reachable before [toward] while following
   * the target's Cheonjiin stroke recipe.
   */
  fun isReachableIntermediateVowel(intermediate: Char, toward: Char): Boolean =
    isReachable(intermediate, toward, HangulTables.medial.toSet())

  /**
   * Returns whether [intermediate] is a tap-cycle consonant reachable before [toward] while
   * following the target's Cheonjiin consonant recipe.
   */
  fun isReachableIntermediateConsonant(intermediate: Char, toward: Char): Boolean =
    isReachable(intermediate, toward, HangulTables.leading.toSet())

  private fun isReachable(intermediate: Char, target: Char, domain: Set<Char>): Boolean {
    if (intermediate == target || intermediate !in domain || target !in domain) return false
    // Every leading consonant and medial vowel has a golden recipe.
    return recipes.getValue(target).startsWith(recipes.getValue(intermediate))
  }

  /**
   * Restricts the OS-IME exception to a differing final committed syllable. All earlier characters
   * must already equal the target, the leading jamo must stay unchanged, and the candidate must not
   * yet have a trailing jamo.
   */
  internal fun committedDocumentEndsInReachableIntermediate(target: String, committedText: String): Boolean {
    val targetCharacters = target.graphemeClusters()
    val committedCharacters = committedText.graphemeClusters()
    val candidateCharacter = committedCharacters.lastOrNull() ?: return false
    if (committedCharacters.size > targetCharacters.size) return false

    val lastIndex = committedCharacters.size - 1
    if (committedCharacters.dropLast(1) != targetCharacters.take(lastIndex)) return false

    val candidate = syllableComponents(candidateCharacter) ?: return false
    val targetComponents = syllableComponents(targetCharacters[lastIndex]) ?: return false
    if (candidate.leading != targetComponents.leading || candidate.trailing != null) return false

    return isReachableIntermediateVowel(candidate.medial, toward = targetComponents.medial)
  }

  /**
   * Recognizes a committed Cheonjiin stroke prefix that cannot be decomposed as modern Hangul yet,
   * such as the measured `ㄷㆍ` state on the way to `돼`.
   */
  internal fun committedDocumentEndsInReachableRawVowelPrefix(target: String, committedText: String): Boolean {
    val targetCharacters = target.graphemeClusters()
    val committedCharacters = committedText.graphemeClusters()

    for (targetIndex in targetCharacters.indices) {
      val stablePrefix = targetCharacters.take(targetIndex)
      if (!committedCharacters.startsWith(stablePrefix)) continue

      val active = committedCharacters.drop(stablePrefix.size)
      val targetComponents = syllableComponents(targetCharacters[targetIndex]) ?: continue
      val targetRecipe = recipes.getValue(targetComponents.medial)

      val rawScalars = active.joinToString("").codePoints().toArray().toMutableList()
      val leading = targetComponents.leading
      if (leading != null) {
        if (rawScalars.firstOrNull() != leading.code) continue
        rawScalars.removeAt(0)
      }
      if (rawScalars.isEmpty()) continue
      val expandedStrokes = rawScalars.mapNotNull { rawVowelKeysByScalar[it] }
      if (expandedStrokes.size != rawScalars.size) continue

      val rawRecipe = expandedStrokes.flatten()
      if (rawRecipe.size < targetRecipe.size && targetRecipe.startsWith(rawRecipe)) return true
    }
    return false
  }

  /**
   * Recognizes only recipe-backed Cheonjiin consonant cycle states. These can appear as a
   * standalone next onset, temporarily attach to the preceding open syllable, or replace a target
   * syllable's final consonant.
   */
  internal fun committedDocumentEndsInReachableConsonantCycle(target: String, committedText: String): Boolean {
    val targetCharacters = target.graphemeClusters()
    val committedCharacters = committedText.graphemeClusters()
    val candidateCharacter = committedCharacters.lastOrNull() ?: return false

    if (committedCharacters.size <= targetCharacters.size) {
      val targetIndex = committedCharacters.size - 1
      val stable = committedCharacters.dropLast(1) == targetCharacters.take(targetIndex)
      // Standalone current consonant, including the onset before a syllable: `ㄴ` -> `ㄹ`, `대ㅅ` -> `대ㅎ`.
      if (stable) {
        val candidateConsonant = standaloneConsonant(candidateCharacter)
        val targetConsonant = leadingConsonant(targetCharacters[targetIndex])
        if (candidateConsonant != null && targetConsonant != null &&
          isReachableIntermediateConsonant(candidateConsonant, toward = targetConsonant)
        ) {
          return true
        }
      }

      // A final consonant tap-cycle within the same syllable: `단` -> `달`.
      if (stable) {
        val candidate = syllableComponents(candidateCharacter)
        val targetComponents = syllableComponents(targetCharacters[targetIndex])
        if (candidate != null && targetComponents != null &&
          candidate.leading == targetComponents.leading &&
          candidate.medial == targetComponents.medial &&
          candidate.trailing != null && targetComponents.trailing != null &&
          isReachableIntermediateConsonant(candidate.trailing, toward = targetComponents.trailing)
        ) {
          return true
        }
      }
    }

    // The next onset may be provisionally absorbed as the previous syllable's final consonant:
    // `댓` -> `대형` while `ㅅ` cycles toward `ㅎ`.
    val nextTargetIndex = committedCharacters.size
    if (nextTargetIndex >= targetCharacters.size) return false
    if (committedCharacters.dropLast(1) != targetCharacters.take(nextTargetIndex - 1)) return false
    val candidate = syllableComponents(candidateCharacter) ?: return false
    val previousTarget = syllableComponents(targetCharacters[nextTargetIndex - 1]) ?: return false
    if (candidate.leading != previousTarget.leading ||
      candidate.medial != previousTarget.medial ||
      previousTarget.trailing != null
    ) {
      return false
    }
    val candidateTrailing = candidate.trailing ?: return false
    val nextTargetLeading = leadingConsonant(targetCharacters[nextTargetIndex]) ?: return false
    return isReachableIntermediateConsonant(candidateTrailing, toward = nextTargetLeading)
  }

  /**
   * Recognizes a closed-syllable boundary where the next onset's tap-cycle precursor temporarily
   * combines with the preceding syllable's original final (`일해` may expose `잀`).
   */
  internal fun committedDocumentEndsInReachableClosedSyllableBoundary(target: String, committedText: String): Boolean {
    val targetCharacters = target.graphemeClusters()
    val committedCharacters = committedText.graphemeClusters()
    val boundary = boundaryCandidate(targetCharacters, committedCharacters) ?: return false
    val originalTrailing = boundary.previousTarget.trailing ?: return false
    val candidateTrailing = boundary.candidate.trailing ?: return false
    val candidatePair = HangulTables.splitTrailing[candidateTrailing] ?: return false
    if (candidatePair.first != originalTrailing) return false
    return isReachableIntermediateConsonant(candidatePair.second, toward = boundary.nextTargetLeading)
  }

  /**
   * Recognizes the path where the first component of a target compound final stays attached while
   * the next tap-cycle consonant is committed on its own (`찬ㅅ` toward `찮`, `살ㅇ` toward `삶`).
   */
  internal fun committedDocumentEndsInReachableDanglingComplexTrailingPrefix(
    target: String,
    committedText: String,
  ): Boolean {
    val targetCharacters = target.graphemeClusters()
    val committedCharacters = committedText.graphemeClusters()
    if (committedCharacters.size < 2) return false
    val danglingConsonant = standaloneConsonant(committedCharacters.last()) ?: return false

    val targetIndex = committedCharacters.size - 2
    if (targetIndex >= targetCharacters.size) return false
    if (committedCharacters.dropLast(2) != targetCharacters.take(targetIndex)) return false
    val candidate = syllableComponents(committedCharacters[targetIndex]) ?: return false
    val targetComponents = syllableComponents(targetCharacters[targetIndex]) ?: return false
    if (candidate.leading != targetComponents.leading || candidate.medial != targetComponents.medial) return false
    val candidateTrailing = candidate.trailing ?: return false
    val targetTrailing = targetComponents.trailing ?: return false
    val targetPair = HangulTables.splitTrailing[targetTrailing] ?: return false
    if (candidateTrailing != targetPair.first) return false

    return isReachableIntermediateConsonant(danglingConsonant, toward = targetPair.second)
  }

  /**
   * Preserves an already-correct closed syllable while the 10-key keyboard is still cycling the same
   * physical key instead of starting the next onset (`학교` may expose `핰`/`핚`). Returns the key
   * sequence of the stable target prefix, or null when the exception does not apply.
   */
  internal fun acceptedSequenceForUnconfirmedSameRecipeBoundaryCycle(
    target: String,
    committedText: String,
  ): List<Char>? {
    val targetCharacters = target.graphemeClusters()
    val committedCharacters = committedText.graphemeClusters()
    val boundary = boundaryCandidate(targetCharacters, committedCharacters) ?: return null
    val originalTrailing = boundary.previousTarget.trailing ?: return null
    val candidateTrailing = boundary.candidate.trailing ?: return null
    if (candidateTrailing == originalTrailing) return null
    val originalRecipe = recipes[originalTrailing] ?: return null
    val nextRecipe = recipes.getValue(boundary.nextTargetLeading)
    if (originalRecipe != nextRecipe) return null
    val sharedKey = originalRecipe.first()
    if (!originalRecipe.all { it == sharedKey }) return null
    val candidateRecipe = recipes[candidateTrailing] ?: return null
    if (!candidateRecipe.all { it == sharedKey }) return null

    // A non-empty prefix of an already-decomposable target always decomposes (Swift used `try?`).
    return JamoDecomposer.keySequence(targetCharacters.take(committedCharacters.size).joinToString(""))
  }

  /**
   * Recognizes only target-derived intermediate states while assembling a complex final: a simple
   * candidate may be a recipe prefix of the first component (`단` -> `닭`), or a compound candidate
   * may contain the exact first component plus a prefix of the second (`앐` -> `앓`).
   */
  internal fun committedDocumentEndsInReachableComplexTrailingAssembly(
    target: String,
    committedText: String,
  ): Boolean {
    val targetCharacters = target.graphemeClusters()
    val committedCharacters = committedText.graphemeClusters()
    val candidateCharacter = committedCharacters.lastOrNull() ?: return false
    if (committedCharacters.size > targetCharacters.size) return false

    val targetIndex = committedCharacters.size - 1
    if (committedCharacters.dropLast(1) != targetCharacters.take(targetIndex)) return false
    val candidate = syllableComponents(candidateCharacter) ?: return false
    val targetComponents = syllableComponents(targetCharacters[targetIndex]) ?: return false
    if (candidate.leading != targetComponents.leading || candidate.medial != targetComponents.medial) return false
    val candidateTrailing = candidate.trailing ?: return false
    val targetTrailing = targetComponents.trailing ?: return false
    val targetPair = HangulTables.splitTrailing[targetTrailing] ?: return false

    val candidatePair = HangulTables.splitTrailing[candidateTrailing]
      ?: return isReachableIntermediateConsonant(candidateTrailing, toward = targetPair.first)
    if (candidatePair.first != targetPair.first) return false
    return isReachableIntermediateConsonant(candidatePair.second, toward = targetPair.second)
  }

  private class BoundaryCandidate(
    val candidate: SyllableComponents,
    val previousTarget: SyllableComponents,
    val nextTargetLeading: Char,
  )

  /**
   * Shared guard for the closed-syllable boundary checks: the committed text is the target prefix
   * except for its last syllable, which keeps the previous target syllable's leading and medial,
   * and a next target onset exists.
   */
  private fun boundaryCandidate(
    targetCharacters: List<String>,
    committedCharacters: List<String>,
  ): BoundaryCandidate? {
    val nextTargetIndex = committedCharacters.size
    if (nextTargetIndex == 0 || nextTargetIndex >= targetCharacters.size) return null
    if (committedCharacters.dropLast(1) != targetCharacters.take(nextTargetIndex - 1)) return null
    val candidate = syllableComponents(committedCharacters.last()) ?: return null
    val previousTarget = syllableComponents(targetCharacters[nextTargetIndex - 1]) ?: return null
    if (candidate.leading != previousTarget.leading || candidate.medial != previousTarget.medial) return null
    val nextTargetLeading = leadingConsonant(targetCharacters[nextTargetIndex]) ?: return null
    return BoundaryCandidate(candidate, previousTarget, nextTargetLeading)
  }

  private data class SyllableComponents(val leading: Char?, val medial: Char, val trailing: Char?)

  private fun syllableComponents(character: String): SyllableComponents? {
    val single = character.singleChar()
    if (single != null && HangulTables.medialIndex.containsKey(single)) {
      return SyllableComponents(leading = null, medial = single, trailing = null)
    }
    val scalar = character.onlyScalar()
    if (scalar == null || !HangulTables.isSyllable(scalar)) return null
    val syllableIndex = scalar - HangulTables.SYLLABLE_FIRST
    return SyllableComponents(
      leading = HangulTables.leading[syllableIndex / (21 * 28)],
      medial = HangulTables.medial[(syllableIndex % (21 * 28)) / 28],
      trailing = HangulTables.trailing[syllableIndex % 28],
    )
  }

  private fun standaloneConsonant(character: String): Char? =
    character.singleChar()?.takeIf { HangulTables.leadingIndex.containsKey(it) }

  private fun leadingConsonant(character: String): Char? =
    standaloneConsonant(character) ?: syllableComponents(character)?.leading

  private fun <T> List<T>.startsWith(prefix: List<T>): Boolean =
    prefix.size <= size && subList(0, prefix.size) == prefix

  private val rawVowelKeysByScalar: Map<Int, List<Korean10KeyKey>> = mapOf(
    0x3163 to listOf(Korean10KeyKey.VERTICAL),
    0x3161 to listOf(Korean10KeyKey.HORIZONTAL),
    0x318D to listOf(Korean10KeyKey.DOT),
    0x119E to listOf(Korean10KeyKey.DOT),
    0x11A2 to listOf(Korean10KeyKey.DOT, Korean10KeyKey.DOT),
  )

  // Golden recipe contract approved for the Apple Korean 10-Key layout in Issue #11. Grouped
  // consonants cycle in label order and then to the tense consonant; vowels retain the visible
  // ㅣ·ㅡ stroke order.
  private val recipes: Map<Char, List<Korean10KeyKey>> = run {
    val v = Korean10KeyKey.VERTICAL
    val d = Korean10KeyKey.DOT
    val h = Korean10KeyKey.HORIZONTAL
    val g = Korean10KeyKey.GIYEOK
    val n = Korean10KeyKey.NIEUN
    val t = Korean10KeyKey.DIGEUT
    val b = Korean10KeyKey.BIEUP
    val s = Korean10KeyKey.SIOT
    val j = Korean10KeyKey.JIEUT
    val o = Korean10KeyKey.IEUNG
    linkedMapOf(
      'ㄱ' to listOf(g), 'ㅋ' to listOf(g, g), 'ㄲ' to listOf(g, g, g),
      'ㄴ' to listOf(n), 'ㄹ' to listOf(n, n),
      'ㄷ' to listOf(t), 'ㅌ' to listOf(t, t), 'ㄸ' to listOf(t, t, t),
      'ㅂ' to listOf(b), 'ㅍ' to listOf(b, b), 'ㅃ' to listOf(b, b, b),
      'ㅅ' to listOf(s), 'ㅎ' to listOf(s, s), 'ㅆ' to listOf(s, s, s),
      'ㅈ' to listOf(j), 'ㅊ' to listOf(j, j), 'ㅉ' to listOf(j, j, j),
      'ㅇ' to listOf(o), 'ㅁ' to listOf(o, o),
      'ㅣ' to listOf(v), 'ㅡ' to listOf(h),
      'ㅏ' to listOf(v, d), 'ㅑ' to listOf(v, d, d),
      'ㅓ' to listOf(d, v), 'ㅕ' to listOf(d, d, v),
      'ㅗ' to listOf(d, h), 'ㅛ' to listOf(d, d, h),
      'ㅜ' to listOf(h, d), 'ㅠ' to listOf(h, d, d),
      'ㅐ' to listOf(v, d, v),
      'ㅒ' to listOf(v, d, d, v),
      'ㅔ' to listOf(d, v, v),
      'ㅖ' to listOf(d, d, v, v),
      'ㅘ' to listOf(d, h, v, d),
      'ㅙ' to listOf(d, h, v, d, v),
      'ㅚ' to listOf(d, h, v),
      'ㅝ' to listOf(h, d, d, v),
      'ㅞ' to listOf(h, d, d, v, v),
      'ㅟ' to listOf(h, d, v),
      'ㅢ' to listOf(h, v),
      ' ' to listOf(Korean10KeyKey.SPACE),
    )
  }
}
