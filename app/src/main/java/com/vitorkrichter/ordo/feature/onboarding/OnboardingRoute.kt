package com.vitorkrichter.ordo.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorkrichter.ordo.R
import com.vitorkrichter.ordo.core.designsystem.component.OrdoActionButton
import com.vitorkrichter.ordo.core.designsystem.component.OrdoCard
import com.vitorkrichter.ordo.core.designsystem.component.OrdoIconBubble
import com.vitorkrichter.ordo.core.designsystem.component.OrdoStatusBadge
import com.vitorkrichter.ordo.core.designsystem.component.StatusTone
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary

@Composable
fun OnboardingRoute(
    onAlreadyConfigured: () -> Unit,
    onImportWorkout: () -> Unit,
    onEditablePlanCreated: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageRes = state.messageRes

    LaunchedEffect(state.hasPlan, state.createdPlan, state.isBusy) {
        when {
            state.createdPlan -> onEditablePlanCreated()
            state.hasPlan && !state.isBusy -> onAlreadyConfigured()
        }
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
            OrdoIconBubble(icon = Icons.Rounded.FitnessCenter)
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
            )
            Text(
                text = stringResource(R.string.onboarding_body),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )

            if (state.isLoading) {
                OrdoCard {
                    Text(
                        text = stringResource(R.string.home_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            } else {
                OrdoCard {
                    OrdoStatusBadge(
                        label = stringResource(R.string.onboarding_step_workouts),
                        tone = StatusTone.Info,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_workouts_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_workouts_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    OrdoActionButton(
                        text = stringResource(R.string.onboarding_import),
                        icon = Icons.Rounded.UploadFile,
                        onClick = onImportWorkout,
                        enabled = !state.isBusy,
                    )
                    OrdoActionButton(
                        text = stringResource(R.string.onboarding_create_editable),
                        icon = Icons.Rounded.EditNote,
                        onClick = viewModel::createEditablePlan,
                        enabled = !state.isBusy,
                        secondary = true,
                    )
                }
            }
        }
    }
}
