package com.ryzix.player.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun getAllHistory(): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE videoPath = :path LIMIT 1")
    suspend fun getHistoryForVideo(path: String): WatchHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: WatchHistory)

    @Query("DELETE FROM watch_history WHERE videoPath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM watch_history")
    suspend fun getCount(): Int
}
