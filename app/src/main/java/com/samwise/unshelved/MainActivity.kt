package com.samwise.unshelved

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.feature.auth.AuthRepository
import com.samwise.unshelved.feature.auth.LoginScreen
import com.samwise.unshelved.feature.auth.TokenValidationResult
import com.samwise.unshelved.feature.offline.OfflineLibraryScreen
import com.samwise.unshelved.feature.player.FullPlayerSheet
import com.samwise.unshelved.feature.player.PlayerViewModel
import com.samwise.unshelved.ui.theme.UnshelvedTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var prefs: UserPreferencesRepository

    @Inject
    lateinit var authRepository: AuthRepository

    companion object {
        private val _oauthCallback = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
        val oauthCallback = _oauthCallback.asSharedFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        handleOAuthIntent(intent)
        setContent {
            UnshelvedTheme {
                AppRoot(prefs = prefs, authRepository = authRepository)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "audiobookshelf" && uri.host == "oauth") {
            val code = uri.getQueryParameter("code") ?: return
            val state = uri.getQueryParameter("state") ?: return
            _oauthCallback.tryEmit(Pair(code, state))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }
}

private sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedIn : AuthState
    data object LoggedOut : AuthState
}

@Composable
fun AppRoot(prefs: UserPreferencesRepository, authRepository: AuthRepository) {
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }
    var showOffline by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val token = prefs.authToken.first()
        authState = if (token != null) AuthState.LoggedIn else AuthState.LoggedOut
    }

    LaunchedEffect(Unit) {
        var initial = true
        prefs.authToken.collect { token ->
            if (initial) {
                initial = false
                return@collect
            }
            authState = if (token != null) AuthState.LoggedIn else AuthState.LoggedOut
        }
    }

    LaunchedEffect(authState) {
        if (authState == AuthState.LoggedIn) {
            showOffline = false
            when (authRepository.validateToken()) {
                is TokenValidationResult.Valid -> {}
                is TokenValidationResult.NetworkError -> {}
                is TokenValidationResult.Rejected -> {
                    prefs.clearAuth(reason = "Your session expired. Please sign in again.")
                }
            }
        }
    }

    when {
        authState == AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
        }
        authState == AuthState.LoggedIn -> MainNavigation()
        showOffline -> {
            val playerVM: PlayerViewModel = hiltViewModel()
            val playerState by playerVM.playerState.collectAsState()
            var showPlayer by remember { mutableStateOf(false) }
            LaunchedEffect(playerState.session) {
                showPlayer = playerState.session != null
            }
            val density = LocalDensity.current
            val navBarDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
            Box(modifier = Modifier.fillMaxSize()) {
                OfflineLibraryScreen(
                    onBack = { showOffline = false },
                    playerBottomPadding = if (playerState.session != null) 64.dp + navBarDp else 0.dp,
                )
                if (showPlayer) {
                    FullPlayerSheet(
                        onDismiss = {},
                        autoExpand = true,
                    )
                }
            }
        }
        else -> LoginScreen(
            onLoginSuccess = { },
            onPlayOffline = { showOffline = true },
        )
    }
}
