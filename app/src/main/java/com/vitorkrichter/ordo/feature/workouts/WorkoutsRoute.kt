package com.vitorkrichter.ordo.feature.workouts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoActionButton
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoEmptyState
import com.vitorkrichter.ordo.core.designsystem.component.OrdoInlineIcon
import com.vitorkrichter.ordo.core.designsystem.component.OrdoStatusBadge
import com.vitorkrichter.ordo.core.designsystem.component.StatusTone
import com.vitorkrichter.ordo.core.designsystem.theme.AccentBlue
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.BgInput
import com.vitorkrichter.ordo.core.designsystem.theme.BorderSubtle
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.data.repository.WorkoutExerciseUi
import com.vitorkrichter.ordo.data.repository.WorkoutUi
import com.vitorkrichter.ordo.domain.model.TrackingMode

@Composable
fun WorkoutsRoute(
    onOpenImport: () -> Unit,
    viewModel: WorkoutsViewModel = hiltViewModel(),
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
    WorkoutsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onToggleEdit = viewModel::toggleEdit,
        onSeriesChange = viewModel::onSeriesChange,
        onNameChange = viewModel::onNameChange,
        onRepetitionsChange = viewModel::onRepetitionsChange,
        onRestChange = viewModel::onRestChange,
        onTransitionChange = viewModel::onTransitionChange,
        onSave = viewModel::saveEditingWorkout,
        onMoveUp = viewModel::moveWorkoutUp,
        onMoveDown = viewModel::moveWorkoutDown,
        onAddExercise = viewModel::addExercise,
        onDeleteExercise = viewModel::deleteExercise,
        onMoveExercise = viewModel::moveExercise,
        onOpenImport = onOpenImport,
        onAddWorkout = viewModel::addWorkout,
        onArchiveWorkout = viewModel::archiveWorkout,
    )
}

@Composable
private fun WorkoutsScreen(
    state: WorkoutsUiState,
    snackbarHostState: SnackbarHostState,
    onToggleEdit: (WorkoutUi) -> Unit,
    onSeriesChange: (String, String) -> Unit,
    onNameChange: (String, String) -> Unit,
    onRepetitionsChange: (String, String) -> Unit,
    onRestChange: (String, String) -> Unit,
    onTransitionChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onMoveUp: (WorkoutUi) -> Unit,
    onMoveDown: (WorkoutUi) -> Unit,
    onAddExercise: (String, String, String, String, String, String) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onOpenImport: () -> Unit,
    onAddWorkout: (String) -> Unit,
    onArchiveWorkout: (String) -> Unit,
) {
    var selectedWorkoutId by remember { mutableStateOf<String?>(null) }
    var addingWorkout by remember { mutableStateOf(false) }
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
            text = stringResource(R.string.workouts_title),
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
            }

            !state.hasPlan || state.workouts.isEmpty() -> EmptyWorkouts()

            else -> {
                Text(
                    text = stringResource(R.string.workouts_plan, state.planName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                OrdoActionButton(
                    text = stringResource(R.string.workouts_manage_plans),
                    icon = Icons.Rounded.UploadFile,
                    onClick = onOpenImport,
                    enabled = !state.isBusy,
                    secondary = true,
                )
                OrdoActionButton(
                    text = stringResource(R.string.workouts_add_workout),
                    icon = Icons.Rounded.Add,
                    onClick = { addingWorkout = true },
                    enabled = !state.isBusy,
                    secondary = true,
                )
                state.workouts.forEachIndexed { index, workout ->
                    WorkoutSummaryCard(
                        workout = workout,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.workouts.lastIndex,
                        onOpen = { selectedWorkoutId = workout.id },
                        onMoveUp = { onMoveUp(workout) },
                        onMoveDown = { onMoveDown(workout) },
                        actionsEnabled = !state.isBusy,
                    )
                }
            }
            }
        }
    }
    state.workouts.firstOrNull { it.id == selectedWorkoutId }?.let { workout ->
        val isEditing = state.editingWorkoutCode == workout.code
        WorkoutDetailsDialog(
            workout = workout,
            isEditing = isEditing,
            drafts = state.drafts,
            isSaving = state.isSaving,
            isBusy = state.isBusy,
            onDismiss = {
                if (isEditing) onToggleEdit(workout)
                selectedWorkoutId = null
            },
            onToggleEdit = { onToggleEdit(workout) },
            onSeriesChange = onSeriesChange,
            onNameChange = onNameChange,
            onRepetitionsChange = onRepetitionsChange,
            onRestChange = onRestChange,
            onTransitionChange = onTransitionChange,
            onSave = onSave,
            onAddExercise = onAddExercise,
            onDeleteExercise = onDeleteExercise,
            onMoveExercise = onMoveExercise,
            onArchiveWorkout = {
                onArchiveWorkout(workout.id)
                selectedWorkoutId = null
            },
        )
    }
    if (addingWorkout) {
        AddWorkoutDialog(
            onDismiss = { addingWorkout = false },
            onSave = {
                onAddWorkout(it)
                addingWorkout = false
            },
        )
    }
}

