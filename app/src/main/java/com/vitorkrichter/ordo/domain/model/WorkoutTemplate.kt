package com.vitorkrichter.ordo.domain.model

import java.time.Instant

data class WorkoutTemplate(
    val id: WorkoutTemplateId,
    val planId: TrainingPlanId,
    val code: String,
    val name: String,
    val position: Int,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)
