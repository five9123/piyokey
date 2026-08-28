import Foundation

public enum UserDeckValidator {
  private static let userDeckIDPattern = #"^user_[0-9a-f]{32}$"#
  private static let userItemIDPattern = #"^item_[0-9a-f]{32}$"#

  public static func validate(_ deck: Deck) -> [ContentValidationIssue] {
    DeckValidator.validate(deck) + validatePackageRules(deck)
  }

  static func validatePackageRules(_ deck: Deck) -> [ContentValidationIssue] {
    var issues: [ContentValidationIssue] = []

    appendMaximumLengthIssue(deck.name, maximum: 120, path: "name", into: &issues)
    appendIdentifierIssue(deck.author.id, path: "author.id", into: &issues)
    appendMaximumLengthIssue(
      deck.author.nickname,
      maximum: 40,
      path: "author.nickname",
      into: &issues
    )
    appendTagLengthIssues(deck.tags, path: "tags", into: &issues)
    if let localizations = deck.localizations {
      for (languageCode, localization) in localizations {
        let path = "localizations.\(languageCode)"
        appendMaximumLengthIssue(
          localization.name,
          maximum: 120,
          path: "\(path).name",
          into: &issues
        )
        appendMaximumLengthIssue(
          localization.authorNickname,
          maximum: 40,
          path: "\(path).author_nickname",
          into: &issues
        )
        appendTagLengthIssues(localization.tags, path: "\(path).tags", into: &issues)
      }
    }

    if deck.deckId.hasPrefix("official_") {
      issues.append(
        .init(
          code: "reserved_identifier",
          path: "deck_id",
          message: "official_ 접두사는 공식 콘텐츠에 예약되어 있습니다"
        )
      )
    }
    if deck.deckId.range(of: userDeckIDPattern, options: .regularExpression) == nil {
      issues.append(
        .init(
          code: "user_deck_identifier",
          path: "deck_id",
          message: "user_와 소문자 16진수 UUID 32자리 형식이어야 합니다"
        )
      )
    }
    if deck.author.id.hasPrefix("official_") {
      issues.append(
        .init(
          code: "reserved_identifier",
          path: "author.id",
          message: "official_ 접두사는 공식 콘텐츠에 예약되어 있습니다"
        )
      )
    }
    if deck.official {
      issues.append(
        .init(
          code: "user_deck_official",
          path: "official",
          message: "사용자 덱은 official=false여야 합니다"
        )
      )
    }
    if deck.items.count > PiyoDeckPackageLimits.maximumItemCount {
      issues.append(
        .init(
          code: "user_deck_item_limit",
          path: "items",
          message: "사용자 덱은 최대 \(PiyoDeckPackageLimits.maximumItemCount)개 항목을 지원합니다"
        )
      )
    }

    for (index, item) in deck.items.enumerated() {
      if item.id.hasPrefix("official_") {
        issues.append(
          .init(
            code: "reserved_identifier",
            path: "items[\(index)].id",
            message: "official_ 접두사는 공식 콘텐츠에 예약되어 있습니다"
          )
        )
      }
      if item.id.range(of: userItemIDPattern, options: .regularExpression) == nil {
        issues.append(
          .init(
            code: "user_item_identifier",
            path: "items[\(index)].id",
            message: "item_과 소문자 16진수 UUID 32자리 형식이어야 합니다"
          )
        )
      }
      appendMaximumLengthIssue(
        item.readingJa,
        maximum: 300,
        path: "items[\(index)].reading_ja",
        into: &issues
      )
      appendMaximumLengthIssue(
        item.meaningJa,
        maximum: 500,
        path: "items[\(index)].meaning_ja",
        into: &issues
      )
      if let localizations = item.localizations {
        for (languageCode, localization) in localizations {
          let path = "items[\(index)].localizations.\(languageCode)"
          appendMaximumLengthIssue(
            localization.reading,
            maximum: 300,
            path: "\(path).reading",
            into: &issues
          )
          appendMaximumLengthIssue(
            localization.meaning,
            maximum: 500,
            path: "\(path).meaning",
            into: &issues
          )
        }
      }
      if item.audio != nil {
        issues.append(
          .init(
            code: "user_deck_audio",
            path: "items[\(index)].audio",
            message: ".typedeck v1에서는 audio가 null이어야 합니다"
          )
        )
      }
    }
    return issues
  }

  private static func appendIdentifierIssue(
    _ value: String,
    path: String,
    into issues: inout [ContentValidationIssue]
  ) {
    if value.range(of: #"^[a-z0-9][a-z0-9_-]{2,63}$"#, options: .regularExpression) == nil {
      issues.append(
        .init(
          code: "identifier",
          path: path,
          message: "3...64자의 소문자 영숫자, 밑줄, 하이픈만 허용됩니다"
        )
      )
    }
  }

  private static func appendMaximumLengthIssue(
    _ value: String,
    maximum: Int,
    path: String,
    into issues: inout [ContentValidationIssue]
  ) {
    if value.count > maximum {
      issues.append(
        .init(
          code: "max_length",
          path: path,
          message: "최대 \(maximum)자여야 합니다"
        )
      )
    }
  }

  private static func appendTagLengthIssues(
    _ tags: [String],
    path: String,
    into issues: inout [ContentValidationIssue]
  ) {
    for (index, tag) in tags.enumerated() {
      appendMaximumLengthIssue(tag, maximum: 40, path: "\(path)[\(index)]", into: &issues)
    }
  }
}
