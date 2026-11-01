package com.hostelattendance.data.backup

import android.content.Context
import android.os.Environment
import android.util.Log
import com.hostelattendance.BuildConfig
import com.hostelattendance.data.local.AttendanceRecordEntity
import com.hostelattendance.data.local.AttendanceSessionEntity
import com.hostelattendance.data.local.HostelDatabase
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupMetadata(
    val lastBackupMillis: Long,
    val formattedDate: String,
    val studentCount: Int,
    val wingCount: Int,
    val sessionCount: Int,
    val recordCount: Int,
    val storagePath: String,
)

sealed interface RestoreResult {
    data class Restored(
        val studentCount: Int,
        val wingCount: Int,
        val sessionCount: Int,
        val recordCount: Int,
        val message: String,
    ) : RestoreResult

    data object AlreadyHasData : RestoreResult
    data object NoBackupFound : RestoreResult
    data class Error(val message: String) : RestoreResult
}

sealed interface BackupResult {
    data class Success(val metadata: BackupMetadata) : BackupResult
    data class Error(val message: String) : BackupResult
}

object PersistentBackupManager {

    private const val TAG = "PersistentBackup"
    private const val FOLDER_NAME = "hostel_attendance_local_data"
    private const val SNAPSHOT_FILE_NAME = "hostel_attendance_snapshot.json"
    private const val DB_BACKUP_FILE_NAME = "hostel_attendance_backup.db"
    private const val METADATA_FILE_NAME = "backup_metadata.json"

