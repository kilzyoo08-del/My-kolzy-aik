package com.mykolzyai.studio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Centerion cloud API client.
 *
 * Only the Supabase publishable key is included in the APK. No paid LLM
 * provider is used; service-role credentials remain server-side.
 */
object CenterionApi {
    data class ChatReply(
        val answer: String,
        val conversationId: String,
        val messageId: String,
        val model: String
    )

    data class Conversation(
        val id: String,
        val title: String,
        val updatedAt: String
    )

    data class MemoryDocument(
        val id: String,
        val title: String,
        val sourcePath: String,
        val content: String,
        val updatedAt: String
    )

    private data class Session(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtMillis: Long
    )

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .build()

    private const val PREFS = "centerion_secure_session"
    private const val SESSION_KEY = "session_blob"
    private const val KEY_ALIAS = "centerion_session_aes_v1"

    private val baseUrl: String
        get() = BuildConfig.CENTERION_SUPABASE_URL.trimEnd('/')

    private val publishableKey: String
        get() = BuildConfig.CENTERION_SUPABASE_PUBLISHABLE_KEY

    fun isConfigured(): Boolean =
        baseUrl.startsWith("https://") && publishableKey.isNotBlank()

    fun signIn(context: Context, email: String, password: String) {
        requireConfigured()
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        val response = authRequest("token?grant_type=password", payload)
        saveSession(context, parseSession(response))
    }

    fun signUp(context: Context, email: String, password: String) {
        requireConfigured()
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        val response = authRequest("signup", payload)
        val accessToken = response.optString("access_token")
        if (accessToken.isBlank()) {
            throw IOException("Регистрация создана. Подтверди адрес электронной почты, затем войди в аккаунт.")
        }
        saveSession(context, parseSession(response))
    }

    fun restoreSession(context: Context): Boolean {
        if (!isConfigured()) return false
        val session = loadSession(context) ?: return false
        if (session.expiresAtMillis > System.currentTimeMillis() + 60_000L) return true
        return try {
            refreshSession(context, session)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(SESSION_KEY).apply()
    }

    fun chat(context: Context, message: String, conversationId: String? = null): ChatReply {
        val payload = JSONObject().put("message", message)
        if (!conversationId.isNullOrBlank()) payload.put("conversation_id", conversationId)
        val response = apiRequest(context, "POST", "/v1/chat", payload)
        return ChatReply(
            answer = response.optString("answer"),
            conversationId = response.optString("conversation_id"),
            messageId = response.optString("message_id"),
            model = response.optString("model")
        )
    }

    fun listConversations(context: Context): List<Conversation> {
        val response = apiRequest(context, "GET", "/v1/conversations")
        val rows = response.optJSONArray("conversations") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            Conversation(
                id = row.optString("id"),
                title = row.optString("title"),
                updatedAt = row.optString("updated_at")
            )
        }
    }

