package com.example.data.youtube

import com.example.data.CatalogRepository
import com.example.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeMapperTest {
  @Test
  fun parseIsoDurationMs_parsesHoursMinutesSeconds() {
    assertEquals(225_000L, YouTubeMapper.parseIsoDurationMs("PT3M45S"))
    assertEquals(3_661_000L, YouTubeMapper.parseIsoDurationMs("PT1H1M1S"))
    assertEquals(58_000L, YouTubeMapper.parseIsoDurationMs("PT58S"))
    assertEquals(12_000L, YouTubeMapper.parseIsoDurationMs("PT12S"))
    assertEquals(0L, YouTubeMapper.parseIsoDurationMs("PT0S"))
  }

  @Test
  fun formatViewCount_formatsMillionsAndThousands() {
    assertEquals("14.5M views", YouTubeMapper.formatViewCount("14500000"))
    assertEquals("842.0K views", YouTubeMapper.formatViewCount("842000"))
    assertEquals("1.2B views", YouTubeMapper.formatViewCount("1200000000"))
  }

  @Test
  fun trackYoutubeVideoId_detectsPrefixedIdAndWatchUrl() {
    val ytTrack =
      Track(
        id = "yt_dQw4w9WgXcQ",
        title = "Never Gonna Give You Up",
        artist = "Rick Astley",
        album = "YouTube Music",
        durationMs = 213_000L,
        previewUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      )
    assertTrue(ytTrack.isYouTubeTrack)
    assertEquals("dQw4w9WgXcQ", ytTrack.youtubeVideoId)

    val itunesTrack =
      Track(
        id = "itunes_12345",
        title = "Midnight City",
        artist = "M83",
        album = "Hurry Up",
        durationMs = 30_000L,
        previewUrl = "https://audio-ssl.itunes.apple.com/preview.m4a",
      )
    assertFalse(itunesTrack.isYouTubeTrack)
    assertNull(itunesTrack.youtubeVideoId)
  }

  @Test
  fun catalogRepository_rejectsPlaceholderKey() {
    val unconfigured = CatalogRepository(youtubeApiKeyProvider = { "MY_YOUTUBE_API_KEY" })
    assertFalse(unconfigured.isYouTubeConfigured)

    val emptyKey = CatalogRepository(youtubeApiKeyProvider = { "" })
    assertFalse(emptyKey.isYouTubeConfigured)

    val configured = CatalogRepository(youtubeApiKeyProvider = { "test-valid-key-token" })
    assertTrue(configured.isYouTubeConfigured)
  }

  @Test
  fun toTrack_filtersNonEmbeddableVideos() {
    val nonEmbeddable =
      YouTubeVideoItem(
        id = "abc123XYZ00",
        snippet = YouTubeSnippet(title = "Blocked Video", channelTitle = "Label"),
        contentDetails = YouTubeContentDetails(duration = "PT3M10S"),
        status = YouTubeVideoStatus(embeddable = false),
      )
    assertNull(YouTubeMapper.toTrack(nonEmbeddable))

    val embeddable = nonEmbeddable.copy(status = YouTubeVideoStatus(embeddable = true))
    val mapped = YouTubeMapper.toTrack(embeddable)
    assertEquals("yt_abc123XYZ00", mapped?.id)
    assertEquals(190_000L, mapped?.durationMs)
  }
}
