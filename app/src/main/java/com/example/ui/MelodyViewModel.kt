package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.CatalogRepository
import com.example.data.CatalogSource
import com.example.data.UserPreferencesStore
import com.example.data.firebase.MelodyLibraryRepository
import com.example.model.AppTab
import com.example.model.DiscoveryCollection
import com.example.model.HomeFilter
import com.example.model.LibraryCategory
import com.example.model.LibrarySortOrder
import com.example.model.MoodCard
import com.example.model.PlaybackSession
import com.example.model.Playlist
import com.example.model.SearchResultState
import com.example.model.Track
import com.example.player.PlaybackController
import com.example.player.YouTubePlayerBridge
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionDetailState(
  val id: String,
  val title: String,
  val subtitle: String,
  val eyebrow: String,
  val artRes: Int?,
  val tracks: List<Track>,
  val isUserPlaylist: Boolean = false,
)

data class MelodyUiState(
  val activeTab: AppTab = AppTab.Home,
  val homeFilter: HomeFilter = HomeFilter.All,
  val isHomeLoading: Boolean = false,
  val activeCatalogSource: CatalogSource = CatalogSource.OfflineStarter,
  val homeOfflineNotice: String? = null,
  val catalogWarning: String? = null,
  val cloudSyncError: String? = null,
  val featuredTracks: List<Track> = emptyList(),
  val jumpBackInTracks: List<Track> = emptyList(),
  val discoveryCollections: List<DiscoveryCollection> = emptyList(),
  val searchQuery: String = "",
  val searchState: SearchResultState = SearchResultState.Idle,
  val recentSearches: List<String> = emptyList(),
  val moodCards: List<MoodCard> = emptyList(),
  val libraryCategory: LibraryCategory = LibraryCategory.Playlists,
  val librarySortOrder: LibrarySortOrder = LibrarySortOrder.Recent,
  val librarySearchQuery: String = "",
  val isLibraryGridView: Boolean = false,
  val likedTracks: List<Track> = emptyList(),
  val playlists: List<Playlist> = emptyList(),
  val pinnedLikedSongs: Boolean = true,
  val selectedCollection: CollectionDetailState? = null,
  val isNowPlayingExpanded: Boolean = false,
  val isYouTubeVideoMode: Boolean = true,
  val isFullscreenVideo: Boolean = false,
  val isQueueSheetOpen: Boolean = false,
  val showCreatePlaylistDialog: Boolean = false,
  val trackForPlaylistDialog: Track? = null,
)

