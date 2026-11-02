package com.example.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object MediaMuxerHelper {

    private const val TAG = "MediaMuxerHelper"

    class IncompatibleCodecException(message: String) : Exception(message)

    /**
     * Checks if the video and audio tracks are compatible with Android's MediaMuxer MP4 container.
     * MP4 container in Android MediaMuxer natively supports:
     * - Video: H.264 (video/avc), H.265 (video/hevc), MPEG-4 (video/mp4v-es)
     * - Audio: AAC (audio/mp4a-latm)
     */
    fun checkMuxerCompatibility(videoFile: File, audioFile: File): Pair<Boolean, String> {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            var videoMime = ""
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoMime = mime
                    break
                }
            }

            var audioMime = ""
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioMime = mime
                    break
                }
            }

            val isVideoCompatible = videoMime == MediaFormat.MIMETYPE_VIDEO_AVC ||
                    videoMime == MediaFormat.MIMETYPE_VIDEO_HEVC ||
                    videoMime.contains("mp4v")

            val isAudioCompatible = audioMime == MediaFormat.MIMETYPE_AUDIO_AAC ||
                    audioMime.contains("mp4a")

            if (!isVideoCompatible) {
                return false to "ترميز الفيديو ($videoMime) غير مدعوم للدمج المباشر في حاوية MP4 (يتطلب H.264 أو H.265)."
            }

            if (!isAudioCompatible) {
                return false to "ترميز الصوت ($audioMime) غير مدعوم للدمج المباشر في حاوية MP4 (يتطلب AAC/M4A)."
            }

            return true to "الترميزات متوافقة تماماً مع مشغل وحاوية أندرويد."
        } catch (e: Exception) {
            return false to "تعذر قراءة مسارات الوسائط: ${e.message}"
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Safely mux separate video-only MP4 file and audio-only M4A file with pre-check
     */
    suspend fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val (isCompatible, reason) = checkMuxerCompatibility(videoFile, audioFile)
        if (!isCompatible) {
            Log.w(TAG, "Muxing rejected due to codec incompatibility: $reason")
            return@withContext Result.failure(IncompatibleCodecException(reason))
        }

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            // Select video track
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }

            // Select audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null || audioTrackIndex == -1 || audioFormat == null) {
                return@withContext Result.failure(IllegalStateException("المسارات غير صالحة للدمج."))
            }

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = 1024 * 1024 // 1 MB
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy Video Track
            videoExtractor.selectTrack(videoTrackIndex)
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Copy Audio Track
            audioExtractor.selectTrack(audioTrackIndex)
            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags

                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            Result.success(outputFile)

        } catch (e: Exception) {
            Log.e(TAG, "Muxing failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}
