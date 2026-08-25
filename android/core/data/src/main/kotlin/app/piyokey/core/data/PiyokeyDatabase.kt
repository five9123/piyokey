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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

@Entity(tableName = "game_records")
data class GameRecordEntity(
  @PrimaryKey val recordId: String,
  val mode: String,
  val deckId: String,
  val course: String,
  val score: Int,
  val maxCombo: Int,
  val accuracyPercent: Double,
  val inputMode: String,
  val correctJamoCount: Int,
  val charactersPerMinute: Double,
  val mistakeCount: Int,
  val completedItemCount: Int,
  val missedItemCount: Int,
  val playDurationMillis: Long,
  val playedAtEpochMillis: Long,
)

@Entity(tableName = "deck_progress", primaryKeys = ["deckId", "mode", "inputMode"])
data class DeckProgressEntity(
  val deckId: String,
  val mode: String,
  val inputMode: String,
  val plays: Int,
  val bestScore: Int,
  val bestAccuracyPercent: Double,
  val lastPlayedAtEpochMillis: Long,
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

  @Insert
  suspend fun insertGameRecord(entity: GameRecordEntity)

  @Query("SELECT * FROM game_records WHERE deckId = :deckId AND mode = :mode AND inputMode = :inputMode ORDER BY playedAtEpochMillis DESC")
  suspend fun gameRecords(deckId: String, mode: String, inputMode: String): List<GameRecordEntity>

  @Query("SELECT * FROM deck_progress WHERE deckId = :deckId AND mode = :mode AND inputMode = :inputMode")
  suspend fun deckProgress(deckId: String, mode: String, inputMode: String): DeckProgressEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDeckProgress(entity: DeckProgressEntity)
}

@Database(
  entities = [
    InstalledDeckEntity::class,
    DownloadHistoryEntity::class,
    CatalogStateEntity::class,
    RecoveryJournalEntity::class,
    GameRecordEntity::class,
    DeckProgressEntity::class,
  ],
  version = 2,
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
      ).addMigrations(MIGRATION_1_2).build().also { instance = it }
    }

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `game_records` (
            |`recordId` TEXT NOT NULL, `mode` TEXT NOT NULL, `deckId` TEXT NOT NULL,
            |`course` TEXT NOT NULL, `score` INTEGER NOT NULL, `maxCombo` INTEGER NOT NULL,
            |`accuracyPercent` REAL NOT NULL, `inputMode` TEXT NOT NULL,
            |`correctJamoCount` INTEGER NOT NULL, `charactersPerMinute` REAL NOT NULL,
            |`mistakeCount` INTEGER NOT NULL,
            |`completedItemCount` INTEGER NOT NULL, `missedItemCount` INTEGER NOT NULL,
            |`playDurationMillis` INTEGER NOT NULL, `playedAtEpochMillis` INTEGER NOT NULL,
            |PRIMARY KEY(`recordId`))""".trimMargin(),
        )
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `deck_progress` (
            |`deckId` TEXT NOT NULL, `mode` TEXT NOT NULL, `inputMode` TEXT NOT NULL,
            |`plays` INTEGER NOT NULL, `bestScore` INTEGER NOT NULL,
            |`bestAccuracyPercent` REAL NOT NULL, `lastPlayedAtEpochMillis` INTEGER NOT NULL,
            |PRIMARY KEY(`deckId`, `mode`, `inputMode`))""".trimMargin(),
        )
      }
    }
  }
}
