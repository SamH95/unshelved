package com.samwise.unshelved.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samwise.unshelved.core.database.DownloadEntity
import com.samwise.unshelved.service.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val downloads: Flow<List<DownloadEntity>> = downloadRepository.allDownloads

    fun deleteDownload(itemId: String) {
        viewModelScope.launch { downloadRepository.deleteDownload(itemId) }
    }
}
