package app.piyokey.core.deckkit

import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Errors thrown by [JsonSchemaValidator] (Swift `JSONSchemaValidationError`). */
sealed class JsonSchemaValidationError(message: String) : Exception(message) {
  data object InvalidSchemaRoot : JsonSchemaValidationError("schema root must be a JSON object")

  data class InvalidReference(val reference: String) :
    JsonSchemaValidationError("unsupported or unresolved \$ref: $reference")

  /** The instance or schema bytes are not RFC 8259 JSON (Foundation throws an NSError here). */
  data class InvalidJson(val reason: String) : JsonSchemaValidationError(reason)
}

/**
 * Dependency-free validator for the JSON Schema keywords used by the shared schemas:
 * `$ref` (local `#/...`), `oneOf`, `type`, `enum`, `properties`, `required`,
 * `additionalProperties` (bool or schema), `propertyNames`, `minProperties`/`maxProperties`,
 * `minItems`/`maxItems`, `uniqueItems`, `items`, `minLength`/`maxLength` (Unicode scalars),
 * `pattern`, `format` (`date-time`, `uri-reference`), `minimum`/`maximum`.
 */
object JsonSchemaValidator {
  @Throws(JsonSchemaValidationError::class)
  fun validate(instanceData: ByteArray, schemaData: ByteArray): List<ContentValidationIssue> {
    val instance = parse(instanceData)
    val schema = parse(schemaData) as? JsonObject ?: throw JsonSchemaValidationError.InvalidSchemaRoot
    return validate(instance, schema)
  }

  @Throws(JsonSchemaValidationError::class)
  fun validate(instance: JsonElement, schema: JsonObject): List<ContentValidationIssue> {
    val issues = mutableListOf<ContentValidationIssue>()
    validateNode(instance, schema, schema, "$", issues)
    return issues
  }

  private fun parse(data: ByteArray): JsonElement =
    try {
      StrictJsonParser.parse(data)
    } catch (error: JsonSyntaxException) {
      throw JsonSchemaValidationError.InvalidJson(error.message ?: "invalid JSON")
    }

  private val patternCache = ConcurrentHashMap<String, Regex>()

  private fun validateNode(
    value: JsonElement,
    schema: JsonObject,
    rootSchema: JsonObject,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    JsonValues.stringOrNull(schema["\$ref"])?.let { reference ->
      val resolved = resolve(reference, rootSchema)
      validateNode(value, resolved, rootSchema, path, issues)
      return
    }

    objectList(schema["oneOf"])?.let { alternatives ->
      var successCount = 0
      for (alternative in alternatives) {
        val alternativeIssues = mutableListOf<ContentValidationIssue>()
        validateNode(value, alternative, rootSchema, path, alternativeIssues)
        if (alternativeIssues.isEmpty()) successCount += 1
      }
      if (successCount != 1) {
        issues.add(
          ContentValidationIssue("schema.oneOf", path, "oneOf 스키마 중 정확히 하나와 일치해야 합니다"),
        )
      }
      return
    }

    val expectedType = JsonValues.stringOrNull(schema["type"])
    if (expectedType != null && !matchesType(value, expectedType)) {
      issues.add(ContentValidationIssue("schema.type", path, "$expectedType 타입이어야 합니다"))
      return
    }

    (schema["enum"] as? JsonArray)?.let { enumValues ->
      val key = canonicalJson(value)
      if (enumValues.none { canonicalJson(it) == key }) {
        issues.add(ContentValidationIssue("schema.enum", path, "허용된 enum 값이 아닙니다"))
      }
    }

    if (value is JsonObject) {
      val properties = objectMap(schema["properties"]) ?: emptyMap()
      validateCount(value.size, schema, path, "properties", issues)
      (schema["required"] as? JsonArray)?.let { required ->
        val keys = required.map { JsonValues.stringOrNull(it) }
        if (keys.all { it != null }) {
          for (key in keys) {
            if (!value.containsKey(key!!)) {
              issues.add(ContentValidationIssue("schema.required", "$path.$key", "필수 필드입니다"))
            }
          }
        }
      }
      val additionalProperties = schema["additionalProperties"]
      if (additionalProperties is JsonPrimitive && JsonValues.isBoolean(additionalProperties) &&
        additionalProperties.booleanOrNull == false
      ) {
        for (key in value.keys) {
          if (key !in properties) {
            issues.add(
              ContentValidationIssue(
                "schema.additionalProperties", "$path.$key", "정의되지 않은 필드입니다",
              ),
            )
          }
        }
      }
      (schema["propertyNames"] as? JsonObject)?.let { propertyNames ->
        for (key in value.keys) {
          validateNode(JsonPrimitive(key), propertyNames, rootSchema, "$path.<key:$key>", issues)
        }
      }
      for ((key, childSchema) in properties) {
        val child = value[key] ?: continue
        validateNode(child, childSchema, rootSchema, "$path.$key", issues)
      }
      if (additionalProperties is JsonObject) {
        for ((key, child) in value) {
          if (key !in properties) {
            validateNode(child, additionalProperties, rootSchema, "$path.$key", issues)
          }
        }
      }
    }

    if (value is JsonArray) {
      validateCount(value.size, schema, path, "items", issues)
      val uniqueItems = schema["uniqueItems"]
      if (uniqueItems != null && JsonValues.isBoolean(uniqueItems) &&
        (uniqueItems as JsonPrimitive).booleanOrNull == true
      ) {
        val seen = mutableSetOf<String>()
        for (item in value) {
          if (!seen.add(canonicalJson(item))) {
            issues.add(
              ContentValidationIssue("schema.uniqueItems", path, "중복 배열 항목을 허용하지 않습니다"),
            )
            break
          }
        }
      }
      (schema["items"] as? JsonObject)?.let { itemSchema ->
        for ((index, child) in value.withIndex()) {
          validateNode(child, itemSchema, rootSchema, "$path[$index]", issues)
        }
      }
    }

    JsonValues.stringOrNull(value)?.let { string ->
      validateCount(string.codePointCount(0, string.length), schema, path, "length", issues)
      JsonValues.stringOrNull(schema["pattern"])?.let { pattern ->
        val regex = patternCache.getOrPut(pattern) { Regex(pattern) }
        if (!regex.containsMatchIn(string)) {
          issues.add(ContentValidationIssue("schema.pattern", path, "문자열 패턴과 일치하지 않습니다"))
        }
      }
      JsonValues.stringOrNull(schema["format"])?.let { format ->
        if (!matchesFormat(string, format)) {
          issues.add(ContentValidationIssue("schema.format", path, "$format 형식과 일치하지 않습니다"))
        }
      }
    }

    if (JsonValues.isNumber(value)) {
      val number = (value as JsonPrimitive).content.toDouble()
      JsonValues.decimalOrNull(schema["minimum"])?.let { minimum ->
        if (number < minimum.toDouble()) {
          issues.add(ContentValidationIssue("schema.minimum", path, "최솟값보다 작습니다"))
        }
      }
      JsonValues.decimalOrNull(schema["maximum"])?.let { maximum ->
        if (number > maximum.toDouble()) {
          issues.add(ContentValidationIssue("schema.maximum", path, "최댓값보다 큽니다"))
        }
      }
    }
  }

