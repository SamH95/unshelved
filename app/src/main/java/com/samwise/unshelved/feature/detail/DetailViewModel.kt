package com.samwise.unshelved.feature.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.network.UpdateProgressRequest
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.DownloadRepository
import com.samwise.unshelved.service.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val isLoading: Boolean = false,
    val item: LibraryItem? = null,
    val progress: MediaProgress? = null,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    private val downloadRepository: DownloadRepository,
    private val progressCache: ProgressCache,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _state = MutableStateFlow(DetailState())
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val downloadState: StateFlow<DownloadEntity?> = downloadRepository.observeDownload(itemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        playerRepository.prewarmPlayback(itemId)
        load()
        viewModelScope.launch {
            progressCache.progressMap.collect { map ->
                map[itemId]?.let { cachedProgress ->
                    _state.update { it.copy(progress = cachedProgress) }
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val cached = libraryRepository.getCachedItem(itemId)
            if (cached != null) {
                _state.update { it.copy(item = cached, isLoading = false) }
                val cachedEntity = libraryRepository.getCachedItemEntity(itemId)
                if (cachedEntity == null || cachedEntity.isStale()) {
                    refreshInBackground()
                }
            } else {
                _state.update { it.copy(isLoading = true) }
                refreshInBackground()
            }
        }
    }

    private fun refreshInBackground() {
        viewModelScope.launch {
            val itemResult = libraryRepository.getItem(itemId)
            val progressResult = libraryRepository.getProgress(itemId)
            val fetchedItem = itemResult.getOrNull()
            val fallbackItem = resolveFallbackItem(fetchedItem)

            _state.update {
                it.copy(
                    isLoading = false,
                    item = fetchedItem ?: it.item ?: fallbackItem,
                    progress = progressResult.getOrNull() ?: it.progress,
                    error = if (it.item == null && fetchedItem == null && fallbackItem == null)
                        itemResult.exceptionOrNull()?.message else null,
                )
            }
        }
    }

    private suspend fun resolveFallbackItem(fetchedItem: LibraryItem?): LibraryItem? {
        if (fetchedItem != null || _state.value.item != null) return null
        val download = downloadRepository.getBookDownload(itemId) ?: return null
        return buildFallbackItemFromDownload(download)
    }

    private fun buildFallbackItemFromDownload(download: DownloadEntity): LibraryItem {
        return LibraryItem(
            id = itemId,
            libraryId = "",
            media = BookMedia(
                id = null,
                metadata = BookMetadata(
                    title = download.title,
                    titleIgnorePrefix = null,
                    subtitle = null,
                    authorName = download.author,
                    narratorName = null,
                    seriesName = null,
                    publishedYear = null,
                    description = null,
                    language = null,
                ),
                coverPath = download.coverPath,
                duration = 0.0,
            ),
            addedAt = download.addedAt,
            updatedAt = download.addedAt,
        )
    }

    fun startDownload() {
        val item = _state.value.item ?: return
        viewModelScope.launch { downloadRepository.startDownload(item) }
    }

    fun deleteDownload() {
        viewModelScope.launch { downloadRepository.deleteDownload(itemId) }
    }

    fun toggleFinished() {
        viewModelScope.launch {
            val progress = _state.value.progress
            val item = _state.value.item ?: return@launch
            val markFinished = progress?.isFinished != true

            try {
                val request = buildToggleProgressRequest(item, progress, markFinished)
                libraryRepository.updateProgress(itemId, request)
                val updatedProgress = buildUpdatedProgress(item, progress, markFinished)
                _state.update { it.copy(progress = updatedProgress) }
                progressCache.updateItem(itemId, null, updatedProgress)
            } catch (e: Exception) {
                Log.e("DetailVM", "toggleFinished failed", e)
                _state.update { it.copy(error = e.message ?: "Failed to update progress") }
            }
        }
    }

    private fun buildToggleProgressRequest(
        item: LibraryItem,
        existingProgress: MediaProgress?,
        markFinished: Boolean,
    ): UpdateProgressRequest {
        return UpdateProgressRequest(
            duration = item.media.duration,
            progress = if (markFinished) 1f else 0f,
            currentTime = if (markFinished) item.media.duration else 0.0,
            isFinished = markFinished,
            startedAt = existingProgress?.startedAt,
            finishedAt = if (markFinished) System.currentTimeMillis() else null,
        )
    }

    private fun buildUpdatedProgress(
        item: LibraryItem,
        existingProgress: MediaProgress?,
        markFinished: Boolean,
    ): MediaProgress {
        val newProgress = if (markFinished) 1f else 0f
        val newCurrentTime = if (markFinished) item.media.duration else 0.0

        return existingProgress?.copy(
            isFinished = markFinished,
            progress = newProgress,
            currentTime = newCurrentTime,
        ) ?: MediaProgress(
            id = itemId,
            libraryItemId = itemId,
            episodeId = null,
            duration = item.media.duration,
            progress = newProgress,
            currentTime = newCurrentTime,
            isFinished = markFinished,
            hideFromContinueListening = false,
            lastUpdate = System.currentTimeMillis(),
            startedAt = null,
            finishedAt = null,
        )
    }
}
