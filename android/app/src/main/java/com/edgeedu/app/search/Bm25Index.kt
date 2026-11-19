package com.edgeedu.app.search

import com.edgeedu.app.data.IndexedChunk
import kotlin.math.ln

/** Unicode-aware tokenizer; handles Latin and Devanagari alike. */
object Tokenizer {
    // \p{M} keeps combining marks inside tokens: Devanagari vowel signs
    // (matras) are marks, and without them "चूने" shreds into "च","न".
    // Latin accents are folded away before this regex ever sees them.
    private val word = Regex("""[\p{L}\p{M}\p{N}]+""")
    fun tokenize(text: String): List<String> =
        word.findAll(foldLatinAccents(text.lowercase())).map { it.value }.toList()

    /**
     * Fold Latin accents so a student's "rene descartes" matches the
     * textbook's "René Descartes". Combining marks are stripped only after
     * Latin letters: Devanagari vowel signs are combining marks too and must
     * survive untouched.
     */
    private fun foldLatinAccents(text: String): String {
        if (text.none { it in 'À'..'ɏ' }) return text
        val nfd = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return buildString(nfd.length) {
            var lastWasLatin = false
            for (ch in nfd) {
                val isMark = Character.getType(ch) == Character.NON_SPACING_MARK.toInt()
                if (isMark && lastWasLatin) continue
                if (!isMark) lastWasLatin = ch.code < 0x370
                append(ch)
            }
        }
    }

    private val devanagari = Regex("""[ऀ-ॿ]""")

    /** "Devanagari" covers Hindi and Marathi (same script). */
    fun isDevanagari(text: String): Boolean {
        val letters = text.filterNot { it.isWhitespace() }
        if (letters.isEmpty()) return false
        return letters.count { devanagari.matches(it.toString()) } / letters.length.toDouble() > 0.3
    }

    /**
     * Question filler words in the three mediums. Students ask doubts
     * conversationally ("explain why the …", "…क्या है"); these words carry no
     * topic signal, so retrieval drops them from the query — otherwise they
     * dilute coverage below the answer gate and heading-boosted fillers
     * ("What is …?") outrank the actual topic.
     */
    private val stopwords = setOf(
        // English articles/copulas/pronouns/prepositions/conjunctions.
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "am", "do", "does", "did", "have", "has", "had",
        "can", "could", "shall", "should", "will", "would", "may", "might", "must",
        "i", "me", "my", "we", "us", "our", "you", "your", "he", "she", "it", "its",
        "they", "them", "their", "this", "that", "these", "those",
        "of", "in", "on", "at", "to", "for", "from", "by", "with", "about", "into",
        "and", "or", "but", "not", "no", "so", "if", "then", "than", "as",
        // Question/command words used when asking a doubt.
        "what", "why", "how", "when", "where", "who", "whom", "which", "whose",
        "explain", "define", "describe", "tell", "say", "said", "please", "help", "doubt", "question",
        // Hindi fillers.
        "क्या", "क्यों", "कैसे", "कौन", "कब", "कहाँ", "कहां", "किसे", "किसका",
        "है", "हैं", "था", "थे", "थी", "हो", "हुआ", "हुई", "हुए",
        "का", "की", "के", "को", "में", "से", "पर", "और", "या", "नहीं", "एक", "यह", "वह",
        "समझाइए", "समझाओ", "समझाइये", "बताइए", "बताओ", "बताइये", "कीजिए", "करें",
        // Marathi fillers.
        "काय", "का", "कसे", "कशी", "कोण", "कोणते", "कोणती", "केव्हा", "कुठे", "कधी",
        "आहे", "आहेत", "होते", "होता", "होती", "झाले", "झाला",
        "चा", "ची", "चे", "च्या", "ला", "ना", "मध्ये", "आणि", "किंवा", "म्हणजे", "हे", "ते", "तो",
        "सांगा", "स्पष्ट", "करा", "द्या", "लिहा",
    )

    /**
     * The query terms retrieval should score: [tokenize]d with filler words
     * removed. If everything is filler (e.g. the query IS "what is"), keep the
     * original tokens rather than matching nothing.
     */
    fun contentTerms(text: String): List<String> {
        val tokens = tokenize(text)
        val content = tokens.filterNot { it in stopwords }
        return content.ifEmpty { tokens }
    }
}

data class SearchHit(
    val chunk: IndexedChunk,
    val score: Double,
    /** IDF-weighted fraction of query terms present in the chunk (0..1). */
    val coverage: Double,
)

data class SearchFilters(
    val language: String? = null,
    val standard: Int? = null,
    val subject: String? = null,
)

/**
 * BM25 over chunk text with boosted heading and keyword fields — a direct
 * port of the Phase-2 web ranker so retrieval behaves identically on device.
 */
class Bm25Index(chunks: List<IndexedChunk>) {

    private class Doc(val chunk: IndexedChunk, val termFreq: Map<String, Double>, val length: Double)

    private val docs: List<Doc>
    private val docFreq = HashMap<String, Int>()
    private val avgLength: Double

    init {
        var totalLength = 0.0
        docs = chunks.map { indexed ->
            val termFreq = HashMap<String, Double>()
            fun add(tokens: List<String>, weight: Double) {
                for (token in tokens) termFreq.merge(token, weight, Double::plus)
            }
            add(Tokenizer.tokenize(indexed.chunk.body), 1.0)
            add(Tokenizer.tokenize(indexed.chunk.heading), HEADING_BOOST)
            add(Tokenizer.tokenize(indexed.chunk.keywords.joinToString(" ")), KEYWORD_BOOST)
            // Textbook Q&A, key terms, formulas, examples: a question typed
            // straight from the textbook must retrieve the chunk that answers it.
            add(Tokenizer.tokenize(indexed.chunk.searchExtras), 1.0)

            val length = termFreq.values.sum()
            totalLength += length
            for (term in termFreq.keys) docFreq.merge(term, 1, Int::plus)
            Doc(indexed, termFreq, length)
        }
        avgLength = if (docs.isEmpty()) 1.0 else totalLength / docs.size
    }

    private fun idf(term: String): Double {
        val df = docFreq[term] ?: 0
        return ln(1 + (docs.size - df + 0.5) / (df + 0.5))
    }

    fun search(query: String, filters: SearchFilters = SearchFilters(), k: Int = 10): List<SearchHit> {
        // Score only the topic-bearing words: a conversational doubt ("explain
        // why the …") must rank and gate on its subject matter, not its filler.
        val terms = Tokenizer.contentTerms(query)
        if (terms.isEmpty()) return emptyList()
        val totalIdf = terms.sumOf { idf(it) }

        val hits = mutableListOf<SearchHit>()
        for (doc in docs) {
            val c = doc.chunk
            if (filters.language != null && c.language != filters.language) continue
            if (filters.standard != null && c.standard != filters.standard) continue
            if (filters.subject != null && c.subject != filters.subject) continue

            var score = 0.0
            var matchedIdf = 0.0
            for (term in terms) {
                val freq = doc.termFreq[term] ?: continue
                val idf = idf(term)
                matchedIdf += idf
                score += idf * freq * (K1 + 1) /
                    (freq + K1 * (1 - B + B * doc.length / avgLength))
            }
            if (score > 0) {
                hits += SearchHit(c, score, if (totalIdf > 0) matchedIdf / totalIdf else 0.0)
            }
        }
        return hits.sortedByDescending { it.score }.take(k)
    }

    companion object {
        private const val K1 = 1.5
        private const val B = 0.75
        private const val HEADING_BOOST = 2.0
        private const val KEYWORD_BOOST = 3.0
    }
}
