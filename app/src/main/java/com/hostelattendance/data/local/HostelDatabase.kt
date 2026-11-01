package com.hostelattendance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WingEntity::class,
        StudentEntity::class,
        AttendanceSessionEntity::class,
        AttendanceRecordEntity::class,
        UserProfileCacheEntity::class,
        PendingOperationEntity::class,
        SyncStateEntity::class,
        AuditEventCacheEntity::class,
        AppUpdateHistoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class HostelDatabase : RoomDatabase() {
    abstract fun attendanceSessionDao(): AttendanceSessionDao
    abstract fun studentDao(): StudentDao
    abstract fun wingDao(): WingDao
    abstract fun appUpdateDao(): AppUpdateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE attendance_records ADD COLUMN studentNameSnapshot TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    """
                    UPDATE attendance_records
                    SET studentNameSnapshot = COALESCE(
                        (SELECT studentName FROM students WHERE students.studentId = attendance_records.studentId),
                        ''
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_update_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `versionName` TEXT NOT NULL,
                        `versionCode` INTEGER NOT NULL,
                        `downloadedAtMillis` INTEGER NOT NULL,
                        `formattedDate` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `apkSourceUrl` TEXT NOT NULL,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_update_history_downloadedAtMillis` ON `app_update_history` (`downloadedAtMillis`)",
                )
            }
        }
    }
}
