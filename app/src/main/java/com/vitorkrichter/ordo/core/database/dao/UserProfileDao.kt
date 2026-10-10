package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vitorkrichter.ordo.core.database.entity.UserProfileEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = :profileId LIMIT 1")
    fun observeById(profileId: String = UserProfileEntity.DEFAULT_PROFILE_ID): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :profileId LIMIT 1")
    suspend fun findById(profileId: String = UserProfileEntity.DEFAULT_PROFILE_ID): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserProfileEntity)

    @Transaction
    suspend fun getOrCreateDefault(now: Instant): UserProfileEntity {
        findById()?.let { return it }
        val entity = UserProfileEntity(
            displayName = null,
            birthDate = null,
            heightCm = null,
            createdAt = now,
            updatedAt = now,
        )
        upsert(entity)
        return entity
    }
}
