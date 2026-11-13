package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutFromMelody
import com.example.data.UserPreferencesStore
import com.example.data.firebase.MelodyLibraryRepository
import com.example.model.AppTab
import com.example.model.PlaybackStatus
import com.example.model.ViewportClass
import com.example.player.MelodyPlaybackService
import com.example.player.MelodyPlayerEngine
import com.example.ui.MelodyViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.MelodyBottomNav
import com.example.ui.components.MelodyNavRail
import com.example.ui.components.MelodyTopBar
import com.example.ui.components.PersistentMiniPlayer
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.QueueBottomSheet
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.YouTubeVideoOverlayControls
import com.example.ui.theme.MelodyTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
  private var openPlayerSignal by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.getBooleanExtra(MelodyPlaybackService.EXTRA_OPEN_NOW_PLAYING, false) == true) {
      openPlayerSignal++
    }
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent {
      MelodyTheme {
        MelodyRootNavigation(openPlayerSignal = openPlayerSignal)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.getBooleanExtra(MelodyPlaybackService.EXTRA_OPEN_NOW_PLAYING, false)) {
      openPlayerSignal++
    }
  }
}

@Composable
fun MelodyRootNavigation(
  openPlayerSignal: Int = 0,
  auth: FirebaseAuth = Firebase.auth,
) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  LaunchedEffect(user?.uid) {
    if (user == null) {
      MelodyPlayerEngine.stopIfInitialized()
    }
  }

  if (user == null) {
    AuthScreen(
      onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ },
    )
  } else {
    key(user.uid) {
      MelodyAuthenticatedApp(
        currentUser = user,
        openPlayerSignal = openPlayerSignal,
      )
    }
  }
}

