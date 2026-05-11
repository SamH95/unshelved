package com.samwise.unshelved.feature.player

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.text.Html
import android.util.Log
import android.view.ContextThemeWrapper
import java.io.File
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouteSelector
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.samwise.unshelved.R
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.model.Chapter
import com.samwise.unshelved.core.model.PlaybackSession
import com.samwise.unshelved.core.model.toHhMmSs
import com.samwise.unshelved.core.ui.buildAnnotatedStringFromSpanned
import com.samwise.unshelved.feature.queue.QueueSheet
import com.samwise.unshelved.service.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private class DragActiveRef(var active: Boolean = false)

private data class CoverGeometry(
    val x: Dp, val y: Dp, val size: Dp, val cornerRadius: Dp,
    val pfIndicatorPhase1End: Float,
    val coverPfPhase3Start: Float,
)

private fun computeCoverGeometry(
    pf: Float,
    f: Float,
    containerWidthPx: Int,
    containerHeightPx: Int,
    effectiveBottomOffset: Int,
    statusBarTopDp: Dp,
    miniPlayerTopDp: Dp,
    cardTopDp: Dp,
    dragHandleHeight: Dp,
    density: androidx.compose.ui.unit.Density,
): CoverGeometry {
    val coverHPad = 24.dp
    val coverTopPadding = statusBarTopDp + dragHandleHeight / 2 + 2.dp + coverHPad
    val pfIndicatorPhase1End = (1f - (statusBarTopDp / miniPlayerTopDp).coerceIn(0f, 1f))
    val coverCSFull = with(density) { containerWidthPx.toDp() } - coverHPad * 2
    val coverMiniCS = 40.dp
    val coverSp2 = ((f - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val coverFCS = lerpDp(coverCSFull, 48.dp, coverSp2)
    val coverFCX = lerpDp(coverHPad, 16.dp, coverSp2)
    val coverChapterMiniY = statusBarTopDp + 16.dp
    val coverMBY = with(density) { containerHeightPx.toDp() - effectiveBottomOffset.toDp() } - coverMiniCS - 12.dp
    val coverPhase3CardTop = (coverMBY + coverMiniCS - coverTopPadding - coverCSFull).coerceAtLeast(0.dp)
    val coverPfPhase3Start = (1f - (coverPhase3CardTop / miniPlayerTopDp).coerceIn(0f, 1f))
    val coverCatchUpDp = statusBarTopDp * 2f
    val pfCoverRideAlong = (1f - (coverCatchUpDp / miniPlayerTopDp).coerceIn(0f, 1f))

    val coverCY: Dp
    val coverCS: Dp
    val coverCX: Dp
    val coverCR: Dp
    when {
        pf >= coverPfPhase3Start -> {
            val fullCY = if (pf > pfCoverRideAlong) {
                cardTopDp * 0.5f + coverTopPadding
            } else {
                cardTopDp - statusBarTopDp + coverTopPadding
            }
            coverCY = lerpDp(fullCY, coverChapterMiniY, coverSp2)
            coverCS = coverFCS; coverCX = coverFCX
            coverCR = lerpDp(12.dp, 6.dp, coverSp2)
        }
        else -> {
            val p3 = if (coverPfPhase3Start > 0f) (pf / coverPfPhase3Start).coerceIn(0f, 1f) else 0f
            val phase3FullCY = if (coverPfPhase3Start > pfCoverRideAlong) {
                coverPhase3CardTop * 0.5f + coverTopPadding
            } else {
                coverPhase3CardTop - statusBarTopDp + coverTopPadding
            }
            coverCY = lerpDp(coverMBY, phase3FullCY, p3)
            coverCS = lerpDp(coverMiniCS, coverCSFull, p3)
            coverCX = lerpDp(16.dp, coverFCX, p3)
            coverCR = lerpDp(4.dp, 12.dp, p3)
        }
    }
    return CoverGeometry(coverCX, coverCY, coverCS, coverCR, pfIndicatorPhase1End, coverPfPhase3Start)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    onDismiss: () -> Unit,
    onExpand: (() -> Unit)? = null,
    autoExpand: Boolean = false,
    onAutoExpandConsumed: () -> Unit = {},
    bottomOffset: Int = -1,
    playerFractionAnim: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null,
    onAuthorClick: ((authorId: String, authorName: String) -> Unit)? = null,
    onSeriesClick: ((seriesId: String) -> Unit)? = null,
    onDetailClick: ((itemId: String) -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val jumpBack by viewModel.jumpBackSeconds.collectAsStateWithLifecycle()
    val jumpFwd by viewModel.jumpForwardSeconds.collectAsStateWithLifecycle()

    val currentItemId = state.session?.libraryItemId
    val dlState by remember(currentItemId) {
        if (currentItemId != null) viewModel.downloadRepository.observeDownload(currentItemId)
        else flowOf(null)
    }.collectAsStateWithLifecycle(null)

    val isDark = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surface
    var dominantColor by remember { mutableStateOf(surfaceColor) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBarBottomPx = with(density) { WindowInsets.navigationBars.getBottom(this) }
    val navBarBottomDp = with(density) { navBarBottomPx.toDp() }
    val effectiveBottomOffset = if (bottomOffset >= 0) bottomOffset else navBarBottomPx

    val tintedColor = remember(dominantColor, isDark) {
        if (isDark) lerp(Color(0xFF121212), dominantColor, 0.3f)
        else lerp(Color.White, dominantColor, 0.18f)
    }

    val bgBrush = remember(tintedColor, surfaceColor) {
        Brush.verticalGradient(listOf(tintedColor, surfaceColor))
    }

    val miniPlayerBg = tintedColor

    // Accent color derived from cover: vibrant enough to be a highlight, readable in light+dark
    val accentColor = remember(dominantColor, isDark) {
        val hsl = FloatArray(3)
        AndroidColor.colorToHSV(
            AndroidColor.argb(
                (dominantColor.alpha * 255).toInt(),
                (dominantColor.red * 255).toInt(),
                (dominantColor.green * 255).toInt(),
                (dominantColor.blue * 255).toInt(),
            ),
            hsl,
        )
        // Cap saturation so vivid covers don't produce eye-straining accents
        hsl[1] = if (isDark) hsl[1].coerceIn(0.20f, 0.40f) else hsl[1].coerceIn(0.30f, 0.55f)
        // In dark mode: push lightness very high so accents are always readable
        hsl[2] = if (isDark) hsl[2].coerceAtLeast(0.92f) else hsl[2].coerceIn(0.30f, 0.45f)
        val argb = AndroidColor.HSVToColor(hsl)
        Color(argb)
    }

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            viewModel.updateCurrentTime()
            delay(1000)
        }
    }

    val session = state.session
    val duration = session?.duration ?: 1.0
    val currentSeconds = state.currentTimeMs / 1000.0
    var sliderValue by remember { mutableFloatStateOf((currentSeconds / duration).toFloat().coerceIn(0f, 1f)) }
    var isSliderDragging by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showEpisodeInfoSheet by remember { mutableStateOf(false) }
    if (!isSliderDragging) sliderValue = (currentSeconds / duration).toFloat().coerceIn(0f, 1f)
    val progressValue = sliderValue
    val trackHeight by animateDpAsState(if (isSliderDragging) 6.dp else 3.dp, label = "trackHeight")
    val accentBg = accentColor.copy(alpha = if (isDark) 0.20f else 0.15f)
    val accentFg = accentColor

    val chapters = session?.chapters ?: emptyList()
    val scope = rememberCoroutineScope()
    // chapterDragValue is the source of truth for the chapter panel position.
    // Written directly (no coroutines) during drag for zero-latency tracking.
    // settleChapters() launches an animation that writes back into it each frame.
    var chapterDragValue by remember { mutableFloatStateOf(0f) }
    // True while the top-bar pointerInput handler owns the gesture (prevents nested scroll double-applying upward delta)
    val topBarDraggingRef = remember { DragActiveRef() }
    // True while the list's nested scroll is driving panel close (prevents list scrolling while panel moves)
    val listDraggingRef = remember { DragActiveRef() }
    val settleJobRef = remember { object { var job: Job? = null } }
    fun snapChapterTo(target: Float) {
        settleJobRef.job?.cancel()
        chapterDragValue = target.coerceIn(0f, 1f)
    }
    fun animateChapterTo(target: Float) {
        settleJobRef.job?.cancel()
        val start = chapterDragValue
        settleJobRef.job = scope.launch {
            val anim = Animatable(start)
            anim.animateTo(target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) {
                chapterDragValue = value
            }
        }
    }
    fun settleChapters() = animateChapterTo(if (chapterDragValue < 0.5f) 0f else 1f)
    fun openChapters() = animateChapterTo(1f)
    fun closeChapters() = animateChapterTo(0f)
    val internalPlayerFractionAnim = remember { Animatable(1f) }
    val actualPlayerFractionAnim = playerFractionAnim ?: internalPlayerFractionAnim
    val f = chapterDragValue
    val pf = actualPlayerFractionAnim.value
    var containerHeightPx by remember { mutableIntStateOf(1) }
    var containerWidthPx by remember { mutableIntStateOf(1) }

    // React to external expand signal
    LaunchedEffect(autoExpand) {
        if (autoExpand) {
            actualPlayerFractionAnim.animateTo(1f)
            onAutoExpandConsumed()
        }
    }

    val chapterListState = rememberLazyListState()

    // Scroll to current chapter when chapter changes, and when panel fully closes (so it's
    // ready at the right position next time it opens). Never scroll while the panel is open.
    LaunchedEffect(state.currentChapter) {
        if (chapters.isNotEmpty() && chapterDragValue < 0.1f) {
            val idx = chapters.indexOfFirst { it == state.currentChapter }
            if (idx >= 0) chapterListState.scrollToItem((idx - 2).coerceAtLeast(0))
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { chapterDragValue < 0.1f }.collect { closed ->
            if (closed && chapters.isNotEmpty()) {
                val idx = chapters.indexOfFirst { it == state.currentChapter }
                if (idx >= 0) chapterListState.scrollToItem((idx - 2).coerceAtLeast(0))
            }
        }
    }

    fun expandPlayer() { scope.launch { actualPlayerFractionAnim.animateTo(1f) } }
    fun dismissPlayer() { scope.launch { actualPlayerFractionAnim.animateTo(0f); onDismiss() } }

    val chapterTravelPxTopLevel = remember { mutableFloatStateOf(1f) }

    val reducedSlopViewConfig = remember {
        object : ViewConfiguration {
            override val longPressTimeoutMillis: Long = 500L
            override val doubleTapTimeoutMillis: Long = 300L
            override val doubleTapMinTimeMillis: Long = 40L
            override val touchSlop: Float = 8f
            override val minimumTouchTargetSize: DpSize = DpSize(48.dp, 48.dp)
            override val handwritingSlop: Float get() = touchSlop
            override val handwritingGestureLineMargin: Float = 8f
        }
    }

    // Card slide offset — computed at composable scope so it's accessible everywhere inside
    val dragHandleHeight = 24.dp
    val miniPlayerTopPx = containerHeightPx - effectiveBottomOffset - with(density) { 64.dp.roundToPx() }
    val cardOffsetPx = (miniPlayerTopPx * (1f - pf)).toInt().coerceAtLeast(0)
    val miniPlayerTopDp = with(density) { miniPlayerTopPx.toFloat().toDp() }
    val cardTopDp = miniPlayerTopDp * (1f - pf)

    val coverGeo = computeCoverGeometry(pf, f, containerWidthPx, containerHeightPx, effectiveBottomOffset, statusBarTopDp, miniPlayerTopDp, cardTopDp, dragHandleHeight, density)
    val coverCX = coverGeo.x; val coverCY = coverGeo.y; val coverCS = coverGeo.size; val coverCR = coverGeo.cornerRadius
    val pfIndicatorPhase1End = coverGeo.pfIndicatorPhase1End
    val coverPfPhase3Start = coverGeo.coverPfPhase3Start

    CompositionLocalProvider(androidx.compose.ui.platform.LocalViewConfiguration provides reducedSlopViewConfig) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.coerceAtLeast(1); containerWidthPx = it.width.coerceAtLeast(1) }
            .then(if (pf > 0.01f) Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    if (actualPlayerFractionAnim.value < 0.01f) return@awaitEachGesture
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastY = down.position.y
                    var lastX = down.position.x
                    var totalDragPx = 0f
                    var totalDragXPx = 0f
                    val recentDeltas = ArrayDeque<Float>(6)
                    val startedWithChaptersOpen = chapterDragValue > 0.5f
                    // Set to true once we've committed this gesture to opening/driving chapters
                    var drivingChapters = false

                    // Wait for initial slop before committing; bail if gesture is primarily horizontal
                    var slopExceeded = false
                    outer@ while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - lastY
                        val dx = change.position.x - lastX
                        lastY = change.position.y
                        lastX = change.position.x
                        if (!slopExceeded) {
                            totalDragPx += dy
                            totalDragXPx += dx
                            val totalMoved = kotlin.math.sqrt(totalDragPx * totalDragPx + totalDragXPx * totalDragXPx)
                            if (totalMoved > viewConfiguration.touchSlop) {
                                // Horizontal gesture — let chip row / progress bar handle it
                                if (kotlin.math.abs(totalDragXPx) > kotlin.math.abs(totalDragPx)) return@awaitEachGesture
                                slopExceeded = true
                            } else continue
                            // Fall through: first post-slop event; totalDragPx already includes dy
                        } else {
                            totalDragPx += dy
                        }
                        when {
                            startedWithChaptersOpen && !drivingChapters && !(dy < 0f && chapterDragValue < 0.99f) -> {
                                // Chapter panel was open at gesture start — let NestedScrollConnection handle
                            }
                            drivingChapters || (startedWithChaptersOpen && dy < 0f && chapterDragValue < 0.99f) || (dy < 0f && chapters.isNotEmpty() && actualPlayerFractionAnim.value > 0.99f && !topBarDraggingRef.active) -> {
                                // Swipe up anywhere on the player area → open chapters; keep driving once started
                                drivingChapters = true
                                topBarDraggingRef.active = false
                                change.consume()
                                if (recentDeltas.size >= 6) recentDeltas.removeFirst()
                                recentDeltas.addLast(dy)
                                val travel = chapterTravelPxTopLevel.floatValue.coerceAtLeast(1f)
                                snapChapterTo(chapterDragValue + (-dy / travel))
                            }
                            chapterDragValue < 0.01f -> {
                                change.consume()
                                if (recentDeltas.size >= 6) recentDeltas.removeFirst()
                                recentDeltas.addLast(dy)
                                scope.launch {
                                    actualPlayerFractionAnim.snapTo((actualPlayerFractionAnim.value + (-dy / containerHeightPx)).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }

                    // Fling / settle decision — skip entirely if no real drag (pure tap)
                    topBarDraggingRef.active = false
                    val recentVelocity = recentDeltas.takeLast(4).sum()
                    if (recentDeltas.isNotEmpty()) scope.launch {
                        when {
                            startedWithChaptersOpen && !drivingChapters -> {
                                // Chapter panel fling handled by NestedScrollConnection
                            }
                            drivingChapters || chapterDragValue > 0.01f -> {
                                if (recentVelocity < -2f || chapterDragValue > 0.5f) openChapters()
                                else closeChapters()
                            }
                            actualPlayerFractionAnim.value < 0.99f -> {
                                if (recentVelocity > 2f || actualPlayerFractionAnim.value < 0.5f) dismissPlayer()
                                else expandPlayer()
                            }
                            (recentVelocity < -2f || totalDragPx < -5f) && chapters.isNotEmpty() -> openChapters()
                            recentVelocity > 2f || totalDragPx > 30f -> dismissPlayer()
                        }
                    }
                }
            } else Modifier),
    ) {
        val miniCoverSize = 48.dp
        val miniCoverPadding = 16.dp
        val miniPlayerHeight = 64.dp
        val shrinkPhase = ((f - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val controlsFadeOut = ((1f - f * 3f) * (pf * 3f).coerceAtMost(1f)).coerceIn(0f, 1f)
        val miniPlayerFadeIn = ((f - 0.7f) / 0.3f).coerceIn(0f, 1f)

        val chapterListBgSolid = remember(dominantColor, isDark) {
            if (isDark) lerp(Color(0xFF252525), dominantColor, 0.15f)
            else lerp(Color.White, dominantColor, 0.06f)
        }

        // Card background — slides up from mini-player top edge.
        // At pf=0, top of card aligns with top of mini-player row. Rounded top corners that
        // flatten to 0 as the card expands to full screen.
        val cardCornerRadius = lerpDp(0.dp, 16.dp, (pf * 4f).coerceIn(0f, 1f)).let {
            // Flatten back to 0 as the card reaches full screen
            lerpDp(it, 0.dp, ((pf - 0.8f) / 0.2f).coerceIn(0f, 1f))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, cardOffsetPx) }
                .clip(RoundedCornerShape(topStart = cardCornerRadius, topEnd = cardCornerRadius))
                .background(lerp(MaterialTheme.colorScheme.surfaceContainerHighest, tintedColor, pf))
                .background(Brush.verticalGradient(listOf(Color.Transparent, lerp(Color.Transparent, surfaceColor, pf))))
                .background(miniPlayerBg.copy(alpha = shrinkPhase))
        )

        if (pf < 1f) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = with(density) { effectiveBottomOffset.toDp() })
                    .alpha((1f - pf * 2f).coerceIn(0f, 1f))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { expandPlayer() }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var lastY = down.position.y
                            val recentDeltas = ArrayDeque<Float>(6)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val dy = change.position.y - lastY
                                lastY = change.position.y
                                change.consume()
                                if (recentDeltas.size >= 6) recentDeltas.removeFirst()
                                recentDeltas.addLast(dy)
                                if (dy != 0f) {
                                    scope.launch {
                                        actualPlayerFractionAnim.snapTo((actualPlayerFractionAnim.value + (-dy / containerHeightPx)).coerceIn(0f, 1f))
                                    }
                                }
                            }
                            val recentVelocity = recentDeltas.takeLast(4).sum()
                            if (recentVelocity < -2f || actualPlayerFractionAnim.value > 0.5f) expandPlayer()
                            else dismissPlayer()
                        }
                    }
                    .background(Color.Transparent)
                    .padding(start = 68.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session?.displayTitle ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(session?.displayAuthor ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() }) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                }
                IconButton(onClick = { viewModel.seekForward() }) {
                    Icon(forwardIcon(jumpFwd), stringResource(R.string.jump_forward))
                }
            }
            } // CompositionLocalProvider
        } // if pf < 1f

        // Main content area — offset by the same amount as the card background
        if (pf > 0f) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {

        // Drag handle pill: stationary until card top reaches it, then rides the card.
        // Fades out when cover phase 2 starts (card has reached the cover).
        val handleHeight = lerpDp(dragHandleHeight, 0.dp, f)
        val indicatorOffsetPx = if (pf >= pfIndicatorPhase1End) {
            // Phase 1: stationary at full-player screen position (no extra offset)
            0
        } else {
            // Phase 2: rides the card — offset is how far the card has gone past the indicator threshold
            val pfAtThreshold = pfIndicatorPhase1End
            val extraTravel = miniPlayerTopDp * (pfAtThreshold - pf)
            with(density) { extraTravel.roundToPx() }
        }
        val indicatorFade = ((pf - (coverPfPhase3Start - 0.1f)) / 0.1f).coerceIn(0f, 1f) * (1f - f * 5f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, indicatorOffsetPx) }
                .statusBarsPadding()
                .height(handleHeight)
                .alpha(indicatorFade),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, cardOffsetPx) }
            .alpha(((pf - 0.7f) / 0.3f).coerceIn(0f, 1f))
        ) {
            // Spacer to reserve space for drag handle above content — height driven by pf (player expand), not f (chapters)
            val handleHeight2 = lerpDp(dragHandleHeight, 0.dp, pf)
            Spacer(modifier = Modifier.statusBarsPadding().height(handleHeight2))

            // Main content area
            var contentHeightPx by remember { mutableIntStateOf(1) }
            val chapterTravelPxRef = remember { mutableFloatStateOf(1f) }
            // Refs so pointerInput(Unit) can read current values without restarting
            val fRef = remember { mutableFloatStateOf(f) }
            fRef.floatValue = f
            val contentHeightPxRef = remember { mutableIntStateOf(1) }

            Box(modifier = Modifier.fillMaxSize().onSizeChanged {
                    contentHeightPx = it.height.coerceAtLeast(1)
                    contentHeightPxRef.intValue = it.height.coerceAtLeast(1)
                }
                .pointerInput(Unit) {
                    // Handle drags that start inside the chapter panel top bar.
                    // Uses Initial pass so this runs before children (clickable) consume the event.
                    // Coordinates are stable because this Box doesn't move with f.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val currentF = fRef.floatValue
                        val currentContentH = contentHeightPxRef.intValue
                        val topBarTopPx = with(density) {
                            val bA = currentContentH.toDp()
                            val uS = (currentContentH * 0.025f).toDp()
                            val colTop = (bA - 60.dp - uS) * (1f - currentF) + (miniPlayerHeight + 16.dp) * currentF
                            colTop.toPx()
                        }
                        val topBarBottomPx = topBarTopPx + with(density) { 60.dp.toPx() }
                        if (down.position.y < topBarTopPx || down.position.y > topBarBottomPx) return@awaitEachGesture
                        topBarDraggingRef.active = true
                        var lastY = down.position.y
                        val travelPx = chapterTravelPxRef.floatValue
                        var dragged = false
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - lastY
                            lastY = change.position.y
                            if (kotlin.math.abs(dy) > 2f) {
                                dragged = true
                                change.consume()
                                snapChapterTo(chapterDragValue + (-dy / travelPx))
                            }
                        }
                        topBarDraggingRef.active = false
                        if (dragged) settleChapters()
                        else if (currentF < 0.5f) openChapters() else closeChapters()
                    }
                }
            ) {
                // === Mini-player text (fades in at end of animation) ===
                if (f > 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(miniPlayerFadeIn)
                            .clickable { closeChapters() }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var lastY = down.position.y
                                    var dragged = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val dy = change.position.y - lastY
                                        lastY = change.position.y
                                        if (kotlin.math.abs(dy) > 2f) {
                                            dragged = true
                                            change.consume()
                                            snapChapterTo(chapterDragValue + (-dy / containerHeightPx))
                                        }
                                    }
                                    if (dragged) settleChapters()
                                }
                            }
                            .padding(start = miniCoverPadding + miniCoverSize + 12.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session?.displayTitle ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state.currentChapter?.title ?: session?.displayAuthor ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { if (state.isPlaying) viewModel.pause() else viewModel.play() }) {
                            Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                        }
                        IconButton(onClick = { closeChapters() }) {
                            Icon(Icons.Default.KeyboardArrowDown, "Close chapters")
                        }
                    }
                }

                // === Chapter list with indicator (always present, slides from bottom) ===
                if (chapters.isNotEmpty()) {
                    ChapterPanel(
                        chapters = chapters,
                        currentChapter = state.currentChapter,
                        f = f,
                        contentHeightPx = contentHeightPx,
                        miniPlayerHeight = miniPlayerHeight,
                        chapterDragValue = { chapterDragValue },
                        chapterListState = chapterListState,
                        chapterTravelPxRef = chapterTravelPxRef,
                        chapterTravelPxTopLevel = chapterTravelPxTopLevel,
                        topBarDraggingRef = topBarDraggingRef,
                        listDraggingRef = listDraggingRef,
                        shrinkPhase = shrinkPhase,
                        bgColor = chapterListBgSolid,
                        accentColor = accentColor,
                        onSeekTo = { viewModel.seekTo(it) },
                        onCloseChapters = { closeChapters() },
                        onOpenChapters = { openChapters() },
                        onSnapChapterTo = { snapChapterTo(it) },
                        density = density,
                    )
                }

                // === Player text + controls (slides up with chapter list, fades before reaching cover) ===
                if (controlsFadeOut > 0f) {
                    PlayerControlsSection(
                        state = state,
                        session = session,
                        controlsFadeOut = controlsFadeOut,
                        f = f,
                        coverCY = coverCY,
                        coverCS = coverCS,
                        cardTopDp = cardTopDp,
                        containerHeightPx = containerHeightPx,
                        miniPlayerHeight = miniPlayerHeight,
                        accentColor = accentColor,
                        accentBg = accentBg,
                        accentFg = accentFg,
                        tintedColor = tintedColor,
                        isDark = isDark,
                        currentSeconds = currentSeconds,
                        duration = duration,
                        progressValue = progressValue,
                        trackHeight = trackHeight,
                        isSliderDragging = isSliderDragging,
                        jumpBack = jumpBack,
                        jumpFwd = jumpFwd,
                        dlState = dlState,
                        chapters = chapters,
                        currentItemId = currentItemId,
                        onSliderDragStart = { isSliderDragging = true },
                        onSliderDragMove = { sliderValue = it },
                        onSliderDragEnd = { frac -> isSliderDragging = false; viewModel.seekTo(frac.toDouble() * duration) },
                        onSeekTo = { viewModel.seekTo(it) },
                        onSeekForward = { viewModel.seekForward() },
                        onSeekBack = { viewModel.seekBack() },
                        onPlay = { viewModel.play() },
                        onPause = { viewModel.pause() },
                        onNextChapter = { viewModel.nextChapter() },
                        onPreviousChapter = { viewModel.previousChapter() },
                        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        onSetSleepTimer = { viewModel.setSleepTimer(it) },
                        onStartDownload = { viewModel.startDownload() },
                        onOpenChapters = { openChapters() },
                        onOpenQueue = { showQueueSheet = true },
                        onShowEpisodeInfo = { showEpisodeInfoSheet = true },
                        isPodcast = session?.mediaType == "podcast",
                        onDismissPlayer = { dismissPlayer() },
                        onDetailClick = onDetailClick,
                        onAuthorClick = onAuthorClick,
                        onSeriesClick = onSeriesClick,
                        density = density,
                    )
                }
            }
        }

        } // Column
        } // CompositionLocalProvider
        } // if pf > 0f

        // Cover always on top
        if (session != null) {
            AsyncImage(
                model = session.libraryItemId.let {
                    val data = if (!serverUrl.isNullOrEmpty()) {
                        "$serverUrl/api/items/$it/cover"
                    } else {
                        File(context.filesDir, "downloads/$it/cover.jpg").takeIf { f -> f.exists() }
                    }
                    ImageRequest.Builder(context)
                        .data(data)
                        .allowHardware(false)
                        .size(800)
                        .build()
                },
                contentDescription = session.displayTitle,
                modifier = Modifier
                    .offset(x = coverCX, y = coverCY)
                    .size(coverCS)
                    .clip(RoundedCornerShape(coverCR)),
                contentScale = ContentScale.Crop,
                onState = { imgState ->
                    if (imgState is AsyncImagePainter.State.Success) {
                        try {
                            val bitmap = imgState.result.image.toBitmap()
                            Palette.from(bitmap).generate { palette ->
                                palette?.let {
                                    val swatch = it.dominantSwatch ?: it.mutedSwatch ?: it.darkMutedSwatch
                                    swatch?.let { s -> dominantColor = Color(s.rgb) }
                                }
                            }
                        } catch (e: Exception) { Log.w("PlayerUI", "Palette extraction failed", e) }
                    }
                },
            )
        }
    }

    if (showQueueSheet) {
        QueueSheet(
            onDismiss = { showQueueSheet = false },
            onPlayItem = { libraryItemId, episodeId ->
                showQueueSheet = false
                if (episodeId != null) viewModel.startEpisodePlayback(libraryItemId, episodeId)
                else viewModel.startPlayback(libraryItemId)
            },
            serverUrl = serverUrl ?: "",
        )
    }

    if (showEpisodeInfoSheet && session != null) {
        EpisodeInfoSheet(
            title = session.displayTitle,
            author = session.displayAuthor,
            description = session.episodeDescription ?: session.mediaMetadata.description,
            onTimestampClick = { viewModel.seekTo(it); showEpisodeInfoSheet = false },
            onDismiss = { showEpisodeInfoSheet = false },
        )
    }
    } // CompositionLocalProvider reducedSlop

