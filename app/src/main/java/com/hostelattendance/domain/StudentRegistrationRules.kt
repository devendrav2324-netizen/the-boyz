package com.hostelattendance.domain

import java.util.Locale

data class StudentRegistrationInput(
    val rollNumber: String,
    val wingId: String,
    val roomNumber: String,
    val studentName: String,
    val branch: String,
    val year: String,
)

data class ValidStudentRegistration(
    val rollNumber: String,
    val normalizedRollNumber: String,
    val wingId: String,
    val roomNumber: String,
    val studentName: String,
    val branch: String?,
    val year: String?,
)

data class RegistrationValidation(
    val value: ValidStudentRegistration?,
    val errors: Map<String, String>,
)

object StudentRegistrationRules {
    fun validate(input: StudentRegistrationInput): RegistrationValidation {
        val roll = input.rollNumber.trim()
        val wing = input.wingId.trim()
        val room = input.roomNumber.trim()
        val name = input.studentName.trim()
        val errors = buildMap {
            if (roll.isEmpty()) put("rollNumber", "Enter a roll number.")
            if (wing.isEmpty()) put("wingId", "Select a wing.")
            if (room.isEmpty()) put("roomNumber", "Enter a room number.")
            if (name.isEmpty()) put("studentName", "Enter the student name.")
        }
        if (errors.isNotEmpty()) return RegistrationValidation(null, errors)

        return RegistrationValidation(
            value = ValidStudentRegistration(
                rollNumber = roll,
                normalizedRollNumber = normalizeRollNumber(roll),
                wingId = wing,
                roomNumber = room,
                studentName = name,
                branch = input.branch.trim().ifEmpty { null },
                year = input.year.trim().ifEmpty { null },
            ),
            errors = emptyMap(),
        )
    }

    fun normalizeRollNumber(value: String): String =
        value.trim().uppercase(Locale.ROOT)

    fun normalizeWingName(value: String): String =
        value.trim().uppercase(Locale.ROOT)
}
