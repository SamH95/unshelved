package com.samwise.unshelved.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onAddToQueue: () -> Unit,
    onMarkFinished: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Add to queue")
            }
            IconButton(onClick = onMarkFinished) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Mark as finished")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        windowInsets = windowInsets,
        modifier = modifier,
    )
}
