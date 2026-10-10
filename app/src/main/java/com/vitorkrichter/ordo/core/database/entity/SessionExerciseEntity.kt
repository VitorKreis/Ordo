package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorkrichter.ordo.domain.model.SessionExerciseStatus
import com.vitorkrichter.ordo.domain.model.TrackingMode

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_exercise_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["source_exercise_id"]),
        Index(value = ["session_id", "position"]),
    ],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "source_exercise_id") val sourceExerciseId: String?,
    @ColumnInfo(name = "exercise_name_snapshot") val exerciseNameSnapshot: String,
    val position: Int,
    @ColumnInfo(name = "tracking_mode_snapshot") val trackingModeSnapshot: TrackingMode,
    @ColumnInfo(name = "rest_between_sets_seconds_snapshot") val restBetweenSetsSecondsSnapshot: Int,
    @ColumnInfo(name = "transition_rest_seconds_snapshot") val transitionRestSecondsSnapshot: Int,
    @ColumnInfo(name = "notes_snapshot") val notesSnapshot: String?,
    val status: SessionExerciseStatus,
)
