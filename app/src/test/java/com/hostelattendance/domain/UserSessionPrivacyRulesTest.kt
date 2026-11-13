package com.hostelattendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class UserSessionPrivacyRulesTest {

    @Test
    fun getGreetingForTime_returnsMorningFor8AM() {
        val greeting = UserSessionPrivacyRules.getGreetingForTime(LocalTime.of(8, 30))
        assertEquals("Good morning", greeting)
    }

    @Test
    fun getGreetingForTime_returnsAfternoonFor2PM() {
        val greeting = UserSessionPrivacyRules.getGreetingForTime(LocalTime.of(14, 0))
        assertEquals("Good afternoon", greeting)
    }

    @Test
    fun getGreetingForTime_returnsEveningFor7PM() {
        val greeting = UserSessionPrivacyRules.getGreetingForTime(LocalTime.of(19, 15))
        assertEquals("Good evening", greeting)
    }

    @Test
    fun formatDisplayDate_formatsCorrectly() {
        val date = LocalDate.of(2026, 11, 2)
        val formatted = UserSessionPrivacyRules.formatDisplayDate(date)
        assertEquals("Mon, 2 Nov 2026", formatted)
    }

    @Test
    fun extractFirstName_handlesMultiWordNames() {
        assertEquals("Devendra", UserSessionPrivacyRules.extractFirstName("devendra verma"))
        assertEquals("Alice", UserSessionPrivacyRules.extractFirstName("Alice"))
        assertEquals("Student", UserSessionPrivacyRules.extractFirstName(null))
        assertEquals("Student", UserSessionPrivacyRules.extractFirstName("   "))
    }

    @Test
    fun calculateMonthlyStats_computesCorrectly() {
        val statuses = listOf("PRESENT", "PRESENT", "ABSENT", "LEAVE", "PRESENT")
        val stats = UserSessionPrivacyRules.calculateMonthlyStats(statuses)
        assertEquals(5, stats.totalDaysMarked)
        assertEquals(3, stats.totalPresent)
        assertEquals(1, stats.totalAbsent)
        assertEquals(1, stats.totalLeave)
        assertEquals(60.0f, stats.attendancePercentage, 0.01f)
    }

    @Test
    fun calculateMonthlyStats_handlesEmptyList() {
        val stats = UserSessionPrivacyRules.calculateMonthlyStats(emptyList())
        assertEquals(0, stats.totalDaysMarked)
        assertEquals(0, stats.totalPresent)
        assertEquals(0f, stats.attendancePercentage, 0.01f)
    }

    @Test
    fun accessRules_enforceAdminOnly() {
        assertTrue(UserSessionPrivacyRules.canAccessFullRoster("ADMIN"))
        assertTrue(UserSessionPrivacyRules.canAccessFullRoster("admin"))
        assertFalse(UserSessionPrivacyRules.canAccessFullRoster("USER"))
        assertFalse(UserSessionPrivacyRules.canAccessFullRoster(null))

        assertTrue(UserSessionPrivacyRules.canMarkAttendance("ADMIN"))
        assertFalse(UserSessionPrivacyRules.canMarkAttendance("USER"))
    }

    @Test
    fun filterRecordsForUser_strictlyProtectsPrivacy() {
        data class MockRecord(val id: String, val studentId: String, val studentName: String)

        val records = listOf(
            MockRecord("1", "S101", "Devendra"),
            MockRecord("2", "S102", "Rahul Sharma"),
            MockRecord("3", "S103", "Amit Kumar"),
        )

        val devendraRecords = UserSessionPrivacyRules.filterRecordsForUser(
            records = records,
            userStudentId = "S101",
            userStudentName = "Devendra",
            getStudentId = { it.studentId },
            getStudentName = { it.studentName },
        )

        assertEquals(1, devendraRecords.size)
        assertEquals("S101", devendraRecords.first().studentId)
        assertEquals("Devendra", devendraRecords.first().studentName)

        val emptyUserRecords = UserSessionPrivacyRules.filterRecordsForUser(
            records = records,
            userStudentId = null,
            userStudentName = null,
            getStudentId = { it.studentId },
            getStudentName = { it.studentName },
        )
        assertTrue(emptyUserRecords.isEmpty())
    }
}
