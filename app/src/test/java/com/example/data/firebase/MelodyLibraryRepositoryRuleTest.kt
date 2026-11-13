package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.Track
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodyLibraryRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun sampleTrack(id: String): Track =
    Track(
      id = id,
      title = "Neon Echoes",
      artist = "Kiasmos",
      album = "Night Drive Essentials",
      durationMs = 218_000L,
      artworkUrl = "https://example.com/art.jpg",
      previewUrl = "https://example.com/preview.m4a",
      genre = "Electronic",
      explicit = false,
    )

  @Test
  fun ensureUserProfile_authenticatedOwner_succeeds() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = MelodyLibraryRepository(firestore, auth)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserProfile("Alice", ALICE_EMAIL) }
    assertTrue("Expected ensureUserProfile to succeed: ${result.exceptionOrNull()}", result.isSuccess)
  }

  @Test
  fun setTrackLiked_andObserveLikedTracks_persistsAndRemoves() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = MelodyLibraryRepository(firestore, auth)
    val trackId = "track_${UUID.randomUUID().toString().replace("-", "_")}"
    val track = sampleTrack(trackId)

    val likeRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.setTrackLiked(track, liked = true) }
    assertTrue("Expected like to succeed: ${likeRes.exceptionOrNull()}", likeRes.isSuccess)

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeLikedTracks(uid).first { list -> list.any { it.id == trackId } }
      }
    assertTrue(observed.any { it.id == trackId && it.title == "Neon Echoes" })

    val unlikeRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.setTrackLiked(track, liked = false) }
    assertTrue(unlikeRes.isSuccess)
  }

  @Test
  fun createPlaylist_addTrack_andObserve_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = MelodyLibraryRepository(firestore, auth)
    val playlistTitle = "Mix_${UUID.randomUUID().toString().take(8)}"

    val plId =
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.createPlaylist(playlistTitle, "Alice").getOrThrow() }
    assertTrue(plId.isNotBlank())

    val trackId = "track_${UUID.randomUUID().toString().replace("-", "_")}"
    val addRes =
      withTimeout(DEFAULT_TIMEOUT_MS) { repo.addTrackToPlaylist(plId, sampleTrack(trackId)) }
    assertTrue("Expected addTrackToPlaylist to succeed: ${addRes.exceptionOrNull()}", addRes.isSuccess)

    val playlists =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePlaylists(uid).first { list -> list.any { it.id == plId && it.trackCount == 1 } }
      }
    assertTrue(playlists.any { it.id == plId && it.title == playlistTitle && it.trackCount == 1 })

    val tracks =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePlaylistTracks(uid, plId).first { list -> list.any { it.id == trackId } }
      }
    assertTrue(tracks.any { it.id == trackId })
  }

  @Test
  fun observeLikedTracks_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    signInTestUser(BOB_EMAIL)
    val bobRepo = MelodyLibraryRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeLikedTracks(aliceUid).first() }
    } catch (t: Throwable) {
      caught = t
    }
    assertNotNull("Expected PERMISSION_DENIED for cross-user read", caught)
    val firestoreException = caught?.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun observePlaylists_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = MelodyLibraryRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observePlaylists("alice_uid").first() }
    } catch (t: Throwable) {
      caught = t
    }
    assertNotNull("Expected PERMISSION_DENIED for unauthenticated read", caught)
    val firestoreException = caught?.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_melody@test.com"
    const val BOB_EMAIL = "bob_melody@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
