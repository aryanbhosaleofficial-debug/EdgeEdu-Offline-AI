package com.edgeedu.app.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One question-and-answer exchange, as stored in local history and uploaded
 * to the sync backend. [client_id] is the device-local row id, so re-uploads
 * are idempotent server-side (same account + client_id = same row).
 */
@Serializable
data class HistoryEntry(
    val client_id: Long = 0,
    val asked_at: Long,
    val standard: Int,
    val medium: String,
    val subject: String,
    val question: String,
    val answer: String,
    val engine: String = "",
)

class SyncException(message: String) : Exception(message)

/**
 * Minimal REST client for the account + history-backup backend (PRD §17).
 * The base URL is build config (-PsyncBaseUrl=…), like the content host — any
 * server implementing three JSON endpoints works:
 *
 *   POST {base}/auth/signup  {"email","password"}          → {"token": "…"}
 *   POST {base}/auth/login   {"email","password"}          → {"token": "…"}
 *   POST {base}/history      Bearer token, [HistoryEntry…] → 2xx
 *
 * Plain HttpURLConnection (no SDK): unit-testable against a loopback server,
 * and zero extra dependencies on the 2 GB-RAM device target.
 */
class SyncClient(private val baseUrl: String, private val timeoutMs: Int = 20_000) {

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /** Creates the account and returns its auth token. */
    fun signup(email: String, password: String): String = authCall("auth/signup", email, password)

    /** Signs in to an existing account and returns its auth token. */
    fun login(email: String, password: String): String = authCall("auth/login", email, password)

    /** Uploads a batch of history entries. Throws on any non-2xx response. */
    fun pushHistory(token: String, entries: List<HistoryEntry>) {
        if (entries.isEmpty()) return
        post("history", json.encodeToString(entries), token)
    }

    private fun authCall(path: String, email: String, password: String): String {
        val cleanEmail = email.trim()
        if (!cleanEmail.contains('@') || cleanEmail.length < 5) {
            throw SyncException("Enter a valid email address.")
        }
        if (password.length < 6) throw SyncException("Password must be at least 6 characters.")
        val body = json.encodeToString(mapOf("email" to cleanEmail, "password" to password))
        val response = post(path, body, token = null)
        val token = try {
            json.parseToJsonElement(response).jsonObject["token"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        return token ?: throw SyncException("The server did not return a token.")
    }

    private fun post(path: String, body: String, token: String?): String {
        if (!isConfigured) throw SyncException("Sync is not configured in this build.")
        val conn = URL("${baseUrl.trimEnd('/')}/$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.use { it.readBytes().decodeToString() }?.take(200).orEmpty()
                throw SyncException("HTTP $code${if (detail.isBlank()) "" else ": $detail"}")
            }
            return conn.inputStream.use { it.readBytes().decodeToString() }
        } catch (e: IOException) {
            throw SyncException(e.message ?: "network error")
        } finally {
            conn.disconnect()
        }
    }
}
