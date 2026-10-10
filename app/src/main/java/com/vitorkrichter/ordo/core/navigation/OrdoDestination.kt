package com.vitorkrichter.ordo.core.navigation

import androidx.annotation.StringRes
import com.vitorkrichter.ordo.R

enum class OrdoDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
) {
    Today("today", R.string.nav_today),
    Workouts("workouts", R.string.nav_workouts),
    Progress("progress", R.string.nav_progress),
    History("history", R.string.nav_history),
    Profile("profile", R.string.nav_my_profile),
}
