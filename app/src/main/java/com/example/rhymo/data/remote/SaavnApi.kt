package com.rhymo.music.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

internal interface SaavnApi {
    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): SaavnSongSearchResponse
}

internal data class SaavnSongSearchResponse(
    val success: Boolean = false,
    val data: SaavnSearchData? = null
)

internal data class SaavnSearchData(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<SaavnSongDto> = emptyList()
)

internal data class SaavnSongDto(
    val id: String? = null,
    val name: String? = null,
    val duration: Long? = null,
    val label: String? = null,
    val language: String? = null,
    val album: SaavnAlbumDto? = null,
    val artists: SaavnArtistsDto? = null,
    val image: List<SaavnMediaUrlDto> = emptyList(),
    val downloadUrl: List<SaavnMediaUrlDto> = emptyList()
)

internal data class SaavnAlbumDto(
    val id: String? = null,
    val name: String? = null
)

internal data class SaavnArtistsDto(
    val primary: List<SaavnArtistDto> = emptyList(),
    val featured: List<SaavnArtistDto> = emptyList()
)

internal data class SaavnArtistDto(
    val id: String? = null,
    val name: String? = null
)

internal data class SaavnMediaUrlDto(
    val quality: String? = null,
    val url: String? = null
)
