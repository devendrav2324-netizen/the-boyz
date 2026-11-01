package com.hostelattendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AttendanceRulesTest {
    @Test
    fun `new draft records start not marked and preserve roster snapshots`() {
        val records = AttendanceRules.newDraftRecords(
            hostelId = "hostel-1",
            localDate = LocalDate.parse("2026-10-25"),
            sessionId = "hostel-1:2026-10-25:wing-a",
            students = listOf(
                AttendanceStudentSnapshot(
                    studentId = "student-1",
                    rollNumber = " 2401 ",
                    studentName = "Aarav Sharma",
                    wingId = "wing-a",
                    wingName = "Wing A",
                    roomNumber = "101",
                ),
            ),
            nowMillis = 42L,
        )

        assertEquals(1, records.size)
        assertEquals("NOT_MARKED", records.single().status)
        assertEquals("2401", records.single().rollNumberSnapshot)
        assertEquals("Aarav Sharma", records.single().studentNameSnapshot)
        assertEquals("101", records.single().roomNumberSnapshot)
        assertEquals(0L, records.single().version)
        assertEquals(42L, records.single().createdAtMillis)
    }

    @Test
    fun `today uses the hostel timezone`() {
        val instant = Instant.parse("2026-10-24T19:00:00Z")

        assertEquals(LocalDate.parse("2026-10-25"), AttendanceRules.today(instant))
    }

    @Test
    fun `summary reconciles marked and not marked statuses`() {
        val totals = AttendanceRules.totals(
            listOf("PRESENT", "PRESENT", "ABSENT", "LEAVE", "NOT_MARKED"),
        )

        assertEquals(4, totals.marked)
        assertEquals(5, totals.total)
        assertEquals(2, totals.present)
        assertEquals(1, totals.absent)
        assertEquals(1, totals.leave)
        assertEquals(1, totals.notMarked)
    }

    @Test
    fun `saving incomplete attendance is not allowed`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttendanceRules.requireSaveAllowed("DRAFT", notMarkedCount = 1, confirmIncomplete = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AttendanceRules.requireSaveAllowed("DRAFT", notMarkedCount = 1, confirmIncomplete = true)
        }
        AttendanceRules.requireSaveAllowed("DRAFT", notMarkedCount = 0, confirmIncomplete = false)
    }

    @Test
    fun `saved attendance cannot be changed by the save workflow`() {
        assertThrows(IllegalArgumentException::class.java) {
            AttendanceRules.requireSaveAllowed("SAVED", notMarkedCount = 0, confirmIncomplete = false)
        }
    }

    @Test
    fun `record IDs are stable per student and hostel-local date`() {
        val date = LocalDate.parse("2026-10-25")

        assertEquals(
            AttendanceRules.recordId("hostel-1", "student-1", date),
            AttendanceRules.recordId("hostel-1", "student-1", date),
        )
        assertEquals(
            "hostel-1:student-1:2026-10-25",
            AttendanceRules.recordId("hostel-1", "student-1", date),
        )
    }

    @Test
    fun `natural roll number comparator sorts numeric and alphanumeric roll numbers in natural increasing order`() {
        val unsorted = listOf("10", "2", "1", "20", "CS-10", "CS-2", "CS-1", "A-5", "A-05", "100", "B-1")
        val sorted = unsorted.sortedWith(NaturalRollNumberComparator)
        // 1, 2, 10, 20, 100, then alphanumeric groups sorted naturally
        val expected = listOf("1", "2", "10", "20", "100", "A-5", "A-05", "B-1", "CS-1", "CS-2", "CS-10")
        assertEquals(expected, sorted)
    }
}
