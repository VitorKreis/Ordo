package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "hydration_settings")
data class HydrationSettingsEntity(
    @PrimaryKey val id: String = DEFAULT_SETTINGS_ID,
    @ColumnInfo(name = "target_ml") val targetMl: Int = DEFAULT_TARGET_ML,
    @ColumnInfo(name = "target_mode") val targetMode: String = DEFAULT_TARGET_MODE,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
) {
    companion object {
        const val DEFAULT_SETTINGS_ID = "default"
        const val DEFAULT_TARGET_ML = 2_000
        const val DEFAULT_TARGET_MODE = "DEFAULT_REFERENCE"
        const val CUSTOM_TARGET_MODE = "CUSTOM"
        const val WEIGHT_35_ML_KG_TARGET_MODE = "WEIGHT_35_ML_KG"
    }
}
