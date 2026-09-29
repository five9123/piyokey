package app.piyokey.android.data.decks

import app.piyokey.android.data.persistence.Base64ByteArraySerializer
import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.android.data.persistence.StoreJson
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckMetadataLocalization
import app.piyokey.core.deckkit.DeckType
import app.piyokey.core.deckkit.Iso8601InstantSerializer
import app.piyokey.core.deckkit.PiyoDeckPackageLimits
import app.piyokey.core.domain.UserDeckDraft
import app.piyokey.core.domain.UserDeckItemDraft
import java.io.File
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The one unfinished Deck Maker document (iOS `ActiveUserDeckDraft`). */
data class ActiveUserDeckDraft(
  val draftId: String,
  val draft: UserDeckDraft,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  /** The deck this draft edits or copies (`null` for a new deck). */
  val baseDeckId: String?
    get() = when (val origin = draft.origin) {
      UserDeckDraft.Origin.New -> null
      UserDeckDraft.Origin.Editing -> draft.deckId
      is UserDeckDraft.Origin.OfficialCopy -> origin.sourceDeckId
    }

  /** Installed version being edited; compare on save to detect a conflicting replacement. */
  val baseVersion: Int? get() = if (draft.origin == UserDeckDraft.Origin.Editing) draft.baseVersion else null
}

sealed class UserDeckDraftStoreException(message: String) : Exception(message) {
  data class UnsupportedSchema(val version: Int) : UserDeckDraftStoreException("Unsupported draft schema $version")
  data object InvalidEnvelope : UserDeckDraftStoreException("Invalid draft envelope")
  data object InvalidPayload : UserDeckDraftStoreException("Invalid draft payload")
}

/**
 * Autosaved single active draft at `Hanco/DeckMaker/active-user-deck-draft.json` (iOS
 * `UserDeckDraftStore`, schema 1). Saves always read first so a future-schema file is never
 * downgraded. Blocking file IO.
 */
