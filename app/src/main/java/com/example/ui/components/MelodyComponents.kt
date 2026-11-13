package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CatalogSource
import com.example.model.AppTab
import com.example.model.DiscoveryCollection
import com.example.model.PlaybackSession
import com.example.model.PlaybackStatus
import com.example.model.Playlist
import com.example.model.Track

@Composable
fun ArtworkImage(
  artworkUrl: String?,
  @DrawableRes fallbackRes: Int?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 12.dp,
) {
  val shape = RoundedCornerShape(cornerRadius)
  Box(
    modifier =
      modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    if (!artworkUrl.isNullOrBlank()) {
      AsyncImage(
        model = artworkUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = fallbackRes?.let { painterResource(it) },
        error = fallbackRes?.let { painterResource(it) },
        modifier = Modifier.fillMaxSize(),
      )
    } else if (fallbackRes != null) {
      Image(
        painter = painterResource(id = fallbackRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp),
      )
    }
  }
}

@Composable
fun MelodyTopBar(
  catalogSource: CatalogSource,
  offlineNotice: String?,
  userInitial: String,
  userDisplayName: String,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var accountMenuExpanded by remember { mutableStateOf(false) }

  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(32.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.primary),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "M",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
    Spacer(modifier = Modifier.width(10.dp))
    Text(
      text = stringResource(R.string.brand_wordmark),
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.weight(1f))
    val badgeText =
      when {
        offlineNotice != null -> offlineNotice
        catalogSource == CatalogSource.YouTube -> stringResource(R.string.badge_youtube_music)
        else -> stringResource(R.string.badge_preview_catalog)
      }
    Surface(
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      border =
        BorderStroke(
          1.dp,
          if (catalogSource == CatalogSource.YouTube && offlineNotice == null) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
          } else {
            MaterialTheme.colorScheme.outline
          },
        ),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (catalogSource == CatalogSource.YouTube && offlineNotice == null) {
          Box(
            modifier =
              Modifier.size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
          )
        }
        Text(
          text = badgeText,
          style = MaterialTheme.typography.labelSmall,
          color =
            when {
              offlineNotice != null -> MaterialTheme.colorScheme.error
              catalogSource == CatalogSource.YouTube -> MaterialTheme.colorScheme.primary
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Spacer(modifier = Modifier.width(8.dp))
    Box {
      Surface(
        onClick = { accountMenuExpanded = true },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(40.dp).testTag("account_menu_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = userInitial,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      DropdownMenu(
        expanded = accountMenuExpanded,
        onDismissRequest = { accountMenuExpanded = false },
      ) {
        DropdownMenuItem(
          text = { Text(userDisplayName, style = MaterialTheme.typography.titleSmall) },
          onClick = { accountMenuExpanded = false },
          enabled = false,
        )
        DropdownMenuItem(
          text = { Text(stringResource(R.string.action_sign_out)) },
          onClick = {
            accountMenuExpanded = false
            onSignOut()
          },
          modifier = Modifier.testTag("sign_out_menu_item"),
        )
      }
    }
  }
}

@Composable
fun MelodyFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outline,
      ),
    modifier = modifier.minimumInteractiveComponentSize(),
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun TrackRow(
  track: Track,
  isPlayingCurrent: Boolean,
  isLiked: Boolean,
  onPlay: () -> Unit,
  onToggleLike: () -> Unit,
  onAddToPlaylist: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color =
      if (isPlayingCurrent) MaterialTheme.colorScheme.surfaceVariant
      else MaterialTheme.colorScheme.background,
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onPlay)
        .testTag("track_row_${track.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ArtworkImage(
        artworkUrl = track.artworkUrl,
        fallbackRes = track.fallbackArtRes,
        contentDescription = stringResource(R.string.cd_album_art, track.title),
        modifier = Modifier.size(52.dp),
        cornerRadius = 10.dp,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.title,
          style = MaterialTheme.typography.titleMedium,
          color =
            if (isPlayingCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (track.isYouTubeTrack) {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
              modifier = Modifier.padding(end = 6.dp),
            ) {
              Text(
                text = "YT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
              )
            }
          }
          Text(
            text = "${track.artist} · ${formatDuration(track.durationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      IconButton(
        onClick = onToggleLike,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("like_btn_${track.id}"),
      ) {
        Icon(
          imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          contentDescription =
            stringResource(if (isLiked) R.string.cd_unlike else R.string.cd_like),
          tint =
            if (isLiked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("track_more_${track.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.cd_track_options),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.action_add_to_playlist)) },
            leadingIcon = {
              Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
            },
            onClick = {
              menuExpanded = false
              onAddToPlaylist()
            },
          )
        }
      }
    }
  }
}