    /**
     * Resolves the persistent backup directory on phone storage that survives app uninstallation.
     */
    fun getBackupDirectory(context: Context): File {
        val candidates = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME),
            File(Environment.getExternalStorageDirectory(), FOLDER_NAME),
            File(context.getExternalFilesDir(null), FOLDER_NAME),
        )

        for (cand in candidates) {
            try {
                if (!cand.exists()) {
                    cand.mkdirs()
                }
                if (cand.exists() && cand.canWrite()) {
                    return cand
                }
            } catch (_: Exception) {}
        }

        return File(context.filesDir, FOLDER_NAME).apply { mkdirs() }
    }

    /**
     * Finds any existing backup directory across all candidate locations on the phone.
     */
    private fun findExistingBackupDir(context: Context): File? {
        val candidates = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER_NAME),
            File(Environment.getExternalStorageDirectory(), FOLDER_NAME),
            File(context.getExternalFilesDir(null), FOLDER_NAME),
            File(context.filesDir, FOLDER_NAME),
        )

        return candidates.firstOrNull { dir ->
            dir.exists() && (File(dir, SNAPSHOT_FILE_NAME).exists() || File(dir, DB_BACKUP_FILE_NAME).exists())
        }
    }

    /**
     * Reads metadata of the latest local device backup.
     */
    fun getLastBackupMetadata(context: Context): BackupMetadata? {
        val dir = findExistingBackupDir(context) ?: getBackupDirectory(context)
        val metaFile = File(dir, METADATA_FILE_NAME)
        if (!metaFile.exists()) return null

        return try {
            val json = JSONObject(metaFile.readText())
            BackupMetadata(
                lastBackupMillis = json.optLong("lastBackupMillis", 0L),
                formattedDate = json.optString("formattedDate", "Unknown"),
                studentCount = json.optInt("studentCount", 0),
                wingCount = json.optInt("wingCount", 0),
                sessionCount = json.optInt("sessionCount", 0),
                recordCount = json.optInt("recordCount", 0),
                storagePath = dir.absolutePath,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read backup metadata: ${e.message}")
            null
        }
    }

    /**
     * Performs a full backup of all Room data into 'hostel_attendance_local_data'.
     */
    suspend fun performBackup(
        context: Context,
        database: HostelDatabase,
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val backupDir = getBackupDirectory(context)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // 1. Fetch current datasets
            val wings = database.wingDao().getAllWings()
            val students = database.studentDao().getAllStudentsAllHostels()
            val sessions = database.attendanceSessionDao().getAllSessions()
            val records = database.attendanceSessionDao().getAllRecords()

            // 2. Build structured JSON Snapshot (Format is schema-safe across app versions)
            val root = JSONObject().apply {
                put("version", 1)
                put("exportedAtMillis", System.currentTimeMillis())
                put("exportedAtReadable", SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()))
                put("appVersionName", BuildConfig.VERSION_NAME)
                put("appVersionCode", BuildConfig.VERSION_CODE)

                // Wings
                val wingsArray = JSONArray()
                wings.forEach { w ->
                    wingsArray.put(JSONObject().apply {
                        put("wingId", w.wingId)
                        put("hostelId", w.hostelId)
                        put("name", w.name)
                        put("normalizedName", w.normalizedName)
                        put("isActive", w.isActive)
                        put("updatedAtMillis", w.updatedAtMillis)
                    })
                }
                put("wings", wingsArray)

                // Students
                val studentsArray = JSONArray()
                students.forEach { s ->
                    studentsArray.put(JSONObject().apply {
                        put("studentId", s.studentId)
                        put("hostelId", s.hostelId)
                        put("rollNumber", s.rollNumber)
                        put("normalizedRollNumber", s.normalizedRollNumber)
                        put("currentWingId", s.currentWingId)
                        put("currentRoomNumber", s.currentRoomNumber)
                        put("studentName", s.studentName)
                        put("branch", s.branch ?: "")
                        put("year", s.year ?: "")
                        put("isActive", s.isActive)
                        put("createdAtMillis", s.createdAtMillis)
                        put("updatedAtMillis", s.updatedAtMillis)
                    })
                }
                put("students", studentsArray)

                // Attendance Sessions
                val sessionsArray = JSONArray()
                sessions.forEach { sess ->
                    sessionsArray.put(JSONObject().apply {
                        put("sessionId", sess.sessionId)
                        put("hostelId", sess.hostelId)
                        put("localDate", sess.localDate)
                        put("timeZone", sess.timeZone)
                        put("wingId", sess.wingId)
                        put("state", sess.state)
                        put("savedAtMillis", sess.savedAtMillis ?: 0L)
                        put("savedBy", sess.savedBy ?: "")
                        put("version", sess.version)
                    })
                }
                put("sessions", sessionsArray)

                // Attendance Records
                val recordsArray = JSONArray()
                records.forEach { r ->
                    recordsArray.put(JSONObject().apply {
                        put("recordId", r.recordId)
                        put("hostelId", r.hostelId)
                        put("studentId", r.studentId)
                        put("rollNumberSnapshot", r.rollNumberSnapshot)
                        put("studentNameSnapshot", r.studentNameSnapshot)
                        put("localDate", r.localDate)
                        put("status", r.status)
                        put("wingIdSnapshot", r.wingIdSnapshot)
                        put("wingNameSnapshot", r.wingNameSnapshot)
                        put("roomNumberSnapshot", r.roomNumberSnapshot)
                        put("sessionId", r.sessionId)
                        put("version", r.version)
                        put("createdAtMillis", r.createdAtMillis)
                        put("updatedAtMillis", r.updatedAtMillis)
                    })
                }
                put("records", recordsArray)
            }

            // Write JSON snapshot safely
            val snapshotFile = File(backupDir, SNAPSHOT_FILE_NAME)
            val tempSnapshotFile = File(backupDir, "$SNAPSHOT_FILE_NAME.tmp")
            tempSnapshotFile.writeText(root.toString(2))
            if (tempSnapshotFile.exists()) {
                if (snapshotFile.exists()) snapshotFile.delete()
                tempSnapshotFile.renameTo(snapshotFile)
            }

            // 3. Write direct binary SQLite copy with WAL checkpoint
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                    cursor.moveToFirst()
                }
                val dbFile = context.getDatabasePath("hostel-attendance.db")
                if (dbFile.exists()) {
                    val targetDbFile = File(backupDir, DB_BACKUP_FILE_NAME)
                    dbFile.copyTo(targetDbFile, overwrite = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Database binary copy checkpoint error: ${e.message}")
            }

            // 4. Write metadata file
            val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            val metaJson = JSONObject().apply {
                put("lastBackupMillis", System.currentTimeMillis())
                put("formattedDate", formattedDate)
                put("studentCount", students.size)
                put("wingCount", wings.size)
                put("sessionCount", sessions.size)
                put("recordCount", records.size)
                put("storagePath", backupDir.absolutePath)
            }
            File(backupDir, METADATA_FILE_NAME).writeText(metaJson.toString())

            Log.d(TAG, "Local device backup saved to ${backupDir.absolutePath} (${students.size} students, ${records.size} records)")
            BackupResult.Success(
                BackupMetadata(
                    lastBackupMillis = System.currentTimeMillis(),
                    formattedDate = formattedDate,
                    studentCount = students.size,
                    wingCount = wings.size,
                    sessionCount = sessions.size,
                    recordCount = records.size,
                    storagePath = backupDir.absolutePath,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            BackupResult.Error(e.message ?: "Unknown backup error")
        }
    }

    /**
     * Checks if the app is freshly installed (empty local DB) and restores data from 'hostel_attendance_local_data'.
     */
    suspend fun restoreIfAvailable(
        context: Context,
        database: HostelDatabase,
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            // Check if current internal database already contains data
            val currentStudentCount = database.studentDao().getTotalStudentCount()
            val currentSessionCount = database.attendanceSessionDao().getTotalSessionCount()
            if (currentStudentCount > 0 || currentSessionCount > 0) {
                Log.d(TAG, "Local database already populated ($currentStudentCount students, $currentSessionCount sessions). No restore needed.")
                return@withContext RestoreResult.AlreadyHasData
            }

            val backupDir = findExistingBackupDir(context)
            if (backupDir == null) {
                Log.d(TAG, "No existing persistent backup directory found on device.")
                return@withContext RestoreResult.NoBackupFound
            }

            val snapshotFile = File(backupDir, SNAPSHOT_FILE_NAME)
            if (!snapshotFile.exists()) {
                Log.d(TAG, "No snapshot file in ${backupDir.absolutePath}")
                return@withContext RestoreResult.NoBackupFound
            }

            Log.i(TAG, "Found persistent backup snapshot in ${snapshotFile.absolutePath}! Beginning restore...")
            val root = JSONObject(snapshotFile.readText())

            // Parse Wings
            val wingsArray = root.optJSONArray("wings") ?: JSONArray()
            val wingsList = mutableListOf<WingEntity>()
            for (i in 0 until wingsArray.length()) {
                val obj = wingsArray.getJSONObject(i)
                wingsList.add(
                    WingEntity(
                        wingId = obj.getString("wingId"),
                        hostelId = obj.getString("hostelId"),
                        name = obj.getString("name"),
                        normalizedName = obj.optString("normalizedName", obj.getString("name")),
                        isActive = obj.optBoolean("isActive", true),
                        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }

            // Parse Students
            val studentsArray = root.optJSONArray("students") ?: JSONArray()
            val studentsList = mutableListOf<StudentEntity>()
            for (i in 0 until studentsArray.length()) {
                val obj = studentsArray.getJSONObject(i)
                studentsList.add(
                    StudentEntity(
                        studentId = obj.getString("studentId"),
                        hostelId = obj.getString("hostelId"),
                        rollNumber = obj.getString("rollNumber"),
                        normalizedRollNumber = obj.optString("normalizedRollNumber", obj.getString("rollNumber")),
                        currentWingId = obj.getString("currentWingId"),
                        currentRoomNumber = obj.getString("currentRoomNumber"),
                        studentName = obj.getString("studentName"),
                        branch = obj.optString("branch").takeIf { it.isNotBlank() },
                        year = obj.optString("year").takeIf { it.isNotBlank() },
                        isActive = obj.optBoolean("isActive", true),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }

            // Parse Sessions
            val sessionsArray = root.optJSONArray("sessions") ?: JSONArray()
            val sessionsList = mutableListOf<AttendanceSessionEntity>()
            for (i in 0 until sessionsArray.length()) {
                val obj = sessionsArray.getJSONObject(i)
                sessionsList.add(
                    AttendanceSessionEntity(
                        sessionId = obj.getString("sessionId"),
                        hostelId = obj.getString("hostelId"),
                        localDate = obj.getString("localDate"),
                        timeZone = obj.optString("timeZone", "Asia/Kolkata"),
                        wingId = obj.getString("wingId"),
                        state = obj.optString("state", "SAVED"),
                        savedAtMillis = obj.optLong("savedAtMillis").takeIf { it > 0L },
                        savedBy = obj.optString("savedBy").takeIf { it.isNotBlank() },
                        version = obj.optLong("version", 1L),
                    )
                )
            }

            // Parse Records
            val recordsArray = root.optJSONArray("records") ?: JSONArray()
            val recordsList = mutableListOf<AttendanceRecordEntity>()
            for (i in 0 until recordsArray.length()) {
                val obj = recordsArray.getJSONObject(i)
                recordsList.add(
                    AttendanceRecordEntity(
                        recordId = obj.getString("recordId"),
                        hostelId = obj.getString("hostelId"),
                        sessionId = obj.getString("sessionId"),
                        studentId = obj.getString("studentId"),
                        rollNumberSnapshot = obj.getString("rollNumberSnapshot"),
                        studentNameSnapshot = obj.optString("studentNameSnapshot", ""),
                        localDate = obj.getString("localDate"),
                        status = obj.getString("status"),
                        wingIdSnapshot = obj.getString("wingIdSnapshot"),
                        wingNameSnapshot = obj.getString("wingNameSnapshot"),
                        roomNumberSnapshot = obj.getString("roomNumberSnapshot"),
                        version = obj.optLong("version", 1L),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }

            // Insert restored entities into Room database
            if (wingsList.isNotEmpty()) {
                database.wingDao().insertWings(wingsList)
            }
            if (studentsList.isNotEmpty()) {
                database.studentDao().insertStudents(studentsList)
            }
            if (sessionsList.isNotEmpty()) {
                database.attendanceSessionDao().insertSessions(sessionsList)
            }
            if (recordsList.isNotEmpty()) {
                database.attendanceSessionDao().insertRecords(recordsList)
            }

            val successMsg = "Successfully restored ${studentsList.size} students and ${recordsList.size} attendance records from phone storage."
            Log.i(TAG, successMsg)
            RestoreResult.Restored(
                studentCount = studentsList.size,
                wingCount = wingsList.size,
                sessionCount = sessionsList.size,
                recordCount = recordsList.size,
                message = successMsg,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup: ${e.message}", e)
            RestoreResult.Error(e.message ?: "Restore failed")
        }
    }
}
