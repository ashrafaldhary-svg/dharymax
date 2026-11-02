package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class InstanceHealthChecker(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2000, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        val DEFAULT_INVIDIOUS_INSTANCES = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://invidious.private.coffee"
        )

        val DEFAULT_PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net"
        )
    }

    private val healthCache = ConcurrentHashMap<String, Long>()

    /**
     * Dual-check: attempts GET with small range first, fallback to HEAD if needed
     */
    suspend fun isInstanceHealthy(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastOk = healthCache[baseUrl]
        if (lastOk != null && (now - lastOk) < 180_000L) { // 3-minute cache
            return@withContext true
        }

        // 1. Try GET with stats endpoint
        try {
            val statsRequest = Request.Builder()
                .url("$baseUrl/api/v1/stats")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                .get()
                .build()

            httpClient.newCall(statsRequest).execute().use { response ->
                if (response.isSuccessful) {
                    healthCache[baseUrl] = now
                    return@withContext true
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to GET with small Range request (never rely on HEAD alone)
        try {
            val getRequest = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                .header("Range", "bytes=0-128")
                .get()
                .build()

            httpClient.newCall(getRequest).execute().use { response ->
                if (response.isSuccessful || response.code == 206 || response.code == 301 || response.code == 302) {
                    healthCache[baseUrl] = now
                    return@withContext true
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: try HEAD as last resort
        try {
            val headRequest = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                .head()
                .build()

            httpClient.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful || response.code == 301 || response.code == 302) {
                    healthCache[baseUrl] = now
                    return@withContext true
                }
            }
        } catch (_: Exception) {}

        false
    }

    suspend fun getOrderedInstances(candidates: List<String>): List<String> = withContext(Dispatchers.IO) {
        val healthy = mutableListOf<String>()
        val rest = mutableListOf<String>()

        for (candidate in candidates) {
            if (isInstanceHealthy(candidate)) {
                healthy.add(candidate)
            } else {
                rest.add(candidate)
            }
        }

        if (healthy.isNotEmpty()) healthy + rest else candidates
    }
}
