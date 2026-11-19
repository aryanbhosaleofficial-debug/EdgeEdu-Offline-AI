package com.edgeedu.app.data

import kotlinx.serialization.Serializable

@Serializable
data class SolutionStep(
    val text: String,
    val latex: String,
    val verified: Boolean,
)

/**
 * A worked example as the v3.1 curriculum ships it: a problem statement plus
 * machine-verified steps ("step" carries the prose, "latex" the working).
 */
@Serializable
data class WorkedExampleStep(
    val step: String = "",
    val text: String = "",
    val latex: String = "",
    val verified: Boolean = false,
) {
    fun toSolutionStep() = SolutionStep(
        text = step.ifBlank { text },
        latex = latex,
        verified = verified,
    )
}

@Serializable
data class WorkedExample(
    val problem: String = "",
    val solution_steps: List<WorkedExampleStep> = emptyList(),
    val answer: String = "",
)

/**
 * A textbook question with its answer: `common_doubts` entries use q/a,
 * `practice_problems` use q/answer(+hint). These are the questions students
 * actually type, so the tutor answers them verbatim from the textbook.
 */
@Serializable
data class TextbookQA(
    val q: String = "",
    val a: String = "",
    val answer: String = "",
    val hint: String = "",
) {
    val reply: String get() = a.ifBlank { answer }
}

@Serializable
data class KeyTerm(val term: String = "", val meaning: String = "")

/** A named formula/equation with renderable LaTeX (v3.1 `formulas`/`equations`). */
@Serializable
data class FormulaRef(
    val name: String = "",
    val latex: String = "",
    val description: String = "",
)

@Serializable
data class Chunk(
    val chunk_id: String,
    val heading: String = "",
    val topic: String = "",
    val keywords: List<String> = emptyList(),
    // Body text: the curriculum schema uses `explanation`; notes/legacy use `text`.
    val text: String = "",
    val explanation: String = "",
    val key_points: List<String> = emptyList(),
    val difficulty: Int = 3,
    val importance: Int = 3,
    val linked_concepts: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val latex: String? = null,
    val solution_steps: List<SolutionStep>? = null,
    val worked_example: WorkedExample? = null,
    // v3.1 enrichments — all searchable, so any textbook question retrieves.
    val common_doubts: List<TextbookQA> = emptyList(),
    val practice_problems: List<TextbookQA> = emptyList(),
    val key_terms: List<KeyTerm> = emptyList(),
    val formulas: List<FormulaRef> = emptyList(),
    val equations: List<FormulaRef> = emptyList(),
    val examples: List<String> = emptyList(),
) {
    /** The chunk's body for display and indexing, from whichever field carries it. */
    val body: String
        get() = explanation.ifBlank { text }.ifBlank { key_points.joinToString(" ") }
            .ifBlank { worked_example?.problem.orEmpty() }

    /**
     * The verified steps this chunk teaches, whichever schema shipped them:
     * flat v3.0 `solution_steps`, or the v3.1 `worked_example` (the shape the
     * current curriculum uses — 300+ examples ride on it).
     */
    val verifiedSteps: List<SolutionStep>
        get() = solution_steps
            ?: worked_example?.solution_steps?.map { it.toSolutionStep() }.orEmpty()

    /** Every question this chunk can answer verbatim, with its textbook answer. */
    val questionBank: List<TextbookQA>
        get() = common_doubts + practice_problems

    /**
     * Renderable LaTeX for the chunk: the explicit v3.0 `latex` field, or the
     * first shipped formula/equation (the v3.1 way maths is attached).
     */
    val displayLatex: String?
        get() = latex ?: (formulas + equations).firstOrNull()?.latex?.takeIf { it.isNotBlank() }

    /**
     * Additional searchable text beyond the body: textbook Q&A, key terms,
     * formula names/descriptions, examples and the worked example's problem —
     * so a question copied from the textbook retrieves this chunk.
     */
    val searchExtras: String
        get() = buildString {
            questionBank.forEach { append(it.q).append(' ').append(it.reply).append(' ') }
            key_terms.forEach { append(it.term).append(' ').append(it.meaning).append(' ') }
            (formulas + equations).forEach { append(it.name).append(' ').append(it.description).append(' ') }
            examples.forEach { append(it).append(' ') }
            worked_example?.let { append(it.problem) }
        }
}

/** A textbook chapter grouping chunks (the curriculum nests chunks this way). */
@Serializable
data class Chapter(
    val chapter_id: Int = 0,
    val chapter_title: String = "",
    val content_chunks: List<Chunk> = emptyList(),
)

@Serializable
data class DocMeta(
    // Core fields the app actually uses for indexing/labelling.
    val standard: Int,
    val subject: String,
    val language: String,
    // Bookkeeping fields — defaulted so a file that omits them still loads
    // (content from different generators may not include all of them).
    val file_name: String = "",
    val board: String = "",
    val schema_version: String = "",
    val content_version: Int = 0,
    val last_updated: String = "",
)

@Serializable
data class CurriculumDoc(
    val metadata: DocMeta,
    // Curriculum files nest chunks under chapters; notes/custom subjects use a
    // flat content_chunks list. Either (or both) may be present.
    val chapters: List<Chapter> = emptyList(),
    val content_chunks: List<Chunk> = emptyList(),
    val generation_status: String = "",
) {
    /** Every chunk in the document, flattened across chapters or the flat list. */
    val allChunks: List<Chunk>
        get() = if (chapters.isNotEmpty()) chapters.flatMap { it.content_chunks } else content_chunks
}

@Serializable
data class ManifestFileInfo(val sha256: String, val bytes: Long)

@Serializable
data class Manifest(
    val manifest_schema: Int,
    val content_version: Int,
    val generated: String,
    val algorithm: String,
    val files: Map<String, ManifestFileInfo>,
    val signature: String,
)

/** Where a retrievable chunk came from — the curriculum, or the student's
 * own imported notes (PRD §8.3, source labelling). */
enum class ChunkSource { Textbook, Notes }

/** A chunk flattened with its document context, as held in the search index. */
data class IndexedChunk(
    val chunk: Chunk,
    val file: String,
    val standard: Int,
    val subject: String,
    val language: String,
    val source: ChunkSource = ChunkSource.Textbook,
    // The textbook chapter this chunk belongs to, so a session can be focused on
    // one chapter (0/"" when the file is flat — notes/custom subjects).
    val chapterId: Int = 0,
    val chapterTitle: String = "",
)
