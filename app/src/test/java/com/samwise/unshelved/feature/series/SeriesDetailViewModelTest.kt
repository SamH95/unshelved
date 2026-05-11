package com.samwise.unshelved.feature.series

import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.model.Series
import com.samwise.unshelved.feature.library.LibraryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SeriesDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seriesId = "series-1"
    private val progressMapFlow = MutableStateFlow<Map<String, MediaProgress>>(emptyMap())

    private fun testItem(id: String, title: String) = LibraryItem(
        id = id,
        libraryId = "lib-1",
        media = BookMedia(
            id = "media-$id",
            metadata = BookMetadata(
                title = title,
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

    private val testSeriesDetail = Series(
        id = seriesId,
        name = "Test Series",
        description = "A series",
        addedAt = 1000L,
        updatedAt = 2000L,
        books = listOf(testItem("book-1", "Book One"), testItem("book-2", "Book Two")),
    )

    private val testItems = listOf(
        testItem("book-1", "Book One"),
        testItem("book-2", "Book Two"),
        testItem("book-3", "Book Three"),
    )

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    private fun createViewModel(
        seriesDetail: Result<Series> = Result.success(testSeriesDetail),
        items: Result<Pair<List<LibraryItem>, Int>> = Result.success(Pair(testItems, testItems.size)),
        libraryId: String? = "lib-1",
    ): SeriesDetailViewModel {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getSeriesDetail(seriesId) } returns seriesDetail
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any()) } returns items
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns flowOf(libraryId)
            every { activeLibraryId } returns flowOf(libraryId)
        }

        val progressCache = mockk<ProgressCache> {
            every { progressMap } returns progressMapFlow
        }

        return SeriesDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("seriesId" to seriesId)),
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = progressCache,
        )
    }

    @Test
    fun `load populates series name and books`() = runTest {
        val vm = createViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Test Series", state.seriesName)
        assertEquals(3, state.books.size)
        assertNull(state.error)
    }

    @Test
    fun `load with no library sets error when no books`() = runTest {
        val vm = createViewModel(
            seriesDetail = Result.failure(RuntimeException("offline")),
            libraryId = null,
        )

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.books.isEmpty())
    }

    @Test
    fun `items failure with series books falls back`() = runTest {
        val vm = createViewModel(
            seriesDetail = Result.success(testSeriesDetail),
            items = Result.failure(RuntimeException("Network error")),
        )

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Test Series", state.seriesName)
        assertEquals(2, state.books.size)
        assertNull(state.error)
    }

    @Test
    fun `both calls fail sets error`() = runTest {
        val vm = createViewModel(
            seriesDetail = Result.failure(RuntimeException("offline")),
            items = Result.failure(RuntimeException("offline")),
        )

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertTrue(state.books.isEmpty())
        assertNotNull(state.error)
    }

    @Test
    fun `toggleViewMode switches grid and list`() = runTest {
        val vm = createViewModel()

        assertTrue(vm.state.value.isGridView)

        vm.toggleViewMode()
        assertFalse(vm.state.value.isGridView)

        vm.toggleViewMode()
        assertTrue(vm.state.value.isGridView)
    }
}
