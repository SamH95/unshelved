package com.samwise.unshelved.service

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.database.DownloadDao
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.database.OfflineProgressDao
import com.samwise.unshelved.core.database.OfflineProgressEntity
import com.samwise.unshelved.core.database.UnshelvedDatabase
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.media3.common.MediaItem.ClippingConfiguration
import com.samwise.unshelved.core.model.AudioTrack
import com.samwise.unshelved.core.model.AudioFile
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.Chapter
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.PlaybackSession
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.model.toDomain
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.LibraryItemDto
import com.samwise.unshelved.core.network.PlayItemRequest
import com.samwise.unshelved.core.network.SyncSessionRequest
import com.google.gson.Gson
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlayerRepo"

data class PlayerState(
    val session: PlaybackSession? = null,
    val currentTimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isCasting: Boolean = false,
    val playbackSpeed: Float = 1f,
    val currentChapter: Chapter? = null,
    val sleepTimerEndMs: Long? = null,
    val sleepTimerMinutes: Int? = null,
)

@Singleton
class PlayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiProvider: ApiProvider,
    private val prefs: UserPreferencesRepository,
    private val downloadDao: DownloadDao,
    private val offlineProgressDao: OfflineProgressDao,
    private val progressCache: ProgressCache,
    private val db: UnshelvedDatabase,
    private val queueRepository: QueueRepository,
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFlow = MutableStateFlow<MediaController?>(null)
    private var syncJob: Job? = null
    private var sessionId: String? = null
    private var lastSyncTime = 0L
    private var sessionStartTime = 0L
    private var totalTimeListened = 0.0
    private var seekInProgress = false
    private var lastSeekTimeMs = 0L

    // Pre-warmed session cache: keyed by itemId
    private var prewarmItemId: String? = null
    private var prewarmJob: Job? = null
    private var prewarmSession: PlaybackSession? = null

    init {
        connectController()
    }

    fun connectController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                try {
                    val ctrl = future.get()
                    controller = ctrl
                    ctrl.addListener(playerListener)
                    controllerFlow.value = ctrl
                    Log.d(TAG, "connectController: connected to service")
                } catch (e: Exception) {
                    Log.e(TAG, "connectController: failed to get controller", e)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.update {
                it.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
            }
            if (playbackState == Player.STATE_READY) {
                controller?.volume = 1.0f
            }
            if (playbackState == Player.STATE_ENDED) {
                scope.launch { onPlaybackEnded() }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && (seekInProgress || System.currentTimeMillis() - lastSeekTimeMs < 600)) return
            _playerState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                controller?.volume = 1.0f
                val minutes = _playerState.value.sleepTimerMinutes
                if (minutes != null && sleepTimerJob?.isActive != true) {
                    startSleepCountdown(minutes)
                }
            }
            if (!isPlaying) syncProgress()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            seekInProgress = false
            updateCurrentTime()
        }
    }

    fun prewarmPlayback(itemId: String) {
        if (prewarmItemId == itemId && prewarmJob?.isActive == true) return
        prewarmJob?.cancel()
        prewarmSession = null
        prewarmItemId = itemId
        prewarmJob = scope.launch(Dispatchers.IO) {
            try {
                val response = apiProvider.getApi().startPlaybackSession(itemId, PlayItemRequest())
                if (response.isSuccessful) {
                    prewarmSession = response.body()?.toDomain()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Prewarm failed for $itemId", e)
            }
        }
    }

    suspend fun startPlayback(itemId: String, serverUrl: String) {
        Log.d(TAG, "startPlayback: itemId=$itemId serverUrl=$serverUrl")

        // Use pre-warmed session if available, otherwise wait for prewarm job or fetch fresh
        val session: PlaybackSession = if (prewarmItemId == itemId) {
            prewarmJob?.join()
            prewarmSession?.also {
                prewarmSession = null
                prewarmItemId = null
                Log.d(TAG, "startPlayback: using pre-warmed session ${it.id}")
            } ?: run {
                // prewarm failed, fetch fresh
                val response = try {
                    apiProvider.getApi().startPlaybackSession(itemId, PlayItemRequest())
                } catch (e: Exception) {
                    Log.e(TAG, "startPlayback: network error", e)
                    _playerState.update { it.copy(isBuffering = false) }
                    return
                }
                if (!response.isSuccessful) {
                    Log.e(TAG, "startPlayback: failed - ${response.errorBody()?.string()}")
                    _playerState.update { it.copy(isBuffering = false) }
                    return
                }
                response.body()?.toDomain() ?: run {
                    Log.e(TAG, "startPlayback: response body was null")
                    _playerState.update { it.copy(isBuffering = false) }
                    return
                }
            }
        } else {
            val response = try {
                apiProvider.getApi().startPlaybackSession(itemId, PlayItemRequest())
            } catch (e: Exception) {
                Log.e(TAG, "startPlayback: network error", e)
                _playerState.update { it.copy(isBuffering = false) }
                return
            }
            Log.d(TAG, "startPlayback: response code=${response.code()} success=${response.isSuccessful}")
            if (!response.isSuccessful) {
                Log.e(TAG, "startPlayback: failed - ${response.errorBody()?.string()}")
                _playerState.update { it.copy(isBuffering = false) }
                return
            }
            response.body()?.toDomain() ?: run {
                Log.e(TAG, "startPlayback: response body was null")
                _playerState.update { it.copy(isBuffering = false) }
                return
            }
        }

        Log.d(TAG, "startPlayback: session=${session.id} tracks=${session.audioTracks.size} duration=${session.duration} currentTime=${session.currentTime}")
        if (session.audioTracks.isNotEmpty()) {
            Log.d(TAG, "startPlayback: first track url=${session.audioTracks.first().contentUrl}")
        }

        sessionId = session.id
        sessionStartTime = System.currentTimeMillis()
        totalTimeListened = 0.0
        isChapterBased = false

        _playerState.update {
            it.copy(
                session = session,
                currentTimeMs = (session.currentTime * 1000).toLong(),
                currentChapter = session.chapterAt(session.currentTime),
            )
        }

        withContext(Dispatchers.IO) {
            queueRepository.setNowPlaying(
                libraryItemId = session.libraryItemId,
                episodeId = session.episodeId,
                mediaType = session.mediaType,
                title = session.displayTitle,
                author = session.displayAuthor,
                duration = session.duration,
            )
        }

        val token = prefs.getAuthToken() ?: ""

        // Check for local downloaded files
        val download = downloadDao.getBookDownload(itemId)
        Log.d(TAG, "Download check: status=${download?.status} localPath=${download?.localPath}")
        val localDir = if (download?.status == DownloadStatus.COMPLETED && download.localPath != null) {
            File(download.localPath).takeIf { it.exists() && it.isDirectory }
        } else null

        val mediaItems = if (localDir != null) {
            val localFiles = localDir.listFiles()
                ?.filter { !it.name.equals("cover.jpg", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()
            Log.d(TAG, "Playing from local: ${localFiles.size} files in ${localDir.absolutePath}")
            if (localFiles.isEmpty()) {
                Log.w(TAG, "Local dir exists but no files, falling back to streaming")
                null
            } else if (localFiles.size != session.audioTracks.size) {
                // Local file order/count must match the server's audioTracks for
                // toTrackIndex/toPositionInTrack to address the right MediaItem.
                // If they don't, fall back to streaming rather than risk seeking
                // into the wrong file.
                Log.w(TAG, "Local file count (${localFiles.size}) != audioTracks (${session.audioTracks.size}), falling back to streaming")
                null
            } else {
                localFiles.map { file ->
                    Log.d(TAG, "Local file: ${file.name} (${file.length()} bytes)")
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setMimeType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "audio/mpeg")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(session.displayTitle)
                                .setArtist(session.displayAuthor)
                                .build()
                        )
                        .build()
                }
            }
        } else null

        val coverUri = Uri.parse("$serverUrl/api/items/${session.libraryItemId}/cover?token=$token")

        val finalMediaItems = mediaItems ?: session.audioTracks.map { track ->
            val baseUrl = if (track.contentUrl.startsWith("http")) {
                track.contentUrl
            } else {
                "$serverUrl${track.contentUrl}"
            }
            val url = baseUrl.appendToken(token)
            MediaItem.Builder()
                .setUri(url)
                .setMimeType(track.mimeType)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(session.displayTitle)
                        .setArtist(session.displayAuthor)
                        .setArtworkUri(coverUri)
                        .build()
                )
                .build()
        }

        Log.d(TAG, "Playing ${finalMediaItems.size} media items, seeking to ${session.currentTime}s")

        withContext(Dispatchers.Main) {
            Log.d(TAG, "Waiting for controller, current value=${controllerFlow.value}")
            val ctrl = withTimeoutOrNull(5000) { controllerFlow.filterNotNull().first() }
            if (ctrl == null) {
                Log.e(TAG, "Timed out waiting for controller — reconnecting")
                connectController()
                return@withContext
            }
            Log.d(TAG, "Got controller: $ctrl isConnected=${ctrl.isConnected}")
            ctrl.volume = 1.0f
            ctrl.setMediaItems(finalMediaItems)
            if (mediaItems != null && finalMediaItems.size == 1) {
                ctrl.seekTo(0, (session.currentTime * 1000).toLong())
            } else {
                ctrl.seekTo(session.currentTime.toTrackIndex(session.audioTracks), (session.currentTime.toPositionInTrack(session.audioTracks) * 1000).toLong())
            }
            ctrl.prepare()
            ctrl.play()
        }

        startPeriodicSync()
    }

    suspend fun startEpisodePlayback(libraryItemId: String, episodeId: String, serverUrl: String) {
        Log.d(TAG, "startEpisodePlayback: itemId=$libraryItemId episodeId=$episodeId")

        val currentSession = _playerState.value.session
        if (currentSession?.mediaType == "podcast" && currentSession.episodeId != null &&
            currentSession.episodeId != episodeId &&
            !queueRepository.isInQueue(currentSession.episodeId)
        ) {
            queueRepository.insertAtFront(
                libraryItemId = currentSession.libraryItemId,
                episodeId = currentSession.episodeId,
                mediaType = "podcast",
                title = currentSession.displayTitle,
                author = currentSession.displayAuthor,
                duration = currentSession.duration,
            )
        }

        queueRepository.removeByEpisodeId(episodeId)

        val response = try {
            apiProvider.getApi().startEpisodePlaybackSession(libraryItemId, episodeId, PlayItemRequest())
        } catch (e: Exception) {
            Log.e(TAG, "startEpisodePlayback: network error", e)
            _playerState.update { it.copy(isBuffering = false) }
            return
        }
        if (!response.isSuccessful) {
            Log.e(TAG, "startEpisodePlayback: failed - ${response.errorBody()?.string()}")
            _playerState.update { it.copy(isBuffering = false) }
            return
        }
        val session = response.body()?.toDomain() ?: run {
            Log.e(TAG, "startEpisodePlayback: response body was null")
            _playerState.update { it.copy(isBuffering = false) }
            return
        }

        sessionId = session.id
        sessionStartTime = System.currentTimeMillis()
        totalTimeListened = 0.0
        isChapterBased = false

        _playerState.update {
            it.copy(
                session = session,
                currentTimeMs = (session.currentTime * 1000).toLong(),
                currentChapter = session.chapterAt(session.currentTime),
            )
        }

        withContext(Dispatchers.IO) {
            queueRepository.setNowPlaying(
                libraryItemId = session.libraryItemId,
                episodeId = session.episodeId,
                mediaType = session.mediaType,
                title = session.displayTitle,
                author = session.displayAuthor,
                duration = session.duration,
            )
        }

        scope.launch(Dispatchers.IO) {
            try {
                val itemResponse = apiProvider.getApi().getItem(libraryItemId)
                val desc = itemResponse.body()?.toDomain()
                    ?.podcastMedia?.episodes?.find { it.id == episodeId }?.description
                if (desc != null) {
                    _playerState.update { state ->
                        val current = state.session ?: return@update state
                        if (current.id == session.id) state.copy(session = current.copy(episodeDescription = desc))
                        else state
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch episode description", e)
            }
        }

        val token = prefs.getAuthToken() ?: ""
        val coverUri = Uri.parse("$serverUrl/api/items/$libraryItemId/cover?token=$token")

        val downloadId = "$libraryItemId-$episodeId"
        val episodeDownload = withContext(Dispatchers.IO) { downloadDao.getDownload(downloadId) }
        val localFile = if (episodeDownload?.status == DownloadStatus.COMPLETED && episodeDownload.localPath != null) {
            File(episodeDownload.localPath).takeIf { it.exists() }
        } else null

        val mediaItems = if (localFile != null) {
            Log.d(TAG, "Playing episode from local file: ${localFile.absolutePath}")
            listOf(
                MediaItem.Builder()
                    .setUri(Uri.fromFile(localFile))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(session.displayTitle)
                            .setArtist(session.displayAuthor)
                            .setArtworkUri(coverUri)
                            .build()
                    )
                    .build()
            )
        } else {
            session.audioTracks.map { track ->
                val baseUrl = if (track.contentUrl.startsWith("http")) {
                    track.contentUrl
                } else {
                    "$serverUrl${track.contentUrl}"
                }
                val url = baseUrl.appendToken(token)
                MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(track.mimeType)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(session.displayTitle)
                            .setArtist(session.displayAuthor)
                            .setArtworkUri(coverUri)
                            .build()
                    )
                    .build()
            }
        }

        Log.d(TAG, "Playing episode: ${mediaItems.size} tracks, seeking to ${session.currentTime}s")

        withContext(Dispatchers.Main) {
            val ctrl = withTimeoutOrNull(5000) { controllerFlow.filterNotNull().first() }
            if (ctrl == null) {
                Log.e(TAG, "Timed out waiting for controller — reconnecting")
                connectController()
                return@withContext
            }
            ctrl.volume = 1.0f
            ctrl.setMediaItems(mediaItems)
            if (localFile != null) {
                ctrl.seekTo(0, (session.currentTime * 1000).toLong())
            } else {
                ctrl.seekTo(session.currentTime.toTrackIndex(session.audioTracks), (session.currentTime.toPositionInTrack(session.audioTracks) * 1000).toLong())
            }
            ctrl.prepare()
            ctrl.play()
        }

        startPeriodicSync()
    }

    suspend fun skipToNextInQueue() {
        val next = queueRepository.popNext() ?: return
        closeSession()
        _playerState.update { it.copy(session = null) }
        val serverUrl = prefs.getServerUrl() ?: return
        if (next.episodeId != null) {
            startEpisodePlayback(next.libraryItemId, next.episodeId, serverUrl)
        } else {
            startPlayback(next.libraryItemId, serverUrl)
        }
    }

    private suspend fun onPlaybackEnded() {
        val endedSession = _playerState.value.session
        if (endedSession != null) {
            val now = System.currentTimeMillis()
            progressCache.updateItem(endedSession.libraryItemId, endedSession.episodeId, MediaProgress(
                id = endedSession.libraryItemId,
                libraryItemId = endedSession.libraryItemId,
                episodeId = endedSession.episodeId,
                duration = endedSession.duration,
                progress = 1f,
                currentTime = endedSession.duration,
                isFinished = true,
                hideFromContinueListening = false,
                lastUpdate = now,
                startedAt = sessionStartTime,
                finishedAt = now,
            ))
            withContext(Dispatchers.IO) {
                offlineProgressDao.upsert(
                    OfflineProgressEntity(
                        libraryItemId = endedSession.libraryItemId,
                        episodeId = endedSession.episodeId ?: "",
                        currentTime = endedSession.duration,
                        duration = endedSession.duration,
                        progress = 1f,
                        isFinished = true,
                        updatedAt = now,
                        synced = false,
                    )
                )
            }
        }
        playNextFromQueue()
    }

    private suspend fun playNextFromQueue() {
        val endedSession = _playerState.value.session
        closeSession()
        _playerState.update { it.copy(session = null) }
        if (endedSession?.mediaType != "podcast") {
            controller?.stop()
            controller?.clearMediaItems()
            return
        }
        val next = queueRepository.popNext()
        if (next == null) {
            controller?.stop()
            controller?.clearMediaItems()
            return
        }
        val serverUrl = prefs.getServerUrl() ?: return
        if (next.episodeId != null) {
            startEpisodePlayback(next.libraryItemId, next.episodeId, serverUrl)
        } else {
            startPlayback(next.libraryItemId, serverUrl)
        }
    }

    fun pause() { controller?.pause() }
    fun play() { controller?.play() }

    fun seekTo(positionSeconds: Double) {
        val session = _playerState.value.session ?: return
        val trackIndex = positionSeconds.toTrackIndex(session.audioTracks)
        val posInTrack = positionSeconds.toPositionInTrack(session.audioTracks)
        seekInProgress = true
        lastSeekTimeMs = System.currentTimeMillis()
        controller?.seekTo(trackIndex, (posInTrack * 1000).toLong())
        _playerState.update {
            it.copy(
                currentTimeMs = (positionSeconds * 1000).toLong(),
                currentChapter = session.chapterAt(positionSeconds),
            )
        }
    }

    fun seekForward(seconds: Int = 30) {
        val current = _playerState.value.currentTimeMs / 1000.0
        seekTo(current + seconds)
    }

    fun seekBack(seconds: Int = 15) {
        val current = _playerState.value.currentTimeMs / 1000.0
        seekTo((current - seconds).coerceAtLeast(0.0))
    }

    fun nextChapter() {
        val session = _playerState.value.session ?: return
        val current = _playerState.value.currentTimeMs / 1000.0
        val next = session.chapters.firstOrNull { it.start > current } ?: return
        seekTo(next.start)
    }

    fun previousChapter() {
        val session = _playerState.value.session ?: return
        val current = _playerState.value.currentTimeMs / 1000.0
        val prev = session.chapters.lastOrNull { it.start < current - 2 } ?: return
        seekTo(prev.start)
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _playerState.update { it.copy(playbackSpeed = speed) }
        scope.launch { prefs.savePlaybackSpeed(speed) }
    }

    fun onSpeedChangedFromNotification(speed: Float) {
        _playerState.update { it.copy(playbackSpeed = speed) }
        scope.launch { prefs.savePlaybackSpeed(speed) }
    }

    fun setCasting(isCasting: Boolean) {
        _playerState.update { it.copy(isCasting = isCasting) }
    }

    suspend fun buildStreamingItems(): List<MediaItem>? {
        val currentSession = _playerState.value.session ?: return null
        val serverUrl = prefs.getServerUrl() ?: return null
        val token = prefs.getAuthToken() ?: return null

        val session = if (currentSession.audioTracks.isNotEmpty()) {
            currentSession
        } else {
            val response = withContext(Dispatchers.IO) {
                apiProvider.getApi().startPlaybackSession(currentSession.libraryItemId, PlayItemRequest())
            }
            if (!response.isSuccessful) return null
            response.body()?.toDomain() ?: return null
        }

        val coverUri = Uri.parse("$serverUrl/api/items/${session.libraryItemId}/cover?token=$token")
        return session.audioTracks.map { track ->
            val baseUrl = if (track.contentUrl.startsWith("http")) track.contentUrl else "$serverUrl${track.contentUrl}"
            val url = baseUrl.appendToken(token)
            MediaItem.Builder()
                .setUri(url)
                .setMimeType(track.mimeType)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(session.displayTitle)
                        .setArtist(session.displayAuthor)
                        .setArtworkUri(coverUri)
                        .build()
                )
                .build()
        }
    }

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        controller?.volume = 1.0f
        _playerState.update { it.copy(sleepTimerEndMs = null, sleepTimerMinutes = minutes) }
        if (minutes != null && _playerState.value.isPlaying) {
            startSleepCountdown(minutes)
        }
    }

    private fun startSleepCountdown(minutes: Int) {
        sleepTimerJob?.cancel()
        val endMs = System.currentTimeMillis() + minutes * 60_000L
        _playerState.update { it.copy(sleepTimerEndMs = endMs) }
        sleepTimerJob = scope.launch {
            val totalMs = minutes * 60_000L
            if (_playerState.value.isCasting) {
                delay(totalMs)
            } else {
                val fadeStartMs = totalMs - 15_000L
                if (fadeStartMs > 0) delay(fadeStartMs)
                val fadeMs = (endMs - System.currentTimeMillis()).coerceAtLeast(100)
                val steps = 30
                val stepMs = fadeMs / steps
                repeat(steps) { i ->
                    val vol = 1f - (i + 1f) / steps
                    controller?.volume = vol.coerceAtLeast(0f)
                    delay(stepMs.coerceAtLeast(16))
                }
            }
            pause()
            controller?.volume = 1.0f
            _playerState.update { it.copy(sleepTimerEndMs = null) }
        }
    }

    private var isChapterBased = false

    fun updateCurrentTime() {
        val posMs = controller?.currentPosition ?: return
        val session = _playerState.value.session ?: return
        val itemIdx = controller?.currentMediaItemIndex ?: 0
        val absoluteSecs = if (isChapterBased) {
            val chapterStart = session.chapters.getOrNull(itemIdx)?.start ?: 0.0
            chapterStart + (posMs / 1000.0)
        } else {
            val trackOffset = session.audioTracks.getOrNull(itemIdx)?.startOffset ?: 0.0
            trackOffset + (posMs / 1000.0)
        }
        _playerState.update {
            it.copy(
                currentTimeMs = (absoluteSecs * 1000).toLong(),
                currentChapter = session.chapterAt(absoluteSecs),
            )
        }
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(30_000)
                if (_playerState.value.isPlaying) syncProgress()
            }
        }
    }

    fun syncProgress() {
        val sid = sessionId ?: return
        val session = _playerState.value.session ?: return
        val currentSecs = _playerState.value.currentTimeMs / 1000.0
        val duration = session.duration
        val now = System.currentTimeMillis()
        val timeSinceLastSync = (now - lastSyncTime) / 1000.0
        totalTimeListened += timeSinceLastSync
        lastSyncTime = now

        val progress = if (duration > 0) (currentSecs / duration).toFloat().coerceIn(0f, 1f) else 0f
        progressCache.updateItem(session.libraryItemId, session.episodeId, MediaProgress(
            id = session.libraryItemId,
            libraryItemId = session.libraryItemId,
            episodeId = session.episodeId,
            duration = duration,
            progress = progress,
            currentTime = currentSecs,
            isFinished = progress >= 0.99f,
            hideFromContinueListening = false,
            lastUpdate = now,
            startedAt = sessionStartTime,
            finishedAt = null,
        ))

        scope.launch(Dispatchers.IO) {
            offlineProgressDao.upsert(
                OfflineProgressEntity(
                    libraryItemId = session.libraryItemId,
                    episodeId = session.episodeId ?: "",
                    currentTime = currentSecs,
                    duration = duration,
                    progress = progress,
                    isFinished = progress >= 0.99f,
                    updatedAt = now,
                    synced = false,
                )
            )
            if (prefs.offlineMode.first()) return@launch
            try {
                apiProvider.getApi().syncSession(
                    sid,
                    SyncSessionRequest(
                        currentTime = currentSecs,
                        timeListened = totalTimeListened,
                        duration = duration,
                    ),
                )
                offlineProgressDao.markSynced(session.libraryItemId, session.episodeId ?: "")
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed: ${e.message}")
            }
        }
    }

    fun closeSession() {
        val sid = sessionId ?: return
        val currentSecs = _playerState.value.currentTimeMs / 1000.0
        val duration = _playerState.value.session?.duration ?: 0.0
        val session = _playerState.value.session
        val now = System.currentTimeMillis()

        if (session != null) {
            val progress = if (duration > 0) (currentSecs / duration).toFloat().coerceIn(0f, 1f) else 0f
            progressCache.updateItem(session.libraryItemId, session.episodeId, MediaProgress(
                id = session.libraryItemId,
                libraryItemId = session.libraryItemId,
                episodeId = session.episodeId,
                duration = duration,
                progress = progress,
                currentTime = currentSecs,
                isFinished = progress >= 0.99f,
                hideFromContinueListening = false,
                lastUpdate = now,
                startedAt = sessionStartTime,
                finishedAt = null,
            ))
        }

        scope.launch(Dispatchers.IO) {
            if (session != null) {
                val progress = if (duration > 0) (currentSecs / duration).toFloat().coerceIn(0f, 1f) else 0f
                offlineProgressDao.upsert(
                    OfflineProgressEntity(
                        libraryItemId = session.libraryItemId,
                        episodeId = session.episodeId ?: "",
                        currentTime = currentSecs,
                        duration = duration,
                        progress = progress,
                        isFinished = progress >= 0.99f,
                        updatedAt = now,
                        synced = false,
                    )
                )
            }
            if (!prefs.offlineMode.first()) {
                try {
                    apiProvider.getApi().closeSession(
                        sid,
                        SyncSessionRequest(currentSecs, totalTimeListened, duration),
                    )
                    if (session != null) offlineProgressDao.markSynced(session.libraryItemId, session.episodeId ?: "")
                } catch (e: Exception) {
                    Log.w(TAG, "Close session failed: ${e.message}")
                }
            }
        }
        syncJob?.cancel()
        sessionId = null
    }

    fun disconnect() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    fun syncOfflineProgress() {
        scope.launch(Dispatchers.IO) {
            val unsynced = offlineProgressDao.getUnsynced()
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
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Offline sync failed for ${entry.libraryItemId}: ${e.message}")
                }
            }
        }
    }

    // Called from AutoMediaLibraryCallback.onSetMediaItems — sets up state and returns items+position
    // for Media3 to feed directly to ExoPlayer. Does NOT touch the controller.
    // Returns one MediaItem per chapter (with ClippingConfiguration) so Auto shows the chapter list.
    suspend fun prepareAutoPlayback(itemId: String, serverUrl: String): MediaSession.MediaItemsWithStartPosition {
        // Check local download first — play offline without any network call if available
        val download = withContext(Dispatchers.IO) { downloadDao.getBookDownload(itemId) }
        val localDir = if (download?.status == DownloadStatus.COMPLETED && download.localPath != null) {
            File(download.localPath).takeIf { it.exists() && it.isDirectory }
        } else null

        if (localDir != null) {
            val files = localDir.listFiles()
                ?.filter { !it.name.equals("cover.jpg", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()
            if (files.isNotEmpty()) {
                val savedProgress = withContext(Dispatchers.IO) { offlineProgressDao.getProgress(itemId) }
                val currentTime = savedProgress?.currentTime ?: 0.0
                val cachedItem = withContext(Dispatchers.IO) {
                    db.cachedItemDao().get(itemId)?.let { entity ->
                        runCatching { gson.fromJson(entity.json, LibraryItemDto::class.java).toDomain() }.getOrNull()
                    }
                }
                val duration = cachedItem?.media?.duration ?: savedProgress?.duration ?: 0.0
                val metadata = cachedItem?.media?.metadata
                val chapters = cachedItem?.media?.chapters ?: emptyList()
                val audioFiles = cachedItem?.media?.audioFiles ?: emptyList()
                val syntheticSession = createOfflineSession(
                    itemId = itemId,
                    metadata = metadata,
                    chapters = chapters,
                    duration = duration,
                    currentTime = currentTime,
                    downloadTitle = download!!.title,
                    downloadAuthor = download.author,
                    audioFiles = audioFiles,
                    fileCount = files.size,
                )
                sessionId = syntheticSession.id
                sessionStartTime = System.currentTimeMillis()
                totalTimeListened = 0.0
                _playerState.update {
                    it.copy(
                        session = syntheticSession,
                        currentTimeMs = (currentTime * 1000).toLong(),
                        currentChapter = syntheticSession.chapterAt(currentTime),
                    )
                }

                val items = run {
                    // Always one item per file, chapters handled via custom commands
                    isChapterBased = false
                    files.map { file ->
                        MediaItem.Builder().setUri(Uri.fromFile(file))
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(syntheticSession.displayTitle).setArtist(syntheticSession.displayAuthor).build())
                            .build()
                    }
                }

                val tracks = syntheticSession.audioTracks
                val startIndex = if (tracks.isNotEmpty()) currentTime.toTrackIndex(tracks) else 0
                val startPosMs = if (tracks.isNotEmpty()) {
                    (currentTime.toPositionInTrack(tracks) * 1000).toLong()
                } else {
                    (currentTime * 1000).toLong()
                }
                startPeriodicSync()
                return MediaSession.MediaItemsWithStartPosition(items, startIndex, startPosMs)
            }
        }

        // Online path — fetch playback session from server
        val response = try {
            withContext(Dispatchers.IO) { apiProvider.getApi().startPlaybackSession(itemId, PlayItemRequest()) }
        } catch (e: Exception) {
            Log.e(TAG, "prepareAutoPlayback: network error", e)
            return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        }
        if (!response.isSuccessful) return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        val session = response.body()?.toDomain()
            ?: return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)

        sessionId = session.id
        sessionStartTime = System.currentTimeMillis()
        totalTimeListened = 0.0
        _playerState.update {
            it.copy(
                session = session,
                currentTimeMs = (session.currentTime * 1000).toLong(),
                currentChapter = session.chapterAt(session.currentTime),
            )
        }

        val token = prefs.getAuthToken() ?: ""
        val tracks = session.audioTracks

        // Always treat as multi-track: one item per track, chapters handled via custom commands
        isChapterBased = false
        val items = tracks.map { track ->
            val base = if (track.contentUrl.startsWith("http")) track.contentUrl else "$serverUrl${track.contentUrl}"
            val url = base.appendToken(token)
            MediaItem.Builder().setUri(url)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(session.displayTitle).setArtist(session.displayAuthor).build())
                .build()
        }
        val startIndex = session.currentTime.toTrackIndex(tracks)
        val startPosMs = (session.currentTime.toPositionInTrack(tracks) * 1000).toLong()

        if (items.isEmpty()) return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        startPeriodicSync()
        return MediaSession.MediaItemsWithStartPosition(items, startIndex, startPosMs)
    }

    suspend fun prepareAutoEpisodePlayback(libraryItemId: String, episodeId: String, serverUrl: String): MediaSession.MediaItemsWithStartPosition {
        val response = try {
            withContext(Dispatchers.IO) { apiProvider.getApi().startEpisodePlaybackSession(libraryItemId, episodeId, PlayItemRequest()) }
        } catch (e: Exception) {
            Log.e(TAG, "prepareAutoEpisodePlayback: network error", e)
            return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        }
        if (!response.isSuccessful) return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
        val session = response.body()?.toDomain()
            ?: return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)

        sessionId = session.id
        sessionStartTime = System.currentTimeMillis()
        totalTimeListened = 0.0
        isChapterBased = false
        _playerState.update {
            it.copy(
                session = session,
                currentTimeMs = (session.currentTime * 1000).toLong(),
                currentChapter = session.chapterAt(session.currentTime),
            )
        }

        val token = prefs.getAuthToken() ?: ""
        val coverUri = Uri.parse("$serverUrl/api/items/$libraryItemId/cover?token=$token")
        val items = session.audioTracks.map { track ->
            val baseUrl = if (track.contentUrl.startsWith("http")) track.contentUrl else "$serverUrl${track.contentUrl}"
            val url = baseUrl.appendToken(token)
            MediaItem.Builder()
                .setUri(url)
                .setMimeType(track.mimeType)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(session.displayTitle)
                        .setArtist(session.displayAuthor)
                        .setArtworkUri(coverUri)
                        .build()
                )
                .build()
        }
        if (items.isEmpty()) return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)

        val startIndex = session.currentTime.toTrackIndex(session.audioTracks)
        val startPosMs = (session.currentTime.toPositionInTrack(session.audioTracks) * 1000).toLong()
        startPeriodicSync()
        return MediaSession.MediaItemsWithStartPosition(items, startIndex, startPosMs)
    }

    suspend fun startOfflinePlayback(itemId: String) {
        val download = withContext(Dispatchers.IO) { downloadDao.getBookDownload(itemId) } ?: return
        val localDir = download.localPath?.let { File(it) }?.takeIf { it.exists() && it.isDirectory } ?: return
        val localFiles = localDir.listFiles()?.filter { !it.name.equals("cover.jpg", ignoreCase = true) }?.sortedBy { it.name } ?: return
        if (localFiles.isEmpty()) return

        val savedProgress = withContext(Dispatchers.IO) { offlineProgressDao.getProgress(itemId) }
        val currentTime = savedProgress?.currentTime ?: 0.0

        val cachedItem = withContext(Dispatchers.IO) {
            db.cachedItemDao().get(itemId)?.let { entity ->
                runCatching { gson.fromJson(entity.json, LibraryItemDto::class.java).toDomain() }.getOrNull()
            }
        }

        val duration = cachedItem?.media?.duration ?: savedProgress?.duration ?: 0.0
        val chapters = cachedItem?.media?.chapters ?: emptyList()
        val metadata = cachedItem?.media?.metadata
        val audioFiles = cachedItem?.media?.audioFiles ?: emptyList()

        val syntheticSession = createOfflineSession(
            itemId = itemId,
            metadata = metadata,
            chapters = chapters,
            duration = duration,
            currentTime = currentTime,
            downloadTitle = download.title,
            downloadAuthor = download.author,
            audioFiles = audioFiles,
            fileCount = localFiles.size,
        )

        sessionId = syntheticSession.id
        sessionStartTime = System.currentTimeMillis()
        totalTimeListened = 0.0
        isChapterBased = false

        _playerState.update {
            it.copy(
                session = syntheticSession,
                currentTimeMs = (currentTime * 1000).toLong(),
                currentChapter = syntheticSession.chapterAt(currentTime),
            )
        }

        withContext(Dispatchers.IO) {
            queueRepository.setNowPlaying(
                libraryItemId = syntheticSession.libraryItemId,
                episodeId = syntheticSession.episodeId,
                mediaType = syntheticSession.mediaType,
                title = syntheticSession.displayTitle,
                author = syntheticSession.displayAuthor,
                duration = syntheticSession.duration,
            )
        }

        val mediaItems = localFiles.map { file ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(syntheticSession.displayTitle)
                        .setArtist(download.author)
                        .build()
                )
                .build()
        }

        withContext(Dispatchers.Main) {
            val ctrl = controllerFlow.filterNotNull().first()
            ctrl.volume = 1.0f
            ctrl.setMediaItems(mediaItems)
            val tracks = syntheticSession.audioTracks
            if (tracks.isEmpty() || mediaItems.size == 1) {
                ctrl.seekTo(0, (currentTime * 1000).toLong())
            } else {
                ctrl.seekTo(
                    currentTime.toTrackIndex(tracks),
                    (currentTime.toPositionInTrack(tracks) * 1000).toLong(),
                )
            }
            ctrl.prepare()
            ctrl.play()
        }
    }
}

