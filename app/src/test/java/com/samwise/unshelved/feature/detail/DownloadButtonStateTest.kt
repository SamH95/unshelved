package com.samwise.unshelved.feature.detail

import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadButtonStateTest {

    private fun entity(
        status: DownloadStatus,
        totalBytes: Long = 0,
        downloadedBytes: Long = 0,
    ) = DownloadEntity(
        downloadId = "item-1",
        libraryItemId = "item-1",
        title = "Test Book",
        author = "Author",
        coverPath = null,
        localPath = null,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status,
    )

    @Test
    fun `null entity returns NotDownloaded`() {
        val result = null.toButtonState()
        assertEquals(DownloadButtonState.NotDownloaded, result)
    }

    @Test
    fun `QUEUED status returns Queued`() {
        val result = entity(DownloadStatus.QUEUED).toButtonState()
        assertEquals(DownloadButtonState.Queued, result)
    }

    @Test
    fun `DOWNLOADING with totalBytes returns Downloading with progress`() {
        val result = entity(DownloadStatus.DOWNLOADING, totalBytes = 1000, downloadedBytes = 500).toButtonState()
        val expected = DownloadButtonState.Downloading(progress = 0.5f)
        assertEquals(expected, result)
    }

    @Test
    fun `DOWNLOADING with zero totalBytes returns Downloading with null progress`() {
        val result = entity(DownloadStatus.DOWNLOADING, totalBytes = 0, downloadedBytes = 0).toButtonState()
        assert(result is DownloadButtonState.Downloading)
        assertNull((result as DownloadButtonState.Downloading).progress)
    }

    @Test
    fun `DOWNLOADING progress is clamped to 0-1 range`() {
        val result = entity(DownloadStatus.DOWNLOADING, totalBytes = 100, downloadedBytes = 200).toButtonState()
        assert(result is DownloadButtonState.Downloading)
        assertEquals(1f, (result as DownloadButtonState.Downloading).progress)
    }

    @Test
    fun `COMPLETED status returns Completed`() {
        val result = entity(DownloadStatus.COMPLETED).toButtonState()
        assertEquals(DownloadButtonState.Completed, result)
    }

    @Test
    fun `FAILED status returns Failed`() {
        val result = entity(DownloadStatus.FAILED).toButtonState()
        assertEquals(DownloadButtonState.Failed, result)
    }

    @Test
    fun `CANCELLED status returns NotDownloaded`() {
        val result = entity(DownloadStatus.CANCELLED).toButtonState()
        assertEquals(DownloadButtonState.NotDownloaded, result)
    }
}
