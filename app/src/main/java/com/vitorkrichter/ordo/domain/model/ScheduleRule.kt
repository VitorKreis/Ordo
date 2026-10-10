package com.vitorkrichter.ordo.domain.model

import java.time.DayOfWeek

data class ScheduleRule(
    val id: String,
    val planId: TrainingPlanId,
    val weekday: DayOfWeek,
    val workoutTemplateId: WorkoutTemplateId? = null,
    val isEnabled: Boolean,
)
