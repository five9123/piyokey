package app.piyokey.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "installed_decks")
data class InstalledDeckEntity(
  @PrimaryKey val deckId: String,
  val version: Int,
  val payloadName: String,
  val payloadSha256: String,
  val backupPayloadName: String?,
  val backupSha256: String?,
  val backupVersion: Int?,
  val source: String,
  val official: Boolean,
  val installedAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
  val lastPlayedAtEpochMillis: Long?,
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
  @PrimaryKey val deckId: String,
  /** JSON array; preserved after an installed deck is deleted. */
  val tagsJson: String,
  val downloadedAtEpochMillis: Long,
)

@Entity(tableName = "catalog_state")
data class CatalogStateEntity(
  @PrimaryKey val singletonId: Int = 1,
  val catalogVersion: Int,
  val payloadName: String,
  val payloadSha256: String,
  val backupPayloadName: String?,
  val backupSha256: String?,
  val backupCatalogVersion: Int?,
  val backupEtag: String?,
  val backupLastModified: String?,
  val etag: String?,
  val lastModified: String?,
  val verifiedAtEpochMillis: Long,
)

@Entity(tableName = "recovery_journal")
data class RecoveryJournalEntity(
  @PrimaryKey val operationId: String,
  val kind: String,
  val deckId: String?,
  val version: Int?,
  val targetName: String,
  val stagedName: String?,
  val backupName: String?,
  val expectedSha256: String?,
  val source: String?,
  val official: Boolean?,
  val tagsJson: String?,
  val catalogVersion: Int?,
  val etag: String?,
  val lastModified: String?,
  val startedAtEpochMillis: Long,
)

@Dao
interface PiyokeyDao {
  @Query("SELECT * FROM installed_decks ORDER BY installedAtEpochMillis DESC")
  suspend fun installedDecks(): List<InstalledDeckEntity>

  @Query("SELECT * FROM installed_decks WHERE deckId = :deckId")
  suspend fun installedDeck(deckId: String): InstalledDeckEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertInstalledDeck(entity: InstalledDeckEntity)

  @Query("DELETE FROM installed_decks WHERE deckId = :deckId")
  suspend fun deleteInstalledDeck(deckId: String)

  @Query("UPDATE installed_decks SET lastPlayedAtEpochMillis = :playedAt WHERE deckId = :deckId")
  suspend fun markPlayed(deckId: String, playedAt: Long)

  @Query("SELECT * FROM download_history ORDER BY downloadedAtEpochMillis DESC")
  suspend fun downloadHistory(): List<DownloadHistoryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDownloadHistory(entity: DownloadHistoryEntity)

  @Query("SELECT * FROM catalog_state WHERE singletonId = 1")
  suspend fun catalogState(): CatalogStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCatalogState(entity: CatalogStateEntity)

  @Query("DELETE FROM catalog_state WHERE singletonId = 1")
  suspend fun deleteCatalogState()

  @Query("SELECT * FROM recovery_journal ORDER BY startedAtEpochMillis")
  suspend fun journals(): List<RecoveryJournalEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertJournal(entity: RecoveryJournalEntity)

  @Query("DELETE FROM recovery_journal WHERE operationId = :operationId")
  suspend fun deleteJournal(operationId: String)
}

@Database(
  entities = [
    InstalledDeckEntity::class,
    DownloadHistoryEntity::class,
    CatalogStateEntity::class,
    RecoveryJournalEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
abstract class PiyokeyDatabase : RoomDatabase() {
  abstract fun dao(): PiyokeyDao

  companion object {
    @Volatile private var instance: PiyokeyDatabase? = null

    fun open(context: Context): PiyokeyDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext,
        PiyokeyDatabase::class.java,
        "piyokey.db",
      ).build().also { instance = it }
    }
  }
}
