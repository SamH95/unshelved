package com.samwise.unshelved.feature.home

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.model.Series
import com.samwise.unshelved.core.network.BookMediaDto
import com.samwise.unshelved.core.network.BookMetadataDto
import com.samwise.unshelved.core.network.LibraryItemDto
import com.samwise.unshelved.core.network.PersonalizedShelfDto
import com.samwise.unshelved.feature.library.LibraryRepository
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val selectedLibraryIdFlow = MutableStateFlow<String?>("lib-1")
    private val progressMapFlow = MutableStateFlow<Map<String, MediaProgress>>(emptyMap())

    private val testItem = LibraryItem(
        id = "item-1",
        libraryId = "lib-1",
        media = BookMedia(
            id = "media-1",
            metadata = BookMetadata(
                title = "Test Book",
                titleIgnorePrefix = null,
                subtitle = null,
                authorName = "Author",
                narratorName = null,
                seriesName = null,
                publishedYear = null,
                description = null,
                language = null,
            ),
            coverPath = null,
            duration = 3600.0,
        ),
        addedAt = 1000L,
        updatedAt = 2000L,
    )

    private val testGson = Gson()

    private val testItemDto = LibraryItemDto(
        id = "item-1",
        libraryId = "lib-1",
        media = testGson.toJsonTree(BookMediaDto(
            metadata = BookMetadataDto(
                title = "Test Book",
                titleIgnorePrefix = null,
                subtitle = null,
                authorName = "Author",
                narratorName = null,
                seriesName = null,
                publishedYear = null,
                description = null,
                language = null,
            ),
            coverPath = null,
        )),
    )

    private fun createShelves(vararg shelfIds: String): List<PersonalizedShelfDto> {
        return shelfIds.map { id ->
            PersonalizedShelfDto(
                id = id,
                label = id,
                type = "book",
                entities = listOf(testItemDto),
            )
        }
    }

    private fun createViewModel(
        cachedShelves: List<PersonalizedShelfDto>? = null,
        networkShelves: Result<List<PersonalizedShelfDto>> = Result.success(emptyList()),
        networkSeries: Result<List<Series>> = Result.success(emptyList()),
    ): HomeViewModel {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getCachedPersonalized("lib-1") } returns cachedShelves
            coEvery { getCachedShelvesEntity("lib-1") } returns null
            coEvery { getPersonalized("lib-1") } returns networkShelves
            coEvery { getLibrarySeries("lib-1", any()) } returns networkSeries
            coEvery { getLibraries() } returns Result.success(emptyList())
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
            every { selectedLibraryMediaType } returns flowOf("book")
        }

        val progressCache = mockk<ProgressCache> {
            every { progressMap } returns progressMapFlow
            every { refresh() } just runs
        }

        return HomeViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = progressCache,
        )
    }

    @Test
    fun `load with no cache fetches from network`() = runTest {
        val shelves = createShelves("recently-added")
        val vm = createViewModel(
            cachedShelves = null,
            networkShelves = Result.success(shelves),
        )

        val state = vm.state.value
        assertFalse(state.isLoading)
    }

    @Test
    fun `load with cached shelves shows items immediately`() = runTest {
        val shelves = createShelves("recently-added", "continue-listening")
        val vm = createViewModel(cachedShelves = shelves)

        val state = vm.state.value
        assertFalse(state.isLoading)
    }

    @Test
    fun `hasContent returns false when all lists empty`() {
        val state = HomeState()
        assertFalse(state.hasContent)
    }

    @Test
    fun `hasContent returns true when continueListening has items`() {
        val state = HomeState(continueListening = listOf(testItem))
        assertTrue(state.hasContent)
    }

    @Test
    fun `hasContent returns true when recentlyAdded has items`() {
        val state = HomeState(recentlyAdded = listOf(testItem))
        assertTrue(state.hasContent)
    }

    @Test
    fun `hasContent returns true when discover has items`() {
        val state = HomeState(discover = listOf(testItem))
        assertTrue(state.hasContent)
    }

    @Test
    fun `hasContent returns true when recentSeries has items`() {
        val series = Series(
            id = "s1", name = "Series 1", description = null,
            addedAt = 1000L, updatedAt = 2000L,
        )
        val state = HomeState(recentSeries = listOf(series))
        assertTrue(state.hasContent)
    }

    @Test
    fun `hasContent returns true when continueSeries has items`() {
        val state = HomeState(continueSeries = listOf(testItem))
        assertTrue(state.hasContent)
    }

    @Test
    fun `load with null libraryId and no preference does not crash`() = runTest {
        selectedLibraryIdFlow.value = null

        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns Result.success(emptyList())
            coEvery { getCachedPersonalized(any()) } returns null
            coEvery { getPersonalized(any()) } returns Result.success(emptyList())
            coEvery { getLibrarySeries(any(), any()) } returns Result.success(emptyList())
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
            every { selectedLibraryMediaType } returns flowOf("book")
        }

        val progressCache = mockk<ProgressCache> {
            every { progressMap } returns progressMapFlow
            every { refresh() } just runs
        }

        val vm = HomeViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = progressCache,
        )

        vm.load(null)

        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `empty shelves result in empty state`() = runTest {
        val vm = createViewModel(
            cachedShelves = null,
            networkShelves = Result.success(emptyList()),
        )

        val state = vm.state.value
        assertTrue(state.continueListening.isEmpty())
        assertTrue(state.recentlyAdded.isEmpty())
        assertTrue(state.discover.isEmpty())
        assertTrue(state.continueSeries.isEmpty())
        assertFalse(state.hasContent)
    }
}
