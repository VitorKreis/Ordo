package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorkrichter.ordo.domain.model.WorkoutDivisionType
import com.vitorkrichter.ordo.domain.model.WorkoutSelectionMode
import java.time.Instant

@Entity(
    tableName = "training_plans",
    indices = [
        Index(value = ["is_active"]),
    ],
)
data class TrainingPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "workout_division_type") val workoutDivisionType: WorkoutDivisionType,
    @ColumnInfo(name = "workout_selection_mode") val workoutSelectionMode: WorkoutSelectionMode,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
)
