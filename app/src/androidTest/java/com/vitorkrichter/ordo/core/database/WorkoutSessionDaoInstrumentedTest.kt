package com.vitorkrichter.ordo.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorkrichter.ordo.core.database.entity.ExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.SessionExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.SessionSetEntity
import com.vitorkrichter.ordo.core.database.entity.SetTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingPlanEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutExerciseTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutSessionEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutTemplateEntity
import com.vitorkrichter.ordo.domain.model.SessionExerciseStatus
import com.vitorkrichter.ordo.domain.model.SessionSetStatus
import com.vitorkrichter.ordo.domain.model.TrackingMode
import com.vitorkrichter.ordo.domain.model.WorkoutDivisionType
import com.vitorkrichter.ordo.domain.model.WorkoutSelectionMode
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSessionDaoInstrumentedTest {
    private lateinit var database: OrdoDatabase
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OrdoDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun snapshotCanBeResumedAndFinalizationIsIdempotent() = runBlocking {
        val planId = "plan-${UUID.randomUUID()}"
        val workoutId = "workout-${UUID.randomUUID()}"
        val exerciseId = "exercise-${UUID.randomUUID()}"
        val workoutExerciseId = "workout-exercise-${UUID.randomUUID()}"
        val templateSetId = "template-set-${UUID.randomUUID()}"
        val sessionId = "session-${UUID.randomUUID()}"
        val sessionExerciseId = "session-exercise-${UUID.randomUUID()}"
        val sessionSetId = "session-set-${UUID.randomUUID()}"

        database.trainingPlanDao().insert(
            TrainingPlanEntity(
                id = planId,
                name = "Plano",
                workoutDivisionType = WorkoutDivisionType.FULL_BODY,
                workoutSelectionMode = WorkoutSelectionMode.SEQUENTIAL,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                archivedAt = null,
            ),
        )
        database.workoutTemplateDao().insertWorkout(
            WorkoutTemplateEntity(
                id = workoutId,
                planId = planId,
                code = "A",
                name = "Treino original",
                position = 0,
                notes = null,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.exerciseDao().insert(
            ExerciseEntity(
                id = exerciseId,
                name = "Agachamento",
                normalizedName = "agachamento",
                category = null,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        database.workoutTemplateDao().insertWorkoutExercise(
            WorkoutExerciseTemplateEntity(
                id = workoutExerciseId,
                workoutTemplateId = workoutId,
                exerciseId = exerciseId,
                position = 0,
                trackingMode = TrackingMode.REPETITIONS,
                restBetweenSetsSeconds = 60,
                transitionRestSeconds = 60,
                notes = null,
            ),
        )
        database.workoutTemplateDao().insertSet(
            SetTemplateEntity(
                id = templateSetId,
                workoutExerciseTemplateId = workoutExerciseId,
                position = 0,
                minimumRepetitions = 8,
                maximumRepetitions = 12,
                targetRepetitions = 10,
                suggestedLoadKg = null,
            ),
        )

        database.workoutSessionDao().insertSessionSnapshot(
            session = WorkoutSessionEntity(
                id = sessionId,
                planId = planId,
                sourceWorkoutTemplateId = workoutId,
                trainingDayId = null,
                workoutNameSnapshot = "Treino original",
                status = WorkoutSessionStatus.IN_PROGRESS,
                startedAt = now,
                finishedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
            exercises = listOf(
                SessionExerciseEntity(
                    id = sessionExerciseId,
                    sessionId = sessionId,
                    sourceExerciseId = exerciseId,
                    exerciseNameSnapshot = "Agachamento",
                    position = 0,
                    trackingModeSnapshot = TrackingMode.REPETITIONS,
                    restBetweenSetsSecondsSnapshot = 60,
                    transitionRestSecondsSnapshot = 60,
                    notesSnapshot = null,
                    status = SessionExerciseStatus.IN_PROGRESS,
                ),
            ),
            sets = listOf(
                SessionSetEntity(
                    id = sessionSetId,
                    sessionExerciseId = sessionExerciseId,
                    position = 0,
                    plannedMinimumRepetitions = 8,
                    plannedMaximumRepetitions = 12,
                    plannedTargetRepetitions = 10,
                    suggestedLoadKg = null,
                    actualRepetitions = 10,
                    actualLoadKg = null,
                    status = SessionSetStatus.COMPLETED,
                ),
            ),
        )

        val resumed = database.workoutSessionDao().activeSession()
        assertNotNull(resumed)
        assertEquals(sessionId, resumed?.id)
        assertEquals(10, database.workoutSessionDao().setsForSessionExercise(sessionExerciseId).single().actualRepetitions)

        assertEquals(
            1,
            database.workoutSessionDao().finishActiveSession(
                sessionId = sessionId,
                status = WorkoutSessionStatus.COMPLETED,
                finishedAt = now,
                updatedAt = now,
            ),
        )
        assertEquals(
            0,
            database.workoutSessionDao().finishActiveSession(
                sessionId = sessionId,
                status = WorkoutSessionStatus.COMPLETED,
                finishedAt = now,
                updatedAt = now,
            ),
        )
        assertNull(database.workoutSessionDao().activeSession())
        assertEquals(WorkoutSessionStatus.COMPLETED, database.workoutSessionDao().sessionById(sessionId)?.status)
    }
}
