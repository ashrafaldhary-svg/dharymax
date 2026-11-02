package com.example

import com.example.download.StorageManager
import com.example.engine.MediaExtractorRegistry
import com.example.engine.MediaValidator
import com.example.engine.parsers.InnertubeParser
import com.example.engine.providers.DirectMediaExtractorProvider
import com.example.engine.providers.FacebookExtractorProvider
import com.example.engine.providers.YouTubeExtractorProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class ExampleUnitTest {

    @Test
    fun testYouTubeVideoIdExtraction() {
        assertEquals("dQw4w9WgXcQ", YouTubeExtractorProvider.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractorProvider.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractorProvider.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractorProvider.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared"))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractorProvider.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeExtractorProvider.extractVideoId("dQw4w9WgXcQ"))
    }

    @Test
    fun testMediaValidatorContentType() {
        assertTrue(MediaValidator.validateContentType("text/html; charset=utf-8") is MediaValidator.ValidationResult.Invalid)
        assertTrue(MediaValidator.validateContentType("text/plain") is MediaValidator.ValidationResult.Invalid)
        assertTrue(MediaValidator.validateContentType("application/json") is MediaValidator.ValidationResult.Invalid)

        assertTrue(MediaValidator.validateContentType("video/mp4") is MediaValidator.ValidationResult.Valid)
        assertTrue(MediaValidator.validateContentType("audio/mpeg") is MediaValidator.ValidationResult.Valid)
        assertTrue(MediaValidator.validateContentType("audio/mp4") is MediaValidator.ValidationResult.Valid)
        assertTrue(MediaValidator.validateContentType("application/octet-stream") is MediaValidator.ValidationResult.Valid)
    }

    @Test
    fun testMediaValidatorMagicBytesRejectsHtml() {
        val tempHtml = File.createTempFile("fake_video", ".mp4")
        try {
            FileOutputStream(tempHtml).use { fos ->
                fos.write("<!DOCTYPE html><html><body>Error 404</body></html>".toByteArray())
            }
            val result = MediaValidator.validateMagicBytes(tempHtml, "mp4")
            assertTrue("Should reject HTML content in MP4 file", result is MediaValidator.ValidationResult.Invalid)
        } finally {
            tempHtml.delete()
        }
    }

    @Test
    fun testMediaValidatorMagicBytesAcceptsValidMp4() {
        val tempMp4 = File.createTempFile("valid_video", ".mp4")
        try {
            FileOutputStream(tempMp4).use { fos ->
                val ftypHeader = byteArrayOf(
                    0x00, 0x00, 0x00, 0x18,
                    'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                    'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                )
                fos.write(ftypHeader)
            }
            val result = MediaValidator.validateMagicBytes(tempMp4, "mp4")
            assertTrue("Should accept valid MP4 header", result is MediaValidator.ValidationResult.Valid)
        } finally {
            tempMp4.delete()
        }
    }

    @Test
    fun testMediaValidatorMagicBytesAcceptsValidMp3() {
        val tempMp3 = File.createTempFile("valid_audio", ".mp3")
        try {
            FileOutputStream(tempMp3).use { fos ->
                val id3Header = byteArrayOf(
                    'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                )
                fos.write(id3Header)
            }
            val result = MediaValidator.validateMagicBytes(tempMp3, "mp3")
            assertTrue("Should accept valid MP3 ID3 header", result is MediaValidator.ValidationResult.Valid)
        } finally {
            tempMp3.delete()
        }
    }

    @Test
    fun testInnertubeParserWithSimulatedPayload() {
        val sampleJson = JSONObject().apply {
            put("videoDetails", JSONObject().apply {
                put("title", "حمود السمه جديد 2026")
                put("author", "قناة يمنية")
                put("lengthSeconds", "320")
            })
            put("streamingData", JSONObject().apply {
                put("formats", JSONArray().apply {
                    put(JSONObject().apply {
                        put("itag", 22)
                        put("mimeType", "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"")
                        put("qualityLabel", "720p")
                        put("width", 1280)
                        put("height", 720)
                        put("url", "https://example.com/video_720.mp4")
                        put("contentLength", 24000000L)
                    })
                    put(JSONObject().apply {
                        put("itag", 18)
                        put("mimeType", "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"")
                        put("qualityLabel", "360p")
                        put("width", 640)
                        put("height", 360)
                        put("url", "https://example.com/video_360.mp4")
                        put("contentLength", 9000000L)
                    })
                })
                put("adaptiveFormats", JSONArray().apply {
                    put(JSONObject().apply {
                        put("itag", 140)
                        put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                        put("bitrate", 128000L)
                        put("url", "https://example.com/audio_140.m4a")
                        put("contentLength", 3500000L)
                    })
                })
            })
        }.toString()

        val parsed = InnertubeParser.parse(sampleJson, "test_video_123", "https://youtube.com/watch?v=test_video_123")
        assertNotNull(parsed)
        assertEquals("حمود السمه جديد 2026", parsed!!.title)
        assertEquals(3, parsed.streams.size)
        assertTrue(parsed.streams.any { !it.isAudioOnly })
        assertTrue(parsed.streams.any { it.isAudioOnly })
    }

    @Test
    fun testProviderRegistryRouting() {
        val registry = MediaExtractorRegistry()

        val ytProvider = registry.findProviderForUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(ytProvider)
        assertTrue(ytProvider is YouTubeExtractorProvider)

        val fbProvider = registry.findProviderForUrl("https://www.facebook.com/watch/?v=1234567")
        assertNotNull(fbProvider)
        assertTrue(fbProvider is FacebookExtractorProvider)

        val directProvider = registry.findProviderForUrl("https://example.com/files/nature.mp4")
        assertNotNull(directProvider)
        assertTrue(directProvider is DirectMediaExtractorProvider)
    }

    @Test
    fun testFormatBytesAndDuration() {
        assertEquals("0 B", StorageManager.formatBytes(0))
        assertEquals("1.0 KB", StorageManager.formatBytes(1024))
        assertEquals("1.5 MB", StorageManager.formatBytes(1572864))
        assertEquals("00:45", StorageManager.formatDuration(45))
        assertEquals("03:20", StorageManager.formatDuration(200))
    }
}
