package com.milkwize.android

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
t

@Database(entities = [LocalEvent::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun milkingDao(): MilkingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "milkwize_db"
                ).build().also { instance = it }
            }
        }
    }
}