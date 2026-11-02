package com.example.engine

import com.example.data.model.MediaInfo
import com.example.data.model.MediaPlatform
import com.example.engine.providers.DirectMediaExtractorProvider
import com.example.engine.providers.FacebookExtractorProvider
import com.example.engine.providers.MediaExtractorProvider
import com.example.engine.providers.YouTubeExtractorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

class MediaExtractorRegistry {

    private val providers = CopyOnWriteArrayList<MediaExtractorProvider>()

    init {
        // Register default providers in priority order
        providers.add(YouTubeExtractorProvider())
        providers.add(FacebookExtractorProvider())
        providers.add(DirectMediaExtractorProvider())
    }

    fun registerProvider(provider: MediaExtractorProvider) {
        providers.add(provider)
        providers.sortByDescending { it.priority }
    }

    fun findProviderForUrl(url: String): MediaExtractorProvider? {
        val trimmed = url.trim()
        return providers.firstOrNull { it.supports(trimmed) }
    }

    suspend fun extract(url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()

        // 1. Check if user clicked on curated test media
        val matchedSample = MediaExtractorEngine.SAMPLE_MEDIA_LIST.firstOrNull {
            cleanUrl.contains(it.url, ignoreCase = true) || it.title.contains(cleanUrl, ignoreCase = true)
        }
        if (matchedSample != null) {
            return@withContext Result.success(matchedSample)
        }

        // 2. Select matching provider
        val provider = findProviderForUrl(cleanUrl)
        if (provider != null) {
            val result = provider.extract(cleanUrl)
            if (result.isSuccess) {
                return@withContext result
            }
        }

        // 3. Fallback: Try all providers that might handle it
        for (p in providers) {
            if (p != provider) {
                val attempt = p.extract(cleanUrl)
                if (attempt.isSuccess) {
                    return@withContext attempt
                }
            }
        }

        Result.failure(IllegalArgumentException("لم نتمكن من العثور على دفق وسائط صالح للرابط. تأكد من أن الرابط متاح للعامة أو جرب رابطاً آخر."))
    }
}
