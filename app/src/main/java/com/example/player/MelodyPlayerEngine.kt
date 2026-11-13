package com.example.player

import android.content.Context
import com.example.data.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped playback holder so Activity recreation, configuration changes,
 * and background playback in MelodyPlaybackService share one persistent engine.
 */
object MelodyPlayerEngine {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  @Volatile private var controller: PlaybackController? = null

  fun getController(context: Context): PlaybackController {
    val existing = controller
    if (existing != null) return existing
    return synchronized(this) {
      controller
        ?: PlaybackController(
          scope = appScope,
          catalogRepository = CatalogRepository(),
          onForegroundPlaybackRequested = {
            MelodyPlaybackService.startForPlayback(context.applicationContext)
          },
        ).also { controller = it }
    }
  }

  fun stopIfInitialized() {
    controller?.stopAndClearSession()
  }
}
