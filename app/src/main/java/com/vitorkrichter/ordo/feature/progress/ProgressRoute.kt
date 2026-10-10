package com.vitorkrichter.ordo.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoEmptyState
import com.vitorkrichter.ordo.core.designsystem.component.OrdoMetricStrip
import com.vitorkrichter.ordo.core.designsystem.theme.AccentBlue
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.BgCard2
import com.vitorkrichter.ordo.core.designsystem.theme.BorderSubtle
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.data.repository.ProgressExerciseUi
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressRoute(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ProgressExerciseUi?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    state.messageRes?.let { messageRes ->
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgApp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).background(BgApp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
        item {
            Text(
                text = stringResource(R.string.progress_title),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
            )
        }
        when {
            state.isLoading -> item { OrdoCard { Text(stringResource(R.string.home_loading)) } }
            !state.hasPlan || state.exercises.isEmpty() -> item {
                OrdoCard {
                    OrdoEmptyState(
                        icon = Icons.Rounded.Insights,
                        title = stringResource(R.string.progress_empty_title),
                        subtitle = stringResource(R.string.progress_empty_body),
                    )
                }
            }
            else -> {
                item { Text(state.planName, color = TextSecondary) }
                items(state.exercises, key = { it.sourceExerciseId ?: "name:${it.name}" }) { exercise ->
                    ProgressExerciseCard(exercise, onClick = { selected = exercise })
                }
            }
        }
    }
    }
    selected?.let { exercise ->
        ProgressDetailsDialog(exercise, onDismiss = { selected = null })
    }
}

@Composable
private fun ProgressExerciseCard(exercise: ProgressExerciseUi, onClick: () -> Unit) {
    OrdoCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Text(exercise.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        OrdoMetricStrip(
            firstLabel = stringResource(R.string.progress_sessions),
            firstValue = exercise.sessionCount.toString(),
            secondLabel = stringResource(R.string.progress_max_load),
            secondValue = exercise.maxLoadKg?.let { "${it.formatNumber()} kg" } ?: "-",
            thirdLabel = stringResource(R.string.progress_max_reps),
            thirdValue = exercise.maxRepetitions?.toString() ?: "-",
        )
        Text(
            text = stringResource(R.string.progress_tap_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun ProgressDetailsDialog(exercise: ProgressExerciseUi, onDismiss: () -> Unit) {
    val maximumLoad = exercise.records.flatMap { it.sets }.mapNotNull { it.actualLoadKg }.maxOrNull() ?: 1.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            LazyColumn(
                modifier = Modifier.widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    Text(
                        stringResource(R.string.progress_total_volume, exercise.totalVolumeKg.formatNumber()),
                        color = TextSecondary,
                    )
                }
                items(exercise.records, key = { it.date.toEpochMilli() }) { record ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            record.date.atZone(ProgressZone).format(ProgressDateFormatter),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                        )
                        record.sets.forEach { set ->
                            val load = set.actualLoadKg ?: 0.0
                            Text(
                                text = "${set.actualRepetitions ?: "-"} reps · ${set.actualLoadKg?.formatNumber() ?: "-"} kg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = BorderSubtle,
                            ) {
                                Row {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((load / maximumLoad).toFloat().coerceIn(0f, 1f))
                                            .height(4.dp)
                                            .background(AccentBlue),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_close)) } },
        containerColor = BgCard2,
    )
}

private fun Double.formatNumber(): String = if (this % 1.0 == 0.0) {
    toInt().toString()
} else {
    String.format(Locale.US, "%.1f", this)
}

private val ProgressZone = ZoneId.of("America/Sao_Paulo")
private val ProgressDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
