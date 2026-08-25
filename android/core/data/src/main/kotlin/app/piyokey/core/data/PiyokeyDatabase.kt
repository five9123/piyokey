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
  /** Official source deck for a user-created copy; never embedded in .piyodeck. */
  val derivedFromDeckId: String? = null,
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
  @PrimaryKey val deckId: String,
  /** JSON array; preserved after an installed deck is deleted. */
  val tagsJson: String,
  val downloadedAtEpochMillis: Long,
)

@Entity(tableName = "user_deck_history")
data class UserDeckHistoryEntity(
  @PrimaryKey val deckId: String,
  val firstImportedAtEpochMillis: Long,
  val lastImportedAtEpochMillis: Long,
  val lastDeletedAtEpochMillis: Long?,
  val lastVersion: Int,
  val lastContentSha256: String,
  /** Restores the deck's recency after a deliberate delete and later re-import. */
  val lastPlayedAtEpochMillis: Long?,
)

@Entity(tableName = "deck_maker_entitlement_cache")
data class DeckMakerEntitlementCacheEntity(
  @PrimaryKey val productId: String,
  /** Last successfully queried PURCHASED value. This is an offline UX cache, not a receipt. */
  val isActive: Boolean,
  val lastVerifiedAtEpochMillis: Long,
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
  val derivedFromDeckId: String? = null,
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

@Entity(tableName = "user_progress")
data class UserProgressEntity(
  @PrimaryKey val stageId: String,
  val stars: Int,
  val bestAccuracyPercent: Double,
  val completedAtEpochMillis: Long,
)

@Entity(tableName = "curriculum_session")
data class CurriculumSessionEntity(
  @PrimaryKey val singletonId: Int = 1,
  val stageId: String,
  val currentTargetIndex: Int,
  val acceptedKeys: String,
  val mistakeCount: Int,
  val currentItemMistakeCount: Int,
  val mistakenJamoIndicesJson: String,
  val itemResolutionsJson: String,
  val activeDurationMillis: Long,
  val updatedAtEpochMillis: Long,
)

@Entity(tableName = "review_items")
data class ReviewItemEntity(
  @PrimaryKey val reviewId: String,
  val itemId: String,
  val sourceDeckId: String,
  val ko: String,
  val readingJa: String,
  val meaningJa: String,
  val localizationsJson: String?,
  val missCount: Int,
  val consecutivePerfect: Int,
  val addedAtEpochMillis: Long,
  val graduatedAtEpochMillis: Long?,
)

@Entity(tableName = "streak_days")
data class StreakDayEntity(
  @PrimaryKey val day: String,
  val activitiesJson: String,
)

@Entity(tableName = "retention_rewards")
data class RetentionRewardEntity(
  @PrimaryKey val threshold: Int,
  val unlockedAtEpochMillis: Long,
)

@Entity(tableName = "reminder_preference")
data class ReminderPreferenceEntity(
  @PrimaryKey val singletonId: Int = 1,
  val isEnabled: Boolean,
  val hour: Int,
  val minute: Int,
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

  @Query("SELECT * FROM user_deck_history WHERE deckId = :deckId")
  suspend fun userDeckHistory(deckId: String): UserDeckHistoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertUserDeckHistory(entity: UserDeckHistoryEntity)

  @Query("SELECT * FROM deck_maker_entitlement_cache WHERE productId = :productId")
  suspend fun deckMakerEntitlementCache(productId: String): DeckMakerEntitlementCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDeckMakerEntitlementCache(entity: DeckMakerEntitlementCacheEntity)

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

  @Query("SELECT * FROM user_progress ORDER BY stageId")
  suspend fun userProgress(): List<UserProgressEntity>

  @Query("SELECT * FROM user_progress WHERE stageId = :stageId")
  suspend fun userProgress(stageId: String): UserProgressEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertUserProgress(entity: UserProgressEntity)

  @Query("SELECT * FROM curriculum_session WHERE singletonId = 1")
  suspend fun curriculumSession(): CurriculumSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCurriculumSession(entity: CurriculumSessionEntity)

  @Query("DELETE FROM curriculum_session WHERE singletonId = 1")
  suspend fun clearCurriculumSession()

  @Query("SELECT * FROM review_items ORDER BY addedAtEpochMillis DESC, reviewId")
  suspend fun reviewItems(): List<ReviewItemEntity>

  @Query("SELECT * FROM review_items WHERE reviewId = :reviewId")
  suspend fun reviewItem(reviewId: String): ReviewItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertReviewItem(entity: ReviewItemEntity)

  @Query("DELETE FROM review_items WHERE reviewId = :reviewId")
  suspend fun deleteReviewItem(reviewId: String)

  @Query("SELECT * FROM streak_days ORDER BY day")
  suspend fun streakDays(): List<StreakDayEntity>

  @Query("SELECT * FROM streak_days WHERE day = :day")
  suspend fun streakDay(day: String): StreakDayEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertStreakDay(entity: StreakDayEntity)

  @Query("SELECT * FROM retention_rewards ORDER BY threshold")
  suspend fun retentionRewards(): List<RetentionRewardEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertRetentionReward(entity: RetentionRewardEntity)

  @Query("SELECT * FROM reminder_preference WHERE singletonId = 1")
  suspend fun reminderPreference(): ReminderPreferenceEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertReminderPreference(entity: ReminderPreferenceEntity)
}

@Database(
  entities = [
    InstalledDeckEntity::class,
    DownloadHistoryEntity::class,
    UserDeckHistoryEntity::class,
    DeckMakerEntitlementCacheEntity::class,
    CatalogStateEntity::class,
    RecoveryJournalEntity::class,
    GameRecordEntity::class,
    DeckProgressEntity::class,
    UserProgressEntity::class,
    CurriculumSessionEntity::class,
    ReviewItemEntity::class,
    StreakDayEntity::class,
    RetentionRewardEntity::class,
    ReminderPreferenceEntity::class,
  ],
  version = 5,
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
      ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build().also { instance = it }
    }

    /** Keeps repeated instrumented fresh-install scenarios isolated in the same process. */
    fun closeSingletonForTesting() = synchronized(this) {
      instance?.close()
      instance = null
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

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `user_progress` (
            |`stageId` TEXT NOT NULL, `stars` INTEGER NOT NULL,
            |`bestAccuracyPercent` REAL NOT NULL, `completedAtEpochMillis` INTEGER NOT NULL,
            |PRIMARY KEY(`stageId`))""".trimMargin(),
        )
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `curriculum_session` (
            |`singletonId` INTEGER NOT NULL, `stageId` TEXT NOT NULL,
            |`currentTargetIndex` INTEGER NOT NULL, `acceptedKeys` TEXT NOT NULL,
            |`mistakeCount` INTEGER NOT NULL, `currentItemMistakeCount` INTEGER NOT NULL,
            |`mistakenJamoIndicesJson` TEXT NOT NULL, `itemResolutionsJson` TEXT NOT NULL,
            |`activeDurationMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL,
            |PRIMARY KEY(`singletonId`))""".trimMargin(),
        )
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `review_items` (
            |`reviewId` TEXT NOT NULL, `itemId` TEXT NOT NULL, `sourceDeckId` TEXT NOT NULL,
            |`ko` TEXT NOT NULL, `readingJa` TEXT NOT NULL, `meaningJa` TEXT NOT NULL,
            |`localizationsJson` TEXT, `missCount` INTEGER NOT NULL,
            |`consecutivePerfect` INTEGER NOT NULL, `addedAtEpochMillis` INTEGER NOT NULL,
            |`graduatedAtEpochMillis` INTEGER, PRIMARY KEY(`reviewId`))""".trimMargin(),
        )
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `streak_days` (
            |`day` TEXT NOT NULL, `activitiesJson` TEXT NOT NULL, PRIMARY KEY(`day`))""".trimMargin(),
        )
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `retention_rewards` (
            |`threshold` INTEGER NOT NULL, `unlockedAtEpochMillis` INTEGER NOT NULL,
            |PRIMARY KEY(`threshold`))""".trimMargin(),
        )
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `reminder_preference` (
            |`singletonId` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL,
            |`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL,
            |PRIMARY KEY(`singletonId`))""".trimMargin(),
        )
      }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `user_deck_history` (
            |`deckId` TEXT NOT NULL, `firstImportedAtEpochMillis` INTEGER NOT NULL,
            |`lastImportedAtEpochMillis` INTEGER NOT NULL, `lastDeletedAtEpochMillis` INTEGER,
            |`lastVersion` INTEGER NOT NULL, `lastContentSha256` TEXT NOT NULL,
            |`lastPlayedAtEpochMillis` INTEGER,
            |PRIMARY KEY(`deckId`))""".trimMargin(),
        )
      }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `installed_decks` ADD COLUMN `derivedFromDeckId` TEXT")
        db.execSQL("ALTER TABLE `recovery_journal` ADD COLUMN `derivedFromDeckId` TEXT")
        db.execSQL(
          """CREATE TABLE IF NOT EXISTS `deck_maker_entitlement_cache` (
            |`productId` TEXT NOT NULL, `isActive` INTEGER NOT NULL,
            |`lastVerifiedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`productId`))""".trimMargin(),
        )
      }
    }
  }
}
