package com.edgeedu.app.notes

import com.edgeedu.app.data.Chapter
import com.edgeedu.app.data.Chunk
import com.edgeedu.app.data.CurriculumDoc
import com.edgeedu.app.data.DocMeta
import com.edgeedu.app.data.FormulaRef
import com.edgeedu.app.data.KeyTerm
import com.edgeedu.app.data.TextbookQA
import com.edgeedu.app.search.Tokenizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A structured section of imported notes: a chapter title plus its chunks. */
data class StructuredChapter(
    val title: String,
    val chunks: List<Chunk>,
)

/**
 * The on-device structuring pipeline for user-uploaded files (no network):
 * it turns any imported text into the SAME v3.1 curriculum shape the shipped
 * subjects use — chapters of chunks with explanation, key points, keywords,
 * and the enrichments the tutor understands:
 *
 *  - "Q: … / A: …" (or a question line followed by its answer) becomes a
 *    `common_doubts` entry, so the tutor answers it verbatim like a textbook
 *    question;
 *  - "Term: meaning" lines become `key_terms`;
 *  - equation-looking lines become `formulas`;
 *  - bullet lines become `key_points`.
 *
 * It is heuristic (not the LLM) — chunking and extraction are mechanical; the
 * LLM's job is only to explain a retrieved chunk at answer time.
 */
