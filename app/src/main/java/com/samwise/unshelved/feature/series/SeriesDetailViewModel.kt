package com.samwise.unshelved.feature.series

import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.LibraryItem
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.feature.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailState(
    val isLoading: Boolean = false,
    val seriesName: String = "",
    val books: List<LibraryItem> = emptyList(),
    val isGridView: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    val progressCache: ProgressCache,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])
    val currentSeriesId: String get() = seriesId

    private val _state = MutableStateFlow(SeriesDetailState())
    val state = _state.asStateFlow()

    val serverUrl = prefs.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val seriesResult = libraryRepository.getSeriesDetail(seriesId)
            val series = seriesResult.getOrNull()
            val seriesName = series?.name ?: ""
            val seriesBooks = series?.books ?: emptyList()

            val libraryId = prefs.activeLibraryId.first()
            if (libraryId == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        seriesName = seriesName,
                        books = seriesBooks,
                        error = if (seriesBooks.isEmpty()) "No library selected" else null,
                    )
                }
                return@launch
            }

            val encoded = Base64.encodeToString(seriesId.toByteArray(), Base64.NO_WRAP)
            libraryRepository.getLibraryItems(
                libraryId = libraryId,
                filter = "series.$encoded",
                limit = 200,
            ).fold(
                onSuccess = { (items, _) ->
                    _state.update { it.copy(isLoading = false, seriesName = seriesName, books = items) }
                },
                onFailure = { e ->
                    Log.e("SeriesDetailVM", "items load failed, using series books fallback", e)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            seriesName = seriesName,
                            books = seriesBooks,
                            error = if (seriesBooks.isEmpty()) e.message else null,
                        )
                    }
                },
            )
        }
    }

    fun toggleViewMode() {
        _state.update { it.copy(isGridView = !it.isGridView) }
    }
}
