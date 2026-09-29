package app.piyokey.core.deckkit

/** One semantic or schema problem. [code] is stable; [message] is Korean developer text. */
data class ContentValidationIssue(
  val code: String,
  val path: String,
  val message: String,
) {
  override fun toString(): String = "[$code] $path: $message"
}

private typealias Issues = MutableList<ContentValidationIssue>

object DeckValidator {
  /**
   * Semantic deck validation. [schemaVersion] `null` validates in-memory/catalog content; a
   * deck with `defaultLocale` is then treated as schema v2. Package readers pass the manifest's
   * `deck_schema_version` explicitly.
   */
  fun validate(deck: Deck, schemaVersion: Int? = null): List<ContentValidationIssue> {
    val issues = mutableListOf<ContentValidationIssue>()
    val resolvedSchemaVersion: Int? = schemaVersion ?: if (deck.defaultLocale == null) null else 2
    validateIdentifier(deck.deckId, "deck_id", issues)
    require(deck.version >= 1, "range", "version", "1 이상이어야 합니다", issues)
    requireNonempty(deck.name, "name", issues)
    requireNonempty(deck.author.id, "author.id", issues)
    requireNonempty(deck.author.nickname, "author.nickname", issues)
    require(deck.level in 1..3, "range", "level", "1...3 범위여야 합니다", issues)
    validateTags(deck.tags, "tags", issues)
    validateMetadataLocalizations(deck.localizations, deck.tags.size, "localizations", issues)
    val declaredCodes: Set<String> = deck.localizations?.keys ?: emptySet()
    if (resolvedSchemaVersion == 1) {
      require(
        deck.defaultLocale == null, "unexpected_default_locale", "default_locale",
        "deck schema v1에는 default_locale이 없습니다", issues,
      )
      for (code in declaredCodes) {
        if (code !in setOf("en", "ko")) {
          issues.add(
            ContentValidationIssue(
              "unsupported_locale", "localizations.$code", "deck schema v1은 en/ko만 지원합니다",
            ),
          )
        }
      }
    } else if (resolvedSchemaVersion == 2) {
      val defaultLocale = deck.defaultLocale
      if (defaultLocale != null) {
        require(
          LocaleTag.isCanonical(defaultLocale), "invalid_locale", "default_locale",
          "canonical BCP 47 태그여야 합니다", issues,
        )
        require(
          defaultLocale in declaredCodes, "missing_default_locale", "default_locale",
          "localizations에 선언된 키여야 합니다", issues,
        )
      } else {
        issues.add(
          ContentValidationIssue(
            "missing_default_locale", "default_locale", "deck schema v2 필수 필드입니다",
          ),
        )
      }
      require(
        declaredCodes.isNotEmpty(), "missing_localization", "localizations",
        "콘텐츠 로케일이 하나 이상 필요합니다", issues,
      )
    }
    val requiredItemCodes =
      if (resolvedSchemaVersion == 2) declaredCodes
      else requiredItemLocalizationCodes(deck.localizations)
    require(
      deck.updatedAt >= deck.createdAt, "date_order", "updated_at",
      "created_at보다 빠를 수 없습니다", issues,
    )
    require(deck.items.isNotEmpty(), "min_items", "items", "항목이 하나 이상 필요합니다", issues)

    val itemIds = mutableSetOf<String>()
    for ((index, item) in deck.items.withIndex()) {
      val path = "items[$index]"
      requireNonempty(item.id, "$path.id", issues)
      if (item.id.isNotEmpty() && !itemIds.add(DeckKitText.canonicalKey(item.id))) {
        issues.add(ContentValidationIssue("duplicate", "$path.id", "덱 안에서 중복된 항목 ID입니다"))
      }
      validateKorean(item.ko, "$path.ko", issues)
      requireNonempty(item.readingJa, "$path.reading_ja", issues)
      requireNonempty(item.meaningJa, "$path.meaning_ja", issues)
      validateItemLocalizations(item.localizations, "$path.localizations", issues)
      validateRequiredItemLocalizations(
        item.localizations, requiredItemCodes, "$path.localizations", issues,
      )
      if (resolvedSchemaVersion == 2) {
        for (code in item.localizations?.keys ?: emptySet()) {
          if (code !in declaredCodes) {
            issues.add(
              ContentValidationIssue(
                "undeclared_localization", "$path.localizations.$code",
                "덱 메타데이터에 선언되지 않은 콘텐츠 로케일입니다",
              ),
            )
          }
        }
      }
      item.audio?.let { requireNonempty(it, "$path.audio", issues) }
    }
    return issues
  }
}

