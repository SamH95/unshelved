package com.samwise.unshelved.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.samwise.unshelved.core.database.CachedItemEntity
import com.samwise.unshelved.core.database.DownloadDao
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.database.UnshelvedDatabase
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.model.toDomain
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val apiProvider: ApiProvider,
    private val downloadDao: DownloadDao,
    private val db: UnshelvedDatabase,
) : CoroutineWorker(appContext, params) {

    private val gson = Gson()

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
        private const val TAG = "DownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
        val serverUrl = inputData.getString(KEY_SERVER_URL) ?: return@withContext Result.failure()
        val token = inputData.getString(KEY_TOKEN) ?: return@withContext Result.failure()
        val episodeId = inputData.getString(KEY_EPISODE_ID)
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: itemId

        if (episodeId != null) {
            downloadEpisode(itemId, episodeId, downloadId, serverUrl, token)
        } else {
            downloadBook(itemId, downloadId, serverUrl, token)
        }
    }

    private suspend fun downloadEpisode(
        itemId: String,
        episodeId: String,
        downloadId: String,
        serverUrl: String,
        token: String,
    ): Result {
        try {
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

            val response = apiProvider.getApi().getItem(itemId)
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch item for episode: ${response.code()}")
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
                return Result.failure()
            }

            val item = response.body()!!
            val domainItem = item.toDomain()
            val episode = domainItem.podcastMedia?.episodes?.find { it.id == episodeId }
            val audioFile = episode?.audioFile
            if (audioFile == null) {
                Log.e(TAG, "No audio file for episode $episodeId")
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
                return Result.failure()
            }

            val downloadDir = File(applicationContext.filesDir, "downloads/$itemId/episodes")
            downloadDir.mkdirs()

            val client = OkHttpClient.Builder().build()
            val filename = audioFile.metadata.filename
            val url = "$serverUrl/api/items/$itemId/file/${audioFile.ino}/download?token=$token"

            Log.d(TAG, "Downloading episode: $filename from $url")

            val request = Request.Builder().url(url).build()
            val httpResponse = client.newCall(request).execute()

            if (!httpResponse.isSuccessful) {
                Log.e(TAG, "Episode download failed: ${httpResponse.code}")
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
                return Result.failure()
            }

            val targetFile = File(downloadDir, "$episodeId-$filename")
            var downloaded = 0L
            httpResponse.body?.byteStream()?.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        downloadDao.updateProgress(downloadId, downloaded)
                    }
                }
            }

            downloadDao.markCompleted(downloadId, targetFile.absolutePath)
            Log.d(TAG, "Episode download complete: $downloadId (${targetFile.length()} bytes)")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Episode download failed for $downloadId", e)
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
            return Result.failure()
        }
    }

    private suspend fun downloadBook(
        itemId: String,
        downloadId: String,
        serverUrl: String,
        token: String,
    ): Result {
        try {
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

            val response = apiProvider.getApi().getItem(itemId)
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch item: ${response.code()}")
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
                return Result.failure()
            }

            val item = response.body()!!
            db.cachedItemDao().upsert(CachedItemEntity(itemId, item.libraryId ?: "", gson.toJson(item), System.currentTimeMillis()))
            val domainItem = item.toDomain()
            val audioFiles = domainItem.media.audioFiles
            if (audioFiles.isEmpty()) {
                Log.e(TAG, "No audio files for item $itemId")
                downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
                return Result.failure()
            }

            val downloadDir = File(applicationContext.filesDir, "downloads/$itemId")
            downloadDir.mkdirs()

            val client = OkHttpClient.Builder().build()
            var totalDownloaded = 0L
            val totalSize = audioFiles.sumOf { it.metadata.size }
            downloadDao.upsert(
                downloadDao.getDownload(downloadId)!!.copy(totalBytes = totalSize)
            )

            for (audioFile in audioFiles) {
                val ino = audioFile.ino
                val filename = audioFile.metadata.filename
                val url = "$serverUrl/api/items/$itemId/file/$ino/download?token=$token"

                Log.d(TAG, "Downloading: $filename from $url")

                val request = Request.Builder().url(url).build()
                val httpResponse = client.newCall(request).execute()

                if (!httpResponse.isSuccessful) {
                    Log.e(TAG, "Download failed for $filename: ${httpResponse.code}")
                    downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
                    return Result.failure()
                }

                val targetFile = File(downloadDir, filename)
                httpResponse.body?.byteStream()?.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead
                            downloadDao.updateProgress(downloadId, totalDownloaded)
                        }
                    }
                }

                Log.d(TAG, "Downloaded: $filename (${targetFile.length()} bytes)")
            }

            downloadDao.markCompleted(downloadId, downloadDir.absolutePath)

            try {
                val coverRequest = Request.Builder()
                    .url("$serverUrl/api/items/$itemId/cover?token=$token")
                    .build()
                val coverResponse = client.newCall(coverRequest).execute()
                if (coverResponse.isSuccessful) {
                    val coverFile = File(downloadDir, "cover.jpg")
                    coverResponse.body?.byteStream()?.use { input ->
                        coverFile.outputStream().use { it.write(input.readBytes()) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cover download failed for $itemId", e)
            }

            Log.d(TAG, "Download complete for $itemId")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $itemId", e)
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED)
            return Result.failure()
        }
    }
}
