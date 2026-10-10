package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorkrichter.ordo.domain.model.SessionSetStatus

@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["session_exercise_id"]),
        Index(value = ["session_exercise_id", "position"]),
    ],
)
data class SessionSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_exercise_id") val sessionExerciseId: String,
    val position: Int,
    @ColumnInfo(name = "planned_minimum_repetitions") val plannedMinimumRepetitions: Int?,
    @ColumnInfo(name = "planned_maximum_repetitions") val plannedMaximumRepetitions: Int?,
    @ColumnInfo(name = "planned_target_repetitions") val plannedTargetRepetitions: Int? = null,
    @ColumnInfo(name = "suggested_load_kg") val suggestedLoadKg: Double? = null,
    @ColumnInfo(name = "actual_repetitions") val actualRepetitions: Int?,
    @ColumnInfo(name = "actual_load_kg") val actualLoadKg: Double?,
    val status: SessionSetStatus,
)