class UserDeckDraftStore(
  val rootDir: File,
  private val fileWriter: (ByteArray, File) -> Unit = RecoverableJsonFile::write,
  private val newDraftId: () -> String = { "draft_" + UserDeckDraft.randomUuidHex() },
) {
  @Serializable
  private data class Envelope(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("draft_id") val draftId: String,
    @SerialName("base_deck_id") val baseDeckId: String? = null,
    @SerialName("base_version") val baseVersion: Int? = null,
    @SerialName("created_at") @Serializable(with = Iso8601InstantSerializer::class) val createdAt: Instant,
    @SerialName("updated_at") @Serializable(with = Iso8601InstantSerializer::class) val updatedAt: Instant,
    @SerialName("validated_json_blob") @Serializable(with = Base64ByteArraySerializer::class) val validatedJsonBlob: ByteArray,
  )

  @Serializable
  private enum class PayloadOrigin {
    @SerialName("new") NEW,
    @SerialName("editing") EDITING,
    @SerialName("official_copy") OFFICIAL_COPY,
  }

  @Serializable
  private data class PayloadItem(
    val id: String,
    val ko: String,
    @SerialName("reading_ja") val readingJa: String,
    @SerialName("meaning_ja") val meaningJa: String,
    val localizations: Map<String, DeckItemLocalization>? = null,
  )

  @Serializable
  private data class Payload(
    @SerialName("schema_version") val schemaVersion: Int,
    val origin: PayloadOrigin,
    @SerialName("source_deck_id") val sourceDeckId: String? = null,
    @SerialName("deck_id") val deckId: String,
    @SerialName("deck_created_at") @Serializable(with = Iso8601InstantSerializer::class) val deckCreatedAt: Instant,
    @SerialName("base_version") val baseVersion: Int,
    @SerialName("author_id") val authorId: String,
    @SerialName("metadata_localizations") val metadataLocalizations: Map<String, DeckMetadataLocalization>? = null,
    @SerialName("default_locale") val defaultLocale: String? = null,
    val name: String,
    @SerialName("author_nickname") val authorNickname: String,
    val type: DeckType,
    val level: Int,
    val tags: List<String>,
    val items: List<PayloadItem>,
  )

  private val file get() = File(rootDir, "active-user-deck-draft.json")

  /** The active draft, recovering a corrupt primary from its backup; `null` when none. */
  fun load(): ActiveUserDeckDraft? = RecoverableJsonFile.load(
    file,
    shouldRecover = { it !is UserDeckDraftStoreException.UnsupportedSchema },
    decode = ::decode,
  )

  /**
   * Persists [draft] (possibly not yet valid). The same editing flow keeps its draft id and first
   * creation time; a different flow atomically replaces the active draft.
   */
  fun save(draft: UserDeckDraft, at: Instant = Instant.now()): ActiveUserDeckDraft {
    val current = load()
    val sameFlow = current != null && flowIdentity(current.draft) == flowIdentity(draft)
    val draftId = if (sameFlow) current.draftId else newDraftId()
    val createdAt = if (sameFlow) current.createdAt else at
    val active = ActiveUserDeckDraft(draftId, draft, createdAt, maxOf(at, createdAt))
    val payloadData = StoreJson.encode(Payload.serializer(), payload(draft))
    val envelopeData = StoreJson.encode(
      Envelope.serializer(),
      Envelope(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        draftId = active.draftId,
        baseDeckId = active.baseDeckId,
        baseVersion = active.baseVersion,
        createdAt = active.createdAt,
        updatedAt = active.updatedAt,
        validatedJsonBlob = payloadData,
      ),
    )
    // Return exactly what a relaunch decodes (timestamps normalize to whole seconds).
    val persisted = decode(envelopeData)
    fileWriter(envelopeData, file)
    return persisted
  }

  /** Clears only the draft that was actually committed (an older completion never deletes a newer flow). */
  fun clear(draftId: String): Boolean {
    val active = load() ?: return false
    if (active.draftId != draftId) return false
    RecoverableJsonFile.removeArtifacts(file)
    return true
  }

  /** Explicit discard/reset. */
  fun clear() = RecoverableJsonFile.removeArtifacts(file)

  private fun decode(data: ByteArray): ActiveUserDeckDraft {
    val envelope = StoreJson.decode(Envelope.serializer(), data)
    if (envelope.schemaVersion != CURRENT_SCHEMA_VERSION) throw UserDeckDraftStoreException.UnsupportedSchema(envelope.schemaVersion)
    if (!isIdentifier(envelope.draftId, "draft_") || envelope.updatedAt < envelope.createdAt) {
      throw UserDeckDraftStoreException.InvalidEnvelope
    }
    val payload = StoreJson.decode(Payload.serializer(), envelope.validatedJsonBlob)
    if (payload.schemaVersion != CURRENT_SCHEMA_VERSION) throw UserDeckDraftStoreException.UnsupportedSchema(payload.schemaVersion)
    val active = ActiveUserDeckDraft(envelope.draftId, materialize(payload), envelope.createdAt, envelope.updatedAt)
    if (envelope.baseDeckId != active.baseDeckId || envelope.baseVersion != active.baseVersion) {
      throw UserDeckDraftStoreException.InvalidEnvelope
    }
    return active
  }

  private fun payload(draft: UserDeckDraft) = Payload(
    schemaVersion = CURRENT_SCHEMA_VERSION,
    origin = when (draft.origin) {
      UserDeckDraft.Origin.New -> PayloadOrigin.NEW
      UserDeckDraft.Origin.Editing -> PayloadOrigin.EDITING
      is UserDeckDraft.Origin.OfficialCopy -> PayloadOrigin.OFFICIAL_COPY
    },
    sourceDeckId = (draft.origin as? UserDeckDraft.Origin.OfficialCopy)?.sourceDeckId,
    deckId = draft.deckId,
    deckCreatedAt = draft.createdAt,
    baseVersion = draft.baseVersion,
    authorId = draft.authorId,
    metadataLocalizations = draft.metadataLocalizations,
    defaultLocale = draft.defaultLocale,
    name = draft.name,
    authorNickname = draft.authorNickname,
    type = draft.type,
    level = draft.level,
    tags = draft.tags,
    items = draft.items.map { PayloadItem(it.id, it.ko, it.readingJa, it.meaningJa, it.localizations) },
  )

  private fun materialize(payload: Payload): UserDeckDraft {
    if (!isIdentifier(payload.deckId, "user_") || payload.authorId.isEmpty() ||
      payload.items.size !in 1..PiyoDeckPackageLimits.MAXIMUM_ITEM_COUNT ||
      payload.items.map { it.id }.toSet().size != payload.items.size ||
      !payload.items.all { isIdentifier(it.id, "item_") }
    ) {
      throw UserDeckDraftStoreException.InvalidPayload
    }
    val origin = when (payload.origin) {
      PayloadOrigin.NEW -> {
        if (payload.sourceDeckId != null || payload.baseVersion != 0) throw UserDeckDraftStoreException.InvalidPayload
        UserDeckDraft.Origin.New
      }
      PayloadOrigin.EDITING -> {
        if (payload.sourceDeckId != null || payload.baseVersion < 1) throw UserDeckDraftStoreException.InvalidPayload
        UserDeckDraft.Origin.Editing
      }
      PayloadOrigin.OFFICIAL_COPY -> {
        val sourceId = payload.sourceDeckId
        if (sourceId.isNullOrEmpty() || payload.baseVersion != 0) throw UserDeckDraftStoreException.InvalidPayload
        UserDeckDraft.Origin.OfficialCopy(sourceId)
      }
    }
    return UserDeckDraft(
      origin = origin,
      deckId = payload.deckId,
      createdAt = payload.deckCreatedAt,
      baseVersion = payload.baseVersion,
      authorId = payload.authorId,
      metadataLocalizations = payload.metadataLocalizations,
      defaultLocale = payload.defaultLocale,
      name = payload.name,
      authorNickname = payload.authorNickname,
      type = payload.type,
      level = payload.level,
      tags = payload.tags,
      items = payload.items.map { UserDeckItemDraft(it.id, it.ko, it.readingJa, it.meaningJa, it.localizations) },
    )
  }

  private fun flowIdentity(draft: UserDeckDraft): String = when (val origin = draft.origin) {
    UserDeckDraft.Origin.New -> "new:${draft.deckId}"
    UserDeckDraft.Origin.Editing -> "editing:${draft.deckId}"
    is UserDeckDraft.Origin.OfficialCopy -> "official-copy:${origin.sourceDeckId}:${draft.deckId}"
  }

  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
    private val IDENTIFIER_PATTERN = Regex("^[a-z]+_[0-9a-f]{32}$")

    private fun isIdentifier(value: String, prefix: String) = value.startsWith(prefix) && IDENTIFIER_PATTERN.matches(value)
  }
}
