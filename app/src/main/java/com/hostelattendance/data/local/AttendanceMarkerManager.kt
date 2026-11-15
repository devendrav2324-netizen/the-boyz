package com.hostelattendance.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AttendanceMarkerInfo(
    val studentId: String,
    val hostelId: String,
    val rollNumber: String,
    val studentName: String,
    val roomNumber: String,
    val isMarker: Boolean = true,
    val grantedBy: String? = null,
    val grantedAtMillis: Long = System.currentTimeMillis(),
)

object AttendanceMarkerManager {
    private const val PREFS_NAME = "attendance_markers_prefs"
    private const val KEY_MARKERS_JSON = "markers_json_"

    private val _activeMarkers = MutableStateFlow<Map<String, AttendanceMarkerInfo>>(emptyMap())
    val activeMarkers: StateFlow<Map<String, AttendanceMarkerInfo>> = _activeMarkers.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context, hostelId: String) {
        val cached = loadCachedMarkers(context, hostelId)
        _activeMarkers.value = cached
    }

    fun loadCachedMarkers(context: Context, hostelId: String): Map<String, AttendanceMarkerInfo> {
        val jsonStr = getPrefs(context).getString(KEY_MARKERS_JSON + hostelId, null) ?: return emptyMap()
        val result = mutableMapOf<String, AttendanceMarkerInfo>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val isMarker = obj.optBoolean("isMarker", true)
                if (isMarker) {
                    val info = AttendanceMarkerInfo(
                        studentId = obj.getString("studentId"),
                        hostelId = obj.optString("hostelId", hostelId),
                        rollNumber = obj.optString("rollNumber", ""),
                        studentName = obj.optString("studentName", ""),
                        roomNumber = obj.optString("roomNumber", ""),
                        isMarker = true,
                        grantedBy = if (obj.has("grantedBy") && !obj.isNull("grantedBy")) obj.optString("grantedBy") else null,
                        grantedAtMillis = obj.optLong("grantedAtMillis", 0L),
                    )
                    result[info.studentId] = info
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun saveMarkersToPrefs(context: Context, hostelId: String, markers: Map<String, AttendanceMarkerInfo>) {
        try {
            val jsonArray = JSONArray()
            markers.values.forEach { info ->
                val obj = JSONObject().apply {
                    put("studentId", info.studentId)
                    put("hostelId", info.hostelId)
                    put("rollNumber", info.rollNumber)
                    put("studentName", info.studentName)
                    put("roomNumber", info.roomNumber)
                    put("isMarker", info.isMarker)
                    put("grantedBy", info.grantedBy ?: "")
                    put("grantedAtMillis", info.grantedAtMillis)
                }
                jsonArray.put(obj)
            }
            getPrefs(context).edit().putString(KEY_MARKERS_JSON + hostelId, jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }

    fun updateMarkersFromRemote(context: Context, hostelId: String, markersList: List<AttendanceMarkerInfo>) {
        val activeMap = markersList.filter { it.isMarker }.associateBy { it.studentId }
        _activeMarkers.value = activeMap
        saveMarkersToPrefs(context, hostelId, activeMap)
    }

    fun grantMarkerLocal(context: Context, marker: AttendanceMarkerInfo) {
        val current = _activeMarkers.value.toMutableMap()
        current[marker.studentId] = marker.copy(isMarker = true)
        _activeMarkers.value = current
        saveMarkersToPrefs(context, marker.hostelId, current)
    }

    fun revokeMarkerLocal(context: Context, hostelId: String, studentId: String) {
        val current = _activeMarkers.value.toMutableMap()
        current.remove(studentId)
        _activeMarkers.value = current
        saveMarkersToPrefs(context, hostelId, current)
    }

    fun isStudentMarker(studentId: String?): Boolean {
        if (studentId.isNullOrBlank()) return false
        return _activeMarkers.value.containsKey(studentId)
    }

    fun isUserMarker(
        context: Context,
        hostelId: String,
        studentId: String?,
        rollNumber: String?,
        uid: String?,
    ): Boolean {
        if (_activeMarkers.value.isEmpty()) {
            val cached = loadCachedMarkers(context, hostelId)
            if (cached.isNotEmpty()) {
                _activeMarkers.value = cached
            }
        }

        val markers = _activeMarkers.value.values
        if (markers.isEmpty()) return false

        if (!studentId.isNullOrBlank()) {
            if (_activeMarkers.value.containsKey(studentId)) return true
        }

        if (!rollNumber.isNullOrBlank()) {
            val normalizedRoll = rollNumber.trim().uppercase()
            if (markers.any { it.rollNumber.trim().uppercase() == normalizedRoll }) {
                return true
            }
        }

        return false
    }

    fun getActiveMarkersList(): List<AttendanceMarkerInfo> {
        return _activeMarkers.value.values.toList().sortedBy { it.studentName }
    }
}