@Composable
fun CollectionCard(
  collection: DiscoveryCollection,
  onOpen: () -> Unit,
  onPlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onOpen,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.testTag("collection_card_${collection.id}"),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Box(modifier = Modifier.fillMaxWidth().height(136.dp)) {
        ArtworkImage(
          artworkUrl = null,
          fallbackRes = collection.artRes,
          contentDescription = stringResource(R.string.cd_album_art, collection.title),
          modifier = Modifier.fillMaxSize(),
          cornerRadius = 14.dp,
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .clip(RoundedCornerShape(14.dp))
              .background(
                Brush.verticalGradient(
                  listOf(
                    MaterialTheme.colorScheme.background.copy(alpha = 0.05f),
                    MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                  )
                )
              )
        )
        Surface(
          onClick = onPlay,
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(8.dp)
              .size(48.dp)
              .testTag("play_collection_${collection.id}"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = stringResource(R.string.cd_play),
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(26.dp),
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = collection.eyebrow,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = collection.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = collection.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun PersistentMiniPlayer(
  session: PlaybackSession,
  isLiked: Boolean,
  onExpand: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onToggleLike: () -> Unit,
  onSkipNext: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val track = session.currentTrack ?: return

  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 4.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onExpand)
        .testTag("mini_player"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      LinearProgressIndicator(
        progress = { session.progressFraction },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outline,
        modifier = Modifier.fillMaxWidth().height(2.5.dp),
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ArtworkImage(
          artworkUrl = track.artworkUrl,
          fallbackRes = track.fallbackArtRes,
          contentDescription = stringResource(R.string.cd_album_art, track.title),
          modifier = Modifier.size(44.dp),
          cornerRadius = 10.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              when (session.status) {
                PlaybackStatus.Loading,
                PlaybackStatus.Buffering -> "Connecting audio…"
                PlaybackStatus.Failed -> session.errorMessage ?: "Stream unavailable"
                else -> track.artist
              },
            style = MaterialTheme.typography.bodySmall,
            color =
              if (session.status == PlaybackStatus.Failed) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = onToggleLike,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_player_like_button"),
        ) {
          Icon(
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription =
              stringResource(if (isLiked) R.string.cd_unlike else R.string.cd_like),
            tint =
              if (isLiked) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Surface(
          onClick = onTogglePlayPause,
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(48.dp).testTag("mini_player_play_pause_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (
              session.status == PlaybackStatus.Loading || session.status == PlaybackStatus.Buffering
            ) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
              )
            } else {
              Icon(
                imageVector =
                  if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription =
                  stringResource(if (session.isPlaying) R.string.cd_pause else R.string.cd_play),
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }
        }
        IconButton(
          onClick = onSkipNext,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_player_next_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
fun MelodyBottomNav(
  activeTab: AppTab,
  onSelectTab: (AppTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.background,
    contentColor = MaterialTheme.colorScheme.onBackground,
    windowInsets = WindowInsets.navigationBars,
    modifier = modifier.border(width = 1.dp, color = MaterialTheme.colorScheme.outline),
  ) {
    AppTab.entries.forEach { tab ->
      val selected = tab == activeTab
      NavigationBarItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        icon = {
          Icon(
            imageVector = tabIcon(tab, selected),
            contentDescription = tabLabel(tab),
          )
        },
        label = { Text(text = tabLabel(tab), style = MaterialTheme.typography.labelLarge) },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
      )
    }
  }
}

@Composable
fun MelodyNavRail(
  activeTab: AppTab,
  onSelectTab: (AppTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavigationRail(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
  ) {
    Spacer(modifier = Modifier.height(16.dp))
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.primary),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "M",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
    Spacer(modifier = Modifier.height(24.dp))
    AppTab.entries.forEach { tab ->
      val selected = tab == activeTab
      NavigationRailItem(
        selected = selected,
        onClick = { onSelectTab(tab) },
        icon = { Icon(imageVector = tabIcon(tab, selected), contentDescription = tabLabel(tab)) },
        label = { Text(text = tabLabel(tab), style = MaterialTheme.typography.labelLarge) },
        colors =
          NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
      )
    }
  }
}

@Composable
fun CreatePlaylistDialog(
  onDismiss: () -> Unit,
  onCreate: (String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var showError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    title = { Text(stringResource(R.string.create_playlist_title)) },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            if (showError && it.isNotBlank()) showError = false
          },
          singleLine = true,
          label = { Text(stringResource(R.string.create_playlist_hint)) },
          isError = showError,
          supportingText =
            if (showError) {
              { Text(stringResource(R.string.create_playlist_error_empty)) }
            } else null,
          modifier = Modifier.fillMaxWidth().testTag("playlist_name_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.trim().isEmpty()) {
            showError = true
          } else {
            onCreate(name)
          }
        },
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.testTag("confirm_create_playlist_button"),
      ) {
        Text(stringResource(R.string.create_playlist_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
fun AddToPlaylistDialog(
  track: Track,
  playlists: List<Playlist>,
  onDismiss: () -> Unit,
  onSelectPlaylist: (Playlist) -> Unit,
  onCreateNewPlaylist: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    title = { Text(stringResource(R.string.action_add_to_playlist)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "${track.title} · ${track.artist}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        playlists.forEach { playlist ->
          Surface(
            onClick = { onSelectPlaylist(playlist) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
              Text(
                text = "${playlist.trackCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onCreateNewPlaylist) {
        Text(stringResource(R.string.library_new_playlist))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
fun EmptyOrErrorCard(
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(34.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Button(
          onClick = onAction,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

@Composable
private fun tabLabel(tab: AppTab): String =
  when (tab) {
    AppTab.Home -> stringResource(R.string.nav_home)
    AppTab.Search -> stringResource(R.string.nav_search)
    AppTab.Library -> stringResource(R.string.nav_library)
  }

private fun tabIcon(tab: AppTab, selected: Boolean) =
  when (tab) {
    AppTab.Home -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    AppTab.Search -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
    AppTab.Library -> if (selected) Icons.Rounded.LibraryMusic else Icons.Outlined.LibraryMusic
  }

fun formatDuration(ms: Long): String {
  val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return "%d:%02d".format(minutes, seconds)
}
