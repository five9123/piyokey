package app.piyokey.core.data

import android.content.Context
import androidx.room.withTransaction
import app.piyokey.core.deckkit.Catalog
import app.piyokey.core.deckkit.CatalogDeck
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckKitJson
import app.piyokey.core.game.FlowGameRecord
import app.piyokey.core.game.FlowRankTuning
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
  private val catalogSchema by lazy { readAssetText("catalog.schema.json") }
  private val deckSchema by lazy { readAssetText("deck.schema.json") }

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

  suspend fun delete(deckId: String) = withContext(Dispatchers.IO) {
    recoverPendingOperations()
    val entity = dao.installedDeck(deckId) ?: return@withContext
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
      startedAtEpochMillis = clock(),
    )
    database.withTransaction { dao.upsertJournal(journal) }
    store.moveToBackup(entity.payloadName, backupName)
    database.withTransaction {
      dao.deleteInstalledDeck(deckId)
      dao.deleteJournal(journal.operationId)
    }
    store.delete(backupName)
    store.delete(entity.backupPayloadName)
  }

  suspend fun markPlayed(deckId: String) = withContext(Dispatchers.IO) {
    dao.markPlayed(deckId, clock())
  }

  suspend fun bundledFlowDecks(): List<Deck> = withContext(Dispatchers.IO) {
    FLOW_PRESET_PATHS.map { path -> decodeDeck(readAssetBytes(path)) }
  }

  suspend fun saveFlowRecord(record: FlowGameRecord): SavedGameResult = withContext(Dispatchers.IO) {
    database.withTransaction {
      val previous = dao.deckProgress(record.deckId, GAME_MODE_FLOW, INPUT_MODE_BUILTIN)
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
          inputMode = INPUT_MODE_BUILTIN,
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
        inputMode = INPUT_MODE_BUILTIN,
        plays = (previous?.plays ?: 0) + 1,
        bestScore = maxOf(previous?.bestScore ?: 0, record.score),
        bestAccuracyPercent = maxOf(previous?.bestAccuracyPercent ?: 0.0, record.accuracyPercent),
        lastPlayedAtEpochMillis = record.playedAtEpochMillis,
      )
      dao.upsertDeckProgress(progress)
      SavedGameResult(progress, isNewBest)
    }
  }

  suspend fun flowProgress(deckId: String): DeckProgressEntity? = withContext(Dispatchers.IO) {
    dao.deckProgress(deckId, GAME_MODE_FLOW, INPUT_MODE_BUILTIN)
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
            source = requireNotNull(journal.source),
            official = requireNotNull(journal.official),
            installedAtEpochMillis = previous?.installedAtEpochMillis ?: journal.startedAtEpochMillis,
            updatedAtEpochMillis = now,
            lastPlayedAtEpochMillis = previous?.lastPlayedAtEpochMillis,
          ),
        )
        journal.tagsJson?.let {
          dao.upsertDownloadHistory(DownloadHistoryEntity(deckId, it, now))
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
        source = "recovered",
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
    private val tagsJson = Json { isLenient = false }
    private const val GAME_MODE_FLOW = "flow"
    private const val INPUT_MODE_BUILTIN = "builtin"
    private val FLOW_PRESET_PATHS = listOf(
      "decks/flow/flow_topik_beginner_v3.json",
      "decks/flow/flow_topik_intermediate_v3.json",
      "decks/flow/flow_topik_advanced_v3.json",
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
  }
}
