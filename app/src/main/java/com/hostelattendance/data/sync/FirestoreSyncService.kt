package com.hostelattendance.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.hostelattendance.data.local.AttendanceRecordEntity
import com.hostelattendance.data.local.AttendanceSessionDao
import com.hostelattendance.data.local.AttendanceSessionEntity
import com.hostelattendance.data.local.AttendanceSessionWithRecords
import com.hostelattendance.data.local.HostelDatabase
import com.hostelattendance.data.local.StudentDao
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingDao
import com.hostelattendance.data.local.WingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Unified, disciplined Cloud Firestore synchronization service.
 * Handles online writes, multi-device cloud recovery, and real-time streaming
 * with guaranteed Room SQLite parent-child session integrity and serialized database writes.
 */
object FirestoreSyncService {

    private const val TAG = "FirestoreSyncService"

    // Serialized single-threaded dispatcher to avoid SQLiteDatabaseLockedException on rapid incoming real-time streams
    private val dbWriteDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Standardized document ID sanitizer for Firestore paths (replaces path separators)
     */
    fun safeDocId(id: String): String = id.replace("/", "_")

    private val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseFirestore instance not available: ${e.message}")
            null
        }

    // Standardized Collection Reference Builders
    private fun hostelsCol(db: FirebaseFirestore) = db.collection("hostels")
    private fun hostelDoc(db: FirebaseFirestore, hostelId: String) = hostelsCol(db).document(hostelId)
    private fun wingsCol(db: FirebaseFirestore, hostelId: String) = hostelDoc(db, hostelId).collection("wings")
    private fun studentsCol(db: FirebaseFirestore, hostelId: String) = hostelDoc(db, hostelId).collection("students")
    private fun sessionsCol(db: FirebaseFirestore, hostelId: String) = hostelDoc(db, hostelId).collection("attendance_sessions")
    private fun recordsCol(db: FirebaseFirestore, hostelId: String) = hostelDoc(db, hostelId).collection("attendance_records")
    private fun attendanceMarkersCol(db: FirebaseFirestore, hostelId: String) = hostelDoc(db, hostelId).collection("attendance_markers")

    // =========================================================================
    // 1. ONLINE WRITE OPERATIONS (IMMEDIATE PUSH)
    // =========================================================================

    /**
     * Synchronize a single wing to Cloud Firestore.
     */
    suspend fun syncWing(hostelId: String, wing: WingEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val data = mapOf(
                "wingId" to wing.wingId,
                "hostelId" to hostelId,
                "name" to wing.name,
                "normalizedName" to wing.normalizedName,
                "isActive" to wing.isActive,
                "updatedAtMillis" to wing.updatedAtMillis,
            )
            wingsCol(db, hostelId).document(safeDocId(wing.wingId))
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Successfully synced wing: ${wing.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync wing '${wing.name}': ${e.message}")
        }
    }

    /**
     * Synchronize a single student entity to Cloud Firestore.
     */
    suspend fun syncStudent(hostelId: String, student: StudentEntity) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val data = mapOf(
                "studentId" to student.studentId,
                "hostelId" to hostelId,
                "rollNumber" to student.rollNumber,
                "normalizedRollNumber" to student.normalizedRollNumber,
                "currentWingId" to student.currentWingId,
                "currentRoomNumber" to student.currentRoomNumber,
                "studentName" to student.studentName,
                "branch" to (student.branch ?: ""),
                "year" to (student.year ?: ""),
                "isActive" to student.isActive,
                "createdAtMillis" to student.createdAtMillis,
                "updatedAtMillis" to student.updatedAtMillis,
            )
            studentsCol(db, hostelId).document(safeDocId(student.studentId))
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Successfully synced student: ${student.rollNumber} - ${student.studentName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync student '${student.rollNumber}': ${e.message}")
        }
    }

    /**
     * Permanently delete a student entity from Cloud Firestore.
     */
    suspend fun deleteStudent(
        hostelId: String,
        studentId: String,
        studentName: String? = null,
        roomNumber: String? = null,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docId = safeDocId(studentId)
            studentsCol(db, hostelId).document(docId).delete().await()
            Log.d(TAG, "Successfully deleted student from Firestore: $studentId")

            // Clean up hosteller credential binding if present
            if (!studentName.isNullOrBlank() && !roomNumber.isNullOrBlank()) {
                val hostellerKey = "${studentName.trim().lowercase()}__${roomNumber.trim().lowercase()}"
                try {
                    db.collection("hosteller_bindings").document(hostellerKey).delete().await()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete student '$studentId' from Firestore: ${e.message}")
        }
    }

    /**
     * Permanently delete a wing and its associated students/sessions from Cloud Firestore.
     */
    suspend fun deleteWing(
        hostelId: String,
        wingId: String,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val docId = safeDocId(wingId)
            wingsCol(db, hostelId).document(docId).delete().await()
            Log.d(TAG, "Successfully deleted wing from Firestore: $wingId")

            // Delete all students belonging to this wing
            val studentsSnapshot = studentsCol(db, hostelId)
                .whereEqualTo("currentWingId", wingId)
                .get()
                .await()
            for (studentDoc in studentsSnapshot.documents) {
                try {
                    studentDoc.reference.delete().await()
                } catch (_: Exception) {}
            }

            // Delete all sessions for this wing
            val sessionsSnapshot = sessionsCol(db, hostelId)
                .whereEqualTo("wingId", wingId)
                .get()
                .await()
            for (sessionDoc in sessionsSnapshot.documents) {
                try {
                    sessionDoc.reference.delete().await()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete wing '$wingId' from Firestore: ${e.message}")
        }
    }

    /**
     * Bulk synchronize master roster (wings and students) in safe batches.
     */
    suspend fun syncWingsAndStudents(
        hostelId: String,
        wings: List<WingEntity>,
        students: List<StudentEntity>,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            withTimeoutOrNull(5000L) {
                hostelDoc(db, hostelId)
                    .set(mapOf("hostelId" to hostelId, "updatedAtMillis" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            }

            // 1. Wings
            for (chunk in wings.chunked(300)) {
                val batch = db.batch()
                for (wing in chunk) {
                    val ref = wingsCol(db, hostelId).document(safeDocId(wing.wingId))
                    val data = mapOf(
                        "wingId" to wing.wingId,
                        "hostelId" to hostelId,
                        "name" to wing.name,
                        "normalizedName" to wing.normalizedName,
                        "isActive" to wing.isActive,
                        "updatedAtMillis" to wing.updatedAtMillis,
                    )
                    batch.set(ref, data, SetOptions.merge())
                }
                withTimeoutOrNull(10000L) {
                    batch.commit().await()
                }
            }

            // 2. Students
            for (chunk in students.chunked(300)) {
                val batch = db.batch()
                for (student in chunk) {
                    val ref = studentsCol(db, hostelId).document(safeDocId(student.studentId))
                    val data = mapOf(
                        "studentId" to student.studentId,
                        "hostelId" to hostelId,
                        "rollNumber" to student.rollNumber,
                        "normalizedRollNumber" to student.normalizedRollNumber,
                        "currentWingId" to student.currentWingId,
                        "currentRoomNumber" to student.currentRoomNumber,
                        "studentName" to student.studentName,
                        "branch" to (student.branch ?: ""),
                        "year" to (student.year ?: ""),
                        "isActive" to student.isActive,
                        "createdAtMillis" to student.createdAtMillis,
                        "updatedAtMillis" to student.updatedAtMillis,
                    )
                    batch.set(ref, data, SetOptions.merge())
                }
                withTimeoutOrNull(12000L) {
                    batch.commit().await()
                }
            }
            Log.d(TAG, "Synced ${wings.size} wings and ${students.size} students to Cloud Firestore")
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing roster to Firestore: ${e.message}")
        }
    }

    /**
     * Synchronize a complete attendance session and its constituent records.
     */
    suspend fun syncAttendanceSession(
        hostelId: String,
        session: AttendanceSessionEntity,
        records: List<AttendanceRecordEntity>,
    ) = withContext(Dispatchers.IO) {
        val db = firestore
        if (db == null) {
            Log.e(TAG, "Cannot sync attendance session: Firebase user is not signed in!")
            return@withContext
        }
        try {
            val sessionData = mapOf(
                "sessionId" to session.sessionId,
                "hostelId" to hostelId,
                "localDate" to session.localDate,
                "timeZone" to session.timeZone,
                "wingId" to session.wingId,
                "state" to session.state,
                "savedAtMillis" to (session.savedAtMillis ?: System.currentTimeMillis()),
                "savedBy" to (session.savedBy ?: (FirebaseAuth.getInstance().currentUser?.uid ?: "")),
                "version" to session.version,
            )

            // Ensure hostel document exists so subcollections display cleanly in console
            try {
                hostelDoc(db, hostelId).set(
                    mapOf("hostelId" to hostelId, "name" to "CGITR Boys Hostel", "updatedAtMillis" to System.currentTimeMillis()),
                    SetOptions.merge()
                ).await()
            } catch (_: Exception) {}

            sessionsCol(db, hostelId).document(safeDocId(session.sessionId))
                .set(sessionData, SetOptions.merge())
                .await()
            Log.d(TAG, "Successfully synced session header: ${session.sessionId}")

            for (chunk in records.chunked(300)) {
                val batch = db.batch()
                for (rec in chunk) {
                    val ref = recordsCol(db, hostelId).document(safeDocId(rec.recordId))
                    val recordData = mapOf(
                        "recordId" to rec.recordId,
                        "hostelId" to hostelId,
                        "sessionId" to rec.sessionId,
                        "studentId" to rec.studentId,
                        "studentNameSnapshot" to rec.studentNameSnapshot,
                        "rollNumberSnapshot" to rec.rollNumberSnapshot,
                        "roomNumberSnapshot" to rec.roomNumberSnapshot,
                        "wingIdSnapshot" to rec.wingIdSnapshot,
                        "wingNameSnapshot" to rec.wingNameSnapshot,
                        "localDate" to rec.localDate,
                        "status" to rec.status,
                        "version" to rec.version,
                        "updatedAtMillis" to rec.updatedAtMillis,
                    )
                    batch.set(ref, recordData, SetOptions.merge())
                }
                batch.commit().await()
            }
            Log.d(TAG, "Successfully synced session ${session.sessionId} with ${records.size} records to Cloud Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing attendance session to Cloud Firestore: ${e.message}", e)
        }
    }

    /**
     * Real-time online sync of an individual marked attendance record.
     */
    suspend fun syncAttendanceRecord(
        hostelId: String,
        record: AttendanceRecordEntity,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val recordData = mapOf(
                "recordId" to record.recordId,
                "hostelId" to hostelId,
                "sessionId" to record.sessionId,
                "studentId" to record.studentId,
                "studentNameSnapshot" to record.studentNameSnapshot,
                "rollNumberSnapshot" to record.rollNumberSnapshot,
                "roomNumberSnapshot" to record.roomNumberSnapshot,
                "wingIdSnapshot" to record.wingIdSnapshot,
                "wingNameSnapshot" to record.wingNameSnapshot,
                "localDate" to record.localDate,
                "status" to record.status,
                "version" to record.version,
                "updatedAtMillis" to record.updatedAtMillis,
            )
            recordsCol(db, hostelId).document(safeDocId(record.recordId))
                .set(recordData, SetOptions.merge())
                .await()
            Log.d(TAG, "Live synced attendance record: ${record.rollNumberSnapshot} -> ${record.status}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed live sync for record ${record.recordId}: ${e.message}")
        }
    }

    /**
     * Batch sync attendance records (e.g. after student profile edit or bulk marking).
     */
    suspend fun syncAttendanceRecordsBatch(
        hostelId: String,
        records: List<AttendanceRecordEntity>,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            for (chunk in records.chunked(300)) {
                val batch = db.batch()
                for (rec in chunk) {
                    val ref = recordsCol(db, hostelId).document(safeDocId(rec.recordId))
                    val recordData = mapOf(
                        "recordId" to rec.recordId,
                        "hostelId" to hostelId,
                        "sessionId" to rec.sessionId,
                        "studentId" to rec.studentId,
                        "studentNameSnapshot" to rec.studentNameSnapshot,
                        "rollNumberSnapshot" to rec.rollNumberSnapshot,
                        "roomNumberSnapshot" to rec.roomNumberSnapshot,
                        "wingIdSnapshot" to rec.wingIdSnapshot,
                        "wingNameSnapshot" to rec.wingNameSnapshot,
                        "localDate" to rec.localDate,
                        "status" to rec.status,
                        "version" to rec.version,
                        "updatedAtMillis" to rec.updatedAtMillis,
                    )
                    batch.set(ref, recordData, SetOptions.merge())
                }
                withTimeoutOrNull(10000L) {
                    batch.commit().await()
                }
            }
            Log.d(TAG, "Batch synced ${records.size} attendance records")
        } catch (e: Exception) {
            Log.w(TAG, "Error in batch sync of attendance records: ${e.message}")
        }
    }

    // =========================================================================
    // 2. CLOUD RESTORATION & DATA RECOVERY
    // =========================================================================

    /**
     * Helper to ensure every attendance record has an existing parent session row in SQLite
     * so that Room's @Relation join emits the records properly.
     */
    private suspend fun ensureParentSessionsExist(
        hostelId: String,
        records: List<AttendanceRecordEntity>,
        sessionDao: AttendanceSessionDao,
        knownSessionIds: Set<String> = emptySet(),
    ) {
        val syntheticSessions = records
            .map { it.sessionId to (it.localDate to it.wingIdSnapshot) }
            .distinctBy { it.first }
            .filterNot { knownSessionIds.contains(it.first) }
            .mapNotNull { (sId, dateAndWing) ->
                val existing = sessionDao.findSession(hostelId, dateAndWing.first, dateAndWing.second)
                if (existing == null) {
                    AttendanceSessionEntity(
                        sessionId = sId,
                        hostelId = hostelId,
                        localDate = dateAndWing.first,
                        timeZone = "Asia/Kolkata",
                        wingId = dateAndWing.second,
                        state = "SAVED",
                        savedAtMillis = System.currentTimeMillis(),
                        savedBy = null,
                        version = 0L,
                    )
                } else null
            }
        if (syntheticSessions.isNotEmpty()) {
            sessionDao.insertSessions(syntheticSessions)
            Log.d(TAG, "Ensured ${syntheticSessions.size} parent session rows in SQLite")
        }
    }

    /**
     * Restores wings and students from Cloud Firestore into local Room SQLite.
     */
    suspend fun restoreWingsAndStudentsFromFirestore(
        hostelId: String,
        wingDao: WingDao,
        studentDao: StudentDao,
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Pair(0, 0)
        try {
            var restoredWingsCount = 0
            val wingDocs = withTimeoutOrNull(15000L) {
                wingsCol(db, hostelId).get().await()
            }
            if (wingDocs != null && !wingDocs.isEmpty) {
                val wings = wingDocs.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    WingEntity(
                        wingId = doc.getString("wingId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        name = name,
                        normalizedName = doc.getString("normalizedName") ?: name.trim().uppercase(),
                        isActive = doc.getBoolean("isActive") ?: true,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis(),
                    )
                }
                val deduplicatedWings = wings
                    .sortedBy { it.updatedAtMillis }
                    .associateBy { "${it.hostelId}:${it.normalizedName}" }
                    .values
                    .toList()

                if (deduplicatedWings.isNotEmpty()) {
                    withContext(dbWriteDispatcher) {
                        wingDao.insertWings(deduplicatedWings)
                    }
                    restoredWingsCount = deduplicatedWings.size
                }
            }

            var restoredStudentsCount = 0
            val studentDocs = withTimeoutOrNull(25000L) {
                studentsCol(db, hostelId).get().await()
            }
            if (studentDocs != null && !studentDocs.isEmpty) {
                val students = studentDocs.documents.mapNotNull { doc ->
                    val roll = doc.getString("rollNumber") ?: return@mapNotNull null
                    val name = doc.getString("studentName") ?: return@mapNotNull null
                    val wingId = doc.getString("currentWingId") ?: return@mapNotNull null
                    val room = doc.getString("currentRoomNumber") ?: ""
                    StudentEntity(
                        studentId = doc.getString("studentId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        rollNumber = roll,
                        normalizedRollNumber = doc.getString("normalizedRollNumber") ?: roll.trim().uppercase(),
                        currentWingId = wingId,
                        currentRoomNumber = room,
                        studentName = name,
                        branch = doc.getString("branch")?.takeIf { it.isNotEmpty() },
                        year = doc.getString("year")?.takeIf { it.isNotEmpty() },
                        isActive = doc.getBoolean("isActive") ?: true,
                        createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis(),
                    )
                }
                val deduplicatedStudents = students
                    .sortedBy { it.updatedAtMillis }
                    .associateBy { "${it.hostelId}:${it.normalizedRollNumber}" }
                    .values
                    .toList()

                if (deduplicatedStudents.isNotEmpty()) {
                    withContext(dbWriteDispatcher) {
                        deduplicatedStudents.chunked(250).forEach { chunk ->
                            studentDao.insertStudents(chunk)
                        }
                    }
                    restoredStudentsCount = deduplicatedStudents.size
                }
            }
            Log.d(TAG, "Restored $restoredWingsCount wings and $restoredStudentsCount students from Firestore")
            Pair(restoredWingsCount, restoredStudentsCount)
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring wings/students from Firestore: ${e.message}")
            Pair(0, 0)
        }
    }

    /**
     * Phased, robust attendance restoration from Cloud Firestore into Room SQLite.
     * Restores session metadata and queries recent records with fallback to ensure
     * fresh device installs immediately recover attendance data.
     */
    suspend fun restoreAttendanceFromFirestore(
        hostelId: String,
        sessionDao: AttendanceSessionDao,
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Pair(0, 0)
        try {
            var restoredSessionsCount = 0
            val knownSessionIds = mutableSetOf<String>()

            // 1. Restore Sessions
            val sessionDocs = withTimeoutOrNull(15000L) {
                sessionsCol(db, hostelId).get().await()
            }
            if (sessionDocs != null && !sessionDocs.isEmpty) {
                val sessions = sessionDocs.documents.mapNotNull { doc ->
                    val localDate = doc.getString("localDate") ?: return@mapNotNull null
                    val wingId = doc.getString("wingId") ?: return@mapNotNull null
                    AttendanceSessionEntity(
                        sessionId = doc.getString("sessionId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        localDate = localDate,
                        timeZone = doc.getString("timeZone") ?: "Asia/Kolkata",
                        wingId = wingId,
                        state = doc.getString("state") ?: "SAVED",
                        savedAtMillis = doc.getLong("savedAtMillis"),
                        savedBy = doc.getString("savedBy"),
                        version = doc.getLong("version") ?: 0L,
                    )
                }
                val deduplicatedSessions = sessions
                    .sortedBy { it.version }
                    .associateBy { "${it.hostelId}:${it.localDate}:${it.wingId}" }
                    .values
                    .toList()

                if (deduplicatedSessions.isNotEmpty()) {
                    knownSessionIds.addAll(deduplicatedSessions.map { it.sessionId })
                    withContext(dbWriteDispatcher) {
                        deduplicatedSessions.chunked(200).forEach { chunk ->
                            sessionDao.insertSessions(chunk)
                        }
                    }
                    restoredSessionsCount = deduplicatedSessions.size
                }
            }

            // 2. Restore Records (Full get with 35s timeout, with 60-day fallback for low bandwidth)
            val recordDocs = try {
                withTimeoutOrNull(35000L) {
                    recordsCol(db, hostelId).get().await()
                }
            } catch (_: Exception) { null } ?: run {
                val sixtyDaysAgo = LocalDate.now().minusDays(60).toString()
                try {
                    withTimeoutOrNull(20000L) {
                        recordsCol(db, hostelId)
                            .whereGreaterThanOrEqualTo("localDate", sixtyDaysAgo)
                            .get()
                            .await()
                    }
                } catch (_: Exception) { null }
            }

            var restoredRecordsCount = 0
            if (recordDocs != null && !recordDocs.isEmpty) {
                val records = recordDocs.documents.mapNotNull { doc ->
                    val studentId = doc.getString("studentId") ?: return@mapNotNull null
                    val localDate = doc.getString("localDate") ?: return@mapNotNull null
                    val wingIdSnapshot = doc.getString("wingIdSnapshot") ?: ""
                    val rawSessionId = doc.getString("sessionId")
                    val sessionId = if (!rawSessionId.isNullOrBlank()) {
                        rawSessionId
                    } else {
                        "$hostelId:$localDate:$wingIdSnapshot"
                    }
                    AttendanceRecordEntity(
                        recordId = doc.getString("recordId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        studentId = studentId,
                        rollNumberSnapshot = doc.getString("rollNumberSnapshot") ?: "",
                        studentNameSnapshot = doc.getString("studentNameSnapshot") ?: "",
                        localDate = localDate,
                        status = doc.getString("status") ?: "NOT_MARKED",
                        wingIdSnapshot = wingIdSnapshot,
                        wingNameSnapshot = doc.getString("wingNameSnapshot") ?: "",
                        roomNumberSnapshot = doc.getString("roomNumberSnapshot") ?: "",
                        sessionId = sessionId,
                        version = doc.getLong("version") ?: 0L,
                        createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L,
                    )
                }
                val deduplicatedRecords = records
                    .sortedBy { it.updatedAtMillis }
                    .associateBy { "${it.hostelId}:${it.studentId}:${it.localDate}" }
                    .values
                    .toList()

                if (deduplicatedRecords.isNotEmpty()) {
                    withContext(dbWriteDispatcher) {
                        ensureParentSessionsExist(hostelId, deduplicatedRecords, sessionDao, knownSessionIds)
                        deduplicatedRecords.chunked(250).forEach { chunk ->
                            sessionDao.insertRecords(chunk)
                        }
                    }
                    restoredRecordsCount = deduplicatedRecords.size
                }
            }
            Log.d(TAG, "Restored $restoredSessionsCount sessions and $restoredRecordsCount attendance records from Firestore")
            Pair(restoredSessionsCount, restoredRecordsCount)
        } catch (e: Exception) {
            Log.w(TAG, "Error in restoreAttendanceFromFirestore: ${e.message}")
            Pair(0, 0)
        }
    }

    /**
     * Pull a specific date & wing attendance session directly from Firestore into Room SQLite.
     * Guarantees atomic insertion of both session header and constituent records.
     */
    suspend fun pullAttendanceSession(
        hostelId: String,
        localDate: String,
        wingId: String,
        sessionDao: AttendanceSessionDao,
    ): AttendanceSessionWithRecords? = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext null
        try {
            val expectedSessionId = "$hostelId:$localDate:$wingId"
            val safeId = safeDocId(expectedSessionId)

            // 1. Fetch Session Header Doc (Direct ID or Query fallback)
            val sessionDoc = try {
                val direct = sessionsCol(db, hostelId).document(safeId).get().await()
                if (direct.exists()) direct else {
                    val query = sessionsCol(db, hostelId)
                        .whereEqualTo("localDate", localDate)
                        .whereEqualTo("wingId", wingId)
                        .limit(1)
                        .get()
                        .await()
                    query.documents.firstOrNull()
                }
            } catch (_: Exception) { null }

            val sessionId = sessionDoc?.getString("sessionId")?.takeIf { it.isNotBlank() }
                ?: sessionDoc?.id
                ?: expectedSessionId

            // 2. Fetch Records for this session
            var recordDocs = try {
                recordsCol(db, hostelId)
                    .whereEqualTo("sessionId", sessionId)
                    .get()
                    .await()
                    .documents
            } catch (_: Exception) { emptyList() }

            if (recordDocs.isEmpty() && safeDocId(sessionId) != sessionId) {
                recordDocs = try {
                    recordsCol(db, hostelId)
                        .whereEqualTo("sessionId", safeDocId(sessionId))
                        .get()
                        .await()
                        .documents
                } catch (_: Exception) { emptyList() }
            }

            if (recordDocs.isEmpty()) {
                recordDocs = try {
                    recordsCol(db, hostelId)
                        .whereEqualTo("localDate", localDate)
                        .whereEqualTo("wingIdSnapshot", wingId)
                        .get()
                        .await()
                        .documents
                } catch (_: Exception) { emptyList() }
            }

            if (recordDocs.isEmpty()) {
                recordDocs = try {
                    recordsCol(db, hostelId)
                        .whereEqualTo("localDate", localDate)
                        .get()
                        .await()
                        .documents
                        .filter { it.getString("wingIdSnapshot") == wingId || it.getString("wingIdSnapshot").isNullOrBlank() }
                } catch (_: Exception) { emptyList() }
            }

            if (sessionDoc == null && recordDocs.isEmpty()) {
                return@withContext null
            }

            val sessionEntity = AttendanceSessionEntity(
                sessionId = sessionId,
                hostelId = sessionDoc?.getString("hostelId") ?: hostelId,
                localDate = sessionDoc?.getString("localDate") ?: localDate,
                timeZone = sessionDoc?.getString("timeZone") ?: "Asia/Kolkata",
                wingId = sessionDoc?.getString("wingId") ?: wingId,
                state = sessionDoc?.getString("state") ?: if (recordDocs.any { it.getString("status") == "PRESENT" || it.getString("status") == "ABSENT" }) "SAVED" else "DRAFT",
                savedAtMillis = sessionDoc?.getLong("savedAtMillis") ?: System.currentTimeMillis(),
                savedBy = sessionDoc?.getString("savedBy"),
                version = sessionDoc?.getLong("version") ?: 0L,
            )

            val records = recordDocs.mapNotNull { doc ->
                val sId = doc.getString("studentId") ?: return@mapNotNull null
                AttendanceRecordEntity(
                    recordId = doc.getString("recordId") ?: doc.id,
                    hostelId = doc.getString("hostelId") ?: hostelId,
                    studentId = sId,
                    rollNumberSnapshot = doc.getString("rollNumberSnapshot") ?: "",
                    studentNameSnapshot = doc.getString("studentNameSnapshot") ?: "",
                    localDate = doc.getString("localDate") ?: localDate,
                    status = doc.getString("status") ?: "NOT_MARKED",
                    wingIdSnapshot = doc.getString("wingIdSnapshot") ?: wingId,
                    wingNameSnapshot = doc.getString("wingNameSnapshot") ?: "",
                    roomNumberSnapshot = doc.getString("roomNumberSnapshot") ?: "",
                    sessionId = sessionId,
                    version = doc.getLong("version") ?: 0L,
                    createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
                    updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L,
                )
            }

            withContext(dbWriteDispatcher) {
                sessionDao.insertSession(sessionEntity)
                if (records.isNotEmpty()) {
                    sessionDao.insertRecords(records)
                }
            }

            Log.d(TAG, "Pulled session $sessionId with ${records.size} records from Firestore")
            sessionDao.findSessionWithRecords(sessionId)
        } catch (e: Exception) {
            Log.w(TAG, "Error in pullAttendanceSession: ${e.message}")
            null
        }
    }

    /**
     * Restore attendance records and sessions for a selected date range (used by Reports screen).
     */
    suspend fun restoreAttendanceForDateRange(
        hostelId: String,
        startDate: String,
        endDate: String,
        sessionDao: AttendanceSessionDao,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        var anyRestored = false
        try {
            val sessionDocs = try {
                withTimeoutOrNull(10000L) {
                    sessionsCol(db, hostelId)
                        .whereGreaterThanOrEqualTo("localDate", startDate)
                        .whereLessThanOrEqualTo("localDate", endDate)
                        .get()
                        .await()
                }
            } catch (_: Exception) { null }

            val knownSessionIds = mutableSetOf<String>()
            if (sessionDocs != null && !sessionDocs.isEmpty) {
                val sessions = sessionDocs.documents.mapNotNull { doc ->
                    val localDate = doc.getString("localDate") ?: return@mapNotNull null
                    val wingId = doc.getString("wingId") ?: return@mapNotNull null
                    AttendanceSessionEntity(
                        sessionId = doc.getString("sessionId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        localDate = localDate,
                        timeZone = doc.getString("timeZone") ?: "Asia/Kolkata",
                        wingId = wingId,
                        state = doc.getString("state") ?: "SAVED",
                        savedAtMillis = doc.getLong("savedAtMillis"),
                        savedBy = doc.getString("savedBy"),
                        version = doc.getLong("version") ?: 0L,
                    )
                }
                if (sessions.isNotEmpty()) {
                    knownSessionIds.addAll(sessions.map { it.sessionId })
                    withContext(dbWriteDispatcher) {
                        sessionDao.insertSessions(sessions)
                    }
                    anyRestored = true
                }
            }

            val recordDocs = try {
                withTimeoutOrNull(15000L) {
                    recordsCol(db, hostelId)
                        .whereGreaterThanOrEqualTo("localDate", startDate)
                        .whereLessThanOrEqualTo("localDate", endDate)
                        .get()
                        .await()
                }
            } catch (_: Exception) { null }

            if (recordDocs != null && !recordDocs.isEmpty) {
                val records = recordDocs.documents.mapNotNull { doc ->
                    val sessionId = doc.getString("sessionId") ?: return@mapNotNull null
                    val studentId = doc.getString("studentId") ?: return@mapNotNull null
                    val localDate = doc.getString("localDate") ?: return@mapNotNull null
                    AttendanceRecordEntity(
                        recordId = doc.getString("recordId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        studentId = studentId,
                        rollNumberSnapshot = doc.getString("rollNumberSnapshot") ?: "",
                        studentNameSnapshot = doc.getString("studentNameSnapshot") ?: "",
                        localDate = localDate,
                        status = doc.getString("status") ?: "NOT_MARKED",
                        wingIdSnapshot = doc.getString("wingIdSnapshot") ?: "",
                        wingNameSnapshot = doc.getString("wingNameSnapshot") ?: "",
                        roomNumberSnapshot = doc.getString("roomNumberSnapshot") ?: "",
                        sessionId = sessionId,
                        version = doc.getLong("version") ?: 0L,
                        createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L,
                    )
                }
                if (records.isNotEmpty()) {
                    withContext(dbWriteDispatcher) {
                        ensureParentSessionsExist(hostelId, records, sessionDao, knownSessionIds)
                        records.chunked(300).forEach { chunk ->
                            sessionDao.insertRecords(chunk)
                        }
                    }
                    anyRestored = true
                }
            }
            Log.d(TAG, "Restored date range [$startDate..$endDate] from Firestore (success=$anyRestored)")
        } catch (e: Exception) {
            Log.w(TAG, "Date range query failed: ${e.message}")
        }

        // Fallback to phased full restore if ranged query returned nothing
        if (!anyRestored) {
            restoreAttendanceFromFirestore(hostelId, sessionDao)
        }
    }

    // =========================================================================
    // 3. REAL-TIME SNAPSHOT LISTENERS (SEQUENTIAL & RACE-FREE)
    // =========================================================================

    /**
     * Active real-time listener for current date and wing attendance.
     * Guarantees sequential write dispatch to Room SQLite so live marking by an admin
     * immediately appears on user session screens without locking or race conditions.
     */
    fun listenToAttendanceRealtime(
        hostelId: String,
        localDate: String,
        wingId: String,
        sessionDao: AttendanceSessionDao,
    ): ListenerRegistration? {
        val db = firestore ?: return null
        val expectedSessionId = "$hostelId:$localDate:$wingId"

        return try {
            val sessionQuery = sessionsCol(db, hostelId)
                .whereEqualTo("localDate", localDate)
                .whereEqualTo("wingId", wingId)
                .limit(1)

            val sessionReg = sessionQuery.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                val doc = snapshot.documents.firstOrNull() ?: return@addSnapshotListener
                val sessionId = doc.getString("sessionId")?.takeIf { it.isNotBlank() } ?: doc.id
                val sessionEntity = AttendanceSessionEntity(
                    sessionId = sessionId,
                    hostelId = doc.getString("hostelId") ?: hostelId,
                    localDate = doc.getString("localDate") ?: localDate,
                    timeZone = doc.getString("timeZone") ?: "Asia/Kolkata",
                    wingId = doc.getString("wingId") ?: wingId,
                    state = doc.getString("state") ?: "SAVED",
                    savedAtMillis = doc.getLong("savedAtMillis"),
                    savedBy = doc.getString("savedBy"),
                    version = doc.getLong("version") ?: 0L,
                )
                syncScope.launch(dbWriteDispatcher) {
                    sessionDao.insertSession(sessionEntity)
                }
            }

            val recordsQuery = recordsCol(db, hostelId)
                .whereEqualTo("localDate", localDate)
                .whereEqualTo("wingIdSnapshot", wingId)

            val recordsReg = recordsQuery.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
                val records = snapshot.documents.mapNotNull { doc ->
                    val studentId = doc.getString("studentId") ?: return@mapNotNull null
                    val sId = doc.getString("sessionId")?.takeIf { it.isNotBlank() } ?: expectedSessionId
                    AttendanceRecordEntity(
                        recordId = doc.getString("recordId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        studentId = studentId,
                        rollNumberSnapshot = doc.getString("rollNumberSnapshot") ?: "",
                        studentNameSnapshot = doc.getString("studentNameSnapshot") ?: "",
                        localDate = doc.getString("localDate") ?: localDate,
                        status = doc.getString("status") ?: "NOT_MARKED",
                        wingIdSnapshot = doc.getString("wingIdSnapshot") ?: wingId,
                        wingNameSnapshot = doc.getString("wingNameSnapshot") ?: "",
                        roomNumberSnapshot = doc.getString("roomNumberSnapshot") ?: "",
                        sessionId = sId,
                        version = doc.getLong("version") ?: 0L,
                        createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L,
                    )
                }
                if (records.isNotEmpty()) {
                    syncScope.launch(dbWriteDispatcher) {
                        val existing = sessionDao.findSession(hostelId, localDate, wingId)
                        if (existing == null) {
                            val syntheticSession = AttendanceSessionEntity(
                                sessionId = records.firstOrNull()?.sessionId ?: expectedSessionId,
                                hostelId = hostelId,
                                localDate = localDate,
                                timeZone = "Asia/Kolkata",
                                wingId = wingId,
                                state = "DRAFT",
                                savedAtMillis = null,
                                savedBy = null,
                                version = 0L,
                            )
                            sessionDao.insertSession(syntheticSession)
                        }
                        sessionDao.insertRecords(records)
                    }
                }
            }

            ListenerRegistration {
                sessionReg.remove()
                recordsReg.remove()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up attendance realtime listener: ${e.message}")
            null
        }
    }

    /**
     * Real-time listener for Wings and Students roster.
     */
    fun listenToWingsAndStudentsRealtime(
        hostelId: String,
        wingDao: WingDao,
        studentDao: StudentDao,
    ): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            val wingsReg = wingsCol(db, hostelId).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val wings = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    WingEntity(
                        wingId = doc.getString("wingId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        name = name,
                        normalizedName = doc.getString("normalizedName") ?: name.trim().uppercase(),
                        isActive = doc.getBoolean("isActive") ?: true,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis(),
                    )
                }
                if (wings.isNotEmpty()) {
                    syncScope.launch(dbWriteDispatcher) {
                        wingDao.insertWings(wings)
                    }
                }
                for (change in snapshot.documentChanges) {
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                        val removedId = change.document.id
                        syncScope.launch(dbWriteDispatcher) {
                            wingDao.deleteById(removedId)
                        }
                    }
                }
            }

            val studentsReg = studentsCol(db, hostelId).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val students = snapshot.documents.mapNotNull { doc ->
                    val roll = doc.getString("rollNumber") ?: return@mapNotNull null
                    val name = doc.getString("studentName") ?: return@mapNotNull null
                    val wingId = doc.getString("currentWingId") ?: return@mapNotNull null
                    val room = doc.getString("currentRoomNumber") ?: ""
                    StudentEntity(
                        studentId = doc.getString("studentId") ?: doc.id,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        rollNumber = roll,
                        normalizedRollNumber = doc.getString("normalizedRollNumber") ?: roll.trim().uppercase(),
                        currentWingId = wingId,
                        currentRoomNumber = room,
                        studentName = name,
                        branch = doc.getString("branch")?.takeIf { it.isNotEmpty() },
                        year = doc.getString("year")?.takeIf { it.isNotEmpty() },
                        isActive = doc.getBoolean("isActive") ?: true,
                        createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: System.currentTimeMillis(),
                    )
                }
                if (students.isNotEmpty()) {
                    syncScope.launch(dbWriteDispatcher) {
                        students.chunked(250).forEach { chunk ->
                            studentDao.insertStudents(chunk)
                        }
                    }
                }
                for (change in snapshot.documentChanges) {
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                        val removedId = change.document.id
                        syncScope.launch(dbWriteDispatcher) {
                            studentDao.deleteById(removedId)
                        }
                    }
                }
            }

            ListenerRegistration {
                wingsReg.remove()
                studentsReg.remove()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up roster realtime listener: ${e.message}")
            null
        }
    }

    /**
     * Synchronize an attendance marker grant or update to Cloud Firestore.
     */
    suspend fun syncAttendanceMarker(
        hostelId: String,
        marker: com.hostelattendance.data.local.AttendanceMarkerInfo,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val data = mapOf(
                "studentId" to marker.studentId,
                "hostelId" to hostelId,
                "rollNumber" to marker.rollNumber,
                "studentName" to marker.studentName,
                "roomNumber" to marker.roomNumber,
                "isMarker" to marker.isMarker,
                "grantedBy" to (marker.grantedBy ?: ""),
                "grantedAtMillis" to marker.grantedAtMillis,
                "updatedAtMillis" to System.currentTimeMillis(),
            )
            attendanceMarkersCol(db, hostelId).document(safeDocId(marker.studentId))
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Successfully synced attendance marker: ${marker.studentName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync attendance marker '${marker.studentName}': ${e.message}")
        }
    }

    /**
     * Revoke attendance marker in Cloud Firestore.
     */
    suspend fun revokeAttendanceMarker(
        hostelId: String,
        studentId: String,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            attendanceMarkersCol(db, hostelId).document(safeDocId(studentId))
                .delete()
                .await()
            Log.d(TAG, "Successfully revoked attendance marker doc: $studentId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to revoke attendance marker doc '$studentId': ${e.message}")
        }
    }

    /**
     * Fetch all active attendance markers from Cloud Firestore.
     */
    suspend fun fetchAttendanceMarkers(
        hostelId: String,
    ): List<com.hostelattendance.data.local.AttendanceMarkerInfo> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext emptyList()
        try {
            val snapshot = attendanceMarkersCol(db, hostelId).get().await()
            snapshot.documents.mapNotNull { doc ->
                val sId = doc.getString("studentId") ?: doc.id
                val isMarker = doc.getBoolean("isMarker") ?: true
                if (!isMarker) return@mapNotNull null
                com.hostelattendance.data.local.AttendanceMarkerInfo(
                    studentId = sId,
                    hostelId = doc.getString("hostelId") ?: hostelId,
                    rollNumber = doc.getString("rollNumber") ?: "",
                    studentName = doc.getString("studentName") ?: "",
                    roomNumber = doc.getString("roomNumber") ?: "",
                    isMarker = true,
                    grantedBy = doc.getString("grantedBy"),
                    grantedAtMillis = doc.getLong("grantedAtMillis") ?: 0L,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch attendance markers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Real-time listener for attendance markers.
     */
    fun listenToAttendanceMarkers(
        hostelId: String,
        onUpdate: (List<com.hostelattendance.data.local.AttendanceMarkerInfo>) -> Unit,
    ): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            attendanceMarkersCol(db, hostelId).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val markers = snapshot.documents.mapNotNull { doc ->
                    val sId = doc.getString("studentId") ?: doc.id
                    val isMarker = doc.getBoolean("isMarker") ?: true
                    if (!isMarker) return@mapNotNull null
                    com.hostelattendance.data.local.AttendanceMarkerInfo(
                        studentId = sId,
                        hostelId = doc.getString("hostelId") ?: hostelId,
                        rollNumber = doc.getString("rollNumber") ?: "",
                        studentName = doc.getString("studentName") ?: "",
                        roomNumber = doc.getString("roomNumber") ?: "",
                        isMarker = true,
                        grantedBy = doc.getString("grantedBy"),
                        grantedAtMillis = doc.getLong("grantedAtMillis") ?: 0L,
                    )
                }
                onUpdate(markers)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up attendance markers realtime listener: ${e.message}")
            null
        }
    }

    /**
     * Real-time listener for attendance disputes / requests for wrong attendance.
     */
    fun listenToAttendanceDisputes(
        hostelId: String,
        onUpdate: (List<com.hostelattendance.data.local.AttendanceDisputeItem>) -> Unit,
    ): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("attendance_disputes")
                .whereEqualTo("hostelId", hostelId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val list = snapshot.documents.mapNotNull { doc ->
                        val disputeId = doc.id
                        val sId = doc.getString("studentId") ?: return@mapNotNull null
                        val sName = doc.getString("studentName") ?: "Student"
                        val roll = doc.getString("rollNumber") ?: ""
                        val room = doc.getString("roomNumber") ?: ""
                        val date = doc.getString("localDate") ?: ""
                        val marked = doc.getString("markedStatus") ?: "ABSENT"
                        val claimed = doc.getString("claimedStatus") ?: "PRESENT"
                        val reason = doc.getString("reason") ?: ""
                        val created = doc.getLong("createdAtMillis") ?: 0L
                        val status = doc.getString("status") ?: "PENDING"
                        val resStatus = doc.getString("resolvedStatus")
                        val resAt = doc.getLong("resolvedAtMillis")
                        com.hostelattendance.data.local.AttendanceDisputeItem(
                            disputeId = disputeId,
                            hostelId = hostelId,
                            studentId = sId,
                            studentName = sName,
                            rollNumber = roll,
                            roomNumber = room,
                            localDate = date,
                            markedStatus = marked,
                            claimedStatus = claimed,
                            reason = reason,
                            createdAtMillis = created,
                            status = status,
                            resolvedStatus = resStatus,
                            resolvedAtMillis = resAt,
                        )
                    }.sortedByDescending { it.createdAtMillis }
                    onUpdate(list)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up attendance disputes realtime listener: ${e.message}")
            null
        }
    }

    /**
     * Real-time listener for attendance disputes submitted by a specific student.
     */
    fun listenToStudentDisputes(
        hostelId: String,
        studentId: String,
        onUpdate: (List<com.hostelattendance.data.local.AttendanceDisputeItem>) -> Unit,
    ): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection("attendance_disputes")
                .whereEqualTo("hostelId", hostelId)
                .whereEqualTo("studentId", studentId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val list = snapshot.documents.mapNotNull { doc ->
                        val disputeId = doc.id
                        val sId = doc.getString("studentId") ?: return@mapNotNull null
                        val sName = doc.getString("studentName") ?: "Student"
                        val roll = doc.getString("rollNumber") ?: ""
                        val room = doc.getString("roomNumber") ?: ""
                        val date = doc.getString("localDate") ?: ""
                        val marked = doc.getString("markedStatus") ?: "ABSENT"
                        val claimed = doc.getString("claimedStatus") ?: "PRESENT"
                        val reason = doc.getString("reason") ?: ""
                        val created = doc.getLong("createdAtMillis") ?: 0L
                        val status = doc.getString("status") ?: "PENDING"
                        val resStatus = doc.getString("resolvedStatus")
                        val resAt = doc.getLong("resolvedAtMillis")
                        com.hostelattendance.data.local.AttendanceDisputeItem(
                            disputeId = disputeId,
                            hostelId = hostelId,
                            studentId = sId,
                            studentName = sName,
                            rollNumber = roll,
                            roomNumber = room,
                            localDate = date,
                            markedStatus = marked,
                            claimedStatus = claimed,
                            reason = reason,
                            createdAtMillis = created,
                            status = status,
                            resolvedStatus = resStatus,
                            resolvedAtMillis = resAt,
                        )
                    }.sortedByDescending { it.createdAtMillis }
                    onUpdate(list)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up student disputes realtime listener: ${e.message}")
            null
        }
    }

    /**
     * Resolve a student attendance dispute as Admin and update Firestore.
     */
    suspend fun resolveDispute(
        disputeId: String,
        newStatus: String,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("attendance_disputes").document(disputeId)
                .set(
                    mapOf(
                        "status" to "RESOLVED",
                        "resolvedStatus" to newStatus,
                        "resolvedAtMillis" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge()
                )
                .await()
            Log.d(TAG, "Successfully resolved attendance dispute: $disputeId to $newStatus")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve dispute '$disputeId': ${e.message}")
        }
    }

    /**
     * Updates an attendance record status directly in Firestore by studentId and localDate.
     * Inserts the record if none existed in Firestore yet.
     */
    suspend fun updateAttendanceStatusDirectly(
        hostelId: String,
        studentId: String,
        localDate: String,
        newStatus: String,
        studentName: String = "",
        rollNumber: String = "",
        roomNumber: String = "",
        wingId: String = "",
        wingName: String = "",
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            val querySnapshot = recordsCol(db, hostelId)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("localDate", localDate)
                .get()
                .await()
            val now = System.currentTimeMillis()
            if (!querySnapshot.isEmpty) {
                for (doc in querySnapshot.documents) {
                    doc.reference.update(
                        mapOf(
                            "status" to newStatus,
                            "updatedAtMillis" to now,
                        )
                    ).await()
                }
            } else {
                val recordId = "rec_${studentId}_$localDate"
                val recordData = mapOf(
                    "recordId" to recordId,
                    "hostelId" to hostelId,
                    "sessionId" to "session_${wingId.ifBlank { "default" }}_$localDate",
                    "studentId" to studentId,
                    "studentNameSnapshot" to studentName,
                    "rollNumberSnapshot" to rollNumber,
                    "roomNumberSnapshot" to roomNumber,
                    "wingIdSnapshot" to wingId,
                    "wingNameSnapshot" to wingName,
                    "localDate" to localDate,
                    "status" to newStatus,
                    "version" to 1,
                    "updatedAtMillis" to now,
                )
                recordsCol(db, hostelId).document(safeDocId(recordId))
                    .set(recordData, SetOptions.merge())
                    .await()
            }
            Log.d(TAG, "Directly updated Firestore attendance status to $newStatus for student $studentId on $localDate")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update attendance directly in Firestore: ${e.message}")
        }
    }

    /**
     * Dismiss / Reject an attendance dispute as Admin.
     */
    suspend fun dismissDispute(
        disputeId: String,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            db.collection("attendance_disputes").document(disputeId)
                .set(
                    mapOf(
                        "status" to "REJECTED",
                        "resolvedAtMillis" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge()
                )
                .await()
            Log.d(TAG, "Successfully dismissed attendance dispute: $disputeId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss dispute '$disputeId': ${e.message}")
        }
    }

    // =========================================================================
    // 4. COORDINATED FULL CLOUD SYNC (SETTINGS SCREEN & APP INITIALIZATION)
    // =========================================================================

    data class SyncSummary(
        val restoredWings: Int,
        val restoredStudents: Int,
        val restoredSessions: Int,
        val restoredRecords: Int,
    )

    /**
     * Complete synchronization of all entities.
     * Pushes local changes if administrator, then performs complete phased restoration.
     */
    suspend fun syncAllData(
        hostelId: String,
        database: HostelDatabase,
        isAdmin: Boolean = false,
    ): SyncSummary = withContext(Dispatchers.IO) {
        val wingDao = database.wingDao()
        val studentDao = database.studentDao()
        val sessionDao = database.attendanceSessionDao()

        // 1. If administrator, push any local wings and students to cloud first
        if (isAdmin) {
            val localWings = wingDao.getAllActiveWings(hostelId)
            val localStudents = studentDao.getAllStudents(hostelId)
            if (localWings.isNotEmpty() || localStudents.isNotEmpty()) {
                syncWingsAndStudents(hostelId, localWings, localStudents)
            }
        }

        // 2. Restore master roster from Firestore
        val (wCount, sCount) = restoreWingsAndStudentsFromFirestore(hostelId, wingDao, studentDao)

        // 3. Restore attendance sessions & records with phased recovery
        val (sessCount, recCount) = restoreAttendanceFromFirestore(hostelId, sessionDao)

        SyncSummary(
            restoredWings = wCount,
            restoredStudents = sCount,
            restoredSessions = sessCount,
            restoredRecords = recCount,
        )
    }
}
