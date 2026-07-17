package com.rhymo.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParserTest {
    @Test
    fun parsesAndSortsTimedLyrics() {
        val lyrics = """
            [01:02.50] Second line
            [00:07.8] First line
            metadata without a timestamp
        """.trimIndent().toTimedLines()

        assertEquals(2, lyrics.size)
        assertEquals("First line", lyrics[0].text)
        assertEquals(7_800L, lyrics[0].timestampMs)
        assertEquals(62_500L, lyrics[1].timestampMs)
    }
}
