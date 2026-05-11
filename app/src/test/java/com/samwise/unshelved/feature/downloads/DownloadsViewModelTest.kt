package com.samwise.unshelved.feature.downloads

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.service.DownloadRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun testEntity(id: String, title: String, status: DownloadStatus = DownloadStatus.COMPLETED) =
        DownloadEntity(
            downloadId = id,
            libraryItemId = id,
            title = title,
            author = "Author",
            coverPath = null,
            localPath = "/downloads/$id",
            totalBytes = 1024,
            downloadedBytes = 1024,
            status = status,
        )

    @Test
    fun `downloads flow emits from repository`() = runTest {
        val entities = listOf(testEntity("b1", "Book One"), testEntity("b2", "Book Two"))
        val repository = mockk<DownloadRepository>(relaxUnitFun = true) {
            every { allDownloads } returns flowOf(entities)
        }

        val vm = DownloadsViewModel(repository)

        val result = vm.downloads.first()
        assertEquals(2, result.size)
        assertEquals("Book One", result[0].title)
        assertEquals("Book Two", result[1].title)
    }

    @Test
    fun `deleteDownload calls repository`() = runTest {
        val repository = mockk<DownloadRepository>(relaxUnitFun = true) {
            every { allDownloads } returns flowOf(emptyList())
        }

        val vm = DownloadsViewModel(repository)
        vm.deleteDownload("b1")

        coVerify { repository.deleteDownload("b1") }
    }
}
