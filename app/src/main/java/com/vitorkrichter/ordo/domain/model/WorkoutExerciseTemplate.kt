package com.vitorkrichter.ordo.domain.model

data class WorkoutExerciseTemplate(
    val id: String,
    val workoutTemplateId: WorkoutTemplateId,
    val exerciseId: ExerciseId,
    val position: Int,
    val trackingMode: TrackingMode,
    val restBetweenSetsSeconds: Int,
    val transitionRestSeconds: Int,
    val notes: String? = null,
)
