package app.piyokey.core.piyodeck

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal object PiyoDeckStrictJson {
  private val parser = Json {
    isLenient = false
    allowSpecialFloatingPointValues = false
    allowTrailingComma = false
  }

  fun decodeObject(data: ByteArray, name: String): JsonObject {
    if (data.size >= 3 &&
      data[0] == 0xEF.toByte() &&
      data[1] == 0xBB.toByte() &&
      data[2] == 0xBF.toByte()
    ) {
      throw PiyoDeckImportException.InvalidJson(name, "UTF-8 BOM is not permitted")
    }
    val text = try {
      StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(data))
        .toString()
    } catch (error: Exception) {
      throw PiyoDeckImportException.InvalidJson(name, "invalid UTF-8")
    }

    try {
      DuplicateKeyScanner(text, name, parser).parseDocument()
    } catch (error: PiyoDeckImportException) {
      throw error
    } catch (error: Exception) {
      throw PiyoDeckImportException.InvalidJson(name, "malformed JSON structure")
    }

    val value = try {
      parser.parseToJsonElement(text)
    } catch (error: SerializationException) {
      throw PiyoDeckImportException.InvalidJson(name, error.message ?: "malformed JSON")
    } catch (error: IllegalArgumentException) {
      throw PiyoDeckImportException.InvalidJson(name, error.message ?: "malformed JSON")
    }
    validateUnicode(value, name, "$")
    return value as? JsonObject
      ?: throw PiyoDeckImportException.InvalidJson(name, "root must be a JSON object")
  }

  fun canonicalData(value: JsonElement): ByteArray {
    validateUnicode(value, "JSON", "$")
    return buildString { appendCanonical(value) }.toByteArray(StandardCharsets.UTF_8)
  }

  private fun validateUnicode(value: JsonElement, name: String, path: String) {
    when (value) {
      is JsonObject -> value.forEach { (key, child) ->
        if (key.hasUnpairedSurrogate()) {
          throw PiyoDeckImportException.InvalidJson(name, "invalid Unicode scalar at $path.<key>")
        }
        validateUnicode(child, name, "$path.$key")
      }
      is JsonArray -> value.forEachIndexed { index, child ->
        validateUnicode(child, name, "$path[$index]")
      }
      is JsonPrimitive -> if (value.isString && value.content.hasUnpairedSurrogate()) {
        throw PiyoDeckImportException.InvalidJson(name, "invalid Unicode scalar at $path")
      }
    }
  }

  private fun String.hasUnpairedSurrogate(): Boolean {
    var index = 0
    while (index < length) {
      val character = this[index]
      when {
        character.isHighSurrogate() -> {
          if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return true
          index += 2
        }
        character.isLowSurrogate() -> return true
        else -> index += 1
      }
    }
    return false
  }

  private fun StringBuilder.appendCanonical(value: JsonElement) {
    when (value) {
      JsonNull -> append("null")
      is JsonObject -> {
        append('{')
        value.entries.sortedBy { it.key }.forEachIndexed { index, (key, child) ->
          if (index != 0) append(',')
          appendJsonString(key)
          append(':')
          appendCanonical(child)
        }
        append('}')
      }
      is JsonArray -> {
        append('[')
        value.forEachIndexed { index, child ->
          if (index != 0) append(',')
          appendCanonical(child)
        }
        append(']')
      }
      is JsonPrimitive -> {
        if (value.isString) {
          appendJsonString(value.content)
        } else {
          require(value.booleanOrNull != null || isCanonicalJsonNumber(value.content)) {
            "unsupported JSON primitive: ${value.content}"
          }
          append(value.content)
        }
      }
    }
  }

  private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { character ->
      when (character) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\b' -> append("\\b")
        '\u000C' -> append("\\f")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> {
          if (character.code < 0x20) {
            append("\\u")
            append(character.code.toString(16).padStart(4, '0'))
          } else {
            append(character)
          }
        }
      }
    }
    append('"')
  }

  private fun isCanonicalJsonNumber(value: String): Boolean =
    JSON_NUMBER.matches(value) && value !in setOf("NaN", "Infinity", "-Infinity")

  private val JSON_NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
}

