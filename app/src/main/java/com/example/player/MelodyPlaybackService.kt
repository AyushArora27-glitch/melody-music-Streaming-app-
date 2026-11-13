package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import com.example.model.PlaybackSession
import com.example.model.PlaybackStatus
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground mediaPlayback service sustaining audio when Melody is backgrounded or screen-locked,
 * exposing system/lockscreen MediaSession controls and managing audio focus + WakeLock.
 */
class MelodyPlaybackService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private lateinit var playbackController: PlaybackController
  private lateinit var audioManager: AudioManager
  private lateinit var notificationManager: NotificationManager
  private lateinit var mediaSession: MediaSession
  private lateinit var imageLoader: ImageLoader

  private var audioFocusRequest: AudioFocusRequest? = null
  private var hasAudioFocus = false
  private var pausedByTransientLoss = false
  private var isForegroundStarted = false
  private var wakeLock: PowerManager.WakeLock? = null
  private var noisyReceiverRegistered = false

  private var lastArtworkTrackId: String? = null
  private var cachedArtwork: Bitmap? = null
  private var artworkJob: Job? = null
  private var lastNotifiedSignature: String? = null

  private val noisyReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
          playbackController.pausePlayback()
        }
      }
    }

  private val audioFocusListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS -> {
          hasAudioFocus = false
          pausedByTransientLoss = false
          playbackController.pausePlayback()
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          if (playbackController.session.value.isPlaying) {
            pausedByTransientLoss = true
            playbackController.pausePlayback()
          }
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
          hasAudioFocus = true
          if (pausedByTransientLoss) {
            pausedByTransientLoss = false
            playbackController.resumePlayback()
          }
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    playbackController = MelodyPlayerEngine.getController(applicationContext)
    audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    imageLoader = ImageLoader(this)

    createNotificationChannel()

    val pm = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock =
      pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:MelodyPlaybackWakeLock").apply {
        setReferenceCounted(false)
      }

    mediaSession =
      MediaSession(this, "MelodyMediaSession").apply {
        setCallback(
          object : MediaSession.Callback() {
            override fun onPlay() {
              playbackController.resumePlayback()
            }

            override fun onPause() {
              pausedByTransientLoss = false
              playbackController.pausePlayback()
            }

            override fun onSkipToNext() {
              playbackController.skipNext()
            }

            override fun onSkipToPrevious() {
              playbackController.skipPrevious()
            }

            override fun onSeekTo(pos: Long) {
              playbackController.seekTo(pos)
            }

            override fun onStop() {
              stopPlaybackAndService()
            }
          }
        )
        isActive = true
      }

    serviceScope.launch {
      playbackController.session.collectLatest { session ->
        handleSessionUpdate(session)
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_PLAY_PAUSE -> playbackController.togglePlayPause()
      ACTION_NEXT -> playbackController.skipNext()
      ACTION_PREV -> playbackController.skipPrevious()
      ACTION_STOP -> stopPlaybackAndService()
      else -> {
        val currentSession = playbackController.session.value
        if (currentSession.isActive) {
          ensureForegroundPosted(currentSession)
        } else {
          // Ensure startForeground contract is satisfied if started before session state propagated
          val placeholder = buildPlaceholderNotification()
          postForegroundNotification(placeholder)
        }
      }
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onTaskRemoved(rootIntent: Intent?) {
    if (!playbackController.session.value.isPlaying) {
      stopPlaybackAndService()
    }
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    super.onDestroy()
    artworkJob?.cancel()
    serviceScope.cancel()
    unregisterNoisyReceiver()
    abandonAudioFocus()
    releaseWakeLock()
    runCatching {
      mediaSession.isActive = false
      mediaSession.release()
    }
  }

  private fun handleSessionUpdate(session: PlaybackSession) {
    val track = session.currentTrack
    if (track == null || session.status == PlaybackStatus.Idle) {
      releaseWakeLock()
      unregisterNoisyReceiver()
      abandonAudioFocus()
      isForegroundStarted = false
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return
    }

    val usesWebViewAudio = track.isYouTubeTrack && !session.isPreviewFallback
    val needsWakeAndFocus =
      session.status == PlaybackStatus.Playing ||
        session.status == PlaybackStatus.Buffering ||
        session.status == PlaybackStatus.Loading
    if (needsWakeAndFocus) {
      // Chromium WebView acquires its own AudioManager focus for HTML5/YouTube playback.
      // Requesting a duplicate AUDIOFOCUS_GAIN from the same app causes Android AudioService
      // to fire AUDIOFOCUS_LOSS back on this listener, immediately pausing the song.
      if (usesWebViewAudio) {
        abandonAudioFocus()
      } else {
        requestAudioFocus()
      }
      acquireWakeLock()
      registerNoisyReceiver()
    } else {
      releaseWakeLock()
      unregisterNoisyReceiver()
      if (session.status == PlaybackStatus.Paused || session.status == PlaybackStatus.Ended) {
        abandonAudioFocus()
      }
    }

    if (lastArtworkTrackId != track.id) {
      lastArtworkTrackId = track.id
      cachedArtwork = decodeFallbackArt(track)
      loadTrackArtworkAsync(track)
    }

    updateMediaSession(track, session)
    ensureForegroundPosted(session)
  }

  private fun updateMediaSession(track: Track, session: PlaybackSession) {
    val duration = if (session.durationMs > 0L) session.durationMs else track.durationMs
    val metadataBuilder =
      MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
        .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
    cachedArtwork?.let { bmp ->
      metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
    }
    mediaSession.setMetadata(metadataBuilder.build())

    val sessionState =
      when (session.status) {
        PlaybackStatus.Playing -> PlaybackState.STATE_PLAYING
        PlaybackStatus.Buffering,
        PlaybackStatus.Loading -> PlaybackState.STATE_BUFFERING
        PlaybackStatus.Paused,
        PlaybackStatus.Ended -> PlaybackState.STATE_PAUSED
        PlaybackStatus.Failed -> PlaybackState.STATE_ERROR
        PlaybackStatus.Idle -> PlaybackState.STATE_NONE
      }
    val actions =
      PlaybackState.ACTION_PLAY or
        PlaybackState.ACTION_PAUSE or
        PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
        PlaybackState.ACTION_SEEK_TO or
        PlaybackState.ACTION_STOP

    val speed = if (session.status == PlaybackStatus.Playing) 1.0f else 0f
    val stateBuilder =
      PlaybackState.Builder()
        .setActions(actions)
        .setState(sessionState, session.positionMs, speed)
    if (session.status == PlaybackStatus.Failed && session.errorMessage != null) {
      stateBuilder.setErrorMessage(session.errorMessage)
    }
    mediaSession.setPlaybackState(stateBuilder.build())
  }

  private fun ensureForegroundPosted(session: PlaybackSession) {
    val track = session.currentTrack ?: return
    val signature = "${track.id}|${session.status}|${cachedArtwork != null}|${cachedArtwork.hashCode()}"
    if (isForegroundStarted && lastNotifiedSignature == signature) return
    lastNotifiedSignature = signature

    val notification = buildMediaNotification(track, session)
    postForegroundNotification(notification)
  }

  private fun postForegroundNotification(notification: Notification) {
    if (!isForegroundStarted) {
      isForegroundStarted = true
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } else {
      notificationManager.notify(NOTIFICATION_ID, notification)
    }
  }

  private fun buildMediaNotification(track: Track, session: PlaybackSession): Notification {
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_NOW_PLAYING, true)
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val prevAction =
      Notification.Action.Builder(
        Icon.createWithResource(this, android.R.drawable.ic_media_previous),
        getString(R.string.cd_previous),
        servicePendingIntent(ACTION_PREV, 1),
      ).build()

    val isPlayingOrConnecting =
      session.status == PlaybackStatus.Playing ||
        session.status == PlaybackStatus.Buffering ||
        session.status == PlaybackStatus.Loading
    val playPauseAction =
      Notification.Action.Builder(
        Icon.createWithResource(
          this,
          if (isPlayingOrConnecting) android.R.drawable.ic_media_pause
          else android.R.drawable.ic_media_play,
        ),
        getString(if (isPlayingOrConnecting) R.string.cd_pause else R.string.cd_play),
        servicePendingIntent(ACTION_PLAY_PAUSE, 2),
      ).build()

    val nextAction =
      Notification.Action.Builder(
        Icon.createWithResource(this, android.R.drawable.ic_media_next),
        getString(R.string.cd_next),
        servicePendingIntent(ACTION_NEXT, 3),
      ).build()

    val stopAction =
      Notification.Action.Builder(
        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
        getString(R.string.notification_action_stop),
        servicePendingIntent(ACTION_STOP, 4),
      ).build()

    val builder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, CHANNEL_ID)
      } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
      }

    builder
      .setSmallIcon(R.drawable.ic_notification_melody)
      .setContentTitle(track.title)
      .setContentText(track.artist)
      .setSubText(getString(R.string.app_name))
      .setContentIntent(contentPendingIntent)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setOngoing(isPlayingOrConnecting)
      .addAction(prevAction)
      .addAction(playPauseAction)
      .addAction(nextAction)
      .addAction(stopAction)
      .setStyle(
        Notification.MediaStyle()
          .setMediaSession(mediaSession.sessionToken)
          .setShowActionsInCompactView(0, 1, 2)
      )

    cachedArtwork?.let { builder.setLargeIcon(it) }
    return builder.build()
  }

  private fun buildPlaceholderNotification(): Notification {
    val builder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, CHANNEL_ID)
      } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
      }
    return builder
      .setSmallIcon(R.drawable.ic_notification_melody)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.now_playing_label))
      .setOngoing(false)
      .build()
  }

  private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, MelodyPlaybackService::class.java).apply { this.action = action }
    return PendingIntent.getService(
      this,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun loadTrackArtworkAsync(track: Track) {
    val artUrl = track.artworkUrl ?: return
    artworkJob?.cancel()
    artworkJob =
      serviceScope.launch(Dispatchers.IO) {
        val request =
          ImageRequest.Builder(this@MelodyPlaybackService)
            .data(artUrl)
            .allowHardware(false)
            .size(256, 256)
            .build()
        val result = runCatching { imageLoader.execute(request) }.getOrNull()
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        if (bitmap != null && lastArtworkTrackId == track.id) {
          serviceScope.launch(Dispatchers.Main.immediate) {
            if (lastArtworkTrackId == track.id) {
              cachedArtwork = bitmap
              val session = playbackController.session.value
              session.currentTrack?.let { current ->
                updateMediaSession(current, session)
                ensureForegroundPosted(session)
              }
            }
          }
        }
      }
  }

  private fun decodeFallbackArt(track: Track): Bitmap? =
    runCatching {
      BitmapFactory.decodeResource(resources, track.fallbackArtRes ?: R.drawable.img_art_daily_discovery)
    }.getOrNull()

  private fun requestAudioFocus() {
    if (hasAudioFocus) return
    val granted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val request =
          audioFocusRequest
            ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
              .setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                  .build()
              )
              .setOnAudioFocusChangeListener(audioFocusListener)
              .build()
              .also { audioFocusRequest = it }
        audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
      } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
          audioFocusListener,
          AudioManager.STREAM_MUSIC,
          AudioManager.AUDIOFOCUS_GAIN,
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
      }
    hasAudioFocus = granted
  }

  private fun abandonAudioFocus() {
    if (!hasAudioFocus) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    } else {
      @Suppress("DEPRECATION")
      audioManager.abandonAudioFocus(audioFocusListener)
    }
    hasAudioFocus = false
  }

  private fun registerNoisyReceiver() {
    if (noisyReceiverRegistered) return
    val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    ContextCompat.registerReceiver(
      this,
      noisyReceiver,
      filter,
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    noisyReceiverRegistered = true
  }

  private fun unregisterNoisyReceiver() {
    if (!noisyReceiverRegistered) return
    runCatching { unregisterReceiver(noisyReceiver) }
    noisyReceiverRegistered = false
  }

  private fun acquireWakeLock() {
    val lock = wakeLock ?: return
    if (!lock.isHeld) {
      lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }
  }

  private fun releaseWakeLock() {
    val lock = wakeLock ?: return
    if (lock.isHeld) {
      runCatching { lock.release() }
    }
  }

  private fun stopPlaybackAndService() {
    pausedByTransientLoss = false
    playbackController.stopAndClearSession()
    releaseWakeLock()
    unregisterNoisyReceiver()
    abandonAudioFocus()
    isForegroundStarted = false
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.notification_channel_playback),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.notification_channel_playback_desc)
          setShowBadge(false)
          lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
      notificationManager.createNotificationChannel(channel)
    }
  }

  companion object {
    private const val TAG = "MelodyPlaybackService"
    private const val CHANNEL_ID = "melody_playback_channel"
    private const val NOTIFICATION_ID = 4107
    private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

    const val EXTRA_OPEN_NOW_PLAYING = "extra_open_now_playing"
    const val ACTION_SYNC = "com.example.melody.action.SYNC"
    const val ACTION_PLAY_PAUSE = "com.example.melody.action.PLAY_PAUSE"
    const val ACTION_NEXT = "com.example.melody.action.NEXT"
    const val ACTION_PREV = "com.example.melody.action.PREV"
    const val ACTION_STOP = "com.example.melody.action.STOP"

    fun startForPlayback(context: Context) {
      val appCtx = context.applicationContext
      val intent = Intent(appCtx, MelodyPlaybackService::class.java).apply { action = ACTION_SYNC }
      try {
        ContextCompat.startForegroundService(appCtx, intent)
      } catch (err: Exception) {
        Log.w(TAG, "Unable to promote playback service to foreground right now", err)
      }
    }
  }
}
