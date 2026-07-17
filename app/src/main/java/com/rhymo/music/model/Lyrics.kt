package com.rhymo.music.model

data class LyricLine(
    val timestampMs: Long?,
    val text: String
)

data class SongLyrics(
    val lines: List<LyricLine>,
    val synchronized: Boolean
)

