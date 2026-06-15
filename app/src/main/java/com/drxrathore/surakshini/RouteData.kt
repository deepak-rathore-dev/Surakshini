package com.drxrathore.surakshini

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_history")
data class RouteData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val isNightTime: Boolean,

    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val accelTotal: Float,

    val isStruggleDetected: Boolean
)