private class DuplicateKeyScanner(
  private val text: String,
  private val name: String,
  private val json: Json,
) {
  private var offset: Int = 0

  fun parseDocument() {
    skipWhitespace()
    parseValue(path = "$", depth = 0)
    skipWhitespace()
    if (offset != text.length) malformed()
  }

  private fun parseValue(path: String, depth: Int) {
    skipWhitespace()
    when (current()) {
      '{' -> parseObject(path, depth + 1)
      '[' -> parseArray(path, depth + 1)
      '"' -> parseString()
      't' -> consumeLiteral("true")
      'f' -> consumeLiteral("false")
      'n' -> consumeLiteral("null")
      '-', in '0'..'9' -> parseNumber()
      else -> malformed()
    }
  }

  private fun parseObject(path: String, depth: Int) {
    checkDepth(depth)
    consume('{')
    skipWhitespace()
    if (current() == '}') {
      offset += 1
      return
    }
    val keys = mutableSetOf<String>()
    while (true) {
      skipWhitespace()
      val key = parseString()
      val keyPath = "$path.$key"
      if (!keys.add(key)) {
        throw PiyoDeckImportException.DuplicateJsonKey(name, keyPath)
      }
      skipWhitespace()
      consume(':')
      parseValue(keyPath, depth)
      skipWhitespace()
      when (current()) {
        '}' -> {
          offset += 1
          return
        }
        ',' -> offset += 1
        else -> malformed()
      }
    }
  }

  private fun parseArray(path: String, depth: Int) {
    checkDepth(depth)
    consume('[')
    skipWhitespace()
    if (current() == ']') {
      offset += 1
      return
    }
    var index = 0
    while (true) {
      parseValue("$path[$index]", depth)
      index += 1
      skipWhitespace()
      when (current()) {
        ']' -> {
          offset += 1
          return
        }
        ',' -> offset += 1
        else -> malformed()
      }
    }
  }

  private fun parseString(): String {
    if (current() != '"') malformed()
    val start = offset
    offset += 1
    while (offset < text.length) {
      when (val character = text[offset++]) {
        '"' -> {
          val literal = text.substring(start, offset)
          return try {
            json.parseToJsonElement(literal).let { (it as JsonPrimitive).content }
          } catch (error: Exception) {
            malformed()
          }
        }
        '\\' -> {
          if (offset >= text.length) malformed()
          val escaped = text[offset++]
          if (escaped == 'u') {
            val scalar = parseEscapedCodeUnit()
            when {
              scalar.isHighSurrogate() -> {
                if (offset + 6 > text.length || text[offset] != '\\' || text[offset + 1] != 'u') {
                  invalidUnicode()
                }
                offset += 2
                if (!parseEscapedCodeUnit().isLowSurrogate()) invalidUnicode()
              }
              scalar.isLowSurrogate() -> invalidUnicode()
            }
          } else if (escaped !in charArrayOf('"', '\\', '/', 'b', 'f', 'n', 'r', 't')) {
            malformed()
          }
        }
        else -> when {
          character.code < 0x20 -> malformed()
          character.isHighSurrogate() -> {
            if (offset >= text.length || !text[offset].isLowSurrogate()) invalidUnicode()
            offset += 1
          }
          character.isLowSurrogate() -> invalidUnicode()
        }
      }
    }
    malformed()
  }

  private fun parseNumber() {
    val start = offset
    if (current() == '-') offset += 1
    if (current() == '0') {
      offset += 1
      if (current() in '0'..'9') malformed()
    } else {
      if (current() !in '1'..'9') malformed()
      while (current() in '0'..'9') offset += 1
    }
    if (current() == '.') {
      offset += 1
      if (current() !in '0'..'9') malformed()
      while (current() in '0'..'9') offset += 1
    }
    if (current() == 'e' || current() == 'E') {
      offset += 1
      if (current() == '+' || current() == '-') offset += 1
      if (current() !in '0'..'9') malformed()
      while (current() in '0'..'9') offset += 1
    }
    if (!JSON_NUMBER.matches(text.substring(start, offset))) malformed()
  }

  private fun parseEscapedCodeUnit(): Char {
    if (offset + 4 > text.length) malformed()
    val digits = text.substring(offset, offset + 4)
    if (digits.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) malformed()
    offset += 4
    return digits.toInt(16).toChar()
  }

  private fun consumeLiteral(literal: String) {
    if (!text.startsWith(literal, offset)) malformed()
    offset += literal.length
  }

  private fun consume(expected: Char) {
    if (current() != expected) malformed()
    offset += 1
  }

  private fun skipWhitespace() {
    while (current() == ' ' || current() == '\t' || current() == '\n' || current() == '\r') {
      offset += 1
    }
  }

  private fun checkDepth(depth: Int) {
    if (depth > 64) {
      throw PiyoDeckImportException.InvalidJson(name, "nesting exceeds 64 levels")
    }
  }

  private fun current(): Char? = text.getOrNull(offset)

  private fun malformed(): Nothing =
    throw PiyoDeckImportException.InvalidJson(name, "malformed JSON structure")

  private fun invalidUnicode(): Nothing =
    throw PiyoDeckImportException.InvalidJson(name, "invalid Unicode scalar")

  private companion object {
    val JSON_NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
  }
}
