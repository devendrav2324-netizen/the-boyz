package com.hostelattendance.domain

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    LEAVE,
    NOT_MARKED,
}

enum class AttendanceSessionState {
    DRAFT,
    SAVED,
}

data class AttendanceTotals(
    val present: Int,
    val absent: Int,
    val leave: Int,
    val notMarked: Int,
) {
    val marked: Int
        get() = present + absent + leave

    val total: Int
        get() = marked + notMarked
}

data class AttendanceStudentSnapshot(
    val studentId: String,
    val rollNumber: String,
    val studentName: String,
    val wingId: String,
    val wingName: String,
    val roomNumber: String,
)

object AttendanceRules {
    val hostelZone: ZoneId = ZoneId.of("Asia/Kolkata")

    fun today(now: java.time.Instant = java.time.Instant.now()): LocalDate =
        now.atZone(hostelZone).toLocalDate()

    fun sessionId(hostelId: String, localDate: LocalDate, wingId: String): String {
        require(hostelId.isNotBlank()) { "Hostel ID is required." }
        require(wingId.isNotBlank()) { "Wing ID is required." }
        return "${hostelId.trim()}:${localDate}:$wingId"
    }

    fun recordId(hostelId: String, studentId: String, localDate: LocalDate): String {
        require(hostelId.isNotBlank()) { "Hostel ID is required." }
        require(studentId.isNotBlank()) { "Student ID is required." }
        return "${hostelId.trim()}:$studentId:$localDate"
    }

    fun normalizeRollNumber(rollNumber: String): String =
        rollNumber.trim().uppercase(Locale.ROOT)

    fun newDraftRecords(
        hostelId: String,
        localDate: LocalDate,
        sessionId: String,
        students: List<AttendanceStudentSnapshot>,
        nowMillis: Long,
    ) = students.map { student ->
        require(student.studentId.isNotBlank()) { "Student ID is required." }
        require(student.rollNumber.isNotBlank()) { "Roll Number is required." }
        require(student.studentName.isNotBlank()) { "Student Name is required." }
        require(student.wingId.isNotBlank()) { "Wing is required." }
        require(student.roomNumber.isNotBlank()) { "Room Number is required." }
        AttendanceRecordSnapshot(
            recordId = recordId(hostelId, student.studentId, localDate),
            hostelId = hostelId,
            studentId = student.studentId,
            rollNumberSnapshot = student.rollNumber.trim(),
            studentNameSnapshot = student.studentName.trim(),
            localDate = localDate.toString(),
            status = AttendanceStatus.NOT_MARKED.name,
            wingIdSnapshot = student.wingId,
            wingNameSnapshot = student.wingName,
            roomNumberSnapshot = student.roomNumber.trim(),
            sessionId = sessionId,
            version = 0,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
    }

    fun totals(statuses: Iterable<String>): AttendanceTotals {
        var present = 0
        var absent = 0
        var leave = 0
        var notMarked = 0
        statuses.forEach { value ->
            when (AttendanceStatus.valueOf(value)) {
                AttendanceStatus.PRESENT -> present++
                AttendanceStatus.ABSENT -> absent++
                AttendanceStatus.LEAVE -> leave++
                AttendanceStatus.NOT_MARKED -> notMarked++
            }
        }
        return AttendanceTotals(present, absent, leave, notMarked)
    }

    fun requireSaveAllowed(
        state: String,
        notMarkedCount: Int,
        confirmIncomplete: Boolean = false,
    ) {
        require(state == AttendanceSessionState.DRAFT.name) {
            "Saved attendance is read-only."
        }
        require(notMarkedCount == 0) {
            "Attendance cannot be saved: all students must be marked Present, Absent, or Leave."
        }
    }

    fun isPastDate(localDate: LocalDate): Boolean = localDate.isBefore(today())

    fun isFutureDate(localDate: LocalDate): Boolean = localDate.isAfter(today())

    fun requireMarkingAllowed(localDate: LocalDate, isAdmin: Boolean, sessionState: String) {
        require(isAdmin) { "Attendance marking is restricted to authorized administrators." }
        require(sessionState == AttendanceSessionState.DRAFT.name) {
            "Attendance session is saved and locked. Click 'Reopen for administrator correction' before making changes."
        }
    }

    fun newOperationId(): String = UUID.randomUUID().toString()
}

data class AttendanceRecordSnapshot(
    val recordId: String,
    val hostelId: String,
    val studentId: String,
    val rollNumberSnapshot: String,
    val studentNameSnapshot: String,
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

/**
 * Natural Order Roll Number Comparator
 * Accurately arranges student roll numbers in increasing order:
 * e.g., "1", "2", "9", "10", "20" (instead of standard string sorting where "10" < "2").
 * Handles alphanumeric prefixes, suffixes, and pure numeric roll numbers.
 */
object NaturalRollNumberComparator : Comparator<String> {
    override fun compare(s1: String?, s2: String?): Int {
        val t1 = s1?.trim().orEmpty()
        val t2 = s2?.trim().orEmpty()

        if (t1 == t2) return 0

        val n1 = t1.toLongOrNull()
        val n2 = t2.toLongOrNull()
        if (n1 != null && n2 != null) {
            return n1.compareTo(n2)
        }

        var i1 = 0
        var i2 = 0
        while (i1 < t1.length && i2 < t2.length) {
            val c1 = t1[i1]
            val c2 = t2[i2]

            if (c1.isDigit() && c2.isDigit()) {
                var j1 = i1
                while (j1 < t1.length && t1[j1].isDigit()) j1++
                var j2 = i2
                while (j2 < t2.length && t2[j2].isDigit()) j2++

                val chunk1 = t1.substring(i1, j1)
                val chunk2 = t2.substring(i2, j2)

                val v1 = chunk1.toLongOrNull()
                val v2 = chunk2.toLongOrNull()

                val numComparison = if (v1 != null && v2 != null) {
                    v1.compareTo(v2)
                } else {
                    val b1 = chunk1.toBigIntegerOrNull()
                    val b2 = chunk2.toBigIntegerOrNull()
                    if (b1 != null && b2 != null) {
                        b1.compareTo(b2)
                    } else {
                        chunk1.compareTo(chunk2)
                    }
                }

                if (numComparison != 0) return numComparison

                i1 = j1
                i2 = j2
            } else {
                val comp = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                if (comp != 0) return comp
                i1++
                i2++
            }
        }
        return t1.length.compareTo(t2.length)
    }
}
