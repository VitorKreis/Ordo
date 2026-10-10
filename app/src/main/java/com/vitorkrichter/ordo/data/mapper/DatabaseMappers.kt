package com.vitorkrichter.ordo.data.mapper

import com.vitorkrichter.ordo.core.database.entity.ScheduleRuleEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingDayEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingPlanEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutSessionEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutTemplateEntity
import com.vitorkrichter.ordo.domain.model.ScheduleRule
import com.vitorkrichter.ordo.domain.model.TrainingDay
import com.vitorkrichter.ordo.domain.model.WorkoutSessionId
import com.vitorkrichter.ordo.domain.model.TrainingPlan
import com.vitorkrichter.ordo.domain.model.TrainingPlanId
import com.vitorkrichter.ordo.domain.model.TrainingDayId
import com.vitorkrichter.ordo.domain.model.WorkoutSession
import com.vitorkrichter.ordo.domain.model.WorkoutTemplate
import com.vitorkrichter.ordo.domain.model.WorkoutTemplateId

fun TrainingPlanEntity.toDomain(): TrainingPlan = TrainingPlan(
    id = TrainingPlanId(id),
    name = name,
    workoutDivisionType = workoutDivisionType,
    workoutSelectionMode = workoutSelectionMode,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt,
)

fun WorkoutTemplateEntity.toDomain(): WorkoutTemplate = WorkoutTemplate(
    id = WorkoutTemplateId(id),
    planId = TrainingPlanId(planId),
    code = code,
    name = name,
    position = position,
    notes = notes,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ScheduleRuleEntity.toDomain(): ScheduleRule = ScheduleRule(
    id = id,
    planId = TrainingPlanId(planId),
    weekday = weekday,
    workoutTemplateId = workoutTemplateId?.let(::WorkoutTemplateId),
    isEnabled = isEnabled,
)

fun TrainingDayEntity.toDomain(): TrainingDay = TrainingDay(
    id = TrainingDayId(id),
    date = date,
    planId = TrainingPlanId(planId),
    workoutTemplateId = workoutTemplateId?.let(::WorkoutTemplateId),
    status = status,
    sessionId = sessionId?.let(::WorkoutSessionId),
    rescheduledFromId = rescheduledFromId?.let(::TrainingDayId),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun WorkoutSessionEntity.toDomain(): WorkoutSession = WorkoutSession(
    id = WorkoutSessionId(id),
    planId = TrainingPlanId(planId),
    sourceWorkoutTemplateId = sourceWorkoutTemplateId?.let(::WorkoutTemplateId),
    trainingDayId = trainingDayId?.let(::TrainingDayId),
    workoutNameSnapshot = workoutNameSnapshot,
    status = status,
    startedAt = startedAt,
    finishedAt = finishedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
