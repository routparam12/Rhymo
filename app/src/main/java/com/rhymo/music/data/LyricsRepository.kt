package com.rhymo.music.data

import com.rhymo.music.model.LyricLine
import com.rhymo.music.model.Song
import com.rhymo.music.model.SongLyrics
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private interface LyricsApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String?,
        @Query("duration") durationSeconds: Long?
    ): LyricsDto
}

private data class LyricsDto(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val instrumental: Boolean = false
)

object LyricsRepository {
    private val api: LyricsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LyricsApi::class.java)
    }

    suspend fun lyricsFor(song: Song): Result<SongLyrics> = runCatching {
        val response = api.getLyrics(
            trackName = song.title,
            artistName = song.artist.substringBefore(','),
            albumName = song.album,
            durationSeconds = song.durationSeconds
        )
        if (response.instrumental) {
            return@runCatching SongLyrics(listOf(LyricLine(null, "Instrumental track")), false)
        }

        val synchronizedLines = response.syncedLyrics.orEmpty().toTimedLines()
        if (synchronizedLines.isNotEmpty()) {
            SongLyrics(synchronizedLines, true)
        } else {
            val plainLines = response.plainLyrics.orEmpty()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { LyricLine(null, it) }
                .toList()
            check(plainLines.isNotEmpty()) { "Lyrics are not available for this song." }
            SongLyrics(plainLines, false)
        }
    }
}

internal fun String.toTimedLines(): List<LyricLine> = lineSequence().mapNotNull { rawLine ->
    val match = LRC_LINE.matchEntire(rawLine.trim()) ?: return@mapNotNull null
    val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
    val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    val text = match.groupValues[4].trim()
    if (text.isBlank()) null else LyricLine((minutes * 60_000L) + (seconds * 1_000L) + fraction, text)
}.sortedBy { it.timestampMs }.toList()

private val LRC_LINE = Regex("""\[(\d{1,3}):(\d{2})[.:](\d{1,3})]\s*(.*)""")

