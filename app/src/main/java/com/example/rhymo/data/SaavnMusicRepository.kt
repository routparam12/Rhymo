package com.rhymo.music.data

import androidx.compose.ui.graphics.Color
import com.rhymo.music.data.remote.SaavnApi
import com.rhymo.music.data.remote.SaavnMediaUrlDto
import com.rhymo.music.data.remote.SaavnSongDto
import com.rhymo.music.model.Song
import com.rhymo.music.ui.theme.DarkInk
import com.rhymo.music.ui.theme.HotPink
import com.rhymo.music.ui.theme.Lime
import com.rhymo.music.ui.theme.NeonBlue
import com.rhymo.music.ui.theme.Violet
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SaavnMusicRepository : MusicRepository {
    private val api: SaavnApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://saavn.sumit.co/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SaavnApi::class.java)
    }

    override suspend fun trending(): Result<List<Song>> = search(
        query = "Trending Hindi songs",
        limit = 20
    )

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> = runCatching {
        require(query.isNotBlank()) { "Type a song or artist to search." }
        val response = api.searchSongs(query.trim(), page.coerceAtLeast(0), limit.coerceIn(1, 40))
        check(response.success) { "The music service could not complete this search." }
        response.data.orEmptyResults().mapNotNull(SaavnSongDto::toSong)
    }
}

private fun com.rhymo.music.data.remote.SaavnSearchData?.orEmptyResults(): List<SaavnSongDto> =
    this?.results.orEmpty()

private fun SaavnSongDto.toSong(): Song? {
    val songId = id?.takeIf(String::isNotBlank) ?: return null
    val stream = downloadUrl.bestUrl(preferredQuality = "320kbps") ?: return null
    val primaryArtists = artists?.primary.orEmpty().mapNotNull { it.name?.decodeEntities() }
    val featuredArtists = artists?.featured.orEmpty().mapNotNull { it.name?.decodeEntities() }
    val artistNames = (primaryArtists + featuredArtists).distinct()
    val languageLabel = language?.replaceFirstChar { it.uppercase() } ?: "Music"

    return Song(
        id = songId,
        title = name?.decodeEntities()?.ifBlank { "Untitled" } ?: "Untitled",
        artist = artistNames.joinToString().ifBlank { "Unknown artist" },
        tag = "$languageLabel · ${label?.decodeEntities()?.ifBlank { "For you" } ?: "For you"}".uppercase(),
        colors = colorsFor(songId),
        duration = duration.toDurationLabel(),
        streamUrl = stream,
        artworkUrl = image.bestUrl(preferredQuality = "500x500"),
        album = album?.name?.decodeEntities()
    )
}

private fun List<SaavnMediaUrlDto>.bestUrl(preferredQuality: String): String? =
    firstOrNull { it.quality.equals(preferredQuality, ignoreCase = true) }?.url
        ?: lastOrNull { !it.url.isNullOrBlank() }?.url

private fun Long?.toDurationLabel(): String {
    val seconds = this?.coerceAtLeast(0) ?: 0
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun String.decodeEntities(): String = this
    .replace("&quot;", "\"")
    .replace("&#039;", "'")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")

private fun colorsFor(id: String): List<Color> {
    val palettes = listOf(
        listOf(HotPink, Violet, DarkInk),
        listOf(Violet, NeonBlue, DarkInk),
        listOf(Lime, Color(0xFF167F88), DarkInk),
        listOf(Color(0xFFFF7043), HotPink, DarkInk)
    )
    return palettes[(id.hashCode() and Int.MAX_VALUE) % palettes.size]
}
