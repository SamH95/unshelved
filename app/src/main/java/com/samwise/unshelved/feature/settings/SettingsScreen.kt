package com.samwise.unshelved.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samwise.unshelved.R
import com.samwise.unshelved.core.model.Library
import com.samwise.unshelved.core.ui.LocalTopPadding

private val JUMP_OPTIONS = listOf(5, 10, 30)

private enum class ListItemPosition { First, Middle, Last, Only }

private fun groupedListShape(position: ListItemPosition): RoundedCornerShape = when (position) {
    ListItemPosition.Only -> RoundedCornerShape(12.dp)
    ListItemPosition.First -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    ListItemPosition.Middle -> RoundedCornerShape(4.dp)
    ListItemPosition.Last -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onManageDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraries by viewModel.libraries.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAudiobookPicker by remember { mutableStateOf(false) }
    var showPodcastPicker by remember { mutableStateOf(false) }
    var showJumpBackDialog by remember { mutableStateOf(false) }
    var showJumpForwardDialog by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        LogoutConfirmDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout(onLogout)
            },
            onDismiss = { showLogoutDialog = false },
        )
    }

    if (showAudiobookPicker) {
        LibraryTypePickerSheet(
            title = stringResource(R.string.audiobook_library),
            libraries = libraries.filter { !it.isPodcast },
            selectedId = state.audiobookLibraryId,
            allowNone = state.podcastLibraryId != null,
            onSelect = { libraryId ->
                viewModel.selectAudiobookLibrary(libraryId)
                showAudiobookPicker = false
            },
            onDismiss = { showAudiobookPicker = false },
        )
    }

    if (showPodcastPicker) {
        LibraryTypePickerSheet(
            title = stringResource(R.string.podcast_library),
            libraries = libraries.filter { it.isPodcast },
            selectedId = state.podcastLibraryId,
            allowNone = state.audiobookLibraryId != null,
            onSelect = { libraryId ->
                viewModel.selectPodcastLibrary(libraryId)
                showPodcastPicker = false
            },
            onDismiss = { showPodcastPicker = false },
        )
    }

    if (showJumpBackDialog) {
        JumpSecondsDialog(
            title = stringResource(R.string.jump_back),
            options = JUMP_OPTIONS,
            selected = state.jumpBackSeconds,
            onSelect = { secs ->
                viewModel.setJumpSeconds(secs, state.jumpForwardSeconds)
                showJumpBackDialog = false
            },
            onDismiss = { showJumpBackDialog = false },
        )
    }

    if (showJumpForwardDialog) {
        JumpSecondsDialog(
            title = stringResource(R.string.jump_forward),
            options = JUMP_OPTIONS,
            selected = state.jumpForwardSeconds,
            onSelect = { secs ->
                viewModel.setJumpSeconds(state.jumpBackSeconds, secs)
                showJumpForwardDialog = false
            },
            onDismiss = { showJumpForwardDialog = false },
        )
    }

    if (showLanguagePicker) {
        LanguagePickerSheet(
            currentTag = viewModel.getCurrentLanguageTag(),
            onSelect = { tag ->
                viewModel.setLanguage(tag)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = LocalTopPadding.current),
    ) {
        AccountSection(
            username = state.username,
            serverUrl = state.serverUrl,
            audiobookLibraryName = state.audiobookLibraryName,
            podcastLibraryName = state.podcastLibraryName,
            onAudiobookLibraryClick = { showAudiobookPicker = true },
            onPodcastLibraryClick = { showPodcastPicker = true },
            onLogoutClick = { showLogoutDialog = true },
        )

        PlaybackSection(
            jumpBackSeconds = state.jumpBackSeconds,
            jumpForwardSeconds = state.jumpForwardSeconds,
            onJumpBackClick = { showJumpBackDialog = true },
            onJumpForwardClick = { showJumpForwardDialog = true },
        )

        DownloadsSection(
            isOfflineMode = state.isOfflineMode,
            autoDeleteFinished = state.autoDeleteFinished,
            onManageDownloads = onManageDownloads,
            onOfflineModeChanged = { viewModel.setOfflineMode(it) },
            onAutoDeleteFinishedChanged = { viewModel.setAutoDeleteFinished(it) },
        )

        LanguageSection(
            currentTag = viewModel.getCurrentLanguageTag(),
            onLanguageClick = { showLanguagePicker = true },
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AccountSection(
    username: String,
    serverUrl: String,
    audiobookLibraryName: String?,
    podcastLibraryName: String?,
    onAudiobookLibraryClick: () -> Unit,
    onPodcastLibraryClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    SettingsSectionHeader("Account")

    SettingsGroupCard(position = ListItemPosition.First) {
        AccountHeader(username = username, serverUrl = serverUrl)
    }

    SettingsGroupCard(position = ListItemPosition.Middle, onClick = onAudiobookLibraryClick) {
        SettingsItemContent(
            icon = Icons.Default.Headphones,
            title = stringResource(R.string.audiobook_library),
            subtitle = audiobookLibraryName ?: stringResource(R.string.none),
        )
    }

    SettingsGroupCard(position = ListItemPosition.Middle, onClick = onPodcastLibraryClick) {
        SettingsItemContent(
            icon = Icons.Default.Podcasts,
            title = stringResource(R.string.podcast_library),
            subtitle = podcastLibraryName ?: stringResource(R.string.none),
        )
    }

    SettingsGroupCard(position = ListItemPosition.Last, onClick = onLogoutClick) {
        SettingsItemContent(
            icon = Icons.Default.Logout,
            title = stringResource(R.string.sign_out),
        )
    }
}

@Composable
private fun AccountHeader(username: String, serverUrl: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(username, style = MaterialTheme.typography.bodyLarge)
            Text(
                serverUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackSection(
    jumpBackSeconds: Int,
    jumpForwardSeconds: Int,
    onJumpBackClick: () -> Unit,
    onJumpForwardClick: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.playback))

    SettingsGroupCard(position = ListItemPosition.First, onClick = onJumpBackClick) {
        SettingsItemContent(
            icon = Icons.Default.FastRewind,
            title = stringResource(R.string.jump_back),
            subtitle = stringResource(R.string.seconds_format, jumpBackSeconds),
        )
    }

    SettingsGroupCard(position = ListItemPosition.Last, onClick = onJumpForwardClick) {
        SettingsItemContent(
            icon = Icons.Default.FastForward,
            title = stringResource(R.string.jump_forward),
            subtitle = stringResource(R.string.seconds_format, jumpForwardSeconds),
        )
    }
}

@Composable
private fun DownloadsSection(
    isOfflineMode: Boolean,
    autoDeleteFinished: Boolean,
    onManageDownloads: () -> Unit,
    onOfflineModeChanged: (Boolean) -> Unit,
    onAutoDeleteFinishedChanged: (Boolean) -> Unit,
) {
    SettingsSectionHeader("Downloads")

    SettingsGroupCard(position = ListItemPosition.First, onClick = onManageDownloads) {
        SettingsItemContent(
            icon = Icons.Default.Download,
            title = stringResource(R.string.manage_downloads),
        )
    }

    SettingsGroupCard(position = ListItemPosition.Middle) {
        SettingsSwitchContent(
            icon = Icons.Default.AutoDelete,
            title = stringResource(R.string.auto_delete_finished),
            subtitle = stringResource(R.string.auto_delete_finished_description),
            checked = autoDeleteFinished,
            onCheckedChange = onAutoDeleteFinishedChanged,
        )
    }

    SettingsGroupCard(position = ListItemPosition.Last) {
        SettingsSwitchContent(
            icon = Icons.Default.WifiOff,
            title = stringResource(R.string.offline_mode),
            subtitle = if (isOfflineMode) stringResource(R.string.offline_only_message) else stringResource(R.string.connected_to_server),
            checked = isOfflineMode,
            onCheckedChange = onOfflineModeChanged,
        )
    }
}

@Composable
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_out)) },
        text = { Text(stringResource(R.string.sign_out_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.sign_out)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTypePickerSheet(
    title: String,
    libraries: List<Library>,
    selectedId: String?,
    allowNone: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(libraries) { lib ->
                ListItem(
                    headlineContent = { Text(lib.name) },
                    trailingContent = {
                        RadioButton(selected = lib.id == selectedId, onClick = null)
                    },
                    modifier = Modifier.clickable { onSelect(lib.id) },
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.none),
                            color = if (allowNone) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = selectedId == null,
                            onClick = null,
                            enabled = allowNone,
                        )
                    },
                    modifier = if (allowNone) Modifier.clickable { onSelect(null) } else Modifier,
                )
            }
        }
    }
}

