package com.example.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

data class SavedMediaFile(
    val id: String,
    val name: String,
    val filePath: String,
    val uriString: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val isAudio: Boolean
) {
    val formattedSize: String
        get() = StorageManager.formatBytes(sizeBytes)
}

class StorageManager(private val context: Context) {

    companion object {
        const val FOLDER_MOVIES_ALMUHAMMIL = "Movies/المحمل"
        const val FOLDER_MUSIC_ALMUHAMMIL = "Music/المحمل"
        const val FOLDER_MOVIES_DHARI = "Movies/Dhari Max"
        const val FOLDER_MUSIC_DHARI = "Music/Dhari Max"

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$bytes B"
            }
        }

        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0) return "0 KB/s"
            val kb = bytesPerSec / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format(Locale.US, "%.2f MB/s", mb)
                else -> String.format(Locale.US, "%.0f KB/s", kb)
            }
        }

        fun formatDuration(seconds: Long): String {
            if (seconds <= 0) return "--:--"
            val hrs = seconds / 3600
            val mins = (seconds % 3600) / 60
            val secs = seconds % 60
            return if (hrs > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format(Locale.US, "%02d:%02d", mins, secs)
            }
        }

        fun formatEta(seconds: Long): String {
            if (seconds <= 0) return "لحظات..."
            val mins = seconds / 60
            val secs = seconds % 60
            return when {
                mins > 60 -> "${mins / 60} ساعة"
                mins > 0 -> "$mins د و $secs ث"
                else -> "$secs ثانية"
            }
        }
    }

    /**
     * Get or create a temporary file in internal cache for streaming/chunking
     */
    fun getTempDownloadFile(taskId: String, format: String): File {
        val tempDir = File(context.cacheDir, "dhari_downloads")
        if (!tempDir.exists()) tempDir.mkdirs()
        return File(tempDir, "task_${taskId}.$format")
    }

    /**
     * Move completed downloaded temporary file to Public MediaStore (Movies/Dhari Max or Music/Dhari Max)
     */
    suspend fun saveToMediaStore(
        tempFile: File,
        title: String,
        mediaType: MediaType,
        format: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safeName = title.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF\\s._-]"), "_").take(60)
            val fileName = "DhariMax_${safeName}_${System.currentTimeMillis()}.$format"
            val mimeType = if (mediaType == MediaType.AUDIO) {
                if (format.equals("m4a", ignoreCase = true)) "audio/mp4" else "audio/mpeg"
            } else {
                "video/mp4"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (mediaType == MediaType.AUDIO) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val relativePath = if (mediaType == MediaType.AUDIO) FOLDER_MUSIC_ALMUHAMMIL else FOLDER_MOVIES_ALMUHAMMIL

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(collection, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("تعذر إنشاء مسار MediaStore"))

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                // Clean up temp
                tempFile.delete()
                Result.success(uri.toString())
            } else {
                // Legacy storage for older devices
                val targetDir = if (mediaType == MediaType.AUDIO) {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "المحمل")
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "المحمل")
                }
                if (!targetDir.exists()) targetDir.mkdirs()

                val targetFile = File(targetDir, fileName)
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()

                Result.success(targetFile.absolutePath)
            }
        } catch (e: Exception) {
            // Fallback: save to external files dir if MediaStore fails
            try {
                val fallbackDir = context.getExternalFilesDir(if (mediaType == MediaType.AUDIO) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
                val fallbackFile = File(fallbackDir, "AlMuhammil_${System.currentTimeMillis()}.$format")
                tempFile.copyTo(fallbackFile, overwrite = true)
                tempFile.delete()
                Result.success(fallbackFile.absolutePath)
            } catch (fallbackError: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Query all saved files from app directories and MediaStore
     */
    suspend fun getSavedFiles(): List<SavedMediaFile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SavedMediaFile>()

        // Scan local app external storage folders
        val movieDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)

        listOf(movieDir, musicDir).filterNotNull().forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.filter { it.isFile && (it.extension == "mp4" || it.extension == "mp3" || it.extension == "m4a") }?.forEach { file ->
                    list.add(
                        SavedMediaFile(
                            id = file.absolutePath,
                            name = file.name,
                            filePath = file.absolutePath,
                            uriString = Uri.fromFile(file).toString(),
                            sizeBytes = file.length(),
                            dateModified = file.lastModified(),
                            isAudio = file.extension.equals("mp3", true) || file.extension.equals("m4a", true)
                        )
                    )
                }
            }
        }

        // Query MediaStore for Dhari Max files if on API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Videos
                val videoProjection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_MODIFIED
                )
                val videoSelection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                val videoArgs = arrayOf("%المحمل%", "%Dhari Max%")

                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    videoProjection,
                    videoSelection,
                    videoArgs,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol)
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol) * 1000
                        val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                        list.add(
                            SavedMediaFile(
                                id = uri.toString(),
                                name = name,
                                filePath = uri.toString(),
                                uriString = uri.toString(),
                                sizeBytes = size,
                                dateModified = date,
                                isAudio = false
                            )
                        )
                    }
                }

                // Audios
                val audioProjection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATE_MODIFIED
                )
                val audioSelection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                val audioArgs = arrayOf("%المحمل%", "%Dhari Max%")

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    audioProjection,
                    audioSelection,
                    audioArgs,
                    "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol)
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol) * 1000
                        val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())

                        list.add(
                            SavedMediaFile(
                                id = uri.toString(),
                                name = name,
                                filePath = uri.toString(),
                                uriString = uri.toString(),
                                sizeBytes = size,
                                dateModified = date,
                                isAudio = true
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // Deduplicate & Sort by date descending
        list.distinctBy { it.name }.sortedByDescending { it.dateModified }
    }

    /**
     * Open media file with default system player
     */
    fun openFile(filePathOrUri: String, isAudio: Boolean) {
        try {
            val uri = if (filePathOrUri.startsWith("content://")) {
                Uri.parse(filePathOrUri)
            } else {
                val file = File(filePathOrUri)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            val mime = if (isAudio) "audio/*" else "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Share media file with contacts/apps
     */
    fun shareFile(filePathOrUri: String, isAudio: Boolean, title: String) {
        try {
            val uri = if (filePathOrUri.startsWith("content://")) {
                Uri.parse(filePathOrUri)
            } else {
                val file = File(filePathOrUri)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            val mime = if (isAudio) "audio/*" else "video/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "تم التحميل عبر تطبيق المحمل: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "مشاركة الوسائط عبر المحمل").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    /**
     * Delete file from disk or MediaStore
     */
    suspend fun deleteSavedFile(filePathOrUri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (filePathOrUri.startsWith("content://")) {
                val uri = Uri.parse(filePathOrUri)
                val rows = context.contentResolver.delete(uri, null, null)
                rows > 0
            } else {
                val file = File(filePathOrUri)
                if (file.exists()) {
                    file.delete()
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Open downloads directory using system file manager
     */
    fun openDownloadsFolder() {
        try {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val targetDir = File(moviesDir, "المحمل").takeIf { it.exists() } ?: moviesDir
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetDir)
            } else {
                Uri.fromFile(targetDir)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }
}
