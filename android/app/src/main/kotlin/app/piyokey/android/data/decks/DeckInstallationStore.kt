package app.piyokey.android.data.decks

import app.piyokey.android.data.persistence.RecoverableJsonFile
import app.piyokey.android.data.persistence.StoreJson
import app.piyokey.android.data.persistence.deleteTree
import app.piyokey.android.data.persistence.sha256Hex
import app.piyokey.core.deckkit.ContentValidationIssue
import app.piyokey.core.deckkit.Deck
import app.piyokey.core.deckkit.DeckJson
import app.piyokey.core.deckkit.DeckValidator
import app.piyokey.core.deckkit.Iso8601InstantSerializer
import app.piyokey.core.deckkit.UserDeckValidator
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InstalledDeckSource(val raw: String) {
  @SerialName("bundle") BUNDLE("bundle"),
  @SerialName("remote") REMOTE("remote"),
  @SerialName("imported") IMPORTED("imported"),
  @SerialName("created") CREATED("created");

  val isUserDeck: Boolean get() = this == IMPORTED || this == CREATED
}

/** One row of `InstalledDecks/installed-decks.json` (iOS `InstalledDeckRecord`). */
@Serializable
data class InstalledDeckRecord(
  @SerialName("deck_id") val deckId: String,
  val version: Int,
  @SerialName("installed_at") @Serializable(with = Iso8601InstantSerializer::class) val installedAt: Instant,
  @SerialName("last_played_at") @Serializable(with = Iso8601InstantSerializer::class) val lastPlayedAt: Instant? = null,
  val source: InstalledDeckSource,
  @SerialName("content_sha256") val contentSha256: String? = null,
  @SerialName("package_format_version") val packageFormatVersion: Int? = null,
  @SerialName("is_locally_modified") val isLocallyModified: Boolean = false,
  @SerialName("derived_from_deck_id") val derivedFromDeckId: String? = null,
) {
  val id: String get() = deckId
}

data class DeckInstallationSnapshot(
  val records: Map<String, InstalledDeckRecord>,
  val decks: Map<String, Deck>,
  val downloadHistory: Map<String, List<String>>,
  val removedRecords: Map<String, InstalledDeckRecord>,
)

sealed class DeckInstallationStoreException(message: String) : Exception(message) {
  data class InvalidDeck(val issues: List<ContentValidationIssue>) : DeckInstallationStoreException("Invalid deck: $issues")
  data class InvalidIndex(val reason: String) : DeckInstallationStoreException("Invalid index: $reason")
  data class MissingDeckFile(val deckId: String) : DeckInstallationStoreException("Missing deck file $deckId")
  data class SourceChanged(val deckId: String, val expectedVersion: Int, val actualVersion: Int?) :
    DeckInstallationStoreException("Deck $deckId changed: expected v$expectedVersion, found v$actualVersion")
  data class UnsupportedSchema(val version: Int) : DeckInstallationStoreException("Unsupported installation schema $version")
}

/**
 * Installed deck payloads + index under `Hanco/InstalledDecks` (iOS `DeckInstallationStore`,
 * schema 3). Every install/remove is staged in `PendingTransaction/` (index, payload, then the
 * `transaction.json` marker) and committed idempotently, so a crash never splits payload and index.
 * Blocking file IO: call off the main thread.
 */
class DeckInstallationStore(val rootDir: File) {
  @Serializable
  private enum class TransactionOperation {
    @SerialName("install") INSTALL,
    @SerialName("remove") REMOVE,
  }

  @Serializable
  private data class PendingTransaction(
    @SerialName("schema_version") val schemaVersion: Int,
    val operation: TransactionOperation,
    @SerialName("deck_id") val deckId: String,
  )

  @Serializable
  private data class Index(
    @SerialName("schema_version") val schemaVersion: Int,
    val records: List<InstalledDeckRecord>,
    @SerialName("download_history") val downloadHistory: Map<String, List<String>> = emptyMap(),
    @SerialName("removed_records") val removedRecords: List<InstalledDeckRecord> = emptyList(),
  )

