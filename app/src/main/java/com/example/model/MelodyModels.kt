package com.example.model

import androidx.annotation.DrawableRes

enum class AppTab {
  Home,
  Search,
  Library,
}

enum class ViewportClass {
  Compact,
  Medium,
  Expanded,
}

enum class PlaybackStatus {
  Idle,
  Loading,
  Buffering,
  Playing,
  Paused,
  Ended,
  Failed,
}

enum class RepeatMode {
  Off,
  All,
  One,
}

enum class LibraryCategory(val label: String) {
  Playlists("Playlists"),
  Artists("Artists"),
  Albums("Albums"),
  Downloaded("Downloaded"),
}

enum class LibrarySortOrder {
  Recent,
  Alphabetical,
}

enum class HomeFilter(val label: String) {
  All("All"),
  Mixes("Mixes"),
  Focus("Focus"),
  Chill("Chill"),
  Energy("Energy"),
}

data class Track(
  val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val artworkUrl: String? = null,
  @param:DrawableRes val fallbackArtRes: Int? = null,
  val previewUrl: String? = null,
  val genre: String = "Electronic",
  val explicit: Boolean = false,
) {
  val youtubeVideoId: String?
    get() =
      when {
        id.startsWith("yt_") && id.length > 3 -> id.removePrefix("yt_")
        previewUrl?.contains("youtube.com/watch?v=") == true ->
          previewUrl.substringAfter("watch?v=").substringBefore("&").takeIf { it.isNotBlank() }
        else -> null
      }

  val isYouTubeTrack: Boolean
    get() = youtubeVideoId != null
}

data class DiscoveryCollection(
  val id: String,
  val title: String,
  val subtitle: String,
  val eyebrow: String,
  val filterTag: HomeFilter,
  @param:DrawableRes val artRes: Int,
  val tracks: List<Track>,
  val isEditorial: Boolean = true,
)

data class Playlist(
  val id: String,
  val title: String,
  val creator: String,
  val trackIds: List<String> = emptyList(),
  val trackCount: Int = trackIds.size,
  val isPinned: Boolean = false,
  val createdAtMs: Long = System.currentTimeMillis(),
  @param:DrawableRes val artRes: Int? = null,
)

data class MoodCard(
  val id: String,
  val title: String,
  val subtitle: String,
  val query: String,
  @param:DrawableRes val artRes: Int,
)

data class PlaybackSession(
  val currentTrack: Track? = null,
  val queue: List<Track> = emptyList(),
  val currentIndex: Int = -1,
  val status: PlaybackStatus = PlaybackStatus.Idle,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val isShuffleEnabled: Boolean = false,
  val repeatMode: RepeatMode = RepeatMode.Off,
  val isPreviewFallback: Boolean = false,
  val errorMessage: String? = null,
) {
  val isPlaying: Boolean
    get() = status == PlaybackStatus.Playing

  val isActive: Boolean
    get() = currentTrack != null

  val progressFraction: Float
    get() =
      if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
      } else {
        0f
      }
}

sealed interface SearchResultState {
  data object Idle : SearchResultState

  data object Loading : SearchResultState

  data class Success(val tracks: List<Track>, val fromFallback: Boolean = false) : SearchResultState

  data object Empty : SearchResultState

  data class Error(val message: String) : SearchResultState
}
