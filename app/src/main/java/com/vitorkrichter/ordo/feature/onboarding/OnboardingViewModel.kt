package com.vitorkrichter.ordo.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoading: Boolean = true,
    val hasPlan: Boolean = false,
    val isBusy: Boolean = false,
    val messageRes: Int? = null,
    val createdPlan: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OrdoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHasActivePlan()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messageRes = R.string.onboarding_error,
                    )
                }
                .collect { hasPlan ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasPlan = hasPlan,
                )
            }
        }
    }

    fun createEditablePlan() {
        if (_uiState.value.isBusy) return
        val state = _uiState.value
        _uiState.value = state.copy(isBusy = true, messageRes = null)
        viewModelScope.launch {
            try {
                repository.createSamplePlanIfNeeded(emptySet())
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    createdPlan = true,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    messageRes = R.string.onboarding_error,
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }
}
