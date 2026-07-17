package com.rhymo.music.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import com.rhymo.music.model.Song
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class MusicPlaylist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList()
)

/** Persistent local library: likes, saves, offline audio, and user playlists. */
class SavedMusicStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _likedSongs = MutableStateFlow(readSongs(KEY_LIKED_SONGS))
    val likedSongs = _likedSongs.asStateFlow()

    private val _likedSongIds = MutableStateFlow(
        preferences.getStringSet(KEY_LIKED_IDS, emptySet()).orEmpty().toSet() + _likedSongs.value.map(Song::id)
    )
    val likedSongIds = _likedSongIds.asStateFlow()

    private val _savedSongs = MutableStateFlow(readSongs(KEY_SAVED_SONGS))
    val savedSongs = _savedSongs.asStateFlow()

    private val _downloadedSongs = MutableStateFlow(readSongs(KEY_DOWNLOADED_SONGS))
    val downloadedSongs = _downloadedSongs.asStateFlow()

    private val _downloadingSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingSongIds = _downloadingSongIds.asStateFlow()

    private val _downloadFailures = MutableStateFlow<Set<String>>(emptySet())
    val downloadFailures = _downloadFailures.asStateFlow()

    private val _playlists = MutableStateFlow(readPlaylists())
    val playlists = _playlists.asStateFlow()

    fun toggleLiked(song: Song) {
        val currentlyLiked = song.id in _likedSongIds.value
        val updatedIds = if (currentlyLiked) _likedSongIds.value - song.id else _likedSongIds.value + song.id
        val updatedSongs = if (currentlyLiked) {
            _likedSongs.value.filterNot { it.id == song.id }
        } else {
            listOf(song) + _likedSongs.value.filterNot { it.id == song.id }
        }
        preferences.edit { putStringSet(KEY_LIKED_IDS, updatedIds) }
        persistSongs(KEY_LIKED_SONGS, updatedSongs)
        _likedSongIds.value = updatedIds
        _likedSongs.value = updatedSongs
    }

    fun toggleSaved(song: Song) {
        val current = _savedSongs.value
        val updated = if (current.any { it.id == song.id }) {
            current.filterNot { it.id == song.id }
        } else {
            listOf(song) + current
        }
        persistSongs(KEY_SAVED_SONGS, updated)
        _savedSongs.value = updated
    }

    fun download(song: Song) {
        if (song.id in _downloadingSongIds.value || _downloadedSongs.value.any { it.id == song.id }) return
        _downloadingSongIds.value += song.id
        _downloadFailures.value -= song.id

        scope.launch {
            val directory = File(appContext.filesDir, "offline_music").apply { mkdirs() }
            val safeId = song.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            val target = File(directory, "${safeId.ifBlank { song.id.hashCode().toString() }}.mp4")
            runCatching {
                val connection = URL(song.streamUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 45_000
                connection.setRequestProperty("User-Agent", "Rhymo Android")
                try {
                    check(connection.responseCode in 200..299) { "Download failed (${connection.responseCode})" }
                    connection.inputStream.buffered().use { input ->
                        target.outputStream().buffered().use(input::copyTo)
                    }
                } finally {
                    connection.disconnect()
                }
                song.copy(streamUrl = target.toURI().toString())
            }.onSuccess { offlineSong ->
                val updated = listOf(offlineSong) + _downloadedSongs.value.filterNot { it.id == song.id }
                persistSongs(KEY_DOWNLOADED_SONGS, updated)
                _downloadedSongs.value = updated
            }.onFailure {
                target.delete()
                _downloadFailures.value += song.id
            }
            _downloadingSongIds.value -= song.id
        }
    }

    fun createPlaylist(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val updated = listOf(MusicPlaylist(UUID.randomUUID().toString(), cleanName)) + _playlists.value
        preferences.edit { putString(KEY_PLAYLISTS, updated.toPlaylistsJson().toString()) }
        _playlists.value = updated
    }

    fun toggleSongInPlaylist(playlistId: String, song: Song) {
        val updated = _playlists.value.map { playlist ->
            if (playlist.id != playlistId) playlist
            else if (playlist.songs.any { it.id == song.id }) playlist.copy(songs = playlist.songs.filterNot { it.id == song.id })
            else playlist.copy(songs = playlist.songs + song)
        }
        preferences.edit { putString(KEY_PLAYLISTS, updated.toPlaylistsJson().toString()) }
        _playlists.value = updated
    }

    fun removeDownload(songId: String) {
        val downloadedSong = _downloadedSongs.value.firstOrNull { it.id == songId }
        downloadedSong?.streamUrl?.let { localUrl ->
            runCatching { File(URI(localUrl)).delete() }
        }
        val updated = _downloadedSongs.value.filterNot { it.id == songId }
        persistSongs(KEY_DOWNLOADED_SONGS, updated)
        _downloadedSongs.value = updated
    }

    fun renamePlaylist(playlistId: String, name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val updated = _playlists.value.map { if (it.id == playlistId) it.copy(name = cleanName) else it }
        preferences.edit { putString(KEY_PLAYLISTS, updated.toPlaylistsJson().toString()) }
        _playlists.value = updated
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _playlists.value.filterNot { it.id == playlistId }
        preferences.edit { putString(KEY_PLAYLISTS, updated.toPlaylistsJson().toString()) }
        _playlists.value = updated
    }

    private fun readSongs(key: String): List<Song> = runCatching {
        val raw = preferences.getString(key, null) ?: return@runCatching emptyList()
        JSONArray(raw).toSongs()
    }.getOrDefault(emptyList())

    private fun persistSongs(key: String, songs: List<Song>) {
        preferences.edit { putString(key, songs.toSongsJson().toString()) }
    }

    private fun readPlaylists(): List<MusicPlaylist> = runCatching {
        val raw = preferences.getString(KEY_PLAYLISTS, null) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                add(MusicPlaylist(id, name, item.optJSONArray("songs")?.toSongs().orEmpty()))
            }
        }
    }.getOrDefault(emptyList())

    private fun List<MusicPlaylist>.toPlaylistsJson(): JSONArray = JSONArray().also { array ->
        forEach { playlist ->
            array.put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("songs", playlist.songs.toSongsJson())
                }
            )
        }
    }

    private fun List<Song>.toSongsJson(): JSONArray = JSONArray().also { array ->
        forEach { song ->
            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("tag", song.tag)
                    put("duration", song.duration)
                    song.durationSeconds?.let { put("durationSeconds", it) }
                    put("streamUrl", song.streamUrl)
                    putNullable("artworkUrl", song.artworkUrl)
                    putNullable("album", song.album)
                    putNullable("shareUrl", song.shareUrl)
                    put("colors", JSONArray().apply { song.colors.forEach { put(it.toArgb()) } })
                }
            )
        }
    }

    private fun JSONArray.toSongs(): List<Song> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
            val streamUrl = item.optString("streamUrl").takeIf(String::isNotBlank) ?: continue
            val colorsJson = item.optJSONArray("colors")
            val colors = buildList {
                if (colorsJson != null) {
                    for (colorIndex in 0 until colorsJson.length()) add(Color(colorsJson.optInt(colorIndex)))
                }
            }
            add(
                Song(
                    id = id,
                    title = item.optString("title", "Untitled"),
                    artist = item.optString("artist", "Unknown artist"),
                    tag = item.optString("tag", "MUSIC · SAVED"),
                    colors = colors.ifEmpty { listOf(Color(0xFF8E78FF), Color(0xFF0B0C0F)) },
                    duration = item.optString("duration", "0:00"),
                    durationSeconds = item.optLong("durationSeconds").takeIf { item.has("durationSeconds") },
                    streamUrl = streamUrl,
                    artworkUrl = item.nullableString("artworkUrl"),
                    album = item.nullableString("album"),
                    shareUrl = item.nullableString("shareUrl")
                )
            )
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private companion object {
        const val PREFERENCES_NAME = "rhymo_listener_library"
        const val KEY_LIKED_IDS = "liked_song_ids"
        const val KEY_LIKED_SONGS = "liked_songs"
        const val KEY_SAVED_SONGS = "saved_songs"
        const val KEY_DOWNLOADED_SONGS = "downloaded_songs"
        const val KEY_PLAYLISTS = "playlists"
    }
}
