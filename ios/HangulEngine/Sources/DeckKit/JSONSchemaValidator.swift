import CoreFoundation
import Foundation

public enum JSONSchemaValidationError: Error, Equatable, Sendable {
  case invalidSchemaRoot
  case invalidReference(String)
}

/// Dependency-free validator for the JSON Schema keywords used by Hanco fixtures.
public enum JSONSchemaValidator {
  public static func validate(instanceData: Data, schemaData: Data) throws
    -> [ContentValidationIssue]
  {
    let instance = try JSONSerialization.jsonObject(with: instanceData, options: [.fragmentsAllowed])
    let schemaObject = try JSONSerialization.jsonObject(with: schemaData)
    guard let schema = schemaObject as? [String: Any] else {
      throw JSONSchemaValidationError.invalidSchemaRoot
    }
    var issues: [ContentValidationIssue] = []
    try validateNode(instance, schema: schema, rootSchema: schema, path: "$", into: &issues)
    return issues
  }

  private static func validateNode(
    _ value: Any,
    schema: [String: Any],
    rootSchema: [String: Any],
    path: String,
    into issues: inout [ContentValidationIssue]
  ) throws {
    if let reference = schema["$ref"] as? String {
      let resolved = try resolve(reference, rootSchema: rootSchema)
      try validateNode(value, schema: resolved, rootSchema: rootSchema, path: path, into: &issues)
      return
    }

    if let alternatives = schema["oneOf"] as? [[String: Any]] {
      var successCount = 0
      for alternative in alternatives {
        var alternativeIssues: [ContentValidationIssue] = []
        try validateNode(
          value, schema: alternative, rootSchema: rootSchema, path: path, into: &alternativeIssues)
        if alternativeIssues.isEmpty { successCount += 1 }
      }
      if successCount != 1 {
        issues.append(
          .init(code: "schema.oneOf", path: path, message: "oneOf 스키마 중 정확히 하나와 일치해야 합니다"))
      }
      return
    }

    if let expectedType = schema["type"] as? String, !matchesType(value, expected: expectedType) {
      issues.append(.init(code: "schema.type", path: path, message: "\(expectedType) 타입이어야 합니다"))
      return
    }

    if let enumValues = schema["enum"] as? [Any],
      !enumValues.contains(where: { jsonEqual($0, value) })
    {
      issues.append(.init(code: "schema.enum", path: path, message: "허용된 enum 값이 아닙니다"))
    }

    if let object = value as? [String: Any] {
      let properties = schema["properties"] as? [String: [String: Any]] ?? [:]
      validateCount(object.count, schema: schema, path: path, unit: "properties", into: &issues)
      if let required = schema["required"] as? [String] {
        for key in required where object[key] == nil {
          issues.append(.init(code: "schema.required", path: "\(path).\(key)", message: "필수 필드입니다"))
        }
      }
      if schema["additionalProperties"] as? Bool == false {
        for key in object.keys where properties[key] == nil {
          issues.append(
            .init(
              code: "schema.additionalProperties", path: "\(path).\(key)", message: "정의되지 않은 필드입니다")
          )
        }
      }
      for (key, childSchema) in properties {
        if let child = object[key] {
          try validateNode(
            child, schema: childSchema, rootSchema: rootSchema, path: "\(path).\(key)",
            into: &issues)
        }
      }
    }

    if let array = value as? [Any] {
      validateCount(array.count, schema: schema, path: path, unit: "items", into: &issues)
      if schema["uniqueItems"] as? Bool == true {
        var seen = Set<String>()
        for item in array {
          let key = canonicalJSON(item)
          if !seen.insert(key).inserted {
            issues.append(
              .init(code: "schema.uniqueItems", path: path, message: "중복 배열 항목을 허용하지 않습니다"))
            break
          }
        }
      }
      if let itemSchema = schema["items"] as? [String: Any] {
        for (index, child) in array.enumerated() {
          try validateNode(
            child, schema: itemSchema, rootSchema: rootSchema, path: "\(path)[\(index)]",
            into: &issues)
        }
      }
    }

    if let string = value as? String {
      validateCount(
        string.unicodeScalars.count,
        schema: schema,
        path: path,
        unit: "length",
        into: &issues
      )
      if let pattern = schema["pattern"] as? String,
        string.range(of: pattern, options: .regularExpression) == nil
      {
        issues.append(.init(code: "schema.pattern", path: path, message: "문자열 패턴과 일치하지 않습니다"))
      }
      if let format = schema["format"] as? String, !matchesFormat(string, format: format) {
        issues.append(.init(code: "schema.format", path: path, message: "\(format) 형식과 일치하지 않습니다"))
      }
    }

    if let number = value as? NSNumber, !isBoolean(number) {
      if let minimum = schema["minimum"] as? NSNumber, number.doubleValue < minimum.doubleValue {
        issues.append(.init(code: "schema.minimum", path: path, message: "최솟값보다 작습니다"))
      }
      if let maximum = schema["maximum"] as? NSNumber, number.doubleValue > maximum.doubleValue {
        issues.append(.init(code: "schema.maximum", path: path, message: "최댓값보다 큽니다"))
      }
    }
  }

