// Local offline Room database instance for VisionGuard.
// Stores derived safety events and telemetry strictly on-device without remote synchronization.
package com.visionguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EyeGuardEventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eyeGuardEventDao(): EyeGuardEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "visionguard.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
