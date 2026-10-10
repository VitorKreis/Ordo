package com.vitorkrichter.ordo.feature.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.time.AppTime
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoEmptyState
import com.vitorkrichter.ordo.core.designsystem.component.OrdoInlineIcon
import com.vitorkrichter.ordo.core.designsystem.component.OrdoStatusBadge
import com.vitorkrichter.ordo.core.designsystem.component.StatusTone
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.data.repository.HistoryExerciseUi
import com.vitorkrichter.ordo.data.repository.HistorySessionUi
import com.vitorkrichter.ordo.data.repository.HistorySetUi
import com.vitorkrichter.ordo.domain.model.SessionSetStatus
import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        isExporting = false
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(state.exportText)
                } ?: error("Output stream unavailable")
            }.onSuccess {
                Toast.makeText(context, R.string.history_export_success, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, R.string.history_export_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    HistoryScreen(
        state = state,
        isExporting = isExporting,
        onExport = {
            if (!isExporting) {
                isExporting = true
                exportLauncher.launch("ordo-historico.txt")
            }
        },
        onUpdateSet = viewModel::updateSetPerformance,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    isExporting: Boolean,
    onExport: () -> Unit,
    onUpdateSet: (String, String, String) -> Unit,
    onMessageShown: () -> Unit,
) {
    var selectedSession by remember { mutableStateOf<HistorySessionUi?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    state.messageRes?.let { messageRes ->
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgApp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BgApp)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
        item {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
            )
        }

        when {
            state.isLoading -> item {
                OrdoCard {
                    Text(
                        text = stringResource(R.string.home_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            }

            !state.hasPlan -> item { EmptyHistory() }

            else -> {
                item { HistorySummary(state, isExporting, onExport) }
                if (state.sessions.isEmpty()) {
                    item { EmptyHistory() }
                } else {
                    items(state.sessions, key = { it.startedAt.toEpochMilli() }) { session ->
                        HistoryCard(session, onClick = { selectedSession = session })
                    }
                }
            }
            }
        }
    }
    selectedSession?.let { session ->
        HistoryDetailsDialog(
            session = session,
            isBusy = state.isBusy,
            onDismiss = { selectedSession = null },
            onUpdateSet = onUpdateSet,
        )
    }
}

@Composable
private fun HistorySummary(state: HistoryUiState, isExporting: Boolean, onExport: () -> Unit) {
    val finishedCount = state.sessions.count {
        it.status == WorkoutSessionStatus.COMPLETED ||
            it.status == WorkoutSessionStatus.PARTIALLY_COMPLETED
    }
    OrdoCard {
        OrdoStatusBadge(
            label = stringResource(R.string.history_plan, state.planName),
            tone = StatusTone.Info,
        )
        Text(
            text = stringResource(R.string.history_finished_count, finishedCount),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        com.vitorkrichter.ordo.core.designsystem.component.OrdoActionButton(
            text = stringResource(R.string.history_export),
            icon = Icons.Rounded.Download,
            onClick = onExport,
            enabled = state.sessions.isNotEmpty() && !isExporting,
        )
    }
}

@Composable
private fun EmptyHistory() {
    OrdoCard {
        OrdoEmptyState(
            icon = Icons.Rounded.History,
            title = stringResource(R.string.history_empty_title),
            subtitle = stringResource(R.string.history_empty_body),
        )
    }
}

@Composable
private fun HistoryCard(session: HistorySessionUi, onClick: () -> Unit) {
    val localDateTime = session.startedAt.atZone(AppTime.zoneId).toLocalDateTime()
    OrdoCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = listOfNotNull(session.workoutCode, session.workoutName).joinToString(" - "),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.history_session_date,
                        localDateTime.format(DateFormatter),
                        localDateTime.format(TimeFormatter),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            OrdoStatusBadge(label = statusLabel(session.status), tone = statusTone(session.status))
        }
        Text(
            text = stringResource(R.string.history_tap_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun HistoryDetailsDialog(
    session: HistorySessionUi,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onUpdateSet: (String, String, String) -> Unit,
) {
    val localDateTime = session.startedAt.atZone(AppTime.zoneId).toLocalDateTime()
    var editingSet by remember(session) { mutableStateOf<HistorySetUi?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(listOfNotNull(session.workoutCode, session.workoutName).joinToString(" - "))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OrdoStatusBadge(label = statusLabel(session.status), tone = statusTone(session.status))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OrdoInlineIcon(icon = Icons.Rounded.Schedule)
                    Text(
                        text = stringResource(
                            R.string.history_session_date,
                            localDateTime.format(DateFormatter),
                            localDateTime.format(TimeFormatter),
                        ),
                        color = TextSecondary,
                    )
                }
                session.finishedAt?.let { finishedAt ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OrdoInlineIcon(icon = Icons.Rounded.Timer)
                        Text(
                            text = stringResource(R.string.history_duration, formatDuration(session.startedAt, finishedAt)),
                            color = TextSecondary,
                        )
                    }
                }
                if (session.exercises.any { exercise -> exercise.sets.any { it.hasPerformanceData } }) {
                    Text(
                        text = stringResource(R.string.history_performance_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    session.exercises.forEach { exercise ->
                        HistoryExerciseBlock(exercise, onEditSet = { editingSet = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_close)) }
        },
        containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
    )
    editingSet?.let { set ->
        HistorySetEditDialog(
            set = set,
            isBusy = isBusy,
            onDismiss = { editingSet = null },
            onSave = { repetitions, load ->
                onUpdateSet(set.id, repetitions, load)
                editingSet = null
            },
        )
    }
}

@Composable
private fun HistoryExerciseBlock(
    exercise: HistoryExerciseUi,
    onEditSet: (HistorySetUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
        )
        exercise.sets
            .filter { it.status == SessionSetStatus.COMPLETED || it.hasPerformanceData }
            .forEach { set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = set.performanceLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEditSet(set) }) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.history_edit_set),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
    }
}

@Composable
private fun HistorySetEditDialog(
    set: HistorySetUi,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var repetitions by remember(set.id) { mutableStateOf(set.actualRepetitions?.toString().orEmpty()) }
    var load by remember(set.id) { mutableStateOf(set.actualLoadKg?.formatKg().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_edit_set_title, set.position + 1)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = repetitions,
                    onValueChange = { repetitions = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.session_actual_reps)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = load,
                    onValueChange = { load = it.take(6) },
                    label = { Text(stringResource(R.string.session_actual_load)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(repetitions, load) }, enabled = !isBusy) {
                Text(stringResource(R.string.history_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_close)) }
        },
        containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
    )
}

@Composable
private fun statusLabel(status: WorkoutSessionStatus): String = when (status) {
    WorkoutSessionStatus.IN_PROGRESS -> stringResource(R.string.history_session_in_progress)
    WorkoutSessionStatus.COMPLETED -> stringResource(R.string.history_session_complete)
    WorkoutSessionStatus.PARTIALLY_COMPLETED -> stringResource(R.string.history_session_partial)
    WorkoutSessionStatus.CANCELED -> stringResource(R.string.history_session_canceled)
}

private fun statusTone(status: WorkoutSessionStatus) = when (status) {
    WorkoutSessionStatus.COMPLETED -> StatusTone.Good
    WorkoutSessionStatus.PARTIALLY_COMPLETED -> StatusTone.Pending
    WorkoutSessionStatus.CANCELED -> StatusTone.Bad
    WorkoutSessionStatus.IN_PROGRESS -> StatusTone.Info
}

private val DateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatDuration(startedAt: java.time.Instant, finishedAt: java.time.Instant): String {
    val minutes = Duration.between(startedAt, finishedAt).toMinutes().coerceAtLeast(0)
    return if (minutes < 60) {
        "$minutes min"
    } else {
        "${minutes / 60}h ${minutes % 60}min"
    }
}

private val HistorySetUi.hasPerformanceData: Boolean
    get() = actualRepetitions != null || actualLoadKg != null

private fun HistorySetUi.performanceLine(): String {
    val reps = actualRepetitions?.let { "$it reps" } ?: plannedRepetitionLabel()
    val load = actualLoadKg?.let { "${it.formatKg()} kg" } ?: "-"
    return "Serie ${position + 1}: $reps · $load"
}

private fun HistorySetUi.plannedRepetitionLabel(): String {
    return when {
        plannedMinimumRepetitions != null &&
            plannedMaximumRepetitions != null &&
            plannedMinimumRepetitions != plannedMaximumRepetitions -> {
            "$plannedMinimumRepetitions-$plannedMaximumRepetitions reps"
        }
        plannedMinimumRepetitions != null -> "$plannedMinimumRepetitions reps"
        plannedMaximumRepetitions != null -> "$plannedMaximumRepetitions reps"
        else -> "-"
    }
}

private fun Double.formatKg(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }
}
