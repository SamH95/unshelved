package com.samwise.unshelved.core.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.samwise.unshelved.core.network.AudioFileDto
import com.samwise.unshelved.core.network.AudioTrackDto
import com.samwise.unshelved.core.network.BookMediaDto
import com.samwise.unshelved.core.network.BookMetadataDto
import com.samwise.unshelved.core.network.ChapterDto
import com.samwise.unshelved.core.network.ItunesSearchResultDto
import com.samwise.unshelved.core.network.LibraryDto
import com.samwise.unshelved.core.network.LibraryItemDto
import com.samwise.unshelved.core.network.MediaProgressDto
import com.samwise.unshelved.core.network.PlaybackSessionDto
import com.samwise.unshelved.core.network.PodcastEpisodeDto
import com.samwise.unshelved.core.network.PodcastFeedEpisodeDto
import com.samwise.unshelved.core.network.PodcastFeedResponse
import com.samwise.unshelved.core.network.PodcastMediaDto
import com.samwise.unshelved.core.network.PodcastMetadataDto
import com.samwise.unshelved.core.network.RecentEpisodeDto
import com.samwise.unshelved.core.network.SeriesDetailDto
import com.samwise.unshelved.core.network.SeriesDto
import com.samwise.unshelved.core.network.SeriesProgressDto
import com.samwise.unshelved.core.network.UserDto

private val mapperGson = Gson()

private val emptyBookMedia = BookMedia(
    id = null,
    metadata = BookMetadata(
        title = "", titleIgnorePrefix = null, subtitle = null,
        authorName = null, narratorName = null, seriesName = null,
        genres = emptyList(), publishedYear = null, description = null,
        language = null, explicit = false, authors = emptyList(), seriesEntries = emptyList(),
    ),
    coverPath = null, duration = 0.0, audioFiles = emptyList(), chapters = emptyList(),
)

fun LibraryDto.toDomain() = Library(
    id = id,
    name = name,
    mediaType = mediaType,
    icon = icon,
    folders = folders.map { LibraryFolder(it.id, it.fullPath) },
)

fun LibraryItemDto.toDomain(): LibraryItem {
    val isPodcast = mediaType == "podcast"
    val bookMedia = if (!isPodcast) {
        runCatching { mapperGson.fromJson(media, BookMediaDto::class.java).toDomain() }
            .getOrDefault(emptyBookMedia)
    } else emptyBookMedia
    val podMedia = if (isPodcast) {
        runCatching { mapperGson.fromJson(media, PodcastMediaDto::class.java).toDomain() }
            .getOrNull()
    } else null
    val recentEp = recentEpisode?.toDomain(
        fallbackLibraryItemId = id,
        podcastTitle = podMedia?.metadata?.title,
        podcastAuthor = podMedia?.metadata?.author,
    )
    return LibraryItem(
        id = id,
        libraryId = libraryId ?: "",
        mediaType = mediaType ?: "book",
        media = bookMedia,
        podcastMedia = podMedia,
        addedAt = addedAt,
        updatedAt = updatedAt,
        progressLastUpdate = progressLastUpdate,
        recentEpisode = recentEp,
    )
}

fun BookMediaDto.toDomain() = BookMedia(
    id = id,
    metadata = metadata.toDomain(),
    coverPath = coverPath,
    duration = duration,
    audioFiles = (audioFiles ?: emptyList()).map { it.toDomain() },
    chapters = (chapters ?: emptyList()).map { it.toDomain() },
)

fun BookMetadataDto.toDomain() = BookMetadata(
    title = title ?: "Unknown Title",
    titleIgnorePrefix = titleIgnorePrefix,
    subtitle = subtitle,
    authorName = authorName,
    narratorName = narratorName,
    seriesName = seriesName,
    genres = genres ?: emptyList(),
    publishedYear = publishedYear,
    description = description,
    language = language,
    explicit = explicit,
    authors = (authors ?: emptyList()).map { Author(it.id, it.name) },
    seriesEntries = parseSeriesEntries(series),
)

private fun parseSeriesEntries(element: com.google.gson.JsonElement?): List<SeriesEntry> {
    if (element == null || element.isJsonNull) return emptyList()
    return try {
        when {
            element.isJsonArray -> {
                element.asJsonArray.mapNotNull { el ->
                    if (el.isJsonObject) parseSeriesEntry(el.asJsonObject) else null
                }
            }
            element.isJsonObject -> {
                listOfNotNull(parseSeriesEntry(element.asJsonObject))
            }
            else -> emptyList()
        }
    } catch (_: Exception) { emptyList() }
}

private fun parseSeriesEntry(obj: JsonObject): SeriesEntry? {
    val id = obj.get("id")?.asString ?: return null
    val name = obj.get("name")?.asString ?: return null
    val sequence = obj.get("sequence")?.takeIf { !it.isJsonNull }?.asString
    return SeriesEntry(id, name, sequence)
}

// --- Podcast mappers ---

fun PodcastMediaDto.toDomain() = PodcastMedia(
    id = id,
    metadata = metadata.toDomain(),
    coverPath = coverPath,
    tags = tags,
    numEpisodes = numEpisodes,
    autoDownloadEpisodes = autoDownloadEpisodes,
    episodes = (episodes ?: emptyList()).map { it.toDomain() },
)

