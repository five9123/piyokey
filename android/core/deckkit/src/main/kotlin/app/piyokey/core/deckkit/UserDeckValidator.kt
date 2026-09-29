package app.piyokey.core.deckkit

/** Stricter `.typedeck` user-document rules (SPEC §4) plus authoring length caps. */
object UserDeckValidator {
  private val userDeckIdPattern = Regex("^user_[0-9a-f]{32}$")
  private val userItemIdPattern = Regex("^item_[0-9a-f]{32}$")

  /** [DeckValidator.validate] followed by [validatePackageRules]. */
  fun validate(deck: Deck): List<ContentValidationIssue> =
    DeckValidator.validate(deck) + validatePackageRules(deck)

  /** Only the user-document rules (IDs, reserved prefixes, caps, `official`, `audio`). */
  fun validatePackageRules(deck: Deck): List<ContentValidationIssue> {
    val issues = mutableListOf<ContentValidationIssue>()

    appendMaximumLengthIssue(deck.name, MAX_NAME_LENGTH, "name", issues)
    appendIdentifierIssue(deck.author.id, "author.id", issues)
    appendMaximumLengthIssue(deck.author.nickname, MAX_NICKNAME_LENGTH, "author.nickname", issues)
    appendTagLengthIssues(deck.tags, "tags", issues)
    deck.localizations?.forEach { (languageCode, localization) ->
      val path = "localizations.$languageCode"
      appendMaximumLengthIssue(localization.name, MAX_NAME_LENGTH, "$path.name", issues)
      appendMaximumLengthIssue(
        localization.authorNickname, MAX_NICKNAME_LENGTH, "$path.author_nickname", issues,
      )
      appendTagLengthIssues(localization.tags, "$path.tags", issues)
    }

    if (deck.deckId.startsWith("official_")) {
      issues.add(
        ContentValidationIssue(
          "reserved_identifier", "deck_id", "official_ 접두사는 공식 콘텐츠에 예약되어 있습니다",
        ),
      )
    }
    if (!userDeckIdPattern.containsMatchIn(deck.deckId)) {
      issues.add(
        ContentValidationIssue(
          "user_deck_identifier", "deck_id", "user_와 소문자 16진수 UUID 32자리 형식이어야 합니다",
        ),
      )
    }
    if (deck.author.id.startsWith("official_")) {
      issues.add(
        ContentValidationIssue(
          "reserved_identifier", "author.id", "official_ 접두사는 공식 콘텐츠에 예약되어 있습니다",
        ),
      )
    }
    if (deck.official) {
      issues.add(
        ContentValidationIssue("user_deck_official", "official", "사용자 덱은 official=false여야 합니다"),
      )
    }
    if (deck.items.size > PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT) {
      issues.add(
        ContentValidationIssue(
          "user_deck_item_limit", "items",
          "사용자 덱은 최대 ${PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT}개 항목을 지원합니다",
        ),
      )
    }

    for ((index, item) in deck.items.withIndex()) {
      if (item.id.startsWith("official_")) {
        issues.add(
          ContentValidationIssue(
            "reserved_identifier", "items[$index].id",
            "official_ 접두사는 공식 콘텐츠에 예약되어 있습니다",
          ),
        )
      }
      if (!userItemIdPattern.containsMatchIn(item.id)) {
        issues.add(
          ContentValidationIssue(
            "user_item_identifier", "items[$index].id",
            "item_과 소문자 16진수 UUID 32자리 형식이어야 합니다",
          ),
        )
      }
      appendMaximumLengthIssue(
        item.readingJa, MAX_READING_LENGTH, "items[$index].reading_ja", issues,
      )
      appendMaximumLengthIssue(
        item.meaningJa, MAX_MEANING_LENGTH, "items[$index].meaning_ja", issues,
      )
      item.localizations?.forEach { (languageCode, localization) ->
        val path = "items[$index].localizations.$languageCode"
        appendMaximumLengthIssue(localization.reading, MAX_READING_LENGTH, "$path.reading", issues)
        appendMaximumLengthIssue(localization.meaning, MAX_MEANING_LENGTH, "$path.meaning", issues)
      }
      if (item.audio != null) {
        issues.add(
          ContentValidationIssue(
            "user_deck_audio", "items[$index].audio", ".typedeck v1에서는 audio가 null이어야 합니다",
          ),
        )
      }
    }
    return issues
  }

  /** Authoring caps, counted in grapheme clusters (Swift `String.count`). */
  const val MAX_NAME_LENGTH = 120
  const val MAX_NICKNAME_LENGTH = 40
  const val MAX_TAG_LENGTH = 40
  const val MAX_READING_LENGTH = 300
  const val MAX_MEANING_LENGTH = 500

  private fun appendIdentifierIssue(
    value: String,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    if (!IDENTIFIER_PATTERN.containsMatchIn(value)) {
      issues.add(
        ContentValidationIssue("identifier", path, "3...64자의 소문자 영숫자, 밑줄, 하이픈만 허용됩니다"),
      )
    }
  }

  private fun appendMaximumLengthIssue(
    value: String,
    maximum: Int,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    if (DeckKitText.graphemeCount(value) > maximum) {
      issues.add(ContentValidationIssue("max_length", path, "최대 ${maximum}자여야 합니다"))
    }
  }

  private fun appendTagLengthIssues(
    tags: List<String>,
    path: String,
    issues: MutableList<ContentValidationIssue>,
  ) {
    for ((index, tag) in tags.withIndex()) {
      appendMaximumLengthIssue(tag, MAX_TAG_LENGTH, "$path[$index]", issues)
    }
  }
}
