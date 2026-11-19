package com.edgeedu.app.session

import com.edgeedu.app.data.IndexedChunk
import com.edgeedu.app.search.Bm25Index
import com.edgeedu.app.tutor.Tutor

/**
 * The PRD's Phase-1 subjects. Exactly one is active at a time: a session
 * loads only that subject's chunks, index, and tools (the math engine is a
 * maths-session tool only).
 */
enum class Subject(val label: String, val isMath: Boolean = false) {
    // Maths and Science are split into their two textbook parts so the tutor
    // knows exactly which book a question belongs to (Std 9 Science is a single
    // combined book, so it keeps the plain "Science" entry).
    Maths1("Maths 1", isMath = true),
    Maths2("Maths 2", isMath = true),
    Science("Science"),
    Science1("Science 1"),
    Science2("Science 2"),
    Geography("Geography"),
    SocialStudies("Social Studies / History");

    companion object {
        /**
         * Canonical subject token from a content file's path, e.g.
         * "data/10th/English/10th_Maths 1_English.json" -> "Maths 1". The file
         * name is the one consistent signal (the in-file `subject` metadata is
         * localised and irregular), formatted "{N}th_{Subject}_{Language}".
         */
        fun subjectTokenFromPath(path: String): String =
            path.substringAfterLast('/')
                .substringBeforeLast('.')
                .substringAfter("th_")
                .substringBeforeLast('_')
                .trim()

        /**
         * Maps a subject string (the canonical token above, or a raw file
         * subject) to its session subject, distinguishing Part 1 vs Part 2 by
         * the digit. Unknown subjects return null and are not offered.
         */
        fun of(fileSubject: String): Subject? {
            val s = fileSubject.lowercase()
            return when {
                "math" in s || "गणित" in s -> if ("2" in s || "ii" in s) Maths2 else Maths1
                // Before "science": History & Political Science is not Science.
                "history" in s || "civic" in s || "political" in s ||
                    "इतिहास" in s -> SocialStudies
                "geograph" in s || "भूगोल" in s -> Geography
                "science" in s || "विज्ञान" in s -> when {
                    "2" in s || "ii" in s -> Science2
                    "1" in s -> Science1
                    else -> Science
                }
                else -> null
            }
        }
    }
}

/**
 * Identifies the active subject — either one of the built-in curriculum
 * [Subject]s, or a user-created [Custom] subject (its content comes entirely
 * from the student's imported notes, structured on-device).
 */
sealed interface SubjectRef {
    val label: String
    val isMath: Boolean

    /**
     * Whether this session enables the verified compute path (step-by-step
     * problem solving + <calc> interception): Mathematics and Science (physics
     * numericals). Custom note subjects do not compute.
     */
    val computes: Boolean

    /** Stable key for storage and active-highlight comparison. */
    val key: String

    data class Builtin(val subject: Subject) : SubjectRef {
        override val label get() = subject.label
        override val isMath get() = subject.isMath
        override val computes get() = subject.isMath ||
            subject == Subject.Science || subject == Subject.Science1 || subject == Subject.Science2
        override val key get() = "builtin:${subject.name}"
    }

    data class Custom(val id: Long, override val label: String) : SubjectRef {
        override val isMath get() = false
        override val computes get() = false
        override val key get() = "custom:$id"
    }
}

/**
 * A focused subject session: only this subject's chunks are indexed and
 * retrievable, and the tutor's math tooling is enabled only for Mathematics.
 * Switching subjects replaces the whole session, freeing the old index.
 */
class SubjectSession(
    val subject: SubjectRef,
    val chunks: List<IndexedChunk>,
    val index: Bm25Index,
    val tutor: Tutor,
    // The focused textbook chapter, or null for the whole subject. When set, only
    // this chapter's chunks are indexed, so doubt-solving retrieval stays precise.
    val chapterId: Int? = null,
    val chapterTitle: String = "",
)

/** One textbook chapter as offered on the chapter-picker screen. */
data class ChapterInfo(val id: Int, val title: String, val chunkCount: Int)
