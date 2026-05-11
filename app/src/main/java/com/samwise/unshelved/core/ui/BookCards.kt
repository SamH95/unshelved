package com.samwise.unshelved.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.samwise.unshelved.R
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress

@Composable
fun BookCoverCard(
    item: LibraryItem,
    serverUrl: String,
    progress: MediaProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seriesSequence: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium),
            ) {
                val coverUrl = "$serverUrl/api/items/${item.id}/cover"
                CoverImage(
                    coverUrl = coverUrl,
                    contentDescription = item.title,
                    itemId = item.id,
                    modifier = Modifier.fillMaxSize(),
                )

                if (seriesSequence != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = "#$seriesSequence",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    item.authorName?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (progress != null && (progress.progress > 0f || progress.isFinished)) {
                    Spacer(modifier = Modifier.width(4.dp))
                    ProgressBadge(progress)
                }
            }
        }
    }
}

@Composable
fun BookListItem(
    item: LibraryItem,
    serverUrl: String,
    progress: MediaProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seriesSequence: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            val coverUrl = "$serverUrl/api/items/${item.id}/cover"
            CoverImage(
                coverUrl = coverUrl,
                contentDescription = null,
                itemId = item.id,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.authorName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (progress != null && (progress.progress > 0f || progress.isFinished)) {
            Spacer(modifier = Modifier.width(8.dp))
            ProgressBadge(progress)
        }

        if (seriesSequence != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = "#$seriesSequence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressBadge(progress: MediaProgress) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (progress.isFinished) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Finished",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            CircularProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        }
    }
}

private val previewCovers = intArrayOf(
    R.drawable.preview_cover_warm,
    R.drawable.preview_cover_cool,
    R.drawable.preview_cover_earth,
)

@Composable
internal fun CoverImage(
    coverUrl: String,
    contentDescription: String?,
    itemId: String,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        val index = itemId.hashCode().mod(previewCovers.size)
        Image(
            painter = painterResource(previewCovers[index]),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        AsyncImage(
            model = coverUrl,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
