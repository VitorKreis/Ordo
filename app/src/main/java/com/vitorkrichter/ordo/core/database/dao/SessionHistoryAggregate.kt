package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.vitorkrichter.ordo.core.database.entity.SessionExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.SessionSetEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutSessionEntity

data class SessionExerciseWithSets(
    @Embedded val exercise: SessionExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "session_exercise_id",
    )
    val sets: List<SessionSetEntity>,
)

data class WorkoutSessionWithDetails(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "session_id",
    )
    val exercises: List<SessionExerciseWithSets>,
)
