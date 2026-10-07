// Room entity representing local Eye Guard safety and lifecycle events.
// Persisted locally without cloud transmission in compliance with zero-network privacy architecture.
package com.visionguard.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "eye_guard_events")
data class EyeGuardEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val eventType: String, // "TOO_CLOSE", "RECOVERED", "CALIBRATION", "PAUSED", "RESUMED", "NO_FACE_POWER_SAVING"
    val distanceCm: Float?,
    val detail: String
)
