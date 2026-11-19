package com.edgeedu.app

import com.edgeedu.app.sync.HistoryEntry
import com.edgeedu.app.sync.HistorySource
import com.edgeedu.app.sync.SyncClient
import com.edgeedu.app.sync.SyncException
import com.edgeedu.app.sync.SyncManager
import com.edgeedu.app.sync.SyncResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The account + offline-history sync feature (PRD §17): the REST client is
 * exercised against a real loopback HTTP server (same hermetic pattern as
 * HttpContentSourceTest), and the sync loop's mark-only-after-accept and
 * batching guarantees are verified with a fake store.
 */
class SyncTest {

    // ---- Mini HTTP server ----------------------------------------------------

    private lateinit var server: ServerSocket
    @Volatile private var running = true
    private val requests = mutableListOf<Pair<String, String>>() // path to body
    @Volatile private var failNext = false

    private fun baseUrl() = "http://localhost:${server.localPort}"

    @Before
    fun startServer() {
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (running) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                socket.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    val requestLine = reader.readLine() ?: return@use
                    val path = requestLine.split(" ").getOrNull(1).orEmpty()
                    var contentLength = 0
                    var auth = ""
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) break
                        if (line.startsWith("Content-Length:")) contentLength = line.substringAfter(':').trim().toInt()
                        if (line.startsWith("Authorization:")) auth = line.substringAfter(':').trim()
                    }
                    val body = CharArray(contentLength).let { buf ->
                        var read = 0
                        while (read < contentLength) {
                            val n = reader.read(buf, read, contentLength - read)
                            if (n < 0) break
                            read += n
                        }
                        String(buf, 0, read)
                    }
                    synchronized(requests) { requests += path to body }
                    val out = s.getOutputStream()
                    val (code, payload) = when {
                        failNext -> { failNext = false; 500 to """{"error":"boom"}""" }
                        path == "/auth/signup" || path == "/auth/login" -> 200 to """{"token":"tok-123"}"""
                        path == "/history" && auth == "Bearer tok-123" -> 200 to """{"ok":true}"""
                        path == "/history" -> 401 to """{"error":"unauthorized"}"""
                        else -> 404 to """{"error":"not found"}"""
                    }
                    out.write(
                        ("HTTP/1.1 $code X\r\nContent-Length: ${payload.length}\r\n" +
                            "Connection: close\r\n\r\n$payload").toByteArray()
                    )
                    out.flush()
                }
            }
        }
    }

    @After
    fun stop() {
        running = false
        server.close()
    }

    // ---- SyncClient ----------------------------------------------------------

    @Test
    fun signupAndLoginReturnTheToken() {
        val client = SyncClient(baseUrl())
        assertEquals("tok-123", client.signup("student@example.com", "secret1"))
        assertEquals("tok-123", client.login("student@example.com", "secret1"))
    }

    @Test(expected = SyncException::class)
    fun rejectsInvalidEmailBeforeTouchingTheNetwork() {
        SyncClient(baseUrl()).signup("not-an-email", "secret1")
    }

    @Test(expected = SyncException::class)
    fun rejectsShortPasswords() {
        SyncClient(baseUrl()).signup("student@example.com", "123")
    }

    @Test
    fun pushHistorySendsBearerTokenAndJsonArray() {
        val client = SyncClient(baseUrl())
        client.pushHistory("tok-123", listOf(entry(1, "what is gravity"), entry(2, "solve 2x+3=7")))
        val (path, body) = synchronized(requests) { requests.last() }
        assertEquals("/history", path)
        assertTrue(body.contains("what is gravity"))
        assertTrue(body.startsWith("["))
    }

    @Test(expected = SyncException::class)
    fun surfacesAuthFailures() {
        SyncClient(baseUrl()).pushHistory("wrong-token", listOf(entry(1, "q")))
    }

    @Test(expected = SyncException::class)
    fun unconfiguredClientRefusesToCall() {
        SyncClient("").signup("student@example.com", "secret1")
    }

    // ---- SyncManager ---------------------------------------------------------

    private fun entry(id: Long, q: String) = HistoryEntry(
        client_id = id, asked_at = 1_000 + id, standard = 10, medium = "English",
        subject = "Science 1", question = q, answer = "answer to $q", engine = "test",
    )

    /** In-memory stand-in for the SQLite history store. */
    private class FakeStore(entries: List<HistoryEntry>) : HistorySource {
        val pending = entries.toMutableList()
        val synced = mutableListOf<Long>()
        override fun unsynced(limit: Int) = pending.take(limit)
        override fun markSynced(clientIds: List<Long>) {
            synced += clientIds
            pending.removeAll { it.client_id in clientIds }
        }
        override fun unsyncedCount() = pending.size
    }

    @Test
    fun syncsInBatchesAndMarksOnlyAcceptedRows() {
        val store = FakeStore((1L..120L).map { entry(it, "q$it") })
        val result = SyncManager(SyncClient(baseUrl()), store, batchSize = 50).sync("tok-123")
        assertEquals(SyncResult.Synced(120), result)
        assertEquals(0, store.unsyncedCount())
        assertEquals(120, store.synced.size)
        // 120 rows at batch size 50 = 3 requests.
        assertEquals(3, synchronized(requests) { requests.count { it.first == "/history" } })
    }

    @Test
    fun serverFailureLeavesRowsPendingForTheNextAttempt() {
        val store = FakeStore((1L..10L).map { entry(it, "q$it") })
        failNext = true
        val result = SyncManager(SyncClient(baseUrl()), store).sync("tok-123")
        assertTrue(result is SyncResult.Failed)
        // Nothing was marked synced: the batch is retried when back online.
        assertEquals(10, store.unsyncedCount())
        // A later attempt succeeds and drains the queue.
        assertEquals(SyncResult.Synced(10), SyncManager(SyncClient(baseUrl()), store).sync("tok-123"))
        assertEquals(0, store.unsyncedCount())
    }

    @Test
    fun skipsWithoutAccountOrWithNothingPending() {
        val store = FakeStore(listOf(entry(1, "q")))
        assertTrue(SyncManager(SyncClient(baseUrl()), store).sync(null) is SyncResult.Skipped)
        assertTrue(SyncManager(SyncClient(""), store).sync("tok-123") is SyncResult.Skipped)
        assertEquals(1, store.unsyncedCount()) // untouched offline
        val drained = FakeStore(emptyList())
        assertTrue(SyncManager(SyncClient(baseUrl()), drained).sync("tok-123") is SyncResult.Skipped)
    }
}
