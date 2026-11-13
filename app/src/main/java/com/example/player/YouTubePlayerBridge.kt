package com.example.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.lang.ref.WeakReference

/**
 * Custom WebView that keeps HTML5/YouTube media active when the Activity enters background
 * and suppresses direct DOM touch chrome so Melody's native YouTube Music controls handle taps.
 */
class BackgroundPlayWebView(context: Context) : WebView(context) {
  @Volatile var keepPlayingInBackground: Boolean = true

  override fun onWindowVisibilityChanged(visibility: Int) {
    if (keepPlayingInBackground && visibility != VISIBLE) {
      super.onWindowVisibilityChanged(VISIBLE)
    } else {
      super.onWindowVisibilityChanged(visibility)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent?): Boolean = false

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true
}

/**
 * Official YouTube IFrame Player JS bridge hosted in a single persistent WebView.
 * Runs playback commands on the main thread and normalizes IFrame events back to PlaybackController.
 */
class YouTubePlayerBridge(
  private val onPlayerReady: () -> Unit,
  private val onStateChanged: (stateCode: Int) -> Unit,
  private val onTimeUpdated: (positionMs: Long, durationMs: Long) -> Unit,
  private val onPlayerError: (errorCode: Int) -> Unit,
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private var webViewRef: WeakReference<BackgroundPlayWebView>? = null
  private var isIframeReady = false
  private var pendingVideoId: String? = null
  private var currentLoadedVideoId: String? = null

  @SuppressLint("SetJavaScriptEnabled")
  fun createConfiguredWebView(context: Context): BackgroundPlayWebView {
    val existing = webViewRef?.get()
    if (existing != null) {
      (existing.parent as? ViewGroup)?.removeView(existing)
      return existing
    }

    val appContext = context.applicationContext
    // YouTube's embed API requires a valid non-YouTube app-scoped HTTPS origin & referrer
    // (using https://www.youtube.com as baseUrl/origin triggers Error 152/153 client identity rejection).
    val appOrigin = "https://${appContext.packageName}"

    val webView =
      BackgroundPlayWebView(appContext).apply {
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        setBackgroundColor(Color.BLACK)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          mediaPlaybackRequiresUserGesture = false
          cacheMode = WebSettings.LOAD_DEFAULT
          useWideViewPort = true
          loadWithOverviewMode = true
          javaScriptCanOpenWindowsAutomatically = false
          userAgentString = userAgentString.replace("; wv", "")
        }
        webChromeClient = WebChromeClient()
        webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?,
            ): Boolean {
              val host = request?.url?.host?.lowercase().orEmpty()
              // Keep YouTube embed/player frame internal; block accidental navigation away
              return !host.contains("youtube.com") &&
                !host.contains("youtube-nocookie.com") &&
                !host.contains("googlevideo.com") &&
                !host.contains("ytimg.com") &&
                !host.contains("google.com")
            }
          }
        addJavascriptInterface(JsBridge(), "MelodyBridge")
        loadDataWithBaseURL(
          appOrigin,
          buildIframeHtml(appOrigin),
          "text/html",
          "utf-8",
          appOrigin,
        )
      }
    webViewRef = WeakReference(webView)
    return webView
  }

  fun setKeepPlayingInBackground(enabled: Boolean) {
    webViewRef?.get()?.keepPlayingInBackground = enabled
  }

  fun loadVideo(videoId: String) {
    val cleanId = videoId.replace(Regex("[^a-zA-Z0-9_\\-]"), "")
    if (cleanId.isBlank()) return
    currentLoadedVideoId = cleanId
    mainHandler.post {
      val webView = webViewRef?.get()
      if (webView == null || !isIframeReady) {
        pendingVideoId = cleanId
      } else {
        pendingVideoId = null
        webView.evaluateJavascript("window.melodyLoadVideo('$cleanId');", null)
      }
    }
  }

  fun play() {
    mainHandler.post {
      webViewRef?.get()?.evaluateJavascript("window.melodyPlay();", null)
    }
  }

  fun pause() {
    mainHandler.post {
      webViewRef?.get()?.evaluateJavascript("window.melodyPause();", null)
    }
  }

  fun stop() {
    pendingVideoId = null
    currentLoadedVideoId = null
    mainHandler.post {
      webViewRef?.get()?.evaluateJavascript("window.melodyStop();", null)
    }
  }

  fun seekTo(positionMs: Long) {
    val seconds = (positionMs.coerceAtLeast(0L) / 1000.0)
    mainHandler.post {
      webViewRef?.get()?.evaluateJavascript("window.melodySeekTo($seconds);", null)
    }
  }

  fun onHostResume() {
    mainHandler.post {
      webViewRef?.get()?.apply {
        onResume()
        resumeTimers()
      }
    }
  }

  fun destroy() {
    pendingVideoId = null
    currentLoadedVideoId = null
    isIframeReady = false
    mainHandler.post {
      webViewRef?.get()?.apply {
        keepPlayingInBackground = false
        stopLoading()
        removeJavascriptInterface("MelodyBridge")
        (parent as? ViewGroup)?.removeView(this)
        destroy()
      }
      webViewRef = null
    }
  }

  private inner class JsBridge {
    @JavascriptInterface
    fun onReady() {
      mainHandler.post {
        isIframeReady = true
        onPlayerReady()
        val queued = pendingVideoId
        if (!queued.isNullOrBlank()) {
          pendingVideoId = null
          webViewRef?.get()?.evaluateJavascript("window.melodyLoadVideo('$queued');", null)
        }
      }
    }

    @JavascriptInterface
    fun onStateChange(stateCode: Int) {
      mainHandler.post { onStateChanged(stateCode) }
    }

    @JavascriptInterface
    fun onTimeUpdate(currentSeconds: Double, durationSeconds: Double) {
      val posMs = (currentSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
      val durMs = (durationSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
      mainHandler.post { onTimeUpdated(posMs, durMs) }
    }

    @JavascriptInterface
    fun onError(errorCode: Int) {
      mainHandler.post { onPlayerError(errorCode) }
    }
  }

  private fun buildIframeHtml(appOrigin: String): String =
    """
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="utf-8" />
        <meta name="referrer" content="strict-origin-when-cross-origin" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
          html, body {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            background-color: #000000;
            overflow: hidden;
          }
          #player, iframe {
            position: fixed;
            inset: 0;
            width: 100% !important;
            height: 100% !important;
            border: 0;
            pointer-events: none;
          }
        </style>
      </head>
      <body>
        <div id="player"></div>
        <script>
          try {
            Object.defineProperty(document, 'hidden', { get: function() { return false; } });
            Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; } });
          } catch (e) {}

          var player = null;
          var timerId = null;

          var tag = document.createElement('script');
          tag.src = "https://www.youtube.com/iframe_api";
          var firstScriptTag = document.getElementsByTagName('script')[0];
          firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

          function enforceIframeAttributes() {
            try {
              if (player && typeof player.getIframe === 'function') {
                var iframe = player.getIframe();
                if (iframe) {
                  iframe.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
                  iframe.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture');
                }
              }
            } catch (e) {}
          }

          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              width: '100%',
              height: '100%',
              playerVars: {
                'autoplay': 1,
                'cc_load_policy': 0,
                'controls': 0,
                'disablekb': 1,
                'enablejsapi': 1,
                'fs': 0,
                'iv_load_policy': 3,
                'modestbranding': 1,
                'playsinline': 1,
                'rel': 0,
                'showinfo': 0,
                'origin': '$appOrigin',
                'widget_referrer': '$appOrigin'
              },
              events: {
                'onReady': function() {
                  enforceIframeAttributes();
                  if (window.MelodyBridge) {
                    window.MelodyBridge.onReady();
                  }
                },
                'onStateChange': function(event) {
                  if (window.MelodyBridge) {
                    window.MelodyBridge.onStateChange(event.data);
                  }
                  if (event.data === YT.PlayerState.PLAYING) {
                    startProgressPoll();
                  } else {
                    stopProgressPoll();
                  }
                },
                'onError': function(event) {
                  stopProgressPoll();
                  if (window.MelodyBridge) {
                    window.MelodyBridge.onError(event.data);
                  }
                }
              }
            });
            enforceIframeAttributes();
          }

          function startProgressPoll() {
            stopProgressPoll();
            timerId = setInterval(function() {
              if (player && typeof player.getCurrentTime === 'function') {
                var cur = player.getCurrentTime() || 0;
                var dur = player.getDuration() || 0;
                if (window.MelodyBridge) {
                  window.MelodyBridge.onTimeUpdate(cur, dur);
                }
              }
            }, 300);
          }

          function stopProgressPoll() {
            if (timerId !== null) {
              clearInterval(timerId);
              timerId = null;
            }
          }

          window.melodyLoadVideo = function(videoId) {
            try {
              enforceIframeAttributes();
              if (player && typeof player.loadVideoById === 'function') {
                player.loadVideoById({ videoId: videoId, startSeconds: 0 });
              }
            } catch (e) {}
          };

          window.melodyPlay = function() {
            try {
              if (player && typeof player.playVideo === 'function') {
                player.playVideo();
              }
            } catch (e) {}
          };

          window.melodyPause = function() {
            try {
              if (player && typeof player.pauseVideo === 'function') {
                player.pauseVideo();
              }
            } catch (e) {}
          };

          window.melodyStop = function() {
            stopProgressPoll();
            try {
              if (player && typeof player.stopVideo === 'function') {
                player.stopVideo();
              }
            } catch (e) {}
          };

          window.melodySeekTo = function(seconds) {
            try {
              if (player && typeof player.seekTo === 'function') {
                player.seekTo(seconds, true);
              }
            } catch (e) {}
          };
        </script>
      </body>
    </html>
    """.trimIndent()

  companion object {
    const val YT_STATE_UNSTARTED = -1
    const val YT_STATE_ENDED = 0
    const val YT_STATE_PLAYING = 1
    const val YT_STATE_PAUSED = 2
    const val YT_STATE_BUFFERING = 3
    const val YT_STATE_CUED = 5
  }
}
