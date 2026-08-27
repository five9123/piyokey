package app.piyokey.core.data

import android.content.Context
import androidx.room.withTransaction
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckItem
import app.piyokey.core.deckkit.DeckItemLocalization
import app.piyokey.core.deckkit.DeckKitJson
import app.piyokey.core.game.FlowGameRecord
import app.piyokey.core.game.FlowRankTuning
import app.piyokey.core.game.GameSessionRecord
import app.piyokey.core.game.PlayGamesAchievementKey
import app.piyokey.core.game.PlayGamesPolicy
import app.piyokey.core.game.PlayGamesResultIdentity
import app.piyokey.core.game.SpacingPassage
import app.piyokey.core.piyodeck.PiyoDeckDigest
import app.piyokey.core.piyodeck.PiyoDeckPackage
import app.piyokey.core.piyodeck.PiyoDeckPackageLimits
import app.piyokey.core.piyodeck.PiyoDeckPackageReader
import app.piyokey.core.piyodeck.PiyoDeckPackageWriter
import app.piyokey.core.piyodeck.UserDeckLanguage
import app.piyokey.core.piyodeck.UserDeckDraft
import app.piyokey.core.piyodeck.UserDeckDraftOrigin
import app.piyokey.core.retention.CurriculumPolicy
import app.piyokey.core.retention.JstDay
import app.piyokey.core.retention.RetentionActivity
import app.piyokey.core.retention.RetentionPolicy
import app.piyokey.core.retention.ReviewItem
import app.piyokey.core.retention.ReviewPolicy
import app.piyokey.core.session.PracticeItemResolution
import app.piyokey.core.session.PracticeSessionCheckpoint
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject

data class InstalledDeck(
  val metadata: InstalledDeckEntity,
  val deck: Deck,
)

data class DeckLibrarySnapshot(
  val catalog: Catalog,
  val installed: List<InstalledDeck>,
  val downloadHistoryTags: List<List<String>>,
) {
  val installedDeckIds: Set<String>
    get() = installed.mapTo(mutableSetOf()) { it.metadata.deckId }
}

data class SavedGameResult(
  val progress: DeckProgressEntity,
  val isNewBest: Boolean,
)

data class CurriculumActiveSession(
  val stageId: String,
  val checkpoint: PracticeSessionCheckpoint,
)

data class LearningSnapshot(
  val progress: Map<String, UserProgressEntity>,
  val activeSession: CurriculumActiveSession?,
  val reviewItems: List<ReviewItem>,
  val activitiesByDay: Map<JstDay, Set<RetentionActivity>>,
  val unlockedRewards: Set<Int>,
  val reminder: ReminderPreferenceEntity,
) {
  val completedDays: Set<JstDay>
    get() = activitiesByDay.filterValues(Set<RetentionActivity>::isNotEmpty).keys
}

sealed interface CatalogRefreshResult {
  data object NotConfigured : CatalogRefreshResult
  data object NotModified : CatalogRefreshResult
  data class Updated(val catalogVersion: Int) : CatalogRefreshResult
  data class KeptCurrent(val reason: String) : CatalogRefreshResult
}

