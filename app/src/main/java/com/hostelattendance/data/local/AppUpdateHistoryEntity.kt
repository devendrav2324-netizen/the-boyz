package com.hostelattendance.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks APK downloads and updates in local SQLite database.
 */
@Entity(
    tableName = "app_update_history",
    indices = [Index(value = ["downloadedAtMillis"])],
)
data class AppUpdateHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val versionName: String,
    val versionCode: Int,
    val downloadedAtMillis: Long,
    val formattedDate: String,
    val fileSizeBytes: Long,
    val apkSourceUrl: String,
    val status: String, // "DOWNLOADED", "INSTALLING", "COMPLETED"
)
