package com.example.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.data.CatalogRepository
import com.example.model.PlaybackSession
import com.example.model.PlaybackStatus
import com.example.model.RepeatMode
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Unified playback controller coordinating official YouTube IFrame playback
 * and native MediaPlayer AAC preview / fallback playback.
 */
class PlaybackController(
  private val scope: CoroutineScope,
  private val catalogRepository: CatalogRepository,
  private val onForegroundPlaybackRequested: (() -> Unit)? = null,
) {
  private enum class ActiveEngine {
    None,
    YouTube,
    MediaPlayer,
  }

  private val _session = MutableStateFlow(PlaybackSession())
  val session: StateFlow<PlaybackSession> = _session.asStateFlow()

  private var mediaPlayer: MediaPlayer? = null
  private var tickerJob: Job? = null
  private var loadJob: Job? = null
  private var activeEngine: ActiveEngine = ActiveEngine.None
  private var activeTrackToken: String? = null
  private var retriedAlternativeForToken: String? = null

  val youTubeBridge: YouTubePlayerBridge =
    YouTubePlayerBridge(
      onPlayerReady = {
        // Pending video auto-loads inside YouTubePlayerBridge
      },
      onStateChanged = { stateCode ->
        if (activeEngine != ActiveEngine.YouTube) return@YouTubePlayerBridge
        when (stateCode) {
          YouTubePlayerBridge.YT_STATE_PLAYING -> {
            _session.update { it.copy(status = PlaybackStatus.Playing, errorMessage = null) }
          }
          YouTubePlayerBridge.YT_STATE_PAUSED -> {
            _session.update {
              if (it.status != PlaybackStatus.Ended && it.status != PlaybackStatus.Failed) {
                it.copy(status = PlaybackStatus.Paused)
              } else {
                it
              }
            }
          }
          YouTubePlayerBridge.YT_STATE_BUFFERING -> {
            _session.update { it.copy(status = PlaybackStatus.Buffering) }
          }
          YouTubePlayerBridge.YT_STATE_CUED -> {
            val status = _session.value.status
            if (status == PlaybackStatus.Loading || status == PlaybackStatus.Buffering) {
              youTubeBridge.play()
            }
          }
          YouTubePlayerBridge.YT_STATE_ENDED -> {
            handleTrackEnded()
          }
        }
      },
      onTimeUpdated = { posMs, durMs ->
        if (activeEngine != ActiveEngine.YouTube) return@YouTubePlayerBridge
        _session.update {
          it.copy(
            positionMs = posMs,
            durationMs = if (durMs > 0L) durMs else it.durationMs,
          )
        }
      },
      onPlayerError = { errorCode ->
        if (activeEngine != ActiveEngine.YouTube) return@YouTubePlayerBridge
        handleYouTubePlaybackError(errorCode)
      },
    )

  fun playTrack(track: Track, queueContext: List<Track> = emptyList()) {
    val mergedQueue =
      if (queueContext.isNotEmpty()) {
        if (queueContext.any { it.id == track.id }) queueContext else listOf(track) + queueContext
      } else {
        val currentQueue = _session.value.queue
        if (currentQueue.any { it.id == track.id }) currentQueue else listOf(track) + currentQueue
      }
    val idx = mergedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    startPlaybackAtIndex(mergedQueue, idx)
  }

  fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
    if (tracks.isEmpty()) return
    val bounded = startIndex.coerceIn(0, tracks.lastIndex)
    startPlaybackAtIndex(tracks, bounded)
  }

  private fun startPlaybackAtIndex(queue: List<Track>, index: Int) {
    val target = queue.getOrNull(index) ?: return
    loadJob?.cancel()
    stopTicker()
    releaseMediaPlayer()

    val requestToken = "${target.id}_${System.nanoTime()}"
    activeTrackToken = requestToken

    _session.update {
      it.copy(
        currentTrack = target,
        queue = queue,
        currentIndex = index,
        status = PlaybackStatus.Loading,
        positionMs = 0L,
        durationMs = target.durationMs,
        isPreviewFallback = false,
        errorMessage = null,
      )
    }
    onForegroundPlaybackRequested?.invoke()

    loadJob =
      scope.launch {
        val resolved = catalogRepository.resolveStreamableTrack(target)
        if (activeTrackToken != requestToken) return@launch

        val updatedQueue =
          queue.mapIndexed { qIdx, item ->
            if (qIdx == index || item.id == target.id || item.id == resolved.id) resolved else item
          }
        _session.update {
          it.copy(
            currentTrack = resolved,
            queue = updatedQueue,
            durationMs = resolved.durationMs,
          )
        }

        val ytVideoId = resolved.youtubeVideoId
        if (!ytVideoId.isNullOrBlank()) {
          activeEngine = ActiveEngine.YouTube
          _session.update { it.copy(isPreviewFallback = false) }
          youTubeBridge.loadVideo(ytVideoId)
          return@launch
        }

        youTubeBridge.pause()
        val streamUrl = resolved.previewUrl
        if (streamUrl.isNullOrBlank()) {
          activeEngine = ActiveEngine.None
          _session.update {
            it.copy(
              status = PlaybackStatus.Failed,
              errorMessage = "Audio stream unavailable offline for this track.",
            )
          }
          return@launch
        }

        activeEngine = ActiveEngine.MediaPlayer
        prepareAndPlayStream(resolved, streamUrl, isFallback = false)
      }
  }

  private fun handleYouTubePlaybackError(errorCode: Int) {
    val stateSnapshot = _session.value
    val current = stateSnapshot.currentTrack ?: return
    val tokenAtFailure = activeTrackToken
    val failedVideoId = current.youtubeVideoId
    val canTryAlternative =
      errorCode in listOf(101, 150, 152, 153) &&
        !failedVideoId.isNullOrBlank() &&
        retriedAlternativeForToken != tokenAtFailure
    if (canTryAlternative) {
      retriedAlternativeForToken = tokenAtFailure
    }

    loadJob?.cancel()
    loadJob =
      scope.launch {
        if (canTryAlternative) {
          val altTrack =
            catalogRepository.resolveAlternativeYouTubeTrack(current, checkNotNull(failedVideoId))
          if (activeTrackToken != tokenAtFailure) return@launch
          val altVideoId = altTrack?.youtubeVideoId
          if (!altVideoId.isNullOrBlank() && altVideoId != failedVideoId) {
            val activeIdx = stateSnapshot.currentIndex
            val updatedQueue =
              _session.value.queue.mapIndexed { qIdx, item ->
                if (qIdx == activeIdx || item.id == current.id) altTrack else item
              }
            activeEngine = ActiveEngine.YouTube
            _session.update {
              it.copy(
                currentTrack = altTrack,
                queue = updatedQueue,
                durationMs = altTrack.durationMs,
                isPreviewFallback = false,
                status = PlaybackStatus.Loading,
                errorMessage = null,
              )
            }
            youTubeBridge.loadVideo(altVideoId)
            return@launch
          }
        }

        val fallbackTrack = catalogRepository.resolveAudioFallback(current)
        if (activeTrackToken != tokenAtFailure) return@launch

        val previewUrl = fallbackTrack.previewUrl
        if (!previewUrl.isNullOrBlank() && !fallbackTrack.isYouTubeTrack) {
          activeEngine = ActiveEngine.MediaPlayer
          _session.update { it.copy(isPreviewFallback = true) }
          prepareAndPlayStream(fallbackTrack, previewUrl, isFallback = true)
          return@launch
        }

        val reason =
          when (errorCode) {
            101, 150, 152, 153 -> "This YouTube video restricts embedded playback."
            100 -> "This YouTube video was removed or marked private."
            else -> "YouTube playback unavailable (code $errorCode)."
          }
        _session.update {
          it.copy(
            status = PlaybackStatus.Failed,
            errorMessage = reason,
          )
        }
      }
  }

  private fun prepareAndPlayStream(
    track: Track,
    streamUrl: String,
    isFallback: Boolean = false,
  ) {
    try {
      val player =
        MediaPlayer().apply {
          setAudioAttributes(
            AudioAttributes.Builder()
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .build()
          )
          setDataSource(streamUrl)
          setOnPreparedListener { mp ->
            val realDuration = mp.duration.takeIf { it > 0 }?.toLong() ?: track.durationMs
            mp.start()
            _session.update {
              it.copy(
                status = PlaybackStatus.Playing,
                durationMs = realDuration,
                positionMs = 0L,
                isPreviewFallback = isFallback,
                errorMessage = null,
              )
            }
            startTicker()
          }
          setOnInfoListener { _, what, _ ->
            when (what) {
              MediaPlayer.MEDIA_INFO_BUFFERING_START ->
                _session.update { it.copy(status = PlaybackStatus.Buffering) }
              MediaPlayer.MEDIA_INFO_BUFFERING_END ->
                _session.update {
                  if (mediaPlayer?.isPlaying == true) it.copy(status = PlaybackStatus.Playing) else it
                }
            }
            false
          }
          setOnCompletionListener {
            stopTicker()
            handleTrackEnded()
          }
          setOnErrorListener { _, _, _ ->
            stopTicker()
            _session.update {
              it.copy(
                status = PlaybackStatus.Failed,
                errorMessage = "Playback interrupted. Tap retry or check your network connection.",
              )
            }
            true
          }
          prepareAsync()
        }
      mediaPlayer = player
    } catch (e: Exception) {
      _session.update {
        it.copy(
          status = PlaybackStatus.Failed,
          errorMessage = e.localizedMessage ?: "Unable to start audio stream.",
        )
      }
    }
  }

  fun togglePlayPause() {
    val current = _session.value
    if (current.status == PlaybackStatus.Playing) {
      pausePlayback()
    } else {
      resumePlayback()
    }
  }

  fun pausePlayback() {
    val current = _session.value
    if (activeEngine == ActiveEngine.YouTube) {
      if (current.status == PlaybackStatus.Playing || current.status == PlaybackStatus.Buffering) {
        youTubeBridge.pause()
        _session.update { it.copy(status = PlaybackStatus.Paused) }
      }
      return
    }
    val player = mediaPlayer
    if (current.status == PlaybackStatus.Playing) {
      player?.pause()
      stopTicker()
      _session.update { it.copy(status = PlaybackStatus.Paused) }
    }
  }

  fun resumePlayback() {
    val current = _session.value
    if (activeEngine == ActiveEngine.YouTube) {
      when (current.status) {
        PlaybackStatus.Paused -> {
          onForegroundPlaybackRequested?.invoke()
          youTubeBridge.play()
          _session.update { it.copy(status = PlaybackStatus.Playing, errorMessage = null) }
        }
        PlaybackStatus.Ended,
        PlaybackStatus.Failed,
        PlaybackStatus.Idle -> {
          current.currentTrack?.let { playTrack(it, current.queue) }
        }
        PlaybackStatus.Playing,
        PlaybackStatus.Loading,
        PlaybackStatus.Buffering -> Unit
      }
      return
    }

    val player = mediaPlayer
    when (current.status) {
      PlaybackStatus.Paused -> {
        if (player != null) {
          onForegroundPlaybackRequested?.invoke()
          player.start()
          startTicker()
          _session.update { it.copy(status = PlaybackStatus.Playing, errorMessage = null) }
        } else {
          current.currentTrack?.let { playTrack(it, current.queue) }
        }
      }
      PlaybackStatus.Ended,
      PlaybackStatus.Failed,
      PlaybackStatus.Idle -> {
        current.currentTrack?.let { playTrack(it, current.queue) }
      }
      PlaybackStatus.Playing,
      PlaybackStatus.Loading,
      PlaybackStatus.Buffering -> Unit
    }
  }

  fun stopAndClearSession() {
    activeTrackToken = null
    retriedAlternativeForToken = null
    loadJob?.cancel()
    stopTicker()
    releaseMediaPlayer()
    youTubeBridge.stop()
    activeEngine = ActiveEngine.None
    _session.value = PlaybackSession()
  }

  fun seekTo(positionMs: Long) {
    val state = _session.value
    val clamped = positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L))
    _session.update { it.copy(positionMs = clamped) }
    if (activeEngine == ActiveEngine.YouTube) {
      youTubeBridge.seekTo(clamped)
      return
    }
    try {
      if (state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Paused) {
        mediaPlayer?.seekTo(clamped.toInt())
      }
    } catch (_: IllegalStateException) {
      // Ignored if player is still preparing
    }
  }

  fun skipNext() {
    val state = _session.value
    if (state.queue.isEmpty()) return
    val nextIndex =
      if (state.isShuffleEnabled && state.queue.size > 1) {
        val candidates = state.queue.indices.filter { it != state.currentIndex }
        candidates.random()
      } else if (state.currentIndex + 1 < state.queue.size) {
        state.currentIndex + 1
      } else {
        0
      }
    startPlaybackAtIndex(state.queue, nextIndex)
  }

  fun skipPrevious() {
    val state = _session.value
    if (state.queue.isEmpty()) return
    if (state.positionMs > 3000L) {
      seekTo(0L)
      return
    }
    val prevIndex =
      if (state.currentIndex - 1 >= 0) state.currentIndex - 1 else state.queue.lastIndex
    startPlaybackAtIndex(state.queue, prevIndex)
  }

  fun toggleShuffle() {
    _session.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
  }

  fun cycleRepeatMode() {
    _session.update {
      val next =
        when (it.repeatMode) {
          RepeatMode.Off -> RepeatMode.All
          RepeatMode.All -> RepeatMode.One
          RepeatMode.One -> RepeatMode.Off
        }
      it.copy(repeatMode = next)
    }
  }

  fun removeFromQueue(index: Int) {
    _session.update { state ->
      if (index !in state.queue.indices || index == state.currentIndex) return@update state
      val mutable = state.queue.toMutableList()
      mutable.removeAt(index)
      val adjustedCurrent =
        if (index < state.currentIndex) state.currentIndex - 1 else state.currentIndex
      state.copy(queue = mutable, currentIndex = adjustedCurrent)
    }
  }

  fun moveQueueItem(fromIndex: Int, toIndex: Int) {
    _session.update { state ->
      if (
        fromIndex !in state.queue.indices ||
          toIndex !in state.queue.indices ||
          fromIndex == state.currentIndex ||
          toIndex == state.currentIndex
      ) {
        return@update state
      }
      val mutable = state.queue.toMutableList()
      val item = mutable.removeAt(fromIndex)
      mutable.add(toIndex, item)
      val newCurrent = mutable.indexOfFirst { it.id == state.currentTrack?.id }
      state.copy(
        queue = mutable,
        currentIndex = if (newCurrent >= 0) newCurrent else state.currentIndex,
      )
    }
  }

  fun resumeFromBackground() {
    youTubeBridge.onHostResume()
  }

  private fun handleTrackEnded() {
    val state = _session.value
    when (state.repeatMode) {
      RepeatMode.One -> startPlaybackAtIndex(state.queue, state.currentIndex.coerceAtLeast(0))
      RepeatMode.All -> skipNext()
      RepeatMode.Off -> {
        if (state.currentIndex + 1 < state.queue.size) {
          startPlaybackAtIndex(state.queue, state.currentIndex + 1)
        } else {
          if (activeEngine == ActiveEngine.YouTube) {
            youTubeBridge.pause()
          }
          _session.update { it.copy(status = PlaybackStatus.Ended, positionMs = it.durationMs) }
        }
      }
    }
  }

  private fun startTicker() {
    stopTicker()
    tickerJob =
      scope.launch {
        while (isActive) {
          val mp = mediaPlayer
          if (mp != null) {
            try {
              if (mp.isPlaying) {
                val pos = mp.currentPosition.toLong()
                val dur = mp.duration.takeIf { it > 0 }?.toLong() ?: _session.value.durationMs
                _session.update { it.copy(positionMs = pos, durationMs = dur) }
              }
            } catch (_: IllegalStateException) {
              // Ignore transient player state transitions
            }
          }
          delay(250L)
        }
      }
  }

  private fun stopTicker() {
    tickerJob?.cancel()
    tickerJob = null
  }

  private fun releaseMediaPlayer() {
    try {
      mediaPlayer?.reset()
      mediaPlayer?.release()
    } catch (_: Exception) {
      // Safe cleanup
    }
    mediaPlayer = null
  }

  fun release() {
    loadJob?.cancel()
    stopTicker()
    releaseMediaPlayer()
    youTubeBridge.destroy()
  }
}
