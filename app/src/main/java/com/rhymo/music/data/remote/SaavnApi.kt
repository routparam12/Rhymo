package com.rhymo.music.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface SaavnApi {
    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): SaavnSongSearchResponse

    @GET("api/search")
    suspend fun globalSearch(
        @Query("query") query: String
    ): SaavnGlobalSearchResponse

    @GET("api/albums")
    suspend fun albumDetails(
        @Query("id") albumId: String
    ): SaavnAlbumDetailResponse

    @GET("api/songs/{id}/suggestions")
    suspend fun songSuggestions(
        @Path("id") songId: String,
        @Query("limit") limit: Int
    ): SaavnSongSuggestionsResponse
}

internal data class SaavnSongSuggestionsResponse(
    val success: Boolean = false,
    val data: List<SaavnSongDto> = emptyList()
)

internal data class SaavnSongSearchResponse(
    val success: Boolean = false,
    val data: SaavnSearchData? = null
)

internal data class SaavnSearchData(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<SaavnSongDto> = emptyList()
)

internal data class SaavnGlobalSearchResponse(
    val success: Boolean = false,
    val data: SaavnGlobalSearchData? = null
)

internal data class SaavnGlobalSearchData(
    val albums: SaavnGlobalResultGroup<SaavnGlobalAlbumDto>? = null
)

internal data class SaavnGlobalResultGroup<T>(
    val results: List<T> = emptyList()
)

internal data class SaavnGlobalAlbumDto(
    val id: String? = null,
    val title: String? = null
)

internal data class SaavnAlbumDetailResponse(
    val success: Boolean = false,
    val data: SaavnAlbumDetailDto? = null
)

internal data class SaavnAlbumDetailDto(
    val songs: List<SaavnSongDto> = emptyList()
)

internal data class SaavnSongDto(
    val id: String? = null,
    val name: String? = null,
    val duration: Long? = null,
    val label: String? = null,
    val language: String? = null,
    val url: String? = null,
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
