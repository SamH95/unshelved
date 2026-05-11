package com.samwise.unshelved.core.model

import com.samwise.unshelved.core.database.OfflineProgressDao
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.MediaProgressDto
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressCacheTest {

    private fun createCache(): ProgressCache {
        val apiProvider = mockk<ApiProvider>()
        val offlineProgressDao = mockk<OfflineProgressDao> {
            coEvery { getAll() } returns emptyList()
        }
        val prefs = mockk<UserPreferencesRepository>()
        return ProgressCache(apiProvider, offlineProgressDao, prefs)
    }

    private fun dto(
        libraryItemId: String,
        currentTime: Double = 100.0,
        duration: Double = 1000.0,
        progress: Float = 0.1f,
        lastUpdate: Long = 1000L,
        isFinished: Boolean = false,
    ) = MediaProgressDto(
        id = libraryItemId,
        libraryItemId = libraryItemId,
        episodeId = null,
        duration = duration,
        progress = progress,
        currentTime = currentTime,
        isFinished = isFinished,
        hideFromContinueListening = false,
        lastUpdate = lastUpdate,
        startedAt = null,
        finishedAt = null,
    )

    private fun progress(
        libraryItemId: String,
        currentTime: Double = 100.0,
        duration: Double = 1000.0,
        progress: Float = 0.1f,
        lastUpdate: Long = 1000L,
        isFinished: Boolean = false,
    ) = MediaProgress(
        id = libraryItemId,
        libraryItemId = libraryItemId,
        episodeId = null,
        duration = duration,
        progress = progress,
        currentTime = currentTime,
        isFinished = isFinished,
        hideFromContinueListening = false,
        lastUpdate = lastUpdate,
        startedAt = null,
        finishedAt = null,
    )

    @Test
    fun `update with empty local map uses server data`() {
        val cache = createCache()

        cache.update(listOf(dto("item-1", currentTime = 200.0, lastUpdate = 5000)))

        val map = cache.progressMap.value
        assertEquals(1, map.size)
        assertEquals(200.0, map["item-1"]!!.currentTime, 0.01)
    }

    @Test
    fun `update preserves newer local entry over stale server data`() {
        val cache = createCache()
        cache.updateItem("item-1", null, progress("item-1", currentTime = 500.0, lastUpdate = 9000))

        cache.update(listOf(dto("item-1", currentTime = 200.0, lastUpdate = 5000)))

        val map = cache.progressMap.value
        assertEquals(500.0, map["item-1"]!!.currentTime, 0.01)
        assertEquals(9000L, map["item-1"]!!.lastUpdate)
    }

    @Test
    fun `update replaces older local entry with newer server data`() {
        val cache = createCache()
        cache.updateItem("item-1", null, progress("item-1", currentTime = 200.0, lastUpdate = 3000))

        cache.update(listOf(dto("item-1", currentTime = 500.0, lastUpdate = 9000)))

        val map = cache.progressMap.value
        assertEquals(500.0, map["item-1"]!!.currentTime, 0.01)
        assertEquals(9000L, map["item-1"]!!.lastUpdate)
    }

    @Test
    fun `update with null list preserves local entries`() {
        val cache = createCache()
        cache.updateItem("item-1", null, progress("item-1", currentTime = 300.0, lastUpdate = 5000))

        cache.update(null)

        val map = cache.progressMap.value
        assertEquals(1, map.size)
        assertEquals(300.0, map["item-1"]!!.currentTime, 0.01)
    }

    @Test
    fun `update merges server and local entries for different items`() {
        val cache = createCache()
        cache.updateItem("item-1", null, progress("item-1", currentTime = 300.0, lastUpdate = 9000))

        cache.update(listOf(dto("item-2", currentTime = 100.0, lastUpdate = 5000)))

        val map = cache.progressMap.value
        assertEquals(2, map.size)
        assertEquals(300.0, map["item-1"]!!.currentTime, 0.01)
        assertEquals(100.0, map["item-2"]!!.currentTime, 0.01)
    }

    @Test
    fun `updateItem adds entry to existing map`() {
        val cache = createCache()
        cache.updateItem("item-1", null, progress("item-1", currentTime = 100.0))
        cache.updateItem("item-2", null, progress("item-2", currentTime = 200.0))

        val map = cache.progressMap.value
        assertEquals(2, map.size)
        assertEquals(100.0, map["item-1"]!!.currentTime, 0.01)
        assertEquals(200.0, map["item-2"]!!.currentTime, 0.01)
    }

    @Test
    fun `updateItem overwrites previous entry for same item`() {
        val cache = createCache()
        cache.updateItem("item-1", null, progress("item-1", currentTime = 100.0))
        cache.updateItem("item-1", null, progress("item-1", currentTime = 500.0))

        val map = cache.progressMap.value
        assertEquals(1, map.size)
        assertEquals(500.0, map["item-1"]!!.currentTime, 0.01)
    }

    @Test
    fun `updateItem then update with stale server data preserves local update`() {
        val cache = createCache()

        cache.updateItem("item-1", null, progress("item-1", currentTime = 800.0, lastUpdate = 10000))

        cache.update(listOf(
            dto("item-1", currentTime = 200.0, lastUpdate = 5000),
            dto("item-2", currentTime = 50.0, lastUpdate = 5000),
        ))

        val map = cache.progressMap.value
        assertEquals(2, map.size)
        assertEquals(800.0, map["item-1"]!!.currentTime, 0.01)
        assertEquals(50.0, map["item-2"]!!.currentTime, 0.01)
    }
}
