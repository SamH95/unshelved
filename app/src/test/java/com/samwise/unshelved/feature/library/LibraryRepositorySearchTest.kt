package com.samwise.unshelved.feature.library

import com.samwise.unshelved.core.network.AbsApi
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.AuthorDto
import com.samwise.unshelved.core.network.BookMediaDto
import com.samwise.unshelved.core.network.BookMetadataDto
import com.samwise.unshelved.core.network.LibraryItemDto
import com.samwise.unshelved.core.network.LibraryItemsResponse
import com.samwise.unshelved.core.network.SearchBookResult
import com.samwise.unshelved.core.network.SearchResponse
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class LibraryRepositorySearchTest {

    private val gson = Gson()

    private fun itemDto(id: String, title: String, author: String? = null) = LibraryItemDto(
        id = id,
        media = gson.toJsonTree(BookMediaDto(
            metadata = BookMetadataDto(
                title = title,
                titleIgnorePrefix = title,
                subtitle = null,
                authorName = author,
                narratorName = null,
                seriesName = null,
                publishedYear = null,
                description = null,
                language = null,
            ),
            coverPath = null,
        )),
    )

    private fun searchResponse(
        books: List<LibraryItemDto> = emptyList(),
        authors: List<AuthorDto>? = null,
    ) = SearchResponse(
        book = books.map { SearchBookResult(it) },
        authors = authors,
    )

    private fun itemsResponse(items: List<LibraryItemDto>) = LibraryItemsResponse(
        results = items,
        total = items.size,
        limit = 30,
        page = 0,
        numPages = 1,
        sortBy = null,
        sortDesc = false,
        filterBy = null,
    )

    private fun createRepo(api: AbsApi): LibraryRepository {
        val apiProvider = mockk<ApiProvider> { every { getApi() } returns api }
        val db = mockk<com.samwise.unshelved.core.database.UnshelvedDatabase>(relaxed = true)
        return LibraryRepository(apiProvider, db)
    }

    @Test
    fun `search returns book results when no authors matched`() = runTest {
        val hobbit = itemDto("item-1", "The Hobbit", "J.R.R. Tolkien")
        val api = mockk<AbsApi> {
            coEvery { search("lib-1", "hobbit") } returns Response.success(
                searchResponse(books = listOf(hobbit))
            )
        }
        val repo = createRepo(api)

        val results = repo.search("lib-1", "hobbit").getOrThrow()

        assertEquals(1, results.size)
        assertEquals("The Hobbit", results[0].media.metadata.title)
        coVerify(exactly = 0) { api.getLibraryItems(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `search fetches author books when authors matched`() = runTest {
        val lotr = itemDto("item-2", "The Lord of the Rings", "J.R.R. Tolkien")
        val tolkien = AuthorDto(id = "aut-tolkien", name = "J.R.R. Tolkien", description = null, imagePath = null)
        val api = mockk<AbsApi> {
            coEvery { search("lib-1", "tolkien") } returns Response.success(
                searchResponse(books = emptyList(), authors = listOf(tolkien))
            )
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any(), any(), any()) } returns Response.success(
                itemsResponse(listOf(lotr))
            )
        }
        val repo = createRepo(api)

        val results = repo.search("lib-1", "tolkien").getOrThrow()

        assertEquals(1, results.size)
        assertEquals("The Lord of the Rings", results[0].media.metadata.title)
    }

    @Test
    fun `search deduplicates books already in search results`() = runTest {
        val hobbit = itemDto("item-1", "The Hobbit", "J.R.R. Tolkien")
        val lotr = itemDto("item-2", "The Lord of the Rings", "J.R.R. Tolkien")
        val tolkien = AuthorDto(id = "aut-tolkien", name = "J.R.R. Tolkien", description = null, imagePath = null)
        val api = mockk<AbsApi> {
            coEvery { search("lib-1", "tolkien") } returns Response.success(
                searchResponse(books = listOf(hobbit), authors = listOf(tolkien))
            )
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any(), any(), any()) } returns Response.success(
                itemsResponse(listOf(hobbit, lotr))
            )
        }
        val repo = createRepo(api)

        val results = repo.search("lib-1", "tolkien").getOrThrow()

        assertEquals(2, results.size)
        assertEquals(listOf("item-1", "item-2"), results.map { it.id })
    }

    @Test
    fun `search merges books from multiple matched authors`() = runTest {
        val book1 = itemDto("item-1", "Good Omens", "Terry Pratchett & Neil Gaiman")
        val book2 = itemDto("item-2", "American Gods", "Neil Gaiman")
        val author1 = AuthorDto(id = "aut-pratchett", name = "Terry Pratchett", description = null, imagePath = null)
        val author2 = AuthorDto(id = "aut-gaiman", name = "Neil Gaiman", description = null, imagePath = null)
        val api = mockk<AbsApi> {
            coEvery { search("lib-1", "gaiman") } returns Response.success(
                searchResponse(books = emptyList(), authors = listOf(author1, author2))
            )
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any(), any(), any()) } returnsMany listOf(
                Response.success(itemsResponse(listOf(book1))),
                Response.success(itemsResponse(listOf(book1, book2))),
            )
        }
        val repo = createRepo(api)

        val results = repo.search("lib-1", "gaiman").getOrThrow()

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "item-1" })
        assertTrue(results.any { it.id == "item-2" })
    }

    @Test
    fun `search returns failure when API fails`() = runTest {
        val api = mockk<AbsApi> {
            coEvery { search("lib-1", "test") } returns Response.error(500, "".toResponseBody())
        }
        val repo = createRepo(api)

        val result = repo.search("lib-1", "test")

        assertTrue(result.isFailure)
    }

    @Test
    fun `search still returns book results when author filter API fails`() = runTest {
        val hobbit = itemDto("item-1", "The Hobbit", "J.R.R. Tolkien")
        val tolkien = AuthorDto(id = "aut-tolkien", name = "J.R.R. Tolkien", description = null, imagePath = null)
        val api = mockk<AbsApi> {
            coEvery { search("lib-1", "tolkien") } returns Response.success(
                searchResponse(books = listOf(hobbit), authors = listOf(tolkien))
            )
            coEvery { getLibraryItems(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                Response.error(500, "".toResponseBody())
        }
        val repo = createRepo(api)

        val results = repo.search("lib-1", "tolkien").getOrThrow()

        assertEquals(1, results.size)
        assertEquals("The Hobbit", results[0].media.metadata.title)
    }
}
