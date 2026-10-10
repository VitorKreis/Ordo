package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "plan_sequence_state",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["next_workout_template_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["next_workout_template_id"]),
    ],
)
data class PlanSequenceStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "next_workout_template_id") val nextWorkoutTemplateId: String?,
    @ColumnInfo(name = "cursor_index") val cursorIndex: Int,
    val revision: Long,
    @ColumnInfo(name = "last_advanced_session_id") val lastAdvancedSessionId: String?,
    @ColumnInfo(name = "last_advanced_at") val lastAdvancedAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
