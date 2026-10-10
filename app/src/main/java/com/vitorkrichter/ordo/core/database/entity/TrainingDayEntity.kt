package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorkrichter.ordo.domain.model.TrainingDayStatus
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "training_days",
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
            childColumns = ["workout_template_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["plan_id", "date"], unique = true),
        Index(value = ["workout_template_id"]),
        Index(value = ["session_id"]),
        Index(value = ["rescheduled_from_id"]),
    ],
)
data class TrainingDayEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "workout_template_id") val workoutTemplateId: String?,
    val status: TrainingDayStatus,
    @ColumnInfo(name = "session_id") val sessionId: String?,
    @ColumnInfo(name = "rescheduled_from_id") val rescheduledFromId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
