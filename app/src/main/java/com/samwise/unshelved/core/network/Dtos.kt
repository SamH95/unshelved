package com.samwise.unshelved.core.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// --- Auth ---

data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val user: UserDto,
    val userDefaultLibraryId: String?,
)

data class AuthorizeResponse(
    val user: UserDto,
    val userDefaultLibraryId: String?,
)

// --- User ---

data class UserDto(
    val id: String,
    val username: String,
    val email: String?,
    val type: String? = null,
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val mediaProgress: List<MediaProgressDto>? = null,
) {
    val effectiveToken: String get() = accessToken ?: token ?: ""
}

data class MediaProgressDto(
    val id: String,
    val libraryItemId: String,
    val episodeId: String?,
    val duration: Double,
    val progress: Float,
    val currentTime: Double,
    val isFinished: Boolean,
    val hideFromContinueListening: Boolean,
    val lastUpdate: Long,
    val startedAt: Long?,
    val finishedAt: Long?,
)

// --- Libraries ---

data class LibrariesResponse(
    val libraries: List<LibraryDto>,
)

data class LibraryDto(
    val id: String,
    val name: String,
    val mediaType: String,
    val icon: String = "database",
    val folders: List<LibraryFolderDto> = emptyList(),
)

data class LibraryFolderDto(
    val id: String,
    val fullPath: String,
)

data class LibraryItemsResponse(
    val results: List<LibraryItemDto>,
    val total: Int,
    val limit: Int,
    val page: Int,
    val numPages: Int,
    val sortBy: String?,
    val sortDesc: Boolean,
    val filterBy: String?,
)

data class LibraryItemDto(
    val id: String,
    val ino: String? = null,
    val libraryId: String? = null,
    val media: JsonElement,
    val mediaType: String? = null,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val numFiles: Int = 0,
    val size: Long = 0,
    val progressLastUpdate: Long = 0,
    val recentEpisode: PodcastEpisodeDto? = null,
)

data class BookMediaDto(
    val id: String? = null,
    val metadata: BookMetadataDto,
    val coverPath: String?,
    val duration: Double = 0.0,
    val numTracks: Int = 0,
    val numAudioFiles: Int = 0,
    val numChapters: Int = 0,
    val audioFiles: List<AudioFileDto>? = null,
    val chapters: List<ChapterDto>? = null,
)

data class BookMetadataDto(
    val title: String?,
    val titleIgnorePrefix: String?,
    val subtitle: String?,
    val authorName: String?,
    val narratorName: String?,
    val seriesName: String?,
    val genres: List<String>? = null,
    val publishedYear: String?,
    val description: String?,
    val language: String?,
    val explicit: Boolean = false,
    val authors: List<AuthorDto>? = null,
    val series: com.google.gson.JsonElement? = null,
)

data class SeriesBookDto(
    val id: String,
    val name: String,
    val sequence: String?,
)

data class AudioFileDto(
    val index: Int,
    val ino: String,
    val metadata: AudioFileMetadataDto,
    val duration: Double,
    val bitRate: Int?,
    val codec: String?,
    val mimeType: String,
)

data class AudioFileMetadataDto(
    val filename: String,
    val ext: String,
    val path: String,
    val relPath: String,
    val size: Long,
)

data class ChapterDto(
    val id: Int,
    val start: Double,
    val end: Double,
    val title: String,
)

// --- Series ---

data class LibrarySeriesResponse(
    val results: List<SeriesDto>,
    val total: Int,
    val limit: Int,
    val page: Int,
)

data class SeriesDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val totalDuration: Double = 0.0,
    val books: List<LibraryItemDto>? = null,
)

data class SeriesDetailDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val addedAt: Long = 0,
    val updatedAt: Long = 0,
    val books: List<LibraryItemDto>? = null,
    val progress: SeriesProgressDto? = null,
)

data class SeriesProgressDto(
    val libraryItemIds: List<String>? = null,
    val libraryItemIdsFinished: List<String>? = null,
    val isFinished: Boolean = false,
)

// --- Progress ---

data class UpdateProgressRequest(
    val duration: Double,
    val progress: Float,
    val currentTime: Double,
    val isFinished: Boolean,
    val startedAt: Long?,
    val finishedAt: Long?,
)

// --- Playback Session ---

data class PlaybackSessionDto(
    val id: String,
    val libraryItemId: String,
    val episodeId: String?,
    val mediaType: String,
    val mediaMetadata: BookMetadataDto,
    val chapters: List<ChapterDto>? = null,
    val displayTitle: String,
    val displayAuthor: String,
    val duration: Double,
    val playMethod: Int,
    val currentTime: Double,
    val audioTracks: List<AudioTrackDto>? = null,
)

data class AudioTrackDto(
    val index: Int,
    val startOffset: Double,
    val duration: Double,
    val title: String,
    val contentUrl: String,
    val mimeType: String,
)

data class SyncSessionRequest(
    val currentTime: Double,
    val timeListened: Double,
    val duration: Double,
)

data class PlayItemRequest(
    val deviceInfo: DeviceInfoDto = DeviceInfoDto(),
    val mediaPlayer: String = "exoplayer",
    val forceDirectPlay: Boolean = true,
    val forceTranscode: Boolean = false,
)

data class DeviceInfoDto(
    val clientName: String = "Unshelved",
    val clientVersion: String = "1.0",
    val manufacturer: String = android.os.Build.MANUFACTURER,
    val model: String = android.os.Build.MODEL,
    val sdkVersion: Int = android.os.Build.VERSION.SDK_INT,
)

// --- Items in progress ---

