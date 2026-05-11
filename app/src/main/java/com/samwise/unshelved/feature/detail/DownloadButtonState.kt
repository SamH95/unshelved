package com.samwise.unshelved.feature.detail

import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus

sealed interface DownloadButtonState {
    data object NotDownloaded : DownloadButtonState
    data object Queued : DownloadButtonState
    data class Downloading(val progress: Float?) : DownloadButtonState
    data object Completed : DownloadButtonState
    data object Failed : DownloadButtonState
}

fun DownloadEntity?.toButtonState(): DownloadButtonState {
    if (this == null) return DownloadButtonState.NotDownloaded
    return when (status) {
        DownloadStatus.QUEUED -> DownloadButtonState.Queued
        DownloadStatus.DOWNLOADING -> DownloadButtonState.Downloading(
            progress = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null,
        )
        DownloadStatus.COMPLETED -> DownloadButtonState.Completed
        DownloadStatus.FAILED -> DownloadButtonState.Failed
        DownloadStatus.CANCELLED -> DownloadButtonState.NotDownloaded
    }
}
