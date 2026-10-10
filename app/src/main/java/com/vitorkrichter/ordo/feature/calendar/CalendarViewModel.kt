package com.vitorkrichter.ordo.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.time.AppTime
import com.vitorkrichter.ordo.data.repository.CalendarDayUi
import com.vitorkrichter.ordo.data.repository.CalendarSnapshot
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import com.vitorkrichter.ordo.data.repository.ScheduleDayUi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class CalendarUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val messageRes: Int? = null,
    val hasPlan: Boolean = false,
    val planName: String = "",
    val scheduleDays: List<ScheduleDayUi> = emptyList(),
    val days: List<CalendarDayUi> = emptyList(),
    val visibleMonth: YearMonth = YearMonth.now(AppTime.clock),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: OrdoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val visibleMonth = MutableStateFlow(YearMonth.now(AppTime.clock))

    init {
        viewModelScope.launch {
            visibleMonth.flatMapLatest { month ->
                repository.observeCalendar(
                    startDate = month.atDay(1),
                    days = month.lengthOfMonth().toLong(),
                )
            }.catch {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isBusy = false,
                    messageRes = R.string.home_error,
                )
            }.collect { snapshot ->
                val month = visibleMonth.value
                val current = _uiState.value
                _uiState.value = when (snapshot) {
                    CalendarSnapshot.Empty -> CalendarUiState(
                        isLoading = false,
                        isBusy = current.isBusy,
                        messageRes = current.messageRes,
                        visibleMonth = month,
                    )
                    is CalendarSnapshot.Ready -> CalendarUiState(
                        isLoading = false,
                        isBusy = current.isBusy,
                        messageRes = current.messageRes,
                        hasPlan = true,
                        planName = snapshot.planName,
                        scheduleDays = snapshot.scheduleDays,
                        days = snapshot.days,
                        visibleMonth = month,
                    )
                }
            }
        }
    }

    fun previousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun setDateSkipped(date: LocalDate, skipped: Boolean) {
        runAction { repository.setCalendarDateSkipped(date, skipped) }
    }

    fun onTrainingDayToggle(weekday: DayOfWeek, isEnabled: Boolean) {
        runAction {
            repository.setTrainingDayEnabled(weekday = weekday, isEnabled = isEnabled)
        }
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
}
