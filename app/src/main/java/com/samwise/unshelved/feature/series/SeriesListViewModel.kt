package com.samwise.unshelved.feature.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.Series
import com.samwise.unshelved.feature.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val series: List<Series> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class SeriesListViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesListState())
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            prefs.activeLibraryId.collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val libraryId = prefs.activeLibraryId.first()
                ?: libraryRepository.getLibraries().getOrNull()?.firstOrNull()?.id
            if (libraryId == null) {
                _state.update { it.copy(isLoading = false, error = "No library selected") }
                return@launch
            }
            libraryRepository.getLibrarySeries(libraryId).fold(
                onSuccess = { series ->
                    _state.update { it.copy(isLoading = false, series = series) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                },
            )
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            try {
                val libraryId = prefs.activeLibraryId.first() ?: return@launch
                libraryRepository.getLibrarySeries(libraryId).onSuccess { series ->
                    _state.update { it.copy(series = series) }
                }
            } catch (e: Exception) {
                Log.e("SeriesListVM", "refresh failed", e)
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