  private static func resolve(_ reference: String, rootSchema: [String: Any]) throws -> [String:
    Any]
  {
    guard reference.hasPrefix("#/") else {
      throw JSONSchemaValidationError.invalidReference(reference)
    }
    var current: Any = rootSchema
    for rawComponent in reference.dropFirst(2).split(separator: "/") {
      let component = rawComponent.replacingOccurrences(of: "~1", with: "/").replacingOccurrences(
        of: "~0", with: "~")
      guard let object = current as? [String: Any], let next = object[component] else {
        throw JSONSchemaValidationError.invalidReference(reference)
      }
      current = next
    }
    guard let resolved = current as? [String: Any] else {
      throw JSONSchemaValidationError.invalidReference(reference)
    }
    return resolved
  }

  private static func matchesType(_ value: Any, expected: String) -> Bool {
    switch expected {
    case "object": return value is [String: Any]
    case "array": return value is [Any]
    case "string": return value is String
    case "boolean": return (value as? NSNumber).map(isBoolean) ?? false
    case "null": return value is NSNull
    case "integer":
      guard let number = value as? NSNumber, !isBoolean(number) else { return false }
      return number.doubleValue.rounded(.towardZero) == number.doubleValue
    case "number": return (value as? NSNumber).map { !isBoolean($0) } ?? false
    default: return false
    }
  }

  private static func validateCount(
    _ count: Int,
    schema: [String: Any],
    path: String,
    unit: String,
    into issues: inout [ContentValidationIssue]
  ) {
    let minimumKey: String
    let maximumKey: String
    switch unit {
    case "items":
      minimumKey = "minItems"
      maximumKey = "maxItems"
    case "properties":
      minimumKey = "minProperties"
      maximumKey = "maxProperties"
    default:
      minimumKey = "minLength"
      maximumKey = "maxLength"
    }
    if let minimum = schema[minimumKey] as? Int, count < minimum {
      issues.append(
        .init(code: "schema.\(minimumKey)", path: path, message: "최소 \(minimum)이어야 합니다"))
    }
    if let maximum = schema[maximumKey] as? Int, count > maximum {
      issues.append(
        .init(code: "schema.\(maximumKey)", path: path, message: "최대 \(maximum)이어야 합니다"))
    }
  }

  private static func matchesFormat(_ value: String, format: String) -> Bool {
    switch format {
    case "date-time": return ISO8601DateFormatter().date(from: value) != nil
    case "uri-reference": return URL(string: value) != nil
    default: return true
    }
  }

  private static func canonicalJSON(_ value: Any) -> String {
    guard JSONSerialization.isValidJSONObject(value),
      let data = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys])
    else { return String(describing: value) }
    return data.base64EncodedString()
  }

  private static func jsonEqual(_ lhs: Any, _ rhs: Any) -> Bool {
    canonicalJSON([lhs]) == canonicalJSON([rhs])
  }

  private static func isBoolean(_ number: NSNumber) -> Bool {
    CFGetTypeID(number) == CFBooleanGetTypeID()
  }
}
