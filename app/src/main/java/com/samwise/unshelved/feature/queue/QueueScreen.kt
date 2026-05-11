package com.samwise.unshelved.feature.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.samwise.unshelved.R
import com.samwise.unshelved.core.model.toHoursMinutes
import com.samwise.unshelved.core.ui.LocalBottomPadding
import com.samwise.unshelved.core.ui.LocalTopPadding
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    onPlayItem: (libraryItemId: String, episodeId: String?) -> Unit = { _, _ -> },
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val items by viewModel.queue.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var localItems by remember(items) { mutableStateOf(items) }

    val lazyListState = rememberLazyListState()
    val hasNowPlaying = nowPlaying != null
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = localItems.indexOfFirst { it.id == from.key }
        val toIdx = localItems.indexOfFirst { it.id == to.key }
        if (fromIdx >= 0 && toIdx >= 0) {
            localItems = localItems.toMutableList().apply {
                add(toIdx, removeAt(fromIdx))
            }
            viewModel.reorder(localItems)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = LocalTopPadding.current),
    ) {
        if (!hasNowPlaying && localItems.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.up_next_count, 0),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 16.dp + LocalBottomPadding.current),
            ) {
                if (hasNowPlaying) {
                    val np = nowPlaying!!
                    item(key = "now_playing_header") {
                        Text(
                            text = stringResource(R.string.now_playing),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    item(key = "now_playing_item") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayItem(np.libraryItemId, np.episodeId) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AsyncImage(
                                model = "$serverUrl/api/items/${np.libraryItemId}/cover",
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = np.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    np.author?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (np.duration > 0) {
                                        Text(
                                            text = np.duration.toHoursMinutes(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    item(key = "up_next_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.up_next_count, localItems.size),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (localItems.isNotEmpty()) {
                                TextButton(onClick = viewModel::clear) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.clear))
                                }
                            }
                        }
                    }
                } else {
                    item(key = "up_next_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.up_next_count, localItems.size),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (localItems.isNotEmpty()) {
                                TextButton(onClick = viewModel::clear) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.clear))
                                }
                            }
                        }
                    }
                }

                items(localItems, key = { it.id }) { item ->
                    ReorderableItem(reorderableState, key = item.id) { isDragging ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onPlayItem(item.libraryItemId, item.episodeId) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.reorder),
                                modifier = Modifier
                                    .size(24.dp)
                                    .draggableHandle(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            AsyncImage(
                                model = "$serverUrl/api/items/${item.libraryItemId}/cover",
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item.author?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (item.duration > 0) {
                                        Text(
                                            text = item.duration.toHoursMinutes(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { viewModel.remove(item.id) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}
