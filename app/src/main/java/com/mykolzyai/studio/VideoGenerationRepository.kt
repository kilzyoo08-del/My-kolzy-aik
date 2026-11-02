package com.mykolzyai.studio

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * Client for Kolzai's own video backend.
 *
 * The provider secret must never be placed in this Android app. Configure
 * VIDEO_BACKEND_URL with the HTTPS base URL of the deployed backend.
 */
data class VideoJob(
    val id: String,
    val status: String,
    val progress: Int? = null,
    val resultUrl: String? = null,
    val error: String? = null
)

class VideoGenerationRepository(
    private val baseUrl: String = BuildConfig.VIDEO_BACKEND_URL,
    private val accessToken: () -> String? = { null }
) {
    private val executor = Executors.newFixedThreadPool(2)

    fun isConfigured(): Boolean =
        baseUrl.startsWith("https://") && baseUrl.removeSuffix("/").isNotBlank()

    fun create(
        prompt: String,
        durationSeconds: Int,
        aspectRatio: String,
        callback: (Result<VideoJob>) -> Unit
    ) {
        if (prompt.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Опиши видео перед запуском.")))
            return
        }
        if (durationSeconds !in 1..60) {
            callback(Result.failure(IllegalArgumentException("Недопустимая длительность видео.")))
            return
        }
        if (!isConfigured()) {
            callback(Result.failure(IllegalStateException("Сервер генерации не настроен. Нужен HTTPS URL backend.")))
            return
        }
        executor.execute {
            runCatching {
                val body = JSONObject()
                    .put("prompt", prompt.trim())
                    .put("duration_seconds", durationSeconds)
                    .put("aspect_ratio", aspectRatio)
                    .put("resolution", "720p")
                request("POST", "/v1/video/jobs", body)
            }.onSuccess { callback(Result.success(it)) }
                .onFailure { callback(Result.failure(it)) }
        }
    }

    fun refresh(jobId: String, callback: (Result<VideoJob>) -> Unit) {
        if (!isConfigured()) {
            callback(Result.failure(IllegalStateException("Сервер генерации не настроен.")))
            return
        }
        executor.execute {
            runCatching { request("GET", "/v1/video/jobs/${encodePath(jobId)}", null) }
                .onSuccess { callback(Result.success(it)) }
                .onFailure { callback(Result.failure(it)) }
        }
    }

    fun cancel(jobId: String, callback: (Result<Unit>) -> Unit) {
        if (!isConfigured()) {
            callback(Result.failure(IllegalStateException("Сервер генерации не настроен.")))
            return
        }
        executor.execute {
            runCatching {
                request("DELETE", "/v1/video/jobs/${encodePath(jobId)}", null)
                Unit
            }.onSuccess { callback(Result.success(Unit)) }
                .onFailure { callback(Result.failure(it)) }
        }
    }

    private fun request(method: String, path: String, body: JSONObject?): VideoJob {
        val connection = (URL(baseUrl.removeSuffix("/") + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            accessToken()?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(raw).optString("message") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Ошибка сервера: HTTP $code")
            }
            val json = JSONObject(raw)
            val result = json.optJSONObject("result")
            return VideoJob(
                id = json.optString("job_id"),
                status = json.optString("status", "queued"),
                progress = if (json.has("progress") && !json.isNull("progress")) json.optInt("progress") else null,
                resultUrl = result?.optString("url)?.takeIf { it.isNotBlank() },
                error = json.optJSONObject("error")?.optString("message")
            ).also {
                if (it.id.isBlank()) throw IllegalStateException("Сервер не вернул job_id.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
