package com.example.engine.parsers

import com.example.data.model.MediaPlatform
import com.example.engine.ExtractedMedia
import com.example.engine.ExtractedStream
import org.json.JSONArray
import org.json.JSONObject

object InnertubeParser {

    fun parse(jsonStr: String, videoId: String, originalUrl: String): ExtractedMedia? {
        return try {
            val root = JSONObject(jsonStr)

            // Video Details
            val videoDetails = root.optJSONObject("videoDetails") ?: return null
            val title = videoDetails.optString("title", "فيديو يوتيوب")
            val author = videoDetails.optString("author", "YouTube Channel")
            val durationSeconds = videoDetails.optLong("lengthSeconds", 0L)

            // Thumbnails
            var thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val thumbObj = videoDetails.optJSONObject("thumbnail")
            val thumbsArray = thumbObj?.optJSONArray("thumbnails")
            if (thumbsArray != null && thumbsArray.length() > 0) {
                thumb = thumbsArray.getJSONObject(thumbsArray.length() - 1).optString("url", thumb)
            }

            val streamingData = root.optJSONObject("streamingData") ?: return null
            val streams = mutableListOf<ExtractedStream>()

            // 1. Progressive Muxed Formats (Audio + Video combined)
            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    val url = f.optString("url")
                    val mimeType = f.optString("mimeType", "")
                    val qualityLabel = f.optString("qualityLabel", "720p")
                    val itag = f.optInt("itag", 22)
                    val contentLength = f.optLong("contentLength", 0L)

                    if (url.isNotBlank() && mimeType.contains("video/mp4", ignoreCase = true)) {
                        streams.add(
                            ExtractedStream(
                                id = "yt_prog_${itag}_$qualityLabel",
                                label = "$qualityLabel HD (مدمج صوت وفيديو)",
                                resolution = "${f.optInt("width", 1280)}x${f.optInt("height", 720)}",
                                format = "mp4",
                                streamUrl = url,
                                isAudioOnly = false,
                                isMuxed = true,
                                videoMime = "video/avc",
                                audioMime = "audio/mp4a-latm",
                                approximateSizeBytes = if (contentLength > 0) contentLength else 24_000_000L,
                                requestHeaders = mapOf(
                                    "User-Agent" to "com.google.android.youtube/19.09.37 (Linux; U; Android 14)"
                                )
                            )
                        )
                    }
                }
            }

            // 2. Adaptive Audio Formats
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val af = adaptive.getJSONObject(i)
                    val url = af.optString("url")
                    val mimeType = af.optString("mimeType", "")
                    val itag = af.optInt("itag", 140)
                    val bitrate = af.optLong("bitrate", 128000L) / 1000
                    val contentLength = af.optLong("contentLength", 0L)

                    if (url.isNotBlank() && mimeType.startsWith("audio/", ignoreCase = true)) {
                        val isM4a = mimeType.contains("mp4", ignoreCase = true)
                        streams.add(
                            ExtractedStream(
                                id = "yt_audio_${itag}",
                                label = if (isM4a) "صوت M4A فائق النقاء ($bitrate kbps)" else "صوت MP3/WebM نقي ($bitrate kbps)",
                                resolution = "Audio",
                                format = if (isM4a) "m4a" else "mp3",
                                streamUrl = url,
                                isAudioOnly = true,
                                isMuxed = true,
                                audioMime = if (isM4a) "audio/mp4a-latm" else "audio/mpeg",
                                approximateSizeBytes = if (contentLength > 0) contentLength else 4_500_000L,
                                bitrate = "$bitrate kbps",
                                requestHeaders = mapOf(
                                    "User-Agent" to "com.google.android.youtube/19.09.37 (Linux; U; Android 14)"
                                )
                            )
                        )
                    }
                }
            }

            if (streams.isNotEmpty()) {
                ExtractedMedia(
                    originalUrl = originalUrl,
                    id = videoId,
                    title = title,
                    author = author,
                    durationSeconds = durationSeconds,
                    thumbnailUrl = thumb,
                    platform = MediaPlatform.YOUTUBE,
                    streams = streams
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

object InvidiousParser {

    fun parse(jsonStr: String, videoId: String, originalUrl: String): ExtractedMedia? {
        return try {
            val json = JSONObject(jsonStr)
            val title = json.optString("title", "فيديو يوتيوب")
            val author = json.optString("author", "قناة يوتيوب")
            val lengthSeconds = json.optLong("lengthSeconds", 0L)

            var thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val thumbs = json.optJSONArray("videoThumbnails")
            if (thumbs != null && thumbs.length() > 0) {
                thumb = thumbs.getJSONObject(thumbs.length() - 1).optString("url", thumb)
            }

            val streams = mutableListOf<ExtractedStream>()

            // Format streams
            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null) {
                for (i in 0 until formatStreams.length()) {
                    val fs = formatStreams.getJSONObject(i)
                    val streamUrl = fs.optString("url")
                    val qualityLabel = fs.optString("qualityLabel", fs.optString("quality", "720p"))
                    val container = fs.optString("container", "mp4")
                    val size = fs.optLong("size", 0L)

                    if (streamUrl.isNotBlank() && container.contains("mp4", ignoreCase = true)) {
                        streams.add(
                            ExtractedStream(
                                id = "inv_fs_${i}_$qualityLabel",
                                label = "$qualityLabel HD (مباشر)",
                                resolution = fs.optString("resolution", "1280x720"),
                                format = "mp4",
                                streamUrl = streamUrl,
                                isAudioOnly = false,
                                isMuxed = true,
                                approximateSizeBytes = if (size > 0) size else 20_000_000L
                            )
                        )
                    }
                }
            }

            // Adaptive audio formats
            val adaptiveFormats = json.optJSONArray("adaptiveFormats")
            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val af = adaptiveFormats.getJSONObject(i)
                    val type = af.optString("type", "")
                    val streamUrl = af.optString("url")
                    val container = af.optString("container", "")
                    val bitrate = af.optLong("bitrate", 128000L) / 1000
                    val clen = af.optLong("clen", af.optLong("contentLength", 4_000_000L))

                    if (streamUrl.isNotBlank() && (type.contains("audio", true) || container.contains("m4a", true))) {
                        streams.add(
                            ExtractedStream(
                                id = "inv_audio_${i}_$container",
                                label = "صوت ${container.uppercase()} ($bitrate kbps)",
                                resolution = "Audio",
                                format = if (container.contains("m4a", true)) "m4a" else "mp3",
                                streamUrl = streamUrl,
                                isAudioOnly = true,
                                isMuxed = true,
                                approximateSizeBytes = clen,
                                bitrate = "$bitrate kbps"
                            )
                        )
                    }
                }
            }

            if (streams.isNotEmpty()) {
                ExtractedMedia(
                    originalUrl = originalUrl,
                    id = videoId,
                    title = title,
                    author = author,
                    durationSeconds = lengthSeconds,
                    thumbnailUrl = thumb,
                    platform = MediaPlatform.YOUTUBE,
                    streams = streams
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
