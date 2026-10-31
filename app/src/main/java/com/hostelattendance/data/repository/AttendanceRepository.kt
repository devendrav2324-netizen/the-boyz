package com.hostelattendance.data.repository

import androidx.room.withTransaction
import com.hostelattendance.data.local.AttendanceRecordEntity
import com.hostelattendance.data.local.AttendanceSessionDao
import com.hostelattendance.data.local.AttendanceSessionEntity
import com.hostelattendance.data.local.AttendanceSessionWithRecords
import com.hostelattendance.data.local.HostelDatabase
import com.hostelattendance.data.local.StudentDao
import com.hostelattendance.data.local.WingDao
import com.hostelattendance.data.local.WingEntity
import com.hostelattendance.domain.AttendanceRules
import com.hostelattendance.domain.AttendanceSessionState
import com.hostelattendance.domain.AttendanceStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface AttendanceRepository {
    fun observeWings(hostelId: String): Flow<List<WingEntity>>
    fun observeActiveStudentCount(hostelId: String, wingId: String): Flow<Int>
    fun observeSession(
        hostelId: String,
        localDate: String,
        wingId: String,
    ): Flow<AttendanceSessionWithRecords?>

    fun observeSavedSessionsForMonth(
        hostelId: String,
        monthPrefix: String,
    ): Flow<List<AttendanceSessionWithRecords>>

    fun observeRecordsForMonth(
        hostelId: String,
        monthPrefix: String,
    ): Flow<List<AttendanceRecordEntity>>

    fun observeRecordsForDateRange(
        hostelId: String,
        startDate: String,
        endDate: String,
    ): Flow<List<AttendanceRecordEntity>>

    suspend fun ensureDraftSession(
        hostelId: String,
        localDate: String,
        wingId: String,
        isAdmin: Boolean = false,
        allowPastCreation: Boolean = false,
    ): AttendanceSessionWithRecords?

    suspend fun mark(
        sessionId: String,
        recordId: String,
        status: AttendanceStatus,
        isAdmin: Boolean = false,
    )

    suspend fun save(
        sessionId: String,
        savedBy: String?,
        confirmIncomplete: Boolean,
    )

    suspend fun reopenSessionAsAdmin(sessionId: String)

    suspend fun updateRecordStatusAsAdmin(
        sessionId: String,
        recordId: String,
        status: AttendanceStatus,
    )
}

