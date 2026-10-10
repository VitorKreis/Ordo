package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = DEFAULT_PROFILE_ID,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "birth_date") val birthDate: LocalDate? = null,
    @ColumnInfo(name = "height_cm") val heightCm: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val DEFAULT_PROFILE_ID = "default"
    }
}
