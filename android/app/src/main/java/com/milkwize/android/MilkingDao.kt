package com.milkwize.android

import androidx.room.*

@Dao
interface MilkingDao {
    @Insert
    suspend fun insert(event: LocalEvent)

    @Query("SELECT * FROM local_milking_events WHERE isSynced = 0")
    suspend fun getUnsyncedEvents(): List<LocalEvent>

    @Update
    suspend fun update(event: LocalEvent)

    @Query("SELECT * FROM local_milking_events ORDER BY id DESC")
    fun getAllLocally(): kotlinx.coroutines.flow.Flow<List<LocalEvent>>
}