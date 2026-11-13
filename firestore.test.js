const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfile(uid) {
  const now = new Date();
  return {
    userId: uid,
    displayName: "Melody Listener",
    email: "alice@example.com",
    createdAt: now,
    updatedAt: now,
  };
}

function validTrack(uid, trackId = "track_1") {
  const now = new Date();
  return {
    userId: uid,
    trackId,
    title: "Blinding Lights",
    artist: "The Weeknd",
    album: "After Hours",
    durationMs: 200040,
    artworkUrl: "https://example.com/art.jpg",
    previewUrl: "https://example.com/preview.m4a",
    genre: "R&B/Soul",
    explicit: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validPlaylist(uid, title = "Night Drive Essentials") {
  const now = new Date();
  return {
    userId: uid,
    title,
    creator: "You",
    isPinned: false,
    trackCount: 0,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write user data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("liked_tracks").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("history").get()
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's profile, likes, or playlists", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID));
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("liked_tracks")
      .doc("track_1")
      .set(validTrack(ALICE_UID, "track_1"));
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_1")
      .set(validPlaylist(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("liked_tracks").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("playlists").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("playlists")
      .doc("pl_2")
      .set(validPlaylist(BOB_UID))
  );
});

test("Owner CRUD: Alice can create profile, like tracks, create playlists, add tracks, and record history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(userRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(userRef.collection("liked_tracks").doc("track_1").set(validTrack(ALICE_UID, "track_1")));
  await assertSucceeds(userRef.collection("liked_tracks").get());

  const plRef = userRef.collection("playlists").doc("pl_1");
  await assertSucceeds(plRef.set(validPlaylist(ALICE_UID)));
  await assertSucceeds(plRef.update({ isPinned: true, updatedAt: new Date() }));
  await assertSucceeds(plRef.collection("tracks").doc("track_1").set(validTrack(ALICE_UID, "track_1")));
  await assertSucceeds(plRef.update({ trackCount: 1, updatedAt: new Date() }));

  await assertSucceeds(userRef.collection("history").doc("track_1").set(validTrack(ALICE_UID, "track_1")));
  await assertSucceeds(userRef.collection("liked_tracks").doc("track_1").delete());
});

test("Security bounds: Rejects shadow fields, identity spoofing, and orphaned playlist tracks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  // Shadow update / extra ghost field
  await assertFails(
    userRef.set({ ...validProfile(ALICE_UID), isAdmin: true })
  );

  // Identity spoofing inside document payload
  await assertFails(
    userRef.collection("liked_tracks").doc("track_1").set(validTrack(BOB_UID, "track_1"))
  );

  // Orphaned playlist track (parent playlist does not exist)
  await assertFails(
    userRef
      .collection("playlists")
      .doc("missing_pl")
      .collection("tracks")
      .doc("track_1")
      .set(validTrack(ALICE_UID, "track_1"))
  );

  // Immutable createdAt violation on update
  await assertSucceeds(userRef.set(validProfile(ALICE_UID)));
  await assertFails(
    userRef.update({
      displayName: "New Name",
      createdAt: new Date(1000),
      updatedAt: new Date(),
    })
  );
});
