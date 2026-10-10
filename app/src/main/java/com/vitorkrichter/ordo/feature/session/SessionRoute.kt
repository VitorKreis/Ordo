package com.vitorkrichter.ordo.feature.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoActionButton
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoEmptyState
import com.vitorkrichter.ordo.core.designsystem.component.OrdoStatusBadge
import com.vitorkrichter.ordo.core.designsystem.component.StatusTone
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.BgCard2
import com.vitorkrichter.ordo.core.designsystem.theme.BgInput
import com.vitorkrichter.ordo.core.designsystem.theme.BorderSubtle
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.data.repository.WorkoutExerciseDetailUi
import com.vitorkrichter.ordo.data.repository.WorkoutSetDetailUi
import com.vitorkrichter.ordo.domain.model.TrackingMode

@Composable
fun SessionRoute(
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val messageRes = state.messageRes

    if (messageRes != null) {
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
        }
    }
    if (state.finished) {
        LaunchedEffect(Unit) {
            onBack()
        }
    }
    LaunchedEffect(state.timer?.setKey) {
        if (
            state.timer != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.session_back))
                }
                Text(
                    text = stringResource(R.string.session_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                )
            }

            when {
                state.isLoading -> OrdoCard {
                    Text(
                        text = stringResource(R.string.home_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
                state.workout == null -> OrdoCard {
                    OrdoEmptyState(
                        icon = Icons.Rounded.FitnessCenter,
                        title = stringResource(R.string.session_empty_title),
                        subtitle = stringResource(R.string.session_empty_body),
                    )
                }
                else -> SessionContent(
                state = state,
                onCompleteSet = viewModel::completeSet,
                onUndoSet = viewModel::undoSet,
                onRepetitionsChange = viewModel::onRepetitionsChange,
                onLoadChange = viewModel::onLoadChange,
                onConfirmPerformance = viewModel::confirmPerformance,
                onEditPerformance = viewModel::editPerformance,
                onMoveExercise = viewModel::moveExercise,
                onAdjustRestTimer = viewModel::adjustRestTimer,
                onSkipRestTimer = viewModel::skipRestTimer,
                onFinish = viewModel::finishWorkout,
            )
            }
        }
    }
}

