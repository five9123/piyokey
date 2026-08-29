import Foundation

/// Structural BCP 47 validation and canonical casing shared by DeckKit boundaries.
public enum LocaleTag {
  public static func canonicalize(_ value: String) -> String? {
    guard (2...63).contains(value.count), !value.contains("_") else { return nil }
    let parts = value.split(separator: "-", omittingEmptySubsequences: false).map(String.init)
    guard !parts.contains(where: \.isEmpty) else { return nil }
    if parts.first?.lowercased() == "x" {
      guard parts.count >= 2,
        parts.dropFirst().allSatisfy({ isASCIIAlphanumeric($0) && (1...8).contains($0.count) })
      else { return nil }
      return parts.map { $0.lowercased() }.joined(separator: "-")
    }

    guard let first = parts.first, isASCIIAlpha(first), (2...8).contains(first.count) else {
      return nil
    }
    var output = [first.lowercased()]
    var index = 1
    if (2...3).contains(first.count) {
      var extlangCount = 0
      while index < parts.count, extlangCount < 3, parts[index].count == 3,
        isASCIIAlpha(parts[index])
      {
        output.append(parts[index].lowercased())
        index += 1
        extlangCount += 1
      }
    }
    if index < parts.count, parts[index].count == 4, isASCIIAlpha(parts[index]) {
      let lower = parts[index].lowercased()
      output.append(lower.prefix(1).uppercased() + lower.dropFirst())
      index += 1
    }
    if index < parts.count,
      (parts[index].count == 2 && isASCIIAlpha(parts[index])
        || parts[index].count == 3 && isASCIIDigits(parts[index]))
    {
      output.append(isASCIIAlpha(parts[index]) ? parts[index].uppercased() : parts[index])
      index += 1
    }

    var variants = Set<String>()
    while index < parts.count {
      let part = parts[index]
      let isVariant =
        (part.count >= 5 && part.count <= 8 && isASCIIAlphanumeric(part))
        || (part.count == 4 && part.first?.isNumber == true && isASCIIAlphanumeric(part))
      guard isVariant else { break }
      let normalized = part.lowercased()
      guard variants.insert(normalized).inserted else { return nil }
      output.append(normalized)
      index += 1
    }

    var extensionSingletons = Set<String>()
    while index < parts.count, parts[index].count == 1, parts[index].lowercased() != "x" {
      let singleton = parts[index].lowercased()
      guard isASCIIAlphanumeric(singleton), extensionSingletons.insert(singleton).inserted else {
        return nil
      }
      output.append(singleton)
      index += 1
      let start = index
      while index < parts.count, (2...8).contains(parts[index].count),
        isASCIIAlphanumeric(parts[index])
      {
        output.append(parts[index].lowercased())
        index += 1
      }
      guard index > start else { return nil }
    }
    if index < parts.count, parts[index].lowercased() == "x" {
      output.append("x")
      index += 1
      let start = index
      while index < parts.count, (1...8).contains(parts[index].count),
        isASCIIAlphanumeric(parts[index])
      {
        output.append(parts[index].lowercased())
        index += 1
      }
      guard index > start else { return nil }
    }
    guard index == parts.count else { return nil }
    return output.joined(separator: "-")
  }

  public static func isCanonical(_ value: String) -> Bool { canonicalize(value) == value }

  public static func lookupCandidates(requested: String, defaultLocale: String?) -> [String] {
    var result: [String] = []
    if let exact = canonicalize(requested.replacingOccurrences(of: "_", with: "-")) {
      result.append(exact)
      if let separator = exact.firstIndex(of: "-") {
        result.append(String(exact[..<separator]))
      }
    }
    if let defaultLocale { result.append(defaultLocale) }
    result.append("en")
    var seen = Set<String>()
    return result.filter { seen.insert($0).inserted }
  }

  private static func isASCIIAlpha(_ value: String) -> Bool {
    !value.isEmpty && value.unicodeScalars.allSatisfy {
      (65...90).contains($0.value) || (97...122).contains($0.value)
    }
  }

  private static func isASCIIDigits(_ value: String) -> Bool {
    !value.isEmpty && value.unicodeScalars.allSatisfy { (48...57).contains($0.value) }
  }

  private static func isASCIIAlphanumeric(_ value: String) -> Bool {
    !value.isEmpty && value.unicodeScalars.allSatisfy {
      (48...57).contains($0.value) || (65...90).contains($0.value) || (97...122).contains($0.value)
    }
  }
}
