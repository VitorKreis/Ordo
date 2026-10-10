package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vitorkrichter.ordo.core.database.entity.SessionExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.SessionSetEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutSessionEntity
import com.vitorkrichter.ordo.domain.model.SessionExerciseStatus
import com.vitorkrichter.ordo.domain.model.SessionSetStatus
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(entity: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionExercises(entities: List<SessionExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionSets(entities: List<SessionSetEntity>)

    @Transaction
    suspend fun insertSessionSnapshot(
        session: WorkoutSessionEntity,
        exercises: List<SessionExerciseEntity>,
        sets: List<SessionSetEntity>,
    ) {
        require(session.status != WorkoutSessionStatus.IN_PROGRESS || activeSession() == null) {
            "Only one workout session may be in progress."
        }
        insertSession(session)
        insertSessionExercises(exercises)
        insertSessionSets(sets)
    }

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY started_at LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY started_at LIMIT 1")
    suspend fun activeSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun sessionById(sessionId: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE plan_id = :planId ORDER BY started_at DESC")
    fun observeSessionsForPlan(planId: String): Flow<List<WorkoutSessionEntity>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE plan_id = :planId ORDER BY started_at DESC")
    fun observeSessionsWithDetailsForPlan(planId: String): Flow<List<WorkoutSessionWithDetails>>

    @Query("SELECT * FROM workout_sessions WHERE plan_id = :planId ORDER BY started_at")
    suspend fun sessionsForPlan(planId: String): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercises WHERE session_id = :sessionId ORDER BY position")
    suspend fun exercisesForSession(sessionId: String): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_sets WHERE session_exercise_id = :sessionExerciseId ORDER BY position")
    suspend fun setsForSessionExercise(sessionExerciseId: String): List<SessionSetEntity>

    @Query(
        """
        SELECT COUNT(*) FROM session_sets ss
        INNER JOIN session_exercises se ON se.id = ss.session_exercise_id
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        WHERE ws.plan_id = :planId
        """,
    )
    fun observeSetChangesForPlan(planId: String): Flow<Int>

    @Query("UPDATE session_sets SET actual_repetitions = :repetitions, actual_load_kg = :loadKg WHERE id = :setId")
    suspend fun updateSetPerformance(setId: String, repetitions: Int?, loadKg: Double?)

    @Query(
        """
        UPDATE session_sets
        SET actual_repetitions = :repetitions,
            actual_load_kg = :loadKg,
            status = :status
        WHERE id = :setId
          AND session_exercise_id IN (
              SELECT se.id
              FROM session_exercises se
              INNER JOIN workout_sessions ws ON ws.id = se.session_id
              WHERE ws.status = 'IN_PROGRESS'
          )
        """,
    )
    suspend fun updateActiveSet(
        setId: String,
        repetitions: Int?,
        loadKg: Double?,
        status: SessionSetStatus,
    )

    @Query(
        """
        UPDATE session_exercises
        SET status = :status
        WHERE id = :sessionExerciseId
          AND session_id IN (SELECT id FROM workout_sessions WHERE status = 'IN_PROGRESS')
        """,
    )
    suspend fun updateActiveExerciseStatus(sessionExerciseId: String, status: SessionExerciseStatus)

    @Query(
        """
        UPDATE session_exercises
        SET position = :position
        WHERE id = :sessionExerciseId
          AND session_id IN (SELECT id FROM workout_sessions WHERE status = 'IN_PROGRESS')
        """,
    )
    suspend fun updateActiveExercisePosition(sessionExerciseId: String, position: Int)

    @Query(
        """
        UPDATE workout_sessions
        SET status = :status,
            finished_at = :finishedAt,
            updated_at = :updatedAt
        WHERE id = :sessionId AND status = 'IN_PROGRESS'
        """,
    )
    suspend fun finishActiveSession(
        sessionId: String,
        status: WorkoutSessionStatus,
        finishedAt: Instant,
        updatedAt: Instant,
    ): Int

    @Query(
        """
        SELECT se.* FROM session_exercises se
        INNER JOIN workout_sessions ws ON ws.id = se.session_id
        WHERE ws.plan_id = :planId
          AND se.source_exercise_id = :sourceExerciseId
          AND ws.status IN ('COMPLETED', 'PARTIALLY_COMPLETED')
        ORDER BY ws.started_at DESC
        LIMIT 1
        """,
    )
    suspend fun latestExerciseForSource(planId: String, sourceExerciseId: String): SessionExerciseEntity?
}
