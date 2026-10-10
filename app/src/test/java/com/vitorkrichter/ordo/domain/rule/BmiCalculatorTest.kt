package com.vitorkrichter.ordo.domain.rule

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BmiCalculatorTest {
    private val queryDate = LocalDate.of(2026, 10, 10)
    private val adultBirthDate = LocalDate.of(2006, 10, 10)

    @Test
    fun exactTwentiethBirthdayIsAdult() {
        val result = BmiCalculator.calculate(70.0, 175.0, adultBirthDate, queryDate)

        assertTrue(result.isAvailable)
        assertEquals(BmiCategory.HEALTHY, result.category)
        assertEquals(70.0 / 1.75 / 1.75, result.bmi!!, 0.000001)
    }

    @Test
    fun beforeTwentiethBirthdayIsUnavailable() {
        val result = BmiCalculator.calculate(
            weightKg = 70.0,
            heightCm = 175.0,
            dateOfBirth = LocalDate.of(2006, 10, 11),
            onDate = queryDate,
        )

        assertFalse(result.isAvailable)
        assertEquals(BmiUnavailableReason.UNDER_20, result.unavailableReason)
        assertNull(result.bmi)
        assertNull(result.category)
    }

    @Test
    fun missingDataIsUnavailable() {
        val missingBirth = BmiCalculator.calculate(70.0, 175.0, null, queryDate)
        val missingWeight = BmiCalculator.calculate(null, 175.0, adultBirthDate, queryDate)
        val missingHeight = BmiCalculator.calculate(70.0, null, adultBirthDate, queryDate)

        assertEquals(BmiUnavailableReason.MISSING_DATA, missingBirth.unavailableReason)
        assertEquals(BmiUnavailableReason.MISSING_DATA, missingWeight.unavailableReason)
        assertEquals(BmiUnavailableReason.MISSING_DATA, missingHeight.unavailableReason)
    }

    @Test
    fun invalidMeasurementsAndFutureBirthAreUnavailable() {
        val zeroWeight = BmiCalculator.calculate(0.0, 175.0, adultBirthDate, queryDate)
        val negativeHeight = BmiCalculator.calculate(70.0, -1.0, adultBirthDate, queryDate)
        val nanWeight = BmiCalculator.calculate(Double.NaN, 175.0, adultBirthDate, queryDate)
        val futureBirth = BmiCalculator.calculate(70.0, 175.0, queryDate.plusDays(1), queryDate)

        assertEquals(BmiUnavailableReason.INVALID_DATA, zeroWeight.unavailableReason)
        assertEquals(BmiUnavailableReason.INVALID_DATA, negativeHeight.unavailableReason)
        assertEquals(BmiUnavailableReason.INVALID_DATA, nanWeight.unavailableReason)
        assertEquals(BmiUnavailableReason.INVALID_DATA, futureBirth.unavailableReason)
    }

    @Test
    fun categoriesUseTheRequestedAdultBoundaries() {
        assertEquals(BmiCategory.UNDERWEIGHT, resultForBmi(18.499).category)
        assertEquals(BmiCategory.HEALTHY, resultForBmi(18.5).category)
        assertEquals(BmiCategory.OVERWEIGHT, resultForBmi(25.0).category)
        assertEquals(BmiCategory.OBESITY_CLASS_I, resultForBmi(30.0).category)
        assertEquals(BmiCategory.OBESITY_CLASS_II, resultForBmi(35.0).category)
        assertEquals(BmiCategory.OBESITY_CLASS_III, resultForBmi(40.0).category)
    }

    private fun resultForBmi(bmi: Double): BmiResult =
        BmiCalculator.calculate(
            weightKg = bmi,
            heightCm = 100.0,
            dateOfBirth = adultBirthDate,
            onDate = queryDate,
        )
}
