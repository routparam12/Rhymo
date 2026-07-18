package com.rhymo.music.model

/** A short-lived shared playback room, backed by a Firebase Firestore document. */
data class ListeningRoom(
    val id: String,
    val song: Song,
    val hostId: String,
    val hostName: String,
    val songAddedById: String = hostId,
    val songAddedByName: String = hostName,
    val isPlaying: Boolean,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long = 0L
) {
    val shareLink: String
        get() = "https://rhymo-aeefd.web.app/listen?room=$id"
}

data class ListeningParticipant(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val isHost: Boolean,
    val joinedAtEpochMs: Long,
    val lastSeenAtEpochMs: Long
)

data class RoomMessage(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val message: String,
    val createdAtEpochMs: Long
)

data class RoomReaction(
    val id: String,
    val authorId: String,
    val authorName: String,
    val emoji: String,
    val createdAtEpochMs: Long
)
