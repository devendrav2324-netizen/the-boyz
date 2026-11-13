package com.hostelattendance.domain

import com.hostelattendance.data.local.StudentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StudentIdentityResolverTest {

    private val roster = listOf(
        StudentEntity(
            studentId = "stu_01",
            hostelId = "hostel_1",
            rollNumber = "101",
            normalizedRollNumber = "101",
            currentWingId = "A",
            currentRoomNumber = "101",
            studentName = "Devendra Singh",
            branch = "CSE",
            year = "3",
            isActive = true,
            createdAtMillis = 1000L,
            updatedAtMillis = 1000L,
        ),
        StudentEntity(
            studentId = "stu_02",
            hostelId = "hostel_1",
            rollNumber = "102",
            normalizedRollNumber = "102",
            currentWingId = "B",
            currentRoomNumber = "102",
            studentName = "Devendra Verma",
            branch = "ECE",
            year = "3",
            isActive = true,
            createdAtMillis = 1000L,
            updatedAtMillis = 1000L,
        ),
        StudentEntity(
            studentId = "stu_03",
            hostelId = "hostel_1",
            rollNumber = "103",
            normalizedRollNumber = "103",
            currentWingId = "A",
            currentRoomNumber = "103",
            studentName = "Aman Kumar",
            branch = "ME",
            year = "2",
            isActive = true,
            createdAtMillis = 1000L,
            updatedAtMillis = 1000L,
        ),
    )

    @Test
    fun resolveStudent_byId_takesHighestPriority() {
        val resolved = StudentIdentityResolver.resolveStudent(
            roster = roster,
            targetStudentId = "stu_02",
            targetRollNumber = "101", // even if roll mismatch, studentId is Tier 1
            targetName = "Devendra Singh",
            targetRoomNumber = "101",
        )
        assertNotNull(resolved)
        assertEquals("stu_02", resolved?.studentId)
        assertEquals("Devendra Verma", resolved?.studentName)
    }

    @Test
    fun resolveStudent_byRollNumber_resolvesAccurately() {
        val resolved = StudentIdentityResolver.resolveStudent(
            roster = roster,
            targetStudentId = null,
            targetRollNumber = "102",
            targetName = null,
            targetRoomNumber = null,
        )
        assertNotNull(resolved)
        assertEquals("stu_02", resolved?.studentId)
        assertEquals("Devendra Verma", resolved?.studentName)
    }

    @Test
    fun resolveStudent_byNameAndRoom_disambiguatesSameFirstNames() {
        // Disambiguate between Devendra Singh (Room 101) and Devendra Verma (Room 102)
        val resolvedVerma = StudentIdentityResolver.resolveStudent(
            roster = roster,
            targetStudentId = null,
            targetRollNumber = null,
            targetName = "Devendra Verma",
            targetRoomNumber = "102",
        )
        assertNotNull(resolvedVerma)
        assertEquals("stu_02", resolvedVerma?.studentId)
        assertEquals("Devendra Verma", resolvedVerma?.studentName)
        assertEquals("102", resolvedVerma?.currentRoomNumber)

        val resolvedSingh = StudentIdentityResolver.resolveStudent(
            roster = roster,
            targetStudentId = null,
            targetRollNumber = null,
            targetName = "Devendra Singh",
            targetRoomNumber = "101",
        )
        assertNotNull(resolvedSingh)
        assertEquals("stu_01", resolvedSingh?.studentId)
        assertEquals("Devendra Singh", resolvedSingh?.studentName)
        assertEquals("101", resolvedSingh?.currentRoomNumber)
    }

    @Test
    fun resolveStudent_neverMatchesPartialSubstringToWrongPerson() {
        // "Devendra" alone does not match if full names are distinct, preventing arbitrary firstOrNull grab
        val resolved = StudentIdentityResolver.resolveStudent(
            roster = roster,
            targetStudentId = null,
            targetRollNumber = null,
            targetName = "Dev",
            targetRoomNumber = null,
        )
        assertNull("Partial name substring should never match a different person", resolved)
    }

    @Test
    fun createFallbackStudent_neverAssignsArbitraryRollNumberOne() {
        val fallback = StudentIdentityResolver.createFallbackStudent(
            hostelId = "hostel_1",
            studentName = "John Doe",
            roomNumber = "205",
            studentId = null,
            rollNumber = null,
        )
        assertEquals("John Doe", fallback.studentName)
        assertEquals("205", fallback.currentRoomNumber)
        assertEquals("—", fallback.rollNumber) // NOT "1"
        assertNotNull(fallback.studentId)
    }
}
