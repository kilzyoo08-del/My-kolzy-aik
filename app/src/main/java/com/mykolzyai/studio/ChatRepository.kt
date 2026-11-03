package com.mykolzyai.studio

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

data class ChatApiResponse(
    val reply: String,
    val conversationId: String
)

class ChatApiException(
    val statusCode: Int?,
    message: String
) : Exception(message)

internal object ChatRequestPolicy {
    const val MAX_MESSAGE_CHARS = 4_000
    private const val MAX_CONVERSATION_ID_CHARS = 128

    fun isSecureBaseUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.isAbsolute &&
            uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.path.orEmpty().let { it.isEmpty() || it == "/" } &&
            (uri.port == -1 || uri.port in 1..65_535)
    }

    fun normalizeMessage(value: String): String {
        val message = value.trim()
        require(message.isNotEmpty()) { "Сообщение не должно быть пустым." }
        require(message.length <= MAX_MESSAGE_CHARS) {
            "Сообщение слишком длинное. Максимум — $MAX_MESSAGE_CHARS символов."
        }
        return message
    }

    fun normalizeConversationId(value: String?): String? {
        val id = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(id.length <= MAX_CONVERSATION_ID_CHARS && id.matches(Regex("[A-Za-z0-9_-]+"))) {
            "Некорректный идентификатор диалога."
        }
        return id
    }
}

/**
 * HTTPS-only client for the Kolzai chat API.
 *
 * Provider secrets stay on the backend. The optional access token must come
 * from a runtime authentication flow; it must never be embedded in BuildConfig.
 */
class ChatRepository(
    private val baseUrl: String = BuildConfig.CHAT_BACKEND_URL,
    private val accessTokenProvider: () -> String? = { null }
) {
    fun isConfigured(): Boolean = ChatRequestPolicy.isSecureBaseUrl(baseUrl)

    fun send(message: String, conversationId: String? = null): ChatApiResponse {
        val normalizedMessage = ChatRequestPolicy.normalizeMessage(message)
        val normalizedConversationId = ChatRequestPolicy.normalizeConversationId(conversationId)
        if (!isConfigured()) {
            throw ChatApiException(null, "Сервер чата не настроен. Нужен HTTPS URL backend.")
        }

        val connection = URL("${baseUrl.trim().trimEnd('/')}/v1/chat")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Cache-Control", "no-store")

            val accessToken = accessTokenProvider()?.trim()?.takeIf { it.isNotEmpty() }
            if (accessToken != null) {
                require(accessToken.length <= 4_096 && accessToken.none { it.isISOControl() }) {
                    "Некорректный токен авторизации."
                }
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val requestBody = JSONObject()
                .put("message", normalizedMessage)
                .put("mode", "roblox")
                .apply {
                    normalizedConversationId?.let { put("conversationId", it) }
                }
                .toString()
            connection.outputStream.use {
                it.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = readBoundedBody(stream)
            if (status !in 200..299) throw errorForStatus(status)

            val json = runCatching { JSONObject(responseBody) }.getOrElse {
                throw ChatApiException(status, "Сервер чата вернул некорректный ответ.")
            }
            val reply = json.optString("reply").trim()
            if (reply.isEmpty()) {
                throw ChatApiException(status, "Сервер чата не вернул текст ответа.")
            }

            val returnedId = json.optString("conversationId").takeIf { it.isNotBlank() }
            val resolvedId = try {
                ChatRequestPolicy.normalizeConversationId(returnedId ?: normalizedConversationId)
                    ?: UUID.randomUUID().toString()
            } catch (_: IllegalArgumentException) {
                throw ChatApiException(status, "Сервер вернул некорректный идентификатор диалога.")
            }
            return ChatApiResponse(reply, resolvedId)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBoundedBody(stream: InputStream?): String {
        if (stream == null) return ""
        val output = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(4_096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw ChatApiException(null, "Ответ сервера превышает допустимый размер.")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun errorForStatus(status: Int): ChatApiException {
        val message = when (status) {
            301, 302, 303, 307, 308 ->
                "Сервер перенаправил запрос. Укажи конечный HTTPS URL backend."
            400, 413 ->
                "Сервер отклонил сообщение. Проверь его формат и длину."
            401, 403 ->
                "Сервер не принял авторизацию. Нужен действительный короткоживущий токен."
            404 ->
                "На backend не найден endpoint /v1/chat."
            408, 504 ->
                "Сервер чата не успел ответить. Попробуй ещё раз."
            429 ->
                "Превышен лимит запросов. Подожди немного и повтори."
            in 500..599 ->
                "Сервис языковой модели временно недоступен."
            else ->
                "Сервер чата вернул ошибку HTTP $status."
        }
        return ChatApiException(status, message)
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1_024
    }
}
