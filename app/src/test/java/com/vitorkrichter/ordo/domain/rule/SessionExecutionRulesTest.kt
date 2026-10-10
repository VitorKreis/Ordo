package com.vitorkrichter.ordo.domain.rule

import com.vitorkrichter.ordo.domain.model.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExecutionRulesTest {
    @Test
    fun plannedOrEmptyValuesAreNotAcceptedAsActualPerformance() {
        assertFalse(SessionExecutionRules.isPerformanceValid(TrackingMode.REPETITIONS, "", ""))
        assertFalse(SessionExecutionRules.isPerformanceValid(TrackingMode.LOAD_AND_REPETITIONS, "12", ""))
    }

    @Test
    fun bodyweightZeroLoadIsAccepted() {
        assertTrue(SessionExecutionRules.isPerformanceValid(TrackingMode.LOAD_AND_REPETITIONS, "12", "0"))
    }

    @Test
    fun remainingTimeUsesAbsoluteDeadlineAndRoundsUp() {
        assertEquals(61, SessionExecutionRules.remainingRestSeconds(61_001L, 1L))
        assertEquals(0, SessionExecutionRules.remainingRestSeconds(1_000L, 2_000L))
    }
}
