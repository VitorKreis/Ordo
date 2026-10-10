package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vitorkrichter.ordo.core.database.entity.HydrationSettingsEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface HydrationSettingsDao {
    @Query("SELECT * FROM hydration_settings WHERE id = :settingsId LIMIT 1")
    fun observeById(settingsId: String = HydrationSettingsEntity.DEFAULT_SETTINGS_ID): Flow<HydrationSettingsEntity?>

    @Query("SELECT * FROM hydration_settings WHERE id = :settingsId LIMIT 1")
    suspend fun findById(settingsId: String = HydrationSettingsEntity.DEFAULT_SETTINGS_ID): HydrationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HydrationSettingsEntity)

    @Transaction
    suspend fun getOrCreateDefault(now: Instant): HydrationSettingsEntity {
        findById()?.let { return it }
        val entity = HydrationSettingsEntity(
            createdAt = now,
            updatedAt = now,
        )
        upsert(entity)
        return entity
    }

    @Query(
        """
        UPDATE hydration_settings
        SET target_ml = :targetMl, target_mode = :targetMode, updated_at = :updatedAt
        WHERE id = :settingsId
        """,
    )
    suspend fun setTarget(
        targetMl: Int,
        targetMode: String,
        updatedAt: Instant,
        settingsId: String = HydrationSettingsEntity.DEFAULT_SETTINGS_ID,
    ): Int

    @Transaction
    suspend fun setManualTarget(targetMl: Int, updatedAt: Instant): Int {
        getOrCreateDefault(updatedAt)
        return setTarget(targetMl, HydrationSettingsEntity.CUSTOM_TARGET_MODE, updatedAt)
    }
}
