package app.piyokey.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PiyokeyDatabaseMigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    PiyokeyDatabase::class.java,
  )

  @Test
  fun migrateOneToTwoPreservesM3DataAndAddsGameTables() {
    helper.createDatabase(DATABASE_NAME, 1).apply {
      execSQL(
        """INSERT INTO download_history(deckId, tagsJson, downloadedAtEpochMillis)
          |VALUES ('kept-deck', '["tag"]', 1234)""".trimMargin(),
      )
      close()
    }

    helper.runMigrationsAndValidate(DATABASE_NAME, 2, true, PiyokeyDatabase.MIGRATION_1_2).use { db ->
      db.query("SELECT COUNT(*) FROM download_history WHERE deckId = 'kept-deck'").use { cursor ->
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
      }
      db.query("SELECT COUNT(*) FROM game_records").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
      db.query("SELECT COUNT(*) FROM deck_progress").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
    }
  }

  @Test
  fun migrateTwoToThreePreservesM4RecordsAndAddsLearningTables() {
    helper.createDatabase(M5_DATABASE_NAME, 2).apply {
      execSQL(
        """INSERT INTO deck_progress(deckId, mode, inputMode, plays, bestScore,
          |bestAccuracyPercent, lastPlayedAtEpochMillis)
          |VALUES ('kept-flow', 'flow', 'builtin', 3, 900, 95.0, 1234)""".trimMargin(),
      )
      close()
    }

    helper.runMigrationsAndValidate(M5_DATABASE_NAME, 3, true, PiyokeyDatabase.MIGRATION_2_3).use { db ->
      db.query("SELECT plays, bestScore FROM deck_progress WHERE deckId = 'kept-flow'").use { cursor ->
        cursor.moveToFirst()
        assertEquals(3, cursor.getInt(0))
        assertEquals(900, cursor.getInt(1))
      }
      db.query("SELECT COUNT(*) FROM user_progress").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
      db.query("SELECT COUNT(*) FROM review_items").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
    }
  }

  @Test
  fun migrateThreeToFourPreservesLearningAndAddsUserDeckHistory() {
    helper.createDatabase(R11_DATABASE_NAME, 3).apply {
      execSQL(
        """INSERT INTO streak_days(day, activitiesJson)
          |VALUES ('2026-08-25', '[\"practice\"]')""".trimMargin(),
      )
      close()
    }
    helper.runMigrationsAndValidate(R11_DATABASE_NAME, 4, true, PiyokeyDatabase.MIGRATION_3_4).use { db ->
      db.query("SELECT COUNT(*) FROM streak_days WHERE day = '2026-08-25'").use { cursor ->
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
      }
      db.query("SELECT COUNT(*) FROM user_deck_history").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
    }
  }

  @Test
  fun migrateFourToFivePreservesDecksAndAddsCopyProvenance() {
    helper.createDatabase(DECK_MAKER_DATABASE_NAME, 4).apply {
      execSQL(
        """INSERT INTO installed_decks(deckId, version, payloadName, payloadSha256,
          |backupPayloadName, backupSha256, backupVersion, source, official,
          |installedAtEpochMillis, updatedAtEpochMillis, lastPlayedAtEpochMillis)
          |VALUES ('user_kept', 2, 'decks/user_kept.json', 'sha', NULL, NULL, NULL,
          |'created', 0, 100, 200, NULL)""".trimMargin(),
      )
      close()
    }
    helper.runMigrationsAndValidate(
      DECK_MAKER_DATABASE_NAME,
      5,
      true,
      PiyokeyDatabase.MIGRATION_4_5,
    ).use { db ->
      db.query("SELECT version, derivedFromDeckId FROM installed_decks WHERE deckId = 'user_kept'")
        .use { cursor ->
          cursor.moveToFirst()
          assertEquals(2, cursor.getInt(0))
          assertEquals(true, cursor.isNull(1))
        }
      db.query("SELECT COUNT(*) FROM deck_maker_entitlement_cache").use { cursor ->
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
      }
    }
  }

  private companion object {
    const val DATABASE_NAME = "m4-migration-test"
    const val M5_DATABASE_NAME = "m5-migration-test"
    const val R11_DATABASE_NAME = "r11-migration-test"
    const val DECK_MAKER_DATABASE_NAME = "deck-maker-migration-test"
  }
}
