package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DiscoveryCollection
import com.example.model.HomeFilter
import com.example.model.Track
import com.example.model.ViewportClass
import com.example.ui.MelodyUiState
import com.example.ui.components.ArtworkImage
import com.example.ui.components.CollectionCard
import com.example.ui.components.MelodyFilterChip
import com.example.ui.components.TrackRow

@Composable
fun HomeScreen(
  uiState: MelodyUiState,
  greetingResId: Int,
  currentTrackId: String?,
  viewportClass: ViewportClass,
  onSelectFilter: (HomeFilter) -> Unit,
  onPlayMix: () -> Unit,
  onPlayTrack: (Track, List<Track>) -> Unit,
  onOpenCollection: (DiscoveryCollection) -> Unit,
  onPlayCollection: (DiscoveryCollection) -> Unit,
  onToggleLike: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val filteredCollections =
    remember(uiState.discoveryCollections, uiState.homeFilter) {
      if (uiState.homeFilter == HomeFilter.All) uiState.discoveryCollections
      else uiState.discoveryCollections.filter { it.filterTag == uiState.homeFilter }
    }

  val columns =
    when (viewportClass) {
      ViewportClass.Compact -> 1
      ViewportClass.Medium -> 2
      ViewportClass.Expanded -> 2
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("home_screen"),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
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
            text = stringResource(greetingResId),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = onRefresh,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("home_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = stringResource(R.string.search_retry),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (uiState.isHomeLoading) {
          Spacer(modifier = Modifier.height(8.dp))
          LinearProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth().height(2.dp),
          )
        }
        val catalogWarning = uiState.catalogWarning
        if (!uiState.isHomeLoading && !catalogWarning.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().testTag("catalog_warning_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = catalogWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Refresh,
                  contentDescription = stringResource(R.string.search_retry),
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HomeFilter.entries, key = { it.name }) { filter ->
          MelodyFilterChip(
            label = filter.label,
            selected = uiState.homeFilter == filter,
            onClick = { onSelectFilter(filter) },
            modifier = Modifier.testTag("home_chip_${filter.name.lowercase()}"),
          )
        }
      }
    }

    item {
      HeroMixBanner(
        onPlayMix = onPlayMix,
        isCompact = viewportClass == ViewportClass.Compact,
      )
    }

    item {
      DailyRotationCard(
        track = uiState.featuredTracks.firstOrNull(),
        onPlay = {
          uiState.featuredTracks.firstOrNull()?.let { first ->
            onPlayTrack(first, uiState.featuredTracks)
          }
        },
      )
    }

    if (uiState.jumpBackInTracks.isNotEmpty()) {
      item {
        Text(
          text = stringResource(R.string.section_jump_back_in),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(uiState.jumpBackInTracks, key = { "jump_${it.id}" }) { track ->
            JumpBackInCard(
              track = track,
              isPlayingCurrent = track.id == currentTrackId,
              onClick = { onPlayTrack(track, uiState.jumpBackInTracks) },
            )
          }
        }
      }
    }

    item {
      Text(
        text = stringResource(R.string.section_daily_discovery),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 6.dp),
      )
    }

    val chunkedCollections = filteredCollections.chunked(if (columns == 1) 2 else columns)
    items(chunkedCollections, key = { row -> row.joinToString("_") { it.id } }) { rowItems ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        rowItems.forEach { col ->
          CollectionCard(
            collection = col,
            onOpen = { onOpenCollection(col) },
            onPlay = { onPlayCollection(col) },
            modifier = Modifier.weight(1f),
          )
        }
        if (rowItems.size == 1) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }

    item {
      Text(
        text = stringResource(R.string.section_quick_picks),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 6.dp),
      )
    }

    if (columns == 1) {
      items(uiState.featuredTracks.take(8), key = { "qp_${it.id}" }) { track ->
        TrackRow(
          track = track,
          isPlayingCurrent = track.id == currentTrackId,
          isLiked = uiState.likedTracks.any { it.id == track.id },
          onPlay = { onPlayTrack(track, uiState.featuredTracks) },
          onToggleLike = { onToggleLike(track) },
          onAddToPlaylist = { onAddToPlaylist(track) },
        )
      }
    } else {
      val trackRows = uiState.featuredTracks.take(8).chunked(2)
      items(trackRows, key = { pair -> pair.joinToString("_") { it.id } }) { pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          pair.forEach { track ->
            TrackRow(
              track = track,
              isPlayingCurrent = track.id == currentTrackId,
              isLiked = uiState.likedTracks.any { it.id == track.id },
              onPlay = { onPlayTrack(track, uiState.featuredTracks) },
              onToggleLike = { onToggleLike(track) },
              onAddToPlaylist = { onAddToPlaylist(track) },
              modifier = Modifier.weight(1f),
            )
          }
          if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
      }
    }

    item {
      Text(
        text = stringResource(R.string.footer_motto),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
      )
    }
  }
}

@Composable
private fun HeroMixBanner(
  onPlayMix: () -> Unit,
  isCompact: Boolean,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    modifier = Modifier.fillMaxWidth().height(if (isCompact) 204.dp else 220.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = painterResource(id = R.drawable.img_hero_banner),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.horizontalGradient(
                listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                  MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                )
              )
            )
      )
      Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = stringResource(R.string.hero_tag),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.hero_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.hero_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
          )
        }
        Button(
          onClick = onPlayMix,
          shape = RoundedCornerShape(999.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("hero_play_mix_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.hero_play_mix),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun DailyRotationCard(
  track: Track?,
  onPlay: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onPlay)
        .testTag("daily_rotation_card"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ArtworkImage(
        artworkUrl = track?.artworkUrl,
        fallbackRes = track?.fallbackArtRes ?: R.drawable.img_art_daily_discovery,
        contentDescription = stringResource(R.string.daily_rotation_title),
        modifier = Modifier.size(64.dp),
        cornerRadius = 12.dp,
      )
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.daily_rotation_label),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
          text = stringResource(R.string.daily_rotation_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.daily_rotation_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Surface(
        onClick = onPlay,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.cd_play),
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    }
  }
}

@Composable
private fun JumpBackInCard(
  track: Track,
  isPlayingCurrent: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color =
      if (isPlayingCurrent) MaterialTheme.colorScheme.surfaceVariant
      else MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (isPlayingCurrent) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outline,
      ),
    modifier = Modifier.width(148.dp).testTag("jump_back_card_${track.id}"),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      ArtworkImage(
        artworkUrl = track.artworkUrl,
        fallbackRes = track.fallbackArtRes,
        contentDescription = stringResource(R.string.cd_album_art, track.title),
        modifier = Modifier.fillMaxWidth().height(124.dp),
        cornerRadius = 12.dp,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = track.title,
        style = MaterialTheme.typography.titleSmall,
        color =
          if (isPlayingCurrent) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurface,
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