class MelodyViewModel(
  private val currentUserId: String,
  private val userDisplayName: String,
  private val userEmail: String?,
  private val libraryRepository: MelodyLibraryRepository,
  private val prefsStore: UserPreferencesStore,
  private val playbackController: PlaybackController,
  private val catalogRepository: CatalogRepository = CatalogRepository(),
) : ViewModel() {

  val youTubeBridge: YouTubePlayerBridge
    get() = playbackController.youTubeBridge

  val playbackSession: StateFlow<PlaybackSession> =
    playbackController.session.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = PlaybackSession(),
    )

  private val _uiState =
    MutableStateFlow(
      MelodyUiState(
        featuredTracks = catalogRepository.starterTracks,
        jumpBackInTracks = catalogRepository.starterTracks.take(4),
        discoveryCollections =
          catalogRepository.buildDiscoveryCollections(catalogRepository.starterTracks),
        moodCards = catalogRepository.moodCards,
      )
    )
  val uiState: StateFlow<MelodyUiState> = _uiState.asStateFlow()

  private var knownTracksById: Map<String, Track> =
    catalogRepository.starterTracks.associateBy { it.id }
  private var searchDebounceJob: Job? = null
  private var activePlaylistTracksJob: Job? = null

  init {
    viewModelScope.launch {
      libraryRepository.ensureUserProfile(userDisplayName, userEmail).onFailure { err ->
        _uiState.update { it.copy(cloudSyncError = err.localizedMessage ?: "Failed to sync profile") }
      }
    }
    observeCloudLibrary()
    viewModelScope.launch {
      prefsStore.isGridViewFlow.collectLatest { isGrid ->
        _uiState.update { it.copy(isLibraryGridView = isGrid) }
      }
    }
    viewModelScope.launch {
      prefsStore.recentSearchesFlow.collectLatest { recents ->
        _uiState.update { it.copy(recentSearches = recents) }
      }
    }
    refreshHomeCatalog()
  }

  private fun observeCloudLibrary() {
    viewModelScope.launch {
      libraryRepository
        .observeLikedTracks(currentUserId)
        .catch { error ->
          val rootError = unwrapFirestoreErrorOrThrow(error)
          Log.w(TAG, "Error observing liked tracks", rootError)
          _uiState.update { it.copy(cloudSyncError = rootError.message ?: "Failed to load liked songs") }
        }
        .collectLatest { liked ->
          knownTracksById = knownTracksById + liked.associateBy { it.id }
          _uiState.update { state ->
            val updatedDetail =
              if (state.selectedCollection?.id == "col_liked_songs") {
                state.selectedCollection.copy(
                  tracks = liked,
                  subtitle = "${liked.size} saved tracks",
                )
              } else {
                state.selectedCollection
              }
            state.copy(likedTracks = liked, selectedCollection = updatedDetail)
          }
        }
    }

    viewModelScope.launch {
      libraryRepository
        .observePlaylists(currentUserId)
        .catch { error ->
          val rootError = unwrapFirestoreErrorOrThrow(error)
          Log.w(TAG, "Error observing playlists", rootError)
          _uiState.update { it.copy(cloudSyncError = rootError.message ?: "Failed to load playlists") }
        }
        .collectLatest { playlists ->
          _uiState.update { state ->
            val activeDetail = state.selectedCollection
            val updatedDetail =
              if (activeDetail != null && activeDetail.isUserPlaylist) {
                val matching = playlists.firstOrNull { it.id == activeDetail.id }
                if (matching != null) {
                  activeDetail.copy(
                    title = matching.title,
                    subtitle = "By ${matching.creator} · ${matching.trackCount} tracks",
                  )
                } else activeDetail
              } else activeDetail
            state.copy(playlists = playlists, selectedCollection = updatedDetail)
          }
        }
    }

    viewModelScope.launch {
      libraryRepository
        .observeHistoryTracks(currentUserId)
        .catch { error ->
          val rootError = unwrapFirestoreErrorOrThrow(error)
          Log.w(TAG, "Error observing listening history", rootError)
        }
        .collectLatest { history ->
          if (history.isNotEmpty()) {
            knownTracksById = knownTracksById + history.associateBy { it.id }
            _uiState.update { it.copy(jumpBackInTracks = history.take(6)) }
          }
        }
    }
  }

  private fun unwrapFirestoreErrorOrThrow(error: Throwable): Throwable {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    return firestoreError ?: error
  }

  fun clearCloudSyncError() {
    _uiState.update { it.copy(cloudSyncError = null) }
  }

  fun greetingResKey(): Int {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
      hour < 12 -> R.string.greeting_morning
      hour < 18 -> R.string.greeting_afternoon
      else -> R.string.greeting_evening
    }
  }

  fun selectTab(tab: AppTab) {
    activePlaylistTracksJob?.cancel()
    _uiState.update {
      it.copy(
        activeTab = tab,
        selectedCollection = null,
        isNowPlayingExpanded = false,
        isFullscreenVideo = false,
      )
    }
  }

  fun selectHomeFilter(filter: HomeFilter) {
    _uiState.update { it.copy(homeFilter = filter) }
  }

  fun refreshHomeCatalog() {
    viewModelScope.launch {
      _uiState.update { it.copy(isHomeLoading = true, homeOfflineNotice = null, catalogWarning = null) }
      val result = catalogRepository.fetchFeaturedCatalog()
      result
        .onSuccess { batch ->
          val remoteTracks = batch.tracks
          val noticeBadge =
            when {
              batch.source == CatalogSource.YouTube -> null
              !catalogRepository.isYouTubeConfigured -> "ADD YT KEY"
              else -> "YT FALLBACK"
            }
          val warning =
            when {
              batch.source == CatalogSource.YouTube -> null
              !catalogRepository.isYouTubeConfigured ->
                "30s previews active. Add YOUTUBE_API_KEY in AI Studio Secrets for full-length songs."
              else ->
                batch.warningMessage?.let { "YouTube API error (30s previews active): $it" }
                  ?: "YouTube catalog unavailable; playing 30s previews."
            }
          if (remoteTracks.isNotEmpty()) {
            knownTracksById = knownTracksById + remoteTracks.associateBy { it.id }
            _uiState.update { state ->
              state.copy(
                isHomeLoading = false,
                activeCatalogSource = batch.source,
                homeOfflineNotice = noticeBadge,
                catalogWarning = warning,
                featuredTracks = remoteTracks,
                jumpBackInTracks =
                  if (state.jumpBackInTracks.all { it.id.startsWith("seed_") }) {
                    remoteTracks.take(4)
                  } else {
                    state.jumpBackInTracks
                  },
                discoveryCollections = catalogRepository.buildDiscoveryCollections(remoteTracks),
              )
            }
          } else {
            _uiState.update {
              it.copy(
                isHomeLoading = false,
                activeCatalogSource = batch.source,
                homeOfflineNotice = noticeBadge,
                catalogWarning = warning,
              )
            }
          }
        }
        .onFailure {
          _uiState.update {
            it.copy(
              isHomeLoading = false,
              activeCatalogSource = CatalogSource.OfflineStarter,
              homeOfflineNotice = "OFFLINE STARTER",
              catalogWarning = "Offline starter catalog active. Check internet connection.",
            )
          }
        }
    }
  }

  fun updateSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
    searchDebounceJob?.cancel()
    if (query.isBlank()) {
      _uiState.update { it.copy(searchState = SearchResultState.Idle) }
      return
    }
    searchDebounceJob =
      viewModelScope.launch {
        delay(550L)
        executeSearch(query, recordRecent = false)
      }
  }

  fun submitSearch(query: String = _uiState.value.searchQuery) {
    searchDebounceJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      _uiState.update { it.copy(searchState = SearchResultState.Idle) }
      return
    }
    _uiState.update { it.copy(searchQuery = trimmed) }
    viewModelScope.launch { executeSearch(trimmed, recordRecent = true) }
  }

  fun runMoodSearch(mood: MoodCard) {
    _uiState.update { it.copy(searchQuery = mood.title) }
    viewModelScope.launch { executeSearch(mood.query, recordRecent = true) }
  }

  fun removeRecentSearch(query: String) {
    viewModelScope.launch { prefsStore.removeRecentSearch(query) }
  }

  fun clearAllRecentSearches() {
    viewModelScope.launch { prefsStore.clearRecentSearches() }
  }

  private suspend fun executeSearch(query: String, recordRecent: Boolean) {
    _uiState.update { it.copy(searchState = SearchResultState.Loading) }
    if (recordRecent) {
      prefsStore.addRecentSearch(query)
    }
    val remoteResult = catalogRepository.searchCatalog(query, limit = 18)
    remoteResult
      .onSuccess { batch ->
        val tracks = batch.tracks
        knownTracksById = knownTracksById + tracks.associateBy { it.id }
        _uiState.update {
          it.copy(
            activeCatalogSource =
              if (batch.source == CatalogSource.YouTube) CatalogSource.YouTube
              else it.activeCatalogSource,
            searchState =
              if (tracks.isEmpty()) SearchResultState.Empty
              else
                SearchResultState.Success(
                  tracks = tracks,
                  fromFallback = batch.source != CatalogSource.YouTube && catalogRepository.isYouTubeConfigured,
                ),
          )
        }
      }
      .onFailure {
        val localMatches =
          knownTracksById.values.filter {
            it.title.contains(query, ignoreCase = true) ||
              it.artist.contains(query, ignoreCase = true) ||
              it.genre.contains(query, ignoreCase = true)
          }
        _uiState.update {
          it.copy(
            searchState =
              if (localMatches.isNotEmpty()) {
                SearchResultState.Success(tracks = localMatches, fromFallback = true)
              } else {
                SearchResultState.Error("Could not reach catalog. Check connection and retry.")
              }
          )
        }
      }
  }

  fun playTrack(track: Track, contextList: List<Track> = emptyList()) {
    knownTracksById = knownTracksById + (track.id to track)
    _uiState.update { state ->
      val updatedRecent =
        (listOf(track) + state.jumpBackInTracks.filterNot { it.id == track.id }).take(6)
      state.copy(jumpBackInTracks = updatedRecent)
    }
    playbackController.playTrack(track, contextList)
    viewModelScope.launch { libraryRepository.recordListeningHistory(track) }
  }

  fun playCollection(tracks: List<Track>, startIndex: Int = 0) {
    if (tracks.isEmpty()) return
    knownTracksById = knownTracksById + tracks.associateBy { it.id }
    val first = tracks.getOrNull(startIndex) ?: tracks.first()
    _uiState.update { state ->
      val updatedRecent =
        (listOf(first) + state.jumpBackInTracks.filterNot { it.id == first.id }).take(6)
      state.copy(jumpBackInTracks = updatedRecent)
    }
    playbackController.playQueue(tracks, startIndex)
    viewModelScope.launch { libraryRepository.recordListeningHistory(first) }
  }

  fun togglePlayPause() = playbackController.togglePlayPause()

  fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

  fun skipNext() = playbackController.skipNext()

  fun skipPrevious() = playbackController.skipPrevious()

  fun toggleShuffle() = playbackController.toggleShuffle()

  fun cycleRepeatMode() = playbackController.cycleRepeatMode()

  fun removeFromQueue(index: Int) = playbackController.removeFromQueue(index)

  fun moveQueueItem(fromIndex: Int, toIndex: Int) =
    playbackController.moveQueueItem(fromIndex, toIndex)

  fun toggleLike(track: Track) {
    knownTracksById = knownTracksById + (track.id to track)
    val previousLiked = _uiState.value.likedTracks
    val alreadyLiked = previousLiked.any { it.id == track.id }
    val optimistic =
      if (alreadyLiked) previousLiked.filterNot { it.id == track.id }
      else listOf(track) + previousLiked
    _uiState.update { it.copy(likedTracks = optimistic) }

    viewModelScope.launch {
      libraryRepository.setTrackLiked(track, liked = !alreadyLiked).onFailure { err ->
        _uiState.update {
          it.copy(
            likedTracks = previousLiked,
            cloudSyncError = err.localizedMessage ?: "Failed to update liked songs",
          )
        }
      }
    }
  }

  fun selectLibraryCategory(category: LibraryCategory) {
    _uiState.update { it.copy(libraryCategory = category) }
  }

  fun toggleLibrarySortOrder() {
    _uiState.update {
      val next =
        if (it.librarySortOrder == LibrarySortOrder.Recent) LibrarySortOrder.Alphabetical
        else LibrarySortOrder.Recent
      it.copy(librarySortOrder = next)
    }
  }

  fun updateLibrarySearchQuery(query: String) {
    _uiState.update { it.copy(librarySearchQuery = query) }
  }

  fun toggleLibraryGridView() {
    val next = !_uiState.value.isLibraryGridView
    viewModelScope.launch { prefsStore.setGridView(next) }
  }

  fun togglePinLikedSongs() {
    _uiState.update { it.copy(pinnedLikedSongs = !it.pinnedLikedSongs) }
  }

  fun togglePinPlaylist(playlistId: String) {
    val playlist = _uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
    viewModelScope.launch {
      libraryRepository.togglePlaylistPinned(playlistId, playlist.isPinned).onFailure { err ->
        _uiState.update { it.copy(cloudSyncError = err.localizedMessage ?: "Failed to pin playlist") }
      }
    }
  }

  fun setShowCreatePlaylistDialog(show: Boolean) {
    _uiState.update { it.copy(showCreatePlaylistDialog = show) }
  }

  fun createPlaylist(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    _uiState.update { it.copy(showCreatePlaylistDialog = false) }
    viewModelScope.launch {
      libraryRepository.createPlaylist(trimmed, userDisplayName).onFailure { err ->
        _uiState.update {
          it.copy(cloudSyncError = err.localizedMessage ?: "Failed to create playlist")
        }
      }
    }
  }

  fun openAddToPlaylistDialog(track: Track?) {
    _uiState.update { it.copy(trackForPlaylistDialog = track) }
  }

  fun addTrackToPlaylist(track: Track, playlistId: String) {
    knownTracksById = knownTracksById + (track.id to track)
    _uiState.update { it.copy(trackForPlaylistDialog = null) }
    viewModelScope.launch {
      libraryRepository.addTrackToPlaylist(playlistId, track).onFailure { err ->
        _uiState.update {
          it.copy(cloudSyncError = err.localizedMessage ?: "Failed to add track to playlist")
        }
      }
    }
  }

  fun openDiscoveryCollection(collection: DiscoveryCollection) {
    activePlaylistTracksJob?.cancel()
    knownTracksById = knownTracksById + collection.tracks.associateBy { it.id }
    _uiState.update {
      it.copy(
        selectedCollection =
          CollectionDetailState(
            id = collection.id,
            title = collection.title,
            subtitle = collection.subtitle,
            eyebrow = collection.eyebrow,
            artRes = collection.artRes,
            tracks = collection.tracks,
            isUserPlaylist = false,
          )
      )
    }
  }

  fun openLikedSongsDetail() {
    activePlaylistTracksJob?.cancel()
    val liked = _uiState.value.likedTracks
    _uiState.update {
      it.copy(
        selectedCollection =
          CollectionDetailState(
            id = "col_liked_songs",
            title = "Liked Songs",
            subtitle = "${liked.size} saved tracks",
            eyebrow = "YOUR COLLECTION",
            artRes = R.drawable.img_art_daily_discovery,
            tracks = liked,
            isUserPlaylist = false,
          )
      )
    }
  }

  fun openPlaylistDetail(playlist: Playlist) {
    activePlaylistTracksJob?.cancel()
    _uiState.update {
      it.copy(
        selectedCollection =
          CollectionDetailState(
            id = playlist.id,
            title = playlist.title,
            subtitle = "By ${playlist.creator} · ${playlist.trackCount} tracks",
            eyebrow = "PLAYLIST",
            artRes = playlist.artRes ?: R.drawable.img_art_lofi_chill,
            tracks = emptyList(),
            isUserPlaylist = true,
          )
      )
    }
    activePlaylistTracksJob =
      viewModelScope.launch {
        libraryRepository
          .observePlaylistTracks(currentUserId, playlist.id)
          .catch { error ->
            val rootError = unwrapFirestoreErrorOrThrow(error)
            Log.w(TAG, "Error observing playlist tracks", rootError)
            _uiState.update {
              it.copy(cloudSyncError = rootError.message ?: "Failed to load playlist tracks")
            }
          }
          .collectLatest { tracks ->
            knownTracksById = knownTracksById + tracks.associateBy { it.id }
            _uiState.update { state ->
              val currentDetail = state.selectedCollection
              if (currentDetail?.id == playlist.id) {
                state.copy(
                  selectedCollection =
                    currentDetail.copy(
                      tracks = tracks,
                      subtitle = "By ${playlist.creator} · ${tracks.size} tracks",
                    )
                )
              } else {
                state
              }
            }
          }
      }
  }

  fun closeCollectionDetail() {
    activePlaylistTracksJob?.cancel()
    _uiState.update { it.copy(selectedCollection = null) }
  }

  fun setNowPlayingExpanded(expanded: Boolean) {
    _uiState.update {
      it.copy(
        isNowPlayingExpanded = expanded,
        isFullscreenVideo = if (!expanded) false else it.isFullscreenVideo,
        isQueueSheetOpen = if (!expanded) false else it.isQueueSheetOpen,
      )
    }
  }

  fun setYouTubeVideoMode(enabled: Boolean) {
    _uiState.update {
      it.copy(
        isYouTubeVideoMode = enabled,
        isFullscreenVideo = if (!enabled) false else it.isFullscreenVideo,
      )
    }
  }

  fun setFullscreenVideo(enabled: Boolean) {
    _uiState.update {
      it.copy(
        isFullscreenVideo = enabled,
        isYouTubeVideoMode = if (enabled) true else it.isYouTubeVideoMode,
      )
    }
  }

  fun setQueueSheetOpen(open: Boolean) {
    _uiState.update { it.copy(isQueueSheetOpen = open) }
  }

  fun resumeFromBackground() = playbackController.resumeFromBackground()

  private companion object {
    const val TAG = "MelodyVM"
  }
}