@Composable
private fun PlayerChip(
    icon: ImageVector,
    text: String,
    bgColor: Color,
    fgColor: Color,
    onClick: () -> Unit,
    menuContainerColor: Color = MaterialTheme.colorScheme.surface,
    dropdownContent: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { if (dropdownContent != null) expanded = true else onClick() },
            shape = RoundedCornerShape(20.dp),
            color = bgColor,
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp), tint = fgColor)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.labelMedium, color = fgColor)
            }
        }
        if (dropdownContent != null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = menuContainerColor) {
                dropdownContent { expanded = false }
            }
        }
    }
}

private fun replayIcon(seconds: Int) = when (seconds) {
    5 -> Icons.Default.Replay5
    30 -> Icons.Default.Replay30
    else -> Icons.Default.Replay10
}

private fun forwardIcon(seconds: Int) = when (seconds) {
    5 -> Icons.Default.Forward5
    10 -> Icons.Default.Forward10
    else -> Icons.Default.Forward30
}

@Composable
fun MiniPlayer(
    state: PlayerState,
    serverUrl: String,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    jumpForwardSeconds: Int = 30,
    onFractionChange: ((Float) -> Unit)? = null,
    screenHeightPx: Int = 1,
) {
    if (state.session == null) return
    val session = state.session
    val progress = if (session.duration > 0) (state.currentTimeMs / 1000.0 / session.duration).toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag < -10f) onExpand()
                    },
                    onVerticalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                        if (onFractionChange != null && dragAmount < 0) {
                            val delta = -dragAmount / screenHeightPx
                            onFractionChange(delta)
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(onClick = onExpand)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = if (serverUrl.isNotEmpty()) "$serverUrl/api/items/${session.libraryItemId}/cover"
                        else File(LocalContext.current.filesDir, "downloads/${session.libraryItemId}/cover.jpg").takeIf { it.exists() },
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.displayTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.displayAuthor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.isCasting) {
                Icon(Icons.Default.Cast, "Casting", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onPlayPause) { Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else stringResource(R.string.play)) }
            IconButton(onClick = onSkipForward) { Icon(forwardIcon(jumpForwardSeconds), stringResource(R.string.jump_forward)) }
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp))
    }
}