object CatalogValidator {
  fun validate(catalog: Catalog): List<ContentValidationIssue> {
    val issues = mutableListOf<ContentValidationIssue>()
    require(
      catalog.catalogVersion >= 1, "range", "catalog_version", "1 이상이어야 합니다", issues,
    )
    require(catalog.decks.isNotEmpty(), "min_items", "decks", "덱이 하나 이상 필요합니다", issues)

    val deckIds = mutableSetOf<String>()
    for ((index, deck) in catalog.decks.withIndex()) {
      val path = "decks[$index]"
      validateIdentifier(deck.deckId, "$path.deck_id", issues)
      if (deck.deckId.isNotEmpty() && !deckIds.add(DeckKitText.canonicalKey(deck.deckId))) {
        issues.add(
          ContentValidationIssue("duplicate", "$path.deck_id", "카탈로그에서 중복된 덱 ID입니다"),
        )
      }
      require(deck.version >= 1, "range", "$path.version", "1 이상이어야 합니다", issues)
      requireNonempty(deck.name, "$path.name", issues)
      requireNonempty(deck.authorNickname, "$path.author_nickname", issues)
      require(deck.level in 1..3, "range", "$path.level", "1...3 범위여야 합니다", issues)
      validateTags(deck.tags, "$path.tags", issues)
      validateMetadataLocalizations(
        deck.localizations, deck.tags.size, "$path.localizations", issues,
      )
      val requiredPreviewCodes = requiredItemLocalizationCodes(deck.localizations)
      require(deck.itemCount >= 1, "range", "$path.item_count", "1 이상이어야 합니다", issues)
      require(deck.sizeBytes >= 1, "range", "$path.size_bytes", "1 이상이어야 합니다", issues)
      require(
        deck.downloadsTotal >= 0, "range", "$path.downloads_total", "0 이상이어야 합니다", issues,
      )
      require(deck.downloads7d >= 0, "range", "$path.downloads_7d", "0 이상이어야 합니다", issues)
      require(
        deck.previewItems.size in 1..10, "preview_count", "$path.preview_items",
        "1...10개여야 합니다", issues,
      )
      for ((previewIndex, preview) in deck.previewItems.withIndex()) {
        val previewPath = "$path.preview_items[$previewIndex]"
        validateKorean(preview.ko, "$previewPath.ko", issues)
        requireNonempty(preview.meaningJa, "$previewPath.meaning_ja", issues)
        validatePreviewLocalizations(preview.localizations, "$previewPath.localizations", issues)
        validateRequiredPreviewLocalizations(
          preview.localizations, requiredPreviewCodes, "$previewPath.localizations", issues,
        )
      }
      val validFileUrl =
        deck.fileUrl.startsWith("decks/") && deck.fileUrl.endsWith(".json") &&
          !deck.fileUrl.contains("..")
      require(
        validFileUrl, "file_url", "$path.file_url",
        "decks/ 아래의 안전한 JSON 상대 경로여야 합니다", issues,
      )
    }

    val tagNames = mutableSetOf<String>()
    for ((index, tag) in catalog.tags.withIndex()) {
      val path = "tags[$index]"
      requireNonempty(tag.tag, "$path.tag", issues)
      if (tag.tag.isNotEmpty() && !tagNames.add(DeckKitText.canonicalKey(tag.tag))) {
        issues.add(ContentValidationIssue("duplicate", "$path.tag", "중복된 태그입니다"))
      }
      require(tag.deckCount >= 1, "range", "$path.deck_count", "1 이상이어야 합니다", issues)
      requireNonempty(tag.category, "$path.category", issues)
      validateTagLocalizations(tag.localizations, "$path.localizations", issues)
      val requiredTagCodes = catalog.decks
        .filter { tag.tag in it.tags }
        .flatMap { it.localizations?.keys ?: emptySet() }
        .toSet()
      validateRequiredTagLocalizations(
        tag.localizations, requiredTagCodes, "$path.localizations", issues,
      )
      val actualCount = catalog.decks.count { tag.tag in it.tags }
      require(
        tag.deckCount == actualCount, "tag_count", "$path.deck_count",
        "실제 덱 수 ${actualCount}와 일치해야 합니다", issues,
      )
    }
    return issues
  }
}

