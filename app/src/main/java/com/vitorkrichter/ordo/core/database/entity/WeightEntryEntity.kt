package com.vitorkrichter.ordo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "weight_entries",
    indices = [
        Index(value = ["measured_at"]),
        Index(value = ["created_at"]),
    ],
)
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "measured_at") val measuredAt: Instant,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
