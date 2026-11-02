package com.example.engine.providers

import com.example.data.model.MediaInfo

interface MediaExtractorProvider {
    val name: String
    val priority: Int // Higher priority runs first

    fun supports(url: String): Boolean
    suspend fun extract(url: String): Result<MediaInfo>
}
