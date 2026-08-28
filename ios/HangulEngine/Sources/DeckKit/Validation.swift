import Foundation
import HangulEngine

public struct ContentValidationIssue: Equatable, Sendable, CustomStringConvertible {
  public let code: String
  public let path: String
  public let message: String

  public init(code: String, path: String, message: String) {
    self.code = code
    self.path = path
    self.message = message
  }

  public var description: String { "[\(code)] \(path): \(message)" }
}

public enum DeckValidator {
  public static func validate(_ deck: Deck) -> [ContentValidationIssue] {
    var issues: [ContentValidationIssue] = []
    validateIdentifier(deck.deckId, path: "deck_id", into: &issues)
    require(
      deck.version >= 1, code: "range", path: "version", message: "1 이상이어야 합니다", into: &issues)
    requireNonempty(deck.name, path: "name", into: &issues)
    requireNonempty(deck.author.id, path: "author.id", into: &issues)
    requireNonempty(deck.author.nickname, path: "author.nickname", into: &issues)
    require(
      (1...3).contains(deck.level), code: "range", path: "level", message: "1...3 범위여야 합니다",
      into: &issues)
    validateTags(deck.tags, path: "tags", into: &issues)
    validateMetadataLocalizations(
      deck.localizations,
      baseTagCount: deck.tags.count,
      path: "localizations",
      into: &issues
    )
    let requiredItemCodes = requiredItemLocalizationCodes(
      for: deck.localizations
    )
    require(
      deck.updatedAt >= deck.createdAt, code: "date_order", path: "updated_at",
      message: "created_at보다 빠를 수 없습니다", into: &issues)
    require(
      !deck.items.isEmpty, code: "min_items", path: "items", message: "항목이 하나 이상 필요합니다",
      into: &issues)

    var itemIDs = Set<String>()
    for (index, item) in deck.items.enumerated() {
      let path = "items[\(index)]"
      requireNonempty(item.id, path: "\(path).id", into: &issues)
      if !item.id.isEmpty, !itemIDs.insert(item.id).inserted {
        issues.append(.init(code: "duplicate", path: "\(path).id", message: "덱 안에서 중복된 항목 ID입니다"))
      }
      validateKorean(item.ko, path: "\(path).ko", into: &issues)
      requireNonempty(item.readingJa, path: "\(path).reading_ja", into: &issues)
      requireNonempty(item.meaningJa, path: "\(path).meaning_ja", into: &issues)
      validateItemLocalizations(item.localizations, path: "\(path).localizations", into: &issues)
      validateRequiredItemLocalizations(
        item.localizations,
        requiredLanguageCodes: requiredItemCodes,
        path: "\(path).localizations",
        into: &issues
      )
      if let audio = item.audio {
        requireNonempty(audio, path: "\(path).audio", into: &issues)
      }
    }
    return issues
  }
}

