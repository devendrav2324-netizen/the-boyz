import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";

const projectId = "demo-hostel-attendance";
let testEnvironment;

before(async () => {
  const rules = await readFile(new URL("../../firestore.rules", import.meta.url), "utf8");
  testEnvironment = await initializeTestEnvironment({
    projectId,
    firestore: { rules },
  });
});

after(async () => {
  await testEnvironment?.cleanup();
});

beforeEach(async () => {
  await testEnvironment.clearFirestore();
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "user_profiles", "warden-a"), {
      hostelId: "hostel-a",
      role: "WARDEN",
      approved: true,
      allowedWingIds: ["wing-a"],
      canRegisterStudents: false,
      isActive: true,
    });
    await setDoc(doc(db, "user_profiles", "pending-user"), {
      hostelId: null,
      role: null,
      approved: false,
      allowedWingIds: [],
      canRegisterStudents: false,
      isActive: true,
    });
    await setDoc(doc(db, "user_profiles", "admin-a"), {
      hostelId: "hostel-a",
      role: "ADMIN",
      approved: true,
      allowedWingIds: [],
      canRegisterStudents: true,
      isActive: true,
    });
    await setDoc(doc(db, "hostels", "hostel-a"), { name: "Test Hostel" });
    await setDoc(doc(db, "hostels", "hostel-a", "wings", "wing-a"), {
      name: "Wing A",
      isActive: true,
    });
    await setDoc(doc(db, "hostels", "hostel-a", "wings", "wing-b"), {
      name: "Wing B",
      isActive: true,
    });
    await setDoc(doc(db, "hostels", "hostel-a", "students", "student-a"), {
      currentWingId: "wing-a",
      isActive: true,
      studentName: "Test Student",
    });
    await setDoc(doc(db, "hostels", "hostel-a", "audit_events", "audit-a"), {
      action: "ATTENDANCE_CORRECTED",
    });
  });
});

test("unauthenticated users cannot read a hostel or profile", async () => {
  const db = testEnvironment.unauthenticatedContext().firestore();

  await assertFails(getDoc(doc(db, "hostels", "hostel-a")));
  await assertFails(getDoc(doc(db, "user_profiles", "warden-a")));
});

test("a pending user can read only their own pending profile and cannot access hostel data", async () => {
  const db = testEnvironment.authenticatedContext("pending-user").firestore();

  await assertSucceeds(getDoc(doc(db, "user_profiles", "pending-user")));
  await assertFails(getDoc(doc(db, "hostels", "hostel-a")));
});

test("a warden can read assigned-wing data but not another wing or its data", async () => {
  const db = testEnvironment.authenticatedContext("warden-a").firestore();

  await assertSucceeds(getDoc(doc(db, "hostels", "hostel-a", "wings", "wing-a")));
  await assertSucceeds(getDoc(doc(db, "hostels", "hostel-a", "students", "student-a")));
  await assertFails(getDoc(doc(db, "hostels", "hostel-a", "wings", "wing-b")));
});

test("clients cannot grant roles or write attendance and audit records", async () => {
  const db = testEnvironment.authenticatedContext("warden-a").firestore();

  await assertFails(setDoc(doc(db, "user_profiles", "warden-a"), {
    hostelId: "hostel-a",
    role: "ADMIN",
    approved: true,
    allowedWingIds: [],
    canRegisterStudents: true,
    isActive: true,
  }));
  await assertFails(setDoc(doc(db, "hostels", "hostel-a", "attendance_sessions", "session-a"), {
    wingId: "wing-a",
    localDate: "2026-10-25",
    state: "SAVED",
  }));
  await assertFails(setDoc(doc(db, "hostels", "hostel-a", "audit_events", "forged"), {
    action: "ATTENDANCE_CORRECTED",
  }));
});

test("only a provisioned admin can inspect audit history; ordinary client writes remain denied", async () => {
  const adminDb = testEnvironment.authenticatedContext(
    "admin-a",
    { admin: true, hostelId: "hostel-a" },
  ).firestore();
  const wardenDb = testEnvironment.authenticatedContext("warden-a").firestore();

  await assertSucceeds(getDoc(doc(adminDb, "hostels", "hostel-a", "audit_events", "audit-a")));
  await assertFails(getDoc(doc(wardenDb, "hostels", "hostel-a", "audit_events", "audit-a")));
  await assertFails(setDoc(doc(adminDb, "hostels", "hostel-a", "audit_events", "forged"), {
    action: "ATTENDANCE_CORRECTED",
  }));
});
