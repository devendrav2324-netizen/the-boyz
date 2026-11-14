package com.hostelattendance.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object UserSessionManager {
    private const val PREFS_NAME = "user_session_prefs"
    private const val KEY_HOSTELLER_NAME = "hosteller_name_"
    private const val KEY_ROOM_NUMBER = "room_number_"
    private const val KEY_STUDENT_ID = "student_id_"
    private const val KEY_ROLL_NUMBER = "roll_number_"
    private const val KEY_HAS_ENTERED = "has_entered_"

    // Permanent Email-Credential Binding Keys
    private const val KEY_BOUND_NAME_EMAIL = "bound_name_email_"
    private const val KEY_BOUND_ROOM_EMAIL = "bound_room_email_"
    private const val KEY_BOUND_STUDENT_ID_EMAIL = "bound_student_id_email_"
    private const val KEY_BOUND_ROLL_EMAIL = "bound_roll_email_"
    private const val KEY_CLAIMED_HOSTELLER = "claimed_hosteller_"

    const val ERROR_OTHER_CREDENTIALS = "Do not Enter other's Credential"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun sanitizeEmailKey(email: String): String {
        return email.trim().lowercase().replace(".", "_").replace("@", "_at_")
    }

    private fun makeHostellerKey(name: String, room: String): String {
        return "${name.trim().lowercase()}__${room.trim().lowercase()}"
    }

    fun hasSelectedHosteller(context: Context, uid: String): Boolean {
        if (uid.isBlank()) return false
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_HAS_ENTERED + uid, false) &&
                !prefs.getString(KEY_HOSTELLER_NAME + uid, null).isNullOrBlank()
    }

    fun getHostellerName(context: Context, uid: String): String? {
        if (uid.isBlank()) return null
        return getPrefs(context).getString(KEY_HOSTELLER_NAME + uid, null)
    }

    fun getRoomNumber(context: Context, uid: String): String? {
        if (uid.isBlank()) return null
        return getPrefs(context).getString(KEY_ROOM_NUMBER + uid, null)
    }

    fun getStudentId(context: Context, uid: String): String? {
        if (uid.isBlank()) return null
        return getPrefs(context).getString(KEY_STUDENT_ID + uid, null)
    }

    fun getRollNumber(context: Context, uid: String): String? {
        if (uid.isBlank()) return null
        return getPrefs(context).getString(KEY_ROLL_NUMBER + uid, null)
    }

    // --- Email-Credential Binding Accessors ---

    fun getBoundHostellerName(context: Context, email: String): String? {
        if (email.isBlank()) return null
        val key = sanitizeEmailKey(email)
        return getPrefs(context).getString(KEY_BOUND_NAME_EMAIL + key, null)
    }

    fun getBoundRoomNumber(context: Context, email: String): String? {
        if (email.isBlank()) return null
        val key = sanitizeEmailKey(email)
        return getPrefs(context).getString(KEY_BOUND_ROOM_EMAIL + key, null)
    }

    fun getBoundStudentId(context: Context, email: String): String? {
        if (email.isBlank()) return null
        val key = sanitizeEmailKey(email)
        return getPrefs(context).getString(KEY_BOUND_STUDENT_ID_EMAIL + key, null)
    }

    fun getBoundRollNumber(context: Context, email: String): String? {
        if (email.isBlank()) return null
        val key = sanitizeEmailKey(email)
        return getPrefs(context).getString(KEY_BOUND_ROLL_EMAIL + key, null)
    }

    fun hasBinding(context: Context, email: String): Boolean {
        return !getBoundHostellerName(context, email).isNullOrBlank() &&
                !getBoundRoomNumber(context, email).isNullOrBlank()
    }

    fun saveLocalBinding(
        context: Context,
        email: String,
        hostellerName: String,
        roomNumber: String,
        studentId: String? = null,
        rollNumber: String? = null,
    ) {
        if (email.isBlank()) return
        val key = sanitizeEmailKey(email)
        getPrefs(context).edit()
            .putString(KEY_BOUND_NAME_EMAIL + key, hostellerName.trim())
            .putString(KEY_BOUND_ROOM_EMAIL + key, roomNumber.trim())
            .putString(KEY_BOUND_STUDENT_ID_EMAIL + key, studentId?.trim())
            .putString(KEY_BOUND_ROLL_EMAIL + key, rollNumber?.trim())
            .apply()
    }

    private fun getClaimedEmailForHosteller(context: Context, name: String, room: String): String? {
        val hKey = makeHostellerKey(name, room)
        return getPrefs(context).getString(KEY_CLAIMED_HOSTELLER + hKey, null)
    }

    private fun recordClaimedHosteller(context: Context, name: String, room: String, email: String) {
        val hKey = makeHostellerKey(name, room)
        getPrefs(context).edit()
            .putString(KEY_CLAIMED_HOSTELLER + hKey, email.trim().lowercase())
            .apply()
    }

    fun saveSelectedHosteller(
        context: Context,
        uid: String,
        hostellerName: String,
        roomNumber: String,
        studentId: String? = null,
        rollNumber: String? = null,
    ) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_HOSTELLER_NAME + uid, hostellerName.trim())
            .putString(KEY_ROOM_NUMBER + uid, roomNumber.trim())
            .putString(KEY_STUDENT_ID + uid, studentId?.trim())
            .putString(KEY_ROLL_NUMBER + uid, rollNumber?.trim())
            .putBoolean(KEY_HAS_ENTERED + uid, true)
            .apply()

        // Sync to Firestore user profile for cloud backup and multi-device persistence
        if (uid.isNotBlank()) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("user_profiles")
                    .document(uid)
                    .set(
                        mapOf(
                            "hostellerName" to hostellerName.trim(),
                            "roomNumber" to roomNumber.trim(),
                            "studentId" to studentId?.trim(),
                            "rollNumber" to rollNumber?.trim(),
                            "updatedAtMillis" to System.currentTimeMillis(),
                        ),
                        SetOptions.merge()
                    )
            } catch (_: Exception) {}
        }
    }

    /**
     * Clear only the active entered session flag, keeping the permanent email-to-hosteller binding intact.
     */
    fun clearActiveSession(context: Context, uid: String) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_HAS_ENTERED + uid)
            .remove(KEY_HOSTELLER_NAME + uid)
            .remove(KEY_ROOM_NUMBER + uid)
            .remove(KEY_STUDENT_ID + uid)
            .remove(KEY_ROLL_NUMBER + uid)
            .apply()
    }

    fun clearSelectedHosteller(context: Context, uid: String) {
        clearActiveSession(context, uid)
    }

    /**
     * Fetch binding from Cloud Firestore if available.
     */
    suspend fun fetchBindingFromCloud(
        context: Context,
        email: String,
        uid: String?,
    ): Pair<String, String>? {
        if (email.isBlank()) return null
        val cleanEmail = email.trim().lowercase()
        val sanitizedEmail = sanitizeEmailKey(cleanEmail)

        try {
            val firestore = FirebaseFirestore.getInstance()
            val bindingDoc = firestore.collection("credential_bindings")
                .document(sanitizedEmail)
                .get()
                .await()

            if (bindingDoc.exists()) {
                val name = bindingDoc.getString("hostellerName")
                val room = bindingDoc.getString("roomNumber")
                val sId = bindingDoc.getString("studentId")
                val roll = bindingDoc.getString("rollNumber")
                if (!name.isNullOrBlank() && !room.isNullOrBlank()) {
                    saveLocalBinding(context, cleanEmail, name, room, sId, roll)
                    return Pair(name, room)
                }
            }

            if (!uid.isNullOrBlank()) {
                val userDoc = firestore.collection("user_profiles")
                    .document(uid)
                    .get()
                    .await()
                val boundName = userDoc.getString("boundHostellerName") ?: userDoc.getString("hostellerName")
                val boundRoom = userDoc.getString("boundRoomNumber") ?: userDoc.getString("roomNumber")
                val boundSid = userDoc.getString("boundStudentId") ?: userDoc.getString("studentId")
                val boundRoll = userDoc.getString("boundRollNumber") ?: userDoc.getString("rollNumber")
                if (!boundName.isNullOrBlank() && !boundRoom.isNullOrBlank()) {
                    saveLocalBinding(context, cleanEmail, boundName, boundRoom, boundSid, boundRoll)
                    return Pair(boundName, boundRoom)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Validates whether the entered credentials match the email binding.
     * If the email is already bound, only the matching hosteller name and room are permitted.
     * If the credentials do not match, returns Pair(false, "Do not Enter other's Credential").
     * If this is the first login for this email, binds the credentials and saves to local & cloud.
     */
    suspend fun validateAndBind(
        context: Context,
        email: String,
        uid: String,
        enteredName: String,
        enteredRoom: String,
        studentId: String?,
        rollNumber: String?,
    ): Pair<Boolean, String?> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = enteredName.trim()
        val cleanRoom = enteredRoom.trim()

        if (cleanEmail.isBlank()) {
            saveSelectedHosteller(context, uid, cleanName, cleanRoom, studentId, rollNumber)
            return Pair(true, null)
        }

        val sanitizedEmail = sanitizeEmailKey(cleanEmail)
        val hostellerKey = makeHostellerKey(cleanName, cleanRoom)

        // 1. Check local SharedPreferences binding
        var boundName = getBoundHostellerName(context, cleanEmail)
        var boundRoom = getBoundRoomNumber(context, cleanEmail)
        var boundStudentId = getBoundStudentId(context, cleanEmail)
        var boundRollNumber = getBoundRollNumber(context, cleanEmail)

        // 2. If not found locally, fetch from Cloud Firestore
        if (boundName.isNullOrBlank() || boundRoom.isNullOrBlank()) {
            val cloud = fetchBindingFromCloud(context, cleanEmail, uid)
            if (cloud != null) {
                boundName = cloud.first
                boundRoom = cloud.second
                boundStudentId = getBoundStudentId(context, cleanEmail)
                boundRollNumber = getBoundRollNumber(context, cleanEmail)
            }
        }

        // 3. If this email already has bound credentials:
        if (!boundName.isNullOrBlank() && !boundRoom.isNullOrBlank()) {
            val matchesName = boundName.trim().equals(cleanName, ignoreCase = true)
            val matchesRoom = boundRoom.trim().equals(cleanRoom, ignoreCase = true)

            if (!matchesName || !matchesRoom) {
                // User enters someone else's credentials: reject immediately!
                return Pair(false, ERROR_OTHER_CREDENTIALS)
            }

            // Credentials match!
            saveSelectedHosteller(
                context = context,
                uid = uid,
                hostellerName = boundName,
                roomNumber = boundRoom,
                studentId = boundStudentId ?: studentId,
                rollNumber = boundRollNumber ?: rollNumber,
            )
            return Pair(true, null)
        }

        // 4. If no prior binding for this email (first time binding):
        // Verify whether another email has already claimed this hosteller
        try {
            val firestore = FirebaseFirestore.getInstance()
            val claimDoc = firestore.collection("hosteller_bindings")
                .document(hostellerKey)
                .get()
                .await()

            if (claimDoc.exists()) {
                val claimedByEmail = claimDoc.getString("email")
                if (!claimedByEmail.isNullOrBlank() && !claimedByEmail.equals(cleanEmail, ignoreCase = true)) {
                    // Hosteller belongs to another student's email!
                    return Pair(false, ERROR_OTHER_CREDENTIALS)
                }
            }
        } catch (_: Exception) {}

        val localClaimedEmail = getClaimedEmailForHosteller(context, cleanName, cleanRoom)
        if (!localClaimedEmail.isNullOrBlank() && !localClaimedEmail.equals(cleanEmail, ignoreCase = true)) {
            return Pair(false, ERROR_OTHER_CREDENTIALS)
        }

        // First-time valid binding: save locally & cloud
        saveLocalBinding(context, cleanEmail, cleanName, cleanRoom, studentId, rollNumber)
        recordClaimedHosteller(context, cleanName, cleanRoom, cleanEmail)

        saveSelectedHosteller(
            context = context,
            uid = uid,
            hostellerName = cleanName,
            roomNumber = cleanRoom,
            studentId = studentId,
            rollNumber = rollNumber,
        )

        try {
            val firestore = FirebaseFirestore.getInstance()
            val bindingData = mapOf(
                "email" to cleanEmail,
                "uid" to uid,
                "hostellerName" to cleanName,
                "roomNumber" to cleanRoom,
                "studentId" to studentId,
                "rollNumber" to rollNumber,
                "boundAtMillis" to System.currentTimeMillis(),
            )
            firestore.collection("credential_bindings").document(sanitizedEmail)
                .set(bindingData, SetOptions.merge())

            firestore.collection("hosteller_bindings").document(hostellerKey)
                .set(
                    mapOf(
                        "email" to cleanEmail,
                        "uid" to uid,
                        "hostellerName" to cleanName,
                        "roomNumber" to cleanRoom,
                        "boundAtMillis" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge()
                )

            if (uid.isNotBlank()) {
                firestore.collection("user_profiles").document(uid)
                    .set(
                        mapOf(
                            "boundEmail" to cleanEmail,
                            "boundHostellerName" to cleanName,
                            "boundRoomNumber" to cleanRoom,
                            "boundStudentId" to studentId,
                            "boundRollNumber" to rollNumber,
                        ),
                        SetOptions.merge()
                    )
            }
        } catch (_: Exception) {}

        return Pair(true, null)
    }

    // --- ID Card Photo Management ---
    private const val KEY_ID_CARD_PHOTO_PATH = "id_card_photo_path_"

    fun getIdCardPhotoPath(context: Context, uid: String): String? {
        if (uid.isBlank()) return null
        val path = getPrefs(context).getString(KEY_ID_CARD_PHOTO_PATH + uid, null)
        if (path != null && java.io.File(path).exists()) {
            return path
        }
        return null
    }

    fun saveIdCardPhotoFromUri(context: Context, uid: String, uri: android.net.Uri): String? {
        if (uid.isBlank()) return null
        return try {
            val dir = java.io.File(context.filesDir, "id_cards")
            if (!dir.exists()) dir.mkdirs()
            val targetFile = java.io.File(dir, "photo_${uid}.jpg")

            context.contentResolver.openInputStream(uri)?.use { input ->
                java.io.FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val savedPath = targetFile.absolutePath
            getPrefs(context).edit().putString(KEY_ID_CARD_PHOTO_PATH + uid, savedPath).apply()
            savedPath
        } catch (_: Exception) {
            null
        }
    }

    fun clearIdCardPhoto(context: Context, uid: String) {
        if (uid.isBlank()) return
        try {
            val path = getPrefs(context).getString(KEY_ID_CARD_PHOTO_PATH + uid, null)
            if (path != null) {
                val file = java.io.File(path)
                if (file.exists()) file.delete()
            }
        } catch (_: Exception) {}
        getPrefs(context).edit().remove(KEY_ID_CARD_PHOTO_PATH + uid).apply()
    }
}
