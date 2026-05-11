package com.samwise.unshelved.feature.preview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.samwise.unshelved.core.model.Author
import com.samwise.unshelved.core.model.BookMedia
import com.samwise.unshelved.core.model.BookMetadata
import com.samwise.unshelved.core.model.Chapter
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.PodcastEpisode
import com.samwise.unshelved.core.model.SeriesEntry
import com.samwise.unshelved.feature.detail.DetailContent
import com.samwise.unshelved.feature.detail.DownloadButtonState
import com.samwise.unshelved.feature.home.HomeContent
import com.samwise.unshelved.feature.home.HomeState
import com.samwise.unshelved.feature.latest.EpisodeListItem
import com.samwise.unshelved.feature.library.LibraryContent
import com.samwise.unshelved.feature.library.LibraryState
import com.samwise.unshelved.ui.theme.UnshelvedTheme

private fun dummyBook(
    id: String,
    title: String,
    author: String,
    narrator: String? = null,
    duration: Double = 36000.0,
    description: String? = null,
    publishedYear: String? = null,
    seriesName: String? = null,
    seriesSequence: String? = null,
    genres: List<String> = emptyList(),
): LibraryItem {
    val authors = listOf(Author(id = "author-$id", name = author))
    val seriesEntries = if (seriesName != null) listOf(
        SeriesEntry(id = "series-$id", name = seriesName, sequence = seriesSequence)
    ) else emptyList()

    return LibraryItem(
        id = id,
        libraryId = "lib-1",
        mediaType = "book",
        media = BookMedia(
            id = "media-$id",
            metadata = BookMetadata(
                title = title,
                titleIgnorePrefix = null,
                subtitle = null,
                authorName = author,
                narratorName = narrator,
                seriesName = seriesName,
                genres = genres,
                publishedYear = publishedYear,
                description = description,
                language = "en",
                authors = authors,
                seriesEntries = seriesEntries,
            ),
            coverPath = null,
            duration = duration,
            chapters = (1..12).map { i ->
                Chapter(id = i, start = (i - 1) * 3000.0, end = i * 3000.0, title = "Chapter $i")
            },
        ),
        addedAt = System.currentTimeMillis() - (86400000L * (6 - id.hashCode().mod(6))),
        updatedAt = System.currentTimeMillis(),
    )
}

private fun dummyProgress(
    itemId: String,
    progress: Float,
    duration: Double = 36000.0,
): MediaProgress = MediaProgress(
    id = "prog-$itemId",
    libraryItemId = itemId,
    episodeId = null,
    duration = duration,
    progress = progress,
    currentTime = (duration * progress).toDouble(),
    isFinished = progress >= 1f,
    hideFromContinueListening = false,
    lastUpdate = System.currentTimeMillis(),
    startedAt = System.currentTimeMillis() - 86400000L,
    finishedAt = if (progress >= 1f) System.currentTimeMillis() else null,
)

private fun dummyEpisode(
    id: String,
    title: String,
    podcastTitle: String,
    libraryItemId: String,
    duration: Double = 3600.0,
    publishedAt: Long = System.currentTimeMillis() - 86400000L,
): PodcastEpisode = PodcastEpisode(
    id = id,
    libraryItemId = libraryItemId,
    podcastId = "podcast-$libraryItemId",
    index = null,
    season = null,
    episode = null,
    episodeType = null,
    title = title,
    subtitle = null,
    description = null,
    pubDate = null,
    publishedAt = publishedAt,
    audioFile = null,
    chapters = emptyList(),
    duration = duration,
    size = 50_000_000L,
    podcastTitle = podcastTitle,
    podcastAuthor = null,
)

private val sampleBooks = listOf(
    dummyBook("b1", "The Midnight Garden", "Elena Marchetti", "Sophie Laurent", 43200.0, "A haunting tale of memory and loss set in a mysterious garden that only blooms at night.", "2023", "The Meridian Cycle", "1", listOf("Fantasy", "Literary Fiction")),
    dummyBook("b2", "Echoes of Tomorrow", "James Whitmore", "David Chen", 52800.0, "In a world where echoes of the future can be heard, one woman must decide what to change.", "2024", "The Meridian Cycle", "2", listOf("Science Fiction")),
    dummyBook("b3", "A Thousand Paper Cranes", "Aisha Kato", "Sophie Laurent", 28800.0, "A multi-generational story spanning three continents and seventy years.", "2022", genres = listOf("Historical Fiction")),
    dummyBook("b4", "Silent Meridian", "Marcus Venn", "David Chen", 39600.0, "The silence between worlds holds a secret that could unravel reality.", "2024", "The Meridian Cycle", "3", listOf("Fantasy", "Thriller")),
    dummyBook("b5", "The Clockwork Atlas", "Elena Marchetti", "Sophie Laurent", 46800.0, "An atlas that maps not geography but time itself falls into unlikely hands.", "2023", genres = listOf("Steampunk", "Adventure")),
    dummyBook("b6", "Beneath the Copper Sky", "James Whitmore", "David Chen", 34200.0, "Under skies turned copper by industry, a revolution brews in whispered code.", "2024", genres = listOf("Dystopian")),
)

