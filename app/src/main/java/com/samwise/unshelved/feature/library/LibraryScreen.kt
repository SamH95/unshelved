package com.samwise.unshelved.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samwise.unshelved.R
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.ui.AlphabetSlider
import com.samwise.unshelved.core.ui.BookCoverCard
import com.samwise.unshelved.core.ui.BookListItem
import com.samwise.unshelved.core.ui.LocalBottomPadding
import com.samwise.unshelved.core.ui.LocalTopPadding
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onPodcastClick: (String) -> Unit = {},
    onAddPodcast: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl = (viewModel.serverUrl.collectAsStateWithLifecycle().value ?: "")
    val progressMap by viewModel.progressCache.progressMap.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize().padding(top = LocalTopPadding.current),
    ) {
        LibraryContent(
            state = state,
            serverUrl = serverUrl,
            progressMap = progressMap,
            onBookClick = onBookClick,
            onPodcastClick = onPodcastClick,
            onAddPodcast = onAddPodcast,
            onToggleViewMode = viewModel::toggleViewMode,
            onSetFilter = { a, g, n -> viewModel.setFilter(author = a, genre = g, narrator = n) },
        )
    }
}

@Composable
internal fun LibraryContent(
    state: LibraryState,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    onBookClick: (String) -> Unit,
    onPodcastClick: (String) -> Unit = {},
    onAddPodcast: () -> Unit = {},
    onToggleViewMode: () -> Unit = {},
    onSetFilter: (FilterAuthor?, String?, String?) -> Unit = { _, _, _ -> },
) {
    val isPodcast = state.isPodcastLibrary
    val onItemClick: (String) -> Unit = if (isPodcast) onPodcastClick else onBookClick

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isPodcast && state.hasFilters) {
            FilterRow(
                authors = state.filterAuthors,
                genres = state.filterGenres,
                narrators = state.filterNarrators,
                selectedAuthor = state.selectedAuthor,
                selectedGenre = state.selectedGenre,
                selectedNarrator = state.selectedNarrator,
                onAuthorSelected = { onSetFilter(it, state.selectedGenre, state.selectedNarrator) },
                onGenreSelected = { onSetFilter(state.selectedAuthor, it, state.selectedNarrator) },
                onNarratorSelected = { onSetFilter(state.selectedAuthor, state.selectedGenre, it) },
                onClearFilters = { onSetFilter(null, null, null) },
            )
        }

        ViewToggleRow(
            total = state.total,
            isGridView = state.isGridView,
            onToggle = onToggleViewMode,
            isPodcast = isPodcast,
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val sortedItems = remember(state.items) {
            state.items.sortedBy {
                val ignorePrefix = if (it.isPodcast) it.podcastMedia?.metadata?.titleIgnorePrefix
                    else it.media.metadata.titleIgnorePrefix
                (ignorePrefix ?: it.title).uppercase()
            }
        }

        val activeLetters = remember(sortedItems) {
            sortedItems.mapNotNullTo(mutableSetOf()) {
                val ignorePrefix = if (it.isPodcast) it.podcastMedia?.metadata?.titleIgnorePrefix
                    else it.media.metadata.titleIgnorePrefix
                (ignorePrefix ?: it.title)
                    .firstOrNull()?.uppercaseChar()?.takeIf { c -> c in 'A'..'Z' }
            }
        }

        val gridState = rememberLazyGridState()
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        val scrollToLetter: (Char) -> Unit = remember(sortedItems, state.isGridView) {
            { letter: Char ->
                val index = sortedItems.indexOfFirst {
                    val ignorePrefix = if (it.isPodcast) it.podcastMedia?.metadata?.titleIgnorePrefix
                        else it.media.metadata.titleIgnorePrefix
                    val first = (ignorePrefix ?: it.title)
                        .firstOrNull()?.uppercaseChar()
                    first != null && first >= letter
                }
                if (index >= 0) {
                    scope.launch {
                        if (state.isGridView) gridState.animateScrollToItem(index)
                        else listState.animateScrollToItem(index)
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isGridView) {
                BookGrid(
                    items = sortedItems,
                    serverUrl = serverUrl,
                    progressMap = progressMap,
                    gridState = gridState,
                    onBookClick = onItemClick,
                )
            } else {
                BookList(
                    items = sortedItems,
                    serverUrl = serverUrl,
                    progressMap = progressMap,
                    listState = listState,
                    onBookClick = onItemClick,
                )
            }

            AlphabetSlider(
                activeLetters = activeLetters,
                onLetterSelected = scrollToLetter,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
            )

            if (isPodcast) {
                FloatingActionButton(
                    onClick = onAddPodcast,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp + LocalBottomPadding.current),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_podcast))
                }
            }
        }
    }
}

