package com.samwise.unshelved.core.model

import android.util.Log
import com.samwise.unshelved.core.database.OfflineProgressDao
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.MediaProgressDto
import com.samwise.unshelved.core.network.PlayItemRequest
import com.samwise.unshelved.core.network.SyncSessionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressCache @Inject constructor(
    private val apiProvider: ApiProvider,
    private val offlineProgressDao: OfflineProgressDao,
    private val prefs: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progressMap = MutableStateFlow<Map<String, MediaProgress>>(emptyMap())
    val progressMap: StateFlow<Map<String, MediaProgress>> = _progressMap.asStateFlow()

    init {
        scope.launch {
            val local = offlineProgressDao.getAll()
            if (local.isNotEmpty()) {
                val seed = local.associate { entry ->
                    progressKey(entry.libraryItemId, entry.episodeId) to MediaProgress(
                        id = entry.libraryItemId,
                        libraryItemId = entry.libraryItemId,
                        episodeId = entry.episodeId,
                        duration = entry.duration,
                        progress = entry.progress,
                        currentTime = entry.currentTime,
                        isFinished = entry.isFinished,
                        hideFromContinueListening = false,
                        lastUpdate = entry.updatedAt,
                        startedAt = null,
                        finishedAt = null,
                    )
                }
                _progressMap.value = seed + _progressMap.value
            }
        }
    }

    fun update(progressList: List<MediaProgressDto>?) {
        val serverMap = (progressList ?: emptyList()).associate {
            progressKey(it.libraryItemId, it.episodeId) to it.toDomain()
        }
        val current = _progressMap.value
        _progressMap.value = serverMap + current.filter { (key, local) ->
            val server = serverMap[key]
            server == null || local.lastUpdate > server.lastUpdate
        }
    }

    fun updateItem(libraryItemId: String, episodeId: String?, progress: MediaProgress) {
        _progressMap.value = _progressMap.value + (progressKey(libraryItemId, episodeId) to progress)
    }

    fun getEpisodeProgress(libraryItemId: String, episodeId: String): MediaProgress? =
        _progressMap.value[progressKey(libraryItemId, episodeId)]

    fun refresh() {
        scope.launch {
            if (prefs.offlineMode.first()) return@launch
            try {
                val response = apiProvider.getApi().getMe()
                if (response.isSuccessful) {
                    update(response.body()?.mediaProgress)
                    syncOfflineProgress()
                }
            } catch (e: Exception) {
                Log.e("ProgressCache", "refresh failed", e)
            }
        }
    }

    private suspend fun syncOfflineProgress() {
        val unsynced = offlineProgressDao.getUnsynced()
        if (unsynced.isEmpty()) return
        for (entry in unsynced) {
            try {
                val session = apiProvider.getApi().startPlaybackSession(entry.libraryItemId, PlayItemRequest())
                if (session.isSuccessful) {
                    val sid = session.body()?.id ?: continue
                    apiProvider.getApi().closeSession(
                        sid,
                        SyncSessionRequest(
                            currentTime = entry.currentTime,
                            timeListened = 0.0,
                            duration = entry.duration,
                        ),
                    )
                    offlineProgressDao.markSynced(entry.libraryItemId, entry.episodeId)
                    Log.d("ProgressCache", "Synced offline progress for ${entry.libraryItemId}")
                }
            } catch (e: Exception) {
                Log.w("ProgressCache", "Failed to sync offline progress for ${entry.libraryItemId}", e)
            }
        }
    }

    companion object {
        fun progressKey(libraryItemId: String, episodeId: String?): String =
            if (!episodeId.isNullOrEmpty()) "$libraryItemId/$episodeId" else libraryItemId
    }
}
