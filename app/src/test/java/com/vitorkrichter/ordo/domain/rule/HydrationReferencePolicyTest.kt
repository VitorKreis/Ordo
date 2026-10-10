package com.vitorkrichter.ordo.domain.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class HydrationReferencePolicyTest {
    @Test
    fun validWeightsUseThirtyFiveMillilitersPerKilogram() {
        assertReference(2_450, HydrationReferenceSource.AUTOMATIC_WEIGHT, 70.0)
        assertReference(2_800, HydrationReferenceSource.AUTOMATIC_WEIGHT, 80.0)
    }

    @Test
    fun missingOrInvalidWeightUsesDefaultReference() {
        assertReference(2_000, HydrationReferenceSource.DEFAULT_REFERENCE, null)
        assertReference(2_000, HydrationReferenceSource.DEFAULT_REFERENCE, 0.0)
        assertReference(2_000, HydrationReferenceSource.DEFAULT_REFERENCE, -70.0)
        assertReference(2_000, HydrationReferenceSource.DEFAULT_REFERENCE, Double.NaN)
    }

    @Test
    fun weightIsRoundedToTheNearestFiftyMilliliters() {
        assertEquals(2_450, HydrationReferencePolicy.automaticReference(70.01).millilitersPerDay)
        assertEquals(2_450, HydrationReferencePolicy.automaticReference(69.99).millilitersPerDay)
        assertEquals(2_500, HydrationReferencePolicy.automaticReference(70.72).millilitersPerDay)
    }

    @Test
    fun manualOverrideSurvivesNewWeight() {
        val custom = HydrationReferencePolicy.customReference(3_333)

        val resolved = HydrationReferencePolicy.resolve(80.0, custom)
        val resolvedFromStoredValue = HydrationReferencePolicy.resolve(80.0, 3_333)

        assertEquals(custom, resolved)
        assertEquals(custom, resolvedFromStoredValue)
    }

    @Test
    fun automaticAndDefaultReferencesCanBeRecomputedAfterWeightChanges() {
        val automatic = HydrationReferencePolicy.automaticReference(70.0)
        val recomputed = HydrationReferencePolicy.resolve(80.0, automatic)
        val fromDefault = HydrationReferencePolicy.resolve(80.0, HydrationReferencePolicy.automaticReference(null))

        assertEquals(2_800, recomputed.millilitersPerDay)
        assertEquals(HydrationReferenceSource.AUTOMATIC_WEIGHT, recomputed.source)
        assertEquals(2_800, fromDefault.millilitersPerDay)
    }

    @Test
    fun invalidCustomOverrideIsNotStoredAsCustom() {
        val resolved = HydrationReferencePolicy.resolve(70.0, 0)

        assertEquals(2_450, resolved.millilitersPerDay)
        assertEquals(HydrationReferenceSource.AUTOMATIC_WEIGHT, resolved.source)
    }

    private fun assertReference(
        milliliters: Int,
        source: HydrationReferenceSource,
        weightKg: Double?,
    ) {
        val reference = HydrationReferencePolicy.automaticReference(weightKg)
        assertEquals(milliliters, reference.millilitersPerDay)
        assertEquals(source, reference.source)
    }
}
