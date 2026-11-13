package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MoodCard
import com.example.model.SearchResultState
import com.example.model.Track
import com.example.model.ViewportClass
import com.example.ui.MelodyUiState
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EmptyOrErrorCard
import com.example.ui.components.TrackRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
  uiState: MelodyUiState,
  currentTrackId: String?,
  viewportClass: ViewportClass,
  onQueryChange: (String) -> Unit,
  onSubmitSearch: (String) -> Unit,
  onSelectMood: (MoodCard) -> Unit,
  onRemoveRecent: (String) -> Unit,
  onClearAllRecent: () -> Unit,
  onPlayTrack: (Track, List<Track>) -> Unit,
  onToggleLike: (Track) -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  modifier: Modifier = Modifier,
) {
  val moodColumns = if (viewportClass == ViewportClass.Compact) 2 else 3

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("search_screen"),
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
        Text(
          text = stringResource(R.string.search_heading),
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
    }

    item {
      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        trailingIcon = {
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(
              onClick = { onQueryChange("") },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("search_clear_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_clear_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(uiState.searchQuery) }),
        shape = RoundedCornerShape(16.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
        modifier = Modifier.fillMaxWidth().testTag("search_input"),
      )
    }

    if (uiState.recentSearches.isNotEmpty() && uiState.searchQuery.isBlank()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.search_recent_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          TextButton(
            onClick = onClearAllRecent,
            modifier = Modifier.testTag("search_clear_recent_button"),
          ) {
            Text(
              text = stringResource(R.string.search_clear_all),
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          uiState.recentSearches.forEach { recent ->
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.surface,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 6.dp),
              ) {
                Text(
                  text = recent,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier =
                    Modifier.clickable { onSubmitSearch(recent) }.padding(vertical = 10.dp),
                )
                IconButton(
                  onClick = { onRemoveRecent(recent) },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_remove_recent, recent),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      }
    }

    when (val state = uiState.searchState) {
      SearchResultState.Loading -> {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
      }
      is SearchResultState.Success -> {
        item {
          Text(
            text = stringResource(R.string.search_results_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          if (state.fromFallback) {
            Text(
              text = stringResource(R.string.search_offline_error),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
        }
        items(state.tracks, key = { "res_${it.id}" }) { track ->
          TrackRow(
            track = track,
            isPlayingCurrent = track.id == currentTrackId,
            isLiked = uiState.likedTracks.any { it.id == track.id },
            onPlay = { onPlayTrack(track, state.tracks) },
            onToggleLike = { onToggleLike(track) },
            onAddToPlaylist = { onAddToPlaylist(track) },
          )
        }
      }
      SearchResultState.Empty -> {
        item {
          EmptyOrErrorCard(
            title = stringResource(R.string.search_empty_title),
            subtitle = stringResource(R.string.search_empty_subtitle),
          )
        }
      }
      is SearchResultState.Error -> {
        item {
          EmptyOrErrorCard(
            title = stringResource(R.string.search_empty_title),
            subtitle = state.message,
            actionLabel = stringResource(R.string.search_retry),
            onAction = { onSubmitSearch(uiState.searchQuery) },
          )
        }
      }
      SearchResultState.Idle -> {
        item {
          Text(
            text = stringResource(R.string.search_browse_moods),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
        val rows = uiState.moodCards.chunked(moodColumns)
        items(rows, key = { r -> r.joinToString("_") { it.id } }) { rowCards ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            rowCards.forEach { mood ->
              MoodBrowseCard(
                mood = mood,
                onClick = { onSelectMood(mood) },
                modifier = Modifier.weight(1f),
              )
            }
            repeat(moodColumns - rowCards.size) { Spacer(modifier = Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}

@Composable
private fun MoodBrowseCard(
  mood: MoodCard,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier = modifier.height(112.dp).testTag("mood_card_${mood.id}"),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      ArtworkImage(
        artworkUrl = null,
        fallbackRes = mood.artRes,
        contentDescription = mood.title,
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 18.dp,
      )
      Box(
        modifier =
          Modifier.fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(
              Brush.verticalGradient(
                listOf(
                  MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                  MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                )
              )
            )
      )
      Column(
        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
      ) {
        Text(
          text = mood.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = mood.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
