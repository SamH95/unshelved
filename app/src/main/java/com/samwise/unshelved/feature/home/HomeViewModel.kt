package com.samwise.unshelved.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.model.Series
import com.samwise.unshelved.core.model.toDomain
import com.samwise.unshelved.core.network.PersonalizedShelfDto
import com.samwise.unshelved.feature.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val continueListening: List<LibraryItem> = emptyList(),
    val continueSeries: List<LibraryItem> = emptyList(),
    val recentlyAdded: List<LibraryItem> = emptyList(),
    val discover: List<LibraryItem> = emptyList(),
    val recentSeries: List<Series> = emptyList(),
    val newestEpisodes: List<LibraryItem> = emptyList(),
    val listenAgain: List<LibraryItem> = emptyList(),
    val isPodcastLibrary: Boolean = false,
    val error: String? = null,
) {
    val hasContent: Boolean
        get() = continueListening.isNotEmpty() || continueSeries.isNotEmpty() ||
                recentlyAdded.isNotEmpty() || discover.isNotEmpty() || recentSeries.isNotEmpty() ||
                newestEpisodes.isNotEmpty() || listenAgain.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    val progressCache: ProgressCache,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val selectedLibraryId = prefs.activeLibraryId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    init {
        progressCache.refresh()
        viewModelScope.launch {
            prefs.selectedLibraryMediaType.collect { mediaType ->
                _state.update { it.copy(isPodcastLibrary = mediaType == "podcast") }
            }
        }
        viewModelScope.launch {
            prefs.activeLibraryId.filterNotNull().collectLatest { libraryId ->
                load(libraryId)
            }
        }
        viewModelScope.launch {
            libraryRepository.libraryInvalidated.collect { load() }
        }
    }

    fun load(libraryId: String? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            progressCache.refresh()
            val resolvedId = libraryId
                ?: prefs.activeLibraryId.first()
                ?: libraryRepository.getLibraries().getOrNull()?.firstOrNull()?.id

            if (resolvedId == null) {
                _state.update { it.copy(isLoading = false, continueListening = emptyList(), recentlyAdded = emptyList()) }
                return@launch
            }

            val cachedShelves = libraryRepository.getCachedPersonalized(resolvedId)
            if (cachedShelves != null) {
                _state.update { parseShelvesToState(cachedShelves, it) }
                if (!_state.value.isPodcastLibrary) {
                    val cachedSeries = libraryRepository.getLibrarySeries(resolvedId).getOrNull()
                        ?.sortedByDescending { it.updatedAt }?.take(10) ?: emptyList()
                    _state.update { it.copy(recentSeries = cachedSeries) }
                }

                val entity = libraryRepository.getCachedShelvesEntity(resolvedId)
                if (entity == null || entity.isStale()) {
                    loadDataInBackground(resolvedId)
                }
            } else {
                _state.update { it.copy(isLoading = true) }
                try {
                    loadData(resolvedId)
                } catch (e: Exception) {
                    Log.e("HomeVM", "load failed", e)
                } finally {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        loadJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try {
                progressCache.refresh()
                val libraryId = prefs.activeLibraryId.first()
                    ?: libraryRepository.getLibraries().getOrNull()?.firstOrNull()?.id
                if (libraryId != null) {
                    val shelves = libraryRepository.getPersonalized(libraryId).getOrNull()
                    if (shelves != null) {
                        _state.update { parseShelvesToState(shelves, it) }
                    }
                    if (!_state.value.isPodcastLibrary) {
                        val series = libraryRepository.getLibrarySeries(libraryId).getOrNull()
                        if (series != null) {
                            _state.update { it.copy(recentSeries = series.sortedByDescending { s -> s.updatedAt }.take(10)) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeVM", "refresh failed", e)
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun loadDataInBackground(libraryId: String) {
        viewModelScope.launch {
            try {
                loadData(libraryId)
            } catch (e: Exception) {
                Log.e("HomeVM", "background load failed", e)
            }
        }
    }

    private suspend fun loadData(libraryId: String) = coroutineScope {
        val isPodcast = _state.value.isPodcastLibrary
        val personalizedDeferred = async { libraryRepository.getPersonalized(libraryId) }
        val seriesDeferred = if (!isPodcast) async { libraryRepository.getLibrarySeries(libraryId) } else null

        val shelvesResult = personalizedDeferred.await()
        if (shelvesResult.isSuccess) {
            val shelves = shelvesResult.getOrElse { emptyList() }
            _state.update { parseShelvesToState(shelves, it) }
        } else {
            _state.update { it.copy(isLoading = false) }
        }

        seriesDeferred?.await()?.getOrNull()?.let { allSeries ->
            _state.update { it.copy(recentSeries = allSeries.sortedByDescending { s -> s.updatedAt }.take(10)) }
        }
    }

    private fun parseShelvesToState(
        shelves: List<PersonalizedShelfDto>,
        existingState: HomeState,
    ): HomeState {
        fun shelfItems(id: String) = shelves.find { it.id == id }?.entities?.map { it.toDomain() } ?: emptyList()
        return existingState.copy(
            isLoading = false,
            continueListening = shelfItems("continue-listening"),
            continueSeries = shelfItems("continue-series"),
            recentlyAdded = shelfItems("recently-added"),
            discover = shelfItems("recommended").ifEmpty { shelfItems("discover") },
            newestEpisodes = shelfItems("newest-episodes"),
            listenAgain = shelfItems("listen-again"),
        )
    }
}
