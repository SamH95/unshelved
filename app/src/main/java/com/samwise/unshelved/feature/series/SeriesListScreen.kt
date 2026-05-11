package com.samwise.unshelved.feature.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samwise.unshelved.core.model.Series
import com.samwise.unshelved.core.ui.AlphabetSlider
import com.samwise.unshelved.core.ui.LocalBottomPadding
import com.samwise.unshelved.core.ui.LocalTopPadding
import com.samwise.unshelved.core.ui.SeriesCoverStack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesListScreen(
    onSeriesClick: (String) -> Unit,
    viewModel: SeriesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl = (viewModel.serverUrl.collectAsStateWithLifecycle().value ?: "")
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            refreshScope.launch {
                isRefreshing = true
                viewModel.refresh()
                delay(1000)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().padding(top = LocalTopPadding.current),
    ) {
        val sortedSeries = remember(state.series) {
            state.series.sortedBy { it.name.uppercase() }
        }

        val activeLetters = remember(sortedSeries) {
            sortedSeries.mapNotNullTo(mutableSetOf()) {
                it.name.firstOrNull()?.uppercaseChar()?.takeIf { c -> c in 'A'..'Z' }
            }
        }

        val gridState = rememberLazyGridState()
        val scope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 240.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 16.dp, bottom = 16.dp + LocalBottomPadding.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sortedSeries, key = { it.id }) { series ->
                    SeriesGridCard(
                        series = series,
                        serverUrl = serverUrl,
                        onClick = { onSeriesClick(series.id) },
                    )
                }
            }

            AlphabetSlider(
                activeLetters = activeLetters,
                onLetterSelected = { letter ->
                    val index = sortedSeries.indexOfFirst {
                        val first = it.name.firstOrNull()?.uppercaseChar()
                        first != null && first >= letter
                    }
                    if (index >= 0) {
                        scope.launch { gridState.animateScrollToItem(index) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SeriesGridCard(
    series: Series,
    serverUrl: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
