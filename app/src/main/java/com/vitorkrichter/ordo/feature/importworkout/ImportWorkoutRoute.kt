package com.vitorkrichter.ordo.feature.importworkout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoActionButton
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoEmptyState
import com.vitorkrichter.ordo.core.designsystem.component.OrdoMetricStrip
import com.vitorkrichter.ordo.core.designsystem.component.OrdoStatusBadge
import com.vitorkrichter.ordo.core.designsystem.component.StatusTone
import com.vitorkrichter.ordo.core.designsystem.theme.AccentBlue
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.BgInput
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.data.importworkout.GymAppImportResult
import com.vitorkrichter.ordo.data.importworkout.ImportedExercise
import com.vitorkrichter.ordo.data.repository.AvailablePlanUi
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun ImportWorkoutRoute(
    preferredWeekdays: Set<DayOfWeek>? = null,
    viewModel: ImportWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val messageRes = state.messageRes
    var renamingPlan by remember { mutableStateOf<AvailablePlanUi?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var archivingPlan by remember { mutableStateOf<AvailablePlanUi?>(null) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restoreBackup) }
    val planExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let(viewModel::exportActivePlan) }
    val textImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val importedText = runCatching {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
            }.getOrNull()
            if (importedText != null) {
                viewModel.onTextChange(importedText)
            } else {
                viewModel.showError()
            }
        }
    }

    LaunchedEffect(preferredWeekdays) {
        viewModel.setPreferredWeekdays(preferredWeekdays)
    }

    if (messageRes != null) {
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgApp,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgApp)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.import_title),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
            )

            if (state.isPlansLoading) {
                OrdoCard {
                    Text(
                        text = stringResource(R.string.home_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            } else if (state.availablePlans.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.import_my_plans),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                state.availablePlans.forEach { plan ->
                    AvailablePlanCard(
                        plan = plan,
                        isBusy = state.isBusy,
                        isActivating = state.activatingPlanId == plan.id,
                        onActivate = { viewModel.activatePlan(plan.id) },
                        onRename = {
                            renamingPlan = plan
                            renameValue = plan.name
                        },
                        onDuplicate = { viewModel.duplicatePlan(plan.id) },
                        onArchive = { archivingPlan = plan },
                    )
                }
            }

            OrdoCard {
                Text(
                    text = stringResource(R.string.backup_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(R.string.backup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                OrdoActionButton(
                    text = stringResource(R.string.import_export_active_plan),
                    icon = Icons.Rounded.Download,
                    onClick = { planExportLauncher.launch("ordo-plano.txt") },
                    enabled = state.availablePlans.any { it.isActive } && !state.isBusy,
                    secondary = true,
                )
                OrdoActionButton(
                    text = stringResource(R.string.backup_export),
                    icon = Icons.Rounded.Download,
                    onClick = {
                        backupExportLauncher.launch("ordo-backup-${LocalDate.now()}.ordo")
                    },
                    enabled = !state.isBusy,
                    secondary = true,
                )
                OrdoActionButton(
                    text = stringResource(R.string.backup_restore),
                    icon = Icons.Rounded.Restore,
                    onClick = { backupRestoreLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.isBusy,
                    secondary = true,
                )
            }

            if (state.preferredWeekdays != null) {
                OrdoCard {
                    OrdoStatusBadge(
                        label = stringResource(R.string.import_onboarding_days_label),
                        tone = StatusTone.Info,
                    )
                    Text(
                        text = stringResource(R.string.import_onboarding_days_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            OrdoCard {
                Text(
                    text = stringResource(R.string.import_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                OrdoActionButton(
                    text = stringResource(R.string.import_choose_file),
                    icon = Icons.Rounded.UploadFile,
                    onClick = { textImportLauncher.launch(arrayOf("text/plain", "text/*")) },
                    enabled = !state.isBusy,
                    secondary = true,
                )
                OutlinedTextField(
                    value = state.planName,
                    onValueChange = viewModel::onPlanNameChange,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.import_plan_name)) },
                    singleLine = true,
                    colors = importFieldColors(),
                )
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::onTextChange,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.xxxl * 6),
                    placeholder = { Text(stringResource(R.string.import_placeholder)) },
                    minLines = 10,
                    colors = importFieldColors(),
                )
                OrdoActionButton(
                    text = stringResource(R.string.import_review),
                    icon = Icons.Rounded.ContentPaste,
                    onClick = viewModel::review,
                    enabled = !state.isBusy,
                )
            }

            state.result?.let { result ->
                ImportReviewCard(
                    result = result,
                    imported = state.imported,
                    isBusy = state.isBusy,
                    hasPlanName = state.planName.isNotBlank(),
                    onSave = viewModel::save,
                )
            }

            OrdoCard {
                OrdoStatusBadge(
                    label = stringResource(R.string.import_template_label),
                    tone = StatusTone.Info,
                )
                Text(
                    text = stringResource(R.string.import_template_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                SelectionContainer {
                    Text(
                        text = stringResource(R.string.import_template_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
                OrdoActionButton(
                    text = stringResource(R.string.import_use_template),
                    icon = Icons.Rounded.Description,
                    onClick = viewModel::useTemplate,
                    enabled = !state.isBusy,
                    secondary = true,
                )
            }

            if (state.result == null) {
                OrdoEmptyState(
                    icon = Icons.Rounded.UploadFile,
                    title = stringResource(R.string.import_waiting_title),
                    subtitle = stringResource(R.string.import_waiting_body),
                )
            }
        }
    }

    renamingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { renamingPlan = null },
            title = { Text(stringResource(R.string.import_rename_plan)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.import_plan_name)) },
                    singleLine = true,
                    colors = importFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renamePlan(plan.id, renameValue)
                        renamingPlan = null
                    },
                    enabled = renameValue.isNotBlank() && !state.isBusy,
                ) { Text(stringResource(R.string.import_rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renamingPlan = null }) {
                    Text(stringResource(R.string.import_rename_cancel))
                }
            },
            containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
        )
    }
    archivingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { archivingPlan = null },
            title = { Text(stringResource(R.string.import_archive_plan)) },
            text = { Text(stringResource(R.string.import_archive_plan_body, plan.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.archivePlan(plan.id)
                        archivingPlan = null
                    },
                    enabled = !state.isBusy,
                ) { Text(stringResource(R.string.import_archive_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { archivingPlan = null }) {
                    Text(stringResource(R.string.import_rename_cancel))
                }
            },
            containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
        )
    }
}

@Composable
private fun AvailablePlanCard(
    plan: AvailablePlanUi,
    isBusy: Boolean,
    isActivating: Boolean,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
) {
    OrdoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(plan.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    text = stringResource(R.string.import_plan_workout_count, plan.workoutCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                if (plan.workoutLabels.isNotEmpty()) {
                    Text(
                        text = plan.workoutLabels.joinToString(" | "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.import_plan_days,
                        plan.trainingWeekdays.sortedBy { it.value }.joinToString(", ") { weekdayLabel(it) },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = onRename, enabled = !isBusy) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.import_rename_plan),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = onDuplicate, enabled = !isBusy) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.import_duplicate_plan),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            IconButton(onClick = onArchive, enabled = !plan.isActive && !isBusy) {
                Icon(
                    imageVector = Icons.Rounded.Archive,
                    contentDescription = stringResource(R.string.import_archive_plan),
                    tint = if (plan.isActive) TextSecondary else MaterialTheme.colorScheme.error,
                )
            }
        }
        if (plan.isActive) {
            OrdoStatusBadge(label = stringResource(R.string.import_plan_active), tone = StatusTone.Good)
        } else {
            Button(onClick = onActivate, enabled = !isActivating && !isBusy) {
                Text(stringResource(R.string.import_activate_plan))
            }
        }
    }
}

@Composable
private fun ImportReviewCard(
    result: GymAppImportResult,
    imported: Boolean,
    isBusy: Boolean,
    hasPlanName: Boolean,
    onSave: () -> Unit,
) {
    OrdoCard {
        OrdoStatusBadge(
            label = if (result.canSave) {
                stringResource(R.string.import_status_ready)
            } else {
                stringResource(R.string.import_status_error)
            },
            tone = if (result.canSave) StatusTone.Good else StatusTone.Bad,
        )
        OrdoMetricStrip(
            firstLabel = stringResource(R.string.import_metric_workouts),
            firstValue = result.workouts.size.toString(),
            secondLabel = stringResource(R.string.import_metric_exercises),
            secondValue = result.exerciseCount.toString(),
            thirdLabel = stringResource(R.string.import_metric_warnings),
            thirdValue = result.warnings.size.toString(),
        )
        result.trainingWeekdays?.let { weekdays ->
            OrdoStatusBadge(
                label = stringResource(
                    R.string.import_days_recognized,
                    weekdays.sortedBy { it.value }.joinToString(", ") { weekdayLabel(it) },
                ),
                tone = StatusTone.Info,
            )
        }

        if (result.errors.isNotEmpty()) {
            Text(
                text = stringResource(R.string.import_errors_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            result.errors.forEach { error ->
                Text(
                    text = "- $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (result.warnings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.import_warnings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            result.warnings.forEach { warning ->
                Text(
                    text = "- $warning",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        Text(
            text = stringResource(R.string.import_recognized_title),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        result.workouts.forEach { workout ->
            Text(
                text = "${workout.code} - ${workout.name}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            workout.exercises.forEach { exercise ->
                Text(
                    text = exercise.previewLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        OrdoActionButton(
            text = stringResource(R.string.import_save),
            icon = if (imported) Icons.Rounded.CheckCircle else Icons.Rounded.Save,
            onClick = onSave,
            enabled = result.canSave && hasPlanName && !isBusy,
        )
    }
}

@Composable
private fun importFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = BgInput,
    unfocusedContainerColor = BgInput,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = TextSecondary,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary,
    cursorColor = MaterialTheme.colorScheme.primary,
)

private fun weekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Seg"
    DayOfWeek.TUESDAY -> "Ter"
    DayOfWeek.WEDNESDAY -> "Qua"
    DayOfWeek.THURSDAY -> "Qui"
    DayOfWeek.FRIDAY -> "Sex"
    DayOfWeek.SATURDAY -> "Sab"
    DayOfWeek.SUNDAY -> "Dom"
}

private fun ImportedExercise.previewLine(): String {
    val reps = repetitionTargets
        .map { target ->
            when {
                target.target != null -> target.target.toString()
                target.minimum != null && target.maximum != null && target.minimum != target.maximum -> {
                    "${target.minimum}-${target.maximum}"
                }
                target.minimum != null -> target.minimum.toString()
                target.maximum != null -> target.maximum.toString()
                else -> "-"
            }
        }
        .distinct()
        .joinToString(",")
    return "$name - $series x $reps - ${restBetweenSetsSeconds}s / ${transitionRestSeconds}s"
}
