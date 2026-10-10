package com.vitorkrichter.ordo.domain.model

import java.time.Instant

data class Exercise(
    val id: ExerciseId,
    val name: String,
    val normalizedName: String,
    val category: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)
