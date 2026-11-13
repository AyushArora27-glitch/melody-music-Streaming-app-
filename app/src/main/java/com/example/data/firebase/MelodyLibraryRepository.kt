package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.model.Playlist
import com.example.model.Track
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MelodyLibraryRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  suspend fun ensureUserProfile(displayName: String?, email: String?): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val safeName = displayName?.trim()?.take(80)?.ifBlank { null } ?: "Melody Listener"
    try {
      val snapshot = docRef.get().await()
      if (!snapshot.exists()) {
        val createPayload =
          mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to safeName,
            "email" to email?.trim()?.take(160)?.takeIf { it.isNotBlank() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.set(createPayload).await()
      } else {
        val updatePayload =
          mutableMapOf<String, Any?>(
            "displayName" to safeName,
            "email" to email?.trim()?.take(160)?.takeIf { it.isNotBlank() },
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.update(updatePayload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      throw e
    }
  }

  fun observeLikedTracks(userId: String): Flow<List<Track>> {
    val colRef = db.collection("users").document(userId).collection("liked_tracks")
    val path = colRef.path
    return colRef
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.documents.mapNotNull { doc ->
          doc.toObject(
            FirestoreSavedTrack::class.java,
            ServerTimestampBehavior.ESTIMATE,
          )?.toDomainTrack()
        }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
        }
        throw error
      }
  }

  suspend fun setTrackLiked(track: Track, liked: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val safeId = sanitizeFirestoreId(track.id)
    val docRef = db.collection("users").document(uid).collection("liked_tracks").document(safeId)
    try {
      if (liked) {
        val existingDoc = docRef.get().await()
        if (existingDoc.exists()) {
          docRef.update(track.toFirestorePayload(uid, safeId, includeCreatedAt = false)).await()
        } else {
          docRef.set(track.toFirestorePayload(uid, safeId, includeCreatedAt = true)).await()
        }
      } else {
        docRef.delete().await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (liked) OperationType.WRITE else OperationType.DELETE,
        docRef.path,
        auth,
      )
      throw e
    }
  }

  fun observePlaylists(userId: String): Flow<List<Playlist>> {
    val colRef = db.collection("users").document(userId).collection("playlists")
    val path = colRef.path
    return colRef
      .snapshots()
      .map { snapshot ->
        snapshot.documents
          .mapNotNull { doc ->
            doc.toObject(
              FirestorePlaylist::class.java,
              ServerTimestampBehavior.ESTIMATE,
            )
          }
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
          .map { it.toDomainPlaylist() }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
        }
        throw error
      }
  }

  suspend fun createPlaylist(title: String, creator: String = "You"): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanTitle = title.trim().take(120)
    require(cleanTitle.isNotEmpty()) { "Playlist name cannot be empty" }
    val safeCreator = creator.trim().take(80).ifBlank { "You" }
    val docRef = db.collection("users").document(uid).collection("playlists").document()
    val payload =
      mapOf(
        "userId" to uid,
        "title" to cleanTitle,
        "creator" to safeCreator,
        "isPinned" to false,
        "trackCount" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun togglePlaylistPinned(playlistId: String, currentPinned: Boolean): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val safePlaylistId = sanitizeFirestoreId(playlistId)
      val docRef =
        db.collection("users").document(uid).collection("playlists").document(safePlaylistId)
      try {
        docRef
          .update(
            mapOf(
              "isPinned" to !currentPinned,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        throw e
      }
    }

  fun observePlaylistTracks(userId: String, playlistId: String): Flow<List<Track>> {
    val safePlaylistId = sanitizeFirestoreId(playlistId)
    val colRef =
      db.collection("users")
        .document(userId)
        .collection("playlists")
        .document(safePlaylistId)
        .collection("tracks")
    val path = colRef.path
    return colRef
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.documents.mapNotNull { doc ->
          doc.toObject(
            FirestoreSavedTrack::class.java,
            ServerTimestampBehavior.ESTIMATE,
          )?.toDomainTrack()
        }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
        }
        throw error
      }
  }

  suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> = runCatching {
    val uid = requireUserId()
    val safePlaylistId = sanitizeFirestoreId(playlistId)
    val safeTrackId = sanitizeFirestoreId(track.id)
    val playlistRef =
      db.collection("users").document(uid).collection("playlists").document(safePlaylistId)
    val trackRef = playlistRef.collection("tracks").document(safeTrackId)

    try {
      val existingTrack = trackRef.get().await()
      if (!existingTrack.exists()) {
        val batch = db.batch()
        batch.set(trackRef, track.toFirestorePayload(uid, safeTrackId, includeCreatedAt = true))
        batch.update(
          playlistRef,
          mapOf(
            "trackCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.commit().await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, trackRef.path, auth)
      throw e
    }
  }

  suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val safePlaylistId = sanitizeFirestoreId(playlistId)
      val safeTrackId = sanitizeFirestoreId(trackId)
      val playlistRef =
        db.collection("users").document(uid).collection("playlists").document(safePlaylistId)
      val trackRef = playlistRef.collection("tracks").document(safeTrackId)

      try {
        val existingTrack = trackRef.get().await()
        if (existingTrack.exists()) {
          val batch = db.batch()
          batch.delete(trackRef)
          batch.update(
            playlistRef,
            mapOf(
              "trackCount" to FieldValue.increment(-1L),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          batch.commit().await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, trackRef.path, auth)
        throw e
      }
    }

  fun observeHistoryTracks(userId: String): Flow<List<Track>> {
    val colRef = db.collection("users").document(userId).collection("history")
    val path = colRef.path
    return colRef
      .orderBy("updatedAt", Query.Direction.DESCENDING)
      .limit(10)
      .snapshots()
      .map { snapshot ->
        snapshot.documents.mapNotNull { doc ->
          doc.toObject(
            FirestoreSavedTrack::class.java,
            ServerTimestampBehavior.ESTIMATE,
          )?.toDomainTrack()
        }
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
          handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
        }
        throw error
      }
  }

  suspend fun recordListeningHistory(track: Track): Result<Unit> = runCatching {
    val uid = requireUserId()
    val safeTrackId = sanitizeFirestoreId(track.id)
    val docRef = db.collection("users").document(uid).collection("history").document(safeTrackId)
    try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(track.toFirestorePayload(uid, safeTrackId, includeCreatedAt = false)).await()
      } else {
        docRef.set(track.toFirestorePayload(uid, safeTrackId, includeCreatedAt = true)).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      throw e
    }
  }
}
