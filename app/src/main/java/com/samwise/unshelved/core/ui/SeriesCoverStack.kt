package com.samwise.unshelved.core.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

@Composable
fun SeriesCoverStack(
    bookIds: List<String>,
    serverUrl: String,
    fallbackText: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(2f)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bookIds.isEmpty() -> EmptyFallback(fallbackText)
            bookIds.size == 1 -> SingleCover(bookIds.first(), serverUrl, fallbackText)
            bookIds.size == 2 -> DualCover(bookIds, serverUrl)
            else -> StackedCovers(bookIds, serverUrl)
        }
    }
}

@Composable
private fun EmptyFallback(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(2).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SingleCover(bookId: String, serverUrl: String, fallbackText: String) {
    val fallbackColor = MaterialTheme.colorScheme.secondaryContainer
    var bgColor by remember { mutableStateOf(fallbackColor) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("$serverUrl/api/items/$bookId/cover")
                .allowHardware(false)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    try {
                        val bitmap = state.result.image.toBitmap()
                        Palette.from(bitmap).generate { palette ->
                            palette?.let {
                                val swatch = it.dominantSwatch ?: it.mutedSwatch ?: it.darkMutedSwatch
                                swatch?.let { s -> bgColor = Color(s.rgb) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("SeriesCoverStack", "Palette extraction failed", e)
                    }
                }
            },
        )
    }
}

@Composable
private fun DualCover(bookIds: List<String>, serverUrl: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        bookIds.take(2).forEachIndexed { index, bookId ->
            AsyncImage(
                model = "$serverUrl/api/items/$bookId/cover",
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .align(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun StackedCovers(bookIds: List<String>, serverUrl: String) {
    val visible = bookIds.take(10)
    val count = visible.size
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(0) }
    var containerHeightPx by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .onSizeChanged {
                containerWidthPx = it.width
                containerHeightPx = it.height
            },
    ) {
        if (containerWidthPx > 0 && containerHeightPx > 0) {
            val coverWidthPx = containerHeightPx
            val totalGap = containerWidthPx - coverWidthPx
            val stepPx = if (count > 1) totalGap / (count - 1) else 0

            visible.asReversed().forEachIndexed { reversedIndex, bookId ->
                val originalIndex = count - 1 - reversedIndex
                val offsetXDp = with(density) { (originalIndex * stepPx).toDp() }

                AsyncImage(
                    model = "$serverUrl/api/items/$bookId/cover",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .absoluteOffset(x = offsetXDp)
                        .zIndex(reversedIndex.toFloat())
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
