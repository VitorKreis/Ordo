package com.vitorkrichter.ordo.data.repository

import androidx.room.withTransaction
import com.vitorkrichter.ordo.core.database.OrdoDatabase
import com.vitorkrichter.ordo.core.time.AppTime
import com.vitorkrichter.ordo.core.database.entity.ExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.PlanSequenceStateEntity
import com.vitorkrichter.ordo.core.database.entity.ScheduleRuleEntity
import com.vitorkrichter.ordo.core.database.entity.SessionExerciseEntity
import com.vitorkrichter.ordo.core.database.entity.SessionSetEntity
import com.vitorkrichter.ordo.core.database.entity.SetTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingPlanEntity
import com.vitorkrichter.ordo.core.database.entity.TrainingDayEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutExerciseTemplateEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutSessionEntity
import com.vitorkrichter.ordo.core.database.entity.WorkoutTemplateEntity
import com.vitorkrichter.ordo.data.importworkout.GymAppImportParser
import com.vitorkrichter.ordo.data.importworkout.GymAppImportResult
import com.vitorkrichter.ordo.data.importworkout.ImportedWorkout
import com.vitorkrichter.ordo.data.mapper.toDomain
import com.vitorkrichter.ordo.data.session.SessionDraftStore
import com.vitorkrichter.ordo.feature.profile.HydrationTargetSource
import com.vitorkrichter.ordo.feature.profile.HydrationUi
import com.vitorkrichter.ordo.feature.profile.ProfileRepository
import com.vitorkrichter.ordo.feature.profile.ProfileSnapshot
import com.vitorkrichter.ordo.feature.profile.WeightEntryUi
import com.vitorkrichter.ordo.domain.model.SessionExerciseStatus
import com.vitorkrichter.ordo.domain.model.SessionSetStatus
import com.vitorkrichter.ordo.domain.model.TrackingMode
import com.vitorkrichter.ordo.domain.model.TrainingDayStatus
import com.vitorkrichter.ordo.domain.model.WorkoutDivisionType
import com.vitorkrichter.ordo.domain.model.WorkoutSelectionMode
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import com.vitorkrichter.ordo.domain.model.WorkoutTemplateId
import com.vitorkrichter.ordo.domain.rule.ScheduleEngine
import com.vitorkrichter.ordo.domain.rule.ScheduleInput
import com.vitorkrichter.ordo.domain.rule.ScheduleResult
import com.vitorkrichter.ordo.domain.rule.SequenceEngine
import com.vitorkrichter.ordo.domain.rule.SessionExecutionRules
import com.vitorkrichter.ordo.domain.rule.HydrationReferencePolicy
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class OrdoRepository @Inject constructor(
    private val database: OrdoDatabase,
    private val sessionDraftStore: SessionDraftStore,
) : ProfileRepository {
    private val scheduleEngine = ScheduleEngine()
    private val importParser = GymAppImportParser()

    fun observeHasActivePlan(): Flow<Boolean> {
        return database.trainingPlanDao().observeActivePlan().map { it != null }
    }

    override fun observeProfile(): Flow<ProfileSnapshot> = database.userProfileDao()
        .observeById()
        .map { profile ->
            ProfileSnapshot(
                displayName = profile?.displayName,
                birthDate = profile?.birthDate,
                heightCm = profile?.heightCm,
            )
        }

    override suspend fun saveProfile(
        displayName: String?,
        birthDate: LocalDate?,
        heightCm: Double?,
    ) = withContext(Dispatchers.IO) {
        require(birthDate == null || !birthDate.isAfter(currentDate())) {
            "Birth date cannot be in the future."
        }
        require(heightCm == null || heightCm in 50.0..300.0) {
            "Height must be between 50 and 300 cm."
        }
        database.withTransaction {
            val now = Instant.now()
            val current = database.userProfileDao().getOrCreateDefault(now)
            database.userProfileDao().upsert(
                current.copy(
                    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
                    birthDate = birthDate,
                    heightCm = heightCm,
                    updatedAt = now,
                ),
            )
        }
    }

    override fun observeWeightEntries(): Flow<List<WeightEntryUi>> = database.weightEntryDao()
        .observeAll()
        .map { entries -> entries.map { WeightEntryUi(it.id, it.measuredAt, it.weightKg) } }

    override suspend fun addWeight(measuredAt: Instant, weightKg: Double) = withContext(Dispatchers.IO) {
        require(weightKg.isFinite() && weightKg > 0.0) { "Weight must be positive." }
        require(!measuredAt.isAfter(Instant.now())) { "Weight measurement cannot be in the future." }
        val now = Instant.now()
        database.weightEntryDao().insert(
            com.vitorkrichter.ordo.core.database.entity.WeightEntryEntity(
                id = UUID.randomUUID().toString(),
                measuredAt = measuredAt,
                weightKg = weightKg,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun updateWeight(id: String, measuredAt: Instant, weightKg: Double) = withContext(Dispatchers.IO) {
        require(weightKg.isFinite() && weightKg > 0.0) { "Weight must be positive." }
        require(!measuredAt.isAfter(Instant.now())) { "Weight measurement cannot be in the future." }
        val current = database.weightEntryDao().findById(id) ?: error("Weight entry not found")
        database.weightEntryDao().update(current.copy(measuredAt = measuredAt, weightKg = weightKg, updatedAt = Instant.now()))
    }

    override suspend fun deleteWeight(id: String) = withContext(Dispatchers.IO) {
        database.weightEntryDao().deleteById(id)
    }

    override fun observeHydration(): Flow<HydrationUi> = combine(
        database.hydrationSettingsDao().observeById(),
        database.weightEntryDao().observeAll(),
    ) { settings, entries ->
        val latestWeight = entries.asSequence()
            .filter { it.weightKg.isFinite() && it.weightKg > 0.0 }
            .maxByOrNull { it.measuredAt }
            ?.weightKg
        if (settings?.targetMode == com.vitorkrichter.ordo.core.database.entity.HydrationSettingsEntity.CUSTOM_TARGET_MODE) {
            HydrationUi(settings.targetMl, HydrationTargetSource.CUSTOM)
        } else {
            val reference = HydrationReferencePolicy.automaticReference(latestWeight)
            HydrationUi(
                targetMl = reference.millilitersPerDay,
                source = when (reference.source) {
                    com.vitorkrichter.ordo.domain.rule.HydrationReferenceSource.AUTOMATIC_WEIGHT -> HydrationTargetSource.AUTOMATIC_WEIGHT
                    com.vitorkrichter.ordo.domain.rule.HydrationReferenceSource.DEFAULT_REFERENCE -> HydrationTargetSource.DEFAULT_REFERENCE
                    com.vitorkrichter.ordo.domain.rule.HydrationReferenceSource.CUSTOM -> HydrationTargetSource.CUSTOM
                },
            )
        }
    }

    override suspend fun setHydrationTarget(targetMl: Int) = withContext(Dispatchers.IO) {
        require(targetMl in 100..20_000) { "Hydration target is out of range." }
        database.hydrationSettingsDao().setManualTarget(targetMl, Instant.now())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDashboard(today: LocalDate? = null): Flow<DashboardSnapshot> {
        return database.trainingPlanDao().observeActivePlan().flatMapLatest { activePlan ->
            if (activePlan == null) {
                flowOf(DashboardSnapshot.Empty)
            } else {
                combine(
                    database.workoutTemplateDao().observeActiveWorkoutsForPlan(activePlan.id),
                    database.scheduleDao().observeRulesForPlan(activePlan.id),
                    database.workoutSessionDao().observeSessionsForPlan(activePlan.id),
                    database.planSequenceStateDao().observeForPlan(activePlan.id),
                ) { workoutEntities, ruleEntities, sessionEntities, sequenceState ->
                    val plan = activePlan.toDomain()
                    val workouts = workoutEntities.map { it.toDomain() }
                    val rules = ruleEntities.filter { it.isEnabled }.map { it.toDomain() }
                    val sessions = sessionEntities.map { it.toDomain() }
                    val workoutCodeById = workoutEntities.associate { it.id to it.code }
                    val sessionsByDate = sessionEntities.groupBy {
                        it.startedAt.atZone(AppZoneId).toLocalDate()
                    }
                    val currentDate = today ?: currentDate()
                    val scheduleResult = if (activePlan.workoutSelectionMode == WorkoutSelectionMode.FIXED_BY_WEEKDAY) {
                        scheduleEngine.workoutForDate(
                            input = ScheduleInput(
                                activePlan = plan,
                                workouts = workouts,
                                scheduleRules = rules,
                                sessions = sessions,
                            ),
                            date = currentDate,
                        )
                    } else {
                        null
                    }
                    val nextWorkout = workouts.takeIf { it.isNotEmpty() }?.let {
                        if (activePlan.workoutSelectionMode == WorkoutSelectionMode.SEQUENTIAL) {
                            nextSequentialDashboardWorkout(
                                workouts = workoutEntities,
                                sessions = sessionEntities,
                                sequenceState = sequenceState,
                            )
                        } else {
                            nextDashboardWorkout(
                                plan = activePlan,
                                workouts = workoutEntities,
                                rules = ruleEntities,
                                sessions = sessionEntities,
                                today = currentDate,
                            )
                        }
                    }
                    val todayWorkout = (scheduleResult as? ScheduleResult.PlannedWorkout)?.workout
                    DashboardSnapshot.Ready(
                        planName = activePlan.name,
                        isTodayPlanned = if (activePlan.workoutSelectionMode == WorkoutSelectionMode.SEQUENTIAL) {
                            nextWorkout != null
                        } else {
                            todayWorkout != null
                        },
                        todayWorkoutCode = if (activePlan.workoutSelectionMode == WorkoutSelectionMode.SEQUENTIAL) {
                            nextWorkout?.code
                        } else {
                            todayWorkout?.code
                        },
                        todayWorkoutName = if (activePlan.workoutSelectionMode == WorkoutSelectionMode.SEQUENTIAL) {
                            nextWorkout?.name
                        } else {
                            todayWorkout?.name
                        },
                        nextWorkoutId = nextWorkout?.id?.value,
                        nextWorkoutCode = nextWorkout?.code,
                        nextWorkoutName = nextWorkout?.name,
                        nextWorkoutPosition = nextWorkout?.position?.plus(1),
                        workoutCount = workouts.size,
                        completedSessionCount = sessionEntities.count { it.status == WorkoutSessionStatus.COMPLETED },
                        partialSessionCount = sessionEntities.count { it.status == WorkoutSessionStatus.PARTIALLY_COMPLETED },
                        lastFiveDays = (0L..4L).map { offset ->
                            val date = currentDate.minusDays(offset)
                            val daySessions = sessionsByDate[date].orEmpty()
                            val session = daySessions.firstOrNull {
                                it.status == WorkoutSessionStatus.COMPLETED ||
                                    it.status == WorkoutSessionStatus.PARTIALLY_COMPLETED
                            } ?: daySessions.firstOrNull()
                            HomeHistoryDayUi(
                                date = date,
                                workoutCode = session?.sourceWorkoutTemplateId?.let(workoutCodeById::get),
                                workoutName = session?.workoutNameSnapshot,
                                status = when (session?.status) {
                                    WorkoutSessionStatus.COMPLETED -> HomeHistoryStatusUi.COMPLETED
                                    WorkoutSessionStatus.PARTIALLY_COMPLETED -> HomeHistoryStatusUi.PARTIAL
                                    WorkoutSessionStatus.CANCELED -> HomeHistoryStatusUi.CANCELED
                                    WorkoutSessionStatus.IN_PROGRESS -> HomeHistoryStatusUi.IN_PROGRESS
                                    null -> HomeHistoryStatusUi.NO_RECORD
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    suspend fun parseImportText(text: String): GymAppImportResult = withContext(Dispatchers.Default) {
        importParser.parse(text)
    }

    suspend fun exportActivePlanText(): String = withContext(Dispatchers.IO) {
        val plan = database.trainingPlanDao().activePlan() ?: error("No active plan")
        val days = database.scheduleDao().enabledRulesForPlan(plan.id).map { it.weekday }.toSet()
        val workouts = database.workoutTemplateDao().activeWorkoutsForPlan(plan.id)
        buildString {
            appendLine("# GYMAPP_IMPORT_V1")
            appendLine()
            appendLine("NOME_PLANO=${plan.name}")
            appendLine("DIAS_TREINO=${days.sortedBy { it.value }.joinToString(",") { it.portugueseName() }}")
            appendLine()
            workouts.forEach { workout ->
                appendLine("[TREINO ${workout.code}]")
                appendLine("NOME=${workout.name}")
                appendLine()
                workoutExercises(workout.id).forEach { exercise ->
                    appendLine("EXERCICIO=${exercise.name}")
                    appendLine("SERIES=${exercise.seriesCount}")
                    appendLine("REPETICOES=${exercise.repetitions}")
                    appendLine("DESCANSO_SERIES=${exercise.restBetweenSetsSeconds}s")
                    appendLine("DESCANSO_PROXIMO=${exercise.transitionRestSeconds}s")
                    appendLine("ACOMPANHAMENTO=${exercise.trackingMode.importName()}")
                    exercise.notes?.let { appendLine("OBSERVACAO=$it") }
                    appendLine("-------------------------------")
                    appendLine()
                }
            }
        }
    }

    suspend fun importGymAppTextAsActivePlan(
        text: String,
        planName: String,
        enabledWeekdays: Set<DayOfWeek>? = null,
    ): GymAppImportResult = withContext(Dispatchers.IO) {
        val result = importParser.parse(text)
        if (!result.canSave) return@withContext result
        database.withTransaction {
            val now = Instant.now()
            val planId = UUID.randomUUID().toString()
            check(database.workoutSessionDao().activeSession() == null) {
                "Finish the active workout before importing another plan."
            }
            val activeWeekdays = result.trainingWeekdays
                ?: enabledWeekdays
            database.trainingPlanDao().deactivateActivePlans(now)
            database.trainingPlanDao().insert(
                TrainingPlanEntity(
                    id = planId,
                    name = planName.trim().ifBlank { result.planName ?: "Plano importado" },
                    workoutDivisionType = divisionFor(result.workouts.size),
                    workoutSelectionMode = WorkoutSelectionMode.SEQUENTIAL,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now,
                    archivedAt = null,
                ),
            )

            result.workouts.forEachIndexed { index, workout ->
                insertImportedWorkout(planId = planId, workout = workout, position = index, now = now)
            }

            activeWeekdays?.takeIf { it.isNotEmpty() }?.let { weekdays ->
                DayOfWeek.entries.forEach { weekday ->
                    database.scheduleDao().insertRule(
                        ScheduleRuleEntity(
                            id = UUID.randomUUID().toString(),
                            planId = planId,
                            weekday = weekday,
                            workoutTemplateId = null,
                            isEnabled = weekday in weekdays,
                        ),
                    )
                }
            }
        }

        sessionDraftStore.clear()

        result
    }

    fun observeAvailablePlans(): Flow<List<AvailablePlanUi>> {
        return database.trainingPlanDao().observeAvailablePlans().map { plans ->
            plans.map { plan ->
                val workouts = database.workoutTemplateDao().activeWorkoutsForPlan(plan.id)
                AvailablePlanUi(
                    id = plan.id,
                    name = plan.name,
                    isActive = plan.isActive,
                    workoutCount = workouts.size,
                    workoutLabels = workouts.map { "${it.code}: ${it.name}" },
                    trainingWeekdays = database.scheduleDao().enabledRulesForPlan(plan.id)
                        .map { it.weekday }
                        .toSet(),
                )
            }
        }
    }

    suspend fun activatePlan(planId: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            check(database.workoutSessionDao().activeSession() == null) {
                "Finish the active workout before changing plans."
            }
            val now = Instant.now()
            database.trainingPlanDao().deactivateActivePlans(now)
            database.trainingPlanDao().activatePlan(planId, now)
        }
        sessionDraftStore.clear()
    }

    suspend fun renamePlan(planId: String, name: String) = withContext(Dispatchers.IO) {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank())
        database.trainingPlanDao().renamePlan(planId, normalizedName, Instant.now())
    }

    suspend fun archivePlan(planId: String) = withContext(Dispatchers.IO) {
        val plan = database.trainingPlanDao().planById(planId) ?: return@withContext
        require(!plan.isActive) { "The active plan cannot be archived" }
        database.trainingPlanDao().archiveInactivePlan(planId, Instant.now())
    }

    suspend fun duplicatePlan(planId: String) = withContext(Dispatchers.IO) {
        val sourcePlan = database.trainingPlanDao().planById(planId) ?: return@withContext
        database.withTransaction {
            val now = Instant.now()
            val newPlanId = UUID.randomUUID().toString()
            database.trainingPlanDao().insert(
                sourcePlan.copy(
                    id = newPlanId,
                    name = "${sourcePlan.name} (copia)",
                    isActive = false,
                    createdAt = now,
                    updatedAt = now,
                    archivedAt = null,
                ),
            )
            val sourceRules = database.scheduleDao().rulesForPlan(planId)
            val sourceWorkouts = database.workoutTemplateDao().activeWorkoutsForPlan(planId)
            val workoutIdMap = mutableMapOf<String, String>()
            sourceWorkouts.forEach { workout ->
                val newWorkoutId = UUID.randomUUID().toString()
                workoutIdMap[workout.id] = newWorkoutId
                database.workoutTemplateDao().insertWorkout(
                    workout.copy(id = newWorkoutId, planId = newPlanId, createdAt = now, updatedAt = now),
                )
                workoutExercises(workout.id).forEachIndexed { position, exercise ->
                    val newExerciseId = UUID.randomUUID().toString()
                    val newWorkoutExerciseId = UUID.randomUUID().toString()
                    database.exerciseDao().insert(
                        ExerciseEntity(
                            id = newExerciseId,
                            name = exercise.name,
                            normalizedName = exercise.name.lowercase(),
                            category = null,
                            isArchived = false,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    database.workoutTemplateDao().insertWorkoutExercise(
                        WorkoutExerciseTemplateEntity(
                            id = newWorkoutExerciseId,
                            workoutTemplateId = newWorkoutId,
                            exerciseId = newExerciseId,
                            position = position,
                            trackingMode = exercise.trackingMode,
                            restBetweenSetsSeconds = exercise.restBetweenSetsSeconds,
                            transitionRestSeconds = exercise.transitionRestSeconds,
                            notes = exercise.notes,
                        ),
                    )
                    exercise.sets.forEach { set ->
                        database.workoutTemplateDao().insertSet(
                            SetTemplateEntity(
                                id = UUID.randomUUID().toString(),
                                workoutExerciseTemplateId = newWorkoutExerciseId,
                                position = set.position,
                                minimumRepetitions = set.minimumRepetitions,
                                maximumRepetitions = set.maximumRepetitions,
                                targetRepetitions = set.targetRepetitions,
                                suggestedLoadKg = set.suggestedLoadKg,
                            ),
                        )
                    }
                }
            }
            sourceRules.forEach { rule ->
                database.scheduleDao().insertRule(
                    ScheduleRuleEntity(
                        id = UUID.randomUUID().toString(),
                        planId = newPlanId,
                        weekday = rule.weekday,
                        workoutTemplateId = rule.workoutTemplateId?.let(workoutIdMap::get),
                        isEnabled = rule.isEnabled,
                    ),
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWorkouts(): Flow<WorkoutsSnapshot> {
        return database.trainingPlanDao().observeActivePlan().flatMapLatest { activePlan ->
            if (activePlan == null) {
                flowOf(WorkoutsSnapshot.Empty)
            } else {
                combine(
                    database.workoutTemplateDao().observeActiveWorkoutsForPlan(activePlan.id),
                    database.workoutTemplateDao().observeExerciseChangesForPlan(activePlan.id),
                ) { workouts, _ -> workouts }.map { workouts ->
                    val workoutCards = workouts.map { workout ->
                        val exercises = database.workoutTemplateDao()
                            .exerciseSummariesForWorkout(workout.id)
                            .map { summary ->
                                WorkoutExerciseUi(
                                    id = summary.workoutExerciseTemplateId,
                                    name = summary.exerciseName,
                                    seriesCount = summary.seriesCount,
                                    repetitions = summary.repetitionLabel(),
                                    restBetweenSetsSeconds = summary.restBetweenSetsSeconds,
                                    transitionRestSeconds = summary.transitionRestSeconds,
                                    trackingMode = summary.trackingMode,
                                )
                            }
                        WorkoutUi(
                            id = workout.id,
                            code = workout.code,
                            name = workout.name,
                            exerciseCount = exercises.size,
                            exercises = exercises,
                        )
                    }
                    WorkoutsSnapshot.Ready(
                        planName = activePlan.name,
                        workouts = workoutCards,
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHistory(): Flow<HistorySnapshot> {
        return database.trainingPlanDao().observeActivePlan().flatMapLatest { activePlan ->
            if (activePlan == null) {
                flowOf(HistorySnapshot.Empty)
            } else {
                combine(
                    database.workoutTemplateDao().observeActiveWorkoutsForPlan(activePlan.id),
                    database.workoutSessionDao().observeSessionsWithDetailsForPlan(activePlan.id),
                ) { workouts, sessionDetails ->
                    val workoutCodeById = workouts.associate { it.id to it.code }
                    HistorySnapshot.Ready(
                        planName = activePlan.name,
                        sessions = sessionDetails.map { detail ->
                            val session = detail.session
                            HistorySessionUi(
                                workoutCode = session.sourceWorkoutTemplateId?.let(workoutCodeById::get),
                                workoutName = session.workoutNameSnapshot,
                                status = session.status,
                                startedAt = session.startedAt,
                                finishedAt = session.finishedAt,
                                exercises = detail.exercises.sortedBy { it.exercise.position }.map { exerciseDetail ->
                                    val exercise = exerciseDetail.exercise
                                    HistoryExerciseUi(
                                        sourceExerciseId = exercise.sourceExerciseId,
                                        name = exercise.exerciseNameSnapshot,
                                        position = exercise.position,
                                        sets = exerciseDetail.sets.sortedBy { it.position }.map { set ->
                                            HistorySetUi(
                                                id = set.id,
                                                position = set.position,
                                                plannedMinimumRepetitions = set.plannedMinimumRepetitions,
                                                plannedMaximumRepetitions = set.plannedMaximumRepetitions,
                                                actualRepetitions = set.actualRepetitions,
                                                actualLoadKg = set.actualLoadKg,
                                                status = set.status,
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    fun observeProgress(): Flow<ProgressSnapshot> {
        return observeHistory().map { snapshot ->
            when (snapshot) {
                HistorySnapshot.Empty -> ProgressSnapshot.Empty
                is HistorySnapshot.Ready -> {
                    val exercises = snapshot.sessions
                        .filter {
                            it.status == WorkoutSessionStatus.COMPLETED ||
                                it.status == WorkoutSessionStatus.PARTIALLY_COMPLETED
                        }
                        .flatMap { session ->
                            session.exercises.map { exercise -> session to exercise }
                        }
                        .groupBy { (_, exercise) ->
                            exercise.sourceExerciseId?.let { "id:$it" }
                                ?: "name:${exercise.name.trim().lowercase()}"
                        }
                        .map { (_, entries) ->
                            val records = entries.mapNotNull { (session, exercise) ->
                                val sets = exercise.sets.filter {
                                    it.status == SessionSetStatus.COMPLETED &&
                                        (it.actualRepetitions != null || it.actualLoadKg != null)
                                }
                                if (sets.isEmpty()) null else ProgressRecordUi(
                                    date = session.startedAt,
                                    workoutName = session.workoutName,
                                    sets = sets,
                                )
                            }.sortedByDescending { it.date }
                            val allSets = records.flatMap { it.sets }
                            ProgressExerciseUi(
                                sourceExerciseId = entries.first().second.sourceExerciseId,
                                name = entries.first().second.name,
                                sessionCount = records.size,
                                maxLoadKg = allSets.mapNotNull { it.actualLoadKg }.maxOrNull(),
                                maxRepetitions = allSets.mapNotNull { it.actualRepetitions }.maxOrNull(),
                                totalVolumeKg = allSets.sumOf {
                                    (it.actualLoadKg ?: 0.0) * (it.actualRepetitions ?: 0)
                                },
                                records = records.take(20),
                            )
                        }
                        .filter { it.records.isNotEmpty() }
                        .sortedBy { it.name.lowercase() }
                    ProgressSnapshot.Ready(snapshot.planName, exercises)
                }
            }
        }
    }

    suspend fun updateHistoricalSetPerformance(
        setId: String,
        repetitions: Int?,
        loadKg: Double?,
    ) = withContext(Dispatchers.IO) {
        require(repetitions == null || repetitions > 0)
        require(loadKg == null || loadKg >= 0.0)
        database.workoutSessionDao().updateSetPerformance(setId, repetitions, loadKg)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCalendar(
        startDate: LocalDate? = null,
        days: Long = 14,
    ): Flow<CalendarSnapshot> {
        return database.trainingPlanDao().observeActivePlan().flatMapLatest { activePlan ->
            if (activePlan == null) {
                flowOf(CalendarSnapshot.Empty)
            } else {
                val rangeStart = startDate ?: currentDate()
                val rangeEnd = rangeStart.plusDays((days - 1).coerceAtLeast(0))
                combine(
                    database.scheduleDao().observeRulesForPlan(activePlan.id),
                    database.workoutTemplateDao().observeActiveWorkoutsForPlan(activePlan.id),
                    database.workoutSessionDao().observeSessionsForPlan(activePlan.id),
                    database.scheduleDao().observeTrainingDays(activePlan.id, rangeStart, rangeEnd),
                ) { rules, workoutEntities, sessions, trainingDays ->
                    val enabledRules = rules.filter { it.isEnabled }.map { it.toDomain() }
                    val workouts = workoutEntities.sortedBy { it.position }
                    val workoutIds = workouts.map { it.id }.toSet()
                    val domainWorkouts = workouts.map { it.toDomain() }
                    val domainSessions = sessions.map { it.toDomain() }
                    val domainTrainingDays = trainingDays.map { it.toDomain() }
                    val sessionsByDate = sessions.groupBy {
                        it.startedAt.atZone(AppZoneId).toLocalDate()
                    }
                    CalendarSnapshot.Ready(
                        planName = activePlan.name,
                        scheduleDays = DayOfWeek.entries.map { weekday ->
                            ScheduleDayUi(
                                weekday = weekday,
                                isEnabled = enabledRules.any { it.weekday == weekday },
                            )
                        },
                        days = (0 until days).map { offset ->
                            val date = (startDate ?: currentDate()).plusDays(offset)
                            val dateSessions = sessionsByDate[date].orEmpty()
                            val isSkipped = domainTrainingDays.any {
                                it.date == date && it.status == TrainingDayStatus.RESCHEDULED
                            }
                            val sessionsBeforeDate = domainSessions.filter {
                                it.startedAt.atZone(AppZoneId).toLocalDate().isBefore(date)
                            }
                            val scheduleResult = scheduleEngine.workoutForDate(
                                input = ScheduleInput(
                                    activePlan = activePlan.toDomain(),
                                    workouts = domainWorkouts,
                                    scheduleRules = enabledRules,
                                    sessions = sessionsBeforeDate,
                                    trainingDays = domainTrainingDays,
                                ),
                                date = date,
                            )
                            val scheduledWorkout = (scheduleResult as? ScheduleResult.PlannedWorkout)?.workout
                            val isPlannedDay = scheduledWorkout != null && !isSkipped
                            val sessionWorkout = dateSessions.firstOrNull { it.sourceWorkoutTemplateId != null }
                            CalendarDayUi(
                                date = date,
                                isToday = date == currentDate(),
                                isSkippedOverride = isSkipped,
                                workoutCode = sessionWorkout?.sourceWorkoutTemplateId
                                    ?.let { workoutId -> workouts.firstOrNull { it.id == workoutId }?.code }
                                    ?: scheduledWorkout?.code,
                                workoutName = sessionWorkout?.workoutNameSnapshot ?: scheduledWorkout?.name,
                                status = when {
                                    dateSessions.any { it.status == WorkoutSessionStatus.IN_PROGRESS } -> CalendarDayStatusUi.IN_PROGRESS
                                    dateSessions.any { it.status == WorkoutSessionStatus.COMPLETED } -> CalendarDayStatusUi.COMPLETED
                                    dateSessions.any { it.status == WorkoutSessionStatus.PARTIALLY_COMPLETED } -> CalendarDayStatusUi.PARTIAL
                                    dateSessions.any { it.status == WorkoutSessionStatus.CANCELED } -> CalendarDayStatusUi.CANCELED
                                    isPlannedDay -> CalendarDayStatusUi.PLANNED
                                    else -> CalendarDayStatusUi.NO_WORKOUT
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    suspend fun setCalendarDateSkipped(date: LocalDate, skipped: Boolean) = withContext(Dispatchers.IO) {
        val activePlan = database.trainingPlanDao().activePlan() ?: return@withContext
        if (!skipped) {
            database.scheduleDao().deleteTrainingDay(activePlan.id, date)
        } else {
            val now = Instant.now()
            database.scheduleDao().upsertTrainingDay(
                TrainingDayEntity(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    planId = activePlan.id,
                    workoutTemplateId = null,
                    status = TrainingDayStatus.RESCHEDULED,
                    sessionId = null,
                    rescheduledFromId = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun createSamplePlanIfNeeded(
        enabledWeekdays: Set<DayOfWeek>? = null,
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (database.trainingPlanDao().activePlan() != null) {
                return@withTransaction
            }

            val now = Instant.now()
            val plan = TrainingPlanEntity(
                id = SAMPLE_PLAN_ID,
                name = "Plano ABC de exemplo",
                workoutDivisionType = WorkoutDivisionType.ABC,
                workoutSelectionMode = WorkoutSelectionMode.SEQUENTIAL,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                archivedAt = null,
            )
            database.trainingPlanDao().insert(plan)

            SAMPLE_WORKOUTS.forEachIndexed { index, workout ->
                database.workoutTemplateDao().insertWorkout(
                    WorkoutTemplateEntity(
                        id = workout.id,
                        planId = SAMPLE_PLAN_ID,
                        code = workout.code,
                        name = workout.name,
                        position = index,
                        notes = null,
                        isArchived = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            SAMPLE_WORKOUTS.flatMap { it.exercises }.distinctBy { it.id }.forEach { exercise ->
                database.exerciseDao().insert(
                    ExerciseEntity(
                        id = exercise.id,
                        name = exercise.name,
                        normalizedName = exercise.name.lowercase(),
                        category = null,
                        isArchived = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }

            SAMPLE_WORKOUTS.forEach { workout ->
                workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                    val workoutExerciseId = "${workout.id}-exercise-${exerciseIndex + 1}"
                    database.workoutTemplateDao().insertWorkoutExercise(
                        WorkoutExerciseTemplateEntity(
                            id = workoutExerciseId,
                            workoutTemplateId = workout.id,
                            exerciseId = exercise.id,
                            position = exerciseIndex,
                            trackingMode = exercise.trackingMode,
                            restBetweenSetsSeconds = exercise.restSeconds,
                            transitionRestSeconds = exercise.transitionSeconds,
                            notes = null,
                        ),
                    )
                    repeat(exercise.series) { setIndex ->
                        database.workoutTemplateDao().insertSet(
                            SetTemplateEntity(
                                id = "$workoutExerciseId-set-${setIndex + 1}",
                                workoutExerciseTemplateId = workoutExerciseId,
                                position = setIndex,
                                minimumRepetitions = exercise.minReps,
                                maximumRepetitions = exercise.maxReps,
                                targetRepetitions = null,
                                suggestedLoadKg = null,
                            ),
                        )
                    }
                }
            }

            enabledWeekdays?.takeIf { it.isNotEmpty() }?.let { weekdays ->
                DayOfWeek.entries.forEach { weekday ->
                    database.scheduleDao().insertRule(
                        ScheduleRuleEntity(
                            id = "sample-rule-${weekday.name.lowercase()}",
                            planId = SAMPLE_PLAN_ID,
                            weekday = weekday,
                            workoutTemplateId = null,
                            isEnabled = weekday in weekdays,
                        ),
                    )
                }
            }
        }
    }

    suspend fun setTrainingDayEnabled(weekday: DayOfWeek, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val activePlan = database.trainingPlanDao().activePlan() ?: return@withTransaction
            val existing = database.scheduleDao().ruleForPlanAndWeekday(activePlan.id, weekday)
            if (existing == null) {
                database.scheduleDao().insertRule(
                    ScheduleRuleEntity(
                        id = UUID.randomUUID().toString(),
                        planId = activePlan.id,
                        weekday = weekday,
                        workoutTemplateId = null,
                        isEnabled = isEnabled,
                    ),
                )
            } else {
                database.scheduleDao().setRuleEnabled(existing.id, isEnabled)
            }
        }
    }

    suspend fun moveWorkout(workoutId: String, direction: MoveWorkoutDirection) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val activePlan = database.trainingPlanDao().activePlan() ?: return@withTransaction
            val workouts = database.workoutTemplateDao().activeWorkoutsForPlan(activePlan.id)
            val currentIndex = workouts.indexOfFirst { it.id == workoutId }
            if (currentIndex < 0) return@withTransaction
            val targetIndex = when (direction) {
                MoveWorkoutDirection.UP -> currentIndex - 1
                MoveWorkoutDirection.DOWN -> currentIndex + 1
            }
            if (targetIndex !in workouts.indices) return@withTransaction

            val now = Instant.now()
            val current = workouts[currentIndex]
            val target = workouts[targetIndex]
            database.workoutTemplateDao().updateWorkoutPosition(
                workoutTemplateId = current.id,
                position = target.position,
                updatedAt = now,
            )
            database.workoutTemplateDao().updateWorkoutPosition(
                workoutTemplateId = target.id,
                position = current.position,
                updatedAt = now,
            )
        }
    }

    suspend fun addWorkout(name: String) = withContext(Dispatchers.IO) {
        val safeName = name.trim()
        require(safeName.isNotBlank())
        val plan = database.trainingPlanDao().activePlan() ?: return@withContext
        val workouts = database.workoutTemplateDao().activeWorkoutsForPlan(plan.id)
        val now = Instant.now()
        val usedCodes = workouts.map { it.code.trim().uppercase() }.toSet()
        val nextCode = (0..25)
            .map { ('A'.code + it).toChar().toString() }
            .firstOrNull { it !in usedCodes }
            ?: generateSequence(workouts.size + 1) { it + 1 }
                .map(Int::toString)
                .first { it !in usedCodes }
        database.workoutTemplateDao().insertWorkout(
            WorkoutTemplateEntity(
                id = UUID.randomUUID().toString(),
                planId = plan.id,
                code = nextCode,
                name = safeName,
                position = workouts.size,
                notes = null,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun archiveWorkout(workoutId: String) = withContext(Dispatchers.IO) {
        val plan = database.trainingPlanDao().activePlan() ?: return@withContext
        val workouts = database.workoutTemplateDao().activeWorkoutsForPlan(plan.id)
        require(workouts.size > 1) { "A plan must keep at least one workout" }
        database.workoutTemplateDao().archiveWorkout(workoutId, Instant.now())
        database.workoutTemplateDao().activeWorkoutsForPlan(plan.id).forEachIndexed { index, workout ->
            database.workoutTemplateDao().updateWorkoutPosition(workout.id, index, Instant.now())
        }
    }

    suspend fun currentWorkoutDetail(): WorkoutDetailUi? = startWorkoutDetail(
        kind = SequenceEngine.SelectionKind.NORMAL,
    )

    /** Starts an explicit repeat without consuming the normal sequence cursor. */
    suspend fun startRepeatWorkout(workoutId: String): WorkoutDetailUi? = startWorkoutDetail(
        kind = SequenceEngine.SelectionKind.REPEAT,
        workoutId = workoutId,
    )

    /** Starts an explicit extra, or the current normal workout when no ID is supplied. */
    suspend fun startExtraWorkout(workoutId: String? = null): WorkoutDetailUi? = startWorkoutDetail(
        kind = SequenceEngine.SelectionKind.EXTRA,
        workoutId = workoutId,
    )

    private suspend fun startWorkoutDetail(
        kind: SequenceEngine.SelectionKind,
        workoutId: String? = null,
    ): WorkoutDetailUi? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existingSession = database.workoutSessionDao().activeSession()
            if (existingSession != null) {
                check(kind == SequenceEngine.SelectionKind.NORMAL) {
                    "Finish the active workout before starting a repeat or extra."
                }
                return@withTransaction detailForSession(existingSession)
            }

            val activePlan = database.trainingPlanDao().activePlan() ?: return@withTransaction null
            val workouts = database.workoutTemplateDao().activeWorkoutsForPlan(activePlan.id)
            if (workouts.isEmpty()) return@withTransaction null

            val now = Instant.now()
            val sessions = database.workoutSessionDao().sessionsForPlan(activePlan.id)
            val selection = selectWorkoutForStart(
                activePlan = activePlan,
                workouts = workouts,
                sessions = sessions,
                kind = kind,
                workoutId = workoutId,
                now = now,
            ) ?: return@withTransaction null
            val workout = selection.workout
            val workoutExercises = workoutExercises(workout.id)
            if (workoutExercises.isEmpty()) return@withTransaction null

            val sessionId = UUID.randomUUID().toString()
            val trainingDay = if (
                kind == SequenceEngine.SelectionKind.NORMAL &&
                activePlan.workoutSelectionMode == WorkoutSelectionMode.FIXED_BY_WEEKDAY
            ) {
                database.scheduleDao().trainingDayForDate(
                    planId = activePlan.id,
                    date = currentDate(),
                )
            } else {
                null
            }
            val sessionExercises = workoutExercises.mapIndexed { index, exercise ->
                SessionExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    sourceExerciseId = exercise.sourceExerciseId,
                    exerciseNameSnapshot = exercise.name,
                    position = index,
                    trackingModeSnapshot = exercise.trackingMode,
                    restBetweenSetsSecondsSnapshot = exercise.restBetweenSetsSeconds,
                    transitionRestSecondsSnapshot = exercise.transitionRestSeconds,
                    notesSnapshot = exercise.notes,
                    status = SessionExerciseStatus.NOT_STARTED,
                )
            }
            val sessionExerciseIds = workoutExercises.zip(sessionExercises)
                .associate { (template, snapshot) -> template.id to snapshot.id }
            val sessionSets = workoutExercises.flatMap { exercise ->
                exercise.sets.map { set ->
                    SessionSetEntity(
                        id = UUID.randomUUID().toString(),
                        sessionExerciseId = sessionExerciseIds.getValue(exercise.id),
                        position = set.position,
                        plannedMinimumRepetitions = set.minimumRepetitions,
                        plannedMaximumRepetitions = set.maximumRepetitions,
                        plannedTargetRepetitions = set.targetRepetitions,
                        suggestedLoadKg = set.suggestedLoadKg,
                        actualRepetitions = null,
                        actualLoadKg = null,
                        status = SessionSetStatus.NOT_STARTED,
                    )
                }
            }
            val session = WorkoutSessionEntity(
                id = sessionId,
                planId = activePlan.id,
                sourceWorkoutTemplateId = workout.id,
                trainingDayId = trainingDay?.id,
                workoutNameSnapshot = workout.name,
                status = WorkoutSessionStatus.IN_PROGRESS,
                startedAt = now,
                finishedAt = null,
                createdAt = now,
                updatedAt = now,
                sessionIntent = selection.intent,
                sessionOrigin = selection.origin,
                advancesSequence = selection.advancesSequence,
                sequenceAdvanceId = sessionId.takeIf { selection.advancesSequence },
            )
            database.workoutSessionDao().insertSessionSnapshot(
                session = session,
                exercises = sessionExercises,
                sets = sessionSets,
            )
            trainingDay?.let {
                database.scheduleDao().updateTrainingDaySession(
                    trainingDayId = it.id,
                    sessionId = session.id,
                    status = TrainingDayStatus.IN_PROGRESS,
                    updatedAt = now,
                )
            }
            detailForSession(session)
        }
    }

    suspend fun finishCurrentWorkout(
        completedSetKeys: Set<String>,
        performances: Map<String, SetPerformance> = emptyMap(),
        orderedExerciseIds: List<String> = emptyList(),
        startedAt: Instant = Instant.now(),
        finishedAt: Instant = Instant.now(),
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val session = database.workoutSessionDao().activeSession() ?: return@withTransaction
            val sessionExercises = database.workoutSessionDao().exercisesForSession(session.id)
            require(sessionExercises.isNotEmpty()) { "Active session has no exercise snapshot" }
            val sessionSetsByExercise = sessionExercises.associateWith {
                database.workoutSessionDao().setsForSessionExercise(it.id)
            }
            val validSetKeys = sessionSetsByExercise.values.flatten().map { set ->
                val exercise = sessionExercises.first { it.id == set.sessionExerciseId }
                "${exercise.id}:${set.id}"
            }.toSet()
            require(completedSetKeys.all { it in validSetKeys }) { "Unknown set in workout finalization" }

            val now = Instant.now()
            var completedSetCount = 0
            sessionExercises.forEach { exercise ->
                val sets = sessionSetsByExercise.getValue(exercise)
                var completedExerciseSetCount = 0
                sets.forEach { set ->
                    val setKey = "${exercise.id}:${set.id}"
                    val performance = performances[setKey]
                    val repetitions = performance?.actualRepetitions ?: set.actualRepetitions
                    val loadKg = performance?.actualLoadKg ?: set.actualLoadKg
                    val isCompleted = setKey in completedSetKeys || set.status == SessionSetStatus.COMPLETED
                    if (isCompleted) {
                        require(isPerformanceValid(exercise.trackingModeSnapshot, repetitions, loadKg)) {
                            "Missing or invalid performance for completed set"
                        }
                        completedSetCount += 1
                        completedExerciseSetCount += 1
                    }
                    database.workoutSessionDao().updateActiveSet(
                        setId = set.id,
                        repetitions = repetitions.takeIf { isCompleted },
                        loadKg = loadKg.takeIf { isCompleted },
                        status = if (isCompleted) SessionSetStatus.COMPLETED else SessionSetStatus.SKIPPED,
                    )
                }
                database.workoutSessionDao().updateActiveExerciseStatus(
                    sessionExerciseId = exercise.id,
                    status = when {
                        completedExerciseSetCount == sets.size -> SessionExerciseStatus.COMPLETED
                        completedExerciseSetCount > 0 -> SessionExerciseStatus.IN_PROGRESS
                        else -> SessionExerciseStatus.SKIPPED
                    },
                )
            }

            val status = if (completedSetCount == validSetKeys.size) {
                WorkoutSessionStatus.COMPLETED
            } else {
                WorkoutSessionStatus.PARTIALLY_COMPLETED
            }
            val didFinish = database.workoutSessionDao().finishActiveSession(
                sessionId = session.id,
                status = status,
                finishedAt = finishedAt.coerceAtLeast(session.startedAt),
                updatedAt = now,
            )
            if (didFinish == 1) {
                session.trainingDayId?.let { trainingDayId ->
                    database.scheduleDao().updateTrainingDaySession(
                        trainingDayId = trainingDayId,
                        sessionId = session.id,
                        status = if (status == WorkoutSessionStatus.COMPLETED) {
                            TrainingDayStatus.COMPLETED
                        } else {
                            TrainingDayStatus.PARTIALLY_COMPLETED
                        },
                        updatedAt = now,
                    )
                }

                val plan = database.trainingPlanDao().planById(session.planId)
                if (
                    status == WorkoutSessionStatus.COMPLETED &&
                    session.sessionIntent == WorkoutSessionEntity.INTENT_NORMAL &&
                    session.advancesSequence &&
                    plan?.workoutSelectionMode == WorkoutSelectionMode.SEQUENTIAL
                ) {
                    val workouts = database.workoutTemplateDao().activeWorkoutsForPlan(session.planId)
                    val sequenceState = database.planSequenceStateDao().getOrCreate(session.planId, now)
                    val nextCursor = SequenceEngine.advance(
                        workouts = workouts.map { it.toDomain() },
                        cursor = sequenceState.toSequenceCursor(),
                        event = SequenceEngine.AdvanceEvent(
                            id = session.id,
                            outcome = SequenceEngine.Outcome.COMPLETED,
                            kind = SequenceEngine.SelectionKind.NORMAL,
                            workoutId = session.sourceWorkoutTemplateId?.let(::WorkoutTemplateId),
                        ),
                    )
                    database.planSequenceStateDao().applyAdvanceIfNew(
                        planId = session.planId,
                        nextWorkoutTemplateId = nextCursor.nextWorkoutId?.value,
                        cursorIndex = nextCursor.nextIndex,
                        revision = sequenceState.revision + 1,
                        advanceId = session.id,
                        advancedAt = finishedAt.coerceAtLeast(session.startedAt),
                        updatedAt = now,
                    )
                }
            }
        }
    }

    suspend fun updateActiveSessionSet(
        setId: String,
        repetitions: Int?,
        loadKg: Double?,
        completed: Boolean,
    ) = withContext(Dispatchers.IO) {
        database.workoutSessionDao().updateActiveSet(
            setId = setId,
            repetitions = repetitions,
            loadKg = loadKg,
            status = if (completed) SessionSetStatus.COMPLETED else SessionSetStatus.NOT_STARTED,
        )
    }

    suspend fun updateActiveSessionExerciseOrder(orderedExerciseIds: List<String>) = withContext(Dispatchers.IO) {
        orderedExerciseIds.forEachIndexed { position, exerciseId ->
            database.workoutSessionDao().updateActiveExercisePosition(exerciseId, position)
        }
    }

    suspend fun updateWorkoutExercises(edits: List<WorkoutExerciseEdit>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val now = Instant.now()
            edits.forEach { edit ->
                val safeSeriesCount = edit.seriesCount.coerceAtLeast(1)
                val safeRest = edit.restBetweenSetsSeconds.coerceAtLeast(0)
                val safeTransition = edit.transitionRestSeconds.coerceAtLeast(0)
                database.workoutTemplateDao().updateExerciseRest(
                    workoutExerciseTemplateId = edit.workoutExerciseTemplateId,
                    restBetweenSetsSeconds = safeRest,
                    transitionRestSeconds = safeTransition,
                )
                val workoutExercise = database.workoutTemplateDao()
                    .workoutExerciseById(edit.workoutExerciseTemplateId)
                workoutExercise?.exerciseId?.let { exerciseId ->
                    val safeName = edit.exerciseName.trim()
                    if (safeName.isNotBlank()) {
                        database.exerciseDao().updateName(
                            id = exerciseId,
                            name = safeName,
                            normalizedName = safeName.lowercase(),
                            updatedAt = now,
                        )
                    }
                }
                parseRepetitionTarget(edit.repetitions)?.let { target ->
                    database.workoutTemplateDao().updateSetTargets(
                        workoutExerciseTemplateId = edit.workoutExerciseTemplateId,
                        minimum = target.minimum,
                        maximum = target.maximum,
                        target = target.target,
                    )
                }

                val sets = database.workoutTemplateDao().setsForWorkoutExercise(edit.workoutExerciseTemplateId)
                when {
                    safeSeriesCount < sets.size -> {
                        database.workoutTemplateDao().deleteSetsFromPosition(
                            workoutExerciseTemplateId = edit.workoutExerciseTemplateId,
                            fromPosition = safeSeriesCount,
                        )
                    }
                    safeSeriesCount > sets.size -> {
                        val baseSet = sets.lastOrNull()
                        (sets.size until safeSeriesCount).forEach { position ->
                            database.workoutTemplateDao().insertSet(
                                SetTemplateEntity(
                                    id = UUID.randomUUID().toString(),
                                    workoutExerciseTemplateId = edit.workoutExerciseTemplateId,
                                    position = position,
                                    minimumRepetitions = baseSet?.minimumRepetitions,
                                    maximumRepetitions = baseSet?.maximumRepetitions,
                                    targetRepetitions = baseSet?.targetRepetitions,
                                    suggestedLoadKg = baseSet?.suggestedLoadKg,
                                ),
                            )
                        }
                    }
                }
                database.workoutTemplateDao().touchWorkoutForExercise(
                    workoutExerciseTemplateId = edit.workoutExerciseTemplateId,
                    updatedAt = now,
                )
            }
        }
    }

    suspend fun addWorkoutExercise(input: NewWorkoutExerciseInput) = withContext(Dispatchers.IO) {
        val name = input.name.trim()
        val repetitionTarget = parseRepetitionTarget(input.repetitions)
            ?: error("Invalid repetitions")
        require(name.isNotBlank() && input.seriesCount > 0)
        database.withTransaction {
            val now = Instant.now()
            val position = database.workoutTemplateDao().exercisesForWorkout(input.workoutId).size
            val exerciseId = UUID.randomUUID().toString()
            val workoutExerciseId = UUID.randomUUID().toString()
            database.exerciseDao().insert(
                ExerciseEntity(
                    id = exerciseId,
                    name = name,
                    normalizedName = name.lowercase(),
                    category = null,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.workoutTemplateDao().insertWorkoutExercise(
                WorkoutExerciseTemplateEntity(
                    id = workoutExerciseId,
                    workoutTemplateId = input.workoutId,
                    exerciseId = exerciseId,
                    position = position,
                    trackingMode = TrackingMode.LOAD_AND_REPETITIONS,
                    restBetweenSetsSeconds = input.restBetweenSetsSeconds.coerceAtLeast(0),
                    transitionRestSeconds = input.transitionRestSeconds.coerceAtLeast(0),
                    notes = null,
                ),
            )
            repeat(input.seriesCount) { setPosition ->
                database.workoutTemplateDao().insertSet(
                    SetTemplateEntity(
                        id = UUID.randomUUID().toString(),
                        workoutExerciseTemplateId = workoutExerciseId,
                        position = setPosition,
                        minimumRepetitions = repetitionTarget.minimum,
                        maximumRepetitions = repetitionTarget.maximum,
                        targetRepetitions = repetitionTarget.target,
                        suggestedLoadKg = null,
                    ),
                )
            }
            database.workoutTemplateDao().touchWorkoutForExercise(workoutExerciseId, now)
        }
    }

    suspend fun deleteWorkoutExercise(workoutExerciseId: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val entity = database.workoutTemplateDao().workoutExerciseById(workoutExerciseId)
                ?: return@withTransaction
            database.workoutTemplateDao().deleteSetsForWorkoutExercise(workoutExerciseId)
            database.workoutTemplateDao().deleteWorkoutExercise(workoutExerciseId)
            database.workoutTemplateDao().exercisesForWorkout(entity.workoutTemplateId)
                .forEachIndexed { index, remaining ->
                    database.workoutTemplateDao().updateWorkoutExercisePosition(remaining.id, index)
                }
            if (database.exerciseDao().countTemplateReferences(entity.exerciseId) == 0) {
                database.exerciseDao().archive(entity.exerciseId, Instant.now())
            }
            database.workoutTemplateDao().touchWorkout(entity.workoutTemplateId, Instant.now())
        }
    }

    suspend fun moveWorkoutExercise(workoutExerciseId: String, direction: Int) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val entity = database.workoutTemplateDao().workoutExerciseById(workoutExerciseId)
                ?: return@withTransaction
            val exercises = database.workoutTemplateDao().exercisesForWorkout(entity.workoutTemplateId)
            val index = exercises.indexOfFirst { it.id == workoutExerciseId }
            val targetIndex = index + direction
            if (index < 0 || targetIndex !in exercises.indices) return@withTransaction
            val reordered = exercises.toMutableList().apply { add(targetIndex, removeAt(index)) }
            reordered.forEachIndexed { position, item ->
                database.workoutTemplateDao().updateWorkoutExercisePosition(item.id, position)
            }
            database.workoutTemplateDao().touchWorkoutForExercise(workoutExerciseId, Instant.now())
        }
    }

    private suspend fun insertImportedWorkout(
        planId: String,
        workout: ImportedWorkout,
        position: Int,
        now: Instant,
    ) {
        val workoutId = UUID.randomUUID().toString()
        database.workoutTemplateDao().insertWorkout(
            WorkoutTemplateEntity(
                id = workoutId,
                planId = planId,
                code = workout.code,
                name = workout.name,
                position = position,
                notes = null,
                isArchived = false,
                createdAt = now,
                updatedAt = now,
            ),
        )

        workout.exercises.forEachIndexed { exerciseIndex, exercise ->
            val exerciseId = UUID.randomUUID().toString()
            val workoutExerciseId = UUID.randomUUID().toString()
            database.exerciseDao().insert(
                ExerciseEntity(
                    id = exerciseId,
                    name = exercise.name,
                    normalizedName = exercise.name.lowercase(),
                    category = null,
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.workoutTemplateDao().insertWorkoutExercise(
                WorkoutExerciseTemplateEntity(
                    id = workoutExerciseId,
                    workoutTemplateId = workoutId,
                    exerciseId = exerciseId,
                    position = exerciseIndex,
                    trackingMode = exercise.trackingMode,
                    restBetweenSetsSeconds = exercise.restBetweenSetsSeconds,
                    transitionRestSeconds = exercise.transitionRestSeconds,
                    notes = exercise.notes,
                ),
            )
            exercise.repetitionTargets.forEachIndexed { setIndex, target ->
                database.workoutTemplateDao().insertSet(
                    SetTemplateEntity(
                        id = UUID.randomUUID().toString(),
                        workoutExerciseTemplateId = workoutExerciseId,
                        position = setIndex,
                        minimumRepetitions = target.minimum,
                        maximumRepetitions = target.maximum,
                        targetRepetitions = target.target,
                        suggestedLoadKg = exercise.suggestedLoadKg,
                    ),
                )
            }
        }
    }

    private data class WorkoutStartSelection(
        val workout: WorkoutTemplateEntity,
        val intent: String,
        val origin: String,
        val advancesSequence: Boolean,
    )

    private suspend fun selectWorkoutForStart(
        activePlan: TrainingPlanEntity,
        workouts: List<WorkoutTemplateEntity>,
        sessions: List<WorkoutSessionEntity>,
        kind: SequenceEngine.SelectionKind,
        workoutId: String? = null,
        now: Instant,
    ): WorkoutStartSelection? {
        val intent = when (kind) {
            SequenceEngine.SelectionKind.NORMAL -> WorkoutSessionEntity.INTENT_NORMAL
            SequenceEngine.SelectionKind.REPEAT -> WorkoutSessionEntity.INTENT_REPEAT
            SequenceEngine.SelectionKind.EXTRA -> WorkoutSessionEntity.INTENT_EXTRA
        }
        val origin = when (kind) {
            SequenceEngine.SelectionKind.NORMAL -> WorkoutSessionEntity.ORIGIN_NORMAL
            SequenceEngine.SelectionKind.REPEAT -> WorkoutSessionEntity.ORIGIN_REPEAT
            SequenceEngine.SelectionKind.EXTRA -> WorkoutSessionEntity.ORIGIN_EXTRA
        }

        if (activePlan.workoutSelectionMode == WorkoutSelectionMode.SEQUENTIAL) {
            val state = database.planSequenceStateDao().getOrCreate(activePlan.id, now)
            val cursor = state.toSequenceCursor()
            val selection = SequenceEngine.select(
                workouts = workouts.map { it.toDomain() },
                cursor = cursor,
                request = SequenceEngine.SelectionRequest(
                    kind = kind,
                    workoutId = workoutId?.let(::WorkoutTemplateId),
                ),
            ) as? SequenceEngine.SelectionResult.Selected ?: return null

            val reconciledCursor = selection.value.reconciledCursor
            if (reconciledCursor != cursor) {
                database.planSequenceStateDao().updateCursor(
                    planId = activePlan.id,
                    nextWorkoutTemplateId = reconciledCursor.nextWorkoutId?.value,
                    cursorIndex = reconciledCursor.nextIndex,
                    revision = state.revision,
                    updatedAt = now,
                )
            }
            val selectedWorkout = workouts.firstOrNull { it.id == selection.value.workout.id.value }
                ?: return null
            return WorkoutStartSelection(
                workout = selectedWorkout,
                intent = intent,
                origin = origin,
                advancesSequence = kind == SequenceEngine.SelectionKind.NORMAL,
            )
        }

        val domainWorkouts = workouts.map { it.toDomain() }
        val domainSessions = sessions.map { it.toDomain() }
        val selectedWorkout = workoutId?.let { id ->
            workouts.firstOrNull { it.id == id }
        } ?: run {
            val enabledRules = database.scheduleDao().enabledRulesForPlan(activePlan.id)
                .map { it.toDomain() }
            val scheduled = scheduleEngine.workoutForDate(
                input = ScheduleInput(
                    activePlan = activePlan.toDomain(),
                    workouts = domainWorkouts,
                    scheduleRules = enabledRules,
                    sessions = domainSessions,
                ),
                date = currentDate(),
            ) as? ScheduleResult.PlannedWorkout
            val scheduledId = scheduled?.workout?.id?.value
            scheduledId?.let { id -> workouts.firstOrNull { it.id == id } }
                ?: scheduleEngine.nextSequentialWorkout(
                    plan = activePlan.toDomain(),
                    workouts = domainWorkouts,
                    sessions = domainSessions,
                ).id.value.let { id -> workouts.firstOrNull { it.id == id } }
        } ?: return null

        return WorkoutStartSelection(
            workout = selectedWorkout,
            intent = intent,
            origin = origin,
            advancesSequence = false,
        )
    }

    private fun nextSequentialDashboardWorkout(
        workouts: List<WorkoutTemplateEntity>,
        sessions: List<WorkoutSessionEntity>,
        sequenceState: PlanSequenceStateEntity?,
    ): com.vitorkrichter.ordo.domain.model.WorkoutTemplate? {
        val activeSession = sessions.firstOrNull { it.status == WorkoutSessionStatus.IN_PROGRESS }
        activeSession?.sourceWorkoutTemplateId?.let { sourceId ->
            workouts.firstOrNull { it.id == sourceId }?.let { return it.toDomain() }
        }
        val cursor = sequenceState?.toSequenceCursor() ?: SequenceEngine.SequenceCursor()
        return (SequenceEngine.select(workouts.map { it.toDomain() }, cursor)
            as? SequenceEngine.SelectionResult.Selected)
            ?.value
            ?.workout
    }

    private fun nextDashboardWorkout(
        plan: TrainingPlanEntity,
        workouts: List<WorkoutTemplateEntity>,
        rules: List<ScheduleRuleEntity>,
        sessions: List<WorkoutSessionEntity>,
        today: LocalDate,
    ): com.vitorkrichter.ordo.domain.model.WorkoutTemplate? {
        val activeSession = sessions.firstOrNull { it.status == WorkoutSessionStatus.IN_PROGRESS }
        activeSession?.sourceWorkoutTemplateId?.let { sourceId ->
            workouts.firstOrNull { it.id == sourceId }?.let { return it.toDomain() }
        }
        val domainPlan = plan.toDomain()
        val domainWorkouts = workouts.map { it.toDomain() }
        val domainSessions = sessions.map { it.toDomain() }
        val domainRules = rules.map { it.toDomain() }
        (0L..7L).forEach { offset ->
            val result = scheduleEngine.workoutForDate(
                input = ScheduleInput(
                    activePlan = domainPlan,
                    workouts = domainWorkouts,
                    scheduleRules = domainRules,
                    sessions = domainSessions.filter { it.status != WorkoutSessionStatus.IN_PROGRESS },
                ),
                date = today.plusDays(offset),
            )
            if (result is ScheduleResult.PlannedWorkout) return result.workout
        }
        return domainWorkouts.firstOrNull()
    }

    private suspend fun detailForSession(session: WorkoutSessionEntity): WorkoutDetailUi? {
        val sourceWorkout = session.sourceWorkoutTemplateId?.let {
            database.workoutTemplateDao().workoutById(it)
        }
        val plan = database.trainingPlanDao().planById(session.planId)
        val planWorkouts = plan?.let { database.workoutTemplateDao().activeWorkoutsForPlan(it.id) }.orEmpty()
        val snapshotExercises = database.workoutSessionDao().exercisesForSession(session.id)
        if (snapshotExercises.isEmpty()) return null
        val exercises = snapshotExercises.map { snapshotExercise ->
            val snapshotSets = database.workoutSessionDao().setsForSessionExercise(snapshotExercise.id)
            val previousExercise = snapshotExercise.sourceExerciseId?.let { sourceId ->
                database.workoutSessionDao().latestExerciseForSource(session.planId, sourceId)
            }
            val previousSets = previousExercise?.let {
                database.workoutSessionDao().setsForSessionExercise(it.id)
            }.orEmpty()
            WorkoutExerciseDetailUi(
                id = snapshotExercise.id,
                sourceExerciseId = snapshotExercise.sourceExerciseId,
                name = snapshotExercise.exerciseNameSnapshot,
                position = snapshotExercise.position,
                seriesCount = snapshotSets.size,
                repetitions = snapshotSets.sessionRepetitionLabel(),
                restBetweenSetsSeconds = snapshotExercise.restBetweenSetsSecondsSnapshot,
                transitionRestSeconds = snapshotExercise.transitionRestSecondsSnapshot,
                trackingMode = snapshotExercise.trackingModeSnapshot,
                notes = snapshotExercise.notesSnapshot,
                sets = snapshotSets.map { set ->
                    WorkoutSetDetailUi(
                        id = set.id,
                        position = set.position,
                        minimumRepetitions = set.plannedMinimumRepetitions,
                        maximumRepetitions = set.plannedMaximumRepetitions,
                        targetRepetitions = set.plannedTargetRepetitions,
                        suggestedLoadKg = set.suggestedLoadKg,
                        actualRepetitions = set.actualRepetitions,
                        actualLoadKg = set.actualLoadKg,
                        isCompleted = set.status == SessionSetStatus.COMPLETED,
                    )
                },
                previousPerformance = previousSets
                    .filter { it.status == SessionSetStatus.COMPLETED }
                    .map { set ->
                        PreviousSetPerformanceUi(
                            repetitions = set.actualRepetitions,
                            loadKg = set.actualLoadKg,
                        )
                    },
            )
        }
        val position = sourceWorkout?.position?.plus(1)
        return WorkoutDetailUi(
            workoutId = sourceWorkout?.id ?: session.sourceWorkoutTemplateId ?: session.id,
            code = sourceWorkout?.code ?: "—",
            name = session.workoutNameSnapshot,
            sequenceLabel = if (position != null && planWorkouts.isNotEmpty()) {
                "$position / ${planWorkouts.size}"
            } else {
                "—"
            },
            exercises = exercises,
            sessionId = session.id,
            startedAtEpochMillis = session.startedAt.toEpochMilli(),
        )
    }

    private fun isPerformanceValid(
        trackingMode: TrackingMode,
        repetitions: Int?,
        loadKg: Double?,
    ): Boolean = when (trackingMode) {
        TrackingMode.NONE -> true
        TrackingMode.REPETITIONS -> repetitions != null && repetitions > 0
        TrackingMode.LOAD_AND_REPETITIONS -> {
            repetitions != null && repetitions > 0 && loadKg != null && loadKg >= 0.0
        }
    }

    private suspend fun workoutExercises(workoutTemplateId: String): List<WorkoutExerciseDetailUi> {
        return database.workoutTemplateDao().exercisesForWorkout(workoutTemplateId).map { exerciseTemplate ->
            val exercise = database.exerciseDao().findById(exerciseTemplate.exerciseId)
            val sets = database.workoutTemplateDao().setsForWorkoutExercise(exerciseTemplate.id)
            WorkoutExerciseDetailUi(
                id = exerciseTemplate.id,
                sourceExerciseId = exerciseTemplate.exerciseId,
                name = exercise?.name.orEmpty(),
                position = exerciseTemplate.position,
                seriesCount = sets.size,
                repetitions = sets.repetitionLabel(),
                restBetweenSetsSeconds = exerciseTemplate.restBetweenSetsSeconds,
                transitionRestSeconds = exerciseTemplate.transitionRestSeconds,
                trackingMode = exerciseTemplate.trackingMode,
                notes = exerciseTemplate.notes,
                sets = sets.map { set ->
                    WorkoutSetDetailUi(
                        id = set.id,
                        position = set.position,
                        minimumRepetitions = set.minimumRepetitions,
                        maximumRepetitions = set.maximumRepetitions,
                        targetRepetitions = set.targetRepetitions,
                        suggestedLoadKg = set.suggestedLoadKg,
                    )
                },
            )
        }
    }

    private fun divisionFor(workoutCount: Int): WorkoutDivisionType = when (workoutCount) {
        1 -> WorkoutDivisionType.FULL_BODY
        2 -> WorkoutDivisionType.AB
        3 -> WorkoutDivisionType.ABC
        4 -> WorkoutDivisionType.ABCD
        5 -> WorkoutDivisionType.ABCDE
        else -> WorkoutDivisionType.CUSTOM
    }

    companion object {
        private const val SAMPLE_PLAN_ID = "sample-plan-abc"

        private val SAMPLE_WORKOUTS = listOf(
            SampleWorkout(
                id = "sample-workout-a",
                code = "A",
                name = "Peito, ombro e triceps",
                exercises = listOf(
                    SampleExercise("sample-supino-reto", "Supino reto com barra", 4, 8, 10, 90, 120, TrackingMode.LOAD_AND_REPETITIONS),
                    SampleExercise("sample-desenvolvimento", "Desenvolvimento com halteres", 3, 8, 10, 90, 90, TrackingMode.LOAD_AND_REPETITIONS),
                    SampleExercise("sample-triceps-corda", "Triceps na corda", 3, 10, 12, 60, 0, TrackingMode.LOAD_AND_REPETITIONS),
                ),
            ),
            SampleWorkout(
                id = "sample-workout-b",
                code = "B",
                name = "Costas e biceps",
                exercises = listOf(
                    SampleExercise("sample-puxada-frontal", "Puxada frontal", 4, 8, 10, 90, 120, TrackingMode.LOAD_AND_REPETITIONS),
                    SampleExercise("sample-remada-baixa", "Remada baixa", 3, 10, 12, 90, 90, TrackingMode.LOAD_AND_REPETITIONS),
                    SampleExercise("sample-rosca-direta", "Rosca direta", 3, 8, 10, 60, 0, TrackingMode.LOAD_AND_REPETITIONS),
                ),
            ),
            SampleWorkout(
                id = "sample-workout-c",
                code = "C",
                name = "Pernas",
                exercises = listOf(
                    SampleExercise("sample-agachamento", "Agachamento livre", 4, 6, 8, 120, 150, TrackingMode.LOAD_AND_REPETITIONS),
                    SampleExercise("sample-leg-press", "Leg press", 4, 10, 12, 90, 120, TrackingMode.LOAD_AND_REPETITIONS),
                    SampleExercise("sample-panturrilha", "Panturrilha em pe", 4, 12, 15, 60, 0, TrackingMode.REPETITIONS),
                ),
            ),
        )
    }
}

private fun PlanSequenceStateEntity.toSequenceCursor(): SequenceEngine.SequenceCursor =
    SequenceEngine.SequenceCursor(
        nextWorkoutId = nextWorkoutTemplateId
            ?.let(::WorkoutTemplateId),
        nextIndex = cursorIndex,
        lastAdvancedEventId = lastAdvancedSessionId,
    )

private fun com.vitorkrichter.ordo.core.database.dao.WorkoutExerciseSummary.repetitionLabel(): String {
    return when {
        targetRepetitions != null -> targetRepetitions.toString()
        minimumRepetitions != null && maximumRepetitions != null && minimumRepetitions != maximumRepetitions -> {
            "$minimumRepetitions-$maximumRepetitions"
        }
        minimumRepetitions != null -> minimumRepetitions.toString()
        maximumRepetitions != null -> maximumRepetitions.toString()
        else -> "-"
    }
}

private fun List<SetTemplateEntity>.repetitionLabel(): String {
    val labels = map { set ->
        when {
            set.targetRepetitions != null -> set.targetRepetitions.toString()
            set.minimumRepetitions != null &&
                set.maximumRepetitions != null &&
                set.minimumRepetitions != set.maximumRepetitions -> {
                "${set.minimumRepetitions}-${set.maximumRepetitions}"
            }
            set.minimumRepetitions != null -> set.minimumRepetitions.toString()
            set.maximumRepetitions != null -> set.maximumRepetitions.toString()
            else -> "-"
        }
    }
    return labels.distinct().singleOrNull() ?: labels.joinToString(",")
}

private fun List<SessionSetEntity>.sessionRepetitionLabel(): String {
    val labels = map { set ->
        when {
            set.plannedTargetRepetitions != null -> set.plannedTargetRepetitions.toString()
            set.plannedMinimumRepetitions != null &&
                set.plannedMaximumRepetitions != null &&
                set.plannedMinimumRepetitions != set.plannedMaximumRepetitions -> {
                "${set.plannedMinimumRepetitions}-${set.plannedMaximumRepetitions}"
            }
            set.plannedMinimumRepetitions != null -> set.plannedMinimumRepetitions.toString()
            set.plannedMaximumRepetitions != null -> set.plannedMaximumRepetitions.toString()
            else -> "-"
        }
    }
    return labels.distinct().singleOrNull() ?: labels.joinToString(",")
}

private data class SampleWorkout(
    val id: String,
    val code: String,
    val name: String,
    val exercises: List<SampleExercise>,
)

private data class SampleExercise(
    val id: String,
    val name: String,
    val series: Int,
    val minReps: Int,
    val maxReps: Int,
    val restSeconds: Int,
    val transitionSeconds: Int,
    val trackingMode: TrackingMode,
)

private data class EditableRepetitionTarget(
    val minimum: Int,
    val maximum: Int,
    val target: Int?,
)

private fun parseRepetitionTarget(value: String): EditableRepetitionTarget? {
    val normalized = value.replace(" ", "")
    return if ("-" in normalized) {
        val minimum = normalized.substringBefore("-").toIntOrNull() ?: return null
        val maximum = normalized.substringAfter("-").toIntOrNull() ?: return null
        if (minimum <= 0 || maximum < minimum) return null
        EditableRepetitionTarget(minimum, maximum, null)
    } else {
        val target = normalized.toIntOrNull()?.takeIf { it > 0 } ?: return null
        EditableRepetitionTarget(target, target, target)
    }
}

private val AppZoneId: ZoneId = AppTime.zoneId

private fun currentDate(): LocalDate = AppTime.today()

private fun DayOfWeek.portugueseName(): String = when (this) {
    DayOfWeek.MONDAY -> "SEGUNDA"
    DayOfWeek.TUESDAY -> "TERCA"
    DayOfWeek.WEDNESDAY -> "QUARTA"
    DayOfWeek.THURSDAY -> "QUINTA"
    DayOfWeek.FRIDAY -> "SEXTA"
    DayOfWeek.SATURDAY -> "SABADO"
    DayOfWeek.SUNDAY -> "DOMINGO"
}

private fun TrackingMode.importName(): String = when (this) {
    TrackingMode.NONE -> "NENHUM"
    TrackingMode.REPETITIONS -> "REPETICOES"
    TrackingMode.LOAD_AND_REPETITIONS -> "CARGA_REPETICOES"
}
