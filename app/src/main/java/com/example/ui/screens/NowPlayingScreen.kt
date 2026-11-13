package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PlaybackSession
import com.example.model.PlaybackStatus
import com.example.model.RepeatMode
import com.example.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.formatDuration
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
  session: PlaybackSession,
  isLiked: Boolean,
  isVideoMode: Boolean,
  isFullscreenVideo: Boolean,
  onToggleVideoMode: (Boolean) -> Unit,
  onToggleFullscreenVideo: (Boolean) -> Unit,
  onVideoStageBoundsChanged: (Rect) -> Unit,
  onOpenExternalYouTube: (Track) -> Unit,
  onCollapse: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrevious: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleLike: () -> Unit,
  onOpenQueue: () -> Unit,
  onAddToPlaylist: (Track) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler {
    if (isFullscreenVideo) {
      onToggleFullscreenVideo(false)
    } else {
      onCollapse()
    }
  }
  val track = session.currentTrack ?: return
  val showYtVideo = track.isYouTubeTrack && isVideoMode && !session.isPreviewFallback

  if (isFullscreenVideo && showYtVideo) {
    Box(
      modifier =
        modifier
          .fillMaxSize()
          .background(Color.Black)
          .testTag("now_playing_fullscreen_video"),
    ) {
      MediaStage(
        track = track,
        showVideoStage = true,
        isFullscreen = true,
        isEnded = session.status == PlaybackStatus.Ended,
        isBuffering =
          session.status == PlaybackStatus.Loading ||
            session.status == PlaybackStatus.Buffering,
        onReplay = onTogglePlayPause,
        onVideoStageBoundsChanged = onVideoStageBoundsChanged,
        modifier = Modifier.fillMaxSize(),
      )
    }
    return
  }

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = modifier.fillMaxSize().testTag("now_playing_screen"),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.background,
              )
            )
          )
          .windowInsetsPadding(WindowInsets.statusBars)
          .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp && maxWidth > maxHeight

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp)) {
          NowPlayingTopBar(
            showVideoToggle = track.isYouTubeTrack,
            isVideoMode = isVideoMode,
            onToggleVideoMode = onToggleVideoMode,
            onCollapse = onCollapse,
            onOpenQueue = onOpenQueue,
          )
          Spacer(modifier = Modifier.height(12.dp))

          if (isWide) {
            Row(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(28.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              MediaStage(
                track = track,
                showVideoStage = showYtVideo,
                isFullscreen = false,
                isEnded = session.status == PlaybackStatus.Ended,
                isBuffering =
                  session.status == PlaybackStatus.Loading ||
                    session.status == PlaybackStatus.Buffering,
                onReplay = onTogglePlayPause,
                onVideoStageBoundsChanged = onVideoStageBoundsChanged,
                modifier = Modifier.weight(1f).aspectRatio(16f / 9f),
              )
              Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.Center,
              ) {
                TrackHeaderAndActions(
                  track = track,
                  isLiked = isLiked,
                  onToggleLike = onToggleLike,
                  onAddToPlaylist = { onAddToPlaylist(track) },
                )
                Spacer(modifier = Modifier.height(14.dp))
                TimelineAndTransport(
                  session = session,
                  onSeekTo = onSeekTo,
                  onTogglePlayPause = onTogglePlayPause,
                  onSkipNext = onSkipNext,
                  onSkipPrevious = onSkipPrevious,
                  onToggleShuffle = onToggleShuffle,
                  onCycleRepeat = onCycleRepeat,
                  onOpenExternalYouTube = { onOpenExternalYouTube(track) },
                )
              }
            }
          } else {
            Column(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                if (showYtVideo) {
                  MediaStage(
                    track = track,
                    showVideoStage = true,
                    isFullscreen = false,
                    isEnded = session.status == PlaybackStatus.Ended,
                    isBuffering =
                      session.status == PlaybackStatus.Loading ||
                        session.status == PlaybackStatus.Buffering,
                    onReplay = onTogglePlayPause,
                    onVideoStageBoundsChanged = onVideoStageBoundsChanged,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                  )
                } else {
                  ArtworkImage(
                    artworkUrl = track.artworkUrl,
                    fallbackRes = track.fallbackArtRes,
                    contentDescription = stringResource(R.string.cd_album_art, track.title),
                    modifier = Modifier.size(292.dp),
                    cornerRadius = 24.dp,
                  )
                }
              }
              Column(modifier = Modifier.fillMaxWidth()) {
                TrackHeaderAndActions(
                  track = track,
                  isLiked = isLiked,
                  onToggleLike = onToggleLike,
                  onAddToPlaylist = { onAddToPlaylist(track) },
                )
                Spacer(modifier = Modifier.height(16.dp))
                TimelineAndTransport(
                  session = session,
                  onSeekTo = onSeekTo,
                  onTogglePlayPause = onTogglePlayPause,
                  onSkipNext = onSkipNext,
                  onSkipPrevious = onSkipPrevious,
                  onToggleShuffle = onToggleShuffle,
                  onCycleRepeat = onCycleRepeat,
                  onOpenExternalYouTube = { onOpenExternalYouTube(track) },
                )
                Spacer(modifier = Modifier.height(14.dp))
                NowPlayingFooterMetadata(
                  track = track,
                  isPreviewFallback = session.isPreviewFallback,
                  queueCount = session.queue.size,
                  onOpenQueue = onOpenQueue,
                  onOpenExternalYouTube = { onOpenExternalYouTube(track) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MediaStage(
  track: Track,
  showVideoStage: Boolean,
  isFullscreen: Boolean,
  isEnded: Boolean,
  isBuffering: Boolean,
  onReplay: () -> Unit,
  onVideoStageBoundsChanged: (Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (showVideoStage) {
    val shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)
    Surface(
      shape = shape,
      color = Color.Black,
      border = if (isFullscreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier =
        modifier.onGloballyPositioned { coords ->
          onVideoStageBoundsChanged(coords.boundsInRoot())
        },
    ) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtworkImage(
          artworkUrl = track.artworkUrl,
          fallbackRes = track.fallbackArtRes,
          contentDescription = stringResource(R.string.cd_album_art, track.title),
          modifier = Modifier.fillMaxSize(),
          cornerRadius = if (isFullscreen) 0.dp else 20.dp,
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = 0.55f)),
          contentAlignment = Alignment.Center,
        ) {
          when {
            isEnded -> {
              Button(
                onClick = onReplay,
                shape = RoundedCornerShape(999.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Replay,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_replay))
              }
            }
            isBuffering -> {
              CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }
  } else {
    ArtworkImage(
      artworkUrl = track.artworkUrl,
      fallbackRes = track.fallbackArtRes,
      contentDescription = stringResource(R.string.cd_album_art, track.title),
      modifier = modifier,
      cornerRadius = 24.dp,
    )
  }
}

/**
 * Clean native YouTube Music-style overlay rendered directly above the YouTube WebView stage.
 * Suppresses native YouTube iframe overlays while offering single-tap HUD toggle,
 * double-tap +/-10s seek, and full-screen cinema video controls.
 */
@Composable
fun YouTubeVideoOverlayControls(
  session: PlaybackSession,
  isFullscreen: Boolean,
  isLiked: Boolean,
  onToggleFullscreen: (Boolean) -> Unit,
  onSwitchToSongMode: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrevious: () -> Unit,
  onToggleLike: () -> Unit,
  onOpenQueue: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val track = session.currentTrack ?: return
  var hudVisible by remember(isFullscreen) { mutableStateOf(isFullscreen) }
  var interactionTick by remember { mutableIntStateOf(0) }
  var seekDeltaFeedback by remember { mutableIntStateOf(0) }
  var isDraggingSeek by remember { mutableStateOf(false) }
  var dragProgress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(hudVisible, session.isPlaying, interactionTick, isDraggingSeek) {
    if (hudVisible && session.isPlaying && !isDraggingSeek) {
      delay(2800L)
      hudVisible = false
    }
  }

  LaunchedEffect(seekDeltaFeedback) {
    if (seekDeltaFeedback != 0) {
      delay(750L)
      seekDeltaFeedback = 0
    }
  }

  Box(
    modifier =
      modifier
        .pointerInput(session.positionMs, session.durationMs) {
          detectTapGestures(
            onTap = {
              hudVisible = !hudVisible
              interactionTick++
            },
            onDoubleTap = { offset ->
              val isLeft = offset.x < size.width / 2f
              val deltaMs = if (isLeft) -10_000L else 10_000L
              val target = (session.positionMs + deltaMs).coerceIn(0L, session.durationMs.coerceAtLeast(1L))
              onSeekTo(target)
              seekDeltaFeedback = if (isLeft) -10 else 10
              interactionTick++
            },
          )
        }
  ) {
    AnimatedVisibility(
      visible = hudVisible || !session.isPlaying || session.status == PlaybackStatus.Buffering,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      if (isFullscreen) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(
                    Color.Black.copy(alpha = 0.72f),
                    Color.Black.copy(alpha = 0.35f),
                    Color.Black.copy(alpha = 0.80f),
                  )
                )
              )
              .windowInsetsPadding(WindowInsets.statusBars)
              .windowInsetsPadding(WindowInsets.navigationBars)
              .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = { onToggleFullscreen(false) },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("fullscreen_exit_top"),
            ) {
              Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_exit_fullscreen_video),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Surface(
              onClick = onSwitchToSongMode,
              shape = RoundedCornerShape(999.dp),
              color = Color.White.copy(alpha = 0.16f),
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Text(
                text = stringResource(R.string.now_playing_mode_artwork),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              )
            }
          }

          Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = {
                interactionTick++
                onSkipPrevious()
              },
              modifier = Modifier.size(56.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
              )
            }

            Surface(
              onClick = {
                interactionTick++
                onTogglePlayPause()
              },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(72.dp).testTag("fullscreen_play_pause"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                if (
                  session.status == PlaybackStatus.Loading ||
                    session.status == PlaybackStatus.Buffering
                ) {
                  CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp),
                  )
                } else {
                  Icon(
                    imageVector =
                      if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription =
                      stringResource(if (session.isPlaying) R.string.cd_pause else R.string.cd_play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                  )
                }
              }
            }

            IconButton(
              onClick = {
                interactionTick++
                onSkipNext()
              },
              modifier = Modifier.size(56.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                tint = Color.White,
                modifier = Modifier.size(36.dp),
              )
            }
          }

          Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            val shownProgress = if (isDraggingSeek) dragProgress else session.progressFraction
            val shownPosMs =
              if (isDraggingSeek) (dragProgress * session.durationMs).toLong()
              else session.positionMs
            Slider(
              value = shownProgress,
              onValueChange = { fraction ->
                isDraggingSeek = true
                dragProgress = fraction
                interactionTick++
              },
              onValueChangeFinished = {
                isDraggingSeek = false
                onSeekTo((dragProgress * session.durationMs.toFloat()).toLong())
                interactionTick++
              },
              colors =
                SliderDefaults.colors(
                  thumbColor = MaterialTheme.colorScheme.primary,
                  activeTrackColor = MaterialTheme.colorScheme.primary,
                  inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                ),
              modifier = Modifier.fillMaxWidth(),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "${formatDuration(shownPosMs)} / ${formatDuration(session.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = {
                    interactionTick++
                    onToggleLike()
                  },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector =
                      if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription =
                      stringResource(if (isLiked) R.string.cd_unlike else R.string.cd_like),
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                  )
                }
                IconButton(
                  onClick = {
                    interactionTick++
                    onOpenQueue()
                  },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.cd_open_queue),
                    tint = Color.White,
                  )
                }
                IconButton(
                  onClick = { onToggleFullscreen(false) },
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("fullscreen_exit_bottom"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.FullscreenExit,
                    contentDescription = stringResource(R.string.cd_exit_fullscreen_video),
                    tint = Color.White,
                  )
                }
              }
            }
          }
        }
      } else {
        Box(
          modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)),
        ) {
          Surface(
            onClick = {
              interactionTick++
              onTogglePlayPause()
            },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f),
            modifier = Modifier.size(60.dp).align(Alignment.Center),
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (
                session.status == PlaybackStatus.Loading ||
                  session.status == PlaybackStatus.Buffering
              ) {
                CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.primary,
                  strokeWidth = 2.5.dp,
                  modifier = Modifier.size(26.dp),
                )
              } else {
                Icon(
                  imageVector =
                    if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                  contentDescription =
                    stringResource(if (session.isPlaying) R.string.cd_pause else R.string.cd_play),
                  tint = Color.White,
                  modifier = Modifier.size(34.dp),
                )
              }
            }
          }
        }
      }
    }

    if (!isFullscreen) {
      Surface(
        onClick = { onToggleFullscreen(true) },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.56f),
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(8.dp)
            .size(48.dp)
            .testTag("video_fullscreen_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Fullscreen,
            contentDescription = stringResource(R.string.cd_fullscreen_video),
            tint = Color.White,
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }

    if (seekDeltaFeedback != 0) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.72f),
        modifier =
          Modifier.align(
            if (seekDeltaFeedback < 0) Alignment.CenterStart else Alignment.CenterEnd
          )
            .padding(horizontal = 24.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector =
              if (seekDeltaFeedback < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = if (seekDeltaFeedback < 0) "-10s" else "+10s",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
          )
        }
      }
    }
  }
}

