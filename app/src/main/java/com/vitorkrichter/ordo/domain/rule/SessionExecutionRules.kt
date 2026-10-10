package com.vitorkrichter.ordo.domain.rule

import com.vitorkrichter.ordo.domain.model.TrackingMode
import kotlin.math.ceil

object SessionExecutionRules {
    fun isPerformanceValid(
        trackingMode: TrackingMode,
        repetitions: String,
        loadKg: String,
    ): Boolean = when (trackingMode) {
        TrackingMode.NONE -> true
        TrackingMode.REPETITIONS -> repetitions.toIntOrNull()?.let { it > 0 } == true
        TrackingMode.LOAD_AND_REPETITIONS -> {
            repetitions.toIntOrNull()?.let { it > 0 } == true &&
                loadKg.replace(',', '.').toDoubleOrNull()?.let { it >= 0.0 } == true
        }
    }

    fun remainingRestSeconds(endsAtEpochMillis: Long, nowEpochMillis: Long): Int {
        return ceil((endsAtEpochMillis - nowEpochMillis) / 1_000.0)
            .toInt()
            .coerceAtLeast(0)
    }
}
