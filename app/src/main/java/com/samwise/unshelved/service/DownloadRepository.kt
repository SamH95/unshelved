package com.samwise.unshelved.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.samwise.unshelved.core.database.DownloadDao
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.PodcastEpisode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadRepository"

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val prefs: UserPreferencesRepository,
) {
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun observeDownload(itemId: String): Flow<DownloadEntity?> = downloadDao.observeBookDownload(itemId)

    fun observeEpisodeDownload(libraryItemId: String, episodeId: String): Flow<DownloadEntity?> =
        downloadDao.observeDownload("$libraryItemId-$episodeId")

    suspend fun startDownload(item: LibraryItem) {
        val serverUrl = prefs.getServerUrl()
        if (serverUrl == null) {
            Log.w(TAG, "startDownload: no server URL configured")
            return
        }
        val token = prefs.getAuthToken()
        if (token == null) {
            Log.w(TAG, "startDownload: no auth token available")
            return
        }

        val downloadId = item.id
        downloadDao.upsert(
            DownloadEntity(
                downloadId = downloadId,
                libraryItemId = item.id,
                episodeId = null,
                title = item.media.metadata.title,
                author = item.media.metadata.authorName,
                coverPath = item.media.coverPath,
                localPath = null,
                totalBytes = 0,
                downloadedBytes = 0,
                status = DownloadStatus.QUEUED,
            )
        )

        val workData = workDataOf(
            DownloadWorker.KEY_ITEM_ID to item.id,
            DownloadWorker.KEY_SERVER_URL to serverUrl,
            DownloadWorker.KEY_TOKEN to token,
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("download_$downloadId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$downloadId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun startEpisodeDownload(libraryItemId: String, episode: PodcastEpisode) {
        val serverUrl = prefs.getServerUrl()
        if (serverUrl == null) {
            Log.w(TAG, "startEpisodeDownload: no server URL configured")
            return
        }
        val token = prefs.getAuthToken()
        if (token == null) {
            Log.w(TAG, "startEpisodeDownload: no auth token available")
            return
        }

        val downloadId = "$libraryItemId-${episode.id}"
        downloadDao.upsert(
            DownloadEntity(
                downloadId = downloadId,
                libraryItemId = libraryItemId,
                episodeId = episode.id,
                title = episode.title,
                author = episode.podcastAuthor,
                coverPath = null,
                localPath = null,
                totalBytes = episode.size,
                downloadedBytes = 0,
                status = DownloadStatus.QUEUED,
            )
        )

        val workData = workDataOf(
            DownloadWorker.KEY_ITEM_ID to libraryItemId,
            DownloadWorker.KEY_EPISODE_ID to episode.id,
            DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
            DownloadWorker.KEY_SERVER_URL to serverUrl,
            DownloadWorker.KEY_TOKEN to token,
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("download_$downloadId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "download_$downloadId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun deleteDownload(downloadId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$downloadId")
        val download = downloadDao.getDownload(downloadId)
        download?.localPath?.let { path ->
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        downloadDao.deleteById(downloadId)
    }

    suspend fun getDownload(downloadId: String): DownloadEntity? = downloadDao.getDownload(downloadId)

    suspend fun getBookDownload(itemId: String): DownloadEntity? = downloadDao.getBookDownload(itemId)

    suspend fun isDownloaded(itemId: String): Boolean {
        val dl = downloadDao.getBookDownload(itemId)
        return dl?.status == DownloadStatus.COMPLETED && dl.localPath != null && File(dl.localPath).exists()
    }

    suspend fun getLocalPath(itemId: String): String? {
        val dl = downloadDao.getBookDownload(itemId) ?: return null
        if (dl.status != DownloadStatus.COMPLETED || dl.localPath == null) return null
        return if (File(dl.localPath).exists()) dl.localPath else null
    }

    suspend fun deleteDownloadsForItem(libraryItemId: String) {
        val downloads = downloadDao.getByLibraryItemId(libraryItemId)
        for (dl in downloads) {
            WorkManager.getInstance(context).cancelUniqueWork("download_${dl.downloadId}")
            dl.localPath?.let { path ->
                val file = File(path)
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            downloadDao.deleteById(dl.downloadId)
        }
    }
}
