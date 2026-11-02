package com.example.download

import android.content.Context
import android.util.Log
import com.example.data.local.DownloadRepository
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.MediaType
import com.example.engine.MediaValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
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
        val tempFile = storageManager.getTempDownloadFile(task.id, task.format)
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        repository.updateProgress(
            id = task.id,
            status = DownloadStatus.DOWNLOADING.name,
            downloadedBytes = existingBytes,
            totalBytes = task.totalBytes,
            speed = 0L,
            eta = 0L
        )

        try {
            var response: Response = executeHttpRequest(task.downloadUrl, existingBytes)

            // If 403 or 401, retry with Android YouTube client headers
            if (response.code == 403 || response.code == 401) {
                response.close()
                response = executeHttpRequestWithAndroidHeaders(task.downloadUrl, existingBytes)
            }

            // 1. Check HTTP Status
            if (!response.isSuccessful && response.code != 206) {
                // If 416 (Range not satisfiable), restart from 0
                if (response.code == 416) {
                    response.close()
                    tempFile.delete()
                    executeDownload(task.copy(downloadedBytes = 0L))
                    return
                }
                val code = response.code
                response.close()
                throw IllegalStateException("استجابة الخادم غير ناجحة: كود $code")
            }

            // 2. Strict Content-Type Validation (Level 1)
            val contentType = response.header("Content-Type")
            val ctValidation = MediaValidator.validateContentType(contentType)
            if (ctValidation is MediaValidator.ValidationResult.Invalid) {
                response.close()
                throw IllegalStateException(ctValidation.reason)
            }

            val body = response.body ?: throw IllegalStateException("جسم الاستجابة فارغ من الخادم.")
            val contentLength = body.contentLength()

            val randomAccessFile = RandomAccessFile(tempFile, "rw")

            // 3. Safe Range & Resumption Logic (HTTP 206 vs HTTP 200)
            val currentBytes: Long
            val totalBytes: Long

            if (existingBytes > 0L) {
                if (response.code == 206) {
                    randomAccessFile.seek(existingBytes)
                    currentBytes = existingBytes
                    totalBytes = if (contentLength > 0) existingBytes + contentLength else task.totalBytes
                } else {
                    Log.w("DownloadManager", "Server returned 200 instead of 206. Truncating to byte 0.")
                    randomAccessFile.setLength(0)
                    randomAccessFile.seek(0)
                    currentBytes = 0L
                    existingBytes = 0L
                    totalBytes = if (contentLength > 0) contentLength else task.totalBytes
                }
            } else {
                randomAccessFile.seek(0)
                currentBytes = 0L
                totalBytes = if (contentLength > 0) contentLength else task.totalBytes
            }

            val inputStream: InputStream = body.byteStream()
            val buffer = ByteArray(64 * 1024) // 64 KB chunk
            var read: Int
            var bytesWritten = currentBytes
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            inputStream.use { stream ->
                randomAccessFile.use { raf ->
                    while (stream.read(buffer).also { read = it } != -1) {
                        raf.write(buffer, 0, read)
                        bytesWritten += read
                        bytesSinceLastUpdate += read

                        val now = System.currentTimeMillis()
                        val diff = now - lastUpdateTime
                        if (diff >= 350) {
                            val speed = (bytesSinceLastUpdate * 1000L) / diff.coerceAtLeast(1L)
                            val remainingBytes = (totalBytes - bytesWritten).coerceAtLeast(0L)
                            val eta = if (speed > 0) remainingBytes / speed else 0L

                            repository.updateProgress(
                                id = task.id,
                                status = DownloadStatus.DOWNLOADING.name,
                                downloadedBytes = bytesWritten,
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

            // 4. Verification before marking as COMPLETED
            activeJobs.remove(task.id)

            // Minimum length check
            if (tempFile.length() < 30_000L && totalBytes > 80_000L) {
                throw IllegalStateException("انقطع الاتصال بالخادم قبل اكتمال ملف الوسائط (${tempFile.length()} بايت فقط).")
            }

            // Level 2: Magic Bytes verification
            val magicValidation = MediaValidator.validateMagicBytes(tempFile, task.format)
            if (magicValidation is MediaValidator.ValidationResult.Invalid) {
                throw IllegalStateException(magicValidation.reason)
            }

            // Level 3: Android native MediaExtractor verification
            val extractorValidation = MediaValidator.validateWithAndroidMediaExtractor(context, tempFile)
            if (extractorValidation is MediaValidator.ValidationResult.Invalid) {
                throw IllegalStateException(extractorValidation.reason)
            }

            // All checks passed -> Move to MediaStore
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
            Log.d("DownloadManager", "Task ${task.id} paused/cancelled")
        } catch (e: Exception) {
            Log.e("DownloadManager", "Task ${task.id} failed: ${e.message}", e)
            activeJobs.remove(task.id)
            val errorMsg = when {
                e.message?.contains("403", true) == true -> "الخادم يطلب مصادقة (كود 403)"
                e.message?.contains("HTML", true) == true -> "الرابط ليس ملف وسائط صالح (صفحة ويب)"
                e.message?.contains("ftyp", true) == true -> "ملف الفيديو تالف أو غير صالح"
                e.message?.contains("ID3", true) == true -> "ملف الصوت تالف أو غير صالح"
                e.message?.contains("Unable to resolve host", true) == true -> "لا يوجد اتصال بالإنترنت"
                e.message?.contains("timeout", true) == true -> "انتهت مهلة الاتصال بالخادم"
                else -> e.localizedMessage ?: "حدث خطأ غير متوقع أثناء التحميل"
            }
            repository.markFailed(task.id, DownloadStatus.FAILED.name, errorMsg)
        }
    }

    private fun executeHttpRequest(url: String, existingBytes: Long): Response {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Connection", "keep-alive")

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        return httpClient.newCall(requestBuilder.build()).execute()
    }

    private fun executeHttpRequestWithAndroidHeaders(url: String, existingBytes: Long): Response {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 14)")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Connection", "keep-alive")

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        return httpClient.newCall(requestBuilder.build()).execute()
    }
}
