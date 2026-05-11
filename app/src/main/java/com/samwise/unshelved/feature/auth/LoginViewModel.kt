package com.samwise.unshelved.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.database.DownloadStatus
import com.samwise.unshelved.core.datastore.UserPreferencesRepository
import com.samwise.unshelved.service.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val oidcSupported: Boolean? = null,
    val showPasswordForm: Boolean = false,
    val savedServerUrl: String = "",
    val logoutReason: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: UserPreferencesRepository,
    downloadRepository: DownloadRepository,
) : ViewModel() {

    val hasOfflineContent = downloadRepository.allDownloads
        .map { list -> list.any { it.status == DownloadStatus.COMPLETED } }

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _openBrowser = MutableSharedFlow<String>()
    val openBrowser = _openBrowser.asSharedFlow()

    private var oidcVerifier: String? = null
    private var oidcState: String? = null
    private var oidcServerUrl: String? = null

    init {
        viewModelScope.launch {
            prefs.serverUrl.collect { url ->
                if (!url.isNullOrEmpty()) _state.update { it.copy(savedServerUrl = url) }
            }
        }
        viewModelScope.launch {
            prefs.logoutReason.collect { reason ->
                if (reason != null) {
                    _state.update { it.copy(logoutReason = reason) }
                    prefs.clearLogoutReason()
                }
            }
        }
    }

    fun login(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _state.update { LoginState(error = "All fields are required", showPasswordForm = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.login(serverUrl.trim(), username.trim(), password)) {
                is AuthResult.Success -> _state.update { it.copy(isLoading = false, success = true) }
                is AuthResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun checkOidc(serverUrl: String) {
        if (serverUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val supported = authRepository.checkOidcSupport(serverUrl.trim())
            _state.update { it.copy(isLoading = false, oidcSupported = supported, showPasswordForm = !supported) }
        }
    }

    fun startOidcLogin(serverUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.getOidcAuthUrl(serverUrl.trim())
            if (result != null) {
                val (authUrl, verifier, state) = result
                oidcVerifier = verifier
                oidcState = state
                oidcServerUrl = serverUrl.trim()
                _state.update { it.copy(isLoading = false) }
                _openBrowser.emit(authUrl)
            } else {
                _state.update { it.copy(isLoading = false, error = "Server does not support SSO or is unreachable") }
            }
        }
    }

    fun handleOidcCallback(code: String, state: String) {
        val verifier = oidcVerifier ?: return
        val expectedState = oidcState ?: return
        val serverUrl = oidcServerUrl ?: return

        if (state != expectedState) {
            _state.update { it.copy(error = "Invalid state — potential CSRF attack") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.exchangeOidcCode(serverUrl, code, state, verifier)) {
                is AuthResult.Success -> _state.update { it.copy(isLoading = false, success = true) }
                is AuthResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
            oidcVerifier = null
            oidcState = null
            oidcServerUrl = null
        }
    }

    fun showPasswordForm() {
        _state.update { it.copy(showPasswordForm = true) }
    }

    fun showSsoForm() {
        _state.update { it.copy(showPasswordForm = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
