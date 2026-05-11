package com.samwise.unshelved.feature.search

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.feature.library.LibraryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val testItem = LibraryItem(
        id = "item-1",
        libraryId = "lib-1",
        media = BookMedia(
            id = "media-1",
            metadata = BookMetadata(
                title = "The Hobbit",
                titleIgnorePrefix = "Hobbit",
                subtitle = null,
                authorName = "J.R.R. Tolkien",
                narratorName = null,
                seriesName = null,
                publishedYear = null,
                description = null,
                language = null,
            ),
            coverPath = "/cover.jpg",
            duration = 3600.0,
        ),
        addedAt = 0,
        updatedAt = 0,
    )

    private fun createViewModel(
        selectedLibraryId: String? = "lib-1",
        searchResult: Result<List<LibraryItem>> = Result.success(listOf(testItem)),
    ): SearchViewModel {
        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { this@mockk.selectedLibraryId } returns flowOf(selectedLibraryId)
            every { this@mockk.activeLibraryId } returns flowOf(selectedLibraryId)
        }

        val libraryRepository = mockk<LibraryRepository> {
            coEvery { search(any(), any()) } returns searchResult
            coEvery { getLibraries() } returns Result.success(emptyList())
        }

        return SearchViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
        )
    }

    @Test
    fun `initial state is empty`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("", state.query)
        assertFalse(state.isLoading)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `blank query clears results`() = runTest {
        val vm = createViewModel()

        vm.onQueryChanged("test")
        vm.onQueryChanged("")
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.results.isEmpty())
    }

    @Test
    fun `query updates state`() = runTest {
        val vm = createViewModel()

        vm.onQueryChanged("hobbit")

        assertEquals("hobbit", vm.state.value.query)
    }

    @Test
    fun `successful search populates results`() = runTest {
        val vm = createViewModel()

        vm.onQueryChanged("hobbit")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.results.size)
        assertEquals("The Hobbit", state.results.first().media.metadata.title)
        assertFalse(state.isLoading)
    }

    @Test
    fun `search failure sets error`() = runTest {
        val vm = createViewModel(
            searchResult = Result.failure(RuntimeException("network error")),
        )

        vm.onQueryChanged("test")
        advanceUntilIdle()

        assertEquals("network error", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }
}
