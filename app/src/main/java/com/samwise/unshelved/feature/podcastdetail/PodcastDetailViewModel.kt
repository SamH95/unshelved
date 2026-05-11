package com.samwise.unshelved.feature.podcastdetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.database.AutoDownloadDao
import com.samwise.unshelved.core.database.AutoDownloadEntity
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.OfflineProgressDao
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.PodcastEpisode
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.network.UpdateProgressRequest
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.DownloadRepository
import com.samwise.unshelved.service.QueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastDetailState(
    val isLoading: Boolean = false,
    val item: LibraryItem? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val descriptionExpanded: Boolean = false,
    val awaitingEpisodes: Boolean = false,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    private val downloadRepository: DownloadRepository,
    private val autoDownloadDao: AutoDownloadDao,
    private val offlineProgressDao: OfflineProgressDao,
    val progressCache: ProgressCache,
    private val queueRepository: QueueRepository,
) : ViewModel() {

    private val itemId: String = savedStateHandle["itemId"] ?: ""

    private val _state = MutableStateFlow(PodcastDetailState(isLoading = true))
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val autoDownloadEnabled = autoDownloadDao.isEnabled(itemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var pollJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            libraryRepository.getItem(itemId)
                .onSuccess { item ->
                    val episodes = (item.podcastMedia?.episodes ?: emptyList())
                        .sortedByDescending { it.publishedAt }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            item = item,
                            episodes = episodes,
                            awaitingEpisodes = it.awaitingEpisodes && episodes.isEmpty(),
                        )
                    }
                    if (episodes.isEmpty() && _state.value.awaitingEpisodes) {
                        startPolling()
                    } else {
                        stopPolling()
                    }
                }
                .onFailure { e ->
                    Log.e("PodcastDetailVM", "Failed to load item $itemId", e)
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun setAwaitingEpisodes() {
        _state.update { it.copy(awaitingEpisodes = true) }
        startPolling()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            repeat(20) {
                delay(5000)
                libraryRepository.getItem(itemId)
                    .onSuccess { item ->
                        val episodes = (item.podcastMedia?.episodes ?: emptyList())
                            .sortedByDescending { it.publishedAt }
                        _state.update {
                            it.copy(
                                item = item,
                                episodes = episodes,
                                awaitingEpisodes = episodes.isEmpty(),
                            )
                        }
                        if (episodes.isNotEmpty()) return@launch
                    }
            }
            _state.update { it.copy(awaitingEpisodes = false) }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun toggleDescription() {
        _state.update { it.copy(descriptionExpanded = !it.descriptionExpanded) }
    }

    fun toggleAutoDownload() {
        viewModelScope.launch {
            if (autoDownloadEnabled.value) {
                autoDownloadDao.disable(itemId)
            } else {
                autoDownloadDao.enable(AutoDownloadEntity(itemId))
            }
        }
    }

    fun getEpisodeProgress(episodeId: String): MediaProgress? =
        progressCache.getEpisodeProgress(itemId, episodeId)

    fun observeEpisodeDownload(episodeId: String): Flow<DownloadEntity?> =
        downloadRepository.observeEpisodeDownload(itemId, episodeId)

    fun downloadEpisode(episode: PodcastEpisode) {
        viewModelScope.launch {
            downloadRepository.startEpisodeDownload(itemId, episode)
        }
    }

    fun batchDownload(episodes: List<PodcastEpisode>) {
        viewModelScope.launch {
            episodes.forEach { ep ->
                downloadRepository.startEpisodeDownload(itemId, ep)
            }
        }
    }

    fun batchAddToQueue(episodes: List<PodcastEpisode>) {
        viewModelScope.launch {
            episodes.forEach { ep ->
                queueRepository.addToQueue(
                    libraryItemId = itemId,
                    episodeId = ep.id,
                    mediaType = "podcast",
                    title = ep.title,
                    author = ep.podcastAuthor,
                    duration = ep.duration,
                )
            }
        }
    }

    fun batchMarkFinished(episodes: List<PodcastEpisode>) {
        val now = System.currentTimeMillis()
        episodes.forEach { ep ->
            val current = progressCache.getEpisodeProgress(itemId, ep.id)
            progressCache.updateItem(
                itemId, ep.id,
                MediaProgress(
                    id = itemId,
                    libraryItemId = itemId,
                    episodeId = ep.id,
                    duration = ep.duration,
                    progress = 1f,
                    currentTime = ep.duration,
                    isFinished = true,
                    hideFromContinueListening = false,
                    lastUpdate = now,
                    startedAt = current?.startedAt ?: now,
                    finishedAt = now,
                ),
            )
        }
        viewModelScope.launch {
            episodes.forEach { ep ->
                libraryRepository.updateEpisodeProgress(
                    itemId, ep.id,
                    UpdateProgressRequest(
                        duration = ep.duration,
                        progress = 1f,
                        currentTime = ep.duration,
                        isFinished = true,
                        startedAt = progressCache.getEpisodeProgress(itemId, ep.id)?.startedAt ?: now,
                        finishedAt = now,
                    ),
                )
            }
        }
    }

    fun deletePodcast() {
        _state.update { it.copy(isDeleting = true, error = null) }
        viewModelScope.launch {
            libraryRepository.deleteLibraryItem(itemId)
                .onSuccess {
                    downloadRepository.deleteDownloadsForItem(itemId)
                    queueRepository.removeByLibraryItemId(itemId)
                    autoDownloadDao.disable(itemId)
                    offlineProgressDao.deleteByLibraryItemId(itemId)
                    _state.update { it.copy(isDeleting = false, deleted = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isDeleting = false, error = "Delete failed: ${e.message}") }
                }
        }
    }
}
