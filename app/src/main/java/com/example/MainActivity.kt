package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DownloadStatus
import com.example.ui.components.MediaPreviewDialog
import com.example.ui.components.QualityPickerDialog
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.DhariMaxTheme
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VelvetBlack
import com.example.ui.viewmodel.AppNavTab
import com.example.ui.viewmodel.DhariMaxViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DhariMaxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming shared link (Share Intent)
        handleIntent(intent)

        setContent {
            DhariMaxTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DhariMaxApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val extractedUrl = extractUrlFromText(sharedText)
            if (extractedUrl.isNotBlank()) {
                viewModel.onUrlChange(extractedUrl)
                viewModel.analyzeUrl(extractedUrl)
            }
        }
    }

    private fun extractUrlFromText(text: String): String {
        val parts = text.split("\\s+".toRegex())
        for (part in parts) {
            if (part.startsWith("http://") || part.startsWith("https://")) {
                return part.trim()
            }
        }
        return text.trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhariMaxApp(viewModel: DhariMaxViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analysisError by viewModel.analysisError.collectAsStateWithLifecycle()
    val selectedMediaInfo by viewModel.selectedMediaInfo.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val downloadFilter by viewModel.downloadFilter.collectAsStateWithLifecycle()
    val savedFiles by viewModel.savedFiles.collectAsStateWithLifecycle()
    val isLoadingFiles by viewModel.isLoadingFiles.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val previewFile by viewModel.previewFile.collectAsStateWithLifecycle()

    val activeCount = downloadTasks.count {
        it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.PENDING.name
    }

    // Handle back button: return to Home tab if on another screen
    BackHandler(enabled = currentTab != AppNavTab.HOME) {
        viewModel.switchTab(AppNavTab.HOME)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(VelvetBlack),
        containerColor = VelvetBlack,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextPrimary,
                tonalElevation = 8.dp
            ) {
                // Home Tab
                NavigationBarItem(
                    selected = currentTab == AppNavTab.HOME,
                    onClick = { viewModel.switchTab(AppNavTab.HOME) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppNavTab.HOME) Icons.Default.Home else Icons.Outlined.Home,
                            contentDescription = "الرئيسية"
                        )
                    },
                    label = { Text("الرئيسية", fontWeight = if (currentTab == AppNavTab.HOME) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = CrimsonNeon,
                        indicatorColor = CrimsonNeon,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_home")
                )

                // Downloads Tab with Badge
                NavigationBarItem(
                    selected = currentTab == AppNavTab.DOWNLOADS,
                    onClick = { viewModel.switchTab(AppNavTab.DOWNLOADS) },
                    icon = {
                        BadgedBox(badge = {
                            if (activeCount > 0) {
                                Badge(containerColor = CrimsonNeon) {
                                    Text("$activeCount", color = Color.White)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (currentTab == AppNavTab.DOWNLOADS) Icons.Default.Download else Icons.Outlined.Download,
                                contentDescription = "التحميلات"
                            )
                        }
                    },
                    label = { Text("التحميلات", fontWeight = if (currentTab == AppNavTab.DOWNLOADS) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = CrimsonNeon,
                        indicatorColor = CrimsonNeon,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_downloads")
                )

                // Files Tab
                NavigationBarItem(
                    selected = currentTab == AppNavTab.FILES,
                    onClick = { viewModel.switchTab(AppNavTab.FILES) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppNavTab.FILES) Icons.Default.Folder else Icons.Outlined.Folder,
                            contentDescription = "الملفات"
                        )
                    },
                    label = { Text("الملفات", fontWeight = if (currentTab == AppNavTab.FILES) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = CrimsonNeon,
                        indicatorColor = CrimsonNeon,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_files")
                )

                // Settings Tab
                NavigationBarItem(
                    selected = currentTab == AppNavTab.SETTINGS,
                    onClick = { viewModel.switchTab(AppNavTab.SETTINGS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == AppNavTab.SETTINGS) Icons.Default.Settings else Icons.Outlined.Settings,
                            contentDescription = "الإعدادات"
                        )
                    },
                    label = { Text("الإعدادات", fontWeight = if (currentTab == AppNavTab.SETTINGS) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = CrimsonNeon,
                        indicatorColor = CrimsonNeon,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "nav_tab_transition"
            ) { tab ->
                when (tab) {
                    AppNavTab.HOME -> {
                        HomeScreen(
                            urlInput = urlInput,
                            isAnalyzing = isAnalyzing,
                            analysisError = analysisError,
                            recentTasks = downloadTasks.take(3),
                            onUrlChange = viewModel::onUrlChange,
                            onClearUrl = viewModel::clearUrl,
                            onAnalyze = viewModel::analyzeUrl,
                            onNavigateToDownloads = { viewModel.switchTab(AppNavTab.DOWNLOADS) }
                        )
                    }

                    AppNavTab.DOWNLOADS -> {
                        DownloadsScreen(
                            tasks = downloadTasks,
                            selectedFilter = downloadFilter,
                            onFilterChange = viewModel::setDownloadFilter,
                            onPause = viewModel::pauseTask,
                            onResume = viewModel::resumeTask,
                            onRetry = viewModel::retryTask,
                            onCancel = viewModel::deleteTask,
                            onDelete = viewModel::deleteTask,
                            onOpenFile = { task ->
                                viewModel.storageManager.openFile(task.localFilePath, task.mediaType == "AUDIO")
                            },
                            onShareFile = { task ->
                                viewModel.storageManager.shareFile(task.localFilePath, task.mediaType == "AUDIO", task.title)
                            },
                            onClearCompleted = viewModel::clearCompletedTasks
                        )
                    }

                    AppNavTab.FILES -> {
                        FilesScreen(
                            files = savedFiles,
                            isLoading = isLoadingFiles,
                            onRefresh = viewModel::refreshSavedFiles,
                            onSelectFile = viewModel::setPreviewFile,
                            onOpenFile = viewModel::openFile,
                            onShareFile = viewModel::shareFile,
                            onDeleteFile = viewModel::deleteSavedFile
                        )
                    }

                    AppNavTab.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            onUpdateSettings = { maxConcurrent, wifiOnly, autoResume, notifs ->
                                viewModel.updateSettings(maxConcurrent, wifiOnly, autoResume, notifs)
                            },
                            onClearHistory = viewModel::clearCompletedTasks
                        )
                    }
                }
            }

            // Quality Picker Dialog (when a link is analyzed)
            selectedMediaInfo?.let { mediaInfo ->
                QualityPickerDialog(
                    mediaInfo = mediaInfo,
                    onDismiss = viewModel::dismissQualityDialog,
                    onConfirmDownload = viewModel::startDownload
                )
            }

            // Media Preview Dialog (when a saved file is clicked)
            previewFile?.let { file ->
                MediaPreviewDialog(
                    file = file,
                    onDismiss = { viewModel.setPreviewFile(null) },
                    onOpen = viewModel::openFile,
                    onShare = viewModel::shareFile,
                    onDelete = viewModel::deleteSavedFile
                )
            }
        }
    }
}
