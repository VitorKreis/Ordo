package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vitorkrichter.ordo.core.database.entity.TrainingPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingPlanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrainingPlanEntity)

    @Query("SELECT * FROM training_plans WHERE is_active = 1 AND archived_at IS NULL LIMIT 1")
    fun observeActivePlan(): Flow<TrainingPlanEntity?>

    @Query("SELECT * FROM training_plans WHERE is_active = 1 AND archived_at IS NULL LIMIT 1")
    suspend fun activePlan(): TrainingPlanEntity?

    @Query("SELECT * FROM training_plans WHERE archived_at IS NULL ORDER BY updated_at DESC")
    fun observeAvailablePlans(): Flow<List<TrainingPlanEntity>>

    @Query("SELECT * FROM training_plans WHERE id = :planId LIMIT 1")
    suspend fun planById(planId: String): TrainingPlanEntity?

    @Query("UPDATE training_plans SET is_active = 0, updated_at = :updatedAt WHERE is_active = 1")
    suspend fun deactivateActivePlans(updatedAt: java.time.Instant)

    @Query("UPDATE training_plans SET is_active = 1, updated_at = :updatedAt WHERE id = :planId AND archived_at IS NULL")
    suspend fun activatePlan(planId: String, updatedAt: java.time.Instant)

    @Query("UPDATE training_plans SET name = :name, updated_at = :updatedAt WHERE id = :planId AND archived_at IS NULL")
    suspend fun renamePlan(planId: String, name: String, updatedAt: java.time.Instant)

    @Query("UPDATE training_plans SET archived_at = :archivedAt, is_active = 0, updated_at = :archivedAt WHERE id = :planId AND is_active = 0")
    suspend fun archiveInactivePlan(planId: String, archivedAt: java.time.Instant)
}