object NoteStructurer {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val lenientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val STOPWORDS = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "can", "her", "was", "one",
        "our", "out", "his", "has", "had", "how", "its", "may", "who", "this", "that", "with",
        "from", "they", "have", "were", "will", "your", "what", "when", "which", "their", "there",
        "these", "those", "into", "than", "then", "them", "also", "such", "been", "more", "some",
    )

    // ---- Line classifiers ----------------------------------------------------

    private val markdownHeading = Regex("""^#{1,6}\s+(.+)$""")
    private val chapterHeading =
        Regex("""^(?:chapter|unit|section|अध्याय|प्रकरण|पाठ)\s+\d+\s*[:.)-]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val numberedHeading = Regex("""^\d{1,2}[.)]\s+(\S.{0,60})$""")
    private val bullet = Regex("""^[-•*▪]\s+(.+)$""")

    // A separator ([.:)-]) is required right after the tag so ordinary words
    // ("Quadratic…", "Answers vary…") are not mistaken for Q/A tags.
    private val questionTag =
        Regex("""^(?:question|que|q|प्रश्न|प्र)\s*\d*\s*[.:)\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val answerTag =
        Regex("""^(?:answer|ans|a|उत्तर|उ)\s*\d*\s*[.:)\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val termLine = Regex("""^([\p{L}\p{M}][\p{L}\p{M}\d ()/-]{1,40})\s*[:—–]\s+(\S.{9,})$""")
    private val TERM_NOISE =
        setOf("note", "notes", "example", "examples", "hint", "answer", "question", "warning", "tip")

    private fun isHeading(line: String, prevBlank: Boolean, next: String?): String? {
        markdownHeading.find(line)?.let { return it.groupValues[1].trim().trimEnd(':') }
        chapterHeading.find(line)?.let {
            val title = it.groupValues[1].trim().trimEnd(':')
            return title.ifBlank { line.trim() }
        }
        numberedHeading.find(line)?.let { m ->
            val title = m.groupValues[1].trim()
            // A numbered TITLE has no sentence punctuation; "1. Mix the acid." is a step.
            if (!title.any { it in ".?!,;" }) return title
        }
        // A short standalone line between a blank line and a real paragraph.
        val t = line.trim()
        if (prevBlank && t.length in 3..48 && t.count { it == ' ' } <= 5 &&
            !t.any { it in ".?!,;:=" } && (next?.trim()?.length ?: 0) > 48
        ) return t
        // ALL-CAPS Latin lines ("PHOTOSYNTHESIS").
        if (t.length in 3..48 && t == t.uppercase() && t != t.lowercase() &&
            !t.any { it in ".?!,;:=" } && t.any { it.isLetter() }
        ) return t
        return null
    }

    private fun isFormula(line: String): Boolean {
        val t = line.trim()
        if (t.length !in 3..90 || '=' !in t) return false
        if (t.count { it == '=' } > 2) return false
        val mathy = t.any { it in "+-*/^×÷√π%²³" } || t.any(Char::isDigit)
        // Prose sentences with '=' ("Success = hard work, they say.") stay prose.
        return mathy && t.count { it == ' ' } <= 12 && !t.endsWith("?")
    }

    // ---- Structuring ----------------------------------------------------------

    /**
     * Structures [text] into curriculum-shaped chapters. [sourceName] labels
     * the fallback chapter when the notes have no detectable headings.
     */
    fun structureChapters(text: String, sourceName: String = "Notes"): List<StructuredChapter> {
        val lines = text.lines()

        // Pass 1: split into sections at heading lines.
        data class Section(val title: String, val lines: MutableList<String>)
        val sections = mutableListOf<Section>()
        var prevBlank = true
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trimEnd()
            val title = if (line.isNotBlank()) {
                isHeading(line, prevBlank, lines.getOrNull(i + 1))
            } else null
            if (title != null) {
                sections += Section(title, mutableListOf())
            } else {
                if (sections.isEmpty()) sections += Section(defaultTitle(sourceName), mutableListOf())
                sections.last().lines += line
            }
            prevBlank = line.isBlank()
        }

        return sections
            .map { structureSection(it.title, it.lines) }
            .filter { it.chunks.isNotEmpty() }
            .ifEmpty {
                // Nothing survived (e.g. whitespace-only input) — no chapters.
                emptyList()
            }
    }

    private fun defaultTitle(sourceName: String): String =
        sourceName.substringBeforeLast('.').replace(Regex("""[_\-]+"""), " ").trim().ifBlank { "Notes" }

    /** Extracts Q&A / terms / formulas / bullets, then chunks the remaining prose. */
    private fun structureSection(title: String, sectionLines: List<String>): StructuredChapter {
        val doubts = mutableListOf<TextbookQA>()
        val terms = mutableListOf<KeyTerm>()
        val formulas = mutableListOf<FormulaRef>()
        val points = mutableListOf<String>()
        val prose = mutableListOf<String>()

        var pendingQ: String? = null
        fun answerPending(answer: String?) {
            val q = pendingQ ?: return
            if (!answer.isNullOrBlank()) {
                doubts += TextbookQA(q = q, a = answer.trim())
            } else {
                prose += q // an unanswered question stays searchable as prose
            }
            pendingQ = null
        }

        for (raw in sectionLines) {
            val line = raw.trim()
            if (line.isBlank()) continue // a Q/A pair may span a blank line
            val qTag = questionTag.find(line)
            val aTag = answerTag.find(line)
            when {
                // "A: …" answers the pending question.
                pendingQ != null && aTag != null -> answerPending(aTag.groupValues[1])
                // "Q: what is …" starts a new question (closing any unanswered one).
                qTag != null && qTag.groupValues[1].isNotBlank() -> {
                    answerPending(null)
                    pendingQ = qTag.groupValues[1].trim()
                }
                // A bare question line: "Why does ice float?"
                line.endsWith("?") && line.length >= 12 -> {
                    answerPending(null)
                    pendingQ = line
                }
                // The first content line after a question is its answer.
                pendingQ != null -> answerPending(line)
                isFormula(line) -> formulas += FormulaRef(
                    name = line.substringBefore('=').trim().take(40),
                    latex = line,
                )
                bullet.find(line) != null -> points += bullet.find(line)!!.groupValues[1].trim()
                termLine.find(line) != null -> {
                    val (term, meaning) = termLine.find(line)!!.destructured
                    if (term.trim().lowercase() !in TERM_NOISE && term.trim().count { it == ' ' } <= 4) {
                        terms += KeyTerm(term = term.trim(), meaning = meaning.trim())
                    } else {
                        prose += line
                    }
                }
                else -> prose += line
            }
        }
        answerPending(null)

        val bodies = NoteChunker.chunk(prose.joinToString(" "))
        val chunks = mutableListOf<Chunk>()
        val extrasOnly = bodies.isEmpty() &&
            (doubts.isNotEmpty() || terms.isNotEmpty() || formulas.isNotEmpty() || points.isNotEmpty())
        if (extrasOnly) {
            // A pure Q&A / glossary / formula-sheet section still needs a chunk to live in.
            val body = points.joinToString(" ").ifBlank {
                terms.joinToString(" ") { "${it.term}: ${it.meaning}" }
            }.ifBlank { doubts.joinToString(" ") { it.q } }
            chunks += Chunk(
                chunk_id = "1",
                heading = title,
                explanation = body,
                keywords = keywordsOf("$title $body"),
            )
        }
        for ((i, body) in bodies.withIndex()) {
            chunks += Chunk(
                chunk_id = "${i + 1}",
                heading = if (bodies.size == 1) title else "$title (${i + 1})",
                explanation = body,
                keywords = keywordsOf(body),
            )
        }
        if (chunks.isEmpty()) return StructuredChapter(title, emptyList())

        // Section-level extractions ride on the first chunk (retrieval finds
        // them there via searchExtras; the tutor answers the Q&A verbatim).
        chunks[0] = chunks[0].copy(
            key_points = points.take(10),
            common_doubts = doubts,
            key_terms = terms,
            formulas = formulas,
        )
        return StructuredChapter(title, chunks)
    }

    /** Top frequent, non-trivial terms in the chunk. */
    private fun keywordsOf(body: String): List<String> =
        Tokenizer.tokenize(body)
            .filter { it.length > 3 && it !in STOPWORDS && !it.all(Char::isDigit) }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(5).map { it.key }

    // ---- Curriculum-JSON round trip -------------------------------------------

    /**
     * If an uploaded .json already IS a curriculum document (flat or nested),
     * import its chapters as-is instead of re-structuring flattened text —
     * a curriculum file round-trips losslessly. Returns null when it isn't one.
     */
    fun fromCurriculumJson(raw: String): List<StructuredChapter>? {
        val doc = try {
            lenientJson.decodeFromString<CurriculumDoc>(raw)
        } catch (_: Exception) {
            return null
        }
        val chapters = when {
            doc.chapters.isNotEmpty() ->
                doc.chapters.map { StructuredChapter(it.chapter_title.ifBlank { "Chapter" }, it.content_chunks) }
            doc.content_chunks.isNotEmpty() ->
                listOf(StructuredChapter(doc.metadata.subject.ifBlank { "Notes" }, doc.content_chunks))
            else -> return null
        }
        return chapters.filter { it.chunks.isNotEmpty() }.ifEmpty { null }
    }

    /**
     * Builds a v3.1 curriculum JSON document (metadata + chapters) for a custom
     * subject, so the structured notes exist as an offline, re-usable file in
     * exactly the shape the shipped subjects use.
     */
    fun buildJson(
        subject: String,
        standard: Int,
        medium: String,
        chapters: List<StructuredChapter>,
    ): String {
        val doc = CurriculumDoc(
            metadata = DocMeta(
                file_name = "${standard}th_${subject}_$medium.json",
                standard = standard,
                subject = subject,
                language = medium,
                board = "User notes",
                schema_version = "3.1-notes",
                content_version = 1,
                last_updated = java.time.LocalDate.now().toString(),
            ),
            chapters = chapters.mapIndexed { ci, ch ->
                Chapter(
                    chapter_id = ci + 1,
                    chapter_title = ch.title,
                    content_chunks = ch.chunks.mapIndexed { i, c ->
                        c.copy(chunk_id = "${ci + 1}.${i + 1}")
                    },
                )
            },
            generation_status = "complete",
        )
        return json.encodeToString(doc)
    }
}
