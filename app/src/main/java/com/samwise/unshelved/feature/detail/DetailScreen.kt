package com.samwise.unshelved.feature.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.samwise.unshelved.R
import com.samwise.unshelved.core.model.Chapter
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.toHhMmSs
import com.samwise.unshelved.core.model.toHoursMinutes
import com.samwise.unshelved.core.ui.CoverImage
import com.samwise.unshelved.core.ui.LocalTopPadding
import com.samwise.unshelved.core.ui.buildAnnotatedStringFromSpanned

private const val DESCRIPTION_MAX_LINES = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onPlay: (String) -> Unit,
    onPlayAtPosition: (String, Double) -> Unit = { id, _ -> onPlay(id) },
    onBack: () -> Unit,
    onAuthorClick: (String, String) -> Unit = { _, _ -> },
    onSeriesClick: (String) -> Unit = {},
    onNarratorClick: (String) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val item = state.item
    if (item == null) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.could_not_load_details), style = MaterialTheme.typography.titleMedium)
                Text(
                    state.error ?: stringResource(R.string.no_connection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        return
    }

    var showInfoSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    val downloadButtonState = downloadState.toButtonState()

    DetailContent(
        item = item,
        serverUrl = serverUrl,
        progress = state.progress,
        downloadButtonState = downloadButtonState,
        onPlay = { onPlay(item.id) },
        onToggleFinished = viewModel::toggleFinished,
        onDownloadAction = {
            when (downloadButtonState) {
                is DownloadButtonState.Completed -> viewModel.deleteDownload()
                is DownloadButtonState.Downloading,
                is DownloadButtonState.Queued -> {}
                is DownloadButtonState.NotDownloaded,
                is DownloadButtonState.Failed -> viewModel.startDownload()
            }
        },
        onShowChapters = { showChapterSheet = true },
        onShowInfo = { showInfoSheet = true },
        onAuthorClick = onAuthorClick,
        onSeriesClick = onSeriesClick,
    )

    if (showInfoSheet) {
        InfoBottomSheet(
            item = item,
            progress = state.progress,
            onDismiss = { showInfoSheet = false },
            onAuthorClick = { id, name -> onAuthorClick(id, name); showInfoSheet = false },
            onSeriesClick = { id -> onSeriesClick(id); showInfoSheet = false },
            onNarratorClick = { name -> onNarratorClick(name); showInfoSheet = false },
            onGenreClick = { genre -> onGenreClick(genre); showInfoSheet = false },
        )
    }

    if (showChapterSheet) {
        ChaptersBottomSheet(
            chapters = item.media.chapters,
            onDismiss = { showChapterSheet = false },
            onChapterClick = { startPosition ->
                onPlayAtPosition(item.id, startPosition)
                showChapterSheet = false
            },
        )
    }
}

