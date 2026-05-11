package com.samwise.unshelved.feature.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samwise.unshelved.MainActivity
import com.samwise.unshelved.R

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onPlayOffline: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasOffline by viewModel.hasOfflineContent.collectAsStateWithLifecycle(false)
    val context = LocalContext.current

    LaunchedEffect(state.success) {
        if (state.success) onLoginSuccess()
    }

    LaunchedEffect(Unit) {
        viewModel.openBrowser.collect { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    LaunchedEffect(Unit) {
        MainActivity.oauthCallback.collect { (code, state) ->
            viewModel.handleOidcCallback(code, state)
        }
    }

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var serverChecked by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedServerUrl) {
        if (serverUrl.isEmpty() && state.savedServerUrl.isNotEmpty()) {
            serverUrl = state.savedServerUrl
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.logoutReason?.let { reason ->
            LogoutReasonCard(reason)
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        ServerUrlInput(
            serverUrl = serverUrl,
            onServerUrlChanged = { serverUrl = it; serverChecked = false },
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            !serverChecked -> {
                Button(
                    onClick = {
                        if (serverUrl.isNotBlank()) {
                            serverChecked = true
                            viewModel.checkOidc(serverUrl)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading && serverUrl.isNotBlank(),
                ) {
                    Text(stringResource(R.string.connect))
                }
            }
            state.isLoading && state.oidcSupported == null -> {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            state.oidcSupported == true && !state.showPasswordForm -> {
                SsoLoginForm(
                    isLoading = state.isLoading,
                    onSsoLogin = { viewModel.startOidcLogin(serverUrl) },
                    onUsePassword = { viewModel.showPasswordForm() },
                )
            }
            else -> {
                PasswordLoginForm(
                    username = username,
                    onUsernameChanged = { username = it },
                    password = password,
                    onPasswordChanged = { password = it },
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                    isLoading = state.isLoading,
                    showSsoOption = state.oidcSupported == true,
                    onLogin = { viewModel.login(serverUrl, username, password) },
                    onUseSso = { viewModel.showSsoForm() },
                )
            }
        }

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            ErrorCard(error)
        }

        if (hasOffline) {
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onPlayOffline) {
                Text(stringResource(R.string.play_offline_library))
            }
        }
    }
}

@Composable
private fun LogoutReasonCard(reason: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = reason,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServerUrlInput(
    serverUrl: String,
    onServerUrlChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChanged,
        label = { Text(stringResource(R.string.server_url)) },
        placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        singleLine = true,
    )
}

@Composable
private fun SsoLoginForm(
    isLoading: Boolean,
    onSsoLogin: () -> Unit,
    onUsePassword: () -> Unit,
) {
    Button(
        onClick = onSsoLogin,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(stringResource(R.string.sign_in_with_sso))
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(onClick = onUsePassword) {
        Text(stringResource(R.string.use_password_instead), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PasswordLoginForm(
    username: String,
    onUsernameChanged: (String) -> Unit,
    password: String,
    onPasswordChanged: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    isLoading: Boolean,
    showSsoOption: Boolean,
    onLogin: () -> Unit,
    onUseSso: () -> Unit,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChanged,
        label = { Text(stringResource(R.string.username)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        singleLine = true,
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChanged,
        label = { Text(stringResource(R.string.password)) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password),
                )
            }
        },
        singleLine = true,
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onLogin,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(stringResource(R.string.sign_in))
    }

    if (showSsoOption) {
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onUseSso) {
            Text(stringResource(R.string.use_sso_instead), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