public enum CatalogValidator {
  public static func validate(_ catalog: Catalog) -> [ContentValidationIssue] {
    var issues: [ContentValidationIssue] = []
    require(
      catalog.catalogVersion >= 1, code: "range", path: "catalog_version", message: "1 이상이어야 합니다",
      into: &issues)
    require(
      !catalog.decks.isEmpty, code: "min_items", path: "decks", message: "덱이 하나 이상 필요합니다",
      into: &issues)

    var deckIDs = Set<String>()
    for (index, deck) in catalog.decks.enumerated() {
      let path = "decks[\(index)]"
      validateIdentifier(deck.deckId, path: "\(path).deck_id", into: &issues)
      if !deck.deckId.isEmpty, !deckIDs.insert(deck.deckId).inserted {
        issues.append(
          .init(code: "duplicate", path: "\(path).deck_id", message: "카탈로그에서 중복된 덱 ID입니다"))
      }
      require(
        deck.version >= 1, code: "range", path: "\(path).version", message: "1 이상이어야 합니다",
        into: &issues)
      requireNonempty(deck.name, path: "\(path).name", into: &issues)
      requireNonempty(deck.authorNickname, path: "\(path).author_nickname", into: &issues)
      require(
        (1...3).contains(deck.level), code: "range", path: "\(path).level",
        message: "1...3 범위여야 합니다", into: &issues)
      validateTags(deck.tags, path: "\(path).tags", into: &issues)
      validateMetadataLocalizations(
        deck.localizations,
        baseTagCount: deck.tags.count,
        path: "\(path).localizations",
        into: &issues
      )
      let requiredPreviewCodes = requiredItemLocalizationCodes(
        for: deck.localizations
      )
      require(
        deck.itemCount >= 1, code: "range", path: "\(path).item_count", message: "1 이상이어야 합니다",
        into: &issues)
      require(
        deck.sizeBytes >= 1, code: "range", path: "\(path).size_bytes", message: "1 이상이어야 합니다",
        into: &issues)
      require(
        deck.downloadsTotal >= 0, code: "range", path: "\(path).downloads_total",
        message: "0 이상이어야 합니다", into: &issues)
      require(
        deck.downloads7d >= 0, code: "range", path: "\(path).downloads_7d", message: "0 이상이어야 합니다",
        into: &issues)
      require(
        (1...10).contains(deck.previewItems.count), code: "preview_count",
        path: "\(path).preview_items", message: "1...10개여야 합니다", into: &issues)
      for (previewIndex, preview) in deck.previewItems.enumerated() {
        validateKorean(preview.ko, path: "\(path).preview_items[\(previewIndex)].ko", into: &issues)
        requireNonempty(
          preview.meaningJa, path: "\(path).preview_items[\(previewIndex)].meaning_ja",
          into: &issues)
        validatePreviewLocalizations(
          preview.localizations,
          path: "\(path).preview_items[\(previewIndex)].localizations",
          into: &issues
        )
        validateRequiredPreviewLocalizations(
          preview.localizations,
          requiredLanguageCodes: requiredPreviewCodes,
          path: "\(path).preview_items[\(previewIndex)].localizations",
          into: &issues
        )
      }
      let validFileURL =
        deck.fileUrl.hasPrefix("decks/") && deck.fileUrl.hasSuffix(".json")
        && !deck.fileUrl.contains("..")
      require(
        validFileURL, code: "file_url", path: "\(path).file_url",
        message: "decks/ 아래의 안전한 JSON 상대 경로여야 합니다", into: &issues)
    }

    var tagNames = Set<String>()
    for (index, tag) in catalog.tags.enumerated() {
      let path = "tags[\(index)]"
      requireNonempty(tag.tag, path: "\(path).tag", into: &issues)
      if !tag.tag.isEmpty, !tagNames.insert(tag.tag).inserted {
        issues.append(.init(code: "duplicate", path: "\(path).tag", message: "중복된 태그입니다"))
      }
      require(
        tag.deckCount >= 1, code: "range", path: "\(path).deck_count", message: "1 이상이어야 합니다",
        into: &issues)
      requireNonempty(tag.category, path: "\(path).category", into: &issues)
      validateTagLocalizations(tag.localizations, path: "\(path).localizations", into: &issues)
      let requiredTagCodes = Set(
        catalog.decks.flatMap { deck in
          guard deck.tags.contains(tag.tag) else { return [String]() }
          return deck.localizations.map { Array($0.keys) } ?? []
        }
      )
      validateRequiredTagLocalizations(
        tag.localizations,
        requiredLanguageCodes: requiredTagCodes,
        path: "\(path).localizations",
        into: &issues
      )
      let actualCount = catalog.decks.filter { $0.tags.contains(tag.tag) }.count
      require(
        tag.deckCount == actualCount, code: "tag_count", path: "\(path).deck_count",
        message: "실제 덱 수 \(actualCount)와 일치해야 합니다", into: &issues)
    }
    return issues
  }
}

