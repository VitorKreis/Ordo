package com.vitorkrichter.ordo.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorkrichter.ordo.core.database.migration.OrdoMigrations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrdoMigrationInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun migrateLegacyDatabasePreservesMetadataAndCreatesAllCurrentTables() {
        context.deleteDatabase(TEST_DB)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).use { database ->
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_metadata` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
            database.execSQL("INSERT INTO app_metadata (`key`, `value`) VALUES ('schema_note', 'v1')")
            database.version = 1
        }

        val roomDatabase = Room.databaseBuilder(context, OrdoDatabase::class.java, TEST_DB)
            .addMigrations(*OrdoMigrations.ALL)
            .build()
        try {
            val sqlite = roomDatabase.openHelper.writableDatabase
            sqlite.query("SELECT value FROM app_metadata WHERE `key` = 'schema_note'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("v1", cursor.getString(0))
            }
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'workout_sessions'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            sqlite.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(6, cursor.getInt(0))
            }
            sqlite.query("SELECT target_ml, target_mode FROM hydration_settings WHERE id = 'default'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2000, cursor.getInt(0))
                assertEquals("DEFAULT_REFERENCE", cursor.getString(1))
            }
            listOf(
                "user_profile",
                "weight_entries",
                "plan_sequence_state",
            ).forEach { table ->
                sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
                    assertTrue("missing table $table", cursor.moveToFirst())
                }
            }
        } finally {
            roomDatabase.close()
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    companion object {
        private const val TEST_DB = "ordo-migration-test.db"
    }
}
