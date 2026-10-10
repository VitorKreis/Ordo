package com.vitorkrichter.ordo.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.notifications.RestTimerScheduler
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import com.vitorkrichter.ordo.data.repository.SetPerformance
import com.vitorkrichter.ordo.data.repository.WorkoutDetailUi
import com.vitorkrichter.ordo.data.repository.WorkoutSetDetailUi
import com.vitorkrichter.ordo.data.session.SessionDraft
import com.vitorkrichter.ordo.data.session.SessionDraftStore
import com.vitorkrichter.ordo.data.session.SessionPerformanceDraft
import com.vitorkrichter.ordo.domain.model.TrackingMode
import com.vitorkrichter.ordo.domain.rule.SessionExecutionRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val workout: WorkoutDetailUi? = null,
    val completedSetKeys: Set<String> = emptySet(),
    val performanceDrafts: Map<String, SetPerformanceDraft> = emptyMap(),
    val lockedPerformanceKeys: Set<String> = emptySet(),
    val startedAtEpochMillis: Long? = null,
    val lastSetCompletedAtEpochMillis: Long? = null,
    val activeSetKey: String? = null,
    val timer: RestTimerUi? = null,
    val messageRes: Int? = null,
    val finished: Boolean = false,
) {
    val allExercisesCompleted: Boolean
        get() = workout?.let { completedSetKeys.containsAll(it.allSetKeys()) } == true

    val completedSetCount: Int get() = completedSetKeys.size
    val totalSetCount: Int get() = workout?.allSetKeys()?.size ?: 0
    val isResting: Boolean get() = (timer?.remainingSeconds ?: 0) > 0
}

