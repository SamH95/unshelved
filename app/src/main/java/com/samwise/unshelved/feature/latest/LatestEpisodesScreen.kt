package com.samwise.unshelved.feature.latest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samwise.unshelved.R
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.PodcastEpisode
import com.samwise.unshelved.core.model.toHoursMinutes
import com.samwise.unshelved.core.model.toLocalizedDate
import com.samwise.unshelved.core.ui.CoverImage
import com.samwise.unshelved.core.ui.LocalBottomPadding
import com.samwise.unshelved.core.ui.LocalTopPadding
import com.samwise.unshelved.core.ui.MultiSelectTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LatestEpisodesScreen(
    onPlayEpisode: (libraryItemId: String, episodeId: String) -> Unit = { _, _ -> },
    onEpisodeClick: (libraryItemId: String, episodeId: String) -> Unit = { _, _ -> },
    queuedEpisodeIds: Set<String> = emptySet(),
    progressMap: Map<String, MediaProgress> = emptyMap(),
    onMultiSelectChanged: (Boolean) -> Unit = {},
    viewModel: LatestEpisodesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()

    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val isMultiSelectActive = selectedIds.isNotEmpty()

    LaunchedEffect(isMultiSelectActive) { onMultiSelectChanged(isMultiSelectActive) }
    BackHandler(enabled = isMultiSelectActive) { selectedIds = emptySet() }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.episodes.size - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoading) {
            viewModel.loadMore()
        }
    }

    val visibleEpisodes = state.episodes.filter { episode ->
                val key = "${episode.libraryItemId}/${episode.id}"
                val p = progressMap[key]
                p == null || (!p.isFinished && p.progress < 0.99f)
            }

    Box(modifier = Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize().padding(top = LocalTopPadding.current),
    ) {
        if (state.isLoading && state.episodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@PullToRefreshBox
        }

        if (state.episodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.no_recent_episodes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@PullToRefreshBox
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp + LocalBottomPadding.current),
        ) {
            items(visibleEpisodes, key = { "${it.libraryItemId}-${it.id}" }) { episode ->
                val compositeKey = "${episode.libraryItemId}-${episode.id}"
                val progressKey = "${episode.libraryItemId}/${episode.id}"
                val progress = progressMap[progressKey]
                val dlState by viewModel.observeEpisodeDownload(episode.libraryItemId, episode.id).collectAsStateWithLifecycle(null)
                EpisodeListItem(
                    episode = episode,
                    serverUrl = serverUrl ?: "",
                    progress = progress,
                    isQueued = episode.id in queuedEpisodeIds,
                    downloadState = dlState,
                    isSelected = compositeKey in selectedIds,
                    isMultiSelectActive = isMultiSelectActive,
                    onTap = {
                        if (isMultiSelectActive) {
                            selectedIds = if (compositeKey in selectedIds) selectedIds - compositeKey else selectedIds + compositeKey
                        } else {
                            onEpisodeClick(episode.libraryItemId, episode.id)
                        }
                    },
                    onLongPress = {
                        selectedIds = if (compositeKey in selectedIds) selectedIds - compositeKey else selectedIds + compositeKey
                    },
                    onPlay = { onPlayEpisode(episode.libraryItemId, episode.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }

            if (state.isLoading && state.episodes.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    if (isMultiSelectActive) {
        MultiSelectTopBar(
            selectedCount = selectedIds.size,
            onClose = { selectedIds = emptySet() },
            onDownload = {
                viewModel.batchDownload(visibleEpisodes.filter { "${it.libraryItemId}-${it.id}" in selectedIds })
                selectedIds = emptySet()
            },
            onAddToQueue = {
                viewModel.batchAddToQueue(visibleEpisodes.filter { "${it.libraryItemId}-${it.id}" in selectedIds })
                selectedIds = emptySet()
            },
            onMarkFinished = {
                viewModel.batchMarkFinished(visibleEpisodes.filter { "${it.libraryItemId}-${it.id}" in selectedIds })
                selectedIds = emptySet()
            },
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier.align(Alignment.TopStart).zIndex(1f),
        )
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeListItem(
    episode: PodcastEpisode,
    serverUrl: String,
    progress: MediaProgress?,
    isQueued: Boolean,
    downloadState: DownloadEntity?,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPlay: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isDownloading = downloadState?.status == DownloadStatus.DOWNLOADING || downloadState?.status == DownloadStatus.QUEUED
    val isDownloaded = downloadState?.status == DownloadStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .combinedClickable(
                onClick = onTap,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            coverUrl = "$serverUrl/api/items/${episode.libraryItemId}/cover",
            contentDescription = null,
            itemId = episode.libraryItemId,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isDownloading) {
                val percent = if (downloadState != null && downloadState.totalBytes > 0) {
                    (downloadState.downloadedBytes * 100 / downloadState.totalBytes).toInt()
                } else 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.downloading_percent, percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isQueued) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAddCheck,
                            contentDescription = stringResource(R.string.in_queue),
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4A90D9),
                        )
                    }
                    if (isDownloaded) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = stringResource(R.string.downloaded),
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF4CAF50),
                        )
                    }
                    if (episode.publishedAt > 0) {
                        Text(
                            text = episode.publishedAt.toLocalizedDate(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (episode.duration > 0) {
                        val hasProgress = progress != null && !progress.isFinished && progress.currentTime > 0
                        val durationText = if (hasProgress) {
                            val remaining = (episode.duration - progress!!.currentTime).coerceAtLeast(0.0)
                            stringResource(R.string.time_left, remaining.toHoursMinutes())
                        } else {
                            episode.duration.toHoursMinutes()
                        }
                        Text(
                            text = "· $durationText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        val progressFraction = if (progress != null && !progress.isFinished && progress.currentTime > 0 && episode.duration > 0) {
            (progress.currentTime / episode.duration).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
            FilledTonalIconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play), modifier = Modifier.size(20.dp))
            }
            if (progressFraction > 0f) {
                CircularProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
