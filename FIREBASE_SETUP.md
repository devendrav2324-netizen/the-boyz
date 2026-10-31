# Firebase Setup & Cloud Synchronization

This document outlines the Firebase Authentication, Cloud Firestore architecture, and security rules for The Boyz Hostel Attendance System.

## Architecture Overview

The system uses an enterprise **Room + Cloud Firestore Hybrid Architecture**:
- **Local Database (Room SQLite):** Offline attendance marking, immediate zero-latency UI response, and local device persistence.
- **Central Cloud Database (Cloud Firestore):** Online real-time multi-device synchronization, remote data backup, and cross-device recovery.
- **Real-Time Streaming:** Active Firestore snapshot listeners (`listenToAttendanceRealtime`, `listenToWingsAndStudentsRealtime`) propagate live attendance marking and roster changes across all authenticated devices in milliseconds.
- **Fail-Closed Security Model:** Strict authorization rules enforce that missing, pending, or unapproved profiles are denied by default. Privilege escalation is prevented at the field level.

---

## Native Android Cloud Synchronization Implementation

Native Android cloud synchronization is **fully implemented and verified** across the following layers:

### 1. `FirestoreSyncService.kt` (`com.hostelattendance.data.sync`):
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
- **Conflict Resolution Engine:**
  - **Deterministic Last-Write-Wins (LWW):** All writes to Firestore and Room carry millisecond timestamps (`updatedAtMillis` / `savedAtMillis`).
  - Incoming remote streams compare timestamps against local records; older writes never overwrite newer changes.
  - Concurrent batch updates sort entities by timestamp (`.sortedBy { it.updatedAtMillis }`) before atomic SQLite transaction commits.

### 2. `RoomAttendanceRepository.kt` (`com.hostelattendance.data.repository`):
- Integrates local Room transactions with `FirestoreSyncService`:
  - `ensureDraftSession()`: Ensures local draft and pushes session to Firestore.
  - `mark()`: Updates SQLite locally and immediately pushes record to Firestore online.
  - `save()`: Locks session as `SAVED` and syncs session + records to Firestore.
  - `reopenSessionAsAdmin()`: Reopens session as `DRAFT` and syncs to Firestore.
  - `updateRecordStatusAsAdmin()`: Updates corrected record and syncs to Firestore.

### 3. `RoomStudentRepository.kt` (`com.hostelattendance.data.repository`):
- Integrates student lifecycle with `FirestoreSyncService`:
  - `registerStudent()`: Inserts student in Room and syncs to Firestore.
  - `updateStudent()`: Updates student profile, updates past attendance snapshots, and syncs both student and records to Firestore.
  - `setStudentActive()`: Updates active status and syncs to Firestore.
  - `createWing()`: Inserts wing and syncs to Firestore.
  - `commitExcelImport()`: Bulk inserts imported students and syncs to Firestore.

---

## Cloud Firestore Security Rules (`firestore.rules`)

The security model is strictly **Fail-Closed** with field-level immutability:

| Security Vector | Implementation in `firestore.rules` | Security Guarantee |
| :--- | :--- | :--- |
| **Missing Profiles** | `hasProfile() && callerProfile().approved == true && callerProfile().isActive == true` | **Fail Closed**: Users without approved profiles are denied all access. |
| **Self-Escalation** | `allow create`: restricts `role == 'USER'`, `canRegisterStudents == false`. `allow update`: verifies all protected fields (`role`, `approved`, `hostelId`, `canRegisterStudents`) match existing `resource.data`. | **Tamper-Proof**: Users cannot elevate themselves to Admin or change hostels. |
| **Attendance Writes** | `allow create, update: if isMasterAdmin() \|\| adminFor(hostelId)` | **Admin Enforced**: Regular users and unauthorized clients cannot mark or tamper with attendance records. |
| **Student Management** | `adminFor(hostelId) \|\| (approved() && callerProfile().canRegisterStudents == true)` | Only authorized administrators or designated wardens can register/modify residents. |
| **Audit Logs** | Client creation and modification are disabled (`allow create, update, delete: if isMasterAdmin()`). | Audit trail cannot be forged by clients. |

---

## Configure a Firebase Project

1. Create or select the institution's Firebase project in the [Firebase Console](https://console.firebase.google.com).
2. Register an Android app with application ID `com.hostelattendance`.
3. Enable Google as a Firebase Authentication provider and add the signing SHA-1 / SHA-256 fingerprints.
4. Download `google-services.json` into `app/`.
5. Deploy `firestore.rules` directly from the Firebase CLI:
   ```bash
   firebase deploy --only firestore:rules
   ```
6. Run the app: On startup or login, data automatically synchronizes between local Room SQLite and Cloud Firestore.
