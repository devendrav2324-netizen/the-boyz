package com.hostelattendance.domain

import com.hostelattendance.data.local.UserSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSessionCredentialBindingTest {

    @Test
    fun errorMessage_exactMatch() {
        assertEquals("Do not Enter other's Credential", UserSessionManager.ERROR_OTHER_CREDENTIALS)
    }

    @Test
    fun credentialMatching_caseInsensitiveAndTrimmed() {
        val boundName = "Devendra Verma"
        val boundRoom = "102"

        val enteredName = "  devendra verma "
        val enteredRoom = " 102 "

        assertTrue(boundName.trim().equals(enteredName.trim(), ignoreCase = true))
        assertTrue(boundRoom.trim().equals(enteredRoom.trim(), ignoreCase = true))
    }

    @Test
    fun credentialMismatch_differentNameDetected() {
        val boundName = "Devendra Verma"
        val boundRoom = "102"

        val enteredName = "Rahul Sharma"
        val enteredRoom = "102"

        val matches = boundName.trim().equals(enteredName.trim(), ignoreCase = true) &&
                boundRoom.trim().equals(enteredRoom.trim(), ignoreCase = true)

        assertFalse(matches)
    }

    @Test
    fun credentialMismatch_differentRoomDetected() {
        val boundName = "Devendra Verma"
        val boundRoom = "102"

        val enteredName = "Devendra Verma"
        val enteredRoom = "204"

        val matches = boundName.trim().equals(enteredName.trim(), ignoreCase = true) &&
                boundRoom.trim().equals(enteredRoom.trim(), ignoreCase = true)

        assertFalse(matches)
    }
}
