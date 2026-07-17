package com.rhymo.music.model

import androidx.compose.ui.graphics.Color

/** UI-ready music entity. The stable [id] is used for efficient lazy-list diffing. */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val tag: String,
    val colors: List<Color>,
    val duration: String,
    val streamUrl: String
)
