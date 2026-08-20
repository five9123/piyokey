import Foundation

enum PiyoDeckStrictJSON {
  static func validate(_ data: Data, name: String) throws {
    if data.starts(with: [0xEF, 0xBB, 0xBF]) {
      throw PiyoDeckImportError.invalidJSON(name: name, reason: "UTF-8 BOM is not permitted")
    }
    guard String(data: data, encoding: .utf8) != nil else {
      throw PiyoDeckImportError.invalidJSON(name: name, reason: "invalid UTF-8")
    }
    do {
      _ = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
    } catch {
      throw PiyoDeckImportError.invalidJSON(name: name, reason: String(describing: error))
    }

    var parser = DuplicateKeyParser(bytes: Array(data))
    do {
      try parser.parseDocument()
    } catch let error as DuplicateKeyParser.Failure {
      switch error {
      case .duplicate(let path):
        throw PiyoDeckImportError.duplicateJSONKey(name: name, path: path)
      case .nestingLimit:
        throw PiyoDeckImportError.invalidJSON(name: name, reason: "nesting exceeds 64 levels")
      case .malformed:
        throw PiyoDeckImportError.invalidJSON(name: name, reason: "malformed JSON structure")
      }
    }
  }
}

private struct DuplicateKeyParser {
  enum Failure: Error {
    case duplicate(String)
    case nestingLimit
    case malformed
  }

  let bytes: [UInt8]
  var offset = 0

  mutating func parseDocument() throws {
    skipWhitespace()
    try parseValue(path: "$", depth: 0)
    skipWhitespace()
    guard offset == bytes.count else { throw Failure.malformed }
  }

  private mutating func parseValue(path: String, depth: Int) throws {
    guard depth <= 64 else { throw Failure.nestingLimit }
    skipWhitespace()
    guard let byte = current else { throw Failure.malformed }
    switch byte {
    case 0x7B:
      try parseObject(path: path, depth: depth + 1)
    case 0x5B:
      try parseArray(path: path, depth: depth + 1)
    case 0x22:
      _ = try parseString()
    default:
      try parsePrimitive()
    }
  }

  private mutating func parseObject(path: String, depth: Int) throws {
    try consume(0x7B)
    skipWhitespace()
    if current == 0x7D {
      offset += 1
      return
    }

    var keys = Set<String>()
    while true {
      skipWhitespace()
      let key = try parseString()
      let keyPath = "\(path).\(key)"
      guard keys.insert(key).inserted else { throw Failure.duplicate(keyPath) }
      skipWhitespace()
      try consume(0x3A)
      try parseValue(path: keyPath, depth: depth)
      skipWhitespace()
      if current == 0x7D {
        offset += 1
        return
      }
      try consume(0x2C)
    }
  }

  private mutating func parseArray(path: String, depth: Int) throws {
    try consume(0x5B)
    skipWhitespace()
    if current == 0x5D {
      offset += 1
      return
    }

    var index = 0
    while true {
      try parseValue(path: "\(path)[\(index)]", depth: depth)
      index += 1
      skipWhitespace()
      if current == 0x5D {
        offset += 1
        return
      }
      try consume(0x2C)
    }
  }

  private mutating func parseString() throws -> String {
    guard current == 0x22 else { throw Failure.malformed }
    let start = offset
    offset += 1
    var escaped = false
    while let byte = current {
      offset += 1
      if escaped {
        escaped = false
      } else if byte == 0x5C {
        escaped = true
      } else if byte == 0x22 {
        let stringData = Data(bytes[start..<offset])
        guard
          let value = try JSONSerialization.jsonObject(
            with: stringData,
            options: [.fragmentsAllowed]
          ) as? String
        else {
          throw Failure.malformed
        }
        return value
      }
    }
    throw Failure.malformed
  }

  private mutating func parsePrimitive() throws {
    let start = offset
    while let byte = current,
      byte != 0x2C, byte != 0x5D, byte != 0x7D,
      byte != 0x20, byte != 0x09, byte != 0x0A, byte != 0x0D
    {
      offset += 1
    }
    guard offset > start else { throw Failure.malformed }
  }

  private mutating func consume(_ expected: UInt8) throws {
    guard current == expected else { throw Failure.malformed }
    offset += 1
  }

  private mutating func skipWhitespace() {
    while let byte = current, byte == 0x20 || byte == 0x09 || byte == 0x0A || byte == 0x0D {
      offset += 1
    }
  }

  private var current: UInt8? {
    offset < bytes.count ? bytes[offset] : nil
  }
}
