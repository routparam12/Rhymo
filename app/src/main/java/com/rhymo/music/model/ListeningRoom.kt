package com.rhymo.music.model

/** A short-lived shared playback room, backed by a Firebase Firestore document. */
data class ListeningRoom(
    val id: String,
    val song: Song,
    val hostId: String,
    val hostName: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long = 0L
) {
    val shareLink: String
        get() = "https://rhymo-aeefd.web.app/listen?room=$id"
}
