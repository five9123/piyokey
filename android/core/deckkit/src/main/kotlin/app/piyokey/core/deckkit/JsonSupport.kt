package app.piyokey.core.deckkit

import java.math.BigDecimal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Thrown by [StrictJsonParser] for any RFC 8259 violation. */
internal class JsonSyntaxException(message: String) : Exception(message)

/**
 * RFC 8259 parser producing kotlinx [JsonElement] trees. Rejects unescaped control characters,
 * leading zeros, trailing commas, invalid escapes and unpaired UTF-16 surrogate escapes (matching
 * Foundation `JSONSerialization`). Top-level scalars are accepted (fragments allowed). Duplicate
 * keys keep the last value; duplicate detection is a separate pass in [PiyoDeckStrictJson].
 */
internal class StrictJsonParser private constructor(private val text: String) {
  private var offset = 0

  companion object {
    private const val MAX_DEPTH = 512

    fun parse(text: String): JsonElement {
      val parser = StrictJsonParser(text)
      parser.skipWhitespace()
      val value = parser.parseValue(0)
      parser.skipWhitespace()
      if (parser.offset != text.length) parser.fail("unexpected trailing characters")
      return value
    }

    fun parse(bytes: ByteArray): JsonElement = parse(PiyoDeckStrictJson.decodeUtf8Strict(bytes)
      ?: throw JsonSyntaxException("invalid UTF-8"))
  }

  private fun fail(reason: String): Nothing =
    throw JsonSyntaxException("$reason at offset $offset")

  private fun skipWhitespace() {
    while (offset < text.length) {
      val c = text[offset]
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') offset++ else break
    }
  }

  private fun parseValue(depth: Int): JsonElement {
    if (depth > MAX_DEPTH) fail("nesting too deep")
    skipWhitespace()
    if (offset >= text.length) fail("unexpected end of input")
    return when (text[offset]) {
      '{' -> parseObject(depth + 1)
      '[' -> parseArray(depth + 1)
      '"' -> JsonPrimitive(parseString())
      't' -> literal("true", JsonPrimitive(true))
      'f' -> literal("false", JsonPrimitive(false))
      'n' -> literal("null", JsonNull)
      else -> parseNumber()
    }
  }

  private fun literal(token: String, value: JsonElement): JsonElement {
    if (!text.startsWith(token, offset)) fail("invalid literal")
    offset += token.length
    return value
  }

  private fun parseObject(depth: Int): JsonElement {
    offset++ // {
    val content = LinkedHashMap<String, JsonElement>()
    skipWhitespace()
    if (peek() == '}') {
      offset++
      return JsonObject(content)
    }
    while (true) {
      skipWhitespace()
      if (peek() != '"') fail("expected object key")
      val key = parseString()
      skipWhitespace()
      if (peek() != ':') fail("expected ':'")
      offset++
      content[key] = parseValue(depth)
      skipWhitespace()
      when (peek()) {
        ',' -> offset++
        '}' -> {
          offset++
          return JsonObject(content)
        }
        else -> fail("expected ',' or '}'")
      }
    }
  }

  private fun parseArray(depth: Int): JsonElement {
    offset++ // [
    val content = ArrayList<JsonElement>()
    skipWhitespace()
    if (peek() == ']') {
      offset++
      return JsonArray(content)
    }
    while (true) {
      content.add(parseValue(depth))
      skipWhitespace()
      when (peek()) {
        ',' -> offset++
        ']' -> {
          offset++
          return JsonArray(content)
        }
        else -> fail("expected ',' or ']'")
      }
    }
  }

  private fun peek(): Char? = if (offset < text.length) text[offset] else null

  internal fun parseString(): String {
    offset++ // opening quote
    val builder = StringBuilder()
    while (true) {
      if (offset >= text.length) fail("unterminated string")
      val c = text[offset]
      when {
        c == '"' -> {
          offset++
          return builder.toString()
        }
        c == '\\' -> {
          offset++
          if (offset >= text.length) fail("unterminated escape")
          when (text[offset]) {
            '"' -> builder.append('"')
            '\\' -> builder.append('\\')
            '/' -> builder.append('/')
            'b' -> builder.append('\b')
            'f' -> builder.append('\u000C')
            'n' -> builder.append('\n')
            'r' -> builder.append('\r')
            't' -> builder.append('\t')
            'u' -> {
              val unit = readHex4()
              when {
                unit in 0xD800..0xDBFF -> {
                  if (!text.startsWith("\\u", offset + 1)) fail("unpaired high surrogate escape")
                  offset += 2
                  val low = readHex4()
                  if (low !in 0xDC00..0xDFFF) fail("unpaired high surrogate escape")
                  builder.append(unit.toChar()).append(low.toChar())
                }
                unit in 0xDC00..0xDFFF -> fail("unpaired low surrogate escape")
                else -> builder.append(unit.toChar())
              }
            }
            else -> fail("invalid escape")
          }
          offset++
        }
        c < ' ' -> fail("unescaped control character")
        else -> {
          builder.append(c)
          offset++
        }
      }
    }
  }

