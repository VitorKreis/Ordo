package com.vitorkrichter.ordo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vitorkrichter.ordo.core.database.dao.ExerciseDao
import com.vitorkrichter.ordo.core.database.dao.HydrationSettingsDao
import com.vitorkrichter.ordo.core.database.dao.PlanSequenceStateDao
import com.vitorkrichter.ordo.core.database.dao.ScheduleDao
import com.vitorkrichter.ordo.core.database.dao.TrainingPlanDao
import com.vitorkrichter.ordo.core.database.dao.UserProfileDao
import com.vitorkrichter.ordo.core.database.dao.WeightEntryDao
import com.vitorkrichter.ordo.core.database.dao.WorkoutSessionDao
import com.vitorkrichter.ordo.core.database.dao.WorkoutTemplateDao
import com.vitorkrichter.ordo.core.database.entity.ExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.HydrationSettingsEntity
import com.vitorkrichter.ordo.core.database.entity.PlanSequenceStateEntity
import com.vitorkrichter.ordo.core.database.entity.ScheduleRuleEntity
import com.vitorkrichter.ordo.core.database.entity.SessionExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.SessionSetEntity
import com.vitorkrichter.ordo.core.database.entity.SetTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingDayEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingPlanEntity
import com.vitorkrichter.ordo.core.database.entity.UserProfileEntity
import com.vitorkrichter.ordo.core.database.entity.WeightEntryEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutExerciseTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutSessionEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutTemplateEntity

@Database(
    entities = [
        AppMetadataEntity::class,
        ExerciseEntity::class,
        TrainingPlanEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutExerciseTemplateEntity::class,
        SetTemplateEntity::class,
        ScheduleRuleEntity::class,
        TrainingDayEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SessionSetEntity::class,
        UserProfileEntity::class,
        WeightEntryEntity::class,
        HydrationSettingsEntity::class,
        PlanSequenceStateEntity::class,
    ],
    version = OrdoDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(OrdoTypeConverters::class)
abstract class OrdoDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun hydrationSettingsDao(): HydrationSettingsDao
    abstract fun planSequenceStateDao(): PlanSequenceStateDao

    companion object {
        const val VERSION = 6
        const val NAME = "ordo.db"
    }
}
