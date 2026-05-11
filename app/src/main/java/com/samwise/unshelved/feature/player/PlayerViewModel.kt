package com.samwise.unshelved.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.DownloadRepository
import com.samwise.unshelved.service.PlayerRepository
import com.samwise.unshelved.service.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    val downloadRepository: DownloadRepository,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerRepository.playerState
    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val jumpBackSeconds = prefs.jumpBackSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 10)
    val jumpForwardSeconds = prefs.jumpForwardSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    fun prewarmPlayback(itemId: String) = playerRepository.prewarmPlayback(itemId)

    fun startPlayback(itemId: String) {
        viewModelScope.launch {
            val isDownloaded = downloadRepository.isDownloaded(itemId)
            if (isDownloaded) {
                playerRepository.startOfflinePlayback(itemId)
            } else {
                val url = prefs.getServerUrl() ?: return@launch
                playerRepository.startPlayback(itemId, url)
            }
        }
    }

    fun startPlaybackAtPosition(itemId: String, positionSeconds: Double) {
        viewModelScope.launch {
            val url = prefs.getServerUrl() ?: return@launch
            playerRepository.startPlayback(itemId, url)
            playerRepository.seekTo(positionSeconds)
        }
    }

    fun startEpisodePlayback(libraryItemId: String, episodeId: String) {
        viewModelScope.launch {
            val url = prefs.getServerUrl() ?: return@launch
            playerRepository.startEpisodePlayback(libraryItemId, episodeId, url)
        }
    }

    fun startEpisodePlaybackAtPosition(libraryItemId: String, episodeId: String, positionSeconds: Double) {
        viewModelScope.launch {
            val url = prefs.getServerUrl() ?: return@launch
            playerRepository.startEpisodePlayback(libraryItemId, episodeId, url)
            playerRepository.seekTo(positionSeconds)
        }
    }

    fun play() = playerRepository.play()
    fun pause() = playerRepository.pause()

    fun seekTo(positionSeconds: Double) = playerRepository.seekTo(positionSeconds)
    fun seekForward() {
        viewModelScope.launch {
            val secs = prefs.jumpForwardSeconds.first()
            playerRepository.seekForward(secs)
        }
    }
    fun seekBack() {
        viewModelScope.launch {
            val secs = prefs.jumpBackSeconds.first()
            playerRepository.seekBack(secs)
        }
    }

    fun nextChapter() {
        if (playerState.value.session?.mediaType == "podcast") {
            viewModelScope.launch { playerRepository.skipToNextInQueue() }
        } else {
            playerRepository.nextChapter()
        }
    }

    fun previousChapter() {
        if (playerState.value.session?.mediaType == "podcast") {
            playerRepository.seekTo(0.0)
        } else {
            playerRepository.previousChapter()
        }
    }

    fun setPlaybackSpeed(speed: Float) = playerRepository.setPlaybackSpeed(speed)
    fun setSleepTimer(minutes: Int?) = playerRepository.setSleepTimer(minutes)

    fun updateCurrentTime() = playerRepository.updateCurrentTime()

    fun closeSession() = playerRepository.closeSession()

    fun startDownload() {
        viewModelScope.launch {
            val itemId = playerState.value.session?.libraryItemId ?: return@launch
            val item = libraryRepository.getItem(itemId).getOrNull() ?: return@launch
            downloadRepository.startDownload(item)
        }
    }
}
