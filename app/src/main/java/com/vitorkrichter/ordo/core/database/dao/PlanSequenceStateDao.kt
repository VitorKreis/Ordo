package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vitorkrichter.ordo.core.database.entity.PlanSequenceStateEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanSequenceStateDao {
    @Query("SELECT * FROM plan_sequence_state WHERE plan_id = :planId LIMIT 1")
    fun observeForPlan(planId: String): Flow<PlanSequenceStateEntity?>

    @Query("SELECT * FROM plan_sequence_state WHERE plan_id = :planId LIMIT 1")
    suspend fun stateForPlan(planId: String): PlanSequenceStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlanSequenceStateEntity)

    @Transaction
    suspend fun getOrCreate(planId: String, now: Instant): PlanSequenceStateEntity {
        stateForPlan(planId)?.let { return it }
        val entity = PlanSequenceStateEntity(
            planId = planId,
            nextWorkoutTemplateId = null,
            cursorIndex = 0,
            revision = 0L,
            lastAdvancedSessionId = null,
            lastAdvancedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        upsert(entity)
        return entity
    }

    @Query(
        """
        UPDATE plan_sequence_state
        SET next_workout_template_id = :nextWorkoutTemplateId,
            cursor_index = :cursorIndex,
            revision = :revision,
            last_advanced_session_id = :advanceId,
            last_advanced_at = :advancedAt,
            updated_at = :updatedAt
        WHERE plan_id = :planId
          AND (last_advanced_session_id IS NULL OR last_advanced_session_id != :advanceId)
        """,
    )
    suspend fun applyAdvanceIfNew(
        planId: String,
        nextWorkoutTemplateId: String?,
        cursorIndex: Int,
        revision: Long,
        advanceId: String,
        advancedAt: Instant,
        updatedAt: Instant,
    ): Int

    @Query(
        """
        UPDATE plan_sequence_state
        SET next_workout_template_id = :nextWorkoutTemplateId,
            cursor_index = :cursorIndex,
            revision = :revision,
            updated_at = :updatedAt
        WHERE plan_id = :planId
        """,
    )
    suspend fun updateCursor(
        planId: String,
        nextWorkoutTemplateId: String?,
        cursorIndex: Int,
        revision: Long,
        updatedAt: Instant,
    ): Int
}
