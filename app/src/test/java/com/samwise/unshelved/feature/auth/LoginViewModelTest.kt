package com.samwise.unshelved.feature.auth

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.service.DownloadRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        savedServerUrl: String? = null,
        logoutReason: String? = null,
        hasCompletedDownloads: Boolean = false,
        authResult: AuthResult = AuthResult.Success("testuser"),
        oidcSupported: Boolean = false,
    ): Pair<LoginViewModel, AuthRepository> {
        val prefs = mockk<UserPreferencesRepository>(relaxUnitFun = true) {
            every { serverUrl } returns flowOf(savedServerUrl)
            every { this@mockk.logoutReason } returns flowOf(logoutReason)
        }

        val authRepository = mockk<AuthRepository> {
            coEvery { login(any(), any(), any()) } returns authResult
            coEvery { checkOidcSupport(any()) } returns oidcSupported
        }

        val downloadEntity: List<DownloadEntity> = if (hasCompletedDownloads) {
            listOf(
                DownloadEntity(
                    downloadId = "item-1",
                    libraryItemId = "item-1",
                    title = "Test Book",
                    author = "Author",
                    coverPath = null,
                    localPath = "/path",
                    totalBytes = 1000,
                    downloadedBytes = 1000,
                    status = DownloadStatus.COMPLETED,
                ),
            )
        } else {
            emptyList()
        }
        val downloadRepository = mockk<DownloadRepository> {
            every { allDownloads } returns flowOf(downloadEntity)
        }

        val vm = LoginViewModel(
            authRepository = authRepository,
            prefs = prefs,
            downloadRepository = downloadRepository,
        )

        return Pair(vm, authRepository)
    }

    @Test
    fun `initial state has defaults`() = runTest {
        val (vm) = createViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertFalse(state.success)
        assertNull(state.oidcSupported)
        assertFalse(state.showPasswordForm)
    }

    @Test
    fun `saved server URL is populated`() = runTest {
        val (vm) = createViewModel(savedServerUrl = "https://abs.example.com")

        assertEquals("https://abs.example.com", vm.state.value.savedServerUrl)
    }

    @Test
    fun `logout reason is populated`() = runTest {
        val (vm) = createViewModel(logoutReason = "Session expired")

        assertEquals("Session expired", vm.state.value.logoutReason)
    }

    @Test
    fun `login with blank fields shows error`() = runTest {
        val (vm) = createViewModel()

        vm.login("", "user", "pass")

        assertEquals("All fields are required", vm.state.value.error)
        assertTrue(vm.state.value.showPasswordForm)
    }

    @Test
    fun `login with blank username shows error`() = runTest {
        val (vm) = createViewModel()

        vm.login("https://abs.example.com", "", "pass")

        assertEquals("All fields are required", vm.state.value.error)
    }

    @Test
    fun `successful login sets success state`() = runTest {
        val (vm) = createViewModel(authResult = AuthResult.Success("alice"))

        vm.login("https://abs.example.com", "alice", "password")

        assertTrue(vm.state.value.success)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `failed login sets error state`() = runTest {
        val (vm) = createViewModel(authResult = AuthResult.Error("Invalid credentials"))

        vm.login("https://abs.example.com", "user", "wrong")

        assertEquals("Invalid credentials", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.success)
    }

    @Test
    fun `showPasswordForm toggles state`() = runTest {
        val (vm) = createViewModel()

        vm.showPasswordForm()
        assertTrue(vm.state.value.showPasswordForm)

        vm.showSsoForm()
        assertFalse(vm.state.value.showPasswordForm)
    }

    @Test
    fun `clearError removes error`() = runTest {
        val (vm) = createViewModel()

        vm.login("", "", "")
        assertEquals("All fields are required", vm.state.value.error)

        vm.clearError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `hasOfflineContent true when completed downloads exist`() = runTest {
        val (vm) = createViewModel(hasCompletedDownloads = true)

        assertTrue(vm.hasOfflineContent.first())
    }

    @Test
    fun `hasOfflineContent false when no downloads`() = runTest {
        val (vm) = createViewModel(hasCompletedDownloads = false)

        assertFalse(vm.hasOfflineContent.first())
    }
}
