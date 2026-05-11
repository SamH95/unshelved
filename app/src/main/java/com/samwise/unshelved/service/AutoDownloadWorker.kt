package com.samwise.unshelved.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samwise.unshelved.core.database.AutoDownloadDao
import com.samwise.unshelved.core.database.AutoDownloadHistoryEntity
import com.samwise.unshelved.core.model.toDomain
import com.samwise.unshelved.core.network.ApiProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class AutoDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val apiProvider: ApiProvider,
    private val autoDownloadDao: AutoDownloadDao,
    private val downloadRepository: DownloadRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "auto_download_episodes"
        private const val TAG = "AutoDownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val podcasts = autoDownloadDao.getAllEntities()
        if (podcasts.isEmpty()) return@withContext Result.success()

        Log.d(TAG, "Checking ${podcasts.size} podcasts for new episodes")

        for (podcast in podcasts) {
            try {
                val response = apiProvider.getApi().getItem(podcast.libraryItemId)
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch item ${podcast.libraryItemId}: ${response.code()}")
                    continue
                }

                val item = response.body()!!.toDomain()
                val episodes = item.podcastMedia?.episodes ?: continue

                val latest = episodes
                    .filter { it.publishedAt >= podcast.enabledAt }
                    .maxByOrNull { it.publishedAt }
                    ?: continue

                if (autoDownloadDao.wasAutoDownloaded(podcast.libraryItemId, latest.id)) continue

                Log.d(TAG, "Auto-downloading latest episode: ${latest.title}")
                downloadRepository.startEpisodeDownload(podcast.libraryItemId, latest)
                autoDownloadDao.recordAutoDownload(
                    AutoDownloadHistoryEntity(
                        episodeId = latest.id,
                        libraryItemId = podcast.libraryItemId,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error checking podcast ${podcast.libraryItemId}", e)
            }
        }

        Result.success()
    }
}
