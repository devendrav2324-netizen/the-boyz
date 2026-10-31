package com.hostelattendance.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "wings",
    indices = [Index(value = ["hostelId", "normalizedName"], unique = true)],
)
data class WingEntity(
    @PrimaryKey val wingId: String,
    val hostelId: String,
    val name: String,
    val normalizedName: String,
    val isActive: Boolean = true,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["hostelId", "normalizedRollNumber"], unique = true),
        Index(value = ["hostelId", "currentWingId", "isActive"]),
    ],
)
data class StudentEntity(
    @PrimaryKey val studentId: String,
    val hostelId: String,
    val rollNumber: String,
    val normalizedRollNumber: String,
    val currentWingId: String,
    val currentRoomNumber: String,
    val studentName: String,
    val branch: String?,
    val year: String?,
    val isActive: Boolean = true,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "attendance_sessions",
    indices = [
        Index(value = ["hostelId", "localDate", "wingId"], unique = true),
        Index(value = ["hostelId", "localDate"]),
        Index(value = ["wingId", "localDate"]),
    ],
)
data class AttendanceSessionEntity(
    @PrimaryKey val sessionId: String,
    val hostelId: String,
    val localDate: String,
    val timeZone: String,
    val wingId: String,
    val state: String,
    val savedAtMillis: Long?,
    val savedBy: String?,
    val version: Long,
)

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["hostelId", "studentId", "localDate"], unique = true),
        Index(value = ["hostelId", "localDate"]),
        Index(value = ["wingIdSnapshot", "localDate"]),
        Index(value = ["studentId", "localDate"]),
        Index(value = ["sessionId"]),
    ],
)
data class AttendanceRecordEntity(
    @PrimaryKey val recordId: String,
    val hostelId: String,
    val studentId: String,
    val rollNumberSnapshot: String,
    @ColumnInfo(defaultValue = "''") val studentNameSnapshot: String,
    val localDate: String,
    val status: String,
    val wingIdSnapshot: String,
    val wingNameSnapshot: String,
    val roomNumberSnapshot: String,
    val sessionId: String,
    val version: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["hostelId", "isActive"])],
)
data class UserProfileCacheEntity(
    @PrimaryKey val uid: String,
    val hostelId: String?,
    val role: String?,
    val approved: Boolean,
    val allowedWingIdsJson: String,
    val canRegisterStudents: Boolean,
    val isActive: Boolean,
    val cachedAtMillis: Long,
)

@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["state", "createdAtMillis"]),
        Index(value = ["entityType", "entityId"]),
    ],
)
data class PendingOperationEntity(
    @PrimaryKey val operationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payloadJson: String,
    val expectedRemoteVersion: Long?,
    val createdAtMillis: Long,
    val retryCount: Int,
    val state: String,
    val lastError: String?,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val stateId: String,
    val lastSuccessfulSyncAtMillis: Long?,
    val lastError: String?,
)

@Entity(
    tableName = "audit_events",
    indices = [
        Index(value = ["hostelId", "entityType", "entityId"]),
        Index(value = ["timestampMillis"]),
    ],
)
data class AuditEventCacheEntity(
    @PrimaryKey val eventId: String,
    val hostelId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val actorUid: String,
    val timestampMillis: Long,
    val reason: String?,
    val beforeValueJson: String?,
    val afterValueJson: String?,
    val previousVersion: Long?,
    val newVersion: Long?,
)
