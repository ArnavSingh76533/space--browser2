package com.spacebrowser.core.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiConfig(val endpoint: String, val model: String, val apiKey: String) {
    val isConfigured: Boolean get() = endpoint.isNotBlank() && model.isNotBlank()
}

/**
 * Minimal chat-completions client for any OpenAI-compatible server
 * (hosted providers, or a local llama.cpp / Ollama-style server). The user
 * supplies endpoint, model and key in Settings > AI; nothing is sent anywhere
 * until they do and explicitly invoke an assistant action.
 */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun complete(config: AiConfig, system: String, user: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured) {
                return@withContext Result.failure(IllegalStateException("AI is not configured. Add an endpoint and model in Settings > AI."))
            }
            try {
                val payload = JSONObject()
                    .put("model", config.model)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", user))
                    )
                    .put("max_tokens", 1024)
                val url = config.endpoint.trimEnd('/') + "/chat/completions"
                val builder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                if (config.apiKey.isNotBlank()) {
                    builder.header("Authorization", "Bearer ${config.apiKey}")
                }
                http.newCall(builder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            RuntimeException("AI request failed (${resp.code}): ${body.take(200)}")
                        )
                    }
                    val content = JSONObject(body)
                        .optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content")
                    if (content.isNullOrBlank()) {
                        Result.failure(RuntimeException("Empty AI response."))
                    } else {
                        Result.success(content.trim())
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
