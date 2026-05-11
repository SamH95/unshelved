package com.samwise.unshelved.feature.library

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.Library
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.MediaProgress
import com.samwise.unshelved.core.model.ProgressCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterAuthor(val id: String, val name: String)

data class LibraryState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<LibraryItem> = emptyList(),
    val libraries: List<Library> = emptyList(),
    val selectedLibraryId: String? = null,
    val isPodcastLibrary: Boolean = false,
    val isGridView: Boolean = true,
    val filterAuthors: List<FilterAuthor> = emptyList(),
    val filterGenres: List<String> = emptyList(),
    val filterNarrators: List<String> = emptyList(),
    val selectedAuthor: FilterAuthor? = null,
    val selectedGenre: String? = null,
    val selectedNarrator: String? = null,
    val progressMap: Map<String, MediaProgress> = emptyMap(),
    val error: String? = null,
    val total: Int = 0,
) {
    val hasFilters: Boolean
        get() = filterAuthors.isNotEmpty() || filterGenres.isNotEmpty() || filterNarrators.isNotEmpty()

    val hasActiveFilter: Boolean
        get() = selectedAuthor != null || selectedGenre != null || selectedNarrator != null
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    val progressCache: ProgressCache,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val cachedLibraryName = prefs.activeLibraryName.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val isGrid = prefs.libraryViewGrid.first()
            _state.update { it.copy(isGridView = isGrid) }
            loadLibraries()
            prefs.activeLibraryId.collect { id ->
                if (id != null && id != _state.value.selectedLibraryId) {
                    val isPodcast = _state.value.libraries.find { it.id == id }?.isPodcast ?: false
                    _state.update { it.copy(selectedLibraryId = id, isPodcastLibrary = isPodcast) }
                    loadItems(id)
                    loadFilterData(id)
                }
            }
        }
        viewModelScope.launch {
            prefs.selectedLibraryMediaType.collect { mediaType ->
                _state.update { it.copy(isPodcastLibrary = mediaType == "podcast") }
            }
        }
        viewModelScope.launch {
            libraryRepository.libraryInvalidated.collect { reloadSilently() }
        }
    }

    private suspend fun loadLibraries() {
        val libs = libraryRepository.getLibraries().getOrElse { emptyList() }
        val selectedId = prefs.activeLibraryId.first() ?: libs.firstOrNull()?.id
        if (selectedId != null && prefs.activeLibraryId.first() == null) {
            val lib = libs.find { it.id == selectedId }
            prefs.saveSelectedLibrary(selectedId, lib?.name, lib?.mediaType)
        }
        if (libs.isNotEmpty()) {
            _state.update { it.copy(libraries = libs, selectedLibraryId = selectedId) }
        } else {
            _state.update { it.copy(selectedLibraryId = selectedId) }
        }
        if (selectedId != null) {
            loadItems(selectedId)
            loadFilterData(selectedId)
        }
    }

    fun selectLibrary(libraryId: String) {
        viewModelScope.launch {
            val lib = _state.value.libraries.find { it.id == libraryId }
            prefs.saveSelectedLibrary(libraryId, lib?.name, lib?.mediaType)
            _state.update {
                it.copy(
                    selectedLibraryId = libraryId,
                    selectedAuthor = null,
                    selectedGenre = null,
                    selectedNarrator = null,
                )
            }
            loadItems(libraryId)
            loadFilterData(libraryId)
        }
    }

    fun loadItems(libraryId: String? = _state.value.selectedLibraryId) {
        if (libraryId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val filter = buildFilter()
            libraryRepository.getLibraryItems(libraryId = libraryId, filter = filter, limit = 200)
                .onSuccess { (items, total) ->
                    _state.update { it.copy(isLoading = false, items = items, total = total) }
                }
                .onFailure { e ->
                    handleLoadFailure(libraryId, filter, e)
                }
        }
    }

    private fun reloadSilently() {
        val libraryId = _state.value.selectedLibraryId ?: return
        viewModelScope.launch {
            val filter = buildFilter()
            libraryRepository.getLibraryItems(libraryId = libraryId, filter = filter, limit = 200)
                .onSuccess { (items, total) ->
                    _state.update { it.copy(items = items, total = total) }
                }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            progressCache.refresh()
            try {
                val libraryId = _state.value.selectedLibraryId ?: return@launch
                if (_state.value.libraries.isEmpty()) {
                    val libs = libraryRepository.getLibraries().getOrElse { emptyList() }
                    if (libs.isNotEmpty()) _state.update { it.copy(libraries = libs) }
                }
                libraryRepository.getLibraryItems(libraryId = libraryId, filter = buildFilter(), limit = 200)
                    .onSuccess { (items, total) ->
                        _state.update { it.copy(items = items, total = total) }
                    }
                loadFilterData(libraryId)
            } catch (e: Exception) {
                Log.e("LibraryVM", "refresh failed", e)
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun setFilter(author: FilterAuthor? = null, genre: String? = null, narrator: String? = null) {
        _state.update { it.copy(selectedAuthor = author, selectedGenre = genre, selectedNarrator = narrator) }
        loadItems()
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val newGrid = !_state.value.isGridView
            _state.update { it.copy(isGridView = newGrid) }
            prefs.setLibraryViewGrid(newGrid)
        }
    }

    private suspend fun loadFilterData(libraryId: String) {
        libraryRepository.getFilterData(libraryId)
            .onSuccess { data ->
                _state.update {
                    it.copy(
                        filterAuthors = data.authors.map { a -> FilterAuthor(a.id, a.name) },
                        filterGenres = data.genres,
                        filterNarrators = data.narrators,
                    )
                }
            }
            .onFailure {
                deriveFiltersFromItems(libraryId)
            }
    }

    private suspend fun deriveFiltersFromItems(libraryId: String) {
        val items = _state.value.items.ifEmpty {
            libraryRepository.getLibraryItems(libraryId, limit = 200).getOrNull()?.first ?: emptyList()
        }
        if (items.isEmpty()) return
        _state.update { s ->
            s.copy(
                filterAuthors = deriveAuthors(items),
                filterGenres = items.flatMap { it.media.metadata.genres }.distinct().sorted(),
                filterNarrators = deriveNarrators(items),
            )
        }
    }

    private suspend fun handleLoadFailure(libraryId: String, filter: String?, error: Throwable) {
        if (filter != null) {
            val baseItems = _state.value.items.ifEmpty {
                libraryRepository.getLibraryItems(libraryId, limit = 200).getOrNull()?.first ?: emptyList()
            }
            val filtered = applyFilterLocally(baseItems, _state.value)
            _state.update { it.copy(isLoading = false, items = filtered, total = filtered.size) }
        } else {
            _state.update { it.copy(isLoading = false, error = error.message) }
        }
        deriveFiltersFromItems(libraryId)
    }

    private fun applyFilterLocally(items: List<LibraryItem>, s: LibraryState): List<LibraryItem> {
        return when {
            s.selectedAuthor != null -> items.filter { item ->
                item.media.metadata.authors.any {
                    it.id == s.selectedAuthor.id || it.name.equals(s.selectedAuthor.name, ignoreCase = true)
                } || item.media.metadata.authorName
                    ?.split(",")?.any { it.trim().equals(s.selectedAuthor.name, ignoreCase = true) } == true
            }
            s.selectedGenre != null -> items.filter { item ->
                item.media.metadata.genres.any { it.equals(s.selectedGenre, ignoreCase = true) }
            }
            s.selectedNarrator != null -> items.filter { item ->
                item.media.metadata.narratorName
                    ?.split(",")?.any { it.trim().equals(s.selectedNarrator, ignoreCase = true) } == true
            }
            else -> items
        }
    }

    private fun buildFilter(): String? {
        val s = _state.value
        return when {
            s.selectedAuthor != null -> "authors.${b64(s.selectedAuthor.id)}"
            s.selectedGenre != null -> "genres.${b64(s.selectedGenre)}"
            s.selectedNarrator != null -> "narrators.${b64(s.selectedNarrator)}"
            else -> null
        }
    }

    private fun b64(value: String): String =
        Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)
}

private fun deriveAuthors(items: List<LibraryItem>): List<FilterAuthor> =
    items.flatMap { item ->
        val authors = item.media.metadata.authors
        if (authors.isNotEmpty()) {
            authors.map { FilterAuthor(it.id, it.name) }
        } else {
            item.media.metadata.authorName
                ?.split(",")?.map { n -> FilterAuthor(n.trim(), n.trim()) }
                ?: emptyList()
        }
    }.distinctBy { it.name }.sortedBy { it.name }

private fun deriveNarrators(items: List<LibraryItem>): List<String> =
    items.mapNotNull { it.media.metadata.narratorName }
        .flatMap { it.split(",").map { n -> n.trim() } }
        .filter { it.isNotEmpty() }.distinct().sorted()
