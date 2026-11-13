package com.hostelattendance.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object UserSessionManager {
    private const val PREFS_NAME = "user_session_prefs"
    private const val KEY_HOSTELLER_NAME = "hosteller_name_"
    private const val KEY_ROOM_NUMBER = "room_number_"
    private const val KEY_STUDENT_ID = "student_id_"
    private const val KEY_ROLL_NUMBER = "roll_number_"
    private const val KEY_HAS_ENTERED = "has_entered_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    fun clearSelectedHosteller(context: Context, uid: String) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_HOSTELLER_NAME + uid)
            .remove(KEY_ROOM_NUMBER + uid)
            .remove(KEY_STUDENT_ID + uid)
            .remove(KEY_ROLL_NUMBER + uid)
            .remove(KEY_HAS_ENTERED + uid)
            .apply()
    }
}