@Composable
internal fun DetailContent(
    item: LibraryItem,
    serverUrl: String?,
    progress: MediaProgress?,
    downloadButtonState: DownloadButtonState,
    onPlay: () -> Unit,
    onToggleFinished: () -> Unit = {},
    onDownloadAction: () -> Unit = {},
    onShowChapters: () -> Unit = {},
    onShowInfo: () -> Unit = {},
    onAuthorClick: (String, String) -> Unit = { _, _ -> },
    onSeriesClick: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LocalTopPadding.current)
            .verticalScroll(rememberScrollState()),
    ) {
        CoverMetadataCard(
            item = item,
            serverUrl = serverUrl,
            progress = progress,
            downloadButtonState = downloadButtonState,
            onPlay = onPlay,
            onToggleFinished = onToggleFinished,
            onDownloadAction = onDownloadAction,
            onShowChapters = onShowChapters,
            onShowInfo = onShowInfo,
            onAuthorClick = onAuthorClick,
            onSeriesClick = onSeriesClick,
        )

        item.media.metadata.description?.let { desc ->
            DescriptionCard(description = desc)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CoverMetadataCard(
    item: LibraryItem,
    serverUrl: String?,
    progress: MediaProgress?,
    downloadButtonState: DownloadButtonState,
    onPlay: () -> Unit,
    onToggleFinished: () -> Unit,
    onDownloadAction: () -> Unit,
    onShowChapters: () -> Unit,
    onShowInfo: () -> Unit,
    onAuthorClick: (String, String) -> Unit,
    onSeriesClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = item.media.metadata
    val context = LocalContext.current
    val isFinished = progress?.isFinished == true
    val hasProgress = progress?.let { it.progress > 0f && !it.isFinished } == true

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (LocalInspectionMode.current) {
            CoverImage(
                coverUrl = "",
                contentDescription = meta.title,
                itemId = item.id,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(
                        item.media.coverPath?.let { java.io.File(it).takeIf { f -> f.exists() } }
                            ?: "$serverUrl/api/items/${item.id}/cover"
                    )
                    .allowHardware(false)
                    .build(),
                contentDescription = meta.title,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = meta.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )

            meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            SeriesRow(meta.seriesEntries, meta.seriesName, onSeriesClick)

            Spacer(modifier = Modifier.height(8.dp))

            AuthorDurationRow(
                authors = meta.authors,
                authorName = meta.authorName,
                duration = item.media.duration,
                publishedYear = meta.publishedYear,
                onAuthorClick = onAuthorClick,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            ActionButtonsRow(
                hasProgress = hasProgress,
                progressFraction = progress?.progress ?: 0f,
                isFinished = isFinished,
                downloadButtonState = downloadButtonState,
                onPlay = onPlay,
                onToggleFinished = onToggleFinished,
                onDownloadAction = onDownloadAction,
                onShowChapters = onShowChapters,
                onShowInfo = onShowInfo,
            )
        }
    }
}

@Composable
private fun SeriesRow(
    seriesEntries: List<com.samwise.unshelved.core.model.SeriesEntry>,
    seriesName: String?,
    onSeriesClick: (String) -> Unit,
) {
    if (seriesEntries.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            seriesEntries.forEachIndexed { index, entry ->
                if (index > 0) Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val label = entry.sequence?.let { "${entry.name} #$it" } ?: entry.name
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onSeriesClick(entry.id) })
            }
        }
    } else {
        seriesName?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AuthorDurationRow(
    authors: List<com.samwise.unshelved.core.model.Author>,
    authorName: String?,
    duration: Double,
    publishedYear: String?,
    onAuthorClick: (String, String) -> Unit,
) {
    val otherInfoParts = buildList {
        if (duration > 0) add(duration.toHoursMinutes())
        publishedYear?.let { add(it) }
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (authors.isNotEmpty()) {
            authors.forEachIndexed { index, author ->
                if (index > 0) Text(", ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(author.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onAuthorClick(author.id, author.name) })
            }
            if (otherInfoParts.isNotEmpty()) Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            authorName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (otherInfoParts.isNotEmpty()) Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(otherInfoParts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionButtonsRow(
    hasProgress: Boolean,
    progressFraction: Float,
    isFinished: Boolean,
    downloadButtonState: DownloadButtonState,
    onPlay: () -> Unit,
    onToggleFinished: () -> Unit,
    onDownloadAction: () -> Unit,
    onShowChapters: () -> Unit,
    onShowInfo: () -> Unit,
) {
    val buttonSize = 48.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayButton(
            hasProgress = hasProgress,
            progressFraction = progressFraction,
            buttonSize = buttonSize,
            onClick = onPlay,
        )

        FilledTonalIconButton(
            onClick = onToggleFinished,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = if (isFinished) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                contentDescription = if (isFinished) stringResource(R.string.mark_unfinished) else stringResource(R.string.mark_finished),
                tint = if (isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        DownloadButton(
            state = downloadButtonState,
            onClick = onDownloadAction,
            buttonSize = buttonSize,
        )

        FilledTonalIconButton(
            onClick = onShowChapters,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = stringResource(R.string.chapters))
        }

        FilledTonalIconButton(
            onClick = onShowInfo,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.info))
        }
    }
}

@Composable
private fun PlayButton(
    hasProgress: Boolean,
    progressFraction: Float,
    buttonSize: Dp,
    onClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(buttonSize)) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(buttonSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
        }
        if (hasProgress) {
            CircularProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.size(buttonSize),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        }
    }
}

@Composable
private fun DownloadButton(
    state: DownloadButtonState,
    onClick: () -> Unit,
    buttonSize: Dp,
) {
    val isInProgress = state is DownloadButtonState.Downloading || state is DownloadButtonState.Queued

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(buttonSize)) {
        when (state) {
            is DownloadButtonState.Downloading -> {
                if (state.progress != null) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(buttonSize),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(buttonSize),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            is DownloadButtonState.Queued -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(buttonSize),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            else -> {}
        }

        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(if (isInProgress) 36.dp else buttonSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = when (state) {
                    is DownloadButtonState.Completed -> Icons.Default.DownloadDone
                    is DownloadButtonState.Downloading,
                    is DownloadButtonState.Queued -> Icons.Default.Downloading
                    is DownloadButtonState.NotDownloaded,
                    is DownloadButtonState.Failed -> Icons.Default.Download
                },
                contentDescription = stringResource(R.string.download),
            )
        }
    }
}

@Composable
private fun DescriptionCard(
    description: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            val annotated = rememberParsedHtmlDescription(description)
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(),
            )
            if (!expanded) {
                Text(
                    text = stringResource(R.string.read_more),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = true }.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberParsedHtmlDescription(html: String): AnnotatedString {
    val spanned = remember(html) {
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
    }
    val trimmed = remember(spanned) {
        val s = spanned.toString()
        val start = s.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val end = s.indexOfLast { !it.isWhitespace() }.coerceAtLeast(0) + 1
        spanned.subSequence(start, end) as android.text.Spanned
    }
    return remember(trimmed) { buildAnnotatedStringFromSpanned(trimmed) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoBottomSheet(
    item: LibraryItem,
    progress: MediaProgress?,
    onDismiss: () -> Unit,
    onAuthorClick: (String, String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onNarratorClick: (String) -> Unit,
    onGenreClick: (String) -> Unit,
) {
    val meta = item.media.metadata

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(stringResource(R.string.details), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))

            if (meta.authors.isNotEmpty()) {
                MetadataLinksRow(stringResource(R.string.author), meta.authors.map { it.name to { onAuthorClick(it.id, it.name) } })
            } else {
                meta.authorName?.let { MetadataRow(stringResource(R.string.author), it) }
            }

            if (meta.seriesEntries.isNotEmpty()) {
                MetadataLinksRow(stringResource(R.string.series), meta.seriesEntries.map { entry ->
                    val label = entry.sequence?.let { "${entry.name} #$it" } ?: entry.name
                    label to { onSeriesClick(entry.id) }
                })
            } else {
                meta.seriesName?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.series), it) }
            }

            meta.narratorName?.takeIf { it.isNotBlank() }?.let { narrators ->
                val names = narrators.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                MetadataLinksRow(stringResource(R.string.narrator), names.map { name -> name to { onNarratorClick(name) } })
            }

            if (meta.genres.isNotEmpty()) {
                MetadataLinksRow(stringResource(R.string.genre), meta.genres.map { genre -> genre to { onGenreClick(genre) } })
            }

            meta.publishedYear?.let { MetadataRow(stringResource(R.string.year), it) }
            if (item.media.duration > 0) MetadataRow(stringResource(R.string.duration), item.media.duration.toHoursMinutes())
            meta.language?.takeIf { it.isNotBlank() }?.let { MetadataRow(stringResource(R.string.language), it) }
            progress?.let {
                val progressText = when {
                    it.isFinished -> stringResource(R.string.finished)
                    it.progress > 0f -> "${(it.progress * 100).toInt()}%"
                    else -> stringResource(R.string.not_started)
                }
                MetadataRow(stringResource(R.string.progress), progressText)
            }
            MetadataRow(stringResource(R.string.added), java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(item.addedAt)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersBottomSheet(
    chapters: List<Chapter>,
    onDismiss: () -> Unit,
    onChapterClick: (Double) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(R.string.chapters_count, chapters.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                items(chapters) { chapter ->
                    val chapterDuration = chapter.end - chapter.start
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterClick(chapter.start) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(chapter.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(chapter.start.toHhMmSs(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(chapterDuration.toHhMmSs(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetadataLinksRow(label: String, links: List<Pair<String, () -> Unit>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            links.forEachIndexed { _, (text, onClick) ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onClick),
                )
            }
        }
    }
}

internal fun formatProgress(progress: MediaProgress): String {
    return when {
        progress.isFinished -> "Finished"
        progress.progress > 0f -> "${(progress.progress * 100).toInt()}%"
        else -> "Not started"
    }
}
