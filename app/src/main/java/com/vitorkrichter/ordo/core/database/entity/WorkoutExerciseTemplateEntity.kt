package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorkrichter.ordo.domain.model.TrackingMode

@Entity(
    tableName = "workout_exercise_templates",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_template_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["workout_template_id"]),
        Index(value = ["exercise_id"]),
        Index(value = ["workout_template_id", "position"]),
    ],
)
data class WorkoutExerciseTemplateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_template_id") val workoutTemplateId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "tracking_mode") val trackingMode: TrackingMode,
    @ColumnInfo(name = "rest_between_sets_seconds") val restBetweenSetsSeconds: Int,
    @ColumnInfo(name = "transition_rest_seconds") val transitionRestSeconds: Int,
    val notes: String?,
)
