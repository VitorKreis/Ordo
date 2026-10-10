package com.vitorkrichter.ordo.domain.model

import java.time.Instant

data class TrainingPlan(
    val id: TrainingPlanId,
    val name: String,
    val workoutDivisionType: WorkoutDivisionType,
    val workoutSelectionMode: WorkoutSelectionMode,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean = archivedAt != null
}
