package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "set_templates",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_exercise_template_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["workout_exercise_template_id"]),
        Index(value = ["workout_exercise_template_id", "position"]),
    ],
)
data class SetTemplateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_exercise_template_id") val workoutExerciseTemplateId: String,
    val position: Int,
    @ColumnInfo(name = "minimum_repetitions") val minimumRepetitions: Int?,
    @ColumnInfo(name = "maximum_repetitions") val maximumRepetitions: Int?,
    @ColumnInfo(name = "target_repetitions") val targetRepetitions: Int?,
    @ColumnInfo(name = "suggested_load_kg") val suggestedLoadKg: Double?,
)
