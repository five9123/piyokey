package app.piyokey.core.deckkit

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

data class DeckKitJsonException(
  val path: String,
  override val message: String,
  override val cause: Throwable? = null,
) : IllegalArgumentException("$path: $message", cause)

data class ContentValidationException(
  val issues: List<ContentValidationIssue>,
) : IllegalArgumentException(issues.joinToString(separator = "\n"))

/** Strict, dependency-light JSON codec shared by catalog and `.typedeck` boundaries. */
object DeckKitJson {
  private val parser = Json {
    isLenient = false
    allowTrailingComma = false
    explicitNulls = true
  }

  fun decodeDeck(source: String): Deck = decodeDeck(parseObject(source))

  fun decodeCatalog(source: String): Catalog = decodeCatalog(parseObject(source))

  fun decodeValidatedDeck(source: String, schemaSource: String): Deck {
    val schemaIssues = JsonSchemaValidator.validate(source, schemaSource)
    if (schemaIssues.isNotEmpty()) throw ContentValidationException(schemaIssues)
    return decodeDeck(source).also { deck ->
      DeckValidator.validate(deck).takeIf(List<ContentValidationIssue>::isNotEmpty)?.let {
        throw ContentValidationException(it)
      }
    }
  }

  fun decodeValidatedCatalog(source: String, schemaSource: String): Catalog {
    val schemaIssues = JsonSchemaValidator.validate(source, schemaSource)
    if (schemaIssues.isNotEmpty()) throw ContentValidationException(schemaIssues)
    return decodeCatalog(source).also { catalog ->
      CatalogValidator.validate(catalog).takeIf(List<ContentValidationIssue>::isNotEmpty)?.let {
        throw ContentValidationException(it)
      }
    }
  }

  fun encodeDeck(deck: Deck): String = encodeDeckElement(deck).toString()

  fun encodeCatalog(catalog: Catalog): String = buildJsonObject {
    put("catalog_version", catalog.catalogVersion)
    put("generated_at", catalog.generatedAt.toString())
    put("decks", JsonArray(catalog.decks.map(::encodeCatalogDeckElement)))
    put("tags", JsonArray(catalog.tags.map(::encodeCatalogTagElement)))
  }.toString()

  private fun parseObject(source: String): JsonObject {
    val element = try {
      parser.parseToJsonElement(source)
    } catch (error: SerializationException) {
      throw DeckKitJsonException("$", "유효한 JSON이어야 합니다", error)
    }
    return element as? JsonObject
      ?: throw DeckKitJsonException("$", "최상위 값은 object여야 합니다")
  }

  private fun decodeDeck(objectValue: JsonObject): Deck {
    objectValue.checkKeys(
      path = "$",
      allowed = setOf(
        "deck_id", "version", "name", "author", "official", "type", "level", "tags",
        "localizations", "created_at", "updated_at", "items",
      ),
      required = setOf(
        "deck_id", "version", "name", "author", "official", "type", "level", "tags",
        "created_at", "updated_at", "items",
      ),
    )
    return Deck(
      deckId = objectValue.requiredString("deck_id", "$"),
      version = objectValue.requiredInt("version", "$"),
      name = objectValue.requiredString("name", "$"),
      author = decodeAuthor(objectValue.requiredObject("author", "$"), "$.author"),
      official = objectValue.requiredBoolean("official", "$"),
      type = decodeDeckType(objectValue.requiredString("type", "$"), "$.type"),
      level = objectValue.requiredInt("level", "$"),
      tags = objectValue.requiredStringList("tags", "$"),
      createdAt = objectValue.requiredInstant("created_at", "$"),
      updatedAt = objectValue.requiredInstant("updated_at", "$"),
      items = objectValue.requiredArray("items", "$").mapIndexed { index, element ->
        decodeDeckItem(element.requireObject("$.items[$index]"), "$.items[$index]")
      },
      localizations = objectValue.optionalObject("localizations", "$")?.mapValues {
        decodeMetadataLocalization(it.value.requireObject("$.localizations.${it.key}"), "$.localizations.${it.key}")
      },
    )
  }

  private fun decodeAuthor(objectValue: JsonObject, path: String): DeckAuthor {
    objectValue.checkKeys(path, setOf("id", "nickname"), setOf("id", "nickname"))
    return DeckAuthor(
      id = objectValue.requiredString("id", path),
      nickname = objectValue.requiredString("nickname", path),
    )
  }

