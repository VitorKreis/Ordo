package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vitorkrichter.ordo.core.database.entity.ScheduleRuleEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingDayEntity
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRule(entity: ScheduleRuleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrainingDay(entity: TrainingDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrainingDay(entity: TrainingDayEntity)

    @Query("DELETE FROM training_days WHERE plan_id = :planId AND date = :date")
    suspend fun deleteTrainingDay(planId: String, date: LocalDate)

    @Query("SELECT * FROM schedule_rules WHERE plan_id = :planId AND is_enabled = 1")
    fun observeEnabledRulesForPlan(planId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE plan_id = :planId")
    fun observeRulesForPlan(planId: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE plan_id = :planId")
    suspend fun rulesForPlan(planId: String): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE plan_id = :planId AND is_enabled = 1")
    suspend fun enabledRulesForPlan(planId: String): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE plan_id = :planId AND weekday = :weekday LIMIT 1")
    suspend fun ruleForPlanAndWeekday(planId: String, weekday: DayOfWeek): ScheduleRuleEntity?

    @Query("UPDATE schedule_rules SET is_enabled = :isEnabled WHERE id = :ruleId")
    suspend fun setRuleEnabled(ruleId: String, isEnabled: Boolean)

    @Query("SELECT * FROM training_days WHERE plan_id = :planId AND date BETWEEN :startDate AND :endDate ORDER BY date")
    fun observeTrainingDays(planId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<TrainingDayEntity>>

    @Query("SELECT * FROM training_days WHERE plan_id = :planId AND date = :date LIMIT 1")
    suspend fun trainingDayForDate(planId: String, date: LocalDate): TrainingDayEntity?

    @Query(
        """
        UPDATE training_days
        SET session_id = :sessionId, status = :status, updated_at = :updatedAt
        WHERE id = :trainingDayId
        """,
    )
    suspend fun updateTrainingDaySession(
        trainingDayId: String,
        sessionId: String,
        status: com.vitorkrichter.ordo.domain.model.TrainingDayStatus,
        updatedAt: java.time.Instant,
    )
}
