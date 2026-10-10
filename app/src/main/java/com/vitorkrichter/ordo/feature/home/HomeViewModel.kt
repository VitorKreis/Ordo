package com.vitorkrichter.ordo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.data.repository.DashboardSnapshot
import com.vitorkrichter.ordo.data.repository.HomeHistoryDayUi
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val hasPlan: Boolean = false,
    val planName: String = "",
    val isTodayPlanned: Boolean = false,
    val todayWorkoutCode: String? = null,
    val todayWorkoutName: String? = null,
    val nextWorkoutId: String? = null,
    val nextWorkoutCode: String? = null,
    val nextWorkoutName: String? = null,
    val nextWorkoutPosition: Int? = null,
    val workoutCount: Int = 0,
    val completedSessionCount: Int = 0,
    val partialSessionCount: Int = 0,
    val lastFiveDays: List<HomeHistoryDayUi> = emptyList(),
    val messageRes: Int? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: OrdoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDashboard()
                .catch {
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        messageRes = R.string.home_error,
                    )
                }
                .collect { snapshot ->
                    val current = _uiState.value
                    _uiState.value = snapshot.toUiState(
                        isBusy = current.isBusy,
                        messageRes = current.messageRes,
                    )
                }
        }
    }

    fun createSamplePlan() {
        runAction(successMessageRes = R.string.home_sample_created) {
            repository.createSamplePlanIfNeeded()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }

    private fun runAction(
        successMessageRes: Int,
        block: suspend () -> Unit,
    ) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, messageRes = null)
        viewModelScope.launch {
            try {
                block()
                _uiState.value = _uiState.value.copy(isBusy = false, messageRes = successMessageRes)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(isBusy = false, messageRes = R.string.home_error)
            }
        }
    }

    private fun DashboardSnapshot.toUiState(
        isBusy: Boolean,
        messageRes: Int?,
    ): HomeUiState = when (this) {
        DashboardSnapshot.Empty -> HomeUiState(
            isLoading = false,
            isBusy = isBusy,
            messageRes = messageRes,
        )

        is DashboardSnapshot.Ready -> HomeUiState(
            isLoading = false,
            isBusy = isBusy,
            hasPlan = true,
            planName = planName,
            isTodayPlanned = isTodayPlanned,
            todayWorkoutCode = todayWorkoutCode,
            todayWorkoutName = todayWorkoutName,
            nextWorkoutId = nextWorkoutId,
            nextWorkoutCode = nextWorkoutCode,
            nextWorkoutName = nextWorkoutName,
            nextWorkoutPosition = nextWorkoutPosition,
            workoutCount = workoutCount,
            completedSessionCount = completedSessionCount,
            partialSessionCount = partialSessionCount,
            lastFiveDays = lastFiveDays,
            messageRes = messageRes,
        )
    }
}
