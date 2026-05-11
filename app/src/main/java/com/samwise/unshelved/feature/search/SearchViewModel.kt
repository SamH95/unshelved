package com.samwise.unshelved.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.ItunesSearchResult
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.feature.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<LibraryItem> = emptyList(),
    val findNewMode: Boolean = false,
    val itunesResults: List<ItunesSearchResult> = emptyList(),
    val existingFeedUrls: Set<String> = emptySet(),
    val existingTitles: Set<String> = emptySet(),
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private var searchJob: Job? = null

    fun setFindNewMode(enabled: Boolean) {
        searchJob?.cancel()
        _state.update {
            it.copy(
                findNewMode = enabled,
                query = "",
                results = emptyList(),
                itunesResults = emptyList(),
                existingFeedUrls = emptySet(),
                existingTitles = emptySet(),
                isLoading = false,
                error = null,
            )
        }
        if (enabled) loadExistingFeedUrls()
    }

    private fun loadExistingFeedUrls() {
        viewModelScope.launch {
            val libraryId = prefs.activeLibraryId.first() ?: return@launch
            val feedUrls = mutableSetOf<String>()
            val titles = mutableSetOf<String>()
            var page = 0
            while (true) {
                val (items, _) = libraryRepository.getLibraryItems(libraryId, page = page, limit = 100).getOrNull() ?: break
                items.forEach { item ->
                    item.podcastMedia?.metadata?.feedUrl?.let { feedUrls.add(it) }
                    item.podcastMedia?.metadata?.title?.lowercase()?.let { titles.add(it) }
                }
                if (items.size < 100) break
                page++
            }
            _state.update { it.copy(existingFeedUrls = feedUrls, existingTitles = titles) }
        }
    }

    fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), itunesResults = emptyList(), isLoading = false) }
            return
        }
        if (_state.value.findNewMode) {
            searchItunes(query)
        } else {
            searchLibrary(query)
        }
    }

    private fun searchLibrary(query: String) {
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(isLoading = true) }
            val libraryId = prefs.activeLibraryId.first()
                ?: libraryRepository.getLibraries().getOrNull()?.firstOrNull()?.id
                ?: run { _state.update { it.copy(isLoading = false) }; return@launch }
            libraryRepository.search(libraryId, query).fold(
                onSuccess = { results -> _state.update { it.copy(isLoading = false, results = results) } },
                onFailure = { e -> _state.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    private fun searchItunes(query: String) {
        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(isLoading = true) }
            libraryRepository.searchItunesPodcasts(query).fold(
                onSuccess = { results ->
                    _state.update { it.copy(isLoading = false, itunesResults = results) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }
}
