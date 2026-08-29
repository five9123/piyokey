package app.piyokey.core.data

import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckMetadataLocalization
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.piyodeck.PiyoDeckPackageLimits
import app.piyokey.core.piyodeck.UserDeckDraft
import app.piyokey.core.piyodeck.UserDeckDraftOrigin
import app.piyokey.core.piyodeck.UserDeckItemDraft
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ActiveUserDeckDraft(
  val draftId: String,
  val draft: UserDeckDraft,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  val baseDeckId: String?
    get() = when (val origin = draft.origin) {
      UserDeckDraftOrigin.New -> null
      UserDeckDraftOrigin.Editing -> draft.deckId
      is UserDeckDraftOrigin.OfficialCopy -> origin.sourceDeckId
    }

  val baseVersion: Int?
    get() = draft.baseVersion.takeIf { draft.origin == UserDeckDraftOrigin.Editing }
}

sealed class UserDeckDraftStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) :
    UserDeckDraftStoreException("Unsupported user-deck draft schema: $version")

  data object InvalidEnvelope : UserDeckDraftStoreException("Invalid user-deck draft envelope")
  data object InvalidPayload : UserDeckDraftStoreException("Invalid user-deck draft payload")
}

/** Recoverable primary/backup persistence for the single active Deck Maker document. */
class UserDeckDraftStore(
  root: File,
  private val writeAtomically: (ByteArray, File) -> Unit = ::atomicWrite,
) {
  private val rootDirectory = root.apply { mkdirs() }
  private val primary = File(rootDirectory, "active-user-deck-draft.json")
  private val backup = File(rootDirectory, "active-user-deck-draft.json.backup")

  fun load(): ActiveUserDeckDraft? {
    if (!primary.isFile) return if (backup.isFile) loadBackupAndRestore() else null
    return try {
      val bytes = primary.readBytes()
      val active = decode(bytes)
      if (backup.isFile) {
        try {
          decode(backup.readBytes())
        } catch (error: UserDeckDraftStoreException.UnsupportedSchema) {
          throw error
        } catch (_: Exception) {
          writeAtomically(bytes, backup)
        }
      } else {
        writeAtomically(bytes, backup)
      }
      active
    } catch (error: UserDeckDraftStoreException.UnsupportedSchema) {
      throw error
    } catch (error: Exception) {
      if (backup.isFile) loadBackupAndRestore() else throw error
    }
  }

  fun save(draft: UserDeckDraft, now: Instant): ActiveUserDeckDraft {
    // Protect future-schema primary and backup files from older app versions.
    val current = load()
    val sameFlow = current?.draft?.flowIdentity() == draft.flowIdentity()
    val active = ActiveUserDeckDraft(
      draftId = if (sameFlow) requireNotNull(current).draftId else "draft_${uuidHex()}",
      draft = draft,
      createdAt = if (sameFlow) requireNotNull(current).createdAt else now,
      updatedAt = maxOf(now, if (sameFlow) requireNotNull(current).createdAt else now),
    )
    val bytes = encode(active)
    if (primary.isFile) writeAtomically(primary.readBytes(), backup)
    writeAtomically(bytes, primary)
    return decode(bytes)
  }

  fun clear(draftId: String): Boolean {
    val active = load() ?: return false
    if (active.draftId != draftId) return false
    clear()
    return true
  }

  fun clear() {
    primary.delete()
    backup.delete()
  }

  private fun loadBackupAndRestore(): ActiveUserDeckDraft {
    if (!backup.isFile) throw UserDeckDraftStoreException.InvalidEnvelope
    val recovered = try {
      decode(backup.readBytes())
    } catch (error: UserDeckDraftStoreException.UnsupportedSchema) {
      throw error
    } catch (error: Exception) {
      throw error
    }
    writeAtomically(backup.readBytes(), primary)
    return recovered
  }

  private fun encode(active: ActiveUserDeckDraft): ByteArray {
    val payload = encodePayload(active.draft).toString()
    return buildJsonObject {
      put("schema_version", CURRENT_SCHEMA)
      put("draft_id", active.draftId)
      putNullable("base_deck_id", active.baseDeckId)
      putNullable("base_version", active.baseVersion)
      put("created_at", active.createdAt.toString())
      put("updated_at", active.updatedAt.toString())
      put("validated_json_blob", payload)
    }.toString().toByteArray()
  }

  private fun decode(bytes: ByteArray): ActiveUserDeckDraft {
    val envelope = parseObject(bytes)
    val schema = envelope.requiredInt("schema_version")
    if (schema != CURRENT_SCHEMA) throw UserDeckDraftStoreException.UnsupportedSchema(schema)
    envelope.requireExactKeys(
      setOf(
        "schema_version", "draft_id", "base_deck_id", "base_version", "created_at",
        "updated_at", "validated_json_blob",
      ),
    )
    val draftId = envelope.requiredString("draft_id")
    val createdAt = envelope.requiredInstant("created_at")
    val updatedAt = envelope.requiredInstant("updated_at")
    if (!DRAFT_ID.matches(draftId) || updatedAt < createdAt) {
      throw UserDeckDraftStoreException.InvalidEnvelope
    }
    val draft = decodePayload(parseObject(envelope.requiredString("validated_json_blob").toByteArray()))
    val active = ActiveUserDeckDraft(draftId, draft, createdAt, updatedAt)
    if (envelope.optionalString("base_deck_id") != active.baseDeckId ||
      envelope.optionalInt("base_version") != active.baseVersion
    ) {
      throw UserDeckDraftStoreException.InvalidEnvelope
    }
    return active
  }

  private fun encodePayload(draft: UserDeckDraft): JsonObject = buildJsonObject {
    put("schema_version", CURRENT_SCHEMA)
    when (val origin = draft.origin) {
      UserDeckDraftOrigin.New -> {
        put("origin", "new")
        put("source_deck_id", JsonNull)
      }
      UserDeckDraftOrigin.Editing -> {
        put("origin", "editing")
        put("source_deck_id", JsonNull)
      }
      is UserDeckDraftOrigin.OfficialCopy -> {
        put("origin", "official_copy")
        put("source_deck_id", origin.sourceDeckId)
      }
    }
    put("deck_id", draft.deckId)
    put("deck_created_at", draft.createdAt.toString())
    put("base_version", draft.baseVersion)
    put("author_id", draft.authorId)
    put("metadata_localizations", encodeMetadataLocalizations(draft.metadataLocalizations))
    putNullable("default_locale", draft.defaultLocale)
    put("name", draft.name)
    put("author_nickname", draft.authorNickname)
    put("type", draft.type.name.lowercase())
    put("level", draft.level)
    put("tags", JsonArray(draft.tags.map(::JsonPrimitive)))
    put("items", buildJsonArray {
      draft.items.forEach { item ->
        add(buildJsonObject {
          put("id", item.id)
          put("ko", item.ko)
          put("reading_ja", item.readingJa)
          put("meaning_ja", item.meaningJa)
          put("localizations", encodeItemLocalizations(item.localizations))
        })
      }
    })
  }

  private fun decodePayload(payload: JsonObject): UserDeckDraft {
    val schema = payload.requiredInt("schema_version")
    if (schema != CURRENT_SCHEMA) throw UserDeckDraftStoreException.UnsupportedSchema(schema)
    val legacyKeys = setOf(
      "schema_version", "origin", "source_deck_id", "deck_id", "deck_created_at",
      "base_version", "author_id", "metadata_localizations", "name", "author_nickname",
      "type", "level", "tags", "items",
    )
    if (payload.keys != legacyKeys && payload.keys != legacyKeys + "default_locale") invalidPayload()
    val deckId = payload.requiredString("deck_id")
    val baseVersion = payload.requiredInt("base_version")
    val sourceDeckId = payload.optionalString("source_deck_id")
    val origin = when (payload.requiredString("origin")) {
      "new" -> if (sourceDeckId == null && baseVersion == 0) UserDeckDraftOrigin.New else invalidPayload()
      "editing" -> if (sourceDeckId == null && baseVersion >= 1) UserDeckDraftOrigin.Editing else invalidPayload()
      "official_copy" -> if (!sourceDeckId.isNullOrBlank() && baseVersion == 0) {
        UserDeckDraftOrigin.OfficialCopy(sourceDeckId)
      } else invalidPayload()
      else -> invalidPayload()
    }
    val items = payload.requiredArray("items").map { element ->
      val item = element as? JsonObject ?: invalidPayload()
      item.requireExactKeys(setOf("id", "ko", "reading_ja", "meaning_ja", "localizations"))
      UserDeckItemDraft(
        id = item.requiredString("id"),
        ko = item.requiredString("ko"),
        readingJa = item.requiredString("reading_ja"),
        meaningJa = item.requiredString("meaning_ja"),
        localizations = decodeItemLocalizations(item["localizations"]),
      )
    }
    if (!USER_DECK_ID.matches(deckId) || items.size !in 1..PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT ||
      items.map(UserDeckItemDraft::id).toSet().size != items.size ||
      items.any { !USER_ITEM_ID.matches(it.id) }
    ) invalidPayload()
    return UserDeckDraft(
      origin = origin,
      deckId = deckId,
      createdAt = payload.requiredInstant("deck_created_at"),
      baseVersion = baseVersion,
      authorId = payload.requiredString("author_id").ifBlank { invalidPayload() },
      metadataLocalizations = decodeMetadataLocalizations(payload["metadata_localizations"]),
      defaultLocale = payload.optionalString("default_locale"),
      name = payload.requiredString("name"),
      authorNickname = payload.requiredString("author_nickname"),
      type = when (payload.requiredString("type")) {
        "word" -> DeckType.WORD
        "sentence" -> DeckType.SENTENCE
        else -> invalidPayload()
      },
      level = payload.requiredInt("level"),
      tags = payload.requiredStringArray("tags"),
      items = items,
    )
  }

  private fun encodeMetadataLocalizations(
    values: Map<String, DeckMetadataLocalization>?,
  ): JsonElement = values?.let { map ->
    buildJsonObject {
      map.toSortedMap().forEach { (code, value) ->
        put(code, buildJsonObject {
          put("name", value.name)
          put("author_nickname", value.authorNickname)
          put("tags", JsonArray(value.tags.map(::JsonPrimitive)))
        })
      }
    }
  } ?: JsonNull

  private fun decodeMetadataLocalizations(element: JsonElement?): Map<String, DeckMetadataLocalization>? {
    if (element == null || element === JsonNull) return null
    val objectValue = element as? JsonObject ?: invalidPayload()
    return objectValue.mapValues { (_, value) ->
      val localization = value as? JsonObject ?: invalidPayload()
      localization.requireExactKeys(setOf("name", "author_nickname", "tags"))
      DeckMetadataLocalization(
        localization.requiredString("name"),
        localization.requiredString("author_nickname"),
        localization.requiredStringArray("tags"),
      )
    }
  }

  private fun encodeItemLocalizations(values: Map<String, DeckItemLocalization>?): JsonElement =
    values?.let { map ->
      buildJsonObject {
        map.toSortedMap().forEach { (code, value) ->
          put(code, buildJsonObject {
            put("meaning", value.meaning)
            put("reading", value.reading)
          })
        }
      }
    } ?: JsonNull

  private fun decodeItemLocalizations(element: JsonElement?): Map<String, DeckItemLocalization>? {
    if (element == null || element === JsonNull) return null
    val objectValue = element as? JsonObject ?: invalidPayload()
    return objectValue.mapValues { (_, value) ->
      val localization = value as? JsonObject ?: invalidPayload()
      localization.requireExactKeys(setOf("meaning", "reading"))
      DeckItemLocalization(
        meaning = localization.requiredString("meaning"),
        reading = localization.requiredString("reading"),
      )
    }
  }

  private fun UserDeckDraft.flowIdentity(): String = when (val origin = origin) {
    UserDeckDraftOrigin.New -> "new:$deckId"
    UserDeckDraftOrigin.Editing -> "editing:$deckId"
    is UserDeckDraftOrigin.OfficialCopy -> "official-copy:${origin.sourceDeckId}:$deckId"
  }

  private companion object {
    const val CURRENT_SCHEMA = 1
    val DRAFT_ID = Regex("^draft_[0-9a-f]{32}$")
    val USER_DECK_ID = Regex("^user_[0-9a-f]{32}$")
    val USER_ITEM_ID = Regex("^item_[0-9a-f]{32}$")
    val parser = Json { isLenient = false; allowTrailingComma = false; explicitNulls = true }

    fun uuidHex(): String = UUID.randomUUID().toString().replace("-", "")

    fun parseObject(bytes: ByteArray): JsonObject = try {
      parser.parseToJsonElement(bytes.decodeToString()) as? JsonObject
        ?: throw UserDeckDraftStoreException.InvalidEnvelope
    } catch (error: UserDeckDraftStoreException) {
      throw error
    } catch (_: SerializationException) {
      throw UserDeckDraftStoreException.InvalidEnvelope
    }

    fun invalidPayload(): Nothing = throw UserDeckDraftStoreException.InvalidPayload

    fun atomicWrite(bytes: ByteArray, target: File) {
      target.parentFile?.mkdirs()
      val temporary = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.tmp")
      temporary.outputStream().use { output ->
        output.write(bytes)
        output.fd.sync()
      }
      try {
        Files.move(
          temporary.toPath(),
          target.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING,
        )
      } catch (_: Exception) {
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }
}

private fun JsonObject.requireExactKeys(keys: Set<String>) {
  if (this.keys != keys) throw UserDeckDraftStoreException.InvalidPayload
}

private fun JsonObject.requiredString(key: String): String =
  this[key]?.jsonPrimitive?.contentOrNull ?: throw UserDeckDraftStoreException.InvalidPayload

private fun JsonObject.optionalString(key: String): String? = when (val value = this[key]) {
  null, JsonNull -> null
  else -> value.jsonPrimitive.contentOrNull ?: throw UserDeckDraftStoreException.InvalidPayload
}

private fun JsonObject.requiredInt(key: String): Int =
  this[key]?.jsonPrimitive?.intOrNull ?: throw UserDeckDraftStoreException.InvalidPayload

private fun JsonObject.optionalInt(key: String): Int? = when (val value = this[key]) {
  null, JsonNull -> null
  else -> value.jsonPrimitive.intOrNull ?: throw UserDeckDraftStoreException.InvalidPayload
}

private fun JsonObject.requiredInstant(key: String): Instant = try {
  Instant.parse(requiredString(key))
} catch (_: Exception) {
  throw UserDeckDraftStoreException.InvalidPayload
}

private fun JsonObject.requiredArray(key: String): JsonArray =
  this[key] as? JsonArray ?: throw UserDeckDraftStoreException.InvalidPayload

private fun JsonObject.requiredStringArray(key: String): List<String> = requiredArray(key).map {
  it.jsonPrimitive.contentOrNull ?: throw UserDeckDraftStoreException.InvalidPayload
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
  put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Int?) {
  put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}
