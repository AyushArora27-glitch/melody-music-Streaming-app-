package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.data.youtube.YouTubeApiService
import com.example.data.youtube.YouTubeMapper
import com.example.model.DiscoveryCollection
import com.example.model.HomeFilter
import com.example.model.MoodCard
import com.example.model.Track
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

enum class CatalogSource {
  YouTube,
  PreviewFallback,
  OfflineStarter,
}

data class CatalogBatchResult(
  val tracks: List<Track>,
  val source: CatalogSource,
  val warningMessage: String? = null,
)

class CatalogRepository(
  private val api: ItunesCatalogApi = createDefaultApi(),
  private val youtubeApi: YouTubeApiService = createDefaultYouTubeApi(),
  private val youtubeApiKeyProvider: () -> String = { BuildConfig.YOUTUBE_API_KEY },
) {
  private val searchCache = ConcurrentHashMap<String, List<Track>>()

  val isYouTubeConfigured: Boolean
    get() {
      val key = youtubeApiKeyProvider().trim()
      return key.isNotEmpty() && key != "MY_YOUTUBE_API_KEY" && !key.startsWith("MY_")
    }

  val moodCards: List<MoodCard> =
    listOf(
      MoodCard(
        id = "mood_trending",
        title = "Trending Now",
        subtitle = "Fresh global charts",
        query = "top music hits 2025 official audio",
        artRes = R.drawable.img_hero_banner,
      ),
      MoodCard(
        id = "mood_midnight_focus",
        title = "Midnight Focus",
        subtitle = "Deep ambient & synth",
        query = "ambient electronic focus music",
        artRes = R.drawable.img_art_night_drive,
      ),
      MoodCard(
        id = "mood_euphoric_rave",
        title = "Euphoric Rave",
        subtitle = "High-energy club sets",
        query = "electronic house dance music",
        artRes = R.drawable.img_art_daily_discovery,
      ),
      MoodCard(
        id = "mood_lofi_chill",
        title = "Lo-Fi Chillout",
        subtitle = "Late-night study beats",
        query = "lofi beats chill music",
        artRes = R.drawable.img_art_lofi_chill,
      ),
      MoodCard(
        id = "mood_workout",
        title = "Workout Pulse",
        subtitle = "Driving tempo & bass",
        query = "workout hip hop gym mix",
        artRes = R.drawable.img_art_daily_discovery,
      ),
      MoodCard(
        id = "mood_night_drive",
        title = "Night Drive",
        subtitle = "Neon synth & R&B",
        query = "the weeknd synthwave official audio",
        artRes = R.drawable.img_art_night_drive,
      ),
    )

  val starterTracks: List<Track> =
    listOf(
      Track(
        id = "seed_blinding_lights",
        title = "Blinding Lights",
        artist = "The Weeknd",
        album = "After Hours",
        durationMs = 200_040L,
        fallbackArtRes = R.drawable.img_art_night_drive,
        genre = "R&B/Soul",
      ),
      Track(
        id = "seed_midnight_city",
        title = "Midnight City",
        artist = "M83",
        album = "Hurry Up, We're Dreaming",
        durationMs = 243_000L,
        fallbackArtRes = R.drawable.img_art_daily_discovery,
        genre = "Electronic",
      ),
      Track(
        id = "seed_levitating",
        title = "Levitating",
        artist = "Dua Lipa",
        album = "Future Nostalgia",
        durationMs = 203_000L,
        fallbackArtRes = R.drawable.img_hero_banner,
        genre = "Pop",
      ),
      Track(
        id = "seed_weightless",
        title = "Neon Echoes",
        artist = "Kiasmos",
        album = "Night Drive Essentials",
        durationMs = 218_000L,
        fallbackArtRes = R.drawable.img_art_lofi_chill,
        genre = "Ambient",
      ),
      Track(
        id = "seed_starboy",
        title = "Starboy",
        artist = "The Weeknd feat. Daft Punk",
        album = "Starboy",
        durationMs = 230_000L,
        fallbackArtRes = R.drawable.img_art_night_drive,
        genre = "R&B/Soul",
      ),
      Track(
        id = "seed_instant_crush",
        title = "Instant Crush",
        artist = "Daft Punk feat. Julian Casablancas",
        album = "Random Access Memories",
        durationMs = 337_000L,
        fallbackArtRes = R.drawable.img_art_daily_discovery,
        genre = "Electronic",
      ),
    )

  fun buildDiscoveryCollections(tracks: List<Track>): List<DiscoveryCollection> {
    val pool = tracks.ifEmpty { starterTracks }
    return listOf(
      DiscoveryCollection(
        id = "col_daily_discovery",
        title = "Daily Discovery",
        subtitle = "Fresh sonic blends picked for your rotation",
        eyebrow = "CURATED MIX",
        filterTag = HomeFilter.Mixes,
        artRes = R.drawable.img_art_daily_discovery,
        tracks = pool.take(4),
      ),
      DiscoveryCollection(
        id = "col_night_drive",
        title = "Night Drive Essentials",
        subtitle = "Velvet synth, basslines & late-hour grooves",
        eyebrow = "MOOD SESSION",
        filterTag = HomeFilter.Energy,
        artRes = R.drawable.img_art_night_drive,
        tracks = pool.reversed().take(4),
      ),
      DiscoveryCollection(
        id = "col_lofi_chill",
        title = "Lo-Fi Chillout",
        subtitle = "Warm textures for slow evenings and flow",
        eyebrow = "CHILL",
        filterTag = HomeFilter.Chill,
        artRes = R.drawable.img_art_lofi_chill,
        tracks = pool.drop(1).ifEmpty { pool }.take(4),
      ),
      DiscoveryCollection(
        id = "col_midnight_focus",
        title = "Midnight Focus",
        subtitle = "Steady atmospheric rhythms without distraction",
        eyebrow = "FOCUS",
        filterTag = HomeFilter.Focus,
        artRes = R.drawable.img_hero_banner,
        tracks = pool.take(5),
      ),
    )
  }

  suspend fun fetchFeaturedCatalog(): Result<CatalogBatchResult> =
    withContext(Dispatchers.IO) {
      if (isYouTubeConfigured) {
        val ytResult = fetchYouTubePopularMusic()
        if (ytResult.isSuccess) {
          val tracks = ytResult.getOrNull().orEmpty()
          if (tracks.isNotEmpty()) {
            return@withContext Result.success(
              CatalogBatchResult(tracks = tracks, source = CatalogSource.YouTube)
            )
          }
        }
        val ytFailure = ytResult.exceptionOrNull()
        val ytWarning = extractYouTubeErrorMessage(ytFailure)
        val itunesFallback = searchItunes("the weeknd daft punk dua lipa", limit = 12)
        if (itunesFallback.isSuccess) {
          return@withContext Result.success(
            CatalogBatchResult(
              tracks = itunesFallback.getOrNull().orEmpty(),
              source = CatalogSource.PreviewFallback,
              warningMessage = ytWarning,
            )
          )
        }
        return@withContext Result.failure(ytFailure ?: Exception("Catalog unavailable"))
      } else {
        searchItunes("the weeknd daft punk dua lipa", limit = 12).map { tracks ->
          CatalogBatchResult(
            tracks = tracks,
            source = CatalogSource.PreviewFallback,
            warningMessage = null,
          )
        }
      }
    }

  suspend fun searchCatalog(query: String, limit: Int = 18): Result<CatalogBatchResult> =
    withContext(Dispatchers.IO) {
      val cleanQuery = query.trim()
      if (cleanQuery.isEmpty()) {
        return@withContext Result.success(CatalogBatchResult(emptyList(), CatalogSource.YouTube))
      }

      val cacheKey = cleanQuery.lowercase()
      searchCache[cacheKey]?.let { cached ->
        val source =
          if (cached.any { it.isYouTubeTrack }) CatalogSource.YouTube
          else CatalogSource.PreviewFallback
        return@withContext Result.success(CatalogBatchResult(tracks = cached, source = source))
      }

      if (isYouTubeConfigured) {
        val ytResult = searchYouTubeMusic(cleanQuery, limit)
        if (ytResult.isSuccess) {
          val tracks = ytResult.getOrNull().orEmpty()
          if (tracks.isNotEmpty()) {
            searchCache[cacheKey] = tracks
            return@withContext Result.success(
              CatalogBatchResult(tracks = tracks, source = CatalogSource.YouTube)
            )
          }
        }
        val ytError = ytResult.exceptionOrNull()
        val ytWarning = extractYouTubeErrorMessage(ytError)
        val fallback = searchItunes(cleanQuery, limit)
        if (fallback.isSuccess) {
          val tracks = fallback.getOrNull().orEmpty()
          return@withContext Result.success(
            CatalogBatchResult(
              tracks = tracks,
              source = CatalogSource.PreviewFallback,
              warningMessage = ytWarning,
            )
          )
        }
        return@withContext Result.failure(ytError ?: Exception("Search unavailable"))
      } else {
        searchItunes(cleanQuery, limit).map { tracks ->
          CatalogBatchResult(tracks = tracks, source = CatalogSource.PreviewFallback)
        }
      }
    }

  private suspend fun fetchYouTubePopularMusic(): Result<List<Track>> = runCatching {
    val apiKey = youtubeApiKeyProvider().trim()
    val region = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
    val primary =
      runCatching {
        youtubeApi.getPopularMusicVideos(maxResults = 16, regionCode = region, apiKey = apiKey)
      }
    val response =
      if (primary.isSuccess && primary.getOrNull()?.items?.isNotEmpty() == true) {
        primary.getOrThrow()
      } else if (region != "US") {
        youtubeApi.getPopularMusicVideos(maxResults = 16, regionCode = "US", apiKey = apiKey)
      } else {
        primary.getOrThrow()
      }
    response.items.mapNotNull { YouTubeMapper.toTrack(it) }
  }

  private suspend fun searchYouTubeMusic(query: String, limit: Int): Result<List<Track>> =
    runCatching {
      val apiKey = youtubeApiKeyProvider().trim()
      val searchResp =
        youtubeApi.searchVideos(
          query = query,
          videoCategoryId = "10",
          maxResults = limit.coerceIn(5, 20),
          apiKey = apiKey,
        )
      val items =
        if (searchResp.items.isNotEmpty()) {
          searchResp.items
        } else {
          youtubeApi.searchVideos(
            query = query,
            videoCategoryId = null,
            maxResults = limit.coerceIn(5, 20),
            apiKey = apiKey,
          ).items
        }

      val videoIds = items.mapNotNull { it.id.videoId?.trim() }.distinct()
      if (videoIds.isEmpty()) return@runCatching emptyList()

      val detailsById =
        runCatching {
          youtubeApi.getVideoDetails(ids = videoIds.joinToString(","), apiKey = apiKey).items
            .associateBy { it.id }
        }.onFailure { err ->
          Log.w("CatalogRepository", "YouTube video details enrichment failed", err)
        }.getOrDefault(emptyMap())

      items.mapNotNull { item ->
        val vid = item.id.videoId?.trim() ?: return@mapNotNull null
        val detailed = detailsById[vid]
        if (detailed != null) YouTubeMapper.toTrack(detailed)
        else YouTubeMapper.toFallbackTrack(item)
      }
    }

  private suspend fun searchItunes(query: String, limit: Int = 20): Result<List<Track>> =
    runCatching {
      val response = api.searchTracks(term = query.trim(), limit = limit)
      response.results
        .filter { !it.trackName.isNullOrBlank() && !it.artistName.isNullOrBlank() }
        .mapIndexed { idx, dto ->
          val highResArt =
            dto.artworkUrl100?.replace("100x100bb", "600x600bb")?.replace("100x100", "600x600")
          Track(
            id = "itunes_${dto.trackId ?: "${query.hashCode()}_$idx"}",
            title = dto.trackName.orEmpty(),
            artist = dto.artistName.orEmpty(),
            album = dto.collectionName ?: "Single",
            durationMs = dto.trackTimeMillis ?: 30_000L,
            artworkUrl = highResArt,
            fallbackArtRes = R.drawable.img_art_daily_discovery,
            previewUrl = dto.previewUrl,
            genre = dto.primaryGenreName ?: "Music",
            explicit = dto.trackExplicitness.equals("explicit", ignoreCase = true),
          )
        }
    }

  suspend fun resolveStreamableTrack(track: Track): Track {
    if (track.isYouTubeTrack) return track
    if (isYouTubeConfigured) {
      val query = "${cleanSongQuery(track.title)} ${track.artist} official audio"
      val ytMatch = withContext(Dispatchers.IO) {
        searchYouTubeMusic(query, limit = 3).getOrNull()?.firstOrNull()
      }
      if (ytMatch != null) {
        return ytMatch.copy(
          title = track.title,
          artist = track.artist,
          artworkUrl = track.artworkUrl ?: ytMatch.artworkUrl,
        )
      }
    }
    if (!track.previewUrl.isNullOrBlank()) return track
    return resolveAudioFallback(track)
  }

  suspend fun resolveAlternativeYouTubeTrack(track: Track, excludeVideoId: String): Track? {
    if (!isYouTubeConfigured) return null
    val cleanTitle = cleanSongQuery(track.title)
    val query = "$cleanTitle ${track.artist} lyrics audio".trim()
    val candidates =
      withContext(Dispatchers.IO) {
        searchYouTubeMusic(query, limit = 5).getOrNull().orEmpty()
      }
    return candidates.firstOrNull {
      val vid = it.youtubeVideoId
      vid != null && vid != excludeVideoId
    }
  }

  suspend fun resolveAudioFallback(track: Track): Track {
    val query = "${cleanSongQuery(track.title)} ${track.artist}".trim()
    val remote = withContext(Dispatchers.IO) {
      searchItunes(query, limit = 1).getOrNull()?.firstOrNull()
        ?: searchItunes(cleanSongQuery(track.title), limit = 1).getOrNull()?.firstOrNull()
    }
    return if (remote != null && !remote.previewUrl.isNullOrBlank()) {
      Track(
        id = remote.id,
        title = track.title,
        artist = track.artist,
        album = track.album,
        durationMs = remote.durationMs,
        artworkUrl = track.artworkUrl ?: remote.artworkUrl,
        fallbackArtRes = track.fallbackArtRes ?: remote.fallbackArtRes,
        previewUrl = remote.previewUrl,
        genre = track.genre,
        explicit = track.explicit,
      )
    } else {
      track
    }
  }

  private fun cleanSongQuery(rawTitle: String): String =
    rawTitle
      .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
      .replace(Regex("(?i)\\b(official|music|video|audio|lyric|lyrics|hd|4k|mv|visualizer)\\b"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
      .ifBlank { rawTitle.trim() }

  private fun extractYouTubeErrorMessage(err: Throwable?): String {
    if (err == null) return "YouTube API unavailable"
    if (err is HttpException) {
      val rawBody = runCatching { err.response()?.errorBody()?.string() }.getOrNull().orEmpty()
      val apiMsg =
        Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
          .find(rawBody)
          ?.groupValues
          ?.getOrNull(1)
          ?.replace("\\n", " ")
          ?.trim()
      if (!apiMsg.isNullOrBlank()) {
        return "HTTP ${err.code()}: $apiMsg"
      }
      return "YouTube HTTP ${err.code()}"
    }
    return err.localizedMessage ?: "YouTube API request failed"
  }

  companion object {
    private const val ITUNES_BASE_URL = "https://itunes.apple.com/"
    private const val YOUTUBE_BASE_URL = "https://www.googleapis.com/youtube/v3/"

    private fun sharedHttpClient(): OkHttpClient {
      val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
      return OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    }

    private fun sharedMoshi(): Moshi =
      Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun createDefaultApi(): ItunesCatalogApi =
      Retrofit.Builder()
        .baseUrl(ITUNES_BASE_URL)
        .client(sharedHttpClient())
        .addConverterFactory(MoshiConverterFactory.create(sharedMoshi()))
        .build()
        .create(ItunesCatalogApi::class.java)

    fun createDefaultYouTubeApi(): YouTubeApiService =
      Retrofit.Builder()
        .baseUrl(YOUTUBE_BASE_URL)
        .client(sharedHttpClient())
        .addConverterFactory(MoshiConverterFactory.create(sharedMoshi()))
        .build()
        .create(YouTubeApiService::class.java)
  }
}
