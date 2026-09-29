package app.piyokey.core.domain

import java.text.BreakIterator
import java.text.Normalizer

/** Swift `String.count`: extended grapheme clusters. */
fun String.graphemeCount(): Int {
  if (isEmpty()) return 0
  val iterator = BreakIterator.getCharacterInstance()
  iterator.setText(this)
  var count = 0
  while (iterator.next() != BreakIterator.DONE) count++
  return count
}

/** Swift `String.unicodeScalars.count`. */
fun String.scalarCount(): Int = codePointCount(0, length)

/** Swift `precomposedStringWithCanonicalMapping` (NFC). */
fun String.nfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

/** Swift `trimmingCharacters(in: .whitespacesAndNewlines)`. */
internal fun String.trimmedWhitespace(): String = trim { it.isWhitespace() || it == ' ' }

/** Lexicographic comparison of Int lists (Swift `lexicographicallyPrecedes`). */
internal fun compareLexicographically(lhs: List<Int>, rhs: List<Int>): Int {
  for (index in 0 until minOf(lhs.size, rhs.size)) {
    val result = lhs[index].compareTo(rhs[index])
    if (result != 0) return result
  }
  return lhs.size.compareTo(rhs.size)
}
