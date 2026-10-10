package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vitorkrichter.ordo.core.database.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun findById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE is_archived = 0 ORDER BY normalized_name")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("UPDATE exercises SET name = :name, normalized_name = :normalizedName, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateName(id: String, name: String, normalizedName: String, updatedAt: Instant)

    @Query("SELECT COUNT(*) FROM workout_exercise_templates WHERE exercise_id = :exerciseId")
    suspend fun countTemplateReferences(exerciseId: String): Int

    @Query("UPDATE exercises SET is_archived = 1, updated_at = :updatedAt WHERE id = :exerciseId")
    suspend fun archive(exerciseId: String, updatedAt: Instant)
}