  private fun decodeDeckItem(objectValue: JsonObject, path: String): DeckItem {
    objectValue.checkKeys(
      path,
      setOf("id", "ko", "reading_ja", "meaning_ja", "localizations", "audio"),
      setOf("id", "ko", "reading_ja", "meaning_ja", "audio"),
    )
    val audioElement = objectValue.getValue("audio")
    val audio = if (audioElement === JsonNull) {
      null
    } else {
      audioElement.requireString("$path.audio")
    }
    return DeckItem(
      id = objectValue.requiredString("id", path),
      ko = objectValue.requiredString("ko", path),
      readingJa = objectValue.requiredString("reading_ja", path),
      meaningJa = objectValue.requiredString("meaning_ja", path),
      audio = audio,
      localizations = objectValue.optionalObject("localizations", path)?.mapValues {
        decodeItemLocalization(it.value.requireObject("$path.localizations.${it.key}"), "$path.localizations.${it.key}")
      },
    )
  }

  private fun decodeMetadataLocalization(
    objectValue: JsonObject,
    path: String,
  ): DeckMetadataLocalization {
    objectValue.checkKeys(
      path,
      setOf("name", "author_nickname", "tags"),
      setOf("name", "author_nickname", "tags"),
    )
    return DeckMetadataLocalization(
      name = objectValue.requiredString("name", path),
      authorNickname = objectValue.requiredString("author_nickname", path),
      tags = objectValue.requiredStringList("tags", path),
    )
  }

  private fun decodeItemLocalization(
    objectValue: JsonObject,
    path: String,
  ): DeckItemLocalization {
    objectValue.checkKeys(path, setOf("meaning", "reading"), setOf("meaning", "reading"))
    return DeckItemLocalization(
      meaning = objectValue.requiredString("meaning", path),
      reading = objectValue.requiredString("reading", path),
    )
  }

  private fun decodeCatalog(objectValue: JsonObject): Catalog {
    objectValue.checkKeys(
      path = "$",
      allowed = setOf("catalog_version", "generated_at", "decks", "tags"),
      required = setOf("catalog_version", "generated_at", "decks", "tags"),
    )
    return Catalog(
      catalogVersion = objectValue.requiredInt("catalog_version", "$"),
      generatedAt = objectValue.requiredInstant("generated_at", "$"),
      decks = objectValue.requiredArray("decks", "$").mapIndexed { index, element ->
        decodeCatalogDeck(element.requireObject("$.decks[$index]"), "$.decks[$index]")
      },
      tags = objectValue.requiredArray("tags", "$").mapIndexed { index, element ->
        decodeCatalogTag(element.requireObject("$.tags[$index]"), "$.tags[$index]")
      },
    )
  }

  private fun decodeCatalogDeck(objectValue: JsonObject, path: String): CatalogDeck {
    objectValue.checkKeys(
      path,
      setOf(
        "deck_id", "version", "name", "author_nickname", "official", "featured", "type",
        "level", "tags", "localizations", "item_count", "size_bytes", "downloads_total",
        "downloads_7d", "created_at", "preview_items", "file_url",
      ),
      setOf(
        "deck_id", "version", "name", "author_nickname", "official", "featured", "type",
        "level", "tags", "item_count", "size_bytes", "downloads_total", "downloads_7d",
        "created_at", "preview_items", "file_url",
      ),
    )
    return CatalogDeck(
      deckId = objectValue.requiredString("deck_id", path),
      version = objectValue.requiredInt("version", path),
      name = objectValue.requiredString("name", path),
      authorNickname = objectValue.requiredString("author_nickname", path),
      official = objectValue.requiredBoolean("official", path),
      featured = objectValue.requiredBoolean("featured", path),
      type = decodeDeckType(objectValue.requiredString("type", path), "$path.type"),
      level = objectValue.requiredInt("level", path),
      tags = objectValue.requiredStringList("tags", path),
      itemCount = objectValue.requiredInt("item_count", path),
      sizeBytes = objectValue.requiredInt("size_bytes", path),
      downloadsTotal = objectValue.requiredInt("downloads_total", path),
      downloads7d = objectValue.requiredInt("downloads_7d", path),
      createdAt = objectValue.requiredInstant("created_at", path),
      previewItems = objectValue.requiredArray("preview_items", path).mapIndexed { index, element ->
        decodePreviewItem(element.requireObject("$path.preview_items[$index]"), "$path.preview_items[$index]")
      },
      fileUrl = objectValue.requiredString("file_url", path),
      localizations = objectValue.optionalObject("localizations", path)?.mapValues {
        decodeMetadataLocalization(it.value.requireObject("$path.localizations.${it.key}"), "$path.localizations.${it.key}")
      },
    )
  }

