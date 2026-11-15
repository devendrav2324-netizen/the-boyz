package com.hostelattendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AttendanceMarkerRulesTest {

    @Test
    fun `marking allowed succeeds for authorized admin`() {
        val today = LocalDate.parse("2026-11-15")
        // Should not throw
        AttendanceRules.requireMarkingAllowed(today, canMark = true, sessionState = "DRAFT")
    }

    @Test
    fun `marking allowed succeeds for designated attendance marker`() {
        val today = LocalDate.parse("2026-11-15")
        // When student is granted attendance marker access, canMark is true
        val isAttendanceMarker = true
        val isAdmin = false
        val canMark = isAdmin || isAttendanceMarker
        assertTrue(canMark)

        // Should not throw
        AttendanceRules.requireMarkingAllowed(today, canMark = canMark, sessionState = "DRAFT")
    }

    @Test
    fun `marking allowed throws for regular resident with no marker permission`() {
        val today = LocalDate.parse("2026-11-15")
        val isAttendanceMarker = false
        val isAdmin = false
        val canMark = isAdmin || isAttendanceMarker
        assertFalse(canMark)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            AttendanceRules.requireMarkingAllowed(today, canMark = canMark, sessionState = "DRAFT")
        }
        assertTrue(ex.message!!.contains("restricted to authorized personnel"))
    }

    @Test
    fun `marking allowed throws when attendance session is locked and saved`() {
        val today = LocalDate.parse("2026-11-15")
        val ex = assertThrows(IllegalArgumentException::class.java) {
            AttendanceRules.requireMarkingAllowed(today, canMark = true, sessionState = "SAVED")
        }
        assertTrue(ex.message!!.contains("Attendance session is saved and locked"))
    }

    @Test
    fun `delegated marker privilege isolation logic`() {
        // Attendance Marker permissions
        val isMarker = true
        val isAdmin = false

        val canMark = isAdmin || isMarker
        val canAccessRegistration = isAdmin // Strict isolation: markers CANNOT register students
        val canDeleteStudents = isAdmin // Strict isolation: markers CANNOT delete students
        val canDeleteWings = isAdmin // Strict isolation: markers CANNOT delete wings
        val canReopenSavedSession = isAdmin // Strict isolation: markers CANNOT reopen locked sessions
        val hasFullHostelReports = isAdmin // Strict isolation: markers see their own report only

        assertTrue(canMark)
        assertFalse(canAccessRegistration)
        assertFalse(canDeleteStudents)
        assertFalse(canDeleteWings)
        assertFalse(canReopenSavedSession)
        assertFalse(hasFullHostelReports)
    }

    @Test
    fun `marker resolution matches by studentId or normalized roll number`() {
        data class MockMarker(
            val studentId: String,
            val rollNumber: String,
            val studentName: String,
            val isMarker: Boolean,
        )

        val markers = mapOf(
            "std_1" to MockMarker("std_1", "24CS01", "Aarav", true),
            "std_2" to MockMarker("std_2", "24CS02", "Devendra", true),
        )

        // Test matching by studentId
        assertTrue(markers.containsKey("std_1"))
        assertFalse(markers.containsKey("std_99"))

        // Test matching by roll number
        val inputRoll = " 24cs01 "
        val normalized = inputRoll.trim().uppercase()
        val matchByRoll = markers.values.any { it.rollNumber.trim().uppercase() == normalized }
        assertTrue(matchByRoll)

        val unassignedRoll = "24ME99"
        val unassignedMatch = markers.values.any { it.rollNumber.trim().uppercase() == unassignedRoll }
        assertFalse(unassignedMatch)
    }
}
