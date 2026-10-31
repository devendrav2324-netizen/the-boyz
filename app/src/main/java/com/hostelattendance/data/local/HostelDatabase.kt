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
    ],
    version = 2,
    exportSchema = true,
)
abstract class HostelDatabase : RoomDatabase() {
    abstract fun attendanceSessionDao(): AttendanceSessionDao
    abstract fun studentDao(): StudentDao
    abstract fun wingDao(): WingDao

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
    }
}
