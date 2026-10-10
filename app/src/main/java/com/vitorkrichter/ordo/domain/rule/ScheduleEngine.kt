package com.vitorkrichter.ordo.domain.rule

import com.vitorkrichter.ordo.domain.model.ScheduleRule
import com.vitorkrichter.ordo.domain.model.TrainingDay
import com.vitorkrichter.ordo.domain.model.TrainingDayStatus
import com.vitorkrichter.ordo.domain.model.TrainingPlan
import com.vitorkrichter.ordo.domain.model.TrainingPlanId
import com.vitorkrichter.ordo.domain.model.WorkoutSelectionMode
import com.vitorkrichter.ordo.domain.model.WorkoutSession
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import com.vitorkrichter.ordo.domain.model.WorkoutTemplate
import com.vitorkrichter.ordo.domain.model.WorkoutTemplateId
import java.time.LocalDate

class ScheduleEngine {
    fun workoutForDate(
        input: ScheduleInput,
        date: LocalDate,
    ): ScheduleResult {
        val plan = input.activePlan
        if (plan == null || !plan.isActive || plan.isArchived) {
            return ScheduleResult.NoActivePlan
        }

        activeSessionForPlan(input.sessions, plan.id)
            ?.let { activeSession ->
                return ScheduleResult.ActiveSession(activeSession)
            }

        val trainingDay = input.trainingDays.firstOrNull { it.planId == plan.id && it.date == date }
        if (trainingDay?.status == TrainingDayStatus.RESCHEDULED) {
            return ScheduleResult.NotPlanned
        }

        val rule = input.scheduleRules.firstOrNull {
            it.planId == plan.id && it.weekday == date.dayOfWeek && it.isEnabled
        } ?: return ScheduleResult.NotPlanned

        val workouts = input.workouts
            .filter { it.planId == plan.id && !it.isArchived }
            .sortedBy { it.position }

        if (workouts.isEmpty()) {
            return ScheduleResult.NoWorkoutTemplates
        }

        return when (plan.workoutSelectionMode) {
            WorkoutSelectionMode.FIXED_BY_WEEKDAY -> {
                val workout = rule.workoutTemplateId?.let { workoutId ->
                    workouts.firstOrNull { it.id == workoutId }
                } ?: return ScheduleResult.MissingFixedWorkout(rule)

                ScheduleResult.PlannedWorkout(
                    plan = plan,
                    workout = workout,
                    trainingDay = trainingDay,
                )
            }

            WorkoutSelectionMode.SEQUENTIAL -> {
                ScheduleResult.PlannedWorkout(
                    plan = plan,
                    workout = nextSequentialWorkout(plan, workouts, input.sessions),
                    trainingDay = trainingDay,
                )
            }
        }
    }

    fun nextSequentialWorkout(
        plan: TrainingPlan,
        workouts: List<WorkoutTemplate>,
        sessions: List<WorkoutSession>,
    ): WorkoutTemplate {
        val sequence = workouts
            .filter { it.planId == plan.id && !it.isArchived }
            .sortedBy { it.position }

        require(sequence.isNotEmpty()) { "Sequential planning requires at least one workout template." }

        val sequenceIds = sequence.map { it.id }.toSet()
        activeSessionForPlan(sessions, plan.id)
            ?.sourceWorkoutTemplateId
            ?.let { activeWorkoutId ->
                sequence.firstOrNull { it.id == activeWorkoutId }
            }
            ?.let { activeWorkout ->
                return activeWorkout
            }

        val completedCount = sessions.countCompletedSessionsInSequence(plan.id, sequenceIds)

        return sequence[completedCount % sequence.size]
    }

    private fun activeSessionForPlan(
        sessions: List<WorkoutSession>,
        planId: TrainingPlanId,
    ): WorkoutSession? = sessions
        .asSequence()
        .filter { it.planId == planId && it.status == WorkoutSessionStatus.IN_PROGRESS }
        .sortedWith(compareBy<WorkoutSession> { it.startedAt }.thenBy { it.id.value })
        .firstOrNull()

    private fun List<WorkoutSession>.countCompletedSessionsInSequence(
        planId: TrainingPlanId,
        sequenceIds: Set<WorkoutTemplateId>,
    ): Int = count {
        it.planId == planId &&
            it.status == WorkoutSessionStatus.COMPLETED &&
            it.sourceWorkoutTemplateId in sequenceIds
    }
}

data class ScheduleInput(
    val activePlan: TrainingPlan?,
    val workouts: List<WorkoutTemplate>,
    val scheduleRules: List<ScheduleRule>,
    val sessions: List<WorkoutSession> = emptyList(),
    val trainingDays: List<TrainingDay> = emptyList(),
)

sealed interface ScheduleResult {
    data object NoActivePlan : ScheduleResult
    data object NotPlanned : ScheduleResult
    data object NoWorkoutTemplates : ScheduleResult
    data class MissingFixedWorkout(val rule: ScheduleRule) : ScheduleResult
    data class ActiveSession(val session: WorkoutSession) : ScheduleResult
    data class PlannedWorkout(
        val plan: TrainingPlan,
        val workout: WorkoutTemplate,
        val trainingDay: TrainingDay?,
    ) : ScheduleResult
}