class DeckRepository private constructor(
  private val context: Context,
  private val database: PiyokeyDatabase,
  private val remoteClient: StaticContentClient,
  private val catalogUrl: URL?,
  private val clock: () -> Long,
) {
  private val dao = database.dao()
  private val store = AtomicPayloadStore(File(context.filesDir, "piyokey/content"))
  private val userDeckDraftStore = UserDeckDraftStore(File(context.filesDir, "piyokey/deck-maker"))
  private val catalogSchema by lazy { readAssetText("catalog.schema.json") }
  private val deckSchema by lazy { readAssetText("deck.schema.json") }
  private val userDeckMutationMutex = Mutex()

  suspend fun snapshot(): DeckLibrarySnapshot = withContext(Dispatchers.IO) {
    recoverPendingOperations()
    val catalog = loadCatalog()
    val installed = dao.installedDecks().mapNotNull { entity -> loadInstalled(entity) }
    DeckLibrarySnapshot(
      catalog = catalog,
      installed = installed,
      downloadHistoryTags = dao.downloadHistory().map { decodeTags(it.tagsJson) },
    )
  }

  suspend fun refreshCatalog(): CatalogRefreshResult = withContext(Dispatchers.IO) {
    val url = catalogUrl ?: return@withContext CatalogRefreshResult.NotConfigured
    recoverPendingOperations()
    val state = dao.catalogState()
    val response = try {
      remoteClient.get(url, state?.etag, state?.lastModified)
    } catch (error: Exception) {
      return@withContext CatalogRefreshResult.KeptCurrent(error.message ?: "network error")
    }
    when (response.statusCode) {
      304 -> {
        if (state == null || !store.exists(state.payloadName)) {
          CatalogRefreshResult.KeptCurrent("304 response without a verified cache")
        } else {
          dao.upsertCatalogState(
            state.copy(
              etag = response.etag ?: state.etag,
              lastModified = response.lastModified ?: state.lastModified,
              verifiedAtEpochMillis = clock(),
            ),
          )
          CatalogRefreshResult.NotModified
        }
      }
      200 -> {
        val body = response.body
          ?: return@withContext CatalogRefreshResult.KeptCurrent("200 response had no body")
        val catalog = try {
          decodeCatalog(body)
        } catch (error: Exception) {
          return@withContext CatalogRefreshResult.KeptCurrent(error.message ?: "invalid catalog")
        }
        commitCatalog(body, catalog, response.etag, response.lastModified)
        CatalogRefreshResult.Updated(catalog.catalogVersion)
      }
      else -> CatalogRefreshResult.KeptCurrent("HTTP ${response.statusCode}")
    }
  }

  suspend fun install(entry: CatalogDeck): InstalledDeck = withContext(Dispatchers.IO) {
    recoverPendingOperations()
    val (bytes, source) = fetchDeckPayload(entry)
    val deck = decodeDeck(bytes)
    requireDeckMatchesCatalog(deck, entry)

    val now = clock()
    val previous = dao.installedDeck(entry.deckId)
    val targetName = store.deckTargetName(entry.deckId)
    val stagedName = store.stage(bytes)
    val backupName = if (store.exists(targetName)) store.makeBackupName(targetName) else null
    val sha = AtomicPayloadStore.sha256(bytes)
    val tagsJson = encodeTags(entry.tags)
    val journal = RecoveryJournalEntity(
      operationId = "deck-install:${entry.deckId}",
      kind = JOURNAL_DECK_INSTALL,
      deckId = entry.deckId,
      version = deck.version,
      targetName = targetName,
      stagedName = stagedName,
      backupName = backupName,
      expectedSha256 = sha,
      source = source,
      official = deck.official,
      tagsJson = tagsJson,
      catalogVersion = null,
      etag = null,
      lastModified = null,
      startedAtEpochMillis = now,
    )
    database.withTransaction { dao.upsertJournal(journal) }
    store.replace(stagedName, targetName, backupName)
    database.withTransaction {
      dao.upsertInstalledDeck(
        InstalledDeckEntity(
          deckId = deck.deckId,
          version = deck.version,
          payloadName = targetName,
          payloadSha256 = sha,
          backupPayloadName = backupName,
          backupSha256 = previous?.payloadSha256,
          backupVersion = previous?.version,
          source = source,
          official = deck.official,
          installedAtEpochMillis = previous?.installedAtEpochMillis ?: now,
          updatedAtEpochMillis = now,
          lastPlayedAtEpochMillis = previous?.lastPlayedAtEpochMillis,
        ),
      )
      dao.upsertDownloadHistory(DownloadHistoryEntity(deck.deckId, tagsJson, now))
      dao.deleteJournal(journal.operationId)
    }
    if (previous?.backupPayloadName != backupName) store.delete(previous?.backupPayloadName)
    InstalledDeck(dao.installedDeck(deck.deckId)!!, deck)
  }

  suspend fun previewImportedDeck(
    stagingFile: File,
    sourceDisplayName: String,
  ): ImportedDeckPreview = withContext(Dispatchers.IO) {
    recoverPendingOperations()
    val imported = readStagedPiyoDeck(stagingFile)
    requireUserDeckIdentifierAvailable(imported)
    val installedEntity = dao.installedDeck(imported.deck.deckId)
    val installed = installedEntity?.let { loadInstalled(it) }
    if (installed != null && installed.metadata.source !in USER_DECK_SOURCES) {
      throw ImportedDeckException.InstalledSourceCollision
    }
    val conflict = ImportedDeckConflictPolicy.classify(
      incoming = ImportedDeckVersionSummary(imported.deck.version, imported.contentSha256),
      installed = installed?.let {
        ImportedDeckVersionSummary(it.metadata.version, it.metadata.payloadSha256)
      },
    )
    ImportedDeckPreview(
      deck = imported.deck,
      contentSha256 = imported.contentSha256,
      packageSha256 = PiyoDeckDigest.sha256Hex(stagingFile.readBytes()),
      sourceDisplayName = sourceDisplayName,
      conflict = conflict,
      installed = installed,
    )
  }

  suspend fun commitImportedDeck(
    stagingFile: File,
    expectedContentSha256: String,
    replaceConfirmed: Boolean,
    hasPiyokeyProAccess: Boolean,
  ): ImportedDeckCommitResult = withContext(Dispatchers.IO) {
    userDeckMutationMutex.withLock {
      recoverPendingOperations()
      val imported = readStagedPiyoDeck(stagingFile)
      requireUserDeckIdentifierAvailable(imported)
      if (imported.contentSha256 != expectedContentSha256) {
        throw ImportedDeckException.SourceChanged(expectedContentSha256, imported.contentSha256)
      }
      val previousEntity = dao.installedDeck(imported.deck.deckId)
      val previous = previousEntity?.let { loadInstalled(it) }
      if (previous != null && previous.metadata.source !in USER_DECK_SOURCES) {
        throw ImportedDeckException.InstalledSourceCollision
      }
      val conflict = ImportedDeckConflictPolicy.classify(
        incoming = ImportedDeckVersionSummary(imported.deck.version, imported.contentSha256),
        installed = previous?.let {
          ImportedDeckVersionSummary(it.metadata.version, it.metadata.payloadSha256)
        },
      )
      if (conflict == ImportedDeckConflict.IDENTICAL) {
        return@withLock ImportedDeckCommitResult.AlreadyInstalled(requireNotNull(previous))
      }
      if (ImportedDeckConflictPolicy.requiresDestructiveConfirmation(conflict) && !replaceConfirmed) {
        throw ImportedDeckException.ReplacementConfirmationRequired(conflict)
      }
      if (previousEntity == null && !hasPiyokeyProAccess) {
        val userDeckCount = dao.installedDecks().count { it.source in USER_DECK_SOURCES }
        if (userDeckCount >= PiyokeyProPolicy.FREE_INSTALLED_USER_DECK_LIMIT) {
          throw ImportedDeckException.FreeUserDeckLimitReached
        }
      }

      val now = clock()
      val targetName = store.deckTargetName(imported.deck.deckId)
      val stagedName = store.stage(imported.deckData)
      val backupName = if (store.exists(targetName)) store.makeBackupName(targetName) else null
      val history = dao.userDeckHistory(imported.deck.deckId)
      val journal = RecoveryJournalEntity(
        operationId = "deck-install:${imported.deck.deckId}",
        kind = JOURNAL_DECK_INSTALL,
        deckId = imported.deck.deckId,
        version = imported.deck.version,
        targetName = targetName,
        stagedName = stagedName,
        backupName = backupName,
        expectedSha256 = imported.contentSha256,
        source = SOURCE_IMPORTED,
        official = false,
        tagsJson = null,
        catalogVersion = null,
        etag = null,
        lastModified = null,
        startedAtEpochMillis = now,
      )
      database.withTransaction { dao.upsertJournal(journal) }
      store.replace(stagedName, targetName, backupName)
      database.withTransaction {
        dao.upsertInstalledDeck(
          InstalledDeckEntity(
            deckId = imported.deck.deckId,
            version = imported.deck.version,
            payloadName = targetName,
            payloadSha256 = imported.contentSha256,
            backupPayloadName = backupName,
            backupSha256 = previousEntity?.payloadSha256,
            backupVersion = previousEntity?.version,
            source = SOURCE_IMPORTED,
            official = false,
            installedAtEpochMillis = previousEntity?.installedAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            lastPlayedAtEpochMillis = previousEntity?.lastPlayedAtEpochMillis
              ?: history?.lastPlayedAtEpochMillis,
          ),
        )
        dao.upsertUserDeckHistory(
          UserDeckHistoryEntity(
            deckId = imported.deck.deckId,
            firstImportedAtEpochMillis = history?.firstImportedAtEpochMillis ?: now,
            lastImportedAtEpochMillis = now,
            lastDeletedAtEpochMillis = null,
            lastVersion = imported.deck.version,
            lastContentSha256 = imported.contentSha256,
            lastPlayedAtEpochMillis = previousEntity?.lastPlayedAtEpochMillis
              ?: history?.lastPlayedAtEpochMillis,
          ),
        )
        dao.deleteJournal(journal.operationId)
      }
      if (previousEntity?.backupPayloadName != backupName) store.delete(previousEntity?.backupPayloadName)
      ImportedDeckCommitResult.Installed(
        deck = InstalledDeck(requireNotNull(dao.installedDeck(imported.deck.deckId)), imported.deck),
        replacedExisting = previousEntity != null,
      )
    }
  }

  suspend fun exportUserDeck(deckId: String): ByteArray = withContext(Dispatchers.IO) {
    userDeckMutationMutex.withLock {
      recoverPendingOperations()
      val entity = dao.installedDeck(deckId) ?: throw ImportedDeckException.ExportRequiresUserDeck
      if (entity.source !in USER_DECK_SOURCES) throw ImportedDeckException.ExportRequiresUserDeck
      val installed = loadInstalled(entity) ?: throw ImportedDeckException.ExportRequiresUserDeck
      PiyoDeckPackageWriter.write(installed.deck, deckSchema)
    }
  }

  /**
   * Converts a conflicting import into a new local deck without replacing the installed deck or
   * borrowing its progress. Deck Maker entitlement is deliberately enforced by the caller at the
   * paid mutation boundary; this repository method still revalidates the staged bytes atomically.
   */
  suspend fun commitImportedDeckAsSeparateCopy(
    stagingFile: File,
    expectedContentSha256: String,
    language: UserDeckLanguage,
  ): InstalledDeck = withContext(Dispatchers.IO) {
    userDeckMutationMutex.withLock {
      recoverPendingOperations()
      val imported = readStagedPiyoDeck(stagingFile)
      requireUserDeckIdentifierAvailable(imported)
      if (imported.contentSha256 != expectedContentSha256) {
        throw ImportedDeckException.SourceChanged(expectedContentSha256, imported.contentSha256)
      }
      val installedEntity = dao.installedDeck(imported.deck.deckId)
      val installed = installedEntity?.let { loadInstalled(it) }
      if (installed != null && installed.metadata.source !in USER_DECK_SOURCES) {
        throw ImportedDeckException.InstalledSourceCollision
      }
      val conflict = ImportedDeckConflictPolicy.classify(
        incoming = ImportedDeckVersionSummary(imported.deck.version, imported.contentSha256),
        installed = installed?.let {
          ImportedDeckVersionSummary(it.metadata.version, it.metadata.payloadSha256)
        },
      )
      if (!ImportedDeckConflictPolicy.requiresDestructiveConfirmation(conflict)) {
        throw ImportedDeckException.SeparateCopyRequiresConflict(conflict)
      }

      val nowMillis = clock()
      val draft = UserDeckDraft.editing(imported.deck).asSeparateCopy(Instant.ofEpochMilli(nowMillis))
      val deck = draft.validatedDeck(Instant.ofEpochMilli(nowMillis), language)
      if (dao.installedDeck(deck.deckId) != null || loadCatalog().decks.any { it.deckId == deck.deckId }) {
        throw UserDeckEditCommitException.IdentifierCollision
      }
      val bytes = DeckKitJson.encodeDeck(deck).toByteArray(StandardCharsets.UTF_8)
      val validatedDeck = decodeDeck(bytes)
      val targetName = store.deckTargetName(deck.deckId)
      val stagedName = store.stage(bytes)
      val sha = AtomicPayloadStore.sha256(bytes)
      val journal = RecoveryJournalEntity(
        operationId = "deck-install:${deck.deckId}",
        kind = JOURNAL_DECK_INSTALL,
        deckId = deck.deckId,
        version = 1,
        targetName = targetName,
        stagedName = stagedName,
        backupName = null,
        expectedSha256 = sha,
        source = SOURCE_CREATED,
        official = false,
        tagsJson = null,
        catalogVersion = null,
        etag = null,
        lastModified = null,
        startedAtEpochMillis = nowMillis,
      )
      database.withTransaction { dao.upsertJournal(journal) }
      store.replace(stagedName, targetName, null)
      database.withTransaction {
        dao.upsertInstalledDeck(
          InstalledDeckEntity(
            deckId = deck.deckId,
            version = 1,
            payloadName = targetName,
            payloadSha256 = sha,
            backupPayloadName = null,
            backupSha256 = null,
            backupVersion = null,
            source = SOURCE_CREATED,
            official = false,
            installedAtEpochMillis = nowMillis,
            updatedAtEpochMillis = nowMillis,
            lastPlayedAtEpochMillis = null,
          ),
        )
        dao.upsertUserDeckHistory(
          UserDeckHistoryEntity(
            deckId = deck.deckId,
            firstImportedAtEpochMillis = nowMillis,
            lastImportedAtEpochMillis = nowMillis,
            lastDeletedAtEpochMillis = null,
            lastVersion = 1,
            lastContentSha256 = sha,
            lastPlayedAtEpochMillis = null,
          ),
        )
        dao.deleteJournal(journal.operationId)
      }
      InstalledDeck(requireNotNull(dao.installedDeck(deck.deckId)), validatedDeck)
    }
  }

  suspend fun loadUserDeckDraft(): ActiveUserDeckDraft? = withContext(Dispatchers.IO) {
    userDeckMutationMutex.withLock { userDeckDraftStore.load() }
  }

  suspend fun saveUserDeckDraft(draft: UserDeckDraft): ActiveUserDeckDraft =
    withContext(Dispatchers.IO) {
      userDeckMutationMutex.withLock {
        userDeckDraftStore.save(draft, Instant.ofEpochMilli(clock()))
      }
    }

  suspend fun discardUserDeckDraft(draftId: String? = null): Boolean = withContext(Dispatchers.IO) {
    userDeckMutationMutex.withLock {
      if (draftId == null) {
        userDeckDraftStore.clear()
        true
      } else {
        userDeckDraftStore.clear(draftId)
      }
    }
  }

  suspend fun commitUserDeckDraft(
    active: ActiveUserDeckDraft,
    language: UserDeckLanguage,
  ): InstalledDeck = withContext(Dispatchers.IO) {
    userDeckMutationMutex.withLock {
      recoverPendingOperations()
      val nowMillis = clock()
      val deck = active.draft.validatedDeck(Instant.ofEpochMilli(nowMillis), language)
      val bytes = DeckKitJson.encodeDeck(deck).toByteArray(StandardCharsets.UTF_8)
      // Re-decode through the installed-content boundary before touching persistent state.
      val validatedDeck = decodeDeck(bytes)
      val existing = dao.installedDeck(deck.deckId)
      when (active.draft.origin) {
        UserDeckDraftOrigin.Editing -> {
          if (existing == null || existing.source !in USER_DECK_SOURCES ||
            existing.version != active.draft.baseVersion
          ) {
            throw UserDeckEditCommitException.SourceChanged(
              expectedVersion = active.draft.baseVersion,
              actualVersion = existing?.version,
            )
          }
        }
        UserDeckDraftOrigin.New,
        is UserDeckDraftOrigin.OfficialCopy,
        -> if (existing != null || loadCatalog().decks.any { it.deckId == deck.deckId }) {
          throw UserDeckEditCommitException.IdentifierCollision
        }
      }

      val targetName = store.deckTargetName(deck.deckId)
      val stagedName = store.stage(bytes)
      val backupName = if (store.exists(targetName)) store.makeBackupName(targetName) else null
      val sha = AtomicPayloadStore.sha256(bytes)
      val history = dao.userDeckHistory(deck.deckId)
      val journal = RecoveryJournalEntity(
        operationId = "deck-install:${deck.deckId}",
        kind = JOURNAL_DECK_INSTALL,
        deckId = deck.deckId,
        version = deck.version,
        targetName = targetName,
        stagedName = stagedName,
        backupName = backupName,
        expectedSha256 = sha,
        source = SOURCE_CREATED,
        official = false,
        tagsJson = null,
        catalogVersion = null,
        etag = null,
        lastModified = null,
        startedAtEpochMillis = nowMillis,
        derivedFromDeckId = active.draft.derivedFromDeckId,
      )
      database.withTransaction {
        // This is the authoritative base-version check at commit time.
        if (active.draft.origin == UserDeckDraftOrigin.Editing) {
          val current = dao.installedDeck(deck.deckId)
          if (current == null || current.source !in USER_DECK_SOURCES ||
            current.version != active.draft.baseVersion
          ) {
            throw UserDeckEditCommitException.SourceChanged(
              active.draft.baseVersion,
              current?.version,
            )
          }
        }
        dao.upsertJournal(journal)
      }
      store.replace(stagedName, targetName, backupName)
      database.withTransaction {
        dao.upsertInstalledDeck(
          InstalledDeckEntity(
            deckId = deck.deckId,
            version = deck.version,
            payloadName = targetName,
            payloadSha256 = sha,
            backupPayloadName = backupName,
            backupSha256 = existing?.payloadSha256,
            backupVersion = existing?.version,
            source = SOURCE_CREATED,
            official = false,
            installedAtEpochMillis = existing?.installedAtEpochMillis ?: nowMillis,
            updatedAtEpochMillis = nowMillis,
            lastPlayedAtEpochMillis = existing?.lastPlayedAtEpochMillis
              ?: history?.lastPlayedAtEpochMillis,
            derivedFromDeckId = active.draft.derivedFromDeckId ?: existing?.derivedFromDeckId,
          ),
        )
        dao.upsertUserDeckHistory(
          UserDeckHistoryEntity(
            deckId = deck.deckId,
            firstImportedAtEpochMillis = history?.firstImportedAtEpochMillis ?: nowMillis,
            lastImportedAtEpochMillis = nowMillis,
            lastDeletedAtEpochMillis = null,
            lastVersion = deck.version,
            lastContentSha256 = sha,
            lastPlayedAtEpochMillis = existing?.lastPlayedAtEpochMillis
              ?: history?.lastPlayedAtEpochMillis,
          ),
        )
        dao.deleteJournal(journal.operationId)
      }
      if (existing?.backupPayloadName != backupName) store.delete(existing?.backupPayloadName)
      userDeckDraftStore.clear(active.draftId)
      InstalledDeck(requireNotNull(dao.installedDeck(deck.deckId)), validatedDeck)
    }
  }

  suspend fun delete(deckId: String) = withContext(Dispatchers.IO) {
    userDeckMutationMutex.withLock {
      recoverPendingOperations()
      val entity = dao.installedDeck(deckId) ?: return@withLock
      val now = clock()
      val backupName = store.makeBackupName(entity.payloadName)
      val journal = RecoveryJournalEntity(
        operationId = "deck-delete:$deckId",
        kind = JOURNAL_DECK_DELETE,
        deckId = deckId,
        version = entity.version,
        targetName = entity.payloadName,
        stagedName = null,
        backupName = backupName,
        expectedSha256 = entity.payloadSha256,
        source = entity.source,
        official = entity.official,
        tagsJson = null,
        catalogVersion = null,
        etag = null,
        lastModified = null,
        startedAtEpochMillis = now,
      )
      database.withTransaction { dao.upsertJournal(journal) }
      store.moveToBackup(entity.payloadName, backupName)
      database.withTransaction {
        if (entity.source in USER_DECK_SOURCES) {
          val history = dao.userDeckHistory(deckId)
          dao.upsertUserDeckHistory(
            UserDeckHistoryEntity(
              deckId = deckId,
              firstImportedAtEpochMillis = history?.firstImportedAtEpochMillis
                ?: entity.installedAtEpochMillis,
              lastImportedAtEpochMillis = history?.lastImportedAtEpochMillis
                ?: entity.updatedAtEpochMillis,
              lastDeletedAtEpochMillis = now,
              lastVersion = entity.version,
              lastContentSha256 = entity.payloadSha256,
              lastPlayedAtEpochMillis = entity.lastPlayedAtEpochMillis
                ?: history?.lastPlayedAtEpochMillis,
            ),
          )
        }
        dao.deleteInstalledDeck(deckId)
        dao.deleteJournal(journal.operationId)
      }
      store.delete(backupName)
      store.delete(entity.backupPayloadName)
    }
  }

  suspend fun markPlayed(deckId: String) = withContext(Dispatchers.IO) {
    dao.markPlayed(deckId, clock())
  }

  suspend fun bundledFlowDecks(): List<Deck> = withContext(Dispatchers.IO) {
    FLOW_PRESET_PATHS.map { path -> decodeDeck(readAssetBytes(path)) }
  }

  suspend fun bundledGameDecks(mode: String): List<Deck> = withContext(Dispatchers.IO) {
    val paths = GAME_PRESET_PATHS[mode] ?: error("Unsupported bundled game mode: $mode")
    paths.map { path -> decodeDeck(readAssetBytes(path)) }
  }

  suspend fun bundledSpacingPassages(): List<SpacingPassage> = withContext(Dispatchers.IO) {
    val root = tagsJson.parseToJsonElement(readAssetBytes(SPACING_PASSAGES_PATH).toString(StandardCharsets.UTF_8)).jsonObject
    require(root.getValue("schema_version").jsonPrimitive.int == 1) { "Unsupported spacing passage schema" }
    root.getValue("passages").jsonArray.map { element ->
      val passage = element.jsonObject
      SpacingPassage(
        id = passage.getValue("id").jsonPrimitive.content,
        level = passage.getValue("level").jsonPrimitive.int,
        text = passage.getValue("text").jsonPrimitive.content,
      )
    }.also { passages ->
      require(passages.map(SpacingPassage::level) == (1..6).toList()) { "Spacing passages must contain ordered levels 1 through 6" }
      require(passages.all { it.characterCount in 100..200 }) { "Spacing passages must contain 100 to 200 characters" }
      require(passages.last().characterCount >= 180) { "Spacing level 6 must contain at least 180 characters" }
    }
  }

  suspend fun saveFlowRecord(
    record: FlowGameRecord,
    deckItems: List<DeckItem> = emptyList(),
    reviewMistakeCounts: Map<String, Int> = emptyMap(),
    inputMode: String = INPUT_MODE_BUILTIN,
    isWeeklyCup: Boolean = false,
  ): SavedGameResult = withContext(Dispatchers.IO) {
    database.withTransaction {
      val storageInputMode = if (isWeeklyCup) INPUT_MODE_WEEKLY_CUP else inputMode
      val previous = dao.deckProgress(record.deckId, GAME_MODE_FLOW, storageInputMode)
      val isNewBest = previous == null || record.score > previous.bestScore
      dao.insertGameRecord(
        GameRecordEntity(
          recordId = UUID.randomUUID().toString(),
          mode = GAME_MODE_FLOW,
          deckId = record.deckId,
          course = record.course,
          score = record.score,
          maxCombo = record.maxCombo,
          accuracyPercent = record.accuracyPercent,
          inputMode = storageInputMode,
          correctJamoCount = record.correctJamoCount,
          charactersPerMinute = record.charactersPerMinute,
          mistakeCount = record.mistakeCount,
          completedItemCount = record.completedItemCount,
          missedItemCount = record.missedItemCount,
          playDurationMillis = record.playDurationMillis,
          playedAtEpochMillis = record.playedAtEpochMillis,
        ),
      )
      val progress = DeckProgressEntity(
        deckId = record.deckId,
        mode = GAME_MODE_FLOW,
        inputMode = storageInputMode,
        plays = (previous?.plays ?: 0) + 1,
        bestScore = maxOf(previous?.bestScore ?: 0, record.score),
        bestAccuracyPercent = maxOf(previous?.bestAccuracyPercent ?: 0.0, record.accuracyPercent),
        lastPlayedAtEpochMillis = record.playedAtEpochMillis,
      )
      dao.upsertDeckProgress(progress)
      recordAcceptedJamoInTransaction(record.correctJamoCount, record.playedAtEpochMillis)
      if (isNewBest) {
        PlayGamesPolicy.leaderboard(
          PlayGamesResultIdentity(GAME_MODE_FLOW, record.deckId, inputMode, isWeeklyCup),
        )?.let { board -> enqueuePlayGamesScoreInTransaction(board.storageKey, record.score.toLong(), record.playedAtEpochMillis) }
      }
      val itemsById = deckItems.associateBy(DeckItem::id)
      reviewMistakeCounts.forEach { (itemId, count) ->
        val item = itemsById[itemId] ?: return@forEach
        val reviewId = "${record.deckId}::$itemId"
        var review = dao.reviewItem(reviewId)?.toModel()
        repeat(count.coerceAtLeast(1)) {
          review = ReviewPolicy.recordMistake(review, item, record.deckId, record.playedAtEpochMillis).item
        }
        review?.let { dao.upsertReviewItem(it.toEntity()) }
      }
      recordActivityInTransaction(
        day = JstDay.fromEpochMillis(record.playedAtEpochMillis),
        activity = RetentionActivity.GAME,
        atEpochMillis = record.playedAtEpochMillis,
      )
      SavedGameResult(progress, isNewBest)
    }
  }

  suspend fun flowProgress(deckId: String, inputMode: String = INPUT_MODE_BUILTIN): DeckProgressEntity? = withContext(Dispatchers.IO) {
    dao.deckProgress(deckId, GAME_MODE_FLOW, inputMode)
  }

  suspend fun saveGameRecord(
    record: GameSessionRecord,
    deckItems: List<DeckItem> = emptyList(),
    reviewMistakeCounts: Map<String, Int> = emptyMap(),
  ): SavedGameResult = withContext(Dispatchers.IO) {
    database.withTransaction {
      val previous = dao.deckProgress(record.deckId, record.mode, record.inputMode)
      val isNewBest = previous == null || record.score > previous.bestScore
      dao.insertGameRecord(
        GameRecordEntity(
          recordId = UUID.randomUUID().toString(),
          mode = record.mode,
          deckId = record.deckId,
          course = record.course,
          score = record.score,
          maxCombo = record.maxCombo,
          accuracyPercent = record.accuracyPercent,
          inputMode = record.inputMode,
          correctJamoCount = record.correctJamoCount,
          charactersPerMinute = record.ratePerMinute,
          mistakeCount = record.mistakeCount,
          completedItemCount = record.completedItemCount,
          missedItemCount = record.missedItemCount,
          playDurationMillis = record.playDurationMillis,
          playedAtEpochMillis = record.playedAtEpochMillis,
        ),
      )
      val progress = DeckProgressEntity(
        deckId = record.deckId,
        mode = record.mode,
        inputMode = record.inputMode,
        plays = (previous?.plays ?: 0) + 1,
        bestScore = maxOf(previous?.bestScore ?: 0, record.score),
        bestAccuracyPercent = maxOf(previous?.bestAccuracyPercent ?: 0.0, record.accuracyPercent),
        lastPlayedAtEpochMillis = record.playedAtEpochMillis,
      )
      dao.upsertDeckProgress(progress)
      if (record.mode != "spacing") {
        recordAcceptedJamoInTransaction(record.correctJamoCount, record.playedAtEpochMillis)
      }
      if (isNewBest) {
        PlayGamesPolicy.leaderboard(
          PlayGamesResultIdentity(record.mode, record.deckId, record.inputMode),
        )?.let { board -> enqueuePlayGamesScoreInTransaction(board.storageKey, record.score.toLong(), record.playedAtEpochMillis) }
      }
      val itemsById = deckItems.associateBy(DeckItem::id)
      reviewMistakeCounts.forEach { (itemId, count) ->
        val item = itemsById[itemId] ?: return@forEach
        val reviewId = "${record.deckId}::$itemId"
        var review = dao.reviewItem(reviewId)?.toModel()
        repeat(count.coerceAtLeast(1)) { review = ReviewPolicy.recordMistake(review, item, record.deckId, record.playedAtEpochMillis).item }
        review?.let { dao.upsertReviewItem(it.toEntity()) }
      }
      recordActivityInTransaction(JstDay.fromEpochMillis(record.playedAtEpochMillis), RetentionActivity.GAME, record.playedAtEpochMillis)
      SavedGameResult(progress, isNewBest)
    }
  }

  suspend fun gameProgress(mode: String, deckId: String, inputMode: String): DeckProgressEntity? = withContext(Dispatchers.IO) {
    dao.deckProgress(deckId, mode, inputMode)
  }

  suspend fun learningSnapshot(): LearningSnapshot = withContext(Dispatchers.IO) {
    val progress = dao.userProgress().associateBy(UserProgressEntity::stageId)
    val active = dao.curriculumSession()?.let { entity ->
      CurriculumActiveSession(entity.stageId, entity.toCheckpoint())
    }
    val activities = dao.streakDays().associate { entity ->
      JstDay(entity.day) to decodeActivities(entity.activitiesJson)
    }
    LearningSnapshot(
      progress = progress,
      activeSession = active,
      reviewItems = dao.reviewItems().map(ReviewItemEntity::toModel),
      activitiesByDay = activities,
      unlockedRewards = dao.retentionRewards().mapTo(mutableSetOf(), RetentionRewardEntity::threshold),
      reminder = dao.reminderPreference() ?: ReminderPreferenceEntity(isEnabled = false, hour = 20, minute = 0),
    )
  }

  suspend fun saveCurriculumCheckpoint(stageId: String, checkpoint: PracticeSessionCheckpoint) =
    withContext(Dispatchers.IO) {
      dao.upsertCurriculumSession(checkpoint.toEntity(stageId, clock()))
    }

  suspend fun clearCurriculumCheckpoint(stageId: String? = null) = withContext(Dispatchers.IO) {
    val active = dao.curriculumSession()
    if (stageId == null || active?.stageId == stageId) dao.clearCurriculumSession()
  }

  suspend fun finishCurriculumStage(
    stageId: String,
    accuracyPercent: Double,
    charactersPerMinute: Double,
    sessionDay: JstDay,
    completedAtEpochMillis: Long = clock(),
  ): Int = withContext(Dispatchers.IO) {
    val stars = CurriculumPolicy.stars(accuracyPercent, charactersPerMinute)
    database.withTransaction {
      dao.clearCurriculumSession()
      if (stars > 0) {
        val previous = dao.userProgress(stageId)
        dao.upsertUserProgress(
          UserProgressEntity(
            stageId = stageId,
            stars = maxOf(previous?.stars ?: 0, stars),
            bestAccuracyPercent = maxOf(previous?.bestAccuracyPercent ?: 0.0, accuracyPercent),
            completedAtEpochMillis = previous?.completedAtEpochMillis ?: completedAtEpochMillis,
          ),
        )
        recordActivityInTransaction(sessionDay, RetentionActivity.CURRICULUM, completedAtEpochMillis)
        updateChapterAchievementsInTransaction(completedAtEpochMillis)
      }
    }
    stars
  }

  suspend fun recordDailyCompletion(
    sessionDay: JstDay,
    completedAtEpochMillis: Long = clock(),
  ) = withContext(Dispatchers.IO) {
    database.withTransaction {
      recordActivityInTransaction(sessionDay, RetentionActivity.DAILY_CHALLENGE, completedAtEpochMillis)
    }
  }

  suspend fun recordPracticeAcceptedJamo(
    eventId: String,
    acceptedJamoCount: Int,
    atEpochMillis: Long = clock(),
  ) = withContext(Dispatchers.IO) {
    require(eventId.isNotBlank())
    require(acceptedJamoCount >= 0)
    database.withTransaction {
      val inserted = dao.insertPracticeJamoEvent(
        PracticeJamoEventEntity(eventId, acceptedJamoCount, atEpochMillis),
      )
      if (inserted != -1L) recordAcceptedJamoInTransaction(acceptedJamoCount, atEpochMillis)
    }
  }

  suspend fun recordPracticeReview(
    sourceDeckId: String,
    items: List<DeckItem>,
    resolutions: List<PracticeItemResolution>,
    isReviewSession: Boolean,
    atEpochMillis: Long = clock(),
  ) = withContext(Dispatchers.IO) {
    database.withTransaction {
      resolutions.forEach { resolution ->
        val item = items.getOrNull(resolution.itemIndex) ?: return@forEach
        val reviewId = "$sourceDeckId::${item.id}"
        val existing = dao.reviewItem(reviewId)?.toModel()
        val reduction = when {
          resolution.hadMistake -> ReviewPolicy.recordMistake(existing, item, sourceDeckId, atEpochMillis)
          isReviewSession -> ReviewPolicy.recordPerfect(existing, atEpochMillis)
          else -> null
        }
        reduction?.item?.let { dao.upsertReviewItem(it.toEntity()) }
      }
    }
  }

  suspend fun addReviewItemManually(
    item: DeckItem,
    sourceDeckId: String,
    atEpochMillis: Long = clock(),
  ) = withContext(Dispatchers.IO) {
    val reviewId = "$sourceDeckId::${item.id}"
    val reduction = ReviewPolicy.addManually(dao.reviewItem(reviewId)?.toModel(), item, sourceDeckId, atEpochMillis)
    reduction.item?.let { dao.upsertReviewItem(it.toEntity()) }
  }

  suspend fun removeReviewItemManually(reviewId: String) = withContext(Dispatchers.IO) {
    dao.deleteReviewItem(reviewId)
  }

  suspend fun saveReminderPreference(isEnabled: Boolean, hour: Int, minute: Int) = withContext(Dispatchers.IO) {
    require(hour in 0..23 && minute in 0..59)
    dao.upsertReminderPreference(ReminderPreferenceEntity(isEnabled = isEnabled, hour = hour, minute = minute))
  }

  private suspend fun recordActivityInTransaction(
    day: JstDay,
    activity: RetentionActivity,
    atEpochMillis: Long,
  ) {
    val current = dao.streakDay(day.value)?.let { decodeActivities(it.activitiesJson) }.orEmpty()
    if (activity !in current) {
      dao.upsertStreakDay(StreakDayEntity(day.value, encodeActivities(current + activity)))
    }
    val completed = dao.streakDays().mapTo(mutableSetOf()) { JstDay(it.day) }
    val existingRewards = dao.retentionRewards().mapTo(mutableSetOf(), RetentionRewardEntity::threshold)
    RetentionPolicy.newlyUnlockedRewards(completed, existingRewards).forEach { threshold ->
      dao.insertRetentionReward(RetentionRewardEntity(threshold, atEpochMillis))
    }
    val longest = RetentionPolicy.streak(completed, day).longest.toLong()
    updatePlayGamesAchievementInTransaction(PlayGamesAchievementKey.STREAK_30, longest, atEpochMillis)
  }

  private suspend fun enqueuePlayGamesScoreInTransaction(boardKey: String, score: Long, atEpochMillis: Long) {
    val previous = dao.pendingPlayGamesScore(boardKey)
    if (previous == null || score > previous.bestScore) {
      dao.upsertPlayGamesScore(PlayGamesScoreOutboxEntity(boardKey, score, atEpochMillis))
    }
  }

  private suspend fun recordAcceptedJamoInTransaction(count: Int, atEpochMillis: Long) {
    if (count <= 0) return
    val total = (dao.lifetimeStats()?.acceptedJamoCount ?: 0L) + count
    dao.upsertLifetimeStats(LifetimeStatsEntity(acceptedJamoCount = total))
    updatePlayGamesAchievementInTransaction(PlayGamesAchievementKey.JAMO_12000, total, atEpochMillis)
  }

  private suspend fun updateChapterAchievementsInTransaction(atEpochMillis: Long) {
    val completedStageIds = dao.userProgress().mapTo(mutableSetOf(), UserProgressEntity::stageId)
    listOf(1 to PlayGamesAchievementKey.CHAPTER_ONE, 3 to PlayGamesAchievementKey.CHAPTER_THREE, 6 to PlayGamesAchievementKey.CHAPTER_SIX)
      .forEach { (chapterNumber, key) ->
        val chapter = app.piyokey.core.retention.CurriculumCatalog.chapters.first { it.number == chapterNumber }
        val value = if (CurriculumPolicy.isChapterCompleted(chapter, completedStageIds)) 1L else 0L
        updatePlayGamesAchievementInTransaction(key, value, atEpochMillis)
      }
  }

  private suspend fun updatePlayGamesAchievementInTransaction(
    key: PlayGamesAchievementKey,
    value: Long,
    atEpochMillis: Long,
  ) {
    val previous = dao.playGamesAchievementProgress(key.storageKey)
    val next = maxOf(previous?.currentValue ?: 0L, value.coerceAtMost(key.target))
    if (previous == null || next > previous.currentValue) {
      dao.upsertPlayGamesAchievementProgress(
        PlayGamesAchievementProgressEntity(
          achievementKey = key.storageKey,
          currentValue = next,
          syncedValue = previous?.syncedValue ?: 0L,
          updatedAtEpochMillis = atEpochMillis,
        ),
      )
    }
  }

  suspend fun flowRankTuning(): FlowRankTuning = withContext(Dispatchers.IO) {
    val objectValue = Json.parseToJsonElement(readAssetText("game_rank_tuning.json")).jsonObject
    require(objectValue.getValue("schema_version").jsonPrimitive.int == 1)
    FlowRankTuning(
      accuracyWeight = objectValue.getValue("accuracy_weight").jsonPrimitive.double,
      speedWeight = objectValue.getValue("speed_weight").jsonPrimitive.double,
      speedCapCharactersPerMinute = objectValue.getValue("speed_cap_characters_per_minute").jsonPrimitive.double,
      sThreshold = objectValue.getValue("s_threshold").jsonPrimitive.double,
      aThreshold = objectValue.getValue("a_threshold").jsonPrimitive.double,
      bThreshold = objectValue.getValue("b_threshold").jsonPrimitive.double,
    )
  }

  private suspend fun loadCatalog(): Catalog {
    val state = dao.catalogState()
    if (state != null) {
      if (store.exists(state.payloadName)) {
        try {
          val bytes = store.read(state.payloadName)
          require(AtomicPayloadStore.sha256(bytes) == state.payloadSha256) { "Catalog cache hash mismatch" }
          return decodeCatalog(bytes)
        } catch (_: Exception) {
          store.quarantine(state.payloadName)
        }
      }
      val backup = loadCatalogBackup(state)
      if (backup != null) return backup
      dao.deleteCatalogState()
    }
    return decodeCatalog(readAssetBytes("catalog.json"))
  }

  private suspend fun loadInstalled(entity: InstalledDeckEntity): InstalledDeck? {
    return try {
      require(store.exists(entity.payloadName)) { "Installed payload missing" }
      val bytes = store.read(entity.payloadName)
      require(AtomicPayloadStore.sha256(bytes) == entity.payloadSha256) { "Installed payload hash mismatch" }
      val deck = decodeDeck(bytes)
      require(deck.deckId == entity.deckId && deck.version == entity.version)
      InstalledDeck(entity, deck)
    } catch (_: Exception) {
      if (store.exists(entity.payloadName)) store.quarantine(entity.payloadName)
      val backup = loadInstalledBackup(entity)
      if (backup != null) return backup
      dao.deleteInstalledDeck(entity.deckId)
      null
    }
  }

  private suspend fun fetchDeckPayload(entry: CatalogDeck): Pair<ByteArray, String> {
    if (catalogUrl != null) {
      val response = try {
        remoteClient.get(StaticContentUrlPolicy.resolveDeckUrl(catalogUrl, entry.fileUrl))
      } catch (error: Exception) {
        if (!entry.official) throw error else null
      }
      if (response?.statusCode == 200 && response.body != null) {
        return response.body to "remote"
      }
      if (!entry.official) error("Remote deck download failed with HTTP ${response?.statusCode}")
    }
    return readAssetBytes(entry.fileUrl) to "bundle"
  }

  private suspend fun commitCatalog(
    bytes: ByteArray,
    catalog: Catalog,
    etag: String?,
    lastModified: String?,
  ) {
    val now = clock()
    val previous = dao.catalogState()
    val targetName = store.catalogTargetName()
    val stagedName = store.stage(bytes)
    val backupName = if (store.exists(targetName)) store.makeBackupName(targetName) else null
    val sha = AtomicPayloadStore.sha256(bytes)
    val journal = RecoveryJournalEntity(
      operationId = "catalog-update",
      kind = JOURNAL_CATALOG_UPDATE,
      deckId = null,
      version = null,
      targetName = targetName,
      stagedName = stagedName,
      backupName = backupName,
      expectedSha256 = sha,
      source = null,
      official = null,
      tagsJson = null,
      catalogVersion = catalog.catalogVersion,
      etag = etag,
      lastModified = lastModified,
      startedAtEpochMillis = now,
    )
    database.withTransaction { dao.upsertJournal(journal) }
    store.replace(stagedName, targetName, backupName)
    database.withTransaction {
      dao.upsertCatalogState(journal.toCatalogState(now, previous))
      dao.deleteJournal(journal.operationId)
    }
    if (previous?.backupPayloadName != backupName) store.delete(previous?.backupPayloadName)
  }

  private suspend fun recoverPendingOperations() {
    dao.journals().forEach { journal ->
      when (journal.kind) {
        JOURNAL_DECK_INSTALL -> recoverDeckInstall(journal)
        JOURNAL_DECK_DELETE -> recoverDeckDelete(journal)
        JOURNAL_CATALOG_UPDATE -> recoverCatalogUpdate(journal)
        else -> dao.deleteJournal(journal.operationId)
      }
    }
  }

  private suspend fun recoverDeckInstall(journal: RecoveryJournalEntity) {
    val deckId = journal.deckId ?: return dao.deleteJournal(journal.operationId)
    val sha = journal.expectedSha256 ?: return dao.deleteJournal(journal.operationId)
    if (!targetMatches(journal.targetName, sha) && store.exists(journal.stagedName)) {
      store.replace(journal.stagedName!!, journal.targetName, journal.backupName)
    }
    if (targetMatches(journal.targetName, sha)) {
      val now = clock()
      val previous = dao.installedDeck(deckId)
      val source = requireNotNull(journal.source)
      val history = if (source in USER_DECK_SOURCES) dao.userDeckHistory(deckId) else null
      database.withTransaction {
        dao.upsertInstalledDeck(
          InstalledDeckEntity(
            deckId = deckId,
            version = requireNotNull(journal.version),
            payloadName = journal.targetName,
            payloadSha256 = sha,
            backupPayloadName = journal.backupName,
            backupSha256 = previous?.payloadSha256,
            backupVersion = previous?.version,
            source = source,
            official = requireNotNull(journal.official),
            installedAtEpochMillis = previous?.installedAtEpochMillis ?: journal.startedAtEpochMillis,
            updatedAtEpochMillis = now,
            lastPlayedAtEpochMillis = previous?.lastPlayedAtEpochMillis
              ?: history?.lastPlayedAtEpochMillis,
            derivedFromDeckId = journal.derivedFromDeckId ?: previous?.derivedFromDeckId,
          ),
        )
        journal.tagsJson?.let {
          dao.upsertDownloadHistory(DownloadHistoryEntity(deckId, it, now))
        }
        if (source in USER_DECK_SOURCES) {
          dao.upsertUserDeckHistory(
            UserDeckHistoryEntity(
              deckId = deckId,
              firstImportedAtEpochMillis = history?.firstImportedAtEpochMillis
                ?: journal.startedAtEpochMillis,
              lastImportedAtEpochMillis = now,
              lastDeletedAtEpochMillis = null,
              lastVersion = requireNotNull(journal.version),
              lastContentSha256 = sha,
              lastPlayedAtEpochMillis = previous?.lastPlayedAtEpochMillis
                ?: history?.lastPlayedAtEpochMillis,
            ),
          )
        }
        dao.deleteJournal(journal.operationId)
      }
      if (previous?.backupPayloadName != journal.backupName) {
        store.delete(previous?.backupPayloadName)
      }
    } else {
      journal.backupName?.let { store.restoreBackup(it, journal.targetName) }
      store.delete(journal.stagedName)
      dao.deleteJournal(journal.operationId)
    }
  }

  private suspend fun recoverDeckDelete(journal: RecoveryJournalEntity) {
    val deckId = journal.deckId ?: return dao.deleteJournal(journal.operationId)
    if (dao.installedDeck(deckId) == null) {
      store.delete(journal.backupName)
      dao.deleteJournal(journal.operationId)
    } else {
      journal.backupName?.let { store.restoreBackup(it, journal.targetName) }
      dao.deleteJournal(journal.operationId)
    }
  }

  private suspend fun recoverCatalogUpdate(journal: RecoveryJournalEntity) {
    val sha = journal.expectedSha256 ?: return dao.deleteJournal(journal.operationId)
    if (!targetMatches(journal.targetName, sha) && store.exists(journal.stagedName)) {
      store.replace(journal.stagedName!!, journal.targetName, journal.backupName)
    }
    if (targetMatches(journal.targetName, sha)) {
      try {
        decodeCatalog(store.read(journal.targetName))
        val previous = dao.catalogState()
        database.withTransaction {
          dao.upsertCatalogState(journal.toCatalogState(clock(), previous))
          dao.deleteJournal(journal.operationId)
        }
        if (previous?.backupPayloadName != journal.backupName) {
          store.delete(previous?.backupPayloadName)
        }
        return
      } catch (_: Exception) {
        store.quarantine(journal.targetName)
      }
    }
    journal.backupName?.let { store.restoreBackup(it, journal.targetName) }
    store.delete(journal.stagedName)
    dao.deleteJournal(journal.operationId)
  }

  private fun targetMatches(targetName: String, expectedSha: String): Boolean =
    store.exists(targetName) && runCatching { store.sha256(targetName) == expectedSha }.getOrDefault(false)

  private fun decodeCatalog(bytes: ByteArray): Catalog = DeckKitJson.decodeValidatedCatalog(
    bytes.toString(StandardCharsets.UTF_8),
    catalogSchema,
  )

  private fun decodeDeck(bytes: ByteArray): Deck = DeckKitJson.decodeValidatedDeck(
    bytes.toString(StandardCharsets.UTF_8),
    deckSchema,
  )

  private fun requireDeckMatchesCatalog(deck: Deck, entry: CatalogDeck) {
    require(deck.deckId == entry.deckId) { "Downloaded deck ID did not match catalog" }
    require(deck.version == entry.version) { "Downloaded deck version did not match catalog" }
    require(deck.official == entry.official && deck.items.size == entry.itemCount) {
      "Downloaded deck metadata did not match catalog"
    }
    require(deck.type == entry.type && deck.level == entry.level && deck.tags == entry.tags) {
      "Downloaded deck classification did not match catalog"
    }
  }

  private fun readAssetBytes(path: String): ByteArray = context.assets.open(path).use { it.readBytes() }

  private fun readAssetText(path: String): String =
    readAssetBytes(path).toString(StandardCharsets.UTF_8)

  private fun readStagedPiyoDeck(stagingFile: File): PiyoDeckPackage {
    val stagingRoot = File(context.cacheDir, PIYODECK_STAGING_DIRECTORY_NAME).canonicalFile
    val canonical = stagingFile.canonicalFile
    if (!canonical.path.startsWith(stagingRoot.path + File.separator) || !canonical.isFile) {
      throw ImportedDeckException.StagingFileRequired
    }
    if (canonical.length() > PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES) {
      throw app.piyokey.core.piyodeck.PiyoDeckImportException.PackageTooLarge(
        canonical.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        PiyoDeckPackageLimits.MAXIMUM_PACKAGE_BYTES,
      )
    }
    return PiyoDeckPackageReader.read(canonical.readBytes(), deckSchema)
  }

  private suspend fun requireUserDeckIdentifierAvailable(imported: PiyoDeckPackage) {
    if (loadCatalog().decks.any { it.deckId == imported.deck.deckId }) {
      throw ImportedDeckException.OfficialIdentifierCollision
    }
  }

  private suspend fun loadCatalogBackup(state: CatalogStateEntity): Catalog? {
    val backupName = state.backupPayloadName ?: return null
    val backupSha = state.backupSha256 ?: return null
    if (!targetMatches(backupName, backupSha)) return null
    return try {
      val backupBytes = store.read(backupName)
      val catalog = decodeCatalog(backupBytes)
      store.restoreBackup(backupName, state.payloadName)
      dao.upsertCatalogState(
        state.copy(
          catalogVersion = state.backupCatalogVersion ?: catalog.catalogVersion,
          payloadSha256 = backupSha,
          backupPayloadName = null,
          backupSha256 = null,
          backupCatalogVersion = null,
          etag = state.backupEtag,
          lastModified = state.backupLastModified,
          backupEtag = null,
          backupLastModified = null,
          verifiedAtEpochMillis = clock(),
        ),
      )
      catalog
    } catch (_: Exception) {
      store.quarantine(backupName)
      null
    }
  }

  private suspend fun loadInstalledBackup(entity: InstalledDeckEntity): InstalledDeck? {
    val backupName = entity.backupPayloadName ?: return null
    val backupSha = entity.backupSha256 ?: return null
    if (!targetMatches(backupName, backupSha)) return null
    return try {
      val backupBytes = store.read(backupName)
      val deck = decodeDeck(backupBytes)
      require(deck.deckId == entity.deckId)
      store.restoreBackup(backupName, entity.payloadName)
      val recovered = entity.copy(
        version = entity.backupVersion ?: deck.version,
        payloadSha256 = backupSha,
        backupPayloadName = null,
        backupSha256 = null,
        backupVersion = null,
        source = if (entity.source in USER_DECK_SOURCES) entity.source else "recovered",
        official = deck.official,
        updatedAtEpochMillis = clock(),
      )
      dao.upsertInstalledDeck(recovered)
      InstalledDeck(recovered, deck)
    } catch (_: Exception) {
      store.quarantine(backupName)
      null
    }
  }

  private fun RecoveryJournalEntity.toCatalogState(
    verifiedAt: Long,
    previous: CatalogStateEntity?,
  ): CatalogStateEntity =
    CatalogStateEntity(
      catalogVersion = requireNotNull(catalogVersion),
      payloadName = targetName,
      payloadSha256 = requireNotNull(expectedSha256),
      backupPayloadName = backupName,
      backupSha256 = previous?.payloadSha256,
      backupCatalogVersion = previous?.catalogVersion,
      backupEtag = previous?.etag,
      backupLastModified = previous?.lastModified,
      etag = etag,
      lastModified = lastModified,
      verifiedAtEpochMillis = verifiedAt,
    )

  companion object {
    private const val JOURNAL_DECK_INSTALL = "deck_install"
    private const val JOURNAL_DECK_DELETE = "deck_delete"
    private const val JOURNAL_CATALOG_UPDATE = "catalog_update"
    private const val SOURCE_IMPORTED = "imported"
    private const val SOURCE_CREATED = "created"
    private val USER_DECK_SOURCES = setOf(SOURCE_IMPORTED, SOURCE_CREATED)
    private val tagsJson = Json { isLenient = false }
    private const val GAME_MODE_FLOW = "flow"
    private const val INPUT_MODE_BUILTIN = "builtin"
    private const val INPUT_MODE_WEEKLY_CUP = "weekly_cup"
    private val FLOW_PRESET_PATHS = listOf(
      "decks/flow/flow_topik_beginner_v3.json",
      "decks/flow/flow_topik_intermediate_v3.json",
      "decks/flow/flow_topik_advanced_v3.json",
    )
    private const val SPACING_PASSAGES_PATH = "spacing_passages.json"
    private val GAME_PRESET_PATHS = mapOf(
      "flow" to FLOW_PRESET_PATHS,
      "acid_rain" to listOf(
        "decks/acid_rain/acid_rain_topik_beginner_v3.json",
        "decks/acid_rain/acid_rain_topik_intermediate_v3.json",
        "decks/acid_rain/acid_rain_topik_advanced_v3.json",
      ),
      "choseong" to listOf(
        "decks/choseong/choseong_topik_beginner_v3.json",
        "decks/choseong/choseong_topik_intermediate_v3.json",
        "decks/choseong/choseong_topik_advanced_v3.json",
      ),
      "word_match" to listOf(
        "decks/word_match/word_match_topik_beginner_v3.json",
        "decks/word_match/word_match_topik_intermediate_v3.json",
        "decks/word_match/word_match_topik_advanced_v3.json",
      ),
      "dictation" to listOf(
        "decks/dictation/dictation_topik_beginner_v3.json",
        "decks/dictation/dictation_topik_intermediate_v3.json",
        "decks/dictation/dictation_topik_advanced_v3.json",
      ),
    )

    fun create(
      context: Context,
      catalogUrl: String? = null,
      remoteClient: StaticContentClient = HttpStaticContentClient(),
      clock: () -> Long = System::currentTimeMillis,
    ): DeckRepository = DeckRepository(
      context = context.applicationContext,
      database = PiyokeyDatabase.open(context),
      remoteClient = remoteClient,
      catalogUrl = StaticContentUrlPolicy.parseCatalogUrl(catalogUrl),
      clock = clock,
    )

    internal fun createForTesting(
      context: Context,
      database: PiyokeyDatabase,
      remoteClient: StaticContentClient = HttpStaticContentClient(),
      catalogUrl: URL? = null,
      clock: () -> Long = System::currentTimeMillis,
    ): DeckRepository = DeckRepository(
      context = context.applicationContext,
      database = database,
      remoteClient = remoteClient,
      catalogUrl = catalogUrl,
      clock = clock,
    )

    internal fun encodeTags(tags: List<String>): String =
      JsonArray(tags.map(::JsonPrimitive)).toString()

    internal fun decodeTags(source: String): List<String> = tagsJson.parseToJsonElement(source)
      .jsonArray
      .map { it.jsonPrimitive.content }

    private fun encodeActivities(activities: Set<RetentionActivity>): String =
      JsonArray(activities.sortedBy(RetentionActivity::storageValue).map { JsonPrimitive(it.storageValue) }).toString()

    private fun decodeActivities(source: String): Set<RetentionActivity> =
      tagsJson.parseToJsonElement(source).jsonArray.mapTo(mutableSetOf()) { value ->
        RetentionActivity.entries.first { it.storageValue == value.jsonPrimitive.content }
      }
  }
}

