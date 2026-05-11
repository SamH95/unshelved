package com.samwise.unshelved.feature.library

import android.util.Base64
import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.Author
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.Library
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.network.FilterItemDto
import com.samwise.unshelved.core.network.LibraryFilterDataDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val selectedLibraryIdFlow = MutableStateFlow<String?>("lib-1")
    private val progressMapFlow = MutableStateFlow<Map<String, MediaProgress>>(emptyMap())

    private val testLibrary = Library(id = "lib-1", name = "Audiobooks", mediaType = "book", icon = "database")

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

    private fun testItem(
        id: String = "item-1",
        title: String = "Test Book",
        authorName: String? = "Author A",
        authors: List<Author> = emptyList(),
        genres: List<String> = emptyList(),
        narratorName: String? = null,
    ) = LibraryItem(
        id = id,
        libraryId = "lib-1",
        media = BookMedia(
            id = "media-$id",
            metadata = BookMetadata(
                title = title,
                titleIgnorePrefix = null,
                subtitle = null,
                authorName = authorName,
                narratorName = narratorName,
                seriesName = null,
                genres = genres,
                publishedYear = null,
                description = null,
                language = null,
                authors = authors,
            ),
            coverPath = null,
            duration = 3600.0,
        ),
        addedAt = 1000L,
        updatedAt = 2000L,
    )

    private val testFilterData = LibraryFilterDataDto(
        authors = listOf(FilterItemDto("a1", "Author A"), FilterItemDto("a2", "Author B")),
        genres = listOf("Fantasy", "Sci-Fi"),
        narrators = listOf("Narrator One"),
    )

    private fun createViewModel(
        libraries: Result<List<Library>> = Result.success(listOf(testLibrary)),
        items: Result<Pair<List<LibraryItem>, Int>> = Result.success(Pair(listOf(testItem()), 1)),
        filterData: Result<LibraryFilterDataDto> = Result.success(testFilterData),
    ): LibraryViewModel {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns libraries
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any()) } returns items
            coEvery { getFilterData(any()) } returns filterData
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
            every { selectedLibraryMediaType } returns flowOf("book")
            every { selectedLibraryName } returns flowOf("Audiobooks")
            every { activeLibraryName } returns flowOf("Audiobooks")
            every { libraryViewGrid } returns flowOf(true)
            coEvery { saveSelectedLibrary(any(), any(), any()) } just runs
            coEvery { setLibraryViewGrid(any()) } just runs
        }

        val progressCache = mockk<ProgressCache> {
            every { progressMap } returns progressMapFlow
        }

        return LibraryViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = progressCache,
        )
    }

    @Test
    fun `initial load populates items and filters from network`() = runTest {
        val vm = createViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.items.size)
        assertEquals("Test Book", state.items.first().media.metadata.title)
        assertEquals(2, state.filterAuthors.size)
        assertEquals("Author A", state.filterAuthors[0].name)
        assertEquals(2, state.filterGenres.size)
        assertEquals(1, state.filterNarrators.size)
    }

    @Test
    fun `load with no connection uses cached items`() = runTest {
        val vm = createViewModel(
            items = Result.failure(RuntimeException("No connection")),
            filterData = Result.failure(RuntimeException("No connection")),
        )

        val state = vm.state.value
        assertFalse(state.isLoading)
    }

    @Test
    fun `filter data derived from items when getFilterData fails`() = runTest {
        val items = listOf(
            testItem(id = "1", authorName = "John Doe", genres = listOf("Fantasy"), narratorName = "Mike"),
            testItem(id = "2", authorName = "Jane Doe", genres = listOf("Sci-Fi", "Fantasy"), narratorName = "Mike, Sarah"),
        )
        val vm = createViewModel(
            items = Result.success(Pair(items, items.size)),
            filterData = Result.failure(RuntimeException("No filter endpoint")),
        )

        val state = vm.state.value
        assertTrue(state.filterAuthors.isNotEmpty())
        assertEquals(2, state.filterAuthors.size)
        assertTrue(state.filterGenres.containsAll(listOf("Fantasy", "Sci-Fi")))
        assertTrue(state.filterNarrators.containsAll(listOf("Mike", "Sarah")))
    }

    @Test
    fun `filter data derived from cached items when in-memory state empty`() = runTest {
        val cachedItems = listOf(
            testItem(id = "1", authorName = "Cached Author", genres = listOf("Mystery")),
        )

        var callCount = 0
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns Result.success(listOf(testLibrary))
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any()) } answers {
                callCount++
                if (callCount <= 2) Result.failure(RuntimeException("offline"))
                else Result.success(Pair(cachedItems, cachedItems.size))
            }
            coEvery { getFilterData(any()) } returns Result.failure(RuntimeException("offline"))
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
            every { selectedLibraryMediaType } returns flowOf("book")
            every { selectedLibraryName } returns flowOf("Audiobooks")
            every { activeLibraryName } returns flowOf("Audiobooks")
            every { libraryViewGrid } returns flowOf(true)
            coEvery { saveSelectedLibrary(any(), any(), any()) } just runs
        }

        val progressCache = mockk<ProgressCache> {
            every { progressMap } returns progressMapFlow
        }

        val vm = LibraryViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = progressCache,
        )

        val state = vm.state.value
        assertTrue(state.hasFilters)
        assertEquals(1, state.filterAuthors.size)
        assertEquals("Cached Author", state.filterAuthors[0].name)
    }

    @Test
    fun `hasFilters returns false when all filter lists empty`() {
        val state = LibraryState()
        assertFalse(state.hasFilters)
    }

    @Test
    fun `hasFilters returns true when authors available`() {
        val state = LibraryState(filterAuthors = listOf(FilterAuthor("a1", "Author")))
        assertTrue(state.hasFilters)
    }

    @Test
    fun `hasActiveFilter returns false when no filter selected`() {
        val state = LibraryState(filterAuthors = listOf(FilterAuthor("a1", "Author")))
        assertFalse(state.hasActiveFilter)
    }

    @Test
    fun `hasActiveFilter returns true when author selected`() {
        val state = LibraryState(selectedAuthor = FilterAuthor("a1", "Author"))
        assertTrue(state.hasActiveFilter)
    }

    @Test
    fun `selectLibrary clears active filters`() = runTest {
        val vm = createViewModel()

        vm.setFilter(author = FilterAuthor("a1", "Author A"))
        assertEquals("Author A", vm.state.value.selectedAuthor?.name)

        vm.selectLibrary("lib-1")

        assertNull(vm.state.value.selectedAuthor)
        assertNull(vm.state.value.selectedGenre)
        assertNull(vm.state.value.selectedNarrator)
    }

    @Test
    fun `setFilter triggers item reload`() = runTest {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns Result.success(listOf(testLibrary))
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any()) } returns Result.success(Pair(emptyList(), 0))
            coEvery { getFilterData(any()) } returns Result.success(testFilterData)
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
            every { selectedLibraryMediaType } returns flowOf("book")
            every { selectedLibraryName } returns flowOf("Audiobooks")
            every { activeLibraryName } returns flowOf("Audiobooks")
            every { libraryViewGrid } returns flowOf(true)
            coEvery { saveSelectedLibrary(any(), any(), any()) } just runs
        }

        val vm = LibraryViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = mockk { every { progressMap } returns progressMapFlow },
        )

        vm.setFilter(genre = "Fantasy")

        assertEquals("Fantasy", vm.state.value.selectedGenre)
        coVerify(atLeast = 2) { libraryRepository.getLibraryItems(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `refresh with empty libraries list reloads libraries`() = runTest {
        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns Result.failure(RuntimeException("offline")) andThen Result.success(listOf(testLibrary))
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any()) } returns Result.success(Pair(emptyList(), 0))
            coEvery { getFilterData(any()) } returns Result.success(testFilterData)
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
            every { selectedLibraryMediaType } returns flowOf("book")
            every { selectedLibraryName } returns flowOf("Audiobooks")
            every { activeLibraryName } returns flowOf("Audiobooks")
            every { libraryViewGrid } returns flowOf(true)
            coEvery { saveSelectedLibrary(any(), any(), any()) } just runs
        }

        val vm = LibraryViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = mockk { every { progressMap } returns progressMapFlow },
        )

        assertTrue(vm.state.value.libraries.isEmpty())

        vm.refresh()

        assertEquals(1, vm.state.value.libraries.size)
        assertEquals("Audiobooks", vm.state.value.libraries.first().name)
    }

    @Test
    fun `applyFilterLocally filters by author correctly`() = runTest {
        val items = listOf(
            testItem(id = "1", title = "Book A", authorName = "Author A"),
            testItem(id = "2", title = "Book B", authorName = "Author B"),
            testItem(id = "3", title = "Book C", authorName = "Author A, Author C"),
        )

        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns Result.success(listOf(testLibrary))
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), isNull()) } returns Result.success(Pair(items, items.size))
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), isNull(inverse = true)) } returns Result.failure(RuntimeException("server filter failed"))
            coEvery { getFilterData(any()) } returns Result.success(testFilterData)
        }

        val prefs = mockk<UserPreferencesRepository> {
            every { serverUrl } returns flowOf("https://abs.example.com")
            every { selectedLibraryId } returns selectedLibraryIdFlow
            every { activeLibraryId } returns selectedLibraryIdFlow
            every { selectedLibraryMediaType } returns flowOf("book")
            every { selectedLibraryName } returns flowOf("Audiobooks")
            every { activeLibraryName } returns flowOf("Audiobooks")
            every { libraryViewGrid } returns flowOf(true)
            coEvery { saveSelectedLibrary(any(), any(), any()) } just runs
        }

        val vm = LibraryViewModel(
            libraryRepository = libraryRepository,
            prefs = prefs,
            progressCache = mockk { every { progressMap } returns progressMapFlow },
        )

        assertEquals(3, vm.state.value.items.size)

        vm.setFilter(author = FilterAuthor("a1", "Author A"))

        val filtered = vm.state.value.items
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.media.metadata.authorName?.contains("Author A") == true })
    }
}
