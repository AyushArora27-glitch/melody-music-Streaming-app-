package com.example.data

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

data class ItunesSearchResponse(
  @param:Json(name = "resultCount") val resultCount: Int = 0,
  @param:Json(name = "results") val results: List<ItunesTrackDto> = emptyList(),
)

data class ItunesTrackDto(
  @param:Json(name = "trackId") val trackId: Long? = null,
  @param:Json(name = "trackName") val trackName: String? = null,
  @param:Json(name = "artistName") val artistName: String? = null,
  @param:Json(name = "collectionName") val collectionName: String? = null,
  @param:Json(name = "artworkUrl100") val artworkUrl100: String? = null,
  @param:Json(name = "previewUrl") val previewUrl: String? = null,
  @param:Json(name = "trackTimeMillis") val trackTimeMillis: Long? = null,
  @param:Json(name = "primaryGenreName") val primaryGenreName: String? = null,
  @param:Json(name = "trackExplicitness") val trackExplicitness: String? = null,
)

interface ItunesCatalogApi {
  @GET("search")
  suspend fun searchTracks(
    @Query("term") term: String,
    @Query("media") media: String = "music",
    @Query("entity") entity: String = "song",
    @Query("limit") limit: Int = 20,
  ): ItunesSearchResponse
}