private fun PracticeSessionCheckpoint.toEntity(stageId: String, updatedAt: Long): CurriculumSessionEntity =
  CurriculumSessionEntity(
    stageId = stageId,
    currentTargetIndex = currentTargetIndex,
    acceptedKeys = acceptedKeys,
    mistakeCount = mistakeCount,
    currentItemMistakeCount = currentItemMistakeCount,
    mistakenJamoIndicesJson = JsonArray(currentItemMistakenJamoIndices.sorted().map(::JsonPrimitive)).toString(),
    itemResolutionsJson = buildJsonArray {
      itemResolutions.forEach { resolution ->
        add(buildJsonObject {
          put("item_index", JsonPrimitive(resolution.itemIndex))
          put("mistake_count", JsonPrimitive(resolution.mistakeCount))
          put("mistaken_indices", JsonArray(resolution.mistakenJamoIndices.sorted().map(::JsonPrimitive)))
        })
      }
    }.toString(),
    activeDurationMillis = activeDurationMillis,
    updatedAtEpochMillis = updatedAt,
  )

private fun CurriculumSessionEntity.toCheckpoint(): PracticeSessionCheckpoint = PracticeSessionCheckpoint(
  currentTargetIndex = currentTargetIndex,
  acceptedKeys = acceptedKeys,
  mistakeCount = mistakeCount,
  currentItemMistakeCount = currentItemMistakeCount,
  currentItemMistakenJamoIndices = Json.parseToJsonElement(mistakenJamoIndicesJson).jsonArray
    .mapTo(mutableSetOf()) { it.jsonPrimitive.int },
  itemResolutions = Json.parseToJsonElement(itemResolutionsJson).jsonArray.map { value ->
    val item = value.jsonObject
    PracticeItemResolution(
      itemIndex = item.getValue("item_index").jsonPrimitive.int,
      mistakeCount = item.getValue("mistake_count").jsonPrimitive.int,
      mistakenJamoIndices = item.getValue("mistaken_indices").jsonArray
        .mapTo(mutableSetOf()) { it.jsonPrimitive.int },
    )
  },
  activeDurationMillis = activeDurationMillis,
)

