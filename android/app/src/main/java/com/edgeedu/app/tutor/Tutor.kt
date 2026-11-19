package com.edgeedu.app.tutor

import com.edgeedu.app.data.IndexedChunk
import com.edgeedu.app.data.SolutionStep
import com.edgeedu.app.data.TextbookQA
import com.edgeedu.app.search.Bm25Index
import com.edgeedu.app.search.SearchFilters
import com.edgeedu.app.search.Tokenizer

data class TutorReply(
    val text: String,
    val grounded: Boolean,
    val latex: String?,
    val steps: List<SolutionStep>,
    val sources: List<IndexedChunk>,
    val engine: String,
)

/**
 * Retrieve → explain → intercept, the PRD §7–8 flow:
 * retrieval grounds the model, the model explains and emits <calc> calls,
 * the interceptor swaps in engine-verified results, and pre-verified
 * solution steps ride along from the retrieved chunk.
 *
 * In a compute session ([computeEnabled] = true, for Mathematics and Science) the
 * tutor first tries to *solve a problem the student typed* — a linear equation or
 * arithmetic expression — with [MathEngine], teaching it step by step with verified
 * working, even when it isn't in the textbook (a "custom problem"). Otherwise it
 * falls back to the grounded explanation flow; with [computeEnabled] = false the
 * engine is told not to emit tool calls and the interceptor never runs.
 */
class Tutor(
    private val index: Bm25Index,
    private val engine: LlmEngine,
    private val computeEnabled: Boolean = false,
) {

    companion object {
        /** Decline unless the top chunk covers this IDF share of the query. */
        private const val MIN_COVERAGE = 0.45

        /**
         * A textbook Q&A answers the student's question directly when the
         * Jaccard similarity of content words reaches this. A question typed
         * straight from the textbook scores 1.0 and beats near-duplicates
         * (their extra terms grow the union and lower the score).
         */
        private const val QA_MATCH = 0.6
    }

    /**
     * The best textbook question (common doubt / practice problem) matching
     * what the student asked, from the retrieved chunks.
     */
    private fun matchTextbookQA(
        question: String,
        hits: List<IndexedChunk>,
    ): Pair<TextbookQA, IndexedChunk>? {
        val qTerms = Tokenizer.contentTerms(question).toSet()
        if (qTerms.isEmpty()) return null
        var best: Pair<TextbookQA, IndexedChunk>? = null
        var bestScore = 0.0
        for (chunk in hits) {
            for (qa in chunk.chunk.questionBank) {
                if (qa.q.isBlank() || qa.reply.isBlank()) continue
                val stored = Tokenizer.contentTerms(qa.q).toSet()
                if (stored.isEmpty()) continue
                val intersection = qTerms.count { it in stored }
                val union = (qTerms + stored).size
                val jaccard = intersection / union.toDouble()
                if (jaccard > bestScore) {
                    bestScore = jaccard
                    best = qa to chunk
                }
            }
        }
        return best?.takeIf { bestScore >= QA_MATCH }
    }

    suspend fun ask(question: String, filters: SearchFilters = SearchFilters()): TutorReply {
        val devanagari = Tokenizer.isDevanagari(question)
        val hits = index.search(question, filters, k = 8).filter {
            when {
                filters.language != null -> true
                devanagari -> it.chunk.language != "English"
                else -> it.chunk.language == "English"
            }
        }
        val context = hits.take(3).map { it.chunk }

        // 1) Custom-problem solver: if the student typed a solvable maths problem,
        //    teach it step by step with verified working — regardless of whether
        //    the textbook covers it. This is the "solve my problem" path (§7).
        if (computeEnabled) {
            val solution = MathEngine.solveSteps(question)
            if (solution.answer != null) {
                // Attach textbook sources only when retrieval actually covers the
                // problem — a typed custom problem usually has none.
                val relatedSources =
                    if ((hits.firstOrNull()?.coverage ?: 0.0) >= MIN_COVERAGE) context else emptyList()
                return TutorReply(
                    text = if (devanagari) {
                        "इसे हल करने का तरीका, चरण-दर-चरण (हर चरण का कारण भी देखें):"
                    } else {
                        "Here's how to solve it, step by step — read the reason on each line so you can do the next one yourself:"
                    },
                    grounded = false,
                    latex = solution.answer,
                    steps = solution.steps,
                    sources = relatedSources,
                    engine = "${engine.name} + verified math",
                )
            }
        }

        // 2) Textbook Q&A: the curriculum ships the questions students actually
        //    ask (common doubts, practice problems) with their answers. When the
        //    typed question matches one, answer verbatim from the textbook. The
        //    scan window is wide (k=24) because a question built from common
        //    words can rank its own chunk below the explanation context; no
        //    script filter here — a verbatim match is language-selective by
        //    itself, and it must survive mixed-script maths ("हल कीजिए: 3a+5b=26").
        val qaHits = index.search(question, filters, k = 24)
        val qaMatch = matchTextbookQA(question, qaHits.map { it.chunk })
        if (qaMatch != null) {
            val (qa, source) = qaMatch
            val hint = qa.hint.takeIf { it.isNotBlank() }
            return TutorReply(
                text = qa.reply + (hint?.let { "\n\nHint: $it" } ?: ""),
                grounded = true,
                latex = source.chunk.displayLatex,
                steps = source.chunk.verifiedSteps,
                sources = listOf(source),
                engine = "${engine.name} + textbook Q&A",
            )
        }

        // 3) A numeric/word problem in a compute session (e.g. a physics numerical)
        //    is answerable by the model even when retrieval is weak; otherwise low
        //    coverage means: decline, don't guess.
        val looksNumericProblem = computeEnabled &&
            (question.contains('=') || question.any { it.isDigit() })
        if ((hits.isEmpty() || hits.first().coverage < MIN_COVERAGE) && !looksNumericProblem) {
            return TutorReply(
                text = if (devanagari) {
                    "यह प्रश्न पाठ्यपुस्तक (महाराष्ट्र बोर्ड, कक्षा ९–१०) में नहीं मिला। कृपया प्रश्न बदलकर पूछें।"
                } else {
                    "I could not find this in the Maharashtra Board Class 9–10 textbooks. " +
                        "Try rephrasing — I only answer from the curriculum."
                },
                grounded = false, latex = null, steps = emptyList(), sources = emptyList(),
                engine = engine.name,
            )
        }

        // 4) Grounded explanation: the model teaches from context and emits <calc>
        //    calls per numeric step, which the interceptor replaces with verified
        //    results. Pre-verified textbook solution steps ride along when present.
        val raw = engine.explain(question, context, computeEnabled)
        val text = if (computeEnabled) CalcInterceptor.process(raw).text else raw

        val best = context.firstOrNull()
        return TutorReply(
            text = text,
            grounded = best != null,
            latex = best?.chunk?.displayLatex,
            steps = best?.chunk?.verifiedSteps.orEmpty(),
            sources = context,
            engine = engine.name,
        )
    }
}