@Composable
private fun SessionContent(
    state: SessionUiState,
    onCompleteSet: (String, String) -> Unit,
    onUndoSet: (String) -> Unit,
    onRepetitionsChange: (String, String) -> Unit,
    onLoadChange: (String, String) -> Unit,
    onConfirmPerformance: (String) -> Unit,
    onEditPerformance: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onAdjustRestTimer: (Int) -> Unit,
    onSkipRestTimer: () -> Unit,
    onFinish: () -> Unit,
) {
    val workout = requireNotNull(state.workout)
    OrdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OrdoStatusBadge(label = workout.code, tone = StatusTone.Info)
            OrdoStatusBadge(
                label = stringResource(R.string.session_sequence, workout.sequenceLabel),
                tone = StatusTone.Good,
            )
        }
        Text(
            text = workout.name,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Text(
            text = stringResource(
                R.string.session_progress,
                state.completedSetCount,
                state.totalSetCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }

    workout.exercises.forEachIndexed { exerciseIndex, exercise ->
        ExerciseSetCard(
            exercise = exercise,
            isLastExercise = exerciseIndex == workout.exercises.lastIndex,
            state = state,
            onCompleteSet = onCompleteSet,
            onUndoSet = onUndoSet,
            onRepetitionsChange = onRepetitionsChange,
            onLoadChange = onLoadChange,
            onConfirmPerformance = onConfirmPerformance,
            onEditPerformance = onEditPerformance,
            canMoveUp = exerciseIndex > 0,
            canMoveDown = exerciseIndex < workout.exercises.lastIndex,
            onMoveUp = { onMoveExercise(exercise.id, -1) },
            onMoveDown = { onMoveExercise(exercise.id, 1) },
            onAdjustRestTimer = onAdjustRestTimer,
            onSkipRestTimer = onSkipRestTimer,
        )
    }

    OrdoActionButton(
        text = if (state.allExercisesCompleted) {
            stringResource(R.string.session_finish_complete)
        } else {
            stringResource(R.string.session_finish_partial)
        },
        icon = Icons.Rounded.DoneAll,
        onClick = onFinish,
        enabled = !state.isSaving,
    )
}

@Composable
private fun ExerciseSetCard(
    exercise: WorkoutExerciseDetailUi,
    isLastExercise: Boolean,
    state: SessionUiState,
    onCompleteSet: (String, String) -> Unit,
    onUndoSet: (String) -> Unit,
    onRepetitionsChange: (String, String) -> Unit,
    onLoadChange: (String, String) -> Unit,
    onConfirmPerformance: (String) -> Unit,
    onEditPerformance: (String) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAdjustRestTimer: (Int) -> Unit,
    onSkipRestTimer: () -> Unit,
) {
    val exerciseSetKeys = exercise.sets.map { it.key(exercise.id) }
    val completedSets = exerciseSetKeys.count { it in state.completedSetKeys }
    val isActiveExercise = state.activeSetKey in exerciseSetKeys

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
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.session_exercise_detail,
                        exercise.seriesCount,
                        exercise.repetitions,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.session_move_exercise_up),
                        tint = if (canMoveUp) MaterialTheme.colorScheme.secondary else TextSecondary,
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.session_move_exercise_down),
                        tint = if (canMoveDown) MaterialTheme.colorScheme.secondary else TextSecondary,
                    )
                }
                OrdoStatusBadge(
                    label = if (isActiveExercise) {
                        stringResource(R.string.session_status_current)
                    } else {
                        stringResource(R.string.session_sets_done, completedSets, exercise.sets.size)
                    },
                    tone = if (completedSets == exercise.sets.size) StatusTone.Good else StatusTone.Muted,
                )
            }
        }

        if (exercise.previousPerformance.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.session_previous_performance,
                    exercise.previousPerformance.joinToString(" | ") { previous ->
                        "${previous.repetitions ?: "-"}x${previous.loadKg?.let(::formatLoad) ?: "-"}kg"
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        exercise.sets.forEachIndexed { index, set ->
            val setKey = set.key(exercise.id)
            SetRow(
                set = set,
                setKey = setKey,
                setNumber = index + 1,
                restSeconds = restAfterSet(
                    exercise = exercise,
                    setIndex = index,
                    isLastExercise = isLastExercise,
                ),
                completed = setKey in state.completedSetKeys,
                active = setKey == state.activeSetKey,
                timer = state.timer?.takeIf { it.setKey == setKey },
                performanceDraft = state.performanceDrafts[setKey],
                trackingMode = exercise.trackingMode,
                performanceLocked = setKey in state.lockedPerformanceKeys,
                enabled = setKey !in state.completedSetKeys,
                onOk = { onCompleteSet(exercise.id, set.id) },
                onUndo = { onUndoSet(setKey) },
                onRepetitionsChange = { onRepetitionsChange(setKey, it) },
                onLoadChange = { onLoadChange(setKey, it) },
                onConfirmPerformance = { onConfirmPerformance(setKey) },
                onEditPerformance = { onEditPerformance(setKey) },
                onAdjustRestTimer = onAdjustRestTimer,
                onSkipRestTimer = onSkipRestTimer,
            )
        }

        if (exercise.notes != null) {
            Text(
                text = exercise.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SetRow(
    set: WorkoutSetDetailUi,
    setKey: String,
    setNumber: Int,
    restSeconds: Int,
    completed: Boolean,
    active: Boolean,
    timer: RestTimerUi?,
    performanceDraft: SetPerformanceDraft?,
    trackingMode: TrackingMode,
    performanceLocked: Boolean,
    enabled: Boolean,
    onOk: () -> Unit,
    onUndo: () -> Unit,
    onRepetitionsChange: (String) -> Unit,
    onLoadChange: (String) -> Unit,
    onConfirmPerformance: () -> Unit,
    onEditPerformance: () -> Unit,
    onAdjustRestTimer: (Int) -> Unit,
    onSkipRestTimer: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else BgCard2,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else BorderSubtle,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.session_set_line, setNumber, set.repetitionLabel()),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Text(
                        text = if (restSeconds > 0) {
                            stringResource(R.string.session_set_rest, restSeconds)
                        } else {
                            stringResource(R.string.session_set_last)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }

                if (timer != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = timer.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                } else if (completed) {
                    IconButton(onClick = onUndo) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.session_undo_set),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                } else {
                    Button(
                        onClick = onOk,
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = TextPrimary,
                        ),
                    ) {
                        Text(text = stringResource(R.string.session_set_ok))
                    }
                }
            }

            if (timer != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onAdjustRestTimer(-15) }) {
                        Text(stringResource(R.string.session_timer_subtract))
                    }
                    TextButton(onClick = onSkipRestTimer) {
                        Text(stringResource(R.string.session_timer_skip))
                    }
                    TextButton(onClick = { onAdjustRestTimer(15) }) {
                        Text(stringResource(R.string.session_timer_add))
                    }
                }
            }

            if (completed && trackingMode != TrackingMode.NONE) {
                PerformanceFields(
                    draft = performanceDraft ?: SetPerformanceDraft(),
                    locked = performanceLocked,
                    onRepetitionsChange = onRepetitionsChange,
                    onLoadChange = onLoadChange,
                    onConfirmPerformance = onConfirmPerformance,
                    onEditPerformance = onEditPerformance,
                )
            }
        }
    }
}

