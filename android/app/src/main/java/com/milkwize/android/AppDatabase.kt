package com.milkwize.android

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(entities = [LocalEvent::class, LocalCow::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun milkingDao(): MilkingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "milkwize_db"
                ).fallbackToDestructiveMigration()
                .build().also { instance = it }
            }
        }
    }
}