data class RestTimerUi(
    val setKey: String,
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val endsAtEpochMillis: Long,
) {
    val label: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

data class SetPerformanceDraft(
    val repetitions: String = "",
    val loadKg: String = "",
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: OrdoRepository,
    private val draftStore: SessionDraftStore,
    private val restTimerScheduler: RestTimerScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, messageRes = null)
            runCatching { repository.currentWorkoutDetail() }
                .onSuccess { workout ->
                    val restored = workout?.let(::restoredDraftFor)
                    val orderedWorkout = workout?.withExerciseOrder(restored?.exerciseOrder.orEmpty())
                    val persistedCompletedSetKeys = orderedWorkout?.persistedCompletedSetKeys().orEmpty()
                    val completedSetKeys = persistedCompletedSetKeys + restored?.completedSetKeys.orEmpty()
                    val persistedPerformanceDrafts = orderedWorkout?.persistedPerformanceDrafts().orEmpty()
                    _uiState.value = SessionUiState(
                        isLoading = false,
                        workout = orderedWorkout,
                        completedSetKeys = completedSetKeys,
                        performanceDrafts = persistedPerformanceDrafts + restored?.performanceDrafts.orEmpty(),
                        lockedPerformanceKeys = restored?.lockedPerformanceKeys.orEmpty(),
                        startedAtEpochMillis = orderedWorkout?.startedAtEpochMillis
                            ?: restored?.startedAtEpochMillis,
                        lastSetCompletedAtEpochMillis = restored?.lastSetCompletedAtEpochMillis,
                        activeSetKey = orderedWorkout?.firstIncompleteSetKey(completedSetKeys),
                    )
                    val timerSetKey = restored?.restTimerSetKey
                    val timerEndsAt = restored?.restTimerEndsAtEpochMillis
                    val timerTotal = restored?.restTimerTotalSeconds
                    if (timerSetKey != null && timerEndsAt != null && timerTotal != null) {
                        if (timerEndsAt > System.currentTimeMillis()) {
                            startRestTimer(timerSetKey, timerTotal, timerEndsAt)
                        } else {
                            saveDraft(_uiState.value)
                        }
                    }
                }
                .onFailure {
                    _uiState.value = SessionUiState(
                        isLoading = false,
                        messageRes = R.string.session_error,
                    )
                }
        }
    }

    fun completeSet(exerciseId: String, setId: String) {
        val state = _uiState.value
        val workout = state.workout ?: return
        val setKey = "$exerciseId:$setId"
        if (setKey in state.completedSetKeys) return

        val completed = state.completedSetKeys + setKey
        val nextActiveSetKey = workout.firstIncompleteSetKey(completed)
        val completedAt = System.currentTimeMillis()
        val nextState = state.copy(
            completedSetKeys = completed,
            performanceDrafts = state.performanceDrafts + (setKey to (state.performanceDrafts[setKey] ?: SetPerformanceDraft())),
            activeSetKey = nextActiveSetKey,
            startedAtEpochMillis = state.startedAtEpochMillis ?: completedAt,
            lastSetCompletedAtEpochMillis = completedAt,
        )
        _uiState.value = nextState
        saveDraft(nextState)
        persistSet(setId = setId, state = nextState, completed = true)

        val restSeconds = workout.restAfter(exerciseId = exerciseId, setId = setId)
        if (restSeconds > 0) {
            startRestTimer(setKey = setKey, restSeconds = restSeconds)
        }
    }

    fun undoSet(setKey: String) {
        val state = _uiState.value
        if (setKey !in state.completedSetKeys) return

        if (state.timer?.setKey == setKey) {
            timerJob?.cancel()
            restTimerScheduler.cancel()
        }
        val completed = state.completedSetKeys - setKey
        val nextState = state.copy(
            completedSetKeys = completed,
            performanceDrafts = state.performanceDrafts - setKey,
            lockedPerformanceKeys = state.lockedPerformanceKeys - setKey,
            activeSetKey = state.workout?.firstIncompleteSetKey(completed),
            timer = state.timer?.takeUnless { it.setKey == setKey },
            startedAtEpochMillis = state.startedAtEpochMillis.takeIf { completed.isNotEmpty() },
            lastSetCompletedAtEpochMillis = state.lastSetCompletedAtEpochMillis.takeIf { completed.isNotEmpty() },
            messageRes = null,
        )
        _uiState.value = nextState
        saveDraft(nextState)
        persistSet(setId = setKey.substringAfterLast(':'), state = nextState, completed = false)
    }

    fun onRepetitionsChange(setKey: String, value: String) {
        if (setKey in _uiState.value.lockedPerformanceKeys) return
        updatePerformance(setKey) {
            copy(repetitions = value.filter(Char::isDigit).take(3))
        }
    }

    fun onLoadChange(setKey: String, value: String) {
        if (setKey in _uiState.value.lockedPerformanceKeys) return
        updatePerformance(setKey) {
            val normalized = value.replace(',', '.')
            copy(loadKg = normalized.filterIndexed { index, char ->
                char.isDigit() || (char == '.' && index > 0 && '.' !in normalized.take(index))
            }.take(6))
        }
    }

    fun confirmPerformance(setKey: String) {
        val state = _uiState.value
        if (setKey !in state.completedSetKeys) return
        val draft = state.performanceDrafts[setKey] ?: SetPerformanceDraft()
        val trackingMode = state.workout?.trackingModeForSetKey(setKey) ?: TrackingMode.NONE
        val isValid = SessionExecutionRules.isPerformanceValid(
            trackingMode = trackingMode,
            repetitions = draft.repetitions,
            loadKg = draft.loadKg,
        )
        if (!isValid) {
            _uiState.value = state.copy(messageRes = R.string.session_performance_required)
            return
        }
        val nextState = state.copy(
            lockedPerformanceKeys = state.lockedPerformanceKeys + setKey,
            messageRes = null,
        )
        _uiState.value = nextState
        saveDraft(nextState)
    }

    fun editPerformance(setKey: String) {
        val state = _uiState.value
        if (setKey !in state.lockedPerformanceKeys) return
        val nextState = state.copy(
            lockedPerformanceKeys = state.lockedPerformanceKeys - setKey,
        )
        _uiState.value = nextState
        saveDraft(nextState)
    }

    fun moveExercise(exerciseId: String, direction: Int) {
        val state = _uiState.value
        val workout = state.workout ?: return
        val currentIndex = workout.exercises.indexOfFirst { it.id == exerciseId }
        val targetIndex = currentIndex + direction
        if (currentIndex < 0 || targetIndex !in workout.exercises.indices) return
        val reordered = workout.exercises.toMutableList().apply {
            add(targetIndex, removeAt(currentIndex))
        }
        val nextWorkout = workout.copy(exercises = reordered)
        val nextState = state.copy(
            workout = nextWorkout,
            activeSetKey = nextWorkout.firstIncompleteSetKey(state.completedSetKeys),
        )
        _uiState.value = nextState
        saveDraft(nextState)
        viewModelScope.launch {
            runCatching {
                repository.updateActiveSessionExerciseOrder(reordered.map { it.id })
            }
        }
    }

    fun adjustRestTimer(seconds: Int) {
        val state = _uiState.value
        val timer = state.timer ?: return
        val now = System.currentTimeMillis()
        val adjustedEnd = (timer.endsAtEpochMillis + seconds * 1_000L).coerceAtLeast(now)
        if (adjustedEnd <= now) {
            skipRestTimer()
            return
        }
        val adjustedTotal = (timer.totalSeconds + seconds).coerceAtLeast(1)
        startRestTimer(timer.setKey, adjustedTotal, adjustedEnd)
    }

    fun skipRestTimer() {
        timerJob?.cancel()
        restTimerScheduler.cancel()
        val nextState = _uiState.value.copy(timer = null)
        _uiState.value = nextState
        saveDraft(nextState)
    }

    fun finishWorkout() {
        val state = _uiState.value
        if (state.workout == null || state.isSaving) return
        val invalidPerformance = state.completedSetKeys.any { setKey ->
            val draft = state.performanceDrafts[setKey] ?: SetPerformanceDraft()
            val trackingMode = state.workout.trackingModeForSetKey(setKey) ?: TrackingMode.NONE
            !SessionExecutionRules.isPerformanceValid(
                trackingMode = trackingMode,
                repetitions = draft.repetitions,
                loadKg = draft.loadKg,
            )
        }
        if (invalidPerformance) {
            _uiState.value = state.copy(messageRes = R.string.session_performance_required)
            return
        }
        val performances = state.performanceDrafts
            .mapValues { (_, draft) ->
            SetPerformance(
                actualRepetitions = draft.repetitions.toIntOrNull(),
                actualLoadKg = draft.loadKg.toDoubleOrNull(),
            )
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, messageRes = null)
            val now = Instant.now()
            val startedAt = state.startedAtEpochMillis?.let(Instant::ofEpochMilli) ?: now
            val finishedAt = state.lastSetCompletedAtEpochMillis?.let(Instant::ofEpochMilli) ?: now
            runCatching {
                repository.finishCurrentWorkout(
                    completedSetKeys = state.completedSetKeys,
                    performances = performances,
                    orderedExerciseIds = state.workout.exercises.map { it.id },
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                )
            }
                .onSuccess {
                    restTimerScheduler.cancel()
                    draftStore.clear()
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        finished = true,
                        messageRes = if (state.allExercisesCompleted) {
                            R.string.session_finished_complete
                        } else {
                            R.string.session_finished_partial
                        },
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        messageRes = R.string.session_error,
                    )
                }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private fun startRestTimer(
        setKey: String,
        restSeconds: Int,
        endsAtEpochMillis: Long = System.currentTimeMillis() + restSeconds * 1_000L,
    ) {
        timerJob?.cancel()
        restTimerScheduler.schedule(endsAtEpochMillis)
        val initialRemaining = remainingSeconds(endsAtEpochMillis)
        val initialState = _uiState.value.copy(
            timer = RestTimerUi(
                setKey = setKey,
                remainingSeconds = initialRemaining,
                totalSeconds = restSeconds,
                endsAtEpochMillis = endsAtEpochMillis,
            ),
        )
        _uiState.value = initialState
        saveDraft(initialState)
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = remainingSeconds(endsAtEpochMillis)
                if (remaining <= 0) {
                    val finishedState = _uiState.value.copy(timer = null)
                    _uiState.value = finishedState
                    saveDraft(finishedState)
                    break
                }
                _uiState.value = _uiState.value.copy(
                    timer = RestTimerUi(
                        setKey = setKey,
                        remainingSeconds = remaining,
                        totalSeconds = restSeconds,
                        endsAtEpochMillis = endsAtEpochMillis,
                    ),
                )
                delay(1_000)
            }
        }
    }

    private fun updatePerformance(
        setKey: String,
        transform: SetPerformanceDraft.() -> SetPerformanceDraft,
    ) {
        val state = _uiState.value
        val current = state.performanceDrafts[setKey] ?: SetPerformanceDraft()
        val nextState = state.copy(
            performanceDrafts = state.performanceDrafts + (setKey to current.transform()),
        )
        _uiState.value = nextState
        saveDraft(nextState)
        if (setKey in state.completedSetKeys) {
            persistSet(setId = setKey.substringAfterLast(':'), state = nextState, completed = true)
        }
    }

    private fun restoredDraftFor(workout: WorkoutDetailUi): RestoredSessionDraft? {
        val draft = draftStore.load() ?: return null
        if (draft.workoutId != workout.workoutId) {
            draftStore.clear()
            return null
        }
        val validSetKeys = workout.allSetKeys()
        return RestoredSessionDraft(
            completedSetKeys = draft.completedSetKeys.intersect(validSetKeys),
            performanceDrafts = draft.performanceDrafts
                .filterKeys { it in validSetKeys }
                .mapValues { (_, value) ->
                    SetPerformanceDraft(
                        repetitions = value.repetitions,
                        loadKg = value.loadKg,
                    )
                },
            lockedPerformanceKeys = draft.lockedPerformanceKeys.intersect(validSetKeys),
            exerciseOrder = draft.exerciseOrder.filter { id -> workout.exercises.any { it.id == id } },
            startedAtEpochMillis = draft.startedAtEpochMillis,
            lastSetCompletedAtEpochMillis = draft.lastSetCompletedAtEpochMillis,
            restTimerSetKey = draft.restTimerSetKey,
            restTimerEndsAtEpochMillis = draft.restTimerEndsAtEpochMillis,
            restTimerTotalSeconds = draft.restTimerTotalSeconds,
        )
    }

    private fun saveDraft(state: SessionUiState) {
        val workout = state.workout ?: return
        draftStore.save(
            SessionDraft(
                workoutId = workout.workoutId,
                completedSetKeys = state.completedSetKeys,
                performanceDrafts = state.performanceDrafts.mapValues { (_, value) ->
                    SessionPerformanceDraft(
                        repetitions = value.repetitions,
                        loadKg = value.loadKg,
                    )
                },
                lockedPerformanceKeys = state.lockedPerformanceKeys,
                exerciseOrder = workout.exercises.map { it.id },
                startedAtEpochMillis = state.startedAtEpochMillis,
                lastSetCompletedAtEpochMillis = state.lastSetCompletedAtEpochMillis,
                restTimerSetKey = state.timer?.setKey,
                restTimerEndsAtEpochMillis = state.timer?.endsAtEpochMillis,
                restTimerTotalSeconds = state.timer?.totalSeconds,
            ),
        )
    }

    private fun persistSet(setId: String, state: SessionUiState, completed: Boolean) {
        val draft = state.performanceDrafts.entries.firstOrNull { it.key.substringAfterLast(':') == setId }
            ?.value
        viewModelScope.launch {
            runCatching {
                repository.updateActiveSessionSet(
                    setId = setId,
                    repetitions = draft?.repetitions?.toIntOrNull(),
                    loadKg = draft?.loadKg?.toDoubleOrNull(),
                    completed = completed,
                )
            }
        }
    }
}

