package app.piyokey.core.hangul

import java.text.BreakIterator
import java.util.Locale

internal data class JamoPair(val first: Char, val second: Char)

/** Unicode tables shared by the composer, decomposer and OS IME judge. Port of `HangulTables.swift`. */
internal object HangulTables {
  val leading: List<Char> = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toList()
  val medial: List<Char> = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".toList()
  val trailing: List<Char?> = listOf<Char?>(null) + "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".toList()

  val compoundMedial: Map<JamoPair, Char> = mapOf(
    JamoPair('ㅗ', 'ㅏ') to 'ㅘ',
    JamoPair('ㅗ', 'ㅐ') to 'ㅙ',
    JamoPair('ㅗ', 'ㅣ') to 'ㅚ',
    JamoPair('ㅜ', 'ㅓ') to 'ㅝ',
    JamoPair('ㅜ', 'ㅔ') to 'ㅞ',
    JamoPair('ㅜ', 'ㅣ') to 'ㅟ',
    JamoPair('ㅡ', 'ㅣ') to 'ㅢ',
  )

  val compoundTrailing: Map<JamoPair, Char> = mapOf(
    JamoPair('ㄱ', 'ㅅ') to 'ㄳ',
    JamoPair('ㄴ', 'ㅈ') to 'ㄵ',
    JamoPair('ㄴ', 'ㅎ') to 'ㄶ',
    JamoPair('ㄹ', 'ㄱ') to 'ㄺ',
    JamoPair('ㄹ', 'ㅁ') to 'ㄻ',
    JamoPair('ㄹ', 'ㅂ') to 'ㄼ',
    JamoPair('ㄹ', 'ㅅ') to 'ㄽ',
    JamoPair('ㄹ', 'ㅌ') to 'ㄾ',
    JamoPair('ㄹ', 'ㅍ') to 'ㄿ',
    JamoPair('ㄹ', 'ㅎ') to 'ㅀ',
    JamoPair('ㅂ', 'ㅅ') to 'ㅄ',
  )

  val splitMedial: Map<Char, JamoPair> = compoundMedial.entries.associate { it.value to it.key }
  val splitTrailing: Map<Char, JamoPair> = compoundTrailing.entries.associate { it.value to it.key }

  val shiftedJamo: Set<Char> = "ㄲㄸㅃㅆㅉㅒㅖ".toSet()
  val allowedLiteralPunctuation: Set<Char> = " .,!?…'\"()-·~♡♥。！？".toSet()

  val leadingIndex: Map<Char, Int> = leading.withIndex().associate { it.value to it.index }
  val medialIndex: Map<Char, Int> = medial.withIndex().associate { it.value to it.index }
  val trailingIndex: Map<Char, Int> =
    trailing.withIndex().mapNotNull { (index, c) -> c?.let { it to index } }.toMap()

  const val SYLLABLE_FIRST: Int = 0xAC00
  const val SYLLABLE_LAST: Int = 0xD7A3

  fun isSyllable(codePoint: Int): Boolean = codePoint in SYLLABLE_FIRST..SYLLABLE_LAST
}

/**
 * Splits text into user-perceived characters (extended grapheme clusters), mirroring Swift's
 * `Character` iteration.
 */
internal fun String.graphemeClusters(): List<String> {
  if (isEmpty()) return emptyList()
  val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
  iterator.setText(this)
  val result = ArrayList<String>(length)
  var start = iterator.first()
  var end = iterator.next()
  while (end != BreakIterator.DONE) {
    result += substring(start, end)
    start = end
    end = iterator.next()
  }
  return result
}

/** Returns the only Unicode scalar of a grapheme, or null when it has zero or several scalars. */
internal fun String.onlyScalar(): Int? =
  if (codePointCount(0, length) == 1) codePointAt(0) else null

/** Returns the grapheme as a single UTF-16 `Char` when it is exactly one BMP scalar. */
internal fun String.singleChar(): Char? = if (length == 1) this[0] else null
