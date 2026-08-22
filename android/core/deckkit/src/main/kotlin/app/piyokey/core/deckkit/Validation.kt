package app.piyokey.core.deckkit

import app.piyokey.core.hangul.JamoDecomposer

data class ContentValidationIssue(
  val code: String,
  val path: String,
  val message: String,
) {
  override fun toString(): String = "[$code] $path: $message"
}

object DeckValidator {
  fun validate(deck: Deck): List<ContentValidationIssue> = buildList {
    validateIdentifier(deck.deckId, "deck_id")
    require(deck.version >= 1, "range", "version", "1 이상이어야 합니다")
    requireNonempty(deck.name, "name")
    requireNonempty(deck.author.id, "author.id")
    requireNonempty(deck.author.nickname, "author.nickname")
    require(deck.level in 1..3, "range", "level", "1...3 범위여야 합니다")
    validateTags(deck.tags, "tags")
    validateMetadataLocalizations(deck.localizations, deck.tags.size, "localizations")
    val requiredItemCodes = requiredItemLocalizationCodes(deck.localizations)
    require(
      !deck.updatedAt.isBefore(deck.createdAt),
      "date_order",
      "updated_at",
      "created_at보다 빠를 수 없습니다",
    )
    require(deck.items.isNotEmpty(), "min_items", "items", "항목이 하나 이상 필요합니다")

    val itemIds = mutableSetOf<String>()
    deck.items.forEachIndexed { index, item ->
      val path = "items[$index]"
      requireNonempty(item.id, "$path.id")
      if (item.id.isNotEmpty() && !itemIds.add(item.id)) {
        add(ContentValidationIssue("duplicate", "$path.id", "덱 안에서 중복된 항목 ID입니다"))
      }
      validateKorean(item.ko, "$path.ko")
      requireNonempty(item.readingJa, "$path.reading_ja")
      requireNonempty(item.meaningJa, "$path.meaning_ja")
      validateItemLocalizations(item.localizations, "$path.localizations")
      validateRequiredItemLocalizations(
        item.localizations,
        requiredItemCodes,
        "$path.localizations",
      )
      item.audio?.let { requireNonempty(it, "$path.audio") }
    }
  }
}

object CatalogValidator {
  fun validate(catalog: Catalog): List<ContentValidationIssue> = buildList {
    require(
      catalog.catalogVersion >= 1,
      "range",
      "catalog_version",
      "1 이상이어야 합니다",
    )
    require(catalog.decks.isNotEmpty(), "min_items", "decks", "덱이 하나 이상 필요합니다")

    val deckIds = mutableSetOf<String>()
    catalog.decks.forEachIndexed { index, deck ->
      val path = "decks[$index]"
      validateIdentifier(deck.deckId, "$path.deck_id")
      if (deck.deckId.isNotEmpty() && !deckIds.add(deck.deckId)) {
        add(ContentValidationIssue("duplicate", "$path.deck_id", "카탈로그에서 중복된 덱 ID입니다"))
      }
      require(deck.version >= 1, "range", "$path.version", "1 이상이어야 합니다")
      requireNonempty(deck.name, "$path.name")
      requireNonempty(deck.authorNickname, "$path.author_nickname")
      require(deck.level in 1..3, "range", "$path.level", "1...3 범위여야 합니다")
      validateTags(deck.tags, "$path.tags")
      validateMetadataLocalizations(deck.localizations, deck.tags.size, "$path.localizations")
      val requiredPreviewCodes = requiredItemLocalizationCodes(deck.localizations)
      require(deck.itemCount >= 1, "range", "$path.item_count", "1 이상이어야 합니다")
      require(deck.sizeBytes >= 1, "range", "$path.size_bytes", "1 이상이어야 합니다")
      require(
        deck.downloadsTotal >= 0,
        "range",
        "$path.downloads_total",
        "0 이상이어야 합니다",
      )
      require(deck.downloads7d >= 0, "range", "$path.downloads_7d", "0 이상이어야 합니다")
      require(
        deck.previewItems.size in 1..10,
        "preview_count",
        "$path.preview_items",
        "1...10개여야 합니다",
      )
      deck.previewItems.forEachIndexed { previewIndex, preview ->
        val previewPath = "$path.preview_items[$previewIndex]"
        validateKorean(preview.ko, "$previewPath.ko")
        requireNonempty(preview.meaningJa, "$previewPath.meaning_ja")
        validatePreviewLocalizations(preview.localizations, "$previewPath.localizations")
        validateRequiredPreviewLocalizations(
          preview.localizations,
          requiredPreviewCodes,
          "$previewPath.localizations",
        )
      }
      val validFileUrl = deck.fileUrl.startsWith("decks/") &&
        deck.fileUrl.endsWith(".json") &&
        !deck.fileUrl.contains("..")
      require(
        validFileUrl,
        "file_url",
        "$path.file_url",
        "decks/ 아래의 안전한 JSON 상대 경로여야 합니다",
      )
    }

    val tagNames = mutableSetOf<String>()
    catalog.tags.forEachIndexed { index, tag ->
      val path = "tags[$index]"
      requireNonempty(tag.tag, "$path.tag")
      if (tag.tag.isNotEmpty() && !tagNames.add(tag.tag)) {
        add(ContentValidationIssue("duplicate", "$path.tag", "중복된 태그입니다"))
      }
      require(tag.deckCount >= 1, "range", "$path.deck_count", "1 이상이어야 합니다")
      requireNonempty(tag.category, "$path.category")
      validateTagLocalizations(tag.localizations, "$path.localizations")
      val requiredTagCodes = catalog.decks
        .filter { tag.tag in it.tags }
        .flatMap { it.localizations?.keys.orEmpty() }
        .toSet()
      validateRequiredTagLocalizations(
        tag.localizations,
        requiredTagCodes,
        "$path.localizations",
      )
      val actualCount = catalog.decks.count { tag.tag in it.tags }
      require(
        tag.deckCount == actualCount,
        "tag_count",
        "$path.deck_count",
        "실제 덱 수 $actualCount 와 일치해야 합니다",
      )
    }
  }
}

