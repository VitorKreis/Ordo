package com.vitorkrichter.ordo.domain.model

data class SessionExercise(
    val id: String,
    val sessionId: WorkoutSessionId,
    val sourceExerciseId: ExerciseId?,
    val exerciseNameSnapshot: String,
    val position: Int,
    val trackingModeSnapshot: TrackingMode,
    val restBetweenSetsSecondsSnapshot: Int,
    val transitionRestSecondsSnapshot: Int,
    val notesSnapshot: String? = null,
    val status: SessionExerciseStatus,
)
