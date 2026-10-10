package com.vitorkrichter.ordo.core.database

import androidx.room.TypeConverter
import com.vitorkrichter.ordo.domain.model.SessionExerciseStatus
import com.vitorkrichter.ordo.domain.model.SessionSetStatus
import com.vitorkrichter.ordo.domain.model.TrackingMode
import com.vitorkrichter.ordo.domain.model.TrainingDayStatus
import com.vitorkrichter.ordo.domain.model.WorkoutDivisionType
import com.vitorkrichter.ordo.domain.model.WorkoutSelectionMode
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class OrdoTypeConverters {
    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun dayOfWeekToIsoValue(value: DayOfWeek?): Int? = value?.value

    @TypeConverter
    fun isoValueToDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)

    @TypeConverter
    fun workoutDivisionTypeToString(value: WorkoutDivisionType?): String? = value?.name

    @TypeConverter
    fun stringToWorkoutDivisionType(value: String?): WorkoutDivisionType? = value?.let(WorkoutDivisionType::valueOf)

    @TypeConverter
    fun workoutSelectionModeToString(value: WorkoutSelectionMode?): String? = value?.name

    @TypeConverter
    fun stringToWorkoutSelectionMode(value: String?): WorkoutSelectionMode? = value?.let(WorkoutSelectionMode::valueOf)

    @TypeConverter
    fun trackingModeToString(value: TrackingMode?): String? = value?.name

    @TypeConverter
    fun stringToTrackingMode(value: String?): TrackingMode? = value?.let(TrackingMode::valueOf)

    @TypeConverter
    fun trainingDayStatusToString(value: TrainingDayStatus?): String? = value?.name

    @TypeConverter
    fun stringToTrainingDayStatus(value: String?): TrainingDayStatus? = value?.let(TrainingDayStatus::valueOf)

    @TypeConverter
    fun workoutSessionStatusToString(value: WorkoutSessionStatus?): String? = value?.name

    @TypeConverter
    fun stringToWorkoutSessionStatus(value: String?): WorkoutSessionStatus? = value?.let(WorkoutSessionStatus::valueOf)

    @TypeConverter
    fun sessionExerciseStatusToString(value: SessionExerciseStatus?): String? = value?.name

    @TypeConverter
    fun stringToSessionExerciseStatus(value: String?): SessionExerciseStatus? = value?.let(SessionExerciseStatus::valueOf)

    @TypeConverter
    fun sessionSetStatusToString(value: SessionSetStatus?): String? = value?.name

    @TypeConverter
    fun stringToSessionSetStatus(value: String?): SessionSetStatus? = value?.let(SessionSetStatus::valueOf)
}
