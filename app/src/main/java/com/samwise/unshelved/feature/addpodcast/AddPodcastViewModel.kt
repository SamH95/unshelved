package com.samwise.unshelved.feature.addpodcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.database.AutoDownloadDao
import com.samwise.unshelved.core.database.AutoDownloadEntity
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.PodcastFeedPreview
import com.samwise.unshelved.core.model.toDomain
import com.samwise.unshelved.core.network.PodcastFeedEpisodeDto
import com.samwise.unshelved.core.network.PodcastFeedResponse
import com.samwise.unshelved.core.network.PodcastMetadataDto
import com.samwise.unshelved.feature.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class AddPodcastState(
    val feedPreview: PodcastFeedPreview? = null,
    val isLoadingFeed: Boolean = true,
    val isCreating: Boolean = false,
    val createdItemId: String? = null,
    val autoDownloadEpisodes: Boolean = false,
    val coverUrl: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AddPodcastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val prefs: UserPreferencesRepository,
    private val autoDownloadDao: AutoDownloadDao,
) : ViewModel() {

    private val feedUrl: String = savedStateHandle["feedUrl"] ?: ""
    private val itunesId: Long = savedStateHandle["itunesId"] ?: 0L
    private val itunesArtistId: Long = savedStateHandle["itunesArtistId"] ?: 0L
    private val itunesPageUrl: String = (savedStateHandle.get<String>("itunesPageUrl") ?: "").ifBlank { "" }

    private val _state = MutableStateFlow(
        AddPodcastState(coverUrl = savedStateHandle["coverUrl"]),
    )
    val state: StateFlow<AddPodcastState> = _state.asStateFlow()

    val serverUrl: StateFlow<String?> = prefs.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var rawEpisodes: List<PodcastFeedEpisodeDto> = emptyList()

    init {
        if (feedUrl.isNotBlank()) {
            loadFeed()
        } else {
            _state.update { it.copy(isLoadingFeed = false, error = "No feed URL provided") }
        }
    }

    private fun loadFeed() {
        viewModelScope.launch {
            libraryRepository.getPodcastFeedRaw(feedUrl).fold(
                onSuccess = { feedDto ->
                    rawEpisodes = feedDto.episodes
                    val preview = PodcastFeedResponse(feedDto).toDomain()
                    _state.update { it.copy(feedPreview = preview, isLoadingFeed = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoadingFeed = false, error = e.message) }
                },
            )
        }
    }

    fun toggleAutoDownload() {
        _state.update { it.copy(autoDownloadEpisodes = !it.autoDownloadEpisodes) }
    }

    private fun recentEpisodes(): List<PodcastFeedEpisodeDto> {
        val cutoff = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        val dateFormats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        )

        fun parsePubDate(pubDate: String?): Long? {
            if (pubDate == null) return null
            for (fmt in dateFormats) {
                runCatching { fmt.parse(pubDate)?.time }
                    .getOrNull()?.let { return it }
            }
            return null
        }

        return rawEpisodes
            .filter { ep ->
                val ts = ep.publishedAt ?: parsePubDate(ep.pubDate)
                ts == null || ts >= cutoff
            }
            .take(10)
    }

    fun addPodcast() {
        val preview = _state.value.feedPreview ?: return
        _state.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            val libraryId = prefs.activeLibraryId.first() ?: return@launch
            val libraries = libraryRepository.getLibraries().getOrNull() ?: return@launch
            val library = libraries.find { it.id == libraryId } ?: return@launch
            val folder = library.folders.firstOrNull() ?: return@launch

            val metadata = preview.metadata
            val title = metadata.title
            val path = "${folder.fullPath}/$title"

            val metadataDto = PodcastMetadataDto(
                title = metadata.title,
                titleIgnorePrefix = metadata.titleIgnorePrefix,
                author = metadata.author,
                description = metadata.description,
                releaseDate = metadata.releaseDate,
                genres = metadata.genres.ifEmpty { null },
                feedUrl = metadata.feedUrl,
                imageUrl = metadata.imageUrl ?: _state.value.coverUrl,
                itunesPageUrl = itunesPageUrl.ifBlank { null },
                itunesId = if (itunesId != 0L) itunesId else null,
                itunesArtistId = if (itunesArtistId != 0L) itunesArtistId else null,
                explicit = metadata.explicit,
                language = metadata.language,
                type = metadata.type,
            )

            val episodes = recentEpisodes()

            libraryRepository.createPodcast(
                libraryId = libraryId,
                folderId = folder.id,
                path = path,
                metadata = metadataDto,
                autoDownload = _state.value.autoDownloadEpisodes,
            ).fold(
                onSuccess = { item ->
                    if (_state.value.autoDownloadEpisodes) {
                        autoDownloadDao.enable(AutoDownloadEntity(item.id))
                    }
                    if (episodes.isNotEmpty()) {
                        libraryRepository.downloadPodcastEpisodes(item.id, episodes)
                    }
                    _state.update { it.copy(isCreating = false, createdItemId = item.id) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isCreating = false, error = e.message) }
                },
            )
        }
    }
}
