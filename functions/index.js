const admin = require("firebase-admin");
const functions = require("firebase-functions");

admin.initializeApp();

const db = admin.firestore();
const auth = admin.auth();

async function requireAdministrator(context) {
  const caller = context.auth;
  if (!caller || caller.token.admin !== true || typeof caller.token.hostelId !== "string") {
    throw new functions.https.HttpsError("permission-denied", "Only a provisioned hostel administrator can perform this action.");
  }
  const profile = await db.collection("user_profiles").doc(caller.uid).get();
  if (!profile.exists
    || profile.get("approved") !== true
    || profile.get("isActive") !== true
    || profile.get("role") !== "ADMIN"
    || profile.get("hostelId") !== caller.token.hostelId
  ) {
    throw new functions.https.HttpsError("permission-denied", "The administrator account is not active for this hostel.");
  }
  return caller;
}

exports.createPendingUserProfile = functions.auth.user().onCreate(async (user) => {
  const profile = db.collection("user_profiles").doc(user.uid);
  try {
    await profile.create({
      hostelId: null,
      role: null,
      approved: false,
      allowedWingIds: [],
      canRegisterStudents: false,
      isActive: true,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  } catch (error) {
    if (error.code !== 6 && error.code !== "already-exists") throw error;
  }
});

exports.approveUser = functions.https.onCall(async (data, context) => {
  const caller = await requireAdministrator(context);

  const uid = typeof data.uid === "string" ? data.uid.trim() : "";
  const role = data.role;
  const hostelId = caller.token.hostelId;
  const canRegisterStudents = data.canRegisterStudents === true;
  const allowedWingIds = data.allowedWingIds;
  if (!uid || !["WARDEN", "READ_ONLY"].includes(role)) {
    throw new functions.https.HttpsError("invalid-argument", "A user UID and supported staff role are required.");
  }
  if (!Array.isArray(allowedWingIds) || allowedWingIds.some((wingId) => typeof wingId !== "string")) {
    throw new functions.https.HttpsError("invalid-argument", "Allowed wings must be a list of wing IDs.");
  }
  if (new Set(allowedWingIds).size !== allowedWingIds.length || allowedWingIds.length > 100) {
    throw new functions.https.HttpsError("invalid-argument", "Allowed wings must be unique and contain at most 100 IDs.");
  }
  if (role === "READ_ONLY" && canRegisterStudents) {
    throw new functions.https.HttpsError("invalid-argument", "Read-only users cannot register students.");
  }

  const [targetUser, profileSnapshot] = await Promise.all([
    auth.getUser(uid),
    db.collection("user_profiles").doc(uid).get(),
  ]);
  if (!profileSnapshot.exists) {
    throw new functions.https.HttpsError("failed-precondition", "The user profile is not ready. Retry after sign-in completes.");
  }
  if (profileSnapshot.get("hostelId") && profileSnapshot.get("hostelId") !== hostelId) {
    throw new functions.https.HttpsError("permission-denied", "The account belongs to another hostel.");
  }
  if (targetUser.customClaims && targetUser.customClaims.admin === true) {
    throw new functions.https.HttpsError("failed-precondition", "Administrator accounts cannot be modified by this staff approval action.");
  }

  const wingRefs = allowedWingIds.map((wingId) =>
    db.collection("hostels").doc(hostelId).collection("wings").doc(wingId));
  const wingSnapshots = await db.getAll(...wingRefs);
  if (wingSnapshots.some((snapshot) => !snapshot.exists || snapshot.get("isActive") !== true)) {
    throw new functions.https.HttpsError("invalid-argument", "Every allowed wing must exist and be active.");
  }

  await profileSnapshot.ref.update({
    hostelId,
    role,
    approved: true,
    allowedWingIds,
    canRegisterStudents: role === "WARDEN" && canRegisterStudents,
    isActive: true,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  return { approved: true, uid };
});

exports.revokeUser = functions.https.onCall(async (data, context) => {
  const caller = await requireAdministrator(context);

  const uid = typeof data.uid === "string" ? data.uid.trim() : "";
  if (!uid || uid === caller.uid) {
    throw new functions.https.HttpsError("invalid-argument", "A different target user UID is required.");
  }

  const profileRef = db.collection("user_profiles").doc(uid);
  const profileSnapshot = await profileRef.get();
  if (!profileSnapshot.exists || profileSnapshot.get("hostelId") !== caller.token.hostelId) {
    throw new functions.https.HttpsError("permission-denied", "The target account is outside your hostel.");
  }
  if (profileSnapshot.get("role") === "ADMIN") {
    throw new functions.https.HttpsError("failed-precondition", "Administrator access requires a separate trusted process.");
  }

  await profileRef.update({
    isActive: false,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await auth.revokeRefreshTokens(uid);
  return { revoked: true, uid };
});
