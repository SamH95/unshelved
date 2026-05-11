package com.samwise.unshelved.feature.detail

import androidx.lifecycle.SavedStateHandle
import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.database.CachedItemEntity
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val itemId = "test-item-1"

    private val testMetadata = BookMetadata(
        title = "Test Book",
        titleIgnorePrefix = null,
        subtitle = null,
        authorName = "Test Author",
        narratorName = null,
        seriesName = null,
        publishedYear = null,
        description = null,
        language = null,
    )

    private val testItem = LibraryItem(
        id = itemId,
        libraryId = "lib-1",
        media = BookMedia(
            id = "media-1",
            metadata = testMetadata,
            coverPath = null,
            duration = 3600.0,
        ),
        addedAt = 1000L,
        updatedAt = 2000L,
    )

    private val testProgress = MediaProgress(
        id = "progress-1",
        libraryItemId = itemId,
        episodeId = null,
        duration = 3600.0,
        progress = 0.5f,
        currentTime = 1800.0,
        isFinished = false,
        hideFromContinueListening = false,
        lastUpdate = 3000L,
        startedAt = 1000L,
        finishedAt = null,
    )

    private val progressMapFlow = MutableStateFlow<Map<String, MediaProgress>>(emptyMap())

    private fun createViewModel(
        cachedItem: LibraryItem? = testItem,
        cachedEntity: CachedItemEntity? = CachedItemEntity(itemId, "lib-1", "{}", 0L),
        fetchedItem: Result<LibraryItem> = Result.success(testItem),
        fetchedProgress: Result<MediaProgress> = Result.success(testProgress),
        download: DownloadEntity? = null,
    ): DetailViewModel {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getCachedItem(itemId) } returns cachedItem
            coEvery { getCachedItemEntity(itemId) } returns cachedEntity
            coEvery { getItem(itemId) } returns fetchedItem
            coEvery { getProgress(itemId) } returns fetchedProgress
            coEvery { updateProgress(any(), any()) } returns Result.success(Unit)
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
        }

        val downloadRepository = mockk<DownloadRepository> {
            every { observeDownload(itemId) } returns flowOf(null)
            coEvery { getBookDownload(itemId) } returns download
        }

        val progressCache = mockk<ProgressCache> {
            every { progressMap } returns progressMapFlow
            every { updateItem(any(), any(), any()) } just runs
        }

        val playerRepository = mockk<PlayerRepository> {
            every { prewarmPlayback(itemId) } just runs
        }

        return DetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to itemId)),
            libraryRepository = libraryRepository,
            prefs = prefs,
            downloadRepository = downloadRepository,
            progressCache = progressCache,
            playerRepository = playerRepository,
        )
    }

    @Test
    fun `load with cached item shows item immediately without loading`() = runTest {
        val vm = createViewModel(cachedItem = testItem)

        val state = vm.state.value
        assertEquals(testItem, state.item)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `load with cache miss shows fetched item after loading`() = runTest {
        val vm = createViewModel(cachedItem = null)

        val state = vm.state.value
        assertEquals(testItem, state.item)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `network failure with no cache and no download shows error`() = runTest {
        val vm = createViewModel(
            cachedItem = null,
            fetchedItem = Result.failure(RuntimeException("Network error")),
            fetchedProgress = Result.failure(RuntimeException("Network error")),
            download = null,
        )

        val state = vm.state.value
        assertNull(state.item)
        assertNotNull(state.error)
        assertEquals("Network error", state.error)
    }

    @Test
    fun `network failure with download fallback shows fallback item`() = runTest {
        val download = DownloadEntity(
            downloadId = itemId,
            libraryItemId = itemId,
            title = "Downloaded Book",
            author = "DL Author",
            coverPath = null,
            localPath = null,
            totalBytes = 1000,
            downloadedBytes = 1000,
            status = DownloadStatus.COMPLETED,
            addedAt = 5000L,
        )

        val vm = createViewModel(
            cachedItem = null,
            fetchedItem = Result.failure(RuntimeException("Network error")),
            fetchedProgress = Result.failure(RuntimeException("Network error")),
            download = download,
        )

        val state = vm.state.value
        assertNotNull(state.item)
        assertEquals("Downloaded Book", state.item!!.media.metadata.title)
        assertEquals("DL Author", state.item!!.media.metadata.authorName)
        assertNull(state.error)
    }

    @Test
    fun `toggleFinished from unfinished marks finished`() = runTest {
        val vm = createViewModel()

        // Wait for initial state to settle with progress
        assertEquals(testProgress, vm.state.value.progress)

        vm.toggleFinished()

        val state = vm.state.value
        assertNotNull(state.progress)
        assertTrue(state.progress!!.isFinished)
        assertEquals(1f, state.progress!!.progress)
    }

    @Test
    fun `toggleFinished from finished marks unfinished`() = runTest {
        val finishedProgress = testProgress.copy(isFinished = true, progress = 1f)
        val vm = createViewModel(
            fetchedProgress = Result.success(finishedProgress),
        )

        vm.toggleFinished()

        val state = vm.state.value
        assertNotNull(state.progress)
        assertEquals(false, state.progress!!.isFinished)
        assertEquals(0f, state.progress!!.progress)
    }

    @Test
    fun `toggleFinished with no existing progress creates new progress`() = runTest {
        val vm = createViewModel(
            fetchedProgress = Result.failure(RuntimeException("No progress")),
        )

        assertNull(vm.state.value.progress)

        vm.toggleFinished()

        val state = vm.state.value
        assertNotNull(state.progress)
        assertTrue(state.progress!!.isFinished)
        assertEquals(1f, state.progress!!.progress)
        assertEquals(itemId, state.progress!!.libraryItemId)
    }

    @Test
    fun `toggleFinished network error updates error state`() = runTest {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getCachedItem(itemId) } returns testItem
            coEvery { getCachedItemEntity(itemId) } returns CachedItemEntity(itemId, "lib-1", "{}", System.currentTimeMillis())
            coEvery { getItem(itemId) } returns Result.success(testItem)
            coEvery { getProgress(itemId) } returns Result.success(testProgress)
            coEvery { updateProgress(any(), any()) } throws RuntimeException("Server down")
        }

        val vm = DetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to itemId)),
            libraryRepository = libraryRepository,
            prefs = mockk { every { serverUrl } returns flowOf("https://abs.example.com") },
            downloadRepository = mockk {
                every { observeDownload(itemId) } returns flowOf(null)
            },
            progressCache = mockk {
                every { progressMap } returns progressMapFlow
            },
            playerRepository = mockk {
                every { prewarmPlayback(itemId) } just runs
            },
        )

        vm.toggleFinished()

        assertNotNull(vm.state.value.error)
        assertEquals("Server down", vm.state.value.error)
    }

    @Test
    fun `progress cache emission updates state`() = runTest {
        val vm = createViewModel()

        val newProgress = testProgress.copy(progress = 0.75f, currentTime = 2700.0)
        progressMapFlow.value = mapOf(itemId to newProgress)

        assertEquals(newProgress, vm.state.value.progress)
    }

    @Test
    fun `toggleFinished calls updateProgress on repository`() = runTest {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getCachedItem(itemId) } returns testItem
            coEvery { getCachedItemEntity(itemId) } returns CachedItemEntity(itemId, "lib-1", "{}", System.currentTimeMillis())
            coEvery { getItem(itemId) } returns Result.success(testItem)
            coEvery { getProgress(itemId) } returns Result.success(testProgress)
            coEvery { updateProgress(any(), any()) } returns Result.success(Unit)
        }

        val vm = DetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to itemId)),
            libraryRepository = libraryRepository,
            prefs = mockk { every { serverUrl } returns flowOf("https://abs.example.com") },
            downloadRepository = mockk {
                every { observeDownload(itemId) } returns flowOf(null)
            },
            progressCache = mockk {
                every { progressMap } returns progressMapFlow
                every { updateItem(any(), any(), any()) } just runs
            },
            playerRepository = mockk {
                every { prewarmPlayback(itemId) } just runs
            },
        )

        vm.toggleFinished()

        coVerify { libraryRepository.updateProgress(itemId, any<UpdateProgressRequest>()) }
    }
}