@Composable
private fun ChapterPanel(
    chapters: List<Chapter>,
    currentChapter: Chapter?,
    f: Float,
    contentHeightPx: Int,
    miniPlayerHeight: Dp,
    chapterDragValue: () -> Float,
    chapterListState: androidx.compose.foundation.lazy.LazyListState,
    chapterTravelPxRef: androidx.compose.runtime.MutableFloatState,
    chapterTravelPxTopLevel: androidx.compose.runtime.MutableFloatState,
    topBarDraggingRef: DragActiveRef,
    listDraggingRef: DragActiveRef,
    shrinkPhase: Float,
    bgColor: Color,
    accentColor: Color,
    onSeekTo: (Double) -> Unit,
    onCloseChapters: () -> Unit,
    onOpenChapters: () -> Unit,
    onSnapChapterTo: (Float) -> Unit,
    density: androidx.compose.ui.unit.Density,
) {
    val expandedY = miniPlayerHeight + 16.dp
    val chapterBgAlpha = shrinkPhase.coerceIn(0f, 1f)
    val indicatorHeightDp = 60.dp
    val bottomAnchor = with(density) { contentHeightPx.toDp() }
    val upShift = with(density) { (contentHeightPx * 0.025f).toDp() }
    val collapsedTop = bottomAnchor - indicatorHeightDp - upShift
    val expandedTop = expandedY
    val chapterTopY = lerpDp(collapsedTop, expandedTop, f)
    val chapterHeight = lerpDp(indicatorHeightDp, bottomAnchor - expandedTop, f)
    val chapterTravelPx = with(density) { (collapsedTop - expandedTop).toPx() }.coerceAtLeast(1f)
    chapterTravelPxRef.floatValue = chapterTravelPx
    chapterTravelPxTopLevel.floatValue = chapterTravelPx

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(chapterHeight)
            .offset(y = chapterTopY)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(bgColor.copy(alpha = chapterBgAlpha)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(stringResource(R.string.chapters_count, chapters.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val chapterNestedScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    val listAtTop = !chapterListState.canScrollBackward
                    val travelPx = chapterTravelPxRef.floatValue
                    return when {
                        dy > 0f && (listAtTop || listDraggingRef.active) -> {
                            listDraggingRef.active = true
                            val maxDelta = chapterDragValue() * travelPx
                            val consumed = dy.coerceAtMost(maxDelta)
                            onSnapChapterTo(chapterDragValue() - consumed / travelPx)
                            available.copy(y = consumed)
                        }
                        dy < 0f && chapterDragValue() < 0.99f && !topBarDraggingRef.active -> {
                            listDraggingRef.active = true
                            val maxDelta = (1f - chapterDragValue()) * travelPx
                            val consumed = (-dy).coerceAtMost(maxDelta)
                            onSnapChapterTo(chapterDragValue() + consumed / travelPx)
                            available.copy(y = -consumed)
                        }
                        else -> Offset.Zero
                    }
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    val listAtTop = !chapterListState.canScrollBackward
                    val mid = chapterDragValue() > 0.01f && chapterDragValue() < 0.99f
                    listDraggingRef.active = false
                    return when {
                        mid -> {
                            if (available.y > 0f || chapterDragValue() < 0.5f) onCloseChapters()
                            else onOpenChapters()
                            available
                        }
                        listAtTop && available.y > 0f -> {
                            onCloseChapters()
                            available
                        }
                        else -> Velocity.Zero
                    }
                }
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    listDraggingRef.active = false
                    if (chapterDragValue() > 0.01f && chapterDragValue() < 0.99f) {
                        if (chapterDragValue() < 0.5f) onCloseChapters()
                        else onOpenChapters()
                    }
                    return super.onPostFling(consumed, available)
                }
            }
        }
        if (f > 0f) LazyColumn(
            state = chapterListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(chapterNestedScroll),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(chapters) { chapter ->
                val isCurrent = chapter == currentChapter
                val chapterDuration = chapter.end - chapter.start
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekTo(chapter.start); onCloseChapters() }
                        .background(if (isCurrent) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCurrent) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp), tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(chapter.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (isCurrent) accentColor else MaterialTheme.colorScheme.onSurface)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControlsSection(
    state: PlayerState,
    session: PlaybackSession?,
    controlsFadeOut: Float,
    f: Float,
    coverCY: Dp,
    coverCS: Dp,
    cardTopDp: Dp,
    containerHeightPx: Int,
    miniPlayerHeight: Dp,
    accentColor: Color,
    accentBg: Color,
    accentFg: Color,
    tintedColor: Color,
    isDark: Boolean,
    currentSeconds: Double,
    duration: Double,
    progressValue: Float,
    trackHeight: Dp,
    isSliderDragging: Boolean,
    jumpBack: Int,
    jumpFwd: Int,
    dlState: DownloadEntity?,
    chapters: List<Chapter>,
    currentItemId: String?,
    onSliderDragStart: () -> Unit,
    onSliderDragMove: (Float) -> Unit,
    onSliderDragEnd: (Float) -> Unit,
    onSeekTo: (Double) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onStartDownload: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenQueue: () -> Unit,
    onShowEpisodeInfo: () -> Unit,
    isPodcast: Boolean,
    onDismissPlayer: () -> Unit,
    onDetailClick: ((String) -> Unit)?,
    onAuthorClick: ((String, String) -> Unit)?,
    onSeriesClick: ((String) -> Unit)?,
    density: androidx.compose.ui.unit.Density,
) {
    val indicatorHeight = 60.dp
    val chapterTravelDist = with(density) { containerHeightPx.toDp() } - indicatorHeight - miniPlayerHeight - 16.dp
    val controlsSlideOffset = with(density) { (chapterTravelDist * f).roundToPx() }
    val upwardShift = with(density) { (containerHeightPx * 0.07f).toInt() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, -controlsSlideOffset - upwardShift) }
            .alpha(controlsFadeOut)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(coverCY - cardTopDp + coverCS + 40.dp))

        val textFade = (1f - f * 50f).coerceIn(0f, 1f)
        Column(
            modifier = Modifier.fillMaxWidth().alpha(textFade),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = session?.displayTitle ?: "",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            session?.mediaMetadata?.subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(2.dp))
            val seriesEntries = session?.mediaMetadata?.seriesEntries ?: emptyList()
            if (seriesEntries.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.Center) {
                    seriesEntries.forEachIndexed { index, entry ->
                        if (index > 0) Text(" · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val label = entry.sequence?.let { "${entry.name} #$it" } ?: entry.name
                        Text(label, style = MaterialTheme.typography.bodySmall, color = accentColor,
                            modifier = Modifier.clickable { onDismissPlayer(); onSeriesClick?.invoke(entry.id) })
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            val authors = session?.mediaMetadata?.authors ?: emptyList()
            if (authors.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.Center) {
                    authors.forEachIndexed { index, author ->
                        if (index > 0) Text(", ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(author.name, style = MaterialTheme.typography.bodyMedium, color = accentColor,
                            modifier = Modifier.clickable { onDismissPlayer(); onAuthorClick?.invoke(author.id, author.name) })
                    }
                }
            } else {
                Text(session?.displayAuthor ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            state.currentChapter?.let { chapter ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(chapter.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PlayerChip(Icons.Default.Speed, "${state.playbackSpeed}x", accentBg, accentFg, onClick = { },
                    menuContainerColor = tintedColor,
                    dropdownContent = { onDismiss -> listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f).forEach { speed -> DropdownMenuItem(text = { Text("${speed}x") }, onClick = { onSetPlaybackSpeed(speed); onDismiss() }, trailingIcon = { if (speed == state.playbackSpeed) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) } })
            }
            item {
                val endMs = state.sleepTimerEndMs
                val timerMinutes = state.sleepTimerMinutes
                val counting = endMs != null && endMs > System.currentTimeMillis()
                val timerSet = timerMinutes != null
                var remainingText by remember { mutableStateOf("") }
                LaunchedEffect(endMs) {
                    if (endMs != null) {
                        while (true) {
                            val remaining = (endMs - System.currentTimeMillis()).coerceAtLeast(0)
                            val mins = remaining / 60000
                            val secs = (remaining % 60000) / 1000
                            remainingText = "%d:%02d".format(mins, secs)
                            if (remaining <= 0) break
                            delay(1000)
                        }
                    }
                }
                val sleepText = when {
                    counting -> remainingText
                    timerSet -> "${timerMinutes}m"
                    else -> stringResource(R.string.sleep)
                }
                val sleepIcon = when {
                    counting -> Icons.Default.Alarm
                    timerSet -> Icons.Default.Alarm
                    else -> Icons.Default.AlarmOff
                }
                PlayerChip(sleepIcon, sleepText, accentBg, accentFg, onClick = { },
                    menuContainerColor = tintedColor,
                    dropdownContent = { onDismiss -> if (timerSet) { DropdownMenuItem(text = { Text(stringResource(R.string.cancel_timer)) }, onClick = { onSetSleepTimer(null); onDismiss() }); HorizontalDivider() }; listOf(5, 10, 15, 20, 30, 45, 60, 90).forEach { min -> DropdownMenuItem(text = { Text(stringResource(R.string.minutes_format, min)) }, onClick = { onSetSleepTimer(min); onDismiss() }) } })
            }
            if (isPodcast) {
                item {
                    PlayerChip(Icons.Default.Info, stringResource(R.string.episode_info), accentBg, accentFg, onClick = { onShowEpisodeInfo() })
                }
                if (onDetailClick != null && currentItemId != null) {
                    item {
                        PlayerChip(Icons.Default.Podcasts, stringResource(R.string.podcast), accentBg, accentFg, onClick = { onDismissPlayer(); onDetailClick(currentItemId) })
                    }
                }
            } else if (onDetailClick != null && currentItemId != null) {
                item {
                    PlayerChip(Icons.Default.Info, stringResource(R.string.details), accentBg, accentFg, onClick = { onDismissPlayer(); onDetailClick(currentItemId) })
                }
            }
            if (chapters.isNotEmpty()) { item { PlayerChip(Icons.Default.FormatListBulleted, stringResource(R.string.chapters), accentBg, accentFg, onClick = { onOpenChapters() }) } }
            if (isPodcast) {
                item { PlayerChip(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.queue), accentBg, accentFg, onClick = { onOpenQueue() }) }
            }
            item {
                val dlIcon = when (dlState?.status) { DownloadStatus.COMPLETED -> Icons.Default.DownloadDone; DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> Icons.Default.Downloading; else -> Icons.Default.Download }
                val dl = dlState
                val dlText = when (dl?.status) { DownloadStatus.COMPLETED -> stringResource(R.string.downloaded); DownloadStatus.DOWNLOADING -> { val pct = if (dl.totalBytes > 0) (dl.downloadedBytes * 100 / dl.totalBytes).toInt() else 0; "$pct%" }; DownloadStatus.QUEUED -> "Queued"; else -> stringResource(R.string.download) }
                PlayerChip(dlIcon, dlText, accentBg, accentFg, onClick = { if (dlState?.status != DownloadStatus.COMPLETED && dlState?.status != DownloadStatus.DOWNLOADING && dlState?.status != DownloadStatus.QUEUED) onStartDownload() })
            }
            item {
                val ctx = LocalContext.current
                val castLabel = if (state.isCasting) stringResource(R.string.casting) else stringResource(R.string.cast)
                val castIcon = if (state.isCasting) Icons.Default.CastConnected else Icons.Default.Cast
                PlayerChip(castIcon, castLabel, accentBg, accentFg, onClick = {
                    try {
                        val activity = ctx as Activity
                        val castCtx = CastContext.getSharedInstance(ctx)
                        val selector = MediaRouteSelector.Builder()
                            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                            .build()
                        val themedCtx = ContextThemeWrapper(activity, R.style.Theme_Unshelved_CastDialog)
                        if (castCtx.sessionManager.currentCastSession?.isConnected == true) {
                            val dialog = MediaRouteControllerDialog(themedCtx)
                            dialog.show()
                        } else {
                            val dialog = MediaRouteChooserDialog(themedCtx)
                            dialog.routeSelector = selector
                            dialog.show()
                        }
                    } catch (e: Throwable) {
                        Log.e("CastButton", "Cast click failed", e)
                    }
                })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(36.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onSliderDragStart()
                        onSliderDragMove((down.position.x / size.width).coerceIn(0f, 1f))
                        down.consume()
                        var lastFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            lastFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSliderDragMove(lastFraction)
                        }
                        onSliderDragEnd(lastFraction)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(50))) {
                Box(modifier = Modifier.fillMaxSize().background(accentColor.copy(alpha = 0.20f)))
                Box(modifier = Modifier.fillMaxWidth(progressValue).fillMaxHeight().background(accentColor))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val sliderValue = progressValue
        val displaySeconds = if (isSliderDragging) sliderValue * duration else currentSeconds
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(displaySeconds.toHhMmSs(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val remaining = (duration - displaySeconds).coerceAtLeast(0.0)
            Text("-${remaining.toHhMmSs()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousChapter, modifier = Modifier.size(46.dp)) { Icon(Icons.Default.SkipPrevious, if (isPodcast) "Restart episode" else "Previous chapter", modifier = Modifier.size(32.dp), tint = accentColor) }
            IconButton(onClick = onSeekBack, modifier = Modifier.size(46.dp)) { Icon(replayIcon(jumpBack), stringResource(R.string.jump_back), modifier = Modifier.size(32.dp), tint = accentColor) }
            FilledIconButton(
                onClick = { if (state.isPlaying) onPause() else onPlay() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = if (isDark) Color.Black else Color.White,
                ),
            ) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.isPlaying) "Pause" else stringResource(R.string.play), modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onSeekForward, modifier = Modifier.size(46.dp)) { Icon(forwardIcon(jumpFwd), stringResource(R.string.jump_forward), modifier = Modifier.size(32.dp), tint = accentColor) }
            IconButton(onClick = onNextChapter, modifier = Modifier.size(46.dp)) { Icon(Icons.Default.SkipNext, if (isPodcast) "Next episode" else "Next chapter", modifier = Modifier.size(32.dp), tint = accentColor) }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeInfoSheet(
    title: String,
    author: String,
    description: String?,
    onTimestampClick: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                val spanned = Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT)
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