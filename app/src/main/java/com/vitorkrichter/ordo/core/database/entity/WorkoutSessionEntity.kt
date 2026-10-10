package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import java.time.Instant

@Entity(
    tableName = "workout_sessions",
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
            childColumns = ["source_workout_template_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TrainingDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["training_day_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["source_workout_template_id"]),
        Index(value = ["training_day_id"]),
        Index(value = ["status"]),
        Index(value = ["started_at"]),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "source_workout_template_id") val sourceWorkoutTemplateId: String?,
    @ColumnInfo(name = "training_day_id") val trainingDayId: String?,
    @ColumnInfo(name = "workout_name_snapshot") val workoutNameSnapshot: String,
    val status: WorkoutSessionStatus,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "finished_at") val finishedAt: Instant?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "session_intent", defaultValue = "'NORMAL'")
    val sessionIntent: String = INTENT_NORMAL,
    @ColumnInfo(name = "session_origin", defaultValue = "'LEGACY'")
    val sessionOrigin: String = ORIGIN_LEGACY,
    @ColumnInfo(name = "advances_sequence", defaultValue = "1")
    val advancesSequence: Boolean = true,
    @ColumnInfo(name = "sequence_advance_id") val sequenceAdvanceId: String? = null,
) {
    companion object {
        const val INTENT_NORMAL = "NORMAL"
        const val INTENT_REPEAT = "REPEAT"
        const val INTENT_EXTRA = "EXTRA"
        const val ORIGIN_NORMAL = "NORMAL"
        const val ORIGIN_REPEAT = "REPEAT"
        const val ORIGIN_EXTRA = "EXTRA"
        const val ORIGIN_LEGACY = "LEGACY"
    }
}
