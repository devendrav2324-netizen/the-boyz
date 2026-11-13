package com.hostelattendance.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Attendance statistics computed for an individual user for a specific date range or month.
 */
data class UserMonthlyAttendanceStats(
    val totalDaysMarked: Int,
    val totalPresent: Int,
    val totalAbsent: Int,
    val totalLeave: Int,
    val attendancePercentage: Float,
)

/**
 * Data model for a wrong attendance dispute request.
 */
data class AttendanceDisputeRequest(
    val requestId: String,
    val hostelId: String,
    val studentId: String,
    val studentName: String,
    val localDate: String,
    val markedStatus: String,
    val claimedStatus: String,
    val reason: String,
    val createdAtMillis: Long,
    val status: String = "PENDING",
)

/**
 * Domain rules and privacy enforcement for user (non-admin) sessions.
 * Guarantees zero leakage of third-party student rosters, records, or personal information.
 */
object UserSessionPrivacyRules {

    private val userFacingDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

    /**
     * Determine time-of-day greeting (e.g. Good morning, Good afternoon, Good evening).
     */
    fun getGreetingForTime(time: LocalTime = LocalTime.now()): String {
        return when (time.hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Good night"
        }
    }

    /**
     * Format a LocalDate into "Mon, 2 Nov 2026" display format.
     */
    fun formatDisplayDate(date: LocalDate = LocalDate.now()): String {
        return date.format(userFacingDateFormatter)
    }

    /**
     * Check if a given session role is permitted to view hostel-wide or wing-wide rosters.
     */
    fun canAccessFullRoster(role: String?): Boolean {
        return role.equals("ADMIN", ignoreCase = true)
    }

    /**
     * Check if a role can mark attendance for other students.
     */
    fun canMarkAttendance(role: String?): Boolean {
        return role.equals("ADMIN", ignoreCase = true)
    }

    /**
     * Extract the display first name or primary title from a full name.
     */
    fun extractFirstName(displayName: String?): String {
        val trimmed = displayName?.trim().orEmpty()
        if (trimmed.isBlank()) return "Student"
        return trimmed.split("\\s+".toRegex()).firstOrNull()?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
        } ?: "Student"
    }

    /**
     * Calculate monthly statistics for an individual student from attendance status strings.
     */
    fun calculateMonthlyStats(statuses: List<String>): UserMonthlyAttendanceStats {
        var present = 0
        var absent = 0
        var leave = 0

        for (status in statuses) {
            when (status.uppercase(Locale.ENGLISH)) {
                AttendanceStatus.PRESENT.name -> present++
                AttendanceStatus.ABSENT.name -> absent++
                AttendanceStatus.LEAVE.name -> leave++
            }
        }

        val total = present + absent + leave
        val percentage = if (total > 0) {
            (present.toFloat() / total.toFloat()) * 100f
        } else {
            0f
        }

        return UserMonthlyAttendanceStats(
            totalDaysMarked = total,
            totalPresent = present,
            totalAbsent = absent,
            totalLeave = leave,
            attendancePercentage = percentage,
        )
    }

    /**
     * Filters a list of attendance records strictly to the records belonging to the user.
     * Prevents any other student's data from being accessed or returned.
     * Enforces strict deterministic matching (exact ID, exact roll, or exact name).
     * No loose substring matches that would pool multiple students' records together.
     */
    fun <T> filterRecordsForUser(
        records: List<T>,
        userStudentId: String?,
        userStudentName: String?,
        userRollNumber: String? = null,
        getStudentId: (T) -> String,
        getStudentName: (T) -> String,
        getRollNumber: ((T) -> String)? = null,
    ): List<T> {
        val normId = userStudentId?.trim().orEmpty()
        val normName = userStudentName?.trim().orEmpty()
        val normRoll = userRollNumber?.trim().orEmpty()

        if (normId.isBlank() && normName.isBlank() && normRoll.isBlank()) {
            return emptyList()
        }

        return records.filter { record ->
            val recordId = getStudentId(record).trim()
            val recordName = getStudentName(record).trim()
            val recordRoll = getRollNumber?.invoke(record)?.trim().orEmpty()

            val matchesId = normId.isNotBlank() && recordId.equals(normId, ignoreCase = true)
            val matchesRoll = normRoll.isNotBlank() && recordRoll.isNotBlank() && recordRoll.equals(normRoll, ignoreCase = true)
            val matchesName = normName.isNotBlank() && recordName.equals(normName, ignoreCase = true)

            matchesId || matchesRoll || matchesName
        }
    }
}
