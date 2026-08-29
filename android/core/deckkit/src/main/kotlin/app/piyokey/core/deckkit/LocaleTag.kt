package app.piyokey.core.deckkit

/** Structural BCP 47 validation and canonical casing used at DeckKit boundaries. */
object LocaleTag {
  fun canonicalize(value: String): String? {
    if (value.length !in 2..63 || '_' in value) return null
    val parts = value.split('-')
    if (parts.any(String::isEmpty)) return null
    if (parts.first().equals("x", ignoreCase = true)) {
      if (parts.size < 2 || parts.drop(1).any { it.length !in 1..8 || !it.isAsciiAlphanumeric() }) return null
      return parts.joinToString("-") { it.lowercase() }
    }
    val first = parts.first()
    if (first.length !in 2..8 || !first.isAsciiAlpha()) return null
    val output = mutableListOf(first.lowercase())
    var index = 1
    if (first.length in 2..3) {
      var extlangCount = 0
      while (index < parts.size && extlangCount < 3 && parts[index].length == 3 && parts[index].isAsciiAlpha()) {
        output += parts[index].lowercase()
        index += 1
        extlangCount += 1
      }
    }
    if (index < parts.size && parts[index].length == 4 && parts[index].isAsciiAlpha()) {
      output += parts[index].lowercase().replaceFirstChar(Char::uppercaseChar)
      index += 1
    }
    if (index < parts.size &&
      ((parts[index].length == 2 && parts[index].isAsciiAlpha()) ||
        (parts[index].length == 3 && parts[index].all(Char::isDigit)))) {
      output += if (parts[index].isAsciiAlpha()) parts[index].uppercase() else parts[index]
      index += 1
    }
    val variants = mutableSetOf<String>()
    while (index < parts.size) {
      val part = parts[index]
      val variant = (part.length in 5..8 && part.isAsciiAlphanumeric()) ||
        (part.length == 4 && part.first().isDigit() && part.isAsciiAlphanumeric())
      if (!variant) break
      val normalized = part.lowercase()
      if (!variants.add(normalized)) return null
      output += normalized
      index += 1
    }
    val singletons = mutableSetOf<String>()
    while (index < parts.size && parts[index].length == 1 && !parts[index].equals("x", true)) {
      val singleton = parts[index].lowercase()
      if (!singleton.isAsciiAlphanumeric() || !singletons.add(singleton)) return null
      output += singleton
      index += 1
      val start = index
      while (index < parts.size && parts[index].length in 2..8 && parts[index].isAsciiAlphanumeric()) {
        output += parts[index].lowercase()
        index += 1
      }
      if (index == start) return null
    }
    if (index < parts.size && parts[index].equals("x", true)) {
      output += "x"
      index += 1
      val start = index
      while (index < parts.size && parts[index].length in 1..8 && parts[index].isAsciiAlphanumeric()) {
        output += parts[index].lowercase()
        index += 1
      }
      if (index == start) return null
    }
    if (index != parts.size) return null
    return output.joinToString("-")
  }

  fun isCanonical(value: String): Boolean = canonicalize(value) == value

  fun lookupCandidates(requested: String, defaultLocale: String?): List<String> = buildList {
    canonicalize(requested.replace('_', '-'))?.let { exact ->
      add(exact)
      exact.substringBefore('-').takeIf { it != exact }?.let(::add)
    }
    defaultLocale?.let(::add)
    add("en")
  }.distinct()

  private fun String.isAsciiAlpha(): Boolean = isNotEmpty() && all { it in 'A'..'Z' || it in 'a'..'z' }
  private fun String.isAsciiAlphanumeric(): Boolean =
    isNotEmpty() && all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }
}
