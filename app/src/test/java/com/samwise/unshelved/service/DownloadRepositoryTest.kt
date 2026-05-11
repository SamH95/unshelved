package com.samwise.unshelved.service

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.database.DownloadDao
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DownloadRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val downloadDao = mockk<DownloadDao>(relaxUnitFun = true) {
        every { getAllDownloads() } returns flowOf(emptyList())
    }
    private val prefs = mockk<UserPreferencesRepository>()

    private fun createRepository() = DownloadRepository(
        context = mockk(relaxed = true),
        downloadDao = downloadDao,
        prefs = prefs,
    )

    private fun testEntity(
        id: String = "item-1",
        status: DownloadStatus = DownloadStatus.COMPLETED,
        localPath: String? = "/downloads/$id",
    ) = DownloadEntity(
        downloadId = id,
        libraryItemId = id,
        title = "Test Book",
        author = "Author",
        coverPath = null,
        localPath = localPath,
        totalBytes = 1024,
        downloadedBytes = 1024,
        status = status,
    )

    @Test
    fun `startDownload with no server url returns early`() = runTest {
        coEvery { prefs.getServerUrl() } returns null
        val repository = createRepository()

        repository.startDownload(mockk())

        coVerify(exactly = 0) { downloadDao.upsert(any()) }
    }

    @Test
    fun `startDownload with no auth token returns early`() = runTest {
        coEvery { prefs.getServerUrl() } returns "https://abs.example.com"
        coEvery { prefs.getAuthToken() } returns null
        val repository = createRepository()

        repository.startDownload(mockk())

        coVerify(exactly = 0) { downloadDao.upsert(any()) }
    }

    @Test
    fun `isDownloaded true for completed with valid path`() = runTest {
        val entity = testEntity(status = DownloadStatus.COMPLETED, localPath = System.getProperty("java.io.tmpdir"))
        coEvery { downloadDao.getBookDownload("item-1") } returns entity
        val repository = createRepository()

        assertTrue(repository.isDownloaded("item-1"))
    }

    @Test
    fun `isDownloaded false for non-completed`() = runTest {
        coEvery { downloadDao.getBookDownload("item-1") } returns testEntity(status = DownloadStatus.DOWNLOADING)
        val repository = createRepository()

        assertFalse(repository.isDownloaded("item-1"))
    }

    @Test
    fun `isDownloaded false for null entity`() = runTest {
        coEvery { downloadDao.getBookDownload("item-1") } returns null
        val repository = createRepository()

        assertFalse(repository.isDownloaded("item-1"))
    }

    @Test
    fun `isDownloaded false when local path missing`() = runTest {
        coEvery { downloadDao.getBookDownload("item-1") } returns testEntity(
            status = DownloadStatus.COMPLETED,
            localPath = "/nonexistent/path/that/does/not/exist",
        )
        val repository = createRepository()

        assertFalse(repository.isDownloaded("item-1"))
    }

    @Test
    fun `getLocalPath returns null when not downloaded`() = runTest {
        coEvery { downloadDao.getBookDownload("item-1") } returns null
        val repository = createRepository()

        assertNull(repository.getLocalPath("item-1"))
    }

    @Test
    fun `getLocalPath returns null for non-completed`() = runTest {
        coEvery { downloadDao.getBookDownload("item-1") } returns testEntity(status = DownloadStatus.QUEUED)
        val repository = createRepository()

        assertNull(repository.getLocalPath("item-1"))
    }

    @Test
    fun `getLocalPath returns path when completed and exists`() = runTest {
        val tmpDir = System.getProperty("java.io.tmpdir")!!
        coEvery { downloadDao.getBookDownload("item-1") } returns testEntity(
            status = DownloadStatus.COMPLETED,
            localPath = tmpDir,
        )
        val repository = createRepository()

        assertEquals(tmpDir, repository.getLocalPath("item-1"))
    }

    @Test
    fun `getLocalPath returns null when path does not exist`() = runTest {
        coEvery { downloadDao.getBookDownload("item-1") } returns testEntity(
            status = DownloadStatus.COMPLETED,
            localPath = "/nonexistent/path",
        )
        val repository = createRepository()

        assertNull(repository.getLocalPath("item-1"))
    }
}
