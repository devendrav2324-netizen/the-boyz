package com.hostelattendance.domain

import com.hostelattendance.data.local.StudentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentRemovalAndSearchTest {

    private val sampleStudents = listOf(
        StudentEntity(
            studentId = "s1",
            hostelId = "hostel-1",
            rollNumber = "101",
            normalizedRollNumber = "101",
            currentWingId = "wing-a",
            currentRoomNumber = "201",
            studentName = "Devendra Verma",
            branch = "CSE",
            year = "3rd",
            isActive = true,
            createdAtMillis = 1000L,
            updatedAtMillis = 1000L,
        ),
        StudentEntity(
            studentId = "s2",
            hostelId = "hostel-1",
            rollNumber = "102",
            normalizedRollNumber = "102",
            currentWingId = "wing-a",
            currentRoomNumber = "202",
            studentName = "Rahul Sharma",
            branch = "ECE",
            year = "3rd",
            isActive = true,
            createdAtMillis = 1000L,
            updatedAtMillis = 1000L,
        ),
        StudentEntity(
            studentId = "s3",
            hostelId = "hostel-1",
            rollNumber = "103",
            normalizedRollNumber = "103",
            currentWingId = "wing-b",
            currentRoomNumber = "301",
            studentName = "Aman Kumar",
            branch = "ME",
            year = "2nd",
            isActive = true,
            createdAtMillis = 1000L,
            updatedAtMillis = 1000L,
        ),
    )

    @Test
    fun filterStudentsByName_matchesCaseInsensitive() {
        val query = "devendra"
        val filtered = sampleStudents.filter {
            it.studentName.contains(query, ignoreCase = true) ||
            it.rollNumber.contains(query, ignoreCase = true) ||
            it.currentRoomNumber.contains(query, ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("Devendra Verma", filtered[0].studentName)
    }

    @Test
    fun filterStudentsByRollNumber_matches() {
        val query = "102"
        val filtered = sampleStudents.filter {
            it.studentName.contains(query, ignoreCase = true) ||
            it.rollNumber.contains(query, ignoreCase = true) ||
            it.currentRoomNumber.contains(query, ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("Rahul Sharma", filtered[0].studentName)
    }

    @Test
    fun filterStudentsByRoomNumber_matches() {
        val query = "301"
        val filtered = sampleStudents.filter {
            it.studentName.contains(query, ignoreCase = true) ||
            it.rollNumber.contains(query, ignoreCase = true) ||
            it.currentRoomNumber.contains(query, ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("Aman Kumar", filtered[0].studentName)
    }

    @Test
    fun deleteStudentFromList_removesTargetStudent() {
        val studentList = sampleStudents.toMutableList()
        val studentToDeleteId = "s2"
        studentList.removeAll { it.studentId == studentToDeleteId }

        assertEquals(2, studentList.size)
        assertTrue(studentList.none { it.studentId == "s2" })
    }

    @Test
    fun deleteWingFromList_removesAllWingStudents() {
        val studentList = sampleStudents.toMutableList()
        val wingToDeleteId = "wing-a"
        studentList.removeAll { it.currentWingId == wingToDeleteId }

        assertEquals(1, studentList.size)
        assertEquals("Aman Kumar", studentList[0].studentName)
    }
}
