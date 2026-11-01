package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadRepository
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.MediaInfo
import com.example.data.model.MediaPlatform
import com.example.data.model.MediaQuality
import com.example.data.model.MediaType
import com.example.download.DownloadManager
import com.example.download.SavedMediaFile
import com.example.download.StorageManager
import com.example.engine.MediaExtractorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppNavTab(val labelAr: String) {
    HOME("الرئيسية"),
    DOWNLOADS("التحميلات"),
    FILES("الملفات"),
    SETTINGS("الإعدادات")
}

enum class DownloadFilterTab(val labelAr: String) {
    ALL("الكل"),
    ACTIVE("النشطة"),
    COMPLETED("المكتملة"),
    PAUSED("المتوقفة"),
    FAILED("الفاشلة")
}

data class AppSettingsState(
    val maxConcurrent: Int = 2,
    val wifiOnly: Boolean = false,
    val autoResume: Boolean = true,
    val notificationsEnabled: Boolean = true
)

class DhariMaxViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = DownloadRepository(database.downloadDao())
    val storageManager = StorageManager(application)
    val downloadManager = DownloadManager(application, repository, storageManager)
    val mediaEngine = MediaExtractorEngine()

    // Navigation & Screen States
    private val _currentTab = MutableStateFlow(AppNavTab.HOME)
    val currentTab: StateFlow<AppNavTab> = _currentTab.asStateFlow()

    private val _downloadFilter = MutableStateFlow(DownloadFilterTab.ALL)
    val downloadFilter: StateFlow<DownloadFilterTab> = _downloadFilter.asStateFlow()

    // URL Parsing States
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // Modal dialog for Quality Selection
    private val _selectedMediaInfo = MutableStateFlow<MediaInfo?>(null)
    val selectedMediaInfo: StateFlow<MediaInfo?> = _selectedMediaInfo.asStateFlow()

    // Saved Media Files list
    private val _savedFiles = MutableStateFlow<List<SavedMediaFile>>(emptyList())
    val savedFiles: StateFlow<List<SavedMediaFile>> = _savedFiles.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(AppSettingsState())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    // Active Preview
    private val _previewFile = MutableStateFlow<SavedMediaFile?>(null)
    val previewFile: StateFlow<SavedMediaFile?> = _previewFile.asStateFlow()

    // Reactive list of download tasks from Room
    val downloadTasks: StateFlow<List<DownloadTask>> = repository.allTasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshSavedFiles()
    }

    fun switchTab(tab: AppNavTab) {
        _currentTab.value = tab
        if (tab == AppNavTab.FILES) {
            refreshSavedFiles()
        }
    }

    fun setDownloadFilter(filter: DownloadFilterTab) {
        _downloadFilter.value = filter
    }

    fun onUrlChange(text: String) {
        _urlInput.value = text
        _analysisError.value = null
    }

    fun clearUrl() {
        _urlInput.value = ""
        _analysisError.value = null
    }

    fun analyzeUrl(customUrl: String? = null) {
        val target = (customUrl ?: _urlInput.value).trim()
        if (target.isBlank()) {
            _analysisError.value = "يرجى إدخال رابط أو اختيار نموذج تجريبي"
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null

            val result = mediaEngine.extractMediaInfo(target)
            _isAnalyzing.value = false

            result.onSuccess { info ->
                _selectedMediaInfo.value = info
            }.onFailure { err ->
                _analysisError.value = err.localizedMessage ?: "تعذر تحليل الرابط. تأكد من صحة الرابط أو جرب العينات الجاهزة."
            }
        }
    }

    fun dismissQualityDialog() {
        _selectedMediaInfo.value = null
    }

    fun startDownload(quality: MediaQuality) {
        val info = _selectedMediaInfo.value ?: return
        _selectedMediaInfo.value = null

        viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            val isAudio = quality.isAudioOnly
            val mediaType = if (isAudio) MediaType.AUDIO.name else MediaType.VIDEO.name

            val task = DownloadTask(
                id = taskId,
                title = info.title,
                sourceUrl = info.url,
                downloadUrl = quality.streamUrl,
                mediaType = mediaType,
                qualityLabel = quality.label,
                format = quality.format,
                thumbnailUrl = info.thumbnailUrl,
                status = DownloadStatus.PENDING.name,
                downloadedBytes = 0L,
                totalBytes = quality.approximateSizeBytes,
                platform = info.platform.name,
                createdAt = System.currentTimeMillis()
            )

            repository.insertTask(task)
            downloadManager.startDownload(task)
            _currentTab.value = AppNavTab.DOWNLOADS
        }
    }

    fun pauseTask(taskId: String) {
        downloadManager.pauseDownload(taskId)
    }

    fun resumeTask(taskId: String) {
        downloadManager.resumeDownload(taskId)
    }

    fun retryTask(taskId: String) {
        downloadManager.retryDownload(taskId)
    }

    fun deleteTask(taskId: String, deleteFileFromStorage: Boolean = true) {
        downloadManager.deleteDownload(taskId, deleteFileFromStorage)
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.clearCompleted()
        }
    }

    fun refreshSavedFiles() {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            val files = storageManager.getSavedFiles()
            _savedFiles.value = files
            _isLoadingFiles.value = false
        }
    }

    fun openFile(file: SavedMediaFile) {
        storageManager.openFile(file.filePath, file.isAudio)
    }

    fun shareFile(file: SavedMediaFile) {
        storageManager.shareFile(file.filePath, file.isAudio, file.name)
    }

    fun deleteSavedFile(file: SavedMediaFile) {
        viewModelScope.launch {
            storageManager.deleteSavedFile(file.filePath)
            refreshSavedFiles()
        }
    }

    fun setPreviewFile(file: SavedMediaFile?) {
        _previewFile.value = file
    }

    fun updateSettings(
        maxConcurrent: Int = _settings.value.maxConcurrent,
        wifiOnly: Boolean = _settings.value.wifiOnly,
        autoResume: Boolean = _settings.value.autoResume,
        notifications: Boolean = _settings.value.notificationsEnabled
    ) {
        _settings.value = AppSettingsState(
            maxConcurrent = maxConcurrent,
            wifiOnly = wifiOnly,
            autoResume = autoResume,
            notificationsEnabled = notifications
        )
        downloadManager.setConcurrentLimit(maxConcurrent)
    }
}
