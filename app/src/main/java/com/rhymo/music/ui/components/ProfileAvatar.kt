package com.rhymo.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

private val fallbackAvatarGradients = listOf(
    listOf(Color(0xFFFF4FA3), Color(0xFF7C4DFF)),
    listOf(Color(0xFF00B8D4), Color(0xFF2962FF)),
    listOf(Color(0xFFFF9100), Color(0xFFFF3D71)),
    listOf(Color(0xFF00C853), Color(0xFF00BFA5)),
    listOf(Color(0xFFFFC400), Color(0xFFFF6D00)),
    listOf(Color(0xFFAA00FF), Color(0xFF6200EA))
)

/** Shows a remote account photo with a stable, distinct fallback for that profile. */
@Composable
fun ProfileAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val gradientIndex = (name.lowercase().hashCode() and Int.MAX_VALUE) % fallbackAvatarGradients.size
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(fallbackAvatarGradients[gradientIndex])),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Black
        )
        avatarUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = "$name profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
