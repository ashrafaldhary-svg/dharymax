package com.example.engine.providers

import com.example.data.model.MediaInfo
import com.example.data.model.MediaPlatform
import com.example.data.model.MediaQuality
import com.example.engine.MediaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

class DirectMediaExtractorProvider(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MediaExtractorProvider {

    override val name: String = "Direct Media Link Extractor"
    override val priority: Int = 50

    override fun supports(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.endsWith(".mp4") || lower.endsWith(".mp3") ||
                lower.endsWith(".m4a") || lower.endsWith(".mkv") ||
                lower.endsWith(".webm") || lower.endsWith(".aac")
    }

    override suspend fun extract(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            var contentLength = 0L
            var contentType = ""

            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "DhariMax/1.0 (Android)")
                .build()

            httpClient.newCall(headReq).execute().use { response ->
                contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                contentType = response.header("Content-Type") ?: ""
            }

            // Reject HTML pages that falsely have .mp4 extension
            val valResult = MediaValidator.validateContentType(contentType)
            if (valResult is MediaValidator.ValidationResult.Invalid) {
                return@withContext Result.failure(IllegalArgumentException(valResult.reason))
            }

            val filename = try {
                URI(url).path.substringAfterLast('/').ifBlank { "ملف وسائط مباشر" }
            } catch (_: Exception) {
                "ملف وسائط مباشر"
            }

            val isAudio = contentType.startsWith("audio/") || url.endsWith(".mp3") || url.endsWith(".m4a") || url.endsWith(".aac")
            val format = if (url.contains('.')) url.substringAfterLast('.').lowercase().take(4) else if (isAudio) "mp3" else "mp4"

            val qualities = mutableListOf<MediaQuality>()

            if (!isAudio) {
                qualities.add(
                    MediaQuality(
                        id = "direct_video",
                        label = "جودة أصلية مباشرة (Original)",
                        resolution = "Direct Stream",
                        approximateSizeBytes = if (contentLength > 0) contentLength else 20_000_000L,
                        format = format,
                        streamUrl = url,
                        isAudioOnly = false
                    )
                )
                qualities.add(
                    MediaQuality(
                        id = "direct_audio",
                        label = "تحميل الصوت فقط",
                        resolution = "Audio",
                        approximateSizeBytes = if (contentLength > 0) (contentLength * 0.2).toLong() else 4_000_000L,
                        format = "mp3",
                        streamUrl = url,
                        isAudioOnly = true,
                        bitrate = "320 kbps"
                    )
                )
            } else {
                qualities.add(
                    MediaQuality(
                        id = "direct_audio_full",
                        label = "ملف صوت أصلي (${format.uppercase()})",
                        resolution = "Audio",
                        approximateSizeBytes = if (contentLength > 0) contentLength else 5_000_000L,
                        format = format,
                        streamUrl = url,
                        isAudioOnly = true,
                        bitrate = "320 kbps"
                    )
                )
            }

            val thumb = if (isAudio) {
                "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop"
            } else {
                "https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?w=800&auto=format&fit=crop"
            }

            Result.success(
                MediaInfo(
                    url = url,
                    title = filename,
                    author = "Direct Media Server",
                    durationSeconds = 180,
                    thumbnailUrl = thumb,
                    platform = MediaPlatform.DIRECT,
                    availableQualities = qualities
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
