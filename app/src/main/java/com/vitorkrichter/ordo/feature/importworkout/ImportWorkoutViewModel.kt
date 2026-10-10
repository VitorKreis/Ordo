package com.vitorkrichter.ordo.feature.importworkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.data.importworkout.GymAppImportResult
import com.vitorkrichter.ordo.data.backup.OrdoBackupManager
import com.vitorkrichter.ordo.data.repository.AvailablePlanUi
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ImportWorkoutUiState(
    val text: String = "",
    val isBusy: Boolean = false,
    val isPlansLoading: Boolean = true,
    val result: GymAppImportResult? = null,
    val messageRes: Int? = null,
    val imported: Boolean = false,
    val preferredWeekdays: Set<DayOfWeek>? = null,
    val planName: String = "",
    val availablePlans: List<AvailablePlanUi> = emptyList(),
    val activatingPlanId: String? = null,
)

@HiltViewModel
class ImportWorkoutViewModel @Inject constructor(
    private val repository: OrdoRepository,
    private val backupManager: OrdoBackupManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportWorkoutUiState())
    val uiState: StateFlow<ImportWorkoutUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAvailablePlans()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isPlansLoading = false,
                        messageRes = R.string.import_error,
                    )
                }
                .collect { plans ->
                    _uiState.value = _uiState.value.copy(
                        isPlansLoading = false,
                        availablePlans = plans,
                    )
                }
        }
    }

    fun onPlanNameChange(value: String) {
        _uiState.value = _uiState.value.copy(
            planName = value.take(60),
            messageRes = null,
        )
    }

    fun onTextChange(value: String) {
        _uiState.value = _uiState.value.copy(
            text = value,
            result = null,
            messageRes = null,
            imported = false,
        )
    }

    fun useTemplate() {
        onTextChange(IMPORT_TEMPLATE)
    }

    fun setPreferredWeekdays(weekdays: Set<DayOfWeek>?) {
        if (weekdays == null) return
        val current = _uiState.value
        if (current.preferredWeekdays == null) {
            _uiState.value = current.copy(preferredWeekdays = weekdays)
        }
    }

    fun review() {
        val text = _uiState.value.text
        if (_uiState.value.isBusy) return
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(messageRes = R.string.import_empty_text)
            return
        }
        _uiState.value = _uiState.value.copy(isBusy = true, messageRes = null)
        viewModelScope.launch {
            try {
                val result = repository.parseImportText(text)
                val importedName = result.planName.orEmpty()
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    result = result,
                    planName = importedName.ifBlank { _uiState.value.planName },
                    messageRes = if (result.canSave) R.string.import_review_ready else R.string.import_review_has_errors,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    messageRes = R.string.import_error,
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val text = state.text
        if (state.isBusy) return
        if (state.planName.isBlank()) {
            _uiState.value = state.copy(messageRes = R.string.import_plan_name_required)
            return
        }
        _uiState.value = state.copy(isBusy = true, messageRes = null)
        viewModelScope.launch {
            try {
                val result = repository.importGymAppTextAsActivePlan(
                    text = text,
                    planName = state.planName,
                    enabledWeekdays = _uiState.value.preferredWeekdays,
                )
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    result = result,
                    imported = result.canSave,
                    messageRes = if (result.canSave) R.string.import_saved else R.string.import_review_has_errors,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    messageRes = R.string.import_error,
                )
            }
        }
    }

    fun activatePlan(planId: String) {
        if (_uiState.value.isBusy || _uiState.value.activatingPlanId != null) return
        _uiState.value = _uiState.value.copy(isBusy = true, activatingPlanId = planId, messageRes = null)
        viewModelScope.launch {
            try {
                repository.activatePlan(planId)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    activatingPlanId = null,
                    messageRes = R.string.import_plan_activated,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    activatingPlanId = null,
                    messageRes = R.string.import_error,
                )
            }
        }
    }

    fun renamePlan(planId: String, name: String) {
        if (name.isBlank()) return
        runBusyAction(R.string.import_plan_renamed) { repository.renamePlan(planId, name) }
    }

    fun duplicatePlan(planId: String) {
        runBusyAction(R.string.import_plan_duplicated) { repository.duplicatePlan(planId) }
    }

    fun archivePlan(planId: String) {
        runBusyAction(R.string.import_plan_archived) { repository.archivePlan(planId) }
    }

    fun exportBackup(uri: Uri) {
        runBusyAction(R.string.backup_exported, R.string.backup_error) { backupManager.exportTo(uri) }
    }

    fun restoreBackup(uri: Uri) {
        runBusyAction(null, R.string.backup_invalid) { backupManager.restoreFrom(uri) }
    }

    fun exportActivePlan(uri: Uri) {
        runBusyAction(R.string.import_plan_exported) {
                backupManager.writeText(uri, repository.exportActivePlanText())
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }

    fun showError() {
        _uiState.value = _uiState.value.copy(messageRes = R.string.import_error)
    }

    private fun runBusyAction(
        successMessageRes: Int?,
        failureMessageRes: Int = R.string.import_error,
        block: suspend () -> Unit,
    ) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, messageRes = null)
        viewModelScope.launch {
            try {
                block()
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    messageRes = successMessageRes,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    messageRes = failureMessageRes,
                )
            }
        }
    }
}

private val IMPORT_TEMPLATE = """
# GYMAPP_IMPORT_V1

NOME_PLANO=Hipertrofia 4 dias
DIAS_TREINO=SEGUNDA,TERCA,QUINTA,SEXTA

[TREINO A]
NOME=Peito, ombro e triceps

EXERCICIO=Supino reto com barra
SERIES=4
REPETICOES=8-10
DESCANSO_SERIES=90s
DESCANSO_PROXIMO=120s
ACOMPANHAMENTO=CARGA_REPETICOES
CARGA_SUGERIDA=40
OBSERVACAO=Amplitude controlada
-------------------------------

EXERCICIO=Desenvolvimento com halteres
SERIES=3
REPETICOES=10
DESCANSO_SERIES=90s
DESCANSO_PROXIMO=0s
ACOMPANHAMENTO=CARGA_REPETICOES
-------------------------------

[TREINO B]
NOME=Costas e biceps

EXERCICIO=Puxada frontal
SERIES=4
REPETICOES=8-10
DESCANSO_SERIES=90s
DESCANSO_PROXIMO=120s
ACOMPANHAMENTO=CARGA_REPETICOES
-------------------------------
""".trimIndent()
