package com.samwise.unshelved.feature.episodedetail

import android.text.Html
import android.text.Spanned
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.samwise.unshelved.R
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.model.toHoursMinutes
import com.samwise.unshelved.core.model.toLocalizedDate
import com.samwise.unshelved.core.ui.buildAnnotatedStringFromSpanned

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailSheet(
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onTimestampClick: (Double) -> Unit,
    onPodcastClick: (libraryItemId: String) -> Unit = {},
    viewModel: EpisodeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isInQueue by viewModel.isInQueue.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        val episode = state.episode
        if (state.isLoading || episode == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = "$serverUrl/api/items/${episode.libraryItemId}/cover",
                contentDescription = episode.title,
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(modifier = Modifier.height(12.dp))

            val buttonSize = 48.dp
            val isFinished = progress?.isFinished == true || (progress?.progress ?: 0f) >= 0.99f
            val playbackProgress = progress?.let {
                if (!it.isFinished && it.currentTime > 0 && episode.duration > 0)
                    (it.currentTime / episode.duration).toFloat().coerceIn(0f, 1f)
                else 0f
            } ?: 0f

            val downloadEnabled = downloadState?.status == null || downloadState?.status == DownloadStatus.FAILED
            val isDownloading = downloadState?.status == DownloadStatus.DOWNLOADING || downloadState?.status == DownloadStatus.QUEUED
            val downloadComplete = downloadState?.status == DownloadStatus.COMPLETED
            val dlProgress = if (isDownloading && downloadState != null && downloadState!!.totalBytes > 0) {
                (downloadState!!.downloadedBytes.toFloat() / downloadState!!.totalBytes).coerceIn(0f, 1f)
            } else 0f

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(buttonSize)) {
                    FilledTonalIconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(buttonSize),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
                    }
                    if (playbackProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { playbackProgress },
                            modifier = Modifier.size(buttonSize),
                            strokeWidth = 3.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = { viewModel.toggleQueue() },
                    modifier = Modifier.size(buttonSize),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        imageVector = if (isInQueue) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = if (isInQueue) stringResource(R.string.remove_from_queue) else stringResource(R.string.add_to_queue),
                        tint = if (isInQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                val downloadIcon = when (downloadState?.status) {
                    DownloadStatus.COMPLETED -> Icons.Default.DownloadDone
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Icons.Default.Downloading
                    else -> Icons.Default.Download
                }
                Box(contentAlignment = Alignment.Center) {
                    if (isDownloading) {
                        if (dlProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { dlProgress },
                                modifier = Modifier.size(buttonSize),
                                strokeWidth = 3.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(buttonSize),
                                strokeWidth = 3.dp,
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = { if (downloadEnabled) viewModel.startDownload() },
                        enabled = downloadEnabled,
                        modifier = Modifier.size(if (isDownloading) 38.dp else buttonSize),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) {
                        Icon(
                            downloadIcon,
                            contentDescription = if (downloadComplete) stringResource(R.string.downloaded) else stringResource(R.string.download),
                            tint = if (downloadComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = { viewModel.toggleFinished() },
                    modifier = Modifier.size(buttonSize),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        imageVector = if (isFinished) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                        contentDescription = if (isFinished) stringResource(R.string.mark_unfinished) else stringResource(R.string.mark_finished),
                        tint = if (isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            episode.podcastTitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { onPodcastClick(episode.libraryItemId) }
                        .padding(horizontal = 24.dp),
                )
            }

            val detailParts = buildList {
                if (episode.publishedAt > 0) add(episode.publishedAt.toLocalizedDate())
                if (episode.duration > 0) {
                    val p = progress
                    val hasProgress = p != null && !p.isFinished && p.currentTime > 0
                    if (hasProgress) {
                        val remaining = (episode.duration - p!!.currentTime).coerceAtLeast(0.0)
                        add(stringResource(R.string.time_left, remaining.toHoursMinutes()))
                    } else {
                        add(episode.duration.toHoursMinutes())
                    }
                }
            }
            if (detailParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = detailParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            if (!episode.description.isNullOrBlank()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    val spanned = remember(episode.description) {
                        val s = Html.fromHtml(episode.description, Html.FROM_HTML_MODE_LEGACY)
                        val text = s.toString()
                        val start = text.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                        val end = text.indexOfLast { !it.isWhitespace() }.coerceAtLeast(0) + 1
                        s.subSequence(start, end) as Spanned
                    }
                    val annotated = buildAnnotatedStringFromSpanned(
                        spanned,
                        linkColor = MaterialTheme.colorScheme.primary,
                        onTimestampClick = onTimestampClick,
                    )
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