object CatalogBundleValidator {
  /** Catalog + every referenced deck: per-deck semantics and catalog/deck metadata agreement. */
  fun validate(catalog: Catalog, decks: List<Deck>): List<ContentValidationIssue> {
    val issues = CatalogValidator.validate(catalog).toMutableList()
    for (deck in decks) {
      DeckValidator.validate(deck).mapTo(issues) {
        ContentValidationIssue(it.code, "decks[${deck.deckId}].${it.path}", it.message)
      }
    }

    val decksById = decks.groupBy { it.deckId }
    for ((index, entry) in catalog.decks.withIndex()) {
      val path = "decks[$index]"
      val matches = decksById[entry.deckId]
      if (matches == null || matches.size != 1) {
        issues.add(
          ContentValidationIssue(
            "missing_deck", "$path.file_url", "정확히 하나의 덱 파일과 대응해야 합니다",
          ),
        )
        continue
      }
      val deck = matches.first()
      compare(entry.version == deck.version, "version", path, issues)
      compare(entry.name == deck.name, "name", path, issues)
      compare(entry.authorNickname == deck.author.nickname, "author_nickname", path, issues)
      compare(entry.official == deck.official, "official", path, issues)
      compare(entry.type == deck.type, "type", path, issues)
      compare(entry.level == deck.level, "level", path, issues)
      compare(entry.tags == deck.tags, "tags", path, issues)
      compare(entry.localizations == deck.localizations, "localizations", path, issues)
      compare(entry.itemCount == deck.items.size, "item_count", path, issues)
      val expectedPreview = deck.items.take(10).map { item ->
        CatalogPreviewItem(
          ko = item.ko,
          meaningJa = item.meaningJa,
          localizations = item.localizations?.mapValues {
            CatalogPreviewItemLocalization(it.value.meaning)
          },
        )
      }
      compare(entry.previewItems == expectedPreview, "preview_items", path, issues)
    }
    val catalogIds = catalog.decks.map { it.deckId }.toSet()
    for (deckId in decks.map { it.deckId }.toSet() - catalogIds) {
      issues.add(
        ContentValidationIssue("unindexed_deck", "decks[$deckId]", "카탈로그에 없는 덱 파일입니다"),
      )
    }
    return issues
  }
}

/**
 * Korean metadata is localized for Korean-language devices while its learning clues
 * intentionally reuse the complete English meaning and romanization. Every other published
 * metadata locale requires complete clues in that language.
 */
private fun requiredItemLocalizationCodes(
  metadata: Map<String, DeckMetadataLocalization>?,
): Set<String> = metadata?.keys?.filter { it != "ko" }?.toSet() ?: emptySet()

private fun validateMetadataLocalizations(
  localizations: Map<String, DeckMetadataLocalization>?,
  baseTagCount: Int,
  path: String,
  issues: Issues,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path, issues)
  for ((languageCode, localization) in localizations) {
    val localizationPath = "$path.$languageCode"
    requireNonempty(localization.name, "$localizationPath.name", issues)
    requireNonempty(localization.authorNickname, "$localizationPath.author_nickname", issues)
    validateTags(localization.tags, "$localizationPath.tags", issues)
    require(
      localization.tags.size == baseTagCount, "localized_tag_count", "$localizationPath.tags",
      "원본 태그 수 ${baseTagCount}개와 일치해야 합니다", issues,
    )
  }
}

private fun validateItemLocalizations(
  localizations: Map<String, DeckItemLocalization>?,
  path: String,
  issues: Issues,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path, issues)
  for ((languageCode, localization) in localizations) {
    requireNonempty(localization.meaning, "$path.$languageCode.meaning", issues)
    requireNonempty(localization.reading, "$path.$languageCode.reading", issues)
  }
}

private fun validateRequiredItemLocalizations(
  localizations: Map<String, DeckItemLocalization>?,
  requiredLanguageCodes: Set<String>,
  path: String,
  issues: Issues,
) {
  for (languageCode in requiredLanguageCodes) {
    if (localizations?.get(languageCode) == null) {
      issues.add(
        ContentValidationIssue(
          "missing_localization", "$path.$languageCode",
          "공개된 덱 메타데이터 로케일의 뜻과 읽기가 필요합니다",
        ),
      )
    }
  }
}

