package com.rhymo.music.data

import androidx.compose.ui.graphics.Color
import com.rhymo.music.model.Song
import com.rhymo.music.ui.theme.DarkInk
import com.rhymo.music.ui.theme.HotPink
import com.rhymo.music.ui.theme.Lime
import com.rhymo.music.ui.theme.NeonBlue
import com.rhymo.music.ui.theme.Violet

interface MusicRepository {
    suspend fun trending(): Result<List<Song>>
    suspend fun search(query: String, page: Int = 0, limit: Int = 20): Result<List<Song>>
}

/** Small offline fallback used when the prototype catalog cannot be reached. */
object DemoMusicRepository {
    private const val demoStream = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"

    val songs = listOf(
        Song("afterglow", "Afterglow", "Mira Vale", "ALT POP · NEW", listOf(HotPink, Color(0xFF672A90), DarkInk), "3:24", demoStream),
        Song("neon-weather", "Neon Weather", "The Sundays", "INDIE · TRENDING", listOf(Lime, NeonBlue, Color(0xFF102B42)), "2:58", demoStream),
        Song("slow-motion", "Slow Motion", "Arlo June", "R&B · FOR YOU", listOf(Violet, HotPink, DarkInk), "3:46", demoStream),
        Song("coastline", "Coastline", "Daylight Club", "ELECTRONIC · RISING", listOf(Color(0xFF46CBEA), Color(0xFF173A5B), DarkInk), "3:12", demoStream)
    )

    fun search(query: String): List<Song> = songs.filter {
        query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true)
    }
}
