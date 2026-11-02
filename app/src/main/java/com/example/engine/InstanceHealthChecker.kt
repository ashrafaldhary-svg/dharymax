package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class InstanceHealthChecker(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    companion object {
        val DEFAULT_INVIDIOUS_INSTANCES = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://yewtu.be",
            "https://invidious.private.coffee",
            "https://invidious.jing.rocks",
            "https://vid.puffyan.us"
        )

        val DEFAULT_PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net"
        )
    }

    private val healthCache = ConcurrentHashMap<String, Long>() // instance -> last healthy timestamp

    /**
     * Check if instance is alive with a fast HEAD / stats request
     */
    suspend fun isInstanceHealthy(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val lastOk = healthCache[baseUrl]
        val now = System.currentTimeMillis()
        if (lastOk != null && (now - lastOk) < 120_000L) { // cached for 2 minutes
            return@withContext true
        }

        try {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/stats")
                .header("User-Agent", "DhariMax/1.0 (Android)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    healthCache[baseUrl] = now
                    true
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            // Also try a light ping to the base domain
            try {
                val ping = Request.Builder()
                    .url(baseUrl)
                    .head()
                    .header("User-Agent", "DhariMax/1.0 (Android)")
                    .build()
                httpClient.newCall(ping).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 301 || resp.code == 302) {
                        healthCache[baseUrl] = now
                        true
                    } else {
                        false
                    }
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Return instances ordered with healthy ones first
     */
    suspend fun getOrderedInstances(candidates: List<String>): List<String> = withContext(Dispatchers.IO) {
        // Find first healthy instance quickly, or return candidates if all offline
        for (candidate in candidates) {
            if (isInstanceHealthy(candidate)) {
                return@withContext listOf(candidate) + candidates.filter { it != candidate }
            }
        }
        candidates
    }
}
