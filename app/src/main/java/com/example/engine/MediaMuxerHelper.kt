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

    /**
     * Mux separate video-only MP4 file and audio-only M4A/AAC file into a single output MP4
     */
    suspend fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            // Find video track
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

            // Find audio track
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

            if (videoTrackIndex == -1 || videoFormat == null) {
                return@withContext Result.failure(IllegalStateException("لم يتم العثور على مسار فيديو صالح للدمج."))
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext Result.failure(IllegalStateException("لم يتم العثور على مسار صوتي صالح للدمج."))
            }

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            // Buffer for samples
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
            try {
                videoExtractor.release()
            } catch (_: Exception) {}
            try {
                audioExtractor.release()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
        }
    }
}
