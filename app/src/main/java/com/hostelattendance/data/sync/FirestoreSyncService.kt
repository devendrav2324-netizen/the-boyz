package com.hostelattendance.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.hostelattendance.data.local.AttendanceRecordEntity
import com.hostelattendance.data.local.AttendanceSessionDao
import com.hostelattendance.data.local.AttendanceSessionEntity
import com.hostelattendance.data.local.AttendanceSessionWithRecords
import com.hostelattendance.data.local.StudentDao
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingDao
import com.hostelattendance.data.local.WingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object FirestoreSyncService {

    private fun safeDocId(id: String): String = id.replace("/", "_")

    private val firestore: FirebaseFirestore?
        get() = try {
            if (FirebaseAuth.getInstance().currentUser != null) {
                FirebaseFirestore.getInstance()
            } else null
        } catch (_: Exception) {
            null
        }

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
            withTimeoutOrNull(5000L) {
                db.collection("hostels").document(hostelId)
                    .collection("wings").document(safeDocId(wing.wingId))
                    .set(data, SetOptions.merge())
                    .await()
            }
        } catch (_: Exception) {}
    }

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
            withTimeoutOrNull(5000L) {
                db.collection("hostels").document(hostelId)
                    .collection("students").document(safeDocId(student.studentId))
                    .set(data, SetOptions.merge())
                    .await()
            }
        } catch (_: Exception) {}
    }

    suspend fun syncWingsAndStudents(
        hostelId: String,
        wings: List<WingEntity>,
        students: List<StudentEntity>,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        try {
            withTimeoutOrNull(5000L) {
                db.collection("hostels").document(hostelId)
                    .set(mapOf("hostelId" to hostelId, "updatedAtMillis" to System.currentTimeMillis()), SetOptions.merge())
                    .await()
            }

            // 1. Wings
            val wingChunks = wings.chunked(300)
            for (chunk in wingChunks) {
                val batch = db.batch()
                for (wing in chunk) {
                    val ref = db.collection("hostels").document(hostelId)
                        .collection("wings").document(safeDocId(wing.wingId))
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
                withTimeoutOrNull(8000L) {
                    batch.commit().await()
                }
            }

            // 2. Students
            val studentChunks = students.chunked(300)
            for (chunk in studentChunks) {
                val batch = db.batch()
                for (student in chunk) {
                    val ref = db.collection("hostels").document(hostelId)
                        .collection("students").document(safeDocId(student.studentId))
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
                withTimeoutOrNull(8000L) {
                    batch.commit().await()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun syncAttendanceSession(
        hostelId: String,
        session: AttendanceSessionEntity,
        records: List<AttendanceRecordEntity>,
    ) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
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
            db.collection("hostels").document(hostelId)
                .collection("attendance_sessions").document(safeDocId(session.sessionId))
                .set(sessionData, SetOptions.merge())
                .await()

            val recordChunks = records.chunked(400)
            for (chunk in recordChunks) {
                val batch = db.batch()
                for (rec in chunk) {
                    val ref = db.collection("hostels").document(hostelId)
                        .collection("attendance_records").document(safeDocId(rec.recordId))
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
        } catch (_: Exception) {}
    }

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
            db.collection("hostels").document(hostelId)
                .collection("attendance_records").document(safeDocId(record.recordId))
                .set(recordData, SetOptions.merge())
                .await()
        } catch (_: Exception) {}
    }

    suspend fun restoreWingsAndStudentsFromFirestore(
        hostelId: String,
        wingDao: WingDao,
        studentDao: StudentDao,
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Pair(0, 0)
        try {
            val wingDocs = withTimeoutOrNull(10000L) {
                db.collection("hostels").document(hostelId)
                    .collection("wings").get().await()
            }

            var restoredWingsCount = 0
            if (wingDocs != null) {
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
                    wingDao.insertWings(deduplicatedWings)
                    restoredWingsCount = deduplicatedWings.size
                }
            }

            val studentDocs = withTimeoutOrNull(15000L) {
                db.collection("hostels").document(hostelId)
                    .collection("students").get().await()
            }

            var restoredStudentsCount = 0
            if (studentDocs != null) {
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
                    deduplicatedStudents.chunked(250).forEach { chunk ->
                        studentDao.insertStudents(chunk)
                    }
                    restoredStudentsCount = deduplicatedStudents.size
                }
            }
            Pair(restoredWingsCount, restoredStudentsCount)
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    suspend fun restoreAttendanceFromFirestore(
        hostelId: String,
        sessionDao: AttendanceSessionDao,
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Pair(0, 0)
        try {
            val sessionDocs = withTimeoutOrNull(12000L) {
                db.collection("hostels").document(hostelId)
                    .collection("attendance_sessions").get().await()
            }

            var restoredSessionsCount = 0
            val knownSessionIds = mutableSetOf<String>()
            if (sessionDocs != null) {
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
                    deduplicatedSessions.chunked(200).forEach { chunk ->
                        sessionDao.insertSessions(chunk)
                    }
                    restoredSessionsCount = deduplicatedSessions.size
                }
            }

            val recordDocs = withTimeoutOrNull(20000L) {
                db.collection("hostels").document(hostelId)
                    .collection("attendance_records").get().await()
            }

            var restoredRecordsCount = 0
            if (recordDocs != null) {
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
                    // Ensure every record has an existing session in SQLite
                    val syntheticSessions = deduplicatedRecords
                        .map { it.sessionId to (it.localDate to it.wingIdSnapshot) }
                        .distinctBy { it.first }
                        .filterNot { knownSessionIds.contains(it.first) }
                        .map { (sId, dateAndWing) ->
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
                        }
                    if (syntheticSessions.isNotEmpty()) {
                        sessionDao.insertSessions(syntheticSessions)
                    }

                    deduplicatedRecords.chunked(250).forEach { chunk ->
                        sessionDao.insertRecords(chunk)
                    }
                    restoredRecordsCount = deduplicatedRecords.size
                }
            }
            Pair(restoredSessionsCount, restoredRecordsCount)
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

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

            // 1. Try to find session doc by direct ID first (both safe and raw), then by query
            val directDoc = try {
                val d = db.collection("hostels").document(hostelId)
                    .collection("attendance_sessions").document(safeId)
                    .get()
                    .await()
                if (d != null && d.exists()) d else {
                    if (safeId != expectedSessionId) {
                        try {
                            db.collection("hostels").document(hostelId)
                                .collection("attendance_sessions").document(expectedSessionId)
                                .get()
                                .await()
                        } catch (_: Exception) { null }
                    } else null
                }
            } catch (_: Exception) { null }

            val sessionDoc = if (directDoc != null && directDoc.exists()) {
                directDoc
            } else {
                try {
                    val query = db.collection("hostels").document(hostelId)
                        .collection("attendance_sessions")
                        .whereEqualTo("localDate", localDate)
                        .whereEqualTo("wingId", wingId)
                        .limit(1)
                        .get()
                        .await()
                    query.documents.firstOrNull()
                } catch (_: Exception) { null }
            }

            val sessionId = sessionDoc?.getString("sessionId")?.takeIf { it.isNotBlank() }
                ?: sessionDoc?.id
                ?: expectedSessionId

            // 2. Fetch records: first by sessionId (and safeId), then fallback by localDate and wingIdSnapshot
            var recordDocs = try {
                db.collection("hostels").document(hostelId)
                    .collection("attendance_records")
                    .whereEqualTo("sessionId", sessionId)
                    .get()
                    .await()
                    .documents
            } catch (_: Exception) { emptyList() }

            if (recordDocs.isEmpty() && safeDocId(sessionId) != sessionId) {
                recordDocs = try {
                    db.collection("hostels").document(hostelId)
                        .collection("attendance_records")
                        .whereEqualTo("sessionId", safeDocId(sessionId))
                        .get()
                        .await()
                        .documents
                } catch (_: Exception) { emptyList() }
            }

            if (recordDocs.isEmpty()) {
                recordDocs = try {
                    db.collection("hostels").document(hostelId)
                        .collection("attendance_records")
                        .whereEqualTo("localDate", localDate)
                        .whereEqualTo("wingIdSnapshot", wingId)
                        .get()
                        .await()
                        .documents
                } catch (_: Exception) { emptyList() }
            }

            // If neither session doc exists nor any records exist, no attendance was ever recorded in cloud
            if (sessionDoc == null && recordDocs.isEmpty()) {
                return@withContext null
            }

            // Ensure session entity is inserted into SQLite
            val sessionEntity = AttendanceSessionEntity(
                sessionId = sessionId,
                hostelId = sessionDoc?.getString("hostelId") ?: hostelId,
                localDate = sessionDoc?.getString("localDate") ?: localDate,
                timeZone = sessionDoc?.getString("timeZone") ?: "Asia/Kolkata",
                wingId = sessionDoc?.getString("wingId") ?: wingId,
                state = sessionDoc?.getString("state") ?: "SAVED",
                savedAtMillis = sessionDoc?.getLong("savedAtMillis") ?: System.currentTimeMillis(),
                savedBy = sessionDoc?.getString("savedBy"),
                version = sessionDoc?.getLong("version") ?: 0L,
            )
            sessionDao.insertSession(sessionEntity)

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
            if (records.isNotEmpty()) {
                sessionDao.insertRecords(records)
            }
            sessionDao.findSessionWithRecords(sessionId)
        } catch (_: Exception) {
            null
        }
    }

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
                withTimeoutOrNull(8000L) {
                    db.collection("hostels").document(hostelId)
                        .collection("attendance_sessions")
                        .whereGreaterThanOrEqualTo("localDate", startDate)
                        .whereLessThanOrEqualTo("localDate", endDate)
                        .get()
                        .await()
                }
            } catch (_: Exception) { null }

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
                    sessionDao.insertSessions(sessions)
                    anyRestored = true
                }
            }

            val recordDocs = try {
                withTimeoutOrNull(10000L) {
                    db.collection("hostels").document(hostelId)
                        .collection("attendance_records")
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
                    records.chunked(300).forEach { chunk ->
                        sessionDao.insertRecords(chunk)
                    }
                    anyRestored = true
                }
            }
        } catch (_: Exception) {}

        // Fallback: If ranged query failed (e.g. index error or permission), restore all attendance
        if (!anyRestored) {
            restoreAttendanceFromFirestore(hostelId, sessionDao)
        }
    }

    fun listenToAttendanceRealtime(
        hostelId: String,
        localDate: String,
        wingId: String,
        sessionDao: AttendanceSessionDao,
    ): com.google.firebase.firestore.ListenerRegistration? {
        val db = firestore ?: return null
        val expectedSessionId = "$hostelId:$localDate:$wingId"

        return try {
            val sessionQuery = db.collection("hostels").document(hostelId)
                .collection("attendance_sessions")
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
                CoroutineScope(Dispatchers.IO).launch {
                    sessionDao.insertSession(sessionEntity)
                }
            }

            val recordsQuery = db.collection("hostels").document(hostelId)
                .collection("attendance_records")
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
                    CoroutineScope(Dispatchers.IO).launch {
                        sessionDao.insertRecords(records)
                    }
                }
            }

            com.google.firebase.firestore.ListenerRegistration {
                sessionReg.remove()
                recordsReg.remove()
            }
        } catch (_: Exception) {
            null
        }
    }

    data class SyncSummary(
        val restoredWings: Int,
        val restoredStudents: Int,
        val restoredSessions: Int,
        val restoredRecords: Int,
    )

    suspend fun syncAllData(
        hostelId: String,
        database: com.hostelattendance.data.local.HostelDatabase,
        isAdmin: Boolean = false,
    ): SyncSummary = withContext(Dispatchers.IO) {
        val wingDao = database.wingDao()
        val studentDao = database.studentDao()
        val sessionDao = database.attendanceSessionDao()

        // 1. If admin, push local wings and students first
        if (isAdmin) {
            val localWings = wingDao.getAllActiveWings(hostelId)
            val localStudents = studentDao.getAllStudents(hostelId)
            if (localWings.isNotEmpty() || localStudents.isNotEmpty()) {
                syncWingsAndStudents(hostelId, localWings, localStudents)
            }
        }

        // 2. Restore wings and students from Firestore
        val (wCount, sCount) = restoreWingsAndStudentsFromFirestore(hostelId, wingDao, studentDao)

        // 3. Fallback: seed if still empty
        val currentStudents = studentDao.getAllStudents(hostelId)
        if (currentStudents.isEmpty()) {
            // Seeder can be called if needed by caller
        }

        // 4. Restore all attendance sessions and records from Firestore
        val (sessCount, recCount) = restoreAttendanceFromFirestore(hostelId, sessionDao)

        SyncSummary(
            restoredWings = wCount,
            restoredStudents = sCount,
            restoredSessions = sessCount,
            restoredRecords = recCount,
        )
    }

    fun listenToWingsAndStudentsRealtime(
        hostelId: String,
        wingDao: WingDao,
        studentDao: StudentDao,
    ): com.google.firebase.firestore.ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            val wingsReg = db.collection("hostels").document(hostelId)
                .collection("wings")
                .addSnapshotListener { snapshot, error ->
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
                        CoroutineScope(Dispatchers.IO).launch {
                            wingDao.insertWings(wings)
                        }
                    }
                }

            val studentsReg = db.collection("hostels").document(hostelId)
                .collection("students")
                .addSnapshotListener { snapshot, error ->
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
                        CoroutineScope(Dispatchers.IO).launch {
                            students.chunked(250).forEach { chunk ->
                                studentDao.insertStudents(chunk)
                            }
                        }
                    }
                }

            com.google.firebase.firestore.ListenerRegistration {
                wingsReg.remove()
                studentsReg.remove()
            }
        } catch (_: Exception) {
            null
        }
    }
}
