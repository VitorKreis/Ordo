package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vitorkrichter.ordo.core.database.entity.SetTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutExerciseTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutTemplateEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(entity: WorkoutTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkoutExercise(entity: WorkoutExerciseTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(entity: SetTemplateEntity)

    @Query("SELECT * FROM workout_templates WHERE plan_id = :planId AND is_archived = 0 ORDER BY position")
    fun observeActiveWorkoutsForPlan(planId: String): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE plan_id = :planId AND is_archived = 0 ORDER BY position")
    suspend fun activeWorkoutsForPlan(planId: String): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    suspend fun workoutById(id: String): WorkoutTemplateEntity?

    @Query(
        """
        SELECT COUNT(st.id) + COUNT(wet.id)
        FROM workout_templates wt
        LEFT JOIN workout_exercise_templates wet ON wet.workout_template_id = wt.id
        LEFT JOIN set_templates st ON st.workout_exercise_template_id = wet.id
        WHERE wt.plan_id = :planId
        """,
    )
    fun observeExerciseChangesForPlan(planId: String): Flow<Int>

    @Query("UPDATE workout_templates SET position = :position, updated_at = :updatedAt WHERE id = :workoutTemplateId")
    suspend fun updateWorkoutPosition(workoutTemplateId: String, position: Int, updatedAt: Instant)

    @Query("UPDATE workout_templates SET is_archived = 1, updated_at = :updatedAt WHERE id = :workoutTemplateId")
    suspend fun archiveWorkout(workoutTemplateId: String, updatedAt: Instant)

    @Query("SELECT * FROM workout_exercise_templates WHERE workout_template_id = :workoutTemplateId ORDER BY position")
    suspend fun exercisesForWorkout(workoutTemplateId: String): List<WorkoutExerciseTemplateEntity>

    @Query("SELECT * FROM workout_exercise_templates WHERE id = :id LIMIT 1")
    suspend fun workoutExerciseById(id: String): WorkoutExerciseTemplateEntity?

    @Query("UPDATE workout_exercise_templates SET position = :position WHERE id = :id")
    suspend fun updateWorkoutExercisePosition(id: String, position: Int)

    @Query("SELECT * FROM set_templates WHERE workout_exercise_template_id = :workoutExerciseTemplateId ORDER BY position")
    suspend fun setsForWorkoutExercise(workoutExerciseTemplateId: String): List<SetTemplateEntity>

    @Query(
        """
        UPDATE workout_exercise_templates
        SET rest_between_sets_seconds = :restBetweenSetsSeconds,
            transition_rest_seconds = :transitionRestSeconds
        WHERE id = :workoutExerciseTemplateId
        """,
    )
    suspend fun updateExerciseRest(
        workoutExerciseTemplateId: String,
        restBetweenSetsSeconds: Int,
        transitionRestSeconds: Int,
    )

    @Query(
        """
        UPDATE workout_templates
        SET updated_at = :updatedAt
        WHERE id = (
            SELECT workout_template_id
            FROM workout_exercise_templates
            WHERE id = :workoutExerciseTemplateId
            LIMIT 1
        )
        """,
    )
    suspend fun touchWorkoutForExercise(workoutExerciseTemplateId: String, updatedAt: Instant)

    @Query("UPDATE workout_templates SET updated_at = :updatedAt WHERE id = :workoutTemplateId")
    suspend fun touchWorkout(workoutTemplateId: String, updatedAt: Instant)

    @Query("DELETE FROM set_templates WHERE workout_exercise_template_id = :workoutExerciseTemplateId AND position >= :fromPosition")
    suspend fun deleteSetsFromPosition(workoutExerciseTemplateId: String, fromPosition: Int)

    @Query(
        """
        UPDATE set_templates SET
            minimum_repetitions = :minimum,
            maximum_repetitions = :maximum,
            target_repetitions = :target
        WHERE workout_exercise_template_id = :workoutExerciseTemplateId
        """,
    )
    suspend fun updateSetTargets(
        workoutExerciseTemplateId: String,
        minimum: Int?,
        maximum: Int?,
        target: Int?,
    )

    @Query("DELETE FROM set_templates WHERE workout_exercise_template_id = :workoutExerciseTemplateId")
    suspend fun deleteSetsForWorkoutExercise(workoutExerciseTemplateId: String)

    @Query("DELETE FROM workout_exercise_templates WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: String)

    @Query(
        """
        SELECT
            wet.workout_template_id AS workoutTemplateId,
            wet.id AS workoutExerciseTemplateId,
            e.name AS exerciseName,
            wet.position AS position,
            wet.tracking_mode AS trackingMode,
            wet.rest_between_sets_seconds AS restBetweenSetsSeconds,
            wet.transition_rest_seconds AS transitionRestSeconds,
            COUNT(st.id) AS seriesCount,
            MIN(st.minimum_repetitions) AS minimumRepetitions,
            MAX(st.maximum_repetitions) AS maximumRepetitions,
            MAX(st.target_repetitions) AS targetRepetitions
        FROM workout_exercise_templates wet
        INNER JOIN exercises e ON e.id = wet.exercise_id
        LEFT JOIN set_templates st ON st.workout_exercise_template_id = wet.id
        WHERE wet.workout_template_id = :workoutTemplateId
        GROUP BY wet.id
        ORDER BY wet.position
        """,
    )
    suspend fun exerciseSummariesForWorkout(workoutTemplateId: String): List<WorkoutExerciseSummary>

    @Transaction
    suspend fun insertWorkoutSnapshot(
        workout: WorkoutTemplateEntity,
        exercises: List<WorkoutExerciseTemplateEntity>,
        sets: List<SetTemplateEntity>,
    ) {
        insertWorkout(workout)
        exercises.forEach { insertWorkoutExercise(it) }
        sets.forEach { insertSet(it) }
    }
}
