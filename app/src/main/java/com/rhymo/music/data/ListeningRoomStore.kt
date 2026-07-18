package com.rhymo.music.data

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.rhymo.music.model.ListeningRoom
import com.rhymo.music.model.ListeningParticipant
import com.rhymo.music.model.RoomMessage
import com.rhymo.music.model.RoomReaction
import com.rhymo.music.model.Song
import com.rhymo.music.ui.theme.DarkInk
import com.rhymo.music.ui.theme.HotPink
import com.rhymo.music.ui.theme.Violet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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
    private var participantsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var reactionsListener: ListenerRegistration? = null
    private var observedRoomId: String? = null
    private var participantSnapshotReady = false
    private val knownParticipantIds = mutableSetOf<String>()
    private var lastPublishedAt = 0L
    private var lastPublishedPosition = -1L
    private var lastPublishedPlaying: Boolean? = null

    private val _room = MutableStateFlow<ListeningRoom?>(null)
    val room: StateFlow<ListeningRoom?> = _room.asStateFlow()
    private val _participants = MutableStateFlow<List<ListeningParticipant>>(emptyList())
    val participants: StateFlow<List<ListeningParticipant>> = _participants.asStateFlow()
    private val _messages = MutableStateFlow<List<RoomMessage>>(emptyList())
    val messages: StateFlow<List<RoomMessage>> = _messages.asStateFlow()
    private val _reactions = MutableStateFlow<List<RoomReaction>>(emptyList())
    val reactions: StateFlow<List<RoomReaction>> = _reactions.asStateFlow()
    private val _participantJoined = MutableSharedFlow<ListeningParticipant>(extraBufferCapacity = 8)
    val participantJoined: SharedFlow<ListeningParticipant> = _participantJoined.asSharedFlow()

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

    suspend fun startRoom(
        song: Song,
        hostName: String,
        hostAvatarUrl: String?,
        isPlaying: Boolean,
        positionMs: Long
    ): Result<ListeningRoom> = runCatching {
        val hostId = requireAuthenticatedListenerId()
        val id = firestore.collection(ROOMS_COLLECTION).document().id
        val now = System.currentTimeMillis()
        val room = ListeningRoom(
            id = id,
            song = song,
            hostId = hostId,
            hostName = hostName,
            isPlaying = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            updatedAtEpochMs = now
        )
        withTimeout(ROOM_OPERATION_TIMEOUT_MS) {
            awaitWrite(id, room.toFirestoreMap(now))
            awaitParticipantWrite(id, hostId, hostName, hostAvatarUrl, isHost = true, joinedAt = now)
        }
        _room.value = room
        observeRoom(id)
        room
    }

    suspend fun joinRoom(
        roomId: String,
        listenerName: String,
        listenerAvatarUrl: String?
    ): Result<ListeningRoom> = runCatching {
        val authenticatedListenerId = requireAuthenticatedListenerId()
        val cleanRoomId = roomId.trim().takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,}")) }
            ?: error("This Listen Together link is invalid.")
        val snapshot = withTimeout(ROOM_OPERATION_TIMEOUT_MS) {
            awaitDocument(cleanRoomId)
        }
        val room = snapshot.toListeningRoom() ?: error("This listening room is no longer available.")
        val now = System.currentTimeMillis()
        withTimeout(ROOM_OPERATION_TIMEOUT_MS) {
            awaitParticipantWrite(
                roomId = cleanRoomId,
                participantId = authenticatedListenerId,
                name = listenerName,
                avatarUrl = listenerAvatarUrl,
                isHost = authenticatedListenerId == room.hostId,
                joinedAt = now
            )
        }
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
        observedRoomId?.let { roomId ->
            firestore.collection(ROOMS_COLLECTION).document(roomId)
                .collection(PARTICIPANTS_COLLECTION).document(listenerId).delete()
        }
        roomListener?.remove()
        participantsListener?.remove()
        messagesListener?.remove()
        reactionsListener?.remove()
        roomListener = null
        participantsListener = null
        messagesListener = null
        reactionsListener = null
        observedRoomId = null
        participantSnapshotReady = false
        knownParticipantIds.clear()
        _room.value = null
        _participants.value = emptyList()
        _messages.value = emptyList()
        _reactions.value = emptyList()
        lastPublishedAt = 0L
        lastPublishedPosition = -1L
        lastPublishedPlaying = null
    }

    private fun observeRoom(roomId: String) {
        observedRoomId = roomId
        roomListener?.remove()
        roomListener = firestore.collection(ROOMS_COLLECTION).document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _room.value = snapshot?.toListeningRoom()
            }
        observeParticipants(roomId)
        observeMessages(roomId)
        observeReactions(roomId)
    }

    fun sendReaction(emoji: String, authorName: String): Result<Unit> = runCatching {
        val roomId = observedRoomId ?: error("Join a listening room first.")
        val authorId = requireAuthenticatedListenerId()
        require(emoji in ALLOWED_REACTIONS) { "Unsupported room reaction." }
        val now = System.currentTimeMillis()
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(REACTIONS_COLLECTION).document().set(
                mapOf(
                    "authorId" to authorId,
                    "authorName" to authorName.take(60),
                    "emoji" to emoji,
                    "createdAtEpochMs" to now
                )
            )
        Unit
    }

    fun sendMessage(message: String, authorName: String, authorAvatarUrl: String?): Result<Unit> = runCatching {
        val roomId = observedRoomId ?: error("Join a listening room first.")
        val authorId = requireAuthenticatedListenerId()
        val cleanMessage = message.trim().take(500)
        require(cleanMessage.isNotEmpty()) { "Write a message first." }
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(MESSAGES_COLLECTION).document().set(
                mapOf(
                    "authorId" to authorId,
                    "authorName" to authorName.take(60),
                    "authorAvatarUrl" to authorAvatarUrl,
                    "message" to cleanMessage,
                    "createdAtEpochMs" to System.currentTimeMillis()
                )
            )
        Unit
    }

    fun heartbeat() {
        val roomId = observedRoomId ?: return
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_COLLECTION).document(listenerId)
            .update("lastSeenAtEpochMs", System.currentTimeMillis())
    }

    private fun observeParticipants(roomId: String) {
        participantsListener?.remove()
        participantSnapshotReady = false
        knownParticipantIds.clear()
        participantsListener = firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_COLLECTION)
            .orderBy("joinedAtEpochMs", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val activeAfter = System.currentTimeMillis() - PARTICIPANT_ACTIVE_WINDOW_MS
                val participants = snapshot.documents
                    .mapNotNull { it.toListeningParticipant() }
                    .filter { it.lastSeenAtEpochMs >= activeAfter }
                if (participantSnapshotReady) {
                    participants.filter { it.id !in knownParticipantIds && it.id != listenerId }
                        .forEach(_participantJoined::tryEmit)
                }
                knownParticipantIds.clear()
                knownParticipantIds.addAll(participants.map(ListeningParticipant::id))
                participantSnapshotReady = true
                _participants.value = participants
            }
    }

    private fun observeMessages(roomId: String) {
        messagesListener?.remove()
        messagesListener = firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(MESSAGES_COLLECTION)
            .orderBy("createdAtEpochMs", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _messages.value = snapshot.documents.mapNotNull { it.toRoomMessage() }
            }
    }

    private fun observeReactions(roomId: String) {
        reactionsListener?.remove()
        reactionsListener = firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(REACTIONS_COLLECTION)
            .orderBy("createdAtEpochMs", Query.Direction.ASCENDING)
            .limitToLast(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _reactions.value = snapshot.documents.mapNotNull { it.toRoomReaction() }
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

    private suspend fun awaitParticipantWrite(
        roomId: String,
        participantId: String,
        name: String,
        avatarUrl: String?,
        isHost: Boolean,
        joinedAt: Long
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        firestore.collection(ROOMS_COLLECTION).document(roomId)
            .collection(PARTICIPANTS_COLLECTION).document(participantId).set(
                mapOf(
                    "userId" to participantId,
                    "name" to name.take(60),
                    "avatarUrl" to avatarUrl,
                    "isHost" to isHost,
                    "joinedAtEpochMs" to joinedAt,
                    "lastSeenAtEpochMs" to joinedAt
                )
            )
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }

    private fun ListeningRoom.toFirestoreMap(createdAt: Long): Map<String, Any?> = mapOf(
        "hostId" to hostId,
        "hostName" to hostName,
        "songAddedById" to songAddedById,
        "songAddedByName" to songAddedByName,
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
            songAddedById = getString("songAddedById") ?: getString("hostId").orEmpty(),
            songAddedByName = getString("songAddedByName") ?: getString("hostName") ?: "A friend",
            isPlaying = getBoolean("isPlaying") ?: false,
            positionMs = getLong("positionMs") ?: 0L,
            updatedAtEpochMs = getLong("updatedAtEpochMs") ?: 0L,
            revision = getLong("revision") ?: 0L
        )
    }

    private fun DocumentSnapshot.toListeningParticipant(): ListeningParticipant? {
        val name = getString("name") ?: return null
        return ListeningParticipant(
            id = id,
            name = name,
            avatarUrl = getString("avatarUrl"),
            isHost = getBoolean("isHost") ?: false,
            joinedAtEpochMs = getLong("joinedAtEpochMs") ?: 0L,
            lastSeenAtEpochMs = getLong("lastSeenAtEpochMs") ?: 0L
        )
    }

    private fun DocumentSnapshot.toRoomMessage(): RoomMessage? {
        val authorId = getString("authorId") ?: return null
        val message = getString("message") ?: return null
        return RoomMessage(
            id = id,
            authorId = authorId,
            authorName = getString("authorName") ?: "Listener",
            authorAvatarUrl = getString("authorAvatarUrl"),
            message = message,
            createdAtEpochMs = getLong("createdAtEpochMs") ?: 0L
        )
    }

    private fun DocumentSnapshot.toRoomReaction(): RoomReaction? {
        val authorId = getString("authorId") ?: return null
        val emoji = getString("emoji") ?: return null
        return RoomReaction(
            id = id,
            authorId = authorId,
            authorName = getString("authorName") ?: "Listener",
            emoji = emoji,
            createdAtEpochMs = getLong("createdAtEpochMs") ?: 0L
        )
    }

    private companion object {
        const val ROOMS_COLLECTION = "listeningRooms"
        const val PARTICIPANTS_COLLECTION = "participants"
        const val MESSAGES_COLLECTION = "messages"
        const val REACTIONS_COLLECTION = "reactions"
        const val ROOM_OPERATION_TIMEOUT_MS = 12_000L
        const val PARTICIPANT_ACTIVE_WINDOW_MS = 90_000L
        val ALLOWED_REACTIONS = setOf("❤️", "🙌", "🔥", "👏", "😍", "🎶")
    }
}
