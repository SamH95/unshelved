package com.samwise.unshelved.feature.detail

import com.samwise.unshelved.core.model.MediaProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatProgressTest {

    private fun progress(
        progressFraction: Float = 0f,
        isFinished: Boolean = false,
    ) = MediaProgress(
        id = "p1",
        libraryItemId = "item-1",
        episodeId = null,
        duration = 3600.0,
        progress = progressFraction,
        currentTime = 0.0,
        isFinished = isFinished,
        hideFromContinueListening = false,
        lastUpdate = 0L,
        startedAt = null,
        finishedAt = null,
    )

    @Test
    fun `finished progress returns Finished`() {
        assertEquals("Finished", formatProgress(progress(isFinished = true)))
    }

    @Test
    fun `half progress returns 50 percent`() {
        assertEquals("50%", formatProgress(progress(progressFraction = 0.5f)))
    }

    @Test
    fun `no progress returns Not started`() {
        assertEquals("Not started", formatProgress(progress(progressFraction = 0f)))
    }

    @Test
    fun `almost finished returns 99 percent`() {
        assertEquals("99%", formatProgress(progress(progressFraction = 0.999f)))
    }

    @Test
    fun `finished takes precedence over progress value`() {
        assertEquals("Finished", formatProgress(progress(progressFraction = 0.5f, isFinished = true)))
    }
}