  /** Reads 4 hex digits after `offset` (which points at `u`), leaving `offset` on the last digit. */
  private fun readHex4(): Int {
    if (offset + 4 >= text.length) fail("truncated unicode escape")
    var value = 0
    for (i in 1..4) {
      val c = text[offset + i]
      val digit = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> fail("invalid unicode escape")
      }
      value = value * 16 + digit
    }
    offset += 4
    return value
  }

  private fun parseNumber(): JsonElement {
    val start = offset
    if (peek() == '-') offset++
    when (peek()) {
      '0' -> offset++
      in '1'..'9' -> while (peek()?.let { it in '0'..'9' } == true) offset++
      else -> fail("invalid value")
    }
    var integral = true
    if (peek() == '.') {
      integral = false
      offset++
      if (peek()?.let { it in '0'..'9' } != true) fail("invalid number")
      while (peek()?.let { it in '0'..'9' } == true) offset++
    }
    if (peek() == 'e' || peek() == 'E') {
      integral = false
      offset++
      if (peek() == '+' || peek() == '-') offset++
      if (peek()?.let { it in '0'..'9' } != true) fail("invalid number")
      while (peek()?.let { it in '0'..'9' } == true) offset++
    }
    val literal = text.substring(start, offset)
    if (integral) literal.toLongOrNull()?.let { return JsonPrimitive(it) }
    return JsonPrimitive(BigDecimal(literal))
  }
}

/** Small helpers over kotlinx JSON trees that mirror Foundation `as?` casts. */
internal object JsonValues {
  fun isNumber(element: JsonElement): Boolean =
    element is JsonPrimitive && element !is JsonNull && !element.isString &&
      element.booleanOrNull == null

  fun isBoolean(element: JsonElement): Boolean =
    element is JsonPrimitive && element !is JsonNull && !element.isString &&
      element.booleanOrNull != null

  fun stringOrNull(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.takeIf { it.isString }?.content

  fun decimalOrNull(element: JsonElement?): BigDecimal? {
    if (element == null || !isNumber(element)) return null
    return (element as JsonPrimitive).content.toBigDecimalOrNull()
  }

  /** Like `NSNumber as? Int`: an integral, in-range number (not a boolean). */
  fun intOrNull(element: JsonElement?): Int? {
    val decimal = decimalOrNull(element) ?: return null
    return try {
      decimal.intValueExact()
    } catch (_: ArithmeticException) {
      null
    }
  }
}

/**
 * Deterministic JSON writer. Keys are sorted by UTF-16 code unit order; non-ASCII text is
 * written raw; `"`, `\` and control characters are escaped (`\n`, `\r`, `\t`, `\b`, `\f`, other
 * controls as lower-case `\u00xx`); `/` is never escaped. Compact output uses `,` and `:` with
 * no whitespace, matching Python `json.dumps(sort_keys=True, ensure_ascii=False,
 * separators=(",", ":"))` and Swift `JSONSerialization` `[.sortedKeys, .withoutEscapingSlashes]`.
 */
internal object CanonicalJson {
  fun write(element: JsonElement, pretty: Boolean = false): String {
    val builder = StringBuilder()
    writeValue(element, builder, pretty, 0)
    return builder.toString()
  }

  fun bytes(element: JsonElement): ByteArray = write(element).toByteArray(Charsets.UTF_8)

  private fun writeValue(element: JsonElement, out: StringBuilder, pretty: Boolean, indent: Int) {
    when (element) {
      is JsonObject -> {
        if (element.isEmpty()) {
          out.append("{}")
          return
        }
        out.append('{')
        element.keys.sorted().forEachIndexed { index, key ->
          if (index > 0) out.append(',')
          if (pretty) newline(out, indent + 1)
          writeString(key, out)
          out.append(if (pretty) ": " else ":")
          writeValue(element.getValue(key), out, pretty, indent + 1)
        }
        if (pretty) newline(out, indent)
        out.append('}')
      }
      is JsonArray -> {
        if (element.isEmpty()) {
          out.append("[]")
          return
        }
        out.append('[')
        element.forEachIndexed { index, child ->
          if (index > 0) out.append(',')
          if (pretty) newline(out, indent + 1)
          writeValue(child, out, pretty, indent + 1)
        }
        if (pretty) newline(out, indent)
        out.append(']')
      }
      is JsonNull -> out.append("null")
      is JsonPrimitive -> if (element.isString) writeString(element.content, out)
      else out.append(element.content)
    }
  }

  private fun newline(out: StringBuilder, indent: Int) {
    out.append('\n')
    repeat(indent) { out.append("  ") }
  }

  fun writeString(value: String, out: StringBuilder) {
    out.append('"')
    for (c in value) {
      when {
        c == '"' -> out.append("\\\"")
        c == '\\' -> out.append("\\\\")
        c == '\n' -> out.append("\\n")
        c == '\r' -> out.append("\\r")
        c == '\t' -> out.append("\\t")
        c == '\b' -> out.append("\\b")
        c == '\u000C' -> out.append("\\f")
        c < ' ' -> out.append("\\u").append(String.format("%04x", c.code))
        else -> out.append(c)
      }
    }
    out.append('"')
  }
}
