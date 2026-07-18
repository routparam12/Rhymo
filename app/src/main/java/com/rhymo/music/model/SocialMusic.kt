package com.rhymo.music.model

data class SongComment(
    val id: String,
    val author: String,
    val message: String,
    val createdAtEpochMs: Long,
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    val parentCommentId: String? = null,
    val authorAvatarUrl: String? = null
)

data class SongConversation(
    val comments: List<SongComment> = emptyList(),
    val reactionCounts: Map<String, Int> = emptyMap(),
    val selectedReaction: String? = null
)
