package com.samwise.unshelved.core.network

import android.content.Context
import android.util.Log
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiProvider @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val authInterceptor: AuthInterceptor,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _unauthorizedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorizedEvent = _unauthorizedEvent.asSharedFlow()

    @Volatile private var currentUrl: String? = null
    @Volatile private var api: AbsApi? = null
    @Volatile private var logoutJob: Job? = null
    @Volatile var suppressUnauthorized: Boolean = false

    fun getApi(): AbsApi {
        val serverUrl = runBlocking { prefs.serverUrl.firstOrNull() } ?: ""
        if (serverUrl != currentUrl || api == null) {
            synchronized(this) {
                if (serverUrl != currentUrl || api == null) {
                    val client = buildOkHttpClient(authInterceptor, context)
                    api = buildRetrofit(serverUrl, client).create(AbsApi::class.java)
                    currentUrl = serverUrl
                    authInterceptor.onUnauthorized = { onUnauthorizedReceived() }
                }
            }
        }
        return api!!
    }

    fun reset() {
        synchronized(this) { api = null; currentUrl = null }
    }

    private fun onUnauthorizedReceived() {
        if (suppressUnauthorized) return
        if (logoutJob?.isActive == true) return
        logoutJob = scope.launch {
            Log.w("ApiProvider", "Token refresh failed or no refresh token, clearing session")
            prefs.clearAuth(reason = "Your session expired. Please sign in again.")
            reset()
            _unauthorizedEvent.emit(Unit)
        }
    }
}
