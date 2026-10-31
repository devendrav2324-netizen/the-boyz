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
        ).addMigrations(HostelDatabase.MIGRATION_1_2).build()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            com.styropyr0.prismal.internal.PrismalAgslAssets.bind(applicationContext.assets)
        } catch (_: Exception) {}
        appScope.launch {
            try {
                DatabaseSeeder.seedIfNeeded(this@HostelAttendanceApplication, database, "local-installation")
            } catch (_: Exception) {}
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