fun PodcastMetadataDto.toDomain() = PodcastMetadata(
    title = title ?: "Unknown Podcast",
    titleIgnorePrefix = titleIgnorePrefix,
    author = author,
    description = description,
    releaseDate = releaseDate,
    genres = resolvedGenres,
    feedUrl = feedUrl,
    imageUrl = resolvedImageUrl,
    explicit = explicit,
    language = language,
    type = type,
)

fun PodcastEpisodeDto.toDomain(
    fallbackLibraryItemId: String = "",
    podcastTitle: String? = null,
    podcastAuthor: String? = null,
) = PodcastEpisode(
    id = id,
    libraryItemId = libraryItemId ?: fallbackLibraryItemId,
    podcastId = podcastId,
    index = index,
    season = season,
    episode = episode,
    episodeType = episodeType,
    title = title,
    subtitle = subtitle,
    description = description,
    pubDate = pubDate,
    publishedAt = publishedAt ?: 0L,
    audioFile = audioFile?.toDomain(),
    chapters = (chapters ?: emptyList()).map { it.toDomain() },
    duration = duration,
    size = size,
    podcastTitle = podcastTitle,
    podcastAuthor = podcastAuthor,
)

fun RecentEpisodeDto.toDomain() = PodcastEpisode(
    id = id,
    libraryItemId = libraryItemId,
    podcastId = podcastId,
    index = null,
    season = season,
    episode = episode,
    episodeType = episodeType,
    title = title,
    subtitle = subtitle,
    description = description,
    pubDate = pubDate,
    publishedAt = publishedAt ?: 0L,
    audioFile = audioFile?.toDomain(),
    chapters = (chapters ?: emptyList()).map { it.toDomain() },
    duration = duration,
    size = size,
    podcastTitle = podcast?.metadata?.title,
    podcastAuthor = podcast?.metadata?.author,
)

fun ItunesSearchResultDto.toDomain() = ItunesSearchResult(
    id = id,
    artistId = artistId,
    title = title,
    artistName = artistName,
    description = description,
    genres = genres ?: emptyList(),
    cover = cover,
    trackCount = trackCount,
    feedUrl = feedUrl,
    pageUrl = pageUrl,
    explicit = explicit,
)

fun PodcastFeedResponse.toDomain() = PodcastFeedPreview(
    metadata = podcast.metadata.toDomain(),
    episodes = podcast.episodes.map { it.toDomain() },
)

fun PodcastFeedEpisodeDto.toDomain() = PodcastFeedEpisodePreview(
    title = title ?: "",
    description = description,
    pubDate = pubDate,
    season = season,
    episode = episode,
)

// --- Common mappers ---

fun AudioFileDto.toDomain() = AudioFile(
    index = index,
    ino = ino,
    metadata = AudioFileMetadata(
        filename = metadata.filename,
        ext = metadata.ext,
        path = metadata.path,
        relPath = metadata.relPath,
        size = metadata.size,
    ),
    duration = duration,
    bitRate = bitRate,
    codec = codec,
    mimeType = mimeType,
)

fun ChapterDto.toDomain() = Chapter(id = id, start = start, end = end, title = title)

fun MediaProgressDto.toDomain() = MediaProgress(
    id = id,
    libraryItemId = libraryItemId,
    episodeId = episodeId,
    duration = duration,
    progress = progress,
    currentTime = currentTime,
    isFinished = isFinished,
    hideFromContinueListening = hideFromContinueListening,
    lastUpdate = lastUpdate,
    startedAt = startedAt,
    finishedAt = finishedAt,
)

fun UserDto.toDomain() = User(
    id = id,
    username = username,
    email = email,
    type = type ?: "",
    token = effectiveToken,
    mediaProgress = (mediaProgress ?: emptyList()).map { it.toDomain() },
)

fun PlaybackSessionDto.toDomain() = PlaybackSession(
    id = id,
    libraryItemId = libraryItemId,
    episodeId = episodeId,
    mediaType = mediaType,
    mediaMetadata = mediaMetadata.toDomain(),
    chapters = (chapters ?: emptyList()).map { it.toDomain() },
    displayTitle = displayTitle,
    displayAuthor = displayAuthor,
    duration = duration,
    playMethod = playMethod,
    currentTime = currentTime,
    audioTracks = (audioTracks ?: emptyList()).map { it.toDomain() },
)

fun AudioTrackDto.toDomain() = AudioTrack(
    index = index,
    startOffset = startOffset,
    duration = duration,
    title = title,
    contentUrl = contentUrl,
    mimeType = mimeType,
)

fun SeriesDto.toDomain() = Series(
    id = id,
    name = name,
    description = description,
    addedAt = addedAt,
    updatedAt = updatedAt,
    books = (books ?: emptyList()).map { it.toDomain() },
)

fun SeriesDetailDto.toDomain() = Series(
    id = id,
    name = name,
    description = description,
    addedAt = addedAt,
    updatedAt = updatedAt,
    books = (books ?: emptyList()).map { it.toDomain() },
    progress = progress?.toDomain(),
)

fun SeriesProgressDto.toDomain() = SeriesProgress(
    libraryItemIds = libraryItemIds ?: emptyList(),
    libraryItemIdsFinished = libraryItemIdsFinished ?: emptyList(),
    isFinished = isFinished,
)