  private val indexFile get() = File(rootDir, "installed-decks.json")
  private val transactionDir get() = File(rootDir, "PendingTransaction")
  private val transactionMarker get() = File(transactionDir, "transaction.json")
  private val transactionDeck get() = File(transactionDir, "deck.json")
  private val transactionIndex get() = File(transactionDir, "index.json")

  private fun deckFile(deckId: String) = File(rootDir, "$deckId.json")

  fun loadSnapshot(): DeckInstallationSnapshot {
    recoverPendingTransactionIfNeeded()
    val index = RecoverableJsonFile.load(indexFile, ::shouldRecoverIndex, ::decodeIndex)
      ?: return DeckInstallationSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    val records = LinkedHashMap<String, InstalledDeckRecord>()
    val decks = LinkedHashMap<String, Deck>()
    val removed = LinkedHashMap(index.removedRecords.associateBy { it.deckId })
    var removedCorruptDeck = false
    for (record in index.records) {
      val deck = try {
        RecoverableJsonFile.load(deckFile(record.deckId)) { decodeInstalledDeck(it, record) }
      } catch (_: Exception) {
        null
      }
      if (deck == null) {
        removed[record.deckId] = record
        removedCorruptDeck = true
        continue
      }
      records[record.deckId] = record
      decks[record.deckId] = deck
    }
    val history = LinkedHashMap(index.downloadHistory)
    for (deck in decks.values) if (!history.containsKey(deck.deckId)) history[deck.deckId] = deck.tags
    if (removedCorruptDeck) writeIndex(records.values.toList(), history, removed.values.toList())
    return DeckInstallationSnapshot(records, decks, history, removed)
  }

  /**
   * Validates [data] (user rules for imported/created), then commits payload + index. Keeps the
   * first install date, play date and derivation of a previous or removed record.
   * [expectedCurrentVersion] makes the replace conditional (throws [DeckInstallationStoreException.SourceChanged]).
   */
  fun install(
    data: ByteArray,
    source: InstalledDeckSource,
    contentSha256: String? = null,
    packageFormatVersion: Int? = null,
    isLocallyModified: Boolean = false,
    derivedFromDeckId: String? = null,
    expectedCurrentVersion: Int? = null,
    now: Instant = Instant.now(),
  ): InstalledDeckRecord {
    val deck = DeckJson.decodeDeck(data)
    val issues = if (source.isUserDeck) UserDeckValidator.validate(deck) else DeckValidator.validate(deck)
    if (issues.isNotEmpty()) throw DeckInstallationStoreException.InvalidDeck(issues)
    if (contentSha256 != null && contentSha256 != sha256Hex(data)) {
      throw DeckInstallationStoreException.InvalidIndex("provided content SHA-256 does not match deck payload")
    }
    val snapshot = loadSnapshot()
    if (expectedCurrentVersion != null) {
      val actual = snapshot.records[deck.deckId]?.version
      if (actual != expectedCurrentVersion) {
        throw DeckInstallationStoreException.SourceChanged(deck.deckId, expectedCurrentVersion, actual)
      }
    }
    createRoot()
    val previous = snapshot.records[deck.deckId] ?: snapshot.removedRecords[deck.deckId]
    val record = InstalledDeckRecord(
      deckId = deck.deckId,
      version = deck.version,
      installedAt = previous?.installedAt ?: now,
      lastPlayedAt = previous?.lastPlayedAt,
      source = source,
      contentSha256 = contentSha256,
      packageFormatVersion = packageFormatVersion,
      isLocallyModified = isLocallyModified,
      derivedFromDeckId = derivedFromDeckId ?: previous?.derivedFromDeckId,
    )
    val records = snapshot.records + (deck.deckId to record)
    val removed = snapshot.removedRecords - deck.deckId
    val history = snapshot.downloadHistory + (deck.deckId to deck.tags)
    val indexData = encodeIndex(records.values.toList(), history, removed.values.toList())
    stageTransaction(TransactionOperation.INSTALL, deck.deckId, data, indexData)
    recoverPendingTransactionIfNeeded()
    return record
  }