data class ItemsInProgressResponse(
    val libraryItems: List<LibraryItemDto>,
)

// --- Search ---

data class SearchResponse(
    val book: List<SearchBookResult>? = null,
    val podcast: List<SearchPodcastResult>? = null,
    val episodes: List<SearchPodcastResult>? = null,
    val series: List<SeriesDto>? = null,
    val authors: List<AuthorDto>? = null,
    val tags: List<String>? = null,
)

data class SearchBookResult(
    val libraryItem: LibraryItemDto,
)

data class SearchPodcastResult(
    val libraryItem: LibraryItemDto,
)

data class AuthorDto(
    val id: String,
    val name: String,
    val description: String?,
    val imagePath: String?,
)

// --- Personalized shelves ---

data class PersonalizedShelfDto(
    val id: String,
    val label: String,
    val labelStringKey: String? = null,
    val type: String,
    val entities: List<LibraryItemDto>? = null,
    val category: String? = null,
)

// --- Filter data ---

data class LibraryFilterDataDto(
    val authors: List<FilterItemDto> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val series: List<FilterItemDto> = emptyList(),
    val narrators: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

data class FilterItemDto(
    val id: String,
    val name: String,
)

// --- Podcast media ---

data class PodcastMediaDto(
    val id: String? = null,
    val metadata: PodcastMetadataDto,
    val coverPath: String?,
    val tags: List<String> = emptyList(),
    val numEpisodes: Int = 0,
    val autoDownloadEpisodes: Boolean = false,
    val autoDownloadSchedule: String? = null,
    val lastEpisodeCheck: Long? = null,
    val maxEpisodesToKeep: Int = 0,
    val maxNewEpisodesToDownload: Int = 0,
    val size: Long = 0,
    val episodes: List<PodcastEpisodeDto>? = null,
)

data class PodcastMetadataDto(
    val title: String?,
    val titleIgnorePrefix: String?,
    val author: String?,
    val description: String?,
    val releaseDate: String?,
    val genres: List<String>? = null,
    val categories: List<String>? = null,
    val feedUrl: String?,
    val imageUrl: String? = null,
    val image: String? = null,
    val itunesPageUrl: String? = null,
    val itunesId: Any? = null,
    val itunesArtistId: Any? = null,
    val explicit: Boolean = false,
    val language: String?,
    val type: String?,
) {
    val resolvedImageUrl: String? get() = imageUrl ?: image
    val resolvedGenres: List<String> get() = genres ?: categories ?: emptyList()
}

data class PodcastEpisodeDto(
    val id: String,
    val libraryItemId: String? = null,
    val podcastId: String? = null,
    val index: Int? = null,
    val season: String? = null,
    val episode: String? = null,
    val episodeType: String? = null,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val pubDate: String? = null,
    val publishedAt: Long? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val audioFile: AudioFileDto? = null,
    val chapters: List<ChapterDto>? = null,
    val audioTrack: AudioTrackDto? = null,
    val duration: Double = 0.0,
    val size: Long = 0,
)

// --- Recent episodes ---

data class RecentEpisodesResponse(
    val episodes: List<RecentEpisodeDto>,
    val total: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
)

data class RecentEpisodeDto(
    val id: String,
    val libraryItemId: String,
    val podcastId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val season: String? = null,
    val episode: String? = null,
    val episodeType: String? = null,
    val pubDate: String? = null,
    val publishedAt: Long? = null,
    val addedAt: Long? = null,
    val audioFile: AudioFileDto? = null,
    val chapters: List<ChapterDto>? = null,
    val audioTrack: AudioTrackDto? = null,
    val duration: Double = 0.0,
    val size: Long = 0,
    val podcast: PodcastParentDto? = null,
    val libraryId: String? = null,
)

data class PodcastParentDto(
    val metadata: PodcastMetadataDto,
    val coverPath: String?,
)

// --- iTunes search ---

data class ItunesSearchResultDto(
    val id: Long,
    val artistId: Long? = null,
    val title: String,
    val artistName: String?,
    val description: String?,
    val genres: List<String>? = null,
    val cover: String?,
    val trackCount: Int = 0,
    val feedUrl: String?,
    val pageUrl: String?,
    val explicit: Boolean = false,
)

// --- Podcast feed ---

data class PodcastFeedResponse(val podcast: PodcastFeedDto)

data class PodcastFeedDto(
    val metadata: PodcastMetadataDto,
    val episodes: List<PodcastFeedEpisodeDto> = emptyList(),
)

data class PodcastFeedEpisodeDto(
    val title: String?,
    val subtitle: String?,
    val description: String?,
    val pubDate: String?,
    val season: String?,
    val episode: String?,
    val episodeType: String?,
    val enclosure: PodcastFeedEnclosureDto? = null,
    val publishedAt: Long? = null,
)

data class PodcastFeedEnclosureDto(
    val url: String?,
    val type: String?,
    val length: String?,
)

data class PodcastFeedRequest(val rssFeed: String)

// --- Create podcast ---

data class CreatePodcastRequest(
    val libraryId: String,
    val folderId: String,
    val path: String,
    val media: CreatePodcastMediaDto,
    val episodesToDownload: List<PodcastFeedEpisodeDto>? = null,
)

data class CreatePodcastMediaDto(
    val metadata: PodcastMetadataDto,
    val autoDownloadEpisodes: Boolean = false,
)

// --- Update podcast media ---

data class UpdatePodcastMediaRequest(val autoDownloadEpisodes: Boolean)

// --- Quick match ---

data class QuickMatchRequest(
    val provider: String? = null,
)
