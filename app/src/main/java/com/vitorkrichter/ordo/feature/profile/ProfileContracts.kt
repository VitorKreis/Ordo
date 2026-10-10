package com.vitorkrichter.ordo.feature.profile

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Boundary consumed by the profile UI. The data layer can adapt its own
 * repository models to these small UI models when the health persistence
 * contract is integrated.
 */
interface ProfileRepository {
    fun observeProfile(): Flow<ProfileSnapshot>

    suspend fun saveProfile(
        displayName: String?,
        birthDate: LocalDate?,
        heightCm: Double?,
    )

    fun observeWeightEntries(): Flow<List<WeightEntryUi>>

    suspend fun addWeight(measuredAt: Instant, weightKg: Double)

    suspend fun updateWeight(id: String, measuredAt: Instant, weightKg: Double)

    suspend fun deleteWeight(id: String): Int

    fun observeHydration(): Flow<HydrationUi>

    suspend fun setHydrationTarget(targetMl: Int): Int
}

data class ProfileSnapshot(
    val displayName: String? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
)

data class WeightEntryUi(
    val id: String,
    val measuredAt: Instant,
    val weightKg: Double,
)

enum class HydrationTargetSource {
    AUTOMATIC_WEIGHT,
    DEFAULT_REFERENCE,
    CUSTOM,
}

data class HydrationUi(
    val targetMl: Int = 2_000,
    val source: HydrationTargetSource = HydrationTargetSource.DEFAULT_REFERENCE,
)
