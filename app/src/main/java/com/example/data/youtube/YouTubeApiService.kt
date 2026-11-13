package com.example.data.youtube

import androidx.core.text.HtmlCompat
import com.example.R
import com.example.model.Track
import com.squareup.moshi.Json
import java.util.Locale
import retrofit2.http.GET
import retrofit2.http.Query

data class YouTubeSearchResponse(
  @param:Json(name = "items") val items: List<YouTubeSearchItem> = emptyList(),
)

data class YouTubeSearchItem(
  @param:Json(name = "id") val id: YouTubeResourceId = YouTubeResourceId(),
  @param:Json(name = "snippet") val snippet: YouTubeSnippet = YouTubeSnippet(),
)

data class YouTubeResourceId(
  @param:Json(name = "kind") val kind: String? = null,
  @param:Json(name = "videoId") val videoId: String? = null,
)

data class YouTubeVideosResponse(
  @param:Json(name = "items") val items: List<YouTubeVideoItem> = emptyList(),
)

data class YouTubeVideoItem(
  @param:Json(name = "id") val id: String = "",
  @param:Json(name = "snippet") val snippet: YouTubeSnippet = YouTubeSnippet(),
  @param:Json(name = "contentDetails") val contentDetails: YouTubeContentDetails? = null,
  @param:Json(name = "statistics") val statistics: YouTubeStatistics? = null,
  @param:Json(name = "status") val status: YouTubeVideoStatus? = null,
)

data class YouTubeVideoStatus(
  @param:Json(name = "embeddable") val embeddable: Boolean = true,
)

data class YouTubeSnippet(
  @param:Json(name = "title") val title: String = "",
  @param:Json(name = "channelTitle") val channelTitle: String = "",
  @param:Json(name = "thumbnails") val thumbnails: YouTubeThumbnails? = null,
  @param:Json(name = "categoryId") val categoryId: String? = null,
)

data class YouTubeThumbnails(
  @param:Json(name = "default") val defaultThumb: YouTubeThumbnail? = null,
  @param:Json(name = "medium") val medium: YouTubeThumbnail? = null,
  @param:Json(name = "high") val high: YouTubeThumbnail? = null,
  @param:Json(name = "standard") val standard: YouTubeThumbnail? = null,
  @param:Json(name = "maxres") val maxres: YouTubeThumbnail? = null,
) {
  fun bestUrl(): String? =
    maxres?.url ?: standard?.url ?: high?.url ?: medium?.url ?: defaultThumb?.url
}

data class YouTubeThumbnail(
  @param:Json(name = "url") val url: String? = null,
)

data class YouTubeContentDetails(
  @param:Json(name = "duration") val duration: String? = null,
)

data class YouTubeStatistics(
  @param:Json(name = "viewCount") val viewCount: String? = null,
)

interface YouTubeApiService {
  @GET("search")
  suspend fun searchVideos(
    @Query("part") part: String = "snippet",
    @Query("type") type: String = "video",
    @Query("videoCategoryId") videoCategoryId: String? = "10",
    @Query("videoEmbeddable") videoEmbeddable: String = "true",
    @Query("maxResults") maxResults: Int = 18,
    @Query("q") query: String,
    @Query("key") apiKey: String,
  ): YouTubeSearchResponse

  @GET("videos")
  suspend fun getPopularMusicVideos(
    @Query("part") part: String = "snippet,contentDetails,statistics,status",
    @Query("chart") chart: String = "mostPopular",
    @Query("videoCategoryId") videoCategoryId: String = "10",
    @Query("regionCode") regionCode: String = "US",
    @Query("maxResults") maxResults: Int = 20,
    @Query("key") apiKey: String,
  ): YouTubeVideosResponse

  @GET("videos")
  suspend fun getVideoDetails(
    @Query("part") part: String = "snippet,contentDetails,statistics,status",
    @Query("id") ids: String,
    @Query("key") apiKey: String,
  ): YouTubeVideosResponse
}

object YouTubeMapper {
  private val isoDurationRegex = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")

  fun parseIsoDurationMs(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0L
    val match = isoDurationRegex.matchEntire(iso.trim()) ?: return 0L
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: 0L
    val seconds = match.groupValues[3].toLongOrNull() ?: 0L
    val totalMs = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
    return totalMs.coerceAtLeast(0L)
  }

  fun formatViewCount(raw: String?): String? {
    val count = raw?.toLongOrNull() ?: return null
    return when {
      count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB views", count / 1_000_000_000.0)
      count >= 1_000_000L -> String.format(Locale.US, "%.1fM views", count / 1_000_000.0)
      count >= 1_000L -> String.format(Locale.US, "%.1fK views", count / 1_000.0)
      else -> "$count views"
    }
  }

  fun decodeHtml(raw: String): String {
    if (!raw.contains('&')) return raw.trim()
    return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
  }

  fun toTrack(item: YouTubeVideoItem): Track? {
    if (item.status?.embeddable == false) return null
    val videoId = item.id.trim()
    if (videoId.isBlank()) return null
    val title = decodeHtml(item.snippet.title)
    if (title.isBlank()) return null
    val channel = decodeHtml(item.snippet.channelTitle).removeSuffix(" - Topic").trim()
      .ifBlank { "YouTube Artist" }
    val views = formatViewCount(item.statistics?.viewCount)
    val durationMs = parseIsoDurationMs(item.contentDetails?.duration)
    return Track(
      id = "yt_$videoId",
      title = title,
      artist = channel,
      album = views ?: "YouTube Music",
      durationMs = durationMs,
      artworkUrl = item.snippet.thumbnails?.bestUrl(),
      fallbackArtRes = R.drawable.img_art_daily_discovery,
      previewUrl = "https://www.youtube.com/watch?v=$videoId",
      genre = "YouTube",
      explicit = false,
    )
  }

  fun toFallbackTrack(item: YouTubeSearchItem): Track? {
    val videoId = item.id.videoId?.trim() ?: return null
    val title = decodeHtml(item.snippet.title)
    if (title.isBlank()) return null
    val channel = decodeHtml(item.snippet.channelTitle).removeSuffix(" - Topic").trim()
      .ifBlank { "YouTube Artist" }
    return Track(
      id = "yt_$videoId",
      title = title,
      artist = channel,
      album = "YouTube Music",
      durationMs = 210_000L,
      artworkUrl = item.snippet.thumbnails?.bestUrl(),
      fallbackArtRes = R.drawable.img_art_daily_discovery,
      previewUrl = "https://www.youtube.com/watch?v=$videoId",
      genre = "YouTube",
      explicit = false,
    )
  }
}
