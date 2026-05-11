package com.samwise.unshelved.core.database

import androidx.room.*

private const val ITEM_TTL_MS = 30 * 60 * 1000L
private const val SHELF_TTL_MS = 10 * 60 * 1000L
private const val LIST_TTL_MS = 60 * 60 * 1000L  // 1 hour for libraries/series lists

@Entity(tableName = "cached_items")
data class CachedItemEntity(
    @PrimaryKey val itemId: String,
    val libraryId: String,
    val json: String,
    val cachedAt: Long,
) {
    fun isStale() = System.currentTimeMillis() - cachedAt > ITEM_TTL_MS
}

@Entity(tableName = "cached_shelves")
data class CachedShelvesEntity(
    @PrimaryKey val libraryId: String,
    val json: String,
    val cachedAt: Long,
) {
    fun isStale() = System.currentTimeMillis() - cachedAt > SHELF_TTL_MS
}

// Generic key-value JSON blob cache (libraries list, series list, library items list)
@Entity(tableName = "cached_lists")
data class CachedListEntity(
    @PrimaryKey val key: String,
    val json: String,
    val cachedAt: Long,
) {
    fun isStale() = System.currentTimeMillis() - cachedAt > LIST_TTL_MS
}

@Dao
interface CachedItemDao {
    @Query("SELECT * FROM cached_items WHERE itemId = :itemId")
    suspend fun get(itemId: String): CachedItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedItemEntity)

    @Query("DELETE FROM cached_items WHERE itemId = :itemId")
    suspend fun delete(itemId: String)

    @Query("DELETE FROM cached_items WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface CachedShelvesDao {
    @Query("SELECT * FROM cached_shelves WHERE libraryId = :libraryId")
    suspend fun get(libraryId: String): CachedShelvesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedShelvesEntity)

    @Query("DELETE FROM cached_shelves WHERE libraryId = :libraryId")
    suspend fun delete(libraryId: String)
}

@Dao
interface CachedListDao {
    @Query("SELECT * FROM cached_lists WHERE `key` = :key")
    suspend fun get(key: String): CachedListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedListEntity)

    @Query("DELETE FROM cached_lists WHERE `key` = :key")
    suspend fun delete(key: String)
}
