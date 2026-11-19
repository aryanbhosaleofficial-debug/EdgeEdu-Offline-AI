package com.edgeedu.app

import com.edgeedu.app.data.Chunk
import com.edgeedu.app.data.IndexedChunk
import com.edgeedu.app.search.Bm25Index
import com.edgeedu.app.session.Subject
import com.edgeedu.app.tutor.LlmEngine
import com.edgeedu.app.tutor.MockLlmEngine
import com.edgeedu.app.tutor.Tutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectSessionTest {

    @Test
    fun mapsEveryShippedSubjectString() {
        // The exact subject values appearing across the 33 curriculum files.
        val expected = mapOf(
            // Canonical tokens (as derived from file names) keep their part.
            "Maths 1" to Subject.Maths1,
            "Maths 2" to Subject.Maths2,
            "Science" to Subject.Science,
            "Science 1" to Subject.Science1,
            "Science 2" to Subject.Science2,
            "Geography" to Subject.Geography,
            "History and Civics" to Subject.SocialStudies,
            "History and Political Science" to Subject.SocialStudies,
            // Raw/irregular file subjects still resolve, with the part inferred.
            "Math 1" to Subject.Maths1,
            "Mathematics 2" to Subject.Maths2,
            "Science and Technology Part 1" to Subject.Science1,
            "Science and Technology Part 2" to Subject.Science2,
            "Science and Technology" to Subject.Science,
        )
        for ((raw, subject) in expected) {
            assertEquals(raw, subject, Subject.of(raw))
        }
        assertNull(Subject.of("Sanskrit"))
    }

    @Test
    fun tokenisesSubjectFromFilePath() {
        assertEquals("Maths 1", Subject.subjectTokenFromPath("data/10th/English/10th_Maths 1_English.json"))
        assertEquals("Science 2", Subject.subjectTokenFromPath("data/10th/Marathi/10th_Science 2_Marathi.json"))
        assertEquals(
            "History and Political Science",
            Subject.subjectTokenFromPath("data/10th/English/10th_History and Political Science_English.json"),
        )
    }

    @Test
    fun onlyMathsAndScienceCompute() {
        assertTrue(Subject.Maths1.isMath)
        assertTrue(Subject.Maths2.isMath)
        assertFalse(Subject.Science1.isMath)
        assertFalse(Subject.Geography.isMath)
        assertFalse(Subject.SocialStudies.isMath)
    }

    private fun chunk(id: String, subject: String, heading: String, text: String) = IndexedChunk(
        chunk = Chunk(chunk_id = id, heading = heading, text = text),
        file = "data/test.json",
        standard = 10,
        subject = subject,
        language = "English",
    )

    /** Engine that always emits a calc call, regardless of session type. */
    private class CalcHappyEngine : LlmEngine {
        override val name = "test"
        override suspend fun explain(
            question: String,
            context: List<IndexedChunk>,
            mathSession: Boolean,
        ) = "Let me compute: <calc>eval: 2 + 2</calc>"
    }

    @Test
    fun computeSessionInterceptsCalcCalls() = runTest {
        // A worded numerical the deterministic solver can't parse (no clean
        // equation) still reaches the engine, whose <calc> call is verified.
        val index = Bm25Index(listOf(chunk("1.1", "Science", "Resistance", "Ohm's law relates V, I and R.")))
        val tutor = Tutor(index, CalcHappyEngine(), computeEnabled = true)
        val reply = tutor.ask("value of a 2 ohm resistor")
        assertTrue(reply.text.contains("= 4 ✓"))
        assertFalse(reply.text.contains("<calc>"))
    }

    @Test
    fun nonComputeSessionNeverRunsTheInterceptor() = runTest {
        val index = Bm25Index(listOf(chunk("1.1", "Geography", "Population", "Population growth between census years.")))
        val tutor = Tutor(index, CalcHappyEngine(), computeEnabled = false)
        val reply = tutor.ask("population growth census years")
        // The interceptor must not run outside maths sessions: the raw output
        // passes through untouched (a real engine is prompted/grammar-gated
        // not to emit calls at all).
        assertTrue(reply.text.contains("<calc>"))
        assertFalse(reply.text.contains("✓"))
    }

    @Test
    fun mockEngineEmitsCalcOnlyInMathSessions() = runTest {
        val engine = MockLlmEngine()
        val context = listOf(chunk("1.1", "Geography", "Census", "Population in 1991 - 1981 grew."))
        val mathOut = engine.explain("what is 1991 - 1981", context, mathSession = true)
        val geoOut = engine.explain("what is 1991 - 1981", context, mathSession = false)
        assertTrue(mathOut.contains("<calc>"))
        assertFalse(geoOut.contains("<calc>"))
    }

    @Test
    fun computeSessionSolvesQuadraticWithSuperscript() = runTest {
        // The student types the symbol-bar form "x² - 2x - 1" (no '='); the
        // engine recognises the power, solves it set to 0, and verifies the roots.
        val mathReply = Tutor(Bm25Index(emptyList()), CalcHappyEngine(), computeEnabled = true)
            .ask("x² - 2x - 1")
        assertTrue(mathReply.steps.isNotEmpty())
        assertTrue(mathReply.steps.any { it.latex.contains("\\sqrt") || it.latex.contains("pm") })
        assertTrue(mathReply.steps.last().verified)
    }

    @Test
    fun computeSessionSolvesRatioValueQuestionStepByStep() = runTest {
        // Textbook ratio question typed into a maths chat (symbol-bar ² form):
        // verified steps with the exact fractional answer, no model guessing.
        val reply = Tutor(Bm25Index(emptyList()), CalcHappyEngine(), computeEnabled = true)
            .ask("If a/b = 7/3 then find the value of (a² + b²)/(a² − b²)")
        assertTrue(reply.steps.isNotEmpty())
        assertTrue(reply.latex!!.contains("29/20"))
        assertTrue(reply.steps.last().verified)
    }

    @Test
    fun computeSessionSolvesCustomEquationStepByStep() = runTest {
        // Empty index -> zero retrieval coverage. A compute session still solves
        // the typed equation step by step (custom problem); a non-compute session
        // declines because nothing in the textbook covers it.
        val index = Bm25Index(emptyList())
        val mathReply = Tutor(index, CalcHappyEngine(), computeEnabled = true)
            .ask("solve 2*x + 3 = 7")
        val geoReply = Tutor(index, CalcHappyEngine(), computeEnabled = false)
            .ask("solve 2*x + 3 = 7")
        assertTrue(mathReply.steps.isNotEmpty())
        assertTrue(mathReply.steps.any { it.latex.contains("x = 2") })
        assertFalse(geoReply.grounded)
        assertTrue(geoReply.text.contains("could not find"))
    }
}
