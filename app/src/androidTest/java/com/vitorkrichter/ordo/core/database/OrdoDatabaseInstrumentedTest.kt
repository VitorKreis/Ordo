package com.vitorkrichter.ordo.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vitorkrichter.ordo.core.database.entity.HydrationSettingsEntity
import com.vitorkrichter.ordo.core.database.entity.UserProfileEntity
import com.vitorkrichter.ordo.core.database.entity.WeightEntryEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrdoDatabaseInstrumentedTest {
    private lateinit var database: OrdoDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrdoDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun databaseCanOpen() {
        assertTrue(database.openHelper.writableDatabase.isOpen)
    }

    @Test
    fun healthDataDaosPersistOptionalProfileMultipleWeightsAndHydrationOverride() = runBlocking {
        val now = Instant.parse("2026-10-10T12:00:00Z")
        database.userProfileDao().upsert(
            UserProfileEntity(
                displayName = "Ordo",
                birthDate = LocalDate.of(1990, 1, 2),
                heightCm = 180.0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.weightEntryDao().insert(WeightEntryEntity("w-1", now, 70.0, now, now))
        database.weightEntryDao().insert(WeightEntryEntity("w-2", now.plusSeconds(60), 70.5, now, now))
        database.hydrationSettingsDao().getOrCreateDefault(now)
        database.hydrationSettingsDao().setManualTarget(2_500, now.plusSeconds(1))

        assertEquals("Ordo", database.userProfileDao().findById()?.displayName)
        assertEquals(2, database.weightEntryDao().observeAll().first().size)
        assertEquals(2_500, database.hydrationSettingsDao().findById()?.targetMl)
        assertEquals(HydrationSettingsEntity.CUSTOM_TARGET_MODE, database.hydrationSettingsDao().findById()?.targetMode)
    }
}
