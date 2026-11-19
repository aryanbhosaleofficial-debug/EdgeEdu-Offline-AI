package com.edgeedu.app

import com.edgeedu.app.content.ContentProvisioner
import com.edgeedu.app.content.ContentScope
import com.edgeedu.app.content.ContentSource
import com.edgeedu.app.content.DowngradeException
import com.edgeedu.app.data.CorpusRepository
import com.edgeedu.app.data.IntegrityException
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the PRD v3.1 §12 content lifecycle end-to-end off-device: download
 * (scoped + checksum-verified), load, scope isolation, downgrade rejection, and
 * logout deletion. Pure java.io, so no Robolectric/emulator needed.
 */
class ContentLifecycleTest {

    private val tmp = Files.createTempDirectory("edgeedu").toFile()
    private val contentDir = File(tmp, "content")
    private val versionFile = File(tmp, "content_version")

    @After fun cleanup() { tmp.deleteRecursively() }

    private fun sha256(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun doc(subject: String, std: Int, lang: String, chunkId: String) =
        ("""{"metadata":{"file_name":"f","standard":$std,"subject":"$subject",""" +
            """"language":"$lang","board":"MH","schema_version":"3.0",""" +
            """"content_version":2,"last_updated":"2026-06-13"},""" +
            """"content_chunks":[{"chunk_id":"$chunkId","heading":"H","text":"Body text."}],""" +
            """"generation_status":"complete"}""").toByteArray()

    /** A ContentSource backed by an in-memory file map + a built manifest. */
    private class MapSource(
        private val files: Map<String, ByteArray>,
        version: Int,
        hashOverride: Map<String, String> = emptyMap(),
        sha: (ByteArray) -> String,
    ) : ContentSource {
        private val manifestBytes: ByteArray
        init {
            val entries = files.entries.joinToString(",") { (path, bytes) ->
                val h = hashOverride[path] ?: sha(bytes)
                "\"$path\":{\"sha256\":\"$h\",\"bytes\":${bytes.size}}"
            }
            manifestBytes = ("""{"manifest_schema":1,"content_version":$version,""" +
                """"generated":"2026-06-13T00:00:00Z","algorithm":"sha256",""" +
                """"files":{$entries},"signature":"test-sig"}""").toByteArray()
        }
        override fun manifest() = manifestBytes
        override fun open(path: String) = files.getValue(path)
    }

    /** A stand-in textbook PDF: opaque bytes, hashed+verified like any file. */
    private fun pdf(tag: String) = "%PDF-1.4\n$tag\n%%EOF".toByteArray()

    private fun corpus(version: Int = 2, hashOverride: Map<String, String> = emptyMap()) = MapSource(
        files = mapOf(
            "data/10th/10th_Geography_English.json" to doc("Geography", 10, "English", "1.1"),
            "data/10th/10th_Geography_English.pdf" to pdf("geo-en"),
            "data/10th/10th_Geography_Hindi.json" to doc("Geography", 10, "Hindi", "1.1"),
            "data/9th/English/9th_Geography_English.pdf" to pdf("geo-9-en"),
            "data/9th/9th_Math 1_English.json" to doc("Math 1", 9, "English", "2.1"),
        ),
        version = version,
        hashOverride = hashOverride,
        sha = ::sha256,
    )

    @Test
    fun downloadsOnlyTheScopedSliceAndLoadsIt() {
        val p = ContentProvisioner(contentDir, versionFile)
        val scope = ContentScope(10, "English")
        val result = p.provision(corpus(), scope)

        // The 10th-English slice: the Geography JSON plus its PDF (both downloaded
        // and hash-verified). Other classes/mediums are not fetched.
        assertEquals(2, result.fileCount)
        assertTrue(File(contentDir, "data/10th/10th_Geography_English.json").isFile)
        assertTrue(File(contentDir, "data/10th/10th_Geography_English.pdf").isFile)
        assertFalse(File(contentDir, "data/10th/10th_Geography_Hindi.json").exists())
        assertFalse(File(contentDir, "data/9th/English/9th_Geography_English.pdf").exists())
        assertFalse(File(contentDir, "data/9th/9th_Math 1_English.json").exists())

        // The corpus index counts only the chunk-bearing JSON; the PDF is on disk
        // for the viewer but contributes no searchable chunks.
        val repo = CorpusRepository.load(contentDir, scope)
        assertEquals(1, repo.fileCount)
        assertEquals(1, repo.chunks.size)
        assertEquals("Geography", repo.chunks.single().subject)
        assertEquals(2, repo.contentVersion)
    }

    @Test(expected = IntegrityException::class)
    fun rejectsTamperedContentOnDownload() {
        val p = ContentProvisioner(contentDir, versionFile)
        // Manifest declares a hash that won't match the bytes -> tamper detected.
        val bad = mapOf("data/10th/10th_Geography_English.json" to "deadbeef".repeat(8))
        p.provision(corpus(hashOverride = bad), ContentScope(10, "English"))
    }

    @Test(expected = DowngradeException::class)
    fun rejectsDowngradeAcrossRedownload() {
        val p = ContentProvisioner(contentDir, versionFile)
        val scope = ContentScope(10, "English")
        p.provision(corpus(version = 2), scope)
        p.provision(corpus(version = 1), scope) // older -> rejected
    }

    @Test
    fun logoutDeletesContentButKeepsDowngradeFloor() {
        val p = ContentProvisioner(contentDir, versionFile)
        val scope = ContentScope(10, "English")
        p.provision(corpus(version = 2), scope)

        p.clear()
        assertFalse(p.isProvisioned())
        assertFalse(contentDir.exists())
        assertTrue(versionFile.isFile) // high-water survives logout

        // A stale v1 is still rejected after logout (§12.7).
        try {
            p.provision(corpus(version = 1), scope)
            throw AssertionError("expected downgrade rejection after logout")
        } catch (_: DowngradeException) { /* expected */ }
    }

    /** A ContentSource that throws on every call — a dead remote origin. */
    private class DeadSource : ContentSource {
        override fun manifest(): ByteArray = throw java.io.IOException("HTTP 404 for manifest.json")
        override fun open(path: String): ByteArray = throw java.io.IOException("HTTP 404 for $path")
    }

    @Test
    fun fallbackUsesBundledContentWhenRemoteIsUnavailable() {
        // The production 404: the content release is unpublished, so the remote
        // origin fails — the app must fall back to the identical bundled content
        // and provision successfully rather than hard-failing.
        val source = com.edgeedu.app.content.FallbackContentSource(DeadSource(), corpus())
        val p = ContentProvisioner(contentDir, versionFile)
        val result = p.provision(source, ContentScope(10, "English"))
        assertEquals(2, result.fileCount)
        assertTrue(File(contentDir, "data/10th/10th_Geography_English.json").isFile)
    }

    @Test
    fun fallbackPrefersTheRemoteOriginWhenItWorks() {
        // When the remote origin is healthy it is used as-is (the bundled source
        // here is dead, proving the remote path was taken).
        val source = com.edgeedu.app.content.FallbackContentSource(corpus(), DeadSource())
        val p = ContentProvisioner(contentDir, versionFile)
        assertEquals(2, p.provision(source, ContentScope(10, "English")).fileCount)
    }

    /** Serves the manifest + JSONs but not PDFs — the bundled-asset reality. */
    private class JsonOnlySource(private val full: ContentSource) : ContentSource {
        override fun manifest() = full.manifest()
        override fun open(path: String) =
            if (path.endsWith(".pdf")) throw java.io.FileNotFoundException("not bundled: $path")
            else full.open(path)
    }

    @Test
    fun optionalPdfIsSkippedWhenTheSourceLacksIt() {
        // The exact production case: the manifest lists a PDF, but the origin
        // (bundled assets) only has the JSON. Provisioning must succeed with the
        // JSON content and simply skip the PDF — not fail the whole download.
        val p = ContentProvisioner(contentDir, versionFile)
        val scope = ContentScope(10, "English")
        val result = p.provision(JsonOnlySource(corpus()), scope)

        assertEquals(1, result.fileCount) // the Geography JSON; the PDF was skipped
        assertTrue(File(contentDir, "data/10th/10th_Geography_English.json").isFile)
        assertFalse(File(contentDir, "data/10th/10th_Geography_English.pdf").exists())
        // The corpus still loads and is fully usable from the JSON alone.
        assertEquals(1, CorpusRepository.load(contentDir, scope).fileCount)
    }

    @Test
    fun mandatoryJsonFailureStillThrows() {
        // A JSON is essential: if it can't be fetched, provisioning must fail
        // (never silently ship an app with missing content).
        val brokenJson = object : ContentSource {
            override fun manifest() = corpus().manifest()
            override fun open(path: String) =
                if (path.endsWith(".json")) throw java.io.IOException("boom") else corpus().open(path)
        }
        try {
            ContentProvisioner(contentDir, versionFile).provision(brokenJson, ContentScope(10, "English"))
            throw AssertionError("expected a mandatory-JSON failure to throw")
        } catch (_: java.io.IOException) { /* expected */ }
    }

    /** A chaptered curriculum file: two named chapters, each with one chunk. */
    private fun chapteredDoc() =
        ("""{"metadata":{"file_name":"f","standard":10,"subject":"Geography",""" +
            """"language":"English","board":"MH","schema_version":"3.0",""" +
            """"content_version":2,"last_updated":"2026-06-13"},"chapters":[""" +
            """{"chapter_id":1,"chapter_title":"Field Visit","content_chunks":""" +
            """[{"chunk_id":"1.1","heading":"H","text":"Body."}]},""" +
            """{"chapter_id":2,"chapter_title":"Location and Extent","content_chunks":""" +
            """[{"chunk_id":"2.1","heading":"H","text":"Body."}]}],""" +
            """"generation_status":"complete"}""").toByteArray()

    @Test
    fun loadPreservesChapterIdAndTitlePerChunk() {
        // A chapter-focused session relies on each chunk knowing its chapter, so
        // the loader must carry chapter_id/title through from the nested file.
        val source = MapSource(
            files = mapOf("data/10th/10th_Geography_English.json" to chapteredDoc()),
            version = 2,
            sha = ::sha256,
        )
        ContentProvisioner(contentDir, versionFile).provision(source, ContentScope(10, "English"))
        val repo = CorpusRepository.load(contentDir, ContentScope(10, "English"))

        assertEquals(2, repo.chunks.size)
        val byChapter = repo.chunks.associateBy { it.chapterId }
        assertEquals("Field Visit", byChapter.getValue(1).chapterTitle)
        assertEquals("Location and Extent", byChapter.getValue(2).chapterTitle)
    }

    @Test
    fun scopeMatchesJsonAndPdfByClassFolderAndMediumSuffix() {
        val s = ContentScope(10, "English")
        assertTrue(s.matches("data/10th/10th_Geography_English.json"))
        assertTrue(s.matches("data/10th/10th_Geography_English.pdf"))
        assertTrue(s.matches("data/10th/10th_Science and Technology Part 1_English.json"))
        // Restructured 9th lays files out under a medium subfolder; still matched
        // by class folder + medium suffix.
        assertTrue(ContentScope(9, "English").matches("data/9th/English/9th_Geography_English.pdf"))
        assertFalse(s.matches("data/10th/10th_Geography_Hindi.pdf"))
        assertFalse(s.matches("data/9th/9th_Math 1_English.json"))
    }
}
