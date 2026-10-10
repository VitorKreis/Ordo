package com.vitorkrichter.ordo.data.repository

import com.vitorkrichter.ordo.domain.model.SessionSetStatus
import com.vitorkrichter.ordo.domain.model.TrackingMode
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

sealed interface DashboardSnapshot {
    data object Empty : DashboardSnapshot
    data class Ready(
        val planName: String,
        val isTodayPlanned: Boolean,
        val todayWorkoutCode: String?,
        val todayWorkoutName: String?,
        val nextWorkoutId: String?,
        val nextWorkoutCode: String?,
        val nextWorkoutName: String?,
        val nextWorkoutPosition: Int?,
        val workoutCount: Int,
        val completedSessionCount: Int,
        val partialSessionCount: Int,
        val lastFiveDays: List<HomeHistoryDayUi>,
    ) : DashboardSnapshot
}

data class HomeHistoryDayUi(
    val date: LocalDate,
    val workoutCode: String?,
    val workoutName: String?,
    val status: HomeHistoryStatusUi,
)

enum class HomeHistoryStatusUi { COMPLETED, PARTIAL, CANCELED, IN_PROGRESS, NO_RECORD }

sealed interface WorkoutsSnapshot {
    data object Empty : WorkoutsSnapshot
    data class Ready(val planName: String, val workouts: List<WorkoutUi>) : WorkoutsSnapshot
}

data class AvailablePlanUi(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val workoutCount: Int,
    val workoutLabels: List<String>,
    val trainingWeekdays: Set<DayOfWeek>,
)

data class WorkoutUi(
    val id: String,
    val code: String,
    val name: String,
    val exerciseCount: Int,
    val exercises: List<WorkoutExerciseUi>,
)

enum class MoveWorkoutDirection { UP, DOWN }

data class WorkoutExerciseUi(
    val id: String,
    val name: String,
    val seriesCount: Int,
    val repetitions: String,
    val restBetweenSetsSeconds: Int,
    val transitionRestSeconds: Int,
    val trackingMode: TrackingMode,
)

data class WorkoutExerciseEdit(
    val workoutExerciseTemplateId: String,
    val exerciseName: String,
    val repetitions: String,
    val seriesCount: Int,
    val restBetweenSetsSeconds: Int,
    val transitionRestSeconds: Int,
)

data class NewWorkoutExerciseInput(
    val workoutId: String,
    val name: String,
    val seriesCount: Int,
    val repetitions: String,
    val restBetweenSetsSeconds: Int,
    val transitionRestSeconds: Int,
)

data class WorkoutDetailUi(
    val workoutId: String,
    val code: String,
    val name: String,
    val sequenceLabel: String,
    val exercises: List<WorkoutExerciseDetailUi>,
    val sessionId: String? = null,
    val startedAtEpochMillis: Long? = null,
)

data class WorkoutExerciseDetailUi(
    val id: String,
    val sourceExerciseId: String?,
    val name: String,
    val position: Int,
    val seriesCount: Int,
    val repetitions: String,
    val restBetweenSetsSeconds: Int,
    val transitionRestSeconds: Int,
    val trackingMode: TrackingMode,
    val notes: String?,
    val sets: List<WorkoutSetDetailUi>,
    val previousPerformance: List<PreviousSetPerformanceUi> = emptyList(),
)

data class PreviousSetPerformanceUi(val repetitions: Int?, val loadKg: Double?)

data class WorkoutSetDetailUi(
    val id: String,
    val position: Int,
    val minimumRepetitions: Int?,
    val maximumRepetitions: Int?,
    val targetRepetitions: Int?,
    val suggestedLoadKg: Double?,
    val actualRepetitions: Int? = null,
    val actualLoadKg: Double? = null,
    val isCompleted: Boolean = false,
) {
    fun key(exerciseId: String): String = "$exerciseId:$id"
}

data class SetPerformance(val actualRepetitions: Int?, val actualLoadKg: Double?)

sealed interface HistorySnapshot {
    data object Empty : HistorySnapshot
    data class Ready(val planName: String, val sessions: List<HistorySessionUi>) : HistorySnapshot
}

data class HistorySessionUi(
    val workoutCode: String?,
    val workoutName: String,
    val status: WorkoutSessionStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val exercises: List<HistoryExerciseUi>,
)

data class HistoryExerciseUi(
    val sourceExerciseId: String?,
    val name: String,
    val position: Int,
    val sets: List<HistorySetUi>,
)

data class HistorySetUi(
    val id: String,
    val position: Int,
    val plannedMinimumRepetitions: Int?,
    val plannedMaximumRepetitions: Int?,
    val actualRepetitions: Int?,
    val actualLoadKg: Double?,
    val status: SessionSetStatus,
)

sealed interface ProgressSnapshot {
    data object Empty : ProgressSnapshot
    data class Ready(val planName: String, val exercises: List<ProgressExerciseUi>) : ProgressSnapshot
}

data class ProgressExerciseUi(
    val sourceExerciseId: String?,
    val name: String,
    val sessionCount: Int,
    val maxLoadKg: Double?,
    val maxRepetitions: Int?,
    val totalVolumeKg: Double,
    val records: List<ProgressRecordUi>,
)

data class ProgressRecordUi(
    val date: Instant,
    val workoutName: String,
    val sets: List<HistorySetUi>,
)

sealed interface CalendarSnapshot {
    data object Empty : CalendarSnapshot
    data class Ready(
        val planName: String,
        val scheduleDays: List<ScheduleDayUi>,
        val days: List<CalendarDayUi>,
    ) : CalendarSnapshot
}

data class ScheduleDayUi(val weekday: DayOfWeek, val isEnabled: Boolean)

data class CalendarDayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val isSkippedOverride: Boolean,
    val workoutCode: String?,
    val workoutName: String?,
    val status: CalendarDayStatusUi,
)

enum class CalendarDayStatusUi { PLANNED, NO_WORKOUT, COMPLETED, PARTIAL, CANCELED, IN_PROGRESS }