public enum CatalogBundleValidator {
  public static func validate(catalog: Catalog, decks: [Deck]) -> [ContentValidationIssue] {
    var issues = CatalogValidator.validate(catalog)
    for deck in decks {
      issues.append(
        contentsOf: DeckValidator.validate(deck).map {
          .init(code: $0.code, path: "decks[\(deck.deckId)].\($0.path)", message: $0.message)
        })
    }

    let decksByID = Dictionary(grouping: decks, by: \Deck.deckId)
    for (index, entry) in catalog.decks.enumerated() {
      let path = "decks[\(index)]"
      guard let matches = decksByID[entry.deckId], matches.count == 1, let deck = matches.first
      else {
        issues.append(
          .init(code: "missing_deck", path: "\(path).file_url", message: "정확히 하나의 덱 파일과 대응해야 합니다"))
        continue
      }
      compare(entry.version == deck.version, field: "version", path: path, into: &issues)
      compare(entry.name == deck.name, field: "name", path: path, into: &issues)
      compare(
        entry.authorNickname == deck.author.nickname, field: "author_nickname", path: path,
        into: &issues)
      compare(entry.official == deck.official, field: "official", path: path, into: &issues)
      compare(entry.type == deck.type, field: "type", path: path, into: &issues)
      compare(entry.level == deck.level, field: "level", path: path, into: &issues)
      compare(entry.tags == deck.tags, field: "tags", path: path, into: &issues)
      compare(
        entry.localizations == deck.localizations,
        field: "localizations",
        path: path,
        into: &issues
      )
      compare(entry.itemCount == deck.items.count, field: "item_count", path: path, into: &issues)
      let expectedPreview = deck.items.prefix(10).map { item in
        let localizations = item.localizations?.mapValues {
          CatalogPreviewItemLocalization(meaning: $0.meaning)
        }
        return CatalogPreviewItem(
          ko: item.ko,
          meaningJa: item.meaningJa,
          localizations: localizations
        )
      }
      compare(
        entry.previewItems == expectedPreview, field: "preview_items", path: path, into: &issues)
    }
    let catalogIDs = Set(catalog.decks.map(\.deckId))
    for deckID in Set(decks.map(\.deckId)).subtracting(catalogIDs) {
      issues.append(
        .init(code: "unindexed_deck", path: "decks[\(deckID)]", message: "카탈로그에 없는 덱 파일입니다"))
    }
    return issues
  }
}

private let supportedContentLocalizationCodes: Set<String> = ["en", "ko", "es", "de", "fr"]

/// Korean metadata is localized for Korean-language devices while its learning
/// clues intentionally reuse the complete English meaning and romanization.
/// Every other published metadata locale requires complete clues in that language.
private func requiredItemLocalizationCodes(
  for metadata: [String: DeckMetadataLocalization]?
) -> Set<String> {
  Set(metadata?.keys.filter { $0 != "ko" } ?? [])
}

private func validateMetadataLocalizations(
  _ localizations: [String: DeckMetadataLocalization]?,
  baseTagCount: Int,
  path: String,
  into issues: inout [ContentValidationIssue]
) {
  guard let localizations else { return }
  validateLocalizationKeys(localizations.keys, path: path, into: &issues)
  for (languageCode, localization) in localizations {
    let localizationPath = "\(path).\(languageCode)"
    requireNonempty(localization.name, path: "\(localizationPath).name", into: &issues)
    requireNonempty(
      localization.authorNickname,
      path: "\(localizationPath).author_nickname",
      into: &issues
    )
    validateTags(localization.tags, path: "\(localizationPath).tags", into: &issues)
    require(
      localization.tags.count == baseTagCount,
      code: "localized_tag_count",
      path: "\(localizationPath).tags",
      message: "원본 태그 수 \(baseTagCount)개와 일치해야 합니다",
      into: &issues
    )
  }
}

private func validateItemLocalizations(
  _ localizations: [String: DeckItemLocalization]?,
  path: String,
  into issues: inout [ContentValidationIssue]
) {
  guard let localizations else { return }
  validateLocalizationKeys(localizations.keys, path: path, into: &issues)
  for (languageCode, localization) in localizations {
    requireNonempty(localization.meaning, path: "\(path).\(languageCode).meaning", into: &issues)
    requireNonempty(localization.reading, path: "\(path).\(languageCode).reading", into: &issues)
  }
}

private func validateRequiredItemLocalizations(
  _ localizations: [String: DeckItemLocalization]?,
  requiredLanguageCodes: Set<String>,
  path: String,
  into issues: inout [ContentValidationIssue]
) {
  for languageCode in requiredLanguageCodes where localizations?[languageCode] == nil {
    issues.append(
      .init(
        code: "missing_localization",
        path: "\(path).\(languageCode)",
        message: "공개된 덱 메타데이터 로케일의 뜻과 읽기가 필요합니다"
      )
    )
  }
}

private func validatePreviewLocalizations(
  _ localizations: [String: CatalogPreviewItemLocalization]?,
  path: String,
  into issues: inout [ContentValidationIssue]
) {
  guard let localizations else { return }
  validateLocalizationKeys(localizations.keys, path: path, into: &issues)
  for (languageCode, localization) in localizations {
    requireNonempty(localization.meaning, path: "\(path).\(languageCode).meaning", into: &issues)
  }
}

