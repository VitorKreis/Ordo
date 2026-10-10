package com.vitorkrichter.ordo.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.data.repository.OrdoRepository
import com.vitorkrichter.ordo.domain.rule.HydrationReferencePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import kotlin.math.roundToInt
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class ProfileField {
    BIRTH_DATE,
    HEIGHT,
    WEIGHT_DATE,
    WEIGHT_TIME,
    WEIGHT,
    HYDRATION_TARGET,
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val loadErrorRes: Int? = null,
    val messageRes: Int? = null,
    val fieldErrors: Map<ProfileField, Int> = emptyMap(),
    val profile: ProfileSnapshot = ProfileSnapshot(),
    val weightEntries: List<WeightEntryUi> = emptyList(),
    val hydration: HydrationUi = HydrationUi(),
) {
    val latestValidWeight: WeightEntryUi?
        get() = weightEntries
            .asSequence()
            .filter { it.weightKg.isFinite() && it.weightKg > 0.0 }
            .maxByOrNull { it.measuredAt }

    /**
     * Manual targets are authoritative. Otherwise the visible reference is
     * derived from the current valid weight, so a new weight is reflected in
     * the UI without replacing a custom reminder.
     */
    val hydrationTargetForDisplayMl: Int
        get() = if (
            hydration.source == HydrationTargetSource.CUSTOM && hydration.targetMl > 0
        ) {
            hydration.targetMl
        } else {
            HydrationReferencePolicy.automaticReference(latestValidWeight?.weightKg).targetMl
        }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: OrdoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(
                    repository.observeProfile(),
                    repository.observeWeightEntries(),
                    repository.observeHydration(),
                ) { profile, weights, hydration ->
                    ProfileData(profile, weights, hydration)
                }.catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadErrorRes = R.string.profile_load_error,
                    )
                }.collect { data ->
                    val current = _uiState.value
                    _uiState.value = ProfileUiState(
                        isLoading = false,
                        isBusy = current.isBusy,
                        messageRes = current.messageRes,
                        fieldErrors = current.fieldErrors,
                        profile = data.profile,
                        weightEntries = data.weights,
                        hydration = data.hydration,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadErrorRes = R.string.profile_load_error,
                )
            }
        }
    }

    fun saveProfile(
        displayNameInput: String,
        birthDateInput: String,
        heightInput: String,
    ) {
        val errors = linkedMapOf<ProfileField, Int>()
        val birthDate = parseOptionalDate(birthDateInput)
        if (birthDateInput.isNotBlank() && birthDate == null) {
            errors[ProfileField.BIRTH_DATE] = R.string.profile_error_birth_date
        }
        val heightCm = parseOptionalDecimal(heightInput)
        if (heightInput.isNotBlank() &&
            (heightCm == null || heightCm !in MIN_HEIGHT_CM..MAX_HEIGHT_CM)
        ) {
            errors[ProfileField.HEIGHT] = R.string.profile_error_height
        }
        if (birthDate != null && birthDate.isAfter(today())) {
            errors[ProfileField.BIRTH_DATE] = R.string.profile_error_birth_date_future
        }
        if (errors.isNotEmpty()) {
            showValidationErrors(errors)
            return
        }

        runAction(R.string.profile_saved) {
            repository.saveProfile(
                displayName = displayNameInput.trim().takeIf { it.isNotEmpty() },
                birthDate = birthDate,
                heightCm = heightCm,
            )
        }
    }

    fun addWeight(dateInput: String, timeInput: String, weightInput: String) {
        val parsed = parseWeight(dateInput, timeInput, weightInput) ?: return
        runAction(R.string.profile_weight_saved) {
            repository.addWeight(parsed.measuredAt, parsed.weightKg)
        }
    }

    fun updateWeight(id: String, dateInput: String, timeInput: String, weightInput: String) {
        val parsed = parseWeight(dateInput, timeInput, weightInput) ?: return
        runAction(R.string.profile_weight_updated) {
            repository.updateWeight(id, parsed.measuredAt, parsed.weightKg)
        }
    }

    fun deleteWeight(id: String) {
        if (id.isBlank()) return
        runAction(R.string.profile_weight_deleted) {
            repository.deleteWeight(id)
        }
    }

    fun setHydrationTarget(targetInput: String) {
        val targetMl = parseHydrationTarget(targetInput)
        if (targetMl == null || targetMl !in MIN_HYDRATION_ML..MAX_HYDRATION_ML) {
            showValidationErrors(
                mapOf(ProfileField.HYDRATION_TARGET to R.string.profile_error_hydration_target),
            )
            return
        }
        runAction(R.string.profile_hydration_saved) {
            repository.setHydrationTarget(targetMl)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null)
    }

    private fun parseWeight(
        dateInput: String,
        timeInput: String,
        weightInput: String,
    ): ParsedWeight? {
        val errors = linkedMapOf<ProfileField, Int>()
        val date = parseOptionalDate(dateInput)
        if (date == null) errors[ProfileField.WEIGHT_DATE] = R.string.profile_error_weight_date
        val time = parseOptionalTime(timeInput)
        if (time == null) errors[ProfileField.WEIGHT_TIME] = R.string.profile_error_weight_time
        val weightKg = parseOptionalDecimal(weightInput)
        if (weightKg == null || weightKg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG) {
            errors[ProfileField.WEIGHT] = R.string.profile_error_weight
        }
        if (errors.isNotEmpty()) {
            showValidationErrors(errors)
            return null
        }

        val measuredAt = date!!
            .atTime(time!!)
            .atZone(ProfileZoneId)
            .toInstant()
        if (measuredAt.isAfter(Instant.now())) {
            showValidationErrors(
                mapOf(ProfileField.WEIGHT_DATE to R.string.profile_error_weight_future),
            )
            return null
        }
        return ParsedWeight(measuredAt, weightKg!!)
    }

    private fun showValidationErrors(errors: Map<ProfileField, Int>) {
        _uiState.value = _uiState.value.copy(
            isBusy = false,
            fieldErrors = errors,
            messageRes = errors.values.firstOrNull(),
        )
    }

    private fun runAction(successMessageRes: Int, block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(
            isBusy = true,
            messageRes = null,
            fieldErrors = emptyMap(),
        )
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
                    messageRes = R.string.profile_action_error,
                )
            }
        }
    }

    private fun parseOptionalDate(value: String): LocalDate? {
        if (value.isBlank()) return null
        return try {
            LocalDate.parse(value.trim(), ProfileDateFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseOptionalTime(value: String): LocalTime? {
        if (value.isBlank()) return null
        return try {
            LocalTime.parse(value.trim(), ProfileTimeFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseOptionalDecimal(value: String): Double? {
        if (value.isBlank()) return null
        return value.trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf(Double::isFinite)
    }

    private fun parseHydrationTarget(value: String): Int? {
        val normalized = value.trim().lowercase().replace(',', '.')
        if (normalized.isBlank()) return null
        return try {
            when {
                normalized.endsWith("ml") -> normalized
                    .removeSuffix("ml")
                    .trim()
                    .toDouble()
                    .roundToInt()
                normalized.endsWith("l") -> normalized
                    .removeSuffix("l")
                    .trim()
                    .toDouble()
                    .times(1_000.0)
                    .roundToInt()
                else -> normalized.toDouble().roundToInt()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun today(): LocalDate = LocalDate.now(ProfileZoneId)

    private data class ProfileData(
        val profile: ProfileSnapshot,
        val weights: List<WeightEntryUi>,
        val hydration: HydrationUi,
    )

    private data class ParsedWeight(
        val measuredAt: Instant,
        val weightKg: Double,
    )

    private companion object {
        const val MIN_HEIGHT_CM = 50.0
        const val MAX_HEIGHT_CM = 300.0
        const val MIN_WEIGHT_KG = 0.1
        const val MAX_WEIGHT_KG = 500.0
        const val MIN_HYDRATION_ML = 100
        const val MAX_HYDRATION_ML = 20_000

        val ProfileZoneId: ZoneId = ZoneId.of("America/Sao_Paulo")
        val ProfileDateFormatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
        val ProfileTimeFormatter: DateTimeFormatter = DateTimeFormatter
            .ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT)
    }
}
