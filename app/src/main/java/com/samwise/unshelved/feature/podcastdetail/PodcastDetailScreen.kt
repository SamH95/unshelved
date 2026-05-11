package com.samwise.unshelved.feature.podcastdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.samwise.unshelved.R
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.PodcastEpisode
import com.samwise.unshelved.core.model.toHoursMinutes
import com.samwise.unshelved.core.model.toLocalizedDate
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.ui.LocalBottomPadding
import com.samwise.unshelved.core.ui.MultiSelectTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PodcastDetailScreen(
    onBack: () -> Unit,
    onPlayEpisode: (libraryItemId: String, episodeId: String) -> Unit = { _, _ -> },
    onEpisodeClick: (libraryItemId: String, episodeId: String) -> Unit = { _, _ -> },
    onDeleted: () -> Unit = {},
    queuedEpisodeIds: Set<String> = emptySet(),
    newlyAdded: Boolean = false,
    viewModel: PodcastDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val autoDownloadEnabled by viewModel.autoDownloadEnabled.collectAsStateWithLifecycle()
    val progressMap by viewModel.progressCache.progressMap.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val isMultiSelectActive = selectedIds.isNotEmpty()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(newlyAdded) {
        if (newlyAdded) viewModel.setAwaitingEpisodes()
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    BackHandler(enabled = isMultiSelectActive) { selectedIds = emptySet() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isMultiSelectActive) {
                MultiSelectTopBar(
                    selectedCount = selectedIds.size,
                    onClose = { selectedIds = emptySet() },
                    onDownload = {
                        viewModel.batchDownload(state.episodes.filter { it.id in selectedIds })
                        selectedIds = emptySet()
                    },
                    onAddToQueue = {
                        viewModel.batchAddToQueue(state.episodes.filter { it.id in selectedIds })
                        selectedIds = emptySet()
                    },
                    onMarkFinished = {
                        viewModel.batchMarkFinished(state.episodes.filter { it.id in selectedIds })
                        selectedIds = emptySet()
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(state.item?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val item = state.item ?: return@Scaffold
        var isRefreshing by remember { mutableStateOf(false) }
        val refreshScope = rememberCoroutineScope()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                refreshScope.launch {
                    isRefreshing = true
                    viewModel.load()
                    delay(1000)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp + LocalBottomPadding.current),
        ) {
            item {
                PodcastHeader(
                    title = item.title,
                    author = item.authorName,
                    description = item.podcastMedia?.metadata?.description,
                    descriptionExpanded = state.descriptionExpanded,
                    onToggleDescription = viewModel::toggleDescription,
                    coverUrl = "$serverUrl/api/items/${item.id}/cover",
                    episodeCount = state.episodes.size,
                )
            }

            item {
                AutoDownloadRow(
                    enabled = autoDownloadEnabled,
                    onToggle = viewModel::toggleAutoDownload,
                )
            }

            if (state.awaitingEpisodes && state.episodes.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.downloading_latest),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.episodes, key = { it.id }) { episode ->
                val key = ProgressCache.progressKey(item.id, episode.id)
                val progress = progressMap[key]
                val isFinished = progress != null && (progress.isFinished || progress.progress >= 0.99f)
                val dlState by viewModel.observeEpisodeDownload(episode.id).collectAsStateWithLifecycle(null)
                EpisodeRow(
                    episode = episode,
                    isFinished = isFinished,
                    progress = progress,
                    isQueued = episode.id in queuedEpisodeIds,
                    downloadState = dlState,
                    isSelected = episode.id in selectedIds,
                    isMultiSelectActive = isMultiSelectActive,
                    onTap = {
                        if (isMultiSelectActive) {
                            selectedIds = if (episode.id in selectedIds) selectedIds - episode.id else selectedIds + episode.id
                        } else {
                            onEpisodeClick(item.id, episode.id)
                        }
                    },
                    onLongPress = {
                        selectedIds = if (episode.id in selectedIds) selectedIds - episode.id else selectedIds + episode.id
                    },
                    onPlay = { onPlayEpisode(item.id, episode.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_podcast)) },
            text = { Text(stringResource(R.string.delete_podcast_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePodcast() },
                    enabled = !state.isDeleting,
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !state.isDeleting,
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PodcastHeader(
    title: String,
    author: String?,
    description: String?,
    descriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    coverUrl: String,
    episodeCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.episodes_count, episodeCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (description != null) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (descriptionExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onToggleDescription).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AutoDownloadRow(
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(stringResource(R.string.auto_download), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.auto_download_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    isFinished: Boolean,
    progress: MediaProgress?,
    isQueued: Boolean,
    downloadState: DownloadEntity?,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPlay: () -> Unit,
) {
    val alpha = if (isFinished) 0.45f else 1f
    val haptic = LocalHapticFeedback.current
    val isDownloading = downloadState?.status == DownloadStatus.DOWNLOADING || downloadState?.status == DownloadStatus.QUEUED
    val isDownloaded = downloadState?.status == DownloadStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
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
