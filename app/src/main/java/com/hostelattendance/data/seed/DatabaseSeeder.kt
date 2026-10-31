package com.hostelattendance.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.hostelattendance.data.local.HostelDatabase
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingEntity
import com.hostelattendance.domain.StudentRegistrationRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

object DatabaseSeeder {

    private const val SEED_VERSION = 3
    private const val PREF_SEED_KEY = "hostel_seed_version"

    suspend fun seedIfNeeded(
        context: Context,
        database: HostelDatabase,
        hostelId: String = "local-installation",
    ) = withContext(Dispatchers.IO) {
        val studentDao = database.studentDao()
        val wingDao = database.wingDao()

        val prefs = context.getSharedPreferences("app_seed_prefs", Context.MODE_PRIVATE)
        val appliedVersion = prefs.getInt(PREF_SEED_KEY, 0)

        var currentCount = 0
        try {
            database.withTransaction {
                val cursor = database.query("SELECT COUNT(*) FROM students WHERE hostelId = '$hostelId'", null)
                if (cursor.moveToFirst()) {
                    currentCount = cursor.getInt(0)
                }
                cursor.close()
            }
        } catch (_: Exception) {}

        if (appliedVersion >= SEED_VERSION && currentCount >= 233) {
            return@withContext
        }

        val jsonString = try {
            context.assets.open("seed_students.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return@withContext
        }

        val root = JSONObject(jsonString)
        val wingsArray = root.getJSONArray("wings")
        val studentsArray = root.getJSONArray("students")
        val now = System.currentTimeMillis()

        database.withTransaction {
            val wingMap = mutableMapOf<String, String>()

            for (i in 0 until wingsArray.length()) {
                val wingName = wingsArray.getString(i)
                val normalized = StudentRegistrationRules.normalizeWingName(wingName)
                val existing = wingDao.findByNormalizedName(hostelId, normalized)
                val wingId = if (existing != null) {
                    existing.wingId
                } else {
                    val id = UUID.nameUUIDFromBytes("$hostelId:wing:$normalized".toByteArray()).toString()
                    wingDao.insert(
                        WingEntity(
                            wingId = id,
                            hostelId = hostelId,
                            name = wingName,
                            normalizedName = normalized,
                            updatedAtMillis = now,
                        ),
                    )
                    id
                }
                wingMap[wingName] = wingId
            }

            for (i in 0 until studentsArray.length()) {
                val obj = studentsArray.getJSONObject(i)
                val sno = obj.getString("sno")
                val name = obj.getString("name")
                val roomNumber = obj.getString("roomNumber")
                val wingName = obj.getString("wing")
                val branch = obj.optString("branch").takeIf { it.isNotBlank() }
                val year = obj.optString("year").takeIf { it.isNotBlank() }

                val normalizedRoll = StudentRegistrationRules.normalizeRollNumber(sno)
                val existingStudent = studentDao.findByNormalizedRollNumber(hostelId, normalizedRoll)
                val wingId = wingMap[wingName] ?: continue

                if (existingStudent == null) {
                    val studentId = UUID.nameUUIDFromBytes("$hostelId:student:$normalizedRoll".toByteArray()).toString()
                    studentDao.insert(
                        StudentEntity(
                            studentId = studentId,
                            hostelId = hostelId,
                            rollNumber = sno,
                            normalizedRollNumber = normalizedRoll,
                            currentWingId = wingId,
                            currentRoomNumber = roomNumber,
                            studentName = name,
                            branch = branch,
                            year = year,
                            isActive = true,
                            createdAtMillis = now,
                            updatedAtMillis = now,
                        ),
                    )
                } else {
                    studentDao.updateStudentWithFullName(
                        studentId = existingStudent.studentId,
                        studentName = name,
                        roomNumber = roomNumber,
                        wingId = wingId,
                        branch = branch,
                        year = year,
                        updatedAtMillis = now,
                    )
                }
            }
        }

        prefs.edit().putInt(PREF_SEED_KEY, SEED_VERSION).apply()
    }
}
