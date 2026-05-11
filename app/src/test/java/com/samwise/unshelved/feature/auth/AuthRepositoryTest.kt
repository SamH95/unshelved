package com.samwise.unshelved.feature.auth

import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.ProgressCache
import com.samwise.unshelved.core.network.AbsApi
import com.samwise.unshelved.core.network.ApiProvider
import com.samwise.unshelved.core.network.AuthorizeResponse
import com.samwise.unshelved.core.network.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {

    private val testUser = UserDto(
        id = "user-1",
        username = "alice",
        email = "alice@example.com",
        token = "new-token",
        refreshToken = "new-refresh",
        mediaProgress = emptyList(),
    )

    private fun createRepository(): Triple<AuthRepository, UserPreferencesRepository, ApiProvider> {
        val prefs = mockk<UserPreferencesRepository>(relaxUnitFun = true) {
            coEvery { getServerUrl() } returns "https://abs.example.com"
            coEvery { getRefreshToken() } returns null
        }

        val api = mockk<AbsApi>(relaxUnitFun = true) {
            coEvery { authorize() } returns Response.success(AuthorizeResponse(user = testUser, userDefaultLibraryId = null))
            coEvery { logout() } returns Response.success(Unit)
        }

        val apiProvider = mockk<ApiProvider>(relaxUnitFun = true) {
            every { getApi() } returns api
        }

        val progressCache = mockk<ProgressCache>(relaxUnitFun = true)

        val repo = AuthRepository(
            prefs = prefs,
            apiProvider = apiProvider,
            progressCache = progressCache,
        )

        return Triple(repo, prefs, apiProvider)
    }

    @Test
    fun `validateToken returns true and saves data on success`() = runTest {
        val (repo, prefs) = createRepository()

        val result = repo.validateToken()

        assertTrue(result)
        coVerify {
            prefs.saveLoginData(
                serverUrl = "https://abs.example.com",
                token = "new-token",
                refreshToken = "new-refresh",
                userId = "user-1",
                username = "alice",
            )
        }
    }

    @Test
    fun `validateToken returns false on API failure`() = runTest {
        val (repo, prefs, apiProvider) = createRepository()
        val api = mockk<AbsApi> {
            coEvery { authorize() } returns Response.error(401, "".toResponseBody())
        }
        every { apiProvider.getApi() } returns api

        val result = repo.validateToken()

        assertFalse(result)
    }

    @Test
    fun `validateToken returns false on exception`() = runTest {
        val (repo, prefs, apiProvider) = createRepository()
        val api = mockk<AbsApi> {
            coEvery { authorize() } throws RuntimeException("network error")
        }
        every { apiProvider.getApi() } returns api

        val result = repo.validateToken()

        assertFalse(result)
    }

    @Test
    fun `logout clears auth and resets API`() = runTest {
        val (repo, prefs, apiProvider) = createRepository()

        repo.logout()

        coVerify { prefs.clearAuth() }
        verify { apiProvider.reset() }
    }

    @Test
    fun `logout with refresh token calls logoutWithToken`() = runTest {
        val (repo, prefs, apiProvider) = createRepository()
        coEvery { prefs.getRefreshToken() } returns "refresh-123"
        val api = apiProvider.getApi()

        repo.logout()

        coVerify { api.logoutWithToken("refresh-123") }
        coVerify { prefs.clearAuth() }
    }

    @Test
    fun `logout without refresh token calls plain logout`() = runTest {
        val (repo, prefs, apiProvider) = createRepository()
        coEvery { prefs.getRefreshToken() } returns null
        val api = apiProvider.getApi()

        repo.logout()

        coVerify { api.logout() }
    }

    @Test
    fun `logout still clears auth on API exception`() = runTest {
        val (repo, prefs, apiProvider) = createRepository()
        val api = mockk<AbsApi> {
            coEvery { logout() } throws RuntimeException("offline")
        }
        every { apiProvider.getApi() } returns api
        coEvery { prefs.getRefreshToken() } returns null

        repo.logout()

        coVerify { prefs.clearAuth() }
        verify { apiProvider.reset() }
    }
}