    fun loadConversation(context: Context, conversationId: String): List<Pair<String, String>> {
        val response = apiRequest(context, "GET", "/v1/conversations/$conversationId/messages")
        val rows = response.optJSONArray("messages") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            row.optString("role") to row.optString("content")
        }
    }

    fun searchMemory(context: Context, query: String, limit: Int = 5): List<MemoryDocument> {
        val safeLimit = limit.coerceIn(1, 8)
        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val response = apiRequest(context, "GET", "/v1/memory?q=$encodedQuery&limit=$safeLimit")
        val rows = response.optJSONArray("memories") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            MemoryDocument(
                id = row.optString("id"),
                title = row.optString("title"),
                sourcePath = row.optString("source_path"),
                content = row.optString("content"),
                updatedAt = row.optString("updated_at")
            )
        }
    }

    fun saveMemory(
        context: Context,
        title: String,
        sourcePath: String,
        content: String
    ): MemoryDocument {
        val payload = JSONObject()
            .put("title", title)
            .put("source_path", sourcePath)
            .put("content", content)
        val response = apiRequest(context, "POST", "/v1/memory", payload)
        val row = response.optJSONObject("memory") ?: throw IOException("Сервер не вернул сохранённый документ памяти.")
        return MemoryDocument(
            id = row.optString("id"),
            title = row.optString("title"),
            sourcePath = row.optString("source_path"),
            content = "",
            updatedAt = row.optString("updated_at")
        )
    }

    fun deleteMemory(context: Context, memoryId: String) {
        apiRequest(context, "DELETE", "/v1/memory/$memoryId")
    }

    fun saveFeedback(
        context: Context,
        rating: Int,
        conversationId: String?,
        messageId: String?,
        comment: String? = null
    ) {
        val payload = JSONObject().put("rating", rating)
        if (!conversationId.isNullOrBlank()) payload.put("conversation_id", conversationId)
        if (!messageId.isNullOrBlank()) payload.put("message_id", messageId)
        if (!comment.isNullOrBlank()) payload.put("comment", comment)
        apiRequest(context, "POST", "/v1/feedback", payload)
    }

    private fun requireConfigured() {
        if (!isConfigured()) {
            throw IOException("Не заданы CENTERION_SUPABASE_URL и CENTERION_SUPABASE_PUBLISHABLE_KEY при сборке APK.")
        }
    }

    private fun authRequest(path: String, payload: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/$path")
            .header("apikey", publishableKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        return execute(request)
    }

    private fun apiRequest(
        context: Context,
        method: String,
        path: String,
        payload: JSONObject? = null
    ): JSONObject {
        requireConfigured()
        val session = validSession(context)
        val builder = Request.Builder()
            .url("$baseUrl/functions/v1/centerion$path")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
        when (method) {
            "GET" -> builder.get()
            "PATCH" -> builder.patch((payload ?: JSONObject()).toString().toRequestBody(jsonType))
            "DELETE" -> builder.delete()
            else -> builder.post((payload ?: JSONObject()).toString().toRequestBody(jsonType))
        }
        return execute(builder.build())
    }

    private fun validSession(context: Context): Session {
        val current = loadSession(context) ?: throw IOException("Войди в аккаунт Centerion.")
        if (current.expiresAtMillis > System.currentTimeMillis() + 60_000L) return current
        return refreshSession(context, current)
    }

    private fun refreshSession(context: Context, old: Session): Session {
        val payload = JSONObject().put("refresh_token", old.refreshToken)
        val response = authRequest("token?grant_type=refresh_token", payload)
        val refreshed = parseSession(response, old.refreshToken)
        saveSession(context, refreshed)
        return refreshed
    }

    private fun parseSession(json: JSONObject, oldRefreshToken: String = ""): Session {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token").ifBlank { oldRefreshToken }
        if (access.isBlank() || refresh.isBlank()) throw IOException("Supabase Auth не вернул действующую сессию.")
        val expiresAt = json.optLong("expires_at", 0L)
        val expiresAtMillis = if (expiresAt > 0L) expiresAt * 1000L
        else System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
        return Session(access, refresh, expiresAtMillis)
    }

    private fun execute(request: Request): JSONObject {
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = try {
                if (raw.isBlank()) JSONObject() else JSONObject(raw)
            } catch (_: Exception) {
                JSONObject()
            }
            if (!response.isSuccessful) {
                val errorObject = json.optJSONObject("error")
                val errorCode = errorObject?.optString("code").orEmpty()
                    .ifBlank { json.optString("error") }
                val message = when (errorCode) {
                    "model_unavailable" -> "Языковое ядро Centerion ещё не подключено. История и память доступны, но ответы чата пока отключены."
                    "unauthorized" -> "Сессия истекла. Войди в аккаунт Centerion ещё раз."
                    "not_found" -> "Запрошенный объект не найден или недоступен."
                    else -> errorObject?.optString("message").orEmpty()
                        .ifBlank { json.optString("msg") }
                        .ifBlank { json.optString("message") }
                        .ifBlank { json.optString("error_description") }
                        .ifBlank { errorCode }
                        .ifBlank { "HTTP ${response.code}" }
                }
                throw IOException(message)
            }
            return json
        }
    }

    private fun saveSession(context: Context, session: Session) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at_ms", session.expiresAtMillis)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        val encoded = Base64.encodeToString(packed, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SESSION_KEY, encoded).apply()
    }

    private fun loadSession(context: Context): Session? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SESSION_KEY, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= 12) return null
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            Session(
                json.getString("access_token"),
                json.getString("refresh_token"),
                json.getLong("expires_at_ms")
            )
        } catch (_: Exception) {
            signOut(context)
            null
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