private data class RestoredSessionDraft(
    val completedSetKeys: Set<String>,
    val performanceDrafts: Map<String, SetPerformanceDraft>,
    val lockedPerformanceKeys: Set<String>,
    val exerciseOrder: List<String>,
    val startedAtEpochMillis: Long?,
    val lastSetCompletedAtEpochMillis: Long?,
    val restTimerSetKey: String?,
    val restTimerEndsAtEpochMillis: Long?,
    val restTimerTotalSeconds: Int?,
)

private fun remainingSeconds(endsAtEpochMillis: Long): Int {
    return SessionExecutionRules.remainingRestSeconds(endsAtEpochMillis, System.currentTimeMillis())
}

private fun WorkoutDetailUi.withExerciseOrder(order: List<String>): WorkoutDetailUi {
    if (order.isEmpty()) return this
    val rank = order.withIndex().associate { it.value to it.index }
    return copy(exercises = exercises.sortedBy { rank[it.id] ?: Int.MAX_VALUE })
}

private fun WorkoutDetailUi.allSetKeys(): Set<String> {
    return exercises.flatMap { exercise ->
        exercise.sets.map { set -> set.key(exercise.id) }
    }.toSet()
}

private fun WorkoutDetailUi.persistedCompletedSetKeys(): Set<String> {
    return exercises.flatMap { exercise ->
        exercise.sets.filter { it.isCompleted }.map { set -> set.key(exercise.id) }
    }.toSet()
}