  /** Removes the payload; the record moves to `removed_records` so a re-import reconnects history. */
  fun remove(deckId: String) {
    validateIdentifier(deckId)
    val snapshot = loadSnapshot()
    val records = LinkedHashMap(snapshot.records)
    val removed = LinkedHashMap(snapshot.removedRecords)
    records.remove(deckId)?.let { removed[deckId] = it }
    createRoot()
    val indexData = encodeIndex(records.values.toList(), snapshot.downloadHistory, removed.values.toList())
    stageTransaction(TransactionOperation.REMOVE, deckId, null, indexData)
    recoverPendingTransactionIfNeeded()
  }

  /** Index-only update of `last_played_at` (does not revalidate every payload). */
  fun markPlayed(deckId: String, at: Instant = Instant.now()): InstalledDeckRecord? {
    validateIdentifier(deckId)
    recoverPendingTransactionIfNeeded()
    val index = RecoverableJsonFile.load(indexFile, ::shouldRecoverIndex, ::decodeIndex) ?: return null
    val position = index.records.indexOfFirst { it.deckId == deckId }
    if (position < 0) return null
    val record = index.records[position].copy(lastPlayedAt = at)
    val records = index.records.toMutableList().also { it[position] = record }
    writeIndex(records, index.downloadHistory, index.removedRecords)
    return record
  }

  fun reset() = rootDir.deleteTree()

  /** Exact installed payload bytes (for `.typedeck` export). */
  fun data(deckId: String): ByteArray {
    validateIdentifier(deckId)
    recoverPendingTransactionIfNeeded()
    val index = RecoverableJsonFile.load(indexFile, ::shouldRecoverIndex, ::decodeIndex)
    val record = index?.records?.firstOrNull { it.deckId == deckId }
      ?: throw DeckInstallationStoreException.MissingDeckFile(deckId)
    return RecoverableJsonFile.load(deckFile(deckId)) { data ->
      decodeInstalledDeck(data, record)
      data
    } ?: throw DeckInstallationStoreException.MissingDeckFile(deckId)
  }

