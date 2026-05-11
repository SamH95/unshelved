package com.samwise.unshelved.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.QueueItemEntity
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.PodcastEpisode
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.network.UpdateProgressRequest
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.DownloadRepository
import com.samwise.unshelved.service.QueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    val progressCache: ProgressCache,
    prefs: UserPreferencesRepository,
) : ViewModel() {

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val queue = queueRepository.queue
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val nowPlaying = queueRepository.nowPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val queueCount = queueRepository.queueCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val queuedEpisodeIds = queueRepository.queuedEpisodeIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun remove(id: Long) {
        viewModelScope.launch { queueRepository.removeFromQueue(id) }
    }

    fun clear() {
        viewModelScope.launch { queueRepository.clearQueue() }
    }

    fun reorder(items: List<QueueItemEntity>) {
        viewModelScope.launch { queueRepository.reorder(items) }
    }

    fun addEpisode(episode: PodcastEpisode) {
        viewModelScope.launch {
            queueRepository.addToQueue(
                libraryItemId = episode.libraryItemId,
                episodeId = episode.id,
                mediaType = "podcast",
                title = episode.title,
                author = episode.podcastAuthor,
                duration = episode.duration,
            )
        }
    }

    fun toggleQueueEpisode(episode: PodcastEpisode) {
        viewModelScope.launch {
            if (queueRepository.isInQueue(episode.id)) {
                queueRepository.removeByEpisodeId(episode.id)
            } else {
                queueRepository.addToQueue(
                    libraryItemId = episode.libraryItemId,
                    episodeId = episode.id,
                    mediaType = "podcast",
                    title = episode.title,
                    author = episode.podcastAuthor,
                    duration = episode.duration,
                )
            }
        }
    }

    fun downloadEpisode(episode: PodcastEpisode) {
        viewModelScope.launch {
            downloadRepository.startEpisodeDownload(episode.libraryItemId, episode)
        }
    }

    fun observeEpisodeDownload(libraryItemId: String, episodeId: String): Flow<DownloadEntity?> =
        downloadRepository.observeEpisodeDownload(libraryItemId, episodeId)

    fun toggleEpisodeFinished(episode: PodcastEpisode) {
        viewModelScope.launch {
            val current = progressCache.getEpisodeProgress(episode.libraryItemId, episode.id)
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
            libraryRepository.updateEpisodeProgress(episode.libraryItemId, episode.id, request)
            progressCache.updateItem(
                episode.libraryItemId, episode.id,
                MediaProgress(
                    id = "${episode.libraryItemId}-${episode.id}",
                    libraryItemId = episode.libraryItemId,
                    episodeId = episode.id,
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
