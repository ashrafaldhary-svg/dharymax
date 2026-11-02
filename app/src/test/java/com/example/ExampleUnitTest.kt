package com.example

import com.example.data.model.MediaPlatform
import com.example.download.StorageManager
import com.example.engine.MediaExtractorEngine
import com.example.engine.MediaExtractorRegistry
import com.example.engine.MediaValidator
import com.example.engine.providers.DirectMediaExtractorProvider
import com.example.engine.providers.FacebookExtractorProvider
import com.example.engine.providers.YouTubeExtractorProvider
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
        // Must reject HTML & JSON
        assertTrue(MediaValidator.validateContentType("text/html; charset=utf-8") is MediaValidator.ValidationResult.Invalid)
        assertTrue(MediaValidator.validateContentType("text/plain") is MediaValidator.ValidationResult.Invalid)
        assertTrue(MediaValidator.validateContentType("application/json") is MediaValidator.ValidationResult.Invalid)

        // Must accept valid media
        assertTrue(MediaValidator.validateContentType("video/mp4") is MediaValidator.ValidationResult.Valid)
        assertTrue(MediaValidator.validateContentType("audio/mpeg") is MediaValidator.ValidationResult.Valid)
        assertTrue(MediaValidator.validateContentType("audio/mp4") is MediaValidator.ValidationResult.Valid)
        assertTrue(MediaValidator.validateContentType("application/octet-stream") is MediaValidator.ValidationResult.Valid)
    }

    @Test
    fun testMediaValidatorMagicBytesRejectsHtml() {
        // Create dummy temporary HTML file pretending to be MP4
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
                // Write standard MP4 ftyp box: 0x00 0x00 0x00 0x18 'f' 't' 'y' 'p' 'm' 'p' '4' '2' ...
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
                // Write standard ID3 header
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
