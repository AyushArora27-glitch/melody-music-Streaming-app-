package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LibraryCategory
import com.example.model.LibrarySortOrder
import com.example.model.Playlist
import com.example.model.Track
import com.example.model.ViewportClass
import com.example.ui.CollectionDetailState
import com.example.ui.MelodyUiState
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EmptyOrErrorCard
import com.example.ui.components.MelodyFilterChip
import com.example.ui.components.TrackRow

@Composable
fun LibraryScreen(
  uiState: MelodyUiState,
  viewportClass: ViewportClass,
  onSelectCategory: (LibraryCategory) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleSort: () -> Unit,
  onToggleGridView: () -> Unit,
  onNewPlaylist: () -> Unit,
  onOpenLikedSongs: () -> Unit,
  onTogglePinLiked: () -> Unit,
  onOpenPlaylist: (Playlist) -> Unit,
  onTogglePinPlaylist: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val filteredPlaylists =
    remember(uiState.playlists, uiState.librarySearchQuery, uiState.librarySortOrder) {
      val query = uiState.librarySearchQuery.trim()
      val base =
        if (query.isEmpty()) uiState.playlists
        else uiState.playlists.filter { it.title.contains(query, ignoreCase = true) }
      val sorted =
        when (uiState.librarySortOrder) {
          LibrarySortOrder.Recent -> base.sortedByDescending { it.createdAtMs }
          LibrarySortOrder.Alphabetical -> base.sortedBy { it.title.lowercase() }
        }
      sorted.sortedByDescending { it.isPinned }
    }

  val gridColumns =
    when (viewportClass) {
      ViewportClass.Compact -> 2
      ViewportClass.Medium -> 3
      ViewportClass.Expanded -> 4
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("library_screen"),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column {
        Text(
          text = stringResource(R.string.tagline_eyebrow),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.library_heading),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
          )
          Button(
            onClick = onNewPlaylist,
            shape = RoundedCornerShape(999.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("new_playlist_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.library_new_playlist),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LibraryCategory.entries, key = { it.name }) { category ->
          MelodyFilterChip(
            label = category.label,
            selected = uiState.libraryCategory == category,
            onClick = { onSelectCategory(category) },
            modifier = Modifier.testTag("library_chip_${category.name.lowercase()}"),
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = uiState.librarySearchQuery,
          onValueChange = onSearchQueryChange,
          singleLine = true,
          placeholder = { Text(stringResource(R.string.library_search_hint)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          trailingIcon = {
            if (uiState.librarySearchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchQueryChange("") },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_clear_search),
                )
              }
            }
          },
          shape = RoundedCornerShape(14.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
          modifier = Modifier.weight(1f).testTag("library_search_input"),
        )
        OutlinedButton(
          onClick = onToggleSort,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("library_sort_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Sort,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              stringResource(
                if (uiState.librarySortOrder == LibrarySortOrder.Recent) R.string.library_sort_recent
                else R.string.library_sort_alpha
              ),
            style = MaterialTheme.typography.labelLarge,
          )
        }
        IconButton(
          onClick = onToggleGridView,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("library_view_toggle"),
        ) {
          Icon(
            imageVector =
              if (uiState.isLibraryGridView) Icons.AutoMirrored.Rounded.ViewList
              else Icons.Rounded.GridView,
            contentDescription = stringResource(R.string.cd_toggle_view),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    when (uiState.libraryCategory) {
      LibraryCategory.Playlists -> {
        item {
          LikedSongsCollectionItem(
            likedCount = uiState.likedTracks.size,
            isPinned = uiState.pinnedLikedSongs,
            isGrid = uiState.isLibraryGridView,
            onOpen = onOpenLikedSongs,
            onTogglePin = onTogglePinLiked,
          )
        }

        if (filteredPlaylists.isEmpty()) {
          item {
            EmptyOrErrorCard(
              title = stringResource(R.string.library_empty_title),
              subtitle = stringResource(R.string.library_empty_subtitle),
              actionLabel = stringResource(R.string.library_new_playlist),
              onAction = onNewPlaylist,
            )
          }
        } else if (!uiState.isLibraryGridView) {
          items(filteredPlaylists, key = { it.id }) { playlist ->
            PlaylistListItem(
              playlist = playlist,
              onOpen = { onOpenPlaylist(playlist) },
              onTogglePin = { onTogglePinPlaylist(playlist.id) },
            )
          }
        } else {
          val rows = filteredPlaylists.chunked(gridColumns)
          items(rows, key = { r -> r.joinToString("_") { it.id } }) { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              rowItems.forEach { playlist ->
                PlaylistGridItem(
                  playlist = playlist,
                  onOpen = { onOpenPlaylist(playlist) },
                  onTogglePin = { onTogglePinPlaylist(playlist.id) },
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(gridColumns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
          }
        }
      }
      LibraryCategory.Artists -> {
        val artists =
          (uiState.likedTracks + uiState.featuredTracks)
            .map { it.artist }
            .distinct()
            .filter {
              uiState.librarySearchQuery.isBlank() ||
                it.contains(uiState.librarySearchQuery, ignoreCase = true)
            }
        items(artists, key = { "artist_$it" }) { artistName ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = artistName.take(1).uppercase(),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = artistName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
      LibraryCategory.Albums -> {
        val albums =
          (uiState.likedTracks + uiState.featuredTracks)
            .distinctBy { it.album }
            .filter {
              uiState.librarySearchQuery.isBlank() ||
                it.album.contains(uiState.librarySearchQuery, ignoreCase = true)
            }
        items(albums, key = { "album_${it.id}" }) { track ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              ArtworkImage(
                artworkUrl = track.artworkUrl,
                fallbackRes = track.fallbackArtRes,
                contentDescription = track.album,
                modifier = Modifier.size(52.dp),
                cornerRadius = 10.dp,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = track.album,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = track.artist,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
      LibraryCategory.Downloaded -> {
        item {
          EmptyOrErrorCard(
            title = "Offline downloads",
            subtitle =
              "Downloaded tracks and storage management require licensed offline playback rights and are scheduled for a later phase.",
          )
        }
      }
    }
  }
}

@Composable
private fun LikedSongsCollectionItem(
  likedCount: Int,
  isPinned: Boolean,
  isGrid: Boolean,
  onOpen: () -> Unit,
  onTogglePin: () -> Unit,
) {
  Surface(
    onClick = onOpen,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    modifier = Modifier.fillMaxWidth().testTag("liked_songs_collection_card"),
  ) {
    Row(
      modifier = Modifier.padding(if (isGrid) 16.dp else 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Favorite,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(26.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.library_liked_songs),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text =
            (if (isPinned) "Pinned · " else "") + "$likedCount tracks",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
      }
      IconButton(
        onClick = onTogglePin,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Rounded.PushPin,
          contentDescription =
            stringResource(if (isPinned) R.string.action_unpin else R.string.action_pin),
          tint =
            if (isPinned) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun PlaylistListItem(
  playlist: Playlist,
  onOpen: () -> Unit,
  onTogglePin: () -> Unit,
) {
  Surface(
    onClick = onOpen,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = Modifier.fillMaxWidth().testTag("playlist_item_${playlist.id}"),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ArtworkImage(
        artworkUrl = null,
        fallbackRes = playlist.artRes ?: R.drawable.img_art_lofi_chill,
        contentDescription = playlist.title,
        modifier = Modifier.size(52.dp),
        cornerRadius = 10.dp,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = playlist.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            (if (playlist.isPinned) "Pinned · " else "") +
              "${playlist.creator} · ${playlist.trackCount} tracks",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onTogglePin,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Rounded.PushPin,
          contentDescription =
            stringResource(if (playlist.isPinned) R.string.action_unpin else R.string.action_pin),
          tint =
            if (playlist.isPinned) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun PlaylistGridItem(
  playlist: Playlist,
  onOpen: () -> Unit,
  onTogglePin: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onOpen,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.testTag("playlist_grid_${playlist.id}"),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      ArtworkImage(
        artworkUrl = null,
        fallbackRes = playlist.artRes ?: R.drawable.img_art_lofi_chill,
        contentDescription = playlist.title,
        modifier = Modifier.fillMaxWidth().height(110.dp),
        cornerRadius = 12.dp,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${playlist.trackCount} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onTogglePin,
          modifier = Modifier.size(36.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription =
              stringResource(if (playlist.isPinned) R.string.action_unpin else R.string.action_pin),
            tint =
              if (playlist.isPinned) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
fun CollectionDetailScreen(
  detail: CollectionDetailState,
  currentTrackId: String?,
  likedTrackIds: Set<String>,
  onBack: () -> Unit,
  onPlayAll: () -> Unit,
  onPlayTrack: (Track, List<Track>) -> Unit,
  onToggleLike: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("collection_detail_screen"),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("collection_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            tint = MaterialTheme.colorScheme.onBackground,
          )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = detail.eyebrow,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
    }

    item {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ArtworkImage(
            artworkUrl = detail.tracks.firstOrNull()?.artworkUrl,
            fallbackRes = detail.artRes ?: R.drawable.img_art_daily_discovery,
            contentDescription = detail.title,
            modifier = Modifier.size(96.dp),
            cornerRadius = 14.dp,
          )
          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = detail.title,
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = detail.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (detail.tracks.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              Button(
                onClick = onPlayAll,
                shape = RoundedCornerShape(999.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("collection_play_all"),
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.cd_play))
              }
            }
          }
        }
      }
    }

    if (detail.tracks.isEmpty()) {
      item {
        EmptyOrErrorCard(
          title = stringResource(R.string.library_empty_title),
          subtitle = stringResource(R.string.library_empty_subtitle),
        )
      }
    } else {
      items(detail.tracks, key = { "detail_${it.id}" }) { track ->
        TrackRow(
          track = track,
          isPlayingCurrent = track.id == currentTrackId,
          isLiked = likedTrackIds.contains(track.id),
          onPlay = { onPlayTrack(track, detail.tracks) },
          onToggleLike = { onToggleLike(track) },
          onAddToPlaylist = { onAddToPlaylist(track) },
        )
      }
    }
  }
}