object CatalogBundleValidator {
  fun validate(catalog: Catalog, decks: List<Deck>): List<ContentValidationIssue> = buildList {
    addAll(CatalogValidator.validate(catalog))
    decks.forEach { deck ->
      addAll(DeckValidator.validate(deck).map { issue ->
        ContentValidationIssue(
          issue.code,
          "decks[${deck.deckId}].${issue.path}",
          issue.message,
        )
      })
    }

    val decksById = decks.groupBy(Deck::deckId)
    catalog.decks.forEachIndexed { index, entry ->
      val path = "decks[$index]"
      val matches = decksById[entry.deckId]
      if (matches?.size != 1) {
        add(ContentValidationIssue("missing_deck", "$path.file_url", "정확히 하나의 덱 파일과 대응해야 합니다"))
        return@forEachIndexed
      }
      val deck = matches.single()
      compare(entry.version == deck.version, "version", path)
      compare(entry.name == deck.name, "name", path)
      compare(entry.authorNickname == deck.author.nickname, "author_nickname", path)
      compare(entry.official == deck.official, "official", path)
      compare(entry.type == deck.type, "type", path)
      compare(entry.level == deck.level, "level", path)
      compare(entry.tags == deck.tags, "tags", path)
      compare(entry.localizations == deck.localizations, "localizations", path)
      compare(entry.itemCount == deck.items.size, "item_count", path)
      val expectedPreview = deck.items.take(10).map { item ->
        CatalogPreviewItem(
          ko = item.ko,
          meaningJa = item.meaningJa,
          localizations = item.localizations?.mapValues { (_, localization) ->
            CatalogPreviewItemLocalization(localization.meaning)
          },
        )
      }
      compare(entry.previewItems == expectedPreview, "preview_items", path)
    }

    val catalogIds = catalog.decks.mapTo(mutableSetOf(), CatalogDeck::deckId)
    decks.map(Deck::deckId).toSet().subtract(catalogIds).forEach { deckId ->
      add(ContentValidationIssue("unindexed_deck", "decks[$deckId]", "카탈로그에 없는 덱 파일입니다"))
    }
  }
}

private val supportedContentLocalizationCodes = setOf("en", "ko")

private fun requiredItemLocalizationCodes(
  metadata: Map<String, DeckMetadataLocalization>?,
): Set<String> = if (metadata?.containsKey("en") == true) setOf("en") else emptySet()

private fun MutableList<ContentValidationIssue>.validateMetadataLocalizations(
  localizations: Map<String, DeckMetadataLocalization>?,
  baseTagCount: Int,
  path: String,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path)
  localizations.forEach { (languageCode, localization) ->
    val localizationPath = "$path.$languageCode"
    requireNonempty(localization.name, "$localizationPath.name")
    requireNonempty(localization.authorNickname, "$localizationPath.author_nickname")
    validateTags(localization.tags, "$localizationPath.tags")
    require(
      localization.tags.size == baseTagCount,
      "localized_tag_count",
      "$localizationPath.tags",
      "원본 태그 수 ${baseTagCount}개와 일치해야 합니다",
    )
  }
}

private fun MutableList<ContentValidationIssue>.validateItemLocalizations(
  localizations: Map<String, DeckItemLocalization>?,
  path: String,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path)
  localizations.forEach { (languageCode, localization) ->
    requireNonempty(localization.meaning, "$path.$languageCode.meaning")
    requireNonempty(localization.reading, "$path.$languageCode.reading")
  }
}

