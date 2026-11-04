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
            val wing = wingDao.findActiveWing(hostelId, student.wingId)
            require(wing != null) {
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

            // Immediately attach student to today's active attendance session (DRAFT) if one exists,
            // so his name immediately appears in the attendance page to mark and in reports
            val today = com.hostelattendance.domain.AttendanceRules.today()
            val todayStr = today.toString()
            val sessionDao = database.attendanceSessionDao()
            val existingSession = sessionDao.findSession(hostelId, todayStr, student.wingId)
            if (existingSession != null && existingSession.state == com.hostelattendance.domain.AttendanceSessionState.DRAFT.name) {
                val recordId = com.hostelattendance.domain.AttendanceRules.recordId(hostelId, newStudent.studentId, today)
                val newRecord = com.hostelattendance.data.local.AttendanceRecordEntity(
                    recordId = recordId,
                    hostelId = hostelId,
                    studentId = newStudent.studentId,
                    rollNumberSnapshot = newStudent.rollNumber,
                    studentNameSnapshot = newStudent.studentName,
                    localDate = todayStr,
                    status = com.hostelattendance.domain.AttendanceStatus.NOT_MARKED.name,
                    wingIdSnapshot = newStudent.currentWingId,
                    wingNameSnapshot = wing.name,
                    roomNumberSnapshot = newStudent.currentRoomNumber,
                    sessionId = existingSession.sessionId,
                    version = 0,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
                sessionDao.insertRecords(listOf(newRecord))
            }

            newStudent
        }
        FirestoreSyncService.syncStudent(hostelId, createdStudent)
        com.hostelattendance.data.backup.PersistentBackupManager.triggerAutoBackup()
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
            val wing = wingDao.findActiveWing(updatedStudent.hostelId, updatedStudent.currentWingId)
            val wingName = wing?.name ?: ""
            val now = System.currentTimeMillis()

            // 1. Update all past attendance record snapshots in local Room SQLite
            database.attendanceSessionDao().updateStudentSnapshots(
                studentId = studentId,
                studentName = updatedStudent.studentName,
                roomNumber = updatedStudent.currentRoomNumber,
                wingId = updatedStudent.currentWingId,
                wingName = wingName,
                updatedAtMillis = now,
            )

            // 2. Sync updated student entity to Cloud Firestore
            FirestoreSyncService.syncStudent(updatedStudent.hostelId, updatedStudent)

            // 3. Sync updated attendance records for this student to Cloud Firestore
            val updatedRecords = database.attendanceSessionDao().findRecordsForStudent(studentId)
            if (updatedRecords.isNotEmpty()) {
                FirestoreSyncService.syncAttendanceRecordsBatch(updatedStudent.hostelId, updatedRecords)
            }
        }
        com.hostelattendance.data.backup.PersistentBackupManager.triggerAutoBackup()
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
        com.hostelattendance.data.backup.PersistentBackupManager.triggerAutoBackup()
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

                val importedStudentId = UUID.randomUUID().toString()
                studentDao.insert(
                    StudentEntity(
                        studentId = importedStudentId,
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

                // Attach imported student to today's draft attendance session if one exists
                val today = com.hostelattendance.domain.AttendanceRules.today()
                val todayStr = today.toString()
                val sessionDao = database.attendanceSessionDao()
                val existingSession = sessionDao.findSession(hostelId, todayStr, effectiveWingId)
                if (existingSession != null && existingSession.state == com.hostelattendance.domain.AttendanceSessionState.DRAFT.name) {
                    val recordId = com.hostelattendance.domain.AttendanceRules.recordId(hostelId, importedStudentId, today)
                    val newRecord = com.hostelattendance.data.local.AttendanceRecordEntity(
                        recordId = recordId,
                        hostelId = hostelId,
                        studentId = importedStudentId,
                        rollNumberSnapshot = row.rollNumber,
                        studentNameSnapshot = row.studentName,
                        localDate = todayStr,
                        status = com.hostelattendance.domain.AttendanceStatus.NOT_MARKED.name,
                        wingIdSnapshot = effectiveWingId,
                        wingNameSnapshot = wing.name,
                        roomNumberSnapshot = row.roomNumber,
                        sessionId = existingSession.sessionId,
                        version = 0,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    )
                    sessionDao.insertRecords(listOf(newRecord))
                }

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
        com.hostelattendance.data.backup.PersistentBackupManager.triggerAutoBackup()

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
        val syncResult = FirestoreSyncService.restoreWingsAndStudentsFromFirestore(
            hostelId = hostelId,
            wingDao = wingDao,
            studentDao = studentDao,
        )
        com.hostelattendance.data.backup.PersistentBackupManager.triggerAutoBackup()
        return syncResult
    }
}
