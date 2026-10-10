package com.vitorkrichter.ordo.core.database

import com.vitorkrichter.ordo.domain.model.WorkoutSessionStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class OrdoTypeConvertersTest {
    private val converters = OrdoTypeConverters()

    @Test
    fun instantRoundTripsThroughEpochMillis() {
        val instant = Instant.parse("2026-08-04T12:34:56Z")

        assertEquals(instant, converters.epochMillisToInstant(converters.instantToEpochMillis(instant)))
    }

    @Test
    fun localDateRoundTripsThroughEpochDay() {
        val date = LocalDate.parse("2026-08-04")

        assertEquals(date, converters.epochDayToLocalDate(converters.localDateToEpochDay(date)))
    }

    @Test
    fun dayOfWeekUsesIsoValue() {
        assertEquals(1, converters.dayOfWeekToIsoValue(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.SUNDAY, converters.isoValueToDayOfWeek(7))
    }

    @Test
    fun enumConvertersUseStableNames() {
        assertEquals("COMPLETED", converters.workoutSessionStatusToString(WorkoutSessionStatus.COMPLETED))
        assertEquals(WorkoutSessionStatus.PARTIALLY_COMPLETED, converters.stringToWorkoutSessionStatus("PARTIALLY_COMPLETED"))
    }
}
