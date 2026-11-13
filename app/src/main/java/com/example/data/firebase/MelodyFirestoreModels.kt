package com.example.data.firebase

import com.example.R
import com.example.model.Playlist
import com.example.model.Track
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreSavedTrack(
  @DocumentId val id: String = "",
  val userId: String = "",
  val trackId: String = "",
  val title: String = "",
  val artist: String = "",
  val album: String = "",
  val durationMs: Long = 0L,
  val artworkUrl: String? = null,
  val previewUrl: String? = null,
  val genre: String = "Music",
  val explicit: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toDomainTrack(): Track =
    Track(
      id = trackId.ifBlank { id },
      title = title,
      artist = artist,
      album = album,
      durationMs = durationMs,
      artworkUrl = artworkUrl,
      fallbackArtRes = R.drawable.img_art_daily_discovery,
      previewUrl = previewUrl,
      genre = genre,
      explicit = explicit,
    )
}

@IgnoreExtraProperties
data class FirestorePlaylist(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val creator: String = "You",
  val isPinned: Boolean = false,
  val trackCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toDomainPlaylist(): Playlist =
    Playlist(
      id = id,
      title = title,
      creator = creator,
      trackIds = emptyList(),
      trackCount = trackCount.toInt(),
      isPinned = isPinned,
      createdAtMs = (createdAt ?: Timestamp(0, 0)).toDate().time,
      artRes = R.drawable.img_art_lofi_chill,
    )
}

fun sanitizeFirestoreId(raw: String): String =
  raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128).ifBlank { "item" }

fun Track.toFirestorePayload(
  uid: String,
  safeTrackId: String = sanitizeFirestoreId(id),
  includeCreatedAt: Boolean = true,
): Map<String, Any> {
  val map = mutableMapOf<String, Any?>(
    "userId" to uid,
    "trackId" to safeTrackId,
    "title" to title.take(200).ifBlank { "Untitled Track" },
    "artist" to artist.take(200).ifBlank { "Unknown Artist" },
    "album" to album.take(200),
    "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
    "artworkUrl" to artworkUrl?.take(1000)?.takeIf { it.isNotBlank() },
    "previewUrl" to previewUrl?.take(1000)?.takeIf { it.isNotBlank() },
    "genre" to genre.take(80),
    "explicit" to explicit,
    "updatedAt" to FieldValue.serverTimestamp(),
  )
  if (includeCreatedAt) {
    map["createdAt"] = FieldValue.serverTimestamp()
  }
  @Suppress("UNCHECKED_CAST")
  return map.filterValues { it != null } as Map<String, Any>
}
