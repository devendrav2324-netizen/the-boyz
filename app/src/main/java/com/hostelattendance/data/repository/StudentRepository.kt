package com.hostelattendance.data.repository

import androidx.room.withTransaction
import com.hostelattendance.data.excel.ExcelImportAnalysis
import com.hostelattendance.data.excel.ExcelRowValidation
import com.hostelattendance.data.local.HostelDatabase
import com.hostelattendance.data.local.StudentDao
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingDao
import com.hostelattendance.data.local.WingEntity
import com.hostelattendance.data.sync.FirestoreSyncService
import com.hostelattendance.domain.StudentRegistrationInput
import com.hostelattendance.domain.StudentRegistrationRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface StudentRepository {
    fun observeWings(hostelId: String): Flow<List<WingEntity>>
    fun observeStudents(hostelId: String, wingId: String): Flow<List<StudentEntity>>
    fun observeAllStudents(hostelId: String): Flow<List<StudentEntity>>
    suspend fun createWing(hostelId: String, name: String)
    suspend fun registerStudent(hostelId: String, input: StudentRegistrationInput)
    suspend fun updateStudent(
        studentId: String,
        studentName: String? = null,
        roomNumber: String,
        wingId: String,
        branch: String?,
        year: String?,
    )
    suspend fun setStudentActive(studentId: String, isActive: Boolean)
    suspend fun commitExcelImport(
        hostelId: String,
        analysis: ExcelImportAnalysis,
        updateExistingDetails: Boolean,
    ): ExcelImportCommitResult
    suspend fun syncWithCloud(hostelId: String): Pair<Int, Int>
}

data class ExcelImportCommitResult(
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
)

