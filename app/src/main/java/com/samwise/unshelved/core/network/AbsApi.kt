package com.samwise.unshelved.core.network

import retrofit2.Response
import retrofit2.http.*

interface AbsApi {

    // --- Auth ---
    @POST("login")
    suspend fun login(@Body request: LoginRequest, @retrofit2.http.Header("x-return-tokens") returnTokens: Boolean = true): Response<LoginResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@retrofit2.http.Header("x-refresh-token") refreshToken: String): Response<LoginResponse>

    @POST("api/authorize")
    suspend fun authorize(): Response<AuthorizeResponse>

    @GET("api/me")
    suspend fun getMe(): Response<UserDto>

    @POST("logout")
    suspend fun logout(): Response<Unit>

    @POST("logout")
    suspend fun logoutWithToken(@retrofit2.http.Header("x-refresh-token") refreshToken: String): Response<Unit>

    // --- Libraries ---
    @GET("api/libraries")
    suspend fun getLibraries(): Response<LibrariesResponse>

    @GET("api/libraries/{libraryId}/items")
    suspend fun getLibraryItems(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
        @Query("sort") sort: String? = null,
        @Query("desc") desc: Int = 0,
        @Query("filter") filter: String? = null,
        @Query("minified") minified: Int = 1,
        @Query("include") include: String = "rssfeed,numEpisodesIncomplete",
    ): Response<LibraryItemsResponse>

    @GET("api/libraries/{libraryId}/series")
    suspend fun getLibrarySeries(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
        @Query("include") include: String = "progress",
    ): Response<LibrarySeriesResponse>

    @GET("api/libraries/{libraryId}/filterdata")
    suspend fun getLibraryFilterData(
        @Path("libraryId") libraryId: String,
    ): Response<LibraryFilterDataDto>

    @GET("api/libraries/{libraryId}/personalized")
    suspend fun getPersonalized(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 10,
    ): Response<List<PersonalizedShelfDto>>

    // --- Items ---
    @GET("api/items/{itemId}")
    suspend fun getItem(
        @Path("itemId") itemId: String,
        @Query("expanded") expanded: Int = 1,
        @Query("include") include: String = "progress,authors",
    ): Response<LibraryItemDto>

    // --- Series ---
    @GET("api/series/{seriesId}")
    suspend fun getSeries(
        @Path("seriesId") seriesId: String,
        @Query("include") include: String = "progress,books",
    ): Response<SeriesDetailDto>

    // --- Search ---
    @GET("api/libraries/{libraryId}/search")
    suspend fun search(
        @Path("libraryId") libraryId: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
    ): Response<SearchResponse>

    // --- Progress ---
    @PATCH("api/me/progress/{libraryItemId}")
    suspend fun updateProgress(
        @Path("libraryItemId") libraryItemId: String,
        @Body request: UpdateProgressRequest,
    ): Response<Unit>

    @PATCH("api/me/progress/{libraryItemId}/{episodeId}")
    suspend fun updateEpisodeProgress(
        @Path("libraryItemId") libraryItemId: String,
        @Path("episodeId") episodeId: String,
        @Body request: UpdateProgressRequest,
    ): Response<Unit>

    @GET("api/me/items-in-progress")
    suspend fun getItemsInProgress(
        @Query("limit") limit: Int = 20,
    ): Response<ItemsInProgressResponse>

    @GET("api/me/progress/{libraryItemId}")
    suspend fun getProgress(
        @Path("libraryItemId") libraryItemId: String,
    ): Response<MediaProgressDto>

    @GET("api/me/progress/{libraryItemId}/{episodeId}")
    suspend fun getEpisodeProgress(
        @Path("libraryItemId") libraryItemId: String,
        @Path("episodeId") episodeId: String,
    ): Response<MediaProgressDto>

    // --- Playback sessions ---
    @POST("api/items/{itemId}/play")
    suspend fun startPlaybackSession(
        @Path("itemId") itemId: String,
        @Body request: PlayItemRequest,
    ): Response<PlaybackSessionDto>

    @POST("api/items/{itemId}/play/{episodeId}")
    suspend fun startEpisodePlaybackSession(
        @Path("itemId") itemId: String,
        @Path("episodeId") episodeId: String,
        @Body request: PlayItemRequest,
    ): Response<PlaybackSessionDto>

    @POST("api/session/{sessionId}/sync")
    suspend fun syncSession(
        @Path("sessionId") sessionId: String,
        @Body request: SyncSessionRequest,
    ): Response<Unit>

    @POST("api/session/{sessionId}/close")
    suspend fun closeSession(
        @Path("sessionId") sessionId: String,
        @Body request: SyncSessionRequest,
    ): Response<Unit>

    @POST("api/session/local")
    suspend fun syncLocalSession(
        @Body request: LocalSessionRequest,
    ): Response<Unit>

    // --- Podcast ---
    @GET("api/libraries/{libraryId}/recent-episodes")
    suspend fun getRecentEpisodes(
        @Path("libraryId") libraryId: String,
        @Query("limit") limit: Int = 25,
        @Query("page") page: Int = 0,
    ): Response<RecentEpisodesResponse>

    @GET("api/search/podcast")
    suspend fun searchPodcasts(
        @Query("term") term: String,
    ): Response<List<ItunesSearchResultDto>>

    @POST("api/podcasts/feed")
    suspend fun getPodcastFeed(
        @Body request: PodcastFeedRequest,
    ): Response<PodcastFeedResponse>

    @POST("api/podcasts")
    suspend fun createPodcast(
        @Body request: CreatePodcastRequest,
    ): Response<LibraryItemDto>

    @PATCH("api/items/{itemId}/media")
    suspend fun updatePodcastMedia(
        @Path("itemId") itemId: String,
        @Body request: UpdatePodcastMediaRequest,
    ): Response<Unit>

    @POST("api/items/{itemId}/match")
    suspend fun quickMatch(
        @Path("itemId") itemId: String,
        @Body request: QuickMatchRequest = QuickMatchRequest(),
    ): Response<Unit>

    @POST("api/podcasts/{itemId}/download-episodes")
    suspend fun downloadPodcastEpisodes(
        @Path("itemId") itemId: String,
        @Body episodes: List<PodcastFeedEpisodeDto>,
    ): Response<Unit>

    @DELETE("api/items/{itemId}")
    suspend fun deleteLibraryItem(
        @Path("itemId") itemId: String,
    ): Response<Unit>
}

data class LocalSessionRequest(
    val id: String,
    val libraryItemId: String,
    val mediaType: String,
    val currentTime: Double,
    val timeListened: Double,
    val duration: Double,
    val startedAt: Long,
    val updatedAt: Long,
)
