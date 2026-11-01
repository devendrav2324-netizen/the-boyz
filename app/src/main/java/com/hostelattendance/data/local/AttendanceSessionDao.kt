package com.hostelattendance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AttendanceSessionWithRecords(
    @androidx.room.Embedded val session: AttendanceSessionEntity,
    @Relation(
        parentColumn = "sessionId",
        entityColumn = "sessionId",
    )
    val records: List<AttendanceRecordEntity>,
)

@Dao
interface AttendanceSessionDao {
    @Query("SELECT * FROM attendance_sessions ORDER BY localDate DESC, wingId")
    fun observeSessions(): Flow<List<AttendanceSessionEntity>>

    @Transaction
    @Query("SELECT * FROM attendance_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun findSessionWithRecords(sessionId: String): AttendanceSessionWithRecords?

    @Query(
        """
        SELECT * FROM attendance_sessions
        WHERE hostelId = :hostelId AND localDate = :localDate AND wingId = :wingId
        LIMIT 1
        """,
    )
    suspend fun findSession(hostelId: String, localDate: String, wingId: String): AttendanceSessionEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM attendance_sessions
        WHERE hostelId = :hostelId AND localDate = :localDate AND wingId = :wingId
        LIMIT 1
        """,
    )
    fun observeSessionWithRecords(
        hostelId: String,
        localDate: String,
        wingId: String,
    ): Flow<AttendanceSessionWithRecords?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AttendanceSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<AttendanceSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<AttendanceRecordEntity>)

    @Update
    suspend fun updateSession(session: AttendanceSessionEntity)

    @Query(
        """
        UPDATE attendance_records
        SET status = :status, updatedAtMillis = :updatedAtMillis, version = version + 1
        WHERE recordId = :recordId AND sessionId = :sessionId
          AND EXISTS (
            SELECT 1 FROM attendance_sessions
            WHERE attendance_sessions.sessionId = :sessionId
              AND attendance_sessions.state = 'DRAFT'
          )
        """,
    )
    suspend fun updateDraftRecordStatus(
        sessionId: String,
        recordId: String,
        status: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE attendance_records
        SET status = 'NOT_MARKED', updatedAtMillis = :updatedAtMillis, version = version + 1
        WHERE sessionId = :sessionId
          AND EXISTS (
            SELECT 1 FROM attendance_sessions
            WHERE attendance_sessions.sessionId = :sessionId
              AND attendance_sessions.state = 'DRAFT'
          )
        """,
    )
    suspend fun clearSessionSelections(
        sessionId: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        SELECT * FROM attendance_records
        WHERE hostelId = :hostelId AND localDate = :localDate
        """,
    )
    suspend fun findRecordsForHostelDate(hostelId: String, localDate: String): List<AttendanceRecordEntity>

    @Query(
        """
        UPDATE attendance_sessions
        SET state = 'SAVED', savedAtMillis = :savedAtMillis, savedBy = :savedBy, version = version + 1
        WHERE sessionId = :sessionId AND state = 'DRAFT'
        """,
    )
    suspend fun markSessionSaved(
        sessionId: String,
        savedAtMillis: Long,
        savedBy: String?,
    ): Int

    @Transaction
    @Query(
        """
        SELECT * FROM attendance_sessions
        WHERE hostelId = :hostelId AND localDate LIKE :monthPrefix || '%' AND state = 'SAVED'
        ORDER BY localDate DESC, wingId ASC
        """,
    )
    fun observeSavedSessionsForMonth(
        hostelId: String,
        monthPrefix: String,
    ): Flow<List<AttendanceSessionWithRecords>>

    @Query(
        """
        SELECT * FROM attendance_records
        WHERE hostelId = :hostelId AND localDate LIKE :monthPrefix || '%'
        ORDER BY localDate DESC, wingIdSnapshot ASC, rollNumberSnapshot ASC
        """,
    )
    fun observeRecordsForMonth(
        hostelId: String,
        monthPrefix: String,
    ): Flow<List<AttendanceRecordEntity>>

    @Query(
        """
        SELECT * FROM attendance_records
        WHERE hostelId = :hostelId AND localDate >= :startDate AND localDate <= :endDate
        ORDER BY localDate DESC, wingIdSnapshot ASC, rollNumberSnapshot ASC
        """,
    )
    fun observeRecordsForDateRange(
        hostelId: String,
        startDate: String,
        endDate: String,
    ): Flow<List<AttendanceRecordEntity>>

    @Query(
        """
        UPDATE attendance_sessions
        SET state = 'DRAFT', version = version + 1
        WHERE sessionId = :sessionId AND state = 'SAVED'
        """,
    )
    suspend fun reopenSessionAsAdmin(sessionId: String): Int

    @Query(
        """
        UPDATE attendance_records
        SET status = :status, updatedAtMillis = :updatedAtMillis, version = version + 1
        WHERE recordId = :recordId AND sessionId = :sessionId
        """,
    )
    suspend fun updateRecordStatusAsAdmin(
        sessionId: String,
        recordId: String,
        status: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        DELETE FROM attendance_records
        WHERE studentId = :studentId
          AND sessionId IN (SELECT sessionId FROM attendance_sessions WHERE state = 'DRAFT')
        """,
    )
    suspend fun deleteDraftRecordsForStudent(studentId: String): Int

    @Query(
        """
        UPDATE attendance_records
        SET studentNameSnapshot = :studentName,
            roomNumberSnapshot = :roomNumber,
            wingIdSnapshot = :wingId,
            wingNameSnapshot = :wingName,
            updatedAtMillis = :updatedAtMillis
        WHERE studentId = :studentId
        """,
    )
    suspend fun updateStudentSnapshots(
        studentId: String,
        studentName: String,
        roomNumber: String,
        wingId: String,
        wingName: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        SELECT * FROM attendance_records
        WHERE studentId = :studentId
        """,
    )
    suspend fun findRecordsForStudent(studentId: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_sessions")
    suspend fun getAllSessions(): List<AttendanceSessionEntity>

    @Query("SELECT * FROM attendance_records")
    suspend fun getAllRecords(): List<AttendanceRecordEntity>

    @Query("SELECT COUNT(*) FROM attendance_sessions")
    suspend fun getTotalSessionCount(): Int
}
