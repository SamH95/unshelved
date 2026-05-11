package com.samwise.unshelved.feature.latest

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.PodcastEpisode
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.network.UpdateProgressRequest
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.DownloadRepository
import com.samwise.unshelved.service.QueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LatestState(
    val isLoading: Boolean = false,
    val episodes: List<PodcastEpisode> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class LatestEpisodesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    private val downloadRepository: DownloadRepository,
    private val queueRepository: QueueRepository,
    private val progressCache: ProgressCache,
) : ViewModel() {

    private val _state = MutableStateFlow(LatestState())
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val activeLibraryId = prefs.activeLibraryId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            activeLibraryId.filterNotNull().collect { libraryId ->
                _state.update { LatestState(isLoading = true) }
                loadPage(libraryId, 0)
            }
        }
        viewModelScope.launch {
            libraryRepository.libraryInvalidated.collect {
                val libraryId = activeLibraryId.value ?: return@collect
                if (!_state.value.isRefreshing) loadPage(libraryId, 0)
            }
        }
    }

    fun loadMore() {
        if (loadJob?.isActive == true || !_state.value.hasMore || _state.value.isLoading) return
        val libraryId = activeLibraryId.value ?: return
        loadPage(libraryId, _state.value.page + 1)
    }

    fun refresh() {
        val libraryId = activeLibraryId.value ?: return
        loadJob?.cancel()
        progressCache.refresh()
        _state.update { it.copy(isRefreshing = true) }
        loadJob = viewModelScope.launch {
            try {
                libraryRepository.getRecentEpisodes(libraryId, page = 0)
                    .onSuccess { episodes ->
                        _state.update { current ->
                            current.copy(
                                episodes = episodes,
                                page = 0,
                                hasMore = episodes.size >= 25,
                            )
                        }
                    }
            } finally {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun loadPage(libraryId: String, page: Int) {
        loadJob = viewModelScope.launch {
            libraryRepository.getRecentEpisodes(libraryId, page = page)
                .onSuccess { episodes ->
                    _state.update { current ->
                        val newList = if (page == 0) episodes else current.episodes + episodes
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            episodes = newList,
                            page = page,
                            hasMore = episodes.size >= 25,
                        )
                    }
                }
                .onFailure { e ->
                    Log.e("LatestVM", "Failed to load episodes", e)
                    _state.update { it.copy(isLoading = false, isRefreshing = false) }
                }
        }
    }

    fun observeEpisodeDownload(libraryItemId: String, episodeId: String): Flow<DownloadEntity?> =
        downloadRepository.observeEpisodeDownload(libraryItemId, episodeId)

    fun batchDownload(episodes: List<PodcastEpisode>) {
        viewModelScope.launch {
            episodes.forEach { ep ->
                downloadRepository.startEpisodeDownload(ep.libraryItemId, ep)
            }
        }
    }

    fun batchAddToQueue(episodes: List<PodcastEpisode>) {
        viewModelScope.launch {
            episodes.forEach { ep ->
                queueRepository.addToQueue(
                    libraryItemId = ep.libraryItemId,
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
            val current = progressCache.getEpisodeProgress(ep.libraryItemId, ep.id)
            progressCache.updateItem(
                ep.libraryItemId, ep.id,
                MediaProgress(
                    id = ep.libraryItemId,
                    libraryItemId = ep.libraryItemId,
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
                    ep.libraryItemId, ep.id,
                    UpdateProgressRequest(
                        duration = ep.duration,
                        progress = 1f,
                        currentTime = ep.duration,
                        isFinished = true,
                        startedAt = progressCache.getEpisodeProgress(ep.libraryItemId, ep.id)?.startedAt ?: now,
                        finishedAt = now,
                    ),
                )
            }
        }
    }
}
