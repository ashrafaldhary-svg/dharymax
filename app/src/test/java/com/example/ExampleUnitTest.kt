package com.example

import com.example.data.model.MediaPlatform
import com.example.download.StorageManager
import com.example.engine.MediaExtractorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPlatformIdentification() {
        val engine = MediaExtractorEngine()

        assertEquals(
            MediaPlatform.YOUTUBE,
            engine.identifyPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            MediaPlatform.YOUTUBE,
            engine.identifyPlatform("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals(
            MediaPlatform.FACEBOOK,
            engine.identifyPlatform("https://www.facebook.com/watch/?v=123456")
        )
        assertEquals(
            MediaPlatform.INSTAGRAM,
            engine.identifyPlatform("https://www.instagram.com/reel/C123456")
        )
        assertEquals(
            MediaPlatform.TIKTOK,
            engine.identifyPlatform("https://www.tiktok.com/@user/video/123456789")
        )
        assertEquals(
            MediaPlatform.DIRECT,
            engine.identifyPlatform("https://cdn.example.com/video.mp4")
        )
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", StorageManager.formatBytes(0))
        assertEquals("500 B", StorageManager.formatBytes(500))
        assertEquals("1.0 KB", StorageManager.formatBytes(1024))
        assertEquals("1.5 MB", StorageManager.formatBytes(1572864))
        assertEquals("1.00 GB", StorageManager.formatBytes(1073741824))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("00:45", StorageManager.formatDuration(45))
        assertEquals("03:20", StorageManager.formatDuration(200))
        assertEquals("01:05:00", StorageManager.formatDuration(3900))
    }

    @Test
    fun testSampleMediaList() {
        assertTrue(MediaExtractorEngine.SAMPLE_MEDIA_LIST.isNotEmpty())
        val first = MediaExtractorEngine.SAMPLE_MEDIA_LIST.first()
        assertTrue(first.availableQualities.any { !it.isAudioOnly })
        assertTrue(first.availableQualities.any { it.isAudioOnly })
    }
}
