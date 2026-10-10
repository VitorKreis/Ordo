package com.vitorkrichter.ordo.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vitorkrichter.ordo.core.designsystem.theme.AccentBlue
import com.vitorkrichter.ordo.core.designsystem.theme.BgApp
import com.vitorkrichter.ordo.core.designsystem.theme.BgCard2
import com.vitorkrichter.ordo.core.designsystem.theme.Spacing
import com.vitorkrichter.ordo.core.designsystem.theme.TextMuted
import com.vitorkrichter.ordo.core.designsystem.theme.TextPrimary
import com.vitorkrichter.ordo.core.designsystem.theme.TextSecondary
import com.vitorkrichter.ordo.feature.calendar.CalendarRoute
import com.vitorkrichter.ordo.feature.history.HistoryRoute
import com.vitorkrichter.ordo.feature.home.HomeRoute
import com.vitorkrichter.ordo.feature.importworkout.ImportWorkoutRoute
import com.vitorkrichter.ordo.feature.onboarding.OnboardingRoute
import com.vitorkrichter.ordo.feature.profile.ProfileRoute
import com.vitorkrichter.ordo.feature.progress.ProgressRoute
import com.vitorkrichter.ordo.feature.session.SessionRoute
import com.vitorkrichter.ordo.feature.workouts.WorkoutsRoute
import java.time.DayOfWeek

@Composable
fun OrdoApp() {
    val navController = rememberNavController()
    val destinations = OrdoDestination.entries
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    val currentRoute = currentDestination?.route

    Scaffold(
        containerColor = BgApp,
        bottomBar = {
            if (currentRoute != ONBOARDING_ROUTE && currentRoute != SESSION_ROUTE) {
                NavigationBar(
                    containerColor = BgCard2,
                    contentColor = TextSecondary,
                ) {
                    destinations.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(OrdoDestination.Today.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (selected) {
                                    Badge(
                                        containerColor = AccentBlue.copy(alpha = 0.26f),
                                        contentColor = TextPrimary,
                                    ) {
                                        Icon(
                                            imageVector = destination.icon(),
                                            contentDescription = stringResource(destination.labelRes),
                                            modifier = Modifier
                                                .padding(Spacing.xs)
                                                .size(20.dp),
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = destination.icon(),
                                        contentDescription = stringResource(destination.labelRes),
                                    )
                                }
                            },
                            label = { Text(text = stringResource(destination.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                indicatorColor = AccentBlue.copy(alpha = 0.14f),
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ONBOARDING_ROUTE,
            modifier = Modifier
                .fillMaxSize()
                .background(BgApp)
                .padding(innerPadding),
        ) {
            composable(ONBOARDING_ROUTE) {
                OnboardingRoute(
                    onAlreadyConfigured = {
                        navController.navigate(OrdoDestination.Today.route) {
                            popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onImportWorkout = {
                        navController.navigate(IMPORT_ROUTE) {
                            popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                        }
                    },
                    onEditablePlanCreated = {
                        navController.navigate(OrdoDestination.Workouts.route) {
                            popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(OrdoDestination.Today.route) {
                HomeRoute(
                    onOpenWorkout = {
                        navController.navigate(SESSION_ROUTE)
                    },
                )
            }
            // Kept as a non-primary route for historical calendar access.
            composable(CALENDAR_ROUTE) {
                CalendarRoute()
            }
            composable(OrdoDestination.Workouts.route) {
                WorkoutsRoute(onOpenImport = { navController.navigate(IMPORT_ROUTE) })
            }
            composable(IMPORT_ROUTE) {
                ImportWorkoutRoute()
            }
            composable(OrdoDestination.Progress.route) {
                ProgressRoute()
            }
            composable(
                route = IMPORT_WITH_DAYS_ROUTE,
                arguments = listOf(
                    navArgument(IMPORT_DAYS_ARGUMENT) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                ImportWorkoutRoute(
                    preferredWeekdays = entry.arguments
                        ?.getString(IMPORT_DAYS_ARGUMENT)
                        .orEmpty()
                        .toWeekdays(),
                )
            }
            composable(OrdoDestination.History.route) {
                HistoryRoute()
            }
            composable(OrdoDestination.Profile.route) {
                ProfileRoute()
            }
            composable(SESSION_ROUTE) {
                SessionRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun OrdoDestination.icon(): ImageVector = when (this) {
    OrdoDestination.Today -> Icons.Rounded.Today
    OrdoDestination.Workouts -> Icons.Rounded.FitnessCenter
    OrdoDestination.Progress -> Icons.Rounded.Insights
    OrdoDestination.History -> Icons.Rounded.History
    OrdoDestination.Profile -> Icons.Rounded.Person
}

private fun importRoute(weekdays: Set<DayOfWeek>): String {
    val encoded = weekdays
        .sortedBy { it.value }
        .joinToString(",") { it.value.toString() }
    return "import/onboarding/$encoded"
}

private fun String.toWeekdays(): Set<DayOfWeek>? {
    if (isBlank()) return null
    return split(",")
        .mapNotNull { value -> value.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of) }
        .toSet()
        .ifEmpty { null }
}

private const val ONBOARDING_ROUTE = "onboarding"
private const val SESSION_ROUTE = "session"
private const val CALENDAR_ROUTE = "calendar"
private const val IMPORT_ROUTE = "import"
private const val IMPORT_DAYS_ARGUMENT = "days"
private const val IMPORT_WITH_DAYS_ROUTE = "import/onboarding/{$IMPORT_DAYS_ARGUMENT}"
