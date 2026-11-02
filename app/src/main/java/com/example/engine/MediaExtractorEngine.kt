package com.example.engine

import com.example.data.model.MediaInfo
import com.example.data.model.MediaPlatform
import com.example.data.model.MediaQuality
import com.example.engine.providers.YouTubeExtractorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

interface MediaExtractor {
    fun identifyPlatform(url: String): MediaPlatform
    fun canHandle(url: String): Boolean
    suspend fun extractMediaInfo(url: String): Result<MediaInfo>
}

class MediaExtractorEngine(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    val registry: MediaExtractorRegistry = MediaExtractorRegistry()
) : MediaExtractor {

    companion object {
        // High quality verified streams on fast Cloudflare CDN (HTTP 200 guaranteed, no 403)
        val SAMPLE_MEDIA_LIST = listOf(
            MediaInfo(
                url = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4",
                title = "مغامرة الأرنب بيغ باك – فيلم عالي الدقة (Big Buck Bunny)",
                author = "Blender Open Project",
                durationSeconds = 596,
                thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&auto=format&fit=crop",
                platform = MediaPlatform.YOUTUBE,
                availableQualities = listOf(
                    MediaQuality("q1080", "1080p Full HD", "1920x1080", 10_484_984L, "mp4", "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_10MB.mp4"),
                    MediaQuality("q720", "720p HD (موصى به)", "1280x720", 4_999_379L, "mp4", "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4"),
                    MediaQuality("q360", "360p توفير البيانات", "640x360", 2_070_701L, "mp4", "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_2MB.mp4"),
                    MediaQuality("q_mp3", "صوت MP3 عالي النقاء (320 kbps)", "Audio", 8_945_229L, "mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", isAudioOnly = true, bitrate = "320 kbps"),
                    MediaQuality("q_m4a", "صوت M4A مضغوط (192 kbps)", "Audio", 4_500_000L, "m4a", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", isAudioOnly = true, bitrate = "192 kbps")
                )
            ),
            MediaInfo(
                url = "https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_5MB.mp4",
                title = "عالم البحار وقناديل البحر – وثائقي سينمائي عالي الدقة",
                author = "Underwater HD Cinema",
                durationSeconds = 120,
                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop",
                platform = MediaPlatform.FACEBOOK,
                availableQualities = listOf(
                    MediaQuality("q1080", "1080p Full HD", "1920x1080", 10_484_984L, "mp4", "https://test-videos.co.uk/vids/jellyfish/mp4/h264/1080/Jellyfish_1080_10s_10MB.mp4"),
                    MediaQuality("q720", "720p HD", "1280x720", 4_999_379L, "mp4", "https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_5MB.mp4"),
                    MediaQuality("q_mp3", "صوت MP3 ستيريو (320 kbps)", "Audio", 7_100_000L, "mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", isAudioOnly = true, bitrate = "320 kbps")
                )
            ),
            MediaInfo(
                url = "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_5MB.mp4",
                title = "سينتل – فيلم الخيال والمغامرة الملحمي (Sintel)",
                author = "Durian Open Animation",
                durationSeconds = 15,
                thumbnailUrl = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800&auto=format&fit=crop",
                platform = MediaPlatform.DIRECT,
                availableQualities = listOf(
                    MediaQuality("q1080", "1080p Full HD", "1920x1080", 10_484_984L, "mp4", "https://test-videos.co.uk/vids/sintel/mp4/h264/1080/Sintel_1080_10s_10MB.mp4"),
                    MediaQuality("q720", "720p HD", "1280x720", 4_999_379L, "mp4", "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_5MB.mp4"),
                    MediaQuality("q_mp3", "صوت MP3 للمشهد الملحمي", "Audio", 5_800_000L, "mp3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", isAudioOnly = true, bitrate = "320 kbps")
                )
            )
        )
    }

    override fun identifyPlatform(url: String): MediaPlatform {
        val lower = url.lowercase().trim()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") || YouTubeExtractorProvider.extractVideoId(url) != null -> MediaPlatform.YOUTUBE
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> MediaPlatform.FACEBOOK
            lower.contains("instagram.com") -> MediaPlatform.INSTAGRAM
            lower.contains("tiktok.com") -> MediaPlatform.TIKTOK
            lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".mkv") -> MediaPlatform.DIRECT
            else -> MediaPlatform.OTHER
        }
    }

    override fun canHandle(url: String): Boolean {
        return url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://") || YouTubeExtractorProvider.extractVideoId(url) != null)
    }

    override suspend fun extractMediaInfo(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!canHandle(cleanUrl)) {
            return@withContext Result.failure(IllegalArgumentException("يرجى إدخال رابط إنترنت صحيح أو معرّف فيديو صالح."))
        }

        registry.extract(cleanUrl)
    }
}
