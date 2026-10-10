package com.vitorkrichter.ordo.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoActionButton
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoEmptyState
import com.vitorkrichter.ordo.core.designsystem.component.OrdoInlineIcon
import com.vitorkrichter.ordo.core.designsystem.component.OrdoMetricStrip
import com.vitorkrichter.ordo.core.designsystem.component.OrdoStatusBadge
import com.vitorkrichter.ordo.core.designsystem.component.StatusTone
import com.vitorkrichter.ordo.core.designsystem.theme.AccentGreen
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.data.repository.HomeHistoryDayUi
import com.vitorkrichter.ordo.data.repository.HomeHistoryStatusUi
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeRoute(
    onOpenWorkout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = uiState,
        onCreateSamplePlan = viewModel::createSamplePlan,
        onOpenWorkout = onOpenWorkout,
        onMessageShown = viewModel::clearMessage,
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onCreateSamplePlan: () -> Unit,
    onOpenWorkout: () -> Unit,
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

    Scaffold(
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
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
            )

            when {
                state.isLoading -> LoadingCard()

                !state.hasPlan -> EmptyPlanContent(
                    isBusy = state.isBusy,
                    onCreateSamplePlan = onCreateSamplePlan,
                )

                else -> DashboardContent(
                    state = state,
                    onOpenWorkout = onOpenWorkout,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    OrdoCard {
        Text(
            text = stringResource(R.string.home_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
    }
}

@Composable
private fun EmptyPlanContent(
    isBusy: Boolean,
    onCreateSamplePlan: () -> Unit,
) {
    OrdoCard {
        OrdoEmptyState(
            icon = Icons.Rounded.FitnessCenter,
            title = stringResource(R.string.home_empty_title),
            subtitle = stringResource(R.string.home_empty_body),
            action = {
                OrdoActionButton(
                    text = stringResource(R.string.home_create_sample_plan),
                    icon = Icons.Rounded.AddCircle,
                    onClick = onCreateSamplePlan,
                    enabled = !isBusy,
                )
            },
        )
    }
}

@Composable
private fun DashboardContent(
    state: HomeUiState,
    onOpenWorkout: () -> Unit,
) {
    OrdoCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OrdoStatusBadge(
                    label = stringResource(R.string.home_plan_label),
                    tone = StatusTone.Info,
                )
                OrdoStatusBadge(
                    label = if (state.isTodayPlanned) {
                        stringResource(R.string.home_today_planned)
                    } else {
                        stringResource(R.string.home_today_not_planned)
                    },
                    tone = if (state.isTodayPlanned) StatusTone.Good else StatusTone.Muted,
                )
            }
            Text(
                text = state.planName,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
            if (state.nextWorkoutCode != null && state.nextWorkoutName != null) {
                Text(
                    text = workoutTitle(state.nextWorkoutCode, state.nextWorkoutName),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (state.isTodayPlanned) {
                        stringResource(R.string.home_today_workout_label)
                    } else {
                        stringResource(R.string.home_next_workout_label)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                OrdoActionButton(
                    text = stringResource(R.string.home_open_workout),
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onOpenWorkout,
                )
            }
        }
    }

    OrdoMetricStrip(
        firstLabel = stringResource(R.string.home_metric_workouts),
        firstValue = state.workoutCount.toString(),
        secondLabel = stringResource(R.string.home_metric_completed),
        secondValue = state.completedSessionCount.toString(),
        thirdLabel = stringResource(R.string.home_metric_partial),
        thirdValue = state.partialSessionCount.toString(),
    )

    Text(
        text = stringResource(R.string.home_summary, state.completedSessionCount, state.partialSessionCount),
        style = MaterialTheme.typography.bodyMedium,
        color = AccentGreen,
    )

    MiniHistoryCard(days = state.lastFiveDays)
}

@Composable
private fun MiniHistoryCard(days: List<HomeHistoryDayUi>) {
    OrdoCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrdoInlineIcon(icon = Icons.Rounded.History)
            Text(
                text = stringResource(R.string.home_mini_history_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        }
        days.forEach { day ->
            MiniHistoryRow(day)
        }
    }
}

@Composable
private fun MiniHistoryRow(day: HomeHistoryDayUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.date.format(MiniHistoryDateFormatter),
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            modifier = Modifier.weight(0.8f),
        )
        OrdoStatusBadge(
            label = statusLabel(day.status),
            tone = statusTone(day.status),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = day.workoutLabel() ?: stringResource(R.string.home_mini_history_no_workout),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1.7f),
        )
    }
}

@Composable
private fun statusLabel(status: HomeHistoryStatusUi): String = when (status) {
    HomeHistoryStatusUi.COMPLETED -> stringResource(R.string.home_mini_history_completed)
    HomeHistoryStatusUi.PARTIAL -> stringResource(R.string.home_mini_history_partial)
    HomeHistoryStatusUi.CANCELED -> stringResource(R.string.home_mini_history_canceled)
    HomeHistoryStatusUi.IN_PROGRESS -> stringResource(R.string.home_mini_history_progress)
    HomeHistoryStatusUi.NO_RECORD -> stringResource(R.string.home_mini_history_empty)
}

private fun statusTone(status: HomeHistoryStatusUi): StatusTone = when (status) {
    HomeHistoryStatusUi.COMPLETED -> StatusTone.Good
    HomeHistoryStatusUi.PARTIAL -> StatusTone.Pending
    HomeHistoryStatusUi.CANCELED -> StatusTone.Bad
    HomeHistoryStatusUi.IN_PROGRESS -> StatusTone.Info
    HomeHistoryStatusUi.NO_RECORD -> StatusTone.Muted
}

private fun HomeHistoryDayUi.workoutLabel(): String? {
    return when {
        workoutCode != null && workoutName != null -> "$workoutCode - $workoutName"
        workoutName != null -> workoutName
        workoutCode != null -> workoutCode
        else -> null
    }
}

private fun workoutTitle(code: String, name: String): String {
    return "Treino $code ($name)"
}

private val MiniHistoryDateFormatter = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("pt-BR"))