private fun validatePreviewLocalizations(
  localizations: Map<String, CatalogPreviewItemLocalization>?,
  path: String,
  issues: Issues,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path, issues)
  for ((languageCode, localization) in localizations) {
    requireNonempty(localization.meaning, "$path.$languageCode.meaning", issues)
  }
}

private fun validateRequiredPreviewLocalizations(
  localizations: Map<String, CatalogPreviewItemLocalization>?,
  requiredLanguageCodes: Set<String>,
  path: String,
  issues: Issues,
) {
  for (languageCode in requiredLanguageCodes) {
    if (localizations?.get(languageCode) == null) {
      issues.add(
        ContentValidationIssue(
          "missing_localization", "$path.$languageCode",
          "공개된 카탈로그 로케일의 미리보기 뜻이 필요합니다",
        ),
      )
    }
  }
}

private fun validateTagLocalizations(
  localizations: Map<String, String>?,
  path: String,
  issues: Issues,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path, issues)
  for ((languageCode, value) in localizations) {
    requireNonempty(value, "$path.$languageCode", issues)
  }
}

private fun validateRequiredTagLocalizations(
  localizations: Map<String, String>?,
  requiredLanguageCodes: Set<String>,
  path: String,
  issues: Issues,
) {
  for (languageCode in requiredLanguageCodes) {
    if (localizations?.get(languageCode) == null) {
      issues.add(
        ContentValidationIssue(
          "missing_localization", "$path.$languageCode",
          "공개된 덱 로케일의 태그 표시명이 필요합니다",
        ),
      )
    }
  }
}

private fun validateLocalizationKeys(keys: Collection<String>, path: String, issues: Issues) {
  for (languageCode in keys) {
    if (LocaleTag.isCanonical(languageCode)) continue
    val canonical = LocaleTag.canonicalize(languageCode)
    issues.add(
      ContentValidationIssue(
        if (canonical == null) "malformed_locale" else "noncanonical_locale",
        "$path.$languageCode",
        canonical?.let { "canonical BCP 47 태그 ${it}를 사용해야 합니다" }
          ?: "올바른 BCP 47 언어 태그여야 합니다",
      ),
    )
  }
}

internal val IDENTIFIER_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{2,63}$")

private fun validateIdentifier(value: String, path: String, issues: Issues) {
  require(
    IDENTIFIER_PATTERN.containsMatchIn(value), "identifier", path,
    "3...64자의 소문자 영숫자, 밑줄, 하이픈만 허용됩니다", issues,
  )
}

private fun validateTags(tags: List<String>, path: String, issues: Issues) {
  require(tags.size in 1..8, "tag_count", path, "1...8개여야 합니다", issues)
  require(
    tags.map(DeckKitText::canonicalKey).toSet().size == tags.size, "duplicate", path,
    "중복 태그를 허용하지 않습니다", issues,
  )
  for ((index, tag) in tags.withIndex()) requireNonempty(tag, "$path[$index]", issues)
}

private fun validateKorean(value: String, path: String, issues: Issues) {
  requireNonempty(value, path, issues)
  if (value.isEmpty()) return
  require(
    DeckKitText.graphemeCount(value) <= 10, "max_target_length", path,
    "공백을 포함해 10자 이하여야 합니다", issues,
  )
  val error = DeckKitHangul.decompositionError(value)
  if (error != null) {
    issues.add(
      ContentValidationIssue("undecomposable_ko", path, "조합 엔진이 분해할 수 없습니다: $error"),
    )
    return
  }
  require(
    DeckKitHangul.containsHangul(value), "missing_hangul", path,
    "한글 음절 또는 자모가 하나 이상 필요합니다", issues,
  )
}

private fun requireNonempty(value: String, path: String, issues: Issues) {
  require(!DeckKitText.isBlank(value), "required", path, "빈 문자열일 수 없습니다", issues)
}

private fun require(
  condition: Boolean,
  code: String,
  path: String,
  message: String,
  issues: Issues,
) {
  if (!condition) issues.add(ContentValidationIssue(code, path, message))
}

private fun compare(condition: Boolean, field: String, path: String, issues: Issues) {
  if (!condition) {
    issues.add(
      ContentValidationIssue(
        "metadata_mismatch", "$path.$field", "덱 파일과 카탈로그 값이 일치해야 합니다",
      ),
    )
  }
}
