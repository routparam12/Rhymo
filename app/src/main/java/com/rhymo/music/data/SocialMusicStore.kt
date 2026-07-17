package com.rhymo.music.data

import android.content.Context
import androidx.core.content.edit
import com.rhymo.music.model.SongComment
import com.rhymo.music.model.SongConversation
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local social repository. The UI depends only on these state/actions so a
 * Firestore implementation can replace it without changing player components.
 */
class SocialMusicStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _followedArtists = MutableStateFlow(
        preferences.getStringSet(KEY_FOLLOWED_ARTISTS, emptySet()).orEmpty().toSet()
    )
    val followedArtists = _followedArtists.asStateFlow()

    private val _conversations = MutableStateFlow(readConversations())
    val conversations = _conversations.asStateFlow()

    fun toggleFollow(artist: String) {
        val cleanArtist = artist.trim()
        if (cleanArtist.isBlank()) return
        val existing = _followedArtists.value.firstOrNull { it.equals(cleanArtist, ignoreCase = true) }
        val updated = if (existing == null) _followedArtists.value + cleanArtist else _followedArtists.value - existing
        preferences.edit { putStringSet(KEY_FOLLOWED_ARTISTS, updated) }
        _followedArtists.value = updated
    }

    fun react(songId: String, emoji: String) {
        val current = _conversations.value[songId] ?: SongConversation()
        val oldReaction = current.selectedReaction
        val counts = current.reactionCounts.toMutableMap()
        if (oldReaction != null) {
            counts[oldReaction] = (counts[oldReaction].orZero() - 1).coerceAtLeast(0)
        }
        val newReaction = emoji.takeUnless { it == oldReaction }
        if (newReaction != null) counts[newReaction] = counts[newReaction].orZero() + 1
        updateConversation(songId, current.copy(reactionCounts = counts.filterValues { it > 0 }, selectedReaction = newReaction))
    }

    fun addComment(songId: String, author: String, message: String) {
        val cleanMessage = message.trim()
        if (cleanMessage.isBlank()) return
        val current = _conversations.value[songId] ?: SongConversation()
        val comment = SongComment(
            id = UUID.randomUUID().toString(),
            author = author.trim().ifBlank { "Rhymo listener" },
            message = cleanMessage.take(MAX_COMMENT_LENGTH),
            createdAtEpochMs = System.currentTimeMillis()
        )
        updateConversation(songId, current.copy(comments = listOf(comment) + current.comments))
    }

    fun toggleCommentLike(songId: String, commentId: String) {
        val current = _conversations.value[songId] ?: return
        val comments = current.comments.map { comment ->
            if (comment.id != commentId) comment
            else comment.copy(
                likedByMe = !comment.likedByMe,
                likeCount = (comment.likeCount + if (comment.likedByMe) -1 else 1).coerceAtLeast(0)
            )
        }
        updateConversation(songId, current.copy(comments = comments))
    }

    private fun updateConversation(songId: String, conversation: SongConversation) {
        val updated = _conversations.value.toMutableMap().apply { put(songId, conversation) }.toMap()
        preferences.edit { putString(KEY_CONVERSATIONS, updated.toJson().toString()) }
        _conversations.value = updated
    }

    private fun readConversations(): Map<String, SongConversation> = runCatching {
        val raw = preferences.getString(KEY_CONVERSATIONS, null) ?: return@runCatching emptyMap()
        val root = JSONObject(raw)
        buildMap {
            root.keys().forEach { songId ->
                val item = root.optJSONObject(songId) ?: return@forEach
                val commentsJson = item.optJSONArray("comments") ?: JSONArray()
                val comments = buildList {
                    for (index in 0 until commentsJson.length()) {
                        val comment = commentsJson.optJSONObject(index) ?: continue
                        add(
                            SongComment(
                                id = comment.optString("id"),
                                author = comment.optString("author", "Rhymo listener"),
                                message = comment.optString("message"),
                                createdAtEpochMs = comment.optLong("createdAtEpochMs"),
                                likeCount = comment.optInt("likeCount"),
                                likedByMe = comment.optBoolean("likedByMe")
                            )
                        )
                    }
                }
                val reactionsJson = item.optJSONObject("reactions") ?: JSONObject()
                val reactions = buildMap {
                    reactionsJson.keys().forEach { emoji -> put(emoji, reactionsJson.optInt(emoji)) }
                }
                put(songId, SongConversation(comments, reactions, item.optString("selectedReaction").takeIf(String::isNotBlank)))
            }
        }
    }.getOrDefault(emptyMap())

    private fun Map<String, SongConversation>.toJson(): JSONObject = JSONObject().also { root ->
        forEach { (songId, conversation) ->
            root.put(songId, JSONObject().apply {
                put("selectedReaction", conversation.selectedReaction.orEmpty())
                put("reactions", JSONObject().apply { conversation.reactionCounts.forEach(::put) })
                put("comments", JSONArray().apply {
                    conversation.comments.forEach { comment ->
                        put(JSONObject().apply {
                            put("id", comment.id)
                            put("author", comment.author)
                            put("message", comment.message)
                            put("createdAtEpochMs", comment.createdAtEpochMs)
                            put("likeCount", comment.likeCount)
                            put("likedByMe", comment.likedByMe)
                        })
                    }
                })
            })
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private companion object {
        const val PREFERENCES_NAME = "rhymo_social_music"
        const val KEY_FOLLOWED_ARTISTS = "followed_artists"
        const val KEY_CONVERSATIONS = "song_conversations"
        const val MAX_COMMENT_LENGTH = 280
    }
}
