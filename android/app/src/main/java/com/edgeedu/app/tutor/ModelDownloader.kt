package com.edgeedu.app.tutor

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the on-device LLM (a ~2 GB GGUF, e.g. Qwen2.5-3B-Instruct) from [url]
 * into [target]. Two properties matter at this size:
 *
 *  - it **streams to disk** (the file is far too big to hold in memory), and
 *  - it **resumes** an interrupted transfer with an HTTP Range request — a 2 GB
 *    download over mobile data will drop, and re-fetching from zero each time is
 *    not acceptable.
 *
 * Bytes land in "<target>.part" and the file is only renamed into [target] once
 * complete, so a half-written model is never mistaken for a usable one. Hugging
 * Face "resolve" URLs 302-redirect to a CDN and honour Range, so redirects are
 * followed manually with the Range header re-applied on each hop.
 */
class ModelDownloader(
    private val url: String,
    private val target: File,
    private val timeoutMs: Int = 30_000,
) {
    data class Progress(val bytesDone: Long, val bytesTotal: Long)

    @Volatile private var cancelled = false

    /** Ask an in-flight [download] to stop; it throws so the caller can react. */
    fun cancel() { cancelled = true }

    fun isDownloaded(): Boolean = target.isFile && target.length() > 0

    private val partFile: File get() = File(target.parentFile, "${target.name}.part")

    /**
     * Download to [target], resuming the ".part" file if one exists. [onProgress]
     * is invoked as bytes arrive. Throws [IOException] on network/IO failure (or
     * cancellation) so the caller can offer retry; returns when the file is
     * complete and in place.
     */
    fun download(onProgress: (Progress) -> Unit) {
        if (isDownloaded()) return
        cancelled = false
        target.parentFile?.mkdirs()
        val part = partFile
        val resumeFrom = if (part.isFile) part.length() else 0L

        val conn = openFollowingRedirects(resumeFrom)
        try {
            val resuming = conn.responseCode == HTTP_PARTIAL
            val startAt = if (resuming) resumeFrom else 0L
            val total = totalBytes(conn, startAt)

            conn.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { out ->
                    if (resuming) out.seek(startAt) else out.setLength(0)
                    var done = startAt
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        if (cancelled) throw IOException("download cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        onProgress(Progress(done, total))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
    }

    private fun openFollowingRedirects(resumeFrom: Long): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK, HTTP_PARTIAL -> return conn
                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER, HTTP_TEMP_REDIRECT, HTTP_PERM_REDIRECT -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location == null) throw IOException("redirect without Location")
                    current = URL(current, location)
                }
                else -> { conn.disconnect(); throw IOException("HTTP $code downloading model") }
            }
        }
        throw IOException("too many redirects downloading model")
    }

    /** Total file size: from Content-Range on a resume, else Content-Length + offset. */
    private fun totalBytes(conn: HttpURLConnection, startAt: Long): Long {
        val range = conn.getHeaderField("Content-Range") // "bytes start-end/total"
        if (range != null && range.contains('/')) {
            range.substringAfterLast('/').trim().toLongOrNull()?.let { return it }
        }
        val len = conn.contentLengthLong
        return if (len > 0) len + startAt else -1L
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val HTTP_PARTIAL = 206
        private const val HTTP_TEMP_REDIRECT = 307
        private const val HTTP_PERM_REDIRECT = 308
    }
}
