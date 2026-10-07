// Data Access Object for querying and recording local safety events.
// Provides reactive Flow streams for UI observation and dashboard metric aggregation.
package com.visionguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EyeGuardEventDao {
    @Insert
    suspend fun insert(event: EyeGuardEventEntity)

    @Query("SELECT * FROM eye_guard_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 30): Flow<List<EyeGuardEventEntity>>

    @Query("SELECT COUNT(*) FROM eye_guard_events WHERE eventType = :eventType")
    suspend fun getCountByEventType(eventType: String): Int

    @Query("SELECT COUNT(*) FROM eye_guard_events WHERE eventType = :eventType AND timestamp >= :sinceTimestamp")
    fun getCountSince(eventType: String, sinceTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM eye_guard_events WHERE eventType = :eventType AND timestamp >= :sinceTimestamp")
    suspend fun getCountSinceDirect(eventType: String, sinceTimestamp: Long): Int

    @Query("DELETE FROM eye_guard_events")
    suspend fun clearAll()
}
