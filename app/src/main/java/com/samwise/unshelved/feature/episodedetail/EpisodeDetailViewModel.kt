package com.samwise.unshelved.feature.episodedetail

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpisodeDetailState(
    val isLoading: Boolean = true,
    val episode: PodcastEpisode? = null,
)

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val queueRepository: QueueRepository,
    private val progressCache: ProgressCache,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    private val itemId: String = savedStateHandle["itemId"]!!
    private val episodeId: String = savedStateHandle["episodeId"]!!

    private val _state = MutableStateFlow(EpisodeDetailState())
    val state: StateFlow<EpisodeDetailState> = _state

    val serverUrl = prefs.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val downloadState = downloadRepository.observeEpisodeDownload(itemId, episodeId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val progress: StateFlow<MediaProgress?> = progressCache.progressMap
        .map { it[ProgressCache.progressKey(itemId, episodeId)] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, progressCache.getEpisodeProgress(itemId, episodeId))

    val isInQueue: StateFlow<Boolean> = queueRepository.queuedEpisodeIds
        .map { episodeId in it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = EpisodeDetailState(isLoading = true)
            val item = libraryRepository.getItem(itemId).getOrNull()
            val episode = item?.podcastMedia?.episodes?.find { it.id == episodeId }
            _state.value = EpisodeDetailState(
                isLoading = false,
                episode = episode?.copy(
                    libraryItemId = itemId,
                    podcastTitle = item.title,
                    podcastAuthor = item.authorName,
                ),
            )
        }
    }

    fun toggleQueue() {
        val episode = _state.value.episode ?: return
        viewModelScope.launch {
            if (queueRepository.isInQueue(episodeId)) {
                queueRepository.removeByEpisodeId(episodeId)
            } else {
                queueRepository.addToQueue(
                    libraryItemId = itemId,
                    episodeId = episodeId,
                    mediaType = "podcast",
                    title = episode.title,
                    author = episode.podcastAuthor,
                    duration = episode.duration,
                )
            }
        }
    }

    fun startDownload() {
        val episode = _state.value.episode ?: return
        viewModelScope.launch {
            downloadRepository.startEpisodeDownload(itemId, episode)
        }
    }

    fun toggleFinished() {
        val episode = _state.value.episode ?: return
        viewModelScope.launch {
            val current = progressCache.getEpisodeProgress(itemId, episodeId)
            val markFinished = current?.isFinished != true
            val now = System.currentTimeMillis()
            val request = UpdateProgressRequest(
                duration = episode.duration,
                progress = if (markFinished) 1f else 0f,
                currentTime = if (markFinished) episode.duration else 0.0,
                isFinished = markFinished,
                startedAt = current?.startedAt ?: now,
                finishedAt = if (markFinished) now else null,
            )
            libraryRepository.updateEpisodeProgress(itemId, episodeId, request)
            progressCache.updateItem(
                itemId, episodeId,
                MediaProgress(
                    id = "$itemId-$episodeId",
                    libraryItemId = itemId,
                    episodeId = episodeId,
                    duration = episode.duration,
                    progress = if (markFinished) 1f else 0f,
                    currentTime = if (markFinished) episode.duration else 0.0,
                    isFinished = markFinished,
                    hideFromContinueListening = false,
                    lastUpdate = now,
                    startedAt = current?.startedAt ?: now,
                    finishedAt = if (markFinished) now else null,
                ),
            )
        }
    }
}
