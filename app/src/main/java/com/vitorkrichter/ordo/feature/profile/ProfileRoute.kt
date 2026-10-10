package com.vitorkrichter.ordo.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoActionButton
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoIconBubble
import com.vitorkrichter.ordo.core.designsystem.component.OrdoInlineIcon
import com.vitorkrichter.ordo.core.designsystem.theme.AccentBlue
import com.vitorkrichter.ordo.core.designsystem.theme.AccentGreen
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.domain.rule.BmiCalculator
import com.vitorkrichter.ordo.domain.rule.BmiCategory
import com.vitorkrichter.ordo.domain.rule.BmiUnavailableReason
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        modifier = modifier,
        onSaveProfile = viewModel::saveProfile,
        onAddWeight = viewModel::addWeight,
        onUpdateWeight = viewModel::updateWeight,
        onDeleteWeight = viewModel::deleteWeight,
        onSetHydrationTarget = viewModel::setHydrationTarget,
        onMessageShown = viewModel::clearMessage,
    )
}

/** Stateless screen entry point kept separate so the data layer can be adapted later. */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    onSaveProfile: (displayName: String, birthDate: String, heightCm: String) -> Unit,
    onAddWeight: (date: String, time: String, weightKg: String) -> Unit,
    onUpdateWeight: (id: String, date: String, time: String, weightKg: String) -> Unit,
    onDeleteWeight: (id: String) -> Unit,
    onSetHydrationTarget: (target: String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageRes = state.messageRes
    if (messageRes != null) {
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    var profileDraftInitialized by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf("") }
    var heightCm by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.profile, state.isLoading) {
        if (!profileDraftInitialized && !state.isLoading) {
            displayName = state.profile.displayName.orEmpty()
            birthDate = state.profile.birthDate?.format(ProfileDateFormatter).orEmpty()
            heightCm = state.profile.heightCm?.formatProfileNumber().orEmpty()
            profileDraftInitialized = true
        }
    }

    val initialDate = remember { LocalDate.now(ProfileZoneId) }
    val initialTime = remember { LocalTime.now(ProfileZoneId) }
    var weightDate by rememberSaveable { mutableStateOf(initialDate.format(ProfileDateFormatter)) }
    var weightTime by rememberSaveable { mutableStateOf(initialTime.format(ProfileTimeFormatter)) }
    var weightKg by rememberSaveable { mutableStateOf("") }
    var editingWeightId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<WeightEntryUi?>(null) }

    var hydrationTarget by rememberSaveable { mutableStateOf("") }
    var hydrationInputEdited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.hydration, state.latestValidWeight) {
        if (!hydrationInputEdited) {
            hydrationTarget = state.hydrationTargetForDisplayMl.toString()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgApp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BgApp)
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.profile_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            when {
                state.isLoading -> item { LoadingProfileCard() }
                state.loadErrorRes != null -> item {
                    ErrorProfileCard(state.loadErrorRes)
                }
                else -> {
                    item {
                        BasicProfileCard(
                            state = state,
                            displayName = displayName,
                            birthDate = birthDate,
                            heightCm = heightCm,
                            isBusy = state.isBusy,
                            fieldErrors = state.fieldErrors,
                            onDisplayNameChange = { displayName = it },
                            onBirthDateChange = { birthDate = it },
                            onHeightChange = { heightCm = it },
                            onSave = { onSaveProfile(displayName, birthDate, heightCm) },
                        )
                    }
                    item {
                        WeightHistoryCard(
                            state = state,
                            dateInput = weightDate,
                            timeInput = weightTime,
                            weightInput = weightKg,
                            editingWeightId = editingWeightId,
                            isBusy = state.isBusy,
                            fieldErrors = state.fieldErrors,
                            onDateChange = { weightDate = it },
                            onTimeChange = { weightTime = it },
                            onWeightChange = { weightKg = it },
                            onSave = {
                                if (editingWeightId == null) {
                                    onAddWeight(weightDate, weightTime, weightKg)
                                } else {
                                    onUpdateWeight(editingWeightId!!, weightDate, weightTime, weightKg)
                                }
                            },
                            onEdit = { entry ->
                                editingWeightId = entry.id
                                val zonedDateTime = entry.measuredAt.atZone(ProfileZoneId)
                                weightDate = zonedDateTime.toLocalDate().format(ProfileDateFormatter)
                                weightTime = zonedDateTime.toLocalTime().format(ProfileTimeFormatter)
                                weightKg = entry.weightKg.formatProfileNumber()
                            },
                            onCancelEdit = {
                                editingWeightId = null
                                weightKg = ""
                            },
                            onRequestDelete = { deleteCandidate = it },
                        )
                    }
                    item {
                        HydrationCard(
                            state = state,
                            targetInput = hydrationTarget,
                            isBusy = state.isBusy,
                            fieldErrorRes = state.fieldErrors[ProfileField.HYDRATION_TARGET],
                            onTargetChange = {
                                hydrationTarget = it
                                hydrationInputEdited = true
                            },
                            onSave = {
                                hydrationInputEdited = false
                                onSetHydrationTarget(hydrationTarget)
                            },
                        )
                    }
                }
            }
        }
    }

    deleteCandidate?.let { entry ->
        val entryWeight = entry.weightKg.formatProfileNumber()
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.profile_weight_delete_title)) },
            text = { Text(stringResource(R.string.profile_weight_delete_body, entryWeight)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        if (editingWeightId == entry.id) {
                            editingWeightId = null
                            weightKg = ""
                        }
                        onDeleteWeight(entry.id)
                    },
                    enabled = !state.isBusy,
                ) {
                    Text(stringResource(R.string.profile_weight_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.profile_weight_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun LoadingProfileCard() {
    OrdoCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.height(24.dp),
                color = AccentBlue,
            )
            Text(
                text = stringResource(R.string.profile_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun ErrorProfileCard(errorRes: Int) {
    OrdoCard {
        OrdoIconBubble(icon = Icons.Rounded.Person, tint = AccentBlue)
        Text(
            text = stringResource(errorRes),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
    }
}

@Composable
private fun BasicProfileCard(
    state: ProfileUiState,
    displayName: String,
    birthDate: String,
    heightCm: String,
    isBusy: Boolean,
    fieldErrors: Map<ProfileField, Int>,
    onDisplayNameChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    OrdoCard {
        SectionHeading(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.profile_basic_title),
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_name_label)) },
            placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        ProfileTextField(
            value = birthDate,
            onValueChange = onBirthDateChange,
            label = stringResource(R.string.profile_birth_date_label),
            placeholder = stringResource(R.string.profile_birth_date_placeholder),
            errorRes = fieldErrors[ProfileField.BIRTH_DATE],
            keyboardType = KeyboardType.Number,
        )
        ProfileTextField(
            value = heightCm,
            onValueChange = onHeightChange,
            label = stringResource(R.string.profile_height_label),
            placeholder = stringResource(R.string.profile_height_placeholder),
            errorRes = fieldErrors[ProfileField.HEIGHT],
            keyboardType = KeyboardType.Decimal,
        )
        OrdoActionButton(
            text = stringResource(R.string.profile_save_basic),
            icon = Icons.Rounded.Save,
            onClick = onSave,
            enabled = !isBusy,
        )
        BmiReference(state)
    }
}

@Composable
private fun BmiReference(state: ProfileUiState) {
    val result = BmiCalculator.calculate(
        weightKg = state.latestValidWeight?.weightKg,
        heightCm = state.profile.heightCm,
        dateOfBirth = state.profile.birthDate,
        onDate = LocalDate.now(ProfileZoneId),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.profile_bmi_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        when {
            result.isAvailable -> {
                Text(
                    text = stringResource(
                        R.string.profile_bmi_value,
                        result.bmi!!.formatProfileNumber(),
                        bmiCategoryLabel(result.category!!),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AccentGreen,
                )
            }
            result.unavailableReason == BmiUnavailableReason.UNDER_20 -> Text(
                text = stringResource(R.string.profile_bmi_under_20),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            result.unavailableReason == BmiUnavailableReason.INVALID_DATA -> Text(
                text = stringResource(R.string.profile_bmi_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            else -> Text(
                text = stringResource(R.string.profile_bmi_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Text(
            text = stringResource(R.string.profile_bmi_note),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun WeightHistoryCard(
    state: ProfileUiState,
    dateInput: String,
    timeInput: String,
    weightInput: String,
    editingWeightId: String?,
    isBusy: Boolean,
    fieldErrors: Map<ProfileField, Int>,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onEdit: (WeightEntryUi) -> Unit,
    onCancelEdit: () -> Unit,
    onRequestDelete: (WeightEntryUi) -> Unit,
) {
    OrdoCard {
        SectionHeading(
            icon = Icons.Rounded.Scale,
            title = stringResource(R.string.profile_weight_title),
        )
        state.latestValidWeight?.let { latest ->
            Text(
                text = stringResource(
                    R.string.profile_latest_weight,
                    latest.weightKg.formatProfileNumber(),
                ),
                color = TextSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ProfileTextField(
                value = dateInput,
                onValueChange = onDateChange,
                label = stringResource(R.string.profile_weight_date_label),
                placeholder = stringResource(R.string.profile_birth_date_placeholder),
                errorRes = fieldErrors[ProfileField.WEIGHT_DATE],
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1.3f),
            )
            ProfileTextField(
                value = timeInput,
                onValueChange = onTimeChange,
                label = stringResource(R.string.profile_weight_time_label),
                placeholder = "hh:mm",
                errorRes = fieldErrors[ProfileField.WEIGHT_TIME],
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        ProfileTextField(
            value = weightInput,
            onValueChange = onWeightChange,
            label = stringResource(R.string.profile_weight_value_label),
            placeholder = "Ex.: 72,5",
            errorRes = fieldErrors[ProfileField.WEIGHT],
            keyboardType = KeyboardType.Decimal,
        )
        OrdoActionButton(
            text = stringResource(
                if (editingWeightId == null) {
                    R.string.profile_weight_add
                } else {
                    R.string.profile_weight_update
                },
            ),
            icon = if (editingWeightId == null) Icons.Rounded.AddCircle else Icons.Rounded.Save,
            onClick = onSave,
            enabled = !isBusy,
        )
        if (editingWeightId != null) {
            TextButton(onClick = onCancelEdit, enabled = !isBusy) {
                Text(stringResource(R.string.profile_weight_cancel))
            }
        }
        if (state.weightEntries.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_weight_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else {
            state.weightEntries
                .sortedByDescending { it.measuredAt }
                .forEach { entry ->
                    WeightEntryRow(
                        entry = entry,
                        onEdit = { onEdit(entry) },
                        onDelete = { onRequestDelete(entry) },
                    )
                }
        }
    }
}

@Composable
private fun WeightEntryRow(
    entry: WeightEntryUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateTime = entry.measuredAt.atZone(ProfileZoneId)
    val date = dateTime.toLocalDate().format(ProfileDateFormatter)
    val time = dateTime.toLocalTime().format(ProfileTimeFormatter)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(
                R.string.profile_weight_entry,
                "$date $time",
                entry.weightKg.formatProfileNumber(),
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(
                    R.string.profile_accessibility_edit,
                    stringResource(R.string.profile_weight_title),
                ),
                tint = TextPrimary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(
                    R.string.profile_accessibility_delete,
                    stringResource(R.string.profile_weight_title),
                ),
                tint = TextPrimary,
            )
        }
    }
}

@Composable
private fun HydrationCard(
    state: ProfileUiState,
    targetInput: String,
    isBusy: Boolean,
    fieldErrorRes: Int?,
    onTargetChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val targetMl = state.hydrationTargetForDisplayMl
    val hasCustomTarget = state.hydration.source == HydrationTargetSource.CUSTOM &&
        state.hydration.targetMl > 0
    val sourceText = when {
        hasCustomTarget -> stringResource(R.string.profile_hydration_custom)
        state.latestValidWeight != null -> stringResource(
            R.string.profile_hydration_automatic,
            targetMl,
        )
        else -> stringResource(R.string.profile_hydration_default, targetMl)
    }
    OrdoCard {
        SectionHeading(
            icon = Icons.Rounded.WaterDrop,
            title = stringResource(R.string.profile_hydration_title),
        )
        Text(
            text = stringResource(
                R.string.profile_hydration_current,
                targetMl.toLitersLabel(),
                targetMl,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = AccentBlue,
        )
        Text(
            text = sourceText,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        ProfileTextField(
            value = targetInput,
            onValueChange = onTargetChange,
            label = stringResource(R.string.profile_hydration_target_label),
            placeholder = stringResource(R.string.profile_hydration_target_placeholder),
            errorRes = fieldErrorRes,
            keyboardType = KeyboardType.Decimal,
        )
        OrdoActionButton(
            text = stringResource(R.string.profile_hydration_save),
            icon = Icons.Rounded.Save,
            onClick = onSave,
            enabled = !isBusy,
        )
        Text(
            text = stringResource(R.string.profile_hydration_note),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OrdoInlineIcon(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    errorRes: Int?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val errorText = errorRes?.let { stringResource(it) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { error -> { Text(error) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun bmiCategoryLabel(category: BmiCategory): String = when (category) {
    BmiCategory.UNDERWEIGHT -> stringResource(R.string.profile_bmi_underweight)
    BmiCategory.HEALTHY -> stringResource(R.string.profile_bmi_healthy)
    BmiCategory.OVERWEIGHT -> stringResource(R.string.profile_bmi_overweight)
    BmiCategory.OBESITY_CLASS_I -> stringResource(R.string.profile_bmi_obesity_i)
    BmiCategory.OBESITY_CLASS_II -> stringResource(R.string.profile_bmi_obesity_ii)
    BmiCategory.OBESITY_CLASS_III -> stringResource(R.string.profile_bmi_obesity_iii)
}

private fun Double.formatProfileNumber(): String = if (this % 1.0 == 0.0) {
    toInt().toString()
} else {
    String.format(Locale.forLanguageTag("pt-BR"), "%.1f", this)
}

private fun Int.toLitersLabel(): String = String.format(
    Locale.forLanguageTag("pt-BR"),
    "%.2f",
    this / 1_000.0,
)

private val ProfileZoneId: ZoneId = ZoneId.of("America/Sao_Paulo")
private val ProfileDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
private val ProfileTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
