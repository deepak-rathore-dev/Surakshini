package com.drxrathore.surakshini

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RouteDao {
    @Insert
    suspend fun insertRoutePoint(routeData: RouteData)

    // ai will call this later to get the entire history to learn the normal routes
    @Query("SELECT * FROM route_history ORDER BY timestamp ASC")
    suspend fun getAllRouteHistory(): List<RouteData>

    @Insert
    suspend fun saveLearnedRoute(learnedRoute: LearnedRoute)

    @Query("SELECT * FROM learned_safe_routes")
    suspend fun getPermanentRoutes(): List<LearnedRoute>

    @Query("DELETE FROM route_history WHERE timestamp < :threshold")
    suspend fun deleteOldData(threshold: Long)

    @Query("SELECT COUNT(*) FROM route_history")
    suspend fun getDataCount(): Int

    @Delete
    suspend fun deleteLearnedRoute(route: LearnedRoute)
}