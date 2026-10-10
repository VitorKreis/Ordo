package com.vitorkrichter.ordo.domain.model

import java.time.Instant
import java.time.LocalDate

data class TrainingDay(
    val id: TrainingDayId,
    val date: LocalDate,
    val planId: TrainingPlanId,
    val workoutTemplateId: WorkoutTemplateId? = null,
    val status: TrainingDayStatus,
    val sessionId: WorkoutSessionId? = null,
    val rescheduledFromId: TrainingDayId? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
