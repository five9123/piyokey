package app.piyokey.core.deckkit

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Transport-level JSON checks for untrusted `.typedeck` entries. */
internal object PiyoDeckStrictJson {
  private const val MAX_NESTING = 64

  /**
   * Rejects a UTF-8 BOM, invalid UTF-8, any RFC 8259 violation (including unpaired surrogate
   * escapes), nesting deeper than 64 levels and duplicate object keys (after unescaping and
   * canonical-equivalence normalization, like Swift `Set<String>`).
   */
  @Throws(PiyoDeckImportError::class)
  fun validate(data: ByteArray, name: String) {
    if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() &&
      data[2] == 0xBF.toByte()
    ) {
      throw PiyoDeckImportError.InvalidJson(name, "UTF-8 BOM is not permitted")
    }
    val text = decodeUtf8Strict(data)
      ?: throw PiyoDeckImportError.InvalidJson(name, "invalid UTF-8")
    try {
      StrictJsonParser.parse(text)
    } catch (error: JsonSyntaxException) {
      throw PiyoDeckImportError.InvalidJson(name, error.message ?: "malformed JSON")
    }
    try {
      DuplicateKeyParser(text).parseDocument()
    } catch (failure: DuplicateKeyParser.Failure) {
      when (failure) {
        is DuplicateKeyParser.Failure.Duplicate ->
          throw PiyoDeckImportError.DuplicateJsonKey(name, failure.path)
        is DuplicateKeyParser.Failure.NestingLimit ->
          throw PiyoDeckImportError.InvalidJson(name, "nesting exceeds $MAX_NESTING levels")
        is DuplicateKeyParser.Failure.Malformed ->
          throw PiyoDeckImportError.InvalidJson(name, "malformed JSON structure")
      }
    }
  }

  fun decodeUtf8Strict(data: ByteArray): String? =
    try {
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(data))
        .toString()
    } catch (_: CharacterCodingException) {
      null
    }

  /** Port of the Swift `DuplicateKeyParser`; runs only on already-valid JSON text. */
  private class DuplicateKeyParser(private val text: String) {
    sealed class Failure : Exception() {
      class Duplicate(val path: String) : Failure()
      class NestingLimit : Failure()
      class Malformed : Failure()
    }

    private var offset = 0

    fun parseDocument() {
      skipWhitespace()
      parseValue("$", 0)
      skipWhitespace()
      if (offset != text.length) throw Failure.Malformed()
    }

    private fun parseValue(path: String, depth: Int) {
      if (depth > MAX_NESTING) throw Failure.NestingLimit()
      skipWhitespace()
      val c = current() ?: throw Failure.Malformed()
      when (c) {
        '{' -> parseObject(path, depth + 1)
        '[' -> parseArray(path, depth + 1)
        '"' -> parseString()
        else -> parsePrimitive()
      }
    }

    private fun parseObject(path: String, depth: Int) {
      consume('{')
      skipWhitespace()
      if (current() == '}') {
        offset++
        return
      }
      val keys = mutableSetOf<String>()
      while (true) {
        skipWhitespace()
        val key = parseString()
        val keyPath = "$path.$key"
        if (!keys.add(DeckKitText.canonicalKey(key))) throw Failure.Duplicate(keyPath)
        skipWhitespace()
        consume(':')
        parseValue(keyPath, depth)
        skipWhitespace()
        if (current() == '}') {
          offset++
          return
        }
        consume(',')
      }
    }

    private fun parseArray(path: String, depth: Int) {
      consume('[')
      skipWhitespace()
      if (current() == ']') {
        offset++
        return
      }
      var index = 0
      while (true) {
        parseValue("$path[$index]", depth)
        index++
        skipWhitespace()
        if (current() == ']') {
          offset++
          return
        }
        consume(',')
      }
    }

    private fun parseString(): String {
      if (current() != '"') throw Failure.Malformed()
      val start = offset
      offset++
      var escaped = false
      while (offset < text.length) {
        val c = text[offset]
        offset++
        if (escaped) {
          escaped = false
        } else if (c == '\\') {
          escaped = true
        } else if (c == '"') {
          val element = try {
            StrictJsonParser.parse(text.substring(start, offset))
          } catch (_: JsonSyntaxException) {
            throw Failure.Malformed()
          }
          return JsonValues.stringOrNull(element) ?: throw Failure.Malformed()
        }
      }
      throw Failure.Malformed()
    }

    private fun parsePrimitive() {
      val start = offset
      while (offset < text.length) {
        val c = text[offset]
        if (c == ',' || c == ']' || c == '}' || c == ' ' || c == '\t' || c == '\n' || c == '\r') {
          break
        }
        offset++
      }
      if (offset <= start) throw Failure.Malformed()
    }

    private fun consume(expected: Char) {
      if (current() != expected) throw Failure.Malformed()
      offset++
    }

    private fun skipWhitespace() {
      while (offset < text.length) {
        val c = text[offset]
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') offset++ else break
      }
    }

    private fun current(): Char? = if (offset < text.length) text[offset] else null
  }
}