// Helpers

internal fun PlaybackSession.chapterAt(seconds: Double): Chapter? =
    chapters.lastOrNull { it.start <= seconds }

internal fun Double.toTrackIndex(tracks: List<AudioTrack>): Int {
    if (tracks.isEmpty()) return 0
    val idx = tracks.indexOfLast { it.startOffset <= this }
    return if (idx < 0) 0 else idx
}

internal fun Double.toPositionInTrack(tracks: List<AudioTrack>): Double {
    if (tracks.isEmpty()) return this
    val idx = toTrackIndex(tracks)
    val track = tracks[idx]
    return this - track.startOffset
}

internal fun String.appendToken(token: String): String =
    if (contains("?")) "$this&token=$token" else "$this?token=$token"

internal fun createOfflineSession(
    itemId: String,
    metadata: BookMetadata?,
    chapters: List<Chapter>,
    duration: Double,
    currentTime: Double,
    downloadTitle: String,
    downloadAuthor: String?,
    audioFiles: List<AudioFile> = emptyList(),
    fileCount: Int = audioFiles.size,
): PlaybackSession = PlaybackSession(
    id = "offline_$itemId",
    libraryItemId = itemId,
    episodeId = null,
    mediaType = "book",
    mediaMetadata = metadata ?: BookMetadata(
        title = downloadTitle, titleIgnorePrefix = null, subtitle = null,
        authorName = downloadAuthor, narratorName = null, seriesName = null,
        publishedYear = null, description = null, language = null,
    ),
    chapters = chapters,
    displayTitle = metadata?.title ?: downloadTitle,
    displayAuthor = metadata?.authorName ?: downloadAuthor ?: "",
    duration = duration,
    playMethod = 0,
    currentTime = currentTime,
    audioTracks = buildOfflineAudioTracks(audioFiles, fileCount, duration),
)

// Build synthetic AudioTracks for an offline session so that helpers like
// toTrackIndex/toPositionInTrack and updateCurrentTime correctly translate
// between absolute book time and per-MediaItem position when there are
// multiple local files. Prefers real per-file durations from cached
// audioFiles; falls back to evenly splitting the total duration when
// only a file count is known.
internal fun buildOfflineAudioTracks(
    audioFiles: List<AudioFile>,
    fileCount: Int,
    totalDuration: Double,
): List<AudioTrack> {
    if (audioFiles.isNotEmpty()) {
        var offset = 0.0
        return audioFiles.mapIndexed { i, file ->
            val track = AudioTrack(
                index = i,
                startOffset = offset,
                duration = file.duration,
                title = file.metadata.filename,
                contentUrl = "",
                mimeType = file.mimeType,
            )
            offset += file.duration
            track
        }
    }
    if (fileCount <= 1 || totalDuration <= 0.0) return emptyList()
    val perFile = totalDuration / fileCount
    return (0 until fileCount).map { i ->
        AudioTrack(
            index = i,
            startOffset = i * perFile,
            duration = perFile,
            title = "",
            contentUrl = "",
            mimeType = "",
        )
    }
}
