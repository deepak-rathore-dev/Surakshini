package com.drxrathore.surakshini

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_safe_routes")
data class LearnedRoute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routeName: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val safeRadiusMeters: Double
)