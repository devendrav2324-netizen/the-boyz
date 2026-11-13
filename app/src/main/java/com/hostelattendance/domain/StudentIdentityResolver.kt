package com.hostelattendance.domain

import com.hostelattendance.data.local.StudentEntity

/**
 * Deterministic and strict student identity resolution.
 * Completely eliminates fuzzy substring matching (`.contains()`) that causes
 * cross-student identity switching and cross-record contamination.
 */
object StudentIdentityResolver {

    /**
     * Resolves a student entity from a roster of students using a strict hierarchical strategy:
     * 1. Exact immutable `studentId` match
     * 2. Exact `rollNumber` match (case-insensitive, trimmed)
     * 3. Exact full `studentName` AND exact `roomNumber` match
     * 4. Exact full `studentName` match
     *
     * Returns null if no exact match is found. Never performs loose substring matches.
     */
    fun resolveStudent(
        roster: List<StudentEntity>,
        targetStudentId: String?,
        targetRollNumber: String?,
        targetName: String?,
        targetRoomNumber: String?,
    ): StudentEntity? {
        val normId = targetStudentId?.trim().orEmpty()
        val normRoll = targetRollNumber?.trim().orEmpty()
        val normName = targetName?.trim().orEmpty()
        val normRoom = targetRoomNumber?.trim().orEmpty()

        if (normId.isBlank() && normRoll.isBlank() && normName.isBlank()) {
            return null
        }

        // Tier 1: Exact Student ID (Unique primary key)
        if (normId.isNotBlank()) {
            val byId = roster.firstOrNull { it.studentId.equals(normId, ignoreCase = true) }
            if (byId != null) return byId
        }

        // Tier 2: Exact Roll Number
        if (normRoll.isNotBlank()) {
            val byRoll = roster.firstOrNull {
                it.rollNumber.trim().equals(normRoll, ignoreCase = true) ||
                it.normalizedRollNumber.trim().equals(normRoll, ignoreCase = true)
            }
            if (byRoll != null) return byRoll
        }

        // Tier 3: Exact Full Name AND Exact Room Number (discards any same-name resident in another room)
        if (normName.isNotBlank() && normRoom.isNotBlank()) {
            val byNameAndRoom = roster.firstOrNull {
                it.studentName.trim().equals(normName, ignoreCase = true) &&
                it.currentRoomNumber.trim().equals(normRoom, ignoreCase = true)
            }
            if (byNameAndRoom != null) return byNameAndRoom
        }

        // Tier 4: Exact Full Name
        if (normName.isNotBlank()) {
            val byExactName = roster.firstOrNull {
                it.studentName.trim().equals(normName, ignoreCase = true)
            }
            if (byExactName != null) return byExactName
        }

        return null
    }

    /**
     * Safely constructs a fallback StudentEntity when a user's record has not yet been registered
     * in the local database roster, ensuring NO arbitrary roll number or third-party details are injected.
     */
    fun createFallbackStudent(
        hostelId: String,
        studentName: String,
        roomNumber: String,
        studentId: String? = null,
        rollNumber: String? = null,
    ): StudentEntity {
        val safeName = studentName.trim().ifBlank { "Hosteller" }
        val safeRoom = roomNumber.trim().ifBlank { "—" }
        val safeId = studentId?.trim()?.ifBlank { null } ?: "self_${safeName.lowercase().replace("\\s+".toRegex(), "_")}_${safeRoom}"
        val safeRoll = rollNumber?.trim()?.ifBlank { null } ?: "—"

        val now = System.currentTimeMillis()
        return StudentEntity(
            studentId = safeId,
            hostelId = hostelId,
            rollNumber = safeRoll,
            normalizedRollNumber = safeRoll,
            currentWingId = "Main",
            currentRoomNumber = safeRoom,
            studentName = safeName,
            branch = null,
            year = null,
            isActive = true,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }
}
