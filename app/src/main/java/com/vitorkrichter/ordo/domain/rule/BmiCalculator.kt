package com.vitorkrichter.ordo.domain.rule

import java.time.LocalDate
import java.time.Period

enum class BmiCategory {
    UNDERWEIGHT,
    HEALTHY,
    OVERWEIGHT,
    OBESITY_CLASS_I,
    OBESITY_CLASS_II,
    OBESITY_CLASS_III,
}

enum class BmiUnavailableReason {
    MISSING_DATA,
    INVALID_DATA,
    UNDER_20,
}

data class BmiResult(
    val bmi: Double?,
    val category: BmiCategory?,
    val unavailableReason: BmiUnavailableReason? = null,
) {
    val isAvailable: Boolean
        get() = bmi != null && category != null && unavailableReason == null
}

/** Pure adult BMI calculation; it does not read a clock or Android state. */
object BmiCalculator {
    const val ADULT_AGE_YEARS: Int = 20

    fun calculate(
        weightKg: Double?,
        heightCm: Double?,
        dateOfBirth: LocalDate?,
        onDate: LocalDate,
    ): BmiResult {
        if (weightKg == null || heightCm == null || dateOfBirth == null) {
            return unavailable(BmiUnavailableReason.MISSING_DATA)
        }
        if (!weightKg.isFinite() || weightKg <= 0.0 ||
            !heightCm.isFinite() || heightCm <= 0.0 ||
            dateOfBirth.isAfter(onDate)
        ) {
            return unavailable(BmiUnavailableReason.INVALID_DATA)
        }

        val age = Period.between(dateOfBirth, onDate).years
        if (age < ADULT_AGE_YEARS) {
            return unavailable(BmiUnavailableReason.UNDER_20)
        }

        val heightMeters = heightCm / 100.0
        val bmi = weightKg / (heightMeters * heightMeters)
        if (!bmi.isFinite()) {
            return unavailable(BmiUnavailableReason.INVALID_DATA)
        }

        return BmiResult(
            bmi = bmi,
            category = when {
                bmi < 18.5 -> BmiCategory.UNDERWEIGHT
                bmi < 25.0 -> BmiCategory.HEALTHY
                bmi < 30.0 -> BmiCategory.OVERWEIGHT
                bmi < 35.0 -> BmiCategory.OBESITY_CLASS_I
                bmi < 40.0 -> BmiCategory.OBESITY_CLASS_II
                else -> BmiCategory.OBESITY_CLASS_III
            },
        )
    }

    fun calculate(input: BmiInput): BmiResult = calculate(
        weightKg = input.weightKg,
        heightCm = input.heightCm,
        dateOfBirth = input.dateOfBirth,
        onDate = input.onDate,
    )

    private fun unavailable(reason: BmiUnavailableReason): BmiResult = BmiResult(
        bmi = null,
        category = null,
        unavailableReason = reason,
    )
}

data class BmiInput(
    val weightKg: Double?,
    val heightCm: Double?,
    val dateOfBirth: LocalDate?,
    val onDate: LocalDate,
)
