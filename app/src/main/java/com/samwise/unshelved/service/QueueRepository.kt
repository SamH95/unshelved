package com.samwise.unshelved.service

import com.samwise.unshelved.core.database.NowPlayingDao
import com.samwise.unshelved.core.database.NowPlayingEntity
import com.samwise.unshelved.core.database.QueueDao
import com.samwise.unshelved.core.database.QueueItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepository @Inject constructor(
    private val queueDao: QueueDao,
    private val nowPlayingDao: NowPlayingDao,
) {
    val queue: Flow<List<QueueItemEntity>> = queueDao.getAll()
    val queueCount: Flow<Int> = queueDao.count()
    val queuedEpisodeIds: Flow<List<String>> = queueDao.observeEpisodeIds()
    val nowPlaying: Flow<NowPlayingEntity?> = nowPlayingDao.observe()

    suspend fun addToQueue(
        libraryItemId: String,
        episodeId: String? = null,
        mediaType: String,
        title: String,
        author: String?,
        duration: Double,
    ) {
        if (episodeId != null && queueDao.findByEpisodeId(episodeId) != null) return
        val items = queueDao.getAllOnce()
        val position = (items.maxOfOrNull { it.position } ?: -1) + 1
        queueDao.insert(
            QueueItemEntity(
                libraryItemId = libraryItemId,
                episodeId = episodeId,
                mediaType = mediaType,
                title = title,
                author = author,
                duration = duration,
                position = position,
            )
        )
    }

    suspend fun removeFromQueue(id: Long) = queueDao.deleteById(id)

    suspend fun removeByEpisodeId(episodeId: String) = queueDao.deleteByEpisodeId(episodeId)

    suspend fun isInQueue(episodeId: String): Boolean = queueDao.findByEpisodeId(episodeId) != null

    suspend fun clearQueue() = queueDao.clearAll()

    suspend fun peekNext(): QueueItemEntity? = queueDao.peekNext()

    suspend fun popNext(): QueueItemEntity? {
        val next = queueDao.peekNext() ?: return null
        queueDao.deleteById(next.id)
        return next
    }

    suspend fun reorder(items: List<QueueItemEntity>) {
        queueDao.clearAll()
        queueDao.insertAll(items.mapIndexed { index, item -> item.copy(position = index) })
    }

    suspend fun insertAtFront(
        libraryItemId: String,
        episodeId: String?,
        mediaType: String,
        title: String,
        author: String?,
        duration: Double,
    ) {
        val items = queueDao.getAllOnce()
        val shifted = items.map { it.copy(position = it.position + 1) }
        val newItem = QueueItemEntity(
            libraryItemId = libraryItemId,
            episodeId = episodeId,
            mediaType = mediaType,
            title = title,
            author = author,
            duration = duration,
            position = 0,
        )
        queueDao.clearAll()
        queueDao.insertAll(listOf(newItem) + shifted)
    }

    suspend fun setNowPlaying(
        libraryItemId: String,
        episodeId: String?,
        mediaType: String,
        title: String,
        author: String?,
        duration: Double,
    ) {
        nowPlayingDao.set(
            NowPlayingEntity(
                libraryItemId = libraryItemId,
                episodeId = episodeId,
                mediaType = mediaType,
                title = title,
                author = author,
                duration = duration,
            )
        )
    }

    suspend fun clearNowPlaying() = nowPlayingDao.clear()

    suspend fun removeByLibraryItemId(libraryItemId: String) = queueDao.deleteByLibraryItemId(libraryItemId)
}