@Composable
private fun NowPlayingTopBar(
  showVideoToggle: Boolean,
  isVideoMode: Boolean,
  onToggleVideoMode: (Boolean) -> Unit,
  onCollapse: () -> Unit,
  onOpenQueue: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    IconButton(
      onClick = onCollapse,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("now_playing_collapse_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = stringResource(R.string.cd_collapse_player),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(30.dp),
      )
    }
    if (showVideoToggle) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier.padding(3.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          StageModePill(
            label = stringResource(R.string.now_playing_mode_video),
            selected = isVideoMode,
            onClick = { onToggleVideoMode(true) },
            modifier = Modifier.testTag("mode_video_pill"),
          )
          StageModePill(
            label = stringResource(R.string.now_playing_mode_artwork),
            selected = !isVideoMode,
            onClick = { onToggleVideoMode(false) },
            modifier = Modifier.testTag("mode_artwork_pill"),
          )
        }
      }
    } else {
      Text(
        text = stringResource(R.string.now_playing_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(
      onClick = onOpenQueue,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("now_playing_queue_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
        contentDescription = stringResource(R.string.cd_open_queue),
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }
  }
}

@Composable
private fun StageModePill(
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
    modifier = modifier,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color =
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun TrackHeaderAndActions(
  track: Track,
  isLiked: Boolean,
  onToggleLike: () -> Unit,
  onAddToPlaylist: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "${track.artist} · ${track.album}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    IconButton(
      onClick = onAddToPlaylist,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("now_playing_add_playlist"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
        contentDescription = stringResource(R.string.action_add_to_playlist),
        tint = MaterialTheme.colorScheme.onBackground,
      )
    }
    IconButton(
      onClick = onToggleLike,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("now_playing_like_button"),
    ) {
      Icon(
        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        contentDescription = stringResource(if (isLiked) R.string.cd_unlike else R.string.cd_like),
        tint =
          if (isLiked) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onBackground,
      )
    }
  }
}

@Composable
private fun TimelineAndTransport(
  session: PlaybackSession,
  onSeekTo: (Long) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrevious: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onOpenExternalYouTube: () -> Unit,
) {
  var isDragging by remember { mutableStateOf(false) }
  var dragProgress by remember { mutableFloatStateOf(0f) }
  val shownProgress = if (isDragging) dragProgress else session.progressFraction

  if (session.status == PlaybackStatus.Failed && session.errorMessage != null) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
      modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = session.errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.weight(1f),
        )
        if (session.currentTrack?.isYouTubeTrack == true) {
          TextButton(onClick = onOpenExternalYouTube) {
            Text(
              text = stringResource(R.string.action_open_youtube),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Button(
          onClick = onTogglePlayPause,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          Text(stringResource(R.string.search_retry), style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }

  Slider(
    value = shownProgress,
    onValueChange = { fraction ->
      isDragging = true
      dragProgress = fraction
    },
    onValueChangeFinished = {
      isDragging = false
      val seekTarget = (dragProgress * session.durationMs.toFloat()).toLong()
      onSeekTo(seekTarget)
    },
    colors =
      SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.outline,
      ),
    modifier = Modifier.fillMaxWidth().testTag("now_playing_seek_slider"),
  )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    val shownPosMs =
      if (isDragging) (dragProgress * session.durationMs).toLong() else session.positionMs
    Text(
      text = formatDuration(shownPosMs),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = formatDuration(session.durationMs),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  Spacer(modifier = Modifier.height(12.dp))

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = onToggleShuffle,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("now_playing_shuffle"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Shuffle,
        contentDescription = stringResource(R.string.cd_shuffle),
        tint =
          if (session.isShuffleEnabled) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    IconButton(
      onClick = onSkipPrevious,
      modifier = Modifier.size(52.dp).testTag("now_playing_previous"),
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipPrevious,
        contentDescription = stringResource(R.string.cd_previous),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(34.dp),
      )
    }

    Surface(
      onClick = onTogglePlayPause,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(68.dp).testTag("now_playing_play_pause"),
    ) {
      Box(contentAlignment = Alignment.Center) {
        if (
          session.status == PlaybackStatus.Loading || session.status == PlaybackStatus.Buffering
        ) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(26.dp),
          )
        } else {
          Icon(
            imageVector =
              if (session.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription =
              stringResource(if (session.isPlaying) R.string.cd_pause else R.string.cd_play),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(34.dp),
          )
        }
      }
    }

    IconButton(
      onClick = onSkipNext,
      modifier = Modifier.size(52.dp).testTag("now_playing_next"),
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipNext,
        contentDescription = stringResource(R.string.cd_next),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(34.dp),
      )
    }

    IconButton(
      onClick = onCycleRepeat,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("now_playing_repeat"),
    ) {
      Icon(
        imageVector =
          if (session.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne
          else Icons.Rounded.Repeat,
        contentDescription = stringResource(R.string.cd_repeat),
        tint =
          if (session.repeatMode != RepeatMode.Off) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun NowPlayingFooterMetadata(
  track: Track,
  isPreviewFallback: Boolean,
  queueCount: Int,
  onOpenQueue: () -> Unit,
  onOpenExternalYouTube: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      onClick = { if (track.isYouTubeTrack) onOpenExternalYouTube() },
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.minimumInteractiveComponentSize(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector =
            if (track.isYouTubeTrack && !isPreviewFallback) Icons.Rounded.PlayCircleOutline
            else Icons.Rounded.Speaker,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text =
            when {
              track.isYouTubeTrack && isPreviewFallback ->
                stringResource(R.string.now_playing_quality_preview_fallback)
              track.isYouTubeTrack -> stringResource(R.string.now_playing_quality_youtube)
              !track.previewUrl.isNullOrBlank() -> stringResource(R.string.now_playing_quality_preview)
              else -> stringResource(R.string.now_playing_output_speaker)
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (track.isYouTubeTrack) {
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = stringResource(R.string.action_open_youtube),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
          )
        }
      }
    }

    Surface(
      onClick = onOpenQueue,
      shape = RoundedCornerShape(999.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.minimumInteractiveComponentSize(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "${stringResource(R.string.now_playing_queue)} ($queueCount)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
  session: PlaybackSession,
  onDismiss: () -> Unit,
  onPlayAtIndex: (Int) -> Unit,
  onRemoveAtIndex: (Int) -> Unit,
  onMoveItem: (Int, Int) -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
          .testTag("queue_bottom_sheet"),
    ) {
      Text(
        text = stringResource(R.string.now_playing_queue),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(12.dp))
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(340.dp),
      ) {
        itemsIndexed(session.queue, key = { index, t -> "${t.id}_$index" }) { index, track ->
          val isCurrent = index == session.currentIndex
          Surface(
            shape = RoundedCornerShape(14.dp),
            color =
              if (isCurrent) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onPlayAtIndex(index) },
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              ArtworkImage(
                artworkUrl = track.artworkUrl,
                fallbackRes = track.fallbackArtRes,
                contentDescription = track.title,
                modifier = Modifier.size(44.dp),
                cornerRadius = 8.dp,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = track.title,
                  style = MaterialTheme.typography.titleSmall,
                  color =
                    if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    if (isCurrent) stringResource(R.string.now_playing_current)
                    else track.artist,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              if (!isCurrent) {
                if (index > session.currentIndex + 1 && index > 0) {
                  IconButton(
                    onClick = { onMoveItem(index, index - 1) },
                    modifier = Modifier.size(36.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.ArrowUpward,
                      contentDescription = "Move up",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
                if (index < session.queue.lastIndex && index + 1 != session.currentIndex) {
                  IconButton(
                    onClick = { onMoveItem(index, index + 1) },
                    modifier = Modifier.size(36.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.ArrowDownward,
                      contentDescription = "Move down",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
                IconButton(
                  onClick = { onRemoveAtIndex(index) },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cd_remove_from_queue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