private val sampleProgress = mapOf(
    "b1" to dummyProgress("b1", 0.72f, 43200.0),
    "b2" to dummyProgress("b2", 0.35f, 52800.0),
    "b3" to dummyProgress("b3", 0.91f, 28800.0),
)

private val sampleEpisodes = listOf(
    dummyEpisode("e1", "The Fall of Constantinople", "History Unscripted", "p1", 4200.0, System.currentTimeMillis() - 86400000L),
    dummyEpisode("e2", "How Paper Changed the World", "The Daily Discovery", "p2", 2700.0, System.currentTimeMillis() - 172800000L),
    dummyEpisode("e3", "Secrets of the Silk Road", "History Unscripted", "p1", 3600.0, System.currentTimeMillis() - 259200000L),
    dummyEpisode("e4", "The Mathematics of Music", "The Daily Discovery", "p2", 3300.0, System.currentTimeMillis() - 345600000L),
    dummyEpisode("e5", "Lost Languages of the Ancient World", "History Unscripted", "p1", 3900.0, System.currentTimeMillis() - 432000000L),
)

// ─── Home Screen ─────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Home")
@Composable
private fun HomePreview() {
    UnshelvedTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HomeContent(
                state = HomeState(
                    continueListening = sampleBooks.take(3),
                    recentlyAdded = sampleBooks.drop(2),
                    discover = sampleBooks.take(3).reversed(),
                ),
                serverUrl = "",
                progressMap = sampleProgress,
                onBookClick = {},
                onPlayBook = {},
                onSeriesClick = {},
                onPodcastClick = {},
                onPlayEpisode = { _, _ -> },
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home Dark")
@Composable
private fun HomePreviewDark() {
    UnshelvedTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HomeContent(
                state = HomeState(
                    continueListening = sampleBooks.take(3),
                    recentlyAdded = sampleBooks.drop(2),
                    discover = sampleBooks.take(3).reversed(),
                ),
                serverUrl = "",
                progressMap = sampleProgress,
                onBookClick = {},
                onPlayBook = {},
                onSeriesClick = {},
                onPodcastClick = {},
                onPlayEpisode = { _, _ -> },
            )
        }
    }
}

// ─── Library Screen ──────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Library Grid")
@Composable
private fun LibraryGridPreview() {
    UnshelvedTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryContent(
                state = LibraryState(
                    items = sampleBooks,
                    total = sampleBooks.size,
                    isGridView = true,
                ),
                serverUrl = "",
                progressMap = sampleProgress,
                onBookClick = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Library Grid Dark")
@Composable
private fun LibraryGridPreviewDark() {
    UnshelvedTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryContent(
                state = LibraryState(
                    items = sampleBooks,
                    total = sampleBooks.size,
                    isGridView = true,
                ),
                serverUrl = "",
                progressMap = sampleProgress,
                onBookClick = {},
            )
        }
    }
}

// ─── Detail Screen ───────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Detail")
@Composable
private fun DetailPreview() {
    UnshelvedTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DetailContent(
                item = sampleBooks[0],
                serverUrl = "",
                progress = sampleProgress["b1"],
                downloadButtonState = DownloadButtonState.NotDownloaded,
                onPlay = {},
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Detail Dark")
@Composable
private fun DetailPreviewDark() {
    UnshelvedTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            DetailContent(
                item = sampleBooks[0],
                serverUrl = "",
                progress = sampleProgress["b1"],
                downloadButtonState = DownloadButtonState.NotDownloaded,
                onPlay = {},
            )
        }
    }
}

// ─── Latest Episodes Screen ──────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Latest Episodes")
@Composable
private fun LatestEpisodesPreview() {
    UnshelvedTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(sampleEpisodes) { episode ->
                    EpisodeListItem(
                        episode = episode,
                        serverUrl = "",
                        progress = null,
                        isQueued = episode.id == "e2",
                        downloadState = null,
                        isSelected = false,
                        isMultiSelectActive = false,
                        onTap = {},
                        onLongPress = {},
                        onPlay = {},
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Latest Episodes Dark")
@Composable
private fun LatestEpisodesPreviewDark() {
    UnshelvedTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(sampleEpisodes) { episode ->
                    EpisodeListItem(
                        episode = episode,
                        serverUrl = "",
                        progress = null,
                        isQueued = episode.id == "e2",
                        downloadState = null,
                        isSelected = false,
                        isMultiSelectActive = false,
                        onTap = {},
                        onLongPress = {},
                        onPlay = {},
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
