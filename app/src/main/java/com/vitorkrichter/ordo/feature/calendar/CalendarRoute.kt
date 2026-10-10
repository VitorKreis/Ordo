package com.vitorkrichter.ordo.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoEmptyState
import com.vitorkrichter.ordo.core.designsystem.component.OrdoInlineIcon
import com.vitorkrichter.ordo.core.designsystem.component.OrdoStatusBadge
import com.vitorkrichter.ordo.core.designsystem.component.StatusTone
import com.vitorkrichter.ordo.core.designsystem.theme.AccentBlue
import com.vitorkrichter.ordo.core.designsystem.theme.AccentTeal
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.BorderSubtle
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextMuted
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.data.repository.CalendarDayStatusUi
import com.vitorkrichter.ordo.data.repository.CalendarDayUi
import com.vitorkrichter.ordo.data.repository.ScheduleDayUi
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import java.util.Locale

@Composable
fun CalendarRoute(
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    state.messageRes?.let { messageRes ->
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    CalendarScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onTrainingDayToggle = viewModel::onTrainingDayToggle,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onDateSkipped = viewModel::setDateSkipped,
    )
}

@Composable
private fun CalendarScreen(
    state: CalendarUiState,
    snackbarHostState: SnackbarHostState,
    onTrainingDayToggle: (DayOfWeek, Boolean) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSkipped: (java.time.LocalDate, Boolean) -> Unit,
) {
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
            text = stringResource(R.string.calendar_title),
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
        )

        when {
            state.isLoading -> OrdoCard {
                Text(
                    text = stringResource(R.string.home_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                MonthNavigation(
                    month = state.visibleMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                )
            }

            !state.hasPlan -> EmptyCalendar()

            else -> {
                Text(
                    text = stringResource(R.string.calendar_plan, state.planName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                TrainingDaysCard(
                    days = state.scheduleDays,
                    enabled = !state.isBusy,
                    onTrainingDayToggle = onTrainingDayToggle,
                )
                state.days.forEach { day ->
                    CalendarDayCard(day, !state.isBusy, onDateSkipped)
                }
            }
        }
        }
    }
}

@Composable
private fun MonthNavigation(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.calendar_previous_month))
        }
        Text(
            month.format(MonthFormatter).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month))
        }
    }
}

@Composable
private fun EmptyCalendar() {
    OrdoCard {
        OrdoEmptyState(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.calendar_empty_title),
            subtitle = stringResource(R.string.calendar_empty_body),
        )
    }
}

