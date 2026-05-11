package com.samwise.unshelved.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samwise.unshelved.core.database.DownloadDao
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.ProgressCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class CleanupDownloadsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: UserPreferencesRepository,
    private val downloadDao: DownloadDao,
    private val downloadRepository: DownloadRepository,
    private val progressCache: ProgressCache,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "cleanup_finished_downloads"
        private const val TAG = "CleanupDownloadsWorker"
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!prefs.autoDeleteFinished.first()) {
            return@withContext Result.success()
        }

        val completed = downloadDao.getCompletedList()
        if (completed.isEmpty()) return@withContext Result.success()

        val now = System.currentTimeMillis()
        var deletedCount = 0

        for (download in completed) {
            try {
                val key = ProgressCache.progressKey(download.libraryItemId, download.episodeId)
                val progress = progressCache.progressMap.value[key] ?: continue

                if (!progress.isFinished) continue
                val finishedAt = progress.finishedAt ?: continue
                if (now - finishedAt < SEVEN_DAYS_MS) continue

                Log.d(TAG, "Auto-deleting finished download: ${download.title} (finished ${(now - finishedAt) / 86400000}d ago)")
                downloadRepository.deleteDownload(download.downloadId)
                deletedCount++
            } catch (e: Exception) {
                Log.w(TAG, "Error checking download ${download.downloadId}", e)
            }
        }

        if (deletedCount > 0) {
            Log.d(TAG, "Cleaned up $deletedCount finished downloads")
        }

        Result.success()
    }
}
