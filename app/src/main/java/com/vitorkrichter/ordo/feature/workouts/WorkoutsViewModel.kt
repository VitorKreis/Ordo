package com.vitorkrichter.ordo.feature.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import com.vitorkrichter.ordo.data.repository.MoveWorkoutDirection
import com.vitorkrichter.ordo.data.repository.NewWorkoutExerciseInput
import com.vitorkrichter.ordo.data.repository.WorkoutExerciseEdit
import com.vitorkrichter.ordo.data.repository.WorkoutExerciseUi
import com.vitorkrichter.ordo.data.repository.WorkoutUi
import com.vitorkrichter.ordo.data.repository.WorkoutsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class WorkoutsUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val isSaving: Boolean = false,
    val messageRes: Int? = null,
    val hasPlan: Boolean = false,
    val planName: String = "",
    val workouts: List<WorkoutUi> = emptyList(),
    val editingWorkoutCode: String? = null,
    val drafts: Map<String, ExerciseEditDraft> = emptyMap(),
)

data class ExerciseEditDraft(
    val exerciseId: String,
    val name: String,
    val repetitions: String,
    val seriesCount: String,
    val restBetweenSetsSeconds: String,
    val transitionRestSeconds: String,
)

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    private val repository: OrdoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutsUiState())
    val uiState: StateFlow<WorkoutsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeWorkouts()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isBusy = false,
                        isSaving = false,
                        messageRes = R.string.home_error,
                    )
                }
                .collect { snapshot ->
                _uiState.value = when (snapshot) {
                    WorkoutsSnapshot.Empty -> _uiState.value.copy(
                        isLoading = false,
                        hasPlan = false,
                        workouts = emptyList(),
                    )
                    is WorkoutsSnapshot.Ready -> {
                        val current = _uiState.value
                        current.copy(
                            isLoading = false,
                            hasPlan = true,
                            planName = snapshot.planName,
                            workouts = snapshot.workouts,
                            drafts = current.drafts.ifEmpty {
                                snapshot.workouts.flatMap { it.exercises }.associate { it.id to it.toDraft() }
                            },
                        )
                    }
                }
            }
        }
    }

    fun toggleEdit(workout: WorkoutUi) {
        if (_uiState.value.isBusy) return
        val current = _uiState.value
        val nextCode = if (current.editingWorkoutCode == workout.code) null else workout.code
        val workoutDrafts = workout.exercises.associate { it.id to it.toDraft() }
        _uiState.value = current.copy(
            editingWorkoutCode = nextCode,
            drafts = current.drafts + workoutDrafts,
        )
    }

    fun onSeriesChange(exerciseId: String, value: String) = updateDraft(exerciseId) {
        copy(seriesCount = value.filter(Char::isDigit).take(2))
    }

    fun onNameChange(exerciseId: String, value: String) = updateDraft(exerciseId) {
        copy(name = value.take(80))
    }

    fun onRepetitionsChange(exerciseId: String, value: String) = updateDraft(exerciseId) {
        copy(repetitions = value.filter { it.isDigit() || it == '-' }.take(7))
    }

    fun onRestChange(exerciseId: String, value: String) = updateDraft(exerciseId) {
        copy(restBetweenSetsSeconds = value.filter(Char::isDigit).take(4))
    }

    fun onTransitionChange(exerciseId: String, value: String) = updateDraft(exerciseId) {
        copy(transitionRestSeconds = value.filter(Char::isDigit).take(4))
    }

    fun saveEditingWorkout() {
        val state = _uiState.value
        if (state.isBusy) return
        val workout = state.workouts.firstOrNull { it.code == state.editingWorkoutCode } ?: return
        val edits = workout.exercises.mapNotNull { exercise ->
            val draft = state.drafts[exercise.id] ?: return@mapNotNull null
            WorkoutExerciseEdit(
                workoutExerciseTemplateId = exercise.id,
                exerciseName = draft.name,
                repetitions = draft.repetitions,
                seriesCount = draft.seriesCount.toIntOrNull() ?: exercise.seriesCount,
                restBetweenSetsSeconds = draft.restBetweenSetsSeconds.toIntOrNull() ?: exercise.restBetweenSetsSeconds,
                transitionRestSeconds = draft.transitionRestSeconds.toIntOrNull() ?: exercise.transitionRestSeconds,
            )
        }
        _uiState.value = _uiState.value.copy(isBusy = true, isSaving = true, messageRes = null)
        viewModelScope.launch {
            try {
                repository.updateWorkoutExercises(edits)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isSaving = false,
                    editingWorkoutCode = null,
                    drafts = emptyMap(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    isSaving = false,
                    messageRes = R.string.home_error,
                )
            }
        }
    }

    fun moveWorkoutUp(workout: WorkoutUi) {
        moveWorkout(workout, MoveWorkoutDirection.UP)
    }

    fun moveWorkoutDown(workout: WorkoutUi) {
        moveWorkout(workout, MoveWorkoutDirection.DOWN)
    }

    fun addWorkout(name: String) {
        runAction { repository.addWorkout(name) }
    }

    fun archiveWorkout(workoutId: String) {
        runAction { repository.archiveWorkout(workoutId) }
    }

    fun addExercise(
        workoutId: String,
        name: String,
        series: String,
        repetitions: String,
        rest: String,
        transition: String,
    ) {
        runAction {
            repository.addWorkoutExercise(
                NewWorkoutExerciseInput(
                    workoutId = workoutId,
                    name = name,
                    seriesCount = series.toIntOrNull() ?: 3,
                    repetitions = repetitions,
                    restBetweenSetsSeconds = rest.toIntOrNull() ?: 60,
                    transitionRestSeconds = transition.toIntOrNull() ?: 90,
                ),
            )
        }
    }

    fun deleteExercise(exerciseId: String) {
        runAction { repository.deleteWorkoutExercise(exerciseId) }
    }

    fun moveExercise(exerciseId: String, direction: Int) {
        runAction { repository.moveWorkoutExercise(exerciseId, direction) }
    }

    private fun moveWorkout(workout: WorkoutUi, direction: MoveWorkoutDirection) {
        runAction { repository.moveWorkout(workout.id, direction) }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }

    private fun runAction(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, messageRes = null)
        viewModelScope.launch {
            try {
                block()
                _uiState.value = _uiState.value.copy(isBusy = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    messageRes = R.string.home_error,
                )
            }
        }
    }

    private fun updateDraft(
        exerciseId: String,
        transform: ExerciseEditDraft.() -> ExerciseEditDraft,
    ) {
        val current = _uiState.value
        val draft = current.drafts[exerciseId] ?: return
        _uiState.value = current.copy(
            drafts = current.drafts + (exerciseId to draft.transform()),
        )
    }
}

private fun WorkoutExerciseUi.toDraft(): ExerciseEditDraft {
    return ExerciseEditDraft(
        exerciseId = id,
        name = name,
        repetitions = repetitions,
        seriesCount = seriesCount.toString(),
        restBetweenSetsSeconds = restBetweenSetsSeconds.toString(),
        transitionRestSeconds = transitionRestSeconds.toString(),
    )
}
