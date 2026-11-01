package com.hostelattendance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadRecord(record: AppUpdateHistoryEntity): Long

    @Query("SELECT * FROM app_update_history ORDER BY downloadedAtMillis DESC")
    fun getAllDownloadHistory(): Flow<List<AppUpdateHistoryEntity>>

    @Query("SELECT * FROM app_update_history ORDER BY downloadedAtMillis DESC LIMIT 1")
    suspend fun getLatestDownload(): AppUpdateHistoryEntity?

    @Query("SELECT * FROM app_update_history ORDER BY downloadedAtMillis DESC LIMIT 1")
    fun getLatestDownloadFlow(): Flow<AppUpdateHistoryEntity?>

    @Query("DELETE FROM app_update_history")
    suspend fun clearHistory()
}
