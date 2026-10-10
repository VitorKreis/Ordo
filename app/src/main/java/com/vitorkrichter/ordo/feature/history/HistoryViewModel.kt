package com.vitorkrichter.ordo.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.data.repository.HistorySessionUi
import com.vitorkrichter.ordo.data.repository.HistorySnapshot
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val messageRes: Int? = null,
    val hasPlan: Boolean = false,
    val planName: String = "",
    val sessions: List<HistorySessionUi> = emptyList(),
    val exportText: String = "",
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: OrdoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHistory()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isBusy = false,
                        messageRes = R.string.home_error,
                    )
                }
                .collect { snapshot ->
                val current = _uiState.value
                _uiState.value = when (snapshot) {
                    HistorySnapshot.Empty -> HistoryUiState(
                        isLoading = false,
                        isBusy = current.isBusy,
                        messageRes = current.messageRes,
                    )
                    is HistorySnapshot.Ready -> HistoryUiState(
                        isLoading = false,
                        isBusy = current.isBusy,
                        messageRes = current.messageRes,
                        hasPlan = true,
                        planName = snapshot.planName,
                        sessions = snapshot.sessions,
                        exportText = buildHistoryExport(snapshot.planName, snapshot.sessions),
                    )
                }
            }
        }
    }

    fun updateSetPerformance(setId: String, repetitions: String, loadKg: String) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, messageRes = null)
        viewModelScope.launch {
            try {
                repository.updateHistoricalSetPerformance(
                    setId = setId,
                    repetitions = repetitions.toIntOrNull(),
                    loadKg = loadKg.replace(',', '.').toDoubleOrNull(),
                )
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

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }
}

private fun buildHistoryExport(planName: String, sessions: List<HistorySessionUi>): String = buildString {
    appendLine("# ORDO - HISTORICO DE TREINOS")
    appendLine("PLANO=$planName")
    appendLine()
    sessions.filter { it.status.name == "COMPLETED" || it.status.name == "PARTIALLY_COMPLETED" }
        .forEach { session ->
            val localStart = session.startedAt.atZone(ExportZone)
            appendLine("[${localStart.format(ExportDateFormatter)}] ${listOfNotNull(session.workoutCode, session.workoutName).joinToString(" - ")}")
            appendLine("INICIO=${localStart.format(ExportTimeFormatter)}")
            session.finishedAt?.let { finishedAt ->
                appendLine("FIM=${finishedAt.atZone(ExportZone).format(ExportTimeFormatter)}")
                appendLine("DURACAO=${formatExportDuration(Duration.between(session.startedAt, finishedAt))}")
            }
            session.exercises.forEach { exercise ->
                val trainedSets = exercise.sets.filter { it.status.name == "COMPLETED" }
                if (trainedSets.isNotEmpty()) {
                    appendLine("EXERCICIO=${exercise.name}")
                    trainedSets.forEach { set ->
                        val reps = set.actualRepetitions?.toString() ?: "-"
                        val load = set.actualLoadKg?.let(::formatExportLoad) ?: "-"
                        appendLine("SERIE_${set.position + 1}=REPS:$reps;PESO_KG:$load")
                    }
                }
            }
            appendLine("-------------------------------")
            appendLine()
        }
}

private fun formatExportDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    return "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)
}

private fun formatExportLoad(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    String.format(Locale.US, "%.1f", value)
}

private val ExportZone = ZoneId.of("America/Sao_Paulo")
private val ExportDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val ExportTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