class RoomStudentRepository(
    private val database: HostelDatabase,
    private val studentDao: StudentDao,
    private val wingDao: WingDao,
) : StudentRepository {
    override fun observeWings(hostelId: String): Flow<List<WingEntity>> =
        wingDao.observeActiveWings(hostelId)

    override fun observeStudents(hostelId: String, wingId: String): Flow<List<StudentEntity>> =
        studentDao.observeActiveStudents(hostelId, wingId)

    override fun observeAllStudents(hostelId: String): Flow<List<StudentEntity>> =
        studentDao.observeAllStudents(hostelId)

    override suspend fun createWing(hostelId: String, name: String) {
        require(hostelId.isNotBlank()) { "Hostel ID is required." }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Enter a wing name." }
        val normalizedName = StudentRegistrationRules.normalizeWingName(trimmedName)
        val createdWing = database.withTransaction {
            require(wingDao.findByNormalizedName(hostelId, normalizedName) == null) {
                "A wing with this name already exists."
            }
            val newWing = WingEntity(
                wingId = UUID.randomUUID().toString(),
                hostelId = hostelId,
                name = trimmedName,
                normalizedName = normalizedName,
                updatedAtMillis = System.currentTimeMillis(),
            )
            wingDao.insert(newWing)
            newWing
        }
        FirestoreSyncService.syncWing(hostelId, createdWing)
    }

    override suspend fun registerStudent(hostelId: String, input: StudentRegistrationInput) {
        require(hostelId.isNotBlank()) { "Hostel ID is required." }
        val validation = StudentRegistrationRules.validate(input)
        val student = validation.value ?: throw IllegalArgumentException(
            validation.errors.values.firstOrNull() ?: "Student details are invalid.",
        )

        val createdStudent = database.withTransaction {
            require(wingDao.findActiveWing(hostelId, student.wingId) != null) {
                "The selected wing is not available."
            }
            require(studentDao.findByNormalizedRollNumber(hostelId, student.normalizedRollNumber) == null) {
                "Roll number ${student.rollNumber} already exists in this hostel."
            }
            val now = System.currentTimeMillis()
            val newStudent = StudentEntity(
                studentId = UUID.randomUUID().toString(),
                hostelId = hostelId,
                rollNumber = student.rollNumber,
                normalizedRollNumber = student.normalizedRollNumber,
                currentWingId = student.wingId,
                currentRoomNumber = student.roomNumber,
                studentName = student.studentName,
                branch = student.branch,
                year = student.year,
                isActive = true,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            studentDao.insert(newStudent)
            newStudent
        }
        FirestoreSyncService.syncStudent(hostelId, createdStudent)
    }

    override suspend fun updateStudent(
        studentId: String,
        studentName: String?,
        roomNumber: String,
        wingId: String,
        branch: String?,
        year: String?,
    ) {
        val trimmedRoom = roomNumber.trim()
        require(trimmedRoom.isNotEmpty()) { "Room number is required." }
        require(wingId.isNotBlank()) { "Wing is required." }
        database.withTransaction {
            val trimmedName = studentName?.trim()
            val updated = if (trimmedName.isNullOrEmpty()) {
                studentDao.updateStudent(
                    studentId = studentId,
                    roomNumber = trimmedRoom,
                    wingId = wingId,
                    branch = branch?.trim()?.takeIf { it.isNotEmpty() },
                    year = year?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAtMillis = System.currentTimeMillis(),
                )
            } else {
                studentDao.updateStudentWithFullName(
                    studentId = studentId,
                    studentName = trimmedName,
                    roomNumber = trimmedRoom,
                    wingId = wingId,
                    branch = branch?.trim()?.takeIf { it.isNotEmpty() },
                    year = year?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAtMillis = System.currentTimeMillis(),
                )
            }
            check(updated == 1) { "Student record could not be updated." }
        }
        val updatedStudent = studentDao.findById(studentId)
        if (updatedStudent != null) {
            FirestoreSyncService.syncStudent(updatedStudent.hostelId, updatedStudent)
        }
    }

    override suspend fun setStudentActive(studentId: String, isActive: Boolean) {
        database.withTransaction {
            val updated = studentDao.setStudentActive(
                studentId = studentId,
                isActive = isActive,
                updatedAtMillis = System.currentTimeMillis(),
            )
            check(updated == 1) { "Student status could not be changed." }
            if (!isActive) {
                database.attendanceSessionDao().deleteDraftRecordsForStudent(studentId)
            }
        }
        val student = studentDao.findById(studentId)
        if (student != null) {
            FirestoreSyncService.syncStudent(student.hostelId, student)
        }
    }

    override suspend fun commitExcelImport(
        hostelId: String,
        analysis: ExcelImportAnalysis,
        updateExistingDetails: Boolean,
    ): ExcelImportCommitResult {
        val result = database.withTransaction {
            var inserted = 0
            var updated = 0
            var skipped = 0
            val now = System.currentTimeMillis()

            // Insert valid new students
            for (item in analysis.validNewRows) {
                val row = item.student
                val wing = item.targetWing
                val normalizedRoll = StudentRegistrationRules.normalizeRollNumber(row.rollNumber)

                // Ensure wing exists in DB
                val existingWing = wingDao.findByNormalizedName(hostelId, wing.normalizedName)
                val effectiveWingId = if (existingWing != null) {
                    existingWing.wingId
                } else {
                    wingDao.insert(wing)
                    wing.wingId
                }

                // Double check uniqueness in transaction
                if (studentDao.findByNormalizedRollNumber(hostelId, normalizedRoll) != null) {
                    skipped++
                    continue
                }

                studentDao.insert(
                    StudentEntity(
                        studentId = UUID.randomUUID().toString(),
                        hostelId = hostelId,
                        rollNumber = row.rollNumber,
                        normalizedRollNumber = normalizedRoll,
                        currentWingId = effectiveWingId,
                        currentRoomNumber = row.roomNumber,
                        studentName = row.studentName,
                        branch = row.branch.ifEmpty { null },
                        year = row.year.ifEmpty { null },
                        isActive = true,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    ),
                )
                inserted++
            }

            // Handle existing students according to reviewed policy
            for (item in analysis.existingRows) {
                if (updateExistingDetails) {
                    val row = item.student
                    val existing = item.existingStudent
                    val wing = item.targetWing

                    val existingWing = wingDao.findByNormalizedName(hostelId, wing.normalizedName)
                    val effectiveWingId = if (existingWing != null) {
                        existingWing.wingId
                    } else {
                        wingDao.insert(wing)
                        wing.wingId
                    }

                    studentDao.updateStudentWithFullName(
                        studentId = existing.studentId,
                        studentName = row.studentName,
                        roomNumber = row.roomNumber,
                        wingId = effectiveWingId,
                        branch = row.branch.ifEmpty { null },
                        year = row.year.ifEmpty { null },
                        updatedAtMillis = now,
                    )
                    // Ensure student is active
                    if (!existing.isActive) {
                        studentDao.setStudentActive(existing.studentId, true, now)
                    }
                    updated++
                } else {
                    skipped++
                }
            }

            ExcelImportCommitResult(
                insertedCount = inserted,
                updatedCount = updated,
                skippedCount = skipped,
            )
        }

        // Sync all wings and students to Cloud Firestore
        val allWings = wingDao.getAllActiveWings(hostelId)
        val allStudents = studentDao.getAllStudents(hostelId)
        FirestoreSyncService.syncWingsAndStudents(hostelId, allWings, allStudents)

        return result
    }

    override suspend fun syncWithCloud(hostelId: String): Pair<Int, Int> {
        // 1. Push any local wings and students to cloud
        val localWings = wingDao.getAllActiveWings(hostelId)
        val localStudents = studentDao.getAllStudents(hostelId)
        if (localWings.isNotEmpty() || localStudents.isNotEmpty()) {
            FirestoreSyncService.syncWingsAndStudents(hostelId, localWings, localStudents)
        }
        // 2. Pull all wings and students from cloud into local SQLite
        return FirestoreSyncService.restoreWingsAndStudentsFromFirestore(
            hostelId = hostelId,
            wingDao = wingDao,
            studentDao = studentDao,
        )
    }
}
