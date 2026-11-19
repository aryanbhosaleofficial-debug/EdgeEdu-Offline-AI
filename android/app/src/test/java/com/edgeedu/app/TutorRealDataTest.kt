package com.edgeedu.app

import com.edgeedu.app.data.Chunk
import com.edgeedu.app.data.CurriculumDoc
import com.edgeedu.app.data.IndexedChunk
import com.edgeedu.app.search.Bm25Index
import com.edgeedu.app.session.Subject
import com.edgeedu.app.tutor.MockLlmEngine
import com.edgeedu.app.tutor.Tutor
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end doubt solving over the real shipped curriculum (repo data/):
 * a realistic student question per subject must either produce verified
 * step-by-step maths or ground in the right textbook chunk — the tutor must
 * not decline in-curriculum doubts (the production bug this guards against).
 */
class TutorRealDataTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun loadChunks(): List<IndexedChunk> {
        val dataDir = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "data") }.first { it.isDirectory }
        return dataDir.walkTopDown().filter { it.extension == "json" }.flatMap { f ->
            val doc: CurriculumDoc = try {
                json.decodeFromString(f.readText())
            } catch (e: Exception) {
                return@flatMap emptyList() // manifest and other non-curriculum JSON
            }
            val rel = f.relativeTo(dataDir.parentFile).invariantSeparatorsPath
            doc.allChunks.map { c: Chunk ->
                IndexedChunk(
                    chunk = c, file = rel, standard = doc.metadata.standard,
                    subject = Subject.subjectTokenFromPath(rel),
                    language = doc.metadata.language,
                )
            }
        }.toList()
    }

    private data class Case(val subject: Subject, val question: String, val expectSteps: Boolean = false)

    @Test
    fun answersRealisticDoubtsInEverySubject() = runTest {
        val all = loadChunks()
        assertTrue("curriculum should load", all.size > 1000)

        val cases = listOf(
            // Maths: typed problems must get verified step-by-step working.
            Case(Subject.Maths1, "solve 2x + 3 = 7", expectSteps = true),
            Case(Subject.Maths1, "solve x² - 5x + 6 = 0", expectSteps = true),
            // Concept doubts, phrased the way students actually ask them.
            Case(Subject.Maths1, "what is a linear equation in two variables"),
            Case(Subject.Maths1, "explain the elimination method for simultaneous equations"),
            Case(Subject.Maths2, "explain Pythagoras theorem"),
            Case(Subject.Science1, "what is gravitation"),
            Case(Subject.Science1, "explain Kepler's laws"),
            Case(Subject.Science1, "what is centripetal force?"),
            Case(Subject.Science2, "what is heredity"),
            Case(Subject.Science2, "explain the process of transcription"),
            Case(Subject.Geography, "explain the Balaghat ranges"),
            Case(Subject.Geography, "why is population important for a country"),
            Case(Subject.SocialStudies, "what is historiography"),
            // Accented textbook names ("René") must match unaccented typing.
            Case(Subject.SocialStudies, "who was Rene Descartes and what did he say about verification"),
        )
        for (case in cases) {
            val chunks = all.filter { Subject.of(it.subject) == case.subject && it.standard == 10 }
                .ifEmpty { all.filter { Subject.of(it.subject) == case.subject } }
            assertTrue("no content for ${case.subject}", chunks.isNotEmpty())
            val computes = SubjectRefComputes(case.subject)
            val tutor = Tutor(Bm25Index(chunks), MockLlmEngine(), computeEnabled = computes)
            val reply = tutor.ask(case.question)

            val label = "[${case.subject}] ${case.question}"
            if (case.expectSteps) {
                assertTrue("$label should show worked steps", reply.steps.isNotEmpty())
                assertTrue("$label final step must be verified", reply.steps.last().verified)
            } else {
                assertTrue("$label should be answered from the textbook", reply.grounded)
                assertTrue("$label should cite sources", reply.sources.isNotEmpty())
                assertFalse(
                    "$label must not decline an in-curriculum doubt",
                    reply.text.contains("could not find"),
                )
            }
        }
    }

    @Test
    fun workedExamplesShipAsVerifiedSteps() = runTest {
        val all = loadChunks()
        // The v3.1 curriculum carries 300+ worked examples; every one must
        // surface as machine-verified steps the app can render.
        val withSteps = all.filter { it.chunk.verifiedSteps.isNotEmpty() }
        assertTrue("expected 300+ worked examples, got ${withSteps.size}", withSteps.size >= 300)
        for (c in withSteps) {
            assertTrue(
                "${c.file} ${c.chunk.chunk_id}: unverified step shipped",
                c.chunk.verifiedSteps.all { it.verified },
            )
        }
        // And the tutor shows them: ask about a worked example's own topic.
        val example = withSteps.first { it.language == "English" }
        val tutor = Tutor(Bm25Index(listOf(example)), MockLlmEngine(), computeEnabled = true)
        val reply = tutor.ask(example.chunk.heading)
        assertTrue("worked example steps should ride on the reply", reply.steps.isNotEmpty())
    }

    @Test
    fun answersEveryTextbookQuestionVerbatim() = runTest {
        // Every common doubt / practice problem shipped in the Std 9 & 10
        // textbooks, asked exactly as printed, must come back grounded with
        // the textbook's own answer — this is "solve any textbook question".
        val all = loadChunks()
        var asked = 0
        val misses = mutableListOf<String>()
        for (subject in Subject.entries) {
            for (standard in listOf(9, 10)) {
                for (language in listOf("English", "Hindi", "Marathi")) {
                    val chunks = all.filter {
                        Subject.of(it.subject) == subject &&
                            it.standard == standard && it.language == language
                    }
                    if (chunks.isEmpty()) continue
                    val computes = SubjectRefComputes(subject)
                    val tutor = Tutor(Bm25Index(chunks), MockLlmEngine(), computeEnabled = computes)
                    for (c in chunks) {
                        for (qa in c.chunk.questionBank) {
                            if (qa.q.isBlank() || qa.reply.isBlank()) continue
                            asked++
                            val reply = tutor.ask(qa.q)
                            if (!reply.text.contains(qa.reply.take(40))) {
                                misses += "[$standard/$language/$subject] ${qa.q.take(80)}"
                            }
                        }
                    }
                }
            }
        }
        assertTrue("no textbook questions found", asked > 400)
        assertTrue(
            "${misses.size}/$asked textbook questions not answered verbatim:\n" +
                misses.take(15).joinToString("\n"),
            misses.isEmpty(),
        )
    }

    @Test
    fun chunkIdsAreUniquePerFile() {
        for ((file, chunks) in loadChunks().groupBy { it.file }) {
            val ids = chunks.map { it.chunk.chunk_id }
            org.junit.Assert.assertEquals(
                "duplicate chunk ids in $file", ids.size, ids.toSet().size,
            )
        }
    }

    private fun SubjectRefComputes(subject: Subject): Boolean =
        subject.isMath || subject == Subject.Science ||
            subject == Subject.Science1 || subject == Subject.Science2
}
