package com.edgeedu.app.content

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads content from a GitHub Release — the project's zero-infra download origin
 * (PRD §9, §12.2). On login the app downloads its class+medium slice (both the
 * curriculum JSON and the textbook PDFs) from the release, PUBG-style, instead of
 * bundling 200 MB into the APK.
 *
 * A release is a FLAT bucket: it has no folders, and GitHub rewrites each space in
 * an asset name to a '.'. So each manifest path (e.g. "data/9th/English/9th_Maths
 * 1_English.pdf") is mapped to the name GitHub stores it under ("9th_Maths.1_English.pdf"),
 * which is exactly how the curriculum files land when uploaded; the signed manifest
 * is uploaded as "manifest.json". Every downloaded byte is still SHA-256-verified
 * against the manifest by [ContentProvisioner], so the untrusted CDN is transport
 * only — it cannot inject or tamper with content.
 */
class GithubContentSource(
    owner: String,
    repo: String,
    tag: String,
    private val timeoutMs: Int = 30_000,
) : ContentSource {
    private val base = "https://github.com/$owner/$repo/releases/download/$tag"

    override fun manifest(): ByteArray = fetch(assetName("data/${ContentProvisioner.MANIFEST}"))

    override fun open(path: String): ByteArray = fetch(assetName(path))

    private fun fetch(asset: String): ByteArray {
        // GitHub release downloads 302-redirect to objects.githubusercontent.com;
        // follow redirects manually so a cross-host hop is always honoured.
        var url = URL("$base/$asset")
        repeat(MAX_REDIRECTS) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> return conn.inputStream.use { it.readBytes() }
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308 -> {
                        val location = conn.getHeaderField("Location")
                            ?: throw IOException("redirect without Location for $asset")
                        url = URL(url, location)
                    }
                    else -> throw IOException("HTTP $code for $asset")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("too many redirects for $asset")
    }

    companion object {
        private const val MAX_REDIRECTS = 5

        /**
         * The release asset name a manifest [path] is served under: the file's base
         * name with each space replaced by '.', mirroring GitHub's own asset-name
         * sanitization. (Curriculum file names contain no other characters GitHub
         * rewrites, so this is an exact round-trip — verified against the published
         * release.)
         */
        fun assetName(path: String): String =
            path.substringAfterLast('/').replace(' ', '.')
    }
}
