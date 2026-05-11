package com.samwise.unshelved.core.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private sealed class RefreshResult {
    data class Success(val response: Response) : RefreshResult()
    data object Rejected : RefreshResult()
    data object NetworkError : RefreshResult()
}

@Singleton
class AuthInterceptor @Inject constructor(
    private val prefs: UserPreferencesRepository,
) : Interceptor {

    var onUnauthorized: (() -> Unit)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { prefs.authToken.firstOrNull() }
        val original = chain.request()
        val request = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        val response = chain.proceed(request)

        if (response.code == 401 && token != null) {
            val refreshToken = runBlocking { prefs.getRefreshToken() }
            if (refreshToken != null) {
                response.close()
                when (val result = tryRefresh(chain, refreshToken)) {
                    is RefreshResult.Success -> return result.response
                    is RefreshResult.NetworkError -> return chain.proceed(request)
                    is RefreshResult.Rejected -> {
                        onUnauthorized?.invoke()
                        return chain.proceed(request)
                    }
                }
            }
            onUnauthorized?.invoke()
        }
        return response
    }

    private fun tryRefresh(chain: Interceptor.Chain, refreshToken: String): RefreshResult {
        return try {
            val serverUrl = runBlocking { prefs.getServerUrl() } ?: return RefreshResult.Rejected
            val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
            val refreshRequest = Request.Builder()
                .url("${baseUrl}auth/refresh")
                .post(ByteArray(0).toRequestBody())
                .header("x-refresh-token", refreshToken)
                .build()
            val refreshResponse = OkHttpClient().newCall(refreshRequest).execute()
            if (!refreshResponse.isSuccessful) return RefreshResult.Rejected

            val body = refreshResponse.body?.string() ?: return RefreshResult.Rejected
            val loginResp = Gson().fromJson(body, LoginResponse::class.java)
            val newToken = loginResp.user.effectiveToken
            val newRefresh = loginResp.user.refreshToken

            runBlocking {
                prefs.saveLoginData(
                    serverUrl = serverUrl,
                    token = newToken,
                    refreshToken = newRefresh,
                    userId = loginResp.user.id,
                    username = loginResp.user.username,
                )
            }

            val retryRequest = chain.request().newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
            RefreshResult.Success(chain.proceed(retryRequest))
        } catch (e: IOException) {
            Log.w("AuthInterceptor", "Token refresh network error", e)
            RefreshResult.NetworkError
        } catch (e: Exception) {
            Log.w("AuthInterceptor", "Token refresh failed", e)
            RefreshResult.Rejected
        }
    }
}

fun buildOkHttpClient(authInterceptor: AuthInterceptor, context: Context): OkHttpClient {
    val cacheDir = File(context.cacheDir, "http_cache")
    val cache = Cache(cacheDir, 20L * 1024 * 1024)

    return OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()
}

fun buildRetrofit(baseUrl: String, okHttpClient: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