  private fun decodePreviewItem(objectValue: JsonObject, path: String): CatalogPreviewItem {
    objectValue.checkKeys(
      path,
      setOf("ko", "meaning_ja", "localizations"),
      setOf("ko", "meaning_ja"),
    )
    return CatalogPreviewItem(
      ko = objectValue.requiredString("ko", path),
      meaningJa = objectValue.requiredString("meaning_ja", path),
      localizations = objectValue.optionalObject("localizations", path)?.mapValues {
        decodePreviewLocalization(it.value.requireObject("$path.localizations.${it.key}"), "$path.localizations.${it.key}")
      },
    )
  }

  private fun decodePreviewLocalization(
    objectValue: JsonObject,
    path: String,
  ): CatalogPreviewItemLocalization {
    objectValue.checkKeys(path, setOf("meaning"), setOf("meaning"))
    return CatalogPreviewItemLocalization(objectValue.requiredString("meaning", path))
  }

  private fun decodeCatalogTag(objectValue: JsonObject, path: String): CatalogTag {
    objectValue.checkKeys(
      path,
      setOf("tag", "deck_count", "category", "localizations"),
      setOf("tag", "deck_count", "category"),
    )
    return CatalogTag(
      tag = objectValue.requiredString("tag", path),
      deckCount = objectValue.requiredInt("deck_count", path),
      category = objectValue.requiredString("category", path),
      localizations = objectValue.optionalObject("localizations", path)?.mapValues {
        it.value.requireString("$path.localizations.${it.key}")
      },
    )
  }

  private fun decodeDeckType(value: String, path: String): DeckType = when (value) {
    "word" -> DeckType.WORD
    "sentence" -> DeckType.SENTENCE
    else -> throw DeckKitJsonException(path, "word 또는 sentence여야 합니다")
  }

  private fun encodeDeckElement(deck: Deck): JsonObject = buildJsonObject {
    put("deck_id", deck.deckId)
    put("version", deck.version)
    put("name", deck.name)
    put("author", buildJsonObject {
      put("id", deck.author.id)
      put("nickname", deck.author.nickname)
    })
    put("official", deck.official)
    put("type", deck.type.wireValue)
    put("level", deck.level)
    put("tags", deck.tags.toJsonArray())
    deck.localizations?.let { put("localizations", encodeMetadataLocalizations(it)) }
    put("created_at", deck.createdAt.toString())
    put("updated_at", deck.updatedAt.toString())
    put("items", JsonArray(deck.items.map(::encodeDeckItemElement)))
  }

  private fun encodeDeckItemElement(item: DeckItem): JsonObject = buildJsonObject {
    put("id", item.id)
    put("ko", item.ko)
    put("reading_ja", item.readingJa)
    put("meaning_ja", item.meaningJa)
    item.localizations?.let { localizations ->
      put("localizations", JsonObject(localizations.mapValues { (_, value) ->
        buildJsonObject {
          put("meaning", value.meaning)
          put("reading", value.reading)
        }
      }))
    }
    put("audio", item.audio?.let(::JsonPrimitive) ?: JsonNull)
  }

  private fun encodeMetadataLocalizations(
    localizations: Map<String, DeckMetadataLocalization>,
  ): JsonObject = JsonObject(localizations.mapValues { (_, value) ->
    buildJsonObject {
      put("name", value.name)
      put("author_nickname", value.authorNickname)
      put("tags", value.tags.toJsonArray())
    }
  })

  private fun encodeCatalogDeckElement(deck: CatalogDeck): JsonObject = buildJsonObject {
    put("deck_id", deck.deckId)
    put("version", deck.version)
    put("name", deck.name)
    put("author_nickname", deck.authorNickname)
    put("official", deck.official)
    put("featured", deck.featured)
    put("type", deck.type.wireValue)
    put("level", deck.level)
    put("tags", deck.tags.toJsonArray())
    deck.localizations?.let { put("localizations", encodeMetadataLocalizations(it)) }
    put("item_count", deck.itemCount)
    put("size_bytes", deck.sizeBytes)
    put("downloads_total", deck.downloadsTotal)
    put("downloads_7d", deck.downloads7d)
    put("created_at", deck.createdAt.toString())
    put("preview_items", JsonArray(deck.previewItems.map(::encodePreviewItemElement)))
    put("file_url", deck.fileUrl)
  }

