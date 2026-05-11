package com.samwise.unshelved

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.samwise.unshelved.core.model.ItunesSearchResult
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.ui.LocalTopPadding
import com.samwise.unshelved.feature.addpodcast.AddPodcastScreen
import com.samwise.unshelved.feature.detail.DetailScreen
import com.samwise.unshelved.feature.downloads.DownloadsScreen
import com.samwise.unshelved.feature.episodedetail.EpisodeDetailSheet
import com.samwise.unshelved.feature.home.HomeScreen
import com.samwise.unshelved.feature.latest.LatestEpisodesScreen
import com.samwise.unshelved.feature.library.FilterAuthor
import com.samwise.unshelved.feature.library.LibraryScreen
import com.samwise.unshelved.feature.library.LibraryViewModel
import com.samwise.unshelved.feature.offline.OfflineLibraryScreen
import com.samwise.unshelved.feature.player.FullPlayerSheet
import com.samwise.unshelved.feature.player.PlayerViewModel
import com.samwise.unshelved.feature.podcastdetail.PodcastDetailScreen
import com.samwise.unshelved.feature.queue.QueueScreen
import com.samwise.unshelved.feature.queue.QueueViewModel
import com.samwise.unshelved.feature.search.SearchViewModel
import com.samwise.unshelved.feature.series.SeriesDetailScreen
import com.samwise.unshelved.feature.series.SeriesListScreen
import com.samwise.unshelved.feature.settings.SettingsScreen
import com.samwise.unshelved.feature.settings.SettingsViewModel
import com.samwise.unshelved.service.PlayerState
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

private sealed class NavRoute(val route: String) {
    object Home : NavRoute("home")
    object Library : NavRoute("library")
    object Series : NavRoute("series_list")
    object Latest : NavRoute("latest")
    object Queue : NavRoute("queue")
    object Settings : NavRoute("settings")
    object SeriesDetail : NavRoute("series/{seriesId}") {
        fun create(id: String) = "series/$id"
    }
    object Detail : NavRoute("detail/{itemId}") {
        fun create(id: String) = "detail/$id"
    }
    object PodcastDetail : NavRoute("podcast/{itemId}?newlyAdded={newlyAdded}") {
        fun create(id: String, newlyAdded: Boolean = false) = "podcast/$id?newlyAdded=$newlyAdded"
    }
    object EpisodeDetail : NavRoute("episode/{itemId}/{episodeId}") {
        fun create(itemId: String, episodeId: String) = "episode/$itemId/$episodeId"
    }
    object Downloads : NavRoute("downloads")
    object AddPodcast : NavRoute("add_podcast?feedUrl={feedUrl}&coverUrl={coverUrl}&itunesId={itunesId}&itunesArtistId={itunesArtistId}&itunesPageUrl={itunesPageUrl}") {
        fun create(feedUrl: String, coverUrl: String?, itunesId: Long?, itunesArtistId: Long?, itunesPageUrl: String?) : String {
            val encoded = java.net.URLEncoder.encode(feedUrl, "UTF-8")
            val coverEncoded = coverUrl?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
            val pageEncoded = itunesPageUrl?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
            return "add_podcast?feedUrl=$encoded&coverUrl=$coverEncoded&itunesId=${itunesId ?: 0}&itunesArtistId=${itunesArtistId ?: 0}&itunesPageUrl=$pageEncoded"
        }
    }
}