  private fun decodeIndex(data: ByteArray): Index {
    val schemaVersion = StoreJson.schemaVersion(data)
    if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) throw DeckInstallationStoreException.UnsupportedSchema(schemaVersion)
    val index = StoreJson.decode(Index.serializer(), data)
    val recordIds = index.records.map { it.deckId }
    val removedIds = index.removedRecords.map { it.deckId }
    if (recordIds.toSet().size != recordIds.size) throw DeckInstallationStoreException.InvalidIndex("duplicate active deck IDs")
    if (removedIds.toSet().size != removedIds.size) throw DeckInstallationStoreException.InvalidIndex("duplicate removed deck IDs")
    if (recordIds.any { it in removedIds }) throw DeckInstallationStoreException.InvalidIndex("active and removed deck IDs overlap")
    for (record in index.records + index.removedRecords) {
      validateIdentifier(record.deckId)
      if (record.version < 1) throw DeckInstallationStoreException.InvalidIndex("invalid deck version")
      val hash = record.contentSha256
      if (hash != null && !SHA_PATTERN.matches(hash)) throw DeckInstallationStoreException.InvalidIndex("invalid content SHA-256")
    }
    return index
  }

  private fun decodeInstalledDeck(data: ByteArray, record: InstalledDeckRecord): Deck {
    val deck = DeckJson.decodeDeck(data)
    val issues = DeckValidator.validate(deck)
    if (issues.isNotEmpty()) throw DeckInstallationStoreException.InvalidDeck(issues)
    if (deck.deckId != record.deckId || deck.version != record.version) {
      throw DeckInstallationStoreException.InvalidDeck(
        listOf(ContentValidationIssue("installation_metadata_mismatch", record.deckId, "설치 인덱스와 덱 ID 또는 버전이 일치하지 않습니다")),
      )
    }
    val expectedHash = record.contentSha256
    if (expectedHash != null && sha256Hex(data) != expectedHash) {
      throw DeckInstallationStoreException.InvalidDeck(
        listOf(ContentValidationIssue("installation_content_hash_mismatch", record.deckId, "설치 인덱스와 덱 콘텐츠 해시가 일치하지 않습니다")),
      )
    }
    if (record.source.isUserDeck) {
      val userIssues = UserDeckValidator.validate(deck)
      if (userIssues.isNotEmpty()) throw DeckInstallationStoreException.InvalidDeck(userIssues)
    }
    return deck
  }

  private fun createRoot() {
    if (!rootDir.isDirectory && !rootDir.mkdirs() && !rootDir.isDirectory) throw IOException("Could not create $rootDir")
  }

  private fun shouldRecoverIndex(error: Exception): Boolean = error !is DeckInstallationStoreException.UnsupportedSchema

  private fun validateIdentifier(value: String) {
    if (!IDENTIFIER_PATTERN.matches(value)) throw DeckInstallationStoreException.InvalidIndex("unsafe deck identifier")
  }

  private fun stageTransaction(operation: TransactionOperation, deckId: String, deckData: ByteArray?, indexData: ByteArray) {
    validateIdentifier(deckId)
    if (transactionDir.exists()) throw DeckInstallationStoreException.InvalidIndex("another deck transaction is pending")
    try {
      if (!transactionDir.mkdirs()) throw IOException("Could not create $transactionDir")
      RecoverableJsonFile.writeAtomically(indexData, transactionIndex)
      if (deckData != null) RecoverableJsonFile.writeAtomically(deckData, transactionDeck)
      val marker = PendingTransaction(1, operation, deckId)
      RecoverableJsonFile.writeAtomically(StoreJson.encode(PendingTransaction.serializer(), marker), transactionMarker)
    } catch (error: Exception) {
      // No marker means no live file changed, so incomplete staging is safe to discard.
      if (!transactionMarker.exists()) runCatching { transactionDir.deleteTree() }
      throw error
    }
  }

  private fun recoverPendingTransactionIfNeeded() {
    if (!transactionDir.exists()) return
    if (!transactionMarker.exists()) {
      // Crash before the commit marker; live state was untouched.
      transactionDir.deleteTree()
      return
    }
    val transaction = StoreJson.decode(PendingTransaction.serializer(), transactionMarker.readBytes())
    if (transaction.schemaVersion != 1) throw DeckInstallationStoreException.InvalidIndex("unsupported transaction schema")
    validateIdentifier(transaction.deckId)
    val indexData = transactionIndex.readBytes()
    val index = decodeIndex(indexData)
    when (transaction.operation) {
      TransactionOperation.INSTALL -> {
        val deckData = transactionDeck.readBytes()
        val record = index.records.firstOrNull { it.deckId == transaction.deckId }
          ?: throw DeckInstallationStoreException.InvalidIndex("pending install has no active index record")
        decodeInstalledDeck(deckData, record)
        RecoverableJsonFile.write(deckData, deckFile(transaction.deckId))
        RecoverableJsonFile.write(indexData, indexFile)
      }
      TransactionOperation.REMOVE -> {
        if (index.records.any { it.deckId == transaction.deckId }) {
          throw DeckInstallationStoreException.InvalidIndex("pending removal still contains an active record")
        }
        RecoverableJsonFile.write(indexData, indexFile)
        RecoverableJsonFile.removeArtifacts(deckFile(transaction.deckId))
      }
    }
    transactionDir.deleteTree()
  }

  private fun encodeIndex(
    records: List<InstalledDeckRecord>,
    downloadHistory: Map<String, List<String>>,
    removedRecords: List<InstalledDeckRecord>,
  ): ByteArray = StoreJson.encode(
    Index.serializer(),
    Index(
      schemaVersion = CURRENT_SCHEMA_VERSION,
      records = records.sortedBy { it.deckId },
      downloadHistory = downloadHistory.toSortedMap(),
      removedRecords = removedRecords.sortedBy { it.deckId },
    ),
  )

  private fun writeIndex(
    records: List<InstalledDeckRecord>,
    downloadHistory: Map<String, List<String>>,
    removedRecords: List<InstalledDeckRecord>,
  ) {
    RecoverableJsonFile.write(encodeIndex(records, downloadHistory, removedRecords), indexFile)
  }

  companion object {
    const val CURRENT_SCHEMA_VERSION = 3
    private val IDENTIFIER_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{2,63}$")
    private val SHA_PATTERN = Regex("^[0-9a-f]{64}$")
  }
}
