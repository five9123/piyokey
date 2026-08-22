package app.piyokey.core.deckkit

import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

sealed class JsonSchemaValidationException(message: String) : IllegalArgumentException(message) {
  data object InvalidSchemaRoot : JsonSchemaValidationException("Schema root must be an object")

  data class InvalidReference(val reference: String) :
    JsonSchemaValidationException("Invalid schema reference: $reference")
}

/** Implements the JSON Schema 2020-12 keywords used by the repository contracts. */
object JsonSchemaValidator {
  private val parser = Json { isLenient = false }

  fun validate(instanceSource: String, schemaSource: String): List<ContentValidationIssue> {
    val instance = parse(instanceSource, "instance")
    val schemaElement = parse(schemaSource, "schema")
    val schema = schemaElement as? JsonObject ?: throw JsonSchemaValidationException.InvalidSchemaRoot
    return buildList {
      validateNode(instance, schema, schema, "$", this)
    }
  }

  private fun parse(source: String, label: String): JsonElement = try {
    parser.parseToJsonElement(source)
  } catch (error: SerializationException) {
    throw IllegalArgumentException("Invalid $label JSON", error)
  }

  private fun validateNode(
    value: JsonElement,
    schema: JsonObject,
    rootSchema: JsonObject,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    schema.stringOrNull("\$ref")?.let { reference ->
      validateNode(value, resolve(reference, rootSchema), rootSchema, path, issues)
      return
    }

    (schema["oneOf"] as? JsonArray)?.let { alternatives ->
      val successCount = alternatives.count { alternative ->
        val alternativeIssues = mutableListOf<ContentValidationIssue>()
        validateNode(
          value,
          alternative as? JsonObject ?: throw JsonSchemaValidationException.InvalidSchemaRoot,
          rootSchema,
          path,
          alternativeIssues,
        )
        alternativeIssues.isEmpty()
      }
      if (successCount != 1) {
        issues += ContentValidationIssue(
          code = "schema.oneOf",
          path = path,
          message = "oneOf 스키마 중 정확히 하나와 일치해야 합니다",
        )
      }
      return
    }

    schema.stringOrNull("type")?.let { expectedType ->
      if (!matchesType(value, expectedType)) {
        issues += ContentValidationIssue("schema.type", path, "$expectedType 타입이어야 합니다")
        return
      }
    }

    (schema["enum"] as? JsonArray)?.let { enumValues ->
      if (enumValues.none { jsonEqual(it, value) }) {
        issues += ContentValidationIssue("schema.enum", path, "허용된 enum 값이 아닙니다")
      }
    }

    if (value is JsonObject) {
      validateObject(value, schema, rootSchema, path, issues)
    }
    if (value is JsonArray) {
      validateArray(value, schema, rootSchema, path, issues)
    }
    if (value is JsonPrimitive && value.isString) {
      validateString(value.content, schema, path, issues)
    }
    if (value is JsonPrimitive && value.isNumber()) {
      validateNumber(value, schema, path, issues)
    }
  }

  private fun validateObject(
    value: JsonObject,
    schema: JsonObject,
    rootSchema: JsonObject,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val required = (schema["required"] as? JsonArray).orEmpty().mapNotNull {
      (it as? JsonPrimitive)?.contentOrNull
    }
    required.filterNot(value::containsKey).forEach { key ->
      issues += ContentValidationIssue("schema.required", "$path.$key", "필수 필드입니다")
    }
    if (schema.booleanOrNull("additionalProperties") == false) {
      (value.keys - properties.keys).sorted().forEach { key ->
        issues += ContentValidationIssue(
          "schema.additionalProperties",
          "$path.$key",
          "정의되지 않은 필드입니다",
        )
      }
    }
    properties.forEach { (key, childSchema) ->
      val child = value[key] ?: return@forEach
      val childObject = childSchema as? JsonObject
        ?: throw JsonSchemaValidationException.InvalidSchemaRoot
      validateNode(child, childObject, rootSchema, "$path.$key", issues)
    }
    validateCount(value.size, schema, path, "Properties", issues)
  }

  private fun validateArray(
    value: JsonArray,
    schema: JsonObject,
    rootSchema: JsonObject,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    validateCount(value.size, schema, path, "Items", issues)
    if (schema.booleanOrNull("uniqueItems") == true) {
      val seen = mutableSetOf<String>()
      if (value.any { !seen.add(canonicalJson(it)) }) {
        issues += ContentValidationIssue(
          "schema.uniqueItems",
          path,
          "중복 배열 항목을 허용하지 않습니다",
        )
      }
    }
    val itemSchema = schema["items"] as? JsonObject ?: return
    value.forEachIndexed { index, child ->
      validateNode(child, itemSchema, rootSchema, "$path[$index]", issues)
    }
  }