private func validateRequiredPreviewLocalizations(
  _ localizations: [String: CatalogPreviewItemLocalization]?,
  requiredLanguageCodes: Set<String>,
  path: String,
  into issues: inout [ContentValidationIssue]
) {
  for languageCode in requiredLanguageCodes where localizations?[languageCode] == nil {
    issues.append(
      .init(
        code: "missing_localization",
        path: "\(path).\(languageCode)",
        message: "공개된 카탈로그 로케일의 미리보기 뜻이 필요합니다"
      )
    )
  }
}

private func validateTagLocalizations(
  _ localizations: [String: String]?,
  path: String,
  into issues: inout [ContentValidationIssue]
) {
  guard let localizations else { return }
  validateLocalizationKeys(localizations.keys, path: path, into: &issues)
  for (languageCode, value) in localizations {
    requireNonempty(value, path: "\(path).\(languageCode)", into: &issues)
  }
}

private func validateRequiredTagLocalizations(
  _ localizations: [String: String]?,
  requiredLanguageCodes: Set<String>,
  path: String,
  into issues: inout [ContentValidationIssue]
) {
  for languageCode in requiredLanguageCodes where localizations?[languageCode] == nil {
    issues.append(
      .init(
        code: "missing_localization",
        path: "\(path).\(languageCode)",
        message: "공개된 덱 로케일의 태그 표시명이 필요합니다"
      )
    )
  }
}

private func validateLocalizationKeys<Keys: Collection>(
  _ keys: Keys,
  path: String,
  into issues: inout [ContentValidationIssue]
) where Keys.Element == String {
  for languageCode in keys where !supportedContentLocalizationCodes.contains(languageCode) {
    issues.append(
      .init(
        code: "unsupported_locale",
        path: "\(path).\(languageCode)",
        message: "지원하지 않는 콘텐츠 로케일입니다"
      )
    )
  }
}

private func validateIdentifier(
  _ value: String, path: String, into issues: inout [ContentValidationIssue]
) {
  let range = value.range(of: "^[a-z0-9][a-z0-9_-]{2,63}$", options: .regularExpression)
  require(
    range != nil, code: "identifier", path: path, message: "3...64자의 소문자 영숫자, 밑줄, 하이픈만 허용됩니다",
    into: &issues)
}

private func validateTags(
  _ tags: [String], path: String, into issues: inout [ContentValidationIssue]
) {
  require(
    (1...8).contains(tags.count), code: "tag_count", path: path, message: "1...8개여야 합니다",
    into: &issues)
  require(
    Set(tags).count == tags.count, code: "duplicate", path: path, message: "중복 태그를 허용하지 않습니다",
    into: &issues)
  for (index, tag) in tags.enumerated() {
    requireNonempty(tag, path: "\(path)[\(index)]", into: &issues)
  }
}

private func validateKorean(
  _ value: String, path: String, into issues: inout [ContentValidationIssue]
) {
  requireNonempty(value, path: path, into: &issues)
  guard !value.isEmpty else { return }
  require(
    value.count <= 10, code: "max_target_length", path: path,
    message: "공백을 포함해 10자 이하여야 합니다", into: &issues)
  do {
    _ = try JamoDecomposer.keySequence(for: value)
  } catch {
    issues.append(
      .init(code: "undecomposable_ko", path: path, message: "조합 엔진이 분해할 수 없습니다: \(error)"))
    return
  }
  require(
    JamoDecomposer.containsHangul(in: value), code: "missing_hangul", path: path,
    message: "한글 음절 또는 자모가 하나 이상 필요합니다", into: &issues)
}

private func requireNonempty(
  _ value: String, path: String, into issues: inout [ContentValidationIssue]
) {
  require(
    !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, code: "required", path: path,
    message: "빈 문자열일 수 없습니다", into: &issues)
}

private func require(
  _ condition: @autoclosure () -> Bool,
  code: String,
  path: String,
  message: String,
  into issues: inout [ContentValidationIssue]
) {
  if !condition() {
    issues.append(.init(code: code, path: path, message: message))
  }
}

private func compare(
  _ condition: Bool, field: String, path: String, into issues: inout [ContentValidationIssue]
) {
  if !condition {
    issues.append(
      .init(code: "metadata_mismatch", path: "\(path).\(field)", message: "덱 파일과 카탈로그 값이 일치해야 합니다"))
  }
}
