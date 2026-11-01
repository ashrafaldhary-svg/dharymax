package com.example.download

import android.content.Context
import android.util.Log
import com.example.data.local.DownloadRepository
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager(
    private val context: Context,
    private val repository: DownloadRepository,
    private val storageManager: StorageManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _concurrentLimit = MutableStateFlow(2)
    val concurrentLimit: StateFlow<Int> = _concurrentLimit.asStateFlow()

    fun setConcurrentLimit(limit: Int) {
        _concurrentLimit.value = limit.coerceIn(1, 5)
    }

    fun startDownload(task: DownloadTask) {
        if (activeJobs.containsKey(task.id)) return

        val job = scope.launch {
            executeDownload(task)
        }
        activeJobs[task.id] = job
    }

    fun pauseDownload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null && task.status == DownloadStatus.DOWNLOADING.name) {
                repository.updateProgress(
                    id = taskId,
                    status = DownloadStatus.PAUSED.name,
                    downloadedBytes = task.downloadedBytes,
                    totalBytes = task.totalBytes,
                    speed = 0L,
                    eta = 0L
                )
            }
        }
    }

    fun resumeDownload(taskId: String) {
        if (activeJobs.containsKey(taskId)) return
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                startDownload(task)
            }
        }
    }

    fun retryDownload(taskId: String) {
        pauseDownload(taskId)
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                val tempFile = storageManager.getTempDownloadFile(task.id, task.format)
                if (tempFile.exists()) tempFile.delete()

                val freshTask = task.copy(
                    status = DownloadStatus.PENDING.name,
                    downloadedBytes = 0L,
                    downloadSpeedBps = 0L,
                    etaSeconds = 0L,
                    errorMessage = null
                )
                repository.updateTask(freshTask)
                startDownload(freshTask)
            }
        }
    }

    fun cancelDownload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                val tempFile = storageManager.getTempDownloadFile(task.id, task.format)
                if (tempFile.exists()) tempFile.delete()
                repository.deleteTask(taskId)
            }
        }
    }

    fun deleteDownload(taskId: String, deleteStorageFile: Boolean = true) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                val tempFile = storageManager.getTempDownloadFile(task.id, task.format)
                if (tempFile.exists()) tempFile.delete()

                if (deleteStorageFile && task.localFilePath.isNotBlank()) {
                    storageManager.deleteSavedFile(task.localFilePath)
                }
                repository.deleteTask(taskId)
            }
        }
    }

    private suspend fun executeDownload(task: DownloadTask) {
        var tempFile = storageManager.getTempDownloadFile(task.id, task.format)
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        repository.updateProgress(
            id = task.id,
            status = DownloadStatus.DOWNLOADING.name,
            downloadedBytes = existingBytes,
            totalBytes = task.totalBytes,
            speed = 0L,
            eta = 0L
        )

        try {
            val requestBuilder = Request.Builder()
                .url(task.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) DhariMax/1.0")

            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                // If 416 (Range not satisfiable), restart from 0
                if (response.code == 416) {
                    tempFile.delete()
                    executeDownload(task.copy(downloadedBytes = 0L))
                    return
                }
                throw IllegalStateException("استجابة الخادم غير ناجحة: كود ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("جسم الاستجابة فارغ")
            val contentLength = body.contentLength()
            val totalBytes = if (existingBytes > 0 && contentLength > 0) {
                existingBytes + contentLength
            } else if (contentLength > 0) {
                contentLength
            } else {
                task.totalBytes
            }

            val inputStream: InputStream = body.byteStream()
            val randomAccessFile = RandomAccessFile(tempFile, "rw")
            if (existingBytes > 0) {
                randomAccessFile.seek(existingBytes)
            }

            val buffer = ByteArray(64 * 1024) // 64 KB chunk
            var read: Int
            var currentBytes = existingBytes
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            inputStream.use { stream ->
                randomAccessFile.use { raf ->
                    while (stream.read(buffer).also { read = it } != -1) {
                        raf.write(buffer, 0, read)
                        currentBytes += read
                        bytesSinceLastUpdate += read

                        val now = System.currentTimeMillis()
                        val diff = now - lastUpdateTime
                        if (diff >= 350) { // Update database every 350ms to keep smooth UI without overwhelming Room
                            val speed = (bytesSinceLastUpdate * 1000L) / diff.coerceAtLeast(1L)
                            val remainingBytes = (totalBytes - currentBytes).coerceAtLeast(0L)
                            val eta = if (speed > 0) remainingBytes / speed else 0L

                            repository.updateProgress(
                                id = task.id,
                                status = DownloadStatus.DOWNLOADING.name,
                                downloadedBytes = currentBytes,
                                totalBytes = totalBytes,
                                speed = speed,
                                eta = eta
                            )

                            lastUpdateTime = now
                            bytesSinceLastUpdate = 0L
                        }
                    }
                }
            }

            // Finished download stream -> Move to MediaStore
            activeJobs.remove(task.id)

            val mType = if (task.mediaType == MediaType.AUDIO.name) MediaType.AUDIO else MediaType.VIDEO
            val saveResult = storageManager.saveToMediaStore(
                tempFile = tempFile,
                title = task.title,
                mediaType = mType,
                format = task.format
            )

            val destination = saveResult.getOrDefault("")
            repository.markCompleted(
                id = task.id,
                status = DownloadStatus.COMPLETED.name,
                filePath = destination,
                completedAt = System.currentTimeMillis()
            )

        } catch (e: CancellationException) {
            // Download was intentionally paused or cancelled by user
            Log.d("DownloadManager", "Task ${task.id} paused/cancelled")
        } catch (e: Exception) {
            Log.e("DownloadManager", "Task ${task.id} failed", e)
            activeJobs.remove(task.id)
            val errorMsg = when {
                e.message?.contains("Unable to resolve host", true) == true -> "لا يوجد اتصال بالإنترنت"
                e.message?.contains("timeout", true) == true -> "انتهت مهلة الاتصال بالخادم"
                else -> e.localizedMessage ?: "حدث خطأ غير متوقع أثناء التحميل"
            }
            repository.markFailed(task.id, DownloadStatus.FAILED.name, errorMsg)
        }
    }
}
