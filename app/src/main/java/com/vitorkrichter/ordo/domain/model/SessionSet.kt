package com.vitorkrichter.ordo.domain.model

data class SessionSet(
    val id: String,
    val sessionExerciseId: String,
    val position: Int,
    val plannedMinimumRepetitions: Int? = null,
    val plannedMaximumRepetitions: Int? = null,
    val actualRepetitions: Int? = null,
    val actualLoadKg: Double? = null,
    val status: SessionSetStatus,
)
