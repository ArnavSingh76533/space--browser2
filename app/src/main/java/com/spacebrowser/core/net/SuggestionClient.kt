package com.spacebrowser.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Fetches search suggestions from an OpenSearch-style endpoint that returns
 * `["query", ["suggestion", ...]]`.
 */
class SuggestionClient(private val http: OkHttpClient) {

    suspend fun fetch(template: String, query: String, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            try {
                val url = template.replace("%s", URLEncoder.encode(query, "UTF-8"))
                val request = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) SPACE/0.1")
                    .build()
                http.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val body = resp.body?.string() ?: return@withContext emptyList()
                    val root = JSONArray(body)
                    val arr = root.optJSONArray(1) ?: return@withContext emptyList()
                    buildList {
                        for (i in 0 until minOf(arr.length(), limit)) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }
}
