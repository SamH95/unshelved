package com.samwise.unshelved.core.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "unshelved_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val SELECTED_LIBRARY_ID = stringPreferencesKey("selected_library_id")
        val SELECTED_LIBRARY_NAME = stringPreferencesKey("selected_library_name")
        val JUMP_BACK_SECONDS = intPreferencesKey("jump_back_seconds")
        val JUMP_FORWARD_SECONDS = intPreferencesKey("jump_forward_seconds")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SELECTED_LIBRARY_MEDIA_TYPE = stringPreferencesKey("selected_library_media_type")
        val PODCAST_LIBRARY_ID = stringPreferencesKey("podcast_library_id")
        val PODCAST_LIBRARY_NAME = stringPreferencesKey("podcast_library_name")
        val LIBRARY_VIEW_GRID = booleanPreferencesKey("library_view_grid")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val AUTO_DELETE_FINISHED = booleanPreferencesKey("auto_delete_finished")
        val LOGOUT_REASON = stringPreferencesKey("logout_reason")
    }

    val serverUrl: Flow<String?> = dataStore.data.map { it[SERVER_URL] }
    val authToken: Flow<String?> = dataStore.data.map { it[AUTH_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[REFRESH_TOKEN] }
    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }
    val username: Flow<String?> = dataStore.data.map { it[USERNAME] }
    val selectedLibraryId: Flow<String?> = dataStore.data.map { it[SELECTED_LIBRARY_ID] }
    val selectedLibraryName: Flow<String?> = dataStore.data.map { it[SELECTED_LIBRARY_NAME] }
    val selectedLibraryMediaType: Flow<String?> = dataStore.data.map { it[SELECTED_LIBRARY_MEDIA_TYPE] }
    val podcastLibraryId: Flow<String?> = dataStore.data.map { it[PODCAST_LIBRARY_ID] }
    val podcastLibraryName: Flow<String?> = dataStore.data.map { it[PODCAST_LIBRARY_NAME] }
    val activeLibraryId: Flow<String?> = combine(selectedLibraryMediaType, selectedLibraryId, podcastLibraryId) { type, bookId, podId ->
        if (type == "podcast") podId else bookId
    }
    val activeLibraryName: Flow<String?> = combine(selectedLibraryMediaType, selectedLibraryName, podcastLibraryName) { type, bookName, podName ->
        if (type == "podcast") podName else bookName
    }
    val jumpBackSeconds: Flow<Int> = dataStore.data.map { it[JUMP_BACK_SECONDS] ?: 10 }
    val jumpForwardSeconds: Flow<Int> = dataStore.data.map { it[JUMP_FORWARD_SECONDS] ?: 30 }
    val playbackSpeed: Flow<Float> = dataStore.data.map { it[PLAYBACK_SPEED] ?: 1f }
    val libraryViewGrid: Flow<Boolean> = dataStore.data.map { it[LIBRARY_VIEW_GRID] ?: true }
    val offlineMode: Flow<Boolean> = dataStore.data.map { it[OFFLINE_MODE] ?: false }
    val autoDeleteFinished: Flow<Boolean> = dataStore.data.map { it[AUTO_DELETE_FINISHED] ?: false }
    val logoutReason: Flow<String?> = dataStore.data.map { it[LOGOUT_REASON] }

    suspend fun saveLoginData(serverUrl: String, token: String, refreshToken: String?, userId: String, username: String) {
        dataStore.edit {
            it[SERVER_URL] = serverUrl
            it[AUTH_TOKEN] = token
            if (refreshToken != null) it[REFRESH_TOKEN] = refreshToken else it.remove(REFRESH_TOKEN)
            it[USER_ID] = userId
            it[USERNAME] = username
        }
    }

    suspend fun saveSelectedLibrary(libraryId: String, libraryName: String? = null, mediaType: String? = null) {
        dataStore.edit {
            it[SELECTED_LIBRARY_ID] = libraryId
            if (libraryName != null) it[SELECTED_LIBRARY_NAME] = libraryName
            if (mediaType != null) it[SELECTED_LIBRARY_MEDIA_TYPE] = mediaType
        }
    }

    suspend fun savePodcastLibrary(libraryId: String, libraryName: String? = null) {
        dataStore.edit {
            it[PODCAST_LIBRARY_ID] = libraryId
            if (libraryName != null) it[PODCAST_LIBRARY_NAME] = libraryName
        }
    }

    suspend fun clearAudiobookLibrary() {
        dataStore.edit {
            it.remove(SELECTED_LIBRARY_ID)
            it.remove(SELECTED_LIBRARY_NAME)
        }
    }

    suspend fun clearPodcastLibrary() {
        dataStore.edit {
            it.remove(PODCAST_LIBRARY_ID)
            it.remove(PODCAST_LIBRARY_NAME)
        }
    }

    suspend fun switchMode(mediaType: String) {
        dataStore.edit { it[SELECTED_LIBRARY_MEDIA_TYPE] = mediaType }
    }

    suspend fun saveJumpSeconds(back: Int, forward: Int) {
        dataStore.edit {
            it[JUMP_BACK_SECONDS] = back
            it[JUMP_FORWARD_SECONDS] = forward
        }
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        dataStore.edit { it[PLAYBACK_SPEED] = speed }
    }

    suspend fun setLibraryViewGrid(grid: Boolean) {
        dataStore.edit { it[LIBRARY_VIEW_GRID] = grid }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { it[OFFLINE_MODE] = enabled }
    }

    suspend fun setAutoDeleteFinished(enabled: Boolean) {
        dataStore.edit { it[AUTO_DELETE_FINISHED] = enabled }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    suspend fun clearAuth(reason: String? = null) {
        dataStore.edit {
            val url = it[SERVER_URL]
            if (url != null && (it[SELECTED_LIBRARY_ID] != null || it[PODCAST_LIBRARY_ID] != null)) {
                val key = serverLibraryKey(url)
                val json = JSONObject().apply {
                    put("libraryId", it[SELECTED_LIBRARY_ID] ?: "")
                    put("libraryName", it[SELECTED_LIBRARY_NAME] ?: "")
                    put("mediaType", it[SELECTED_LIBRARY_MEDIA_TYPE] ?: "")
                    put("podcastLibraryId", it[PODCAST_LIBRARY_ID] ?: "")
                    put("podcastLibraryName", it[PODCAST_LIBRARY_NAME] ?: "")
                }
                Log.d("UserPrefs", "Saving library settings for $key: $json")
                it[stringPreferencesKey(key)] = json.toString()
            } else {
                Log.d("UserPrefs", "clearAuth: no library settings to save (url=$url, libId=${it[SELECTED_LIBRARY_ID]}, podId=${it[PODCAST_LIBRARY_ID]})")
            }
            it.remove(AUTH_TOKEN)
            it.remove(REFRESH_TOKEN)
            it.remove(USER_ID)
            it.remove(USERNAME)
            it.remove(SELECTED_LIBRARY_ID)
            it.remove(SELECTED_LIBRARY_NAME)
            it.remove(SELECTED_LIBRARY_MEDIA_TYPE)
            it.remove(PODCAST_LIBRARY_ID)
            it.remove(PODCAST_LIBRARY_NAME)
            if (reason != null) it[LOGOUT_REASON] = reason else it.remove(LOGOUT_REASON)
        }
    }

    suspend fun restoreLibrarySettingsForServer(serverUrl: String): Boolean {
        val key = stringPreferencesKey(serverLibraryKey(serverUrl))
        val json = dataStore.data.first()[key]
        if (json == null) {
            Log.d("UserPrefs", "restoreLibrarySettings: no saved settings for ${serverLibraryKey(serverUrl)}")
            return false
        }
        return try {
            val obj = JSONObject(json)
            Log.d("UserPrefs", "restoreLibrarySettings: restoring $json")
            dataStore.edit {
                val libId = obj.optString("libraryId", "")
                if (libId.isNotEmpty()) {
                    it[SELECTED_LIBRARY_ID] = libId
                    it[SELECTED_LIBRARY_NAME] = obj.optString("libraryName", "")
                }
                val mediaType = obj.optString("mediaType", "")
                if (mediaType.isNotEmpty()) it[SELECTED_LIBRARY_MEDIA_TYPE] = mediaType
                val podId = obj.optString("podcastLibraryId", "")
                if (podId.isNotEmpty()) {
                    it[PODCAST_LIBRARY_ID] = podId
                    it[PODCAST_LIBRARY_NAME] = obj.optString("podcastLibraryName", "")
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun serverLibraryKey(serverUrl: String): String {
        val normalized = serverUrl.lowercase().trimEnd('/')
        return "server_libs:$normalized"
    }

    suspend fun clearLogoutReason() {
        dataStore.edit { it.remove(LOGOUT_REASON) }
    }

    suspend fun isLoggedIn(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[AUTH_TOKEN] != null && prefs[SERVER_URL] != null
    }

    suspend fun getRefreshToken(): String? = dataStore.data.first()[REFRESH_TOKEN]
    suspend fun getServerUrl(): String? = dataStore.data.first()[SERVER_URL]
    suspend fun getAuthToken(): String? = dataStore.data.first()[AUTH_TOKEN]
}
