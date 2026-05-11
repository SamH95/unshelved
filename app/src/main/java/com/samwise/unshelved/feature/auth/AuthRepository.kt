package com.samwise.unshelved.feature.auth

import android.util.Log
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.network.AbsApi
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.LoginRequest
import com.samwise.unshelved.core.network.LoginResponse
import com.samwise.unshelved.core.network.PkceUtils
import com.samwise.unshelved.core.network.buildRetrofit
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val username: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class TokenValidationResult {
    data object Valid : TokenValidationResult()
    data object Rejected : TokenValidationResult()
    data object NetworkError : TokenValidationResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val apiProvider: ApiProvider,
    private val progressCache: ProgressCache,
) {
    private val oidcCookieJar = object : CookieJar {
        private val cookies = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this.cookies.addAll(cookies) }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.toList()
        fun clear() { cookies.clear() }
    }

    suspend fun login(serverUrl: String, username: String, password: String): AuthResult {
        return try {
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
            val api = buildRetrofit(serverUrl, client).create(AbsApi::class.java)
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return AuthResult.Error("Login succeeded but response body was empty")
                saveLoginAndSetup(serverUrl, body)
            } else {
                AuthResult.Error("Login failed: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    fun getOidcAuthUrl(serverUrl: String): Triple<String, String, String>? {
        val verifier = PkceUtils.generateVerifier()
        val challenge = PkceUtils.generateChallenge(verifier)
        val state = PkceUtils.generateState()

        val url = buildString {
            append(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
            append("auth/openid")
            append("?code_challenge=$challenge")
            append("&code_challenge_method=S256")
            append("&redirect_uri=audiobookshelf://oauth")
            append("&client_id=Unshelved")
            append("&response_type=code")
            append("&state=$state")
        }

        return try {
            oidcCookieJar.clear()
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .cookieJar(oidcCookieJar)
                .build()
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val location = response.header("Location")
            if (response.code == 302 && location != null) {
                Triple(location, verifier, state)
            } else {
                Log.e(TAG, "OIDC auth request failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "OIDC auth request error", e)
            null
        }
    }

    suspend fun exchangeOidcCode(serverUrl: String, code: String, state: String, verifier: String): AuthResult {
        return try {
            val url = buildString {
                append(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                append("auth/openid/callback")
                append("?state=$state")
                append("&code=$code")
                append("&code_verifier=$verifier")
            }

            Log.d(TAG, "Exchanging OIDC code at: ${serverUrl}/auth/openid/callback")
            val client = OkHttpClient.Builder()
                .cookieJar(oidcCookieJar)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            val responseBody = response.body?.string()
            Log.d(TAG, "OIDC callback response: ${response.code} body=${responseBody?.take(500)}")

            if (response.isSuccessful && responseBody != null) {
                val body = Gson().fromJson(responseBody, LoginResponse::class.java)
                saveLoginAndSetup(serverUrl, body)
            } else {
                AuthResult.Error("SSO login failed (${response.code}): ${responseBody?.take(200) ?: "No response"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OIDC exchange failed", e)
            AuthResult.Error("SSO login failed: ${e.message}")
        }
    }

    fun checkOidcSupport(serverUrl: String): Boolean {
        return try {
            val url = buildString {
                append(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                append("status")
            }
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                body.contains("\"openid\"")
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun saveLoginAndSetup(serverUrl: String, body: LoginResponse): AuthResult {
        prefs.saveLoginData(
            serverUrl = serverUrl,
            token = body.user.effectiveToken,
            refreshToken = body.user.refreshToken,
            userId = body.user.id,
            username = body.user.username,
        )
        apiProvider.reset()

        val libs = try {
            apiProvider.getApi().getLibraries().body()?.libraries.orEmpty()
        } catch (_: Exception) { emptyList() }

        val libIds = libs.map { it.id }.toSet()
        val restored = prefs.restoreLibrarySettingsForServer(serverUrl)

        if (restored) {
            val savedBookLib = prefs.selectedLibraryId.first()
            val savedPodLib = prefs.podcastLibraryId.first()
            if (savedBookLib != null && savedBookLib !in libIds) prefs.clearAudiobookLibrary()
            if (savedPodLib != null && savedPodLib !in libIds) prefs.clearPodcastLibrary()
        }

        if (!restored || prefs.selectedLibraryId.first() == null) {
            val bookLib = libs.firstOrNull { it.mediaType != "podcast" }
            if (bookLib != null) prefs.saveSelectedLibrary(bookLib.id, bookLib.name)
        }
        if (!restored || prefs.podcastLibraryId.first() == null) {
            val podLib = libs.firstOrNull { it.mediaType == "podcast" }
            if (podLib != null) prefs.savePodcastLibrary(podLib.id, podLib.name)
        }

        if (prefs.selectedLibraryMediaType.first() == null) {
            val defaultLib = libs.find { it.id == body.userDefaultLibraryId } ?: libs.firstOrNull()
            if (defaultLib != null) prefs.switchMode(defaultLib.mediaType)
        }

        progressCache.update(body.user.mediaProgress)
        return AuthResult.Success(body.user.username)
    }

    suspend fun validateToken(): TokenValidationResult {
        apiProvider.suppressUnauthorized = true
        return try {
            val response = apiProvider.getApi().authorize()
            if (response.isSuccessful) {
                val body = response.body()
                    ?: return TokenValidationResult.Rejected
                prefs.saveLoginData(
                    serverUrl = prefs.getServerUrl() ?: "",
                    token = body.user.effectiveToken,
                    refreshToken = body.user.refreshToken,
                    userId = body.user.id,
                    username = body.user.username,
                )
                progressCache.update(body.user.mediaProgress)
                detectLibraries()
                TokenValidationResult.Valid
            } else {
                TokenValidationResult.Rejected
            }
        } catch (e: java.io.IOException) {
            TokenValidationResult.NetworkError
        } catch (e: Exception) {
            TokenValidationResult.NetworkError
        } finally {
            apiProvider.suppressUnauthorized = false
        }
    }

    private suspend fun detectLibraries() {
        val libs = try {
            apiProvider.getApi().getLibraries().body()?.libraries.orEmpty()
        } catch (_: Exception) { return }

        if (prefs.selectedLibraryId.first() == null) {
            val bookLib = libs.firstOrNull { it.mediaType != "podcast" }
            if (bookLib != null) prefs.saveSelectedLibrary(bookLib.id, bookLib.name)
        }
        if (prefs.podcastLibraryId.first() == null) {
            val podLib = libs.firstOrNull { it.mediaType == "podcast" }
            if (podLib != null) prefs.savePodcastLibrary(podLib.id, podLib.name)
        }
        if (prefs.selectedLibraryMediaType.first() == null) {
            val defaultType = libs.firstOrNull()?.mediaType ?: "book"
            prefs.switchMode(defaultType)
        }
    }

    suspend fun logout() {
        try {
            val refreshToken = prefs.getRefreshToken()
            if (refreshToken != null) {
                apiProvider.getApi().logoutWithToken(refreshToken)
            } else {
                apiProvider.getApi().logout()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Logout API call failed", e)
        }
        prefs.clearAuth()
        apiProvider.reset()
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