private fun MutableList<ContentValidationIssue>.validateRequiredItemLocalizations(
  localizations: Map<String, DeckItemLocalization>?,
  requiredLanguageCodes: Set<String>,
  path: String,
) {
  requiredLanguageCodes.filterNot { localizations?.containsKey(it) == true }.forEach { languageCode ->
    add(
      ContentValidationIssue(
        "missing_localization",
        "$path.$languageCode",
        "공개된 덱 메타데이터 로케일의 뜻과 읽기가 필요합니다",
      ),
    )
  }
}

private fun MutableList<ContentValidationIssue>.validatePreviewLocalizations(
  localizations: Map<String, CatalogPreviewItemLocalization>?,
  path: String,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path)
  localizations.forEach { (languageCode, localization) ->
    requireNonempty(localization.meaning, "$path.$languageCode.meaning")
  }
}

private fun MutableList<ContentValidationIssue>.validateRequiredPreviewLocalizations(
  localizations: Map<String, CatalogPreviewItemLocalization>?,
  requiredLanguageCodes: Set<String>,
  path: String,
) {
  requiredLanguageCodes.filterNot { localizations?.containsKey(it) == true }.forEach { languageCode ->
    add(
      ContentValidationIssue(
        "missing_localization",
        "$path.$languageCode",
        "공개된 카탈로그 로케일의 미리보기 뜻이 필요합니다",
      ),
    )
  }
}

private fun MutableList<ContentValidationIssue>.validateTagLocalizations(
  localizations: Map<String, String>?,
  path: String,
) {
  if (localizations == null) return
  validateLocalizationKeys(localizations.keys, path)
  localizations.forEach { (languageCode, value) ->
    requireNonempty(value, "$path.$languageCode")
  }
}

private fun MutableList<ContentValidationIssue>.validateRequiredTagLocalizations(
  localizations: Map<String, String>?,
  requiredLanguageCodes: Set<String>,
  path: String,
) {
  requiredLanguageCodes.filterNot { localizations?.containsKey(it) == true }.forEach { languageCode ->
    add(
      ContentValidationIssue(
        "missing_localization",
        "$path.$languageCode",
        "공개된 덱 로케일의 태그 표시명이 필요합니다",
      ),
    )
  }
}

private fun MutableList<ContentValidationIssue>.validateLocalizationKeys(
  keys: Collection<String>,
  path: String,
) {
  keys.filterNot(supportedContentLocalizationCodes::contains).forEach { languageCode ->
    add(
      ContentValidationIssue(
        "unsupported_locale",
        "$path.$languageCode",
        "지원하지 않는 콘텐츠 로케일입니다",
      ),
    )
  }
}

private fun MutableList<ContentValidationIssue>.validateIdentifier(value: String, path: String) {
  require(
    Regex("^[a-z0-9][a-z0-9_-]{2,63}$").matches(value),
    "identifier",
    path,
    "3...64자의 소문자 영숫자, 밑줄, 하이픈만 허용됩니다",
  )
}

private fun MutableList<ContentValidationIssue>.validateTags(tags: List<String>, path: String) {
  require(tags.size in 1..8, "tag_count", path, "1...8개여야 합니다")
  require(tags.toSet().size == tags.size, "duplicate", path, "중복 태그를 허용하지 않습니다")
  tags.forEachIndexed { index, tag -> requireNonempty(tag, "$path[$index]") }
}

private fun MutableList<ContentValidationIssue>.validateKorean(value: String, path: String) {
  requireNonempty(value, path)
  if (value.isEmpty()) return
  val characterCount = value.codePointCount(0, value.length)
  require(
    characterCount <= 10,
    "max_target_length",
    path,
    "공백을 포함해 10자 이하여야 합니다",
  )
  try {
    JamoDecomposer.keySequenceFor(value)
  } catch (error: IllegalArgumentException) {
    add(ContentValidationIssue("undecomposable_ko", path, "조합 엔진이 분해할 수 없습니다: $error"))
    return
  }
  require(
    JamoDecomposer.containsHangul(value),
    "missing_hangul",
    path,
    "한글 음절 또는 자모가 하나 이상 필요합니다",
  )
}

private fun MutableList<ContentValidationIssue>.requireNonempty(value: String, path: String) {
  require(value.isNotBlank(), "required", path, "빈 문자열일 수 없습니다")
}

private fun MutableList<ContentValidationIssue>.require(
  condition: Boolean,
  code: String,
  path: String,
  message: String,
) {
  if (!condition) add(ContentValidationIssue(code, path, message))
}

private fun MutableList<ContentValidationIssue>.compare(
  condition: Boolean,
  field: String,
  path: String,
) {
  if (!condition) {
    add(ContentValidationIssue("metadata_mismatch", "$path.$field", "덱 파일과 카탈로그 값이 일치해야 합니다"))
  }
}
