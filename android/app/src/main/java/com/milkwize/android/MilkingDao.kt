package com.milkwize.android

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MilkingDao {
    @Insert
    suspend fun insert(event: LocalEvent): Long

    @Update
    suspend fun update(event: LocalEvent): Int

    @Delete
    suspend fun delete(event: LocalEvent): Int

    @Query("SELECT * FROM local_milking_events WHERE isSynced = 0 AND ownerId = :userId")
    suspend fun getUnsyncedEvents(userId: String): List<LocalEvent>

    @Query("SELECT * FROM local_milking_events WHERE ownerId = :userId ORDER BY timestamp DESC")
    fun getAllLocally(userId: String): Flow<List<LocalEvent>>

    @Query("SELECT COUNT(*) FROM local_milking_events WHERE isSynced = 0 AND ownerId = :userId")
    fun getUnsyncedCount(userId: String): Flow<Int>

    @Query("SELECT * FROM local_milking_events WHERE isSynced = 0 AND ownerId = :userId")
    suspend fun getAllUnsynced(userId: String): List<LocalEvent>
}