  private fun encodePreviewItemElement(item: CatalogPreviewItem): JsonObject = buildJsonObject {
    put("ko", item.ko)
    put("meaning_ja", item.meaningJa)
    item.localizations?.let { localizations ->
      put("localizations", JsonObject(localizations.mapValues { (_, value) ->
        buildJsonObject { put("meaning", value.meaning) }
      }))
    }
  }

  private fun encodeCatalogTagElement(tag: CatalogTag): JsonObject = buildJsonObject {
    put("tag", tag.tag)
    put("deck_count", tag.deckCount)
    put("category", tag.category)
    tag.localizations?.let { values ->
      put("localizations", JsonObject(values.mapValues { JsonPrimitive(it.value) }))
    }
  }

  private val DeckType.wireValue: String
    get() = when (this) {
      DeckType.WORD -> "word"
      DeckType.SENTENCE -> "sentence"
    }

  private fun List<String>.toJsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))
}

private fun JsonObject.checkKeys(
  path: String,
  allowed: Set<String>,
  required: Set<String>,
) {
  (keys - allowed).sorted().firstOrNull()?.let { key ->
    throw DeckKitJsonException("$path.$key", "정의되지 않은 필드입니다")
  }
  (required - keys).sorted().firstOrNull()?.let { key ->
    throw DeckKitJsonException("$path.$key", "필수 필드입니다")
  }
}

private fun JsonObject.requiredString(key: String, path: String): String =
  getValue(key).requireString("$path.$key")

private fun JsonObject.requiredInt(key: String, path: String): Int {
  val primitive = getValue(key) as? JsonPrimitive
    ?: throw DeckKitJsonException("$path.$key", "integer여야 합니다")
  if (primitive.isString || primitive.booleanOrNull != null) {
    throw DeckKitJsonException("$path.$key", "integer여야 합니다")
  }
  return primitive.intOrNull
    ?: throw DeckKitJsonException("$path.$key", "integer여야 합니다")
}

private fun JsonObject.requiredBoolean(key: String, path: String): Boolean {
  val primitive = getValue(key) as? JsonPrimitive
    ?: throw DeckKitJsonException("$path.$key", "boolean이어야 합니다")
  if (primitive.isString) throw DeckKitJsonException("$path.$key", "boolean이어야 합니다")
  return primitive.booleanOrNull
    ?: throw DeckKitJsonException("$path.$key", "boolean이어야 합니다")
}

private fun JsonObject.requiredInstant(key: String, path: String): Instant {
  val stringValue = requiredString(key, path)
  return try {
    Instant.parse(stringValue)
  } catch (error: DateTimeParseException) {
    throw DeckKitJsonException("$path.$key", "RFC 3339 date-time이어야 합니다", error)
  }
}

private fun JsonObject.requiredArray(key: String, path: String): JsonArray =
  this[key] as? JsonArray ?: throw DeckKitJsonException("$path.$key", "array여야 합니다")

private fun JsonObject.requiredStringList(key: String, path: String): List<String> =
  requiredArray(key, path).mapIndexed { index, element ->
    element.requireString("$path.$key[$index]")
  }

private fun JsonObject.requiredObject(key: String, path: String): JsonObject =
  this[key] as? JsonObject ?: throw DeckKitJsonException("$path.$key", "object여야 합니다")

private fun JsonObject.optionalObject(key: String, path: String): JsonObject? = when (val value = this[key]) {
  null -> null
  is JsonObject -> value
  else -> throw DeckKitJsonException("$path.$key", "object여야 합니다")
}

private fun JsonElement.requireObject(path: String): JsonObject =
  this as? JsonObject ?: throw DeckKitJsonException(path, "object여야 합니다")

private fun JsonElement.requireString(path: String): String {
  val primitive = this as? JsonPrimitive
    ?: throw DeckKitJsonException(path, "string이어야 합니다")
  if (!primitive.isString) throw DeckKitJsonException(path, "string이어야 합니다")
  return primitive.content
}
