package com.rhymo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhymo.music.data.LyricsRepository
import com.rhymo.music.data.SaavnMusicRepository
import com.rhymo.music.model.Song
import com.rhymo.music.model.SongConversation
import com.rhymo.music.model.SongLyrics
import com.rhymo.music.ui.theme.HotPink
import com.rhymo.music.ui.theme.InkSoft
import com.rhymo.music.ui.theme.Muted
import com.rhymo.music.ui.theme.Paper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    song: Song,
    playbackPositionProvider: () -> Long,
    onShareLine: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var lyrics by remember(song.id) { mutableStateOf<SongLyrics?>(null) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    var selectedLineIndex by remember(song.id) { mutableStateOf<Int?>(null) }
    var playbackPositionMs by remember(song.id) { mutableLongStateOf(0L) }
    val latestPositionProvider by rememberUpdatedState(playbackPositionProvider)
    val listState = rememberLazyListState()

    LaunchedEffect(song.id) {
        LyricsRepository.lyricsFor(song)
            .onSuccess { lyrics = it }
            .onFailure { error = "Lyrics aren’t available for this track yet." }
    }
    LaunchedEffect(song.id) {
        while (true) {
            playbackPositionMs = latestPositionProvider()
            kotlinx.coroutines.delay(250)
        }
    }

    val currentIndex = lyrics?.lines?.indexOfLast { line ->
        line.timestampMs?.let { it <= playbackPositionMs } ?: false
    } ?: -1
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex, scrollOffset = -120)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 720.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(song.colors)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Lyrics, contentDescription = null) }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lyrics", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (lyrics?.synchronized == true) "Synced with playback · tap a line to share" else "Tap a line to share",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                lyrics == null && error == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Lyrics, contentDescription = null, tint = Muted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(error.orEmpty(), color = Muted)
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(lyrics.orEmptyLines().lines.size) { index ->
                        val line = lyrics.orEmptyLines().lines[index]
                        val active = index == currentIndex
                        val selected = selectedLineIndex == index
                        Surface(
                            color = when {
                                active -> MaterialTheme.colorScheme.primary.copy(.15f)
                                selected -> HotPink.copy(.10f)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selectedLineIndex = index }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line.text,
                                    modifier = Modifier.weight(1f),
                                    color = if (active) MaterialTheme.colorScheme.primary else Paper,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = if (active) 19.sp else 16.sp,
                                    lineHeight = 24.sp
                                )
                                if (selected) IconButton(onClick = { onShareLine(line.text) }) {
                                    Icon(Icons.Outlined.Share, contentDescription = "Share lyric line")
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    song: Song,
    conversation: SongConversation,
    listenerName: String,
    onReact: (String) -> Unit,
    onAddComment: (String, String) -> Unit,
    onToggleCommentLike: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(song.id) { mutableStateOf("") }
    val reactions = listOf("🔥", "💜", "🎧", "✨")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.88f)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Text("Song conversation", style = MaterialTheme.typography.headlineSmall)
            Text("React and talk about ${song.title}", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reactions) { emoji ->
                    val selected = conversation.selectedReaction == emoji
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(.18f) else InkSoft,
                        shape = CircleShape,
                        modifier = Modifier.clickable { onReact(emoji) }
                    ) {
                        Text(
                            "$emoji ${conversation.reactionCounts[emoji] ?: 0}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            if (conversation.comments.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Start the conversation", fontWeight = FontWeight.Bold)
                        Text("Share what this song makes you feel.", color = Muted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(conversation.comments, key = { it.id }) { comment ->
                        Surface(color = InkSoft, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(HotPink, MaterialTheme.colorScheme.primary))),
                                    contentAlignment = Alignment.Center
                                ) { Text(comment.author.take(1).uppercase(), fontWeight = FontWeight.Black) }
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(comment.author, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(comment.message, lineHeight = 20.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = { onToggleCommentLike(comment.id) }, modifier = Modifier.size(38.dp)) {
                                        Icon(
                                            if (comment.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Like comment",
                                            tint = if (comment.likedByMe) HotPink else Muted
                                        )
                                    }
                                    if (comment.likeCount > 0) Text(comment.likeCount.toString(), color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(280) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a comment…") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        enabled = draft.isNotBlank(),
                        onClick = { onAddComment(listenerName, draft); draft = "" }
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post comment") }
                }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsSheet(
    song: Song,
    onPlayQueue: (Song, List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    var related by remember(song.id) { mutableStateOf<List<Song>?>(null) }
    var error by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        SaavnMusicRepository.recommendations(song)
            .onSuccess { related = it }
            .onFailure { error = true }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 720.dp)
                .navigationBarsPadding().padding(horizontal = 20.dp)
        ) {
            Text("More like this", style = MaterialTheme.typography.headlineSmall)
            Text("A fresh queue based on ${song.artist}", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            when {
                related == null && !error -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error || related.isNullOrEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No related tracks found right now.", color = Muted)
                }
                else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    musicItems(
                        songs = related.orEmpty(),
                        onSongClick = { selected -> onDismiss(); onPlayQueue(selected, related.orEmpty()) }
                    )
                    item { Spacer(Modifier.height(14.dp)) }
                }
            }
        }
    }
}

private fun SongLyrics?.orEmptyLines(): SongLyrics = this ?: SongLyrics(emptyList(), false)
