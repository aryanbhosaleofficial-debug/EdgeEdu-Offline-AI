package com.edgeedu.app.content

import com.edgeedu.app.data.IntegrityException
import com.edgeedu.app.data.Manifest
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json

/** Raised when an offered content version is older than what's installed (§12.7). */
class DowngradeException(message: String) : Exception(message)

data class ProvisionResult(val contentVersion: Int, val fileCount: Int)

/**
 * Owns the on-device content lifecycle (PRD §12). On login it copies the
 * profile's class+medium slice from a [ContentSource] into [contentDir],
 * verifying every file's SHA-256 against the signed manifest before it lands;
 * on logout it deletes that slice ([clear]). A monotonic high-water version,
 * kept in [versionFile] outside [contentDir] so it survives logout, rejects
 * downgrade attacks (§12.7).
 *
 * Deliberately built on java.io + MessageDigest (no Android framework types)
 * so the whole lifecycle is unit-testable off-device.
 */
class ContentProvisioner(
    private val contentDir: File,
    private val versionFile: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isProvisioned(): Boolean = File(contentDir, MANIFEST).isFile

    /**
     * Downloads and verifies the [scope] slice into [contentDir], replacing any
     * existing content atomically (staged in a sibling dir, then swapped, so a
     * failed or cancelled download never leaves a half-written corpus).
     *
     * [onProgress] is called with (filesDone, filesTotal) after each verified
     * file, so the UI can show real download progress (PRD §12.2 / §16).
     */
    fun provision(
        source: ContentSource,
        scope: ContentScope,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ProvisionResult {
        val manifestBytes = source.manifest()
        val manifest: Manifest = json.decodeFromString(manifestBytes.decodeToString())

        val floor = readHighWater()
        if (manifest.content_version < floor) {
            throw DowngradeException(
                "offered content v${manifest.content_version} is older than installed v$floor"
            )
        }

        val scoped = manifest.files.entries
            .filter { scope.matches(it.key) }
            .sortedBy { it.key }
        if (scoped.isEmpty()) throw IntegrityException("no content for ${scope.label}")

        // JSON files carry all searchable content and are mandatory; PDFs are
        // supplementary (a reader convenience) and OPTIONAL — a PDF that the
        // source can't provide (e.g. the bundled-asset fallback ships JSON only,
        // to keep the APK small) is skipped rather than failing the whole
        // download. Every file that IS fetched is still SHA-256 verified.
        val mandatory = scoped.filter { it.key.endsWith(".json") }
        if (mandatory.isEmpty()) throw IntegrityException("no content for ${scope.label}")

        val staging = File(contentDir.parentFile, "${contentDir.name}.tmp")
        staging.deleteRecursively()
        staging.mkdirs()
        onProgress(0, scoped.size)
        var done = 0
        var written = 0
        scoped.forEach { (rel, info) ->
            val optional = !rel.endsWith(".json")
            val bytes = try {
                source.open(rel)
            } catch (e: Exception) {
                if (optional) { onProgress(++done, scoped.size); return@forEach }
                throw e
            }
            if (sha256(bytes) != info.sha256) {
                if (optional) { onProgress(++done, scoped.size); return@forEach }
                throw IntegrityException("hash mismatch for $rel")
            }
            File(staging, rel).apply {
                parentFile?.mkdirs()
                writeBytes(bytes)
            }
            written++
            onProgress(++done, scoped.size)
        }
        // Keep the full signed manifest (signature intact) alongside the scoped
        // files; CorpusRepository re-verifies the active scope against it.
        File(staging, MANIFEST).writeBytes(manifestBytes)

        contentDir.deleteRecursively()
        if (!staging.renameTo(contentDir)) {
            staging.copyRecursively(contentDir, overwrite = true)
            staging.deleteRecursively()
        }
        writeHighWater(maxOf(floor, manifest.content_version))
        return ProvisionResult(manifest.content_version, written)
    }

    /**
     * Logout (§12.4): delete downloaded content and the derived index dir. The
     * version high-water mark ([versionFile]) and user data (a separate DB)
     * live elsewhere and are deliberately left intact.
     */
    fun clear() {
        contentDir.deleteRecursively()
    }

    private fun readHighWater(): Int =
        versionFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: 0

    private fun writeHighWater(v: Int) {
        versionFile.parentFile?.mkdirs()
        versionFile.writeText(v.toString())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val MANIFEST = "manifest.json"
    }
}