private fun WorkoutDetailUi.persistedPerformanceDrafts(): Map<String, SetPerformanceDraft> {
    return exercises.flatMap { exercise ->
        exercise.sets.mapNotNull { set ->
            if (set.actualRepetitions == null && set.actualLoadKg == null) return@mapNotNull null
            set.key(exercise.id) to SetPerformanceDraft(
                repetitions = set.actualRepetitions?.toString().orEmpty(),
                loadKg = set.actualLoadKg?.toString().orEmpty(),
            )
        }
    }.toMap()
}

private fun WorkoutDetailUi.firstIncompleteSetKey(completedSetKeys: Set<String>): String? {
    exercises.forEach { exercise ->
        exercise.sets.forEach { set ->
            val key = set.key(exercise.id)
            if (key !in completedSetKeys) return key
        }
    }
    return null
}

private fun WorkoutDetailUi.restAfter(exerciseId: String, setId: String): Int {
    val exerciseIndex = exercises.indexOfFirst { it.id == exerciseId }
    if (exerciseIndex < 0) return 0
    val exercise = exercises[exerciseIndex]
    val setIndex = exercise.sets.indexOfFirst { it.id == setId }
    if (setIndex < 0) return 0

    val isLastSetOfExercise = setIndex == exercise.sets.lastIndex
    val isLastExercise = exerciseIndex == exercises.lastIndex
    return when {
        isLastSetOfExercise && isLastExercise -> 0
        isLastSetOfExercise -> exercise.transitionRestSeconds
        else -> exercise.restBetweenSetsSeconds
    }
}

private fun WorkoutDetailUi.setByKey(setKey: String): WorkoutSetDetailUi? {
    exercises.forEach { exercise ->
        exercise.sets.forEach { set ->
            if (set.key(exercise.id) == setKey) return set
        }
    }
    return null
}

private fun WorkoutDetailUi.trackingModeForSetKey(setKey: String): TrackingMode? {
    return exercises.firstOrNull { exercise ->
        exercise.sets.any { it.key(exercise.id) == setKey }
    }?.trackingMode
}