  private fun resolve(reference: String, rootSchema: JsonObject): JsonObject {
    if (!reference.startsWith("#/")) throw JsonSchemaValidationError.InvalidReference(reference)
    var current: JsonElement = rootSchema
    for (rawComponent in reference.substring(2).split('/').filter { it.isNotEmpty() }) {
      val component = rawComponent.replace("~1", "/").replace("~0", "~")
      val next = (current as? JsonObject)?.get(component)
        ?: throw JsonSchemaValidationError.InvalidReference(reference)
      current = next
    }
    return current as? JsonObject ?: throw JsonSchemaValidationError.InvalidReference(reference)
  }

  private fun matchesType(value: JsonElement, expected: String): Boolean =
    when (expected) {
      "object" -> value is JsonObject
      "array" -> value is JsonArray
      "string" -> JsonValues.stringOrNull(value) != null
      "boolean" -> JsonValues.isBoolean(value)
      "null" -> value is JsonNull
      "integer" -> JsonValues.isNumber(value) &&
        (value as JsonPrimitive).content.toDouble().let { it == kotlin.math.truncate(it) }
      "number" -> JsonValues.isNumber(value)
      else -> false
    }

  private fun validateCount(
    count: Int,
    schema: JsonObject,
    path: String,
    unit: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    val (minimumKey, maximumKey) = when (unit) {
      "items" -> "minItems" to "maxItems"
      "properties" -> "minProperties" to "maxProperties"
      else -> "minLength" to "maxLength"
    }
    JsonValues.intOrNull(schema[minimumKey])?.let { minimum ->
      if (count < minimum) {
        issues.add(ContentValidationIssue("schema.$minimumKey", path, "최소 ${minimum}이어야 합니다"))
      }
    }
    JsonValues.intOrNull(schema[maximumKey])?.let { maximum ->
      if (count > maximum) {
        issues.add(ContentValidationIssue("schema.$maximumKey", path, "최대 ${maximum}이어야 합니다"))
      }
    }
  }

  private fun matchesFormat(value: String, format: String): Boolean =
    when (format) {
      "date-time" -> Iso8601InstantSerializer.parse(value) != null
      "uri-reference" -> try {
        URI(value)
        true
      } catch (_: URISyntaxException) {
        false
      }
      else -> true
    }

  /** Order-insensitive structural key; numbers compare by value (1 == 1.0), booleans distinct. */
  private fun canonicalJson(value: JsonElement): String =
    when (value) {
      is JsonObject -> value.keys.sorted()
        .joinToString(",", "{", "}") { "${quote(it)}:${canonicalJson(value.getValue(it))}" }
      is JsonArray -> value.joinToString(",", "[", "]") { canonicalJson(it) }
      is JsonNull -> "null"
      is JsonPrimitive -> when {
        value.isString -> quote(value.content)
        JsonValues.isBoolean(value) -> value.content
        else -> "n:" + (value.content.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString()
          ?: value.content)
      }
    }

  private fun quote(value: String): String =
    StringBuilder().also { CanonicalJson.writeString(value, it) }.toString()

  private fun objectMap(element: JsonElement?): Map<String, JsonObject>? {
    val obj = element as? JsonObject ?: return null
    if (obj.values.any { it !is JsonObject }) return null
    @Suppress("UNCHECKED_CAST")
    return obj as Map<String, JsonObject>
  }

  private fun objectList(element: JsonElement?): List<JsonObject>? {
    val array = element as? JsonArray ?: return null
    if (array.any { it !is JsonObject }) return null
    return array.map { it as JsonObject }
  }
}
