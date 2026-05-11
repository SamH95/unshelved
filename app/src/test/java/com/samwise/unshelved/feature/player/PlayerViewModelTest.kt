package com.samwise.unshelved.feature.player

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.AudioTrack
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.Chapter
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.PlaybackSession
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.DownloadRepository
import com.samwise.unshelved.service.PlayerRepository
import com.samwise.unshelved.service.PlayerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testSession = PlaybackSession(
        id = "sess-1",
        libraryItemId = "item-1",
        episodeId = null,
        mediaType = "book",
        mediaMetadata = BookMetadata(
            title = "The Hobbit", titleIgnorePrefix = null, subtitle = null,
            authorName = "Tolkien", narratorName = null, seriesName = null,
            publishedYear = null, description = null, language = null,
        ),
        chapters = listOf(
            Chapter(0, 0.0, 300.0, "Chapter 1"),
            Chapter(1, 300.0, 600.0, "Chapter 2"),
        ),
        displayTitle = "The Hobbit",
        displayAuthor = "Tolkien",
        duration = 600.0,
        playMethod = 1,
        currentTime = 100.0,
        audioTracks = listOf(
            AudioTrack(0, 0.0, 600.0, "Track 1", "/track1.mp3", "audio/mpeg"),
        ),
    )

    private data class TestEnv(
        val vm: PlayerViewModel,
        val playerRepository: PlayerRepository,
        val downloadRepository: DownloadRepository,
        val libraryRepository: LibraryRepository,
    )

    private val playerStateFlow = MutableStateFlow(PlayerState())

    private fun createEnv(
        serverUrl: String? = "https://abs.example.com",
        jumpBack: Int = 10,
        jumpForward: Int = 30,
        isDownloaded: Boolean = false,
    ): TestEnv {
        val playerRepository = mockk<PlayerRepository>(relaxUnitFun = true) {
            every { playerState } returns playerStateFlow
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { this@mockk.serverUrl } returns flowOf(serverUrl)
            every { jumpBackSeconds } returns flowOf(jumpBack)
            every { jumpForwardSeconds } returns flowOf(jumpForward)
            coEvery { getServerUrl() } returns serverUrl
        }

        val libraryRepository = mockk<LibraryRepository>()

        val downloadRepository = mockk<DownloadRepository> {
            coEvery { isDownloaded(any()) } returns isDownloaded
        }

        val vm = PlayerViewModel(
            playerRepository = playerRepository,
            libraryRepository = libraryRepository,
            prefs = prefs,
            downloadRepository = downloadRepository,
        )

        return TestEnv(vm, playerRepository, downloadRepository, libraryRepository)
    }

    @Test
    fun `playerState exposes repository state`() {
        val env = createEnv()
        val stateWithSession = PlayerState(session = testSession, isPlaying = true)
        playerStateFlow.value = stateWithSession

        assertEquals(stateWithSession, env.vm.playerState.value)
    }

    @Test
    fun `serverUrl flows from prefs`() = runTest {
        val env = createEnv(serverUrl = "https://my-server.com")
        assertEquals("https://my-server.com", env.vm.serverUrl.value)
    }

    @Test
    fun `jumpBackSeconds flows from prefs`() = runTest {
        val env = createEnv(jumpBack = 15)
        assertEquals(15, env.vm.jumpBackSeconds.value)
    }

    @Test
    fun `jumpForwardSeconds flows from prefs`() = runTest {
        val env = createEnv(jumpForward = 45)
        assertEquals(45, env.vm.jumpForwardSeconds.value)
    }

    @Test
    fun `prewarmPlayback delegates to repository`() {
        val env = createEnv()
        env.vm.prewarmPlayback("item-42")
        verify { env.playerRepository.prewarmPlayback("item-42") }
    }

    @Test
    fun `startPlayback routes to streaming when not downloaded`() = runTest {
        val env = createEnv(isDownloaded = false, serverUrl = "https://abs.example.com")

        env.vm.startPlayback("item-1")
        advanceUntilIdle()

        coVerify { env.downloadRepository.isDownloaded("item-1") }
        coVerify { env.playerRepository.startPlayback("item-1", "https://abs.example.com") }
        coVerify(exactly = 0) { env.playerRepository.startOfflinePlayback(any()) }
    }

    @Test
    fun `startPlayback routes to offline when downloaded`() = runTest {
        val env = createEnv(isDownloaded = true)

        env.vm.startPlayback("item-1")
        advanceUntilIdle()

        coVerify { env.downloadRepository.isDownloaded("item-1") }
        coVerify { env.playerRepository.startOfflinePlayback("item-1") }
        coVerify(exactly = 0) { env.playerRepository.startPlayback(any(), any()) }
    }

    @Test
    fun `startPlayback does nothing when serverUrl is null and not downloaded`() = runTest {
        val env = createEnv(serverUrl = null, isDownloaded = false)

        env.vm.startPlayback("item-1")
        advanceUntilIdle()

        coVerify(exactly = 0) { env.playerRepository.startPlayback(any(), any()) }
        coVerify(exactly = 0) { env.playerRepository.startOfflinePlayback(any()) }
    }

    @Test
    fun `startPlaybackAtPosition starts playback then seeks`() = runTest {
        val env = createEnv(serverUrl = "https://abs.example.com")

        env.vm.startPlaybackAtPosition("item-1", 42.5)
        advanceUntilIdle()

        coVerify { env.playerRepository.startPlayback("item-1", "https://abs.example.com") }
        verify { env.playerRepository.seekTo(42.5) }
    }

    @Test
    fun `play delegates to repository`() {
        val env = createEnv()
        env.vm.play()
        verify { env.playerRepository.play() }
    }

    @Test
    fun `pause delegates to repository`() {
        val env = createEnv()
        env.vm.pause()
        verify { env.playerRepository.pause() }
    }

    @Test
    fun `seekTo delegates to repository`() {
        val env = createEnv()
        env.vm.seekTo(42.5)
        verify { env.playerRepository.seekTo(42.5) }
    }

    @Test
    fun `seekForward reads jump seconds from prefs`() = runTest {
        val env = createEnv(jumpForward = 45)

        env.vm.seekForward()
        advanceUntilIdle()

        verify { env.playerRepository.seekForward(45) }
    }

    @Test
    fun `seekBack reads jump seconds from prefs`() = runTest {
        val env = createEnv(jumpBack = 15)

        env.vm.seekBack()
        advanceUntilIdle()

        verify { env.playerRepository.seekBack(15) }
    }

    @Test
    fun `nextChapter delegates to repository`() {
        val env = createEnv()
        env.vm.nextChapter()
        verify { env.playerRepository.nextChapter() }
    }

    @Test
    fun `previousChapter delegates to repository`() {
        val env = createEnv()
        env.vm.previousChapter()
        verify { env.playerRepository.previousChapter() }
    }

    @Test
    fun `setPlaybackSpeed delegates to repository`() {
        val env = createEnv()
        env.vm.setPlaybackSpeed(1.5f)
        verify { env.playerRepository.setPlaybackSpeed(1.5f) }
    }

    @Test
    fun `setSleepTimer delegates to repository`() {
        val env = createEnv()
        env.vm.setSleepTimer(30)
        verify { env.playerRepository.setSleepTimer(30) }
    }

    @Test
    fun `setSleepTimer with null clears timer`() {
        val env = createEnv()
        env.vm.setSleepTimer(null)
        verify { env.playerRepository.setSleepTimer(null) }
    }

    @Test
    fun `updateCurrentTime delegates to repository`() {
        val env = createEnv()
        env.vm.updateCurrentTime()
        verify { env.playerRepository.updateCurrentTime() }
    }

    @Test
    fun `closeSession delegates to repository`() {
        val env = createEnv()
        env.vm.closeSession()
        verify { env.playerRepository.closeSession() }
    }

    @Test
    fun `startDownload does nothing without active session`() = runTest {
        val env = createEnv()
        playerStateFlow.value = PlayerState(session = null)

        env.vm.startDownload()
        advanceUntilIdle()

        coVerify(exactly = 0) { env.libraryRepository.getItem(any()) }
    }

    @Test
    fun `startDownload fetches item and starts download`() = runTest {
        val env = createEnv()
        playerStateFlow.value = PlayerState(session = testSession)

        val testItem = LibraryItem(
            id = "item-1",
            libraryId = "lib-1",
            media = BookMedia(
                id = "media-1",
                metadata = testSession.mediaMetadata,
                coverPath = null,
                duration = 600.0,
            ),
            addedAt = 0,
            updatedAt = 0,
        )
        coEvery { env.libraryRepository.getItem("item-1") } returns Result.success(testItem)
        coEvery { env.downloadRepository.startDownload(any()) } returns Unit

        env.vm.startDownload()
        advanceUntilIdle()

        coVerify { env.libraryRepository.getItem("item-1") }
        coVerify { env.downloadRepository.startDownload(testItem) }
    }
}
