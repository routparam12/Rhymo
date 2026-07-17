package com.rhymo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhymo.music.model.Song
import com.rhymo.music.ui.theme.Muted
import com.rhymo.music.ui.theme.DarkPaper

/** Compose equivalent of a RecyclerView adapter, reusable by every music list. */
fun LazyListScope.musicItems(songs: List<Song>, onSongClick: (Song) -> Unit) {
    items(items = songs, key = Song::id, contentType = { "song" }) { song ->
        MusicListItem(song = song, onClick = { onSongClick(song) })
    }
}

@Composable
fun MusicListItem(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(song.colors)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DarkPaper)
        }
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(song.artist, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(song.duration, color = Muted, fontSize = 12.sp)
        IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Muted, modifier = Modifier.size(22.dp))
        }
    }
}
