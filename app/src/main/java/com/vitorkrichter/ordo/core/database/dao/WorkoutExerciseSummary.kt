package com.vitorkrichter.ordo.core.database.dao

import com.vitorkrichter.ordo.domain.model.TrackingMode

data class WorkoutExerciseSummary(
    val workoutTemplateId: String,
    val workoutExerciseTemplateId: String,
    val exerciseName: String,
    val position: Int,
    val trackingMode: TrackingMode,
    val restBetweenSetsSeconds: Int,
    val transitionRestSeconds: Int,
    val seriesCount: Int,
    val minimumRepetitions: Int?,
    val maximumRepetitions: Int?,
    val targetRepetitions: Int?,
)
