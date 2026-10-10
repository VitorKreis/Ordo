package com.vitorkrichter.ordo.domain.rule

import com.vitorkrichter.ordo.domain.model.ScheduleRule
import com.vitorkrichter.ordo.domain.model.TrainingDay
import com.vitorkrichter.ordo.domain.model.TrainingDayId
import com.vitorkrichter.ordo.domain.model.TrainingDayStatus
import com.vitorkrichter.ordo.domain.model.TrainingPlan
import com.vitorkrichter.ordo.domain.model.TrainingPlanId
import com.vitorkrichter.ordo.domain.model.WorkoutDivisionType
import com.vitorkrichter.ordo.domain.model.WorkoutSelectionMode
import com.vitorkrichter.ordo.domain.model.WorkoutSession
import com.vitorkrichter.ordo.domain.model.WorkoutSessionId
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import com.vitorkrichter.ordo.domain.model.WorkoutTemplate
import com.vitorkrichter.ordo.domain.model.WorkoutTemplateId
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScheduleEngineTest {
    private val engine = ScheduleEngine()
    private val now: Instant = Instant.parse("2026-08-04T12:00:00Z")
    private val monday: LocalDate = LocalDate.parse("2026-08-03")
    private val tuesday: LocalDate = LocalDate.parse("2026-08-04")
    private val wednesday: LocalDate = LocalDate.parse("2026-08-05")
    private val planId = TrainingPlanId("plan-1")
    private val workoutAId = WorkoutTemplateId("workout-a")
    private val workoutBId = WorkoutTemplateId("workout-b")
    private val workoutCId = WorkoutTemplateId("workout-c")

    @Test
    fun sequentialPlanWithoutPreviousSessionReturnsFirstWorkout() {
        val result = engine.workoutForDate(
            input = sequentialInput(),
            date = monday,
        )

        assertEquals("A", result.plannedWorkoutCode())
    }

    @Test
    fun completedSessionAdvancesFromAToB() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                sessions = listOf(session("session-a", workoutAId, WorkoutSessionStatus.COMPLETED)),
            ),
            date = wednesday,
        )

        assertEquals("B", result.plannedWorkoutCode())
    }

    @Test
    fun partialSessionKeepsSameWorkoutAsNext() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                sessions = listOf(session("session-a-partial", workoutAId, WorkoutSessionStatus.PARTIALLY_COMPLETED)),
            ),
            date = wednesday,
        )

        assertEquals("A", result.plannedWorkoutCode())
    }

    @Test
    fun partialSessionAfterCompletedWorkoutDoesNotAdvancePastCurrentWorkout() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                sessions = listOf(
                    session("session-a-complete", workoutAId, WorkoutSessionStatus.COMPLETED),
                    session("session-b-partial", workoutBId, WorkoutSessionStatus.PARTIALLY_COMPLETED),
                ),
            ),
            date = wednesday,
        )

        assertEquals("B", result.plannedWorkoutCode())
    }

    @Test
    fun canceledSessionDoesNotAdvanceSequence() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                sessions = listOf(
                    session("session-a-canceled", workoutAId, WorkoutSessionStatus.CANCELED),
                ),
            ),
            date = wednesday,
        )

        assertEquals("A", result.plannedWorkoutCode())
    }

    @Test
    fun missedAndCanceledTrainingDaysDoNotAdvanceAfterCompletedWorkout() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                sessions = listOf(session("session-a", workoutAId, WorkoutSessionStatus.COMPLETED)),
                trainingDays = listOf(
                    trainingDay(monday, TrainingDayStatus.MISSED),
                    trainingDay(tuesday, TrainingDayStatus.CANCELED),
                ),
            ),
            date = wednesday,
        )

        assertEquals("B", result.plannedWorkoutCode())
    }

    @Test
    fun rescheduledTrainingDayIsNotPlanned() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                trainingDays = listOf(trainingDay(monday, TrainingDayStatus.RESCHEDULED)),
            ),
            date = monday,
        )

        assertEquals(ScheduleResult.NotPlanned, result)
    }

    @Test
    fun missedTrainingDayDoesNotAdvanceSequence() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                trainingDays = listOf(trainingDay(monday, TrainingDayStatus.MISSED)),
            ),
            date = wednesday,
        )

        assertEquals("A", result.plannedWorkoutCode())
    }

    @Test
    fun changingPlannedWeekdaysDoesNotResetOrChangeSequence() {
        val input = sequentialInput(
            rules = listOf(
                rule(DayOfWeek.TUESDAY),
                rule(DayOfWeek.THURSDAY),
            ),
            sessions = listOf(session("session-a", workoutAId, WorkoutSessionStatus.COMPLETED)),
        )

        val result = engine.workoutForDate(input, tuesday)

        assertEquals("B", result.plannedWorkoutCode())
    }

    @Test
    fun sequenceReturnsToStartAfterLastWorkout() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                sessions = listOf(
                    session("session-a", workoutAId, WorkoutSessionStatus.COMPLETED),
                    session("session-b", workoutBId, WorkoutSessionStatus.COMPLETED),
                    session("session-c", workoutCId, WorkoutSessionStatus.COMPLETED),
                ),
            ),
            date = wednesday,
        )

        assertEquals("A", result.plannedWorkoutCode())
    }

    @Test
    fun sequentialPlannedDayIsNotTiedToWorkoutLetter() {
        val mondayResult = engine.workoutForDate(
            input = sequentialInput(
                sessions = listOf(session("session-a", workoutAId, WorkoutSessionStatus.COMPLETED)),
            ),
            date = monday,
        )

        assertEquals("B", mondayResult.plannedWorkoutCode())
    }

    @Test
    fun fixedPlanUsesWorkoutAssignedToWeekday() {
        val result = engine.workoutForDate(
            input = ScheduleInput(
                activePlan = plan(WorkoutSelectionMode.FIXED_BY_WEEKDAY),
                workouts = workouts(),
                scheduleRules = listOf(rule(DayOfWeek.MONDAY, workoutBId)),
            ),
            date = monday,
        )

        assertEquals("B", result.plannedWorkoutCode())
    }

    @Test
    fun fixedPlanIgnoresSequentialProgression() {
        val result = engine.workoutForDate(
            input = ScheduleInput(
                activePlan = plan(WorkoutSelectionMode.FIXED_BY_WEEKDAY),
                workouts = workouts(),
                scheduleRules = listOf(rule(DayOfWeek.MONDAY, workoutBId)),
                sessions = listOf(
                    session("session-a", workoutAId, WorkoutSessionStatus.COMPLETED),
                    session("session-b", workoutBId, WorkoutSessionStatus.COMPLETED),
                ),
            ),
            date = monday,
        )

        assertEquals("B", result.plannedWorkoutCode())
    }

    @Test
    fun activeSessionTakesPriorityOverTodaysPlan() {
        val active = session("active-session", workoutBId, WorkoutSessionStatus.IN_PROGRESS)

        val result = engine.workoutForDate(
            input = sequentialInput(sessions = listOf(active)),
            date = monday,
        )

        assertSame(active, (result as ScheduleResult.ActiveSession).session)
    }

    @Test
    fun activeSessionTakesPriorityInFixedMode() {
        val active = session("active-session", workoutCId, WorkoutSessionStatus.IN_PROGRESS)

        val result = engine.workoutForDate(
            input = ScheduleInput(
                activePlan = plan(WorkoutSelectionMode.FIXED_BY_WEEKDAY),
                workouts = workouts(),
                scheduleRules = listOf(rule(DayOfWeek.MONDAY, workoutAId)),
                sessions = listOf(active),
            ),
            date = monday,
        )

        assertSame(active, (result as ScheduleResult.ActiveSession).session)
    }

    @Test
    fun activeSessionIsAlsoReturnedBySequentialProgression() {
        val active = session("active-session", workoutBId, WorkoutSessionStatus.IN_PROGRESS)

        val workout = engine.nextSequentialWorkout(
            plan = plan(WorkoutSelectionMode.SEQUENTIAL),
            workouts = workouts(),
            sessions = listOf(active),
        )

        assertEquals("B", workout.code)
    }

    @Test
    fun archivedPlanDoesNotReturnWorkout() {
        val result = engine.workoutForDate(
            input = sequentialInput(
                activePlan = plan(WorkoutSelectionMode.SEQUENTIAL, archived = true),
            ),
            date = monday,
        )

        assertEquals(ScheduleResult.NoActivePlan, result)
    }

    private fun sequentialInput(
        activePlan: TrainingPlan = plan(WorkoutSelectionMode.SEQUENTIAL),
        rules: List<ScheduleRule> = listOf(
            rule(DayOfWeek.MONDAY),
            rule(DayOfWeek.WEDNESDAY),
            rule(DayOfWeek.FRIDAY),
        ),
        sessions: List<WorkoutSession> = emptyList(),
        trainingDays: List<TrainingDay> = emptyList(),
    ): ScheduleInput = ScheduleInput(
        activePlan = activePlan,
        workouts = workouts(),
        scheduleRules = rules,
        sessions = sessions,
        trainingDays = trainingDays,
    )

    private fun plan(
        selectionMode: WorkoutSelectionMode,
        archived: Boolean = false,
    ): TrainingPlan = TrainingPlan(
        id = planId,
        name = "Plano ABC",
        workoutDivisionType = WorkoutDivisionType.ABC,
        workoutSelectionMode = selectionMode,
        isActive = true,
        createdAt = now,
        updatedAt = now,
        archivedAt = if (archived) now else null,
    )

    private fun workouts(): List<WorkoutTemplate> = listOf(
        workout(workoutAId, "A", 0),
        workout(workoutBId, "B", 1),
        workout(workoutCId, "C", 2),
    )

    private fun workout(
        id: WorkoutTemplateId,
        code: String,
        position: Int,
    ): WorkoutTemplate = WorkoutTemplate(
        id = id,
        planId = planId,
        code = code,
        name = "Treino $code",
        position = position,
        createdAt = now,
        updatedAt = now,
    )

    private fun rule(
        weekday: DayOfWeek,
        workoutTemplateId: WorkoutTemplateId? = null,
    ): ScheduleRule = ScheduleRule(
        id = "rule-$weekday",
        planId = planId,
        weekday = weekday,
        workoutTemplateId = workoutTemplateId,
        isEnabled = true,
    )

    private fun session(
        id: String,
        workoutTemplateId: WorkoutTemplateId,
        status: WorkoutSessionStatus,
    ): WorkoutSession = WorkoutSession(
        id = WorkoutSessionId(id),
        planId = planId,
        sourceWorkoutTemplateId = workoutTemplateId,
        workoutNameSnapshot = "Treino",
        status = status,
        startedAt = now,
        finishedAt = if (status == WorkoutSessionStatus.IN_PROGRESS) null else now,
        createdAt = now,
        updatedAt = now,
    )

    private fun trainingDay(
        date: LocalDate,
        status: TrainingDayStatus,
    ): TrainingDay = TrainingDay(
        id = TrainingDayId("day-$date"),
        date = date,
        planId = planId,
        status = status,
        createdAt = now,
        updatedAt = now,
    )

    private fun ScheduleResult.plannedWorkoutCode(): String {
        if (this is ScheduleResult.PlannedWorkout) {
            return workout.code
        }
        throw AssertionError("Expected PlannedWorkout but was $this")
    }
}
