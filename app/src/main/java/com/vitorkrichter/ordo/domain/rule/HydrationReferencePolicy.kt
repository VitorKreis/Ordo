package com.vitorkrichter.ordo.domain.rule

import kotlin.math.floor

enum class HydrationReferenceSource {
    AUTOMATIC_WEIGHT,
    DEFAULT_REFERENCE,
    CUSTOM,
}

data class HydrationReference(
    val millilitersPerDay: Int,
    val source: HydrationReferenceSource,
) {
    val milliliters: Int
        get() = millilitersPerDay

    val targetMl: Int
        get() = millilitersPerDay
}

/**
 * Selects an operational hydration reference only. The 35 ml/kg value is a
 * configurable reminder, not a medical prescription or an exact physiological
 * requirement.
 */
object HydrationReferencePolicy {
    const val MILLILITERS_PER_KILOGRAM: Int = 35
    const val ROUNDING_STEP_MILLILITERS: Int = 50
    const val DEFAULT_REFERENCE_MILLILITERS: Int = 2_000

    fun automaticReference(weightKg: Double?): HydrationReference {
        val validWeight = weightKg?.takeIf(::isValidWeight)
        if (validWeight == null) {
            return HydrationReference(
                millilitersPerDay = DEFAULT_REFERENCE_MILLILITERS,
                source = HydrationReferenceSource.DEFAULT_REFERENCE,
            )
        }

        val rawMilliliters = validWeight * MILLILITERS_PER_KILOGRAM
        val rounded = floor(rawMilliliters / ROUNDING_STEP_MILLILITERS + 0.5) *
            ROUNDING_STEP_MILLILITERS
        if (!rounded.isFinite() || rounded <= 0.0 || rounded > Int.MAX_VALUE) {
            return HydrationReference(
                millilitersPerDay = DEFAULT_REFERENCE_MILLILITERS,
                source = HydrationReferenceSource.DEFAULT_REFERENCE,
            )
        }

        return HydrationReference(
            millilitersPerDay = rounded.toInt(),
            source = HydrationReferenceSource.AUTOMATIC_WEIGHT,
        )
    }

    /**
     * Resolves a stored reference after a new weight is recorded. A valid
     * CUSTOM reference wins and is therefore never replaced by a new weight.
     * Automatic/default references are recalculated from the current weight.
     */
    fun resolve(
        weightKg: Double?,
        storedReference: HydrationReference? = null,
    ): HydrationReference {
        if (storedReference?.source == HydrationReferenceSource.CUSTOM &&
            isValidCustomReference(storedReference.millilitersPerDay)
        ) {
            return storedReference
        }
        return automaticReference(weightKg)
    }

    /** Applies a manual override, preserving it across future weight changes. */
    fun customReference(millilitersPerDay: Int): HydrationReference {
        require(isValidCustomReference(millilitersPerDay)) {
            "Custom hydration reference must be greater than zero."
        }
        return HydrationReference(
            millilitersPerDay = millilitersPerDay,
            source = HydrationReferenceSource.CUSTOM,
        )
    }

    /** Convenient overload when persistence stores only the manual value. */
    fun resolve(
        weightKg: Double?,
        customMillilitersPerDay: Int?,
    ): HydrationReference = customMillilitersPerDay
        ?.takeIf(::isValidCustomReference)
        ?.let(::customReference)
        ?: automaticReference(weightKg)

    private fun isValidWeight(weightKg: Double): Boolean =
        weightKg.isFinite() && weightKg > 0.0

    private fun isValidCustomReference(millilitersPerDay: Int): Boolean =
        millilitersPerDay > 0
}