@Composable
fun MelodyAuthenticatedApp(
  currentUser: FirebaseUser,
  openPlayerSignal: Int = 0,
) {
  val uid = currentUser.uid
  val displayName =
    currentUser.displayName?.takeIf { it.isNotBlank() }
      ?: currentUser.email?.substringBefore("@")
      ?: "Melody Listener"
  val userEmail = currentUser.email
  val userInitial = displayName.take(1).uppercase()

  val viewModel: MelodyViewModel =
    viewModel(
      key = uid,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val repo = MelodyLibraryRepository(db = db, auth = Firebase.auth)
            val prefs = UserPreferencesStore(app.applicationContext)
            val playbackController = MelodyPlayerEngine.getController(app.applicationContext)
            MelodyViewModel(
              currentUserId = uid,
              userDisplayName = displayName,
              userEmail = userEmail,
              libraryRepository = repo,
              prefsStore = prefs,
              playbackController = playbackController,
            )
          }
        },
    )

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val density = LocalDensity.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val session by viewModel.playbackSession.collectAsStateWithLifecycle()
  val likedIds = remember(uiState.likedTracks) { uiState.likedTracks.map { it.id }.toSet() }
  val greetingResId = remember { viewModel.greetingResKey() }
  val snackbarHostState = remember { SnackbarHostState() }
  var videoStageBounds by remember { mutableStateOf<Rect?>(null) }
  var askedNotificationPermission by remember { mutableStateOf(false) }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

  LaunchedEffect(openPlayerSignal, session.isActive) {
    if (openPlayerSignal > 0 && session.isActive) {
      viewModel.setNowPlayingExpanded(true)
    }
  }

  LaunchedEffect(session.isActive) {
    if (
      session.isActive &&
        !askedNotificationPermission &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
      askedNotificationPermission = true
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_START) {
        viewModel.resumeFromBackground()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(uiState.cloudSyncError) {
    val err = uiState.cloudSyncError
    if (err != null) {
      snackbarHostState.showSnackbar(err)
      viewModel.clearCloudSyncError()
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val viewportClass =
      when {
        maxWidth < 600.dp -> ViewportClass.Compact
        maxWidth < 840.dp -> ViewportClass.Medium
        else -> ViewportClass.Expanded
      }
    val useNavRail = viewportClass != ViewportClass.Compact

    Scaffold(
      containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavRail) {
          Column(modifier = Modifier.fillMaxWidth()) {
            if (session.isActive) {
              PersistentMiniPlayer(
                session = session,
                isLiked = session.currentTrack?.id?.let { likedIds.contains(it) } == true,
                onExpand = { viewModel.setNowPlayingExpanded(true) },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onToggleLike = { session.currentTrack?.let { viewModel.toggleLike(it) } },
                onSkipNext = { viewModel.skipNext() },
              )
            }
            MelodyBottomNav(
              activeTab = uiState.activeTab,
              onSelectTab = { viewModel.selectTab(it) },
            )
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (useNavRail) {
          MelodyNavRail(
            activeTab = uiState.activeTab,
            onSelectTab = { viewModel.selectTab(it) },
          )
        }

        Column(
          modifier =
            Modifier.weight(1f)
              .fillMaxHeight()
              .windowInsetsPadding(WindowInsets.statusBars),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier.weight(1f)
                .fillMaxWidth()
                .widthIn(max = 1040.dp),
          ) {
            Column(modifier = Modifier.fillMaxSize()) {
              if (uiState.selectedCollection == null) {
                MelodyTopBar(
                  catalogSource = uiState.activeCatalogSource,
                  offlineNotice = uiState.homeOfflineNotice,
                  userInitial = userInitial,
                  userDisplayName = displayName,
                  onSignOut = { signOutFromMelody(credentialManager, coroutineScope) },
                )
              }

              val activeDetail = uiState.selectedCollection
              if (activeDetail != null) {
                CollectionDetailScreen(
                  detail = activeDetail,
                  currentTrackId = session.currentTrack?.id,
                  likedTrackIds = likedIds,
                  onBack = { viewModel.closeCollectionDetail() },
                  onPlayAll = { viewModel.playCollection(activeDetail.tracks) },
                  onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                  onToggleLike = { viewModel.toggleLike(it) },
                  onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                  modifier = Modifier.weight(1f),
                )
              } else {
                when (uiState.activeTab) {
                  AppTab.Home ->
                    HomeScreen(
                      uiState = uiState,
                      greetingResId = greetingResId,
                      currentTrackId = session.currentTrack?.id,
                      viewportClass = viewportClass,
                      onSelectFilter = { viewModel.selectHomeFilter(it) },
                      onPlayMix = {
                        viewModel.playCollection(uiState.featuredTracks)
                        viewModel.setNowPlayingExpanded(true)
                      },
                      onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                      onOpenCollection = { viewModel.openDiscoveryCollection(it) },
                      onPlayCollection = { col -> viewModel.playCollection(col.tracks) },
                      onToggleLike = { viewModel.toggleLike(it) },
                      onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                      onRefresh = { viewModel.refreshHomeCatalog() },
                      modifier = Modifier.weight(1f),
                    )
                  AppTab.Search ->
                    SearchScreen(
                      uiState = uiState,
                      currentTrackId = session.currentTrack?.id,
                      viewportClass = viewportClass,
                      onQueryChange = { viewModel.updateSearchQuery(it) },
                      onSubmitSearch = { viewModel.submitSearch(it) },
                      onSelectMood = { viewModel.runMoodSearch(it) },
                      onRemoveRecent = { viewModel.removeRecentSearch(it) },
                      onClearAllRecent = { viewModel.clearAllRecentSearches() },
                      onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                      onToggleLike = { viewModel.toggleLike(it) },
                      onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
                      modifier = Modifier.weight(1f),
                    )
                  AppTab.Library ->
                    LibraryScreen(
                      uiState = uiState,
                      viewportClass = viewportClass,
                      onSelectCategory = { viewModel.selectLibraryCategory(it) },
                      onSearchQueryChange = { viewModel.updateLibrarySearchQuery(it) },
                      onToggleSort = { viewModel.toggleLibrarySortOrder() },
                      onToggleGridView = { viewModel.toggleLibraryGridView() },
                      onNewPlaylist = { viewModel.setShowCreatePlaylistDialog(true) },
                      onOpenLikedSongs = { viewModel.openLikedSongsDetail() },
                      onTogglePinLiked = { viewModel.togglePinLikedSongs() },
                      onOpenPlaylist = { viewModel.openPlaylistDetail(it) },
                      onTogglePinPlaylist = { viewModel.togglePinPlaylist(it) },
                      modifier = Modifier.weight(1f),
                    )
                }
              }
            }
          }

          if (useNavRail && session.isActive) {
            PersistentMiniPlayer(
              session = session,
              isLiked = session.currentTrack?.id?.let { likedIds.contains(it) } == true,
              onExpand = { viewModel.setNowPlayingExpanded(true) },
              onTogglePlayPause = { viewModel.togglePlayPause() },
              onToggleLike = { session.currentTrack?.let { viewModel.toggleLike(it) } },
              onSkipNext = { viewModel.skipNext() },
            )
          }
        }
      }
    }

    AnimatedVisibility(
      visible = uiState.isNowPlayingExpanded && session.isActive,
      enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
      modifier = Modifier.zIndex(1f),
    ) {
      NowPlayingScreen(
        session = session,
        isLiked = session.currentTrack?.id?.let { likedIds.contains(it) } == true,
        isVideoMode = uiState.isYouTubeVideoMode,
        isFullscreenVideo = uiState.isFullscreenVideo,
        onToggleVideoMode = { viewModel.setYouTubeVideoMode(it) },
        onToggleFullscreenVideo = { viewModel.setFullscreenVideo(it) },
        onVideoStageBoundsChanged = { videoStageBounds = it },
        onOpenExternalYouTube = { track ->
          val vid = track.youtubeVideoId ?: return@NowPlayingScreen
          runCatching {
            context.startActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$vid"))
            )
          }
        },
        onCollapse = { viewModel.setNowPlayingExpanded(false) },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSeekTo = { viewModel.seekTo(it) },
        onSkipNext = { viewModel.skipNext() },
        onSkipPrevious = { viewModel.skipPrevious() },
        onToggleShuffle = { viewModel.toggleShuffle() },
        onCycleRepeat = { viewModel.cycleRepeatMode() },
        onToggleLike = { session.currentTrack?.let { viewModel.toggleLike(it) } },
        onOpenQueue = { viewModel.setQueueSheetOpen(true) },
        onAddToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
      )
    }

    val activeStageBounds =
      videoStageBounds?.takeIf {
        uiState.isNowPlayingExpanded &&
          session.currentTrack?.isYouTubeTrack == true &&
          uiState.isYouTubeVideoMode &&
          !session.isPreviewFallback &&
          session.status != PlaybackStatus.Ended &&
          session.status != PlaybackStatus.Failed &&
          !uiState.isQueueSheetOpen &&
          uiState.trackForPlaylistDialog == null &&
          !uiState.showCreatePlaylistDialog &&
          !it.isEmpty
      }

    val playerModifier =
      if (activeStageBounds != null) {
        val widthDp = with(density) { activeStageBounds.width.toDp() }
        val heightDp = with(density) { activeStageBounds.height.toDp() }
        Modifier.zIndex(2f)
          .offset {
            IntOffset(activeStageBounds.left.roundToInt(), activeStageBounds.top.roundToInt())
          }
          .size(width = widthDp, height = heightDp)
          .let { base ->
            if (uiState.isFullscreenVideo) base else base.clip(RoundedCornerShape(20.dp))
          }
      } else {
        // Keep inside viewport bounds behind the opaque Scaffold/NowPlaying surface
        // so Chromium and YouTube's IntersectionObserver do not pause background audio.
        Modifier.zIndex(-1f)
          .size(width = 240.dp, height = 135.dp)
      }

    val hasUsedYouTube = remember { mutableStateOf(false) }
    if (session.currentTrack?.isYouTubeTrack == true) {
      hasUsedYouTube.value = true
    }

    if (hasUsedYouTube.value) {
      AndroidView(
        factory = { ctx ->
          viewModel.youTubeBridge.createConfiguredWebView(ctx).also { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
          }
        },
        update = { webView ->
          if (activeStageBounds != null) {
            webView.requestLayout()
          }
        },
        modifier = playerModifier,
      )
    }

    if (activeStageBounds != null) {
      val widthDp = with(density) { activeStageBounds.width.toDp() }
      val heightDp = with(density) { activeStageBounds.height.toDp() }
      YouTubeVideoOverlayControls(
        session = session,
        isFullscreen = uiState.isFullscreenVideo,
        isLiked = session.currentTrack?.id?.let { likedIds.contains(it) } == true,
        onToggleFullscreen = { viewModel.setFullscreenVideo(it) },
        onSwitchToSongMode = { viewModel.setYouTubeVideoMode(false) },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSeekTo = { viewModel.seekTo(it) },
        onSkipNext = { viewModel.skipNext() },
        onSkipPrevious = { viewModel.skipPrevious() },
        onToggleLike = { session.currentTrack?.let { viewModel.toggleLike(it) } },
        onOpenQueue = { viewModel.setQueueSheetOpen(true) },
        modifier =
          Modifier.zIndex(3f)
            .offset {
              IntOffset(activeStageBounds.left.roundToInt(), activeStageBounds.top.roundToInt())
            }
            .size(width = widthDp, height = heightDp)
            .let { base ->
              if (uiState.isFullscreenVideo) base else base.clip(RoundedCornerShape(20.dp))
            },
      )
    }
  }

  if (uiState.isQueueSheetOpen && session.isActive) {
    QueueBottomSheet(
      session = session,
      onDismiss = { viewModel.setQueueSheetOpen(false) },
      onPlayAtIndex = { idx -> viewModel.playCollection(session.queue, idx) },
      onRemoveAtIndex = { idx -> viewModel.removeFromQueue(idx) },
      onMoveItem = { from, to -> viewModel.moveQueueItem(from, to) },
    )
  }

  if (uiState.showCreatePlaylistDialog) {
    CreatePlaylistDialog(
      onDismiss = { viewModel.setShowCreatePlaylistDialog(false) },
      onCreate = { name -> viewModel.createPlaylist(name) },
    )
  }

  val pendingTrack = uiState.trackForPlaylistDialog
  if (pendingTrack != null) {
    AddToPlaylistDialog(
      track = pendingTrack,
      playlists = uiState.playlists,
      onDismiss = { viewModel.openAddToPlaylistDialog(null) },
      onSelectPlaylist = { playlist -> viewModel.addTrackToPlaylist(pendingTrack, playlist.id) },
      onCreateNewPlaylist = {
        viewModel.openAddToPlaylistDialog(null)
        viewModel.setShowCreatePlaylistDialog(true)
      },
    )
  }
}
