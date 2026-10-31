# Firebase authentication and authorization scaffold

This directory contains a fail-closed Firebase scaffold. It does not connect
to a Firebase project until that project is configured, and the native app's
cloud data synchronization is not implemented yet.

## Configure a Firebase project

1. Create or select the institution's Firebase project.
2. Register an Android app with application ID `com.hostelattendance`.
3. Enable Google as a Firebase Authentication provider and configure the
   Android package's signing SHA-1/SHA-256 fingerprints.
4. Download the Android `google-services.json` into `app/`. This file is
   ignored by Git. The Google Services Gradle plugin is applied only when that
   file exists.
5. Enable Cloud Firestore and deploy `firestore.rules`. The rules deny all
   client writes to user profiles, student/wing data, attendance, and audit
   events. Data write APIs must be implemented and tested in trusted backend
   functions before enabling cloud synchronization.
6. Install Functions dependencies in `functions/` and deploy the functions.
   The Auth user-created trigger adds a pending profile. The callable
   `approveUser` supports only `WARDEN` and `READ_ONLY`, validates requested
   active wings, and cannot create or modify an administrator. `revokeUser`
   deactivates staff access and revokes refresh tokens.

## Provision the first administrator

There is deliberately no client-side bootstrap or default administrator.
First create the intended Google account in Firebase Authentication and create
its hostel record and active wing records through a trusted administrative
process. Authenticate the one-time script with Google Application Default
Credentials for an operator permitted to manage Firebase Auth and Firestore.

From `functions/`, after installing dependencies:

```powershell
$env:CONFIRM_HOSTEL_ADMIN_BOOTSTRAP = "FIREBASE_AUTH_UID:HOSTEL_ID"
node scripts/bootstrap-admin.mjs --uid FIREBASE_AUTH_UID --hostel-id HOSTEL_ID
```

Verify the target UID and hostel ID before running the script. It adds the
`admin` and `hostelId` custom claims and writes the approved administrator
profile. Never commit service-account credentials or run this script from the
mobile app.

Staff approvals must be initiated by an authenticated administrator through
the trusted `approveUser` callable. `allowedWingIds: []` means all active
wings in that administrator's hostel; a non-empty list restricts the user to
those wing IDs.

## Verification

Install Functions dependencies in `functions/`, then run:

```powershell
npm.cmd run test:rules
```

The command starts the Firestore Emulator using the demo-only project ID,
executes `functions/tests/firestore.rules.test.mjs`, and shuts the emulator
down.
Authentication UI remains in the setup-required state until
`google-services.json`, Google sign-in, deployed Functions, and deployed
Firestore rules are configured. An approved account is shown as approved, but
the Attendance and Registration screens still use local Room data until a
separately reviewed sync implementation is built.

Do not deploy this scaffold as a complete production backend. In particular,
cloud attendance writes, student/wing writes, audited admin corrections,
conflict resolution, and synchronization are intentionally not enabled.
