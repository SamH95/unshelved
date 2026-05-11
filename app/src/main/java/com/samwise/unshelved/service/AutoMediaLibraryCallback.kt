package com.samwise.unshelved.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.samwise.unshelved.R
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.toDomain
import com.samwise.unshelved.feature.library.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val ROOT_ID = "root"
private const val TAB_CONTINUE = "tab_continue"
private const val TAB_RECENT = "tab_recent"
private const val TAB_LIBRARY = "tab_library"
private const val TAB_LATEST = "tab_latest"
private const val ITEM_PREFIX = "item/"
private const val EPISODE_PREFIX = "episode/"

const val CMD_JUMP_BACK = "CMD_JUMP_BACK"
const val CMD_JUMP_FORWARD = "CMD_JUMP_FORWARD"
const val CMD_PREV_CHAPTER = "CMD_PREV_CHAPTER"
const val CMD_NEXT_CHAPTER = "CMD_NEXT_CHAPTER"
const val CMD_CYCLE_SPEED = "CMD_CYCLE_SPEED"

@Singleton
class AutoMediaLibraryCallback @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastSearchResults: List<MediaItem> = emptyList()

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand(CMD_JUMP_BACK, Bundle.EMPTY))
            .add(SessionCommand(CMD_JUMP_FORWARD, Bundle.EMPTY))
            .add(SessionCommand(CMD_PREV_CHAPTER, Bundle.EMPTY))
            .add(SessionCommand(CMD_NEXT_CHAPTER, Bundle.EMPTY))
            .add(SessionCommand(CMD_CYCLE_SPEED, Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        val root = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setTitle("Unshelved")
                    .build()
            )
            .build()
        LibraryResult.ofItem(root, null)
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        when {
            parentId == ROOT_ID -> buildRootChildren()
            parentId == TAB_CONTINUE -> buildContinueItems()
            parentId == TAB_RECENT -> buildRecentItems()
            parentId == TAB_LIBRARY -> buildLibraryItems(page, pageSize)
            parentId == TAB_LATEST -> buildLatestEpisodeItems()
            else -> LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    private suspend fun buildRootChildren(): LibraryResult<ImmutableList<MediaItem>> {
        val loggedIn = prefs.isLoggedIn()
        val pkg = context.packageName
        val hasPodcastLib = prefs.podcastLibraryId.firstOrNull() != null
        val tabs = buildList {
            if (loggedIn) {
                add(browseTab(TAB_CONTINUE, context.getString(R.string.auto_tab_continue), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, pkg, R.drawable.ic_tab_continue))
                add(browseTab(TAB_RECENT, context.getString(R.string.auto_tab_recent), MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS, pkg, R.drawable.ic_tab_library))
                if (hasPodcastLib) {
                    add(browseTab(TAB_LATEST, context.getString(R.string.auto_tab_latest), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS, pkg, R.drawable.ic_tab_latest))
                }
                add(browseTab(TAB_LIBRARY, context.getString(R.string.auto_tab_library), MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS, pkg, R.drawable.ic_tab_library))
            }
        }
        return LibraryResult.ofItemList(tabs, null)
    }

    private suspend fun buildContinueItems(): LibraryResult<ImmutableList<MediaItem>> {
        val serverUrl = prefs.getServerUrl() ?: return LibraryResult.ofItemList(emptyList(), null)
        val token = prefs.getAuthToken() ?: return LibraryResult.ofItemList(emptyList(), null)
        val items = mutableListOf<MediaItem>()

        val bookLibId = prefs.selectedLibraryId.firstOrNull()
        if (bookLibId != null) {
            val shelves = libraryRepository.getPersonalized(bookLibId).getOrNull()
            val continueItems = shelves?.find { it.id == "continue-listening" }?.entities?.map { it.toDomain() } ?: emptyList()
            items += continueItems.map { it.toMediaItem(serverUrl, token) }
        }

        val podLibId = prefs.podcastLibraryId.firstOrNull()
        if (podLibId != null) {
            val shelves = libraryRepository.getPersonalized(podLibId).getOrNull()
            val continueItems = shelves?.find { it.id == "continue-listening" }?.entities?.map { it.toDomain() } ?: emptyList()
            items += continueItems.map { item ->
                val ep = item.recentEpisode
                if (ep != null) {
                    val artUri = Uri.parse("$serverUrl/api/items/${item.id}/cover?token=$token")
                    MediaItem.Builder()
                        .setMediaId("$EPISODE_PREFIX${item.id}/${ep.id}")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                .setTitle(ep.title)
                                .setArtist(item.title)
                                .setArtworkUri(artUri)
                                .build()
                        )
                        .build()
                } else {
                    item.toMediaItem(serverUrl, token)
                }
            }
        }

        return LibraryResult.ofItemList(items, null)
    }

    private suspend fun buildRecentItems(): LibraryResult<ImmutableList<MediaItem>> {
        val serverUrl = prefs.getServerUrl() ?: return LibraryResult.ofItemList(emptyList(), null)
        val token = prefs.getAuthToken() ?: return LibraryResult.ofItemList(emptyList(), null)
        val bookLibId = prefs.selectedLibraryId.firstOrNull() ?: return LibraryResult.ofItemList(emptyList(), null)
        val shelves = libraryRepository.getPersonalized(bookLibId).getOrNull() ?: return LibraryResult.ofItemList(emptyList(), null)
        val items = shelves.find { it.id == "recently-added" }?.entities?.map { it.toDomain() } ?: emptyList()
        return LibraryResult.ofItemList(items.map { it.toMediaItem(serverUrl, token) }, null)
    }

    private suspend fun buildLibraryItems(page: Int, pageSize: Int): LibraryResult<ImmutableList<MediaItem>> {
        val serverUrl = prefs.getServerUrl() ?: return LibraryResult.ofItemList(emptyList(), null)
        val token = prefs.getAuthToken() ?: return LibraryResult.ofItemList(emptyList(), null)
        val libraryId = prefs.selectedLibraryId.firstOrNull() ?: return LibraryResult.ofItemList(emptyList(), null)
        val limit = if (pageSize > 0) pageSize else 100
        val (items, _) = libraryRepository.getLibraryItems(libraryId, page = page, limit = limit).getOrElse { return LibraryResult.ofItemList(emptyList(), null) }
        return LibraryResult.ofItemList(items.map { it.toMediaItem(serverUrl, token) }, null)
    }

    private suspend fun buildLatestEpisodeItems(): LibraryResult<ImmutableList<MediaItem>> {
        val serverUrl = prefs.getServerUrl() ?: return LibraryResult.ofItemList(emptyList(), null)
        val token = prefs.getAuthToken() ?: return LibraryResult.ofItemList(emptyList(), null)
        val libraryId = prefs.podcastLibraryId.firstOrNull() ?: return LibraryResult.ofItemList(emptyList(), null)
        val episodes = libraryRepository.getRecentEpisodes(libraryId, limit = 50).getOrElse { return LibraryResult.ofItemList(emptyList(), null) }
        val items = episodes.map { ep ->
            val artUri = Uri.parse("$serverUrl/api/items/${ep.libraryItemId}/cover?token=$token")
            MediaItem.Builder()
                .setMediaId("$EPISODE_PREFIX${ep.libraryItemId}/${ep.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                        .setTitle(ep.title)
                        .setArtist(ep.podcastTitle ?: ep.podcastAuthor ?: "")
                        .setArtworkUri(artUri)
                        .build()
                )
                .build()
        }
        return LibraryResult.ofItemList(items, null)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val firstId = mediaItems.firstOrNull()?.mediaId ?: ""
        if (firstId.startsWith(EPISODE_PREFIX)) {
            return scope.future {
                val parts = firstId.removePrefix(EPISODE_PREFIX).split("/", limit = 2)
                if (parts.size != 2) return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
                val (libraryItemId, episodeId) = parts
                val serverUrl = prefs.getServerUrl()
                    ?: return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
                playerRepository.prepareAutoEpisodePlayback(libraryItemId, episodeId, serverUrl)
            }
        }
        if (!firstId.startsWith(ITEM_PREFIX)) {
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
        return scope.future {
            val itemId = firstId.removePrefix(ITEM_PREFIX)
            val serverUrl = prefs.getServerUrl()
                ?: return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            playerRepository.prepareAutoPlayback(itemId, serverUrl)
        }
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        val serverUrl = prefs.getServerUrl() ?: return@future LibraryResult.ofError<Void>(SessionError.ERROR_NOT_SUPPORTED)
        val token = prefs.getAuthToken() ?: return@future LibraryResult.ofError<Void>(SessionError.ERROR_NOT_SUPPORTED)
        val libraryId = prefs.activeLibraryId.firstOrNull() ?: return@future LibraryResult.ofError<Void>(SessionError.ERROR_NOT_SUPPORTED)
        val items = libraryRepository.search(libraryId, query).getOrElse { emptyList() }
        lastSearchResults = items.map { it.toMediaItem(serverUrl, token) }
        session.notifySearchResultChanged(browser, query, lastSearchResults.size, null)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        LibraryResult.ofItemList(lastSearchResults, null)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> = scope.future {
        val player = session.player
        withContext(Dispatchers.Main) {
            when (customCommand.customAction) {
                CMD_JUMP_BACK -> {
                    val secs = prefs.jumpBackSeconds.firstOrNull() ?: 10
                    val newPos = (player.currentPosition - secs * 1000).coerceAtLeast(0)
                    player.seekTo(newPos)
                }
                CMD_JUMP_FORWARD -> {
                    val secs = prefs.jumpForwardSeconds.firstOrNull() ?: 30
                    val newPos = player.currentPosition + secs * 1000
                    player.seekTo(newPos)
                }
                CMD_PREV_CHAPTER -> playerRepository.previousChapter()
                CMD_NEXT_CHAPTER -> playerRepository.nextChapter()
                CMD_CYCLE_SPEED -> {
                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
                    val current = player.playbackParameters.speed
                    val nextIndex = (speeds.indexOfFirst { it >= current } + 1) % speeds.size
                    val newSpeed = speeds[nextIndex]
                    player.setPlaybackSpeed(newSpeed)
                    playerRepository.onSpeedChangedFromNotification(newSpeed)
                }
            }
        }
        SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun browseTab(mediaId: String, title: String, mediaType: Int, pkg: String, iconRes: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .setTitle(title)
                    .setArtworkUri(Uri.parse("android.resource://$pkg/$iconRes"))
                    .build()
            )
            .build()
}

private fun LibraryItem.toMediaItem(serverUrl: String, token: String): MediaItem {
    val artUri = Uri.parse("$serverUrl/api/items/$id/cover?token=$token")
    return MediaItem.Builder()
        .setMediaId("$ITEM_PREFIX$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(if (isPodcast) MediaMetadata.MEDIA_TYPE_PODCAST else MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                .setTitle(title)
                .setArtist(authorName)
                .setArtworkUri(artUri)
                .build()
        )
        .build()
}
