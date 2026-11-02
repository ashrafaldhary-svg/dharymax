package com.example.engine

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream

object MediaValidator {

    private const val TAG = "MediaValidator"

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Level 1: Check HTTP Content-Type header
     */
    fun validateContentType(contentType: String?): ValidationResult {
        if (contentType == null) return ValidationResult.Valid // Some CDNs omit Content-Type
        val lower = contentType.lowercase().trim()
        if (lower.startsWith("text/html") || lower.startsWith("text/") || lower.contains("html")) {
            return ValidationResult.Invalid("الرابط المُرجع هو صفحة ويب HTML وليس ملف وسائط صالحاً.")
        }
        if (lower.startsWith("application/json")) {
            return ValidationResult.Invalid("استجابة الخادم هي بيانات JSON نصية وليست ملف فيديو أو صوت.")
        }
        return ValidationResult.Valid
    }

    /**
     * Level 2: Check Magic Bytes in file header
     */
    fun validateMagicBytes(file: File, expectedFormat: String): ValidationResult {
        if (!file.exists() || file.length() < 32) {
            return ValidationResult.Invalid("حجم الملف صغير جداً أو الملف غير موجود (${file.length()} بايت).")
        }

        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(32)
                val read = fis.read(header)
                if (read < 16) {
                    return ValidationResult.Invalid("تعذر قراءة ترويسة الملف.")
                }

                // Check for HTML text in the file header
                val headerStr = String(header, 0, read).lowercase()
                if (headerStr.contains("<!doctype") || headerStr.contains("<html") || headerStr.contains("<body")) {
                    return ValidationResult.Invalid("الملف المحفوظ يحتوي على صفحة HTML وليس ملف وسائط.")
                }

                val fmt = expectedFormat.lowercase()
                when (fmt) {
                    "mp4", "m4v", "mov" -> {
                        // MP4 files start with [4 bytes size][ftyp] e.g. 'f' 't' 'y' 'p' at index 4..7
                        val hasFtyp = header[4] == 'f'.code.toByte() &&
                                header[5] == 't'.code.toByte() &&
                                header[6] == 'y'.code.toByte() &&
                                header[7] == 'p'.code.toByte()
                        // Or starts with moov / mdat
                        val hasMoov = header[4] == 'm'.code.toByte() && header[5] == 'o'.code.toByte()
                        val hasMdat = header[4] == 'm'.code.toByte() && header[5] == 'd'.code.toByte()

                        if (!hasFtyp && !hasMoov && !hasMdat) {
                            return ValidationResult.Invalid("بنية ملف MP4 غير صالحة (توقيع ftyp مفقود).")
                        }
                    }

                    "m4a" -> {
                        val hasFtyp = header[4] == 'f'.code.toByte() &&
                                header[5] == 't'.code.toByte() &&
                                header[6] == 'y'.code.toByte() &&
                                header[7] == 'p'.code.toByte()
                        if (!hasFtyp) {
                            return ValidationResult.Invalid("بنية ملف M4A غير صالحة.")
                        }
                    }

                    "mp3" -> {
                        // MP3 starts with "ID3" (0x49 0x44 0x33) or MPEG sync frame (0xFF and high 3 bits of byte 1 set)
                        val isId3 = header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()
                        val isMpegSync = (header[0].toInt() and 0xFF) == 0xFF && ((header[1].toInt() and 0xE0) == 0xE0)

                        if (!isId3 && !isMpegSync) {
                            return ValidationResult.Invalid("توقيع ملف MP3 غير مطابق (ID3 / MPEG Frame مفقود).")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return ValidationResult.Invalid("حدث خطأ أثناء فحص بنية الملف: ${e.localizedMessage}")
        }

        return ValidationResult.Valid
    }

    /**
     * Level 3: Check with Android native MediaExtractor
     */
    fun validateWithAndroidMediaExtractor(context: Context, file: File): ValidationResult {
        if (!file.exists() || file.length() < 1024) {
            return ValidationResult.Invalid("حجم الملف غير كافٍ للفحص عبر محرك النظام.")
        }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount
            if (trackCount > 0) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("محرك أندرويد للوسائط لم يجد أي مسار صوتي أو مرئي صالح داخل الملف.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor parsing check failed: ${e.message}")
            // If MediaExtractor throws, file might be corrupted or unrecognized
            ValidationResult.Invalid("الملف تالف ولا يمكن تشغيله عبر نظام أندرويد: ${e.message}")
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }
}
