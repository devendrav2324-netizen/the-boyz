# Firebase Setup & Cloud Synchronization

This document outlines the Firebase Authentication and Cloud Firestore architecture for The Boyz Hostel Attendance System.

## Architecture Overview

The system uses a **Room + Cloud Firestore Hybrid Architecture**:
- **Local Database (Room SQLite):** Offline attendance marking, immediate zero-latency UI response, and local data persistence.
- **Central Cloud Database (Cloud Firestore):** Online real-time multi-device synchronization, remote data backup, and cross-device recovery.
- **Real-Time Streaming:** Active Firestore snapshot listeners (`listenToAttendanceRealtime`, `listenToWingsAndStudentsRealtime`) propagate live attendance marking and roster changes across all authenticated devices.
- **Role-Based Controls:** Administrators hold exclusive marking, reopening, and registration permissions; regular users operate in live view-only mode.

---

## Native Android Cloud Synchronization Implementation

Native Android cloud synchronization is **fully implemented** across the following layers:

1. **`FirestoreSyncService.kt` (`com.hostelattendance.data.sync`):**
   - **Online Real-Time Writes:**
     - `syncWing()`: Synchronizes wing configuration to Firestore.
     - `syncStudent()`: Pushes student registrations and detail updates to Firestore.
     - `syncAttendanceRecord()`: Streams live attendance marks (**P**, **A**, **L**) to Firestore immediately as the administrator taps.
     - `syncAttendanceSession()`: Synchronizes session states (`DRAFT` / `SAVED`) and full record sets.
     - `syncAttendanceRecordsBatch()`: Bulk updates historical records when student info changes.
   - **Multi-Device Data Recovery:**
     - `restoreWingsAndStudentsFromFirestore()`: Restores the entire master roster on login or new phone installation.
     - `restoreAttendanceFromFirestore()`: Phased recovery of attendance sessions and records with fallback queries.
     - `restoreAttendanceForDateRange()`: Targeted range recovery for the Reports tab.
     - `pullAttendanceSession()`: Pulls individual date/wing sessions on-demand.
   - **Concurrency & Concurrency Safety:**
     - Serialized SQLite write dispatcher (`Dispatchers.IO.limitedParallelism(1)`) preventing database locking during real-time streaming.
     - Automated parent session integrity guard (`ensureParentSessionsExist`) guaranteeing Room `@Relation` joins always resolve.
   - **Real-Time Snapshot Listeners:**
     - `listenToAttendanceRealtime()`: Streams live attendance changes across devices.
     - `listenToWingsAndStudentsRealtime()`: Streams roster updates across devices.

2. **`RoomAttendanceRepository.kt` (`com.hostelattendance.data.repository`):**
   - Integrates local Room transactions with `FirestoreSyncService`:
     - `ensureDraftSession()`: Ensures local draft and pushes session to Firestore.
     - `mark()`: Updates SQLite locally and immediately pushes record to Firestore online.
     - `save()`: Locks session as `SAVED` and syncs session + records to Firestore.
     - `reopenSessionAsAdmin()`: Reopens session as `DRAFT` and syncs to Firestore.
     - `updateRecordStatusAsAdmin()`: Updates corrected record and syncs to Firestore.

3. **`RoomStudentRepository.kt` (`com.hostelattendance.data.repository`):**
   - Integrates student lifecycle with `FirestoreSyncService`:
     - `registerStudent()`: Inserts student in Room and syncs to Firestore.
     - `updateStudent()`: Updates student profile, updates past attendance snapshots, and syncs both student and records to Firestore.
     - `setStudentActive()`: Updates active status and syncs to Firestore.
     - `createWing()`: Inserts wing and syncs to Firestore.
     - `commitExcelImport()`: Bulk inserts imported students and syncs to Firestore.

---

## Configure a Firebase Project

1. Create or select the institution's Firebase project in the [Firebase Console](https://console.firebase.google.com).
2. Register an Android app with application ID `com.hostelattendance`.
3. Enable Google as a Firebase Authentication provider and add the signing SHA-1 / SHA-256 fingerprints.
4. Download `google-services.json` into `app/`.
5. Enable Cloud Firestore and deploy `firestore.rules`.
6. Run the app: On startup or login, data automatically synchronizes between local Room SQLite and Cloud Firestore.
