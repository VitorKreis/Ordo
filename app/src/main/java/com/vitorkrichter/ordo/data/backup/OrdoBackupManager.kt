package com.vitorkrichter.ordo.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vitorkrichter.ordo.core.database.OrdoDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

@Singleton
class OrdoBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: OrdoDatabase,
) {
    private val operationLock = Any()

    fun exportTo(uri: Uri) = synchronized(operationLock) {
        val source = context.getDatabasePath(OrdoDatabase.NAME)
        require(source.isFile) { "Database unavailable" }

        withTemporaryFile(context.cacheDir, EXPORT_TEMP_PREFIX, DATABASE_SUFFIX) { snapshot ->
            createConsistentSnapshot(source, snapshot)
            validateBackup(snapshot)

            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                FileInputStream(snapshot).use { input ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: error("Could not open backup destination")
        }
    }

    fun writeText(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
            ?: error("Could not open export destination")
    }

    fun restoreFrom(uri: Uri) = synchronized(operationLock) {
        withTemporaryFile(context.cacheDir, RESTORE_TEMP_PREFIX, DATABASE_SUFFIX) { imported ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(imported, false).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: error("Could not open backup")

            validateBackup(imported)

            val target = context.getDatabasePath(OrdoDatabase.NAME)
            val databaseDirectory = target.parentFile
                ?: error("Database directory unavailable")
            require(databaseDirectory.isDirectory || databaseDirectory.mkdirs()) {
                "Database directory unavailable"
            }

            withTemporaryFile(databaseDirectory, STAGED_RESTORE_PREFIX, DATABASE_SUFFIX) { staged ->
                copyFile(imported, staged)
                validateBackup(staged)

                // Room must release the SQLite file before the sibling files are moved.
                // The staged database has already passed validation at this point.
                try {
                    database.close()
                    replaceDatabase(target, staged)
                } catch (failure: Throwable) {
                    reopenDatabaseAfterFailure(failure)
                    throw failure
                }
            }
        }

        restartApplication()
    }

    private fun createConsistentSnapshot(source: File, destination: File) {
        deleteFileOrThrow(destination)

        val sqlite = database.openHelper.writableDatabase
        if (supportsVacuumInto(sqlite)) {
            sqlite.execSQL("VACUUM INTO ${sqliteStringLiteral(destination.absolutePath)}")
            require(destination.isFile && destination.length() > 0L) {
                "Could not create database snapshot"
            }
            return
        }

        // VACUUM INTO is not available in some SQLite versions shipped with older
        // Android releases. In WAL mode, a successful checkpoint makes the main
        // file a consistent snapshot; later writes go to the WAL file instead of
        // changing the bytes copied below.
        val journalMode = readString(sqlite, "PRAGMA journal_mode")
        if (journalMode.equals("wal", ignoreCase = true)) {
            val checkpoint = checkpoint(sqlite)
            require(checkpoint.busy == 0) { "Database is busy; backup was not created" }
            require(checkpoint.log == checkpoint.checkpointed) {
                "Could not checkpoint database before backup"
            }
            copyFile(source, destination)
            return
        }

        // Rollback-journal databases need the exclusive transaction to prevent a
        // concurrent writer from changing the main file while it is copied.
        sqlite.beginTransaction()
        try {
            copyFile(source, destination)
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun replaceDatabase(target: File, staged: File) {
        require(staged.isFile) { "Staged backup unavailable" }
        val directory = target.parentFile ?: error("Database directory unavailable")
        val token = UUID.randomUUID().toString()
        val previousDatabase = reservePath(directory, ".ordo-previous-$token", DATABASE_SUFFIX)
        val previousWal = reservePath(directory, ".ordo-previous-$token", "-wal")
        val previousShm = reservePath(directory, ".ordo-previous-$token", "-shm")
        val targetWal = File(target.path + "-wal")
        val targetShm = File(target.path + "-shm")

        var databaseMoved = false
        var walMoved = false
        var shmMoved = false
        var replacementInstalled = false
        try {
            walMoved = moveIfExists(targetWal, previousWal)
            shmMoved = moveIfExists(targetShm, previousShm)
            databaseMoved = moveIfExists(target, previousDatabase)
            moveAtomically(staged, target)
            replacementInstalled = true
        } catch (failure: Throwable) {
            rollbackReplacement(
                failure = failure,
                target = target,
                targetWal = targetWal,
                targetShm = targetShm,
                previousDatabase = previousDatabase,
                previousWal = previousWal,
                previousShm = previousShm,
                databaseMoved = databaseMoved,
                walMoved = walMoved,
                shmMoved = shmMoved,
                replacementInstalled = replacementInstalled,
            )
            throw failure
        }

        // The new database is already installed. If cleanup fails, preserve the
        // old sibling file and surface the exception instead of hiding it.
        deleteFileOrThrow(previousDatabase)
        deleteFileOrThrow(previousWal)
        deleteFileOrThrow(previousShm)
    }

    private fun rollbackReplacement(
        failure: Throwable,
        target: File,
        targetWal: File,
        targetShm: File,
        previousDatabase: File,
        previousWal: File,
        previousShm: File,
        databaseMoved: Boolean,
        walMoved: Boolean,
        shmMoved: Boolean,
        replacementInstalled: Boolean,
    ) {
        if (replacementInstalled) {
            try {
                deleteFileOrThrow(target)
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
        }
        try {
            if (databaseMoved) moveAtomically(previousDatabase, target)
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressed(rollbackFailure)
        }
        try {
            if (walMoved) moveAtomically(previousWal, targetWal)
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressed(rollbackFailure)
        }
        try {
            if (shmMoved) moveAtomically(previousShm, targetShm)
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressed(rollbackFailure)
        }
    }

    private fun validateBackup(file: File) {
        require(file.isFile && file.length() > 0L) { "Empty backup" }
        val sqlite = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            val version = readInt(sqlite, "PRAGMA user_version")
            require(version in SUPPORTED_SCHEMA_VERSIONS) { "Incompatible backup version" }

            val tables = mutableSetOf<String>()
            sqlite.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tables += cursor.getString(0)
                }
            }
            val missingTables = requiredTablesForVersion(version) - tables
            require(missingTables.isEmpty()) {
                "Invalid Ordo backup; missing tables: ${missingTables.sorted().joinToString()}"
            }
            checkRequiredColumns(sqlite, version)

            val identityHash = sqlite.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null,
            ).use { cursor ->
                require(cursor.moveToFirst()) { "Invalid Ordo backup; Room identity is missing" }
                cursor.getString(0)
            }
            require(identityHash == ROOM_IDENTITY_HASHES_BY_VERSION[version]) {
                "Incompatible Ordo database schema"
            }

            checkIntegrity(sqlite)
            checkForeignKeys(sqlite)
        } finally {
            sqlite.close()
        }
    }

    private fun checkIntegrity(sqlite: SQLiteDatabase) {
        val errors = mutableListOf<String>()
        var resultCount = 0
        sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            while (cursor.moveToNext()) {
                resultCount += 1
                val result = cursor.getString(0)
                if (!result.equals("ok", ignoreCase = true) && errors.size < MAX_VALIDATION_ERRORS) {
                    errors += result
                }
            }
        }
        require(resultCount > 0 && errors.isEmpty()) {
            if (errors.isEmpty()) {
                "SQLite integrity check returned no result"
            } else {
                "Invalid SQLite backup: ${errors.joinToString("; ")}"
            }
        }
    }

    private fun checkForeignKeys(sqlite: SQLiteDatabase) {
        sqlite.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
            if (cursor.moveToFirst()) {
                val table = cursor.getString(0)
                val rowId = cursor.getString(1)
                val parent = cursor.getString(2)
                throw IllegalArgumentException(
                    "Invalid backup foreign key: table=$table row=$rowId parent=$parent",
                )
            }
        }
    }

    private fun checkRequiredColumns(sqlite: SQLiteDatabase, version: Int) {
        REQUIRED_COLUMNS_BY_VERSION[version].orEmpty().forEach { (table, requiredColumns) ->
            val columns = mutableSetOf<String>()
            sqlite.rawQuery("PRAGMA table_info(${quoteIdentifier(table)})", null).use { cursor ->
                while (cursor.moveToNext()) {
                    columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
            }
            val missingColumns = requiredColumns - columns
            require(missingColumns.isEmpty()) {
                "Invalid Ordo backup; table $table is missing columns: ${missingColumns.sorted().joinToString()}"
            }
        }
    }

    private fun requiredTablesForVersion(version: Int): Set<String> = when {
        version >= 6 -> REQUIRED_TABLES_V6
        version >= 5 -> REQUIRED_TABLES_V5
        else -> REQUIRED_TABLES_LEGACY
    }

    private fun quoteIdentifier(value: String): String = "`$value`"

    private fun supportsVacuumInto(sqlite: SupportSQLiteDatabase): Boolean {
        val version = readString(sqlite, "SELECT sqlite_version()")
        val components = version.split('.').map { it.toIntOrNull() }
        if (components.size < 3 || components.take(3).any { it == null }) return false
        val (major, minor, patch) = components.take(3).map { it!! }
        return major > 3 || (major == 3 && (minor > 27 || (minor == 27 && patch >= 0)))
    }

    private fun checkpoint(sqlite: SupportSQLiteDatabase): WalCheckpoint {
        return sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            require(cursor.moveToFirst() && cursor.columnCount >= 3) {
                "Could not checkpoint database before backup"
            }
            WalCheckpoint(
                busy = cursor.getInt(0),
                log = cursor.getInt(1),
                checkpointed = cursor.getInt(2),
            )
        }
    }

    private fun readString(sqlite: SupportSQLiteDatabase, sql: String): String {
        return sqlite.query(sql).use { cursor ->
            require(cursor.moveToFirst()) { "SQLite query returned no result" }
            cursor.getString(0)
        }
    }

    private fun readInt(sqlite: SQLiteDatabase, sql: String): Int {
        return sqlite.rawQuery(sql, null).use { cursor ->
            require(cursor.moveToFirst()) { "SQLite query returned no result" }
            cursor.getInt(0)
        }
    }

    private fun copyFile(source: File, destination: File) {
        require(source.isFile) { "Database unavailable" }
        FileInputStream(source).use { input ->
            FileOutputStream(destination, false).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun moveIfExists(source: File, destination: File): Boolean {
        if (!source.exists()) return false
        moveAtomically(source, destination)
        return true
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: UnsupportedOperationException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun reservePath(directory: File, prefix: String, suffix: String): File {
        val reserved = File.createTempFile(prefix, suffix, directory)
        deleteFileOrThrow(reserved)
        return reserved
    }

    private inline fun <T> withTemporaryFile(
        directory: File,
        prefix: String,
        suffix: String,
        block: (File) -> T,
    ): T {
        require(directory.isDirectory || directory.mkdirs()) { "Temporary directory unavailable" }
        val temporary = File.createTempFile(prefix, suffix, directory)
        var failure: Throwable? = null
        try {
            return block(temporary)
        } catch (throwable: Throwable) {
            failure = throwable
            throw throwable
        } finally {
            try {
                deleteFileOrThrow(temporary)
            } catch (cleanupFailure: Throwable) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure)
                } else {
                    throw cleanupFailure
                }
            }
        }
    }

    private fun deleteFileOrThrow(file: File) {
        if (file.exists() && !file.delete()) {
            throw IOException("Could not delete temporary file: ${file.absolutePath}")
        }
    }

    private fun reopenDatabaseAfterFailure(failure: Throwable) {
        try {
            database.openHelper.writableDatabase
        } catch (reopenFailure: Throwable) {
            failure.addSuppressed(reopenFailure)
        }
    }

    private fun sqliteStringLiteral(value: String): String = "'${value.replace("'", "''")}'"

    private fun restartApplication() {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
            context.startActivity(intent)
        }
        exitProcess(0)
    }

    private data class WalCheckpoint(
        val busy: Int,
        val log: Int,
        val checkpointed: Int,
    )

    private companion object {
        const val DATABASE_SUFFIX = ".db"
        const val EXPORT_TEMP_PREFIX = "ordo-export-"
        const val RESTORE_TEMP_PREFIX = "ordo-restore-"
        const val STAGED_RESTORE_PREFIX = ".ordo-staged-"
        const val MAX_VALIDATION_ERRORS = 5
        val SUPPORTED_SCHEMA_VERSIONS = 2..OrdoDatabase.VERSION
        val ROOM_IDENTITY_HASHES_BY_VERSION = mapOf(
            2 to "c611de025adfc28c429f6ac5d3c3b199",
            // Version 3 only added a partial SQLite index, which is not part of Room's
            // entity identity hash; it can still be migrated to the current schema.
            3 to "c611de025adfc28c429f6ac5d3c3b199",
            4 to "1410fd639ecfb14d1307b8d6042e07d4",
            5 to "53dbce63c5eadb0389ebabdb4eaec0c4",
            6 to "0160023ea7c538a39defee97b9ef074f",
        )
        val REQUIRED_TABLES_LEGACY = setOf(
            "app_metadata",
            "exercises",
            "training_plans",
            "workout_templates",
            "workout_exercise_templates",
            "set_templates",
            "schedule_rules",
            "training_days",
            "workout_sessions",
            "session_exercises",
            "session_sets",
            "room_master_table",
        )
        val REQUIRED_TABLES_V5 = REQUIRED_TABLES_LEGACY + setOf(
            "user_profile",
            "weight_entries",
            "hydration_settings",
        )
        val REQUIRED_TABLES_V6 = REQUIRED_TABLES_V5 + "plan_sequence_state"
        val REQUIRED_COLUMNS_BY_VERSION = mapOf(
            5 to mapOf(
                "user_profile" to setOf(
                    "id",
                    "display_name",
                    "birth_date",
                    "height_cm",
                    "created_at",
                    "updated_at",
                ),
                "weight_entries" to setOf(
                    "id",
                    "measured_at",
                    "weight_kg",
                    "created_at",
                    "updated_at",
                ),
                "hydration_settings" to setOf(
                    "id",
                    "target_ml",
                    "target_mode",
                    "created_at",
                    "updated_at",
                ),
            ),
            6 to mapOf(
                "user_profile" to setOf(
                    "id",
                    "display_name",
                    "birth_date",
                    "height_cm",
                    "created_at",
                    "updated_at",
                ),
                "weight_entries" to setOf(
                    "id",
                    "measured_at",
                    "weight_kg",
                    "created_at",
                    "updated_at",
                ),
                "hydration_settings" to setOf(
                    "id",
                    "target_ml",
                    "target_mode",
                    "created_at",
                    "updated_at",
                ),
                "plan_sequence_state" to setOf(
                    "plan_id",
                    "next_workout_template_id",
                    "cursor_index",
                    "revision",
                    "last_advanced_session_id",
                    "last_advanced_at",
                    "created_at",
                    "updated_at",
                ),
                "workout_sessions" to setOf(
                    "session_intent",
                    "session_origin",
                    "advances_sequence",
                    "sequence_advance_id",
                ),
            ),
        )
    }
}
