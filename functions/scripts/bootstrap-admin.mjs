import { applicationDefault, getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const args = new Map();
for (let index = 2; index < process.argv.length; index += 2) {
  const key = process.argv[index];
  const value = process.argv[index + 1];
  if (!key || !key.startsWith("--") || !value || value.startsWith("--")) {
    throw new Error("Usage: node scripts/bootstrap-admin.mjs --uid <FIREBASE_AUTH_UID> --hostel-id <HOSTEL_ID>");
  }
  args.set(key, value);
}

const uid = args.get("--uid")?.trim();
const hostelId = args.get("--hostel-id")?.trim();
if (!uid || !hostelId || args.size !== 2) {
  throw new Error("Both --uid and --hostel-id are required.");
}
if (process.env.CONFIRM_HOSTEL_ADMIN_BOOTSTRAP !== `${uid}:${hostelId}`) {
  throw new Error("Set CONFIRM_HOSTEL_ADMIN_BOOTSTRAP to '<uid>:<hostel-id>' after verifying the target account.");
}

if (getApps().length === 0) initializeApp({ credential: applicationDefault() });
const auth = getAuth();
const db = getFirestore();
const user = await auth.getUser(uid);
const existingClaims = user.customClaims ?? {};
await auth.setCustomUserClaims(uid, {
  ...existingClaims,
  admin: true,
  hostelId,
});
await db.collection("user_profiles").doc(uid).set({
  hostelId,
  role: "ADMIN",
  approved: true,
  allowedWingIds: [],
  canRegisterStudents: true,
  isActive: true,
  createdAt: FieldValue.serverTimestamp(),
  updatedAt: FieldValue.serverTimestamp(),
}, { merge: true });

console.log(`Provisioned administrator ${uid} for hostel ${hostelId}.`);