@Composable
private fun ViewToggleRow(
    total: Int,
    isGridView: Boolean,
    onToggle: () -> Unit,
    isPodcast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isPodcast) "$total podcasts" else "$total books",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                contentDescription = if (isGridView) stringResource(R.string.switch_to_list) else stringResource(R.string.switch_to_grid),
            )
        }
    }
}

@Composable
private fun BookGrid(
    items: List<LibraryItem>,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    gridState: LazyGridState,
    onBookClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 16.dp, bottom = 16.dp + LocalBottomPadding.current),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { item ->
            BookCoverCard(
                item = item,
                serverUrl = serverUrl,
                progress = if (item.isPodcast) null else progressMap[item.id],
                onClick = { onBookClick(item.id) },
            )
        }
    }
}

@Composable
private fun BookList(
    items: List<LibraryItem>,
    serverUrl: String,
    progressMap: Map<String, MediaProgress>,
    listState: LazyListState,
    onBookClick: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(end = 24.dp),
        contentPadding = PaddingValues(bottom = LocalBottomPadding.current),
    ) {
        items(items, key = { it.id }) { item ->
            BookListItem(
                item = item,
                serverUrl = serverUrl,
                progress = if (item.isPodcast) null else progressMap[item.id],
                onClick = { onBookClick(item.id) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    authors: List<FilterAuthor>,
    genres: List<String>,
    narrators: List<String>,
    selectedAuthor: FilterAuthor?,
    selectedGenre: String?,
    selectedNarrator: String?,
    onAuthorSelected: (FilterAuthor?) -> Unit,
    onGenreSelected: (String?) -> Unit,
    onNarratorSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val hasFilter = selectedAuthor != null || selectedGenre != null || selectedNarrator != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasFilter) {
            FilterChip(
                selected = true,
                onClick = onClearFilters,
                label = { Text(stringResource(R.string.clear)) },
            )
        }

        AuthorFilterDropdown(
            label = stringResource(R.string.author),
            options = authors,
            selected = selectedAuthor,
            onSelect = onAuthorSelected,
        )

        FilterPickerChip(
            label = stringResource(R.string.genre),
            options = genres,
            selected = selectedGenre,
            onSelect = onGenreSelected,
        )

        if (narrators.isNotEmpty()) {
            FilterPickerChip(
                label = stringResource(R.string.narrator),
                options = narrators,
                selected = selectedNarrator,
                onSelect = onNarratorSelected,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AuthorFilterDropdown(
    label: String,
    options: List<FilterAuthor>,
    selected: FilterAuthor?,
    onSelect: (FilterAuthor?) -> Unit,
) {
    FilterPickerChip(
        label = label,
        options = options.map { it.name },
        selected = selected?.name,
        onSelect = { name -> onSelect(if (name == null) null else options.first { it.name == name }) },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FilterPickerChip(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    FilterChip(
        selected = selected != null,
        onClick = { showSheet = true; query = "" },
        label = { Text(text = selected?.take(15) ?: label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            val filtered = remember(query, options) {
                if (query.isBlank()) options else options.filter { it.contains(query, ignoreCase = true) }
            }
            Column(modifier = Modifier.fillMaxHeight(0.85f)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_hint, label)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, stringResource(R.string.clear)) }
                    },
                    singleLine = true,
                )
                if (selected != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.clear_filter), color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { onSelect(null); showSheet = false },
                    )
                    HorizontalDivider()
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { option ->
                        ListItem(
                            headlineContent = { Text(option, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal) },
                            trailingContent = if (option == selected) ({ Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                            modifier = Modifier.clickable { onSelect(option); showSheet = false },
                        )
                    }
                }
            }
        }
    }
}