@Composable
private fun TrainingDaysCard(
    days: List<ScheduleDayUi>,
    enabled: Boolean,
    onTrainingDayToggle: (DayOfWeek, Boolean) -> Unit,
) {
    OrdoCard {
        Text(
            text = stringResource(R.string.calendar_training_days_title),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.calendar_training_days_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        WeekdayRow(
            days = days.take(4),
            enabled = enabled,
            onTrainingDayToggle = onTrainingDayToggle,
        )
        WeekdayRow(
            days = days.drop(4),
            enabled = enabled,
            onTrainingDayToggle = onTrainingDayToggle,
        )
    }
}

@Composable
private fun WeekdayRow(
    days: List<ScheduleDayUi>,
    enabled: Boolean,
    onTrainingDayToggle: (DayOfWeek, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        days.forEach { day ->
            WeekdayChip(
                day = day,
                enabled = enabled,
                onToggle = { onTrainingDayToggle(day.weekday, !day.isEnabled) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekdayChip(
    day: ScheduleDayUi,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (day.isEnabled) {
        AccentBlue.copy(alpha = 0.20f)
    } else {
        BorderSubtle
    }
    val borderColor = if (day.isEnabled) {
        AccentBlue.copy(alpha = 0.55f)
    } else {
        BorderSubtle
    }
    val contentColor = if (day.isEnabled) TextPrimary else TextMuted
    Surface(
        modifier = modifier.selectable(
            selected = day.isEnabled,
            enabled = enabled,
            role = Role.Checkbox,
            onClick = onToggle,
        ),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (day.isEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (day.isEnabled) AccentTeal else TextMuted,
            )
            Text(
                text = weekdayShortLabel(day.weekday),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun CalendarDayCard(
    day: CalendarDayUi,
    enabled: Boolean,
    onDateSkipped: (java.time.LocalDate, Boolean) -> Unit,
) {
    OrdoCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OrdoStatusBadge(
                label = statusLabel(day.status),
                tone = statusTone(day.status),
            )
            if (day.isToday) {
                OrdoStatusBadge(
                    label = stringResource(R.string.calendar_today),
                    tone = StatusTone.Info,
                )
            }
        }
        Text(
            text = day.date.format(DateFormatter),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        if (day.status == CalendarDayStatusUi.PLANNED || day.status == CalendarDayStatusUi.IN_PROGRESS) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OrdoInlineIcon(icon = Icons.Rounded.EventAvailable)
                Text(
                    text = day.workoutLabel() ?: stringResource(R.string.calendar_sequence_visible),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        } else if (day.status == CalendarDayStatusUi.NO_WORKOUT) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OrdoInlineIcon(icon = Icons.Rounded.SelfImprovement)
                Text(
                    text = stringResource(R.string.calendar_rest_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }
        if (day.status == CalendarDayStatusUi.PLANNED || day.isSkippedOverride) {
            TextButton(
                onClick = { onDateSkipped(day.date, !day.isSkippedOverride) },
                enabled = enabled,
            ) {
                Text(
                    if (day.isSkippedOverride) {
                        stringResource(R.string.calendar_restore_day)
                    } else {
                        stringResource(R.string.calendar_skip_day)
                    },
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: CalendarDayStatusUi): String = when (status) {
    CalendarDayStatusUi.PLANNED -> stringResource(R.string.calendar_planned)
    CalendarDayStatusUi.NO_WORKOUT -> stringResource(R.string.calendar_no_workout)
    CalendarDayStatusUi.COMPLETED -> stringResource(R.string.calendar_completed)
    CalendarDayStatusUi.PARTIAL -> stringResource(R.string.calendar_partial)
    CalendarDayStatusUi.CANCELED -> stringResource(R.string.calendar_canceled)
    CalendarDayStatusUi.IN_PROGRESS -> stringResource(R.string.calendar_in_progress)
}

private fun statusTone(status: CalendarDayStatusUi) = when (status) {
    CalendarDayStatusUi.COMPLETED -> StatusTone.Good
    CalendarDayStatusUi.PARTIAL -> StatusTone.Pending
    CalendarDayStatusUi.CANCELED -> StatusTone.Bad
    CalendarDayStatusUi.IN_PROGRESS -> StatusTone.Info
    CalendarDayStatusUi.PLANNED -> StatusTone.Good
    CalendarDayStatusUi.NO_WORKOUT -> StatusTone.Muted
}

private fun CalendarDayUi.workoutLabel(): String? {
    val code = workoutCode
    val name = workoutName
    return when {
        code != null && name != null -> "$code - $name"
        name != null -> name
        code != null -> code
        else -> null
    }
}

@Composable
private fun weekdayShortLabel(weekday: DayOfWeek): String = when (weekday) {
    DayOfWeek.MONDAY -> stringResource(R.string.weekday_monday_short)
    DayOfWeek.TUESDAY -> stringResource(R.string.weekday_tuesday_short)
    DayOfWeek.WEDNESDAY -> stringResource(R.string.weekday_wednesday_short)
    DayOfWeek.THURSDAY -> stringResource(R.string.weekday_thursday_short)
    DayOfWeek.FRIDAY -> stringResource(R.string.weekday_friday_short)
    DayOfWeek.SATURDAY -> stringResource(R.string.weekday_saturday_short)
    DayOfWeek.SUNDAY -> stringResource(R.string.weekday_sunday_short)
}

private val DateFormatter = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("pt-BR"))
private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR"))
