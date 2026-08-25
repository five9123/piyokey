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

  private companion object {
    const val DATABASE_NAME = "m4-migration-test"
  }
}