@Composable
private fun EmptyWorkouts() {
    OrdoCard {
        OrdoEmptyState(
            icon = Icons.Rounded.FitnessCenter,
            title = stringResource(R.string.workouts_empty_title),
            subtitle = stringResource(R.string.workouts_empty_body),
        )
    }
}

@Composable
private fun WorkoutSummaryCard(
    workout: WorkoutUi,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    actionsEnabled: Boolean,
) {
    OrdoCard(modifier = Modifier.clickable(role = Role.Button, enabled = actionsEnabled, onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OrdoStatusBadge(label = workout.code, tone = StatusTone.Info)
                OrdoStatusBadge(
                    label = stringResource(R.string.workouts_exercise_count, workout.exerciseCount),
                    tone = StatusTone.Good,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp && actionsEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.workouts_move_up),
                        tint = if (canMoveUp) MaterialTheme.colorScheme.secondary else TextSecondary,
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown && actionsEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.workouts_move_down),
                        tint = if (canMoveDown) MaterialTheme.colorScheme.secondary else TextSecondary,
                    )
                }
            }
        }
        Text(
            text = workout.name,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.workouts_tap_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun WorkoutDetailsDialog(
    workout: WorkoutUi,
    isEditing: Boolean,
    drafts: Map<String, ExerciseEditDraft>,
    isBusy: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onToggleEdit: () -> Unit,
    onSeriesChange: (String, String) -> Unit,
    onNameChange: (String, String) -> Unit,
    onRepetitionsChange: (String, String) -> Unit,
    onRestChange: (String, String) -> Unit,
    onTransitionChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onAddExercise: (String, String, String, String, String, String) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onMoveExercise: (String, Int) -> Unit,
    onArchiveWorkout: () -> Unit,
) {
    var addingExercise by remember(workout.id) { mutableStateOf(false) }
    var exercisePendingDeletion by remember(workout.id) { mutableStateOf<WorkoutExerciseUi?>(null) }
    var confirmingArchive by remember(workout.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "${workout.code} - ${workout.name}", modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleEdit, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.workouts_edit),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                IconButton(onClick = { confirmingArchive = true }, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Rounded.Archive,
                        contentDescription = stringResource(R.string.workouts_archive_workout),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                workout.exercises.forEachIndexed { index, exercise ->
                    if (isEditing) {
                        ExerciseEditorBlock(
                            exercise = exercise,
                            draft = drafts[exercise.id],
                            enabled = !isBusy,
                            canMoveUp = index > 0,
                            canMoveDown = index < workout.exercises.lastIndex,
                            onNameChange = onNameChange,
                            onRepetitionsChange = onRepetitionsChange,
                            onSeriesChange = onSeriesChange,
                            onRestChange = onRestChange,
                            onTransitionChange = onTransitionChange,
                            onMoveUp = { onMoveExercise(exercise.id, -1) },
                            onMoveDown = { onMoveExercise(exercise.id, 1) },
                            onDelete = { exercisePendingDeletion = exercise },
                        )
                    } else {
                        ExerciseBlock(exercise)
                    }
                }
                if (isEditing) {
                    OrdoActionButton(
                        text = stringResource(R.string.workouts_add_exercise),
                        icon = Icons.Rounded.Add,
                        onClick = { addingExercise = true },
                        enabled = !isBusy,
                        secondary = true,
                    )
                    OrdoActionButton(
                        text = stringResource(R.string.workouts_save_changes),
                        icon = Icons.Rounded.Save,
                        onClick = onSave,
                        enabled = !isBusy && !isSaving,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.workouts_close)) }
        },
        containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
    )
    if (addingExercise) {
        AddExerciseDialog(
            isBusy = isBusy,
            onDismiss = { addingExercise = false },
            onSave = { name, series, repetitions, rest, transition ->
                onAddExercise(workout.id, name, series, repetitions, rest, transition)
                addingExercise = false
            },
        )
    }
    exercisePendingDeletion?.let { exercise ->
        AlertDialog(
            onDismissRequest = { exercisePendingDeletion = null },
            title = { Text(stringResource(R.string.workouts_delete_exercise)) },
            text = { Text(stringResource(R.string.workouts_delete_exercise_body, exercise.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteExercise(exercise.id)
                        exercisePendingDeletion = null
                    },
                    enabled = !isBusy,
                ) { Text(stringResource(R.string.workouts_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { exercisePendingDeletion = null }) {
                    Text(stringResource(R.string.workouts_close))
                }
            },
            containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
        )
    }
    if (confirmingArchive) {
        AlertDialog(
            onDismissRequest = { confirmingArchive = false },
            title = { Text(stringResource(R.string.workouts_archive_workout)) },
            text = { Text(stringResource(R.string.workouts_archive_workout_body, workout.name)) },
            confirmButton = {
                TextButton(onClick = onArchiveWorkout, enabled = !isBusy) {
                    Text(stringResource(R.string.workouts_archive_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingArchive = false }) {
                    Text(stringResource(R.string.workouts_close))
                }
            },
            containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
        )
    }
}

@Composable
private fun AddWorkoutDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workouts_add_workout)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text(stringResource(R.string.workouts_workout_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.workouts_add_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workouts_close)) } },
        containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
    )
}

