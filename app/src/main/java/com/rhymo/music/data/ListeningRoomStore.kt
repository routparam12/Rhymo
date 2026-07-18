package com.rhymo.music.data

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.rhymo.music.model.ListeningRoom
import com.rhymo.music.model.Song
import com.rhymo.music.ui.theme.DarkInk
import com.rhymo.music.ui.theme.HotPink
import com.rhymo.music.ui.theme.Violet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.getValue

/**
 * Keeps the current Listen Together room in sync with Cloud Firestore.
 *
 * Only the room host publishes transport changes. Guests use the timestamp plus
 * position to catch up locally, so network latency does not permanently drift
 * playback. The room document intentionally contains just one song.
 */
class ListeningRoomStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("rhymo_listening", Context.MODE_PRIVATE)
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var roomListener: ListenerRegistration? = null
    private var lastPublishedAt = 0L
    private var lastPublishedPosition = -1L
    private var lastPublishedPlaying: Boolean? = null

    private val _room = MutableStateFlow<ListeningRoom?>(null)
    val room: StateFlow<ListeningRoom?> = _room.asStateFlow()

    /**
     * Uses the Firebase UID whenever a listener signed in with Google. This is
     * what the production Firestore rules authenticate. A device ID lets the
     * UI retain a stable identity in local/development mode as well.
     */
    private val fallbackListenerId: String = preferences.getString("listener_id", null) ?: buildString {
        append("device_")
        append(java.util.UUID.randomUUID().toString())
    }.also { preferences.edit().putString("listener_id", it).apply() }
    val listenerId: String
        get() = runCatching { FirebaseAuth.getInstance().currentUser?.uid }
            .getOrNull()
            ?: fallbackListenerId

    suspend fun startRoom(song: Song, hostName: String): Result<ListeningRoom> = runCatching {
        val hostId = requireAuthenticatedListenerId()
        val id = firestore.collection(ROOMS_COLLECTION).document().id
        val now = System.currentTimeMillis()
        val room = ListeningRoom(
            id = id,
            song = song,
            hostId = hostId,
            hostName = hostName,
            isPlaying = true,
            positionMs = 0L,
            updatedAtEpochMs = now
        )
        awaitWrite(id, room.toFirestoreMap(now))
        _room.value = room
        observeRoom(id)
        room
    }

    suspend fun joinRoom(roomId: String): Result<ListeningRoom> = runCatching {
        requireAuthenticatedListenerId()
        val cleanRoomId = roomId.trim().takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,}")) }
            ?: error("This Listen Together link is invalid.")
        val snapshot = awaitDocument(cleanRoomId)
        val room = snapshot.toListeningRoom() ?: error("This listening room is no longer available.")
        _room.value = room
        observeRoom(cleanRoomId)
        room
    }

    /** Publishes at most once per 850 ms unless play/pause state changes. */
    fun publishPlayback(room: ListeningRoom, isPlaying: Boolean, positionMs: Long) {
        if (room.hostId != listenerId) return
        val now = System.currentTimeMillis()
        val shouldPublish = isPlaying != lastPublishedPlaying ||
            kotlin.math.abs(positionMs - lastPublishedPosition) >= 700L ||
            now - lastPublishedAt >= 850L
        if (!shouldPublish) return

        lastPublishedAt = now
        lastPublishedPosition = positionMs
        lastPublishedPlaying = isPlaying
        firestore.collection(ROOMS_COLLECTION).document(room.id).update(
            mapOf(
                "isPlaying" to isPlaying,
                "positionMs" to positionMs.coerceAtLeast(0L),
                "updatedAtEpochMs" to now,
                "revision" to FieldValue.increment(1L)
            )
        )
    }

    fun leaveRoom() {
        roomListener?.remove()
        roomListener = null
        _room.value = null
        lastPublishedAt = 0L
        lastPublishedPosition = -1L
        lastPublishedPlaying = null
    }

    private fun observeRoom(roomId: String) {
        roomListener?.remove()
        roomListener = firestore.collection(ROOMS_COLLECTION).document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _room.value = snapshot?.toListeningRoom()
            }
    }

    private fun requireAuthenticatedListenerId(): String {
        check(FirebaseApp.getApps(appContext).isNotEmpty()) {
            "Firebase is not configured for this app. Add the correct google-services.json and rebuild."
        }
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Sign in with Google to use Listen Together.")
    }

    private suspend fun awaitWrite(roomId: String, payload: Map<String, Any?>) =
        suspendCancellableCoroutine<Unit> { continuation ->
            firestore.collection(ROOMS_COLLECTION).document(roomId).set(payload)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

    private suspend fun awaitDocument(roomId: String): DocumentSnapshot =
        suspendCancellableCoroutine { continuation ->
            firestore.collection(ROOMS_COLLECTION).document(roomId).get()
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

    private fun ListeningRoom.toFirestoreMap(createdAt: Long): Map<String, Any?> = mapOf(
        "hostId" to hostId,
        "hostName" to hostName,
        "isPlaying" to isPlaying,
        "positionMs" to positionMs,
        "updatedAtEpochMs" to updatedAtEpochMs,
        "revision" to revision,
        "createdAtEpochMs" to createdAt,
        "song" to mapOf(
            "id" to song.id,
            "title" to song.title,
            "artist" to song.artist,
            "tag" to song.tag,
            "duration" to song.duration,
            "durationSeconds" to song.durationSeconds,
            "streamUrl" to song.streamUrl,
            "artworkUrl" to song.artworkUrl,
            "album" to song.album,
            "shareUrl" to song.shareUrl
        )
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toListeningRoom(): ListeningRoom? {
        val songData = get("song") as? Map<String, Any?> ?: return null
        val songId = songData["id"] as? String ?: return null
        val streamUrl = songData["streamUrl"] as? String ?: return null
        val title = songData["title"] as? String ?: return null
        val artist = songData["artist"] as? String ?: return null
        val song = Song(
            id = songId,
            title = title,
            artist = artist,
            tag = songData["tag"] as? String ?: "LISTEN TOGETHER",
            colors = listOf(HotPink, Violet, DarkInk),
            duration = songData["duration"] as? String ?: "",
            streamUrl = streamUrl,
            durationSeconds = (songData["durationSeconds"] as? Number)?.toLong(),
            artworkUrl = songData["artworkUrl"] as? String,
            album = songData["album"] as? String,
            shareUrl = songData["shareUrl"] as? String
        )
        return ListeningRoom(
            id = id,
            song = song,
            hostId = getString("hostId") ?: return null,
            hostName = getString("hostName") ?: "A friend",
            isPlaying = getBoolean("isPlaying") ?: false,
            positionMs = getLong("positionMs") ?: 0L,
            updatedAtEpochMs = getLong("updatedAtEpochMs") ?: 0L,
            revision = getLong("revision") ?: 0L
        )
    }

    private companion object {
        const val ROOMS_COLLECTION = "listeningRooms"
    }
}
