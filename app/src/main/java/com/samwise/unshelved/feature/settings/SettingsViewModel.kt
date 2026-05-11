package com.samwise.unshelved.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.Library
import com.samwise.unshelved.feature.auth.AuthRepository
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val username: String = "",
    val serverUrl: String = "",
    val audiobookLibraryId: String? = null,
    val audiobookLibraryName: String? = null,
    val podcastLibraryId: String? = null,
    val podcastLibraryName: String? = null,
    val jumpBackSeconds: Int = 10,
    val jumpForwardSeconds: Int = 30,
    val isOfflineMode: Boolean = false,
    val autoDeleteFinished: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        combine(prefs.username, prefs.serverUrl) { username, url -> username to url },
        combine(prefs.selectedLibraryId, prefs.selectedLibraryName, prefs.podcastLibraryId, prefs.podcastLibraryName) { bookId, bookName, podId, podName ->
            arrayOf(bookId, bookName, podId, podName)
        },
        combine(prefs.jumpBackSeconds, prefs.jumpForwardSeconds, prefs.offlineMode, prefs.autoDeleteFinished) { jumpBack, jumpFwd, offline, autoDelete ->
            arrayOf(jumpBack, jumpFwd, offline, autoDelete)
        },
    ) { (username, url), libArr, settingsArr ->
        SettingsState(
            username = username ?: "",
            serverUrl = url ?: "",
            audiobookLibraryId = libArr[0],
            audiobookLibraryName = libArr[1],
            podcastLibraryId = libArr[2],
            podcastLibraryName = libArr[3],
            jumpBackSeconds = settingsArr[0] as Int,
            jumpForwardSeconds = settingsArr[1] as Int,
            isOfflineMode = settingsArr[2] as Boolean,
            autoDeleteFinished = settingsArr[3] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    val selectedMediaType: StateFlow<String?> = prefs.selectedLibraryMediaType
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasBothLibraries: StateFlow<Boolean> = combine(prefs.selectedLibraryId, prefs.podcastLibraryId) { bookId, podId ->
        bookId != null && podId != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries = _libraries.asStateFlow()

    init {
        loadLibraries()
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            if (prefs.offlineMode.first()) return@launch
            _libraries.value = libraryRepository.getLibraries().getOrElse { emptyList() }
        }
    }

    fun selectAudiobookLibrary(libraryId: String?) {
        viewModelScope.launch {
            if (libraryId == null) {
                prefs.clearAudiobookLibrary()
                prefs.switchMode("podcast")
            } else {
                val lib = _libraries.value.find { it.id == libraryId }
                prefs.saveSelectedLibrary(libraryId, lib?.name)
            }
        }
    }

    fun selectPodcastLibrary(libraryId: String?) {
        viewModelScope.launch {
            if (libraryId == null) {
                prefs.clearPodcastLibrary()
                prefs.switchMode("book")
            } else {
                val lib = _libraries.value.find { it.id == libraryId }
                prefs.savePodcastLibrary(libraryId, lib?.name)
            }
        }
    }

    fun switchMode() {
        viewModelScope.launch {
            val current = prefs.selectedLibraryMediaType.first()
            val newMode = if (current == "podcast") "book" else "podcast"
            prefs.switchMode(newMode)
        }
    }

    fun setJumpSeconds(back: Int, forward: Int) {
        viewModelScope.launch { prefs.saveJumpSeconds(back, forward) }
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setOfflineMode(enabled)
            if (!enabled) playerRepository.syncOfflineProgress()
        }
    }

    fun setAutoDeleteFinished(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoDeleteFinished(enabled) }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }

    fun getCurrentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "" else locales.toLanguageTags()
    }

    fun setLanguage(localeTag: String) {
        val localeList = if (localeTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(localeTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
