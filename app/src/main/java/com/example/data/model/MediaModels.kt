package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaPlatform(val labelAr: String, val iconRes: String) {
    YOUTUBE("يوتيوب – YouTube", "youtube"),
    FACEBOOK("فيسبوك – Facebook", "facebook"),
    INSTAGRAM("انستغرام – Instagram", "instagram"),
    TIKTOK("تيك توك – TikTok", "tiktok"),
    DIRECT("رابط مباشر – Direct Link", "link"),
    OTHER("وسائط إنترنت – Web Media", "web")
}

enum class MediaType(val labelAr: String) {
    VIDEO("فيديو"),
    AUDIO("صوت فقط")
}

enum class DownloadStatus(val labelAr: String) {
    PENDING("قيد الانتظار"),
    DOWNLOADING("جارٍ التحميل"),
    PAUSED("متوقف مؤقتاً"),
    COMPLETED("مكتمل"),
    FAILED("فشل التحميل"),
    CANCELLED("ملغي")
}

data class MediaQuality(
    val id: String,
    val label: String,            // e.g. "1080p Full HD", "720p HD", "MP3 320kbps"
    val resolution: String,       // "1920x1080" or "Audio"
    val approximateSizeBytes: Long,
    val format: String,           // "mp4", "mp3", "m4a"
    val streamUrl: String,
    val isAudioOnly: Boolean = false,
    val bitrate: String = ""      // e.g. "320 kbps"
)

data class MediaInfo(
    val url: String,
    val title: String,
    val author: String = "Dhari Max Engine",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String,
    val platform: MediaPlatform,
    val availableQualities: List<MediaQuality>
)

@Entity(tableName = "download_tasks")
data class DownloadTask(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val mediaType: String,            // "VIDEO" or "AUDIO"
    val qualityLabel: String,         // "1080p FHD"
    val format: String,               // "mp4", "mp3", "m4a"
    val thumbnailUrl: String,
    val status: String,               // DownloadStatus name
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeedBps: Long = 0L,
    val etaSeconds: Long = 0L,
    val localFilePath: String = "",
    val platform: String = MediaPlatform.DIRECT.name,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()
}
