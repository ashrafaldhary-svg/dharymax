package com.example.engine

import com.example.data.model.MediaInfo
import com.example.data.model.MediaPlatform
import com.example.data.model.MediaQuality

data class ExtractedStream(
    val id: String,
    val label: String,                 // e.g. "1080p FHD", "720p HD", "360p", "MP3 320kbps"
    val resolution: String,            // e.g. "1280x720" or "Audio"
    val format: String,                // "mp4", "mp3", "m4a"
    val streamUrl: String,
    val audioUrl: String? = null,      // For separate video+audio streams
    val requestHeaders: Map<String, String> = emptyMap(),
    val isAudioOnly: Boolean = false,
    val isMuxed: Boolean = true,       // True if audio and video are already combined
    val videoMime: String = "video/mp4",
    val audioMime: String = "audio/mp4",
    val approximateSizeBytes: Long = 0L,
    val bitrate: String = ""
) {
    fun toMediaQuality(): MediaQuality {
        return MediaQuality(
            id = id,
            label = label,
            resolution = resolution,
            approximateSizeBytes = approximateSizeBytes,
            format = format,
            streamUrl = streamUrl,
            isAudioOnly = isAudioOnly,
            bitrate = bitrate
        )
    }
}

data class ExtractedMedia(
    val originalUrl: String,
    val id: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val platform: MediaPlatform,
    val streams: List<ExtractedStream>
) {
    fun toMediaInfo(): MediaInfo {
        return MediaInfo(
            url = originalUrl,
            title = title,
            author = author,
            durationSeconds = durationSeconds,
            thumbnailUrl = thumbnailUrl,
            platform = platform,
            availableQualities = streams.map { it.toMediaQuality() }
        )
    }
}
