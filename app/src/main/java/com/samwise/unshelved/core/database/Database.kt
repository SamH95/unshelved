package com.samwise.unshelved.core.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val downloadId: String,
    val libraryItemId: String,
    val episodeId: String? = null,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val localPath: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val addedAt: Long = System.currentTimeMillis(),
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId")
    suspend fun getDownload(downloadId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE libraryItemId = :itemId AND episodeId IS NULL")
    suspend fun getBookDownload(itemId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId")
    fun observeDownload(downloadId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE libraryItemId = :itemId AND episodeId IS NULL")
    fun observeBookDownload(itemId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedList(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE downloadId = :downloadId")
    suspend fun deleteById(downloadId: String)

    @Query("UPDATE downloads SET status = :status WHERE downloadId = :downloadId")
    suspend fun updateStatus(downloadId: String, status: DownloadStatus)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE downloadId = :downloadId")
    suspend fun updateProgress(downloadId: String, bytes: Long)

    @Query("UPDATE downloads SET localPath = :path, status = 'COMPLETED' WHERE downloadId = :downloadId")
    suspend fun markCompleted(downloadId: String, path: String)

    @Query("SELECT * FROM downloads WHERE libraryItemId = :libraryItemId")
    suspend fun getByLibraryItemId(libraryItemId: String): List<DownloadEntity>
}

@Entity(tableName = "offline_progress", primaryKeys = ["libraryItemId", "episodeId"])
data class OfflineProgressEntity(
    val libraryItemId: String,
    val episodeId: String = "",
    val currentTime: Double,
    val duration: Double,
    val progress: Float,
    val isFinished: Boolean,
    val updatedAt: Long,
    val synced: Boolean = false,
)

@Dao
interface OfflineProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: OfflineProgressEntity)

    @Query("SELECT * FROM offline_progress WHERE libraryItemId = :itemId AND episodeId = ''")
    suspend fun getProgress(itemId: String): OfflineProgressEntity?

    @Query("SELECT * FROM offline_progress WHERE libraryItemId = :itemId AND episodeId = :episodeId")
    suspend fun getEpisodeProgress(itemId: String, episodeId: String): OfflineProgressEntity?

    @Query("SELECT * FROM offline_progress WHERE synced = 0")
    suspend fun getUnsynced(): List<OfflineProgressEntity>

    @Query("SELECT * FROM offline_progress")
    suspend fun getAll(): List<OfflineProgressEntity>

    @Query("UPDATE offline_progress SET synced = 1 WHERE libraryItemId = :itemId AND episodeId = :episodeId")
    suspend fun markSynced(itemId: String, episodeId: String)

    @Query("DELETE FROM offline_progress WHERE libraryItemId = :itemId AND episodeId = ''")
    suspend fun delete(itemId: String)

    @Query("DELETE FROM offline_progress WHERE libraryItemId = :libraryItemId")
    suspend fun deleteByLibraryItemId(libraryItemId: String)
}

// --- Play queue ---

@Entity(tableName = "play_queue")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val libraryItemId: String,
    val episodeId: String? = null,
    val mediaType: String,
    val title: String,
    val author: String?,
    val duration: Double,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface QueueDao {
    @Query("SELECT * FROM play_queue ORDER BY position ASC")
    fun getAll(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM play_queue ORDER BY position ASC")
    suspend fun getAllOnce(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)

    @Query("DELETE FROM play_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM play_queue")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM play_queue")
    fun count(): Flow<Int>

    @Query("SELECT * FROM play_queue ORDER BY position ASC LIMIT 1")
    suspend fun peekNext(): QueueItemEntity?

    @Query("SELECT episodeId FROM play_queue WHERE episodeId IS NOT NULL")
    fun observeEpisodeIds(): Flow<List<String>>

    @Query("SELECT * FROM play_queue WHERE episodeId = :episodeId LIMIT 1")
    suspend fun findByEpisodeId(episodeId: String): QueueItemEntity?

    @Query("DELETE FROM play_queue WHERE episodeId = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: String)

    @Query("DELETE FROM play_queue WHERE libraryItemId = :libraryItemId")
    suspend fun deleteByLibraryItemId(libraryItemId: String)
}

// --- Auto-download preferences ---

@Entity(tableName = "auto_download_podcasts")
data class AutoDownloadEntity(
    @PrimaryKey val libraryItemId: String,
    val enabledAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "auto_download_history")
data class AutoDownloadHistoryEntity(
    @PrimaryKey val episodeId: String,
    val libraryItemId: String,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Dao
interface AutoDownloadDao {
    @Query("SELECT * FROM auto_download_podcasts")
    fun getAll(): Flow<List<AutoDownloadEntity>>

    @Query("SELECT * FROM auto_download_podcasts")
    suspend fun getAllEntities(): List<AutoDownloadEntity>

    @Query("SELECT libraryItemId FROM auto_download_podcasts")
    suspend fun getAllItemIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM auto_download_podcasts WHERE libraryItemId = :itemId)")
    fun isEnabled(itemId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enable(entity: AutoDownloadEntity)

    @Query("DELETE FROM auto_download_podcasts WHERE libraryItemId = :itemId")
    suspend fun disable(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordAutoDownload(entity: AutoDownloadHistoryEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM auto_download_history WHERE episodeId = :episodeId AND libraryItemId = :libraryItemId)")
    suspend fun wasAutoDownloaded(libraryItemId: String, episodeId: String): Boolean
}

// --- Now playing ---

@Entity(tableName = "now_playing")
data class NowPlayingEntity(
    @PrimaryKey val id: Int = 0,
    val libraryItemId: String,
    val episodeId: String? = null,
    val mediaType: String,
    val title: String,
    val author: String?,
    val duration: Double,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface NowPlayingDao {
    @Query("SELECT * FROM now_playing WHERE id = 0")
    fun observe(): Flow<NowPlayingEntity?>

    @Query("SELECT * FROM now_playing WHERE id = 0")
    suspend fun get(): NowPlayingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: NowPlayingEntity)

    @Query("DELETE FROM now_playing")
    suspend fun clear()
}

@Database(
    entities = [DownloadEntity::class, OfflineProgressEntity::class, CachedItemEntity::class, CachedShelvesEntity::class, CachedListEntity::class, QueueItemEntity::class, AutoDownloadEntity::class, AutoDownloadHistoryEntity::class, NowPlayingEntity::class],
    version = 9,
    exportSchema = false,
)
abstract class UnshelvedDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun offlineProgressDao(): OfflineProgressDao
    abstract fun cachedItemDao(): CachedItemDao
    abstract fun cachedShelvesDao(): CachedShelvesDao
    abstract fun cachedListDao(): CachedListDao
    abstract fun queueDao(): QueueDao
    abstract fun autoDownloadDao(): AutoDownloadDao
    abstract fun nowPlayingDao(): NowPlayingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cached_items` (`itemId` TEXT NOT NULL, `libraryId` TEXT NOT NULL, `json` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `cached_shelves` (`libraryId` TEXT NOT NULL, `json` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`libraryId`))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cached_lists` (`key` TEXT NOT NULL, `json` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `play_queue` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `libraryItemId` TEXT NOT NULL,
                    `episodeId` TEXT,
                    `mediaType` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `author` TEXT,
                    `duration` REAL NOT NULL,
                    `position` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL
                )""")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `downloads_new` (
                    `downloadId` TEXT NOT NULL,
                    `libraryItemId` TEXT NOT NULL,
                    `episodeId` TEXT,
                    `title` TEXT NOT NULL,
                    `author` TEXT,
                    `coverPath` TEXT,
                    `localPath` TEXT,
                    `totalBytes` INTEGER NOT NULL,
                    `downloadedBytes` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`downloadId`)
                )""")
                db.execSQL("""INSERT INTO `downloads_new` (`downloadId`, `libraryItemId`, `episodeId`, `title`, `author`, `coverPath`, `localPath`, `totalBytes`, `downloadedBytes`, `status`, `addedAt`)
                    SELECT `libraryItemId`, `libraryItemId`, NULL, `title`, `author`, `coverPath`, `localPath`, `totalBytes`, `downloadedBytes`, `status`, `addedAt` FROM `downloads`""")
                db.execSQL("DROP TABLE `downloads`")
                db.execSQL("ALTER TABLE `downloads_new` RENAME TO `downloads`")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `offline_progress_new` (
                    `libraryItemId` TEXT NOT NULL,
                    `episodeId` TEXT NOT NULL DEFAULT '',
                    `currentTime` REAL NOT NULL,
                    `duration` REAL NOT NULL,
                    `progress` REAL NOT NULL,
                    `isFinished` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `synced` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`libraryItemId`, `episodeId`)
                )""")
                db.execSQL("""INSERT INTO `offline_progress_new` (`libraryItemId`, `episodeId`, `currentTime`, `duration`, `progress`, `isFinished`, `updatedAt`, `synced`)
                    SELECT `libraryItemId`, '', `currentTime`, `duration`, `progress`, `isFinished`, `updatedAt`, `synced` FROM `offline_progress`""")
                db.execSQL("DROP TABLE `offline_progress`")
                db.execSQL("ALTER TABLE `offline_progress_new` RENAME TO `offline_progress`")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `auto_download_podcasts` (
                    `libraryItemId` TEXT NOT NULL,
                    `enabledAt` INTEGER NOT NULL,
                    PRIMARY KEY(`libraryItemId`)
                )""")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `now_playing` (
                    `id` INTEGER NOT NULL,
                    `libraryItemId` TEXT NOT NULL,
                    `episodeId` TEXT,
                    `mediaType` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `author` TEXT,
                    `duration` REAL NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )""")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `auto_download_history` (
                    `episodeId` TEXT NOT NULL,
                    `libraryItemId` TEXT NOT NULL,
                    `downloadedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`episodeId`)
                )""")
            }
        }
    }
}
