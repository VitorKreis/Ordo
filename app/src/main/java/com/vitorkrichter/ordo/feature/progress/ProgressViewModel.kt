package com.vitorkrichter.ordo.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import com.vitorkrichter.ordo.data.repository.ProgressExerciseUi
import com.vitorkrichter.ordo.data.repository.ProgressSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ProgressUiState(
    val isLoading: Boolean = true,
    val messageRes: Int? = null,
    val hasPlan: Boolean = false,
    val planName: String = "",
    val exercises: List<ProgressExerciseUi> = emptyList(),
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    repository: OrdoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProgress()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messageRes = R.string.home_error,
                    )
                }
                .collect { snapshot ->
                _uiState.value = when (snapshot) {
                    ProgressSnapshot.Empty -> ProgressUiState(isLoading = false)
                    is ProgressSnapshot.Ready -> ProgressUiState(
                        isLoading = false,
                        hasPlan = true,
                        planName = snapshot.planName,
                        exercises = snapshot.exercises,
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }
}
