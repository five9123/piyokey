package app.piyokey.core.deckkit

/** Structural BCP 47 validation and canonical casing shared by DeckKit boundaries. */
object LocaleTag {
  /**
   * Returns the canonical casing of a structurally well-formed BCP 47 tag, or `null` when the tag
   * is malformed (including `_` separators, empty subtags, duplicate variants or singletons).
   */
  fun canonicalize(value: String): String? {
    if (value.length !in 2..63 || value.contains('_')) return null
    val parts = value.split('-')
    if (parts.any { it.isEmpty() }) return null
    if (parts.first().lowercase() == "x") {
      if (parts.size < 2 || !parts.drop(1).all { isAsciiAlphanumeric(it) && it.length in 1..8 }) {
        return null
      }
      return parts.joinToString("-") { it.lowercase() }
    }

    val first = parts.first()
    if (!isAsciiAlpha(first) || first.length !in 2..8) return null
    val output = mutableListOf(first.lowercase())
    var index = 1
    if (first.length in 2..3) {
      var extlangCount = 0
      while (index < parts.size && extlangCount < 3 && parts[index].length == 3 &&
        isAsciiAlpha(parts[index])
      ) {
        output.add(parts[index].lowercase())
        index += 1
        extlangCount += 1
      }
    }
    if (index < parts.size && parts[index].length == 4 && isAsciiAlpha(parts[index])) {
      val lower = parts[index].lowercase()
      output.add(lower.substring(0, 1).uppercase() + lower.substring(1))
      index += 1
    }
    if (index < parts.size &&
      ((parts[index].length == 2 && isAsciiAlpha(parts[index])) ||
        (parts[index].length == 3 && isAsciiDigits(parts[index])))
    ) {
      output.add(if (isAsciiAlpha(parts[index])) parts[index].uppercase() else parts[index])
      index += 1
    }

    val variants = mutableSetOf<String>()
    while (index < parts.size) {
      val part = parts[index]
      val isVariant =
        (part.length in 5..8 && isAsciiAlphanumeric(part)) ||
          (part.length == 4 && part[0] in '0'..'9' && isAsciiAlphanumeric(part))
      if (!isVariant) break
      val normalized = part.lowercase()
      if (!variants.add(normalized)) return null
      output.add(normalized)
      index += 1
    }

    val extensionSingletons = mutableSetOf<String>()
    while (index < parts.size && parts[index].length == 1 && parts[index].lowercase() != "x") {
      val singleton = parts[index].lowercase()
      if (!isAsciiAlphanumeric(singleton) || !extensionSingletons.add(singleton)) return null
      output.add(singleton)
      index += 1
      val start = index
      while (index < parts.size && parts[index].length in 2..8 && isAsciiAlphanumeric(parts[index])) {
        output.add(parts[index].lowercase())
        index += 1
      }
      if (index <= start) return null
    }
    if (index < parts.size && parts[index].lowercase() == "x") {
      output.add("x")
      index += 1
      val start = index
      while (index < parts.size && parts[index].length in 1..8 && isAsciiAlphanumeric(parts[index])) {
        output.add(parts[index].lowercase())
        index += 1
      }
      if (index <= start) return null
    }
    if (index != parts.size) return null
    return output.joinToString("-")
  }

  fun isCanonical(value: String): Boolean = canonicalize(value) == value

  /**
   * The exact canonical requested tag followed by its primary language subtag
   * (`fr_ca` -> `["fr-CA", "fr"]`). Malformed requests yield an empty list.
   */
  fun requestedCandidates(requested: String): List<String> {
    val exact = canonicalize(requested.replace('_', '-')) ?: return emptyList()
    val result = mutableListOf(exact)
    val separator = exact.indexOf('-')
    if (separator >= 0) result.add(exact.substring(0, separator))
    return result
  }

  fun isJapanese(requested: String): Boolean = requestedCandidates(requested).lastOrNull() == "ja"

  /** Ordered, de-duplicated display candidates: exact, base, `defaultLocale`, `en`. */
  fun lookupCandidates(requested: String, defaultLocale: String?): List<String> {
    val result = requestedCandidates(requested).toMutableList()
    if (defaultLocale != null) result.add(defaultLocale)
    result.add("en")
    return result.distinct()
  }

  private fun isAsciiAlpha(value: String): Boolean =
    value.isNotEmpty() && value.all { it in 'A'..'Z' || it in 'a'..'z' }

  private fun isAsciiDigits(value: String): Boolean =
    value.isNotEmpty() && value.all { it in '0'..'9' }

  private fun isAsciiAlphanumeric(value: String): Boolean =
    value.isNotEmpty() && value.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }
}
