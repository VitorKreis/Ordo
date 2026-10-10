package com.vitorkrichter.ordo.domain.model

data class SetTemplate(
    val id: String,
    val workoutExerciseTemplateId: String,
    val position: Int,
    val minimumRepetitions: Int? = null,
    val maximumRepetitions: Int? = null,
    val targetRepetitions: Int? = null,
    val suggestedLoadKg: Double? = null,
)
