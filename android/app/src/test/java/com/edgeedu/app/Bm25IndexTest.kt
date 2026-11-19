package com.edgeedu.app

import com.edgeedu.app.data.Chunk
import com.edgeedu.app.data.IndexedChunk
import com.edgeedu.app.search.Bm25Index
import com.edgeedu.app.search.SearchFilters
import com.edgeedu.app.search.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bm25IndexTest {

    private fun chunk(id: String, heading: String, text: String, language: String) = IndexedChunk(
        chunk = Chunk(chunk_id = id, heading = heading, text = text, keywords = emptyList()),
        file = "test.json",
        standard = 10,
        subject = "Math",
        language = language,
    )

    private val index = Bm25Index(
        listOf(
            chunk("1.1", "Quadratic equations", "A quadratic equation has degree two.", "English"),
            chunk("1.2", "Linear equations", "A linear equation has degree one.", "English"),
            chunk("2.1", "द्विघात समीकरण", "द्विघात समीकरण की घात दो होती है।", "Hindi"),
        )
    )

    @Test
    fun ranksTheRightChunkFirst() {
        val hits = index.search("quadratic equation")
        assertEquals("1.1", hits.first().chunk.chunk.chunk_id)
        assertTrue(hits.first().coverage > 0.9)
    }

    @Test
    fun devanagariQueriesMatchDevanagariContent() {
        val hits = index.search("द्विघात समीकरण")
        assertEquals("2.1", hits.first().chunk.chunk.chunk_id)
    }

    @Test
    fun languageFilterApplies() {
        val hits = index.search("equation", SearchFilters(language = "Hindi"))
        assertTrue(hits.isEmpty())
    }

    @Test
    fun unknownTermsLowerCoverage() {
        val hits = index.search("quadratic pasta carbonara recipe")
        assertTrue(hits.first().coverage < 0.45)
    }

    @Test
    fun conversationalFillerDoesNotDiluteCoverage() {
        // The topic words are fully covered by chunk 1.1; the question filler
        // ("explain what ... is") must not drag coverage below the answer gate.
        val hits = index.search("explain what a quadratic equation is")
        assertEquals("1.1", hits.first().chunk.chunk.chunk_id)
        assertTrue(hits.first().coverage > 0.9)
    }

    @Test
    fun headingFillerWordsCannotOutrankTheTopic() {
        // A chunk whose heading is itself a question ("What is Mass Media?")
        // must not win on the words "what is" alone.
        val trap = Bm25Index(
            listOf(
                chunk("9.1", "What is Mass Media?", "Mass media is a mode of communication.", "English"),
                chunk("9.2", "Tradition of Historiography", "Historiography is the writing of history.", "English"),
            )
        )
        val hits = trap.search("what is historiography")
        assertEquals("9.2", hits.first().chunk.chunk.chunk_id)
    }

    @Test
    fun allFillerQueryStillSearchesSomething() {
        // Degenerate query of only stopwords: fall back to the raw tokens
        // instead of matching nothing.
        assertEquals(listOf("what", "is"), Tokenizer.contentTerms("what is"))
        assertEquals(listOf("quadratic"), Tokenizer.contentTerms("what is a quadratic"))
    }

    @Test
    fun tokenizerHandlesBothScripts() {
        assertEquals(listOf("solve", "2x", "3", "7"), Tokenizer.tokenize("Solve 2x + 3 = 7"))
        assertTrue(Tokenizer.isDevanagari("द्विघात समीकरण के मूल"))
        assertTrue(!Tokenizer.isDevanagari("quadratic roots"))
    }
}
