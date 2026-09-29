package app.piyokey.core.deckkit

import java.text.BreakIterator
import java.text.Normalizer

/**
 * Swift `String` semantics used by the validators: grapheme-cluster counts (`String.count`),
 * canonical-equivalence comparisons (`String ==`), and whitespace trimming.
 */
internal object DeckKitText {
  /** Extended grapheme clusters, like Swift `Character`s. */
  fun graphemes(value: String): List<String> {
    if (value.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
    iterator.setText(value)
    val result = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
      result.add(value.substring(start, end))
      start = end
      end = iterator.next()
    }
    return result
  }

  fun graphemeCount(value: String): Int = graphemes(value).size

  /** Swift `String` equality/hashing uses canonical equivalence; NFC gives the same classes. */
  fun canonicalKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)

  /** `trimmingCharacters(in: .whitespacesAndNewlines).isEmpty`. */
  fun isBlank(value: String): Boolean =
    value.all { Character.isWhitespace(it) || Character.isSpaceChar(it) || it == '\u0085' }

  /** Unicode `White_Space` property (Swift `Unicode.Scalar.Properties.isWhitespace`). */
  fun isUnicodeWhitespace(codePoint: Int): Boolean =
    codePoint in 0x09..0x0D || codePoint == 0x20 || codePoint == 0x85 || codePoint == 0xA0 ||
      codePoint == 0x1680 || codePoint in 0x2000..0x200A || codePoint == 0x2028 ||
      codePoint == 0x2029 || codePoint == 0x202F || codePoint == 0x205F || codePoint == 0x3000
}

/**
 * Minimal private copy of the HangulEngine `JamoDecomposer` rules DeckKit needs
 * (`keySequence(for:)` succeeding and `containsHangul(in:)`). Kept local so DeckKit does not
 * depend on the in-progress `:core:hangul` API surface.
 */
internal object DeckKitHangul {
  private const val LEADING = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
  private const val MEDIAL = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
  private const val SPLIT_TRAILING = "ㄳㄵㄶㄺㄻㄼㄽㄾㄿㅀㅄ"
  private const val ALLOWED_LITERAL_PUNCTUATION = " .,!?…'\"()-·~♡♥。！？"

  /** Returns `null` when [target] decomposes, otherwise a Swift-style error description. */
  fun decompositionError(target: String): String? {
    if (target.isEmpty()) return "emptyTarget"
    for ((offset, character) in DeckKitText.graphemes(target).withIndex()) {
      if (isSyllable(character)) continue
      if (character.length == 1 && (character[0] in LEADING || character[0] in MEDIAL ||
          character[0] in SPLIT_TRAILING)
      ) continue
      if (isAllowedLiteral(character)) continue
      return "unsupportedCharacter(\"$character\", offset: $offset)"
    }
    return null
  }

  fun isDecomposable(target: String): Boolean = decompositionError(target) == null

  fun containsHangul(text: String): Boolean =
    DeckKitText.graphemes(text).any { character ->
      (character.length == 1 && (character[0] in LEADING || character[0] in MEDIAL)) ||
        isSyllable(character)
    }

  private fun isSyllable(character: String): Boolean =
    character.length == 1 && character[0].code in 0xAC00..0xD7A3

  private fun isAllowedLiteral(character: String): Boolean {
    if (character.length == 1 && character[0] in ALLOWED_LITERAL_PUNCTUATION) return true
    return character.codePoints().allMatch { codePoint ->
      DeckKitText.isUnicodeWhitespace(codePoint) || hasNumericType(codePoint)
    }
  }

  /** Approximates Unicode `Numeric_Type != None` via the Nd/Nl/No general categories. */
  private fun hasNumericType(codePoint: Int): Boolean =
    when (Character.getType(codePoint).toByte()) {
      Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
      else -> false
    }
}
