package com.hostelattendance.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IdCardFormattingTest {

    @Test
    fun splitFullName_multiWord() {
        val fullName = "Samira Hadid"
        val parts = fullName.trim().split("\\s+".toRegex())
        val firstName = parts.firstOrNull()?.uppercase() ?: "HOSTELLER"
        val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ").uppercase() else "RESIDENT"

        assertEquals("SAMIRA", firstName)
        assertEquals("HADID", lastName)
    }

    @Test
    fun splitFullName_singleWord() {
        val fullName = "Devendra"
        val parts = fullName.trim().split("\\s+".toRegex())
        val firstName = parts.firstOrNull()?.uppercase() ?: "HOSTELLER"
        val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ").uppercase() else "RESIDENT"

        assertEquals("DEVENDRA", firstName)
        assertEquals("RESIDENT", lastName)
    }

    @Test
    fun formatRoomNumber() {
        val rawRoom = " 102 "
        val formatted = "ROOM NUMBER- ${rawRoom.trim()}"
        assertEquals("ROOM NUMBER- 102", formatted)
    }

    @Test
    fun formatRoomNumber_complexFormat() {
        val rawRoom = "FF/05/48"
        val formatted = "ROOM NUMBER- ${rawRoom.trim()}"
        assertEquals("ROOM NUMBER- FF/05/48", formatted)
    }
}
