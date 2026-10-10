package com.vitorkrichter.ordo.domain.model

import java.time.Instant

data class WorkoutSession(
    val id: WorkoutSessionId,
    val planId: TrainingPlanId,
    val sourceWorkoutTemplateId: WorkoutTemplateId?,
    val trainingDayId: TrainingDayId? = null,
    val workoutNameSnapshot: String,
    val status: WorkoutSessionStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
