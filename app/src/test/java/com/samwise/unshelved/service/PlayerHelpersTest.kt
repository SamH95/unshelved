package com.samwise.unshelved.service

import com.samwise.unshelved.core.model.AudioTrack
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.Chapter
import com.samwise.unshelved.core.model.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerHelpersTest {

    private fun chapter(id: Int, start: Double, end: Double, title: String = "Ch $id") =
        Chapter(id = id, start = start, end = end, title = title)

    private fun track(index: Int, startOffset: Double, duration: Double) =
        AudioTrack(
            index = index,
            startOffset = startOffset,
            duration = duration,
            title = "Track $index",
            contentUrl = "/track$index.mp3",
            mimeType = "audio/mpeg",
        )

    private fun session(chapters: List<Chapter> = emptyList()) =
        PlaybackSession(
            id = "sess-1",
            libraryItemId = "item-1",
            episodeId = null,
            mediaType = "book",
            mediaMetadata = BookMetadata(
                title = "Test", titleIgnorePrefix = null, subtitle = null,
                authorName = null, narratorName = null, seriesName = null,
                publishedYear = null, description = null, language = null,
            ),
            chapters = chapters,
            displayTitle = "Test",
            displayAuthor = "Author",
            duration = 3600.0,
            playMethod = 1,
            currentTime = 0.0,
            audioTracks = emptyList(),
        )

    // --- chapterAt ---

    @Test
    fun `chapterAt returns null for empty chapters`() {
        val s = session(emptyList())
        assertNull(s.chapterAt(100.0))
    }

    @Test
    fun `chapterAt returns first chapter at start`() {
        val chapters = listOf(
            chapter(0, 0.0, 300.0),
            chapter(1, 300.0, 600.0),
        )
        val s = session(chapters)
        assertEquals(chapters[0], s.chapterAt(0.0))
    }

    @Test
    fun `chapterAt returns first chapter for position within it`() {
        val chapters = listOf(
            chapter(0, 0.0, 300.0),
            chapter(1, 300.0, 600.0),
        )
        val s = session(chapters)
        assertEquals(chapters[0], s.chapterAt(150.0))
    }

    @Test
    fun `chapterAt returns second chapter at boundary`() {
        val chapters = listOf(
            chapter(0, 0.0, 300.0),
            chapter(1, 300.0, 600.0),
        )
        val s = session(chapters)
        assertEquals(chapters[1], s.chapterAt(300.0))
    }

    @Test
    fun `chapterAt returns last chapter for position beyond all`() {
        val chapters = listOf(
            chapter(0, 0.0, 300.0),
            chapter(1, 300.0, 600.0),
        )
        val s = session(chapters)
        assertEquals(chapters[1], s.chapterAt(9999.0))
    }

    @Test
    fun `chapterAt returns null for position before first chapter start`() {
        val chapters = listOf(
            chapter(0, 10.0, 300.0),
        )
        val s = session(chapters)
        assertNull(s.chapterAt(5.0))
    }

    @Test
    fun `chapterAt with single chapter`() {
        val chapters = listOf(chapter(0, 0.0, 3600.0))
        val s = session(chapters)
        assertEquals(chapters[0], s.chapterAt(1800.0))
    }

    @Test
    fun `chapterAt with many chapters finds correct one`() {
        val chapters = (0 until 20).map { i ->
            chapter(i, i * 180.0, (i + 1) * 180.0)
        }
        val s = session(chapters)
        assertEquals(chapters[10], s.chapterAt(1805.0))
    }

    // --- toTrackIndex ---

    @Test
    fun `toTrackIndex returns 0 for empty tracks`() {
        assertEquals(0, 100.0.toTrackIndex(emptyList()))
    }

    @Test
    fun `toTrackIndex returns 0 for single track`() {
        val tracks = listOf(track(0, 0.0, 3600.0))
        assertEquals(0, 500.0.toTrackIndex(tracks))
    }

    @Test
    fun `toTrackIndex returns correct index for multi-track`() {
        val tracks = listOf(
            track(0, 0.0, 600.0),
            track(1, 600.0, 600.0),
            track(2, 1200.0, 600.0),
        )
        assertEquals(0, 0.0.toTrackIndex(tracks))
        assertEquals(0, 300.0.toTrackIndex(tracks))
        assertEquals(0, 599.9.toTrackIndex(tracks))
        assertEquals(1, 600.0.toTrackIndex(tracks))
        assertEquals(1, 900.0.toTrackIndex(tracks))
        assertEquals(2, 1200.0.toTrackIndex(tracks))
        assertEquals(2, 1500.0.toTrackIndex(tracks))
    }

    @Test
    fun `toTrackIndex returns last track for position past all tracks`() {
        val tracks = listOf(
            track(0, 0.0, 600.0),
            track(1, 600.0, 600.0),
        )
        assertEquals(1, 9999.0.toTrackIndex(tracks))
    }

    @Test
    fun `toTrackIndex returns 0 for position before first track offset`() {
        val tracks = listOf(
            track(0, 10.0, 600.0),
        )
        assertEquals(0, 5.0.toTrackIndex(tracks))
    }

    // --- toPositionInTrack ---

    @Test
    fun `toPositionInTrack returns absolute position for empty tracks`() {
        assertEquals(100.0, 100.0.toPositionInTrack(emptyList()), 0.001)
    }

    @Test
    fun `toPositionInTrack returns position relative to single track`() {
        val tracks = listOf(track(0, 0.0, 3600.0))
        assertEquals(500.0, 500.0.toPositionInTrack(tracks), 0.001)
    }

    @Test
    fun `toPositionInTrack subtracts track offset for multi-track`() {
        val tracks = listOf(
            track(0, 0.0, 600.0),
            track(1, 600.0, 600.0),
            track(2, 1200.0, 600.0),
        )
        assertEquals(0.0, 0.0.toPositionInTrack(tracks), 0.001)
        assertEquals(300.0, 300.0.toPositionInTrack(tracks), 0.001)
        assertEquals(0.0, 600.0.toPositionInTrack(tracks), 0.001)
        assertEquals(150.0, 750.0.toPositionInTrack(tracks), 0.001)
        assertEquals(0.0, 1200.0.toPositionInTrack(tracks), 0.001)
        assertEquals(100.5, 1300.5.toPositionInTrack(tracks), 0.001)
    }

    @Test
    fun `toTrackIndex and toPositionInTrack are consistent`() {
        val tracks = listOf(
            track(0, 0.0, 300.0),
            track(1, 300.0, 400.0),
            track(2, 700.0, 500.0),
        )
        val absolutePos = 850.0
        val idx = absolutePos.toTrackIndex(tracks)
        val posInTrack = absolutePos.toPositionInTrack(tracks)
        assertEquals(2, idx)
        assertEquals(150.0, posInTrack, 0.001)
        assertEquals(absolutePos, tracks[idx].startOffset + posInTrack, 0.001)
    }

    // --- appendToken ---

    @Test
    fun `appendToken adds with question mark when no params`() {
        assertEquals(
            "https://example.com/file.mp3?token=abc",
            "https://example.com/file.mp3".appendToken("abc"),
        )
    }

    @Test
    fun `appendToken adds with ampersand when params exist`() {
        assertEquals(
            "https://example.com/file.mp3?foo=bar&token=abc",
            "https://example.com/file.mp3?foo=bar".appendToken("abc"),
        )
    }
}