private fun ReviewItem.toEntity(): ReviewItemEntity = ReviewItemEntity(
  reviewId = id,
  itemId = item.id,
  sourceDeckId = sourceDeckId,
  ko = item.ko,
  readingJa = item.readingJa,
  meaningJa = item.meaningJa,
  localizationsJson = item.localizations?.let { localizations ->
    buildJsonObject {
      localizations.toSortedMap().forEach { (language, localization) ->
        put(language, buildJsonObject {
          put("meaning", JsonPrimitive(localization.meaning))
          put("reading", JsonPrimitive(localization.reading))
        })
      }
    }.toString()
  },
  missCount = missCount,
  consecutivePerfect = consecutivePerfect,
  addedAtEpochMillis = addedAtEpochMillis,
  graduatedAtEpochMillis = graduatedAtEpochMillis,
)

private fun ReviewItemEntity.toModel(): ReviewItem {
  val localizations = localizationsJson?.let { source ->
    Json.parseToJsonElement(source).jsonObject.mapValues { (_, value) ->
      val objectValue = value.jsonObject
      DeckItemLocalization(
        meaning = objectValue.getValue("meaning").jsonPrimitive.content,
        reading = objectValue.getValue("reading").jsonPrimitive.content,
      )
    }
  }
  return ReviewItem(
    item = DeckItem(itemId, ko, readingJa, meaningJa, null, localizations),
    sourceDeckId = sourceDeckId,
    missCount = missCount,
    consecutivePerfect = consecutivePerfect,
    addedAtEpochMillis = addedAtEpochMillis,
    graduatedAtEpochMillis = graduatedAtEpochMillis,
  )
}