  private fun validateString(
    value: String,
    schema: JsonObject,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    val codePointCount = value.codePointCount(0, value.length)
    validateCount(codePointCount, schema, path, "Length", issues)
    schema.stringOrNull("pattern")?.let { pattern ->
      if (!Regex(pattern).containsMatchIn(value)) {
        issues += ContentValidationIssue("schema.pattern", path, "문자열 패턴과 일치하지 않습니다")
      }
    }
    schema.stringOrNull("format")?.let { format ->
      if (!matchesFormat(value, format)) {
        issues += ContentValidationIssue("schema.format", path, "$format 형식과 일치하지 않습니다")
      }
    }
  }

  private fun validateNumber(
    value: JsonPrimitive,
    schema: JsonObject,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    val number = value.content.toBigDecimalOrNull() ?: return
    schema.numberOrNull("minimum")?.let { minimum ->
      if (number < minimum) {
        issues += ContentValidationIssue("schema.minimum", path, "최솟값보다 작습니다")
      }
    }
    schema.numberOrNull("maximum")?.let { maximum ->
      if (number > maximum) {
        issues += ContentValidationIssue("schema.maximum", path, "최댓값보다 큽니다")
      }
    }
  }

  private fun validateCount(
    count: Int,
    schema: JsonObject,
    path: String,
    suffix: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    schema.intOrNull("min$suffix")?.let { minimum ->
      if (count < minimum) {
        issues += ContentValidationIssue("schema.min$suffix", path, "최소 $minimum 이어야 합니다")
      }
    }
    schema.intOrNull("max$suffix")?.let { maximum ->
      if (count > maximum) {
        issues += ContentValidationIssue("schema.max$suffix", path, "최대 $maximum 이어야 합니다")
      }
    }
  }

  private fun resolve(reference: String, rootSchema: JsonObject): JsonObject {
    if (!reference.startsWith("#/")) throw JsonSchemaValidationException.InvalidReference(reference)
    var current: JsonElement = rootSchema
    reference.removePrefix("#/").split('/').forEach { rawComponent ->
      val component = rawComponent.replace("~1", "/").replace("~0", "~")
      current = (current as? JsonObject)?.get(component)
        ?: throw JsonSchemaValidationException.InvalidReference(reference)
    }
    return current as? JsonObject ?: throw JsonSchemaValidationException.InvalidReference(reference)
  }

  private fun matchesType(value: JsonElement, expected: String): Boolean = when (expected) {
    "object" -> value is JsonObject
    "array" -> value is JsonArray
    "string" -> value is JsonPrimitive && value.isString
    "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
    "null" -> value === JsonNull
    "integer" -> value is JsonPrimitive && value.isNumber() && value.isInteger()
    "number" -> value is JsonPrimitive && value.isNumber()
    else -> false
  }

  private fun matchesFormat(value: String, format: String): Boolean = when (format) {
    "date-time" -> try {
      OffsetDateTime.parse(value)
      true
    } catch (_: DateTimeParseException) {
      false
    }
    "uri-reference" -> try {
      URI(value)
      true
    } catch (_: URISyntaxException) {
      false
    }
    else -> true
  }

  private fun JsonPrimitive.isNumber(): Boolean =
    !isString && booleanOrNull == null && doubleOrNull != null

  private fun JsonPrimitive.isInteger(): Boolean =
    content.toBigDecimalOrNull()?.stripTrailingZeros()?.scale()?.let { it <= 0 } == true

  private fun canonicalJson(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries.sortedBy(Map.Entry<String, JsonElement>::key)
      .joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "${JsonPrimitive(key)}:${canonicalJson(value)}"
      }
    is JsonArray -> element.joinToString(prefix = "[", postfix = "]", transform = ::canonicalJson)
    else -> element.toString()
  }

  private fun jsonEqual(left: JsonElement, right: JsonElement): Boolean =
    canonicalJson(left) == canonicalJson(right)
}

private fun JsonObject.stringOrNull(key: String): String? =
  (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

private fun JsonObject.booleanOrNull(key: String): Boolean? =
  (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.intOrNull(key: String): Int? =
  (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

private fun JsonObject.numberOrNull(key: String): BigDecimal? =
  (this[key] as? JsonPrimitive)?.content?.toBigDecimalOrNull()
