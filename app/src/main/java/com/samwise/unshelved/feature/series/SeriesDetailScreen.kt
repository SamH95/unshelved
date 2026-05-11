package com.samwise.unshelved.feature.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samwise.unshelved.R
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.ui.BookCoverCard
import com.samwise.unshelved.core.ui.BookListItem
import com.samwise.unshelved.core.ui.LocalBottomPadding
import com.samwise.unshelved.core.ui.LocalTopPadding

@Composable
fun SeriesDetailScreen(
    onBookClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl = (viewModel.serverUrl.collectAsStateWithLifecycle().value ?: "")
    val progressMap by viewModel.progressCache.progressMap.collectAsStateWithLifecycle()
    val seriesId = viewModel.currentSeriesId

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state.error != null && state.books.isEmpty()) {
        ErrorState(error = state.error ?: "")
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = LocalTopPadding.current)) {
        SeriesHeader(
            name = state.seriesName,
            bookCount = state.books.size,
            isGridView = state.isGridView,
            onToggleView = viewModel::toggleViewMode,
        )

        if (state.isGridView) {
            SeriesBookGrid(
                books = state.books,
                serverUrl = serverUrl,
                progressMap = progressMap,
                seriesId = seriesId,
                onBookClick = onBookClick,
            )
        } else {
            SeriesBookList(
                books = state.books,
                serverUrl = serverUrl,
                progressMap = progressMap,
                seriesId = seriesId,
                onBookClick = onBookClick,
            )
        }
    }
}

@Composable
private fun ErrorState(error: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.could_not_load_series), style = MaterialTheme.typography.titleMedium)
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    name: String,
    bookCount: Int,
    isGridView: Boolean,
    onToggleView: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "$bookCount books",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleView) {
            Icon(
                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                contentDescription = if (isGridView) "Switch to list" else "Switch to grid",
            )
        }
    }
}

@Composable
private fun SeriesBookGrid(
    books: List<LibraryItem>,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    seriesId: String,
    onBookClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomPadding.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            BookCoverCard(
                item = book,
                serverUrl = serverUrl,
                progress = progressMap[book.id],
                onClick = { onBookClick(book.id) },
                seriesSequence = book.media.metadata.seriesEntries.find { it.id == seriesId }?.sequence,
            )
        }
    }
}

@Composable
private fun SeriesBookList(
    books: List<LibraryItem>,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    seriesId: String,
    onBookClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomPadding.current),
    ) {
        items(books, key = { it.id }) { book ->
            BookListItem(
                item = book,
                serverUrl = serverUrl,
                progress = progressMap[book.id],
                onClick = { onBookClick(book.id) },
                seriesSequence = book.media.metadata.seriesEntries.find { it.id == seriesId }?.sequence,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}