private fun bottomNavItems(isPodcast: Boolean) = if (isPodcast) listOf(
    Triple(NavRoute.Latest, Icons.Default.NewReleases, R.string.latest),
    Triple(NavRoute.Library, Icons.AutoMirrored.Filled.LibraryBooks, R.string.library),
    Triple(NavRoute.Queue, Icons.AutoMirrored.Filled.QueueMusic, R.string.queue),
    Triple(NavRoute.Settings, Icons.Default.Settings, R.string.settings),
) else listOf(
    Triple(NavRoute.Home, Icons.Default.Home, R.string.home),
    Triple(NavRoute.Library, Icons.AutoMirrored.Filled.LibraryBooks, R.string.library),
    Triple(NavRoute.Series, Icons.Default.AutoStories, R.string.series),
    Triple(NavRoute.Settings, Icons.Default.Settings, R.string.settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val playerVM: PlayerViewModel = hiltViewModel()
    val settingsVM: SettingsViewModel = hiltViewModel()
    val queueVM: QueueViewModel = hiltViewModel()
    val queuedEpisodeIds by queueVM.queuedEpisodeIds.collectAsStateWithLifecycle()
    val settingsState by settingsVM.state.collectAsStateWithLifecycle()
    val isOfflineMode = settingsState.isOfflineMode

    val playerState by playerVM.playerState.collectAsStateWithLifecycle()
    val serverUrl by playerVM.serverUrl.collectAsStateWithLifecycle()
    val selectedMediaType by settingsVM.selectedMediaType.collectAsStateWithLifecycle()
    val isPodcastLibrary = selectedMediaType == "podcast"
    val hasBothLibraries by settingsVM.hasBothLibraries.collectAsStateWithLifecycle()

    val hasSession = playerState.session != null
    var autoExpand by remember { mutableStateOf(false) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val sharedPlayerFraction = remember { Animatable(0f) }

    LaunchedEffect(playerState.session?.id) {
        if (playerState.session != null) autoExpand = true
        else sharedPlayerFraction.snapTo(0f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isOfflineMode) {
            OfflineModeNavigation(
                playerVM = playerVM,
                playerState = playerState,
                hasSession = hasSession,
                autoExpand = autoExpand,
                onAutoExpandConsumed = { autoExpand = false },
                sharedPlayerFraction = sharedPlayerFraction,
                bottomBarHeightPx = bottomBarHeightPx,
                onBottomBarHeightChanged = { bottomBarHeightPx = it },
            )
        } else {
        key(isPodcastLibrary) {
        val navController = rememberNavController()
        val libraryVM: LibraryViewModel = hiltViewModel()
        val searchVM: SearchViewModel = hiltViewModel()
        val searchState by searchVM.state.collectAsStateWithLifecycle()

        val currentBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStack?.destination?.route

        var searchBarHeightPx by remember { mutableIntStateOf(0) }
        var searchExpanded by remember { mutableStateOf(false) }
        var latestMultiSelectActive by remember { mutableStateOf(false) }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.surface,
        ) { scaffoldPadding ->
            val density = androidx.compose.ui.platform.LocalDensity.current
            val navBarDp = with(density) { bottomBarHeightPx.toDp() }
            val miniPlayerHeight = if (hasSession) 64.dp else 0.dp
            val extraBottomPadding = navBarDp + miniPlayerHeight
            Box(modifier = Modifier.padding(scaffoldPadding).fillMaxSize()) {
                val searchBarDp = with(density) { searchBarHeightPx.toDp() }
                CompositionLocalProvider(LocalTopPadding provides searchBarDp) {
                val startDest = if (isPodcastLibrary) NavRoute.Latest.route else NavRoute.Home.route
                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    modifier = Modifier.fillMaxSize().padding(bottom = extraBottomPadding),
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = { fadeOut(animationSpec = tween(200)) },
                ) {
                composable(NavRoute.Home.route) {
                    HomeScreen(
                        onBookClick = { navController.navigate(NavRoute.Detail.create(it)) },
                        onPlayBook = { playerVM.startPlayback(it) },
                        onSeriesClick = { navController.navigate(NavRoute.SeriesDetail.create(it)) },
                        onPodcastClick = { navController.navigate(NavRoute.PodcastDetail.create(it)) },
                        onPlayEpisode = { itemId, episodeId -> playerVM.startEpisodePlayback(itemId, episodeId) },
                    )
                }
                composable(NavRoute.Library.route) {
                    LibraryScreen(
                        onBookClick = { navController.navigate(NavRoute.Detail.create(it)) },
                        onPodcastClick = { navController.navigate(NavRoute.PodcastDetail.create(it)) },
                        onAddPodcast = {
                            searchVM.setFindNewMode(true)
                            searchExpanded = true
                        },
                        viewModel = libraryVM,
                    )
                }
                composable(NavRoute.Series.route) {
                    SeriesListScreen(onSeriesClick = { navController.navigate(NavRoute.SeriesDetail.create(it)) })
                }
                composable(NavRoute.Latest.route) {
                    val progressMap by queueVM.progressCache.progressMap.collectAsStateWithLifecycle()
                    LatestEpisodesScreen(
                        onPlayEpisode = { itemId, episodeId -> playerVM.startEpisodePlayback(itemId, episodeId) },
                        onEpisodeClick = { itemId, episodeId ->
                            navController.navigate(NavRoute.EpisodeDetail.create(itemId, episodeId))
                        },
                        queuedEpisodeIds = queuedEpisodeIds,
                        progressMap = progressMap,
                        onMultiSelectChanged = { latestMultiSelectActive = it },
                    )
                }
                composable(NavRoute.Queue.route) {
                    QueueScreen(
                        onPlayItem = { libraryItemId, episodeId ->
                            if (episodeId != null) playerVM.startEpisodePlayback(libraryItemId, episodeId)
                            else playerVM.startPlayback(libraryItemId)
                        },
                    )
                }
                composable(NavRoute.Settings.route) {
                    SettingsScreen(
                        onLogout = {},
                        onManageDownloads = { navController.navigate(NavRoute.Downloads.route) },
                    )
                }
                composable(
                    NavRoute.SeriesDetail.route,
                    arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
                ) {
                    SeriesDetailScreen(
                        onBookClick = { navController.navigate(NavRoute.Detail.create(it)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    NavRoute.Detail.route,
                    arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                ) {
                    DetailScreen(
                        onPlay = { itemId -> playerVM.startPlayback(itemId) },
                        onPlayAtPosition = { itemId, pos -> playerVM.startPlaybackAtPosition(itemId, pos) },
                        onBack = { navController.popBackStack() },
                        onAuthorClick = { authorId, authorName ->
                            libraryVM.setFilter(author = FilterAuthor(authorId, authorName))
                            navController.navigate(NavRoute.Library.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onSeriesClick = { seriesId ->
                            navController.navigate(NavRoute.SeriesDetail.create(seriesId))
                        },
                        onNarratorClick = { narrator ->
                            libraryVM.setFilter(narrator = narrator)
                            navController.navigate(NavRoute.Library.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onGenreClick = { genre ->
                            libraryVM.setFilter(genre = genre)
                            navController.navigate(NavRoute.Library.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(NavRoute.Downloads.route) {
                    DownloadsScreen(
                        serverUrl = serverUrl ?: "",
                    )
                }
                composable(
                    NavRoute.PodcastDetail.route,
                    arguments = listOf(
                        navArgument("itemId") { type = NavType.StringType },
                        navArgument("newlyAdded") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { backStackEntry ->
                    val newlyAdded = backStackEntry.arguments?.getBoolean("newlyAdded") == true
                    PodcastDetailScreen(
                        onBack = { navController.popBackStack() },
                        onPlayEpisode = { itemId, episodeId -> playerVM.startEpisodePlayback(itemId, episodeId) },
                        onEpisodeClick = { itemId, episodeId ->
                            navController.navigate(NavRoute.EpisodeDetail.create(itemId, episodeId))
                        },
                        onDeleted = { navController.popBackStack() },
                        queuedEpisodeIds = queuedEpisodeIds,
                        newlyAdded = newlyAdded,
                    )
                }
                dialog(
                    NavRoute.EpisodeDetail.route,
                    arguments = listOf(
                        navArgument("itemId") { type = NavType.StringType },
                        navArgument("episodeId") { type = NavType.StringType },
                    ),
                ) {
                    val itemId = it.arguments?.getString("itemId") ?: ""
                    val episodeId = it.arguments?.getString("episodeId") ?: ""
                    EpisodeDetailSheet(
                        onDismiss = { navController.popBackStack() },
                        onPlay = { playerVM.startEpisodePlayback(itemId, episodeId); navController.popBackStack() },
                        onTimestampClick = { seconds ->
                            val s = playerVM.playerState.value.session
                            if (s?.libraryItemId == itemId && s.episodeId == episodeId) playerVM.seekTo(seconds)
                            else playerVM.startEpisodePlaybackAtPosition(itemId, episodeId, seconds)
                            navController.popBackStack()
                        },
                        onPodcastClick = { libItemId ->
                            navController.popBackStack()
                            navController.navigate(NavRoute.PodcastDetail.create(libItemId))
                        },
                    )
                }
                composable(
                    NavRoute.AddPodcast.route,
                    arguments = listOf(
                        navArgument("feedUrl") { type = NavType.StringType },
                        navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" },
                        navArgument("itunesId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("itunesArtistId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("itunesPageUrl") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    AddPodcastScreen(
                        onBack = { navController.popBackStack() },
                        onPodcastCreated = { itemId ->
                            navController.navigate(NavRoute.PodcastDetail.create(itemId, newlyAdded = true)) {
                                popUpTo(NavRoute.AddPodcast.route) { inclusive = true }
                            }
                        },
                    )
                }
            }
                }
                val isPodcastDetailRoute = currentRoute?.startsWith("podcast/") == true
                val isAddPodcastRoute = currentRoute?.startsWith("add_podcast") == true
                if (!latestMultiSelectActive && !isPodcastDetailRoute && !isAddPodcastRoute) {
                Surface(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
                        .onSizeChanged { searchBarHeightPx = it.height },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    MainTopBar(
                        searchQuery = searchState.query,
                        onSearchQueryChanged = { searchVM.onQueryChanged(it) },
                        onSearchResultClick = { item ->
                            val ep = item.recentEpisode
                            if (ep != null) {
                                playerVM.startEpisodePlayback(item.id, ep.id)
                            } else if (isPodcastLibrary) {
                                navController.navigate(NavRoute.PodcastDetail.create(item.id))
                            } else {
                                navController.navigate(NavRoute.Detail.create(item.id))
                            }
                        },
                        searchResults = searchState.results,
                        isSearchLoading = searchState.isLoading,
                        serverUrl = serverUrl ?: "",
                        searchExpanded = searchExpanded,
                        onSearchExpandedChange = { expanded ->
                            searchExpanded = expanded
                            if (!expanded) searchVM.setFindNewMode(false)
                        },
                        isDetailScreen = currentRoute?.startsWith("detail/") == true || currentRoute?.startsWith("series/") == true || currentRoute?.startsWith("podcast/") == true || currentRoute == NavRoute.Downloads.route,
                        onBack = { navController.popBackStack() },
                        isPodcast = isPodcastLibrary,
                        showSwitchButton = hasBothLibraries,
                        onSwitch = { settingsVM.switchMode() },
                        findNewMode = searchState.findNewMode,
                        itunesResults = searchState.itunesResults,
                        existingFeedUrls = searchState.existingFeedUrls,
                        existingTitles = searchState.existingTitles,
                        onItunesResultClick = { result ->
                            searchExpanded = false
                            searchVM.setFindNewMode(false)
                            navController.navigate(NavRoute.AddPodcast.create(result.feedUrl ?: "", result.cover, result.id, result.artistId, result.pageUrl))
                        },
                    )
                }
                }
            }
        }

        if (hasSession && !searchExpanded) {
            FullPlayerSheet(
                onDismiss = {},
                autoExpand = autoExpand,
                onAutoExpandConsumed = { autoExpand = false },
                bottomOffset = bottomBarHeightPx,
                playerFractionAnim = sharedPlayerFraction,
                onAuthorClick = { authorId, authorName ->
                    libraryVM.setFilter(author = FilterAuthor(authorId, authorName))
                    navController.navigate(NavRoute.Library.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesId ->
                    navController.navigate(NavRoute.SeriesDetail.create(seriesId))
                },
                onDetailClick = { itemId ->
                    val isPodcast = playerState.session?.mediaType == "podcast"
                    if (isPodcast) navController.navigate(NavRoute.PodcastDetail.create(itemId))
                    else navController.navigate(NavRoute.Detail.create(itemId))
                },
                viewModel = playerVM,
            )
        }

        val navBarTranslationTarget = if (searchExpanded) 1f else sharedPlayerFraction.value
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .graphicsLayer { translationY = bottomBarHeightPx * navBarTranslationTarget },
        ) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                bottomNavItems(isPodcastLibrary).forEach { (route, icon, labelRes) ->
                    val selected = currentRoute == route.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(route.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineModeNavigation(
    playerVM: PlayerViewModel,
    playerState: PlayerState,
    hasSession: Boolean,
    autoExpand: Boolean,
    onAutoExpandConsumed: () -> Unit,
    sharedPlayerFraction: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    bottomBarHeightPx: Int,
    onBottomBarHeightChanged: (Int) -> Unit,
) {
    val downloadsVM: com.samwise.unshelved.feature.downloads.DownloadsViewModel = hiltViewModel()
    var showSettings by remember { mutableStateOf(false) }
    var showManageDownloads by remember { mutableStateOf(false) }
    val serverUrl by playerVM.serverUrl.collectAsStateWithLifecycle()

    var localAutoExpand by remember { mutableStateOf(false) }
    LaunchedEffect(autoExpand) {
        if (autoExpand) {
            localAutoExpand = true
            onAutoExpandConsumed()
        }
    }

    val navBarDp = with(androidx.compose.ui.platform.LocalDensity.current) { bottomBarHeightPx.toDp() }
    val miniPlayerHeight = if (hasSession) 64.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        if (showManageDownloads) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.manage_downloads)) },
                        navigationIcon = {
                            IconButton(onClick = { showManageDownloads = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
            ) { innerPadding ->
                DownloadsScreen(
                    serverUrl = serverUrl ?: "",
                    modifier = Modifier.padding(innerPadding),
                )
            }
        } else if (showSettings) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
            ) { innerPadding ->
                SettingsScreen(
                    onLogout = {},
                    onManageDownloads = { showManageDownloads = true },
                    modifier = Modifier.padding(innerPadding).padding(bottom = navBarDp + miniPlayerHeight),
                )
            }
        } else {
            OfflineLibraryScreen(
                onBack = {},
                showBackButton = false,
                playerBottomPadding = navBarDp + miniPlayerHeight,
                downloadsViewModel = downloadsVM,
            )
        }

        if (hasSession) {
            FullPlayerSheet(
                onDismiss = {},
                autoExpand = localAutoExpand,
                onAutoExpandConsumed = { localAutoExpand = false },
                bottomOffset = bottomBarHeightPx,
                playerFractionAnim = sharedPlayerFraction,
                onAuthorClick = { _, _ -> },
                onSeriesClick = {},
                onDetailClick = {},
                viewModel = playerVM,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { onBottomBarHeightChanged(it.height) }
                .graphicsLayer { translationY = bottomBarHeightPx * sharedPlayerFraction.value },
        ) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                NavigationBarItem(
                    selected = !showSettings && !showManageDownloads,
                    onClick = { showSettings = false; showManageDownloads = false },
                    icon = { Icon(Icons.Default.Download, contentDescription = stringResource(R.string.offline_library)) },
                    label = { Text(stringResource(R.string.library)) },
                )
                NavigationBarItem(
                    selected = showSettings || showManageDownloads,
                    onClick = { showSettings = true; showManageDownloads = false },
                    icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) },
                    label = { Text(stringResource(R.string.settings)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchResultClick: (LibraryItem) -> Unit,
    searchResults: List<LibraryItem>,
    isSearchLoading: Boolean,
    serverUrl: String,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    isDetailScreen: Boolean = false,
    onBack: () -> Unit = {},
    isPodcast: Boolean = false,
    showSwitchButton: Boolean = false,
    onSwitch: () -> Unit = {},
    findNewMode: Boolean = false,
    itunesResults: List<ItunesSearchResult> = emptyList(),
    existingFeedUrls: Set<String> = emptySet(),
    existingTitles: Set<String> = emptySet(),
    onItunesResultClick: (ItunesSearchResult) -> Unit = {},
) {
    AnimatedContent(
        targetState = isDetailScreen && !searchExpanded,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "topbar",
    ) { detail ->
        if (detail) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onSearchExpandedChange(true) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        } else {
            val placeholderText = when {
                findNewMode -> stringResource(R.string.search_new_podcasts)
                isPodcast -> stringResource(R.string.search_podcasts)
                else -> stringResource(R.string.search_audiobooks)
            }
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChanged,
                        onSearch = {},
                        expanded = searchExpanded,
                        onExpandedChange = onSearchExpandedChange,
                        placeholder = { Text(placeholderText) },
                        leadingIcon = {
                            if (searchExpanded) {
                                IconButton(onClick = { onSearchExpandedChange(false); onSearchQueryChanged("") }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                        },
                        trailingIcon = {
                            if (searchExpanded && searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            } else if (!searchExpanded && showSwitchButton) {
                                IconButton(onClick = onSwitch) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "Switch library")
                                }
                            }
                        },
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = onSearchExpandedChange,
                windowInsets = WindowInsets.statusBars,
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (searchExpanded) 0.dp else 16.dp)
                    .padding(bottom = if (searchExpanded) 0.dp else 8.dp),
            ) {
                if (isSearchLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (findNewMode) {
                    itunesResults.forEach { result ->
                        val alreadyAdded = (result.feedUrl != null && result.feedUrl in existingFeedUrls) ||
                            result.title.lowercase() in existingTitles
                        ListItem(
                            headlineContent = {
                                Text(
                                    result.title,
                                    color = if (alreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            supportingContent = {
                                if (alreadyAdded) {
                                    Text(stringResource(R.string.already_added), color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text(
                                        buildString {
                                            result.artistName?.let { append(it) }
                                            if (result.trackCount > 0) {
                                                if (isNotEmpty()) append(" · ")
                                                append("${result.trackCount} episodes")
                                            }
                                        },
                                    )
                                }
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = result.cover,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .then(if (alreadyAdded) Modifier.graphicsLayer { alpha = 0.5f } else Modifier),
                                )
                            },
                            modifier = Modifier.clickable(enabled = !alreadyAdded) {
                                onItunesResultClick(result)
                                onSearchQueryChanged("")
                            },
                        )
                        HorizontalDivider()
                    }
                } else {
                    searchResults.forEach { item ->
                        val episode = item.recentEpisode
                        ListItem(
                            headlineContent = { Text(episode?.title ?: item.title) },
                            supportingContent = {
                                Text(if (episode != null) item.title else item.authorName ?: "")
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = "$serverUrl/api/items/${item.id}/cover",
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                                )
                            },
                            modifier = Modifier.clickable {
                                onSearchResultClick(item)
                                onSearchExpandedChange(false)
                                onSearchQueryChanged("")
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
