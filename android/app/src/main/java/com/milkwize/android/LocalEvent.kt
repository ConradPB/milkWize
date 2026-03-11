package com.milkwize.android

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_milking_events")
data class LocalEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cowId: String,
    val ownerId: String,
    val recordedBy: String? = null,
    val milkLiters: Double,
    val timestamp: String,
    val isSynced: Boolean = false // This is the magic flag!
)