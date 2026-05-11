package com.samwise.unshelved.core.model

data class Library(
    val id: String,
    val name: String,
    val mediaType: String, // "book" | "podcast"
    val icon: String,
    val folders: List<LibraryFolder> = emptyList(),
) {
    val isPodcast: Boolean get() = mediaType == "podcast"
}

data class LibraryFolder(val id: String, val fullPath: String)

data class LibraryItem(
    val id: String,
    val libraryId: String,
    val mediaType: String = "book",
    val media: BookMedia,
    val podcastMedia: PodcastMedia? = null,
    val addedAt: Long,
    val updatedAt: Long,
    val progressLastUpdate: Long = 0,
    val recentEpisode: PodcastEpisode? = null,
) {
    val isPodcast: Boolean get() = mediaType == "podcast"
    val title: String get() = if (isPodcast) podcastMedia?.metadata?.title ?: "" else media.metadata.title
    val authorName: String? get() = if (isPodcast) podcastMedia?.metadata?.author else media.metadata.authorName
}

data class BookMedia(
    val id: String?,
    val metadata: BookMetadata,
    val coverPath: String?,
    val duration: Double,
    val audioFiles: List<AudioFile> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
)

data class BookMetadata(
    val title: String,
    val titleIgnorePrefix: String?,
    val subtitle: String?,
    val authorName: String?,
    val narratorName: String?,
    val seriesName: String?,
    val genres: List<String> = emptyList(),
    val publishedYear: String?,
    val description: String?,
    val language: String?,
    val explicit: Boolean = false,
    val authors: List<Author> = emptyList(),
    val seriesEntries: List<SeriesEntry> = emptyList(),
)

data class Author(val id: String, val name: String)

data class SeriesEntry(val id: String, val name: String, val sequence: String?)

data class AudioFile(
    val index: Int,
    val ino: String,
    val metadata: AudioFileMetadata,
    val duration: Double,
    val bitRate: Int?,
    val codec: String?,
    val mimeType: String,
)

data class AudioFileMetadata(
    val filename: String,
    val ext: String,
    val path: String,
    val relPath: String,
    val size: Long,
)

data class Chapter(
    val id: Int,
    val start: Double,
    val end: Double,
    val title: String,
)

data class Series(
    val id: String,
    val name: String,
    val description: String?,
    val addedAt: Long,
    val updatedAt: Long,
    val books: List<LibraryItem> = emptyList(),
    val progress: SeriesProgress? = null,
)

data class SeriesProgress(
    val libraryItemIds: List<String>,
    val libraryItemIdsFinished: List<String>,
    val isFinished: Boolean,
)

data class MediaProgress(
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

data class User(
    val id: String,
    val username: String,
    val email: String?,
    val type: String,
    val token: String,
    val mediaProgress: List<MediaProgress> = emptyList(),
)

data class PlaybackSession(
    val id: String,
    val libraryItemId: String,
    val episodeId: String?,
    val mediaType: String,
    val mediaMetadata: BookMetadata,
    val chapters: List<Chapter>,
    val displayTitle: String,
    val displayAuthor: String,
    val duration: Double,
    val playMethod: Int,
    val currentTime: Double,
    val audioTracks: List<AudioTrack>,
    val episodeDescription: String? = null,
)

data class AudioTrack(
    val index: Int,
    val startOffset: Double,
    val duration: Double,
    val title: String,
    val contentUrl: String,
    val mimeType: String,
)

data class ServerConfig(
    val url: String,
    val token: String,
    val userId: String,
    val username: String,
    val selectedLibraryId: String?,
)

// --- Podcast models ---

data class PodcastMedia(
    val id: String?,
    val metadata: PodcastMetadata,
    val coverPath: String?,
    val tags: List<String>,
    val numEpisodes: Int,
    val autoDownloadEpisodes: Boolean,
    val episodes: List<PodcastEpisode>,
)

data class PodcastMetadata(
    val title: String,
    val titleIgnorePrefix: String?,
    val author: String?,
    val description: String?,
    val releaseDate: String?,
    val genres: List<String>,
    val feedUrl: String?,
    val imageUrl: String?,
    val explicit: Boolean,
    val language: String?,
    val type: String?,
)

data class PodcastEpisode(
    val id: String,
    val libraryItemId: String,
    val podcastId: String?,
    val index: Int?,
    val season: String?,
    val episode: String?,
    val episodeType: String?,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val pubDate: String?,
    val publishedAt: Long,
    val audioFile: AudioFile?,
    val chapters: List<Chapter>,
    val duration: Double,
    val size: Long,
    val podcastTitle: String? = null,
    val podcastAuthor: String? = null,
)

data class ItunesSearchResult(
    val id: Long,
    val artistId: Long?,
    val title: String,
    val artistName: String?,
    val description: String?,
    val genres: List<String>,
    val cover: String?,
    val trackCount: Int,
    val feedUrl: String?,
    val pageUrl: String?,
    val explicit: Boolean,
)

data class PodcastFeedPreview(
    val metadata: PodcastMetadata,
    val episodes: List<PodcastFeedEpisodePreview>,
)

data class PodcastFeedEpisodePreview(
    val title: String,
    val description: String?,
    val pubDate: String?,
    val season: String?,
    val episode: String?,
)