class RoomAttendanceRepository(
    private val database: HostelDatabase,
    private val sessionDao: AttendanceSessionDao,
    private val studentDao: StudentDao,
    private val wingDao: WingDao,
) : AttendanceRepository {
    override fun observeWings(hostelId: String): Flow<List<WingEntity>> =
        wingDao.observeActiveWings(hostelId)

    override fun observeActiveStudentCount(hostelId: String, wingId: String): Flow<Int> =
        studentDao.observeActiveStudentCount(hostelId, wingId)

    override fun observeSession(
        hostelId: String,
        localDate: String,
        wingId: String,
    ): Flow<AttendanceSessionWithRecords?> =
        sessionDao.observeSessionWithRecords(hostelId, localDate, wingId)

    override fun observeSavedSessionsForMonth(
        hostelId: String,
        monthPrefix: String,
    ): Flow<List<AttendanceSessionWithRecords>> =
        sessionDao.observeSavedSessionsForMonth(hostelId, monthPrefix)

    override fun observeRecordsForMonth(
        hostelId: String,
        monthPrefix: String,
    ): Flow<List<AttendanceRecordEntity>> =
        sessionDao.observeRecordsForMonth(hostelId, monthPrefix)

    override fun observeRecordsForDateRange(
        hostelId: String,
        startDate: String,
        endDate: String,
    ): Flow<List<AttendanceRecordEntity>> =
        sessionDao.observeRecordsForDateRange(hostelId, startDate, endDate)

    override suspend fun ensureDraftSession(
        hostelId: String,
        localDate: String,
        wingId: String,
        isAdmin: Boolean,
        allowPastCreation: Boolean,
    ): AttendanceSessionWithRecords? {
        val parsedDate = LocalDate.parse(localDate)

        // 1. Try to pull latest session from Firestore to stay in real-time sync with cloud
        val remoteSession = com.hostelattendance.data.sync.FirestoreSyncService.pullAttendanceSession(
            hostelId = hostelId,
            localDate = localDate,
            wingId = wingId,
            sessionDao = sessionDao,
        )
        if (remoteSession != null && remoteSession.records.isNotEmpty()) {
            return remoteSession
        }

        // 2. Check local SQLite (offline support)
        val localExisting = sessionDao.findSession(hostelId, localDate, wingId)
        if (localExisting != null) {
            val sessionWithRecords = sessionDao.findSessionWithRecords(localExisting.sessionId)
            if (sessionWithRecords != null && sessionWithRecords.records.isNotEmpty()) {
                return sessionWithRecords
            }
        }

        // 3. Not in Firestore or local SQLite.
        // Rule: Only administrators can initialize a new attendance session! Regular users can only view marked records.
        if (!isAdmin) {
            return null
        }

        // Rule: Previous dates cannot be automatically created when just browsing.
        // Admins can explicitly create a past attendance draft by clicking "Create Past Attendance Draft".
        if (AttendanceRules.isPastDate(parsedDate) && !allowPastCreation) {
            return null
        }

        // 4. Admin creating a new draft session in Room
        return database.withTransaction {
            sessionDao.findSession(hostelId, localDate, wingId)?.let { existing ->
                return@withTransaction sessionDao.findSessionWithRecords(existing.sessionId)
            }

            val students = studentDao.findActiveStudentsWithWingName(hostelId, wingId)
            if (students.isEmpty()) return@withTransaction null

            val now = System.currentTimeMillis()
            val sessionId = AttendanceRules.sessionId(hostelId, parsedDate, wingId)
            val existingRecords = sessionDao.findRecordsForHostelDate(hostelId, localDate)
            val existingStudentIds = existingRecords.mapTo(HashSet()) { it.studentId }
            val duplicate = students.firstOrNull { it.student.studentId in existingStudentIds }
            if (duplicate != null) {
                throw DuplicateAttendanceException(
                    "Attendance already exists for roll number ${duplicate.student.rollNumber} on $localDate.",
                )
            }

            val session = AttendanceSessionEntity(
                sessionId = sessionId,
                hostelId = hostelId,
                localDate = localDate,
                timeZone = AttendanceRules.hostelZone.id,
                wingId = wingId,
                state = AttendanceSessionState.DRAFT.name,
                savedAtMillis = null,
                savedBy = null,
                version = 0,
            )
            val snapshots = students.map { joined ->
                val student = joined.student
                com.hostelattendance.domain.AttendanceStudentSnapshot(
                    studentId = student.studentId,
                    rollNumber = student.rollNumber,
                    studentName = student.studentName,
                    wingId = student.currentWingId,
                    wingName = joined.currentWingName,
                    roomNumber = student.currentRoomNumber,
                )
            }
            val records = AttendanceRules.newDraftRecords(
                hostelId = hostelId,
                localDate = parsedDate,
                sessionId = sessionId,
                students = snapshots,
                nowMillis = now,
            ).map { record ->
                AttendanceRecordEntity(
                    recordId = record.recordId,
                    hostelId = record.hostelId,
                    studentId = record.studentId,
                    rollNumberSnapshot = record.rollNumberSnapshot,
                    studentNameSnapshot = record.studentNameSnapshot,
                    localDate = record.localDate,
                    status = record.status,
                    wingIdSnapshot = record.wingIdSnapshot,
                    wingNameSnapshot = record.wingNameSnapshot,
                    roomNumberSnapshot = record.roomNumberSnapshot,
                    sessionId = record.sessionId,
                    version = record.version,
                    createdAtMillis = record.createdAtMillis,
                    updatedAtMillis = record.updatedAtMillis,
                )
            }

            sessionDao.insertSession(session)
            sessionDao.insertRecords(records)
            sessionDao.findSessionWithRecords(sessionId)
        }
    }

    override suspend fun mark(
        sessionId: String,
        recordId: String,
        status: AttendanceStatus,
        isAdmin: Boolean,
    ) {
        require(status in AttendanceStatus.entries) { "Invalid attendance status." }
        require(isAdmin) { "Attendance marking is restricted to authorized administrators." }

        val sessionWithRecords = sessionDao.findSessionWithRecords(sessionId)
            ?: error("Attendance session was not found.")

        if (sessionWithRecords.session.state == AttendanceSessionState.SAVED.name) {
            error("Attendance is locked. Click 'Reopen for administrator correction' to make changes.")
        }

        val updated = sessionDao.updateDraftRecordStatus(
            sessionId = sessionId,
            recordId = recordId,
            status = status.name,
            updatedAtMillis = System.currentTimeMillis(),
        )
        check(updated == 1) { "This attendance record could not be updated." }
    }

    override suspend fun save(
        sessionId: String,
        savedBy: String?,
        confirmIncomplete: Boolean,
    ) {
        database.withTransaction {
            val session = sessionDao.findSessionWithRecords(sessionId)
                ?: error("Attendance session was not found.")
            if (session.session.state == AttendanceSessionState.SAVED.name) {
                return@withTransaction
            }
            val notMarkedCount = session.records.count {
                it.status == AttendanceStatus.NOT_MARKED.name
            }
            AttendanceRules.requireSaveAllowed(
                state = session.session.state,
                notMarkedCount = notMarkedCount,
                confirmIncomplete = confirmIncomplete,
            )
            val updated = sessionDao.markSessionSaved(
                sessionId = sessionId,
                savedAtMillis = System.currentTimeMillis(),
                savedBy = savedBy,
            )
            check(updated == 1) { "Attendance was already saved or changed." }
        }
        val savedSession = sessionDao.findSessionWithRecords(sessionId)
        if (savedSession != null) {
            com.hostelattendance.data.sync.FirestoreSyncService.syncAttendanceSession(
                hostelId = savedSession.session.hostelId,
                session = savedSession.session,
                records = savedSession.records,
            )
        }
    }

    override suspend fun reopenSessionAsAdmin(sessionId: String) {
        database.withTransaction {
            val updated = sessionDao.reopenSessionAsAdmin(sessionId)
            check(updated == 1) { "Session could not be reopened." }
        }
        val session = sessionDao.findSessionWithRecords(sessionId)
        if (session != null) {
            com.hostelattendance.data.sync.FirestoreSyncService.syncAttendanceSession(
                hostelId = session.session.hostelId,
                session = session.session,
                records = session.records,
            )
        }
    }

    override suspend fun updateRecordStatusAsAdmin(
        sessionId: String,
        recordId: String,
        status: AttendanceStatus,
    ) {
        require(status in AttendanceStatus.entries) { "Invalid attendance status." }
        val updated = sessionDao.updateRecordStatusAsAdmin(
            sessionId = sessionId,
            recordId = recordId,
            status = status.name,
            updatedAtMillis = System.currentTimeMillis(),
        )
        check(updated == 1) { "Record could not be updated." }
        val session = sessionDao.findSessionWithRecords(sessionId)
        val record = session?.records?.firstOrNull { it.recordId == recordId }
        if (session != null && record != null) {
            com.hostelattendance.data.sync.FirestoreSyncService.syncAttendanceRecord(
                hostelId = session.session.hostelId,
                record = record,
            )
        }
    }
}

class DuplicateAttendanceException(message: String) : IllegalStateException(message)
