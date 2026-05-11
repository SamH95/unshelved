package com.samwise.unshelved.feature.settings

import com.samwise.unshelved.MainDispatcherRule
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.core.model.Library
import com.samwise.unshelved.feature.auth.AuthRepository
import com.samwise.unshelved.feature.library.LibraryRepository
import com.samwise.unshelved.service.PlayerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testLibraries = listOf(
        Library(id = "lib-1", name = "Audiobooks", mediaType = "book", icon = "database"),
        Library(id = "lib-2", name = "Podcasts", mediaType = "podcast", icon = "podcast"),
    )

    private fun createViewModel(
        username: String? = "testuser",
        serverUrl: String? = "https://abs.example.com",
        audiobookLibraryId: String? = "lib-1",
        audiobookLibraryName: String? = "Audiobooks",
        podcastLibraryId: String? = null,
        podcastLibraryName: String? = null,
        jumpBack: Int = 10,
        jumpForward: Int = 30,
        offlineMode: Boolean = false,
        libraries: Result<List<Library>> = Result.success(testLibraries),
    ): Triple<SettingsViewModel, UserPreferencesRepository, PlayerRepository> {
        val prefs = mockk<UserPreferencesRepository>(relaxUnitFun = true) {
            every { this@mockk.username } returns flowOf(username)
            every { this@mockk.serverUrl } returns flowOf(serverUrl)
            every { this@mockk.selectedLibraryId } returns flowOf(audiobookLibraryId)
            every { this@mockk.selectedLibraryName } returns flowOf(audiobookLibraryName)
            every { this@mockk.podcastLibraryId } returns flowOf(podcastLibraryId)
            every { this@mockk.podcastLibraryName } returns flowOf(podcastLibraryName)
            every { jumpBackSeconds } returns flowOf(jumpBack)
            every { jumpForwardSeconds } returns flowOf(jumpForward)
            every { this@mockk.offlineMode } returns flowOf(offlineMode)
            every { selectedLibraryMediaType } returns flowOf("book")
        }

        val authRepository = mockk<AuthRepository> {
            coEvery { logout() } returns Unit
        }

        val libraryRepository = mockk<LibraryRepository> {
            coEvery { getLibraries() } returns libraries
        }

        val playerRepository = mockk<PlayerRepository>(relaxUnitFun = true)

        val vm = SettingsViewModel(
            prefs = prefs,
            authRepository = authRepository,
            libraryRepository = libraryRepository,
            playerRepository = playerRepository,
        )

        return Triple(vm, prefs, playerRepository)
    }

    @Test
    fun `initial state reflects preferences`() = runTest {
        val (vm) = createViewModel(
            username = "alice",
            serverUrl = "https://books.example.com",
            audiobookLibraryId = "lib-2",
            audiobookLibraryName = "My Books",
            jumpBack = 5,
            jumpForward = 10,
            offlineMode = true,
        )

        val state = vm.state.value
        assertEquals("alice", state.username)
        assertEquals("https://books.example.com", state.serverUrl)
        assertEquals("lib-2", state.audiobookLibraryId)
        assertEquals("My Books", state.audiobookLibraryName)
        assertEquals(5, state.jumpBackSeconds)
        assertEquals(10, state.jumpForwardSeconds)
        assertTrue(state.isOfflineMode)
    }

    @Test
    fun `null preferences produce empty defaults`() = runTest {
        val (vm) = createViewModel(username = null, serverUrl = null, audiobookLibraryId = null, audiobookLibraryName = null)

        val state = vm.state.value
        assertEquals("", state.username)
        assertEquals("", state.serverUrl)
        assertEquals(null, state.audiobookLibraryId)
    }

    @Test
    fun `selectAudiobookLibrary saves to preferences`() = runTest {
        val (vm, prefs) = createViewModel()

        vm.selectAudiobookLibrary("lib-1")

        coVerify { prefs.saveSelectedLibrary("lib-1", any()) }
    }

    @Test
    fun `setJumpSeconds saves to preferences`() = runTest {
        val (vm, prefs) = createViewModel()

        vm.setJumpSeconds(5, 30)

        coVerify { prefs.saveJumpSeconds(5, 30) }
    }

    @Test
    fun `setOfflineMode enables offline`() = runTest {
        val (vm, prefs, playerRepository) = createViewModel()

        vm.setOfflineMode(true)

        coVerify { prefs.setOfflineMode(true) }
        verify(exactly = 0) { playerRepository.syncOfflineProgress() }
    }

    @Test
    fun `setOfflineMode disable syncs offline progress`() = runTest {
        val (vm, prefs, playerRepository) = createViewModel(offlineMode = true)

        vm.setOfflineMode(false)

        coVerify { prefs.setOfflineMode(false) }
        verify { playerRepository.syncOfflineProgress() }
    }

    @Test
    fun `logout calls auth repository and callback`() = runTest {
        val (vm) = createViewModel()
        var loggedOut = false

        vm.logout { loggedOut = true }

        assertTrue(loggedOut)
    }

    @Test
    fun `libraries loaded on init`() = runTest {
        val (vm) = createViewModel()

        val libs = vm.libraries.value
        assertEquals(2, libs.size)
        assertEquals("Audiobooks", libs[0].name)
        assertEquals("Podcasts", libs[1].name)
    }

    @Test
    fun `libraries failure produces empty list`() = runTest {
        val (vm) = createViewModel(libraries = Result.failure(RuntimeException("offline")))

        assertTrue(vm.libraries.value.isEmpty())
    }

    @Test
    fun `default state matches repository defaults`() = runTest {
        val (vm) = createViewModel()

        val state = vm.state.value
        assertEquals(10, state.jumpBackSeconds)
        assertEquals(30, state.jumpForwardSeconds)
        assertFalse(state.isOfflineMode)
    }

    @Test
    fun `libraries not loaded in offline mode`() = runTest {
        val (vm) = createViewModel(offlineMode = true)

        assertTrue(vm.libraries.value.isEmpty())
    }
}