@Composable
private fun ExerciseBlock(exercise: WorkoutExerciseUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OrdoInlineIcon(icon = Icons.Rounded.Repeat, tint = AccentBlue)
            Text(
                text = stringResource(
                    R.string.workouts_exercise_line,
                    exercise.seriesCount,
                    exercise.repetitions,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OrdoInlineIcon(icon = Icons.Rounded.Timer)
            Text(
                text = stringResource(
                    R.string.workouts_rest_line,
                    exercise.restBetweenSetsSeconds,
                    exercise.transitionRestSeconds,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        OrdoStatusBadge(
            label = trackingLabel(exercise.trackingMode),
            tone = StatusTone.Pending,
        )
    }
}

@Composable
private fun ExerciseEditorBlock(
    exercise: WorkoutExerciseUi,
    draft: ExerciseEditDraft?,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onNameChange: (String, String) -> Unit,
    onRepetitionsChange: (String, String) -> Unit,
    onSeriesChange: (String, String) -> Unit,
    onRestChange: (String, String) -> Unit,
    onTransitionChange: (String, String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = BgInput.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = exercise.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = canMoveUp && enabled) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.workouts_move_exercise_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown && enabled) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.workouts_move_exercise_down))
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.workouts_delete_exercise), tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = draft?.name.orEmpty(),
                onValueChange = { onNameChange(exercise.id, it) },
                enabled = enabled,
                label = { Text(stringResource(R.string.workouts_edit_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField(
                    label = stringResource(R.string.workouts_edit_series),
                    value = draft?.seriesCount.orEmpty(),
                    onValueChange = { onSeriesChange(exercise.id, it) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.workouts_edit_repetitions),
                    value = draft?.repetitions.orEmpty(),
                    onValueChange = { onRepetitionsChange(exercise.id, it) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.workouts_edit_rest),
                    value = draft?.restBetweenSetsSeconds.orEmpty(),
                    onValueChange = { onRestChange(exercise.id, it) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = stringResource(R.string.workouts_edit_next),
                    value = draft?.transitionRestSeconds.orEmpty(),
                    onValueChange = { onTransitionChange(exercise.id, it) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddExerciseDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var series by remember { mutableStateOf("3") }
    var repetitions by remember { mutableStateOf("8-12") }
    var rest by remember { mutableStateOf("60") }
    var transition by remember { mutableStateOf("90") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workouts_add_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text(stringResource(R.string.workouts_edit_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    NumberField(stringResource(R.string.workouts_edit_series), series, { series = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.workouts_edit_repetitions), repetitions, { repetitions = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    NumberField(stringResource(R.string.workouts_edit_rest), rest, { rest = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.workouts_edit_next), transition, { transition = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, series, repetitions, rest, transition) },
                enabled = name.isNotBlank() && repetitions.isNotBlank() && !isBusy,
            ) { Text(stringResource(R.string.workouts_add_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.workouts_close)) } },
        containerColor = com.vitorkrichter.ordo.core.designsystem.theme.BgCard2,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

@Composable
private fun trackingLabel(mode: TrackingMode): String = when (mode) {
    TrackingMode.NONE -> stringResource(R.string.workouts_tracking_none)
    TrackingMode.REPETITIONS -> stringResource(R.string.workouts_tracking_reps)
    TrackingMode.LOAD_AND_REPETITIONS -> stringResource(R.string.workouts_tracking_load_reps)
}
