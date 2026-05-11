package com.samwise.unshelved.feature.library

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samwise.unshelved.core.database.CachedItemEntity
import com.samwise.unshelved.core.database.CachedListEntity
import com.samwise.unshelved.core.database.CachedShelvesEntity
import com.samwise.unshelved.core.database.UnshelvedDatabase
import com.samwise.unshelved.core.model.*
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.LibraryDto
import com.samwise.unshelved.core.network.LibraryItemDto
import com.samwise.unshelved.core.network.PersonalizedShelfDto
import com.samwise.unshelved.core.network.SeriesDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LibraryRepo"
private const val KEY_LIBRARIES = "libraries"
private fun keySeriesList(libraryId: String) = "series_$libraryId"
private fun keyItemList(libraryId: String) = "items_$libraryId"
private fun keyFilterData(libraryId: String) = "filterdata_$libraryId"

@Singleton
class LibraryRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val db: UnshelvedDatabase,
) {
    private val gson = Gson()

    private val _libraryInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val libraryInvalidated: SharedFlow<Unit> = _libraryInvalidated.asSharedFlow()

    suspend fun getCachedItem(itemId: String): LibraryItem? {
        val entity = db.cachedItemDao().get(itemId) ?: return null
        return runCatching {
            gson.fromJson(entity.json, LibraryItemDto::class.java).toDomain()
        }.getOrNull()
    }

    suspend fun getCachedPersonalized(libraryId: String): List<PersonalizedShelfDto>? {
        val entity = db.cachedShelvesDao().get(libraryId) ?: return null
        return runCatching {
            val type = object : TypeToken<List<PersonalizedShelfDto>>() {}.type
            gson.fromJson<List<PersonalizedShelfDto>>(entity.json, type)
        }.getOrNull()
    }

    suspend fun getCachedItemEntity(itemId: String) = db.cachedItemDao().get(itemId)
    suspend fun getCachedShelvesEntity(libraryId: String) = db.cachedShelvesDao().get(libraryId)

    suspend fun getLibraries(): Result<List<Library>> = runCatching {
        val response = apiProvider.getApi().getLibraries()
        Log.d(TAG, "getLibraries: code=${response.code()} success=${response.isSuccessful}")
        if (response.isSuccessful) {
            val libs = response.body()!!.libraries.map { it.toDomain() }
            Log.d(TAG, "getLibraries: found ${libs.size} libraries: ${libs.map { "${it.id}:${it.name}" }}")
            db.cachedListDao().upsert(CachedListEntity(KEY_LIBRARIES, gson.toJson(response.body()!!.libraries), System.currentTimeMillis()))
            libs
        } else error("Failed to load libraries: ${response.code()}")
    }.recoverCatching { e ->
        Log.w(TAG, "getLibraries failed, trying cache", e)
        val cached = db.cachedListDao().get(KEY_LIBRARIES) ?: throw e
        val type = object : TypeToken<List<LibraryDto>>() {}.type
        gson.fromJson<List<LibraryDto>>(cached.json, type).map { it.toDomain() }
    }.onFailure { Log.e(TAG, "getLibraries failed with no cache", it) }

    suspend fun getLibraryItems(
        libraryId: String,
        page: Int = 0,
        limit: Int = 100,
        sort: String? = null,
        desc: Boolean = false,
        filter: String? = null,
    ): Result<Pair<List<LibraryItem>, Int>> = runCatching {
        Log.d(TAG, "getLibraryItems: libraryId=$libraryId page=$page limit=$limit sort=$sort desc=$desc filter=$filter")
        val response = apiProvider.getApi().getLibraryItems(
            libraryId = libraryId,
            page = page,
            limit = limit,
            sort = sort,
            desc = if (desc) 1 else 0,
            filter = filter,
        )
        Log.d(TAG, "getLibraryItems: code=${response.code()} success=${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()!!
            Log.d(TAG, "getLibraryItems: total=${body.total} results=${body.results.size}")
            val items = body.results.map { it.toDomain() }
            if (filter == null && page == 0) {
                db.cachedListDao().upsert(CachedListEntity(keyItemList(libraryId), gson.toJson(body.results), System.currentTimeMillis()))
            }
            Pair(items, body.total)
        } else {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "getLibraryItems failed: ${response.code()} $errorBody")
            error("Failed to load items: ${response.code()}")
        }
    }.recoverCatching { e ->
        if (filter != null) throw e
        Log.w(TAG, "getLibraryItems failed, trying cache", e)
        val cached = db.cachedListDao().get(keyItemList(libraryId)) ?: throw e
        val type = object : TypeToken<List<LibraryItemDto>>() {}.type
        val dtos = gson.fromJson<List<LibraryItemDto>>(cached.json, type)
        Pair(dtos.map { it.toDomain() }, dtos.size)
    }.onFailure { Log.e(TAG, "getLibraryItems exception", it) }

    suspend fun getLibrarySeries(libraryId: String, page: Int = 0): Result<List<Series>> =
        runCatching {
            Log.d(TAG, "getLibrarySeries: libraryId=$libraryId")
            val response = apiProvider.getApi().getLibrarySeries(libraryId, page = page)
            Log.d(TAG, "getLibrarySeries: code=${response.code()} success=${response.isSuccessful}")
            if (response.isSuccessful) {
                val body = response.body()!!
                Log.d(TAG, "getLibrarySeries: total=${body.total} results=${body.results.size}")
                val series = body.results.map { it.toDomain() }
                if (page == 0) {
                    db.cachedListDao().upsert(CachedListEntity(keySeriesList(libraryId), gson.toJson(body.results), System.currentTimeMillis()))
                }
                series
            } else error("Failed to load series: ${response.code()}")
        }.recoverCatching { e ->
            Log.w(TAG, "getLibrarySeries failed, trying cache", e)
            val cached = db.cachedListDao().get(keySeriesList(libraryId)) ?: throw e
            val type = object : TypeToken<List<SeriesDto>>() {}.type
            gson.fromJson<List<SeriesDto>>(cached.json, type).map { it.toDomain() }
        }.onFailure { Log.e(TAG, "getLibrarySeries failed", it) }

    suspend fun getFilterData(libraryId: String): Result<com.samwise.unshelved.core.network.LibraryFilterDataDto> =
        runCatching {
            val response = apiProvider.getApi().getLibraryFilterData(libraryId)
            if (response.isSuccessful) {
                val data = response.body()!!
                db.cachedListDao().upsert(CachedListEntity(keyFilterData(libraryId), gson.toJson(data), System.currentTimeMillis()))
                data
            } else error("Failed to load filter data: ${response.code()}")
        }.recoverCatching { e ->
            Log.w(TAG, "getFilterData failed, trying cache", e)
            val cached = db.cachedListDao().get(keyFilterData(libraryId)) ?: throw e
            gson.fromJson(cached.json, com.samwise.unshelved.core.network.LibraryFilterDataDto::class.java)
        }.onFailure { Log.e(TAG, "getFilterData failed with no cache", it) }

    suspend fun getPersonalized(libraryId: String): Result<List<PersonalizedShelfDto>> =
        runCatching {
            Log.d(TAG, "getPersonalized: libraryId=$libraryId")
            val response = apiProvider.getApi().getPersonalized(libraryId)
            Log.d(TAG, "getPersonalized: code=${response.code()} success=${response.isSuccessful}")
            if (response.isSuccessful) {
                val shelves = response.body() ?: emptyList()
                Log.d(TAG, "getPersonalized: ${shelves.size} shelves: ${shelves.map { "${it.id}(${it.entities?.size ?: 0})" }}")
                db.cachedShelvesDao().upsert(CachedShelvesEntity(libraryId, gson.toJson(shelves), System.currentTimeMillis()))
                shelves
            } else error("Failed to load personalized: ${response.code()}")
        }.onFailure { Log.e(TAG, "getPersonalized failed", it) }

    suspend fun getItem(itemId: String): Result<LibraryItem> = runCatching {
        val response = apiProvider.getApi().getItem(itemId)
        if (response.isSuccessful) {
            val dto = response.body()!!
            val libraryId = dto.libraryId ?: ""
            db.cachedItemDao().upsert(CachedItemEntity(itemId, libraryId, gson.toJson(dto), System.currentTimeMillis()))
            dto.toDomain()
        } else error("Failed to load item: ${response.code()}")
    }

    suspend fun getSeriesDetail(seriesId: String): Result<Series> = runCatching {
        Log.d(TAG, "getSeriesDetail: seriesId=$seriesId")
        val response = apiProvider.getApi().getSeries(seriesId)
        Log.d(TAG, "getSeriesDetail: code=${response.code()} success=${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()!!
            Log.d(TAG, "getSeriesDetail: name=${body.name} books=${body.books?.size}")
            body.toDomain()
        } else error("Failed to load series: ${response.code()}")
    }.onFailure { Log.e(TAG, "getSeriesDetail failed", it) }

    suspend fun getItemsInProgress(): Result<List<LibraryItem>> = runCatching {
        val response = apiProvider.getApi().getItemsInProgress()
        Log.d(TAG, "getItemsInProgress: code=${response.code()} success=${response.isSuccessful}")
        if (response.isSuccessful) {
            val items = response.body()!!.libraryItems.map { it.toDomain() }
            Log.d(TAG, "getItemsInProgress: found ${items.size} items")
            items
        } else error("Failed: ${response.code()}")
    }.onFailure { Log.e(TAG, "getItemsInProgress failed", it) }

    suspend fun search(libraryId: String, query: String): Result<List<LibraryItem>> = runCatching {
        val response = apiProvider.getApi().search(libraryId, query)
        if (!response.isSuccessful) error("Search failed: ${response.code()}")
        val body = response.body()!!
        val bookResults = body.book?.map { it.libraryItem.toDomain() } ?: emptyList()
        val podcastResults = body.podcast?.map { it.libraryItem.toDomain() } ?: emptyList()
        val episodeResults = body.episodes?.map { it.libraryItem.toDomain() } ?: emptyList()
        val directResults = bookResults + podcastResults + episodeResults
        val matchedAuthors = body.authors ?: emptyList()
        if (matchedAuthors.isEmpty()) return@runCatching directResults

        val ids = directResults.map { it.id }.toMutableSet()
        val authorBooks = matchedAuthors.flatMap { author ->
            val filter = "authors." + android.util.Base64.encodeToString(author.id.toByteArray(), android.util.Base64.NO_WRAP)
            val itemsResponse = apiProvider.getApi().getLibraryItems(libraryId, limit = 30, filter = filter)
            if (itemsResponse.isSuccessful) {
                itemsResponse.body()!!.results.map { it.toDomain() }.filter { ids.add(it.id) }
            } else emptyList()
        }
        directResults + authorBooks
    }

    suspend fun getProgress(itemId: String): Result<MediaProgress> = runCatching {
        val response = apiProvider.getApi().getProgress(itemId)
        if (response.isSuccessful) response.body()!!.toDomain()
        else error("No progress found")
    }

    suspend fun getRecentEpisodes(libraryId: String, page: Int = 0, limit: Int = 25): Result<List<PodcastEpisode>> =
        runCatching {
            val response = apiProvider.getApi().getRecentEpisodes(libraryId, limit = limit, page = page)
            if (response.isSuccessful) {
                response.body()!!.episodes.map { it.toDomain() }
            } else error("Failed to load recent episodes: ${response.code()}")
        }.onFailure { Log.e(TAG, "getRecentEpisodes failed", it) }

    suspend fun searchItunesPodcasts(term: String): Result<List<ItunesSearchResult>> =
        runCatching {
            val response = apiProvider.getApi().searchPodcasts(term)
            if (response.isSuccessful) {
                (response.body() ?: emptyList()).map { it.toDomain() }
            } else error("Podcast search failed: ${response.code()}")
        }.onFailure { Log.e(TAG, "searchItunesPodcasts failed", it) }

    suspend fun getPodcastFeed(feedUrl: String): Result<PodcastFeedPreview> =
        runCatching {
            val response = apiProvider.getApi().getPodcastFeed(com.samwise.unshelved.core.network.PodcastFeedRequest(feedUrl))
            if (response.isSuccessful) {
                response.body()!!.toDomain()
            } else error("Failed to load feed: ${response.code()}")
        }.onFailure { Log.e(TAG, "getPodcastFeed failed", it) }

    suspend fun createPodcast(
        libraryId: String,
        folderId: String,
        path: String,
        metadata: com.samwise.unshelved.core.network.PodcastMetadataDto,
        autoDownload: Boolean,
        episodesToDownload: List<com.samwise.unshelved.core.network.PodcastFeedEpisodeDto>? = null,
    ): Result<LibraryItem> = runCatching {
        val request = com.samwise.unshelved.core.network.CreatePodcastRequest(
            libraryId = libraryId,
            folderId = folderId,
            path = path,
            media = com.samwise.unshelved.core.network.CreatePodcastMediaDto(
                metadata = metadata,
                autoDownloadEpisodes = autoDownload,
            ),
            episodesToDownload = episodesToDownload,
        )
        val response = apiProvider.getApi().createPodcast(request)
        if (response.isSuccessful) {
            response.body()!!.toDomain()
        } else error("Failed to create podcast: ${response.code()}")
    }.onFailure { Log.e(TAG, "createPodcast failed", it) }

    suspend fun updateProgress(itemId: String, request: com.samwise.unshelved.core.network.UpdateProgressRequest): Result<Unit> = runCatching {
        val response = apiProvider.getApi().updateProgress(itemId, request)
        if (!response.isSuccessful) error("Failed to update progress: ${response.code()}")
    }

    suspend fun updateEpisodeProgress(itemId: String, episodeId: String, request: com.samwise.unshelved.core.network.UpdateProgressRequest): Result<Unit> = runCatching {
        val response = apiProvider.getApi().updateEpisodeProgress(itemId, episodeId, request)
        if (!response.isSuccessful) error("Failed to update episode progress: ${response.code()}")
    }

    suspend fun quickMatch(itemId: String): Result<Unit> = runCatching {
        val response = apiProvider.getApi().quickMatch(itemId)
        if (!response.isSuccessful) error("Quick match failed: ${response.code()}")
    }.onFailure { Log.e(TAG, "quickMatch failed", it) }

    suspend fun downloadPodcastEpisodes(itemId: String, episodes: List<com.samwise.unshelved.core.network.PodcastFeedEpisodeDto>): Result<Unit> = runCatching {
        val response = apiProvider.getApi().downloadPodcastEpisodes(itemId, episodes)
        if (!response.isSuccessful) error("Download episodes failed: ${response.code()}")
    }.onFailure { Log.e(TAG, "downloadPodcastEpisodes failed", it) }

    suspend fun deleteLibraryItem(itemId: String): Result<Unit> = runCatching {
        runCatching {
            apiProvider.getApi().updatePodcastMedia(itemId, com.samwise.unshelved.core.network.UpdatePodcastMediaRequest(autoDownloadEpisodes = false))
        }
        val response = apiProvider.getApi().deleteLibraryItem(itemId)
        if (!response.isSuccessful) error("Delete failed: ${response.code()}")
        invalidateCacheForItem(itemId)
    }.onSuccess {
        _libraryInvalidated.tryEmit(Unit)
    }.onFailure { Log.e(TAG, "deleteLibraryItem failed", it) }

    private suspend fun invalidateCacheForItem(itemId: String) {
        val cached = db.cachedItemDao().get(itemId)
        db.cachedItemDao().delete(itemId)
        if (cached != null && cached.libraryId.isNotEmpty()) {
            db.cachedListDao().delete(keyItemList(cached.libraryId))
            db.cachedShelvesDao().delete(cached.libraryId)
        }
    }

    suspend fun getPodcastFeedRaw(feedUrl: String): Result<com.samwise.unshelved.core.network.PodcastFeedDto> =
        runCatching {
            val response = apiProvider.getApi().getPodcastFeed(com.samwise.unshelved.core.network.PodcastFeedRequest(feedUrl))
            if (response.isSuccessful) {
                response.body()!!.podcast
            } else error("Failed to load feed: ${response.code()}")
        }.onFailure { Log.e(TAG, "getPodcastFeedRaw failed", it) }
}
