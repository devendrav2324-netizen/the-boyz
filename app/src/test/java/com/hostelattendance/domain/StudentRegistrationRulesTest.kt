package com.hostelattendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentRegistrationRulesTest {
    @Test
    fun `required fields are validated and values are trimmed`() {
        val empty = StudentRegistrationRules.validate(
            StudentRegistrationInput("", "", "", "", "", ""),
        )

        assertNull(empty.value)
        assertEquals(
            setOf("rollNumber", "wingId", "roomNumber", "studentName"),
            empty.errors.keys,
        )

        val valid = StudentRegistrationRules.validate(
            StudentRegistrationInput(
                rollNumber = " 2401 ",
                wingId = " wing-a ",
                roomNumber = " 101 ",
                studentName = " Aarav Sharma ",
                branch = " ",
                year = "",
            ),
        )
        assertTrue(valid.errors.isEmpty())
        assertNotNull(valid.value)
        assertEquals("2401", valid.value?.rollNumber)
        assertEquals("2401", valid.value?.normalizedRollNumber)
        assertEquals("Aarav Sharma", valid.value?.studentName)
        assertNull(valid.value?.branch)
        assertNull(valid.value?.year)
    }

    @Test
    fun `roll and wing uniqueness keys normalize case and whitespace`() {
        assertEquals("CS-2401", StudentRegistrationRules.normalizeRollNumber(" cs-2401 "))
        assertEquals("WING A", StudentRegistrationRules.normalizeWingName(" Wing A "))
    }
}
