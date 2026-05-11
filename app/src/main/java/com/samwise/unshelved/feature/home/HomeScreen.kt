package com.samwise.unshelved.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samwise.unshelved.R
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.Series
import com.samwise.unshelved.core.ui.BookCoverCard
import com.samwise.unshelved.core.ui.CoverImage
import com.samwise.unshelved.core.ui.LocalBottomPadding
import com.samwise.unshelved.core.ui.LocalTopPadding
import com.samwise.unshelved.core.ui.SeriesCoverStack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBookClick: (String) -> Unit,
    onPlayBook: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    onPodcastClick: (String) -> Unit = {},
    onPlayEpisode: (libraryItemId: String, episodeId: String) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl = (viewModel.serverUrl.collectAsStateWithLifecycle().value ?: "")
    val progressMap by viewModel.progressCache.progressMap.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize().padding(top = LocalTopPadding.current),
    ) {
        HomeContent(
            state = state,
            serverUrl = serverUrl,
            progressMap = progressMap,
            onBookClick = onBookClick,
            onPlayBook = onPlayBook,
            onSeriesClick = onSeriesClick,
            onPodcastClick = onPodcastClick,
            onPlayEpisode = onPlayEpisode,
        )
    }
}

@Composable
internal fun HomeContent(
    state: HomeState,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    onBookClick: (String) -> Unit,
    onPlayBook: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    onPodcastClick: (String) -> Unit = {},
    onPlayEpisode: (libraryItemId: String, episodeId: String) -> Unit = { _, _ -> },
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp + LocalBottomPadding.current),
    ) {
        if (state.isPodcastLibrary) {
            shelfSection(R.string.shelf_continue_listening, state.continueListening) {
                ContinueListeningRow(
                    items = state.continueListening,
                    serverUrl = serverUrl,
                    progressMap = progressMap,
                    onBookClick = { itemId ->
                        val ep = state.continueListening.find { it.id == itemId }?.recentEpisode
                        if (ep != null) onPlayEpisode(itemId, ep.id) else onPlayBook(itemId)
                    },
                )
            }

            shelfSection(R.string.shelf_newest_episodes, state.newestEpisodes) {
                BookRow(
                    items = state.newestEpisodes,
                    serverUrl = serverUrl,
                    progressMap = progressMap,
                    onBookClick = { itemId ->
                        val ep = state.newestEpisodes.find { it.id == itemId }?.recentEpisode
                        if (ep != null) onPlayEpisode(itemId, ep.id) else onPodcastClick(itemId)
                    },
                )
            }

            shelfSection(R.string.shelf_recently_added, state.recentlyAdded) {
                BookRow(
                    items = state.recentlyAdded,
                    serverUrl = serverUrl,
                    progressMap = progressMap,
                    onBookClick = { onPodcastClick(it) },
                )
            }

            shelfSection(R.string.shelf_listen_again, state.listenAgain) {
                BookRow(
                    items = state.listenAgain,
                    serverUrl = serverUrl,
                    progressMap = progressMap,
                    onBookClick = { itemId ->
                        val ep = state.listenAgain.find { it.id == itemId }?.recentEpisode
                        if (ep != null) onPlayEpisode(itemId, ep.id) else onPodcastClick(itemId)
                    },
                )
            }
        } else {
        shelfSection(R.string.shelf_continue_listening, state.continueListening) {
            ContinueListeningRow(
                items = state.continueListening,
                serverUrl = serverUrl,
                progressMap = progressMap,
                onBookClick = onPlayBook,
            )
        }

        shelfSection(R.string.shelf_continue_series, state.continueSeries) {
            BookRow(
                items = state.continueSeries,
                serverUrl = serverUrl,
                progressMap = progressMap,
                onBookClick = onBookClick,
            )
        }

        shelfSection(R.string.shelf_recently_added, state.recentlyAdded) {
            BookRow(
                items = state.recentlyAdded,
                serverUrl = serverUrl,
                progressMap = progressMap,
                onBookClick = onBookClick,
            )
        }

        shelfSection(R.string.shelf_discover, state.discover) {
            BookRow(
                items = state.discover,
                serverUrl = serverUrl,
                progressMap = progressMap,
                onBookClick = onBookClick,
            )
        }

        shelfSection(R.string.shelf_recent_series, state.recentSeries) {
            SeriesRow(
                series = state.recentSeries,
                serverUrl = serverUrl,
                onSeriesClick = onSeriesClick,
            )
        }
        }

        if (!state.hasContent) {
            emptyState()
        }
    }
}

private fun <T> LazyListScope.shelfSection(
    @androidx.annotation.StringRes titleRes: Int,
    items: List<T>,
    content: @Composable () -> Unit,
) {
    if (items.isNotEmpty()) {
        item {
            SectionHeader(stringResource(titleRes))
            content()
        }
    }
}

private fun LazyListScope.emptyState() {
    item {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.no_content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 16.dp),
    )
}

@Composable
private fun ContinueListeningRow(
    items: List<LibraryItem>,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    onBookClick: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            ContinueListeningCard(
                item = item,
                serverUrl = serverUrl,
                progress = progressMap[item.id],
                onClick = { onBookClick(item.id) },
            )
        }
    }
}

@Composable
private fun ContinueListeningCard(
    item: LibraryItem,
    serverUrl: String,
    progress: MediaProgress?,
    onClick: () -> Unit,
) {
    val coverUrl = "$serverUrl/api/items/${item.id}/cover"

    Surface(
        modifier = Modifier.size(200.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CoverImage(
                coverUrl = coverUrl,
                contentDescription = item.title,
                itemId = item.id,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.3f to Color.Black.copy(alpha = 0.7f),
                                1.0f to Color.Black,
                            ),
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.recentEpisode?.title ?: item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = if (item.isPodcast) item.title else item.authorName
                        subtitle?.let { s ->
                            Text(
                                text = s,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (progress != null && (progress.progress > 0f || progress.isFinished)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        ProgressIndicator(progress = progress, onDarkBackground = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressIndicator(
    progress: MediaProgress,
    onDarkBackground: Boolean = false,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (progress.isFinished) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.finished),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            CircularProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = if (onDarkBackground) Color.White else MaterialTheme.colorScheme.primary,
                trackColor = if (onDarkBackground) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        }
    }
}

@Composable
private fun BookRow(
    items: List<LibraryItem>,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    onBookClick: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            BookCoverCard(
                item = item,
                serverUrl = serverUrl,
                progress = progressMap[item.id],
                onClick = { onBookClick(item.id) },
                modifier = Modifier.width(150.dp),
            )
        }
    }
}

@Composable
private fun SeriesRow(
    series: List<Series>,
    serverUrl: String,
    onSeriesClick: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(series, key = { it.id }) { s ->
            SeriesCard(series = s, serverUrl = serverUrl, onClick = { onSeriesClick(s.id) })
        }
    }
}

@Composable
private fun SeriesCard(
    series: Series,
    serverUrl: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(336.dp)
            .padding(bottom = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Column {
            SeriesCoverStack(
                bookIds = series.books.map { it.id },
                serverUrl = serverUrl,
                fallbackText = series.name,
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${series.books.size} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
