package com.vitorkrichter.ordo.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vitorkrichter.ordo.core.database.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {
    @Query("SELECT * FROM weight_entries ORDER BY measured_at DESC, id DESC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY measured_at DESC, id DESC")
    suspend fun listAll(): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries WHERE id = :entryId LIMIT 1")
    suspend fun findById(entryId: String): WeightEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WeightEntryEntity)

    @Update
    suspend fun update(entity: WeightEntryEntity)

    @Delete
    suspend fun delete(entity: WeightEntryEntity)

    @Query("DELETE FROM weight_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: String): Int

    @Query("SELECT * FROM weight_entries ORDER BY measured_at DESC, id DESC LIMIT 1")
    suspend fun latest(): WeightEntryEntity?
}