@Composable
private fun PerformanceFields(
    draft: SetPerformanceDraft,
    locked: Boolean,
    onRepetitionsChange: (String) -> Unit,
    onLoadChange: (String) -> Unit,
    onConfirmPerformance: () -> Unit,
    onEditPerformance: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PerformanceField(
                label = stringResource(R.string.session_actual_reps),
                value = draft.repetitions,
                keyboardType = KeyboardType.Number,
                onValueChange = onRepetitionsChange,
                enabled = !locked,
                modifier = Modifier.weight(1f),
            )
            PerformanceField(
                label = stringResource(R.string.session_actual_load),
                value = draft.loadKg,
                keyboardType = KeyboardType.Decimal,
                onValueChange = onLoadChange,
                enabled = !locked,
                modifier = Modifier.weight(1f),
            )
        }
        if (locked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrdoStatusBadge(
                    label = stringResource(R.string.session_performance_locked),
                    tone = StatusTone.Good,
                )
                IconButton(onClick = onEditPerformance) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.session_edit_performance),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        } else {
            Button(
                onClick = onConfirmPerformance,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = TextPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.session_confirm_performance),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun PerformanceField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = BgInput,
            unfocusedContainerColor = BgInput,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = TextSecondary,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

private fun WorkoutSetDetailUi.repetitionLabel(): String {
    return when {
        targetRepetitions != null -> targetRepetitions.toString()
        minimumRepetitions != null && maximumRepetitions != null && minimumRepetitions != maximumRepetitions -> {
            "$minimumRepetitions-$maximumRepetitions"
        }
        minimumRepetitions != null -> minimumRepetitions.toString()
        maximumRepetitions != null -> maximumRepetitions.toString()
        else -> "-"
    }
}

private fun restAfterSet(
    exercise: WorkoutExerciseDetailUi,
    setIndex: Int,
    isLastExercise: Boolean,
): Int {
    val isLastSet = setIndex == exercise.sets.lastIndex
    return when {
        isLastSet && isLastExercise -> 0
        isLastSet -> exercise.transitionRestSeconds
        else -> exercise.restBetweenSetsSeconds
    }
}

private fun formatLoad(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    String.format(java.util.Locale.US, "%.1f", value)
}