@Composable
private fun JumpSecondsDialog(
    title: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { secs ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(secs) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = secs == selected, onClick = { onSelect(secs) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.seconds_format, secs))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsGroupCard(
    position: ListItemPosition,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val verticalPad = 1.dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(
                top = if (position == ListItemPosition.First || position == ListItemPosition.Only) 4.dp else verticalPad,
                bottom = if (position == ListItemPosition.Last || position == ListItemPosition.Only) 4.dp else verticalPad,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = groupedListShape(position),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
private fun SettingsItemContent(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSwitchContent(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LanguageSection(
    currentTag: String,
    onLanguageClick: () -> Unit,
) {
    SettingsSectionHeader(stringResource(R.string.language))

    SettingsGroupCard(position = ListItemPosition.Only, onClick = onLanguageClick) {
        SettingsItemContent(
            icon = Icons.Default.Language,
            title = stringResource(R.string.language),
            subtitle = languageDisplayName(currentTag),
        )
    }
}

@Composable
private fun languageDisplayName(tag: String): String = when {
    tag.startsWith("de") -> stringResource(R.string.language_german)
    tag.startsWith("en") -> stringResource(R.string.language_english)
    tag.startsWith("fr") -> stringResource(R.string.language_french)
    tag.startsWith("it") -> stringResource(R.string.language_italian)
    tag.startsWith("es") -> stringResource(R.string.language_spanish)
    tag.startsWith("pt") -> stringResource(R.string.language_portuguese)
    tag.startsWith("pl") -> stringResource(R.string.language_polish)
    tag.startsWith("uk") -> stringResource(R.string.language_ukrainian)
    tag.startsWith("ru") -> stringResource(R.string.language_russian)
    tag.startsWith("el") -> stringResource(R.string.language_greek)
    tag.startsWith("zh") -> stringResource(R.string.language_chinese)
    tag.startsWith("ja") -> stringResource(R.string.language_japanese)
    else -> stringResource(R.string.language_automatic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    currentTag: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "" to R.string.language_automatic,
        "en" to R.string.language_english,
        "de" to R.string.language_german,
        "fr" to R.string.language_french,
        "it" to R.string.language_italian,
        "es" to R.string.language_spanish,
        "pt" to R.string.language_portuguese,
        "pl" to R.string.language_polish,
        "uk" to R.string.language_ukrainian,
        "ru" to R.string.language_russian,
        "el" to R.string.language_greek,
        "zh" to R.string.language_chinese,
        "ja" to R.string.language_japanese,
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.language),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(options.size) { index ->
                val (tag, labelRes) = options[index]
                val isSelected = if (tag.isEmpty()) currentTag.isEmpty()
                else currentTag.startsWith(tag)
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    trailingContent = {
                        RadioButton(selected = isSelected, onClick = null)
                    },
                    modifier = Modifier.clickable { onSelect(tag) },
                )
            }
        }
    }
}
