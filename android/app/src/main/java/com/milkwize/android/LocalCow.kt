package com.milkwize.android

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_cows")
data class LocalCow(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val breed: String? = null
)
