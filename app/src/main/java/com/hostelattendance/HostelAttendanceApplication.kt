package com.hostelattendance

import android.app.Application
import androidx.room.Room
import com.hostelattendance.data.local.HostelDatabase
import com.hostelattendance.data.repository.RoomAttendanceRepository
import com.hostelattendance.data.repository.RoomStudentRepository
import com.hostelattendance.data.seed.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HostelAttendanceApplication : Application() {
    private val appScope = CoroutineScope(Dispatchers.IO)

    val database: HostelDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            HostelDatabase::class.java,
            "hostel-attendance.db",
        ).addMigrations(HostelDatabase.MIGRATION_1_2, HostelDatabase.MIGRATION_2_3).build()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            com.styropyr0.prismal.internal.PrismalAgslAssets.bind(applicationContext.assets)
        } catch (_: Exception) {}

        // Initialize automatic background phone storage persistence
        com.hostelattendance.data.backup.PersistentBackupManager.initialize(this, database)

        appScope.launch {
            try {
                // 1. Try restoring from persistent phone storage (hostel_attendance_local_data) if reinstalled
                val restoreResult = com.hostelattendance.data.backup.PersistentBackupManager.restoreIfAvailable(
                    this@HostelAttendanceApplication,
                    database,
                )
                android.util.Log.i("HostelApp", "Persistent storage restore on launch: $restoreResult")

                // 2. Seed database if needed
                DatabaseSeeder.seedIfNeeded(this@HostelAttendanceApplication, database, "cgitr-boys-hostel")

                // 3. Ensure phone storage backup is updated
                com.hostelattendance.data.backup.PersistentBackupManager.performBackup(
                    this@HostelAttendanceApplication,
                    database,
                )
            } catch (e: Exception) {
                android.util.Log.w("HostelApp", "Startup storage error: ${e.message}")
            }
        }
    }

    val attendanceRepository: RoomAttendanceRepository by lazy {
        RoomAttendanceRepository(
            database = database,
            sessionDao = database.attendanceSessionDao(),
            studentDao = database.studentDao(),
            wingDao = database.wingDao(),
        )
    }

    val studentRepository: RoomStudentRepository by lazy {
        RoomStudentRepository(
            database = database,
            studentDao = database.studentDao(),
            wingDao = database.wingDao(),
        )
    }
}
