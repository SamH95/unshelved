package com.samwise.unshelved.feature.series

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.Library
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.Series
import com.samwise.unshelved.feature.library.LibraryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SeriesListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val selectedLibraryIdFlow = MutableStateFlow<String?>("lib-1")

    private val testSeries = listOf(
        Series(id = "s1", name = "Series A", description = null, addedAt = 1000L, updatedAt = 2000L),
        Series(id = "s2", name = "Series B", description = "A great series", addedAt = 1000L, updatedAt = 3000L),
    )

    private fun createViewModel(
        libraries: Result<List<Library>> = Result.success(
            listOf(Library(id = "lib-1", name = "Audiobooks", mediaType = "book", icon = "database"))
        ),
        series: Result<List<Series>> = Result.success(testSeries),
    ): SeriesListViewModel {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns libraries
            coEvery { getLibrarySeries(any(), any()) } returns series
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
        }

        return SeriesListViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
        )
    }

    @Test
    fun `load populates series from repository`() = runTest {
        val vm = createViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.series.size)
        assertEquals("Series A", state.series[0].name)
        assertNull(state.error)
    }

    @Test
    fun `load with no library sets error`() = runTest {
        selectedLibraryIdFlow.value = null

        val vm = createViewModel(
            libraries = Result.success(emptyList()),
        )

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals("No library selected", state.error)
    }

    @Test
    fun `load failure sets error state`() = runTest {
        val vm = createViewModel(
            series = Result.failure(RuntimeException("Network error")),
        )

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Network error", state.error)
        assertTrue(state.series.isEmpty())
    }

    @Test
    fun `refresh updates series`() = runTest {
        val updatedSeries = listOf(
            Series(id = "s1", name = "Series A", description = null, addedAt = 1000L, updatedAt = 2000L),
            Series(id = "s3", name = "Series C", description = null, addedAt = 1000L, updatedAt = 4000L),
        )

        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns Result.success(emptyList())
            coEvery { getLibrarySeries(any(), any()) } returns Result.success(testSeries) andThen Result.success(updatedSeries)
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
        }

        val vm = SeriesListViewModel(libraryRepository = libraryRepository, prefs = prefs)
        assertEquals(2, vm.state.value.series.size)

        vm.refresh()

        assertEquals(2, vm.state.value.series.size)
        assertTrue(vm.state.value.series.any { it.id == "s3" })
    }

    @Test
    fun `library change reloads series`() = runTest {
        val vm = createViewModel()
        assertEquals(2, vm.state.value.series.size)

        selectedLibraryIdFlow.value = "lib-2"

        assertFalse(vm.state.value.isLoading)
    }
}
