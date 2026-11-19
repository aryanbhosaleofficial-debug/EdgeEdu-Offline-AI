package com.edgeedu.app

import com.edgeedu.app.data.CurriculumDoc
import com.edgeedu.app.data.IndexedChunk
import com.edgeedu.app.notes.NoteStructurer
import com.edgeedu.app.search.Bm25Index
import com.edgeedu.app.session.Subject
import com.edgeedu.app.tutor.MockLlmEngine
import com.edgeedu.app.tutor.Tutor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The upload pipeline (PRD §8): any user file becomes the same v3.1 curriculum
 * shape the shipped subjects use — chapters, chunks with explanation / key
 * points / keywords, and extracted Q&A, key terms and formulas the tutor can
 * answer from just like the built-in textbooks.
 */
class NoteStructurerTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val sampleNotes = """
        # Photosynthesis

        Photosynthesis is the process by which green plants prepare their own food
        using sunlight, water and carbon dioxide in the chloroplasts of their cells.

        - Occurs only in green parts of the plant
        - Needs chlorophyll to trap sunlight

        Chlorophyll: the green pigment in plants that absorbs light energy.

        6CO2 + 6H2O = C6H12O6 + 6O2

        Q: Why are leaves green?
        A: Because they contain chlorophyll, which reflects green light.

        # Respiration

        Respiration is the process of releasing energy from food inside cells.
        Why does respiration produce carbon dioxide?
        Glucose is broken down and its carbon leaves as carbon dioxide gas.
    """.trimIndent()

    @Test
    fun structuresIntoV31Chapters() {
        val chapters = NoteStructurer.structureChapters(sampleNotes, "biology.txt")
        assertEquals(listOf("Photosynthesis", "Respiration"), chapters.map { it.title })

        val photo = chapters.first().chunks.first()
        assertTrue("body kept", photo.body.contains("prepare their own food"))
        assertTrue("key points", photo.key_points.any { it.contains("chlorophyll", ignoreCase = true) })
        assertTrue("keywords", photo.keywords.isNotEmpty())
        // The equation line becomes a formula.
        assertTrue("formula", photo.formulas.any { it.latex.contains("6CO2") })
        // "Chlorophyll: …" becomes a key term.
        assertTrue("key term", photo.key_terms.any { it.term.equals("Chlorophyll", ignoreCase = true) })
        // "Q: …/A: …" becomes a common doubt.
        assertTrue("Q&A", photo.common_doubts.any { it.q.contains("green") && it.a.contains("chlorophyll") })
        // A bare "Why …?" followed by its answer line is captured too.
        val resp = chapters[1].chunks.first()
        assertTrue(resp.common_doubts.any { it.q.startsWith("Why does respiration") })
    }

    @Test
    fun buildsValidCurriculumJson() {
        val chapters = NoteStructurer.structureChapters(sampleNotes, "biology.txt")
        val jsonText = NoteStructurer.buildJson("My Biology", 10, "English", chapters)
        // It parses back as a curriculum document with unique chapter/chunk ids.
        val doc: CurriculumDoc = json.decodeFromString(jsonText)
        assertEquals(2, doc.chapters.size)
        assertEquals("1.1", doc.chapters.first().content_chunks.first().chunk_id)
        val ids = doc.chapters.flatMap { it.content_chunks.map { c -> c.chunk_id } }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("My Biology", doc.metadata.subject)
    }

    @Test
    fun tutorAnswersAnUploadedQuestionVerbatim() = runTest {
        // The whole point: a question in an uploaded file is answered by the
        // tutor exactly like a shipped textbook question.
        val chapters = NoteStructurer.structureChapters(sampleNotes, "biology.txt")
        val doc: CurriculumDoc = json.decodeFromString(
            NoteStructurer.buildJson("My Biology", 10, "English", chapters)
        )
        val chunks = doc.chapters.flatMap { ch ->
            ch.content_chunks.map {
                IndexedChunk(it, "custom:1", 10, "My Biology", "English")
            }
        }
        val tutor = Tutor(Bm25Index(chunks), MockLlmEngine(), computeEnabled = false)
        val reply = tutor.ask("Why are leaves green?")
        assertTrue(reply.grounded)
        assertTrue(reply.text.contains("chlorophyll"))
    }

    @Test
    fun curriculumJsonRoundTrips() {
        // Uploading a file that already IS a curriculum JSON keeps its chapters
        // and all enrichments, rather than flattening it back to prose.
        val original = """
            {"metadata":{"file_name":"x","standard":10,"subject":"Science",
             "language":"English","board":"MH","schema_version":"3.1","content_version":1,
             "last_updated":"2026-07-05"},
             "chapters":[{"chapter_id":1,"chapter_title":"Gravitation",
               "content_chunks":[{"chunk_id":"1.1","heading":"Newton","explanation":"Bodies attract.",
                 "difficulty":3,"importance":4,
                 "common_doubts":[{"q":"What is gravity?","a":"A force of attraction between masses."}]}]}]}
        """.trimIndent()
        val chapters = NoteStructurer.fromCurriculumJson(original)
        assertNotNull(chapters)
        assertEquals("Gravitation", chapters!!.first().title)
        val chunk = chapters.first().chunks.first()
        assertEquals("Newton", chunk.heading)
        assertTrue(chunk.common_doubts.any { it.q == "What is gravity?" })
    }

    @Test
    fun plainTextWithoutHeadingsStillStructures() {
        val chapters = NoteStructurer.structureChapters(
            "The mitochondria is the powerhouse of the cell. It produces ATP energy.",
            "my_notes.txt",
        )
        assertEquals(1, chapters.size)
        assertEquals("my notes", chapters.first().title)
        assertTrue(chapters.first().chunks.first().body.contains("mitochondria"))
    }

    @Test
    fun emptyInputYieldsNoChapters() {
        assertTrue(NoteStructurer.structureChapters("   \n  \n", "empty.txt").isEmpty())
        assertNull(NoteStructurer.fromCurriculumJson("not json at all"))
    }

    @Test
    fun uploadedContentIsSubjectAgnostic() = runTest {
        // A non-science upload (history) works identically — the pipeline is
        // subject-agnostic, matching every shipped subject format.
        val notes = """
            # The French Revolution

            The French Revolution began in 1789 and ended the absolute monarchy in France.

            Q: When did the French Revolution begin?
            A: It began in 1789.
        """.trimIndent()
        val chapters = NoteStructurer.structureChapters(notes, "history.txt")
        val doc: CurriculumDoc = json.decodeFromString(
            NoteStructurer.buildJson("My History", 9, "English", chapters)
        )
        val chunks = doc.chapters.flatMap { ch ->
            ch.content_chunks.map { IndexedChunk(it, "custom:2", 9, "My History", "English") }
        }
        val reply = Tutor(Bm25Index(chunks), MockLlmEngine(), computeEnabled = false)
            .ask("When did the French Revolution begin?")
        assertTrue(reply.text.contains("1789"))
    }
}
