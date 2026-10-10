package com.vitorkrichter.ordo.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorkrichter.ordo.core.database.OrdoDatabase
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrdoBackupManagerInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: OrdoDatabase
    private lateinit var backupManager: OrdoBackupManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(OrdoDatabase.NAME)
        database = Room.databaseBuilder(context, OrdoDatabase::class.java, OrdoDatabase.NAME).build()
        database.openHelper.writableDatabase
        backupManager = OrdoBackupManager(context, database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(OrdoDatabase.NAME)
    }

    @Test
    fun exportCreatesReadableCompleteSqliteBackup() {
        val destination = File(context.cacheDir, "ordo-test-export-${UUID.randomUUID()}.ordo")
        try {
            backupManager.exportTo(Uri.fromFile(destination))

            assertTrue(destination.isFile)
            SQLiteDatabase.openDatabase(
                destination.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { sqlite ->
                sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(OrdoDatabase.VERSION, cursor.getInt(0))
                }
                sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ok", cursor.getString(0))
                }
                sqlite.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'session_sets'",
                    null,
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
                listOf(
                    "user_profile",
                    "weight_entries",
                    "hydration_settings",
                    "plan_sequence_state",
                ).forEach { table ->
                    sqlite.rawQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                        arrayOf(table),
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("missing table $table", 1, cursor.getInt(0))
                    }
                }
            }
        } finally {
            destination.delete()
        }
    }

    @Test
    fun invalidSchemaDoesNotReplaceExistingDatabase() {
        val target = context.getDatabasePath(OrdoDatabase.NAME)
        val originalBytes = target.readBytes()
        val invalidBackup = File(context.cacheDir, "ordo-test-invalid-${UUID.randomUUID()}.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(invalidBackup, null).use { sqlite ->
                sqlite.execSQL("CREATE TABLE app_metadata (`key` TEXT PRIMARY KEY, `value` TEXT NOT NULL)")
                sqlite.version = OrdoDatabase.VERSION
            }

            try {
                backupManager.restoreFrom(Uri.fromFile(invalidBackup))
                fail("Expected the invalid backup to be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("missing tables"))
            }

            assertArrayEquals(originalBytes, target.readBytes())
            assertTrue(database.openHelper.writableDatabase.isOpen)
        } finally {
            invalidBackup.delete()
        }
    }
}
